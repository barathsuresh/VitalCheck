package com.barathsuresh.vitalcheck.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Health Records
 * @author barathsuresh
 */
@Dao
interface HealthRecordDao {
    @Insert
    suspend fun insert(record: HealthRecordEntity)

    @Query("SELECT * FROM records ORDER BY timestamp DESC")
    fun getAll(): Flow<List<HealthRecordEntity>>

    @Query("DELETE FROM records")
    suspend fun deleteAll()
}