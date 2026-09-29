package com.barathsuresh.vitalcheck.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Data to be stored to the table
 * @author barathsuresh
 */
@Entity(tableName = "records")
data class HealthRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val heartRate: Int,
    val respiratoryRate: Int,
    val nausea: Int,
    val headache: Int,
    val diarrhea: Int,
    val soreThroat: Int,
    val fever: Int,
    val muscleAche: Int,
    val lossOfSmellOrTaste: Int,
    val cough: Int,
    val shortnessOfBreath: Int,
    val feelingTired: Int,
)