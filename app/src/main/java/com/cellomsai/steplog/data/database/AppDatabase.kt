package com.cellomsai.steplog.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cellomsai.steplog.data.database.entity.DailyRecord

@Database(
    entities = [DailyRecord::class],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dailyRecordDao(): DailyRecordDao

    companion object {
        const val DATABASE_NAME = "steplog.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_records ADD COLUMN weightKg REAL")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_records ADD COLUMN precipitationMm REAL")
                db.execSQL("ALTER TABLE daily_records ADD COLUMN weatherCode INTEGER")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_records ADD COLUMN tinnitusLevel INTEGER")
            }
        }
    }
}
