package com.example.testapplication

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * App Db base on Room, takes storage of vital importance data, such as
 * - activities performed,
 * - daily steps taken,
 * - geofences settings and relative crossings.
 *
 * In order to provide data consistency and avoid trouble, there is one -and only one- instance
 * of the Db (implementing the Singleton Pattern idea), with which every object interacts.
 */
@Database(entities = [ActivityEntity::class, DailyStepsEntity::class, GeofenceEntity::class], version = 4, exportSchema = false)
abstract class Db : RoomDatabase() {
    abstract fun activityEntityDao(): ActivityDao
    abstract fun dailyStepsEntityDao(): DailyStepsDao
    abstract fun geofenceEntityDao(): GeofenceDao

    // implementing SINGLETON pattern, to guarantee Db loneliness,
    // by creating a single instance of the Db
    // and a synchronized method to access it to handle multiple calls
    companion object {
        @Volatile /** Reads and writes to this field are atomic and writes are always visible to other threads.
                    * If another thread reads the value of this field, it sees not only that value,
                    * but all side effects that led to writing that value.                                     */
        private var INSTANCE: Db? = null

        fun getDb(context: Context): Db {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext, Db::class.java, "Db").build()
                INSTANCE = instance
                instance
            }

        }
    }
}