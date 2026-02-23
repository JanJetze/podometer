// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.podometer.data.db.ActivityTransition
import com.podometer.data.repository.PreferencesManager
import com.podometer.data.repository.StepRepository
import com.podometer.data.sensor.AccelerometerSampler
import com.podometer.data.sensor.CyclingClassifier
import com.podometer.data.sensor.StepFrequencyTracker
import com.podometer.data.sensor.StepSensorManager
import com.podometer.domain.model.ActivityState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Foreground service that owns the sensor lifecycle and writes step data to the
 * database.
 *
 * Lifecycle:
 * - [onCreate]: Creates the notification channel, starts itself as a foreground
 *   service with a persistent notification built by [NotificationHelper].
 * - [onStartCommand]: Starts sensor listening, launches the step-event collector,
 *   and launches a periodic notification updater that fires every ~30 s.
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
    lateinit var accelerometerSampler: AccelerometerSampler

    @Inject
    lateinit var stepFrequencyTracker: StepFrequencyTracker

    @Inject
    lateinit var cyclingClassifier: CyclingClassifier

    @Inject
    lateinit var stepRepository: StepRepository

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var preferencesManager: PreferencesManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var accumulator: StepAccumulator

    private var collectorJob: Job? = null
    private var notificationTickerJob: Job? = null
    private var classifierJob: Job? = null

    // ─── Service lifecycle ────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val strideKm = runBlocking { preferencesManager.strideLengthKm().first() }
        accumulator = StepAccumulator(System.currentTimeMillis(), strideLengthKm = strideKm)
        startForegroundWithNotification()
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stepSensorManager.startListening()
        accelerometerSampler.startSampling()
        if (collectorJob == null || collectorJob?.isActive != true) {
            collectorJob = collectStepEvents()
        }
        if (notificationTickerJob == null || notificationTickerJob?.isActive != true) {
            notificationTickerJob = launchNotificationTicker()
        }
        if (classifierJob == null || classifierJob?.isActive != true) {
            classifierJob = launchClassifier()
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
        classifierJob?.cancel()
        stepSensorManager.stopListening()
        accelerometerSampler.stopSampling()
        stepFrequencyTracker.reset()
        cyclingClassifier.reset()
        serviceScope.cancel()
        Log.d(TAG, "Service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ─── Notification ─────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NotificationHelper.NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        )
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun startForegroundWithNotification() {
        val notification = notificationHelper.buildNotification(
            steps = accumulator.totalStepsToday,
            distanceKm = 0f,
            activity = ActivityState.STILL,
            style = NotificationStyle.MINIMAL,
        )
        ServiceCompat.startForeground(
            this,
            NotificationHelper.NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH,
        )
    }

    // ─── Step collection ──────────────────────────────────────────────────────

    private fun collectStepEvents(): Job = serviceScope.launch {
        stepSensorManager.stepEvents.collect { delta ->
            // Record each step arrival for frequency tracking.  We record once
            // per delta step so that burst events from TYPE_STEP_COUNTER are
            // each individually timestamped, giving a realistic frequency estimate.
            val nowMs = System.currentTimeMillis()
            repeat(delta) { stepFrequencyTracker.recordStep(nowMs) }

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

    // ─── Cycling classifier ───────────────────────────────────────────────────

    /**
     * Coroutine that periodically evaluates accelerometer and step-frequency
     * features, detects activity transitions, persists them, and updates the
     * accumulator's current activity.
     *
     * Runs every [CLASSIFIER_INTERVAL_MS] (~5 seconds) until the service
     * scope is cancelled.
     */
    private fun launchClassifier(): Job = serviceScope.launch {
        while (isActive) {
            delay(CLASSIFIER_INTERVAL_MS)
            val features = accelerometerSampler.sampleBuffer.computeWindowFeatures()
                ?: continue // not enough samples yet
            val stepFreq = stepFrequencyTracker.computeStepFrequency()
            val transition = cyclingClassifier.evaluate(features, stepFreq) ?: continue

            Log.d(TAG, "Activity transition: ${transition.fromState} → ${transition.toState}")
            accumulator.setActivity(transition.toState.name)

            stepRepository.insertTransition(
                ActivityTransition(
                    timestamp = System.currentTimeMillis(),
                    fromActivity = transition.fromState.name,
                    toActivity = transition.toState.name,
                    isManualOverride = false,
                ),
            )
        }
    }

    // ─── Notification ticker ──────────────────────────────────────────────────

    /**
     * Coroutine that updates the foreground notification every [NOTIFICATION_UPDATE_INTERVAL_MS]
     * milliseconds with the current step count, computed distance, and detected
     * activity state. Runs until the service scope is cancelled.
     */
    private fun launchNotificationTicker(): Job = serviceScope.launch {
        while (isActive) {
            delay(NOTIFICATION_UPDATE_INTERVAL_MS)
            val strideKm = preferencesManager.strideLengthKm().first()
            val distanceKm = accumulator.totalStepsToday * strideKm
            val currentActivity = cyclingClassifier.getCurrentState()
            notificationHelper.updateNotification(
                steps = accumulator.totalStepsToday,
                distanceKm = distanceKm,
                activity = currentActivity,
                style = NotificationStyle.MINIMAL,
            )
            Log.d(TAG, "Notification updated: ${accumulator.totalStepsToday} steps, $distanceKm km, activity=$currentActivity")
        }
    }

    // ─── Constants ───────────────────────────────────────────────────────────

    private companion object {
        private const val TAG = "StepTrackingService"
        private const val NOTIFICATION_CHANNEL_NAME = "Step Tracking"
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 30_000L

        /**
         * Classifier evaluation interval in milliseconds (~5 seconds).
         *
         * At SENSOR_DELAY_NORMAL (~5 Hz) this gives ~25 new accelerometer
         * samples per evaluation — sufficient for a stable variance estimate.
         */
        private const val CLASSIFIER_INTERVAL_MS = 5_000L
    }
}
