package com.techmarketplace.data.storage.profilecache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [UserListingEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ProfileCacheDatabase : RoomDatabase() {
    abstract fun userListingDao(): UserListingDao

    companion object {
        fun create(context: Context): ProfileCacheDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                ProfileCacheDatabase::class.java,
                "profile_cache.db"
            ).fallbackToDestructiveMigration().build()
    }
}
