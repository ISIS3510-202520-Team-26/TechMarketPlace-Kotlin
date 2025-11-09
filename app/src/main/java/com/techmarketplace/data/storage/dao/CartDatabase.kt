package com.techmarketplace.data.storage.dao

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [CartItemEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(CartTypeConverters::class)
abstract class CartDatabase : RoomDatabase() {
    abstract fun cartDao(): CartDao
}

object CartDatabaseProvider {

    @Volatile
    private var instance: CartDatabase? = null

    fun get(context: Context): CartDatabase {
        return instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }
    }

    private fun build(context: Context): CartDatabase {
        return Room.databaseBuilder(context, CartDatabase::class.java, "cart.db")
            .fallbackToDestructiveMigration()
            .build()
    }
}
