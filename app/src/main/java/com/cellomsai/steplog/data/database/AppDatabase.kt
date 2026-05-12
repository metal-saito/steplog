package com.cellomsai.steplog.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.cellomsai.steplog.data.database.entity.DailyRecord

@Database(
    entities = [DailyRecord::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dailyRecordDao(): DailyRecordDao

    companion object {
        const val DATABASE_NAME = "steplog.db"
    }
}
