package com.barathsuresh.vitalcheck.sensors

/*
 * Generative AI Used: Claude (Anthropic), September 28, 2026
 * Purpose: Needed help extracting a pulse signal from video frame brightness.
 * Prompt: "Kotlin function that reads an Android video file, samples frame
 *          brightness at a rate based on the video's real fps, and counts
 *          peaks to estimate heart rate in bpm."
 */

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val TAG = "VitalCheckHR"

/** Brightness readings per second. A 1-1.7 Hz pulse needs well above 2 Hz to locate peaks. */
private const val TARGET_SAMPLE_HZ = 10f

/** Seconds of video analysed. Decoding a frame costs ~10ms, so all 1350 would take minutes. */
private const val WINDOW_SECONDS = 10f

/** Detrend window. Spans several beats, so averaging over it leaves only the slow drift. */
private const val DETREND_SECONDS = 3f

/** Upper physiological limit, used to reject peaks too close together to be real beats. */
private const val MAX_BPM = 180f

/**
 * Heart rate in bpm from a finger-over-lens recording.
 *
 * Each beat pushes blood into the fingertip, which absorbs more of the flash, so the
 * frames dim once per beat. Every constant here is in seconds and converted using the
 * video's measured frame rate, so the maths holds whatever speed the camera recorded at.
 *
 * Returns 0 if the video cannot be read or holds too little usable signal.
 */
suspend fun heartRateFromVideo(uri: Uri, contentResolver: ContentResolver): Int =
    withContext(Dispatchers.IO) {
        // Open the content URI directly. The MediaStore DATA path column is deprecated
        // and returns null on some devices under scoped storage.
        val descriptor = contentResolver.openFileDescriptor(uri, "r")
        if (descriptor == null) {
            Log.e(TAG, "could not open $uri")
            return@withContext 0
        }

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(descriptor.fileDescriptor)

            // Measure the real frame rate instead of assuming 30. It varies by device,
            // capture mode and lighting, and every step below is scaled from it.
            val frameCount = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                ?.toIntOrNull() ?: 0
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val fps = if (durationMs > 0 && frameCount > 0) frameCount * 1000f / durationMs else 0f
            if (fps <= 0f || frameCount < 32) return@withContext 0

            val step = (fps / TARGET_SAMPLE_HZ).roundToInt().coerceAtLeast(1)
            // Recomputed because step was rounded: at 25fps the real rate is 8.33Hz, not 10.
            val sampleHz = fps / step
            val lastFrame = minOf(frameCount - 1, (10 + WINDOW_SECONDS * fps).toInt())

            // Skip the first 10 frames, recorded while auto-exposure is still settling.
            val signal = mutableListOf<Float>()
            var index = 10
            while (index <= lastFrame) {
                retriever.getFrameAtIndex(index)?.let { bitmap ->
                    signal.add(averageBrightness(bitmap))
                    bitmap.recycle() // Each frame is several MB; holding ~100 would run out of memory.
                }
                index += step
            }
            if (signal.size < 16) return@withContext 0

            val detrendWindow = (DETREND_SECONDS * sampleHz).roundToInt().coerceAtLeast(3)
            val detrended = detrend(signal, detrendWindow)
            val refractorySamples = sampleHz * 60f / MAX_BPM
            val peaks = countPeaks(detrended, refractorySamples)
            // Elapsed time comes from the samples actually collected, not a fixed constant.
            val seconds = signal.size / sampleHz
            val bpm = if (seconds > 0f) (peaks * 60f / seconds).roundToInt() else 0

            Log.d(TAG, "fps=$fps sampleHz=$sampleHz peaks=$peaks over ${seconds}s -> $bpm bpm")
            bpm
        } catch (e: Exception) {
            Log.e(TAG, "heartRateFromVideo failed", e)
            0
        } finally {
            // The retriever reads frames long after setDataSource returns, so the
            // descriptor has to stay open until the analysis is done.
            retriever.release()
            descriptor.close()
        }
    }

/**
 * Mean of R+G+B over a grid of roughly 64x64 pixels.
 *
 * The fingertip fills the frame and is near uniform, so a grid is as good as all two
 * million pixels. Deriving the step from the bitmap keeps this correct at any resolution.
 */
private fun averageBrightness(bitmap: Bitmap): Float {
    val stepX = (bitmap.width / 64).coerceAtLeast(1)
    val stepY = (bitmap.height / 64).coerceAtLeast(1)
    var total = 0L
    var count = 0
    var y = 0
    while (y < bitmap.height) {
        var x = 0
        while (x < bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            total += Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)
            count++
            x += stepX
        }
        y += stepY
    }
    return if (count > 0) total.toFloat() / count else 0f
}

/**
 * Subtracts a centred moving average, leaving the signal wobbling around zero.
 *
 * Finger movement and pressure drift far more than the pulse does. The window spans
 * several beats, so its average tracks only that drift and subtracting it leaves the pulse.
 */
private fun detrend(signal: List<Float>, window: Int): FloatArray {
    val out = FloatArray(signal.size)
    val half = window / 2
    for (i in signal.indices) {
        // Clamped at both ends, and divided by the count actually in range.
        val from = (i - half).coerceAtLeast(0)
        val to = (i + half).coerceAtMost(signal.size - 1)
        var sum = 0f
        for (j in from..to) sum += signal[j]
        out[i] = signal[i] - sum / (to - from + 1)
    }
    return out
}

/**
 * Counts beats as local maxima above the trend line.
 *
 * [refractorySamples] is the closest two beats could physically be, which stops one
 * jagged peak from being counted several times.
 */
private fun countPeaks(signal: FloatArray, refractorySamples: Float): Int {
    var peaks = 0
    var lastPeak = Float.NEGATIVE_INFINITY
    // Ends are skipped since they have no neighbour on one side. The > and >= pair
    // counts a flat top once rather than twice or not at all.
    for (i in 1 until signal.size - 1) {
        val isLocalMax = signal[i] > signal[i - 1] && signal[i] >= signal[i + 1]
        // Above zero means above the local trend, so bumps inside a trough are ignored.
        if (isLocalMax && signal[i] > 0f && i - lastPeak >= refractorySamples) {
            peaks++
            lastPeak = i.toFloat()
        }
    }
    return peaks
}
