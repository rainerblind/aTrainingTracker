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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.trainingtracker.smartwatch.pebble

import android.app.Application
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrainingApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Real-SQLite Robolectric tests for [PebbleDatabaseManager] verifying clean connection
 * closure via [PebbleDatabaseManager.close] / [PebbleDatabaseManager.closeIfInitialized]
 * and transparent reopen for ConfigPebbleView* callers (REQ-STB-024.3, TST-STB-024.3).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PebbleDatabaseManagerTest {

    private lateinit var context: Application

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        TrainingApplication.setAppContextForTesting(context)
        TrainingApplication.setSharedPreferencesForTesting(
            PreferenceManager.getDefaultSharedPreferences(context)
        )
        PebbleDatabaseManager.resetInstanceForTesting()
        context.deleteDatabase(PebbleDatabaseManager.PebbleDbHelper.DB_NAME)
    }

    @After
    fun tearDown() {
        PebbleDatabaseManager.resetInstanceForTesting()
        context.deleteDatabase(PebbleDatabaseManager.PebbleDbHelper.DB_NAME)
        TrainingApplication.setAppContextForTesting(null)
        TrainingApplication.setSharedPreferencesForTesting(null)
    }

    /**
     * TST-STB-024.3: closeIfInitialized() does not initialize PebbleView.db when unused.
     */
    @Test
    fun closeIfInitialized_whenNeverInitialized_remainsUninitializedWithoutCreatingDb() {
        assertFalse(PebbleDatabaseManager.isInitialized())
        PebbleDatabaseManager.closeIfInitialized()
        assertFalse(PebbleDatabaseManager.isInitialized())
        assertFalse(context.getDatabasePath(PebbleDatabaseManager.PebbleDbHelper.DB_NAME).exists())
    }

    /**
     * TST-STB-024.3: Closing PebbleDatabaseManager closes PebbleView.db cleanly and
     * subsequent ConfigPebbleView* reads transparently reopen without IllegalStateException.
     */
    @Test
    fun closeAndReopen_closesOpenHandle_andTransparentlyReopensOnSubsequentQueries() {
        val manager = PebbleDatabaseManager.getInstance(context)
        val viewId = manager.getFirstViewId(ActivityType.GENERIC_HR)
        assertTrue("Default GENERIC_HR Pebble view must exist", viewId > 0)
        assertTrue(manager.isDatabaseOpen())

        val initialSensors = manager.getSensorTypeList(viewId)
        assertEquals(listOf(SensorType.TIME_ACTIVE, SensorType.HR, SensorType.SPEED_mps), initialSensors)

        // Close cleanly
        manager.close()
        assertFalse("Database handle must be closed after close()", manager.isDatabaseOpen())

        // Subsequent ConfigPebbleView* queries transparently reopen PebbleView.db
        val reopenedName = manager.getName(viewId)
        assertNotNull(reopenedName)
        assertTrue("Database must reopen transparently on subsequent query", manager.isDatabaseOpen())
        assertEquals(initialSensors, manager.getSensorTypeList(viewId))

        // Static closeIfInitialized() also closes cleanly
        PebbleDatabaseManager.closeIfInitialized()
        assertFalse("Database handle must be closed after closeIfInitialized()", manager.isDatabaseOpen())
    }
}
