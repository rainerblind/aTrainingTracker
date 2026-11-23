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

package com.atrainingtracker.trainingtracker.migration

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.database.OpenDatabaseTracker
import com.atrainingtracker.database.TrackingSQLiteOpenHelper
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.ActiveDevicesDbHelper
import com.atrainingtracker.trainingtracker.database.AthleteMetricsDatabaseManager
import com.atrainingtracker.trainingtracker.database.AthleteMetricsDbHelper
import com.atrainingtracker.trainingtracker.database.ClimbsDatabaseManager
import com.atrainingtracker.trainingtracker.database.ClimbsDbHelper
import com.atrainingtracker.trainingtracker.database.ElevationCacheDatabaseManager
import com.atrainingtracker.trainingtracker.database.ElevationCacheDbHelper
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager
import com.atrainingtracker.trainingtracker.database.RoutesDatabaseManager
import com.atrainingtracker.trainingtracker.database.SportTypeEquipmentLinkManager
import com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManager.TrackingViewsDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutClusterDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummariesDbHelper
import com.atrainingtracker.trainingtracker.exporter.db.ExportStatusDatabaseManager
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
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
import java.io.File
import java.lang.reflect.Modifier

/**
 * Behavioral & classpath-reflection contract tests enforcing 100% parity between every concrete
 * production [SQLiteOpenHelper] subclass and [DatabaseSchemaRegistry], plus non-eager WAL
 * connection tracking & reopen-after-quiescence in [DatabaseConnectionManager]
 * (`REQ-MIG-057`, `TST-MIG-057.1`, `TST-MIG-057.2`, `ATT-8122`).
 */
// Linked test specs: TST-MIG-057
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DatabaseSchemaRegistryParityContractTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        setStaticField(TrainingApplication::class.java, "cAppContext", context)
        setStaticField(
            TrainingApplication::class.java,
            "cSharedPreferences",
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        )
        resetAllTrackedState()
    }

    @After
    fun tearDown() {
        resetAllTrackedState()
        setStaticField(TrainingApplication::class.java, "cAppContext", null)
        setStaticField(TrainingApplication::class.java, "cSharedPreferences", null)
    }

    private fun resetAllTrackedState() {
        DatabaseConnectionManager.quiesceAllDatabases(context, closeConnections = true)
        OpenDatabaseTracker.clearForTesting()
        WorkoutSummariesDatabaseManager.setInstanceForTesting(null)
        ExportStatusDatabaseManager.resetForTesting(null)
        resetStaticField(ExportStatusDatabaseManager.ExportStatusDbHelper::class.java, "sInstance")
        ElevationCacheDatabaseManager.resetForTesting(null)
        WorkoutClusterDatabaseManager.resetForTesting(null)
        RoutesDatabaseManager.resetForTesting(null)
        ClimbsDatabaseManager.resetForTesting(null)
        LapsDatabaseManager.resetForTesting(null)
        StravaUploadDbHelper.resetForTesting()
        EquipmentDbHelper.resetForTesting()
        ActiveDevicesDbHelper.resetForTesting()
        resetStaticField(AthleteMetricsDatabaseManager::class.java, "INSTANCE")
        resetStaticField(SportTypeEquipmentLinkManager::class.java, "instance")
        DatabaseSchemaRegistry.getSupportedDatabases().forEach { context.deleteDatabase(it) }
    }

    private fun resetStaticField(clazz: Class<*>, fieldName: String) {
        setStaticField(clazz, fieldName, null)
    }

    private fun setStaticField(clazz: Class<*>, fieldName: String, value: Any?) {
        try {
            val field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(null, value)
        } catch (_: Throwable) {
        }
    }

    private fun getProjectRoot(): File {
        var dir: File? = File(".").canonicalFile
        while (dir != null) {
            if (File(dir, "gradlew").exists() && File(dir, "settings.gradle").exists()) {
                return dir
            }
            dir = dir.parentFile
        }
        throw IllegalStateException("Project root containing gradlew not found")
    }

    private fun discoverProductionSqliteOpenHelpers(): List<Class<out SQLiteOpenHelper>> {
        val root = getProjectRoot()
        val classRoots = listOf(
            File(root, "app/build/tmp/kotlin-classes/debug"),
            File(root, "app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes")
        ).filter { it.isDirectory }

        assertTrue("Expected compiled debug class directories to exist", classRoots.isNotEmpty())

        val discovered = linkedSetOf<Class<out SQLiteOpenHelper>>()
        val classLoader = DatabaseSchemaRegistry::class.java.classLoader!!

        for (classRoot in classRoots) {
            classRoot.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".class") && !it.name.contains("Test") }
                .forEach { classFile ->
                    val relPath = classFile.relativeTo(classRoot).path
                    val className = relPath
                        .removeSuffix(".class")
                        .replace(File.separatorChar, '.')
                    if (!className.startsWith("com.atrainingtracker.")) return@forEach
                    val clazz = try {
                        Class.forName(className, false, classLoader)
                    } catch (_: Throwable) {
                        return@forEach
                    }
                    if (SQLiteOpenHelper::class.java.isAssignableFrom(clazz) &&
                        clazz != SQLiteOpenHelper::class.java &&
                        clazz != TrackingSQLiteOpenHelper::class.java &&
                        !Modifier.isAbstract(clazz.modifiers) &&
                        !Modifier.isInterface(clazz.modifiers)
                    ) {
                        @Suppress("UNCHECKED_CAST")
                        discovered.add(clazz as Class<out SQLiteOpenHelper>)
                    }
                }
        }
        return discovered.sortedBy { it.name }
    }

    @Test
    fun testAllProductionSqliteOpenHelpers_registeredWithExactVersionInDatabaseSchemaRegistry_TST_MIG_057_1() {
        // TST-MIG-057.1
        val helpers = discoverProductionSqliteOpenHelpers()
        assertTrue(
            "Expected at least 20 concrete SQLiteOpenHelper subclasses, found ${helpers.size}: ${helpers.map { it.name }}",
            helpers.size >= 20
        )

        val discoveredDbNames = mutableSetOf<String>()
        for (helperClass in helpers) {
            val ctor = helperClass.getDeclaredConstructor(Context::class.java)
            ctor.isAccessible = true
            val helper = ctor.newInstance(context)
            try {
                val dbName = helper.databaseName
                assertNotNull("Helper ${helperClass.name} must declare a non-null databaseName", dbName)
                assertTrue("Helper ${helperClass.name} must declare a non-blank databaseName", dbName!!.isNotBlank())

                val liveVersion = helper.readableDatabase.version
                assertTrue("Helper ${helperClass.name} ($dbName) must have liveVersion >= 1", liveVersion >= 1)

                val registeredMax = DatabaseSchemaRegistry.getMaxSupportedVersion(dbName)
                assertNotNull(
                    "Database '$dbName' (${helperClass.name}) is missing from DatabaseSchemaRegistry",
                    registeredMax
                )
                assertEquals(
                    "DatabaseSchemaRegistry max version for '$dbName' (${helperClass.name}) drifted from live DB_VERSION",
                    liveVersion,
                    registeredMax
                )
                assertTrue(
                    "DatabaseSchemaRegistry must accept live version $liveVersion for '$dbName'",
                    DatabaseSchemaRegistry.isVersionSupported(dbName, liveVersion)
                )
                assertFalse(
                    "DatabaseSchemaRegistry must reject future version ${liveVersion + 1} for '$dbName'",
                    DatabaseSchemaRegistry.isVersionSupported(dbName, liveVersion + 1)
                )
                assertTrue(
                    "DatabaseSchemaRegistry.getSupportedDatabases() must contain '$dbName'",
                    DatabaseSchemaRegistry.getSupportedDatabases().contains(dbName)
                )
                discoveredDbNames.add(dbName)
            } finally {
                helper.close()
            }
        }

        // Verify obsolete wrong filenames were removed while legacy TrackingViews.db alias is preserved
        assertFalse(
            "Typo filename 'KnownLocations.db' must not remain in DatabaseSchemaRegistry (real file is StartLocation2Altitude.db)",
            DatabaseSchemaRegistry.getSupportedDatabases().contains("KnownLocations.db")
        )
        assertFalse(
            "Typo filename 'Pebble.db' must not remain in DatabaseSchemaRegistry",
            DatabaseSchemaRegistry.getSupportedDatabases().contains("Pebble.db")
        )
        assertFalse(
            "Decommissioned 'PebbleView.db' must not remain in DatabaseSchemaRegistry (ATT-8130)",
            DatabaseSchemaRegistry.getSupportedDatabases().contains("PebbleView.db")
        )
        assertFalse(
            "Decommissioned 'PeriodSummaries.db' must not remain in DatabaseSchemaRegistry (ATT-8131)",
            DatabaseSchemaRegistry.getSupportedDatabases().contains("PeriodSummaries.db")
        )
        assertEquals(
            "Legacy alias 'TrackingViews.db' must track TrackingViewsDbHelper.DB_VERSION",
            TrackingViewsDbHelper.DB_VERSION,
            DatabaseSchemaRegistry.getMaxSupportedVersion("TrackingViews.db")
        )
        assertEquals(
            "Registry entries should equal discovered DBs + 1 legacy TrackingViews.db alias",
            discoveredDbNames + "TrackingViews.db",
            DatabaseSchemaRegistry.getSupportedDatabases()
        )
    }

    @Test
    fun testDatabaseConnectionManager_tracksOnlyOpenedDatabasesWithoutEagerOpening_TST_MIG_057_2() {
        // TST-MIG-057.2
        val unopenedFiles = listOf(
            ClimbsDbHelper.DATABASE_NAME,
            RoutesDatabaseManager.RoutesDbHelper.DB_NAME,
            StravaUploadDbHelper.DB_NAME,
            EquipmentDbHelper.DB_NAME,
            ActiveDevicesDbHelper.DB_NAME,
            LapsDatabaseManager.LapsDbHelper.DB_NAME
        )

        // 1. With no databases opened, getActiveDatabases must return empty and create zero .db files
        assertTrue(
            "getActiveDatabases must return empty when no DB has been opened",
            DatabaseConnectionManager.getActiveDatabases(context).isEmpty()
        )
        for (dbName in unopenedFiles + listOf(
            ElevationCacheDbHelper.DB_NAME,
            AthleteMetricsDbHelper.DB_NAME
        )) {
            assertFalse("Unopened '$dbName' must not be created on disk", context.getDatabasePath(dbName).exists())
        }

        // 2. Open a representative subset across BaseDatabaseManager, direct mDbHelper (ExportStatus), and Kotlin managers
        WorkoutSummariesDatabaseManager.getInstance(context).database
        ExportStatusDatabaseManager.getInstance(context).getExportRows("sample_workout")
        ElevationCacheDatabaseManager.getInstance(context).database
        AthleteMetricsDatabaseManager.getInstance(context).database
        WorkoutClusterDatabaseManager.getInstance(context).getDatabase()
        SportTypeEquipmentLinkManager.getInstance(context).getDatabase()

        val expectedOpen = setOf(
            WorkoutSummariesDbHelper.DB_NAME,
            ExportStatusDatabaseManager.ExportStatusDbHelper.DB_NAME,
            ElevationCacheDbHelper.DB_NAME,
            AthleteMetricsDbHelper.DB_NAME,
            WorkoutClusterDatabaseManager.DB_NAME,
            SportTypeEquipmentLinkManager.DB_NAME
        )
        val activeNames = DatabaseConnectionManager.getActiveDatabases(context)
            .mapNotNull { db -> db.path?.let { File(it).name } }
            .toSet()

        assertEquals(
            "getActiveDatabases must return strictly the opened databases",
            expectedOpen,
            activeNames
        )
        for (unopened in unopenedFiles) {
            assertFalse("Unopened '$unopened' must still not exist on disk", context.getDatabasePath(unopened).exists())
        }

        // 3. Quiesce & close all open handles; active list becomes empty and unopened DBs stay untouched
        DatabaseConnectionManager.quiesceAllDatabases(context, closeConnections = true)
        assertTrue(
            "getActiveDatabases must be empty after quiesceAllDatabases(closeConnections = true)",
            DatabaseConnectionManager.getActiveDatabases(context).isEmpty()
        )
        for (unopened in unopenedFiles) {
            assertFalse("Unopened '$unopened' must not be created by quiesceAllDatabases", context.getDatabasePath(unopened).exists())
        }

        // 4. ATT-289 transparent reopen without resetting singletons
        val reopenedSummaries = WorkoutSummariesDatabaseManager.getInstance(context).database
        val reopenedElevation = ElevationCacheDatabaseManager.getInstance(context).database
        assertTrue("WorkoutSummaries must transparently reopen after quiescence", reopenedSummaries.isOpen)
        assertTrue("ElevationCache must transparently reopen after quiescence", reopenedElevation.isOpen)

        val reopenedNames = DatabaseConnectionManager.getActiveDatabases(context)
            .mapNotNull { db -> db.path?.let { File(it).name } }
            .toSet()
        assertEquals(
            setOf(
                WorkoutSummariesDbHelper.DB_NAME,
                ElevationCacheDbHelper.DB_NAME
            ),
            reopenedNames
        )
    }
}
