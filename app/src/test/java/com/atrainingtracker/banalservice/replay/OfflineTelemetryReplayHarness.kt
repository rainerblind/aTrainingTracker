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

package com.atrainingtracker.banalservice.replay

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarH10RunningDynamicsEstimator
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarLiveRunningCadenceEstimator
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarRawSession
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarRawTelemetryReader
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.RawTelemetryChannel
import com.atrainingtracker.banalservice.devices.bluetooth_le.polar.RawTelemetryRow
import com.atrainingtracker.banalservice.devices.raw.PhoneRawChannel
import com.atrainingtracker.banalservice.devices.raw.PhoneRawTelemetryReader
import com.atrainingtracker.banalservice.devices.raw.PhoneRawTelemetryRow
import com.atrainingtracker.banalservice.odometry.ExtendedRtsAltitudeSlopeSmoother
import com.atrainingtracker.banalservice.odometry.ExtendedRtsOdometrySmoother
import com.atrainingtracker.banalservice.odometry.KinematicAltitudeSlopeEkf
import com.atrainingtracker.banalservice.odometry.KinematicSportTypeDiscriminator
import com.atrainingtracker.banalservice.odometry.MultisportHardwareSignals
import com.atrainingtracker.banalservice.odometry.MultisportTelemetryAnchor
import com.atrainingtracker.banalservice.odometry.MultisportTransitionDetector
import com.atrainingtracker.banalservice.odometry.PhoneCarriageClassifier
import com.atrainingtracker.banalservice.odometry.PhoneCarriageMode
import com.atrainingtracker.banalservice.odometry.QuaternionGyroProjector
import com.atrainingtracker.banalservice.odometry.RawAltitudeSample
import com.atrainingtracker.banalservice.odometry.RawOdometrySample
import com.atrainingtracker.banalservice.odometry.SmartphoneImuCadenceEstimator
import com.atrainingtracker.banalservice.odometry.SportContextHint
import com.atrainingtracker.banalservice.odometry.UnicycleOdometryEkf
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pure-JVM deterministic offline simulation and replay harness ingesting `.phone_raw.csv.gz`
 * (`ATT-9348`) and `.polar_raw.csv.gz` (`ATT-8019`) workout sidecars and driving production
 * pure-Kotlin estimators end-to-end without Android framework dependencies
 * (REQ-EXP-046.2, REQ-EXP-046.3, ATT-9349).
 */
object OfflineTelemetryReplayHarness {

    private sealed interface ReplayEvent {
        val tRelMs: Long
        val sourceOrder: Int
        val seqIndex: Int
    }

    private data class PhoneEvent(
        override val tRelMs: Long,
        override val seqIndex: Int,
        val row: PhoneRawTelemetryRow
    ) : ReplayEvent {
        override val sourceOrder: Int = 0
    }

    private data class PolarEvent(
        override val tRelMs: Long,
        override val sourceOrder: Int,
        override val seqIndex: Int,
        val isVerity: Boolean,
        val row: RawTelemetryRow
    ) : ReplayEvent

    private data class CadenceAccumulator(val sourceName: String) {
        private val values = ArrayList<Double>()
        val bySecond = LinkedHashMap<Long, Double>()

        fun add(sec: Long, spm: Double) {
            if (!spm.isFinite() || spm <= 0.0) return
            values.add(spm)
            bySecond[sec] = spm
        }

        fun toStats(): CadenceStats {
            if (values.isEmpty()) {
                return CadenceStats(sourceName, 0, null, null, null)
            }
            var sum = 0.0
            var minV = Double.POSITIVE_INFINITY
            var maxV = Double.NEGATIVE_INFINITY
            for (v in values) {
                sum += v
                if (v < minV) minV = v
                if (v > maxV) maxV = v
            }
            return CadenceStats(sourceName, values.size, sum / values.size, minV, maxV)
        }
    }

    /**
     * Replays the given [inputs] deterministically and returns the quantitative scorecard + 1-Hz series.
     */
    fun replay(inputs: ReplayInputs): OfflineReplayResult {
        val phoneArchive = inputs.phoneRawFile?.let { PhoneRawTelemetryReader.readAll(it) }
        val polarSessions = inputs.polarRawFiles.map { PolarRawTelemetryReader.readFile(it) }
        return replayParsed(
            phoneRows = phoneArchive?.rows.orEmpty(),
            polarSessions = polarSessions,
            config = inputs.config
        )
    }

    /**
     * Replays already-parsed phone and Polar rows deterministically (useful for fast pure-JVM tests).
     */
    fun replayParsed(
        phoneRows: List<PhoneRawTelemetryRow>,
        polarSessions: List<PolarRawSession>,
        config: ReplayConfig = ReplayConfig()
    ): OfflineReplayResult {
        val merged = mergeChronologically(phoneRows, polarSessions)
        if (merged.isEmpty()) {
            return emptyResult()
        }

        val classifier = PhoneCarriageClassifier(sportSpeedPriors = config.sportSpeedPriors)
        val gyroProjector = QuaternionGyroProjector()
        val sportDiscriminator = KinematicSportTypeDiscriminator(sportSpeedPriors = config.sportSpeedPriors)
        val multisportDetector = MultisportTransitionDetector(sportSpeedPriors = config.sportSpeedPriors)
        val odometryEkf = UnicycleOdometryEkf(recordSnapshots = true)
        val altitudeEkf = KinematicAltitudeSlopeEkf(
            is3StateMode = config.altitude3StateEnabled,
            qg = config.altitudeQg,
            qh = config.altitudeQh,
            rBaro = config.altitudeRBaro,
            rGps = config.altitudeRGps
        )

        val phoneCadenceAcc = CadenceAccumulator("PhoneIMU")
        val h10CadenceAcc = CadenceAccumulator("PolarH10")
        val verityCadenceAcc = CadenceAccumulator("PolarVerity")

        var currentSec = 0L
        var lastPhoneCadence: Double? = null
        var phoneTotalStrides = 0
        var lastEstimatorStrides = 0
        var lastH10Cadence: Double? = null
        var lastVerityCadence: Double? = null
        var lastH10Vo: Double? = null
        var lastH10Gct: Double? = null
        val h10VoValues = ArrayList<Double>()
        val h10GctValues = ArrayList<Double>()

        var hasRotVec = false
        val rotVec = FloatArray(4)
        var lastGyroMs = Long.MIN_VALUE
        var lastWorldYawRate = 0.0
        var sportHint = SportContextHint.UNKNOWN
        var resolvedStateSport = BSportType.UNKNOWN
        var cadenceSportHint = SportContextHint.UNKNOWN
        var currentMode = PhoneCarriageMode.UNKNOWN
        var lastInferredSport = BSportType.UNKNOWN
        var lastClassifyMs = Long.MIN_VALUE
        var lastMultisportTickMs = Long.MIN_VALUE
        var multisportTransitionCount = 0
        var lastWheelMs = Long.MIN_VALUE
        var hasEverSeenWheel = false
        var hasStepEvent = false

        lateinit var phoneCadenceEstimator: SmartphoneImuCadenceEstimator
        phoneCadenceEstimator = SmartphoneImuCadenceEstimator(
            onCadence = { spm ->
                if (spm == null) {
                    lastPhoneCadence = null
                } else if (spm.isFinite() && spm > 0.0) {
                    lastPhoneCadence = spm
                    phoneCadenceAcc.add(currentSec, spm)
                }
            },
            onStride = { total ->
                val stridesAllowed = cadenceSportHint == SportContextHint.RUNNING ||
                    (cadenceSportHint == SportContextHint.UNKNOWN && phoneCadenceEstimator.isRunningOrWalkingActive)
                if (stridesAllowed) {
                    phoneTotalStrides += (total - lastEstimatorStrides).coerceAtLeast(0)
                }
                lastEstimatorStrides = total
            }
        )
        val h10CadenceEstimator = PolarLiveRunningCadenceEstimator(
            mode = PolarLiveRunningCadenceEstimator.Mode.H10_CHEST,
            onCadence = { spm ->
                if (spm.isFinite() && spm > 0.0) {
                    lastH10Cadence = spm
                    h10CadenceAcc.add(currentSec, spm)
                }
            }
        )
        val verityCadenceEstimator = PolarLiveRunningCadenceEstimator(
            mode = PolarLiveRunningCadenceEstimator.Mode.VERITY_SENSE_ARM,
            onCadence = { spm ->
                if (spm.isFinite() && spm > 0.0) {
                    lastVerityCadence = spm
                    verityCadenceAcc.add(currentSec, spm)
                }
            }
        )
        val h10RunningDynamics = PolarH10RunningDynamicsEstimator(
            onVerticalOscillation = { vo ->
                if (vo != null && vo.isFinite() && vo > 0.0) {
                    lastH10Vo = vo
                    h10VoValues.add(vo)
                }
            },
            onGroundContactTime = { gct ->
                if (gct != null && gct.isFinite() && gct > 0.0) {
                    lastH10Gct = gct
                    h10GctValues.add(gct)
                }
            }
        )

        var refLat: Double? = null
        var refLon: Double? = null
        var lastGnssLat: Double? = null
        var lastGnssLon: Double? = null
        var lastGnssAlt: Double? = null
        var lastGnssSpeed: Double? = null
        var lastGnssBearing: Double? = null
        var lastGnssAcc: Double? = null
        var lastWheelSpeed: Double? = null
        var lastBaroAltM: Double = Double.NaN

        var prevEkfEast = 0.0
        var prevEkfNorth = 0.0
        var hasPrevEkfPos = false
        var ekfTotalDistanceM = 0.0
        var lastAltStepMs = Long.MIN_VALUE
        var prevEkfAlt = Double.NaN
        var ekfAscentM = 0.0
        var ekfDescentM = 0.0
        var ekfMinSlopePct = Double.POSITIVE_INFINITY
        var ekfMaxSlopePct = Double.NEGATIVE_INFINITY

        var firstMotionCueMs: Long? = null
        var firstDetectedMotionMs: Long? = null

        val modeCounts = LinkedHashMap<PhoneCarriageMode, Int>()
        var transitions = 0
        var lastRecordedModeForTransitions: PhoneCarriageMode? = null

        val rawOdometry1Hz = ArrayList<RawOdometrySample>()
        val rawAltitude1Hz = ArrayList<RawAltitudeSample>()
        val liveSeries1Hz = ArrayList<OneHzReplayRow>()
        var nextEmitSec = 0L

        var phoneEvents = 0
        var polarEvents = 0
        val lastTRelMs = merged.last().tRelMs

        // REQ-EXP-047.3 (ATT-9604): Resolve active GNSS stream and deduplicate exact legacy v1 duplicates.
        val activeGnssChannel = when (config.gnssSource) {
            ReplayGnssSource.GPS -> PhoneRawChannel.GNSS
            ReplayGnssSource.FUSED -> PhoneRawChannel.GNSS_FUSED
            ReplayGnssSource.AUTO -> if (phoneRows.any { it.channel == PhoneRawChannel.GNSS }) {
                PhoneRawChannel.GNSS
            } else {
                PhoneRawChannel.GNSS_FUSED
            }
        }
        var hasLastAcceptedGnss = false
        var lastAcceptedGnssElapsedNs = 0L
        var lastAcceptedGnssEpochMs = 0L
        var lastAcceptedGnssLat = Double.NaN
        var lastAcceptedGnssLon = Double.NaN

        fun emitUpToSecond(targetSec: Long) {
            while (nextEmitSec <= targetSec) {
                val secMs = nextEmitSec * 1_000L
                val speed = odometryEkf.speedMps.coerceAtLeast(0.0)
                val slopePct = if (altitudeEkf.isInitialized) altitudeEkf.slope * 100.0 else 0.0
                val altM = if (altitudeEkf.isInitialized) altitudeEkf.altitude else (lastGnssAlt ?: 0.0)

                if (altitudeEkf.isInitialized) {
                    if (slopePct < ekfMinSlopePct) ekfMinSlopePct = slopePct
                    if (slopePct > ekfMaxSlopePct) ekfMaxSlopePct = slopePct
                }

                modeCounts[currentMode] = (modeCounts[currentMode] ?: 0) + 1
                if (lastRecordedModeForTransitions != null && lastRecordedModeForTransitions != currentMode) {
                    transitions++
                }
                lastRecordedModeForTransitions = currentMode

                rawOdometry1Hz.add(
                    RawOdometrySample(
                        timestampMs = secMs,
                        latitude = lastGnssLat,
                        longitude = lastGnssLon,
                        speedMps = lastGnssSpeed,
                        bearingDeg = lastGnssBearing,
                        accuracyMeters = lastGnssAcc,
                        worldYawRateRadPerSec = lastWorldYawRate,
                        wheelSpeedMps = lastWheelSpeed,
                        carriageMode = currentMode
                    )
                )

                val altInput = when {
                    lastBaroAltM.isFinite() -> lastBaroAltM
                    lastGnssAlt != null -> lastGnssAlt!!
                    else -> Double.NaN
                }
                if (altInput.isFinite()) {
                    rawAltitude1Hz.add(
                        RawAltitudeSample(
                            timestampMs = secMs,
                            rawAltitudeM = altInput,
                            referenceAltitudeM = lastGnssAlt,
                            smoothedSpeedMps = speed,
                            isStandstill = speed < 0.3,
                            carriageMode = currentMode
                        )
                    )
                }

                liveSeries1Hz.add(
                    OneHzReplayRow(
                        elapsedSec = nextEmitSec,
                        carriageMode = currentMode,
                        inferredSport = lastInferredSport,
                        ekfSpeedMps = speed,
                        ekfDistanceM = ekfTotalDistanceM,
                        ekfAltitudeM = altM,
                        ekfSlopePct = slopePct,
                        ertsSpeedMps = null,
                        ertsAltitudeM = null,
                        ertsSlopePct = null,
                        phoneCadenceSpm = lastPhoneCadence,
                        polarH10CadenceSpm = lastH10Cadence,
                        polarVerityCadenceSpm = lastVerityCadence,
                        h10VoCm = lastH10Vo,
                        h10GctMs = lastH10Gct
                    )
                )

                nextEmitSec++
            }
        }

        fun syncCadenceSportHint(tMs: Long) {
            // Mirror production Tier 3 (kinematic) > Tier 4 (STATE auto-resolved) precedence (REQ-FIL-046.1).
            val effectiveSport = if (lastInferredSport != BSportType.UNKNOWN) lastInferredSport else resolvedStateSport
            val nextCadenceHint = SportContextHint.fromBSportType(effectiveSport)
            if (nextCadenceHint != cadenceSportHint) {
                cadenceSportHint = nextCadenceHint
                phoneCadenceEstimator.onModeOrSportChanged(currentMode, cadenceSportHint, tMs)
            }
        }

        fun updateCarriageIfDue(tMs: Long, force: Boolean) {
            if (!force && lastClassifyMs != Long.MIN_VALUE && (tMs - lastClassifyMs) < 1_000L) return
            lastClassifyMs = tMs
            classifier.setSportContext(sportHint)
            val newMode = classifier.classify(tMs)
            val inferred = sportDiscriminator.onCarriageMode(newMode, tMs)
            if (inferred != BSportType.UNKNOWN) lastInferredSport = inferred
            if (lastMultisportTickMs == Long.MIN_VALUE || (tMs > lastMultisportTickMs && (tMs - lastMultisportTickMs) >= 1_000L)) {
                lastMultisportTickMs = tMs
                val activeBikeHw = lastWheelSpeed != null && lastWheelSpeed!! > 0.3 &&
                    lastWheelMs != Long.MIN_VALUE && (tMs - lastWheelMs) <= 3_000L
                val hwSignals = MultisportHardwareSignals(
                    hasActiveBikeHardware = activeBikeHw,
                    hardwareSportType = if (hasEverSeenWheel) BSportType.BIKE else BSportType.UNKNOWN
                )
                val secInt = (tMs / 1_000L).toInt()
                val ev = multisportDetector.onTick(
                    carriageMode = newMode,
                    kinematicSport = lastInferredSport,
                    hardwareSignals = hwSignals,
                    anchor = MultisportTelemetryAnchor(tMs, secInt, secInt, ekfTotalDistanceM),
                    isTracking = true,
                    isPausedOrAutoPaused = false,
                    isUserSportLocked = false,
                    hasActiveRunFootstrike = classifier.hasActiveRunFootstrike(tMs)
                )
                if (ev != null) multisportTransitionCount++
            }
            syncCadenceSportHint(tMs)
            if (newMode != currentMode) {
                currentMode = newMode
                phoneCadenceEstimator.onModeOrSportChanged(currentMode, cadenceSportHint, tMs)
            }
        }

        fun advanceAltitudePredict(tMs: Long) {
            if (!altitudeEkf.isInitialized) return
            if (lastAltStepMs != Long.MIN_VALUE && tMs > lastAltStepMs) {
                val dt = (tMs - lastAltStepMs) / 1000.0
                if (dt in 0.001..10.0) {
                    val v = odometryEkf.speedMps.coerceAtLeast(0.0)
                    altitudeEkf.predict(dt, v, v * dt)
                }
            }
            lastAltStepMs = tMs
        }

        fun recordEkfAltitude(tMs: Long) {
            if (!altitudeEkf.isInitialized) return
            if (lastAltStepMs == Long.MIN_VALUE) lastAltStepMs = tMs
            val curAlt = altitudeEkf.altitude
            if (prevEkfAlt.isFinite() && curAlt.isFinite()) {
                val delta = curAlt - prevEkfAlt
                if (delta > 0.0) ekfAscentM += delta else ekfDescentM += -delta
            }
            if (curAlt.isFinite()) prevEkfAlt = curAlt
        }

        fun noteMotion(tMs: Long) {
            if (firstDetectedMotionMs == null && odometryEkf.speedMps >= config.motionSpeedThresholdMps) {
                firstDetectedMotionMs = tMs
            }
        }

        for (ev in merged) {
            val tMs = ev.tRelMs
            currentSec = tMs / 1_000L
            if (currentSec > nextEmitSec) {
                emitUpToSecond(currentSec - 1L)
            }

            when (ev) {
                is PhoneEvent -> {
                    phoneEvents++
                    val r = ev.row
                    when (r.channel) {
                        PhoneRawChannel.ACC -> {
                            sportDiscriminator.onAccSample(r.v0, r.v1, r.v2, tMs)
                            classifier.ingestAccelerometer(r.v0, r.v1, r.v2, tMs)
                            phoneCadenceEstimator.onAccSample(r.v0, r.v1, r.v2, tMs, currentMode, cadenceSportHint)
                            updateCarriageIfDue(tMs, force = false)
                        }
                        PhoneRawChannel.ROTVEC -> {
                            rotVec[0] = r.v0.toFloat()
                            rotVec[1] = r.v1.toFloat()
                            rotVec[2] = r.v2.toFloat()
                            rotVec[3] = r.v3.toFloat()
                            hasRotVec = true
                            classifier.ingestRotationVector(rotVec, tMs)
                        }
                        PhoneRawChannel.GYRO -> {
                            val gyroArr = floatArrayOf(r.v0.toFloat(), r.v1.toFloat(), r.v2.toFloat())
                            val yawRate = if (hasRotVec) gyroProjector.projectYawRate(rotVec, gyroArr) else r.v2
                            val rawMag = sqrt(r.v0 * r.v0 + r.v1 * r.v1 + r.v2 * r.v2)
                            lastWorldYawRate = yawRate
                            classifier.ingestGyro(yawRate, rawMag, tMs)
                            sportDiscriminator.onGyroSample(rawMag, tMs)
                            phoneCadenceEstimator.onGyroSample(r.v0, r.v1, r.v2, tMs, currentMode, cadenceSportHint)
                            if (lastGyroMs != Long.MIN_VALUE && tMs > lastGyroMs) {
                                val dt = (tMs - lastGyroMs) / 1000.0
                                if (dt in 0.001..0.5) {
                                    odometryEkf.predict(dt, yawRate, tMs, currentMode.profile.gyroNoiseScale)
                                    val e = odometryEkf.eastMeters
                                    val n = odometryEkf.northMeters
                                    if (hasPrevEkfPos) {
                                        ekfTotalDistanceM += hypot(e - prevEkfEast, n - prevEkfNorth)
                                    }
                                    prevEkfEast = e
                                    prevEkfNorth = n
                                    hasPrevEkfPos = true
                                    noteMotion(tMs)
                                }
                            }
                            lastGyroMs = tMs
                        }
                        PhoneRawChannel.BARO -> {
                            val altM = r.v1
                            if (altM.isFinite()) {
                                lastBaroAltM = altM
                                classifier.ingestBarometerAltitude(altM, tMs)
                                advanceAltitudePredict(tMs)
                                val speed = odometryEkf.speedMps.coerceAtLeast(0.0)
                                altitudeEkf.updateBarometer(
                                    baroAltM = altM,
                                    varBaro = config.altitudeRBaro,
                                    isStandstill = speed < 0.3 && hasPrevEkfPos,
                                    speedMps = speed
                                )
                                recordEkfAltitude(tMs)
                            }
                        }
                        PhoneRawChannel.STEP -> {
                            if (!hasStepEvent) {
                                hasStepEvent = true
                                classifier.setStepDetectorRegistered(true)
                            }
                            if (firstMotionCueMs == null) firstMotionCueMs = tMs
                            classifier.ingestStepEvent(tMs)
                            sportDiscriminator.onStepDetected(tMs)
                            phoneCadenceEstimator.onStepDetected(tMs, currentMode, cadenceSportHint)
                        }
                        PhoneRawChannel.GNSS, PhoneRawChannel.GNSS_FUSED -> {
                            if (r.channel != activeGnssChannel) {
                                continue
                            }
                            if (hasLastAcceptedGnss &&
                                r.elapsedRealtimeNs == lastAcceptedGnssElapsedNs &&
                                r.epochMs == lastAcceptedGnssEpochMs &&
                                r.v0 == lastAcceptedGnssLat &&
                                r.v1 == lastAcceptedGnssLon
                            ) {
                                continue
                            }
                            hasLastAcceptedGnss = true
                            lastAcceptedGnssElapsedNs = r.elapsedRealtimeNs
                            lastAcceptedGnssEpochMs = r.epochMs
                            lastAcceptedGnssLat = r.v0
                            lastAcceptedGnssLon = r.v1

                            val lat = r.v0
                            val lon = r.v1
                            val alt = r.v2
                            val spd = r.v3
                            val brg = r.v4
                            val acc = r.v5
                            lastGnssLat = lat
                            lastGnssLon = lon
                            lastGnssAlt = alt
                            lastGnssSpeed = spd
                            lastGnssBearing = brg
                            lastGnssAcc = acc

                            if (spd >= config.motionSpeedThresholdMps && firstMotionCueMs == null) {
                                firstMotionCueMs = tMs
                            }
                            val accValid = acc > 0.0 && acc <= config.gnssMaxAccuracyM
                            classifier.ingestGnssSpeed(
                                speedMps = spd,
                                bearingDeg = if (brg.isFinite()) brg else null,
                                accuracyMeters = if (acc.isFinite()) acc else null,
                                timestampMs = tMs,
                                latitude = if (accValid) lat else null,
                                longitude = if (accValid) lon else null
                            )
                            val inferred = sportDiscriminator.onSpeedSample(spd, tMs)
                            if (inferred != BSportType.UNKNOWN) lastInferredSport = inferred
                            updateCarriageIfDue(tMs, force = true)

                            if (accValid) {
                                val rLat = refLat
                                val rLon = refLon
                                if (rLat == null || rLon == null) {
                                    refLat = lat
                                    refLon = lon
                                    odometryEkf.updateGnssPosition(0.0, 0.0, acc, tMs)
                                    prevEkfEast = odometryEkf.eastMeters
                                    prevEkfNorth = odometryEkf.northMeters
                                    hasPrevEkfPos = true
                                } else {
                                    val (eastM, northM) = ExtendedRtsOdometrySmoother.latLngToEnu(lat, lon, rLat, rLon)
                                    odometryEkf.updateGnssPosition(eastM, northM, acc, tMs)
                                    val e = odometryEkf.eastMeters
                                    val n = odometryEkf.northMeters
                                    ekfTotalDistanceM += hypot(e - prevEkfEast, n - prevEkfNorth)
                                    prevEkfEast = e
                                    prevEkfNorth = n
                                }
                                if (spd > 1.0 && brg.isFinite()) {
                                    odometryEkf.updateGnssDoppler(
                                        speedMps = spd,
                                        bearingDeg = brg,
                                        speedVariance = max(0.25, (acc / 10.0) * (acc / 10.0)),
                                        timestampMs = tMs
                                    )
                                } else if (spd.isFinite() && spd >= 0.0) {
                                    odometryEkf.updateSpeed(spd, max(0.25, (acc / 10.0) * (acc / 10.0)), tMs)
                                }
                                noteMotion(tMs)
                            }
                            if (alt.isFinite()) {
                                advanceAltitudePredict(tMs)
                                altitudeEkf.updateReferenceAltitude(alt, config.altitudeRGps)
                                recordEkfAltitude(tMs)
                            }
                        }
                        PhoneRawChannel.WHEEL -> {
                            val speedMps = r.v0
                            val variance = if (r.v1.isFinite() && r.v1 > 0.0) r.v1 else 0.05
                            lastWheelSpeed = speedMps
                            lastWheelMs = tMs
                            hasEverSeenWheel = true
                            if (speedMps >= config.motionSpeedThresholdMps && firstMotionCueMs == null) {
                                firstMotionCueMs = tMs
                            }
                            classifier.ingestWheelSpeed(speedMps, tMs)
                            val inferred = sportDiscriminator.onSpeedSample(speedMps, tMs)
                            if (inferred != BSportType.UNKNOWN) {
                                lastInferredSport = inferred
                                syncCadenceSportHint(tMs)
                            }
                            odometryEkf.updateSpeed(speedMps, variance, tMs)
                            updateCarriageIfDue(tMs, force = false)
                            noteMotion(tMs)
                        }
                        PhoneRawChannel.STATE -> {
                            val bSportOrd = r.v1.toInt()
                            val bSport = BSportType.entries.getOrNull(bSportOrd) ?: BSportType.UNKNOWN
                            resolvedStateSport = bSport
                            val nextHint = SportContextHint.fromBSportType(bSport)
                            if (nextHint != sportHint) {
                                sportHint = nextHint
                                classifier.setSportContext(sportHint)
                            }
                            syncCadenceSportHint(tMs)
                        }
                    }
                }
                is PolarEvent -> {
                    polarEvents++
                    val r = ev.row
                    when (r.channel) {
                        RawTelemetryChannel.ACC -> {
                            val ax = r.axMg ?: continue
                            val ay = r.ayMg ?: continue
                            val az = r.azMg ?: continue
                            if (ev.isVerity) {
                                verityCadenceEstimator.onAccSample(ax, ay, az, tMs)
                            } else {
                                h10CadenceEstimator.onAccSample(ax, ay, az, tMs)
                                // Match PolarOnlineStreamingBridge.kt:1181-1183 external cadence wiring parity
                                h10RunningDynamics.setCurrentCadenceStepsPerMin(
                                    h10CadenceEstimator.latestCadenceStridesPerMin * 2.0
                                )
                                h10RunningDynamics.onAccSample(ax, ay, az, tMs)
                            }
                        }
                        RawTelemetryChannel.GYRO -> {
                            if (ev.isVerity) {
                                // PolarRawTelemetryRecorder stores Polar BLE SDK deg/s unscaled in gxMdps/gyMdps/gzMdps
                                // (matching PolarOnlineStreamingBridge.kt:1211).
                                val gxDps = r.gxMdps ?: continue
                                val gyDps = r.gyMdps ?: continue
                                val gzDps = r.gzMdps ?: continue
                                verityCadenceEstimator.onGyroSample(gxDps, gyDps, gzDps, tMs)
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }

        emitUpToSecond(lastTRelMs / 1_000L)

        // Post-workout ERTS forward-backward smoothing pass
        var ertsDistM: Double? = null
        var ertsAscM: Double? = null
        var ertsDescM: Double? = null
        var ertsMinSlope: Double? = null
        var ertsMaxSlope: Double? = null

        val ertsSpeedBySec = HashMap<Long, Double>()
        val ertsAltBySec = HashMap<Long, Double>()
        val ertsSlopeBySec = HashMap<Long, Double>()

        if (config.enableErtsSmoothing) {
            if (rawOdometry1Hz.count { it.hasValidGnssFix() } >= 2) {
                val smoothedOdo = ExtendedRtsOdometrySmoother.smoothSamples(rawOdometry1Hz)
                if (smoothedOdo.isNotEmpty()) {
                    ertsDistM = smoothedOdo.sumOf { it.deltaDistanceMeters }
                    for (pt in smoothedOdo) {
                        ertsSpeedBySec[pt.timestampMs / 1_000L] = pt.speedMps
                    }
                }
            }
            if (rawAltitude1Hz.size >= 2) {
                val updatedAltInputs = rawAltitude1Hz.map { s ->
                    val sSpeed = ertsSpeedBySec[s.timestampMs / 1_000L] ?: s.smoothedSpeedMps
                    s.copy(smoothedSpeedMps = sSpeed, isStandstill = sSpeed < 0.3)
                }
                val smoothedAlt = ExtendedRtsAltitudeSlopeSmoother.smooth(updatedAltInputs)
                if (smoothedAlt.isNotEmpty()) {
                    ertsAscM = smoothedAlt.last().ascentM
                    ertsDescM = smoothedAlt.last().descentM
                    ertsMinSlope = smoothedAlt.minOf { it.slopePercent }
                    ertsMaxSlope = smoothedAlt.maxOf { it.slopePercent }
                    for (pt in smoothedAlt) {
                        val sec = pt.timestampMs / 1_000L
                        ertsAltBySec[sec] = pt.altitudeM
                        ertsSlopeBySec[sec] = pt.slopePercent
                    }
                }
            }
        }

        val finalSeries = liveSeries1Hz.map { row ->
            row.copy(
                ertsSpeedMps = ertsSpeedBySec[row.elapsedSec],
                ertsAltitudeM = ertsAltBySec[row.elapsedSec],
                ertsSlopePct = ertsSlopeBySec[row.elapsedSec]
            )
        }

        val totalModeTicks = modeCounts.values.sum().coerceAtLeast(1)
        val distMap = LinkedHashMap<PhoneCarriageMode, Double>()
        for ((m, cnt) in modeCounts) {
            distMap[m] = (cnt.toDouble() / totalModeTicks.toDouble()) * 100.0
        }
        val dominantEntry = distMap.entries.maxByOrNull { it.value }
        val dominantMode = dominantEntry?.key ?: PhoneCarriageMode.UNKNOWN
        val dominantPct = dominantEntry?.value ?: 0.0

        val cadenceComparisons = buildList {
            compareCadence(phoneCadenceAcc, h10CadenceAcc)?.let(::add)
            compareCadence(phoneCadenceAcc, verityCadenceAcc)?.let(::add)
            compareCadence(h10CadenceAcc, verityCadenceAcc)?.let(::add)
        }

        val cueMs = firstMotionCueMs
        val detMs = firstDetectedMotionMs
        val coldStartSec = if (cueMs != null && detMs != null) {
            max(0.0, (detMs - cueMs) / 1000.0)
        } else {
            null
        }

        val scorecard = OfflineReplayScorecard(
            durationSec = lastTRelMs / 1000.0,
            totalEventsProcessed = merged.size,
            phoneEventsProcessed = phoneEvents,
            polarEventsProcessed = polarEvents,
            carriageModeDistributionPct = distMap,
            dominantCarriageMode = dominantMode,
            dominantCarriagePct = dominantPct,
            carriageTransitionCount = transitions,
            inferredSport = if (lastInferredSport != BSportType.UNKNOWN) lastInferredSport else resolvedStateSport,
            phoneCadence = phoneCadenceAcc.toStats(),
            polarH10Cadence = h10CadenceAcc.toStats(),
            polarVerityCadence = verityCadenceAcc.toStats(),
            cadenceComparisons = cadenceComparisons,
            h10MeanVerticalOscillationCm = if (h10VoValues.isNotEmpty()) h10VoValues.average() else null,
            h10MeanGroundContactTimeMs = if (h10GctValues.isNotEmpty()) h10GctValues.average() else null,
            ekfTotalDistanceM = ekfTotalDistanceM,
            ekfAscentM = ekfAscentM,
            ekfDescentM = ekfDescentM,
            ekfMinSlopePct = if (ekfMinSlopePct.isFinite()) ekfMinSlopePct else 0.0,
            ekfMaxSlopePct = if (ekfMaxSlopePct.isFinite()) ekfMaxSlopePct else 0.0,
            ertsTotalDistanceM = ertsDistM,
            ertsAscentM = ertsAscM,
            ertsDescentM = ertsDescM,
            ertsMinSlopePct = ertsMinSlope,
            ertsMaxSlopePct = ertsMaxSlope,
            coldStartTimeToMotionSec = coldStartSec,
            phoneTotalStrides = phoneTotalStrides,
            multisportEstablishedSport = multisportDetector.currentEstablishedSport,
            multisportTransitionCount = multisportTransitionCount
        )
        return OfflineReplayResult(scorecard = scorecard, series = finalSeries)
    }

    private fun mergeChronologically(
        phoneRows: List<PhoneRawTelemetryRow>,
        polarSessions: List<PolarRawSession>
    ): List<ReplayEvent> {
        val totalSize = phoneRows.size + polarSessions.sumOf { it.rows.size }
        val events = ArrayList<ReplayEvent>(totalSize)

        if (phoneRows.isNotEmpty()) {
            val firstNs = phoneRows.minOf { it.elapsedRealtimeNs }
            for (i in phoneRows.indices) {
                val r = phoneRows[i]
                val tRelMs = max(0L, (r.elapsedRealtimeNs - firstNs) / 1_000_000L)
                events.add(PhoneEvent(tRelMs = tRelMs, seqIndex = i, row = r))
            }
        }

        for (sIdx in polarSessions.indices) {
            val session = polarSessions[sIdx]
            if (session.rows.isEmpty()) continue
            val isVerity = isVerityModel(session.metadata.sensorModel, session.metadata.gyroRateHz)
            val firstMs = session.rows.minOf { it.elapsedMs }
            val sourceOrder = sIdx + 1
            for (i in session.rows.indices) {
                val r = session.rows[i]
                val tRelMs = max(0L, r.elapsedMs - firstMs)
                events.add(PolarEvent(tRelMs = tRelMs, sourceOrder = sourceOrder, seqIndex = i, isVerity = isVerity, row = r))
            }
        }

        events.sortWith(
            compareBy<ReplayEvent>({ it.tRelMs }, { it.sourceOrder }, { it.seqIndex })
        )
        return events
    }

    private fun isVerityModel(sensorModel: String, gyroRateHz: Int): Boolean {
        val lower = sensorModel.lowercase()
        return when {
            lower.contains("verity") || lower.contains("sense") || lower.contains("oh1") -> true
            lower.contains("h10") -> false
            else -> gyroRateHz > 0
        }
    }

    private fun compareCadence(a: CadenceAccumulator, b: CadenceAccumulator): CadencePairComparison? {
        if (a.bySecond.isEmpty() || b.bySecond.isEmpty()) return null
        var count = 0
        var sumAbs = 0.0
        var sumSigned = 0.0
        for ((sec, valA) in a.bySecond) {
            val valB = b.bySecond[sec] ?: continue
            val diff = valA - valB
            sumAbs += abs(diff)
            sumSigned += diff
            count++
        }
        if (count == 0) return null
        return CadencePairComparison(
            sourceA = a.sourceName,
            sourceB = b.sourceName,
            pairedSeconds = count,
            maeSpm = sumAbs / count,
            biasSpm = sumSigned / count
        )
    }

    private fun emptyResult(): OfflineReplayResult {
        return OfflineReplayResult(
            scorecard = OfflineReplayScorecard(
                durationSec = 0.0,
                totalEventsProcessed = 0,
                phoneEventsProcessed = 0,
                polarEventsProcessed = 0,
                carriageModeDistributionPct = emptyMap(),
                dominantCarriageMode = PhoneCarriageMode.UNKNOWN,
                dominantCarriagePct = 0.0,
                carriageTransitionCount = 0,
                inferredSport = BSportType.UNKNOWN,
                phoneCadence = CadenceStats("PhoneIMU", 0, null, null, null),
                polarH10Cadence = CadenceStats("PolarH10", 0, null, null, null),
                polarVerityCadence = CadenceStats("PolarVerity", 0, null, null, null),
                cadenceComparisons = emptyList(),
                h10MeanVerticalOscillationCm = null,
                h10MeanGroundContactTimeMs = null,
                ekfTotalDistanceM = 0.0,
                ekfAscentM = 0.0,
                ekfDescentM = 0.0,
                ekfMinSlopePct = 0.0,
                ekfMaxSlopePct = 0.0,
                ertsTotalDistanceM = null,
                ertsAscentM = null,
                ertsDescentM = null,
                ertsMinSlopePct = null,
                ertsMaxSlopePct = null,
                coldStartTimeToMotionSec = null
            ),
            series = emptyList()
        )
    }
}
