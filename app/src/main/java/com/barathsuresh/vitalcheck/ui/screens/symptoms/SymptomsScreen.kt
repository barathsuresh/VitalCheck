package com.barathsuresh.vitalcheck.ui.screens.symptoms

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
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.barathsuresh.vitalcheck.data.Symptoms
import com.barathsuresh.vitalcheck.ui.components.StarRating

/**
 * SymptomsScreen: Screen where we can store the details to the room
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SymptomsScreen(
    heartRate: Int,
    respiratoryRate: Int,
    onUploadComplete: () -> Unit,
    viewModel: SymptomsViewModel = viewModel(),
) {
    // Observable variables to be used in the UI
    var selectedSymptom by remember { mutableStateOf(Symptoms.ALL.first()) }
    var expanded by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Symptom Logging Page", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text("Heart Rate: $heartRate bpm   ·   Respiratory Rate: $respiratoryRate breaths/min")
            Spacer(Modifier.height(24.dp))

            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = selectedSymptom,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Symptom") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    Symptoms.ALL.forEach { symptom ->
                        DropdownMenuItem(
                            text = { Text(symptom) },
                            onClick = {
                                selectedSymptom = symptom
                                expanded = false
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            StarRating(
                rating = viewModel.ratings[selectedSymptom] ?: 0,
                onRatingChange = { viewModel.setRating(selectedSymptom, it) },
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text("Reported symptoms", style = MaterialTheme.typography.titleSmall)
            Symptoms.ALL.forEach { symptom ->
                val rating = viewModel.ratings[symptom] ?: 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(symptom)
                    Text(if (rating > 0) "$rating / 5" else "-")
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { viewModel.uploadSymptoms(heartRate, respiratoryRate, onUploadComplete) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("UPLOAD SYMPTOMS")
            }
        }
    }
}