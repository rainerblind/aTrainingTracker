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

package com.atrainingtracker.trainingtracker.settings

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.SavedStateHandle
import com.atrainingtracker.trainingtracker.activities.ZonesSettingsViewModel
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore.ZoneType
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit test suite for atomic DataStore zone persistence (REQ-UI-428.1, TST-UI-393.1),
 * SavedStateHandle draft preservation (REQ-UI-428.2, TST-UI-393.2), and consolidated
 * profile loading (REQ-UI-428.3, TST-UI-393.3).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsDataStoreZoneAtomicTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var app: Application
    private lateinit var dataStore: SettingsDataStore

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val testFile = tempFolder.newFile("test_atomic_zones_${System.nanoTime()}.preferences_pb")
        val dataStoreScope = kotlinx.coroutines.CoroutineScope(testDispatcher + kotlinx.coroutines.SupervisorJob())
        val rawDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { testFile }
        )
        val context = mockk<Context>(relaxed = true)
        app = mockk<Application>(relaxed = true)
        dataStore = SettingsDataStore(context, rawDataStore)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * TST-UI-393.1: saveZoneProfile atomically writes all threshold cutoffs and display options in single transaction.
     */
    @Test
    fun saveZoneProfile_commitsAllCutoffsAndDisplayOptionsAtomically_pwrBike() = runTest(testDispatcher) {
        val pwrProfile = ZoneProfileState(
            type = ZoneType.PWR_BIKE,
            z1 = 150,
            z2 = 200,
            z3 = 250,
            z4 = 300,
            z5 = 350,
            z6 = 450,
            displayOptions = ZoneDisplayOptions(
                showBackground = false,
                showLeftBar = true,
                showRightBar = true,
                showTextColor = false,
                showGauge = true
            )
        )

        dataStore.saveZoneProfile(pwrProfile)

        val loaded = dataStore.loadZoneProfile(ZoneType.PWR_BIKE)
        assertEquals(ZoneType.PWR_BIKE, loaded.type)
        assertEquals(150, loaded.z1)
        assertEquals(200, loaded.z2)
        assertEquals(250, loaded.z3)
        assertEquals(300, loaded.z4)
        assertEquals(350, loaded.z5)
        assertEquals(450, loaded.z6)
        assertFalse(loaded.displayOptions.showBackground)
        assertTrue(loaded.displayOptions.showLeftBar)
        assertTrue(loaded.displayOptions.showRightBar)
        assertFalse(loaded.displayOptions.showTextColor)
        assertTrue(loaded.displayOptions.showGauge)
    }

    /**
     * TST-UI-393.1: saveZoneProfile atomically writes all 4 threshold cutoffs and display options for HR profiles.
     */
    @Test
    fun saveZoneProfile_commitsAllCutoffsAndDisplayOptionsAtomically_hrRun() = runTest(testDispatcher) {
        val hrProfile = ZoneProfileState(
            type = ZoneType.HR_RUN,
            z1 = 135,
            z2 = 148,
            z3 = 162,
            z4 = 175,
            displayOptions = ZoneDisplayOptions(
                showBackground = true,
                showLeftBar = false,
                showRightBar = true,
                showTextColor = true,
                showGauge = true
            )
        )

        dataStore.saveZoneProfile(hrProfile)

        val loaded = dataStore.loadZoneProfile(ZoneType.HR_RUN)
        assertEquals(ZoneType.HR_RUN, loaded.type)
        assertEquals(135, loaded.z1)
        assertEquals(148, loaded.z2)
        assertEquals(162, loaded.z3)
        assertEquals(175, loaded.z4)
        assertEquals(0, loaded.z5)
        assertEquals(0, loaded.z6)
        assertTrue(loaded.displayOptions.showBackground)
        assertFalse(loaded.displayOptions.showLeftBar)
        assertTrue(loaded.displayOptions.showRightBar)
        assertTrue(loaded.displayOptions.showTextColor)
        assertTrue(loaded.displayOptions.showGauge)
    }

    /**
     * TST-UI-393.3: loadAllZoneProfiles returns HR_RUN, HR_BIKE, PWR_BIKE in order with migrations checked.
     */
    @Test
    fun loadAllZoneProfiles_returnsCanonicalProfilesInStandardOrder() = runTest(testDispatcher) {
        val profiles = dataStore.loadAllZoneProfiles()
        assertEquals(3, profiles.size)
        assertEquals(ZoneType.HR_RUN, profiles[0].type)
        assertEquals(ZoneType.HR_BIKE, profiles[1].type)
        assertEquals(ZoneType.PWR_BIKE, profiles[2].type)

        // Initial default values verify migration and defaults
        assertTrue(profiles[0].z1 > 0)
        assertTrue(profiles[1].z1 > 0)
        assertTrue(profiles[2].z1 > 0)
        assertTrue(profiles[2].z6 > profiles[2].z5)
    }

    /**
     * TST-UI-393.2: Non-monotonic drafts are preserved in SavedStateHandle without writing invalid state to DataStore.
     */
    @Test
    fun viewModel_retainsNonMonotonicDraftInSavedStateHandleWithoutDataStoreWrite() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle()
        val viewModel = ZonesSettingsViewModel(app, savedStateHandle, dataStore, testDispatcher)
        advanceUntilIdle()

        val initialProfile = viewModel.allProfiles.value.first { it.type == ZoneType.HR_RUN }
        val validZ1 = initialProfile.z1

        // User enters a non-monotonic value: z2 is made smaller than z1 (invalid)
        viewModel.updateThreshold(ZoneType.HR_RUN, 2, validZ1 - 10, debounceMs = 0L)
        advanceUntilIdle()

        // In-memory StateFlow and SavedStateHandle retain the draft
        val draftProfile = viewModel.allProfiles.value.first { it.type == ZoneType.HR_RUN }
        assertEquals(validZ1 - 10, draftProfile.z2)
        assertFalse(draftProfile.isMonotonic())
        assertTrue(viewModel.hasDraft(ZoneType.HR_RUN))

        // DataStore was NOT updated with non-monotonic value
        val persisted = dataStore.loadZoneProfile(ZoneType.HR_RUN)
        assertEquals("DataStore must not store non-monotonic cutoffs", initialProfile.z2, persisted.z2)

        // Draft survives ViewModel recreation with the same SavedStateHandle
        val recreatedVm = ZonesSettingsViewModel(app, savedStateHandle, dataStore, testDispatcher)
        advanceUntilIdle()

        val restoredProfile = recreatedVm.allProfiles.value.first { it.type == ZoneType.HR_RUN }
        assertEquals("Draft must be restored across ViewModel recreation", validZ1 - 10, restoredProfile.z2)
    }

    /**
     * TST-UI-393.2: Monotonic draft is persisted to DataStore upon satisfying monotonicity.
     */
    @Test
    fun viewModel_monotonicUpdatePersistsToDataStore() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle()
        val viewModel = ZonesSettingsViewModel(app, savedStateHandle, dataStore, testDispatcher)
        advanceUntilIdle()

        val initial = viewModel.allProfiles.value.first { it.type == ZoneType.HR_RUN }
        val newZ2 = initial.z1 + 5 // strictly monotonic: z1 < newZ2 < z3
        assertTrue(newZ2 < initial.z3)

        viewModel.updateThreshold(ZoneType.HR_RUN, 2, newZ2, debounceMs = 0L)
        advanceUntilIdle()

        val persisted = dataStore.loadZoneProfile(ZoneType.HR_RUN)
        assertEquals("Monotonic draft must be persisted to DataStore", newZ2, persisted.z2)
    }

    /**
     * TST-UI-393.2: reloadProfileFromDataStore discards in-flight draft and reloads cleanly.
     */
    @Test
    fun viewModel_reloadProfileFromDataStore_discardsDraft() = runTest(testDispatcher) {
        val savedStateHandle = SavedStateHandle()
        val viewModel = ZonesSettingsViewModel(app, savedStateHandle, dataStore, testDispatcher)
        advanceUntilIdle()

        val initial = viewModel.allProfiles.value.first { it.type == ZoneType.HR_RUN }
        viewModel.updateThreshold(ZoneType.HR_RUN, 2, 999, debounceMs = 0L)
        advanceUntilIdle()
        assertTrue(viewModel.hasDraft(ZoneType.HR_RUN))

        viewModel.reloadProfileFromDataStore(ZoneType.HR_RUN)
        advanceUntilIdle()

        assertFalse(viewModel.hasDraft(ZoneType.HR_RUN))
        val current = viewModel.allProfiles.value.first { it.type == ZoneType.HR_RUN }
        assertEquals(initial.z2, current.z2)
    }

    /**
     * Verification of ZoneProfileState monotonicity predicate.
     */
    @Test
    fun zoneProfileState_isMonotonicPredicate() {
        val validHr = ZoneProfileState(ZoneType.HR_RUN, 130, 145, 160, 175)
        assertTrue(validHr.isMonotonic())

        val invertedHr = ZoneProfileState(ZoneType.HR_RUN, 145, 130, 160, 175)
        assertFalse(invertedHr.isMonotonic())

        val zeroHr = ZoneProfileState(ZoneType.HR_RUN, 0, 145, 160, 175)
        assertFalse(zeroHr.isMonotonic())

        val validPwr = ZoneProfileState(ZoneType.PWR_BIKE, 100, 150, 200, 250, 300, 400)
        assertTrue(validPwr.isMonotonic())

        val invertedPwr = ZoneProfileState(ZoneType.PWR_BIKE, 100, 150, 200, 250, 400, 300)
        assertFalse(invertedPwr.isMonotonic())
    }
}
