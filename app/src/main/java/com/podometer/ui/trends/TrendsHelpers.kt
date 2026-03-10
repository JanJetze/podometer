// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.trends

import com.podometer.domain.model.DaySummary
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

// ─── Internal formatters ──────────────────────────────────────────────────────

internal val DATE_FORMATTER_ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

/** Formatter for the week/month header, e.g. "Mar 3". */
internal val HEADER_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")

/** Formatter for the month header, e.g. "March 2026". */
internal val MONTH_LABEL_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

private val DAY_LABELS = mapOf(
    DayOfWeek.MONDAY to "M",
    DayOfWeek.TUESDAY to "T",
    DayOfWeek.WEDNESDAY to "W",
    DayOfWeek.THURSDAY to "T",
    DayOfWeek.FRIDAY to "F",
    DayOfWeek.SATURDAY to "S",
    DayOfWeek.SUNDAY to "S",
)

// ─── Bar tier enum ────────────────────────────────────────────────────────────

/**
 * The colour tier a [TrendsChartBar] falls into based on its step count relative
 * to [minimumGoal] and [targetGoal].
 */
enum class TrendsBarTier {
    /** Steps are strictly below the minimum goal — muted colour. */
    BELOW_MINIMUM,

    /** Steps meet or exceed minimum goal but are strictly below target — sage green. */
    MINIMUM_TO_TARGET,

    /** Steps meet or exceed the target goal — primary accent colour. */
    ABOVE_TARGET,
}

// ─── Bar data model ───────────────────────────────────────────────────────────

/**
 * A single bar entry for the trends charts.
 *
 * @property date          Calendar date in "yyyy-MM-dd" format.
 * @property dayLabel      Single-character day abbreviation (M, T, W, T, F, S, S) for weekly charts,
 *                         or day-of-month number string for monthly charts.
 * @property steps         Total steps for this day.
 * @property heightFraction Normalised bar height in [0f, 1f] relative to the chart ceiling.
 * @property tier          Colour tier based on step count vs goals.
 */
data class TrendsChartBar(
    val date: String,
    val dayLabel: String,
    val steps: Int,
    val heightFraction: Float,
    val tier: TrendsBarTier,
)

// ─── Pure helper functions ────────────────────────────────────────────────────

/**
 * Returns the single-character day label for a date in "yyyy-MM-dd" format.
 *
 * @param dateStr ISO local date string.
 * @return Single-character label from the set {M, T, W, T, F, S, S}.
 */
fun weekDayLabel(dateStr: String): String {
    val date = LocalDate.parse(dateStr, DATE_FORMATTER_ISO)
    return DAY_LABELS[date.dayOfWeek] ?: "?"
}

/**
 * Builds a list of [TrendsChartBar]s from a list of [DaySummary] objects.
 *
 * The bars are ordered in the same order as [daySummaries]. The Y-axis ceiling is
 * `max(targetGoal, maxSteps)` so bars above goal are never clipped.
 *
 * @param daySummaries Ordered list of per-day summaries (weekly: 7 items, monthly: up to 31).
 * @param targetGoal   User's daily target goal; used as minimum axis ceiling.
 * @param minimumGoal  User's minimum daily activity level; determines tier boundaries.
 * @return List of [TrendsChartBar]s in the same order as [daySummaries].
 */
fun buildTrendsChartBars(
    daySummaries: List<DaySummary>,
    targetGoal: Int,
    minimumGoal: Int,
): List<TrendsChartBar> {
    if (daySummaries.isEmpty()) return emptyList()

    val maxSteps = daySummaries.maxOf { it.totalSteps }
    val ceiling = maxOf(targetGoal, maxSteps)

    return daySummaries.map { summary ->
        val heightFraction = if (ceiling > 0) {
            (summary.totalSteps.toFloat() / ceiling.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        val tier = when {
            summary.totalSteps >= targetGoal -> TrendsBarTier.ABOVE_TARGET
            summary.totalSteps >= minimumGoal -> TrendsBarTier.MINIMUM_TO_TARGET
            else -> TrendsBarTier.BELOW_MINIMUM
        }
        TrendsChartBar(
            date = summary.date,
            dayLabel = weekDayLabel(summary.date),
            steps = summary.totalSteps,
            heightFraction = heightFraction,
            tier = tier,
        )
    }
}

/**
 * Builds a list of [TrendsChartBar]s for a monthly chart.
 *
 * Same as [buildTrendsChartBars] but the [TrendsChartBar.dayLabel] is the day-of-month
 * number as a string (e.g., "1", "15", "31").
 *
 * @param daySummaries Ordered list of per-day summaries for the month.
 * @param targetGoal   User's daily target goal.
 * @param minimumGoal  User's minimum daily activity level.
 * @return List of [TrendsChartBar]s with day-of-month labels.
 */
fun buildMonthlyChartBars(
    daySummaries: List<DaySummary>,
    targetGoal: Int,
    minimumGoal: Int,
): List<TrendsChartBar> {
    if (daySummaries.isEmpty()) return emptyList()

    val maxSteps = daySummaries.maxOf { it.totalSteps }
    val ceiling = maxOf(targetGoal, maxSteps)

    return daySummaries.map { summary ->
        val date = LocalDate.parse(summary.date, DATE_FORMATTER_ISO)
        val heightFraction = if (ceiling > 0) {
            (summary.totalSteps.toFloat() / ceiling.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        val tier = when {
            summary.totalSteps >= targetGoal -> TrendsBarTier.ABOVE_TARGET
            summary.totalSteps >= minimumGoal -> TrendsBarTier.MINIMUM_TO_TARGET
            else -> TrendsBarTier.BELOW_MINIMUM
        }
        TrendsChartBar(
            date = summary.date,
            dayLabel = date.dayOfMonth.toString(),
            steps = summary.totalSteps,
            heightFraction = heightFraction,
            tier = tier,
        )
    }
}

/**
 * Computes the Y-axis fraction for the goal line in a trends chart.
 *
 * The fraction is relative to `max(targetGoal, maxSteps)` so the line always appears
 * within the visible chart area. Returns 1f as a safe default when both values are zero.
 *
 * @param targetGoal The user's target daily step goal.
 * @param maxSteps   Maximum step count across all bars in the current period.
 * @return Fractional goal-line position in [0f, 1f].
 */
fun computeTrendsGoalFraction(targetGoal: Int, maxSteps: Int): Float {
    val ceiling = maxOf(targetGoal, maxSteps)
    if (ceiling == 0) return 1f
    return (targetGoal.toFloat() / ceiling.toFloat()).coerceIn(0f, 1f)
}

/**
 * Computes aggregate [TrendsStats] from a list of [DaySummary] objects.
 *
 * @param daySummaries List of per-day summaries; may be empty.
 * @param targetGoal   User's target daily step goal; used for achievement rate computation.
 * @return [TrendsStats] with all metrics computed from [daySummaries].
 */
fun computeTrendsStats(daySummaries: List<DaySummary>, targetGoal: Int): TrendsStats {
    if (daySummaries.isEmpty()) {
        return TrendsStats(
            averageSteps = 0,
            bestDaySteps = 0,
            bestDayDate = "",
            totalDistanceKm = 0.0,
            achievementRate = 0f,
        )
    }

    val averageSteps = daySummaries.map { it.totalSteps }.average().roundToInt()
    val bestDay = daySummaries.maxByOrNull { it.totalSteps }!!
    val totalDistanceKm = daySummaries.sumOf { it.totalDistanceKm.toDouble() }
    val achievingDays = daySummaries.count { it.totalSteps >= targetGoal }
    val achievementRate = achievingDays.toFloat() / daySummaries.size.toFloat()

    return TrendsStats(
        averageSteps = averageSteps,
        bestDaySteps = bestDay.totalSteps,
        bestDayDate = bestDay.date,
        totalDistanceKm = totalDistanceKm,
        achievementRate = achievementRate,
    )
}

/**
 * Formats a week date range label, e.g. "Mar 3 – Mar 9".
 *
 * @param startDate ISO date string for the first day of the week.
 * @param endDate   ISO date string for the last day of the week.
 * @return Formatted week label string.
 */
fun formatWeekLabel(startDate: String, endDate: String): String {
    val start = LocalDate.parse(startDate, DATE_FORMATTER_ISO)
    val end = LocalDate.parse(endDate, DATE_FORMATTER_ISO)
    return "${start.format(HEADER_DATE_FORMATTER)} – ${end.format(HEADER_DATE_FORMATTER)}"
}

/**
 * Formats a total distance value with one decimal place and a "km" suffix.
 *
 * Example: 7.56 → "7.6 km".
 *
 * @param distanceKm Total distance in kilometres.
 * @param locale     Locale used for decimal formatting; defaults to [Locale.getDefault].
 * @return Formatted distance string, e.g. "7.6 km".
 */
fun formatTrendsDistance(distanceKm: Double, locale: Locale = Locale.getDefault()): String {
    val format = NumberFormat.getNumberInstance(locale)
    format.minimumFractionDigits = 1
    format.maximumFractionDigits = 1
    return "${format.format(distanceKm)} km"
}

/**
 * Formats an achievement rate as a percentage string.
 *
 * Example: 0.75f → "75%".
 *
 * @param rate Achievement rate in [0f, 1f].
 * @return Formatted percentage string without decimal places.
 */
fun formatTrendsAchievementRate(rate: Float): String {
    val percent = (rate * 100f).roundToInt()
    return "$percent%"
}
