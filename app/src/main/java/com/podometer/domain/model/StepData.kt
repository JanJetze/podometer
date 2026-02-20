// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.domain.model

/**
 * Domain model representing today's step-count snapshot.
 *
 * @property steps         Total steps taken today.
 * @property goal          Daily step goal (default: 10,000).
 * @property progressPercent Percentage of goal achieved, capped at 100.
 * @property distanceKm    Approximate distance walked in kilometres,
 *                         computed using the stride length from user preferences (default: 0.75 m).
 */
data class StepData(
    val steps: Int,
    val goal: Int,
    val progressPercent: Float,
    val distanceKm: Float,
)
