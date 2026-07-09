package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BatteryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReading(reading: BatteryReading)

    @Query("SELECT * FROM battery_readings ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentReadings(limit: Int): Flow<List<BatteryReading>>

    @Query("SELECT * FROM battery_readings WHERE timestamp >= :start AND timestamp <= :end ORDER BY timestamp ASC")
    suspend fun getReadingsBetween(start: Long, end: Long): List<BatteryReading>

    @Query("DELETE FROM battery_readings WHERE timestamp < :cutoff")
    suspend fun deleteOldReadings(cutoff: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChargingSession)

    @Query("SELECT * FROM charging_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<ChargingSession>>

    @Query("SELECT * FROM charging_sessions ORDER BY startTime DESC LIMIT 1")
    suspend fun getLastSession(): ChargingSession?
}
