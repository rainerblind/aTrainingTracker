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
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.response.ReadRecordsResponse
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.hrv.HrvPosture
import com.atrainingtracker.trainingtracker.database.hrv.HrvReadinessDatabaseManager
import com.atrainingtracker.trainingtracker.database.hrv.HrvReadinessRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.lang.reflect.Field
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Unit and contract test suite verifying Android Health Connect resting HRV and resting HR ingestion
 * into [HrvReadinessRepository] (REQ-PER-031, TST-PER-039).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HealthConnectHrvImporterTest {

    private lateinit var context: Context
    private lateinit var repository: HrvReadinessRepository
    private val mockClient = mockk<HealthConnectClient>(relaxed = true)
    private val mockPermissionController = mockk<PermissionController>()

    private fun setStaticField(clazz: Class<*>, fieldName: String, value: Any?) {
        val field: Field = clazz.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(null, value)
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        setStaticField(TrainingApplication::class.java, "cAppContext", context)
        setStaticField(TrainingApplication::class.java, "cSharedPreferences", prefs)

        // Clear database state
        context.deleteDatabase("HrvReadiness.db")
        HrvReadinessDatabaseManager.resetForTesting(null)
        HrvReadinessRepository.resetForTesting(null)
        repository = HrvReadinessRepository.getInstance(context)

        HealthConnectManager.sdkStatusOverride = HealthConnectClient.SDK_AVAILABLE
        HealthConnectManager.clientProvider = { mockClient }

        coEvery { mockClient.permissionController } returns mockPermissionController
        coEvery { mockPermissionController.getGrantedPermissions() } returns HealthConnectManager.HRV_READ_PERMISSIONS
    }

    @After
    fun tearDown() {
        HealthConnectManager.sdkStatusOverride = null
        HealthConnectManager.clientProvider = null
        TrainingApplication.setHealthConnectHrvImportEnabled(false)
        context.deleteDatabase("HrvReadiness.db")
        HrvReadinessDatabaseManager.resetForTesting(null)
        HrvReadinessRepository.resetForTesting(null)
    }

    private fun createTestMetadata(id: String, packageName: String): Metadata {
        val meta = mockk<Metadata>(relaxed = true)
        every { meta.id } returns id
        every { meta.dataOrigin } returns DataOrigin(packageName)
        return meta
    }

    /**
     * TST-PER-039.1: Permission & Opt-In Gating Contract Test.
     */
    @Test
    fun testPermissionAndOptInGating_whenDisabledOrMissingPermissions_abortsSafely() = runBlocking {
        // Case A: Opt-in disabled (default)
        TrainingApplication.setHealthConnectHrvImportEnabled(false)
        val resultDisabled = HealthConnectHrvImporter.importRecentHrv(context, daysBack = 7, repository = repository)
        assertTrue(resultDisabled.isSuccess)
        assertEquals(0, resultDisabled.getOrNull())
        assertEquals(0, repository.getAllEntries().size)

        // Case B: Opt-in enabled, but SDK unavailable
        TrainingApplication.setHealthConnectHrvImportEnabled(true)
        HealthConnectManager.sdkStatusOverride = HealthConnectClient.SDK_UNAVAILABLE
        val resultUnavailable = HealthConnectHrvImporter.importRecentHrv(context, daysBack = 7, repository = repository)
        assertTrue(resultUnavailable.isSuccess)
        assertEquals(0, resultUnavailable.getOrNull())
        assertEquals(0, repository.getAllEntries().size)

        // Case C: SDK available, but permissions missing
        HealthConnectManager.sdkStatusOverride = HealthConnectClient.SDK_AVAILABLE
        coEvery { mockPermissionController.getGrantedPermissions() } returns emptySet()
        val resultNoPerms = HealthConnectHrvImporter.importRecentHrv(context, daysBack = 7, repository = repository)
        assertTrue(resultNoPerms.isSuccess)
        assertEquals(0, resultNoPerms.getOrNull())
        assertEquals(0, repository.getAllEntries().size)

        // Case D: Verify manifest permissions declaration & HealthConnectManager permission sets
        assertTrue(HealthConnectManager.HRV_READ_PERMISSIONS.contains(HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class)))
        assertTrue(HealthConnectManager.HRV_READ_PERMISSIONS.contains(HealthPermission.getReadPermission(RestingHeartRateRecord::class)))

        val manifestFile = File("src/main/AndroidManifest.xml").takeIf { it.exists() }
            ?: File("app/src/main/AndroidManifest.xml")
        val manifestXml = manifestFile.readText()
        assertTrue(manifestXml.contains("android.permission.health.READ_HEART_RATE_VARIABILITY"))
        assertTrue(manifestXml.contains("android.permission.health.READ_RESTING_HEART_RATE"))
    }

    /**
     * TST-PER-039.2: Nocturnal Log-Normal Aggregation & Date Attribution Test.
     */
    @Test
    fun testNocturnalLogNormalAggregationAndDateAttribution() {
        // 1. Math verification: 30.0, 40.0, 50.0 ms
        val samples = listOf(30.0, 40.0, 50.0)
        val agg = HealthConnectHrvImporter.computeLogNormalRmssd(samples)
        assertNotNull(agg)
        val (lnRmssd, rmssdMs) = agg!!

        // Expected: ln(30)+ln(40)+ln(50) = 11.0020998 -> mean = 3.6673666 -> exp(mean) = 39.14867
        assertEquals(3.6674, lnRmssd, 1e-4)
        assertEquals(39.1487, rmssdMs, 1e-4)

        // Single sample
        val singleAgg = HealthConnectHrvImporter.computeLogNormalRmssd(listOf(45.0))
        assertNotNull(singleAgg)
        assertEquals(Math.log(45.0), singleAgg!!.first, 1e-6)
        assertEquals(45.0, singleAgg.second, 1e-6)

        // Empty or non-positive
        assertNull(HealthConnectHrvImporter.computeLogNormalRmssd(emptyList()))
        assertNull(HealthConnectHrvImporter.computeLogNormalRmssd(listOf(0.0, -5.0)))

        // 2. Date attribution verification
        val offset = ZoneOffset.UTC

        // 23:00 on 2026-11-09 -> attributes to morning 2026-11-10
        val evening = LocalDateTime.of(2026, 11, 9, 23, 15).toInstant(offset)
        assertEquals(LocalDate.of(2026, 11, 10), HealthConnectHrvImporter.attributeDate(evening, offset))

        // 05:45 on 2026-11-10 -> attributes to morning 2026-11-10
        val morning = LocalDateTime.of(2026, 11, 10, 5, 45).toInstant(offset)
        assertEquals(LocalDate.of(2026, 11, 10), HealthConnectHrvImporter.attributeDate(morning, offset))

        // 11:59 on 2026-11-10 -> attributes to morning 2026-11-10
        val noonEdge = LocalDateTime.of(2026, 11, 10, 11, 59).toInstant(offset)
        assertEquals(LocalDate.of(2026, 11, 10), HealthConnectHrvImporter.attributeDate(noonEdge, offset))

        // 14:00 on 2026-11-10 -> daytime spot check outside nocturnal window -> null
        val daytime = LocalDateTime.of(2026, 11, 10, 14, 0).toInstant(offset)
        assertNull(HealthConnectHrvImporter.attributeDate(daytime, offset))
    }

    /**
     * TST-PER-039.3: Isolated Cohort Persistence & Deduplication Test.
     */
    @Test
    fun testIsolatedCohortPersistenceAndDeduplication() = runBlocking {
        TrainingApplication.setHealthConnectHrvImportEnabled(true)
        HealthConnectManager.sdkStatusOverride = HealthConnectClient.SDK_AVAILABLE
        coEvery { mockPermissionController.getGrantedPermissions() } returns HealthConnectManager.HRV_READ_PERMISSIONS

        val offset = ZoneOffset.UTC
        val meta1 = createTestMetadata("rec-hrv-1", "com.ouraring.oura")
        val meta2 = createTestMetadata("rec-hrv-2", "com.ouraring.oura")
        val metaRhr = createTestMetadata("rec-rhr-1", "com.ouraring.oura")

        val t1 = LocalDateTime.of(2026, 11, 9, 23, 30).toInstant(offset)
        val t2 = LocalDateTime.of(2026, 11, 10, 4, 30).toInstant(offset)
        val tRhr = LocalDateTime.of(2026, 11, 10, 3, 0).toInstant(offset)

        val hrv1 = HeartRateVariabilityRmssdRecord(
            time = t1,
            zoneOffset = offset,
            heartRateVariabilityMillis = 35.0,
            metadata = meta1
        )
        val hrv2 = HeartRateVariabilityRmssdRecord(
            time = t2,
            zoneOffset = offset,
            heartRateVariabilityMillis = 45.0,
            metadata = meta2
        )
        val rhr = RestingHeartRateRecord(
            time = tRhr,
            zoneOffset = offset,
            beatsPerMinute = 52L,
            metadata = metaRhr
        )

        val mockHrvResponse = mockk<ReadRecordsResponse<HeartRateVariabilityRmssdRecord>>(relaxed = true)
        val mockRhrResponse = mockk<ReadRecordsResponse<RestingHeartRateRecord>>(relaxed = true)
        coEvery { mockHrvResponse.records } returns listOf(hrv1, hrv2)
        coEvery { mockHrvResponse.pageToken } returns null
        coEvery { mockRhrResponse.records } returns listOf(rhr)
        coEvery { mockRhrResponse.pageToken } returns null

        coEvery {
            mockClient.readRecords(match<ReadRecordsRequest<HeartRateVariabilityRmssdRecord>> {
                it.recordType == HeartRateVariabilityRmssdRecord::class
            })
        } returns mockHrvResponse

        coEvery {
            mockClient.readRecords(match<ReadRecordsRequest<RestingHeartRateRecord>> {
                it.recordType == RestingHeartRateRecord::class
            })
        } returns mockRhrResponse

        val start = LocalDateTime.of(2026, 11, 9, 18, 0).toInstant(offset)
        val end = LocalDateTime.of(2026, 11, 10, 12, 0).toInstant(offset)

        // First import -> should insert 1 entry for 2026-11-10
        val result1 = HealthConnectHrvImporter.importHrvRange(context, start, end, repository = repository, clientOverride = mockClient)
        assertTrue(result1.isSuccess)
        assertEquals(1, result1.getOrNull())

        val entries = repository.getAllEntries(HrvPosture.OVERNIGHT_WEARABLE)
        assertEquals(1, entries.size)
        val entry = entries.first()

        assertEquals("2026-11-10", entry.dateIso)
        assertEquals(HrvPosture.OVERNIGHT_WEARABLE, entry.posture)
        assertNull(entry.artifactRatePct)
        assertEquals(52, entry.restingHrBpm)
        assertTrue(entry.isPrimaryForDay)
        assertTrue(entry.notes?.contains("com.ouraring.oura") == true)

        // Check math: sqrt(35 * 45) = sqrt(1575) approx 39.686
        assertEquals(39.686, entry.rmssdMs!!, 1e-2)
        assertEquals(Math.log(entry.rmssdMs!!), entry.lnRmssd!!, 1e-4)
        assertEquals(20.0 * entry.lnRmssd!!, entry.hrvScore100!!, 1e-4)

        // Second import (idempotent deduplication) -> should insert 0 new entries
        val result2 = HealthConnectHrvImporter.importHrvRange(context, start, end, repository = repository, clientOverride = mockClient)
        assertTrue(result2.isSuccess)
        assertEquals(0, result2.getOrNull())
        assertEquals(1, repository.getAllEntries(HrvPosture.OVERNIGHT_WEARABLE).size)
    }

    /**
     * Posture alias symmetry and fromNameOrNull parity.
     */
    @Test
    fun testHrvPosture_overnightWearableAliasAndResolution() {
        assertEquals(HrvPosture.OVERNIGHT_WEARABLE, HrvPosture.OVERNIGHT_HC)
        assertEquals(HrvPosture.OVERNIGHT_WEARABLE, HrvPosture.fromNameOrNull("OVERNIGHT_WEARABLE"))
        assertEquals(HrvPosture.OVERNIGHT_WEARABLE, HrvPosture.fromNameOrNull("OVERNIGHT_HC"))
        assertEquals(HrvPosture.OVERNIGHT_WEARABLE, HrvPosture.fromNameOrNull("overnight_hc"))
        assertEquals(HrvPosture.SUPINE, HrvPosture.fromNameOrNull("SUPINE"))
        assertNull(HrvPosture.fromNameOrNull("UNKNOWN_POSTURE"))
    }

    /**
     * TST-PER-039.1: Relaxation of RHR permission requirement.
     */
    @Test
    fun testPermissionRelaxation_whenHrvGrantedWithoutRhr_succeeds() = runBlocking {
        TrainingApplication.setHealthConnectHrvImportEnabled(true)
        HealthConnectManager.sdkStatusOverride = HealthConnectClient.SDK_AVAILABLE
        val hrvOnlyPermissions = setOf(HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class))
        coEvery { mockPermissionController.getGrantedPermissions() } returns hrvOnlyPermissions

        val offset = ZoneOffset.UTC
        val meta = createTestMetadata("rec-hrv-only", "com.ouraring.oura")
        val t1 = LocalDateTime.of(2026, 11, 10, 4, 0).toInstant(offset)
        val hrv = HeartRateVariabilityRmssdRecord(
            time = t1,
            zoneOffset = offset,
            heartRateVariabilityMillis = 42.0,
            metadata = meta
        )

        val mockHrvResp = mockk<ReadRecordsResponse<HeartRateVariabilityRmssdRecord>>(relaxed = true)
        coEvery { mockHrvResp.records } returns listOf(hrv)
        coEvery { mockHrvResp.pageToken } returns null
        coEvery {
            mockClient.readRecords(match<ReadRecordsRequest<HeartRateVariabilityRmssdRecord>> {
                it.recordType == HeartRateVariabilityRmssdRecord::class
            })
        } returns mockHrvResp

        coEvery {
            mockClient.readRecords(match<ReadRecordsRequest<RestingHeartRateRecord>> {
                it.recordType == RestingHeartRateRecord::class
            })
        } throws SecurityException("Missing READ_RESTING_HEART_RATE")

        val start = LocalDateTime.of(2026, 11, 9, 18, 0).toInstant(offset)
        val end = LocalDateTime.of(2026, 11, 10, 12, 0).toInstant(offset)

        val result = HealthConnectHrvImporter.importHrvRange(
            context,
            start,
            end,
            repository = repository,
            clientOverride = mockClient
        )
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())

        val entry = repository.getPrimaryEntryForDate("2026-11-10", HrvPosture.OVERNIGHT_WEARABLE)
        assertNotNull(entry)
        assertEquals(42.0, entry!!.rmssdMs!!, 1e-3)
        assertEquals(0, entry.restingHrBpm)
    }

    /**
     * TST-PER-039.2: PageToken pagination loop consuming multiple pages.
     */
    @Test
    fun testPageTokenPaginationLoop_multiplePages_aggregatesAllRecords() = runBlocking {
        TrainingApplication.setHealthConnectHrvImportEnabled(true)
        HealthConnectManager.sdkStatusOverride = HealthConnectClient.SDK_AVAILABLE
        coEvery { mockPermissionController.getGrantedPermissions() } returns HealthConnectManager.HRV_READ_PERMISSIONS

        val offset = ZoneOffset.UTC
        val meta1 = createTestMetadata("page1-hrv", "com.ouraring.oura")
        val meta2 = createTestMetadata("page2-hrv", "com.ouraring.oura")

        val t1 = LocalDateTime.of(2026, 11, 10, 2, 0).toInstant(offset)
        val t2 = LocalDateTime.of(2026, 11, 10, 5, 0).toInstant(offset)

        val hrv1 = HeartRateVariabilityRmssdRecord(
            time = t1,
            zoneOffset = offset,
            heartRateVariabilityMillis = 30.0,
            metadata = meta1
        )
        val hrv2 = HeartRateVariabilityRmssdRecord(
            time = t2,
            zoneOffset = offset,
            heartRateVariabilityMillis = 50.0,
            metadata = meta2
        )

        val page1Resp = mockk<ReadRecordsResponse<HeartRateVariabilityRmssdRecord>>(relaxed = true)
        val page2Resp = mockk<ReadRecordsResponse<HeartRateVariabilityRmssdRecord>>(relaxed = true)
        coEvery { page1Resp.records } returns listOf(hrv1)
        coEvery { page1Resp.pageToken } returns "token-page-2"
        coEvery { page2Resp.records } returns listOf(hrv2)
        coEvery { page2Resp.pageToken } returns null

        coEvery {
            mockClient.readRecords(match<ReadRecordsRequest<HeartRateVariabilityRmssdRecord>> {
                it.recordType == HeartRateVariabilityRmssdRecord::class && it.pageToken == null
            })
        } returns page1Resp

        coEvery {
            mockClient.readRecords(match<ReadRecordsRequest<HeartRateVariabilityRmssdRecord>> {
                it.recordType == HeartRateVariabilityRmssdRecord::class && it.pageToken == "token-page-2"
            })
        } returns page2Resp

        val rhrResp = mockk<ReadRecordsResponse<RestingHeartRateRecord>>(relaxed = true)
        coEvery { rhrResp.records } returns emptyList()
        coEvery { rhrResp.pageToken } returns null
        coEvery {
            mockClient.readRecords(match<ReadRecordsRequest<RestingHeartRateRecord>> {
                it.recordType == RestingHeartRateRecord::class
            })
        } returns rhrResp

        val start = LocalDateTime.of(2026, 11, 9, 18, 0).toInstant(offset)
        val end = LocalDateTime.of(2026, 11, 10, 12, 0).toInstant(offset)

        val result = HealthConnectHrvImporter.importHrvRange(
            context,
            start,
            end,
            repository = repository,
            clientOverride = mockClient
        )
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())

        val entry = repository.getPrimaryEntryForDate("2026-11-10", HrvPosture.OVERNIGHT_WEARABLE)
        assertNotNull(entry)
        assertEquals(38.73, entry!!.rmssdMs!!, 0.05)
    }

    /**
     * TST-PER-039.3: In-place nocturnal updates when newer samples or non-zero RHR arrive.
     */
    @Test
    fun testInPlaceNocturnalUpdate_whenNewerSamplesOrNonZeroRhrArrive_updatesExistingRow() = runBlocking {
        TrainingApplication.setHealthConnectHrvImportEnabled(true)
        HealthConnectManager.sdkStatusOverride = HealthConnectClient.SDK_AVAILABLE
        coEvery { mockPermissionController.getGrantedPermissions() } returns HealthConnectManager.HRV_READ_PERMISSIONS

        val offset = ZoneOffset.UTC
        val meta1 = createTestMetadata("midsleep-hrv", "com.ouraring.oura")
        val tEarly = LocalDateTime.of(2026, 11, 10, 3, 0).toInstant(offset)
        val hrvEarly = HeartRateVariabilityRmssdRecord(
            time = tEarly,
            zoneOffset = offset,
            heartRateVariabilityMillis = 30.0,
            metadata = meta1
        )

        val earlyHrvResp = mockk<ReadRecordsResponse<HeartRateVariabilityRmssdRecord>>(relaxed = true)
        coEvery { earlyHrvResp.records } returns listOf(hrvEarly)
        coEvery { earlyHrvResp.pageToken } returns null
        coEvery {
            mockClient.readRecords(match<ReadRecordsRequest<HeartRateVariabilityRmssdRecord>> {
                it.recordType == HeartRateVariabilityRmssdRecord::class
            })
        } returns earlyHrvResp

        val emptyRhrResp = mockk<ReadRecordsResponse<RestingHeartRateRecord>>(relaxed = true)
        coEvery { emptyRhrResp.records } returns emptyList()
        coEvery { emptyRhrResp.pageToken } returns null
        coEvery {
            mockClient.readRecords(match<ReadRecordsRequest<RestingHeartRateRecord>> {
                it.recordType == RestingHeartRateRecord::class
            })
        } returns emptyRhrResp

        val start = LocalDateTime.of(2026, 11, 9, 18, 0).toInstant(offset)
        val end = LocalDateTime.of(2026, 11, 10, 12, 0).toInstant(offset)

        // Mid-sleep import: inserts initial row with restingHrBpm = 0
        val r1 = HealthConnectHrvImporter.importHrvRange(
            context,
            start,
            end,
            repository = repository,
            clientOverride = mockClient
        )
        assertEquals(1, r1.getOrNull())
        val initialEntry = repository.getPrimaryEntryForDate("2026-11-10", HrvPosture.OVERNIGHT_WEARABLE)
        assertNotNull(initialEntry)
        assertEquals(0, initialEntry!!.restingHrBpm)
        assertEquals(30.0, initialEntry.rmssdMs!!, 1e-3)

        // Morning sync arrives: full night records + RHR = 50
        val metaLate = createTestMetadata("morning-hrv", "com.ouraring.oura")
        val tLate = LocalDateTime.of(2026, 11, 10, 7, 0).toInstant(offset)
        val hrvLate = HeartRateVariabilityRmssdRecord(
            time = tLate,
            zoneOffset = offset,
            heartRateVariabilityMillis = 50.0,
            metadata = metaLate
        )

        val lateHrvResp = mockk<ReadRecordsResponse<HeartRateVariabilityRmssdRecord>>(relaxed = true)
        coEvery { lateHrvResp.records } returns listOf(hrvEarly, hrvLate)
        coEvery { lateHrvResp.pageToken } returns null
        coEvery {
            mockClient.readRecords(match<ReadRecordsRequest<HeartRateVariabilityRmssdRecord>> {
                it.recordType == HeartRateVariabilityRmssdRecord::class
            })
        } returns lateHrvResp

        val fullRhr = RestingHeartRateRecord(
            time = tLate,
            zoneOffset = offset,
            beatsPerMinute = 50L,
            metadata = metaLate
        )
        val fullRhrResp = mockk<ReadRecordsResponse<RestingHeartRateRecord>>(relaxed = true)
        coEvery { fullRhrResp.records } returns listOf(fullRhr)
        coEvery { fullRhrResp.pageToken } returns null
        coEvery {
            mockClient.readRecords(match<ReadRecordsRequest<RestingHeartRateRecord>> {
                it.recordType == RestingHeartRateRecord::class
            })
        } returns fullRhrResp

        val r2 = HealthConnectHrvImporter.importHrvRange(
            context,
            start,
            end,
            repository = repository,
            clientOverride = mockClient
        )
        assertEquals(1, r2.getOrNull())

        val allEntries = repository.getAllEntries(HrvPosture.OVERNIGHT_WEARABLE)
        assertEquals(1, allEntries.size)
        val updatedEntry = allEntries.first()
        assertEquals(initialEntry.id, updatedEntry.id)
        assertEquals(50, updatedEntry.restingHrBpm)
        assertEquals(38.73, updatedEntry.rmssdMs!!, 0.05)
        assertEquals(tLate.toEpochMilli(), updatedEntry.timestampMs)
    }

    /**
     * TST-PER-039.3: User-pinned readings are never overwritten.
     */
    @Test
    fun testInPlaceNocturnalUpdate_userPinnedEntry_neverOverwritten() = runBlocking {
        TrainingApplication.setHealthConnectHrvImportEnabled(true)
        HealthConnectManager.sdkStatusOverride = HealthConnectClient.SDK_AVAILABLE
        coEvery { mockPermissionController.getGrantedPermissions() } returns HealthConnectManager.HRV_READ_PERMISSIONS

        val userPinnedEntry = com.atrainingtracker.trainingtracker.database.hrv.HrvReadinessEntry(
            timestampMs = 1731210000000L,
            dateIso = "2026-11-10",
            timezoneId = "UTC",
            restingHrBpm = 48,
            rmssdMs = 65.0,
            lnRmssd = 4.174,
            hrvScore100 = 83.48,
            durationSec = 180,
            posture = HrvPosture.OVERNIGHT_WEARABLE,
            isPrimaryForDay = true,
            notes = "UserManualNote"
        )
        repository.insertEntry(userPinnedEntry)

        val offset = ZoneOffset.UTC
        val meta = createTestMetadata("hc-hrv", "com.ouraring.oura")
        val t = LocalDateTime.of(2026, 11, 10, 6, 0).toInstant(offset)
        val hrv = HeartRateVariabilityRmssdRecord(
            time = t,
            zoneOffset = offset,
            heartRateVariabilityMillis = 40.0,
            metadata = meta
        )
        val mockHrvResp = mockk<ReadRecordsResponse<HeartRateVariabilityRmssdRecord>>(relaxed = true)
        coEvery { mockHrvResp.records } returns listOf(hrv)
        coEvery { mockHrvResp.pageToken } returns null
        coEvery {
            mockClient.readRecords(match<ReadRecordsRequest<HeartRateVariabilityRmssdRecord>> {
                it.recordType == HeartRateVariabilityRmssdRecord::class
            })
        } returns mockHrvResp

        val rhrResp = mockk<ReadRecordsResponse<RestingHeartRateRecord>>(relaxed = true)
        coEvery { rhrResp.records } returns emptyList()
        coEvery { rhrResp.pageToken } returns null
        coEvery {
            mockClient.readRecords(match<ReadRecordsRequest<RestingHeartRateRecord>> {
                it.recordType == RestingHeartRateRecord::class
            })
        } returns rhrResp

        val start = LocalDateTime.of(2026, 11, 9, 18, 0).toInstant(offset)
        val end = LocalDateTime.of(2026, 11, 10, 12, 0).toInstant(offset)

        val result = HealthConnectHrvImporter.importHrvRange(
            context,
            start,
            end,
            repository = repository,
            clientOverride = mockClient
        )
        assertEquals(0, result.getOrNull())

        val entry = repository.getPrimaryEntryForDate("2026-11-10", HrvPosture.OVERNIGHT_WEARABLE)
        assertNotNull(entry)
        assertEquals("UserManualNote", entry!!.notes)
        assertEquals(65.0, entry.rmssdMs!!, 1e-4)
    }
}
