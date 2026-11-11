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
import com.atrainingtracker.trainingtracker.migration.DatabaseConnectionManager
import com.atrainingtracker.trainingtracker.migration.DatabaseSchemaRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * TST-PER-038.3: Contract tests verifying [DatabaseSchemaRegistry] and [DatabaseConnectionManager]
 * registration of `HrvReadiness.db` for `.attbackup` export/restore parity (Rule 62).
 */
@RunWith(RobolectricTestRunner::class)
class DatabaseSchemaRegistryHrvTest {

    @Test
    fun databaseSchemaRegistry_referencesHrvReadinessDbVersionDirectly_Rule62() {
        val supportedVersion = DatabaseSchemaRegistry.getMaxSupportedVersion(HrvReadinessDbHelper.DB_NAME)
        assertNotNull("HrvReadiness.db must be registered in DatabaseSchemaRegistry", supportedVersion)
        assertEquals(
            "DatabaseSchemaRegistry must match HrvReadinessDbHelper.DB_VERSION directly (Rule 62)",
            HrvReadinessDbHelper.DB_VERSION,
            supportedVersion
        )

        assertTrue(DatabaseSchemaRegistry.isVersionSupported(HrvReadinessDbHelper.DB_NAME, 1))
        assertFalse(DatabaseSchemaRegistry.isVersionSupported(HrvReadinessDbHelper.DB_NAME, 2))
    }

    @Test
    fun databaseConnectionManager_includesHrvReadinessDb_inActiveDatabases() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val activeDatabases = DatabaseConnectionManager.getActiveDatabases(context)

        val found = activeDatabases.any { db ->
            val path = db.path
            path != null && File(path).name == HrvReadinessDbHelper.DB_NAME
        }

        assertTrue("DatabaseConnectionManager.getActiveDatabases() must include HrvReadiness.db", found)
    }

    @Test
    fun databaseSchemaRegistry_referencesWorkoutRrDbVersionDirectly_Rule62() {
        val supportedVersion = DatabaseSchemaRegistry.getMaxSupportedVersion(WorkoutRrDbHelper.DB_NAME)
        assertNotNull("WorkoutRr.db must be registered in DatabaseSchemaRegistry", supportedVersion)
        assertEquals(
            "DatabaseSchemaRegistry must match WorkoutRrDbHelper.DB_VERSION directly (Rule 62)",
            WorkoutRrDbHelper.DB_VERSION,
            supportedVersion
        )

        assertTrue(DatabaseSchemaRegistry.isVersionSupported(WorkoutRrDbHelper.DB_NAME, 1))
        assertTrue(DatabaseSchemaRegistry.isVersionSupported(WorkoutRrDbHelper.DB_NAME, 2))
        assertFalse(DatabaseSchemaRegistry.isVersionSupported(WorkoutRrDbHelper.DB_NAME, 3))
    }
}
