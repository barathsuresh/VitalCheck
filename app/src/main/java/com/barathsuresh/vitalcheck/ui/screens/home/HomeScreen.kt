package com.barathsuresh.vitalcheck.ui.screens.home
/*
 * Generative AI Used: Claude (Anthropic), September 29, 2026
 * Scope: the stored-records list and the RecordCard composable only. The screen
 *        layout, buttons and delete dialog are my own.
 * Purpose: Needed help listing the saved database rows on the home screen.
 * Prompt: "Compose screen that shows how many records are stored and a card per
 *          record with its heart rate, respiratory rate and symptom ratings."
 */

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.barathsuresh.vitalcheck.data.Symptoms
import com.barathsuresh.vitalcheck.data.local.HealthRecordEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * HomeScreen
 * @author barathsuresh
 */
@Composable
fun HomeScreen(
    onRecordHealthData: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    // reactive elements state must be changed
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val records by viewModel.records.collectAsState()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("VitalCheck", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(32.dp))

            Button(onClick = onRecordHealthData, modifier = Modifier.fillMaxWidth()) {
                Text("Record Health Data")
            }
            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = records.isNotEmpty(),
            ) {
                Text("Delete All Recorded Data")
            }

            Spacer(Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text(
                "Stored records: ${records.size}",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            // Database Records
            if (records.isEmpty()) {
                Text(
                    "The database is empty.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                records.forEach { record ->
                    RecordCard(record)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
    // Delete Dialog to confirm deletion
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete all data?") },
            text = { Text("This permanently erases all recorded health data from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteAllData()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

private val TIMESTAMP_FORMAT = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault())

/** One stored row, showing every column so the saved record can be read back in full. */
@Composable
private fun RecordCard(record: HealthRecordEntity) {
    val symptomRatings = listOf(
        Symptoms.NAUSEA to record.nausea,
        Symptoms.HEADACHE to record.headache,
        Symptoms.DIARRHEA to record.diarrhea,
        Symptoms.SORE_THROAT to record.soreThroat,
        Symptoms.FEVER to record.fever,
        Symptoms.MUSCLE_ACHE to record.muscleAche,
        Symptoms.LOSS_OF_SMELL_OR_TASTE to record.lossOfSmellOrTaste,
        Symptoms.COUGH to record.cough,
        Symptoms.SHORTNESS_OF_BREATH to record.shortnessOfBreath,
        Symptoms.FEELING_TIRED to record.feelingTired,
    )

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Record #${record.id}", style = MaterialTheme.typography.titleSmall)
            Text(
                TIMESTAMP_FORMAT.format(Date(record.timestamp)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text("Heart rate: ${record.heartRate} bpm")
            Text("Respiratory rate: ${record.respiratoryRate} breaths/min")
            Spacer(Modifier.height(8.dp))
            symptomRatings.forEach { (symptom, rating) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(symptom, style = MaterialTheme.typography.bodySmall)
                    Text("$rating", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
