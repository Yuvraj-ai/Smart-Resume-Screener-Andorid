package com.yuvraj.resumescreener.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ScreeningEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun screeningDao(): ScreeningDao

    companion object {
        const val NAME = "resume_screener.db"
    }
}
