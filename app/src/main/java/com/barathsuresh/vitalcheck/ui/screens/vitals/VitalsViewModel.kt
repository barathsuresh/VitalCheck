package com.barathsuresh.vitalcheck.ui.screens.vitals
/*
 * Generative AI Used: Claude (Anthropic), September 27, 2026
 * Purpose: Needed help structuring the measurement states in a ViewModel.
 * Prompt: "Android ViewModel in Kotlin that runs the heart rate and respiratory
 *          rate captures and exposes Idle/Recording/Done/Error states to Compose."
 */
import android.app.Application
import android.util.Log
import androidx.camera.core.Preview
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.barathsuresh.vitalcheck.sensors.HeartRateCapture
import com.barathsuresh.vitalcheck.sensors.RespiratoryRateCapture
import com.barathsuresh.vitalcheck.sensors.heartRateFromVideo
import com.barathsuresh.vitalcheck.sensors.respiratoryRateCalculator
import kotlinx.coroutines.launch

private const val TAG = "VitalCheckVVM"

/**
 * respiratoryRateCalculator is calibrated for samples spaced at SENSOR_DELAY_NORMAL's
 * nominal 200ms. The delay constants are only hints and this hardware delivers at
 * ~66ms regardless, so the samples are re-spaced to 200ms explicitly.
 */
private const val RESPIRATORY_SAMPLE_INTERVAL_MS = 200L

sealed class VitalCaptureState {
    data object Idle : VitalCaptureState()
    data class Recording(val progress: Float) : VitalCaptureState()
    data object Processing : VitalCaptureState()
    data class Done(val value: Int) : VitalCaptureState()
    data class Error(val message: String) : VitalCaptureState()
}

class VitalsViewModel(application: Application) : AndroidViewModel(application) {
    var heartRateState by mutableStateOf<VitalCaptureState>(VitalCaptureState.Idle)
        private set
    var respiratoryRateState by mutableStateOf<VitalCaptureState>(VitalCaptureState.Idle)
        private set

    val canContinue: Boolean
        get() = heartRateState is VitalCaptureState.Done && respiratoryRateState is VitalCaptureState.Done

    fun heartRateValue(): Int = (heartRateState as? VitalCaptureState.Done)?.value ?: 0
    fun respiratoryRateValue(): Int = (respiratoryRateState as? VitalCaptureState.Done)?.value ?: 0

    fun measureHeartRate(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider? = null) {
        if (heartRateState is VitalCaptureState.Recording || heartRateState is VitalCaptureState.Processing) return
        viewModelScope.launch {
            heartRateState = VitalCaptureState.Recording(0f)
            try {
                val app = getApplication<Application>()
                val uri = HeartRateCapture(app, lifecycleOwner).recordVideo(
                    surfaceProvider = surfaceProvider,
                ) { progress ->
                    heartRateState = VitalCaptureState.Recording(progress)
                }
                heartRateState = VitalCaptureState.Processing
                val bpm = heartRateFromVideo(uri, app.contentResolver)
                heartRateState = VitalCaptureState.Done(bpm)
            } catch (e: Exception) {
                heartRateState = VitalCaptureState.Error(e.message ?: "Heart rate capture failed")
            }
        }
    }

    fun measureRespiratoryRate() {
        if (respiratoryRateState is VitalCaptureState.Recording) return
        viewModelScope.launch {
            respiratoryRateState = VitalCaptureState.Recording(0f)
            try {
                val app = getApplication<Application>()
                val samples = RespiratoryRateCapture(app).collect { progress ->
                    respiratoryRateState = VitalCaptureState.Recording(progress)
                }
                val (x, y, z) = samples.downsample(RESPIRATORY_SAMPLE_INTERVAL_MS)
                val bpm = respiratoryRateCalculator(x, y, z)
                respiratoryRateState = VitalCaptureState.Done(bpm)
            } catch (e: Throwable) {
                Log.e(TAG, "measureRespiratoryRate() failed", e)
                respiratoryRateState = VitalCaptureState.Error(e.message ?: "Respiratory rate capture failed")
            }
        }
    }
}