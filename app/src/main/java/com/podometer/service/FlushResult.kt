// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.service

import com.podometer.data.db.DailySummary
import com.podometer.data.db.StepBucket

/**
 * Holds the data produced when [StepAccumulator] crosses a bucket boundary or
 * is explicitly flushed.
 *
 * Both objects should be persisted to the repository:
 * - [bucket] via [com.podometer.data.repository.StepBucketRepository.upsert]
 * - [dailySummary] via [com.podometer.data.repository.StepRepository.upsertStepsAndDistance]
 *
 * @property bucket      The completed 5-minute step-count bucket.
 * @property dailySummary The up-to-date daily summary (steps and distance only).
 */
data class FlushResult(
    val bucket: StepBucket,
    val dailySummary: DailySummary,
)
