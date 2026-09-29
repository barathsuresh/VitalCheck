package com.barathsuresh.vitalcheck.data.repository

import com.barathsuresh.vitalcheck.data.local.HealthRecordDao
import com.barathsuresh.vitalcheck.data.local.HealthRecordEntity
import kotlinx.coroutines.flow.Flow

/**
 * HealthRepository
 * @author barathsuresh
 */
class HealthRepository(private val dao: HealthRecordDao) {
    fun getAllRecords(): Flow<List<HealthRecordEntity>> = dao.getAll()

    suspend fun saveRecord(record: HealthRecordEntity) = dao.insert(record)

    suspend fun deleteAllRecords() = dao.deleteAll()
}