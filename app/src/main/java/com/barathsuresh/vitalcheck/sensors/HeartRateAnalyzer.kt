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
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val TAG = "VitalCheckHR"
private const val TARGET_SAMPLE_HZ = 10f
private const val WINDOW_SECONDS = 10f
private const val DETREND_SECONDS = 3f
private const val MAX_BPM = 180f

/**
 * Derives heart rate from the finger-over-lens recording.
 *
 * This reads the same signal as the course's [heartRateCalculator] — total frame
 * brightness while the flash shines through the fingertip, which dips with each pulse
 * as blood volume rises — but samples and scales it from the video's real frame rate.
 *
 * The provided helper steps 15 frames at a time over at most 425 frames and divides by
 * a hardcoded 4 seconds, which only lines up with a recording near 106fps. At the 30fps
 * this app records, those same constants sample the pulse at ~2Hz (below what a ~1.2Hz
 * pulse needs) and then apply a 5-point moving average spanning 2.5s, which averages the
 * beat away entirely. Its final `count * 60 / 4` also quantises every result to a
 * multiple of 15 bpm.
 */
suspend fun heartRateFromVideo(uri: Uri, contentResolver: ContentResolver): Int =
    withContext(Dispatchers.IO) {
        val path = resolveVideoPath(uri, contentResolver)
        if (path == null) {
            Log.e(TAG, "could not resolve a file path for $uri")
            return@withContext 0
        }

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(path)

            val frameCount = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                ?.toIntOrNull() ?: 0
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val fps = if (durationMs > 0 && frameCount > 0) frameCount * 1000f / durationMs else 0f
            if (fps <= 0f || frameCount < 32) return@withContext 0

            // Sampling every `step` frames lands close to TARGET_SAMPLE_HZ whatever the
            // camera recorded at, so the pulse is never under-sampled.
            val step = (fps / TARGET_SAMPLE_HZ).roundToInt().coerceAtLeast(1)
            val sampleHz = fps / step
            val lastFrame = minOf(frameCount - 1, (10 + WINDOW_SECONDS * fps).toInt())

            val signal = mutableListOf<Float>()
            var index = 10
            while (index <= lastFrame) {
                retriever.getFrameAtIndex(index)?.let { bitmap ->
                    signal.add(averageBrightness(bitmap))
                    bitmap.recycle()
                }
                index += step
            }
            if (signal.size < 16) return@withContext 0

            val detrendWindow = (DETREND_SECONDS * sampleHz).roundToInt().coerceAtLeast(3)
            val detrended = detrend(signal, detrendWindow)
            val refractorySamples = sampleHz * 60f / MAX_BPM
            val peaks = countPeaks(detrended, refractorySamples)
            val seconds = signal.size / sampleHz
            val bpm = if (seconds > 0f) (peaks * 60f / seconds).roundToInt() else 0

            Log.d(TAG, "fps=$fps sampleHz=$sampleHz peaks=$peaks over ${seconds}s -> $bpm bpm")
            bpm
        } catch (e: Exception) {
            Log.e(TAG, "heartRateFromVideo failed", e)
            0
        } finally {
            retriever.release()
        }
    }

private fun resolveVideoPath(uri: Uri, contentResolver: ContentResolver): String? {
    val projection = arrayOf(MediaStore.Images.Media.DATA)
    contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
        if (cursor.moveToFirst()) return cursor.getString(columnIndex)
    }
    return null
}

/** Mean of R+G+B over a grid of roughly 64x64 sampled pixels. */
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
 * Subtracts a centered moving average. The window is long relative to a heartbeat, so
 * it tracks only lighting drift and finger-pressure changes, leaving the pulse behind.
 */
private fun detrend(signal: List<Float>, window: Int): FloatArray {
    val out = FloatArray(signal.size)
    val half = window / 2
    for (i in signal.indices) {
        val from = (i - half).coerceAtLeast(0)
        val to = (i + half).coerceAtMost(signal.size - 1)
        var sum = 0f
        for (j in from..to) sum += signal[j]
        out[i] = signal[i] - sum / (to - from + 1)
    }
    return out
}

/** Counts local maxima above the trend, ignoring any closer together than MAX_BPM allows. */
private fun countPeaks(signal: FloatArray, refractorySamples: Float): Int {
    var peaks = 0
    var lastPeak = Float.NEGATIVE_INFINITY
    for (i in 1 until signal.size - 1) {
        val isLocalMax = signal[i] > signal[i - 1] && signal[i] >= signal[i + 1]
        if (isLocalMax && signal[i] > 0f && i - lastPeak >= refractorySamples) {
            peaks++
            lastPeak = i.toFloat()
        }
    }
    return peaks
}
