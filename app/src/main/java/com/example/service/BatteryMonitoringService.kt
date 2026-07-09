package com.example.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import com.example.data.BatteryDatabase
import com.example.data.BatteryReading
import com.example.data.BatteryRepository
import com.example.data.ChargingSession
import kotlinx.coroutines.*
import kotlin.math.abs

class BatteryMonitoringService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repository: BatteryRepository
    private lateinit var batteryManager: BatteryManager

    private var currentLevel = 100
    private var isChargingState = false
    private var currentTemp = 0.0f
    private var currentVoltage = 0 // mV
    private var currentCurrentNow = 0 // mA

    // Session tracking variables
    private var sessionStartTime: Long = 0
    private var sessionStartLevel: Int = 0
    private val sessionCurrents = mutableListOf<Int>()
    private var isTrackingSession = false

    // Alerts state
    private var hasAlerted80 = false
    private var hasAlertedOverheat = false
    private var hasAlertedSlowCharge = false

    private var loggingJob: Job? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1

                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                val tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                val tempC = tempRaw / 10.0f

                val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)

                currentLevel = pct
                isChargingState = isCharging
                currentTemp = tempC
                currentVoltage = voltage

                updateCurrentNow()
                handleBatteryUpdate()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannels(this)
        batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        
        val database = BatteryDatabase.getDatabase(this)
        repository = BatteryRepository(database.batteryDao())

        // Start Foreground immediately
        val initialNotification = NotificationHelper.buildMonitorNotification(
            this, currentLevel, currentTemp, isChargingState, currentCurrentNow
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NotificationHelper.NOTIFICATION_MONITOR_ID,
                initialNotification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NotificationHelper.NOTIFICATION_MONITOR_ID, initialNotification)
        }

        // Register battery receiver dynamically (MANDATORY on Android 8.0+)
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)

        startLoggingLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(batteryReceiver)
        loggingJob?.cancel()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateCurrentNow() {
        var currentMicroAmps = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        // Check if value is reasonable or microAmps
        currentCurrentNow = if (abs(currentMicroAmps) > 20000) {
            currentMicroAmps / 1000
        } else {
            currentMicroAmps
        }

        // Standardize sign: charging should be positive, discharging negative
        if (isChargingState && currentCurrentNow < 0) {
            currentCurrentNow = -currentCurrentNow
        } else if (!isChargingState && currentCurrentNow > 0) {
            currentCurrentNow = -currentCurrentNow
        }
    }

    private fun handleBatteryUpdate() {
        // 1. Update Foreground Notification
        val notification = NotificationHelper.buildMonitorNotification(
            this, currentLevel, currentTemp, isChargingState, currentCurrentNow
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NotificationHelper.NOTIFICATION_MONITOR_ID, notification)

        // 2. Manage charging sessions
        if (isChargingState) {
            if (!isTrackingSession) {
                // Start a new session
                isTrackingSession = true
                sessionStartTime = System.currentTimeMillis()
                sessionStartLevel = currentLevel
                sessionCurrents.clear()
                hasAlerted80 = false
                hasAlertedSlowCharge = false
            }
            if (currentCurrentNow > 0) {
                sessionCurrents.add(currentCurrentNow)
            }

            // Check for Smart Alerts
            // Alert 80%
            if (currentLevel >= 80 && !hasAlerted80) {
                hasAlerted80 = true
                NotificationHelper.sendAlert(
                    this,
                    "Battery reaches 80%",
                    "Unplug now to preserve battery health!"
                )
            }

            // Alert slow charging (if active for 2 mins and average current is extremely low)
            val durationMin = (System.currentTimeMillis() - sessionStartTime) / 60000.0f
            if (durationMin >= 2.0f && !hasAlertedSlowCharge && sessionCurrents.isNotEmpty()) {
                val avgCurrent = sessionCurrents.average()
                if (avgCurrent < 300) {
                    hasAlertedSlowCharge = true
                    NotificationHelper.sendAlert(
                        this,
                        "Slow Charging Detected",
                        "Charging current is low (${avgCurrent.toInt()} mA). Check your cable/charger."
                    )
                }
            }

        } else {
            if (isTrackingSession) {
                // End active session and save to db
                isTrackingSession = false
                val sessionEndTime = System.currentTimeMillis()
                val sessionEndLevel = currentLevel
                val levelDiff = sessionEndLevel - sessionStartLevel

                if (levelDiff >= 5 && (sessionEndTime - sessionStartTime) > 60000L) {
                    serviceScope.launch {
                        val durationHrs = (sessionEndTime - sessionStartTime) / 3600000.0f
                        val avgCurrent = if (sessionCurrents.isNotEmpty()) sessionCurrents.average().toInt() else 0
                        val maxCurrent = if (sessionCurrents.isNotEmpty()) sessionCurrents.maxOrNull() ?: 0 else 0
                        val mahAdded = avgCurrent.toFloat() * durationHrs

                        // Estimate actual capacity & health (AccuBattery approach)
                        // Formula: (mAh added / percentage charged) * 100
                        val percentageFraction = levelDiff / 100.0f
                        val estimatedCapacity = mahAdded / percentageFraction
                        val designCapacity = 4000.0f // standard phone default
                        val health = (estimatedCapacity / designCapacity) * 100.0f

                        val session = ChargingSession(
                            startTime = sessionStartTime,
                            endTime = sessionEndTime,
                            startLevel = sessionStartLevel,
                            endLevel = sessionEndLevel,
                            mahAdded = mahAdded,
                            maxCurrent = maxCurrent,
                            avgCurrent = avgCurrent,
                            estimatedHealth = health.coerceIn(50.0f, 100.0f)
                        )
                        repository.insertSession(session)
                    }
                }
            }
        }

        // Alert overheating
        if (currentTemp >= 45.0f && !hasAlertedOverheat) {
            hasAlertedOverheat = true
            NotificationHelper.sendAlert(
                this,
                "Battery Overheating!",
                "Battery temperature is high ($currentTemp°C). Avoid gaming or high load."
            )
        } else if (currentTemp < 42.0f) {
            hasAlertedOverheat = false
        }
    }

    private fun startLoggingLoop() {
        loggingJob?.cancel()
        loggingJob = serviceScope.launch {
            while (isActive) {
                logCurrentBatteryState()
                // Log state every 30s if charging, otherwise every 3 minutes
                val delayMs = if (isChargingState) 30000L else 180000L
                delay(delayMs)
            }
        }
    }

    private suspend fun logCurrentBatteryState() {
        updateCurrentNow()
        val reading = BatteryReading(
            timestamp = System.currentTimeMillis(),
            level = currentLevel,
            voltage = currentVoltage,
            temperature = currentTemp,
            currentNow = currentCurrentNow,
            isCharging = isChargingState
        )
        repository.insertReading(reading)

        // Trim readings older than 7 days to keep database compact
        val cutoff = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        repository.deleteOldReadings(cutoff)
    }
}
