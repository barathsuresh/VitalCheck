package com.barathsuresh.vitalcheck.ui.screens.home
/*
 * Generative AI Used: Claude (Anthropic), September 29, 2026
 * Scope: the `records` StateFlow below only. The rest of this file is my own.
 * Purpose: Needed help exposing the stored records to the UI so inserts and
 *          deletes show up without a manual refresh.
 * Prompt: "In an Android ViewModel, turn a Room Flow of records into a StateFlow
 *          that a Compose screen can collect."
 */

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.barathsuresh.vitalcheck.data.local.AppDatabase
import com.barathsuresh.vitalcheck.data.local.HealthRecordEntity
import com.barathsuresh.vitalcheck.data.repository.HealthRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * HomeViewModel helper for HomeScreen
 * @author barathsuresh
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository =
        HealthRepository(AppDatabase.getInstance(application).healthRecordDao())

    /**
     * Room re-emits this whenever health_records changes, so the screen reflects an
     * insert or a delete without being told to refresh.
     */
    val records: StateFlow<List<HealthRecordEntity>> = repository.getAllRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteAllData(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteAllRecords()
            onComplete()
        }
    }
}
