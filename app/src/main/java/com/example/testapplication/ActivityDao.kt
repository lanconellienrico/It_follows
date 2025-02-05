package com.example.testapplication

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 DataAccessObject : interface to interact with Room Db about Activity,
 defined here there are abstract methods for Db Operations,
 both writing (insert) and reading (select act. by id, select act.s by month)
 */
@Dao
interface ActivityDao {
    @Insert
    suspend fun insert(activity: ActivityEntity)

    @Query("SELECT * FROM Activity ORDER BY stopTime DESC")
    suspend fun getActivities(): List<ActivityEntity>

    @Query("SELECT * FROM Activity WHERE id = :id")
    suspend fun getActivityById(id: Int): ActivityEntity?

    //@Query("SELECT * FROM Activity WHERE year = :year AND month = :month AND day = :day")
    //suspend fun getActivitiesByDate(year: Int, month: Int, day: Int): List<ActivityEntity>

    /**
     * The getActivitiesByDate-query takes two times:
     * - dayStart : 00.00... of the selected day [in milliseconds Long]
     * - dayEnd : 23:59.59.999 of that day [ms - Long]
     * The activities to select are all those:
     * startTime falls in the interval dayStart-dayEnd,
     * or whose stopTime falls in the interval dayStart-dayEnd,
     * or whose startTime is before dayStart, but has a stopTime greater than dayEnd.
     */
    @Query("""
        SELECT * FROM Activity
        WHERE (startTime BETWEEN :start AND :end)
        OR (stopTime BETWEEN :start AND :end)     
        OR (startTime < :start AND stopTime > :end)
        ORDER BY stopTime DESC
    """)
    suspend fun getActivitiesByDate(start: Long, end :Long): List<ActivityEntity>

    @Query("SELECT * FROM Activity WHERE activityType = :type ORDER BY stopTime DESC")
    suspend fun getActivitiesByType(type: String): List<ActivityEntity>

    @Query("""
        SELECT * FROM Activity
        WHERE (
            (startTime BETWEEN :dayStart AND :dayEnd)
            OR (stopTime BETWEEN :dayStart AND :dayEnd)     
            OR (startTime < :dayStart AND stopTime > :dayEnd)
        )
        AND activityType = :type
        ORDER BY stopTime DESC
    """)
    suspend fun getActivitiesByDateAndType(dayStart: Long, dayEnd :Long, type: String): List<ActivityEntity>

    //@Query("SELECT * FROM Activity WHERE year = :year AND month = :month AND day = :day AND activityType = :type")
    //suspend fun getActivitiesByDateAndType(year: Int, month: Int, day: Int, type: String): List<ActivityEntity>

    @Query("DELETE FROM Activity")
    suspend fun deleteEveryActivity()

}