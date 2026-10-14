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

package com.atrainingtracker.trainingtracker

import android.content.SharedPreferences
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field

/**
 * Unit tests verifying Strava automated synchronization preference accessors and null resilience
 * in [TrainingApplication] (REQ-EXP-019, TST-EXP-016).
 */
class TrainingApplicationStravaSyncTest {

    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    private fun setStaticField(clazz: Class<*>, fieldName: String, value: Any?) {
        val field: Field = clazz.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(null, value)
    }

    @Before
    fun setUp() {
        mockPrefs = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putBoolean(any(), any()) } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockEditor.apply() } just Runs

        setStaticField(TrainingApplication::class.java, "cSharedPreferences", mockPrefs)
    }

    @After
    fun tearDown() {
        setStaticField(TrainingApplication::class.java, "cSharedPreferences", null)
        unmockkAll()
    }

    @Test
    fun testEquipmentSyncPreferences_readAndWrite() {
        every { mockPrefs.getBoolean(TrainingApplication.SP_AUTOMATED_STRAVA_EQUIPMENT_SYNC, true) } returns true
        assertTrue(TrainingApplication.isAutomatedStravaEquipmentSyncEnabled())

        every { mockPrefs.getBoolean(TrainingApplication.SP_AUTOMATED_STRAVA_EQUIPMENT_SYNC, true) } returns false
        assertFalse(TrainingApplication.isAutomatedStravaEquipmentSyncEnabled())

        TrainingApplication.setAutomatedStravaEquipmentSyncEnabled(false)
        verify { mockEditor.putBoolean(TrainingApplication.SP_AUTOMATED_STRAVA_EQUIPMENT_SYNC, false) }

        every { mockPrefs.getString(TrainingApplication.SP_STRAVA_EQUIPMENT_SYNC_INTERVAL_DAYS, "1") } returns "3"
        assertEquals("3", TrainingApplication.getStravaEquipmentSyncIntervalDays())

        TrainingApplication.setStravaEquipmentSyncIntervalDays("7")
        verify { mockEditor.putString(TrainingApplication.SP_STRAVA_EQUIPMENT_SYNC_INTERVAL_DAYS, "7") }
    }

    @Test
    fun testRoutesSyncPreferences_readAndWrite() {
        every { mockPrefs.getBoolean(TrainingApplication.SP_AUTOMATED_STRAVA_ROUTES_SYNC, true) } returns true
        assertTrue(TrainingApplication.isAutomatedStravaRoutesSyncEnabled())

        every { mockPrefs.getBoolean(TrainingApplication.SP_AUTOMATED_STRAVA_ROUTES_SYNC, true) } returns false
        assertFalse(TrainingApplication.isAutomatedStravaRoutesSyncEnabled())

        TrainingApplication.setAutomatedStravaRoutesSyncEnabled(false)
        verify { mockEditor.putBoolean(TrainingApplication.SP_AUTOMATED_STRAVA_ROUTES_SYNC, false) }

        every { mockPrefs.getString(TrainingApplication.SP_STRAVA_ROUTES_SYNC_INTERVAL_DAYS, "1") } returns "2"
        assertEquals("2", TrainingApplication.getStravaRoutesSyncIntervalDays())

        TrainingApplication.setStravaRoutesSyncIntervalDays("5")
        verify { mockEditor.putString(TrainingApplication.SP_STRAVA_ROUTES_SYNC_INTERVAL_DAYS, "5") }
    }

    @Test
    fun testSegmentsSyncPreferences_readAndWrite() {
        every { mockPrefs.getBoolean(TrainingApplication.SP_AUTOMATED_STRAVA_SEGMENTS_SYNC, true) } returns true
        assertTrue(TrainingApplication.isAutomatedStravaSegmentsSyncEnabled())

        every { mockPrefs.getBoolean(TrainingApplication.SP_AUTOMATED_STRAVA_SEGMENTS_SYNC, true) } returns false
        assertFalse(TrainingApplication.isAutomatedStravaSegmentsSyncEnabled())

        TrainingApplication.setAutomatedStravaSegmentsSyncEnabled(false)
        verify { mockEditor.putBoolean(TrainingApplication.SP_AUTOMATED_STRAVA_SEGMENTS_SYNC, false) }

        every { mockPrefs.getString(TrainingApplication.SP_STRAVA_SEGMENTS_SYNC_INTERVAL_DAYS, "1") } returns "7"
        assertEquals("7", TrainingApplication.getStravaSegmentsSyncIntervalDays())

        TrainingApplication.setStravaSegmentsSyncIntervalDays("14")
        verify { mockEditor.putString(TrainingApplication.SP_STRAVA_SEGMENTS_SYNC_INTERVAL_DAYS, "14") }
    }

    @Test
    fun testNullSharedPreferences_returnsSafeDefaultsWithoutCrashing() {
        setStaticField(TrainingApplication::class.java, "cSharedPreferences", null)

        assertTrue(TrainingApplication.isAutomatedStravaEquipmentSyncEnabled())
        assertEquals("1", TrainingApplication.getStravaEquipmentSyncIntervalDays())
        assertTrue(TrainingApplication.isAutomatedStravaRoutesSyncEnabled())
        assertEquals("1", TrainingApplication.getStravaRoutesSyncIntervalDays())
        assertTrue(TrainingApplication.isAutomatedStravaSegmentsSyncEnabled())
        assertEquals("1", TrainingApplication.getStravaSegmentsSyncIntervalDays())

        // Setters should be no-ops without throwing NPE
        TrainingApplication.setAutomatedStravaEquipmentSyncEnabled(false)
        TrainingApplication.setStravaEquipmentSyncIntervalDays("3")
        TrainingApplication.setAutomatedStravaRoutesSyncEnabled(false)
        TrainingApplication.setStravaRoutesSyncIntervalDays("3")
        TrainingApplication.setAutomatedStravaSegmentsSyncEnabled(false)
        TrainingApplication.setStravaSegmentsSyncIntervalDays("3")
    }
}
