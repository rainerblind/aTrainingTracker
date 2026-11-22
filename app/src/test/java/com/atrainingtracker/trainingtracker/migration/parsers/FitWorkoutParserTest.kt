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

package com.atrainingtracker.trainingtracker.migration.parsers

import android.content.ContentValues
import android.content.Context
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.exporter.writer.FitFileWriter
import com.garmin.fit.DateTime
import com.garmin.fit.DeveloperDataIdMesg
import com.garmin.fit.DeveloperField
import com.garmin.fit.FieldDescriptionMesg
import com.garmin.fit.FileEncoder
import com.garmin.fit.FileIdMesg
import com.garmin.fit.Fit
import com.garmin.fit.LapMesg
import com.garmin.fit.Manufacturer
import com.garmin.fit.RecordMesg
import com.garmin.fit.SessionMesg
import com.garmin.fit.Sport
import com.garmin.fit.SubSport
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Date

/**
 * Targeted unit tests for [FitWorkoutParser] (REQ-MIG-038, TST-MIG-035, REQ-MIG-058, TST-MIG-058).
 */
// Linked test specs: TST-MIG-035, TST-MIG-058, TST-MIG-058.1
class FitWorkoutParserTest {

    private lateinit var mockContext: Context
    private lateinit var mockSportTypeDb: SportTypeDatabaseManager
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

        mockContext = mockk(relaxed = true)
        mockkStatic(SportTypeDatabaseManager::class)
        every { SportTypeDatabaseManager.getSportTypeId(BSportType.BIKE) } returns 2L

        contentValueStores.clear()
        mockkConstructor(ContentValues::class)
        every { constructedWith<ContentValues>().put(any<String>(), any<String>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores.computeIfAbsent(cv) { mutableMapOf() }
            map[firstArg<String>()] = secondArg<String>()
        }
        every { constructedWith<ContentValues>().put(any<String>(), any<Long>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores.computeIfAbsent(cv) { mutableMapOf() }
            map[firstArg<String>()] = secondArg<Long>()
        }
        every { constructedWith<ContentValues>().put(any<String>(), any<Int>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores.computeIfAbsent(cv) { mutableMapOf() }
            map[firstArg<String>()] = secondArg<Int>()
        }
        every { constructedWith<ContentValues>().put(any<String>(), any<Double>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores.computeIfAbsent(cv) { mutableMapOf() }
            map[firstArg<String>()] = secondArg<Double>()
        }
        every { constructedWith<ContentValues>().getAsLong(any<String>()) } answers {
            val cv = getRealInstance()
            (contentValueStores[cv]?.get(firstArg<String>()) as? Number)?.toLong()
        }
        every { constructedWith<ContentValues>().getAsInteger(any<String>()) } answers {
            val cv = getRealInstance()
            (contentValueStores[cv]?.get(firstArg<String>()) as? Number)?.toInt()
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testParseValidFit() {
        val tempFile = File.createTempFile("test_workout", ".fit")
        try {
            val encoder = FileEncoder(tempFile, Fit.ProtocolVersion.V2_0)

            val fileIdMesg = FileIdMesg().apply {
                type = com.garmin.fit.File.ACTIVITY
                manufacturer = Manufacturer.GARMIN
                product = 1
                serialNumber = 12345L
                timeCreated = DateTime(Date(1700000000000L))
            }
            encoder.write(fileIdMesg)

            val sessionMesg = SessionMesg().apply {
                sport = Sport.CYCLING
                subSport = SubSport.ROAD
                startTime = DateTime(Date(1700000000000L))
                totalElapsedTime = 600f
                totalTimerTime = 600f
                totalDistance = 3000f
            }
            encoder.write(sessionMesg)

            val lapMesg = LapMesg().apply {
                startTime = DateTime(Date(1700000000000L))
                totalElapsedTime = 600f
                totalTimerTime = 600f
                totalDistance = 3000f
                enhancedMaxSpeed = 8.5f
                totalCalories = 120
                avgHeartRate = 140.toShort()
            }
            encoder.write(lapMesg)

            val latSemi1 = (48.137154 * (2147483648.0 / 180.0)).toInt()
            val lngSemi1 = (11.576124 * (2147483648.0 / 180.0)).toInt()
            val record1 = RecordMesg().apply {
                timestamp = DateTime(Date(1700000000000L))
                positionLat = latSemi1
                positionLong = lngSemi1
                altitude = 520f
                distance = 0f
                speed = 5.0f
                heartRate = 135.toShort()
                cadence = 80.toShort()
                power = 200
            }
            encoder.write(record1)

            val latSemi2 = (48.140000 * (2147483648.0 / 180.0)).toInt()
            val lngSemi2 = (11.580000 * (2147483648.0 / 180.0)).toInt()
            val record2 = RecordMesg().apply {
                timestamp = DateTime(Date(1700000600000L))
                positionLat = latSemi2
                positionLong = lngSemi2
                altitude = 530f
                distance = 3000f
                speed = 6.0f
                heartRate = 145.toShort()
                cadence = 85.toShort()
                power = 220
            }
            encoder.write(record2)

            encoder.close()

            val result = FitWorkoutParser.parse(tempFile, mockContext)
            assertTrue("FitWorkoutParser should succeed", result.isSuccess)
            val workout = result.getOrThrow()

            assertEquals(BSportType.BIKE, workout.bSportType)
            assertEquals(2L, workout.resolvedSportId)
            assertEquals(2, workout.points.size)
            assertEquals(2, workout.altitudes.size)
            assertEquals(520.0, workout.minAltVal, 0.001)
            assertEquals(530.0, workout.maxAltVal, 0.001)
            assertEquals(1, workout.parsedLaps.size)
            assertEquals(3000.0, workout.parsedLaps[0].distanceMeters, 0.001)
            assertEquals(120, workout.parsedLaps[0].calories)
            assertEquals(2, workout.bufferedSamples.size)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testResolveLapIndex_handlesSortedDuplicateNullAndUnsortedLaps_TST_MIG_058_1() {
        val sorted = longArrayOf(1000L, 2000L, 3000L)
        assertTrue(FitWorkoutParser.isNonNullSorted(sorted))
        assertEquals(0L, FitWorkoutParser.resolveLapIndex(500L, sorted, true))
        assertEquals(0L, FitWorkoutParser.resolveLapIndex(1000L, sorted, true))
        assertEquals(0L, FitWorkoutParser.resolveLapIndex(1999L, sorted, true))
        assertEquals(1L, FitWorkoutParser.resolveLapIndex(2000L, sorted, true))
        assertEquals(2L, FitWorkoutParser.resolveLapIndex(3000L, sorted, true))
        assertEquals(2L, FitWorkoutParser.resolveLapIndex(9999L, sorted, true))

        // Duplicate equal lap start timestamps must resolve to the rightmost matching index
        val duplicates = longArrayOf(1000L, 2000L, 2000L, 3000L)
        assertTrue(FitWorkoutParser.isNonNullSorted(duplicates))
        assertEquals(2L, FitWorkoutParser.resolveLapIndex(2000L, duplicates, true))
        assertEquals(2L, FitWorkoutParser.resolveLapIndex(2500L, duplicates, true))

        // Null lap startTime sentinel (Long.MIN_VALUE) must never be matched as <= recordEpoch
        val withNullLap = longArrayOf(1000L, Long.MIN_VALUE, 3000L)
        assertFalse(FitWorkoutParser.isNonNullSorted(withNullLap))
        assertEquals(0L, FitWorkoutParser.resolveLapIndex(2000L, withNullLap, false))
        assertEquals(2L, FitWorkoutParser.resolveLapIndex(3500L, withNullLap, false))
        assertEquals(0L, FitWorkoutParser.resolveLapIndex(500L, withNullLap, false))

        // Unsorted laps fall back to primitive LongArray reverse scan
        val unsorted = longArrayOf(2000L, 1000L, 3000L)
        assertFalse(FitWorkoutParser.isNonNullSorted(unsorted))
        assertEquals(1L, FitWorkoutParser.resolveLapIndex(1500L, unsorted, false))
        assertEquals(2L, FitWorkoutParser.resolveLapIndex(3000L, unsorted, false))
    }

    @Test
    fun testParseMultiLapFit_assignsExactLapNrFromEpochBounds_TST_MIG_058_1() {
        val tempFile = File.createTempFile("test_multilap", ".fit")
        try {
            val baseMs = 1_700_000_000_000L
            val encoder = FileEncoder(tempFile, Fit.ProtocolVersion.V2_0)
            encoder.write(FileIdMesg().apply {
                type = com.garmin.fit.File.ACTIVITY
                manufacturer = Manufacturer.GARMIN
                product = 1
                serialNumber = 1L
                timeCreated = DateTime(Date(baseMs))
            })
            encoder.write(SessionMesg().apply {
                sport = Sport.CYCLING
                startTime = DateTime(Date(baseMs))
                totalElapsedTime = 300f
                totalTimerTime = 300f
            })
            encoder.write(LapMesg().apply {
                startTime = DateTime(Date(baseMs))
                totalElapsedTime = 100f
                totalTimerTime = 100f
            })
            encoder.write(LapMesg().apply {
                startTime = DateTime(Date(baseMs + 100_000L))
                totalElapsedTime = 100f
                totalTimerTime = 100f
            })
            encoder.write(LapMesg().apply {
                startTime = DateTime(Date(baseMs + 200_000L))
                totalElapsedTime = 100f
                totalTimerTime = 100f
            })

            val recordOffsetsSec = longArrayOf(-5L, 0L, 99L, 100L, 150L, 200L, 250L)
            for (offsetSec in recordOffsetsSec) {
                encoder.write(RecordMesg().apply {
                    timestamp = DateTime(Date(baseMs + offsetSec * 1000L))
                    power = 200
                })
            }
            encoder.close()

            val workout = FitWorkoutParser.parse(tempFile, mockContext).getOrThrow()
            assertEquals(7, workout.bufferedSamples.size)
            val expectedLaps = listOf(0L, 0L, 0L, 1L, 1L, 2L, 2L)
            val actualLaps = workout.bufferedSamples.map { it.getAsLong(SensorType.LAP_NR.name) }
            assertEquals(expectedLaps, actualLaps)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testParseFit_ignoresForeignConnectIqDeveloperFieldsAndAcceptsOwnAppIds_TST_MIG_058_1() {
        val tempFile = File.createTempFile("test_ciq_collision", ".fit")
        try {
            val baseMs = 1_700_000_000_000L
            val encoder = FileEncoder(tempFile, Fit.ProtocolVersion.V2_0)
            encoder.write(FileIdMesg().apply {
                type = com.garmin.fit.File.ACTIVITY
                manufacturer = Manufacturer.GARMIN
                product = 1
                serialNumber = 42L
                timeCreated = DateTime(Date(baseMs))
            })

            // Foreign Connect-IQ app (e.g. Stryd / Moxy / Core Body Temp) reusing field nums 0..7
            val foreignAppId = ByteArray(16) { (0xA0 + it).toByte() }
            val foreignDevId = DeveloperDataIdMesg().apply {
                developerDataIndex = 0.toShort()
                manufacturerId = Manufacturer.STRYD
                applicationVersion = 1L
                FitFileWriter.writeApplicationIdUnsigned(this, foreignAppId)
            }
            encoder.write(foreignDevId)

            val foreignRec0 = FieldDescriptionMesg().apply {
                developerDataIndex = 0.toShort()
                fieldDefinitionNumber = 0.toShort()
                fitBaseTypeId = Fit.BASE_TYPE_UINT16.toShort()
                setFieldName(0, "radar_current") // even matching fieldName must be rejected on foreign APP_ID
            }
            val foreignRec1 = FieldDescriptionMesg().apply {
                developerDataIndex = 0.toShort()
                fieldDefinitionNumber = 1.toShort()
                fitBaseTypeId = Fit.BASE_TYPE_UINT16.toShort()
                setFieldName(0, "stryd_leg_spring_stiffness")
            }
            val foreignRec2 = FieldDescriptionMesg().apply {
                developerDataIndex = 0.toShort()
                fieldDefinitionNumber = 2.toShort()
                fitBaseTypeId = Fit.BASE_TYPE_UINT16.toShort()
                setFieldName(0, "moxy_smo2")
            }
            val foreignRec4 = FieldDescriptionMesg().apply {
                developerDataIndex = 0.toShort()
                fieldDefinitionNumber = 4.toShort()
                fitBaseTypeId = Fit.BASE_TYPE_UINT16.toShort()
                setFieldName(0, "core_body_temp")
            }
            val foreignRec5 = FieldDescriptionMesg().apply {
                developerDataIndex = 0.toShort()
                fieldDefinitionNumber = 5.toShort()
                fitBaseTypeId = Fit.BASE_TYPE_UINT16.toShort()
                setFieldName(0, "foreign_count")
            }
            val foreignSessAppId = ByteArray(16) { (0xB0 + it).toByte() }
            val foreignSessDevId = DeveloperDataIdMesg().apply {
                developerDataIndex = 1.toShort()
                manufacturerId = Manufacturer.GARMIN
                applicationVersion = 1L
                FitFileWriter.writeApplicationIdUnsigned(this, foreignSessAppId)
            }
            encoder.write(foreignSessDevId)

            val foreignSess0 = FieldDescriptionMesg().apply {
                developerDataIndex = 1.toShort()
                fieldDefinitionNumber = 0.toShort()
                fitBaseTypeId = Fit.BASE_TYPE_STRING.toShort()
                setFieldName(0, "workout_name")
            }
            val foreignSess5 = FieldDescriptionMesg().apply {
                developerDataIndex = 1.toShort()
                fieldDefinitionNumber = 5.toShort()
                fitBaseTypeId = Fit.BASE_TYPE_UINT8.toShort()
                setFieldName(0, "is_commute")
            }
            listOf(foreignRec0, foreignRec1, foreignRec2, foreignRec4, foreignRec5, foreignSess0, foreignSess5)
                .forEach { encoder.write(it) }

            val rec = RecordMesg().apply {
                timestamp = DateTime(Date(baseMs))
                power = 250
                addDeveloperField(DeveloperField(foreignRec0, foreignDevId).apply { setValue(99) })
                addDeveloperField(DeveloperField(foreignRec1, foreignDevId).apply { setValue(88) })
                addDeveloperField(DeveloperField(foreignRec2, foreignDevId).apply { setValue(77) })
                addDeveloperField(DeveloperField(foreignRec4, foreignDevId).apply { setValue(66) })
                addDeveloperField(DeveloperField(foreignRec5, foreignDevId).apply { setValue(55) })
            }
            encoder.write(rec)

            val sess = SessionMesg().apply {
                sport = Sport.CYCLING
                startTime = DateTime(Date(baseMs))
                totalElapsedTime = 60f
                totalTimerTime = 60f
                addDeveloperField(DeveloperField(foreignSess0, foreignSessDevId).apply { setValue("Spoofed CIQ Name") })
                addDeveloperField(DeveloperField(foreignSess5, foreignSessDevId).apply { setValue(1.toShort()) })
            }
            encoder.write(sess)
            encoder.close()

            val workout = FitWorkoutParser.parse(tempFile, mockContext).getOrThrow()
            assertFalse(workout.foundSensors.contains(SensorType.RADAR_THREAT_COUNT))
            assertFalse(workout.foundSensors.contains(SensorType.RADAR_VEHICLE_COUNT))
            assertFalse(workout.foundSensors.contains(SensorType.RADAR_APPROACH_SPEED_mps))
            assertFalse(workout.foundSensors.contains(SensorType.RADAR_PASSING_DISTANCE_m))
            assertFalse(workout.foundSensors.contains(SensorType.RADAR_LARGE_VEHICLE_COUNT))
            assertNull("Foreign Connect-IQ session field 0 must not overwrite workoutName", workout.workoutName)
            assertFalse("Foreign Connect-IQ session field 5 must not set isCommute", workout.isCommute)

            // Verify FitFileWriter exposes non-empty 16-byte APP_IDs distinct from foreignAppId
            assertEquals(16, FitFileWriter.RADAR_DEVELOPER_DATA_APP_ID.size)
            assertEquals(16, FitFileWriter.APP_METADATA_DEVELOPER_DATA_ID.size)
            assertFalse(FitFileWriter.RADAR_DEVELOPER_DATA_APP_ID.contentEquals(foreignAppId))
            assertFalse(FitFileWriter.APP_METADATA_DEVELOPER_DATA_ID.contentEquals(foreignAppId))
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testParseFit_mixedForeignAndOwnDeveloperFieldsInSameFile_isolatesCleanly_TST_MIG_058_1() {
        val tempFile = File.createTempFile("test_ciq_mixed", ".fit")
        try {
            val baseMs = 1_700_000_000_000L
            val encoder = FileEncoder(tempFile, Fit.ProtocolVersion.V2_0)
            encoder.write(FileIdMesg().apply {
                type = com.garmin.fit.File.ACTIVITY
                manufacturer = Manufacturer.GARMIN
                product = 1
                serialNumber = 77L
                timeCreated = DateTime(Date(baseMs))
            })

            // Foreign Connect-IQ record app (index 0) & session app (index 1)
            val foreignRecDevId = DeveloperDataIdMesg().apply {
                developerDataIndex = 0.toShort()
                manufacturerId = Manufacturer.STRYD
                applicationVersion = 1L
                FitFileWriter.writeApplicationIdUnsigned(this, ByteArray(16) { (0xC0 + it).toByte() })
            }
            val foreignSessDevId = DeveloperDataIdMesg().apply {
                developerDataIndex = 1.toShort()
                manufacturerId = Manufacturer.GARMIN
                applicationVersion = 1L
                FitFileWriter.writeApplicationIdUnsigned(this, ByteArray(16) { (0xD0 + it).toByte() })
            }
            // Own aTrainingTracker Radar (index 2) & Metadata (index 3)
            val ownRadarDevId = DeveloperDataIdMesg().apply {
                developerDataIndex = 2.toShort()
                manufacturerId = Manufacturer.DEVELOPMENT
                applicationVersion = 1L
                FitFileWriter.writeApplicationIdUnsigned(this, FitFileWriter.RADAR_DEVELOPER_DATA_APP_ID)
            }
            val ownMetaDevId = DeveloperDataIdMesg().apply {
                developerDataIndex = 3.toShort()
                manufacturerId = Manufacturer.DEVELOPMENT
                applicationVersion = 1L
                FitFileWriter.writeApplicationIdUnsigned(this, FitFileWriter.APP_METADATA_DEVELOPER_DATA_ID)
            }
            listOf(foreignRecDevId, foreignSessDevId, ownRadarDevId, ownMetaDevId).forEach { encoder.write(it) }

            val foreignRecDesc0 = FieldDescriptionMesg().apply {
                developerDataIndex = 0.toShort()
                fieldDefinitionNumber = 0.toShort()
                fitBaseTypeId = Fit.BASE_TYPE_UINT16.toShort()
                setFieldName(0, "radar_current")
            }
            val foreignSessDesc0 = FieldDescriptionMesg().apply {
                developerDataIndex = 1.toShort()
                fieldDefinitionNumber = 0.toShort()
                fitBaseTypeId = Fit.BASE_TYPE_STRING.toShort()
                setFieldName(0, "workout_name")
            }
            val ownRadarDesc0 = FieldDescriptionMesg().apply {
                developerDataIndex = 2.toShort()
                fieldDefinitionNumber = 0.toShort()
                fitBaseTypeId = Fit.BASE_TYPE_UINT8.toShort()
                setFieldName(0, "radar_current")
            }
            val ownRadarDesc1 = FieldDescriptionMesg().apply {
                developerDataIndex = 2.toShort()
                fieldDefinitionNumber = 1.toShort()
                fitBaseTypeId = Fit.BASE_TYPE_UINT16.toShort()
                setFieldName(0, "radar_total")
            }
            val ownMetaDesc0 = FieldDescriptionMesg().apply {
                developerDataIndex = 3.toShort()
                fieldDefinitionNumber = 0.toShort()
                fitBaseTypeId = Fit.BASE_TYPE_STRING.toShort()
                setFieldName(0, "workout_name")
            }
            val ownMetaDesc5 = FieldDescriptionMesg().apply {
                developerDataIndex = 3.toShort()
                fieldDefinitionNumber = 5.toShort()
                fitBaseTypeId = Fit.BASE_TYPE_UINT8.toShort()
                setFieldName(0, "is_commute")
            }
            listOf(foreignRecDesc0, foreignSessDesc0, ownRadarDesc0, ownRadarDesc1, ownMetaDesc0, ownMetaDesc5)
                .forEach { encoder.write(it) }

            encoder.write(RecordMesg().apply {
                timestamp = DateTime(Date(baseMs))
                power = 210
                addDeveloperField(DeveloperField(ownRadarDesc0, ownRadarDevId).apply { setValue(3.toShort()) })
                addDeveloperField(DeveloperField(ownRadarDesc1, ownRadarDevId).apply { setValue(12) })
                // Foreign field written AFTER own field must NOT overwrite own value
                addDeveloperField(DeveloperField(foreignRecDesc0, foreignRecDevId).apply { setValue(999) })
            })

            encoder.write(SessionMesg().apply {
                sport = Sport.CYCLING
                startTime = DateTime(Date(baseMs))
                totalElapsedTime = 60f
                totalTimerTime = 60f
                addDeveloperField(DeveloperField(ownMetaDesc0, ownMetaDevId).apply { setValue("Authentic Ride") })
                addDeveloperField(DeveloperField(ownMetaDesc5, ownMetaDevId).apply { setValue(1.toShort()) })
                // Foreign metadata field written AFTER own field must NOT overwrite workoutName
                addDeveloperField(DeveloperField(foreignSessDesc0, foreignSessDevId).apply { setValue("Spoofed Name") })
            })
            encoder.close()

            val workout = FitWorkoutParser.parse(tempFile, mockContext).getOrThrow()
            assertTrue(workout.foundSensors.contains(SensorType.RADAR_THREAT_COUNT))
            assertTrue(workout.foundSensors.contains(SensorType.RADAR_VEHICLE_COUNT))
            assertEquals(3, workout.bufferedSamples[0].getAsInteger(SensorType.RADAR_THREAT_COUNT.name))
            assertEquals(12, workout.bufferedSamples[0].getAsInteger(SensorType.RADAR_VEHICLE_COUNT.name))
            assertEquals("Authentic Ride", workout.workoutName)
            assertTrue(workout.isCommute)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testParseFit_legacySignedByteRadarAppIdRemainsBackwardCompatible_TST_MIG_058_1() {
        val tempFile = File.createTempFile("test_legacy_radar_appid", ".fit")
        try {
            val baseMs = 1_700_000_000_000L
            val encoder = FileEncoder(tempFile, Fit.ProtocolVersion.V2_0)
            encoder.write(FileIdMesg().apply {
                type = com.garmin.fit.File.ACTIVITY
                manufacturer = Manufacturer.DEVELOPMENT
                product = 1
                serialNumber = 1L
                timeCreated = DateTime(Date(baseMs))
            })

            // Reproduce historical pre-ATT-8127 export using signed setApplicationId(i, Byte)
            val legacyDevId = DeveloperDataIdMesg().apply {
                developerDataIndex = 0.toShort()
                manufacturerId = Manufacturer.DEVELOPMENT
                applicationVersion = 1L
                for (i in 0 until 16) {
                    setApplicationId(i, FitFileWriter.RADAR_DEVELOPER_DATA_APP_ID[i])
                }
            }
            encoder.write(legacyDevId)

            val radarDesc0 = FieldDescriptionMesg().apply {
                developerDataIndex = 0.toShort()
                fieldDefinitionNumber = 0.toShort()
                fitBaseTypeId = Fit.BASE_TYPE_UINT8.toShort()
                setFieldName(0, "radar_current")
            }
            val radarDesc1 = FieldDescriptionMesg().apply {
                developerDataIndex = 0.toShort()
                fieldDefinitionNumber = 1.toShort()
                fitBaseTypeId = Fit.BASE_TYPE_UINT16.toShort()
                setFieldName(0, "radar_total")
            }
            encoder.write(radarDesc0)
            encoder.write(radarDesc1)

            encoder.write(RecordMesg().apply {
                timestamp = DateTime(Date(baseMs))
                addDeveloperField(DeveloperField(radarDesc0, legacyDevId).apply { setValue(2.toShort()) })
                addDeveloperField(DeveloperField(radarDesc1, legacyDevId).apply { setValue(7) })
            })
            encoder.write(SessionMesg().apply {
                sport = Sport.CYCLING
                startTime = DateTime(Date(baseMs))
                totalElapsedTime = 60f
                totalTimerTime = 60f
            })
            encoder.close()

            val workout = FitWorkoutParser.parse(tempFile, mockContext).getOrThrow()
            assertTrue(workout.foundSensors.contains(SensorType.RADAR_THREAT_COUNT))
            assertTrue(workout.foundSensors.contains(SensorType.RADAR_VEHICLE_COUNT))
            assertEquals(2, workout.bufferedSamples[0].getAsInteger(SensorType.RADAR_THREAT_COUNT.name))
            assertEquals(7, workout.bufferedSamples[0].getAsInteger(SensorType.RADAR_VEHICLE_COUNT.name))
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testParseFit_redefinedDeveloperDataIndexUsesMesgDefBoundAppId_TST_MIG_058_1() {
        val tempFile = File.createTempFile("test_redefined_dev_idx", ".fit")
        try {
            val baseMs = 1_700_000_000_000L
            val encoder = FileEncoder(tempFile, Fit.ProtocolVersion.V2_0)
            encoder.write(FileIdMesg().apply {
                type = com.garmin.fit.File.ACTIVITY
                manufacturer = Manufacturer.GARMIN
                product = 1
                serialNumber = 99L
                timeCreated = DateTime(Date(baseMs))
            })

            // Segment 1: developerDataIndex = 0 belongs to a FOREIGN Connect-IQ app
            val foreignDevId = DeveloperDataIdMesg().apply {
                developerDataIndex = 0.toShort()
                manufacturerId = Manufacturer.STRYD
                applicationVersion = 1L
                FitFileWriter.writeApplicationIdUnsigned(this, ByteArray(16) { (0xE0 + it).toByte() })
            }
            encoder.write(foreignDevId)
            val foreignDesc0 = FieldDescriptionMesg().apply {
                developerDataIndex = 0.toShort()
                fieldDefinitionNumber = 0.toShort()
                fitBaseTypeId = Fit.BASE_TYPE_UINT8.toShort()
                setFieldName(0, "radar_current")
            }
            encoder.write(foreignDesc0)
            encoder.write(RecordMesg().apply {
                timestamp = DateTime(Date(baseMs))
                addDeveloperField(DeveloperField(foreignDesc0, foreignDevId).apply { setValue(99.toShort()) })
            })

            // Segment 2: developerDataIndex = 0 is REDEFINED to own MyBikeTraffic Radar APP_ID
            val ownRadarDevId = DeveloperDataIdMesg().apply {
                developerDataIndex = 0.toShort()
                manufacturerId = Manufacturer.DEVELOPMENT
                applicationVersion = 1L
                FitFileWriter.writeApplicationIdUnsigned(this, FitFileWriter.RADAR_DEVELOPER_DATA_APP_ID)
            }
            encoder.write(ownRadarDevId)
            val ownRadarDesc0 = FieldDescriptionMesg().apply {
                developerDataIndex = 0.toShort()
                fieldDefinitionNumber = 0.toShort()
                fitBaseTypeId = Fit.BASE_TYPE_UINT8.toShort()
                setFieldName(0, "radar_current")
            }
            encoder.write(ownRadarDesc0)
            encoder.write(RecordMesg().apply {
                timestamp = DateTime(Date(baseMs + 1000L))
                addDeveloperField(DeveloperField(ownRadarDesc0, ownRadarDevId).apply { setValue(2.toShort()) })
            })

            encoder.write(SessionMesg().apply {
                sport = Sport.CYCLING
                startTime = DateTime(Date(baseMs))
                totalElapsedTime = 60f
                totalTimerTime = 60f
            })
            encoder.close()

            val workout = FitWorkoutParser.parse(tempFile, mockContext).getOrThrow()
            assertEquals(2, workout.bufferedSamples.size)
            // Record 1 (bound to foreign MESG_DEF) MUST NOT be retroactively spoofed by later devIndex=0 redefinition
            assertNull(
                "Record 1 foreign field must not be misattributed to RADAR_THREAT_COUNT after devIndex=0 redefinition",
                workout.bufferedSamples[0].getAsInteger(SensorType.RADAR_THREAT_COUNT.name)
            )
            // Record 2 (bound to own Radar MESG_DEF) MUST be decoded accurately
            assertEquals(2, workout.bufferedSamples[1].getAsInteger(SensorType.RADAR_THREAT_COUNT.name))
        } finally {
            tempFile.delete()
        }
    }
}
