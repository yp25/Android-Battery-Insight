package com.example.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BatteryDatabase
import com.example.data.BatteryReading
import com.example.data.BatteryRepository
import com.example.data.ChargingSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

data class CurrentBatteryState(
    val level: Int = 50,
    val isCharging: Boolean = false,
    val voltage: Float = 3.8f, // in V
    val temperature: Float = 25.0f, // in °C
    val currentNow: Int = 0, // in mA
    val remainingTimeText: String = "--",
    val statusText: String = "Unknown"
)

class BatteryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BatteryRepository
    private val batteryManager = application.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    private val _uiState = MutableStateFlow(CurrentBatteryState())
    val uiState: StateFlow<CurrentBatteryState> = _uiState.asStateFlow()

    private val _recentReadings = MutableStateFlow<List<BatteryReading>>(emptyList())
    val recentReadings: StateFlow<List<BatteryReading>> = _recentReadings.asStateFlow()

    private val _chargingSessions = MutableStateFlow<List<ChargingSession>>(emptyList())
    val chargingSessions: StateFlow<List<ChargingSession>> = _chargingSessions.asStateFlow()

    private val _estimatedHealth = MutableStateFlow(95.0f) // default
    val estimatedHealth: StateFlow<Float> = _estimatedHealth.asStateFlow()

    private val _actualCapacity = MutableStateFlow(3800.0f) // default estimated actual
    val actualCapacity: StateFlow<Float> = _actualCapacity.asStateFlow()

    val designCapacity = 4000.0f // in mAh

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                updateStateFromIntent(intent)
            }
        }
    }

    init {
        val database = BatteryDatabase.getDatabase(application)
        repository = BatteryRepository(database.batteryDao())

        // Register dynamic receiver to capture instant system updates
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        application.registerReceiver(batteryReceiver, filter)

        // Gather database information
        viewModelScope.launch {
            repository.recentReadings.collectLatest { readings ->
                _recentReadings.value = readings
            }
        }

        viewModelScope.launch {
            repository.allSessions.collectLatest { sessions ->
                _chargingSessions.value = sessions
                if (sessions.isNotEmpty()) {
                    // Compute overall health as the average of recorded valid sessions
                    val validSessions = sessions.filter { it.estimatedHealth in 50f..100f }
                    if (validSessions.isNotEmpty()) {
                        val avgHealth = validSessions.map { it.estimatedHealth }.average().toFloat()
                        _estimatedHealth.value = avgHealth
                        _actualCapacity.value = (designCapacity * avgHealth) / 100.0f
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            // receiver not registered
        }
    }

    private fun updateStateFromIntent(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else 50

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val statusText = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
            else -> "Unknown"
        }

        val tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val tempC = tempRaw / 10.0f

        val voltageRaw = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        val voltageV = if (voltageRaw > 1000) voltageRaw / 1000.0f else voltageRaw.toFloat()

        // Get Instant Current mA
        var currentMicroAmps = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        var currentMA = if (abs(currentMicroAmps) > 20000) {
            currentMicroAmps / 1000
        } else {
            currentMicroAmps
        }

        // Apply signs (charging is positive, discharging is negative)
        if (isCharging && currentMA < 0) {
            currentMA = -currentMA
        } else if (!isCharging && currentMA > 0) {
            currentMA = -currentMA
        }

        // Estimate remaining time
        val remainingTimeText = estimateRemainingTime(pct, isCharging, currentMA)

        _uiState.value = CurrentBatteryState(
            level = pct,
            isCharging = isCharging,
            voltage = voltageV,
            temperature = tempC,
            currentNow = currentMA,
            remainingTimeText = remainingTimeText,
            statusText = statusText
        )
    }

    private fun estimateRemainingTime(level: Int, isCharging: Boolean, currentMA: Int): String {
        if (currentMA == 0) return "--"
        return try {
            if (isCharging) {
                if (level >= 100) return "Fully Charged"
                val capacityNeeded = designCapacity * (100 - level) / 100.0f // mAh
                val currentPower = abs(currentMA).toFloat() // mA
                val hours = capacityNeeded / currentPower
                formatHours(hours)
            } else {
                if (level <= 0) return "Out of Power"
                val capacityAvailable = designCapacity * level / 100.0f // mAh
                val currentPower = abs(currentMA).toFloat() // mA
                val hours = capacityAvailable / currentPower
                formatHours(hours)
            }
        } catch (e: Exception) {
            "--"
        }
    }

    private fun formatHours(hours: Float): String {
        if (hours.isInfinite() || hours.isNaN() || hours > 100f) return "--"
        val totalMinutes = (hours * 60).toInt()
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    fun exportToCSV(context: Context): String? {
        val readings = _recentReadings.value
        if (readings.isEmpty()) return null

        val csvHeader = "Timestamp,Battery Level (%),Voltage (V),Temperature (°C),Current (mA),Is Charging\n"
        val csvBody = readings.joinToString("\n") { r ->
            val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(r.timestamp)
            "$dateStr,${r.level},${r.voltage / 1000.0f},${r.temperature},${r.currentNow},${r.isCharging}"
        }

        return try {
            val filename = "battery_insight_history_${System.currentTimeMillis()}.csv"
            context.openFileOutput(filename, Context.MODE_PRIVATE).use {
                it.write((csvHeader + csvBody).toByteArray())
            }
            context.getFileStreamPath(filename).absolutePath
        } catch (e: Exception) {
            null
        }
    }
}
