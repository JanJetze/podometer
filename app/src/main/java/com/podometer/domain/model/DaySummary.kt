// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.domain.model

/**
 * Domain model representing the aggregated activity summary for a single day.
 *
 * Maps from the DB entity [com.podometer.data.db.DailySummary].
 *
 * @property date            Calendar date in "yyyy-MM-dd" format.
 * @property totalSteps      Total steps accumulated over the day.
 * @property totalDistanceKm Total distance walked or cycled in kilometres.
 * @property walkingMinutes  Total minutes spent walking.
 * @property cyclingMinutes  Total minutes spent cycling.
 */
data class DaySummary(
    val date: String,
    val totalSteps: Int,
    val totalDistanceKm: Float,
    val walkingMinutes: Int,
    val cyclingMinutes: Int,
)
