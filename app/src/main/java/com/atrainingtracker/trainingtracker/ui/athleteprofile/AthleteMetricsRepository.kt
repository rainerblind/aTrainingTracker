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

package com.atrainingtracker.trainingtracker.ui.athleteprofile

import android.content.Context
import com.atrainingtracker.trainingtracker.database.AthleteMetricEntry
import com.atrainingtracker.trainingtracker.database.AthleteMetricType
import com.atrainingtracker.trainingtracker.database.AthleteMetricsDatabaseManager
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Storage abstraction used by [AthleteMetricsRepository] so pure JVM unit tests can verify the
 * unidirectional state machine without requiring an Android SQLite runtime (`TST-UI-354.2`).
 */
interface AthleteMetricsDataSource {
    fun getAllEntries(): List<AthleteMetricEntry>
    fun getLatestEntry(metricType: AthleteMetricType): AthleteMetricEntry?
    fun insertEntry(timestampMs: Long, metricType: AthleteMetricType, value: Double): Long
    fun updateEntry(id: Long, metricType: AthleteMetricType, timestampMs: Long, value: Double): Int
    fun deleteEntry(id: Long): Int
    fun seedEntriesInTransaction(seedCandidates: List<AthleteMetricEntry>)
}

/**
 * Production [AthleteMetricsDataSource] delegating to [AthleteMetricsDatabaseManager] (`AthleteMetrics.db`).
 */
class SqliteAthleteMetricsDataSource(
    private val dbManager: AthleteMetricsDatabaseManager
) : AthleteMetricsDataSource {
    override fun getAllEntries(): List<AthleteMetricEntry> = dbManager.getAllEntries()
    override fun getLatestEntry(metricType: AthleteMetricType): AthleteMetricEntry? =
        dbManager.getLatestEntry(metricType)
    override fun insertEntry(timestampMs: Long, metricType: AthleteMetricType, value: Double): Long =
        dbManager.insertEntry(timestampMs, metricType, value)
    override fun updateEntry(
        id: Long,
        metricType: AthleteMetricType,
        timestampMs: Long,
        value: Double
    ): Int = dbManager.updateEntry(id, metricType, timestampMs, value)
    override fun deleteEntry(id: Long): Int = dbManager.deleteEntry(id)
    override fun seedEntriesInTransaction(seedCandidates: List<AthleteMetricEntry>) =
        dbManager.seedEntriesInTransaction(seedCandidates)
}

/**
 * Unidirectional Single Source of Truth repository for timestamped athlete metrics (`REQ-UI-391.3`).
 *
 * Writes all Weight (`kg`), Resting HR (`bpm`), and FTP (`W`) measurements to SQLite (`AthleteMetrics.db`)
 * and projects the chronologically latest entry per metric (`ORDER BY timestamp_ms DESC, _id DESC LIMIT 1`)
 * into [SettingsDataStore].
 */
class AthleteMetricsRepository(
    private val dataSource: AthleteMetricsDataSource,
    private val settingsDataStore: SettingsDataStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val mutex = Mutex()
    private val _entriesFlow = MutableStateFlow<List<AthleteMetricEntry>>(emptyList())
    val entriesFlow: StateFlow<List<AthleteMetricEntry>> = _entriesFlow.asStateFlow()

    /**
     * Loads all entries from SQLite, seeding initial entries once inside a transaction if not yet seeded (`REQ-UI-391.3`).
     */
    suspend fun initializeAndRefresh(nowMs: Long = System.currentTimeMillis()) = withContext(ioDispatcher) {
        mutex.withLock {
            ensureSeededLocked(nowMs)
            _entriesFlow.value = dataSource.getAllEntries()
        }
    }

    /**
     * Atomically seeds existing positive baselines from [SettingsDataStore] into the history table
     * once, guarded by `SettingsDataStore.athleteMetricsSeededFlow` (`REQ-UI-391.3`).
     */
    suspend fun ensureSeededFromDataStore(nowMs: Long = System.currentTimeMillis()) = withContext(ioDispatcher) {
        mutex.withLock {
            ensureSeededLocked(nowMs)
            _entriesFlow.value = dataSource.getAllEntries()
        }
    }

    private suspend fun ensureSeededLocked(nowMs: Long) {
        val alreadySeeded = settingsDataStore.athleteMetricsSeededFlow.first()
        if (alreadySeeded) return

        val candidates = mutableListOf<AthleteMetricEntry>()

        val currentWeightKg = settingsDataStore.athleteWeightKgFlow.first().toDouble()
        if (AthleteMetricType.WEIGHT_KG.isValidMetricValue(currentWeightKg)) {
            candidates.add(
                AthleteMetricEntry(
                    timestampMs = nowMs,
                    metricType = AthleteMetricType.WEIGHT_KG,
                    value = currentWeightKg
                )
            )
        }

        val currentHrRest = settingsDataStore.hrRestFlow.first()?.toDouble()
        if (currentHrRest != null && AthleteMetricType.HR_REST_BPM.isValidMetricValue(currentHrRest)) {
            candidates.add(
                AthleteMetricEntry(
                    timestampMs = nowMs,
                    metricType = AthleteMetricType.HR_REST_BPM,
                    value = currentHrRest
                )
            )
        }

        val currentFtp = settingsDataStore.ftpFlow.first()?.toDouble()
        val ftpTimestamp = settingsDataStore.ftpUpdatedAtFlow.first() ?: nowMs
        if (currentFtp != null && AthleteMetricType.FTP_WATTS.isValidMetricValue(currentFtp)) {
            candidates.add(
                AthleteMetricEntry(
                    timestampMs = ftpTimestamp,
                    metricType = AthleteMetricType.FTP_WATTS,
                    value = currentFtp
                )
            )
        }

        dataSource.seedEntriesInTransaction(candidates)
        settingsDataStore.setAthleteMetricsSeeded(true)
    }

    /**
     * Inserts a new measurement entry, refreshes [entriesFlow], and projects the chronologically latest
     * entry for [metricType] into [SettingsDataStore].
     */
    suspend fun addEntry(
        metricType: AthleteMetricType,
        value: Double,
        timestampMs: Long = System.currentTimeMillis()
    ): Long = withContext(ioDispatcher) {
        mutex.withLock {
            ensureSeededLocked(timestampMs)
            val id = dataSource.insertEntry(timestampMs, metricType, value)
            syncLatestToDataStoreLocked(metricType)
            _entriesFlow.value = dataSource.getAllEntries()
            id
        }
    }

    /**
     * Updates an existing measurement entry, refreshes [entriesFlow], and projects the chronologically latest
     * entry for [metricType] into [SettingsDataStore].
     */
    suspend fun updateEntry(
        id: Long,
        metricType: AthleteMetricType,
        value: Double,
        timestampMs: Long
    ) = withContext(ioDispatcher) {
        mutex.withLock {
            dataSource.updateEntry(id, metricType, timestampMs, value)
            syncLatestToDataStoreLocked(metricType)
            _entriesFlow.value = dataSource.getAllEntries()
        }
    }

    /**
     * Deletes a measurement entry by [id], refreshes [entriesFlow], and projects the remaining latest
     * entry (or clears when empty) into [SettingsDataStore].
     */
    suspend fun deleteEntry(
        id: Long,
        metricType: AthleteMetricType
    ) = withContext(ioDispatcher) {
        mutex.withLock {
            dataSource.deleteEntry(id)
            syncLatestToDataStoreLocked(metricType)
            _entriesFlow.value = dataSource.getAllEntries()
        }
    }

    private suspend fun syncLatestToDataStoreLocked(metricType: AthleteMetricType) {
        val latest = dataSource.getLatestEntry(metricType)
        when (metricType) {
            AthleteMetricType.WEIGHT_KG -> {
                settingsDataStore.setAthleteWeightKg(latest?.value?.toFloat() ?: 0.0f)
            }
            AthleteMetricType.HR_REST_BPM -> {
                settingsDataStore.setHrRest(latest?.value?.roundToInt())
            }
            AthleteMetricType.FTP_WATTS -> {
                settingsDataStore.setFtp(
                    ftp = latest?.value?.roundToInt(),
                    updatedAtMillis = latest?.timestampMs ?: 0L
                )
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AthleteMetricsRepository? = null

        @JvmStatic
        fun getInstance(context: Context): AthleteMetricsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AthleteMetricsRepository(
                    dataSource = SqliteAthleteMetricsDataSource(
                        AthleteMetricsDatabaseManager.getInstance(context.applicationContext)
                    ),
                    settingsDataStore = SettingsDataStore.getInstance(context.applicationContext)
                ).also { INSTANCE = it }
            }
        }
    }
}
