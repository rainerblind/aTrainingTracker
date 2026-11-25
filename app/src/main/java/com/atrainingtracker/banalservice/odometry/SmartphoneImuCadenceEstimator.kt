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

package com.atrainingtracker.banalservice.odometry

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pure-Kotlin live smartphone IMU cadence and running stride estimator (`ATT-8895`, `REQ-FIL-029`),
 * accepting standard Android SI units (`m/s^2` for accelerometer, `rad/s` for gyroscope) plus
 * `Sensor.TYPE_STEP_DETECTOR` events routed by [PhoneCarriageMode].
 *
 * - **Suppressed Modes (`null`)**: `BIKE_HANDLEBAR`, `UNKNOWN`, and `WALKING_TRANSITION` outside
 *   running are strictly suppressed (`onCadence(null)`, zero false RPM, zero stride increments).
 * - **`BIKE_FRONT_POCKET`**: `0.65–2.2 Hz` (`39–132 RPM`) 3D `GYRO+ACC` thigh-flexion ACF with a
 *   fast `1.5 s` de-biased angular RMS coasting gate (`< 0.25 rad/s` -> `0.0 RPM` within `2.0 s`).
 * - **`BIKE_JERSEY_BACK`**: `0.65–2.2 Hz` (`39–132 RPM`) 3D gyro ACF with `f`/`2f` subharmonic
 *   disambiguation and strict normalized prominence (`R(tau)/R(0) >= 0.45`).
 * - **`RUN_WAIST_HIP` (+ `WALKING_TRANSITION` while running)**: `1.2–4.0 Hz` impact bandpass +
 *   `TYPE_STEP_DETECTOR` fusion (`strides/min = steps/min / 2`, `+1` stride per `2` steps).
 * - **`RUN_BICEP_ARM` & `RUN_HANDHELD`**: `0.6–2.0 Hz` 3D `GYRO+ACC` arm-swing ACF +
 *   `TYPE_STEP_DETECTOR` fusion, wrist-glance IMU blanking (`isGlanceActive`), and deterministic
 *   `ACC + TYPE_STEP_DETECTOR` fallback when hardware/configured gyro is absent.
 */
class SmartphoneImuCadenceEstimator(
    private val onCadence: (Double?) -> Unit = {},
    private val onStride: (Int) -> Unit = {}
) {
    private enum class ModeCategory {
        SUPPRESSED,
        BIKE_FRONT_POCKET,
        BIKE_JERSEY_BACK,
        RUNNING_STEP,
        RUNNING_ARM
    }

    var latestCadence: Double? = null
        private set
    var totalStrides: Int = 0
        private set

    @Volatile
    var isRunningOrWalkingActive: Boolean = false
        private set

    private var activeCategory: ModeCategory = ModeCategory.SUPPRESSED
    private var unknownWalkStartTsMs: Long = Long.MIN_VALUE
    private var hasActiveCadenceSession = false
    private var lastEmittedWasNull: Boolean? = null
    private var lastEmitTsMs: Long = Long.MIN_VALUE
    private var lastMotionTsMs: Long = Long.MIN_VALUE
    private var lastAccSampleTsMs: Long = Long.MIN_VALUE
    private var lastGyroSampleTsMs: Long = Long.MIN_VALUE
    private var lastValidCandidateTsMs: Long = Long.MIN_VALUE
    private var coastingLowStartTsMs: Long = Long.MIN_VALUE
    private var isZeroed = false
    private var smoothedCadence = 0.0
    private var currentStepAcfIntervalMs: Long? = null
    private var currentArmAcfIntervalMs: Long? = null
    private var wasGlanceActive = false

    // Cycling resampling, bandpass, ring buffers & short coasting windows
    private val bikeAccResampler = UniformGridResampler(GRID_STEP_MS)
    private val bikeGyroResampler = UniformGridResampler(GRID_STEP_MS)
    private val bikeAccBp = Vector3BandPass(FS_HZ, BIKE_HPF_HZ, BIKE_LPF_HZ)
    private val bikeGyroBp = Vector3BandPass(FS_HZ, BIKE_HPF_HZ, BIKE_LPF_HZ)
    private val bikeAccVecRing = Vector3RingBuffer(WINDOW_SAMPLES)
    private val bikeGyroVecRing = Vector3RingBuffer(WINDOW_SAMPLES)
    private val pocketRawGyroWindow = ShortVector3RmsWindow(COASTING_RAW_WINDOW_SAMPLES)
    private val pocketRawAccWindow = ShortVector3RmsWindow(COASTING_RAW_WINDOW_SAMPLES)

    // Running resampling, bandpass, ring buffers (shared seamlessly across RUN_* transitions)
    private val runAccResampler = UniformGridResampler(GRID_STEP_MS)
    private val runGyroResampler = UniformGridResampler(GRID_STEP_MS)
    private val runImpactBp = BiquadBandPass(FS_HZ, RUN_IMPACT_HPF_HZ, RUN_IMPACT_LPF_HZ)
    private val runImpactRing = DoubleRingBuffer(WINDOW_SAMPLES)
    private val armAccBp = Vector3BandPass(FS_HZ, ARM_HPF_HZ, ARM_LPF_HZ)
    private val armGyroBp = Vector3BandPass(FS_HZ, ARM_HPF_HZ, ARM_LPF_HZ)
    private val armAccVecRing = Vector3RingBuffer(WINDOW_SAMPLES)
    private val armGyroVecRing = Vector3RingBuffer(WINDOW_SAMPLES)
    private val runRawAccWindow = ShortVector3RmsWindow(COASTING_RAW_WINDOW_SAMPLES)

    // Hardware TYPE_STEP_DETECTOR interval tracker
    private val stepIntervalsMs = LongArray(STEP_INTERVAL_HISTORY)
    private var stepIntervalCount = 0
    private var stepIntervalHead = 0
    private var lastHardwareStepTsMs: Long = Long.MIN_VALUE
    private var pendingHardwareSteps = 0

    // Waveform fallback stride/step peak detectors (active only without recent TYPE_STEP_DETECTOR)
    private var prevWave = 0.0
    private var prevPrevWave = 0.0
    private var wavePeakEnvelope = 0.0
    private var lastWaveStepTsMs: Long = Long.MIN_VALUE
    private var pendingWaveSteps = 0

    private var axisX = 0.0
    private var axisY = 0.0
    private var axisZ = 0.0
    private var prevArm = 0.0
    private var prevPrevArm = 0.0
    private var armPeakEnvelope = 0.0
    private var lastArmStrideTsMs: Long = Long.MIN_VALUE

    @Synchronized
    fun onAccSample(
        axMps2: Double,
        ayMps2: Double,
        azMps2: Double,
        timestampMs: Long,
        mode: PhoneCarriageMode,
        sportContext: SportContextHint = SportContextHint.UNKNOWN,
        isGyroEnabled: Boolean = true,
        isGlanceActive: Boolean = false
    ) {
        if (!axMps2.isFinite() || !ayMps2.isFinite() || !azMps2.isFinite() || timestampMs < 0L) return
        val category = syncModeState(mode, sportContext, timestampMs)
        if (category == ModeCategory.SUPPRESSED) return
        if (shouldBlankGlance(mode, isGlanceActive, timestampMs)) {
            maybeEmitCadence(category, timestampMs, isGyroEnabled, glanceActive = true)
            return
        }

        lastAccSampleTsMs = max(lastAccSampleTsMs, timestampMs)
        when (category) {
            ModeCategory.BIKE_FRONT_POCKET, ModeCategory.BIKE_JERSEY_BACK -> {
                ingestBikeAcc(axMps2, ayMps2, azMps2, timestampMs, category, isGyroEnabled)
            }
            ModeCategory.RUNNING_STEP, ModeCategory.RUNNING_ARM -> {
                ingestRunAcc(axMps2, ayMps2, azMps2, timestampMs, category, isGyroEnabled)
            }
            ModeCategory.SUPPRESSED -> Unit
        }
        evaluateCoastOrStandstill(category, timestampMs, isGyroEnabled, glanceActive = false)
        maybeEmitCadence(category, timestampMs, isGyroEnabled, glanceActive = false)
    }

    @Synchronized
    fun onGyroSample(
        gxRadPerSec: Double,
        gyRadPerSec: Double,
        gzRadPerSec: Double,
        timestampMs: Long,
        mode: PhoneCarriageMode,
        sportContext: SportContextHint = SportContextHint.UNKNOWN,
        isGlanceActive: Boolean = false
    ) {
        if (!gxRadPerSec.isFinite() || !gyRadPerSec.isFinite() || !gzRadPerSec.isFinite() || timestampMs < 0L) return
        val category = syncModeState(mode, sportContext, timestampMs)
        if (category == ModeCategory.SUPPRESSED) return
        if (shouldBlankGlance(mode, isGlanceActive, timestampMs)) return

        lastGyroSampleTsMs = max(lastGyroSampleTsMs, timestampMs)
        when (category) {
            ModeCategory.BIKE_FRONT_POCKET, ModeCategory.BIKE_JERSEY_BACK -> {
                ingestBikeGyro(gxRadPerSec, gyRadPerSec, gzRadPerSec, timestampMs, category)
            }
            ModeCategory.RUNNING_ARM, ModeCategory.RUNNING_STEP -> {
                ingestRunGyro(gxRadPerSec, gyRadPerSec, gzRadPerSec, timestampMs, category)
            }
            ModeCategory.SUPPRESSED -> Unit
        }
        evaluateCoastOrStandstill(category, timestampMs, isGyroEnabled = true, glanceActive = false)
        maybeEmitCadence(category, timestampMs, isGyroEnabled = true, glanceActive = false)
    }

    @Synchronized
    fun onStepDetected(
        timestampMs: Long,
        mode: PhoneCarriageMode,
        sportContext: SportContextHint = SportContextHint.UNKNOWN,
        isGlanceActive: Boolean = false
    ) {
        if (timestampMs < 0L) return
        val category = syncModeState(mode, sportContext, timestampMs)
        if (category != ModeCategory.RUNNING_STEP && category != ModeCategory.RUNNING_ARM) return

        val glanceBlocked = shouldBlankGlance(mode, isGlanceActive, timestampMs)
        if (!recordHardwareStep(timestampMs)) return
        if (glanceBlocked) {
            lastMotionTsMs = max(lastMotionTsMs, timestampMs)
            return
        }

        lastMotionTsMs = max(lastMotionTsMs, timestampMs)
        hasActiveCadenceSession = true
        pendingHardwareSteps++
        if (pendingHardwareSteps >= STEPS_PER_STRIDE) {
            pendingHardwareSteps -= STEPS_PER_STRIDE
            totalStrides++
            onStride(totalStrides)
        }
    }

    @Synchronized
    fun pollTick(
        timestampMs: Long,
        mode: PhoneCarriageMode,
        sportContext: SportContextHint = SportContextHint.UNKNOWN,
        isGyroEnabled: Boolean = true,
        isGlanceActive: Boolean = false
    ) {
        if (timestampMs < 0L) return
        val category = syncModeState(mode, sportContext, timestampMs)
        if (category == ModeCategory.SUPPRESSED) return
        val glanceActive = shouldBlankGlance(mode, isGlanceActive, timestampMs)
        evaluateCoastOrStandstill(category, timestampMs, isGyroEnabled, glanceActive)
        maybeEmitCadence(category, timestampMs, isGyroEnabled, glanceActive)
    }

    @Synchronized
    fun onModeOrSportChanged(
        mode: PhoneCarriageMode,
        sportContext: SportContextHint,
        timestampMs: Long
    ) {
        if (timestampMs < 0L) return
        syncModeState(mode, sportContext, timestampMs)
    }

    @Synchronized
    fun reset() {
        setActiveCategory(ModeCategory.SUPPRESSED)
        unknownWalkStartTsMs = Long.MIN_VALUE
        hasActiveCadenceSession = false
        latestCadence = null
        totalStrides = 0
        lastEmittedWasNull = null
        lastEmitTsMs = Long.MIN_VALUE
        lastMotionTsMs = Long.MIN_VALUE
        lastAccSampleTsMs = Long.MIN_VALUE
        lastGyroSampleTsMs = Long.MIN_VALUE
        lastValidCandidateTsMs = Long.MIN_VALUE
        coastingLowStartTsMs = Long.MIN_VALUE
        isZeroed = false
        smoothedCadence = 0.0
        currentStepAcfIntervalMs = null
        currentArmAcfIntervalMs = null
        wasGlanceActive = false
        clearCyclingBuffers()
        clearRunningBuffers()
    }

    private fun setActiveCategory(category: ModeCategory) {
        activeCategory = category
        isRunningOrWalkingActive = isRunningCategory(category)
    }

    private fun updateUnknownWalkDwell(
        mode: PhoneCarriageMode,
        sportContext: SportContextHint,
        timestampMs: Long
    ) {
        if (mode == PhoneCarriageMode.WALKING_TRANSITION && sportContext == SportContextHint.UNKNOWN) {
            if (unknownWalkStartTsMs == Long.MIN_VALUE || timestampMs < unknownWalkStartTsMs) {
                unknownWalkStartTsMs = timestampMs
            }
        } else if (!(mode == PhoneCarriageMode.STATIONARY &&
                hasActiveCadenceSession &&
                activeCategory == ModeCategory.RUNNING_STEP &&
                sportContext == SportContextHint.UNKNOWN)
        ) {
            unknownWalkStartTsMs = Long.MIN_VALUE
        }
    }

    private fun syncModeState(
        mode: PhoneCarriageMode,
        sportContext: SportContextHint,
        timestampMs: Long
    ): ModeCategory {
        updateUnknownWalkDwell(mode, sportContext, timestampMs)
        val desired = resolveCategory(mode, sportContext, timestampMs)
        if (desired == activeCategory) return desired

        if (desired == ModeCategory.SUPPRESSED) {
            suppressAll()
            return desired
        }

        val wasBike = isBikeCategory(activeCategory)
        val isBike = isBikeCategory(desired)
        if (wasBike != isBike || (isBike && desired != activeCategory)) {
            if (isBike) {
                clearRunningBuffers()
                clearCyclingBuffers()
            } else {
                clearCyclingBuffers()
            }
            smoothedCadence = 0.0
            currentStepAcfIntervalMs = null
            currentArmAcfIntervalMs = null
            lastValidCandidateTsMs = Long.MIN_VALUE
            isZeroed = false
            coastingLowStartTsMs = Long.MIN_VALUE
        } else if (activeCategory == ModeCategory.RUNNING_ARM && desired == ModeCategory.RUNNING_STEP) {
            currentArmAcfIntervalMs = null
        }
        setActiveCategory(desired)
        if (mode == PhoneCarriageMode.STATIONARY && hasActiveCadenceSession) {
            return desired
        }
        lastMotionTsMs = max(lastMotionTsMs, timestampMs)
        return desired
    }

    private fun resolveCategory(
        mode: PhoneCarriageMode,
        sportContext: SportContextHint,
        timestampMs: Long
    ): ModeCategory {
        return when (mode) {
            PhoneCarriageMode.BIKE_HANDLEBAR,
            PhoneCarriageMode.UNKNOWN -> ModeCategory.SUPPRESSED

            PhoneCarriageMode.WALKING_TRANSITION -> when (sportContext) {
                SportContextHint.RUNNING -> ModeCategory.RUNNING_STEP
                SportContextHint.UNKNOWN ->
                    if (unknownWalkStartTsMs != Long.MIN_VALUE &&
                        timestampMs - unknownWalkStartTsMs >= UNKNOWN_WALK_CONFIRM_DWELL_MS
                    ) {
                        ModeCategory.RUNNING_STEP
                    } else {
                        ModeCategory.SUPPRESSED
                    }
                SportContextHint.CYCLING,
                SportContextHint.OTHER -> ModeCategory.SUPPRESSED
            }

            PhoneCarriageMode.STATIONARY ->
                if (hasActiveCadenceSession &&
                    sportContext != SportContextHint.OTHER &&
                    !(sportContext == SportContextHint.CYCLING && isRunningCategory(activeCategory)) &&
                    !(sportContext == SportContextHint.RUNNING && isBikeCategory(activeCategory))
                ) {
                    activeCategory
                } else {
                    ModeCategory.SUPPRESSED
                }

            PhoneCarriageMode.BIKE_FRONT_POCKET ->
                if (sportContext == SportContextHint.RUNNING) {
                    ModeCategory.SUPPRESSED
                } else {
                    ModeCategory.BIKE_FRONT_POCKET
                }

            PhoneCarriageMode.BIKE_JERSEY_BACK ->
                if (sportContext == SportContextHint.RUNNING) {
                    ModeCategory.SUPPRESSED
                } else {
                    ModeCategory.BIKE_JERSEY_BACK
                }

            PhoneCarriageMode.RUN_WAIST_HIP -> ModeCategory.RUNNING_STEP
            PhoneCarriageMode.RUN_BICEP_ARM,
            PhoneCarriageMode.RUN_HANDHELD -> ModeCategory.RUNNING_ARM
        }
    }

    private fun suppressAll() {
        setActiveCategory(ModeCategory.SUPPRESSED)
        unknownWalkStartTsMs = Long.MIN_VALUE
        hasActiveCadenceSession = false
        latestCadence = null
        smoothedCadence = 0.0
        currentStepAcfIntervalMs = null
        currentArmAcfIntervalMs = null
        lastValidCandidateTsMs = Long.MIN_VALUE
        isZeroed = false
        coastingLowStartTsMs = Long.MIN_VALUE
        lastMotionTsMs = Long.MIN_VALUE
        wasGlanceActive = false
        clearCyclingBuffers()
        clearRunningBuffers()
        if (lastEmittedWasNull != true) {
            lastEmittedWasNull = true
            onCadence(null)
        }
    }

    private fun clearCyclingBuffers() {
        bikeAccResampler.reset()
        bikeGyroResampler.reset()
        bikeAccBp.reset()
        bikeGyroBp.reset()
        bikeAccVecRing.clear()
        bikeGyroVecRing.clear()
        pocketRawGyroWindow.clear()
        pocketRawAccWindow.clear()
    }

    private fun clearRunningBuffers() {
        runAccResampler.reset()
        runGyroResampler.reset()
        runImpactBp.reset()
        runImpactRing.clear()
        armAccBp.reset()
        armGyroBp.reset()
        armAccVecRing.clear()
        armGyroVecRing.clear()
        runRawAccWindow.clear()
        clearStepCounters()
    }

    private fun clearStepCounters() {
        stepIntervalCount = 0
        stepIntervalHead = 0
        lastHardwareStepTsMs = Long.MIN_VALUE
        pendingHardwareSteps = 0
        prevWave = 0.0
        prevPrevWave = 0.0
        wavePeakEnvelope = 0.0
        lastWaveStepTsMs = Long.MIN_VALUE
        pendingWaveSteps = 0
        axisX = 0.0
        axisY = 0.0
        axisZ = 0.0
        prevArm = 0.0
        prevPrevArm = 0.0
        armPeakEnvelope = 0.0
        lastArmStrideTsMs = Long.MIN_VALUE
    }

    private fun shouldBlankGlance(
        mode: PhoneCarriageMode,
        isGlanceActive: Boolean,
        timestampMs: Long
    ): Boolean {
        val active = isGlanceActive && mode.isRunningMode()
        if (active) {
            wasGlanceActive = true
            lastMotionTsMs = max(lastMotionTsMs, timestampMs)
            return true
        }
        if (wasGlanceActive) {
            wasGlanceActive = false
            // Reset resamplers only so UniformGridResampler never interpolates across glance hiatus.
            runAccResampler.reset()
            runGyroResampler.reset()
            lastMotionTsMs = max(lastMotionTsMs, timestampMs)
        }
        return false
    }

    private fun ingestBikeAcc(
        ax: Double,
        ay: Double,
        az: Double,
        timestampMs: Long,
        category: ModeCategory,
        isGyroEnabled: Boolean
    ) {
        val mag = sqrt(ax * ax + ay * ay + az * az)
        if (!mag.isFinite()) return
        bikeAccResampler.push(timestampMs, ax, ay, az, mag) { tMs, ix, iy, iz, _ ->
            pocketRawAccWindow.add(ix, iy, iz)
            val (fx, fy, fz) = bikeAccBp.step(ix, iy, iz)
            bikeAccVecRing.add(fx, fy, fz)
            if (category == ModeCategory.BIKE_FRONT_POCKET &&
                !isGyroStreamActive(tMs, isGyroEnabled) &&
                bikeAccVecRing.size >= MOTION_RMS_WINDOW &&
                pocketRawAccWindow.debiasedRms() >= BIKE_ACC_COASTING_RMS_MPS2
            ) {
                isZeroed = false
                coastingLowStartTsMs = Long.MIN_VALUE
                lastMotionTsMs = max(lastMotionTsMs, tMs)
            }
        }
    }

    private fun ingestBikeGyro(
        gx: Double,
        gy: Double,
        gz: Double,
        timestampMs: Long,
        category: ModeCategory
    ) {
        val mag = sqrt(gx * gx + gy * gy + gz * gz)
        if (!mag.isFinite()) return
        bikeGyroResampler.push(timestampMs, gx, gy, gz, mag) { tMs, ix, iy, iz, _ ->
            pocketRawGyroWindow.add(ix, iy, iz)
            val (fx, fy, fz) = bikeGyroBp.step(ix, iy, iz)
            bikeGyroVecRing.add(fx, fy, fz)
            val rawGyroRms = pocketRawGyroWindow.debiasedRms()
            val motionThreshold = if (category == ModeCategory.BIKE_JERSEY_BACK) {
                JERSEY_MIN_GYRO_RMS_RAD_S
            } else {
                POCKET_COASTING_GYRO_RMS_RAD_S
            }
            if (rawGyroRms >= motionThreshold) {
                isZeroed = false
                coastingLowStartTsMs = Long.MIN_VALUE
                lastMotionTsMs = max(lastMotionTsMs, tMs)
            }
        }
    }

    private fun ingestRunAcc(
        ax: Double,
        ay: Double,
        az: Double,
        timestampMs: Long,
        category: ModeCategory,
        isGyroEnabled: Boolean
    ) {
        val mag = sqrt(ax * ax + ay * ay + az * az)
        if (!mag.isFinite()) return
        runAccResampler.push(timestampMs, ax, ay, az, mag) { tMs, ix, iy, iz, iMag ->
            runRawAccWindow.add(ix, iy, iz)
            val impactFiltered = runImpactBp.step(iMag)
            runImpactRing.add(impactFiltered)
            val (afx, afy, afz) = armAccBp.step(ix, iy, iz)
            armAccVecRing.add(afx, afy, afz)

            val rawDevRms = runRawAccWindow.debiasedRms()
            if (rawDevRms >= RUN_ACC_MOTION_RMS_MPS2) {
                isZeroed = false
                lastMotionTsMs = max(lastMotionTsMs, tMs)
            }

            val useImpactFallback = category == ModeCategory.RUNNING_STEP ||
                (category == ModeCategory.RUNNING_ARM && !isGyroStreamActive(tMs, isGyroEnabled))
            if (useImpactFallback && !hasRecentHardwareSteps(tMs)) {
                detectImpactWaveformStep(tMs, impactFiltered)
            }
        }
    }

    private fun ingestRunGyro(
        gx: Double,
        gy: Double,
        gz: Double,
        timestampMs: Long,
        category: ModeCategory
    ) {
        val mag = sqrt(gx * gx + gy * gy + gz * gz)
        if (!mag.isFinite()) return
        runGyroResampler.push(timestampMs, gx, gy, gz, mag) { tMs, ix, iy, iz, _ ->
            val (gfx, gfy, gfz) = armGyroBp.step(ix, iy, iz)
            armGyroVecRing.add(gfx, gfy, gfz)
            if (mag >= ARM_GYRO_MOTION_RAD_S ||
                armGyroVecRing.recentRms(MOTION_RMS_WINDOW) >= ARM_GYRO_RMS_MIN_RAD_S
            ) {
                isZeroed = false
                lastMotionTsMs = max(lastMotionTsMs, tMs)
            }
            if (category == ModeCategory.RUNNING_ARM && !hasRecentHardwareSteps(tMs)) {
                detectArmWaveformStride(tMs, gfx, gfy, gfz)
            }
        }
    }

    private fun evaluateCoastOrStandstill(
        category: ModeCategory,
        timestampMs: Long,
        isGyroEnabled: Boolean,
        glanceActive: Boolean
    ) {
        if (glanceActive) return
        val gyroPresent = isGyroStreamActive(timestampMs, isGyroEnabled)

        if (category == ModeCategory.BIKE_FRONT_POCKET) {
            val isQuiet = if (gyroPresent && pocketRawGyroWindow.size >= COASTING_MIN_WINDOW_SAMPLES) {
                pocketRawGyroWindow.debiasedRms() < POCKET_COASTING_GYRO_RMS_RAD_S
            } else if (!gyroPresent && pocketRawAccWindow.size >= COASTING_MIN_WINDOW_SAMPLES) {
                pocketRawAccWindow.debiasedRms() < BIKE_ACC_COASTING_RMS_MPS2
            } else {
                false
            }
            updateCoastingGate(isQuiet, timestampMs)
            return
        }

        if (hasActiveCadenceSession &&
            lastMotionTsMs != Long.MIN_VALUE &&
            timestampMs - lastMotionTsMs >= STANDSTILL_TIMEOUT_MS
        ) {
            if (!isZeroed) {
                triggerZeroState(timestampMs, clearCycling = isBikeCategory(category))
            }
        }
    }

    private fun updateCoastingGate(isQuiet: Boolean, timestampMs: Long) {
        if (!isQuiet) {
            coastingLowStartTsMs = Long.MIN_VALUE
            return
        }
        if (coastingLowStartTsMs == Long.MIN_VALUE) {
            coastingLowStartTsMs = timestampMs
            return
        }
        if (!isZeroed && timestampMs - coastingLowStartTsMs >= POCKET_COASTING_TIMEOUT_MS) {
            triggerZeroState(timestampMs, clearCycling = true)
        }
    }

    private fun triggerZeroState(timestampMs: Long, clearCycling: Boolean) {
        isZeroed = true
        currentStepAcfIntervalMs = null
        currentArmAcfIntervalMs = null
        smoothedCadence = 0.0
        lastValidCandidateTsMs = Long.MIN_VALUE
        if (clearCycling) {
            bikeAccVecRing.clear()
            bikeGyroVecRing.clear()
        } else {
            runImpactRing.clear()
            armAccVecRing.clear()
            armGyroVecRing.clear()
            wavePeakEnvelope = 0.0
            armPeakEnvelope = 0.0
        }
        if (hasActiveCadenceSession) {
            lastEmitTsMs = timestampMs
            emitCadence(0.0)
        }
    }

    private fun maybeEmitCadence(
        category: ModeCategory,
        timestampMs: Long,
        isGyroEnabled: Boolean,
        glanceActive: Boolean
    ) {
        if (lastEmitTsMs != Long.MIN_VALUE && timestampMs - lastEmitTsMs < EMIT_INTERVAL_MS) return
        lastEmitTsMs = timestampMs

        if (isZeroed) {
            if (hasActiveCadenceSession) emitCadence(0.0)
            return
        }

        if (glanceActive) {
            if (smoothedCadence > 0.0) emitCadence(smoothedCadence)
            return
        }

        val candidate = computeCandidateCadence(category, timestampMs, isGyroEnabled)
        if (candidate != null) {
            hasActiveCadenceSession = true
            lastValidCandidateTsMs = timestampMs
            smoothedCadence = smoothCadence(smoothedCadence, candidate)
            emitCadence(smoothedCadence)
        } else if (smoothedCadence > 0.0) {
            if (category == ModeCategory.BIKE_JERSEY_BACK &&
                lastValidCandidateTsMs != Long.MIN_VALUE &&
                timestampMs - lastValidCandidateTsMs >= JERSEY_PROMINENCE_DROPOUT_MS
            ) {
                smoothedCadence = 0.0
                currentStepAcfIntervalMs = null
                currentArmAcfIntervalMs = null
                lastValidCandidateTsMs = Long.MIN_VALUE
                latestCadence = null
                if (lastEmittedWasNull != true) {
                    lastEmittedWasNull = true
                    onCadence(null)
                }
            } else {
                emitCadence(smoothedCadence)
            }
        }
    }

    private fun emitCadence(cadence: Double) {
        if (!cadence.isFinite()) return
        val rounded = (cadence * ROUNDING_SCALE).roundToInt() / ROUNDING_SCALE
        latestCadence = rounded
        lastEmittedWasNull = false
        onCadence(rounded)
    }

    private fun computeCandidateCadence(
        category: ModeCategory,
        timestampMs: Long,
        isGyroEnabled: Boolean
    ): Double? {
        return when (category) {
            ModeCategory.SUPPRESSED -> null
            ModeCategory.BIKE_FRONT_POCKET -> estimateFrontPocketCadence(timestampMs, isGyroEnabled)
            ModeCategory.BIKE_JERSEY_BACK -> estimateJerseyBackCadence(timestampMs, isGyroEnabled)
            ModeCategory.RUNNING_STEP -> estimateRunningStepCadence(timestampMs)
            ModeCategory.RUNNING_ARM -> estimateRunningArmCadence(timestampMs, isGyroEnabled)
        }
    }

    private fun estimateFrontPocketCadence(timestampMs: Long, isGyroEnabled: Boolean): Double? {
        val gyroPresent = isGyroStreamActive(timestampMs, isGyroEnabled)
        val gyroMoving = gyroPresent &&
            bikeGyroVecRing.size >= BIKE_MIN_OVERLAP_SAMPLES + BIKE_MIN_LAG_SAMPLES &&
            (pocketRawGyroWindow.size < COASTING_MIN_WINDOW_SAMPLES ||
                pocketRawGyroWindow.debiasedRms() >= POCKET_COASTING_GYRO_RMS_RAD_S)
        val accActive = !gyroPresent &&
            bikeAccVecRing.size >= BIKE_MIN_OVERLAP_SAMPLES + BIKE_MIN_LAG_SAMPLES &&
            pocketRawAccWindow.debiasedRms() >= BIKE_ACC_COASTING_RMS_MPS2
        val accFusionReady = gyroPresent &&
            bikeAccVecRing.size >= bikeGyroVecRing.size &&
            hasRecentAccSample(timestampMs)

        if (!gyroMoving && !accActive) return null
        val available = when {
            gyroMoving && accFusionReady -> min(bikeGyroVecRing.size, bikeAccVecRing.size)
            gyroMoving -> bikeGyroVecRing.size
            else -> bikeAccVecRing.size
        }

        val lagSamples = findFundamentalLag(
            minLag = BIKE_MIN_LAG_SAMPLES,
            maxLag = BIKE_MAX_LAG_SAMPLES,
            minOverlap = BIKE_MIN_OVERLAP_SAMPLES,
            availableSamples = available
        ) { lag ->
            when {
                gyroMoving && accFusionReady ->
                    GYRO_FUSION_WEIGHT * bikeGyroVecRing.normalizedVectorCorrelation(lag) +
                        ACC_FUSION_WEIGHT * bikeAccVecRing.normalizedVectorCorrelation(lag)
                gyroMoving -> bikeGyroVecRing.normalizedVectorCorrelation(lag)
                else -> bikeAccVecRing.normalizedVectorCorrelation(lag)
            }
        } ?: return null

        val periodSec = lagSamples / FS_HZ
        return (SECONDS_PER_MINUTE / periodSec).coerceIn(MIN_BIKE_CADENCE_RPM, MAX_BIKE_CADENCE_RPM)
    }

    private fun estimateJerseyBackCadence(timestampMs: Long, isGyroEnabled: Boolean): Double? {
        if (!isGyroStreamActive(timestampMs, isGyroEnabled)) return null
        if (bikeGyroVecRing.size < JERSEY_MIN_WARMUP_SAMPLES) return null
        if (pocketRawGyroWindow.size >= COASTING_MIN_WINDOW_SAMPLES &&
            pocketRawGyroWindow.debiasedRms() < JERSEY_MIN_GYRO_RMS_RAD_S
        ) {
            return null
        }

        val result = findFundamentalLagResult(
            minLag = BIKE_MIN_LAG_SAMPLES,
            maxLag = BIKE_MAX_LAG_SAMPLES,
            minOverlap = BIKE_MIN_OVERLAP_SAMPLES,
            availableSamples = bikeGyroVecRing.size,
            minPeakCorrelation = JERSEY_MIN_ACF_PEAK_CORRELATION,
            subharmonicGainThreshold = JERSEY_SUBHARMONIC_GAIN
        ) { lag -> bikeGyroVecRing.normalizedVectorCorrelation(lag) } ?: return null

        val periodSec = result.lagSamples / FS_HZ
        return (SECONDS_PER_MINUTE / periodSec).coerceIn(MIN_BIKE_CADENCE_RPM, MAX_BIKE_CADENCE_RPM)
    }

    private fun estimateRunningStepCadence(timestampMs: Long): Double? {
        val impactCand = estimateImpactAcfCadence()
        val stepCand = currentHardwareStepStridesPerMin(timestampMs)
        return when {
            impactCand != null && stepCand != null -> {
                if (abs(impactCand - stepCand) > HARMONIC_DISAGREEMENT_SPM) stepCand else impactCand
            }
            impactCand != null -> impactCand
            stepCand != null && hasRunningAccMotion() -> stepCand
            else -> null
        }
    }

    private fun estimateRunningArmCadence(timestampMs: Long, isGyroEnabled: Boolean): Double? {
        val gyroActive = isGyroStreamActive(timestampMs, isGyroEnabled) &&
            armGyroVecRing.size >= ARM_MIN_OVERLAP_SAMPLES + ARM_MIN_LAG_SAMPLES &&
            armGyroVecRing.recentRms(MOTION_RMS_WINDOW) >= ARM_GYRO_RMS_MIN_RAD_S
        if (!gyroActive) return estimateGyroAbsentArmCadence(timestampMs)

        val accArmReady = hasRecentAccSample(timestampMs) &&
            armAccVecRing.size >= armGyroVecRing.size &&
            armAccVecRing.recentRms(MOTION_RMS_WINDOW) >= ARM_ACC_RMS_MIN_MPS2
        val available = if (accArmReady) {
            min(armGyroVecRing.size, armAccVecRing.size)
        } else {
            armGyroVecRing.size
        }

        val lagSamples = findFundamentalLag(
            minLag = ARM_MIN_LAG_SAMPLES,
            maxLag = ARM_MAX_LAG_SAMPLES,
            minOverlap = ARM_MIN_OVERLAP_SAMPLES,
            availableSamples = available
        ) { lag ->
            if (accArmReady) {
                GYRO_FUSION_WEIGHT * armGyroVecRing.normalizedVectorCorrelation(lag) +
                    ACC_FUSION_WEIGHT * armAccVecRing.normalizedVectorCorrelation(lag)
            } else {
                armGyroVecRing.normalizedVectorCorrelation(lag)
            }
        } ?: return estimateGyroAbsentArmCadence(timestampMs)

        val periodSec = lagSamples / FS_HZ
        currentArmAcfIntervalMs = (periodSec * MILLIS_PER_SECOND).toLong()
        val acfSpm = (SECONDS_PER_MINUTE / periodSec).coerceIn(MIN_RUN_CADENCE_SPM, MAX_RUN_CADENCE_SPM)
        val stepCand = currentHardwareStepStridesPerMin(timestampMs)
        return if (stepCand != null && abs(acfSpm - stepCand) > HARMONIC_DISAGREEMENT_SPM) {
            stepCand
        } else {
            acfSpm
        }
    }

    private fun estimateGyroAbsentArmCadence(timestampMs: Long): Double? {
        val impactCand = estimateImpactAcfCadence()
        val armAccCand = estimateArmAccAcfCadence()
        if (impactCand == null && armAccCand != null) {
            // Preserve step-level refractory period when only arm-swing stride ACF locks on.
            currentArmAcfIntervalMs?.let { currentStepAcfIntervalMs = it / STEPS_PER_STRIDE }
        }
        val stepCand = currentHardwareStepStridesPerMin(timestampMs)

        if (stepCand != null && (hasRunningAccMotion() || runImpactRing.size > 0)) {
            if (impactCand != null && abs(impactCand - stepCand) <= HARMONIC_DISAGREEMENT_SPM) {
                return impactCand
            }
            if (armAccCand != null && abs(armAccCand - stepCand) <= HARMONIC_DISAGREEMENT_SPM) {
                return armAccCand
            }
            return stepCand
        }
        return impactCand ?: armAccCand
    }

    private fun estimateImpactAcfCadence(): Double? {
        if (runImpactRing.size < RUN_IMPACT_MIN_OVERLAP_SAMPLES + RUN_IMPACT_MIN_LAG_SAMPLES) return null
        if (runImpactRing.recentRms(MOTION_RMS_WINDOW) < RUN_ACC_MOTION_RMS_MPS2) return null
        val lagSamples = findFundamentalLag(
            minLag = RUN_IMPACT_MIN_LAG_SAMPLES,
            maxLag = RUN_IMPACT_MAX_LAG_SAMPLES,
            minOverlap = RUN_IMPACT_MIN_OVERLAP_SAMPLES,
            availableSamples = runImpactRing.size
        ) { lag -> runImpactRing.normalizedCorrelation(lag) } ?: return null

        val periodSec = lagSamples / FS_HZ
        currentStepAcfIntervalMs = (periodSec * MILLIS_PER_SECOND).toLong()
        val stepsPerMin = SECONDS_PER_MINUTE / periodSec
        return (stepsPerMin / STEPS_PER_STRIDE).coerceIn(MIN_RUN_CADENCE_SPM, MAX_RUN_CADENCE_SPM)
    }

    private fun estimateArmAccAcfCadence(): Double? {
        if (armAccVecRing.size < ARM_MIN_OVERLAP_SAMPLES + ARM_MIN_LAG_SAMPLES) return null
        if (armAccVecRing.recentRms(MOTION_RMS_WINDOW) < ARM_ACC_RMS_MIN_MPS2) return null
        val lagSamples = findFundamentalLag(
            minLag = ARM_MIN_LAG_SAMPLES,
            maxLag = ARM_MAX_LAG_SAMPLES,
            minOverlap = ARM_MIN_OVERLAP_SAMPLES,
            availableSamples = armAccVecRing.size
        ) { lag -> armAccVecRing.normalizedVectorCorrelation(lag) } ?: return null

        val periodSec = lagSamples / FS_HZ
        currentArmAcfIntervalMs = (periodSec * MILLIS_PER_SECOND).toLong()
        return (SECONDS_PER_MINUTE / periodSec).coerceIn(MIN_RUN_CADENCE_SPM, MAX_RUN_CADENCE_SPM)
    }

    private fun recordHardwareStep(timestampMs: Long): Boolean {
        if (timestampMs < 0L) return false
        val prevTs = lastHardwareStepTsMs
        if (prevTs != Long.MIN_VALUE) {
            val deltaMs = timestampMs - prevTs
            if (deltaMs < MIN_STEP_INTERVAL_MS) return false
            if (deltaMs in MIN_STEP_INTERVAL_MS..MAX_STEP_INTERVAL_MS) {
                stepIntervalsMs[stepIntervalHead] = deltaMs
                stepIntervalHead = (stepIntervalHead + 1) % STEP_INTERVAL_HISTORY
                if (stepIntervalCount < STEP_INTERVAL_HISTORY) stepIntervalCount++
            } else if (deltaMs > MAX_STEP_INTERVAL_MS) {
                stepIntervalCount = 0
                stepIntervalHead = 0
                pendingHardwareSteps = 0
            }
        }
        lastHardwareStepTsMs = max(prevTs, timestampMs)
        return true
    }

    private fun currentHardwareStepStridesPerMin(nowMs: Long): Double? {
        if (lastHardwareStepTsMs == Long.MIN_VALUE || nowMs - lastHardwareStepTsMs > STEP_STREAM_STALE_MS) {
            return null
        }
        if (stepIntervalCount < MIN_HARDWARE_STEP_INTERVALS) return null
        var sumMs = 0L
        for (i in 0 until stepIntervalCount) {
            sumMs += stepIntervalsMs[i]
        }
        val meanStepMs = sumMs.toDouble() / stepIntervalCount
        if (meanStepMs <= ImuCadenceDspConstants.EPSILON) return null
        val stridesPerMin = SECONDS_PER_MINUTE / ((meanStepMs * STEPS_PER_STRIDE) / MILLIS_PER_SECOND)
        return stridesPerMin.coerceIn(MIN_RUN_CADENCE_SPM, MAX_RUN_CADENCE_SPM)
    }

    private fun detectImpactWaveformStep(tMs: Long, current: Double) {
        if (!current.isFinite()) return
        wavePeakEnvelope = max(abs(current), wavePeakEnvelope * PEAK_ENVELOPE_DECAY)
        val threshold = max(RUN_IMPACT_MIN_PEAK_MPS2, ADAPTIVE_PEAK_RATIO * wavePeakEnvelope)
        val refractoryMs = currentStepAcfIntervalMs
            ?.let { max(MIN_STEP_INTERVAL_MS, (REFRACTORY_FRACTION * it).toLong()) }
            ?: MIN_STEP_INTERVAL_MS

        if (prevWave > prevPrevWave &&
            prevWave >= current &&
            prevWave >= threshold &&
            (lastWaveStepTsMs == Long.MIN_VALUE || tMs - lastWaveStepTsMs >= refractoryMs)
        ) {
            lastWaveStepTsMs = tMs
            hasActiveCadenceSession = true
            pendingWaveSteps++
            if (pendingWaveSteps >= STEPS_PER_STRIDE) {
                pendingWaveSteps -= STEPS_PER_STRIDE
                totalStrides++
                onStride(totalStrides)
            }
        }
        prevPrevWave = prevWave
        prevWave = current
    }

    private fun detectArmWaveformStride(tMs: Long, gx: Double, gy: Double, gz: Double) {
        if (!gx.isFinite() || !gy.isFinite() || !gz.isFinite()) return
        val norm = sqrt(gx * gx + gy * gy + gz * gz)
        if (norm > ImuCadenceDspConstants.EPSILON) {
            val dot = gx * axisX + gy * axisY + gz * axisZ
            val sign = if (dot < 0.0) -1.0 else 1.0
            val ux = (1.0 - AXIS_ALPHA) * axisX + AXIS_ALPHA * sign * (gx / norm)
            val uy = (1.0 - AXIS_ALPHA) * axisY + AXIS_ALPHA * sign * (gy / norm)
            val uz = (1.0 - AXIS_ALPHA) * axisZ + AXIS_ALPHA * sign * (gz / norm)
            val uLen = sqrt(ux * ux + uy * uy + uz * uz)
            if (uLen > ImuCadenceDspConstants.EPSILON) {
                axisX = ux / uLen
                axisY = uy / uLen
                axisZ = uz / uLen
            }
        }

        val proj = gx * axisX + gy * axisY + gz * axisZ
        armPeakEnvelope = max(abs(proj), armPeakEnvelope * PEAK_ENVELOPE_DECAY)
        val threshold = max(ARM_MIN_GYRO_PEAK_RAD_S, ADAPTIVE_PEAK_RATIO * armPeakEnvelope)
        val refractoryMs = currentArmAcfIntervalMs
            ?.let { max(MIN_STRIDE_INTERVAL_MS, (REFRACTORY_FRACTION * it).toLong()) }
            ?: MIN_STRIDE_INTERVAL_MS

        if (prevArm > prevPrevArm &&
            prevArm >= proj &&
            prevArm >= threshold &&
            (lastArmStrideTsMs == Long.MIN_VALUE || tMs - lastArmStrideTsMs >= refractoryMs)
        ) {
            lastArmStrideTsMs = tMs
            hasActiveCadenceSession = true
            totalStrides++
            onStride(totalStrides)
        }
        prevPrevArm = prevArm
        prevArm = proj
    }

    private fun isGyroStreamActive(nowMs: Long, isGyroEnabled: Boolean): Boolean =
        isGyroEnabled &&
            lastGyroSampleTsMs != Long.MIN_VALUE &&
            nowMs - lastGyroSampleTsMs <= STREAM_STALE_MS

    private fun hasRecentAccSample(nowMs: Long): Boolean =
        lastAccSampleTsMs != Long.MIN_VALUE &&
            nowMs - lastAccSampleTsMs <= STREAM_STALE_MS

    private fun hasRecentHardwareSteps(nowMs: Long): Boolean =
        lastHardwareStepTsMs != Long.MIN_VALUE &&
            nowMs - lastHardwareStepTsMs <= STEP_STREAM_STALE_MS

    private fun hasRunningAccMotion(): Boolean =
        (runRawAccWindow.size >= COASTING_MIN_WINDOW_SAMPLES &&
            runRawAccWindow.debiasedRms() >= STEP_FALLBACK_ACC_RMS_MPS2) ||
            runImpactRing.recentRms(MOTION_RMS_WINDOW) >= STEP_FALLBACK_ACC_RMS_MPS2

    private fun isBikeCategory(category: ModeCategory): Boolean =
        category == ModeCategory.BIKE_FRONT_POCKET || category == ModeCategory.BIKE_JERSEY_BACK

    private fun isRunningCategory(category: ModeCategory): Boolean =
        category == ModeCategory.RUNNING_STEP || category == ModeCategory.RUNNING_ARM

    companion object {
        const val UNKNOWN_WALK_CONFIRM_DWELL_MS = 25_000L
        private const val FS_HZ = ImuCadenceDspConstants.FS_HZ
        private const val GRID_STEP_MS = ImuCadenceDspConstants.GRID_STEP_MS
        private const val WINDOW_SAMPLES = ImuCadenceDspConstants.WINDOW_SAMPLES
        private const val MILLIS_PER_SECOND = ImuCadenceDspConstants.MILLIS_PER_SECOND
        private const val SECONDS_PER_MINUTE = 60.0
        private const val ROUNDING_SCALE = 10.0

        // Cycling bandpass (0.65-2.2 Hz = 39-132 RPM)
        private const val BIKE_HPF_HZ = 0.65
        private const val BIKE_LPF_HZ = 2.2
        private const val BIKE_MIN_LAG_SAMPLES = 22 // ~2.27 Hz
        private const val BIKE_MAX_LAG_SAMPLES = 77 // ~0.65 Hz
        private const val BIKE_MIN_OVERLAP_SAMPLES = 35
        private const val JERSEY_MIN_WARMUP_SAMPLES = BIKE_MAX_LAG_SAMPLES + BIKE_MIN_OVERLAP_SAMPLES
        private const val JERSEY_MIN_ACF_PEAK_CORRELATION = 0.45
        private const val JERSEY_SUBHARMONIC_GAIN = 0.06
        private const val JERSEY_PROMINENCE_DROPOUT_MS = 3000L
        private const val MIN_BIKE_CADENCE_RPM = 39.0
        private const val MAX_BIKE_CADENCE_RPM = 132.0

        // Running waist/hip impact bandpass (1.2-4.0 Hz = 72-240 steps/min = 36-120 strides/min)
        private const val RUN_IMPACT_HPF_HZ = 1.2
        private const val RUN_IMPACT_LPF_HZ = 4.0
        private const val RUN_IMPACT_MIN_LAG_SAMPLES = 12
        private const val RUN_IMPACT_MAX_LAG_SAMPLES = 42
        private const val RUN_IMPACT_MIN_OVERLAP_SAMPLES = 45

        // Running bicep/handheld arm-swing bandpass (0.6-2.0 Hz = 36-120 strides/min)
        private const val ARM_HPF_HZ = 0.6
        private const val ARM_LPF_HZ = 2.0
        private const val ARM_MIN_LAG_SAMPLES = 25
        private const val ARM_MAX_LAG_SAMPLES = 83
        private const val ARM_MIN_OVERLAP_SAMPLES = 35

        private const val MIN_RUN_CADENCE_SPM = 36.0
        private const val MAX_RUN_CADENCE_SPM = 120.0

        // Fusion & coasting/standstill gates
        private const val GYRO_FUSION_WEIGHT = 0.70
        private const val ACC_FUSION_WEIGHT = 0.30
        private const val COASTING_RAW_WINDOW_SAMPLES = 15 // 0.30 s at 50 Hz
        private const val COASTING_MIN_WINDOW_SAMPLES = 10
        private const val POCKET_COASTING_GYRO_RMS_RAD_S = 0.25
        private const val POCKET_COASTING_TIMEOUT_MS = 1500L
        private const val BIKE_ACC_COASTING_RMS_MPS2 = 0.35
        private const val JERSEY_MIN_GYRO_RMS_RAD_S = 0.04

        private const val MOTION_RMS_WINDOW = 25
        private const val EMIT_INTERVAL_MS = 1000L
        private const val STANDSTILL_TIMEOUT_MS = 2000L
        private const val STREAM_STALE_MS = 1500L
        private const val STEP_STREAM_STALE_MS = 2500L

        private const val RUN_ACC_MOTION_RMS_MPS2 = 0.35
        private const val RUN_IMPACT_MIN_PEAK_MPS2 = 0.45
        private const val STEP_FALLBACK_ACC_RMS_MPS2 = 0.20
        private const val ARM_GYRO_MOTION_RAD_S = 0.18
        private const val ARM_GYRO_RMS_MIN_RAD_S = 0.11
        private const val ARM_ACC_RMS_MIN_MPS2 = 0.40
        private const val ARM_MIN_GYRO_PEAK_RAD_S = 0.18

        // Step/stride intervals & waveform peak detection
        private const val STEPS_PER_STRIDE = 2
        private const val STEP_INTERVAL_HISTORY = 4
        private const val MIN_HARDWARE_STEP_INTERVALS = 2
        private const val MIN_STEP_INTERVAL_MS = 220L
        private const val MAX_STEP_INTERVAL_MS = 1667L
        private const val MIN_STRIDE_INTERVAL_MS = 480L
        private const val HARMONIC_DISAGREEMENT_SPM = 15.0
        private const val PEAK_ENVELOPE_DECAY = 0.99
        private const val ADAPTIVE_PEAK_RATIO = 0.35
        private const val REFRACTORY_FRACTION = 0.62
        private const val AXIS_ALPHA = 0.05
    }
}

/** Sliding 3D vector window computing mean-removed (DC-bias-free) RMS for coasting gates. */
private class ShortVector3RmsWindow(private val capacity: Int) {
    private val xs = DoubleArray(capacity)
    private val ys = DoubleArray(capacity)
    private val zs = DoubleArray(capacity)
    private var head = 0
    var size = 0
        private set

    fun clear() {
        head = 0
        size = 0
    }

    fun add(x: Double, y: Double, z: Double) {
        if (!x.isFinite() || !y.isFinite() || !z.isFinite() || capacity <= 0) return
        xs[head] = x
        ys[head] = y
        zs[head] = z
        head = (head + 1) % capacity
        if (size < capacity) size++
    }

    fun debiasedRms(): Double {
        if (size <= 0) return 0.0
        var sumX = 0.0
        var sumY = 0.0
        var sumZ = 0.0
        for (i in 0 until size) {
            sumX += xs[i]
            sumY += ys[i]
            sumZ += zs[i]
        }
        val mx = sumX / size
        val my = sumY / size
        val mz = sumZ / size
        var sumSq = 0.0
        for (i in 0 until size) {
            val dx = xs[i] - mx
            val dy = ys[i] - my
            val dz = zs[i] - mz
            sumSq += dx * dx + dy * dy + dz * dz
        }
        val rms = sqrt(sumSq / size)
        return if (rms.isFinite()) rms else 0.0
    }
}
