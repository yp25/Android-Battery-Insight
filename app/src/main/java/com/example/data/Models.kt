package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "battery_readings")
data class BatteryReading(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val level: Int,
    val voltage: Int, // in mV
    val temperature: Float, // in °C
    val currentNow: Int, // in mA
    val isCharging: Boolean
)

@Entity(tableName = "charging_sessions")
data class ChargingSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val startLevel: Int,
    val endLevel: Int,
    val mahAdded: Float,
    val maxCurrent: Int, // in mA
    val avgCurrent: Int, // in mA
    val estimatedHealth: Float // estimated health percentage, e.g., 92.5f
)
