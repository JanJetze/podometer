// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.dashboard

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_FORMATTER_ISO = DateTimeFormatter.ISO_LOCAL_DATE

/**
 * Formats a step count with locale-aware thousands separators.
 *
 * Examples (en-US locale): 0 → "0", 1000 → "1,000", 100000 → "100,000".
 *
 * This is a pure-Kotlin function with no Compose or Android resource dependencies,
 * making it directly testable on the JVM.
 *
 * @param steps  The step count to format (non-negative).
 * @param locale The [Locale] used for grouping separator; defaults to [Locale.getDefault].
 * @return Locale-formatted string with thousands separators, e.g. "10,000".
 */
fun formatStepCount(steps: Int, locale: Locale = Locale.getDefault()): String {
    val format = NumberFormat.getIntegerInstance(locale)
    format.isGroupingUsed = true
    return format.format(steps)
}

/**
 * Returns `true` when [currentDate] represents a different (later) calendar day than
 * [lastKnownDate], indicating that a date rollover (midnight boundary) has occurred.
 *
 * Both parameters must be in "yyyy-MM-dd" ISO-8601 format. If [currentDate] equals
 * or predates [lastKnownDate], returns `false`.
 *
 * This is a pure-Kotlin function with no Android or Compose dependencies,
 * making it directly testable on the JVM. The ViewModel or service layer should call
 * this helper to detect when a new day has started and reset daily counters.
 *
 * @param lastKnownDate The last recorded date string in "yyyy-MM-dd" format.
 * @param currentDate   The current date string in "yyyy-MM-dd" format.
 * @return `true` if [currentDate] is strictly after [lastKnownDate]; `false` otherwise.
 */
fun isNewDay(lastKnownDate: String, currentDate: String): Boolean {
    val last = LocalDate.parse(lastKnownDate, DATE_FORMATTER_ISO)
    val current = LocalDate.parse(currentDate, DATE_FORMATTER_ISO)
    return current.isAfter(last)
}

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a")

/**
 * Formats an epoch-millisecond timestamp as a human-readable time string,
 * e.g. "9:30 AM".
 *
 * @param epochMillis Epoch milliseconds to format.
 * @return Formatted time string in the system default timezone.
 */
fun formatActivityTime(epochMillis: Long): String {
    val localTime = Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
    return localTime.format(TIME_FORMATTER)
}
