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
        val now = System.currentTimeMillis()
        val updated = dao.updateSteps(date.toString(), steps, now)
        if (updated == 0) dao.upsert(DailyRecord(date = date.toString(), steps = steps))
    }

    suspend fun saveBodyCondition(
        date: LocalDate,
        dizzinessLevel: Int?,
        fatigueLevel: Int?,
        sleepHours: Float?,
        memo: String?,
    ) {
        val now = System.currentTimeMillis()
        val updated = dao.updateBodyCondition(
            date = date.toString(),
            dizziness = dizzinessLevel,
            fatigue = fatigueLevel,
            sleep = sleepHours,
            memo = memo,
            updatedAt = now,
        )
        if (updated == 0) dao.upsert(
            DailyRecord(
                date = date.toString(),
                dizzinessLevel = dizzinessLevel,
                fatigueLevel = fatigueLevel,
                sleepHours = sleepHours,
                memo = memo,
            )
        )
    }

    suspend fun savePressure(date: LocalDate, pressure: Float) {
        val now = System.currentTimeMillis()
        val updated = dao.updatePressure(date.toString(), pressure, now)
        if (updated == 0) dao.upsert(DailyRecord(date = date.toString(), pressure = pressure))
    }

    suspend fun upsertAll(records: List<DailyRecord>) = dao.upsertAll(records)

    suspend fun deleteByDate(date: LocalDate) = dao.deleteByDate(date.toString())

    suspend fun deleteAll() = dao.deleteAll()

    suspend fun getAllForExport(): List<DailyRecord> = dao.getAllForExport()
}
