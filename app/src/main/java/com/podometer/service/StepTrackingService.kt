// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.app.ServiceCompat
import com.podometer.data.db.StepBucket
import com.podometer.data.repository.PreferencesManager
import com.podometer.data.repository.StepBucketRepository
import com.podometer.data.repository.StepRepository
import com.podometer.data.sensor.StepSensorManager
import com.podometer.util.DateTimeUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.cancellation.CancellationException
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
    lateinit var stepRepository: StepRepository

    @Inject
    lateinit var stepBucketRepository: StepBucketRepository

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var preferencesManager: PreferencesManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var accumulator: StepAccumulator

    private var collectorJob: Job? = null
    private var notificationTickerJob: Job? = null
    private var notificationStyleJob: Job? = null

    /**
     * Stride length in kilometres, read from [PreferencesManager] during
     * [onCreate] and refreshed by the notification ticker every 30 s.
     * Used by [collectStepEvents] for live dashboard distance updates and
     * by [updateForegroundNotification] for notification content.
     *
     * Defaults to [StepAccumulator.DEFAULT_STRIDE_LENGTH_KM] if the preference
     * read fails.
     */
    private var strideLengthKm: Float = StepAccumulator.DEFAULT_STRIDE_LENGTH_KM

    // ─── Service lifecycle ────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val strideKm = runBlockingWithDefault(
            default = StepAccumulator.DEFAULT_STRIDE_LENGTH_KM,
            tag = "read stride length preference",
        ) { preferencesManager.strideLengthKm().first() }
        strideLengthKm = strideKm
        val now = System.currentTimeMillis()
        val bucketStart = StepAccumulator.truncateToBucket(now)
        val currentBucketSteps = runBlockingWithDefault(
            default = 0,
            tag = "read current-bucket steps from DB",
        ) { stepBucketRepository.getStepsForBucket(bucketStart) }
        val totalToday = runBlockingWithDefault(
            default = 0,
            tag = "read today's total steps from DB",
        ) { stepRepository.getTodayTotalStepsSnapshot() }
        accumulator = StepAccumulator(
            initialBucketTimestamp = now,
            strideLengthKm = strideKm,
            initialCurrentBucketSteps = currentBucketSteps,
            initialTotalStepsToday = totalToday,
        )
        startForegroundWithNotification()
        Log.d(TAG, "Service created: restored $currentBucketSteps current-bucket steps, $totalToday today total")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stepSensorManager.startListening()
        if (collectorJob == null || collectorJob?.isActive != true) {
            collectorJob = collectStepEvents()
        }
        if (notificationTickerJob == null || notificationTickerJob?.isActive != true) {
            notificationTickerJob = launchNotificationTicker()
        }
        if (notificationStyleJob == null || notificationStyleJob?.isActive != true) {
            notificationStyleJob = launchNotificationStyleObserver()
        }
        Log.d(TAG, "Service started, sensor listening")
        return START_STICKY
    }

    override fun onDestroy() {
        // Must complete flush before cancelling scope to avoid data loss.
        val results = accumulator.flush()
        if (results.isNotEmpty()) {
            runBlocking {
                for (result in results) {
                    stepBucketRepository.upsert(result.bucket)
                    stepRepository.upsertStepsAndDistance(
                        date = result.dailySummary.date,
                        totalSteps = result.dailySummary.totalSteps,
                        totalDistance = result.dailySummary.totalDistance,
                    )
                }
                Log.d(TAG, "Final flush on destroy: ${results.size} result(s)")
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
            NotificationHelper.NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        )
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun startForegroundWithNotification() {
        val style = runBlockingWithDefault(
            default = NotificationStyle.MINIMAL,
            tag = "read notification style preference",
        ) { NotificationStyle.fromPreference(preferencesManager.notificationStyle().first()) }
        val notification = notificationHelper.buildNotification(
            steps = accumulator.totalStepsToday,
            distanceKm = 0f,
            style = style,
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
            val nowMs = System.currentTimeMillis()
            val flushResults = accumulator.addSteps(delta)
            for (flushResult in flushResults) {
                stepBucketRepository.upsert(flushResult.bucket)
                stepRepository.upsertStepsAndDistance(
                    date = flushResult.dailySummary.date,
                    totalSteps = flushResult.dailySummary.totalSteps,
                    totalDistance = flushResult.dailySummary.totalDistance,
                )
                Log.d(
                    TAG,
                    "Flushed ${flushResult.bucket.stepCount} steps for bucket " +
                        "${flushResult.bucket.timestamp}",
                )
            }

            // Always write the current partial-bucket state so the dashboard sees live updates.
            val partialBucket = StepBucket(
                timestamp = StepAccumulator.truncateToBucket(nowMs),
                stepCount = accumulator.currentBucketSteps,
            )
            stepBucketRepository.upsert(partialBucket)
            stepRepository.upsertStepsAndDistance(
                date = DateTimeUtils.toLocalDate(nowMs).toString(),
                totalSteps = accumulator.totalStepsToday,
                totalDistance = accumulator.totalStepsToday * strideLengthKm,
            )
            Log.d(TAG, "Partial-bucket write: ${accumulator.currentBucketSteps} steps in current bucket, ${accumulator.totalStepsToday} today")
        }
    }

    // ─── Notification ticker ──────────────────────────────────────────────────

    /**
     * Re-posts the foreground notification via [ServiceCompat.startForeground]
     * with the current step data and the given [style].
     */
    private fun updateForegroundNotification(style: NotificationStyle) {
        val strideKm = strideLengthKm
        val distanceKm = accumulator.totalStepsToday * strideKm
        val notification = notificationHelper.buildNotification(
            steps = accumulator.totalStepsToday,
            distanceKm = distanceKm,
            style = style,
        )
        ServiceCompat.startForeground(
            this,
            NotificationHelper.NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH,
        )
        Log.d(TAG, "Notification updated: ${accumulator.totalStepsToday} steps, $distanceKm km, style=$style")
    }

    /**
     * Coroutine that updates the foreground notification every [NOTIFICATION_UPDATE_INTERVAL_MS]
     * milliseconds with the current step count, computed distance, and detected
     * activity state. Runs until the service scope is cancelled.
     */
    private fun launchNotificationTicker(): Job = serviceScope.launch {
        while (isActive) {
            delay(NOTIFICATION_UPDATE_INTERVAL_MS)
            strideLengthKm = preferencesManager.strideLengthKm().first()
            val style = NotificationStyle.fromPreference(
                preferencesManager.notificationStyle().first(),
            )
            updateForegroundNotification(style)
        }
    }

    /**
     * Observes the notification style preference and immediately updates the
     * notification when it changes. The initial emission is dropped because
     * the ticker (or the initial [startForegroundWithNotification]) already
     * handles the first value.
     */
    private fun launchNotificationStyleObserver(): Job = serviceScope.launch {
        preferencesManager.notificationStyle()
            .map { NotificationStyle.fromPreference(it) }
            .drop(1) // skip the initial value — already applied
            .collect { style ->
                updateForegroundNotification(style)
            }
    }

    // ─── Constants and helpers ────────────────────────────────────────────────

    internal companion object {
        internal const val TAG = "StepTrackingService"
        private const val NOTIFICATION_CHANNEL_NAME = "Step Tracking"
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 30_000L

        /**
         * Runs [block] inside [runBlocking] and returns its result. If [block]
         * throws any non-cancellation [Exception], the exception is caught, an
         * error is logged, and [default] is returned so the service can continue
         * starting with a safe fallback value.
         *
         * [CancellationException] is always rethrown so that coroutine
         * cancellation signals are never swallowed.
         *
         * @param default The fallback value returned when [block] throws.
         * @param tag A short human-readable label used in the error log message.
         * @param block The suspend lambda to execute.
         * @return The result of [block], or [default] on failure.
         */
        @VisibleForTesting
        internal fun <T> runBlockingWithDefault(default: T, tag: String, block: suspend () -> T): T {
            return try {
                runBlocking { block() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to $tag, using default: $default", e)
                default
            }
        }
    }
}
