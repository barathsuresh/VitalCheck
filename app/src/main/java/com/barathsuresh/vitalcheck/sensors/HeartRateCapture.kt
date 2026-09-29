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

class HeartRateCapture(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
) {
    suspend fun recordVideo(
        durationMs: Long = 45_000L,
        surfaceProvider: Preview.SurfaceProvider? = null,
        onProgress: (Float) -> Unit = {},
    ): Uri = coroutineScope {
        val provider = withContext(Dispatchers.IO) { ProcessCameraProvider.getInstance(context).get() }
        val recorder = Recorder.Builder().build()
        val videoCapture = VideoCapture.withOutput(recorder)

        provider.unbindAll()
        val camera = withContext(Dispatchers.Main) {
            val preview = surfaceProvider?.let {
                Preview.Builder().build().apply { setSurfaceProvider(it) }
            }
            val useCases = listOfNotNull<UseCase>(videoCapture, preview).toTypedArray()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, *useCases)
        }
        camera.cameraControl.enableTorch(true)

        val name = "vitalcheck_hr_${System.currentTimeMillis()}.mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        }
        val outputOptions = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        ).setContentValues(contentValues).build()

        val resultUri = CompletableDeferred<Uri>()
        val recording = videoCapture.output
            .prepareRecording(context, outputOptions)
            .start(ContextCompat.getMainExecutor(context)) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    if (!event.hasError()) {
                        resultUri.complete(event.outputResults.outputUri)
                    } else {
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
        recording.stop()

        val uri = resultUri.await()
        camera.cameraControl.enableTorch(false)
        withContext(Dispatchers.Main) { provider.unbindAll() }
        uri
    }
}