package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity

object NotificationHelper {
    const val CHANNEL_MONITOR_ID = "battery_monitor_channel"
    const val CHANNEL_ALERTS_ID = "battery_alerts_channel"
    const val NOTIFICATION_MONITOR_ID = 1001
    const val NOTIFICATION_ALERT_ID = 1002

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val monitorChannel = NotificationChannel(
                CHANNEL_MONITOR_ID,
                "Battery Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time battery status in the notification drawer"
            }

            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS_ID,
                "Smart Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for temperature, charge limits, and charging speeds"
                enableVibration(true)
            }

            manager.createNotificationChannel(monitorChannel)
            manager.createNotificationChannel(alertsChannel)
        }
    }

    fun buildMonitorNotification(context: Context, level: Int, temp: Float, isCharging: Boolean, current: Int): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = if (isCharging) "Charging" else "Discharging"
        val currentText = "${if (current > 0) "+" else ""}$current mA"
        val title = "Battery Status: $level% ($statusText)"
        val content = "Temp: $temp°C | Current: $currentText"

        return NotificationCompat.Builder(context, CHANNEL_MONITOR_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun sendAlert(context: Context, title: String, content: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ALERT_ID, notification)
    }
}
