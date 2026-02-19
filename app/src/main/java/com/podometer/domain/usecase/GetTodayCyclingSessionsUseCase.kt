// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.domain.usecase

import com.podometer.data.db.CyclingSession
import com.podometer.data.repository.CyclingRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Returns a [Flow] of [List<CyclingSession>] for all cycling sessions
 * recorded today.
 *
 * The DB entity [CyclingSession] is reused directly at this layer —
 * no separate domain model is needed yet.
 */
class GetTodayCyclingSessionsUseCase @Inject constructor(
    private val cyclingRepository: CyclingRepository,
) {

    operator fun invoke(): Flow<List<CyclingSession>> =
        cyclingRepository.getTodaySessions()
}
