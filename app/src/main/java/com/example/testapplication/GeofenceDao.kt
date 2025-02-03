package com.example.testapplication

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * GeofenceDao is the interface to interact with RoomDB about Geofences recordings,
 * in order to keep trace of geofences' crossings, it provides abstract method to
 * accomplish operations of insert, get, update and delete.
 */
@Dao
interface GeofenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(geofence: GeofenceEntity)

    @Query("SELECT * FROM Geofence")
    suspend fun getGeofences(): List<GeofenceEntity>

    @Query("SELECT id FROM Geofence WHERE id = :name")
    suspend fun getGeofenceByName(name: String): String?

    @Query("UPDATE Geofence SET crossCount = crossCount+1 WHERE id = :name")
    suspend fun addCrossToGeofenceByName(name: String)

    @Query("DELETE FROM Geofence")
    suspend fun deleteGeofences()
}