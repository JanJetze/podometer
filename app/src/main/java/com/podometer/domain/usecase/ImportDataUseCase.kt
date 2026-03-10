// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.domain.usecase

import com.podometer.data.db.DailySummary
import com.podometer.data.db.StepBucket
import com.podometer.data.db.StepBucketDao
import com.podometer.data.db.StepDao
import com.podometer.data.export.ExportData
import kotlinx.serialization.json.Json

/**
 * Use case that parses an exported JSON file and inserts all data into the local database.
 *
 * Existing daily-summary rows with matching primary keys are replaced.
 * Step-bucket rows are upserted (timestamp is the primary key).
 *
 * @param stepDao DAO for daily summaries.
 * @param stepBucketDao DAO for 5-minute step buckets.
 */
class ImportDataUseCase(
    private val stepDao: StepDao,
    private val stepBucketDao: StepBucketDao,
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Parses [jsonString] as an [ExportData] and inserts all records into the database.
     */
    suspend fun importFromJson(jsonString: String) {
        val data = json.decodeFromString(ExportData.serializer(), jsonString)

        stepDao.insertAllDailySummaries(
            data.dailySummaries.map { s ->
                DailySummary(
                    date = s.date,
                    totalSteps = s.totalSteps,
                    totalDistance = s.totalDistance,
                )
            },
        )

        for (bucket in data.stepBuckets) {
            stepBucketDao.upsert(
                StepBucket(
                    timestamp = bucket.timestamp,
                    stepCount = bucket.stepCount,
                )
            )
        }
    }
}
