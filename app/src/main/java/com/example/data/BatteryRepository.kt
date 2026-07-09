package com.example.data

import kotlinx.coroutines.flow.Flow

class BatteryRepository(private val batteryDao: BatteryDao) {
    val recentReadings: Flow<List<BatteryReading>> = batteryDao.getRecentReadings(100)
    val allSessions: Flow<List<ChargingSession>> = batteryDao.getAllSessions()

    suspend fun insertReading(reading: BatteryReading) {
        batteryDao.insertReading(reading)
    }

    suspend fun insertSession(session: ChargingSession) {
        batteryDao.insertSession(session)
    }

    suspend fun getLastSession(): ChargingSession? {
        return batteryDao.getLastSession()
    }

    suspend fun getReadingsBetween(start: Long, end: Long): List<BatteryReading> {
        return batteryDao.getReadingsBetween(start, end)
    }

    suspend fun deleteOldReadings(cutoff: Long) {
        batteryDao.deleteOldReadings(cutoff)
    }
}
