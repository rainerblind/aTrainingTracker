/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.batterysaver

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests verifying pure function [calculateZoneIndex] and its backward-compatible overload
 * (REQ-SET-079 / TST-SET-079.2).
 */
class BatterySaverModelPureTest {

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
    }

    @After
    fun tearDown() {
        SettingsDataStore.setInstanceForTesting(null)
    }

    @Test
    fun testCalculateZoneIndex_fourCutoffs() {
        val cutoffs = listOf(135, 155, 175, 185)

        assertEquals(1, calculateZoneIndex(100.0, cutoffs))
        assertEquals(1, calculateZoneIndex(135.0, cutoffs))
        assertEquals(2, calculateZoneIndex(135.1, cutoffs))
        assertEquals(2, calculateZoneIndex(155.0, cutoffs))
        assertEquals(3, calculateZoneIndex(155.1, cutoffs))
        assertEquals(3, calculateZoneIndex(175.0, cutoffs))
        assertEquals(4, calculateZoneIndex(175.1, cutoffs))
        assertEquals(4, calculateZoneIndex(185.0, cutoffs))
        assertEquals(5, calculateZoneIndex(185.1, cutoffs))
        assertEquals(5, calculateZoneIndex(220.0, cutoffs))
    }

    @Test
    fun testCalculateZoneIndex_sixCutoffs() {
        val cutoffs = listOf(150, 200, 250, 300, 350, 450)

        assertEquals(1, calculateZoneIndex(100.0, cutoffs))
        assertEquals(1, calculateZoneIndex(150.0, cutoffs))
        assertEquals(2, calculateZoneIndex(150.1, cutoffs))
        assertEquals(2, calculateZoneIndex(200.0, cutoffs))
        assertEquals(3, calculateZoneIndex(200.1, cutoffs))
        assertEquals(4, calculateZoneIndex(250.1, cutoffs))
        assertEquals(5, calculateZoneIndex(300.1, cutoffs))
        assertEquals(6, calculateZoneIndex(350.1, cutoffs))
        assertEquals(6, calculateZoneIndex(450.0, cutoffs))
        assertEquals(7, calculateZoneIndex(450.1, cutoffs))
        assertEquals(7, calculateZoneIndex(1000.0, cutoffs))
    }

    @Test
    fun testCalculateZoneIndex_emptyCutoffs_returnsZoneOne() {
        assertEquals(1, calculateZoneIndex(120.0, emptyList()))
    }

    @Test
    fun testCalculateZoneIndex_negativeAndZeroValues() {
        val cutoffs = listOf(135, 155, 175, 185)
        assertEquals(1, calculateZoneIndex(0.0, cutoffs))
        assertEquals(1, calculateZoneIndex(-50.0, cutoffs))
    }

    @Test
    fun testBackwardCompatibleOverload_delegatesSynchronously() {
        // HR_RUN defaults: 132, 145, 158, 171
        assertEquals(1, calculateZoneIndex(mockContext, SettingsDataStore.ZoneType.HR_RUN, 120.0))
        assertEquals(2, calculateZoneIndex(mockContext, SettingsDataStore.ZoneType.HR_RUN, 140.0))
        assertEquals(3, calculateZoneIndex(mockContext, SettingsDataStore.ZoneType.HR_RUN, 150.0))
        assertEquals(4, calculateZoneIndex(mockContext, SettingsDataStore.ZoneType.HR_RUN, 165.0))
        assertEquals(5, calculateZoneIndex(mockContext, SettingsDataStore.ZoneType.HR_RUN, 180.0))

        // PWR_BIKE defaults: 149, 203, 243, 284, 324, 405
        assertEquals(1, calculateZoneIndex(mockContext, SettingsDataStore.ZoneType.PWR_BIKE, 100.0))
        assertEquals(7, calculateZoneIndex(mockContext, SettingsDataStore.ZoneType.PWR_BIKE, 500.0))
    }
}
