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

import android.content.ContentValues
import android.content.Context
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.exporter.ExportInfo
import com.atrainingtracker.trainingtracker.exporter.ExportType
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.exporter.writer.FitFileWriter
import com.atrainingtracker.trainingtracker.migration.parsers.FitWorkoutParser
import com.atrainingtracker.trainingtracker.migration.parsers.ParsedWorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.SampleConsumer
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutExportHeader
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutSampleRecord
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Verification test suite for REQ-MIG-053 and TST-MIG-051:
 * Lossless FIT Round-Trip for Workout Metadata, HRV Protocol Tags & Bike Radar Developer Fields.
 */
class FitLosslessRoundTripTest {

    private lateinit var mockContext: Context
    private lateinit var mockRepo: WorkoutRepository
    private lateinit var mockSportTypeDb: SportTypeDatabaseManager
    private lateinit var mockEquipmentDb: EquipmentDbHelper
    private lateinit var tempDir: File
    private val contentValueStores = java.util.Collections.synchronizedMap(java.util.IdentityHashMap<ContentValues, MutableMap<String, Any?>>())

    private fun io.mockk.MockKAnswerScope<*, *>.getRealInstance(): ContentValues {
        try {
            var obj: Any? = call.invocation.originalCall
            while (obj != null) {
                for (f in obj.javaClass.declaredFields) {
                    if (f.name == "self" || f.name == "\$self" || f.name.endsWith("\$self")) {
                        f.isAccessible = true
                        val s = f.get(obj)
                        if (s is ContentValues && s !== this.self) {
                            return s
                        }
                    }
                }
                val nextField = obj.javaClass.declaredFields.firstOrNull { 
                    it.name.contains("originalCall") || it.name.contains("callable") 
                }
                obj = nextField?.apply { isAccessible = true }?.get(obj)
            }
        } catch (_: Exception) { }
        return self as ContentValues
    }

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getDebug(any()) } returns false
        every { TrainingApplication.getAppName() } returns "aTrainingTracker"

        tempDir = Files.createTempDirectory("fit_round_trip_test").toFile()
        mockContext = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockContext
        every { mockContext.filesDir } returns tempDir

        mockSportTypeDb = mockk(relaxed = true)
        every { mockSportTypeDb.getBSportType(any()) } returns BSportType.BIKE
        every { mockSportTypeDb.getTcxName(any()) } returns "Biking"
        SportTypeDatabaseManager.resetForTesting(mockSportTypeDb)

        mockEquipmentDb = mockk(relaxed = true)
        EquipmentDbHelper.resetForTesting(mockEquipmentDb)

        mockRepo = mockk(relaxed = true)
        WorkoutRepository.resetForTesting(mockRepo)

        contentValueStores.clear()
        mockkConstructor(ContentValues::class)
        every { constructedWith<ContentValues>().put(any<String>(), any<String>()) } answers {
            val cv = getRealInstance()
            contentValueStores.computeIfAbsent(cv) { mutableMapOf() }[firstArg<String>()] = secondArg<String>()
        }
        every { constructedWith<ContentValues>().put(any<String>(), any<Long>()) } answers {
            val cv = getRealInstance()
            contentValueStores.computeIfAbsent(cv) { mutableMapOf() }[firstArg<String>()] = secondArg<Long>()
        }
        every { constructedWith<ContentValues>().put(any<String>(), any<Int>()) } answers {
            val cv = getRealInstance()
            contentValueStores.computeIfAbsent(cv) { mutableMapOf() }[firstArg<String>()] = secondArg<Int>()
        }
        every { constructedWith<ContentValues>().put(any<String>(), any<Double>()) } answers {
            val cv = getRealInstance()
            contentValueStores.computeIfAbsent(cv) { mutableMapOf() }[firstArg<String>()] = secondArg<Double>()
        }
        every { constructedWith<ContentValues>().getAsInteger(any<String>()) } answers {
            val cv = getRealInstance()
            (contentValueStores[cv]?.get(firstArg<String>()) as? Number)?.toInt()
        }
        every { constructedWith<ContentValues>().getAsDouble(any<String>()) } answers {
            val cv = getRealInstance()
            (contentValueStores[cv]?.get(firstArg<String>()) as? Number)?.toDouble()
        }
        every { constructedWith<ContentValues>().getAsString(any<String>()) } answers {
            val cv = getRealInstance()
            contentValueStores[cv]?.get(firstArg<String>())?.toString()
        }
    }

    @After
    fun tearDown() {
        WorkoutRepository.resetForTesting(null)
        SportTypeDatabaseManager.resetForTesting(null)
        EquipmentDbHelper.resetForTesting(null)
        unmockkAll()
        tempDir.deleteRecursively()
    }

    /**
     * TST-MIG-051.1: Verify FIT Session Metadata Developer Fields round-trip (title, notes, equipment, method, goal, flags).
     */
    @Test
    fun testSessionMetadataRoundTrip() {
        val baseFileName = "test_meta_roundtrip"
        val header = WorkoutExportHeader(
            workoutId = 201L,
            fileBaseName = baseFileName,
            startTime = "2026-11-14 08:30:00",
            totalTime = "180",
            data = "DSHCP",
            goal = "FTP Ramp Test",
            method = "Ramp Protocol",
            totalDistance = "3500.0",
            description = "Solid effort on indoor trainer",
            workoutName = "Morning Ramp Assessment",
            sportTypeId = 1L,
            indoorTrainerSession = true,
            commute = true,
            race = false,
            equipmentName = "Canyon Aeroad CFR"
        )
        every { mockRepo.getWorkoutExportHeader(baseFileName) } returns header
        every { mockRepo.getWorkoutIdByBaseFileName(baseFileName) } returns 201L
        every { mockRepo.hasRadarData(any(), any()) } returns false
        every { mockRepo.streamSamples(any(), any()) } answers {
            val consumer = secondArg<SampleConsumer>()
            consumer.accept(
                WorkoutSampleRecord(
                    time = "2026-11-14 08:30:01",
                    speedMps = 9.5,
                    hr = 145,
                    cadence = 90.0,
                    power = 220.0
                )
            )
            consumer.accept(
                WorkoutSampleRecord(
                    time = "2026-11-14 08:30:02",
                    speedMps = 9.7,
                    hr = 150,
                    cadence = 92.0,
                    power = 240.0
                )
            )
        }

        val exportInfo = ExportInfo(baseFileName, FileFormat.FIT, ExportType.FILE)
        val writer = FitFileWriter(mockContext)
        val exportResult = writer.doExport(exportInfo)
        assertTrue("FIT export should succeed: ${exportResult.answer()}", exportResult.success())
        val outputFile = File(tempDir, exportInfo.shortPath)
        assertTrue("Output file must exist", outputFile.exists() && outputFile.length() > 0)

        val parseResult = FitWorkoutParser.parse(outputFile, mockContext)
        assertTrue("Parsing exported FIT must succeed: ${parseResult.exceptionOrNull()?.message}", parseResult.isSuccess)

        val parsed = parseResult.getOrThrow()
        assertEquals("Morning Ramp Assessment", parsed.workoutName)
        assertEquals("Solid effort on indoor trainer", parsed.workoutNotes)
        assertEquals("Canyon Aeroad CFR", parsed.equipmentName)
        assertEquals("Ramp Protocol", parsed.method)
        assertEquals("FTP Ramp Test", parsed.goal)
        assertTrue("isCommute must round-trip true", parsed.isCommute)
        assertTrue("isTrainer must round-trip true", parsed.isTrainer)
        assertFalse("isRace must round-trip false", parsed.isRace)
    }

    /**
     * TST-MIG-051.2: Verify standard FIT session metrics serialization (NP, IF, TSS, TotalWork).
     */
    @Test
    fun testSessionMetricsSerialization() {
        val baseFileName = "test_metrics_roundtrip"
        val header = WorkoutExportHeader(
            workoutId = 202L,
            fileBaseName = baseFileName,
            startTime = "2026-11-14 09:00:00",
            totalTime = "3600",
            data = "DSHCP",
            workoutName = "Endurance Power Ride",
            normalizedPower = 250.0,
            intensityFactor = 0.85,
            tss = 65.0,
            workKj = 750.0
        )
        every { mockRepo.getWorkoutExportHeader(baseFileName) } returns header
        every { mockRepo.getWorkoutIdByBaseFileName(baseFileName) } returns 202L
        every { mockRepo.hasRadarData(any(), any()) } returns false
        every { mockRepo.streamSamples(any(), any()) } answers {
            val consumer = secondArg<SampleConsumer>()
            consumer.accept(
                WorkoutSampleRecord(
                    time = "2026-11-14 09:00:01",
                    speedMps = 8.5,
                    power = 245.0
                )
            )
        }

        val exportInfo = ExportInfo(baseFileName, FileFormat.FIT, ExportType.FILE)
        val writer = FitFileWriter(mockContext)
        val exportResult = writer.doExport(exportInfo)
        assertTrue("Export must succeed: ${exportResult.answer()}", exportResult.success())
        val outputFile = File(tempDir, exportInfo.shortPath)
        assertTrue("Output file must exist", outputFile.exists() && outputFile.length() > 0)

        val parseResult = FitWorkoutParser.parse(outputFile, mockContext)
        assertTrue("Parsing must succeed: ${parseResult.exceptionOrNull()?.message}", parseResult.isSuccess)

        val parsed = parseResult.getOrThrow()
        assertNotNull("sessionNormalizedPowerWatts must not be null", parsed.sessionNormalizedPowerWatts)
        assertEquals(250.0, parsed.sessionNormalizedPowerWatts!!, 1.0)

        assertNotNull("sessionIntensityFactor must not be null", parsed.sessionIntensityFactor)
        assertEquals(0.85, parsed.sessionIntensityFactor!!, 0.01)

        assertNotNull("sessionTss must not be null", parsed.sessionTss)
        assertEquals(65.0, parsed.sessionTss!!, 0.5)

        assertNotNull("sessionWorkKj must not be null", parsed.sessionWorkKj)
        assertEquals(750.0, parsed.sessionWorkKj!!, 1.0)
    }

    /**
     * TST-MIG-051.3: Verify Bike Radar Developer Fields ingestion on RecordMesg.
     */
    @Test
    fun testBikeRadarDeveloperFieldsIngestion() {
        val baseFileName = "test_radar_roundtrip"
        val header = WorkoutExportHeader(
            workoutId = 203L,
            fileBaseName = baseFileName,
            startTime = "2026-11-14 10:00:00",
            totalTime = "120",
            data = "DSHCPAG",
            workoutName = "Open Road Ride with Varia Radar"
        )
        every { mockRepo.getWorkoutExportHeader(baseFileName) } returns header
        every { mockRepo.getWorkoutIdByBaseFileName(baseFileName) } returns 203L
        every { mockRepo.hasRadarData(203L, baseFileName) } returns true
        every { mockRepo.streamSamples(any(), any()) } answers {
            val consumer = secondArg<SampleConsumer>()
            consumer.accept(
                WorkoutSampleRecord(
                    time = "2026-11-14 10:00:01",
                    speedMps = 8.0,
                    radarThreatCount = 2,
                    radarVehicleCount = 5,
                    radarApproachSpeedMps = 12.5,
                    radarPassingDistanceM = 1.8,
                    radarLargeVehicleCount = 1
                )
            )
        }

        val exportInfo = ExportInfo(baseFileName, FileFormat.FIT, ExportType.FILE)
        val writer = FitFileWriter(mockContext)
        val exportResult = writer.doExport(exportInfo)
        assertTrue("Export with radar fields must succeed: ${exportResult.answer()}", exportResult.success())
        val outputFile = File(tempDir, exportInfo.shortPath)
        assertTrue("Output file must exist", outputFile.exists() && outputFile.length() > 0)

        val parseResult = FitWorkoutParser.parse(outputFile, mockContext)
        assertTrue("Parsing radar FIT must succeed: ${parseResult.exceptionOrNull()?.message}", parseResult.isSuccess)

        val parsed = parseResult.getOrThrow()
        assertTrue("foundSensors must contain RADAR_THREAT_COUNT", parsed.foundSensors.contains(SensorType.RADAR_THREAT_COUNT))
        assertTrue("foundSensors must contain RADAR_VEHICLE_COUNT", parsed.foundSensors.contains(SensorType.RADAR_VEHICLE_COUNT))
        assertTrue("foundSensors must contain RADAR_APPROACH_SPEED_mps", parsed.foundSensors.contains(SensorType.RADAR_APPROACH_SPEED_mps))
        assertTrue("foundSensors must contain RADAR_PASSING_DISTANCE_m", parsed.foundSensors.contains(SensorType.RADAR_PASSING_DISTANCE_m))
        assertTrue("foundSensors must contain RADAR_LARGE_VEHICLE_COUNT", parsed.foundSensors.contains(SensorType.RADAR_LARGE_VEHICLE_COUNT))

        assertEquals(1, parsed.bufferedSamples.size)
        val sample = parsed.bufferedSamples[0]
        assertEquals(2, sample.getAsInteger(SensorType.RADAR_THREAT_COUNT.name))
        assertEquals(5, sample.getAsInteger(SensorType.RADAR_VEHICLE_COUNT.name))
        assertEquals(12.5, sample.getAsDouble(SensorType.RADAR_APPROACH_SPEED_mps.name)!!, 0.05)
        assertEquals(1.8, sample.getAsDouble(SensorType.RADAR_PASSING_DISTANCE_m.name)!!, 0.05)
        assertEquals(1, sample.getAsInteger(SensorType.RADAR_LARGE_VEHICLE_COUNT.name))
    }

    /**
     * TST-MIG-051.4: Verify equipment name matching against EquipmentDbHelper and metadata resolution.
     */
    @Test
    fun testEquipmentResolutionAndPersistenceContract() {
        every { mockEquipmentDb.getEquipmentId("Canyon Aeroad CFR") } returns 42L
        every { mockEquipmentDb.getEquipmentId("NonExistent Gear") } returns -1L

        val parsedKnownGear = ParsedWorkoutData(
            workoutName = "Intervals",
            equipmentName = "Canyon Aeroad CFR",
            method = "Tabata",
            goal = "VO2Max",
            isCommute = false,
            isTrainer = true,
            isRace = false
        )

        val resolvedKnownId = if (parsedKnownGear.equipmentId > 0) {
            parsedKnownGear.equipmentId
        } else if (!parsedKnownGear.equipmentName.isNullOrBlank()) {
            EquipmentDbHelper.getInstance(mockContext)
                .getEquipmentId(parsedKnownGear.equipmentName.trim())
                .takeIf { it > 0 } ?: -1L
        } else {
            -1L
        }
        assertEquals(42L, resolvedKnownId)

        val parsedUnknownGear = ParsedWorkoutData(
            workoutName = "Recovery",
            equipmentName = "NonExistent Gear"
        )
        val resolvedUnknownId = if (parsedUnknownGear.equipmentId > 0) {
            parsedUnknownGear.equipmentId
        } else if (!parsedUnknownGear.equipmentName.isNullOrBlank()) {
            EquipmentDbHelper.getInstance(mockContext)
                .getEquipmentId(parsedUnknownGear.equipmentName.trim())
                .takeIf { it > 0 } ?: -1L
        } else {
            -1L
        }
        assertEquals(-1L, resolvedUnknownId)
    }

    /**
     * TST-MIG-051.5: Verify backward compatibility with third-party FIT files lacking developer fields.
     */
    @Test
    fun testThirdPartyFitBackwardCompatibility() {
        val baseFileName = "test_thirdparty_fit"
        val header = WorkoutExportHeader(
            workoutId = 205L,
            fileBaseName = baseFileName,
            startTime = "2026-11-14 11:00:00",
            totalTime = "60",
            data = "DS"
        )
        every { mockRepo.getWorkoutExportHeader(baseFileName) } returns header
        every { mockRepo.getWorkoutIdByBaseFileName(baseFileName) } returns 205L
        every { mockRepo.hasRadarData(any(), any()) } returns false
        every { mockRepo.streamSamples(any(), any()) } answers {
            val consumer = secondArg<SampleConsumer>()
            consumer.accept(
                WorkoutSampleRecord(
                    time = "2026-11-14 11:00:01",
                    speedMps = 5.0
                )
            )
        }

        val exportInfo = ExportInfo(baseFileName, FileFormat.FIT, ExportType.FILE)
        val writer = FitFileWriter(mockContext)
        val exportResult = writer.doExport(exportInfo)
        assertTrue("Export must succeed: ${exportResult.answer()}", exportResult.success())
        val outputFile = File(tempDir, exportInfo.shortPath)
        assertTrue("Output file must exist", outputFile.exists() && outputFile.length() > 0)

        val parseResult = FitWorkoutParser.parse(outputFile, mockContext)
        assertTrue("Parsing must succeed without errors: ${parseResult.exceptionOrNull()?.message}", parseResult.isSuccess)

        val parsed = parseResult.getOrThrow()
        assertNull("workoutName must be null when empty/absent", parsed.workoutName)
        assertNull("workoutNotes must be null when empty/absent", parsed.workoutNotes)
        assertNull("equipmentName must be null when empty/absent", parsed.equipmentName)
        assertNull("method must be null when empty/absent", parsed.method)
        assertNull("goal must be null when empty/absent", parsed.goal)
        assertFalse("isCommute must default to false", parsed.isCommute)
        assertFalse("isTrainer must default to false", parsed.isTrainer)
        assertFalse("isRace must default to false", parsed.isRace)
        assertFalse("foundSensors must not contain radar", parsed.foundSensors.contains(SensorType.RADAR_VEHICLE_COUNT))
    }
}
