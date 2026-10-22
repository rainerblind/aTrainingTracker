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

package com.atrainingtracker.trainingtracker.zones

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore.Zone
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore.ZoneType
import com.atrainingtracker.trainingtracker.settings.SettingsSnapshot
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit tests for DataStore baselines, ACID batch transactions, and customization state transitions.
 *
 * Covers [TST-UI-336.4].
 */
class SettingsDataStoreBaselinesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createTestDataStore(): Pair<DataStore<Preferences>, SettingsDataStore> {
        val testFile = tempFolder.newFile("test_baselines_${System.nanoTime()}.preferences_pb")
        val testDataStore = PreferenceDataStoreFactory.create(
            produceFile = { testFile }
        )

        val context = mockk<Context>(relaxed = true)
        val settingsDataStore = SettingsDataStore(context, testDataStore)
        return Pair(testDataStore, settingsDataStore)
    }

    @Test
    fun saveCalculatedPowerZones_atomicBatchCommit_updatesAllFields() = runBlocking {
        val (prefsDataStore, dataStore) = createTestDataStore()

        val ftp = 250
        val cutoffs = listOf(138, 188, 225, 263, 300, 375)

        val beforeTime = System.currentTimeMillis()
        dataStore.saveCalculatedPowerZones(ftp, cutoffs)
        val afterTime = System.currentTimeMillis()

        // Verify baseline and version
        assertEquals(250, dataStore.ftpFlow.first())
        val updatedAt = dataStore.ftpUpdatedAtFlow.first()
        assertNotNull(updatedAt)
        assertTrue(updatedAt!! in beforeTime..afterTime)
        assertEquals(SettingsDataStore.CURRENT_ZONE_CALC_VERSION, dataStore.zoneCalcVersionFlow.first())

        // Verify customization flag is false
        assertFalse(dataStore.isZoneCustomizedFlow(ZoneType.PWR_BIKE).first())

        // Verify cutoffs
        assertEquals(138, dataStore.getZone1MaxFlow(ZoneType.PWR_BIKE).first())
        assertEquals(188, dataStore.getZone2MaxFlow(ZoneType.PWR_BIKE).first())
        assertEquals(225, dataStore.getZone3MaxFlow(ZoneType.PWR_BIKE).first())
        assertEquals(263, dataStore.getZone4MaxFlow(ZoneType.PWR_BIKE).first())
        assertEquals(300, dataStore.getZone5MaxFlow(ZoneType.PWR_BIKE).first())
        assertEquals(375, dataStore.getZone6MaxFlow(ZoneType.PWR_BIKE).first())
    }

    @Test
    fun saveCalculatedHeartRateZones_atomicBatchCommit_updatesAllFields() = runBlocking {
        val (_, dataStore) = createTestDataStore()

        val hrMax = 190
        val hrRest = 50
        val method = "KARVONEN"
        val cutoffs = listOf(133, 147, 161, 175)

        dataStore.saveCalculatedHeartRateZones(ZoneType.HR_BIKE, hrMax, hrRest, method, cutoffs)

        assertEquals(190, dataStore.hrMaxFlow.first())
        assertEquals(50, dataStore.hrRestFlow.first())
        assertEquals("KARVONEN", dataStore.hrCalcMethodFlow.first())
        assertFalse(dataStore.isZoneCustomizedFlow(ZoneType.HR_BIKE).first())

        assertEquals(133, dataStore.getZone1MaxFlow(ZoneType.HR_BIKE).first())
        assertEquals(147, dataStore.getZone2MaxFlow(ZoneType.HR_BIKE).first())
        assertEquals(161, dataStore.getZone3MaxFlow(ZoneType.HR_BIKE).first())
        assertEquals(175, dataStore.getZone4MaxFlow(ZoneType.HR_BIKE).first())
    }

    @Test
    fun manualCutoffEdit_flipsCustomizedFlagToTrue() = runBlocking {
        val (_, dataStore) = createTestDataStore()

        // First apply calculated zones
        dataStore.saveCalculatedPowerZones(250, listOf(138, 188, 225, 263, 300, 375))
        assertFalse(dataStore.isZoneCustomizedFlow(ZoneType.PWR_BIKE).first())

        // Now edit an individual zone cutoff manually
        dataStore.saveHrZoneMax(ZoneType.PWR_BIKE, Zone.ZONE_3, 230)

        // Custom flag should now be true
        assertTrue(dataStore.isZoneCustomizedFlow(ZoneType.PWR_BIKE).first())
        assertEquals(230, dataStore.getZone3MaxFlow(ZoneType.PWR_BIKE).first())
    }

    @Test
    fun resetToDefaults_resetsCustomizedFlagToFalse() = runBlocking {
        val (_, dataStore) = createTestDataStore()

        dataStore.saveHrZoneMax(ZoneType.PWR_BIKE, Zone.ZONE_2, 210)
        assertTrue(dataStore.isZoneCustomizedFlow(ZoneType.PWR_BIKE).first())

        dataStore.resetToDefaults(ZoneType.PWR_BIKE)
        assertFalse(dataStore.isZoneCustomizedFlow(ZoneType.PWR_BIKE).first())
    }

    @Test
    fun saveCalculatedPowerZones_invalidFtpOrCutoffs_abortsWithoutMutation() = runBlocking {
        val (_, dataStore) = createTestDataStore()

        // Set initial valid calculated state
        dataStore.saveCalculatedPowerZones(200, listOf(110, 150, 180, 210, 240, 300))
        assertEquals(200, dataStore.ftpFlow.first())

        // Attempt to save invalid non-monotonic cutoffs
        try {
            dataStore.saveCalculatedPowerZones(250, listOf(110, 150, 140, 210, 240, 300))
            fail("Expected IllegalArgumentException for non-monotonic cutoffs")
        } catch (e: IllegalArgumentException) {
            // Expected
        }

        // Verify previous state was completely untouched
        assertEquals(200, dataStore.ftpFlow.first())
        assertEquals(180, dataStore.getZone3MaxFlow(ZoneType.PWR_BIKE).first())
    }

    @Test
    fun unconfiguredState_returnsNullBaselines() = runBlocking {
        val (_, dataStore) = createTestDataStore()

        assertNull(dataStore.ftpFlow.first())
        assertNull(dataStore.ftpUpdatedAtFlow.first())
        assertNull(dataStore.hrMaxFlow.first())
        assertNull(dataStore.hrRestFlow.first())
        assertFalse(dataStore.isZoneCustomizedFlow(ZoneType.PWR_BIKE).first())
    }

    @Test
    fun defaultConstantsAndSnapshot_matchCanonicalBaselines() {
        assertEquals(270, SettingsDataStore.DEFAULT_CYCLING_FTP)
        assertEquals(185, SettingsDataStore.DEFAULT_HR_MAX)
        assertEquals(55, SettingsDataStore.DEFAULT_HR_REST)

        val snapshot = SettingsSnapshot.DEFAULT
        assertEquals(listOf(132, 145, 158, 171), snapshot.hrRunZoneMax)
        assertEquals(listOf(127, 140, 153, 166), snapshot.hrBikeZoneMax)
        assertEquals(listOf(149, 203, 243, 284, 324, 405), snapshot.pwrBikeZoneMax)
    }

    @Test
    fun getDefaultValueAndResetToDefaults_matchCanonicalBaselines() = runBlocking {
        val (_, dataStore) = createTestDataStore()

        // Verify getDefaultValue outputs
        val hrRunExpected = listOf(132, 145, 158, 171)
        val hrRunActual = listOf(
            dataStore.getDefaultValue(ZoneType.HR_RUN, Zone.ZONE_1),
            dataStore.getDefaultValue(ZoneType.HR_RUN, Zone.ZONE_2),
            dataStore.getDefaultValue(ZoneType.HR_RUN, Zone.ZONE_3),
            dataStore.getDefaultValue(ZoneType.HR_RUN, Zone.ZONE_4)
        )
        assertEquals(hrRunExpected, hrRunActual)

        val hrBikeExpected = listOf(127, 140, 153, 166)
        val hrBikeActual = listOf(
            dataStore.getDefaultValue(ZoneType.HR_BIKE, Zone.ZONE_1),
            dataStore.getDefaultValue(ZoneType.HR_BIKE, Zone.ZONE_2),
            dataStore.getDefaultValue(ZoneType.HR_BIKE, Zone.ZONE_3),
            dataStore.getDefaultValue(ZoneType.HR_BIKE, Zone.ZONE_4)
        )
        assertEquals(hrBikeExpected, hrBikeActual)

        val pwrBikeExpected = listOf(149, 203, 243, 284, 324, 405)
        val pwrBikeActual = listOf(
            dataStore.getDefaultValue(ZoneType.PWR_BIKE, Zone.ZONE_1),
            dataStore.getDefaultValue(ZoneType.PWR_BIKE, Zone.ZONE_2),
            dataStore.getDefaultValue(ZoneType.PWR_BIKE, Zone.ZONE_3),
            dataStore.getDefaultValue(ZoneType.PWR_BIKE, Zone.ZONE_4),
            dataStore.getDefaultValue(ZoneType.PWR_BIKE, Zone.ZONE_5),
            dataStore.getDefaultValue(ZoneType.PWR_BIKE, Zone.ZONE_6)
        )
        assertEquals(pwrBikeExpected, pwrBikeActual)

        // Mutate each profile, then resetToDefaults and verify reset values
        dataStore.saveHrZoneMax(ZoneType.HR_RUN, Zone.ZONE_1, 140)
        dataStore.resetToDefaults(ZoneType.HR_RUN)
        assertFalse(dataStore.isZoneCustomizedFlow(ZoneType.HR_RUN).first())
        assertEquals(132, dataStore.getZone1MaxFlow(ZoneType.HR_RUN).first())
        assertEquals(145, dataStore.getZone2MaxFlow(ZoneType.HR_RUN).first())
        assertEquals(158, dataStore.getZone3MaxFlow(ZoneType.HR_RUN).first())
        assertEquals(171, dataStore.getZone4MaxFlow(ZoneType.HR_RUN).first())

        dataStore.saveHrZoneMax(ZoneType.HR_BIKE, Zone.ZONE_1, 135)
        dataStore.resetToDefaults(ZoneType.HR_BIKE)
        assertFalse(dataStore.isZoneCustomizedFlow(ZoneType.HR_BIKE).first())
        assertEquals(127, dataStore.getZone1MaxFlow(ZoneType.HR_BIKE).first())
        assertEquals(140, dataStore.getZone2MaxFlow(ZoneType.HR_BIKE).first())
        assertEquals(153, dataStore.getZone3MaxFlow(ZoneType.HR_BIKE).first())
        assertEquals(166, dataStore.getZone4MaxFlow(ZoneType.HR_BIKE).first())

        dataStore.saveHrZoneMax(ZoneType.PWR_BIKE, Zone.ZONE_1, 140)
        dataStore.resetToDefaults(ZoneType.PWR_BIKE)
        assertFalse(dataStore.isZoneCustomizedFlow(ZoneType.PWR_BIKE).first())
        assertEquals(149, dataStore.getZone1MaxFlow(ZoneType.PWR_BIKE).first())
        assertEquals(203, dataStore.getZone2MaxFlow(ZoneType.PWR_BIKE).first())
        assertEquals(243, dataStore.getZone3MaxFlow(ZoneType.PWR_BIKE).first())
        assertEquals(284, dataStore.getZone4MaxFlow(ZoneType.PWR_BIKE).first())
        assertEquals(324, dataStore.getZone5MaxFlow(ZoneType.PWR_BIKE).first())
        assertEquals(405, dataStore.getZone6MaxFlow(ZoneType.PWR_BIKE).first())
    }
}
