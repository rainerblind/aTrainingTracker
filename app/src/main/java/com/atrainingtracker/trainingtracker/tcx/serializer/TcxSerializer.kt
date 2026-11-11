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
import com.atrainingtracker.trainingtracker.tcx.model.TcxCourse
import com.atrainingtracker.trainingtracker.tcx.model.TcxCoursePoint
import com.atrainingtracker.trainingtracker.tcx.model.TcxCreator
import com.atrainingtracker.trainingtracker.tcx.model.TcxDocument
import com.atrainingtracker.trainingtracker.tcx.model.TcxLap
import com.atrainingtracker.trainingtracker.tcx.model.TcxLapExtensions
import com.atrainingtracker.trainingtracker.tcx.model.TcxTrackpoint
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.nio.charset.StandardCharsets

/**
 * Streaming serializer converting structured [TcxDocument] DTOs into schema-compliant TCX XML.
 *
 * Enforces:
 * - REQ-DAT-040.2: Streaming DTO XML Serializer.
 * - REQ-EXP-001: Integer Watts, Cadence, RunCadence.
 * - REQ-EXP-022: Garmin TCX v2 sequence ordering and safe XML character escaping.
 */
object TcxSerializer {

    const val TCX_SCHEMA_HEADER = "<?xml version=\"1.0\"?>\n" +
            "<TrainingCenterDatabase xmlns=\"http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2\" " +
            "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
            "xsi:schemaLocation=\"http://www.garmin.com/xmlschemas/ActivityExtension/v2 http://www.garmin.com/xmlschemas/ActivityExtensionv2.xsd http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2 http://www.garmin.com/xmlschemas/TrainingCenterDatabasev2.xsd\">\n"

    const val EXT_NAMESPACE_ATT = "http://atrainingtracker.com/xmlschemas/TrainingCenterDatabaseExtensions/v1"
    const val EXT_NAMESPACE_TPX = "http://www.garmin.com/xmlschemas/ActivityExtension/v2"

    /**
     * Escapes standard XML reserved characters.
     */
    fun escapeXml(text: String?): String {
        if (text == null) return ""
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    /**
     * Serializes a complete [TcxDocument] to an [OutputStream].
     */
    fun serialize(document: TcxDocument, outputStream: OutputStream) {
        val writer = BufferedWriter(OutputStreamWriter(outputStream, StandardCharsets.UTF_8))
        serialize(document, writer)
        writer.flush()
    }

    /**
     * Serializes a complete [TcxDocument] to a [Writer].
     */
    fun serialize(document: TcxDocument, writer: Writer) {
        writer.write(TCX_SCHEMA_HEADER)
        if (document.activities.isNotEmpty() || document.courses.isEmpty()) {
            writer.write("  <Activities>\n")
            for (activity in document.activities) {
                writeActivity(activity, writer)
            }
            writer.write("  </Activities>\n")
        }
        if (document.courses.isNotEmpty()) {
            writer.write("  <Courses>\n")
            for (course in document.courses) {
                writeCourse(course, writer)
            }
            writer.write("  </Courses>\n")
        }
        document.author?.let { author ->
            writeAuthor(author, writer)
        }
        writer.write("</TrainingCenterDatabase>\n")
    }

    /**
     * Starts streaming TCX document header and opening `<Activities>` container.
     */
    @JvmStatic
    fun startDocument(writer: Writer) {
        writer.write(TCX_SCHEMA_HEADER)
        writer.write("  <Activities>\n")
    }

    /**
     * Writes opening `<Activity>` element with sport and ID.
     */
    @JvmStatic
    fun startActivityHeader(sport: String, id: String, writer: Writer) {
        writer.write("    <Activity Sport=\"${escapeXml(sport)}\">\n")
        writer.write("      <Id>${escapeXml(id)}</Id>\n")
    }

    /**
     * Writes opening `<Lap>` element with mandatory and optional telemetry headers up to opening `<Track>`.
     */
    @JvmStatic
    @JvmOverloads
    fun startLapHeader(
        startTime: String,
        totalTimeSeconds: Double,
        distanceMeters: Double,
        writer: Writer,
        maximumSpeed: Double? = null,
        calories: Int? = null,
        averageHeartRateBpm: Int? = null,
        maximumHeartRateBpm: Int? = null,
        intensity: String? = null,
        cadence: Int? = null,
        triggerMethod: String? = null
    ) {
        writer.write("      <Lap StartTime=\"${escapeXml(startTime)}\">\n")
        writer.write("        <TotalTimeSeconds>$totalTimeSeconds</TotalTimeSeconds>\n")
        writer.write("        <DistanceMeters>$distanceMeters</DistanceMeters>\n")

        maximumSpeed?.let {
            writer.write("        <MaximumSpeed>$it</MaximumSpeed>\n")
        }
        calories?.let {
            writer.write("        <Calories>$it</Calories>\n")
        }
        averageHeartRateBpm?.let {
            writer.write("        <AverageHeartRateBpm xsi:type=\"HeartRateInBeatsPerMinute_t\">\n")
            writer.write("          <Value>$it</Value>\n")
            writer.write("        </AverageHeartRateBpm>\n")
        }
        maximumHeartRateBpm?.let {
            writer.write("        <MaximumHeartRateBpm xsi:type=\"HeartRateInBeatsPerMinute_t\">\n")
            writer.write("          <Value>$it</Value>\n")
            writer.write("        </MaximumHeartRateBpm>\n")
        }
        intensity?.let {
            writer.write("        <Intensity>${escapeXml(it)}</Intensity>\n")
        }
        cadence?.let {
            writer.write("        <Cadence>$it</Cadence>\n")
        }
        triggerMethod?.let {
            writer.write("        <TriggerMethod>${escapeXml(it)}</TriggerMethod>\n")
        }

        writer.write("        <Track>\n")
    }

    /**
     * Closes `<Track>`, writes lap notes and extensions, and closes `</Lap>`.
     */
    @JvmStatic
    @JvmOverloads
    fun endLap(
        writer: Writer,
        notes: String? = null,
        extensions: TcxLapExtensions? = null
    ) {
        writer.write("        </Track>\n")

        notes?.takeIf { it.isNotBlank() }?.let { n ->
            writer.write("        <Notes>${escapeXml(n)}</Notes>\n")
        }

        extensions?.let { ext ->
            val hasName = !ext.name.isNullOrBlank()
            val hasDesc = !ext.description.isNullOrBlank()
            if (hasName || hasDesc) {
                writer.write("        <Extensions>\n")
                writer.write("          <att:LapExtension xmlns:att=\"$EXT_NAMESPACE_ATT\">\n")
                if (hasName) {
                    writer.write("            <att:Name>${escapeXml(ext.name)}</att:Name>\n")
                }
                if (hasDesc) {
                    writer.write("            <att:Description>${escapeXml(ext.description)}</att:Description>\n")
                }
                writer.write("          </att:LapExtension>\n")
                writer.write("        </Extensions>\n")
            }
        }

        writer.write("      </Lap>\n")
    }

    /**
     * Writes activity notes, creator, extensions, and closes `</Activity>`.
     */
    @JvmStatic
    @JvmOverloads
    fun endActivity(
        writer: Writer,
        notes: String? = null,
        creator: TcxCreator? = null,
        extensions: TcxActivityExtensions? = null
    ) {
        notes?.takeIf { it.isNotBlank() }?.let { n ->
            writer.write("      <Notes>${escapeXml(n)}</Notes>\n")
        }

        creator?.let { c ->
            writeCreator(c, writer)
        }

        extensions?.let { ext ->
            val hasName = !ext.customName.isNullOrBlank()
            val hasDesc = !ext.customDescription.isNullOrBlank()
            if (hasName || hasDesc) {
                writer.write("      <Extensions>\n")
                writer.write("        <att:ActivityExtension xmlns:att=\"$EXT_NAMESPACE_ATT\">\n")
                if (hasName) {
                    writer.write("          <att:Name>${escapeXml(ext.customName)}</att:Name>\n")
                }
                if (hasDesc) {
                    writer.write("          <att:Description>${escapeXml(ext.customDescription)}</att:Description>\n")
                }
                writer.write("        </att:ActivityExtension>\n")
                writer.write("      </Extensions>\n")
            }
        }

        writer.write("    </Activity>\n")
    }

    /**
     * Closes `</Activities>`, writes author metadata, and closes `</TrainingCenterDatabase>`.
     */
    @JvmStatic
    @JvmOverloads
    fun endDocument(writer: Writer, author: TcxAuthor? = null) {
        writer.write("  </Activities>\n")
        author?.let { a ->
            writeAuthor(a, writer)
        }
        writer.write("</TrainingCenterDatabase>\n")
    }

    /**
     * Serializes an individual [TcxActivity] within the `<Activities>` block.
     */
    fun writeActivity(activity: TcxActivity, writer: Writer) {
        startActivityHeader(activity.sport, activity.id, writer)
        for (lap in activity.laps) {
            writeLap(lap, writer)
        }
        endActivity(writer, activity.notes, activity.creator, activity.extensions)
    }

    /**
     * Serializes an individual [TcxLap] within an activity.
     */
    fun writeLap(lap: TcxLap, writer: Writer) {
        startLapHeader(
            startTime = lap.startTime,
            totalTimeSeconds = lap.totalTimeSeconds,
            distanceMeters = lap.distanceMeters,
            writer = writer,
            maximumSpeed = lap.maximumSpeed,
            calories = lap.calories,
            averageHeartRateBpm = lap.averageHeartRateBpm,
            maximumHeartRateBpm = lap.maximumHeartRateBpm,
            intensity = lap.intensity,
            cadence = lap.cadence,
            triggerMethod = lap.triggerMethod
        )
        for (tp in lap.trackpoints) {
            writeTrackpoint(tp, writer)
        }
        endLap(writer, lap.notes, lap.extensions)
    }

    /**
     * Serializes an individual [TcxCourse] within the `<Courses>` block.
     */
    fun writeCourse(course: TcxCourse, writer: Writer) {
        writer.write("    <Course>\n")
        writer.write("      <Name>${escapeXml(course.name)}</Name>\n")
        for (lap in course.laps) {
            writeLap(lap, writer)
        }
        if (course.trackpoints.isNotEmpty()) {
            writer.write("      <Track>\n")
            for (tp in course.trackpoints) {
                writeTrackpoint(tp, writer)
            }
            writer.write("      </Track>\n")
        }
        for (cp in course.coursePoints) {
            writeCoursePoint(cp, writer)
        }
        course.notes?.takeIf { it.isNotBlank() }?.let { n ->
            writer.write("      <Notes>${escapeXml(n)}</Notes>\n")
        }
        writer.write("    </Course>\n")
    }

    /**
     * Serializes an individual [TcxCoursePoint] within a course.
     */
    fun writeCoursePoint(cp: TcxCoursePoint, writer: Writer) {
        writer.write("      <CoursePoint>\n")
        writer.write("        <Name>${escapeXml(cp.name)}</Name>\n")
        cp.time?.takeIf { it.isNotBlank() }?.let { t ->
            writer.write("        <Time>${escapeXml(t)}</Time>\n")
        }
        writer.write("        <Position>\n")
        writer.write("          <LatitudeDegrees>${cp.position.latitudeDegrees}</LatitudeDegrees>\n")
        writer.write("          <LongitudeDegrees>${cp.position.longitudeDegrees}</LongitudeDegrees>\n")
        writer.write("        </Position>\n")
        cp.altitudeMeters?.let { alt ->
            writer.write("        <AltitudeMeters>$alt</AltitudeMeters>\n")
        }
        cp.pointType?.takeIf { it.isNotBlank() }?.let { pt ->
            writer.write("        <PointType>${escapeXml(pt)}</PointType>\n")
        }
        cp.notes?.takeIf { it.isNotBlank() }?.let { n ->
            writer.write("        <Notes>${escapeXml(n)}</Notes>\n")
        }
        writer.write("      </CoursePoint>\n")
    }

    /**
     * Serializes an individual [TcxTrackpoint] within a track.
     */
    fun writeTrackpoint(tp: TcxTrackpoint, writer: Writer) {
        writer.write("          <Trackpoint>\n")
        writer.write("            <Time>${escapeXml(tp.time)}</Time>\n")

        tp.position?.let { pos ->
            writer.write("            <Position>\n")
            writer.write("              <LatitudeDegrees>${pos.latitudeDegrees}</LatitudeDegrees>\n")
            writer.write("              <LongitudeDegrees>${pos.longitudeDegrees}</LongitudeDegrees>\n")
            writer.write("            </Position>\n")
        }

        tp.altitudeMeters?.let { alt ->
            if (alt in -500.0..9000.0) {
                writer.write("            <AltitudeMeters>$alt</AltitudeMeters>\n")
            }
        }

        tp.distanceMeters?.let { dist ->
            writer.write("            <DistanceMeters>$dist</DistanceMeters>\n")
        }

        tp.heartRateBpm?.let { hr ->
            writer.write("            <HeartRateBpm xsi:type=\"HeartRateInBeatsPerMinute_t\">\n")
            writer.write("              <Value>$hr</Value>\n")
            writer.write("            </HeartRateBpm>\n")
        }

        tp.cadence?.let { cad ->
            writer.write("            <Cadence>$cad</Cadence>\n")
        }

        tp.extensions?.let { ext ->
            val hasSpeed = ext.speed != null
            val hasWatts = ext.watts != null
            val hasRunCadence = ext.runCadence != null
            if (hasSpeed || hasWatts || hasRunCadence) {
                writer.write("            <Extensions>\n")
                writer.write("              <TPX xmlns=\"$EXT_NAMESPACE_TPX\">\n")
                ext.speed?.let {
                    writer.write("            <Speed>$it</Speed>\n")
                }
                ext.watts?.let {
                    writer.write("            <Watts>$it</Watts>\n")
                }
                ext.runCadence?.let {
                    writer.write("            <RunCadence>$it</RunCadence>\n")
                }
                writer.write("              </TPX>\n")
                writer.write("            </Extensions>\n")
            }
        }

        writer.write("          </Trackpoint>\n")
    }

    /**
     * Serializes the Device_t Creator block.
     */
    fun writeCreator(creator: TcxCreator, writer: Writer) {
        writer.write("      <Creator xsi:type=\"Device_t\">\n")
        writer.write("        <Name>${escapeXml(creator.name)}</Name>\n")
        writer.write("        <UnitId>${creator.unitId}</UnitId>\n")
        writer.write("        <ProductID>${creator.productId}</ProductID>\n")
        writer.write("        <Version>\n")
        writer.write("          <VersionMajor>${creator.versionMajor}</VersionMajor>\n")
        writer.write("          <VersionMinor>${creator.versionMinor}</VersionMinor>\n")
        writer.write("          <BuildMajor>${creator.buildMajor}</BuildMajor>\n")
        writer.write("          <BuildMinor>${creator.buildMinor}</BuildMinor>\n")
        writer.write("        </Version>\n")
        writer.write("      </Creator>\n")
    }

    /**
     * Serializes the Application_t Author block.
     */
    fun writeAuthor(author: TcxAuthor, writer: Writer) {
        writer.write("  <Author xsi:type=\"Application_t\">\n")
        writer.write("    <Name>${escapeXml(author.name)}</Name>\n")
        writer.write("    <Build>\n")
        writer.write("      <Version>\n")
        writer.write("        <VersionMajor>${author.versionMajor}</VersionMajor>\n")
        writer.write("        <VersionMinor>${author.versionMinor}</VersionMinor>\n")
        writer.write("        <BuildMajor>${author.buildMajor}</BuildMajor>\n")
        writer.write("        <BuildMinor>${author.buildMinor}</BuildMinor>\n")
        writer.write("      </Version>\n")
        writer.write("      <Type>${escapeXml(author.type)}</Type>\n")
        writer.write("    </Build>\n")
        writer.write("    <LangID>${escapeXml(author.langId)}</LangID>\n")
        writer.write("    <PartNumber>${escapeXml(author.partNumber)}</PartNumber>\n")
        writer.write("  </Author>\n")
    }
}
