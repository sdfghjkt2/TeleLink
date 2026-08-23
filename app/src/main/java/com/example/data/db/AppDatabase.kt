package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.data.model.FileCategory
import com.example.data.model.LogLevel
import com.example.data.model.ServerLog
import com.example.data.model.StreamFileItem

class Converters {
    @TypeConverter
    fun fromFileCategory(category: FileCategory): String = category.name

    @TypeConverter
    fun toFileCategory(value: String): FileCategory = try {
        FileCategory.valueOf(value)
    } catch (_: Exception) {
        FileCategory.OTHER
    }

    @TypeConverter
    fun fromLogLevel(level: LogLevel): String = level.name

    @TypeConverter
    fun toLogLevel(value: String): LogLevel = try {
        LogLevel.valueOf(value)
    } catch (_: Exception) {
        LogLevel.INFO
    }
}

@Database(
    entities = [StreamFileItem::class, ServerLog::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun streamFileDao(): StreamFileDao
    abstract fun serverLogDao(): ServerLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "telestream_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
