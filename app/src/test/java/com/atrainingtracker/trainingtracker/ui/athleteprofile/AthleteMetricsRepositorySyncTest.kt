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
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.atrainingtracker.trainingtracker.database.AthleteMetricEntry
import com.atrainingtracker.trainingtracker.database.AthleteMetricType
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * In-memory [AthleteMetricsDataSource] for testing [AthleteMetricsRepository]'s transactional seeding
 * and unidirectional [SettingsDataStore] projection state machine (`REQ-UI-391.3`, `TST-UI-354.2`).
 */
private class FakeAthleteMetricsDataSource : AthleteMetricsDataSource {
    private var nextId = 1L
    private val rows = mutableListOf<AthleteMetricEntry>()
    var seedTransactionCallCount = 0

    override fun getAllEntries(): List<AthleteMetricEntry> =
        rows.sortedWith(compareByDescending<AthleteMetricEntry> { it.timestampMs }.thenByDescending { it.id })

    override fun getLatestEntry(metricType: AthleteMetricType): AthleteMetricEntry? =
        rows.filter { it.metricType == metricType }
            .maxWithOrNull(compareBy<AthleteMetricEntry> { it.timestampMs }.thenBy { it.id })

    override fun insertEntry(timestampMs: Long, metricType: AthleteMetricType, value: Double): Long {
        require(metricType.isValidMetricValue(value))
        val id = nextId++
        rows.add(AthleteMetricEntry(id = id, timestampMs = timestampMs, metricType = metricType, value = value))
        return id
    }

    override fun updateEntry(id: Long, metricType: AthleteMetricType, timestampMs: Long, value: Double): Int {
        require(metricType.isValidMetricValue(value))
        val idx = rows.indexOfFirst { it.id == id }
        if (idx < 0) return 0
        rows[idx] = AthleteMetricEntry(id = id, timestampMs = timestampMs, metricType = metricType, value = value)
        return 1
    }

    override fun deleteEntry(id: Long): Int {
        val removed = rows.removeIf { it.id == id }
        return if (removed) 1 else 0
    }

    override fun seedEntriesInTransaction(seedCandidates: List<AthleteMetricEntry>) {
        seedTransactionCallCount++
        for (c in seedCandidates) {
            if (!c.metricType.isValidMetricValue(c.value)) continue
            if (getLatestEntry(c.metricType) == null) {
                insertEntry(c.timestampMs, c.metricType, c.value)
            }
        }
    }
}

/**
 * Unit tests for [AthleteMetricsRepository] initial seeding and unidirectional [SettingsDataStore]
 * synchronization state machine (`REQ-UI-391.3`, `TST-UI-354.2`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AthleteMetricsRepositorySyncTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    @Test
    fun `ensureSeededFromDataStore seeds existing positive baselines once and is idempotent`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val dataStoreScope = TestScope(testDispatcher + Job())
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { tmpFolder.newFile("seed_test.preferences_pb") }
        )
        val settingsDataStore = SettingsDataStore(mockk<Context>(relaxed = true), testDataStore)
        val fakeDb = FakeAthleteMetricsDataSource()
        val repository = AthleteMetricsRepository(fakeDb, settingsDataStore, testDispatcher)

        val ftpTime = 1_750_000_000_000L
        val seedNow = 1_755_000_000_000L
        settingsDataStore.setAthleteWeightKg(73.5f)
        settingsDataStore.setHrRest(51)
        settingsDataStore.setFtp(285, updatedAtMillis = ftpTime)

        repository.ensureSeededFromDataStore(nowMs = seedNow)
        assertTrue(settingsDataStore.athleteMetricsSeededFlow.first())
        assertEquals(1, fakeDb.seedTransactionCallCount)

        val entries = repository.entriesFlow.value
        assertEquals(3, entries.size)
        val seededWeight = entries.first { it.metricType == AthleteMetricType.WEIGHT_KG }
        val seededHr = entries.first { it.metricType == AthleteMetricType.HR_REST_BPM }
        val seededFtp = entries.first { it.metricType == AthleteMetricType.FTP_WATTS }

        assertEquals(73.5, seededWeight.value, 1e-3)
        assertEquals(seedNow, seededWeight.timestampMs)
        assertEquals(51.0, seededHr.value, 1e-3)
        assertEquals(285.0, seededFtp.value, 1e-3)
        assertEquals(ftpTime, seededFtp.timestampMs)

        // Calling a second time must be a no-op
        repository.ensureSeededFromDataStore(nowMs = seedNow + 10_000L)
        assertEquals(1, fakeDb.seedTransactionCallCount)
        assertEquals(3, repository.entriesFlow.value.size)
    }

    @Test
    fun `add, backdated add, edit, and delete state machine projects chronological latest to SettingsDataStore`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val dataStoreScope = TestScope(testDispatcher + Job())
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { tmpFolder.newFile("sync_state_machine.preferences_pb") }
        )
        val settingsDataStore = SettingsDataStore(mockk<Context>(relaxed = true), testDataStore)
        val fakeDb = FakeAthleteMetricsDataSource()
        val repository = AthleteMetricsRepository(fakeDb, settingsDataStore, testDispatcher)

        val t1 = 1_700_000_000_000L
        val t2 = 1_700_100_000_000L
        val tBackdated = 1_699_900_000_000L

        // 1. Add first weight entry at t1 -> updates DataStore to 74.0
        val id1 = repository.addEntry(AthleteMetricType.WEIGHT_KG, 74.0, t1)
        assertEquals(74.0f, settingsDataStore.athleteWeightKgFlow.first(), 1e-3f)

        // 2. Add newer weight entry at t2 -> updates DataStore to 71.5
        val id2 = repository.addEntry(AthleteMetricType.WEIGHT_KG, 71.5, t2)
        assertEquals(71.5f, settingsDataStore.athleteWeightKgFlow.first(), 1e-3f)

        // 3. Add backdated weight entry at tBackdated (< t2) -> DataStore MUST remain at 71.5
        val idBackdated = repository.addEntry(AthleteMetricType.WEIGHT_KG, 76.0, tBackdated)
        assertEquals(71.5f, settingsDataStore.athleteWeightKgFlow.first(), 1e-3f)

        // 4. Edit latest entry (id2) from 71.5 to 70.8 -> DataStore updates to 70.8
        repository.updateEntry(id2, AthleteMetricType.WEIGHT_KG, 70.8, t2)
        assertEquals(70.8f, settingsDataStore.athleteWeightKgFlow.first(), 1e-3f)

        // 5. Delete latest entry (id2) -> DataStore reverts to next most recent entry (id1 at t1 = 74.0)
        repository.deleteEntry(id2, AthleteMetricType.WEIGHT_KG)
        assertEquals(74.0f, settingsDataStore.athleteWeightKgFlow.first(), 1e-3f)

        // 6. Delete remaining entries (id1, idBackdated) -> DataStore resets to 0.0f
        repository.deleteEntry(id1, AthleteMetricType.WEIGHT_KG)
        assertEquals(76.0f, settingsDataStore.athleteWeightKgFlow.first(), 1e-3f)
        repository.deleteEntry(idBackdated, AthleteMetricType.WEIGHT_KG)
        assertEquals(0.0f, settingsDataStore.athleteWeightKgFlow.first(), 1e-3f)

        // 7. Verify FTP & HR_REST clear behavior when last entry is deleted
        val ftpId = repository.addEntry(AthleteMetricType.FTP_WATTS, 295.0, t2)
        assertEquals(295, settingsDataStore.ftpFlow.first())
        assertEquals(t2, settingsDataStore.ftpUpdatedAtFlow.first())
        repository.deleteEntry(ftpId, AthleteMetricType.FTP_WATTS)
        assertNull(settingsDataStore.ftpFlow.first())
        assertNull(settingsDataStore.ftpUpdatedAtFlow.first())

        val hrId = repository.addEntry(AthleteMetricType.HR_REST_BPM, 48.0, t2)
        assertEquals(48, settingsDataStore.hrRestFlow.first())
        repository.deleteEntry(hrId, AthleteMetricType.HR_REST_BPM)
        assertNull(settingsDataStore.hrRestFlow.first())
    }
}
