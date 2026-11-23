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

package com.atrainingtracker.trainingtracker.migration

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager.LapsDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutClusterDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummariesDbHelper
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Robolectric SQLite test verifying sparse TCX data import (missing HR and Power streams)
 * omits absent sensor flags in `GC_DATA` and leaves absent sample columns `NULL` rather than
 * zero-filling them (REQ-MIG-018, TST-MIG-014).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LegacyImportEngineSparseDataTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        setStaticField(com.atrainingtracker.trainingtracker.TrainingApplication::class.java, "cAppContext", context)
        setStaticField(
            com.atrainingtracker.trainingtracker.TrainingApplication::class.java,
            "cSharedPreferences",
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        )
        cleanupState()
    }

    @After
    fun tearDown() {
        cleanupState()
        setStaticField(com.atrainingtracker.trainingtracker.TrainingApplication::class.java, "cAppContext", null)
        setStaticField(com.atrainingtracker.trainingtracker.TrainingApplication::class.java, "cSharedPreferences", null)
    }

    private fun setStaticField(clazz: Class<*>, fieldName: String, value: Any?) {
        try {
            val field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(null, value)
        } catch (_: Exception) {
        }
    }

    private fun cleanupState() {
        DatabaseConnectionManager.quiesceAllDatabases(context, closeConnections = true)
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        WorkoutRepository.resetForTesting(null)
        WorkoutClusterEngine.resetForTesting(null)
        com.atrainingtracker.trainingtracker.database.WorkoutClusterRepository.resetForTesting(null)
        WorkoutClusterDatabaseManager.resetForTesting(null)
        WorkoutSummariesDatabaseManager.setInstanceForTesting(null)
        com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusRepository.resetForTesting(null)
        com.atrainingtracker.trainingtracker.exporter.db.ExportStatusDatabaseManager.resetForTesting(null)
        com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodsRepository.resetInstanceForTesting()
        com.atrainingtracker.trainingtracker.database.RoutesDatabaseManager.resetForTesting(null)
        listOf(
            WorkoutSummariesDbHelper.DB_NAME,
            WorkoutSamplesDbHelper.DB_NAME,
            LapsDbHelper.DB_NAME,
            "WorkoutCluster.db",
            "RouteClusters.db",
            "ExportStatus.db",
            "PeriodSummaries.db",
            "Routes.db"
        ).forEach { context.deleteDatabase(it) }
    }

    @Test
    fun testSparseTcxImport_missingHrAndPower_doesNotZeroFillOrFlagSensors_TST_MIG_014() = runBlocking {
        val sparseTcx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
              <Activities>
                <Activity Sport="Running">
                  <Id>2026-08-05T07:00:00Z</Id>
                  <Lap StartTime="2026-08-05T07:00:00Z">
                    <TotalTimeSeconds>120.0</TotalTimeSeconds>
                    <DistanceMeters>500.0</DistanceMeters>
                    <Track>
                      <Trackpoint>
                        <Time>2026-08-05T07:00:00Z</Time>
                        <Position><LatitudeDegrees>48.1000</LatitudeDegrees><LongitudeDegrees>11.5000</LongitudeDegrees></Position>
                        <DistanceMeters>0.0</DistanceMeters>
                      </Trackpoint>
                      <Trackpoint>
                        <Time>2026-08-05T07:02:00Z</Time>
                        <Position><LatitudeDegrees>48.1040</LatitudeDegrees><LongitudeDegrees>11.5040</LongitudeDegrees></Position>
                        <DistanceMeters>500.0</DistanceMeters>
                      </Trackpoint>
                    </Track>
                  </Lap>
                </Activity>
              </Activities>
            </TrainingCenterDatabase>
        """.trimIndent()

        val tempDir = java.nio.file.Files.createTempDirectory("sparse_tcx_test").toFile()
        val tcxFile = File(tempDir, "2026-08-05_07-00-00.tcx").apply { writeText(sparseTcx) }
        try {
            val result = LegacyImportEngine.importFromTcxResult(context, tcxFile, listener = null, uploadToStrava = false)
            assertEquals(LegacyImportEngine.ImportStatus.SUCCESS, result.status)

            val summariesDb = WorkoutSummariesDatabaseManager.getInstance(context).database
            summariesDb.query(
                WorkoutSummaries.TABLE,
                arrayOf(WorkoutSummaries.GC_DATA),
                "${WorkoutSummaries.C_ID} = ?",
                arrayOf(result.workoutId.toString()),
                null, null, null
            ).use { cursor ->
                assertTrue("Summary row must exist", cursor.moveToFirst())
                val gcData = cursor.getString(0)
                // GC_DATA format positions: 0=T, 1=D, 2=S, 3=P, 4=H, 5=C, 6=N, 7=A, 8=G
                assertEquals('-', gcData[3]) // Power absent
                assertEquals('-', gcData[4]) // HR absent
                assertEquals('-', gcData[5]) // Cadence absent
                assertEquals('G', gcData[8]) // GPS present
            }

            val samplesDb = WorkoutSamplesDatabaseManager.getInstance(context).database
            val tableName = WorkoutSamplesDatabaseManager.getTableName("2026-08-05_07-00-00")
            samplesDb.query(tableName, null, null, null, null, null, null).use { cursor ->
                assertEquals(2, cursor.count)
                val hrIdx = cursor.getColumnIndex(SensorType.HR.name)
                val powerIdx = cursor.getColumnIndex(SensorType.POWER.name)
                while (cursor.moveToNext()) {
                    if (hrIdx >= 0) {
                        assertTrue("Absent HR must remain NULL (never zero-filled)", cursor.isNull(hrIdx))
                    }
                    if (powerIdx >= 0) {
                        assertTrue("Absent POWER must remain NULL (never zero-filled)", cursor.isNull(powerIdx))
                    }
                    assertFalse("Distance must be populated", cursor.isNull(cursor.getColumnIndexOrThrow(SensorType.DISTANCE_m.name)))
                }
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
