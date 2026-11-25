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
import android.util.Log
import com.atrainingtracker.BuildConfig
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.database.DatabaseConstants
import com.atrainingtracker.trainingtracker.database.ExtremaType
import com.atrainingtracker.trainingtracker.database.hrv.WorkoutRrInterval
import com.atrainingtracker.trainingtracker.database.hrv.WorkoutRrRepository
import com.atrainingtracker.trainingtracker.exporter.ExportInfo
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository
import com.atrainingtracker.trainingtracker.settings.PoolLengthUnit
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper
import com.atrainingtracker.trainingtracker.ui.aftermath.LapData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutSampleRecord
import com.garmin.fit.Activity
import com.garmin.fit.ActivityMesg
import com.garmin.fit.DateTime
import com.garmin.fit.DeveloperDataIdMesg
import com.garmin.fit.DeveloperField
import com.garmin.fit.DeviceIndex
import com.garmin.fit.DeviceInfoMesg
import com.garmin.fit.DisplayMeasure
import com.garmin.fit.Event
import com.garmin.fit.EventMesg
import com.garmin.fit.EventType
import com.garmin.fit.FieldDescriptionMesg
import com.garmin.fit.FileEncoder
import com.garmin.fit.FileIdMesg
import com.garmin.fit.Fit
import com.garmin.fit.HrvMesg
import com.garmin.fit.Intensity
import com.garmin.fit.LapMesg
import com.garmin.fit.LeftRightBalance
import com.garmin.fit.LeftRightBalance100
import com.garmin.fit.Manufacturer
import com.garmin.fit.RadarThreatLevelType
import com.garmin.fit.RecordMesg
import com.garmin.fit.SessionMesg
import com.garmin.fit.Sport
import com.garmin.fit.SubSport
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.text.ParseException
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CancellationException
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Inline resource management extension for [FileEncoder], providing scoped `.use {}`
 * cleanup for Garmin FIT SDK encoding streams.
 */
inline fun <R> FileEncoder.use(block: (FileEncoder) -> R): R {
    var exception: Exception? = null
    try {
        return block(this)
    } catch (e: Exception) {
        exception = e
        throw e
    } finally {
        try {
            close()
        } catch (e: Exception) {
            if (exception != null) {
                exception.addSuppressed(e)
            } else {
                throw e
            }
        }
    }
}

/**
 * High-fidelity binary workout exporter implementing the Garmin FIT protocol
 * utilizing the official Garmin FIT SDK (com.garmin:fit).
 * Encodes FileIdMesg, RecordMesg trackpoints, LapMesg splits, SessionMesg summaries,
 * and ActivityMesg termination conforming to FIT Protocol V2.0.
 *
 * Traceability:
 * - REQ-ARC-010: Java Core Migration: Streaming File Writers Kotlin Migration & Interoperability Parity.
 * - REQ-DAT-042.5: Zero-SQLite Exporter Invariant & O(1) Memory Streaming via WorkoutRepository.
 * - REQ-EXP-043: Cycling Pedal Dynamics Serialization & Online Accumulator.
 * - REQ-EXP-042: Bike Radar Event & Developer Fields Serialization.
 * - REQ-MIG-053: Lossless FIT Metadata Developer Fields Serialization.
 */
class FitFileWriter(context: Context) : BaseFileWriter(context.applicationContext ?: context) {

    companion object {
        private const val TAG: String = "FitFileWriter"
        private const val DEBUG: Boolean = false

        const val CREATOR_PRODUCT_NAME: String = "aTrainingTracker"
        internal const val MAX_FIT_SOFTWARE_VERSION: Float = 655.34f

        private const val SEMICIRCLES_FACTOR: Double = 2147483648.0 / 180.0
        private const val CM_TO_MM: Double = 10.0
        private val DATE_TIME_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)

        /**
         * Parses an app version string (e.g. `"5.0.0-dev"`, `"5.12.3"`) into a FIT
         * `DeviceInfoMesg.software_version` float (`UINT16`, `scale = 100`, bounded to
         * `[0.0f, 655.34f]` so `0xFFFF` `UINT16_INVALID` is never emitted; REQ-EXP-051.3).
         */
        internal fun parseSoftwareVersion(versionName: String? = BuildConfig.VERSION_NAME): Float {
            if (versionName.isNullOrBlank()) return 0.0f
            val parts = versionName.trim().split(Regex("[.-]"))
            val major = parts.getOrNull(0)?.toIntOrNull()?.takeIf { it >= 0 } ?: return 0.0f
            val minor = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it >= 0 }
            val candidate = if (minor != null) "$major.$minor".toFloatOrNull() else major.toFloat()
            if (candidate == null || candidate.isNaN() || candidate < 0.0f) return 0.0f
            return candidate.coerceAtMost(MAX_FIT_SOFTWARE_VERSION)
        }

        /**
         * 16-byte MyBikeTraffic / Bike Radar Connect-IQ application ID
         * (`6a4e3200-667b-11e3-949a-0800200c9a66`) scoping `RecordMesg` radar developer fields
         * (`REQ-EXP-042`, `REQ-MIG-058.1`).
         */
        @JvmField
        val RADAR_DEVELOPER_DATA_APP_ID: ByteArray = ByteBuffer.allocate(16).apply {
            val appIdUuid = UUID.fromString("6a4e3200-667b-11e3-949a-0800200c9a66")
            putLong(appIdUuid.mostSignificantBits)
            putLong(appIdUuid.leastSignificantBits)
        }.array()

        /**
         * Backward-compatibility 16-byte radar application ID produced by historical pre-`ATT-8127`
         * exports where Garmin FIT SDK's `setApplicationId(int, Byte)` clamped signed bytes `>= 0x80`
         * (`0xe3`, `0x94`, `0x9a`) to `Fit.BYTE_INVALID` (`0xFF`) (`REQ-MIG-058.1`).
         */
        @JvmField
        val LEGACY_RADAR_DEVELOPER_DATA_APP_ID: ByteArray = ByteArray(16) { i ->
            val b = RADAR_DEVELOPER_DATA_APP_ID[i]
            if (b < 0) 0xFF.toByte() else b
        }

        @JvmField
        val APP_METADATA_DEVELOPER_DATA_ID: ByteArray = byteArrayOf(
            0x54, 0x72, 0x61, 0x69, 0x6e, 0x69, 0x6e, 0x67,
            0x54, 0x72, 0x61, 0x63, 0x6b, 0x65, 0x72, 0x01
        )

        /**
         * Encodes a 16-byte FIT `application_id` as unsigned `0..255` values so Garmin FIT SDK's
         * `BASE_TYPE_BYTE` range check (`0..255`) preserves bytes `>= 0x80` without clamping to `0xFF`.
         */
        internal fun writeApplicationIdUnsigned(mesg: DeveloperDataIdMesg, appId: ByteArray) {
            for (i in appId.indices) {
                mesg.setFieldValue(
                    DeveloperDataIdMesg.ApplicationIdFieldNum,
                    i,
                    (appId[i].toInt() and 0xFF).toShort(),
                    Fit.SUBFIELD_INDEX_MAIN_FIELD
                )
            }
        }

        private fun parseUtcTimestamp(timeStr: String?): Date? {
            if (timeStr.isNullOrBlank()) {
                return null
            }
            val ldt = LocalDateTime.parse(timeStr.trim(), DATE_TIME_FORMATTER)
            return Date(ldt.toInstant(ZoneOffset.UTC).toEpochMilli())
        }

        private fun writeHrvMesg(encoder: FileEncoder, intervalsSec: List<Float>) {
            if (intervalsSec.isEmpty()) return
            val hrvMesg = HrvMesg()
            for (i in intervalsSec.indices) {
                hrvMesg.setTime(i, intervalsSec[i])
            }
            encoder.write(hrvMesg)
        }

        private fun applyPedalDynamicsToLap(lapMesg: LapMesg, acc: PedalDynamicsAccumulator?) {
            if (acc != null) {
                if (acc.balanceCount > 0) {
                    val avgRight = acc.balanceSum / acc.balanceCount
                    val encoded100 = ((avgRight * 100.0).roundToInt() and LeftRightBalance100.MASK.toInt()) or LeftRightBalance100.RIGHT.toInt()
                    lapMesg.setLeftRightBalance(encoded100)
                }
                if (acc.teLCount > 0) {
                    lapMesg.setAvgLeftTorqueEffectiveness((acc.teLSum / acc.teLCount).toFloat())
                }
                if (acc.teRCount > 0) {
                    lapMesg.setAvgRightTorqueEffectiveness((acc.teRSum / acc.teRCount).toFloat())
                }
                if (acc.psLCount > 0) {
                    lapMesg.setAvgLeftPedalSmoothness((acc.psLSum / acc.psLCount).toFloat())
                }
                if (acc.psRCount > 0) {
                    lapMesg.setAvgRightPedalSmoothness((acc.psRSum / acc.psRCount).toFloat())
                }
                if (acc.psCombCount > 0) {
                    lapMesg.setAvgCombinedPedalSmoothness((acc.psCombSum / acc.psCombCount).toFloat())
                }
            }
        }

        private fun applyPedalDynamicsToSession(sessionMesg: SessionMesg, acc: PedalDynamicsAccumulator?) {
            if (acc != null) {
                if (acc.balanceCount > 0) {
                    val avgRight = acc.balanceSum / acc.balanceCount
                    val encoded100 = ((avgRight * 100.0).roundToInt() and LeftRightBalance100.MASK.toInt()) or LeftRightBalance100.RIGHT.toInt()
                    sessionMesg.setLeftRightBalance(encoded100)
                }
                if (acc.teLCount > 0) {
                    sessionMesg.setAvgLeftTorqueEffectiveness((acc.teLSum / acc.teLCount).toFloat())
                }
                if (acc.teRCount > 0) {
                    sessionMesg.setAvgRightTorqueEffectiveness((acc.teRSum / acc.teRCount).toFloat())
                }
                if (acc.psLCount > 0) {
                    sessionMesg.setAvgLeftPedalSmoothness((acc.psLSum / acc.psLCount).toFloat())
                }
                if (acc.psRCount > 0) {
                    sessionMesg.setAvgRightPedalSmoothness((acc.psRSum / acc.psRCount).toFloat())
                }
                if (acc.psCombCount > 0) {
                    sessionMesg.setAvgCombinedPedalSmoothness((acc.psCombSum / acc.psCombCount).toFloat())
                }
            }
        }

        private fun applyRunningDynamicsToLap(lapMesg: LapMesg, acc: RunningDynamicsAccumulator?) {
            if (acc == null) return
            if (acc.voCount > 0) {
                val avgVoMm = ((acc.voSumCm / acc.voCount) * CM_TO_MM).toFloat()
                lapMesg.setAvgVerticalOscillation(avgVoMm)
            }
            if (acc.gctCount > 0) {
                val avgGctMs = (acc.gctSumMs / acc.gctCount).toFloat()
                lapMesg.setAvgStanceTime(avgGctMs)
            }
        }

        private fun applyRunningDynamicsToSession(
            sessionMesg: SessionMesg,
            acc: RunningDynamicsAccumulator?,
            avgVoFallbackCm: Double?,
            avgGctFallbackMs: Double?
        ) {
            val voCm = if (acc != null && acc.voCount > 0) {
                acc.voSumCm / acc.voCount
            } else {
                avgVoFallbackCm
            }
            if (voCm != null && !voCm.isNaN() && voCm > 0.0) {
                sessionMesg.setAvgVerticalOscillation((voCm * CM_TO_MM).toFloat())
            }
            val gctMs = if (acc != null && acc.gctCount > 0) {
                acc.gctSumMs / acc.gctCount
            } else {
                avgGctFallbackMs
            }
            if (gctMs != null && !gctMs.isNaN() && gctMs > 0.0) {
                sessionMesg.setAvgStanceTime(gctMs.toFloat())
            }
        }
    }

    @Throws(IOException::class, ParseException::class)
    public override fun doExport(exportInfo: ExportInfo): ExportResult {
        if (DEBUG) Log.d(TAG, "doExport: ${exportInfo.fileBaseName}")

        getHeaderData(exportInfo)

        // Pre-flight header validation: fail-fast on invalid ground-truth parameters
        val curStartTime = startTime
        if (curStartTime.isNullOrBlank()) {
            return ExportResult(false, false, "FIT export failed: missing or empty startTime")
        }
        val startDate: Date = try {
            parseUtcTimestamp(curStartTime) ?: return ExportResult(false, false, "FIT export failed: empty startTime")
        } catch (_: DateTimeParseException) {
            return ExportResult(false, false, "FIT export failed: unparseable startTime: $curStartTime")
        }

        var totalTimeSec = 0.0
        try {
            val curTotalTime = totalTime
            if (!curTotalTime.isNullOrBlank()) {
                totalTimeSec = curTotalTime.toDouble()
            }
        } catch (_: NumberFormatException) {
            return ExportResult(false, false, "FIT export failed: invalid totalTime: $totalTime")
        }
        if (totalTimeSec < 0.0) {
            return ExportResult(false, false, "FIT export failed: negative totalTime: $totalTimeSec")
        }

        var distTotal = 0.0
        try {
            val curTotalDist = totalDistance
            if (!curTotalDist.isNullOrBlank()) {
                distTotal = curTotalDist.toDouble()
            }
        } catch (e: NumberFormatException) {
            Log.w(TAG, "Failed to parse totalDistance: $totalDistance, falling back to 0.0", e)
        }
        if (distTotal < 0.0) distTotal = 0.0

        val outputFile = File(getBaseDirFile(mContext), exportInfo.shortPath)
        val parentDir = outputFile.parentFile
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                Log.e(TAG, "Failed to create directory: ${parentDir.absolutePath}")
            }
        }

        var encoder: FileEncoder? = null
        var success = false
        var errorMessage: String? = null

        try {
            encoder = FileEncoder(outputFile, Fit.ProtocolVersion.V2_0)

            // 1. FileIdMesg (mandatory first message)
            val fileIdMesg = FileIdMesg()
            fileIdMesg.setType(com.garmin.fit.File.ACTIVITY)
            fileIdMesg.setManufacturer(Manufacturer.DEVELOPMENT)
            fileIdMesg.setProduct(1)
            fileIdMesg.setSerialNumber(1L)
            fileIdMesg.setTimeCreated(DateTime(startDate))
            fileIdMesg.setProductName(CREATOR_PRODUCT_NAME)
            encoder.write(fileIdMesg)

            // 1b. Creator DeviceInfoMesg (REQ-EXP-051.3 / ATT-8082)
            val creatorDeviceInfo = DeviceInfoMesg().apply {
                setTimestamp(DateTime(startDate))
                setDeviceIndex(DeviceIndex.CREATOR)
                setManufacturer(Manufacturer.DEVELOPMENT)
                setProduct(1)
                setSerialNumber(1L)
                setProductName(CREATOR_PRODUCT_NAME)
                val swVersion = parseSoftwareVersion(BuildConfig.VERSION_NAME)
                if (swVersion > 0.0f) {
                    setSoftwareVersion(swVersion)
                }
            }
            encoder.write(creatorDeviceInfo)

            val isStravaExport = exportInfo.fileFormat == FileFormat.STRAVA
            val includeHrData = !isStravaExport || haveHR
            val includePowerData = !isStravaExport || havePower
            val includeCadenceData = !isStravaExport || haveCadence
            val includeAltitudeData = !isStravaExport || haveAltitude

            // Determine sport mapping
            var sport = Sport.GENERIC
            var subSport = SubSport.GENERIC

            val sportTypesRepo = SportTypesRepository.getInstance(mContext)
            val bSport = sportTypesRepo.getBSportType(sportTypeId)
            val tcxName = sportTypesRepo.getTcxName(sportTypeId) ?: ""
            val stravaName = sportTypesRepo.getStravaName(sportTypeId) ?: ""
            val gcName = sportTypesRepo.getGcName(sportTypeId) ?: ""
            val tcxLower = tcxName.lowercase(Locale.US)
            val isSwimmingSport = tcxLower.contains("swim")
                || "Swim".equals(stravaName.trim(), ignoreCase = true)
                || "swim".equals(gcName.trim(), ignoreCase = true)

            var sessionPoolLengthMeters: Float? = null
            var sessionPoolLengthUnit: DisplayMeasure? = null

            if (isSwimmingSport) {
                sport = Sport.SWIMMING
                val isIndoorSwim = indoorTrainerSession || sportTypesRepo.isIndoorSportType(sportTypeId)
                val configuredPoolLengthMeters = SettingsDataStoreJavaHelper.getDefaultPoolLengthMeters(mContext)
                if (isIndoorSwim || configuredPoolLengthMeters > 0.0f) {
                    subSport = SubSport.LAP_SWIMMING
                    if (configuredPoolLengthMeters > 0.0f) {
                        sessionPoolLengthMeters = configuredPoolLengthMeters
                        val unit = SettingsDataStoreJavaHelper.getDefaultPoolLengthUnit(mContext)
                        sessionPoolLengthUnit = if (unit == PoolLengthUnit.YARDS) {
                            DisplayMeasure.STATUTE
                        } else {
                            DisplayMeasure.METRIC
                        }
                    }
                }
            } else if (bSport == BSportType.BIKE) {
                sport = Sport.CYCLING
                if (indoorTrainerSession) {
                    subSport = SubSport.INDOOR_CYCLING
                }
            } else if (bSport == BSportType.RUN) {
                if (tcxLower.contains("walk") || tcxLower.contains("hike")) {
                    sport = Sport.WALKING
                } else {
                    sport = Sport.RUNNING
                    if (indoorTrainerSession) {
                        subSport = SubSport.TREADMILL
                    }
                }
            } else if (indoorTrainerSession) {
                sport = Sport.FITNESS_EQUIPMENT
            }

            // 2. Iterate samples and write RecordMesg entries
            var resolvedWorkoutId = this.workoutID
            if (resolvedWorkoutId <= 0) {
                resolvedWorkoutId = WorkoutRepository.getInstance(mContext).getWorkoutIdByBaseFileName(exportInfo.fileBaseName)
            }

            var rrIntervals: List<WorkoutRrInterval>? = null
            if (includeHrData && resolvedWorkoutId > 0) {
                try {
                    rrIntervals = WorkoutRrRepository.getInstance(mContext).getIntervalsForWorkoutBlocking(resolvedWorkoutId)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not query RR intervals for workout $resolvedWorkoutId: ${e.message}")
                }
            }
            val rrIntervalIndex = intArrayOf(0)
            val totalRr = rrIntervals?.size ?: 0
            val finalRrIntervals = rrIntervals

            val firstSampleDate = arrayOf<Date?>(null)
            val lastSampleDate = arrayOf<Date?>(null)
            val sampleCount = longArrayOf(0L)
            val activeEncoder = encoder

            // Determine if radar metrics are present in this workout (REQ-EXP-042 / ATT-5088)
            val haveRadar = resolvedWorkoutId > 0 && WorkoutRepository.getInstance(mContext).hasRadarData(resolvedWorkoutId, exportInfo.fileBaseName)
            var devDataIdMesg: DeveloperDataIdMesg? = null
            var radarCurrentDesc: FieldDescriptionMesg? = null
            var radarTotalDesc: FieldDescriptionMesg? = null
            var passingSpeedDesc: FieldDescriptionMesg? = null
            var passingSpeedAbsDesc: FieldDescriptionMesg? = null
            var passingDistDesc: FieldDescriptionMesg? = null
            var largeVehCountDesc: FieldDescriptionMesg? = null

            if (haveRadar) {
                devDataIdMesg = DeveloperDataIdMesg().apply {
                    developerDataIndex = 0.toShort()
                    manufacturerId = Manufacturer.DEVELOPMENT
                    applicationVersion = 1L
                    writeApplicationIdUnsigned(this, RADAR_DEVELOPER_DATA_APP_ID)
                }
                activeEncoder.write(devDataIdMesg)

                radarCurrentDesc = FieldDescriptionMesg().apply {
                    developerDataIndex = 0.toShort()
                    fieldDefinitionNumber = 0.toShort()
                    fitBaseTypeId = Fit.BASE_TYPE_UINT8.toShort()
                    setFieldName(0, "radar_current")
                    scale = 1.toShort()
                    setUnits(0, "targets")
                }
                activeEncoder.write(radarCurrentDesc)

                radarTotalDesc = FieldDescriptionMesg().apply {
                    developerDataIndex = 0.toShort()
                    fieldDefinitionNumber = 1.toShort()
                    fitBaseTypeId = Fit.BASE_TYPE_UINT16.toShort()
                    setFieldName(0, "radar_total")
                    scale = 1.toShort()
                    setUnits(0, "vehicles")
                }
                activeEncoder.write(radarTotalDesc)

                passingSpeedDesc = FieldDescriptionMesg().apply {
                    developerDataIndex = 0.toShort()
                    fieldDefinitionNumber = 2.toShort()
                    fitBaseTypeId = Fit.BASE_TYPE_UINT16.toShort()
                    setFieldName(0, "passing_speed")
                    scale = 100.toShort()
                    setUnits(0, "m/s")
                }
                activeEncoder.write(passingSpeedDesc)

                passingSpeedAbsDesc = FieldDescriptionMesg().apply {
                    developerDataIndex = 0.toShort()
                    fieldDefinitionNumber = 3.toShort()
                    fitBaseTypeId = Fit.BASE_TYPE_UINT16.toShort()
                    setFieldName(0, "passing_speed_abs")
                    scale = 100.toShort()
                    setUnits(0, "m/s")
                }
                activeEncoder.write(passingSpeedAbsDesc)

                passingDistDesc = FieldDescriptionMesg().apply {
                    developerDataIndex = 0.toShort()
                    fieldDefinitionNumber = 4.toShort()
                    fitBaseTypeId = Fit.BASE_TYPE_UINT16.toShort()
                    setFieldName(0, "passing_distance")
                    scale = 100.toShort()
                    setUnits(0, "m")
                }
                activeEncoder.write(passingDistDesc)

                largeVehCountDesc = FieldDescriptionMesg().apply {
                    developerDataIndex = 0.toShort()
                    fieldDefinitionNumber = 5.toShort()
                    fitBaseTypeId = Fit.BASE_TYPE_UINT16.toShort()
                    setFieldName(0, "large_vehicle_count")
                    scale = 1.toShort()
                    setUnits(0, "vehicles")
                }
                activeEncoder.write(largeVehCountDesc)
            }

            // Write Application Metadata Developer Data ID & Field Descriptions (REQ-MIG-053 / ATT-7747)
            val metaDevDataIdMesg = DeveloperDataIdMesg().apply {
                developerDataIndex = 1.toShort()
                manufacturerId = Manufacturer.DEVELOPMENT
                applicationVersion = 1L
                writeApplicationIdUnsigned(this, APP_METADATA_DEVELOPER_DATA_ID)
            }
            activeEncoder.write(metaDevDataIdMesg)

            val workoutNameDesc = FieldDescriptionMesg().apply {
                developerDataIndex = 1.toShort()
                fieldDefinitionNumber = 0.toShort()
                fitBaseTypeId = Fit.BASE_TYPE_STRING.toShort()
                setFieldName(0, "workout_name")
            }
            activeEncoder.write(workoutNameDesc)

            val notesDesc = FieldDescriptionMesg().apply {
                developerDataIndex = 1.toShort()
                fieldDefinitionNumber = 1.toShort()
                fitBaseTypeId = Fit.BASE_TYPE_STRING.toShort()
                setFieldName(0, "notes")
            }
            activeEncoder.write(notesDesc)

            val equipmentNameDesc = FieldDescriptionMesg().apply {
                developerDataIndex = 1.toShort()
                fieldDefinitionNumber = 2.toShort()
                fitBaseTypeId = Fit.BASE_TYPE_STRING.toShort()
                setFieldName(0, "equipment_name")
            }
            activeEncoder.write(equipmentNameDesc)

            val methodDesc = FieldDescriptionMesg().apply {
                developerDataIndex = 1.toShort()
                fieldDefinitionNumber = 3.toShort()
                fitBaseTypeId = Fit.BASE_TYPE_STRING.toShort()
                setFieldName(0, "method")
            }
            activeEncoder.write(methodDesc)

            val goalDesc = FieldDescriptionMesg().apply {
                developerDataIndex = 1.toShort()
                fieldDefinitionNumber = 4.toShort()
                fitBaseTypeId = Fit.BASE_TYPE_STRING.toShort()
                setFieldName(0, "goal")
            }
            activeEncoder.write(goalDesc)

            val isCommuteDesc = FieldDescriptionMesg().apply {
                developerDataIndex = 1.toShort()
                fieldDefinitionNumber = 5.toShort()
                fitBaseTypeId = Fit.BASE_TYPE_UINT8.toShort()
                setFieldName(0, "is_commute")
            }
            activeEncoder.write(isCommuteDesc)

            val isTrainerDesc = FieldDescriptionMesg().apply {
                developerDataIndex = 1.toShort()
                fieldDefinitionNumber = 6.toShort()
                fitBaseTypeId = Fit.BASE_TYPE_UINT8.toShort()
                setFieldName(0, "is_trainer")
            }
            activeEncoder.write(isTrainerDesc)

            val isRaceDesc = FieldDescriptionMesg().apply {
                developerDataIndex = 1.toShort()
                fieldDefinitionNumber = 7.toShort()
                fitBaseTypeId = Fit.BASE_TYPE_UINT8.toShort()
                setFieldName(0, "is_race")
            }
            activeEncoder.write(isRaceDesc)

            val isEpicDesc = FieldDescriptionMesg().apply {
                developerDataIndex = 1.toShort()
                fieldDefinitionNumber = 8.toShort()
                fitBaseTypeId = Fit.BASE_TYPE_UINT8.toShort()
                setFieldName(0, "is_epic")
            }
            activeEncoder.write(isEpicDesc)

            val finalHaveRadar = haveRadar
            val finalDevDataIdMesg = devDataIdMesg
            val finalRadarCurrentDesc = radarCurrentDesc
            val finalRadarTotalDesc = radarTotalDesc
            val finalPassingSpeedDesc = passingSpeedDesc
            val finalPassingSpeedAbsDesc = passingSpeedAbsDesc
            val finalPassingDistDesc = passingDistDesc
            val finalLargeVehCountDesc = largeVehCountDesc
            val prevVehCount = intArrayOf(0)
            val sessionPedalAcc = PedalDynamicsAccumulator()
            val lapPedalAccMap = HashMap<Int, PedalDynamicsAccumulator>()
            val sessionRdAcc = RunningDynamicsAccumulator()
            val lapRdAccMap = HashMap<Int, RunningDynamicsAccumulator>()

            try {
                WorkoutRepository.getInstance(mContext).streamSamples(exportInfo.fileBaseName) { sample ->
                    if (isCancelled) {
                        throw CancellationException("FIT export cancelled at start of loop iteration")
                    }
                    val record = RecordMesg()

                    val sampleDate: Date = if (!sample.time.isNullOrEmpty()) {
                        try {
                            parseUtcTimestamp(sample.time) ?: Date(startDate.time + sampleCount[0] * 1000L)
                        } catch (_: Exception) {
                            Date(startDate.time + sampleCount[0] * 1000L)
                        }
                    } else {
                        Date(startDate.time + sampleCount[0] * 1000L)
                    }

                    if (firstSampleDate[0] == null) firstSampleDate[0] = sampleDate
                    lastSampleDate[0] = sampleDate

                    record.setTimestamp(DateTime(sampleDate))

                    // Coordinates (only if not indoor session and coordinates are valid finite numbers)
                    if (!indoorTrainerSession && haveGeo && sample.latitude != null && sample.longitude != null) {
                        val lat = sample.latitude
                        val lng = sample.longitude
                        if (!lat.isNaN() && !lng.isNaN()
                            && lat >= -90.0 && lat <= 90.0 && lng >= -180.0 && lng <= 180.0
                            && (lat != 0.0 || lng != 0.0)) {
                            val latSemicircles = (lat * SEMICIRCLES_FACTOR).roundToLong().toInt()
                            val lngSemicircles = (lng * SEMICIRCLES_FACTOR).roundToLong().toInt()
                            record.setPositionLat(latSemicircles)
                            record.setPositionLong(lngSemicircles)
                        }
                    }

                    // Altitude
                    if (haveAltitude && sample.altitude != null) {
                        val alt = sample.altitude
                        if (!alt.isNaN() && alt >= -500.0 && alt <= 9000.0) {
                            record.setAltitude(alt.toFloat())
                        }
                    }

                    // Distance
                    if (haveDistance && sample.distanceM != null) {
                        val dist = sample.distanceM
                        if (!dist.isNaN() && dist >= 0.0) {
                            record.setDistance(dist.toFloat())
                        }
                    }

                    // Speed
                    if (haveSpeed && sample.speedMps != null) {
                        val spd = sample.speedMps
                        if (!spd.isNaN() && spd >= 0.0) {
                            record.setSpeed(spd.toFloat())
                        }
                    }

                    // Heart Rate
                    if (haveHR && sample.hr != null) {
                        val hr = sample.hr
                        if (hr in 1..299) {
                            record.setHeartRate(hr.toShort())
                        }
                    }

                    // Cadence
                    if (haveCadence && sample.cadence != null) {
                        val cad = sample.cadence
                        if (!cad.isNaN() && cad >= 0.0) {
                            record.setCadence(cad.roundToInt().toShort())
                        }
                    }

                    // Power
                    if (havePower && sample.power != null) {
                        val pwr = sample.power
                        if (!pwr.isNaN() && pwr >= 0.0) {
                            record.setPower(pwr.roundToInt())
                        }
                    }

                    // Cycling Power Dynamics (REQ-EXP-043.2 / ATT-8196, gated on Strava power opt-out per REQ-EXP-051.3)
                    if (includePowerData) {
                        if (sample.pedalPowerBalance != null) {
                            val bal = sample.pedalPowerBalance
                            if (!bal.isNaN() && bal in 0.0..100.0) {
                                val encoded = ((bal.roundToInt() and LeftRightBalance.MASK.toInt()) or LeftRightBalance.RIGHT.toInt()).toShort()
                                record.setLeftRightBalance(encoded)
                            }
                        }
                        if (sample.torqueEffectivenessL != null) {
                            val teL = sample.torqueEffectivenessL
                            if (!teL.isNaN() && teL in 0.0..100.0) {
                                record.setLeftTorqueEffectiveness(teL.toFloat())
                            }
                        }
                        if (sample.torqueEffectivenessR != null) {
                            val teR = sample.torqueEffectivenessR
                            if (!teR.isNaN() && teR in 0.0..100.0) {
                                record.setRightTorqueEffectiveness(teR.toFloat())
                            }
                        }
                        if (sample.pedalSmoothnessL != null) {
                            val psL = sample.pedalSmoothnessL
                            if (!psL.isNaN() && psL in 0.0..100.0) {
                                record.setLeftPedalSmoothness(psL.toFloat())
                            }
                        }
                        if (sample.pedalSmoothnessR != null) {
                            val psR = sample.pedalSmoothnessR
                            if (!psR.isNaN() && psR in 0.0..100.0) {
                                record.setRightPedalSmoothness(psR.toFloat())
                            }
                        }
                        if (sample.pedalSmoothnessCombined != null) {
                            val psComb = sample.pedalSmoothnessCombined
                            if (!psComb.isNaN() && psComb in 0.0..100.0) {
                                record.setCombinedPedalSmoothness(psComb.toFloat())
                            }
                        }
                    }

                    // Running Dynamics (REQ-SET-116.4 / ATT-8018: FIT SDK expects mm and ms)
                    val voCm = sample.verticalOscillationCm
                    if (voCm != null && !voCm.isNaN() && voCm in 1.0..25.0) {
                        record.setVerticalOscillation((voCm * CM_TO_MM).toFloat())
                    }
                    val gctMs = sample.groundContactTimeMs
                    if (gctMs != null && !gctMs.isNaN() && gctMs in 50.0..1000.0) {
                        record.setStanceTime(gctMs.toFloat())
                    }

                    // Accumulate online pedal and running dynamics (REQ-EXP-043.3, REQ-SET-116.4, REQ-EXP-051.3)
                    val sampleLap = if (sample.lapNr > 0) sample.lapNr else 1
                    if (includePowerData) {
                        sessionPedalAcc.add(sample)
                        var lapAcc = lapPedalAccMap[sampleLap]
                        if (lapAcc == null) {
                            lapAcc = PedalDynamicsAccumulator()
                            lapPedalAccMap[sampleLap] = lapAcc
                        }
                        lapAcc.add(sample)
                    }
                    sessionRdAcc.add(sample)
                    val lapRdAcc = lapRdAccMap.getOrPut(sampleLap) { RunningDynamicsAccumulator() }
                    lapRdAcc.add(sample)

                    // Temperature
                    if (sample.temperature != null) {
                        val temp = sample.temperature
                        if (!temp.isNaN()) {
                            record.setTemperature(temp.roundToInt().toByte())
                        }
                    }

                    // Write pending HrvMesg intervals that occurred up to sampleDate (REQ-PER-034.4)
                    if (finalRrIntervals != null && rrIntervalIndex[0] < totalRr) {
                        val sampleTs = sampleDate.time
                        val hrvBatch = ArrayList<Float>(5)
                        while (rrIntervalIndex[0] < totalRr && finalRrIntervals[rrIntervalIndex[0]].beatTimestampMs <= sampleTs) {
                            hrvBatch.add(finalRrIntervals[rrIntervalIndex[0]].rrMs / 1000.0f)
                            rrIntervalIndex[0]++
                            if (hrvBatch.size == 5) {
                                writeHrvMesg(activeEncoder, hrvBatch)
                                hrvBatch.clear()
                            }
                        }
                        if (hrvBatch.isNotEmpty()) {
                            writeHrvMesg(activeEncoder, hrvBatch)
                            hrvBatch.clear()
                        }
                    }

                    // Vehicle pass event emission (REQ-EXP-042 / ATT-5088)
                    if (finalHaveRadar && sample.radarVehicleCount != null) {
                        val currentVehCount = sample.radarVehicleCount
                        if (currentVehCount > prevVehCount[0]) {
                            prevVehCount[0] = currentVehCount
                            val eventMesg = EventMesg().apply {
                                setTimestamp(DateTime(sampleDate))
                                event = Event.RADAR_THREAT_ALERT
                                eventType = EventType.MARKER
                                if (sample.radarThreatCount != null) {
                                    setRadarThreatCount(sample.radarThreatCount.toShort())
                                }
                                val appSpdKph = (sample.radarApproachSpeedMps ?: 0.0) * 3.6
                                val isHighThreat = (sample.radarThreatCount != null && sample.radarThreatCount >= 2) || appSpdKph >= 25.0
                                setRadarThreatLevelMax(if (isHighThreat) RadarThreatLevelType.THREAT_APPROACHING_FAST else RadarThreatLevelType.THREAT_APPROACHING)
                                if (sample.radarApproachSpeedMps != null) {
                                    val spd = sample.radarApproachSpeedMps.coerceIn(0.0, 25.4).toFloat()
                                    setRadarThreatMaxApproachSpeed(spd)
                                    setRadarThreatAvgApproachSpeed(spd)
                                }
                            }
                            activeEncoder.write(eventMesg)
                        }
                    }

                    // Attach FIT Protocol V2.0 Developer Fields on RecordMesg (REQ-EXP-042 / ATT-5088)
                    if (finalHaveRadar) {
                        if (sample.radarThreatCount != null && finalRadarCurrentDesc != null) {
                            val f = DeveloperField(finalRadarCurrentDesc, finalDevDataIdMesg)
                            f.setValue(sample.radarThreatCount.toShort())
                            record.addDeveloperField(f)
                        }
                        if (sample.radarVehicleCount != null && finalRadarTotalDesc != null) {
                            val f = DeveloperField(finalRadarTotalDesc, finalDevDataIdMesg)
                            f.setValue(sample.radarVehicleCount)
                            record.addDeveloperField(f)
                        }
                        if (sample.radarApproachSpeedMps != null) {
                            if (finalPassingSpeedDesc != null) {
                                val f = DeveloperField(finalPassingSpeedDesc, finalDevDataIdMesg)
                                f.setValue(sample.radarApproachSpeedMps)
                                record.addDeveloperField(f)
                            }
                            if (finalPassingSpeedAbsDesc != null && sample.speedMps != null) {
                                val f = DeveloperField(finalPassingSpeedAbsDesc, finalDevDataIdMesg)
                                f.setValue(sample.speedMps + sample.radarApproachSpeedMps)
                                record.addDeveloperField(f)
                            }
                        }
                        if (sample.radarPassingDistanceM != null && finalPassingDistDesc != null) {
                            val f = DeveloperField(finalPassingDistDesc, finalDevDataIdMesg)
                            f.setValue(sample.radarPassingDistanceM)
                            record.addDeveloperField(f)
                        }
                        if (sample.radarLargeVehicleCount != null && finalLargeVehCountDesc != null) {
                            val f = DeveloperField(finalLargeVehCountDesc, finalDevDataIdMesg)
                            f.setValue(sample.radarLargeVehicleCount)
                            record.addDeveloperField(f)
                        }
                    }

                    activeEncoder.write(record)
                    if (isCancelled) {
                        throw CancellationException("FIT export cancelled post-I/O")
                    }
                    sampleCount[0]++
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error querying samples for FIT export: ${e.message}", e)
                throw IOException("FIT export failed during sample encoding: ${e.message}", e)
            }

            // Write remaining trailing HrvMesg intervals (REQ-PER-034.4)
            if (finalRrIntervals != null && rrIntervalIndex[0] < totalRr) {
                val hrvBatch = ArrayList<Float>(5)
                while (rrIntervalIndex[0] < totalRr) {
                    hrvBatch.add(finalRrIntervals[rrIntervalIndex[0]].rrMs / 1000.0f)
                    rrIntervalIndex[0]++
                    if (hrvBatch.size == 5) {
                        writeHrvMesg(encoder, hrvBatch)
                        hrvBatch.clear()
                    }
                }
                if (hrvBatch.isNotEmpty()) {
                    writeHrvMesg(encoder, hrvBatch)
                }
            }

            // Calculate timing metrics supporting pauses
            var elapsedSampleSec = 0.0
            if (sampleCount[0] > 0 && firstSampleDate[0] != null && lastSampleDate[0] != null) {
                val elapsedMs = lastSampleDate[0]!!.time - firstSampleDate[0]!!.time
                elapsedSampleSec = elapsedMs / 1000.0
            }
            val totalTimerSec = if (totalTimeSec > 0.0) totalTimeSec else elapsedSampleSec
            val totalElapsedTimeSec = maxOf(totalTimerSec, elapsedSampleSec)
            val sessionEndDate = lastSampleDate[0] ?: Date(startDate.time + (totalElapsedTimeSec * 1000.0).roundToLong())

            // 3. LapMesg entries
            val laps = WorkoutRepository.getInstance(mContext).getLaps(workoutID)
            if (!laps.isNullOrEmpty()) {
                var lapSeq = 1
                for (lap in laps) {
                    val lapMesg = LapMesg()
                    var lapStart = startDate
                    if (lap.timeStart != null) {
                        try {
                            val parsed = parseUtcTimestamp(lap.timeStart)
                            if (parsed != null) lapStart = parsed
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse lap start time: ${lap.timeStart}, falling back to workout startDate", e)
                        }
                    }
                    lapMesg.setStartTime(DateTime(lapStart))
                    val lapEndMs = lapStart.time + (lap.timeTotalS * 1000.0).roundToLong()
                    lapMesg.setTimestamp(DateTime(Date(lapEndMs)))
                    lapMesg.setTotalElapsedTime(lap.timeTotalS.toFloat())
                    lapMesg.setTotalTimerTime(lap.timeTotalS.toFloat())
                    lapMesg.setTotalDistance(lap.distanceTotalM.toFloat())
                    if (lap.speedAverageMps > 0.0) {
                        lapMesg.setAvgSpeed(lap.speedAverageMps.toFloat())
                    }
                    lapMesg.setIntensity(Intensity.ACTIVE)
                    lapMesg.setSport(sport)
                    lapMesg.setSubSport(subSport)
                    var lapAcc = lapPedalAccMap[lap.lapNr.toInt()]
                    if (lapAcc == null) {
                        lapAcc = lapPedalAccMap[lapSeq]
                    }
                    applyPedalDynamicsToLap(lapMesg, lapAcc ?: sessionPedalAcc)
                    val lapRdAcc = lapRdAccMap[lap.lapNr.toInt()] ?: lapRdAccMap[lapSeq]
                    applyRunningDynamicsToLap(lapMesg, lapRdAcc ?: sessionRdAcc)
                    encoder.write(lapMesg)
                    lapSeq++
                }
            } else {
                // Transient representation for FIT protocol compliance; zero mutations to Laps.db
                val lapMesg = LapMesg().apply {
                    setStartTime(DateTime(startDate))
                    setTimestamp(DateTime(sessionEndDate))
                    setTotalElapsedTime(totalElapsedTimeSec.toFloat())
                    setTotalTimerTime(totalTimerSec.toFloat())
                    setTotalDistance(distTotal.toFloat())
                    if (totalTimerSec > 0.0 && distTotal > 0.0) {
                        setAvgSpeed((distTotal / totalTimerSec).toFloat())
                    }
                    setIntensity(Intensity.ACTIVE)
                    setSport(sport)
                    setSubSport(subSport)
                }
                applyPedalDynamicsToLap(lapMesg, sessionPedalAcc)
                applyRunningDynamicsToLap(lapMesg, sessionRdAcc)
                encoder.write(lapMesg)
            }

            // 4. SessionMesg
            val sessionMesg = SessionMesg().apply {
                applyPedalDynamicsToSession(this, sessionPedalAcc)
                setStartTime(DateTime(startDate))
                setTimestamp(DateTime(sessionEndDate))
                setTotalElapsedTime(totalElapsedTimeSec.toFloat())
                setTotalTimerTime(totalTimerSec.toFloat())
                setTotalDistance(distTotal.toFloat())
                setSport(sport)
                setSubSport(subSport)
                if (sessionPoolLengthMeters != null && sessionPoolLengthMeters > 0.0f) {
                    setPoolLength(sessionPoolLengthMeters)
                    if (sessionPoolLengthUnit != null) {
                        setPoolLengthUnit(sessionPoolLengthUnit)
                    }
                }
                setNumLaps(if (!laps.isNullOrEmpty()) laps.size else 1)
            }

            // Populate summary metrics from WorkoutExportHeader (REQ-DAT-042.3)
            val header = exportHeader
            if (header != null) {
                header.calories?.let { sessionMesg.setTotalCalories(it) }
                if (includeAltitudeData) {
                    header.totalAscent?.let { sessionMesg.setTotalAscent(it) }
                    header.totalDescent?.let { sessionMesg.setTotalDescent(it) }
                }
                header.avgSpeedMps?.let { sessionMesg.setAvgSpeed(it.toFloat()) }
                header.rpe?.let { sessionMesg.setWorkoutRpe((it * 10).toShort()) }
                header.feel?.let { sessionMesg.setWorkoutFeel(((it - 1) * 25).toShort()) }
            }

            // Populate extrema from WorkoutRepository (REQ-DAT-042.1)
            val repo = WorkoutRepository.getInstance(mContext)
            if (includeHrData) {
                val maxHr = repo.getExtremaValue(resolvedWorkoutId, SensorType.HR, ExtremaType.MAX)
                if (maxHr != null) sessionMesg.setMaxHeartRate(maxHr.roundToInt().toShort())
                val avgHr = repo.getExtremaValue(resolvedWorkoutId, SensorType.HR, ExtremaType.AVG)
                if (avgHr != null) sessionMesg.setAvgHeartRate(avgHr.roundToInt().toShort())
            }

            if (includePowerData) {
                val maxPower = repo.getExtremaValue(resolvedWorkoutId, SensorType.POWER, ExtremaType.MAX)
                if (maxPower != null) sessionMesg.setMaxPower(maxPower.roundToInt())
                val avgPower = repo.getExtremaValue(resolvedWorkoutId, SensorType.POWER, ExtremaType.AVG)
                if (avgPower != null) sessionMesg.setAvgPower(avgPower.roundToInt())
            }

            if (includeCadenceData) {
                val maxCad = repo.getExtremaValue(resolvedWorkoutId, SensorType.CADENCE, ExtremaType.MAX)
                if (maxCad != null) sessionMesg.setMaxCadence(maxCad.roundToInt().toShort())
                val avgCad = repo.getExtremaValue(resolvedWorkoutId, SensorType.CADENCE, ExtremaType.AVG)
                if (avgCad != null) sessionMesg.setAvgCadence(avgCad.roundToInt().toShort())
            }

            val maxSpeed = repo.getExtremaValue(resolvedWorkoutId, SensorType.SPEED_mps, ExtremaType.MAX)
            if (maxSpeed != null) sessionMesg.setMaxSpeed(maxSpeed.toFloat())
            val avgSpeed = repo.getExtremaValue(resolvedWorkoutId, SensorType.SPEED_mps, ExtremaType.AVG)
            if (avgSpeed != null) sessionMesg.setAvgSpeed(avgSpeed.toFloat())

            val avgVoFallback = repo.getExtremaValue(resolvedWorkoutId, SensorType.VERTICAL_OSCILLATION, ExtremaType.AVG)
            val avgGctFallback = repo.getExtremaValue(resolvedWorkoutId, SensorType.GROUND_CONTACT_TIME, ExtremaType.AVG)
            applyRunningDynamicsToSession(sessionMesg, sessionRdAcc, avgVoFallback, avgGctFallback)

            // Populate standard FIT session power/stress metrics (REQ-MIG-053.2 / ATT-7747, REQ-EXP-051.3)
            if (includePowerData) {
                var np = if (header?.normalizedPower != null) {
                    header.normalizedPower
                } else {
                    repo.getExtremaValue(resolvedWorkoutId, SensorType.NORMALIZED_POWER, ExtremaType.END)
                }
                if (np == null && resolvedWorkoutId > 0) {
                    np = repo.getExtremaValue(resolvedWorkoutId, SensorType.NORMALIZED_POWER, ExtremaType.AVG)
                }
                if (np != null && np > 0.0) {
                    sessionMesg.setNormalizedPower(np.roundToInt())
                }

                var ifVal = if (header?.intensityFactor != null) {
                    header.intensityFactor
                } else {
                    repo.getExtremaValue(resolvedWorkoutId, SensorType.INTENSITY_FACTOR, ExtremaType.END)
                }
                if (ifVal == null && resolvedWorkoutId > 0) {
                    ifVal = repo.getExtremaValue(resolvedWorkoutId, SensorType.INTENSITY_FACTOR, ExtremaType.AVG)
                }
                if (ifVal != null && ifVal > 0.0) {
                    sessionMesg.setIntensityFactor(ifVal.toFloat())
                }

                var tss = if (header?.tss != null) {
                    header.tss
                } else {
                    repo.getExtremaValue(resolvedWorkoutId, SensorType.TSS, ExtremaType.END)
                }
                if (tss == null && resolvedWorkoutId > 0) {
                    tss = repo.getExtremaValue(resolvedWorkoutId, SensorType.TSS, ExtremaType.AVG)
                }
                if (tss == null && resolvedWorkoutId > 0) {
                    tss = repo.getExtremaValue(resolvedWorkoutId, SensorType.TSS, ExtremaType.MAX)
                }
                if (tss != null && tss > 0.0) {
                    sessionMesg.setTrainingStressScore(tss.toFloat())
                }

                var workKj = if (header?.workKj != null) {
                    header.workKj
                } else {
                    repo.getExtremaValue(resolvedWorkoutId, SensorType.WORK_KJ, ExtremaType.END)
                }
                if (workKj == null && resolvedWorkoutId > 0) {
                    workKj = repo.getExtremaValue(resolvedWorkoutId, SensorType.WORK_KJ, ExtremaType.AVG)
                }
                if (workKj == null && resolvedWorkoutId > 0) {
                    workKj = repo.getExtremaValue(resolvedWorkoutId, SensorType.WORK_KJ, ExtremaType.MAX)
                }
                if (workKj != null && workKj > 0.0) {
                    sessionMesg.setTotalWork((workKj * 1000.0).roundToLong())
                }
            }

            // Attach Session Metadata Developer Fields (REQ-MIG-053.1 / ATT-7747)
            if (header != null) {
                val curWorkoutName = header.workoutName
                if (!curWorkoutName.isNullOrBlank()) {
                    val f = DeveloperField(workoutNameDesc, metaDevDataIdMesg)
                    f.setValue(curWorkoutName.trim())
                    sessionMesg.addDeveloperField(f)
                }
                val curDesc = header.description
                if (!curDesc.isNullOrBlank()) {
                    val f = DeveloperField(notesDesc, metaDevDataIdMesg)
                    f.setValue(curDesc.trim())
                    sessionMesg.addDeveloperField(f)
                }
                val curEquip = header.equipmentName
                if (!curEquip.isNullOrBlank()) {
                    val f = DeveloperField(equipmentNameDesc, metaDevDataIdMesg)
                    f.setValue(curEquip.trim())
                    sessionMesg.addDeveloperField(f)
                }
                val curMethod = header.method
                if (!curMethod.isNullOrBlank()) {
                    val f = DeveloperField(methodDesc, metaDevDataIdMesg)
                    f.setValue(curMethod.trim())
                    sessionMesg.addDeveloperField(f)
                }
                val curGoal = header.goal
                if (!curGoal.isNullOrBlank()) {
                    val f = DeveloperField(goalDesc, metaDevDataIdMesg)
                    f.setValue(curGoal.trim())
                    sessionMesg.addDeveloperField(f)
                }
                val fCommute = DeveloperField(isCommuteDesc, metaDevDataIdMesg)
                fCommute.setValue((if (header.commute) 1 else 0).toShort())
                sessionMesg.addDeveloperField(fCommute)

                val fTrainer = DeveloperField(isTrainerDesc, metaDevDataIdMesg)
                fTrainer.setValue((if (header.indoorTrainerSession) 1 else 0).toShort())
                sessionMesg.addDeveloperField(fTrainer)

                val fRace = DeveloperField(isRaceDesc, metaDevDataIdMesg)
                fRace.setValue((if (header.race) 1 else 0).toShort())
                sessionMesg.addDeveloperField(fRace)

                val fEpic = DeveloperField(isEpicDesc, metaDevDataIdMesg)
                fEpic.setValue((if (header.epic) 1 else 0).toShort())
                sessionMesg.addDeveloperField(fEpic)
            }

            encoder.write(sessionMesg)

            // 5. ActivityMesg
            val activityMesg = ActivityMesg().apply {
                setTimestamp(DateTime(sessionEndDate))
                setTotalTimerTime(totalTimerSec.toFloat())
                setNumSessions(1)
                setType(Activity.MANUAL)
            }
            encoder.write(activityMesg)

            success = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error during FIT export: ${e.message}", e)
            errorMessage = e.message ?: "FIT export failed: ${e.javaClass.simpleName}"
            success = false
        } finally {
            if (encoder != null) {
                try {
                    encoder.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing FileEncoder: ${e.message}", e)
                }
            }
            if (!success && outputFile.exists()) {
                try {
                    val deleted = outputFile.delete()
                    if (!deleted) {
                        Log.w(TAG, "Cleanup failure: failed to delete partial FIT file: ${outputFile.absolutePath}")
                        errorMessage = (errorMessage ?: "FIT export failed") + " (Cleanup failure: could not delete partial file)"
                    } else {
                        Log.d(TAG, "Partial FIT file cleaned up: ${outputFile.absolutePath}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Exception during partial file deletion: ${e.message}", e)
                }
            }
        }

        if (!success) {
            return ExportResult(false, false, errorMessage ?: "FIT export failed")
        }
        return ExportResult(true, false, "FIT export successful: ${outputFile.absolutePath}")
    }

    /**
     * Bounded O(1) online accumulator for cycling pedal dynamics (REQ-EXP-043.3 / ATT-8196).
     */
    private class PedalDynamicsAccumulator {
        var balanceSum: Double = 0.0
        var balanceCount: Int = 0
        var teLSum: Double = 0.0
        var teLCount: Int = 0
        var teRSum: Double = 0.0
        var teRCount: Int = 0
        var psLSum: Double = 0.0
        var psLCount: Int = 0
        var psRSum: Double = 0.0
        var psRCount: Int = 0
        var psCombSum: Double = 0.0
        var psCombCount: Int = 0

        fun add(sample: WorkoutSampleRecord) {
            val bal = sample.pedalPowerBalance
            if (bal != null && !bal.isNaN() && bal in 0.0..100.0) {
                balanceSum += bal
                balanceCount++
            }
            val teL = sample.torqueEffectivenessL
            if (teL != null && !teL.isNaN() && teL in 0.0..100.0) {
                teLSum += teL
                teLCount++
            }
            val teR = sample.torqueEffectivenessR
            if (teR != null && !teR.isNaN() && teR in 0.0..100.0) {
                teRSum += teR
                teRCount++
            }
            val psL = sample.pedalSmoothnessL
            if (psL != null && !psL.isNaN() && psL in 0.0..100.0) {
                psLSum += psL
                psLCount++
            }
            val psR = sample.pedalSmoothnessR
            if (psR != null && !psR.isNaN() && psR in 0.0..100.0) {
                psRSum += psR
                psRCount++
            }
            val psComb = sample.pedalSmoothnessCombined
            if (psComb != null && !psComb.isNaN() && psComb in 0.0..100.0) {
                psCombSum += psComb
                psCombCount++
            }
        }
    }

    /**
     * Bounded O(1) online accumulator for Polar H10 Running Dynamics averages (`REQ-SET-116.4`).
     */
    private class RunningDynamicsAccumulator {
        var voSumCm: Double = 0.0
        var voCount: Int = 0
        var gctSumMs: Double = 0.0
        var gctCount: Int = 0

        fun add(sample: WorkoutSampleRecord) {
            val vo = sample.verticalOscillationCm
            if (vo != null && !vo.isNaN() && vo in 1.0..25.0) {
                voSumCm += vo
                voCount++
            }
            val gct = sample.groundContactTimeMs
            if (gct != null && !gct.isNaN() && gct in 50.0..1000.0) {
                gctSumMs += gct
                gctCount++
            }
        }
    }
}
