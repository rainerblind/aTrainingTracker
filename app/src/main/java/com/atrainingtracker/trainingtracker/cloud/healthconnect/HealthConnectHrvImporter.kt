/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.trainingtracker.cloud.healthconnect

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.hrv.HrvPosture
import com.atrainingtracker.trainingtracker.database.hrv.HrvReadinessEntry
import com.atrainingtracker.trainingtracker.database.hrv.HrvReadinessRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Ingestion service for nocturnal resting HRV and resting heart rate data from Android Health Connect (`REQ-PER-031`).
 *
 * Persists aggregated multi-epoch nocturnal readings into [HrvReadinessRepository] under the isolated
 * [HrvPosture.OVERNIGHT_WEARABLE] cohort, enforcing log-normal mathematical aggregation, date attribution,
 * permission/opt-in gating, and idempotent deduplication.
 */
object HealthConnectHrvImporter {

    private const val TAG = "HealthConnectHrvImport"

    /**
     * Determines waking date attribution for nocturnal sleep records (`REQ-PER-031.2`).
     *
     * - Records from 18:00 to 23:59 attribute to the following calendar day (waking date).
     * - Records from 00:00 to 11:59 attribute to the current calendar day (waking date).
     * - Daytime records (12:00 to 17:59) fall outside the nocturnal sleep window and return null.
     */
    @JvmStatic
    fun attributeDate(instant: Instant, zoneOffset: ZoneOffset?): LocalDate? {
        val offset = zoneOffset ?: ZoneId.systemDefault().rules.getOffset(instant)
        val localDateTime = instant.atOffset(offset).toLocalDateTime()
        val hour = localDateTime.hour
        return when {
            hour >= 18 -> localDateTime.toLocalDate().plusDays(1)
            hour < 12 -> localDateTime.toLocalDate()
            else -> null
        }
    }

    /**
     * Computes log-normal mean of logs and geometric mean rMSSD for multi-epoch readings (`REQ-PER-031.2`):
     * ln_rmssd = (1/M) * sum(ln(rmssd_i))
     * rmssd_ms = exp(ln_rmssd)
     */
    @JvmStatic
    fun computeLogNormalRmssd(rmssdValues: List<Double>): Pair<Double, Double>? {
        val validValues = rmssdValues.filter { it > 0.0 }
        if (validValues.isEmpty()) return null
        val lnSum = validValues.sumOf { ln(it) }
        val lnRmssd = lnSum / validValues.size
        val rmssdMs = exp(lnRmssd)
        return Pair(lnRmssd, rmssdMs)
    }

    /**
     * Imports recent overnight resting HRV and resting heart rate records from Health Connect (`REQ-PER-031`).
     */
    suspend fun importRecentHrv(
        context: Context,
        daysBack: Int = 14,
        repository: HrvReadinessRepository = HrvReadinessRepository.getInstance(context),
        clientOverride: HealthConnectClient? = null
    ): Result<Int> {
        val endTime = Instant.now()
        val startTime = endTime.minus(daysBack.toLong(), ChronoUnit.DAYS)
        return importHrvRange(context, startTime, endTime, repository, clientOverride)
    }

    /**
     * Synchronously imports records within [startTime] and [endTime] on [Dispatchers.IO].
     */
    suspend fun importHrvRange(
        context: Context,
        startTime: Instant,
        endTime: Instant,
        repository: HrvReadinessRepository = HrvReadinessRepository.getInstance(context),
        clientOverride: HealthConnectClient? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (!TrainingApplication.isHealthConnectHrvImportEnabled()) {
            Log.d(TAG, "Health Connect HRV import is disabled in user preferences")
            return@withContext Result.success(0)
        }

        if (!HealthConnectManager.isAvailable(context)) {
            Log.d(TAG, "Health Connect SDK is not available on this device")
            return@withContext Result.success(0)
        }

        if (!HealthConnectManager.hasHrvReadPermissions(context)) {
            Log.w(TAG, "Health Connect HRV read permissions not granted")
            return@withContext Result.success(0)
        }

        try {
            val client = clientOverride ?: HealthConnectManager.getClient(context)

            // Query HeartRateVariabilityRmssdRecord
            val hrvRequest = ReadRecordsRequest(
                recordType = HeartRateVariabilityRmssdRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
            val hrvResponse = client.readRecords(hrvRequest)
            val hrvRecords = hrvResponse.records

            if (hrvRecords.isEmpty()) {
                Log.d(TAG, "No Health Connect HeartRateVariabilityRmssdRecord entries found in range")
                return@withContext Result.success(0)
            }

            // Query RestingHeartRateRecord
            val rhrRecords = try {
                val rhrRequest = ReadRecordsRequest(
                    recordType = RestingHeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
                client.readRecords(rhrRequest).records
            } catch (e: Exception) {
                Log.w(TAG, "Could not read RestingHeartRateRecord from Health Connect: ${e.message}")
                emptyList()
            }

            // Group RestingHeartRateRecords by attributed waking date
            val rhrByDate = mutableMapOf<String, MutableList<RestingHeartRateRecord>>()
            for (rhr in rhrRecords) {
                val wakingDate = attributeDate(rhr.time, rhr.zoneOffset) ?: continue
                val dateIso = wakingDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                rhrByDate.getOrPut(dateIso) { mutableListOf() }.add(rhr)
            }

            // Group HRV records by attributed waking date
            val hrvByDate = mutableMapOf<String, MutableList<HeartRateVariabilityRmssdRecord>>()
            for (hrv in hrvRecords) {
                val wakingDate = attributeDate(hrv.time, hrv.zoneOffset) ?: continue
                val dateIso = wakingDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                hrvByDate.getOrPut(dateIso) { mutableListOf() }.add(hrv)
            }

            var insertedCount = 0

            for ((dateIso, dateRecords) in hrvByDate) {
                // Deduplication check: do not duplicate rows or overwrite existing entries
                val existingEntries = repository.getEntriesForDate(dateIso, HrvPosture.OVERNIGHT_WEARABLE)
                if (existingEntries.isNotEmpty()) {
                    Log.d(TAG, "Entry for $dateIso (${HrvPosture.OVERNIGHT_WEARABLE}) already exists; skipping")
                    continue
                }

                val rmssdValues = dateRecords.map { it.heartRateVariabilityMillis }
                val agg = computeLogNormalRmssd(rmssdValues) ?: continue
                val (lnRmssd, rmssdMs) = agg
                val hrvScore100 = 20.0 * lnRmssd

                // Calculate mean resting HR for the night
                val dayRhrList = rhrByDate[dateIso]
                val restingHrBpm = if (!dayRhrList.isNullOrEmpty()) {
                    (dayRhrList.sumOf { it.beatsPerMinute.toDouble() } / dayRhrList.size).roundToInt()
                } else {
                    0
                }

                val minTs = dateRecords.minOf { it.time.toEpochMilli() }
                val maxTs = dateRecords.maxOf { it.time.toEpochMilli() }
                val durationSec = ((maxTs - minTs) / 1000).toInt().coerceAtLeast(60)
                val timestampMs = maxTs

                val tzId = dateRecords.last().zoneOffset?.id ?: ZoneId.systemDefault().id
                val firstRecord = dateRecords.first()
                val pkgName = firstRecord.metadata.dataOrigin.packageName
                val recId = firstRecord.metadata.id
                val notes = if (recId.isNotBlank()) "HealthConnect:$pkgName:$recId" else "HealthConnect:$pkgName"

                val entry = HrvReadinessEntry(
                    timestampMs = timestampMs,
                    dateIso = dateIso,
                    timezoneId = tzId,
                    restingHrBpm = restingHrBpm,
                    rmssdMs = rmssdMs,
                    lnRmssd = lnRmssd,
                    hrvScore100 = hrvScore100,
                    sdnnMs = null,
                    artifactRatePct = null,
                    durationSec = durationSec,
                    posture = HrvPosture.OVERNIGHT_WEARABLE,
                    isPrimaryForDay = true,
                    notes = notes
                )

                repository.insertEntry(entry)
                insertedCount++
            }

            Log.i(TAG, "Successfully ingested $insertedCount overnight HRV readiness entries from Health Connect")
            Result.success(insertedCount)
        } catch (e: Exception) {
            Log.e(TAG, "Error importing Health Connect HRV records: ${e.message}", e)
            Result.failure(e)
        }
    }
}
