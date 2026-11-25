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
import androidx.datastore.preferences.core.edit
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.AthleteMetricEntry
import com.atrainingtracker.trainingtracker.database.AthleteMetricType
import com.atrainingtracker.trainingtracker.database.ZoneSnapshotEntry
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    override fun getEntryEffectiveAt(metricType: AthleteMetricType, timestampMs: Long): AthleteMetricEntry? {
        if (timestampMs > 0L) {
            val historical = rows.filter { it.metricType == metricType && it.timestampMs <= timestampMs }
                .maxWithOrNull(compareBy<AthleteMetricEntry> { it.timestampMs }.thenBy { it.id })
            if (historical != null) return historical
        }
        return getLatestEntry(metricType)
    }

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

    private var nextZoneId = 1L
    private val zoneRows = mutableListOf<ZoneSnapshotEntry>()
    var seedZoneTransactionCallCount = 0

    override fun getAllZoneSnapshots(): List<ZoneSnapshotEntry> =
        zoneRows.sortedWith(compareByDescending<ZoneSnapshotEntry> { it.timestampMs }.thenByDescending { it.id })

    override fun getZoneHistory(zoneType: SettingsDataStore.ZoneType): List<ZoneSnapshotEntry> =
        zoneRows.filter { it.zoneType == zoneType }
            .sortedWith(compareByDescending<ZoneSnapshotEntry> { it.timestampMs }.thenByDescending { it.id })

    override fun getEffectiveZoneSnapshot(zoneType: SettingsDataStore.ZoneType, timestampMs: Long): ZoneSnapshotEntry? =
        zoneRows.filter { it.zoneType == zoneType && it.timestampMs <= timestampMs }
            .maxWithOrNull(compareBy<ZoneSnapshotEntry> { it.timestampMs }.thenBy { it.id })

    override fun getLatestZoneSnapshot(zoneType: SettingsDataStore.ZoneType): ZoneSnapshotEntry? =
        zoneRows.filter { it.zoneType == zoneType }
            .maxWithOrNull(compareBy<ZoneSnapshotEntry> { it.timestampMs }.thenBy { it.id })

    override fun insertZoneSnapshot(timestampMs: Long, zoneType: SettingsDataStore.ZoneType, boundaries: List<Int>): Long {
        require(boundaries.size == zoneType.boundaryCount)
        val id = nextZoneId++
        zoneRows.add(ZoneSnapshotEntry(id = id, timestampMs = timestampMs, zoneType = zoneType, boundaries = boundaries))
        return id
    }

    override fun updateZoneSnapshot(id: Long, timestampMs: Long, zoneType: SettingsDataStore.ZoneType, boundaries: List<Int>): Int {
        require(boundaries.size == zoneType.boundaryCount)
        val idx = zoneRows.indexOfFirst { it.id == id }
        if (idx < 0) return 0
        zoneRows[idx] = ZoneSnapshotEntry(id = id, timestampMs = timestampMs, zoneType = zoneType, boundaries = boundaries)
        return 1
    }

    override fun deleteZoneSnapshot(id: Long): Int {
        val removed = zoneRows.removeIf { it.id == id }
        return if (removed) 1 else 0
    }

    override fun seedZoneSnapshotsInTransaction(seedCandidates: List<ZoneSnapshotEntry>) {
        seedZoneTransactionCallCount++
        for (c in seedCandidates) {
            if (getLatestZoneSnapshot(c.zoneType) == null) {
                insertZoneSnapshot(c.timestampMs, c.zoneType, c.boundaries)
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
        val settingsDataStore = SettingsDataStore.createForTesting(mockk<Context>(relaxed = true), testDataStore)
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
        val settingsDataStore = SettingsDataStore.createForTesting(mockk<Context>(relaxed = true), testDataStore)
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

        // 8. Verify CP_WATTS & W_PRIME_KJ projection and clear behavior
        val cpId = repository.addEntry(AthleteMetricType.CP_WATTS, 285.0, t2)
        assertEquals(285, settingsDataStore.cpWattsFlow.first())
        repository.deleteEntry(cpId, AthleteMetricType.CP_WATTS)
        assertNull(settingsDataStore.cpWattsFlow.first())

        val wPrimeId = repository.addEntry(AthleteMetricType.W_PRIME_KJ, 21.6, t2)
        assertEquals(21.6f, settingsDataStore.wPrimeKjFlow.first()!!, 1e-3f)
        repository.deleteEntry(wPrimeId, AthleteMetricType.W_PRIME_KJ)
        assertNull(settingsDataStore.wPrimeKjFlow.first())
    }

    @Test
    fun `zone snapshot sync projects latest to DataStore and respects historical backdating`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val dataStoreScope = TestScope(testDispatcher + Job())
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { tmpFolder.newFile("zone_sync_state_machine.preferences_pb") }
        )
        val settingsDataStore = SettingsDataStore.createForTesting(mockk<Context>(relaxed = true), testDataStore)
        val fakeDb = FakeAthleteMetricsDataSource()
        val repository = AthleteMetricsRepository(fakeDb, settingsDataStore, testDispatcher)

        val t0 = 1_600_000_000_000L
        val t1 = 1_700_000_000_000L
        val t2 = 1_700_100_000_000L
        val tBackdated = 1_699_900_000_000L

        // 1. Initial seeding
        repository.ensureZoneHistorySeeded(t0)
        assertEquals(1, fakeDb.seedZoneTransactionCallCount)
        assertEquals(3, fakeDb.getAllZoneSnapshots().size)

        // 2. Save snapshot at t1
        val hrRunZones1 = listOf(130, 145, 160, 175)
        val id1 = repository.saveZoneSnapshot(SettingsDataStore.ZoneType.HR_RUN, hrRunZones1, t1)
        assertEquals(hrRunZones1, repository.getEffectiveZoneBoundaries(SettingsDataStore.ZoneType.HR_RUN, t1))
        assertEquals(130, settingsDataStore.getZone1MaxFlow(SettingsDataStore.ZoneType.HR_RUN).first())

        // 3. Save newer snapshot at t2
        val hrRunZones2 = listOf(135, 150, 165, 180)
        val id2 = repository.saveZoneSnapshot(SettingsDataStore.ZoneType.HR_RUN, hrRunZones2, t2)
        assertEquals(135, settingsDataStore.getZone1MaxFlow(SettingsDataStore.ZoneType.HR_RUN).first())

        // 4. Backdated snapshot at tBackdated (< t2) must NOT overwrite latest DataStore boundaries
        val hrRunBackdated = listOf(125, 140, 155, 170)
        val idBackdated = repository.saveZoneSnapshot(SettingsDataStore.ZoneType.HR_RUN, hrRunBackdated, tBackdated)
        assertEquals(135, settingsDataStore.getZone1MaxFlow(SettingsDataStore.ZoneType.HR_RUN).first())

        // 5. Querying effective zones at different timestamps returns exact historical boundaries
        assertEquals(hrRunBackdated, repository.getEffectiveZoneBoundaries(SettingsDataStore.ZoneType.HR_RUN, tBackdated + 1000L))
        assertEquals(hrRunZones1, repository.getEffectiveZoneBoundaries(SettingsDataStore.ZoneType.HR_RUN, t1 + 1000L))
        assertEquals(hrRunZones2, repository.getEffectiveZoneBoundaries(SettingsDataStore.ZoneType.HR_RUN, t2 + 1000L))

        // 6. Delete latest snapshot (id2) -> DataStore projection reverts to next latest (id1)
        repository.deleteZoneSnapshot(id2, SettingsDataStore.ZoneType.HR_RUN)
        assertEquals(130, settingsDataStore.getZone1MaxFlow(SettingsDataStore.ZoneType.HR_RUN).first())
    }

    @Test
    fun `legacy KEY_HR_MAX seeds HR_MAX_RUN and HR_MAX_BIKE idempotently even when athlete_metrics_seeded is true (TST-SET-135_1)`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val dataStoreScope = TestScope(testDispatcher + Job())
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { tmpFolder.newFile("legacy_hrmax_seed.preferences_pb") }
        )
        testDataStore.edit { prefs ->
            prefs[SettingsDataStore.KEY_ATHLETE_METRICS_SEEDED] = true
            prefs[SettingsDataStore.KEY_HR_MAX] = 188
        }
        val settingsDataStore = SettingsDataStore.createForTesting(mockk<Context>(relaxed = true), testDataStore, dataStoreScope)
        val fakeDb = FakeAthleteMetricsDataSource()
        val repository = AthleteMetricsRepository(fakeDb, settingsDataStore, testDispatcher)

        val t0 = 1_760_000_000_000L
        repository.initializeAndRefresh(nowMs = t0)
        assertTrue(settingsDataStore.sportHrMaxSeededFlow.first())
        assertEquals(1, fakeDb.seedTransactionCallCount)

        val seededRun = fakeDb.getLatestEntry(AthleteMetricType.HR_MAX_RUN_BPM)
        val seededBike = fakeDb.getLatestEntry(AthleteMetricType.HR_MAX_BIKE_BPM)
        assertEquals(188.0, seededRun!!.value, 1e-3)
        assertEquals(188.0, seededBike!!.value, 1e-3)
        assertEquals(188, settingsDataStore.hrMaxRunFlow.first())
        assertEquals(188, settingsDataStore.hrMaxBikeFlow.first())
        assertEquals(188, settingsDataStore.hrMaxFlow.first())

        // Re-initializing produces zero duplicate rows
        repository.initializeAndRefresh(nowMs = t0 + 10_000L)
        assertEquals(1, fakeDb.seedTransactionCallCount)
        assertEquals(2, repository.entriesFlow.value.size)

        // Deleting both sport HRmax entries clears hrMaxFlow and is NOT resurrected on subsequent initialize
        repository.deleteEntry(seededRun.id, AthleteMetricType.HR_MAX_RUN_BPM)
        assertEquals(188, settingsDataStore.hrMaxFlow.first())
        repository.deleteEntry(seededBike.id, AthleteMetricType.HR_MAX_BIKE_BPM)
        assertNull(settingsDataStore.hrMaxRunFlow.first())
        assertNull(settingsDataStore.hrMaxBikeFlow.first())
        assertNull(settingsDataStore.hrMaxFlow.first())

        repository.initializeAndRefresh(nowMs = t0 + 20_000L)
        assertTrue(repository.entriesFlow.value.isEmpty())
        dataStoreScope.coroutineContext[Job]?.cancel()
    }

    @Test
    fun `all 7 sport threshold types sync atomically to DataStore and resolve historically via getEffectiveThresholdsAt (TST-SET-135_2)`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val dataStoreScope = TestScope(testDispatcher + Job())
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { tmpFolder.newFile("sport_thresholds_sync.preferences_pb") }
        )
        val settingsDataStore = SettingsDataStore.createForTesting(mockk<Context>(relaxed = true), testDataStore, dataStoreScope)
        val fakeDb = FakeAthleteMetricsDataSource()
        val repository = AthleteMetricsRepository(fakeDb, settingsDataStore, testDispatcher)

        val tOct1 = 1_790_812_800_000L
        val tOct15 = 1_792_022_400_000L
        val tNov1 = 1_793_491_200_000L

        repository.addEntry(AthleteMetricType.HR_REST_BPM, 50.0, tOct1)
        val hrMaxRun1 = repository.addEntry(AthleteMetricType.HR_MAX_RUN_BPM, 190.0, tOct1)
        val hrMaxBike1 = repository.addEntry(AthleteMetricType.HR_MAX_BIKE_BPM, 184.0, tOct1)
        val lthrRun1 = repository.addEntry(AthleteMetricType.LTHR_RUN_BPM, 172.0, tOct1)
        val lthrBike1 = repository.addEntry(AthleteMetricType.LTHR_BIKE_BPM, 166.0, tOct1)
        val lt1Run1 = repository.addEntry(AthleteMetricType.LT1_HR_RUN_BPM, 148.0, tOct1)
        val lt1Bike1 = repository.addEntry(AthleteMetricType.LT1_HR_BIKE_BPM, 142.0, tOct1)
        val lt1Pwr1 = repository.addEntry(AthleteMetricType.LT1_PWR_BIKE_WATTS, 210.0, tOct1)
        repository.addEntry(AthleteMetricType.FTP_WATTS, 285.0, tOct1)

        // Newer Nov 1 thresholds should update DataStore without altering Oct 15 historical lookup
        val lthrRun2 = repository.addEntry(AthleteMetricType.LTHR_RUN_BPM, 176.0, tNov1)
        val hrMaxRun2 = repository.addEntry(AthleteMetricType.HR_MAX_RUN_BPM, 194.0, tNov1)

        assertEquals(194, settingsDataStore.hrMaxRunFlow.first())
        assertEquals(184, settingsDataStore.hrMaxBikeFlow.first())
        assertEquals(194, settingsDataStore.hrMaxFlow.first())
        assertEquals(176, settingsDataStore.lthrRunFlow.first())
        assertEquals(166, settingsDataStore.lthrBikeFlow.first())
        assertEquals(148, settingsDataStore.lt1HrRunFlow.first())
        assertEquals(142, settingsDataStore.lt1HrBikeFlow.first())
        assertEquals(210, settingsDataStore.lt1PwrBikeFlow.first())

        val runOct15 = repository.getEffectiveThresholdsAt(BSportType.RUN, tOct15)
        assertEquals(190, runOct15.hrMaxBpm)
        assertEquals(50, runOct15.hrRestBpm)
        assertEquals(172, runOct15.lthrBpm)
        assertEquals(148, runOct15.lt1HrBpm)
        assertNull(runOct15.ftpWatts)

        val bikeOct15 = repository.getEffectiveThresholdsAt(BSportType.BIKE, tOct15)
        assertEquals(184, bikeOct15.hrMaxBpm)
        assertEquals(166, bikeOct15.lthrBpm)
        assertEquals(142, bikeOct15.lt1HrBpm)
        assertEquals(285, bikeOct15.ftpWatts)
        assertEquals(210, bikeOct15.lt1PowerWatts)

        val unknownOct15 = repository.getEffectiveThresholdsAt(BSportType.UNKNOWN, tOct15)
        assertEquals(190, unknownOct15.hrMaxBpm)
        assertNull(unknownOct15.lthrBpm)

        // Delete newer Nov 1 entries -> DataStore reverts cleanly to Oct 1
        repository.deleteEntry(lthrRun2, AthleteMetricType.LTHR_RUN_BPM)
        repository.deleteEntry(hrMaxRun2, AthleteMetricType.HR_MAX_RUN_BPM)
        assertEquals(172, settingsDataStore.lthrRunFlow.first())
        assertEquals(190, settingsDataStore.hrMaxRunFlow.first())
        assertEquals(190, settingsDataStore.hrMaxFlow.first())

        // Delete remaining sport entries -> keys clear cleanly
        repository.deleteEntry(hrMaxRun1, AthleteMetricType.HR_MAX_RUN_BPM)
        assertEquals(184, settingsDataStore.hrMaxFlow.first())
        repository.deleteEntry(hrMaxBike1, AthleteMetricType.HR_MAX_BIKE_BPM)
        assertNull(settingsDataStore.hrMaxFlow.first())
        repository.deleteEntry(lthrRun1, AthleteMetricType.LTHR_RUN_BPM)
        repository.deleteEntry(lthrBike1, AthleteMetricType.LTHR_BIKE_BPM)
        repository.deleteEntry(lt1Run1, AthleteMetricType.LT1_HR_RUN_BPM)
        repository.deleteEntry(lt1Bike1, AthleteMetricType.LT1_HR_BIKE_BPM)
        repository.deleteEntry(lt1Pwr1, AthleteMetricType.LT1_PWR_BIKE_WATTS)
        assertNull(settingsDataStore.lthrRunFlow.first())
        assertNull(settingsDataStore.lthrBikeFlow.first())
        assertNull(settingsDataStore.lt1HrRunFlow.first())
        assertNull(settingsDataStore.lt1HrBikeFlow.first())
        assertNull(settingsDataStore.lt1PwrBikeFlow.first())
        dataStoreScope.coroutineContext[Job]?.cancel()
    }

    @Test
    fun `upgraded user saving HR_BIKE before first profile open preserves legacy HR_MAX_RUN without duplicate HR_MAX_BIKE rows`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val dataStoreScope = TestScope(testDispatcher + Job())
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { tmpFolder.newFile("upgrade_save_bike_first.preferences_pb") }
        )
        // Pre-upgrade state: legacy KEY_HR_MAX=188, sport keys unset, sport_hr_max_seeded=false
        testDataStore.edit { prefs ->
            prefs[SettingsDataStore.KEY_ATHLETE_METRICS_SEEDED] = true
            prefs[SettingsDataStore.KEY_HR_MAX] = 188
        }
        val settingsDataStore = SettingsDataStore.createForTesting(mockk<Context>(relaxed = true), testDataStore, dataStoreScope)
        val fakeDb = FakeAthleteMetricsDataSource()
        val repository = AthleteMetricsRepository(fakeDb, settingsDataStore, testDispatcher)
        assertFalse(settingsDataStore.sportHrMaxSeededFlow.first())

        // ZoneSettingsScreen.onSaveHeartRate order: persist via repository BEFORE saveCalculatedHeartRateZones
        repository.addEntry(AthleteMetricType.HR_MAX_BIKE_BPM, 180.0, timestampMs = 1_760_000_000_000L)
        settingsDataStore.saveCalculatedHeartRateZones(
            zoneType = SettingsDataStore.ZoneType.HR_BIKE,
            hrMax = 180,
            hrRest = 50,
            method = "KARVONEN",
            cutoffs = listOf(128, 141, 154, 167)
        )

        val runRows = repository.entriesFlow.value.filter { it.metricType == AthleteMetricType.HR_MAX_RUN_BPM }
        val bikeRows = repository.entriesFlow.value.filter { it.metricType == AthleteMetricType.HR_MAX_BIKE_BPM }
        assertEquals("Legacy Run HRmax must be preserved as a single row", 1, runRows.size)
        assertEquals(188.0, runRows.single().value, 1e-3)
        assertEquals("Saved Bike HRmax must produce exactly 1 row (no duplicate seeded row)", 1, bikeRows.size)
        assertEquals(180.0, bikeRows.single().value, 1e-3)
        assertEquals(188, settingsDataStore.hrMaxRunFlow.first())
        assertEquals(180, settingsDataStore.hrMaxBikeFlow.first())
        assertEquals(188, settingsDataStore.hrMaxFlow.first())
        assertTrue(settingsDataStore.sportHrMaxSeededFlow.first())
        dataStoreScope.coroutineContext[Job]?.cancel()
    }

    @Test
    fun `fresh install saving HR_BIKE creates zero HR_MAX_RUN rows and single HR_MAX_BIKE row`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val dataStoreScope = TestScope(testDispatcher + Job())
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { tmpFolder.newFile("fresh_save_bike_first.preferences_pb") }
        )
        val settingsDataStore = SettingsDataStore.createForTesting(mockk<Context>(relaxed = true), testDataStore, dataStoreScope)
        val fakeDb = FakeAthleteMetricsDataSource()
        val repository = AthleteMetricsRepository(fakeDb, settingsDataStore, testDispatcher)

        assertNull(settingsDataStore.legacyHrMaxRawFlow.first())
        assertFalse(settingsDataStore.sportHrMaxSeededFlow.first())

        repository.addEntry(AthleteMetricType.HR_MAX_BIKE_BPM, 180.0, timestampMs = 1_760_000_000_000L)
        settingsDataStore.saveCalculatedHeartRateZones(
            zoneType = SettingsDataStore.ZoneType.HR_BIKE,
            hrMax = 180,
            hrRest = 52,
            method = "KARVONEN",
            cutoffs = listOf(129, 142, 155, 168)
        )

        val runRows = repository.entriesFlow.value.filter { it.metricType == AthleteMetricType.HR_MAX_RUN_BPM }
        val bikeRows = repository.entriesFlow.value.filter { it.metricType == AthleteMetricType.HR_MAX_BIKE_BPM }
        assertTrue("Fresh install must NOT fabricate HR_MAX_RUN_BPM", runRows.isEmpty())
        assertEquals("Fresh install must insert exactly 1 HR_MAX_BIKE_BPM row", 1, bikeRows.size)
        assertEquals(180.0, bikeRows.single().value, 1e-3)
        assertNull(settingsDataStore.hrMaxRunFlow.first())
        assertEquals(180, settingsDataStore.hrMaxBikeFlow.first())
        assertEquals(180, settingsDataStore.hrMaxFlow.first())
        dataStoreScope.coroutineContext[Job]?.cancel()
    }

    @Test
    fun `fresh install calling saveCalculatedHeartRateZones before ensureSeeded never fabricates other sport HRmax`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val dataStoreScope = TestScope(testDispatcher + Job())
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { tmpFolder.newFile("fresh_save_before_seed_guard.preferences_pb") }
        )
        val settingsDataStore = SettingsDataStore.createForTesting(mockk<Context>(relaxed = true), testDataStore, dataStoreScope)
        val fakeDb = FakeAthleteMetricsDataSource()
        val repository = AthleteMetricsRepository(fakeDb, settingsDataStore, testDispatcher)

        // Even if a caller writes sport zones directly before ensureSeededFromDataStore runs,
        // ensureSeededLocked sees hr_max_bike != null and refuses to fabricate HR_MAX_RUN_BPM.
        settingsDataStore.saveCalculatedHeartRateZones(
            zoneType = SettingsDataStore.ZoneType.HR_BIKE,
            hrMax = 180,
            hrRest = 52,
            method = "KARVONEN",
            cutoffs = listOf(129, 142, 155, 168)
        )
        repository.ensureSeededFromDataStore(nowMs = 1_760_000_000_000L)

        assertTrue(
            "ensureSeededFromDataStore must not fabricate HR_MAX_RUN_BPM from freshly saved HR_BIKE",
            repository.entriesFlow.value.none { it.metricType == AthleteMetricType.HR_MAX_RUN_BPM }
        )
        assertNull(settingsDataStore.hrMaxRunFlow.first())
        assertEquals(180, settingsDataStore.hrMaxBikeFlow.first())
        dataStoreScope.coroutineContext[Job]?.cancel()
    }
}
