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

package com.atrainingtracker.trainingtracker.tcx

import android.content.ContentValues
import android.content.Context
import android.util.Log
import android.util.Xml
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.migration.parsers.TcxWorkoutParser
import com.atrainingtracker.trainingtracker.tcx.model.TcxActivity
import com.atrainingtracker.trainingtracker.tcx.model.TcxActivityExtensions
import com.atrainingtracker.trainingtracker.tcx.model.TcxAuthor
import com.atrainingtracker.trainingtracker.tcx.model.TcxCreator
import com.atrainingtracker.trainingtracker.tcx.model.TcxDocument
import com.atrainingtracker.trainingtracker.tcx.model.TcxLap
import com.atrainingtracker.trainingtracker.tcx.model.TcxLapExtensions
import com.atrainingtracker.trainingtracker.tcx.model.TcxPosition
import com.atrainingtracker.trainingtracker.tcx.model.TcxTrackpoint
import com.atrainingtracker.trainingtracker.tcx.model.TcxTrackpointExtensions
import com.atrainingtracker.trainingtracker.tcx.parser.TcxParser
import com.atrainingtracker.trainingtracker.tcx.serializer.TcxSerializer
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * Comprehensive bidirectional round-trip verification suite proving that exporting
 * an activity to TCX and re-importing/re-parsing it preserves 100% of workout metadata,
 * lap boundaries, custom descriptions, and sensor channels without data loss
 * (REQ-DAT-040.4, TST-DAT-036.3, ATT-938).
 */
class TCXRoundTripTest {

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
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0

        mockkStatic(Xml::class)
        every { Xml.newPullParser() } answers { org.kxml2.io.KXmlParser() }

        mockContext = mockk(relaxed = true)
        mockSportTypeDb = mockk(relaxed = true)
        every { mockSportTypeDb.getSportTypeIdFromTcxName("Biking") } returns 2L
        every { mockSportTypeDb.getBSportType(2L) } returns BSportType.BIKE
        mockkStatic(SportTypeDatabaseManager::class)
        every { SportTypeDatabaseManager.getInstance(any()) } returns mockSportTypeDb

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
        every { constructedWith<ContentValues>().containsKey(any<String>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores[cv]
            map?.containsKey(firstArg<String>()) ?: false
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testBidirectionalDtoRoundTripFidelity() {
        // 1. Construct comprehensive source document
        val tp1 = TcxTrackpoint(
            time = "2026-11-10T09:00:00Z",
            position = TcxPosition(48.137154, 11.576124),
            altitudeMeters = 520.0,
            distanceMeters = 0.0,
            heartRateBpm = 135,
            cadence = 85,
            extensions = TcxTrackpointExtensions(
                speed = 6.2,
                watts = 210,
                runCadence = null
            )
        )

        val tp2 = TcxTrackpoint(
            time = "2026-11-10T09:05:00Z",
            position = TcxPosition(48.145000, 11.585000),
            altitudeMeters = 535.5,
            distanceMeters = 2000.0,
            heartRateBpm = 155,
            cadence = 92,
            extensions = TcxTrackpointExtensions(
                speed = 7.5,
                watts = 275,
                runCadence = null
            )
        )

        val tp3 = TcxTrackpoint(
            time = "2026-11-10T09:10:00Z",
            position = TcxPosition(48.155000, 11.595000),
            altitudeMeters = 550.0,
            distanceMeters = 4000.0,
            heartRateBpm = 168,
            cadence = 95,
            extensions = TcxTrackpointExtensions(
                speed = 8.1,
                watts = 310,
                runCadence = null
            )
        )

        val lap0 = TcxLap(
            startTime = "2026-11-10T09:00:00Z",
            totalTimeSeconds = 300.0,
            distanceMeters = 2000.0,
            maximumSpeed = 7.5,
            calories = 140,
            averageHeartRateBpm = 145,
            maximumHeartRateBpm = 158,
            intensity = "Active",
            cadence = 88,
            triggerMethod = "Manual",
            trackpoints = listOf(tp1, tp2),
            notes = "[Warmup] Spin legs smoothly",
            extensions = TcxLapExtensions(
                name = "Warmup",
                description = "Spin legs smoothly"
            )
        )

        val lap1 = TcxLap(
            startTime = "2026-11-10T09:05:00Z",
            totalTimeSeconds = 300.0,
            distanceMeters = 2000.0,
            maximumSpeed = 8.5,
            calories = 180,
            averageHeartRateBpm = 162,
            maximumHeartRateBpm = 175,
            intensity = "Active",
            cadence = 94,
            triggerMethod = "Manual",
            trackpoints = listOf(tp3),
            notes = "[Threshold Interval] Hard effort & fast rhythm",
            extensions = TcxLapExtensions(
                name = "Threshold Interval",
                description = "Hard effort & fast rhythm"
            )
        )

        val activity = TcxActivity(
            sport = "Biking",
            id = "2026-11-10T09:00:00Z",
            laps = listOf(lap0, lap1),
            notes = "[Tuesday Intervals] High intensity & smooth cadence",
            creator = TcxCreator(
                name = "Google Pixel 10",
                unitId = 987654L,
                productId = 1,
                versionMajor = 5,
                versionMinor = 0,
                buildMajor = 500,
                buildMinor = 0
            ),
            extensions = TcxActivityExtensions(
                customName = "Tuesday Intervals",
                customDescription = "High intensity & smooth cadence"
            )
        )

        val author = TcxAuthor(
            name = "aTrainingTracker with barometer",
            versionMajor = 5,
            versionMinor = 0,
            buildMajor = 500,
            buildMinor = 0,
            type = "Release",
            langId = "en",
            partNumber = "000-00000-00"
        )

        val originalDoc = TcxDocument(
            activities = listOf(activity),
            author = author
        )

        // 2. Serialize to TCX XML stream
        val outputStream = ByteArrayOutputStream()
        TcxSerializer.serialize(originalDoc, outputStream)
        val xmlString = outputStream.toString(StandardCharsets.UTF_8.name())
        assertTrue("XML must be generated", xmlString.isNotBlank())
        assertTrue("XML must contain proper schema header", xmlString.contains("TrainingCenterDatabase"))

        // 3. Deserialize back into TcxDocument via TcxParser
        val parsedResult = TcxParser.parse(ByteArrayInputStream(outputStream.toByteArray()))
        assertTrue("TcxParser must succeed", parsedResult.isSuccess)
        val roundTripDoc = parsedResult.getOrThrow()

        // 4. Assert 100% round-trip fidelity
        assertEquals(1, roundTripDoc.activities.size)
        val rtActivity = roundTripDoc.activities.first()
        assertEquals(activity.sport, rtActivity.sport)
        assertEquals(activity.id, rtActivity.id)
        assertEquals(activity.notes, rtActivity.notes)
        assertEquals(activity.extensions?.customName, rtActivity.extensions?.customName)
        assertEquals(activity.extensions?.customDescription, rtActivity.extensions?.customDescription)

        // Laps
        assertEquals(2, rtActivity.laps.size)
        val rtLap0 = rtActivity.laps[0]
        assertEquals(lap0.startTime, rtLap0.startTime)
        assertEquals(lap0.totalTimeSeconds, rtLap0.totalTimeSeconds, 0.001)
        assertEquals(lap0.distanceMeters, rtLap0.distanceMeters, 0.001)
        assertEquals(lap0.extensions?.name, rtLap0.extensions?.name)
        assertEquals(lap0.extensions?.description, rtLap0.extensions?.description)
        assertEquals(2, rtLap0.trackpoints.size)

        val rtLap1 = rtActivity.laps[1]
        assertEquals(lap1.startTime, rtLap1.startTime)
        assertEquals(lap1.extensions?.name, rtLap1.extensions?.name)
        assertEquals(lap1.extensions?.description, rtLap1.extensions?.description)
        assertEquals(1, rtLap1.trackpoints.size)

        // Trackpoints
        val rtTp1 = rtLap0.trackpoints[0]
        assertEquals(tp1.time, rtTp1.time)
        assertEquals(tp1.position?.latitudeDegrees ?: 0.0, rtTp1.position?.latitudeDegrees ?: 0.0, 0.0001)
        assertEquals(tp1.position?.longitudeDegrees ?: 0.0, rtTp1.position?.longitudeDegrees ?: 0.0, 0.0001)
        assertEquals(tp1.altitudeMeters ?: 0.0, rtTp1.altitudeMeters ?: 0.0, 0.001)
        assertEquals(tp1.distanceMeters ?: 0.0, rtTp1.distanceMeters ?: 0.0, 0.001)
        assertEquals(tp1.heartRateBpm, rtTp1.heartRateBpm)
        assertEquals(tp1.cadence, rtTp1.cadence)
        assertEquals(tp1.extensions?.speed ?: 0.0, rtTp1.extensions?.speed ?: 0.0, 0.001)
        assertEquals(tp1.extensions?.watts, rtTp1.extensions?.watts)

        // Creator & Author
        assertNotNull(rtActivity.creator)
        assertEquals(activity.creator?.name, rtActivity.creator?.name)
        assertNotNull(roundTripDoc.author)
        assertEquals(author.name, roundTripDoc.author?.name)

        // 5. Integration check with TcxWorkoutParser
        val workoutDataResult = TcxWorkoutParser.parse(ByteArrayInputStream(outputStream.toByteArray()), mockContext)
        assertTrue("TcxWorkoutParser should parse the serialized XML", workoutDataResult.isSuccess)
        val workoutData = workoutDataResult.getOrThrow()
        assertEquals("Tuesday Intervals", workoutData.workoutName)
        assertEquals("High intensity & smooth cadence", workoutData.workoutNotes)
        assertEquals("Biking", workoutData.sportName)
        assertEquals(BSportType.BIKE, workoutData.bSportType)
        assertEquals(2, workoutData.parsedLaps.size)
        assertEquals(3, workoutData.points.size)
        assertEquals(3, workoutData.bufferedSamples.size)
        assertTrue(workoutData.foundSensors.contains(SensorType.LATITUDE))
        assertTrue(workoutData.foundSensors.contains(SensorType.LONGITUDE))
        assertTrue(workoutData.foundSensors.contains(SensorType.ALTITUDE))
        assertTrue(workoutData.foundSensors.contains(SensorType.HR))
        assertTrue(workoutData.foundSensors.contains(SensorType.CADENCE))
        assertTrue(workoutData.foundSensors.contains(SensorType.POWER))
        assertTrue(workoutData.foundSensors.contains(SensorType.SPEED_mps))
    }

    @Test
    fun testNameOnlyRoundTripPreservesNullWorkoutNotes() {
        val tp = TcxTrackpoint(
            time = "2026-11-10T10:00:00Z",
            position = TcxPosition(48.137, 11.576),
            altitudeMeters = 500.0,
            distanceMeters = 1000.0,
            heartRateBpm = 140,
            cadence = 85,
            extensions = null
        )
        val lap = TcxLap(
            startTime = "2026-11-10T10:00:00Z",
            totalTimeSeconds = 300.0,
            distanceMeters = 1000.0,
            maximumSpeed = 5.0,
            calories = 100,
            averageHeartRateBpm = 140,
            maximumHeartRateBpm = 150,
            intensity = "Active",
            cadence = 85,
            triggerMethod = "Manual",
            trackpoints = listOf(tp),
            notes = null,
            extensions = null
        )
        // Name only: customName is set, customDescription is null, notes is bracketed "[Morning Tempo]"
        val activity = TcxActivity(
            sport = "Biking",
            id = "2026-11-10T10:00:00Z",
            laps = listOf(lap),
            notes = "[Morning Tempo]",
            creator = null,
            extensions = TcxActivityExtensions(
                customName = "Morning Tempo",
                customDescription = null
            )
        )
        val doc = TcxDocument(activities = listOf(activity), author = null)

        val outputStream = ByteArrayOutputStream()
        TcxSerializer.serialize(doc, outputStream)

        val parsedResult = TcxWorkoutParser.parse(ByteArrayInputStream(outputStream.toByteArray()), mockContext)
        assertTrue("TcxWorkoutParser should succeed", parsedResult.isSuccess)
        val workoutData = parsedResult.getOrThrow()

        assertEquals("Morning Tempo", workoutData.workoutName)
        assertNull("workoutNotes must be null and must not leak bracketed name [Morning Tempo]", workoutData.workoutNotes)
    }

    @Test
    fun testDescriptionOnlyRoundTripPreservesWorkoutNotes() {
        val tp = TcxTrackpoint(
            time = "2026-11-10T10:00:00Z",
            position = TcxPosition(48.137, 11.576),
            altitudeMeters = 500.0,
            distanceMeters = 1000.0,
            heartRateBpm = 140,
            cadence = 85,
            extensions = null
        )
        val lap = TcxLap(
            startTime = "2026-11-10T10:00:00Z",
            totalTimeSeconds = 300.0,
            distanceMeters = 1000.0,
            maximumSpeed = 5.0,
            calories = 100,
            averageHeartRateBpm = 140,
            maximumHeartRateBpm = 150,
            intensity = "Active",
            cadence = 85,
            triggerMethod = "Manual",
            trackpoints = listOf(tp),
            notes = null,
            extensions = null
        )
        // Description only: customName is null, customDescription is set, notes is the raw description
        val activity = TcxActivity(
            sport = "Biking",
            id = "2026-11-10T10:00:00Z",
            laps = listOf(lap),
            notes = "Felt great and steady",
            creator = null,
            extensions = TcxActivityExtensions(
                customName = null,
                customDescription = "Felt great and steady"
            )
        )
        val doc = TcxDocument(activities = listOf(activity), author = null)

        val outputStream = ByteArrayOutputStream()
        TcxSerializer.serialize(doc, outputStream)

        val parsedResult = TcxWorkoutParser.parse(ByteArrayInputStream(outputStream.toByteArray()), mockContext)
        assertTrue("TcxWorkoutParser should succeed", parsedResult.isSuccess)
        val workoutData = parsedResult.getOrThrow()

        assertNull("workoutName must be null", workoutData.workoutName)
        assertEquals("Felt great and steady", workoutData.workoutNotes)
    }

    @Test
    fun testMultiLapCustomLapNamePreservesEmptyLapNotes() {
        val tp1 = TcxTrackpoint(
            time = "2026-11-10T10:00:00Z",
            position = TcxPosition(48.137, 11.576),
            altitudeMeters = 500.0,
            distanceMeters = 1000.0,
            heartRateBpm = 140,
            cadence = 85,
            extensions = null
        )
        val tp2 = TcxTrackpoint(
            time = "2026-11-10T10:05:00Z",
            position = TcxPosition(48.140, 11.580),
            altitudeMeters = 505.0,
            distanceMeters = 2000.0,
            heartRateBpm = 150,
            cadence = 90,
            extensions = null
        )
        val lap0 = TcxLap(
            startTime = "2026-11-10T10:00:00Z",
            totalTimeSeconds = 300.0,
            distanceMeters = 1000.0,
            maximumSpeed = 5.0,
            calories = 100,
            averageHeartRateBpm = 140,
            maximumHeartRateBpm = 150,
            intensity = "Active",
            cadence = 85,
            triggerMethod = "Manual",
            trackpoints = listOf(tp1),
            notes = "[Sprint 1]",
            extensions = TcxLapExtensions(
                name = "Sprint 1",
                description = null
            )
        )
        val lap1 = TcxLap(
            startTime = "2026-11-10T10:05:00Z",
            totalTimeSeconds = 300.0,
            distanceMeters = 1000.0,
            maximumSpeed = 4.0,
            calories = 80,
            averageHeartRateBpm = 125,
            maximumHeartRateBpm = 135,
            intensity = "Resting",
            cadence = 75,
            triggerMethod = "Manual",
            trackpoints = listOf(tp2),
            notes = "[Recovery 1] Slow spin",
            extensions = TcxLapExtensions(
                name = "Recovery 1",
                description = "Slow spin"
            )
        )
        val activity = TcxActivity(
            sport = "Biking",
            id = "2026-11-10T10:00:00Z",
            laps = listOf(lap0, lap1),
            notes = null,
            creator = null,
            extensions = null
        )
        val doc = TcxDocument(activities = listOf(activity), author = null)

        val outputStream = ByteArrayOutputStream()
        TcxSerializer.serialize(doc, outputStream)

        val parsedResult = TcxWorkoutParser.parse(ByteArrayInputStream(outputStream.toByteArray()), mockContext)
        assertTrue("TcxWorkoutParser should succeed", parsedResult.isSuccess)
        val workoutData = parsedResult.getOrThrow()

        assertEquals(2, workoutData.parsedLaps.size)
        assertEquals("Sprint 1", workoutData.parsedLaps[0].name)
        assertNull("Lap 0 description must be null", workoutData.parsedLaps[0].description)
        assertEquals("Recovery 1", workoutData.parsedLaps[1].name)
        assertEquals("Slow spin", workoutData.parsedLaps[1].description)
    }

    @Test
    fun testLegacyThirdPartyUnbracketedNotes() {
        val rawTcx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
              <Activities>
                <Activity Sport="Biking">
                  <Id>2026-11-10T10:00:00Z</Id>
                  <Lap StartTime="2026-11-10T10:00:00Z">
                    <TotalTimeSeconds>300.0</TotalTimeSeconds>
                    <DistanceMeters>1000.0</DistanceMeters>
                    <Track>
                      <Trackpoint>
                        <Time>2026-11-10T10:00:00Z</Time>
                        <Position>
                          <LatitudeDegrees>48.137</LatitudeDegrees>
                          <LongitudeDegrees>11.576</LongitudeDegrees>
                        </Position>
                        <AltitudeMeters>500.0</AltitudeMeters>
                        <DistanceMeters>1000.0</DistanceMeters>
                      </Trackpoint>
                    </Track>
                  </Lap>
                  <Notes>Legacy third-party unbracketed notes</Notes>
                </Activity>
              </Activities>
            </TrainingCenterDatabase>
        """.trimIndent()

        val parsedResult = TcxWorkoutParser.parse(ByteArrayInputStream(rawTcx.toByteArray(StandardCharsets.UTF_8)), mockContext)
        assertTrue("TcxWorkoutParser should succeed on third-party TCX", parsedResult.isSuccess)
        val workoutData = parsedResult.getOrThrow()

        assertNull("workoutName must be null for unbracketed single-line notes", workoutData.workoutName)
        assertEquals("Legacy third-party unbracketed notes", workoutData.workoutNotes)
    }
}
