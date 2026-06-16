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
        tinnitusLevel: Int?,
        sleepHours: Float?,
        weightKg: Float?,
        memo: String?,
    ) {
        val now = System.currentTimeMillis()
        val updated = dao.updateBodyCondition(
            date = date.toString(),
            dizziness = dizzinessLevel,
            fatigue = fatigueLevel,
            tinnitus = tinnitusLevel,
            sleep = sleepHours,
            memo = memo,
            weightKg = weightKg,
            updatedAt = now,
        )
        if (updated == 0) dao.upsert(
            DailyRecord(
                date = date.toString(),
                dizzinessLevel = dizzinessLevel,
                fatigueLevel = fatigueLevel,
                tinnitusLevel = tinnitusLevel,
                sleepHours = sleepHours,
                memo = memo,
                weightKg = weightKg,
            )
        )
    }

    suspend fun savePressure(date: LocalDate, pressure: Float) {
        val now = System.currentTimeMillis()
        val updated = dao.updatePressure(date.toString(), pressure, now)
        if (updated == 0) dao.upsert(DailyRecord(date = date.toString(), pressure = pressure))
    }

    /** 当日の気圧・降水量・天気コードをまとめて保存する。 */
    suspend fun saveWeather(
        date: LocalDate,
        pressure: Float,
        precipitationMm: Float,
        weatherCode: Int?,
    ) {
        val now = System.currentTimeMillis()
        val updated = dao.updateWeather(date.toString(), pressure, precipitationMm, weatherCode, now)
        if (updated == 0) dao.upsert(
            DailyRecord(
                date = date.toString(),
                pressure = pressure,
                precipitationMm = precipitationMm,
                weatherCode = weatherCode,
            )
        )
    }

    /**
     * 取得済みの日次降水量を、まだ降水量が未記録（null）の既存レコードにだけ書き込む。
     * 該当日のレコードが無い場合・既に降水量がある場合は何もしない。
     * @return 補完できた日数
     */
    suspend fun backfillPrecipitation(precipitationByDate: Map<String, Float>): Int {
        val now = System.currentTimeMillis()
        var filled = 0
        precipitationByDate.forEach { (date, mm) ->
            filled += dao.fillPrecipitationIfMissing(date, mm, now)
        }
        return filled
    }

    suspend fun upsertAll(records: List<DailyRecord>) = dao.upsertAll(records)

    suspend fun deleteByDate(date: LocalDate) = dao.deleteByDate(date.toString())

    suspend fun deleteAll() = dao.deleteAll()

    suspend fun getAllForExport(): List<DailyRecord> = dao.getAllForExport()
}
