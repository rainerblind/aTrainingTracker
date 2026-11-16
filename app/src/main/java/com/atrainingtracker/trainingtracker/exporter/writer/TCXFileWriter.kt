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

package com.atrainingtracker.trainingtracker.exporter.writer

import android.content.Context
import android.os.Build
import android.util.Log
import com.atrainingtracker.BuildConfig
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.helpers.HavePressureSensor
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.exporter.ExportInfo
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository
import com.atrainingtracker.trainingtracker.tcx.model.TcxActivityExtensions
import com.atrainingtracker.trainingtracker.tcx.model.TcxAuthor
import com.atrainingtracker.trainingtracker.tcx.model.TcxCreator
import com.atrainingtracker.trainingtracker.tcx.model.TcxLap
import com.atrainingtracker.trainingtracker.tcx.model.TcxLapExtensions
import com.atrainingtracker.trainingtracker.tcx.model.TcxPosition
import com.atrainingtracker.trainingtracker.tcx.model.TcxTrackpoint
import com.atrainingtracker.trainingtracker.tcx.model.TcxTrackpointExtensions
import com.atrainingtracker.trainingtracker.tcx.serializer.TcxSerializer
import com.atrainingtracker.trainingtracker.ui.aftermath.LapData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import java.io.BufferedWriter
import java.io.IOException
import java.text.ParseException
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.concurrent.CancellationException
import kotlin.math.roundToInt

/**
 * TCX workout exporter refactored to use structured DTO serialization via [TcxSerializer]
 * and memory-bounded O(1) sample streaming.
 *
 * Traceability:
 * - REQ-ARC-010: Java Core Migration: Streaming File Writers Kotlin Migration & Interoperability Parity.
 * - REQ-DAT-040: TCX Structured DTO Serialization, Parsing Decoupling & Round-Trip Fidelity.
 * - REQ-EXP-001: Export Interoperability & integer Watts/Cadence.
 * - REQ-EXP-022: Garmin TCX v2 sequence ordering, XML escaping, thread safety.
 */
class TCXFileWriter(context: Context) : BaseFileWriter(context) {

    companion object {
        const val WRITE_ONLY_ON_NEW_GEO_DATA: Boolean = true
        private const val TAG: String = "TCXFileExporter"
        private const val DEBUG: Boolean = false

        private val DB_TIME_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)
        private val XML_TIME_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)

        private const val GARMIN_AUTHOR_LANG_ID: String = "en"
        private const val GARMIN_AUTHOR_PART_NUMBER: String = "000-00000-00"
        private const val DEFAULT_VERSION_MAJOR: Int = 4
        private const val DEFAULT_VERSION_MINOR: Int = 9

        @JvmStatic
        @Throws(ParseException::class)
        fun dbTime2XMLTime(dbTime: String): String {
            return try {
                val ldt = LocalDateTime.parse(dbTime, DB_TIME_FORMATTER)
                ldt.atOffset(ZoneOffset.UTC).format(XML_TIME_FORMATTER)
            } catch (e: DateTimeParseException) {
                throw ParseException("Unparseable date: $dbTime", 0)
            }
        }

        @JvmStatic
        fun escapeXml(text: String?): String {
            return TcxSerializer.escapeXml(text)
        }

        @JvmStatic
        fun parseVersionMajor(): Int {
            try {
                val versionName = BuildConfig.VERSION_NAME
                if (!versionName.isNullOrEmpty()) {
                    val parts = versionName.split("[.-]".toRegex())
                    if (parts.isNotEmpty() && parts[0].isNotEmpty()) {
                        return parts[0].toInt()
                    }
                }
            } catch (_: Exception) {
            }
            return DEFAULT_VERSION_MAJOR
        }

        @JvmStatic
        fun parseVersionMinor(): Int {
            try {
                val versionName = BuildConfig.VERSION_NAME
                if (!versionName.isNullOrEmpty()) {
                    val parts = versionName.split("[.-]".toRegex())
                    if (parts.size > 1 && parts[1].isNotEmpty()) {
                        return parts[1].toInt()
                    }
                }
            } catch (_: Exception) {
            }
            return DEFAULT_VERSION_MINOR
        }
    }

    @Throws(IOException::class, ParseException::class)
    public override fun doExport(exportInfo: ExportInfo): ExportResult {
        if (DEBUG) Log.d(TAG, "exportToFile")

        getHeaderData(exportInfo)

        try {
            val lapsList: List<LapData> = WorkoutRepository.getInstance(mContext).getLaps(workoutID) ?: emptyList()
            val lapDataMap = HashMap<Long, LapData>()
            for (lapData in lapsList) {
                lapDataMap[lapData.lapNr] = lapData
            }

            val currentWorkoutName = workoutName
            val activityWorkoutName = if (!currentWorkoutName.isNullOrBlank() && currentWorkoutName != exportInfo.fileBaseName) {
                currentWorkoutName.trim()
            } else {
                null
            }

            val currentDesc = description
            val activityDesc = if (!currentDesc.isNullOrBlank()) {
                currentDesc.trim()
            } else {
                null
            }

            val activityNotes = when {
                activityWorkoutName != null && activityDesc != null -> "[$activityWorkoutName] $activityDesc"
                activityWorkoutName != null -> "[$activityWorkoutName]"
                else -> activityDesc
            }

            val actExt = if (activityWorkoutName != null || activityDesc != null) {
                TcxActivityExtensions(activityWorkoutName, activityDesc)
            } else {
                null
            }

            val creator = TcxCreator(
                "${Build.MANUFACTURER} ${Build.MODEL}",
                0L,
                1,
                parseVersionMajor(),
                parseVersionMinor(),
                BuildConfig.VERSION_CODE,
                0
            )

            var authorName = TrainingApplication.getAppName()
            if (HavePressureSensor.havePressureSensor(mContext)) {
                authorName += " with barometer"
            }
            val author = TcxAuthor(
                authorName,
                parseVersionMajor(),
                parseVersionMinor(),
                BuildConfig.VERSION_CODE,
                0,
                "Release",
                GARMIN_AUTHOR_LANG_ID,
                GARMIN_AUTHOR_PART_NUMBER
            )

            getBufferedWriter(exportInfo.shortPath).use { bufferedWriter ->
                TcxSerializer.startDocument(bufferedWriter)
                val tcxSport = SportTypesRepository.getInstance(mContext).getTcxName(sportTypeId) ?: ""
                val actId = dbTime2XMLTime(startTime ?: "")
                TcxSerializer.startActivityHeader(tcxSport, actId, bufferedWriter)

                val prevLineLap = longArrayOf(BANALService.INIT_LAP_NR - 1L)
                val currentLapNotes = arrayOf<String?>(null)
                val currentLapExt = arrayOf<TcxLapExtensions?>(null)
                val latitude = doubleArrayOf(0.0)
                val longitude = doubleArrayOf(0.0)

                WorkoutRepository.getInstance(mContext).streamSamples(exportInfo.fileBaseName) { sample ->
                    if (isCancelled) {
                        throw CancellationException("TCX export cancelled at start of loop iteration")
                    }

                    try {
                        val lap = sample.lapNr
                        if (prevLineLap[0] != lap.toLong()) { // new lap
                            if (prevLineLap[0] != BANALService.INIT_LAP_NR - 1L) { // finish previous lap
                                TcxSerializer.endLap(bufferedWriter, currentLapNotes[0], currentLapExt[0])
                            }

                            // get the lap data
                            var lapData = lapDataMap[lap.toLong()]
                            if (lapData == null) {
                                lapData = WorkoutRepository.getInstance(mContext).getLap(workoutID, lap.toLong())
                            }

                            val lapTotalTime = lapData?.timeTotalS?.toDouble() ?: 0.0
                            val lapTotalDist = lapData?.distanceTotalM ?: 0.0

                            var lapStartTime = ""
                            if (sample.time != null) {
                                try {
                                    lapStartTime = dbTime2XMLTime(sample.time)
                                } catch (_: ParseException) {
                                    lapStartTime = ""
                                }
                            }

                            currentLapNotes[0] = null
                            currentLapExt[0] = null
                            if (lapData != null) {
                                val name = lapData.name
                                val desc = lapData.description
                                val hasName = !name.isNullOrBlank()
                                val hasDesc = !desc.isNullOrBlank()

                                if (hasName || hasDesc) {
                                    currentLapNotes[0] = when {
                                        hasName && hasDesc -> "[${name!!.trim()}] ${desc!!.trim()}"
                                        hasName -> "[${name!!.trim()}]"
                                        else -> desc!!.trim()
                                    }
                                    currentLapExt[0] = TcxLapExtensions(
                                        if (hasName) name!!.trim() else null,
                                        if (hasDesc) desc!!.trim() else null
                                    )
                                }
                            }

                            TcxSerializer.startLapHeader(
                                lapStartTime,
                                lapTotalTime,
                                lapTotalDist,
                                bufferedWriter,
                                null, null, null, null, null, null, null
                            )
                        }
                        prevLineLap[0] = lap.toLong()

                        var tpTime = ""
                        if (sample.time != null) {
                            try {
                                tpTime = dbTime2XMLTime(sample.time)
                            } catch (_: ParseException) {
                                tpTime = ""
                            }
                        }

                        var position: TcxPosition? = null
                        // we do not write location data when it was a (indoor) trainer session
                        if (!indoorTrainerSession && haveGeo && sample.latitude != null && sample.longitude != null) {
                            val latitudeOld = latitude[0]
                            val longitudeOld = longitude[0]
                            latitude[0] = sample.latitude
                            longitude[0] = sample.longitude

                            if (!(WRITE_ONLY_ON_NEW_GEO_DATA && latitude[0] == latitudeOld && longitude[0] == longitudeOld)) {
                                position = TcxPosition(latitude[0], longitude[0])
                            }
                        }

                        var altitude: Double? = null
                        if (haveAltitude && sample.altitude != null) {
                            val alt = sample.altitude
                            if (alt >= -500.0 && alt <= 9000.0) {
                                altitude = alt
                            }
                        }

                        val distance: Double? = if (haveDistance && sample.distanceM != null) {
                            sample.distanceM
                        } else {
                            null
                        }

                        val hrVal: Int? = if (haveHR && sample.hr != null) {
                            sample.hr
                        } else {
                            null
                        }

                        val cadenceVal: Int? = if (haveBikeCadence && sample.cadence != null) {
                            sample.cadence.roundToInt()
                        } else {
                            null
                        }

                        val speedVal: Double? = if (haveSpeed && sample.speedMps != null) {
                            sample.speedMps
                        } else {
                            null
                        }

                        val powerVal: Int? = if (havePower && sample.power != null) {
                            sample.power.roundToInt()
                        } else {
                            null
                        }

                        val runCadenceVal: Int? = if (haveRunCadence && sample.cadence != null) {
                            sample.cadence.roundToInt()
                        } else {
                            null
                        }

                        val tpExt = if (speedVal != null || powerVal != null || runCadenceVal != null) {
                            TcxTrackpointExtensions(speedVal, powerVal, runCadenceVal)
                        } else {
                            null
                        }

                        TcxSerializer.writeTrackpoint(
                            TcxTrackpoint(
                                tpTime,
                                position,
                                altitude,
                                distance,
                                hrVal,
                                cadenceVal,
                                tpExt
                            ),
                            bufferedWriter
                        )

                        if (isCancelled) {
                            throw CancellationException("TCX export cancelled post-I/O")
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        throw RuntimeException(e)
                    }
                }

                if (prevLineLap[0] != BANALService.INIT_LAP_NR - 1L) {
                    TcxSerializer.endLap(bufferedWriter, currentLapNotes[0], currentLapExt[0])
                }

                TcxSerializer.endActivity(bufferedWriter, activityNotes, creator, actExt)
                TcxSerializer.endDocument(bufferedWriter, author)
                bufferedWriter.flush()
            }
        } catch (e: CancellationException) {
            deleteOutputFileSafely(exportInfo)
            throw e
        } catch (e: Exception) {
            deleteOutputFileSafely(exportInfo)
            val cause = e.cause
            if (cause is IOException) {
                throw cause
            } else if (e is IOException) {
                throw e
            } else {
                throw IOException("Failed to export TCX", e)
            }
        }

        return ExportResult(true, false, "Successfully exported to TCX File")
    }

    private fun buildLap(
        lapNr: Long,
        startTime: String?,
        totalTime: String?,
        totalDistance: String?,
        trackpoints: List<TcxTrackpoint>,
        lapDataMap: Map<Long, LapData>
    ): TcxLap {
        var lapNotes: String? = null
        var lapExt: TcxLapExtensions? = null

        val lapData = lapDataMap[lapNr]
        if (lapData != null) {
            val name = lapData.name
            val desc = lapData.description
            val hasName = !name.isNullOrBlank()
            val hasDesc = !desc.isNullOrBlank()

            if (hasName || hasDesc) {
                lapNotes = when {
                    hasName && hasDesc -> "[${name!!.trim()}] ${desc!!.trim()}"
                    hasName -> "[${name!!.trim()}]"
                    else -> desc!!.trim()
                }
                lapExt = TcxLapExtensions(
                    if (hasName) name!!.trim() else null,
                    if (hasDesc) desc!!.trim() else null
                )
            }
        }

        var totalTimeSec = 0.0
        try {
            totalTimeSec = totalTime?.toDouble() ?: 0.0
        } catch (_: Exception) {
        }

        var totalDistM = 0.0
        try {
            totalDistM = totalDistance?.toDouble() ?: 0.0
        } catch (_: Exception) {
        }

        return TcxLap(
            startTime ?: "",
            totalTimeSec,
            totalDistM,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            trackpoints,
            lapNotes,
            lapExt
        )
    }
}
