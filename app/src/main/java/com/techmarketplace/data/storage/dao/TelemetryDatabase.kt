package com.techmarketplace.data.storage.dao

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [SellerResponseMetricsEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(TelemetryTypeConverters::class)
abstract class TelemetryDatabase : RoomDatabase() {
    abstract fun sellerMetricsDao(): SellerMetricsDao
}

object TelemetryDatabaseProvider {
    @Volatile
    private var instance: TelemetryDatabase? = null

    fun get(context: Context): TelemetryDatabase {
        return instance ?: synchronized(this) {
            instance ?: buildDatabase(context.applicationContext).also { instance = it }
        }
    }

    private fun buildDatabase(context: Context): TelemetryDatabase =
        Room.databaseBuilder(
            context,
            TelemetryDatabase::class.java,
            "telemetry.db"
        ).fallbackToDestructiveMigration()
            .build()
}
