package com.example.testapplication

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * DailyStepsDao is the interface to interact with RoomDB about DailySteps entries,
 * it provides insert, get and delete operations.
 */
@Dao
interface DailyStepsDao {
    @Insert
    suspend fun insert(dailySteps: DailyStepsEntity)

    @Query("SELECT * FROM DailySteps ORDER BY date DESC LIMIT :nDays")
    suspend fun getLastNDaysDailySteps(nDays: Int): List<DailyStepsEntity>

    @Query("DELETE FROM DailySteps")
    suspend fun deleteEveryDailySteps()
}