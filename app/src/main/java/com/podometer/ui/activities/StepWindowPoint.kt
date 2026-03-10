// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.activities

/**
 * A single 30-second window of step data, used by the step graph for rendering.
 *
 * This is a pure UI data class with no Room/database dependency.
 *
 * @property id        Optional identifier (0 for synthetic data points).
 * @property timestamp Window start in epoch milliseconds.
 * @property stepCount Number of steps detected during this window.
 */
data class StepWindowPoint(
    val id: Long = 0,
    val timestamp: Long,
    val stepCount: Int,
)

/** Nominal duration of a single step window in milliseconds (30 seconds). */
const val NOMINAL_STEP_WINDOW_MS = 30_000L

/**
 * Sums [StepWindowPoint.stepCount] for windows whose [StepWindowPoint.timestamp]
 * falls within the half-open range `[startTime, endTime)`.
 */
fun List<StepWindowPoint>.sumStepsInRange(startTime: Long, endTime: Long): Int =
    filter { it.timestamp in startTime until endTime }.sumOf { it.stepCount }

/**
 * Spreads step windows that span gaps longer than twice [NOMINAL_STEP_WINDOW_MS]
 * into multiple virtual sub-windows at [NOMINAL_STEP_WINDOW_MS] intervals,
 * distributing steps evenly.
 *
 * The total step count is preserved.
 *
 * @return A new list where gap-spanning windows have been replaced by
 *         multiple evenly spaced virtual windows.
 */
fun List<StepWindowPoint>.spreadWindows(): List<StepWindowPoint> {
    if (size <= 1) return this
    val result = mutableListOf<StepWindowPoint>()

    for (i in indices) {
        val window = this[i]
        val prevTimestamp = if (i > 0) this[i - 1].timestamp else null

        if (prevTimestamp == null || window.stepCount == 0) {
            result.add(window)
            continue
        }

        val gap = window.timestamp - prevTimestamp
        if (gap <= NOMINAL_STEP_WINDOW_MS * 2) {
            result.add(window)
            continue
        }

        val subWindowCount = (gap / NOMINAL_STEP_WINDOW_MS).toInt().coerceAtLeast(1)
        val stepsPerWindow = window.stepCount / subWindowCount
        val remainder = window.stepCount % subWindowCount
        val intervalMs = gap / subWindowCount

        for (j in 0 until subWindowCount) {
            val subTimestamp = prevTimestamp + (j + 1) * intervalMs
            val steps = stepsPerWindow + if (j < remainder) 1 else 0
            result.add(
                StepWindowPoint(
                    timestamp = subTimestamp,
                    stepCount = steps,
                ),
            )
        }
    }
    return result
}
