// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.podometer.MainActivity
import com.podometer.R
import com.podometer.data.repository.StepRepository
import com.podometer.data.sensor.StepSensorManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Foreground service that owns the sensor lifecycle and writes step data to the
 * database.
 *
 * Lifecycle:
 * - [onCreate]: Creates the notification channel, starts itself as a foreground
 *   service with a persistent notification.
 * - [onStartCommand]: Starts sensor listening and launches a coroutine that
 *   collects step events and flushes hourly aggregates to [StepRepository].
 * - [onDestroy]: Flushes any remaining in-memory steps, stops sensor listening,
 *   and cancels the coroutine scope.
 * - [onBind]: Returns `null` — this is a started service, not a bound service.
 *
 * Step accumulation is delegated to [StepAccumulator], which is pure Kotlin and
 * therefore unit-testable without the Android framework.
 */
@AndroidEntryPoint
class StepTrackingService : Service() {

    @Inject
    lateinit var stepSensorManager: StepSensorManager

    @Inject
    lateinit var stepRepository: StepRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var accumulator: StepAccumulator

    private var collectorJob: Job? = null

    // ─── Service lifecycle ────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundWithNotification()
        accumulator = StepAccumulator(System.currentTimeMillis())
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stepSensorManager.startListening()
        if (collectorJob == null || collectorJob?.isActive != true) {
            collectorJob = collectStepEvents()
        }
        Log.d(TAG, "Service started, sensor listening")
        return START_STICKY
    }

    override fun onDestroy() {
        // Must complete flush before cancelling scope to avoid data loss
        val result = accumulator.flush()
        if (result != null) {
            runBlocking {
                stepRepository.insertHourlyAggregate(result.aggregate)
                stepRepository.upsertDailySummary(result.dailySummary)
                Log.d(TAG, "Final flush on destroy: ${result.aggregate.stepCountDelta} steps")
            }
        }
        stepSensorManager.stopListening()
        serviceScope.cancel()
        Log.d(TAG, "Service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ─── Notification ─────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        )
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun startForegroundWithNotification() {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_tracking_title))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH,
        )
    }

    // ─── Step collection ──────────────────────────────────────────────────────

    private fun collectStepEvents(): Job = serviceScope.launch {
        stepSensorManager.stepEvents.collect { delta ->
            val flushResult = accumulator.addSteps(delta)
            if (flushResult != null) {
                stepRepository.insertHourlyAggregate(flushResult.aggregate)
                stepRepository.upsertDailySummary(flushResult.dailySummary)
                Log.d(
                    TAG,
                    "Flushed ${flushResult.aggregate.stepCountDelta} steps for hour " +
                        "${flushResult.aggregate.timestamp}",
                )
            }
        }
    }

    // ─── Constants ───────────────────────────────────────────────────────────

    private companion object {
        private const val TAG = "StepTrackingService"
        private const val NOTIFICATION_CHANNEL_ID = "step_tracking"
        private const val NOTIFICATION_CHANNEL_NAME = "Step Tracking"
        private const val NOTIFICATION_ID = 1
    }
}
