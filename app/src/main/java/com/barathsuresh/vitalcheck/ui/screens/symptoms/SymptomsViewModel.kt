package com.barathsuresh.vitalcheck.ui.screens.symptoms

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.barathsuresh.vitalcheck.data.Symptoms
import com.barathsuresh.vitalcheck.data.local.AppDatabase
import com.barathsuresh.vitalcheck.data.local.HealthRecordEntity
import com.barathsuresh.vitalcheck.data.repository.HealthRepository
import kotlinx.coroutines.launch

/**
 * SymptomsViewModel: helper class for symptoms screen
 * @author barathsuresh
 */
class SymptomsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository =
        HealthRepository(AppDatabase.getInstance(application).healthRecordDao())

    var ratings by mutableStateOf(Symptoms.ALL.associateWith { 0 })
        private set

    var uploaded by mutableStateOf(false)
        private set

    fun setRating(symptom: String, rating: Int) {
        ratings = ratings.toMutableMap().apply { this[symptom] = rating }
    }

    fun uploadSymptoms(heartRate: Int, respiratoryRate: Int, onComplete: () -> Unit) {
        viewModelScope.launch {
            val record = HealthRecordEntity(
                heartRate = heartRate,
                respiratoryRate = respiratoryRate,
                nausea = ratings[Symptoms.NAUSEA] ?: 0,
                headache = ratings[Symptoms.HEADACHE] ?: 0,
                diarrhea = ratings[Symptoms.DIARRHEA] ?: 0,
                soreThroat = ratings[Symptoms.SORE_THROAT] ?: 0,
                fever = ratings[Symptoms.FEVER] ?: 0,
                muscleAche = ratings[Symptoms.MUSCLE_ACHE] ?: 0,
                lossOfSmellOrTaste = ratings[Symptoms.LOSS_OF_SMELL_OR_TASTE] ?: 0,
                cough = ratings[Symptoms.COUGH] ?: 0,
                shortnessOfBreath = ratings[Symptoms.SHORTNESS_OF_BREATH] ?: 0,
                feelingTired = ratings[Symptoms.FEELING_TIRED] ?: 0,
            )
            repository.saveRecord(record)
            uploaded = true
            onComplete()
        }
    }
}