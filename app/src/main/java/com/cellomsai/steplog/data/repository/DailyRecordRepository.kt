package com.cellomsai.steplog.data.repository

import com.cellomsai.steplog.data.database.DailyRecordDao
import com.cellomsai.steplog.data.database.entity.DailyRecord
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DailyRecordRepository @Inject constructor(
    private val dao: DailyRecordDao,
) {
    fun observeAll(): Flow<List<DailyRecord>> = dao.observeAll()

    fun observeByDate(date: LocalDate): Flow<DailyRecord?> =
        dao.observeByDate(date.toString())

    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<DailyRecord>> =
        dao.observeRange(from.toString(), to.toString())

    suspend fun findByDate(date: LocalDate): DailyRecord? =
        dao.findByDate(date.toString())

    suspend fun saveSteps(date: LocalDate, steps: Int) {
        val existing = dao.findByDate(date.toString())
        val record = existing?.copy(steps = steps, updatedAt = System.currentTimeMillis())
            ?: DailyRecord(date = date.toString(), steps = steps)
        dao.upsert(record)
    }

    suspend fun saveBodyCondition(
        date: LocalDate,
        dizzinessLevel: Int?,
        fatigueLevel: Int?,
        sleepHours: Float?,
        memo: String?,
    ) {
        val existing = dao.findByDate(date.toString())
        val record = existing?.copy(
            dizzinessLevel = dizzinessLevel,
            fatigueLevel = fatigueLevel,
            sleepHours = sleepHours,
            memo = memo,
            updatedAt = System.currentTimeMillis(),
        ) ?: DailyRecord(
            date = date.toString(),
            dizzinessLevel = dizzinessLevel,
            fatigueLevel = fatigueLevel,
            sleepHours = sleepHours,
            memo = memo,
        )
        dao.upsert(record)
    }

    suspend fun savePressure(date: LocalDate, pressure: Float) {
        val existing = dao.findByDate(date.toString())
        val record = existing?.copy(pressure = pressure, updatedAt = System.currentTimeMillis())
            ?: DailyRecord(date = date.toString(), pressure = pressure)
        dao.upsert(record)
    }

    suspend fun upsertAll(records: List<DailyRecord>) = dao.upsertAll(records)

    suspend fun deleteByDate(date: LocalDate) = dao.deleteByDate(date.toString())

    suspend fun deleteAll() = dao.deleteAll()

    suspend fun getAllForExport(): List<DailyRecord> = dao.getAllForExport()
}
