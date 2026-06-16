package com.cellomsai.steplog.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.cellomsai.steplog.data.database.entity.DailyRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyRecordDao {

    @Query("SELECT * FROM daily_records ORDER BY date DESC")
    fun observeAll(): Flow<List<DailyRecord>>

    @Query("SELECT * FROM daily_records WHERE date = :date LIMIT 1")
    suspend fun findByDate(date: String): DailyRecord?

    @Query("SELECT * FROM daily_records WHERE date = :date LIMIT 1")
    fun observeByDate(date: String): Flow<DailyRecord?>

    @Query("SELECT * FROM daily_records WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    fun observeRange(from: String, to: String): Flow<List<DailyRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: DailyRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(records: List<DailyRecord>)

    // 列ごとの atomic UPDATE（レースコンディション防止）
    @Query("UPDATE daily_records SET steps = :steps, updatedAt = :updatedAt WHERE date = :date")
    suspend fun updateSteps(date: String, steps: Int, updatedAt: Long): Int

    @Query("UPDATE daily_records SET pressure = :pressure, updatedAt = :updatedAt WHERE date = :date")
    suspend fun updatePressure(date: String, pressure: Float, updatedAt: Long): Int

    // 降水量が未取得（null）の日にだけ書き込む（既存値・他カラムを壊さない）
    @Query("""
        UPDATE daily_records
        SET precipitationMm = :precipitationMm, updatedAt = :updatedAt
        WHERE date = :date AND precipitationMm IS NULL
    """)
    suspend fun fillPrecipitationIfMissing(date: String, precipitationMm: Float, updatedAt: Long): Int

    @Query("""
        UPDATE daily_records
        SET pressure = :pressure, precipitationMm = :precipitationMm,
            weatherCode = :weatherCode, updatedAt = :updatedAt
        WHERE date = :date
    """)
    suspend fun updateWeather(
        date: String,
        pressure: Float,
        precipitationMm: Float,
        weatherCode: Int?,
        updatedAt: Long,
    ): Int

    @Query("""
        UPDATE daily_records
        SET dizzinessLevel = :dizziness, fatigueLevel = :fatigue, tinnitusLevel = :tinnitus,
            sleepHours = :sleep, memo = :memo, weightKg = :weightKg, updatedAt = :updatedAt
        WHERE date = :date
    """)
    suspend fun updateBodyCondition(
        date: String,
        dizziness: Int?,
        fatigue: Int?,
        tinnitus: Int?,
        sleep: Float?,
        memo: String?,
        weightKg: Float?,
        updatedAt: Long,
    ): Int

    @Query("DELETE FROM daily_records WHERE date = :date")
    suspend fun deleteByDate(date: String)

    @Query("DELETE FROM daily_records")
    suspend fun deleteAll()

    @Query("SELECT * FROM daily_records ORDER BY date ASC")
    suspend fun getAllForExport(): List<DailyRecord>
}
