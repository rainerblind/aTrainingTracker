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

package com.atrainingtracker.trainingtracker.database.hrv

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * TST-PER-038.1 / TST-PER-038.2: Real SQLite integration tests for [HrvReadinessDatabaseManager]
 * verifying schema creation, CRUD operations, and transactional daily primary deduplication.
 */
@RunWith(RobolectricTestRunner::class)
class HrvReadinessDatabaseManagerTest {

    private lateinit var context: Context
    private lateinit var manager: HrvReadinessDatabaseManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(HrvReadinessDbHelper.DB_NAME)
        HrvReadinessDatabaseManager.resetForTesting(null)
        manager = HrvReadinessDatabaseManager.getInstance(context)
    }

    @After
    fun tearDown() {
        manager.clearDatabase()
        manager.close()
        context.deleteDatabase(HrvReadinessDbHelper.DB_NAME)
        HrvReadinessDatabaseManager.resetForTesting(null)
    }

    @Test
    fun databaseSchema_createsTableAndIndexesSuccessfully() {
        val db = manager.database
        assertTrue("Database must be open", db.isOpen)

        // Verify table exists
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='${HrvReadinessDbHelper.TABLE_HRV_READINESS}';",
            null
        ).use { cursor ->
            assertTrue("Table must exist in sqlite_master", cursor.moveToFirst())
        }

        // Verify indexes exist
        val indexNames = mutableListOf<String>()
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='${HrvReadinessDbHelper.TABLE_HRV_READINESS}';",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                indexNames.add(cursor.getString(0))
            }
        }
        assertTrue("Date/posture index must exist", indexNames.contains("idx_hrv_readiness_date_posture"))
        assertTrue("Timestamp index must exist", indexNames.contains("idx_hrv_readiness_ts"))
    }

    @Test
    fun insertAndQuery_persistsAllAttributes_correctly() {
        val entry = HrvReadinessEntry(
            timestampMs = 1731222000000L,
            dateIso = "2026-11-10",
            timezoneId = "Europe/Berlin",
            restingHrBpm = 50,
            rmssdMs = 72.5,
            lnRmssd = 4.2835,
            hrvScore100 = 85.67,
            sdnnMs = 88.0,
            artifactRatePct = 1.0,
            durationSec = 180,
            posture = HrvPosture.SUPINE,
            notes = "Resting baseline"
        )

        val id = manager.insertEntry(entry)
        assertTrue("Inserted row id must be positive", id > 0)

        val retrieved = manager.getEntryById(id)
        assertNotNull(retrieved)
        assertEquals(id, retrieved!!.id)
        assertEquals(1731222000000L, retrieved.timestampMs)
        assertEquals("2026-11-10", retrieved.dateIso)
        assertEquals("Europe/Berlin", retrieved.timezoneId)
        assertEquals(50, retrieved.restingHrBpm)
        assertEquals(72.5, retrieved.rmssdMs, 1e-4)
        assertEquals(4.2835, retrieved.lnRmssd, 1e-4)
        assertEquals(85.67, retrieved.hrvScore100, 1e-4)
        assertEquals(88.0, retrieved.sdnnMs!!, 1e-4)
        assertEquals(1.0, retrieved.artifactRatePct!!, 1e-4)
        assertEquals(180, retrieved.durationSec)
        assertEquals(HrvPosture.SUPINE, retrieved.posture)
        assertTrue("First valid reading on day must become primary", retrieved.isPrimaryForDay)
        assertEquals("Resting baseline", retrieved.notes)
    }

    @Test
    fun multipleReadingsOnSameDay_earliestBecomesPrimary_subsequentBecomesSecondary() {
        val morningEntry = HrvReadinessEntry(
            timestampMs = 1731222000000L, // 07:00
            dateIso = "2026-11-10",
            timezoneId = "Europe/Berlin",
            restingHrBpm = 49,
            rmssdMs = 75.0,
            lnRmssd = 4.317,
            hrvScore100 = 86.34,
            durationSec = 180,
            posture = HrvPosture.SUPINE
        )
        val eveningEntry = HrvReadinessEntry(
            timestampMs = 1731265200000L, // 19:00
            dateIso = "2026-11-10",
            timezoneId = "Europe/Berlin",
            restingHrBpm = 55,
            rmssdMs = 60.0,
            lnRmssd = 4.094,
            hrvScore100 = 81.88,
            durationSec = 180,
            posture = HrvPosture.SUPINE
        )

        val morningId = manager.insertEntry(morningEntry)
        val eveningId = manager.insertEntry(eveningEntry)

        val morningRetrieved = manager.getEntryById(morningId)!!
        val eveningRetrieved = manager.getEntryById(eveningId)!!

        assertTrue("Morning reading must be primary", morningRetrieved.isPrimaryForDay)
        assertFalse("Evening reading must be secondary", eveningRetrieved.isPrimaryForDay)

        val primaryForDay = manager.getPrimaryEntryForDate("2026-11-10", HrvPosture.SUPINE)
        assertNotNull(primaryForDay)
        assertEquals(morningId, primaryForDay!!.id)
    }

    @Test
    fun manualPinPromotion_swapsPrimaryFlagsTransactionally() {
        val id1 = manager.insertEntry(
            HrvReadinessEntry(
                timestampMs = 1731222000000L,
                dateIso = "2026-11-10",
                timezoneId = "UTC",
                restingHrBpm = 52,
                rmssdMs = 65.0,
                lnRmssd = 4.174,
                hrvScore100 = 83.48,
                durationSec = 180,
                posture = HrvPosture.SUPINE
            )
        )
        val id2 = manager.insertEntry(
            HrvReadinessEntry(
                timestampMs = 1731230000000L,
                dateIso = "2026-11-10",
                timezoneId = "UTC",
                restingHrBpm = 50,
                rmssdMs = 70.0,
                lnRmssd = 4.248,
                hrvScore100 = 84.96,
                durationSec = 180,
                posture = HrvPosture.SUPINE
            )
        )

        // Initially id1 is primary
        assertTrue(manager.getEntryById(id1)!!.isPrimaryForDay)
        assertFalse(manager.getEntryById(id2)!!.isPrimaryForDay)

        // Pin id2
        val pinSuccess = manager.setPrimaryReading(id2, true)
        assertTrue(pinSuccess)

        assertFalse("id1 must now be secondary", manager.getEntryById(id1)!!.isPrimaryForDay)
        assertTrue("id2 must now be primary", manager.getEntryById(id2)!!.isPrimaryForDay)

        assertEquals(id2, manager.getPrimaryEntryForDate("2026-11-10", HrvPosture.SUPINE)!!.id)

        // Unpin id2: recomputes earliest valid reading (id1) as primary
        manager.setPrimaryReading(id2, false)
        assertTrue("id1 must be promoted back to primary", manager.getEntryById(id1)!!.isPrimaryForDay)
        assertFalse("id2 must now be secondary", manager.getEntryById(id2)!!.isPrimaryForDay)
    }

    @Test
    fun deletePrimaryEntry_automaticallyPromotesNextEarliestValidReading() {
        val id1 = manager.insertEntry(
            HrvReadinessEntry(
                timestampMs = 1731222000000L,
                dateIso = "2026-11-10",
                timezoneId = "UTC",
                restingHrBpm = 52,
                rmssdMs = 65.0,
                lnRmssd = 4.174,
                hrvScore100 = 83.48,
                durationSec = 180,
                posture = HrvPosture.SUPINE
            )
        )
        val id2 = manager.insertEntry(
            HrvReadinessEntry(
                timestampMs = 1731230000000L,
                dateIso = "2026-11-10",
                timezoneId = "UTC",
                restingHrBpm = 50,
                rmssdMs = 70.0,
                lnRmssd = 4.248,
                hrvScore100 = 84.96,
                durationSec = 180,
                posture = HrvPosture.SUPINE
            )
        )

        assertTrue(manager.getEntryById(id1)!!.isPrimaryForDay)

        // Delete id1
        val deleteSuccess = manager.deleteEntry(id1)
        assertTrue(deleteSuccess)
        assertNull(manager.getEntryById(id1))

        // id2 should now be primary
        val remaining = manager.getEntryById(id2)!!
        assertTrue("Next earliest valid reading must be promoted to primary", remaining.isPrimaryForDay)
        assertEquals(id2, manager.getPrimaryEntryForDate("2026-11-10", HrvPosture.SUPINE)!!.id)
    }

    @Test
    fun invalidReading_failsArtifactGate_notPromotedOverValidReading() {
        // High artifact rate (> 5.0%)
        val invalidEarly = manager.insertEntry(
            HrvReadinessEntry(
                timestampMs = 1731220000000L, // 06:30
                dateIso = "2026-11-10",
                timezoneId = "UTC",
                restingHrBpm = 55,
                rmssdMs = 40.0,
                lnRmssd = 3.68,
                hrvScore100 = 73.6,
                artifactRatePct = 7.5, // INVALID
                durationSec = 180,
                posture = HrvPosture.SUPINE
            )
        )

        // Valid later reading
        val validLater = manager.insertEntry(
            HrvReadinessEntry(
                timestampMs = 1731225000000L, // 07:50
                dateIso = "2026-11-10",
                timezoneId = "UTC",
                restingHrBpm = 50,
                rmssdMs = 70.0,
                lnRmssd = 4.248,
                hrvScore100 = 84.96,
                artifactRatePct = 1.0, // VALID
                durationSec = 180,
                posture = HrvPosture.SUPINE
            )
        )

        val primary = manager.getPrimaryEntryForDate("2026-11-10", HrvPosture.SUPINE)
        assertNotNull(primary)
        assertEquals("Valid later reading must be selected as primary over invalid earlier reading", validLater, primary!!.id)
    }

    @Test
    fun postureIsolation_preservesIndependentPrimaryFlags() {
        val supineId = manager.insertEntry(
            HrvReadinessEntry(
                timestampMs = 1731222000000L,
                dateIso = "2026-11-10",
                timezoneId = "UTC",
                restingHrBpm = 48,
                rmssdMs = 80.0,
                lnRmssd = 4.382,
                hrvScore100 = 87.64,
                durationSec = 180,
                posture = HrvPosture.SUPINE
            )
        )
        val seatedId = manager.insertEntry(
            HrvReadinessEntry(
                timestampMs = 1731223000000L,
                dateIso = "2026-11-10",
                timezoneId = "UTC",
                restingHrBpm = 56,
                rmssdMs = 50.0,
                lnRmssd = 3.912,
                hrvScore100 = 78.24,
                durationSec = 180,
                posture = HrvPosture.SEATED
            )
        )

        val supinePrimary = manager.getPrimaryEntryForDate("2026-11-10", HrvPosture.SUPINE)
        val seatedPrimary = manager.getPrimaryEntryForDate("2026-11-10", HrvPosture.SEATED)

        assertNotNull(supinePrimary)
        assertNotNull(seatedPrimary)
        assertEquals(supineId, supinePrimary!!.id)
        assertEquals(seatedId, seatedPrimary!!.id)
        assertTrue(supinePrimary.isPrimaryForDay)
        assertTrue(seatedPrimary.isPrimaryForDay)
    }

    @Test
    fun pinningHighArtifactEntry_isRejected_andPreservesValidPrimary() {
        // 1. Insert an earlier valid reading (1.0% artifacts)
        val validId = manager.insertEntry(
            HrvReadinessEntry(
                timestampMs = 1731222000000L,
                dateIso = "2026-11-10",
                timezoneId = "UTC",
                restingHrBpm = 50,
                rmssdMs = 60.0,
                lnRmssd = 4.094,
                hrvScore100 = 81.88,
                artifactRatePct = 1.0,
                durationSec = 60,
                posture = HrvPosture.SUPINE
            )
        )
        assertTrue(manager.getEntryById(validId)!!.isPrimaryForDay)

        // 2. Insert a later noisy reading (8.5% artifacts)
        val noisyId = manager.insertEntry(
            HrvReadinessEntry(
                timestampMs = 1731230000000L,
                dateIso = "2026-11-10",
                timezoneId = "UTC",
                restingHrBpm = 52,
                rmssdMs = 65.0,
                lnRmssd = 4.174,
                hrvScore100 = 83.48,
                artifactRatePct = 8.5,
                durationSec = 60,
                posture = HrvPosture.SUPINE
            )
        )
        assertFalse(manager.getEntryById(noisyId)!!.isPrimaryForDay)

        // 3. Attempt to pin the noisy reading
        val pinResult = manager.setPrimaryReading(noisyId, true)
        assertFalse("Pinning entry with artifact rate > 5.0% must be rejected", pinResult)

        // 4. Verify valid reading remains primary and noisy reading remains secondary
        assertTrue("Valid reading must remain primary", manager.getEntryById(validId)!!.isPrimaryForDay)
        assertFalse("Noisy reading must not become primary", manager.getEntryById(noisyId)!!.isPrimaryForDay)
        assertEquals(validId, manager.getPrimaryEntryForDate("2026-11-10", HrvPosture.SUPINE)!!.id)
    }

    @Test
    fun insertingHighArtifactEntryWithPrimaryFlag_doesNotDisplaceValidPrimary() {
        val validId = manager.insertEntry(
            HrvReadinessEntry(
                timestampMs = 1731222000000L,
                dateIso = "2026-11-10",
                timezoneId = "UTC",
                restingHrBpm = 50,
                rmssdMs = 60.0,
                lnRmssd = 4.094,
                hrvScore100 = 81.88,
                artifactRatePct = 2.0,
                durationSec = 60,
                posture = HrvPosture.SUPINE
            )
        )
        assertTrue(manager.getEntryById(validId)!!.isPrimaryForDay)

        // Insert high artifact entry created with isPrimaryForDay = true
        val noisyId = manager.insertEntry(
            HrvReadinessEntry(
                timestampMs = 1731230000000L,
                dateIso = "2026-11-10",
                timezoneId = "UTC",
                restingHrBpm = 55,
                rmssdMs = 70.0,
                lnRmssd = 4.248,
                hrvScore100 = 84.96,
                artifactRatePct = 9.0,
                durationSec = 60,
                posture = HrvPosture.SUPINE,
                isPrimaryForDay = true
            )
        )

        assertTrue("Valid reading must retain primary status", manager.getEntryById(validId)!!.isPrimaryForDay)
        assertFalse("Noisy reading must not displace valid reading as primary", manager.getEntryById(noisyId)!!.isPrimaryForDay)
    }

    @Test
    fun updateEntry_updatesValuesAndMaintainsPrimary() {
        val id = manager.insertEntry(
            HrvReadinessEntry(
                timestampMs = 1731210000000L,
                dateIso = "2026-11-10",
                timezoneId = "UTC",
                restingHrBpm = 0,
                rmssdMs = 45.0,
                lnRmssd = 3.806,
                hrvScore100 = 76.12,
                durationSec = 180,
                posture = HrvPosture.OVERNIGHT_WEARABLE,
                isPrimaryForDay = true,
                notes = "HealthConnect:com.ouraring.oura:1"
            )
        )
        val initial = manager.getEntryById(id)
        assertNotNull(initial)
        assertEquals(0, initial!!.restingHrBpm)

        val updated = initial.copy(
            timestampMs = 1731220000000L,
            restingHrBpm = 52,
            rmssdMs = 50.0,
            lnRmssd = 3.912,
            hrvScore100 = 78.24,
            durationSec = 28800,
            notes = "HealthConnect:com.ouraring.oura:2"
        )
        val success = manager.updateEntry(updated)
        assertTrue(success)

        val retrieved = manager.getEntryById(id)
        assertNotNull(retrieved)
        assertEquals(1731220000000L, retrieved!!.timestampMs)
        assertEquals(52, retrieved.restingHrBpm)
        assertEquals(50.0, retrieved.rmssdMs, 1e-4)
        assertEquals(3.912, retrieved.lnRmssd, 1e-4)
        assertEquals(78.24, retrieved.hrvScore100, 1e-4)
        assertEquals(28800, retrieved.durationSec)
        assertTrue(retrieved.isPrimaryForDay)
        assertEquals("HealthConnect:com.ouraring.oura:2", retrieved.notes)
    }
}
