package com.barathsuresh.vitalcheck.ui.screens.vitals
/*
 * Generative AI Used: Claude (Anthropic), September 27, 2026
 * Purpose: Needed help with the camera permission request and preview in Compose.
 * Prompt: "Jetpack Compose screen that asks for CAMERA permission, shows a CameraX
 *          preview, and displays the heart rate and respiratory rate results."
 */
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun VitalsScreen(
    onContinue: (heartRate: Int, respiratoryRate: Int) -> Unit,
    viewModel: VitalsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Both captures run for 45s while the phone sits untouched (on the chest, or
    // finger held over the camera). Without this, the screen times out mid-capture
    // and many OEMs freeze the app's main thread the moment the screen locks, which
    // stalls the capture indefinitely instead of just running slower.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
        if (granted) viewModel.measureHeartRate(lifecycleOwner, previewView.surfaceProvider)
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start,
        ) {
            Text("Vital Signs Measurement", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(24.dp))

            Text("1. Heart Rate", style = MaterialTheme.typography.titleMedium)
            Text("Cover the back camera and flash with your index finger, then hold still for 45 seconds.")
            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                if (viewModel.heartRateState is VitalCaptureState.Idle) {
                    Text(
                        "Camera preview starts when you begin measuring",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Text(
                "When covered correctly the preview should be a solid dark red.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))

            HeartRateSection(
                state = viewModel.heartRateState,
                onMeasure = {
                    if (hasCameraPermission) viewModel.measureHeartRate(lifecycleOwner, previewView.surfaceProvider)
                    else permissionLauncher.launch(Manifest.permission.CAMERA)
                },
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text("2. Respiratory Rate", style = MaterialTheme.typography.titleMedium)
            Text("Lay down and place the phone flat on your chest, then hold still for 45 seconds.")
            Spacer(Modifier.height(8.dp))
            RespiratoryRateSection(
                state = viewModel.respiratoryRateState,
                onMeasure = { viewModel.measureRespiratoryRate() },
            )

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = { onContinue(viewModel.heartRateValue(), viewModel.respiratoryRateValue()) },
                enabled = viewModel.canContinue,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Continue to Symptoms")
            }
        }
    }
}

@Composable
private fun HeartRateSection(state: VitalCaptureState, onMeasure: () -> Unit) {
    when (state) {
        is VitalCaptureState.Idle -> Button(onClick = onMeasure) { Text("Measure Heart Rate") }
        is VitalCaptureState.Recording -> Column {
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            Text("Recording... keep your finger still over the camera and flash")
        }
        is VitalCaptureState.Processing -> Column {
            CircularProgressIndicator()
            Spacer(Modifier.height(4.dp))
            Text("Processing video (this can take a few minutes)...")
        }
        is VitalCaptureState.Done -> Text(
            "Heart Rate: ${state.value} bpm",
            style = MaterialTheme.typography.bodyLarge,
        )
        is VitalCaptureState.Error -> Column {
            Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(4.dp))
            Button(onClick = onMeasure) { Text("Retry") }
        }
    }
}

@Composable
private fun RespiratoryRateSection(state: VitalCaptureState, onMeasure: () -> Unit) {
    when (state) {
        is VitalCaptureState.Idle -> Button(onClick = onMeasure) { Text("Measure Respiratory Rate") }
        is VitalCaptureState.Recording -> Column {
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            Text("Measuring... keep the phone still on your chest")
        }
        is VitalCaptureState.Processing -> CircularProgressIndicator()
        is VitalCaptureState.Done -> Text(
            "Respiratory Rate: ${state.value} breaths/min",
            style = MaterialTheme.typography.bodyLarge,
        )
        is VitalCaptureState.Error -> Column {
            Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(4.dp))
            Button(onClick = onMeasure) { Text("Retry") }
        }
    }
}