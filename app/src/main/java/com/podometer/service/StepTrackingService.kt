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
import com.podometer.data.db.ActivityTransition
import com.podometer.data.db.HourlyStepAggregate
import com.podometer.data.db.SensorWindow
import com.podometer.data.repository.CyclingRepository
import com.podometer.data.repository.PreferencesManager
import com.podometer.data.repository.SensorWindowRepository
import com.podometer.data.repository.StepRepository
import com.podometer.data.sensor.AccelerometerSampler
import com.podometer.data.sensor.CyclingClassifier
import com.podometer.data.sensor.StepFrequencyTracker
import com.podometer.data.sensor.StepSensorManager
import com.podometer.domain.model.ActivityState
import com.podometer.util.DateTimeUtils
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
    lateinit var sensorWindowRepository: SensorWindowRepository

    @Inject
    lateinit var preferencesManager: PreferencesManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var accumulator: StepAccumulator

    private val cyclingSessionManager = CyclingSessionManager()

    private var collectorJob: Job? = null
    private var notificationTickerJob: Job? = null
    private var classifierJob: Job? = null
    private var orphanCleanupJob: Job? = null

    /**
     * Timestamp (ms) of the most recently processed step event.  Used by
     * [collectStepEvents] to spread burst step timestamps evenly over the
     * elapsed interval so that [StepFrequencyTracker] receives distinct
     * timestamps even when the sensor delivers multiple steps at once.
     *
     * Reset to `0L` in [onDestroy] alongside the other sensor state.
     */
    private var lastStepEventMs: Long = 0L

    /**
     * Stride length in kilometres, read once from [PreferencesManager] during
     * [onCreate] and cached here so that [collectStepEvents] can construct
     * partial-hour [DailySummary] records for live dashboard updates without
     * re-reading the preference on every step event.
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
        val hourStart = StepAccumulator.truncateToHour(now)
        val currentHourSteps = runBlockingWithDefault(
            default = 0,
            tag = "read current-hour steps from DB",
        ) { stepRepository.getStepsForHour(hourStart) }
        val totalToday = runBlockingWithDefault(
            default = 0,
            tag = "read today's total steps from DB",
        ) { stepRepository.getTodayTotalStepsSnapshot() }
        accumulator = StepAccumulator(
            initialHourTimestamp = now,
            strideLengthKm = strideKm,
            initialCurrentHourSteps = currentHourSteps,
            initialTotalStepsToday = totalToday,
        )
        startForegroundWithNotification()
        Log.d(TAG, "Service created: restored $currentHourSteps current-hour steps, $totalToday today total")
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
        if (orphanCleanupJob == null || orphanCleanupJob?.isActive != true) {
            closeOrphanedSession()
        }
        // Purge sensor windows older than 7 days on each service start.
        serviceScope.launch {
            try {
                sensorWindowRepository.deleteOlderThan(
                    System.currentTimeMillis() - SENSOR_WINDOW_RETENTION_MS,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to purge old sensor windows", e)
            }
        }
        if (classifierJob == null || classifierJob?.isActive != true) {
            classifierJob = launchClassifier()
        }
        Log.d(TAG, "Service started, sensor listening")
        return START_STICKY
    }

    override fun onDestroy() {
        // Must complete flush before cancelling scope to avoid data loss.
        // accumulator.flush() is a mid-hour partial flush — it does not produce
        // activity minutes (walkingMinutes/cyclingMinutes are always 0 here).
        // Only steps and distance are updated to preserve previously accumulated
        // activity minutes stored in the database.
        val results = accumulator.flush()
        if (results.isNotEmpty()) {
            runBlocking {
                for (result in results) {
                    stepRepository.upsertHourlyAggregate(result.aggregate)
                    stepRepository.upsertStepsAndDistance(
                        date = result.dailySummary.date,
                        totalSteps = result.dailySummary.totalSteps,
                        totalDistance = result.dailySummary.totalDistance,
                    )
                }
                Log.d(TAG, "Final flush on destroy: ${results.size} result(s)")
            }
        }
        classifierJob?.cancel()
        stepSensorManager.stopListening()
        accelerometerSampler.stopSampling()
        lastStepEventMs = 0L
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
            // Spread burst step-counter events evenly across the elapsed interval
            // so that StepFrequencyTracker receives distinct timestamps.
            // TYPE_STEP_COUNTER can deliver multiple steps at once; recording them
            // all at the same instant causes computeStepFrequency() to return 0.0
            // (zero duration), which prevents the cycling classifier from detecting
            // walking during burst events.
            // Step frequency tracking continues even while cycling — the classifier
            // needs it to detect when cycling ends and walking resumes.
            val nowMs = System.currentTimeMillis()
            val timestamps = spreadTimestamps(lastStepEventMs, nowMs, delta)
            for (ts in timestamps) {
                stepFrequencyTracker.recordStep(ts)
            }
            lastStepEventMs = nowMs

            // Skip step accumulation while cycling is active.
            if (cyclingSessionManager.isStepCountingPaused) {
                Log.d(TAG, "Step counting paused (cycling) — ignoring $delta steps")
                return@collect
            }

            val flushResults = accumulator.addSteps(delta)
            for (flushResult in flushResults) {
                stepRepository.upsertHourlyAggregate(flushResult.aggregate)
                // Update steps/distance first so the row exists before incrementing minutes.
                stepRepository.upsertStepsAndDistance(
                    date = flushResult.dailySummary.date,
                    totalSteps = flushResult.dailySummary.totalSteps,
                    totalDistance = flushResult.dailySummary.totalDistance,
                )
                if (flushResult.walkingMinutes > 0) {
                    stepRepository.addWalkingMinutes(flushResult.dailySummary.date, flushResult.walkingMinutes)
                }
                if (flushResult.cyclingMinutes > 0) {
                    stepRepository.addCyclingMinutes(flushResult.dailySummary.date, flushResult.cyclingMinutes)
                }
                Log.d(
                    TAG,
                    "Flushed ${flushResult.aggregate.stepCountDelta} steps for hour " +
                        "${flushResult.aggregate.timestamp}, " +
                        "walkingMinutes=${flushResult.walkingMinutes}, " +
                        "cyclingMinutes=${flushResult.cyclingMinutes}",
                )
            }

            // Always write the current partial-hour state so the dashboard (which reads
            // from Room Flows) sees live step-count updates without waiting up to 59
            // minutes for the first hour-boundary flush.
            // Only steps and distance are updated here; activity minutes are only
            // finalized at hour boundaries and must not be reset mid-hour.
            // The upsert (delete-by-timestamp + insert) ensures no duplicate rows.
            val partialAggregate = HourlyStepAggregate(
                timestamp = StepAccumulator.truncateToHour(nowMs),
                stepCountDelta = accumulator.currentHourSteps,
                detectedActivity = accumulator.currentActivity,
            )
            stepRepository.upsertHourlyAggregate(partialAggregate)
            stepRepository.upsertStepsAndDistance(
                date = DateTimeUtils.toLocalDate(nowMs).toString(),
                totalSteps = accumulator.totalStepsToday,
                totalDistance = accumulator.totalStepsToday * strideLengthKm,
            )
            Log.d(TAG, "Partial-hour write: ${accumulator.currentHourSteps} steps in current hour, ${accumulator.totalStepsToday} today")
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
     * Runs every [CLASSIFIER_INTERVAL_MS] (~30 seconds) until the service
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
            val now = System.currentTimeMillis()
            val stepFreq = stepFrequencyTracker.computeStepFrequency(now)

            // Persist raw sensor window for retroactive recomputation (7-day retention).
            serviceScope.launch {
                try {
                    sensorWindowRepository.insertWindow(
                        SensorWindow(
                            timestamp = now,
                            magnitudeVariance = features.magnitudeVariance,
                            stepFrequencyHz = stepFreq,
                            stepCount = 0, // per-window step count not tracked; reserved for future use
                        ),
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to insert sensor window", e)
                }
            }

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
                val endedSession = cyclingSessionManager.endSession(transition.effectiveTimestamp) // fast, clears step pause
                if (endedSession != null) {
                    val durationMs = endedSession.endTime!! - endedSession.startTime
                    if (durationMs < CyclingSessionManager.MIN_SESSION_DURATION_MS) {
                        serviceScope.launch {
                            try {
                                cyclingRepository.deleteSession(endedSession)
                                Log.d(TAG, "Cycling session discarded (too short, ${durationMs}ms < ${CyclingSessionManager.MIN_SESSION_DURATION_MS}ms)")
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to delete short cycling session from DB", e)
                            }
                        }
                    } else {
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
            }

            val transitionEntity = ActivityTransition(
                timestamp = transition.effectiveTimestamp,
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
            try {
                val ongoing = cyclingRepository.getOngoingSession() ?: return@launch
                val nowMs = System.currentTimeMillis()
                val durationMs = nowMs - ongoing.startTime
                if (durationMs < CyclingSessionManager.MIN_SESSION_DURATION_MS) {
                    cyclingRepository.deleteSession(ongoing)
                    Log.d(TAG, "Deleted orphaned cycling session id=${ongoing.id} (too short, ${durationMs}ms < ${CyclingSessionManager.MIN_SESSION_DURATION_MS}ms)")
                } else {
                    val durationMinutes = CyclingSessionManager.msToNearestMinute(durationMs)
                    val closed = ongoing.copy(endTime = nowMs, durationMinutes = durationMinutes)
                    cyclingRepository.updateSession(closed)
                    Log.d(TAG, "Closed orphaned cycling session id=${ongoing.id}, duration=${durationMinutes} min")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to close orphaned cycling session", e)
            }
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

    // ─── Constants and helpers ────────────────────────────────────────────────

    // Companion is `internal` rather than `private` to allow direct unit-test access from
    // the same module. @VisibleForTesting marks the intent; production callers should not
    // rely on this symbol.
    internal companion object {
        internal const val TAG = "StepTrackingService"
        private const val NOTIFICATION_CHANNEL_NAME = "Step Tracking"
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 30_000L

        /** Sensor window retention period: 7 days in milliseconds. */
        private const val SENSOR_WINDOW_RETENTION_MS = 7L * 24 * 60 * 60 * 1000

        /**
         * Classifier evaluation interval in milliseconds (~30 seconds).
         *
         * Aligned with [StepFrequencyTracker.DEFAULT_WINDOW_MS] so each
         * evaluation samples an independent 30-second step-frequency reading.
         * At SENSOR_DELAY_NORMAL (~5 Hz) this gives ~150 new accelerometer
         * samples per evaluation — sufficient for a stable variance estimate.
         * Each evaluation is stored as a [SensorWindow] for retroactive replay.
         */
        private const val CLASSIFIER_INTERVAL_MS = 30_000L

        /**
         * Distributes [delta] step events evenly between [lastEventMs] and [nowMs],
         * returning a [LongArray] of size [delta] with strictly ascending timestamps.
         *
         * Android's TYPE_STEP_COUNTER sensor delivers steps in batches.  Without
         * spreading, all steps in a burst would share the same timestamp, causing
         * [com.podometer.data.sensor.StepFrequencyTracker.computeStepFrequency] to
         * return `0.0` (zero duration between oldest and newest).  By distributing
         * the timestamps across the elapsed interval the tracker produces a realistic
         * cadence estimate for the cycling classifier.
         *
         * Algorithm:
         * - The span to spread across is `nowMs - lastEventMs`, coerced to at least
         *   `delta` milliseconds so that every step gets a unique timestamp even when
         *   the sensor fires twice in rapid succession.
         * - When [lastEventMs] is `0L` (first ever event) the same coercion applies,
         *   so no burst is lost on start-up.
         * - Step `i` (0-based) is placed at `nowMs - (delta - 1 - i) * intervalMs`,
         *   which guarantees the last step lands exactly on [nowMs].
         * - When [delta] is 1 the single timestamp is always [nowMs] (unchanged
         *   compared with the previous implementation).
         *
         * This function is pure Kotlin with no Android dependencies and is therefore
         * unit-testable directly on the JVM.
         *
         * @param lastEventMs Wall-clock time of the previous step event, or `0L` if
         *   no prior event has been recorded in this service session.
         * @param nowMs       Wall-clock time of the current event (`System.currentTimeMillis()`).
         * @param delta       Number of steps to spread (must be >= 1).
         * @return A [LongArray] of size [delta] with strictly ascending timestamps
         *         whose last element equals [nowMs].
         */
        @VisibleForTesting
        internal fun spreadTimestamps(lastEventMs: Long, nowMs: Long, delta: Int): LongArray {
            if (delta == 1) return longArrayOf(nowMs)
            val rawSpan = if (lastEventMs > 0L) nowMs - lastEventMs else 0L
            val spanMs = rawSpan.coerceAtLeast(delta.toLong())
            val intervalMs = spanMs / delta
            return LongArray(delta) { i -> nowMs - (delta - 1 - i) * intervalMs }
        }

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
