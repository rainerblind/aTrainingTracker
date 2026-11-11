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

package com.atrainingtracker.trainingtracker.tcx.serializer

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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter

/**
 * Unit tests verifying [TcxSerializer] schema compliance, sequence ordering,
 * integer element formatting, and XML character escaping (REQ-DAT-040.2, TST-DAT-036.1).
 */
class TcxSerializerTest {

    @Test
    fun testEscapeXml() {
        assertEquals("Tom &amp; Jerry &lt;1&gt; &quot;fast&quot; &apos;run&apos;",
            TcxSerializer.escapeXml("Tom & Jerry <1> \"fast\" 'run'"))
        assertEquals("", TcxSerializer.escapeXml(null))
    }

    @Test
    fun testSerializationSchemaComplianceAndSequence() {
        val trackpoint = TcxTrackpoint(
            time = "2026-11-10T08:00:00Z",
            position = TcxPosition(48.137154, 11.576124),
            altitudeMeters = 520.5,
            distanceMeters = 1500.0,
            heartRateBpm = 145,
            cadence = 88,
            extensions = TcxTrackpointExtensions(
                speed = 3.5,
                watts = 250,
                runCadence = 176
            )
        )

        val lap = TcxLap(
            startTime = "2026-11-10T08:00:00Z",
            totalTimeSeconds = 600.0,
            distanceMeters = 2000.0,
            maximumSpeed = 4.2,
            calories = 120,
            averageHeartRateBpm = 142,
            maximumHeartRateBpm = 155,
            intensity = "Active",
            cadence = 88,
            triggerMethod = "Manual",
            trackpoints = listOf(trackpoint),
            notes = "[Warmup Lap] Easy & relaxed",
            extensions = TcxLapExtensions(
                name = "Warmup Lap",
                description = "Easy & relaxed"
            )
        )

        val activity = TcxActivity(
            sport = "Biking",
            id = "2026-11-10T08:00:00Z",
            laps = listOf(lap),
            notes = "[Morning Tempo Ride] Good power & rhythm",
            creator = TcxCreator(
                name = "Google Pixel 10",
                unitId = 0L,
                productId = 1,
                versionMajor = 5,
                versionMinor = 0,
                buildMajor = 500,
                buildMinor = 0
            ),
            extensions = TcxActivityExtensions(
                customName = "Morning Tempo Ride",
                customDescription = "Good power & rhythm"
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

        val document = TcxDocument(
            activities = listOf(activity),
            author = author
        )

        val writer = StringWriter()
        TcxSerializer.serialize(document, writer)
        val xml = writer.toString()

        // 1. Verify XML header and namespaces
        assertTrue(xml.startsWith("<?xml version=\"1.0\"?>"))
        assertTrue(xml.contains("xmlns=\"http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2\""))
        assertTrue(xml.contains("<Activity Sport=\"Biking\">"))
        assertTrue(xml.contains("<Id>2026-11-10T08:00:00Z</Id>"))

        // 2. Verify Lap structure & sequence
        val lapIdx = xml.indexOf("<Lap StartTime=\"2026-11-10T08:00:00Z\">")
        val trackIdx = xml.indexOf("<Track>", lapIdx)
        val lapNotesIdx = xml.indexOf("<Notes>[Warmup Lap] Easy &amp; relaxed</Notes>", trackIdx)
        val lapExtIdx = xml.indexOf("<att:LapExtension", lapNotesIdx)
        val lapEndIdx = xml.indexOf("</Lap>", lapExtIdx)
        assertTrue(lapIdx in 1 until trackIdx)
        assertTrue(trackIdx in 1 until lapNotesIdx)
        assertTrue(lapNotesIdx in 1 until lapExtIdx)
        assertTrue(lapExtIdx in 1 until lapEndIdx)

        // 3. Verify Trackpoint integers and tags
        assertTrue(xml.contains("<LatitudeDegrees>48.137154</LatitudeDegrees>"))
        assertTrue(xml.contains("<LongitudeDegrees>11.576124</LongitudeDegrees>"))
        assertTrue(xml.contains("<AltitudeMeters>520.5</AltitudeMeters>"))
        assertTrue(xml.contains("<DistanceMeters>1500.0</DistanceMeters>"))
        assertTrue(xml.contains("<Value>145</Value>"))
        assertTrue(xml.contains("<Cadence>88</Cadence>"))
        assertTrue(xml.contains("<Speed>3.5</Speed>"))
        assertTrue(xml.contains("<Watts>250</Watts>"))
        assertTrue(xml.contains("<RunCadence>176</RunCadence>"))

        // 4. Verify Activity sequence: Laps -> Notes -> Creator -> Extensions
        val actNotesIdx = xml.indexOf("<Notes>[Morning Tempo Ride] Good power &amp; rhythm</Notes>", lapEndIdx)
        val creatorIdx = xml.indexOf("<Creator xsi:type=\"Device_t\">", actNotesIdx)
        val actExtIdx = xml.indexOf("<att:ActivityExtension", creatorIdx)
        val actEndIdx = xml.indexOf("</Activity>", actExtIdx)
        assertTrue(lapEndIdx in 1 until actNotesIdx)
        assertTrue(actNotesIdx in 1 until creatorIdx)
        assertTrue(creatorIdx in 1 until actExtIdx)
        assertTrue(actExtIdx in 1 until actEndIdx)

        // 5. Verify Author block
        val authorIdx = xml.indexOf("<Author xsi:type=\"Application_t\">", actEndIdx)
        assertTrue(authorIdx > actEndIdx)
        assertTrue(xml.contains("<Name>aTrainingTracker with barometer</Name>"))
        assertTrue(xml.contains("<VersionMajor>5</VersionMajor>"))
        assertTrue(xml.contains("</TrainingCenterDatabase>"))
    }

    @Test
    fun testStreamingParityWithBatchSerialization() {
        val trackpoint = TcxTrackpoint(
            time = "2026-11-10T08:00:00Z",
            position = TcxPosition(48.137154, 11.576124),
            altitudeMeters = 520.5,
            distanceMeters = 1500.0,
            heartRateBpm = 145,
            cadence = 88,
            extensions = TcxTrackpointExtensions(
                speed = 3.5,
                watts = 250,
                runCadence = 176
            )
        )

        val lap = TcxLap(
            startTime = "2026-11-10T08:00:00Z",
            totalTimeSeconds = 600.0,
            distanceMeters = 2000.0,
            maximumSpeed = 4.2,
            calories = 120,
            averageHeartRateBpm = 142,
            maximumHeartRateBpm = 155,
            intensity = "Active",
            cadence = 88,
            triggerMethod = "Manual",
            trackpoints = listOf(trackpoint),
            notes = "[Warmup Lap] Easy & relaxed",
            extensions = TcxLapExtensions(
                name = "Warmup Lap",
                description = "Easy & relaxed"
            )
        )

        val activity = TcxActivity(
            sport = "Biking",
            id = "2026-11-10T08:00:00Z",
            laps = listOf(lap),
            notes = "[Morning Tempo Ride] Good power & rhythm",
            creator = TcxCreator(
                name = "Google Pixel 10",
                unitId = 0L,
                productId = 1,
                versionMajor = 5,
                versionMinor = 0,
                buildMajor = 500,
                buildMinor = 0
            ),
            extensions = TcxActivityExtensions(
                customName = "Morning Tempo Ride",
                customDescription = "Good power & rhythm"
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

        val document = TcxDocument(
            activities = listOf(activity),
            author = author
        )

        // Batch serialization
        val batchWriter = StringWriter()
        TcxSerializer.serialize(document, batchWriter)
        val batchXml = batchWriter.toString()

        // Incremental streaming serialization
        val streamWriter = StringWriter()
        TcxSerializer.startDocument(streamWriter)
        TcxSerializer.startActivityHeader(activity.sport, activity.id, streamWriter)
        TcxSerializer.startLapHeader(
            startTime = lap.startTime,
            totalTimeSeconds = lap.totalTimeSeconds,
            distanceMeters = lap.distanceMeters,
            writer = streamWriter,
            maximumSpeed = lap.maximumSpeed,
            calories = lap.calories,
            averageHeartRateBpm = lap.averageHeartRateBpm,
            maximumHeartRateBpm = lap.maximumHeartRateBpm,
            intensity = lap.intensity,
            cadence = lap.cadence,
            triggerMethod = lap.triggerMethod
        )
        for (tp in lap.trackpoints) {
            TcxSerializer.writeTrackpoint(tp, streamWriter)
        }
        TcxSerializer.endLap(streamWriter, lap.notes, lap.extensions)
        TcxSerializer.endActivity(streamWriter, activity.notes, activity.creator, activity.extensions)
        TcxSerializer.endDocument(streamWriter, author)
        val streamXml = streamWriter.toString()

        assertEquals("Streaming XML must match batch XML exactly", batchXml, streamXml)
    }
}
