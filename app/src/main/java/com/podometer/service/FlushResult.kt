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
 * - [dailySummary] via [StepRepository.upsertDailySummary]
 *
 * @property aggregate   The completed hourly bucket.
 * @property dailySummary The up-to-date daily summary including this bucket.
 */
data class FlushResult(
    val aggregate: HourlyStepAggregate,
    val dailySummary: DailySummary,
)
