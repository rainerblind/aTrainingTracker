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

package com.atrainingtracker.trainingtracker.database

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
 * TST-MAP-068.2: Verifies [DatabaseSchemaRegistry] and [DatabaseConnectionManager]
 * registration of `Climbs.db` for WAL quiescence, connection tracking, and backup parity (REQ-MAP-066.2, ATT-6691).
 */
@RunWith(RobolectricTestRunner::class)
class ClimbsDatabaseRegistryTest {

    @Test
    fun databaseSchemaRegistry_referencesClimbsDbVersionDirectly() {
        val supportedVersion = DatabaseSchemaRegistry.getMaxSupportedVersion(ClimbsDbHelper.DATABASE_NAME)
        assertNotNull("Climbs.db must be registered in DatabaseSchemaRegistry", supportedVersion)
        assertEquals(
            "DatabaseSchemaRegistry must match ClimbsDbHelper.DATABASE_VERSION directly",
            ClimbsDbHelper.DATABASE_VERSION,
            supportedVersion
        )

        assertTrue(DatabaseSchemaRegistry.isVersionSupported(ClimbsDbHelper.DATABASE_NAME, 1))
        assertFalse(DatabaseSchemaRegistry.isVersionSupported(ClimbsDbHelper.DATABASE_NAME, 2))
        assertTrue(DatabaseSchemaRegistry.getSupportedDatabases().contains(ClimbsDbHelper.DATABASE_NAME))
    }

    @Test
    fun databaseConnectionManager_includesClimbsDb_inActiveDatabases() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val activeDatabases = DatabaseConnectionManager.getActiveDatabases(context)

        val found = activeDatabases.any { db ->
            val path = db.path
            path != null && File(path).name == ClimbsDbHelper.DATABASE_NAME
        }

        assertTrue("DatabaseConnectionManager.getActiveDatabases() must include Climbs.db", found)
    }

    @Test
    fun climbsDatabaseManager_exposesValidDatabaseHandle() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = ClimbsDatabaseManager.getInstance(context)

        assertNotNull("ClimbsDatabaseManager.database property must not be null", manager.database)
        assertTrue("ClimbsDatabaseManager.database must be open", manager.database.isOpen)
    }
}
