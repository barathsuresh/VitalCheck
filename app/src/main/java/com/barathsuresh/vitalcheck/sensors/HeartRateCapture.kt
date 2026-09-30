package com.barathsuresh.vitalcheck.sensors

/*
 * Generative AI Used: Claude (Anthropic), September 27, 2026
 * Purpose: Needed help with the CameraX video recording API.
 * Prompt: "Kotlin suspend function that records a 45-second video from the
 *          Android back camera with the flash on and returns the file Uri."
 */

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Records the finger-over-lens video that [heartRateFromVideo] measures.
 *
 * The torch stays on for the whole recording so light passes through the fingertip
 * and reaches the sensor. Without it there is no signal to measure.
 */
class HeartRateCapture(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
) {
    /**
     * Records for [durationMs] and returns the saved video's Uri.
     *
     * @param surfaceProvider optional preview target, so the user can check the frame
     *   is solid red and their finger is actually covering the lens.
     * @param onProgress called every 200ms with 0f..1f.
     */
    suspend fun recordVideo(
        durationMs: Long = 45_000L,
        surfaceProvider: Preview.SurfaceProvider? = null,
        onProgress: (Float) -> Unit = {},
    ): Uri = coroutineScope {
        // get() blocks until the camera process connects, so it must not run on main.
        val provider = withContext(Dispatchers.IO) { ProcessCameraProvider.getInstance(context).get() }
        val recorder = Recorder.Builder().build()
        val videoCapture = VideoCapture.withOutput(recorder)

        // Clear any previous binding, otherwise a retry throws on the second attempt.
        provider.unbindAll()
        // bindToLifecycle must run on main, unlike the provider lookup above.
        val camera = withContext(Dispatchers.Main) {
            val preview = surfaceProvider?.let {
                Preview.Builder().build().apply { setSurfaceProvider(it) }
            }
            val useCases = listOfNotNull<UseCase>(videoCapture, preview).toTypedArray()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, *useCases)
        }
        // Torch, not flashMode: flash fires momentarily for stills, torch stays lit.
        camera.cameraControl.enableTorch(true)

        // MediaStore needs no storage permission on API 29+ for the app's own files.
        val name = "vitalcheck_hr_${System.currentTimeMillis()}.mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        }
        val outputOptions = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        ).setContentValues(contentValues).build()

        // CameraX reports the finished file through a listener, so this bridges that
        // callback back into the coroutine: the listener completes it, await() resumes.
        val resultUri = CompletableDeferred<Uri>()
        val recording = videoCapture.output
            .prepareRecording(context, outputOptions)
            .start(ContextCompat.getMainExecutor(context)) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    if (!event.hasError()) {
                        resultUri.complete(event.outputResults.outputUri)
                    } else {
                        // Surfaces as a normal exception for the caller's try/catch.
                        resultUri.completeExceptionally(
                            RuntimeException("Video recording error: ${event.error}"),
                        )
                    }
                }
            }

        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < durationMs) {
            delay(200)
            onProgress(((System.currentTimeMillis() - startTime).toFloat() / durationMs).coerceIn(0f, 1f))
        }
        onProgress(1f)
        // stop() only starts finalisation. The file is truncated until Finalize arrives,
        // which is why the await below cannot be skipped.
        recording.stop()

        val uri = resultUri.await()
        // Release the camera only after finalising, or the last frames lose the torch.
        camera.cameraControl.enableTorch(false)
        withContext(Dispatchers.Main) { provider.unbindAll() }
        uri
    }
}
