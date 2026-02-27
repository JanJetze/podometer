// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.service

import com.podometer.data.db.DailySummary
import com.podometer.data.db.HourlyStepAggregate

/**
 * Holds the data produced when [StepAccumulator] crosses an hour boundary or is
 * explicitly flushed.
 *
 * Both objects should be persisted to the repository:
 * - [aggregate] via [StepRepository.insertHourlyAggregate]
 * - [dailySummary] via [StepRepository.upsertStepsAndDistance]
 *
 * Activity minutes should be persisted separately when non-zero:
 * - [walkingMinutes] via [StepRepository.addWalkingMinutes]
 * - [cyclingMinutes] via [StepRepository.addCyclingMinutes]
 *
 * Activity minutes are only non-zero for completed hour buckets (i.e. results
 * produced by hour-boundary crossings in [StepAccumulator.addSteps]). Results
 * from [StepAccumulator.flush] (mid-hour, partial) always carry zero minutes
 * because the hour is not yet complete.
 *
 * @property aggregate       The completed hourly bucket.
 * @property dailySummary    The up-to-date daily summary (steps and distance only;
 *                           activity minutes are tracked separately).
 * @property walkingMinutes  Minutes of walking to add to the daily summary (0 or 60
 *                           for a completed hour; always 0 for a mid-hour flush).
 * @property cyclingMinutes  Minutes of cycling to add to the daily summary (0 or 60
 *                           for a completed hour; always 0 for a mid-hour flush).
 */
data class FlushResult(
    val aggregate: HourlyStepAggregate,
    val dailySummary: DailySummary,
    val walkingMinutes: Int = 0,
    val cyclingMinutes: Int = 0,
)
