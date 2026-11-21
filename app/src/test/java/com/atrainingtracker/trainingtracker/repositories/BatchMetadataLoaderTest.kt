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

package com.atrainingtracker.trainingtracker.repositories

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.database.AthleteMetricEntry
import com.atrainingtracker.trainingtracker.database.AthleteMetricType
import com.atrainingtracker.trainingtracker.database.AthleteMetricsDatabaseManager
import com.atrainingtracker.trainingtracker.database.CardioPowerDynamicsRecord
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.ExtremaType
import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutClusterDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import com.atrainingtracker.trainingtracker.ui.aftermath.LapData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDataMapper
import com.google.android.gms.maps.model.LatLng
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * Verifies [BatchMetadataLoader] populates all 9 [WorkoutDataMapper.BatchMetadata] maps
 * and matches historical `athleteMetrics` resolution semantics (`REQ-DAT-045.3`, `TST-DAT-041.3`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BatchMetadataLoaderTest {

    private fun snapshot(id: Long, startTimeS: Long, sportId: Long = 1L, equipmentId: Long = 7L, clusterId: Long = 11L) =
        WorkoutDataMapper.RawCursorSnapshot(
            workoutId = id,
            sportId = sportId,
            equipmentId = equipmentId,
            formattedDate = "2026-11-21",
            formattedTime = "06:00",
            startTimeS = startTimeS,
            localDateTime = LocalDateTime.of(2026, 11, 21, 6, 0),
            fileBaseName = "file_$id",
            totalDistance = 10_000.0,
            mapPolyline = "",
            encodedAltitudes = "",
            encodedDistances = "",
            workoutName = "Workout $id",
            clusterId = clusterId,
            finished = true,
            commute = false,
            trainer = false,
            race = false,
            uploadToStrava = 0,
            minLat = null,
            minLng = null,
            maxLat = null,
            maxLng = null,
            activeTimeSec = 1800L,
            totalTimeSec = 1850L,
            avgSpeedMps = 5.5,
            ascentMeters = 100L,
            descentMeters = 100L,
            description = null,
            goal = null,
            method = null
        )

    @Test
    fun testLoadForSnapshots_populatesAllNineBatchMetadataMapsAndAthleteMetricsParity_TST_DAT_041_3() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val summariesDb = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
        val sportDb = mockk<SportTypeDatabaseManager>(relaxed = true)
        val equipDb = mockk<EquipmentDbHelper>(relaxed = true)
        val stravaDb = mockk<StravaUploadDbHelper>(relaxed = true)
        val clusterDb = mockk<WorkoutClusterDatabaseManager>(relaxed = true)
        val lapsDb = mockk<LapsDatabaseManager>(relaxed = true)
        val locationsDb = mockk<KnownLocationsDatabaseManager>(relaxed = true)
        val metricsDb = mockk<AthleteMetricsDatabaseManager>(relaxed = true)

        val homePos = LatLng(48.1370, 11.5760)
        every { summariesDb.getExtremaForWorkouts(listOf(1L, 2L, 3L)) } returns listOf(
            WorkoutSummariesDatabaseManager.ExtremaRecord(1L, SensorType.LATITUDE, ExtremaType.START, 0.0, homePos)
        )
        every { stravaDb.getStravaActivityDataForWorkouts(listOf("file_1", "file_2", "file_3")) } returns mapOf("file_1" to "{}")
        every { clusterDb.getClusterNamesForIds(setOf(11L)) } returns mapOf(11L to "Morning Loop")
        every { lapsDb.getLapsForWorkouts(listOf(1L, 2L, 3L)) } returns mapOf(
            1L to listOf(LapData(1L, 1L, 1L, null, 600, 3000.0, 5.0))
        )
        val cpd = mockk<CardioPowerDynamicsRecord>(relaxed = true)
        every { summariesDb.getCardioPowerDynamicsForWorkouts(listOf(1L, 2L, 3L)) } returns mapOf(1L to cpd)

        every { sportDb.getBSportType(1L) } returns BSportType.BIKE
        every { sportDb.getUIName(1L) } returns "Road Bike"
        every { sportDb.getStravaName(1L) } returns "Ride"
        every { equipDb.getEquipmentItems() } returns listOf(
            EquipmentDbHelper.EquipmentData(7L, "Gravel Bike", BSportType.BIKE, 0, null, null)
        )
        every { locationsDb.allLocations } returns listOf(
            KnownLocationsDatabaseManager.MyLocation(1L, homePos.latitude, homePos.longitude, "Home", 500.0, 250, 1)
        )

        // Ordered DESC by timestampMs, matching AthleteMetricsDatabaseManager.getEntriesByType
        every { metricsDb.getEntriesByType(AthleteMetricType.FTP_WATTS) } returns listOf(
            AthleteMetricEntry(2L, 2_000_000L, AthleteMetricType.FTP_WATTS, 310.0),
            AthleteMetricEntry(1L, 1_000_000L, AthleteMetricType.FTP_WATTS, 275.0)
        )
        every { metricsDb.getEntriesByType(AthleteMetricType.WEIGHT_KG) } returns listOf(
            AthleteMetricEntry(3L, 2_000_000L, AthleteMetricType.WEIGHT_KG, 70.5)
        )

        val loader = BatchMetadataLoader(
            context = context,
            summariesManagerProvider = { summariesDb },
            sportTypeDbProvider = { sportDb },
            equipmentDbProvider = { equipDb },
            stravaUploadDbProvider = { stravaDb },
            clusterDbProvider = { clusterDb },
            lapsDbProvider = { lapsDb },
            knownLocationsDbProvider = { locationsDb },
            athleteMetricsDbProvider = { metricsDb },
            fallbackFtpProvider = { 250.0 },
            fallbackWeightKgProvider = { 75.0 }
        )

        val batch = loader.loadForSnapshots(
            listOf(
                snapshot(1L, startTimeS = 1_500L), // 1_500_000ms -> matches entry 1 (275W) & fallback-to-latest weight (70.5kg)
                snapshot(2L, startTimeS = 500L),  // 500_000ms (< all) -> falls back to latest entry (310W, 70.5kg)
                snapshot(3L, startTimeS = 0L)     // timestamp <= 0 -> falls back to SettingsDataStore defaults (250W, 75.0kg)
            )
        )

        assertEquals(1, batch.extrema[1L]?.size)
        assertEquals("{}", batch.stravaData["file_1"])
        assertEquals("Morning Loop", batch.clusterNames[11L])
        assertEquals(1, batch.laps[1L]?.size)
        assertNotNull(batch.cardioPowerDynamics[1L])
        assertEquals("Road Bike", batch.sportTypes[1L]?.sportName)
        assertEquals("Gravel Bike", batch.equipmentNames[7L])
        assertEquals("Home", batch.locationNames[homePos])

        assertEquals(275.0, batch.athleteMetrics[1L]!!.effectiveFtpWatts, 1e-6)
        assertEquals(70.5, batch.athleteMetrics[1L]!!.effectiveWeightKg, 1e-6)
        assertEquals(310.0, batch.athleteMetrics[2L]!!.effectiveFtpWatts, 1e-6)
        assertEquals(70.5, batch.athleteMetrics[2L]!!.effectiveWeightKg, 1e-6)
        assertEquals(250.0, batch.athleteMetrics[3L]!!.effectiveFtpWatts, 1e-6)
        assertEquals(75.0, batch.athleteMetrics[3L]!!.effectiveWeightKg, 1e-6)

        // Single batch scan invariants
        verify(exactly = 1) { locationsDb.allLocations }
        verify(exactly = 0) { locationsDb.getMyLocation(any<LatLng>()) }
        verify(exactly = 1) { metricsDb.getEntriesByType(AthleteMetricType.FTP_WATTS) }
        verify(exactly = 1) { metricsDb.getEntriesByType(AthleteMetricType.WEIGHT_KG) }
        verify(exactly = 0) { metricsDb.getEntryEffectiveAt(any(), any()) }
    }

    @Test
    fun testLoadForSnapshots_emptyList_returnsEmptyBatchWithoutQueryingDbs_TST_DAT_041_3() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val summariesDb = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
        val sportDb = mockk<SportTypeDatabaseManager>(relaxed = true)
        val equipDb = mockk<EquipmentDbHelper>(relaxed = true)
        val stravaDb = mockk<StravaUploadDbHelper>(relaxed = true)
        val clusterDb = mockk<WorkoutClusterDatabaseManager>(relaxed = true)
        val lapsDb = mockk<LapsDatabaseManager>(relaxed = true)
        val locationsDb = mockk<KnownLocationsDatabaseManager>(relaxed = true)
        val metricsDb = mockk<AthleteMetricsDatabaseManager>(relaxed = true)

        val loader = BatchMetadataLoader(
            context = context,
            summariesManagerProvider = { summariesDb },
            sportTypeDbProvider = { sportDb },
            equipmentDbProvider = { equipDb },
            stravaUploadDbProvider = { stravaDb },
            clusterDbProvider = { clusterDb },
            lapsDbProvider = { lapsDb },
            knownLocationsDbProvider = { locationsDb },
            athleteMetricsDbProvider = { metricsDb }
        )

        val batch = loader.loadForSnapshots(emptyList())

        org.junit.Assert.assertTrue(batch.extrema.isEmpty())
        org.junit.Assert.assertTrue(batch.stravaData.isEmpty())
        org.junit.Assert.assertTrue(batch.clusterNames.isEmpty())
        org.junit.Assert.assertTrue(batch.laps.isEmpty())
        org.junit.Assert.assertFalse(batch.hasEquipmentNames)
        org.junit.Assert.assertFalse(batch.hasLocationNames)
        org.junit.Assert.assertFalse(batch.hasCardioPowerDynamics)

        verify(exactly = 0) { summariesDb.getExtremaForWorkouts(any()) }
        verify(exactly = 0) { stravaDb.getStravaActivityDataForWorkouts(any()) }
        verify(exactly = 0) { clusterDb.getClusterNamesForIds(any()) }
        verify(exactly = 0) { lapsDb.getLapsForWorkouts(any()) }
        verify(exactly = 0) { equipDb.getEquipmentItems() }
        verify(exactly = 0) { locationsDb.allLocations }
        verify(exactly = 0) { metricsDb.getEntriesByType(any()) }
    }

    @Test
    fun testLoadForSingleWorkout_populatesSingleWorkoutBatchMapsAndSetsCardioFlag_TST_DAT_041_3() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val summariesDb = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
        val stravaDb = mockk<StravaUploadDbHelper>(relaxed = true)
        val clusterDb = mockk<WorkoutClusterDatabaseManager>(relaxed = true)
        val lapsDb = mockk<LapsDatabaseManager>(relaxed = true)

        val record = WorkoutSummariesDatabaseManager.ExtremaRecord(42L, SensorType.POWER, ExtremaType.AVG, 240.0, null)
        val lap = LapData(1L, 42L, 1L, null, 300, 1500.0, 5.0)
        val cpd = mockk<CardioPowerDynamicsRecord>(relaxed = true)

        every { summariesDb.getExtremaForWorkouts(listOf(42L)) } returns listOf(record)
        every { stravaDb.getStravaActivityData("file_42") } returns "{\"id\":42}"
        every { clusterDb.getClusterNameById(11L) } returns "Hill Repeats"
        every { lapsDb.getLaps(42L) } returns listOf(lap)
        every { summariesDb.getCardioPowerDynamics(42L) } returns cpd

        val loader = BatchMetadataLoader(
            context = context,
            summariesManagerProvider = { summariesDb },
            stravaUploadDbProvider = { stravaDb },
            clusterDbProvider = { clusterDb },
            lapsDbProvider = { lapsDb }
        )

        val batch = loader.loadForSingleWorkout(snapshot(42L, startTimeS = 1_700_000L, clusterId = 11L))

        assertEquals(listOf(record), batch.extrema[42L])
        assertEquals("{\"id\":42}", batch.stravaData["file_42"])
        assertEquals("Hill Repeats", batch.clusterNames[11L])
        assertEquals(listOf(lap), batch.laps[42L])
        assertEquals(cpd, batch.cardioPowerDynamics[42L])
        org.junit.Assert.assertTrue(batch.hasCardioPowerDynamics)
        org.junit.Assert.assertFalse(batch.hasEquipmentNames)
        org.junit.Assert.assertFalse(batch.hasLocationNames)

        verify(exactly = 1) { summariesDb.getExtremaForWorkouts(listOf(42L)) }
        verify(exactly = 1) { stravaDb.getStravaActivityData("file_42") }
        verify(exactly = 1) { clusterDb.getClusterNameById(11L) }
        verify(exactly = 1) { lapsDb.getLaps(42L) }
        verify(exactly = 1) { summariesDb.getCardioPowerDynamics(42L) }
    }
}
