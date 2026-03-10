// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Shared date/time utilities for epoch-millisecond ↔ calendar conversions.
 *
 * All functions use [ZoneId.systemDefault] so behaviour is consistent with the
 * rest of the codebase.  This object is pure Kotlin with no Android-framework
 * references and is therefore directly unit-testable on the JVM.
 *
 * Centralising these computations ensures that [com.podometer.data.repository.StepRepository]
 * and [com.podometer.service.StepAccumulator] use exactly the same day-boundary
 * logic, eliminating potential divergence during DST transitions.
 */
object DateTimeUtils {

    /**
     * Returns the epoch-millisecond timestamp for midnight (00:00:00.000) at
     * the start of today in the system-default time zone.
     *
     * This is the single authoritative implementation for "start of today".
     * All repositories and the accumulator should call this function instead of
     * computing it independently.
     */
    fun todayStartMillis(): Long =
        LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    /**
     * Returns the epoch-millisecond timestamp for midnight (00:00:00.000) at
     * the start of [date] in the system-default time zone.
     *
     * @param date The [LocalDate] to convert.
     * @return Epoch-millisecond timestamp at the start of [date].
     */
    fun startOfDayMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    /**
     * Converts [epochMillis] to the local [LocalDate] in the system-default
     * time zone.
     *
     * @param epochMillis Epoch-millisecond timestamp to convert.
     * @return The [LocalDate] corresponding to [epochMillis] in the local zone.
     */
    fun toLocalDate(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()

    /**
     * Truncates [epochMillis] to the start of its local hour.
     *
     * Example: 08:37:22.456 → 08:00:00.000 (in the system-default time zone).
     *
     * @param epochMillis Epoch-millisecond timestamp to truncate.
     * @return Epoch-millisecond timestamp at the start of the hour containing
     *   [epochMillis].
     */
    fun truncateToHour(epochMillis: Long): Long =
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .let { zdt ->
                zdt.withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli()
            }

    /**
     * Truncates [epochMillis] to the start of its local 5-minute clock-aligned bucket.
     *
     * Buckets align to :00, :05, :10, :15, :20, :25, :30, :35, :40, :45, :50, :55.
     *
     * Example: 08:37:22.456 → 08:35:00.000 (in the system-default time zone).
     *
     * @param epochMillis Epoch-millisecond timestamp to truncate.
     * @return Epoch-millisecond timestamp at the start of the 5-minute bucket
     *   containing [epochMillis].
     */
    fun truncateToBucket(epochMillis: Long): Long =
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .let { zdt ->
                val bucketMinute = (zdt.minute / 5) * 5
                zdt.withMinute(bucketMinute).withSecond(0).withNano(0).toInstant().toEpochMilli()
            }
}
