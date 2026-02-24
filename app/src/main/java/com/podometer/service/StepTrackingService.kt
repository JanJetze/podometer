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
import com.podometer.data.repository.CyclingRepository
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
 *
 * Cycling session persistence is delegated to [CyclingSessionManager] (pure Kotlin)
 * with DB operations via [CyclingRepository]. When a cycling session is active,
 * step accumulation is paused while step-frequency tracking continues (so the
 * classifier can detect when cycling ends).
 *
 * On service start, any orphaned ongoing cycling session (from a previous service
 * kill) is closed with the current time. [closeOrphanedSession] is called before
 * [launchClassifier] so the classifier always starts from a clean session state.
 * The classifier awaits the cleanup job before its first evaluation, ensuring it
 * never reads a stale open session. The main thread is not blocked.
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
    lateinit var cyclingRepository: CyclingRepository

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var preferencesManager: PreferencesManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var accumulator: StepAccumulator

    private val cyclingSessionManager = CyclingSessionManager()

    private var collectorJob: Job? = null
    private var notificationTickerJob: Job? = null
    private var classifierJob: Job? = null
    private var orphanCleanupJob: Job? = null

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
        // Orphan cleanup must be launched before the classifier so the classifier
        // can join() it before its first evaluation (see launchClassifier).
        closeOrphanedSession()
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
        cyclingSessionManager.reset()
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
            // Step frequency tracking continues even while cycling — the classifier
            // needs it to detect when cycling ends and walking resumes.
            val nowMs = System.currentTimeMillis()
            repeat(delta) { stepFrequencyTracker.recordStep(nowMs) }

            // Skip step accumulation while cycling is active.
            if (cyclingSessionManager.isStepCountingPaused) {
                Log.d(TAG, "Step counting paused (cycling) — ignoring $delta steps")
                return@collect
            }

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
     * features, detects activity transitions, persists them, updates the
     * accumulator's current activity, and manages the cycling session lifecycle.
     *
     * Before entering the evaluation loop, the coroutine awaits
     * [orphanCleanupJob] (launched by [closeOrphanedSession] in
     * [onStartCommand]). This guarantees the classifier never reads a stale
     * open session on its first evaluation. If there was no orphaned session,
     * the job completes almost immediately and causes no meaningful delay.
     * The main thread is not blocked because the join occurs inside this
     * coroutine.
     *
     * All in-memory state changes ([CyclingSessionManager.startSession],
     * [CyclingSessionManager.endSession], [StepAccumulator.setActivity]) happen
     * synchronously inside the loop so that step-count pausing activates
     * immediately on the same iteration that detected the transition.
     *
     * DB writes are launched as separate fire-and-forget coroutines on
     * [serviceScope] so they cannot delay the next classifier evaluation. Each
     * async DB block is wrapped in a try/catch and logs any failure — a DB
     * error therefore does not crash the service or disrupt the loop.
     *
     * When a transition **to** [ActivityState.CYCLING] is detected:
     * 1. [CyclingSessionManager.startSession] is called synchronously, which
     *    immediately sets `sessionActive = true` and pauses step accumulation.
     * 2. The DB insert and subsequent [CyclingSessionManager.setOngoingSessionId]
     *    run asynchronously in a child coroutine.
     *
     * When a transition **from** [ActivityState.CYCLING] is detected:
     * 1. [CyclingSessionManager.endSession] is called synchronously, which
     *    immediately clears step-count pausing.
     * 2. The DB update runs asynchronously in a child coroutine.
     *
     * The [ActivityTransition] DB insert runs asynchronously because it has no
     * downstream effect on classifier state.
     *
     * Runs every [CLASSIFIER_INTERVAL_MS] (~5 seconds) until the service
     * scope is cancelled.
     */
    private fun launchClassifier(): Job = serviceScope.launch {
        // Wait for any orphaned session to be closed before the first evaluation
        // so the classifier never reads a stale open session.
        orphanCleanupJob?.join()
        while (isActive) {
            delay(CLASSIFIER_INTERVAL_MS)
            val features = accelerometerSampler.sampleBuffer.computeWindowFeatures()
                ?: continue // not enough samples yet
            val stepFreq = stepFrequencyTracker.computeStepFrequency()
            val now = System.currentTimeMillis()
            val transition = cyclingClassifier.evaluate(features, stepFreq, now) ?: continue

            Log.d(TAG, "Activity transition: ${transition.fromState} → ${transition.toState}")
            accumulator.setActivity(transition.toState.name)

            // Cycling session lifecycle — in-memory ops are synchronous; DB I/O
            // is offloaded to child coroutines so the loop is never blocked.
            if (transition.toState == ActivityState.CYCLING) {
                val session = cyclingSessionManager.startSession(now) // fast, sets sessionActive=true
                serviceScope.launch {
                    try {
                        val insertedId = cyclingRepository.insertSession(session)
                        cyclingSessionManager.setOngoingSessionId(insertedId.toInt())
                        Log.d(TAG, "Cycling session started, db id=$insertedId")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to insert cycling session into DB", e)
                    }
                }
            }
            if (transition.fromState == ActivityState.CYCLING) {
                val endedSession = cyclingSessionManager.endSession(now) // fast, clears step pause
                if (endedSession != null) {
                    serviceScope.launch {
                        try {
                            cyclingRepository.updateSession(endedSession)
                            Log.d(TAG, "Cycling session ended, duration=${endedSession.durationMinutes} min")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to update cycling session in DB", e)
                        }
                    }
                }
            }

            val transitionEntity = ActivityTransition(
                timestamp = System.currentTimeMillis(),
                fromActivity = transition.fromState.name,
                toActivity = transition.toState.name,
                isManualOverride = false,
            )
            serviceScope.launch {
                try {
                    stepRepository.insertTransition(transitionEntity)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to insert activity transition into DB", e)
                }
            }
        }
    }

    // ─── App restart recovery ─────────────────────────────────────────────────

    /**
     * Closes any orphaned cycling session left open from a previous service
     * kill (i.e. a [com.podometer.data.db.CyclingSession] row with a null
     * [com.podometer.data.db.CyclingSession.endTime]).
     *
     * The launched [Job] is stored in [orphanCleanupJob] so that
     * [launchClassifier] can `join()` it before the classifier's first
     * evaluation, guaranteeing no stale open session is visible to the loop.
     * If there is no orphaned session the job completes almost immediately.
     *
     * Must be called in [onStartCommand] before [launchClassifier].
     */
    private fun closeOrphanedSession() {
        orphanCleanupJob = serviceScope.launch {
            val ongoing = cyclingRepository.getOngoingSession() ?: return@launch
            val nowMs = System.currentTimeMillis()
            val durationMs = nowMs - ongoing.startTime
            val durationMinutes = ((durationMs + 30_000L) / 60_000L).toInt()
            val closed = ongoing.copy(endTime = nowMs, durationMinutes = durationMinutes)
            cyclingRepository.updateSession(closed)
            Log.d(TAG, "Closed orphaned cycling session id=${ongoing.id}, duration=${durationMinutes} min")
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
