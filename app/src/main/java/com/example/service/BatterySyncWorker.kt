package com.example.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.BatteryDatabase
import com.example.data.BatteryRepository

class BatterySyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val database = BatteryDatabase.getDatabase(applicationContext)
        val repository = BatteryRepository(database.batteryDao())

        return try {
            // Trim logs older than 7 days to keep database compact
            val cutoff = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
            repository.deleteOldReadings(cutoff)

            // Success
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
