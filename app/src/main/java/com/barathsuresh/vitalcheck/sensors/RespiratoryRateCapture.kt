package com.barathsuresh.vitalcheck.sensors
/*
 * Generative AI Used: Claude (Anthropic), September 27, 2026
 * Purpose: Needed help collecting accelerometer data inside a coroutine.
 * Prompt: "Kotlin class that collects 45 seconds of accelerometer x/y/z samples
 *          with SensorManager and can re-space them to a fixed interval."
 */
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Raw accelerometer samples with timestamps.
 *
 * [respiratoryRateCalculator] compares each consecutive pair of samples against a
 * fixed 0.15 threshold, so the interval the samples are spaced at decides the
 * result entirely: too fast and sensor noise trips the threshold constantly, too
 * slow and the breathing signal never trips it. [downsample] re-spaces the raw
 * capture to a chosen interval before the calculation runs.
 */
class AccelerometerSamples(
    val timestamps: MutableList<Long> = mutableListOf(),
    val x: MutableList<Float> = mutableListOf(),
    val y: MutableList<Float> = mutableListOf(),
    val z: MutableList<Float> = mutableListOf(),
) {
    val size: Int get() = timestamps.size

    fun downsample(intervalMs: Long): Triple<MutableList<Float>, MutableList<Float>, MutableList<Float>> {
        val dx = mutableListOf<Float>()
        val dy = mutableListOf<Float>()
        val dz = mutableListOf<Float>()
        var nextAt = Long.MIN_VALUE
        for (i in 0 until size) {
            val t = timestamps[i]
            if (t >= nextAt) {
                dx.add(x[i])
                dy.add(y[i])
                dz.add(z[i])
                nextAt = t + intervalMs
            }
        }
        return Triple(dx, dy, dz)
    }
}

/**
 * Collects 45s of accelerometer samples with the phone lying on the chest, at a
 * high rate so the caller can re-space them to whatever interval the calculation
 * needs.
 */
class RespiratoryRateCapture(context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    suspend fun collect(
        durationMs: Long = 45_000L,
        onProgress: (Float) -> Unit = {},
    ): AccelerometerSamples =
        suspendCancellableCoroutine { cont ->
            val samples = AccelerometerSamples()

            if (accelerometer == null) {
                cont.resume(samples)
                return@suspendCancellableCoroutine
            }

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    samples.timestamps.add(System.currentTimeMillis())
                    samples.x.add(event.values[0])
                    samples.y.add(event.values[1])
                    samples.z.add(event.values[2])
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }

            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)

            val handler = Handler(Looper.getMainLooper())
            val startTime = System.currentTimeMillis()
            // The ticker and the finish callback are separate timers, so the ticker can
            // still have a tick queued when the capture completes. Without this guard it
            // wakes up afterward and reports progress over the final result.
            var finished = false
            val progressRunnable = object : Runnable {
                override fun run() {
                    if (finished) return
                    val elapsed = System.currentTimeMillis() - startTime
                    onProgress((elapsed.toFloat() / durationMs).coerceIn(0f, 1f))
                    if (elapsed < durationMs) handler.postDelayed(this, 200)
                }
            }
            handler.post(progressRunnable)

            handler.postDelayed({
                finished = true
                handler.removeCallbacks(progressRunnable)
                sensorManager.unregisterListener(listener)
                onProgress(1f)
                if (cont.isActive) cont.resume(samples)
            }, durationMs)

            cont.invokeOnCancellation {
                sensorManager.unregisterListener(listener)
                handler.removeCallbacksAndMessages(null)
            }
        }
}