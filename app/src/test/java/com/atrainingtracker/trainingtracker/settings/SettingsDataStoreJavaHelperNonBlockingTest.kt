/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License/
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.settings

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests verifying synchronous, non-blocking $O(1)$ in-memory reads via
 * [SettingsDataStoreJavaHelper] (REQ-SET-079 / TST-SET-079.1).
 */
class SettingsDataStoreJavaHelperNonBlockingTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        val filesDir = tempFolder.newFolder("files")
        every { mockContext.filesDir } returns filesDir
        every { mockContext.applicationContext } returns mockContext

        val testFile = File(filesDir, "test_settings_${System.nanoTime()}.preferences_pb")
        val testDataStore = PreferenceDataStoreFactory.create(produceFile = { testFile })
        val settingsDataStore = SettingsDataStore(mockContext, testDataStore)
        SettingsDataStore.setInstanceForTesting(settingsDataStore)

        val mockTuningDataStore = mockk<TuningPreferencesDataStore>()
        every { mockTuningDataStore.getTuningConfig() } returns TuningConfig()
        TuningPreferencesDataStore.setInstanceForTesting(mockTuningDataStore)
    }

    @After
    fun tearDown() {
        SettingsDataStore.setInstanceForTesting(null)
        TuningPreferencesDataStore.setInstanceForTesting(null)
    }

    @Test
    fun testGetZoneMax_returnsDefaultsSynchronously() {
        // HR_RUN defaults: 132, 145, 158, 171
        assertEquals(132, SettingsDataStoreJavaHelper.getZoneMax(mockContext, SettingsDataStore.ZoneType.HR_RUN, 1))
        assertEquals(145, SettingsDataStoreJavaHelper.getZoneMax(mockContext, SettingsDataStore.ZoneType.HR_RUN, 2))
        assertEquals(158, SettingsDataStoreJavaHelper.getZoneMax(mockContext, SettingsDataStore.ZoneType.HR_RUN, 3))
        assertEquals(171, SettingsDataStoreJavaHelper.getZoneMax(mockContext, SettingsDataStore.ZoneType.HR_RUN, 4))

        // HR_BIKE defaults: 127, 140, 153, 166
        assertEquals(127, SettingsDataStoreJavaHelper.getZoneMax(mockContext, SettingsDataStore.ZoneType.HR_BIKE, 1))
        assertEquals(140, SettingsDataStoreJavaHelper.getZoneMax(mockContext, SettingsDataStore.ZoneType.HR_BIKE, 2))
        assertEquals(153, SettingsDataStoreJavaHelper.getZoneMax(mockContext, SettingsDataStore.ZoneType.HR_BIKE, 3))
        assertEquals(166, SettingsDataStoreJavaHelper.getZoneMax(mockContext, SettingsDataStore.ZoneType.HR_BIKE, 4))

        // PWR_BIKE defaults: 132, 180, 216, 252, 288, 360
        assertEquals(132, SettingsDataStoreJavaHelper.getZoneMax(mockContext, SettingsDataStore.ZoneType.PWR_BIKE, 1))
        assertEquals(180, SettingsDataStoreJavaHelper.getZoneMax(mockContext, SettingsDataStore.ZoneType.PWR_BIKE, 2))
        assertEquals(216, SettingsDataStoreJavaHelper.getZoneMax(mockContext, SettingsDataStore.ZoneType.PWR_BIKE, 3))
        assertEquals(252, SettingsDataStoreJavaHelper.getZoneMax(mockContext, SettingsDataStore.ZoneType.PWR_BIKE, 4))
        assertEquals(288, SettingsDataStoreJavaHelper.getZoneMax(mockContext, SettingsDataStore.ZoneType.PWR_BIKE, 5))
        assertEquals(360, SettingsDataStoreJavaHelper.getZoneMax(mockContext, SettingsDataStore.ZoneType.PWR_BIKE, 6))
    }

    @Test
    fun testGetZoneDisplayOptions_returnsDefaultsSynchronously() {
        val hrRunOpts = SettingsDataStoreJavaHelper.getZoneDisplayOptions(mockContext, SettingsDataStore.ZoneType.HR_RUN)
        assertTrue(hrRunOpts.showBackground)
        assertTrue(hrRunOpts.showLeftBar)

        val hrBikeOpts = SettingsDataStoreJavaHelper.getZoneDisplayOptions(mockContext, SettingsDataStore.ZoneType.HR_BIKE)
        assertTrue(hrBikeOpts.showBackground)
        assertTrue(hrBikeOpts.showLeftBar)

        val pwrBikeOpts = SettingsDataStoreJavaHelper.getZoneDisplayOptions(mockContext, SettingsDataStore.ZoneType.PWR_BIKE)
        assertTrue(pwrBikeOpts.showBackground)
        assertTrue(pwrBikeOpts.showLeftBar)
    }

    @Test
    fun testGetAthleteWeightKg_returnsDefaultSynchronously() {
        val weight = SettingsDataStoreJavaHelper.getAthleteWeightKg(mockContext)
        assertEquals(SettingsDataStore.DEFAULT_ATHLETE_WEIGHT_KG, weight, 0.001f)
    }

    @Test
    fun testGetFtp_returnsNullByDefaultSynchronously() {
        val ftp = SettingsDataStoreJavaHelper.getFtp(mockContext)
        assertNull(ftp)
    }

    @Test
    fun testGetCpWatts_returnsNullByDefaultSynchronously() {
        val cp = SettingsDataStoreJavaHelper.getCpWatts(mockContext)
        assertNull(cp)
    }

    @Test
    fun testGetWPrimeKj_returnsNullByDefaultSynchronously() {
        val wPrime = SettingsDataStoreJavaHelper.getWPrimeKj(mockContext)
        assertNull(wPrime)
    }

    @Test
    fun testGetTuningConfig_returnsDefaultsSynchronously() {
        val config = SettingsDataStoreJavaHelper.getTuningConfig(mockContext)
        assertNotNull(config)
        assertEquals(TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC, config.navigationCueDismissDurationSec)
    }
}
