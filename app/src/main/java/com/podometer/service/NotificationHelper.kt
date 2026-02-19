// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.podometer.MainActivity
import com.podometer.R
import com.podometer.domain.model.ActivityState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages building and updating the persistent foreground notification.
 *
 * Notification content depends on [NotificationStyle]:
 * - [NotificationStyle.MINIMAL]: "7,432 steps"
 * - [NotificationStyle.DETAILED]: "7,432 steps · 5.2 km · Walking"
 *
 * The formatting logic lives in pure [companion object] functions so it can be
 * unit-tested without an Android Context.
 *
 * @param context Application context, used only for building notifications.
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val notificationManager: NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    private fun buildPendingIntent(): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /**
     * Builds a [Notification] with the given step data and display style.
     *
     * @param steps      Total steps to show.
     * @param distanceKm Distance in km (used only in [NotificationStyle.DETAILED]).
     * @param activity   Current activity state (used only in [NotificationStyle.DETAILED]).
     * @param style      Controls how much information is shown.
     */
    fun buildNotification(
        steps: Int,
        distanceKm: Float,
        activity: ActivityState,
        style: NotificationStyle,
    ): Notification {
        val contentText = buildContentText(steps, distanceKm, activity, style)
        return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(buildPendingIntent())
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    /**
     * Updates the existing foreground notification in-place via
     * [NotificationManager.notify] so the system does not recreate it.
     *
     * @param steps      Current step count.
     * @param distanceKm Current distance in km.
     * @param activity   Current activity state.
     * @param style      Display style to use.
     */
    fun updateNotification(
        steps: Int,
        distanceKm: Float,
        activity: ActivityState,
        style: NotificationStyle,
    ) {
        val notification = buildNotification(steps, distanceKm, activity, style)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    // ─── Pure formatting helpers (companion — no Context needed) ─────────────

    companion object {

        internal const val NOTIFICATION_CHANNEL_ID = "step_tracking"
        internal const val NOTIFICATION_ID = 1

        /**
         * Formats [steps] with locale-aware grouping separators and a "step"/"steps" suffix.
         *
         * Examples (US locale): 0 → "0 steps", 1 → "1 step", 7432 → "7,432 steps".
         *
         * @param steps  The step count to format.
         * @param locale Locale for number formatting (defaults to the system default).
         */
        fun formatSteps(steps: Int, locale: Locale = Locale.getDefault()): String {
            val formatted = NumberFormat.getIntegerInstance(locale).format(steps)
            val suffix = if (steps == 1) "step" else "steps"
            return "$formatted $suffix"
        }

        /**
         * Formats [distanceKm] to exactly one decimal place followed by " km".
         *
         * Example: 5.2f → "5.2 km", 3.456f → "3.5 km".
         */
        fun formatDistance(distanceKm: Float): String =
            String.format(Locale.US, "%.1f km", distanceKm)

        /**
         * Returns a human-readable label for [activity] with title-case capitalisation.
         *
         * Examples: WALKING → "Walking", CYCLING → "Cycling", STILL → "Still".
         */
        fun activityDisplayText(activity: ActivityState): String =
            activity.name.lowercase().replaceFirstChar { it.uppercaseChar() }

        /**
         * Builds the notification content text for the given parameters and style.
         *
         * This is a pure function with no side effects and no Android dependencies,
         * making it straightforward to unit-test.
         *
         * @param steps      Total step count.
         * @param distanceKm Distance in km.
         * @param activity   Current activity.
         * @param style      [NotificationStyle.MINIMAL] or [NotificationStyle.DETAILED].
         * @param locale     Locale for number formatting (defaults to system default).
         */
        fun buildContentText(
            steps: Int,
            distanceKm: Float,
            activity: ActivityState,
            style: NotificationStyle,
            locale: Locale = Locale.getDefault(),
        ): String {
            val stepsText = formatSteps(steps, locale)
            return when (style) {
                NotificationStyle.MINIMAL -> stepsText
                NotificationStyle.DETAILED ->
                    "$stepsText · ${formatDistance(distanceKm)} · ${activityDisplayText(activity)}"
            }
        }
    }
}
