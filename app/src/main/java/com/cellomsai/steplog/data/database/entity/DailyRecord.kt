package com.cellomsai.steplog.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_records")
data class DailyRecord(
    @PrimaryKey val date: String,           // "yyyy-MM-dd"
    val steps: Int = 0,
    val dizzinessLevel: Int? = null,        // 0-5, null = not entered
    val fatigueLevel: Int? = null,          // 0-5
    val sleepHours: Float? = null,          // 0.0-12.0
    val pressure: Float? = null,            // morning pressure in hPa
    val memo: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
