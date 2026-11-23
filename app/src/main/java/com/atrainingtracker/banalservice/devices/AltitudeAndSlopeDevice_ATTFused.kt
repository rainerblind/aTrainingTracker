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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.banalservice.devices

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.preference.PreferenceManager
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.odometry.BarometricBucketDispersion
import com.atrainingtracker.banalservice.odometry.KinematicAltitudeSlopeEkf
import com.atrainingtracker.banalservice.odometry.PhoneCarriageMode
import com.atrainingtracker.banalservice.sensor.MyDoubleAccumulatorSensor
import com.atrainingtracker.banalservice.sensor.MySensor
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max

/**
 * Android Device Driver for ATT Kinematic Altitude & Slope Fusion Virtual Sensor (REQ-FIL-016, ATT-6279).
 *
 * Coordinates:
 * - [KinematicAltitudeSlopeEkf]: 2-State / 3-State coupled kinematic filter ($dh = g \cdot ds$) with standstill fix.
 * - [BarometricAltitudeFilter]: Slew-clamped barometric altitude ingestion without double-EMA phase lag.
 *
 * Emits fused [SensorType.ALTITUDE], [SensorType.SLOPE], [SensorType.VERTICAL_SPEED],
 * [SensorType.ASCENT], and [SensorType.DESCENT].
 */
@Suppress("ClassNaming", "MagicNumber", "TooGenericExceptionCaught", "EmptyFunctionBlock", "ReturnCount")
open class AltitudeAndSlopeDevice_ATTFused @JvmOverloads constructor(
    context: Context,
    mySensorManager: MySensorManager,
    autoStartScheduler: Boolean = true
) : MyDevice(context, mySensorManager, DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED),
    SensorEventListener {

    companion object {
        private const val TAG = "AltitudeSlopeDev_ATT"
        private val DEBUG = BANALService.getDebug(false)
        private const val DEFAULT_MIN_SPEED_MPS = 0.5
        const val TECHNICAL_DEVICE_NAME = "att_fused_altitude_slope"
        const val ASCENT_DESCENT_HYSTERESIS_METERS = 2.0
        const val DEM_STALENESS_THRESHOLD_MS = 15_000L
        const val STANDSTILL_SLOPE_CLEAR_DELAY_MS = 10_000L
        /** 1-Hz rate-matching window (tolerates ~200 ms hardware sensor jitter while flushing 1-Hz ticks). */
        const val BARO_AGGREGATION_WINDOW_MS = 950L
        /** Grace window deferring unanchored 3-State sensor publication until DEM/GPS lock (REQ-FIL-040.3). */
        const val STARTUP_REFERENCE_GRACE_MS = 5_000L
    }

    val ekf = KinematicAltitudeSlopeEkf()
    val baroFilter = BarometricAltitudeFilter()

    var mAltitudeSensor: MySensor<Number>? = null
        protected set
    var mSlopeSensor: MySensor<Int>? = null
        protected set
    var mVerticalSpeedSensor: MySensor<Int>? = null
        protected set
    var mAscentSensor: MyDoubleAccumulatorSensor? = null
        protected set
    var mDescentSensor: MyDoubleAccumulatorSensor? = null
        protected set

    protected var mHardwareSensorManager: SensorManager? = null
    protected var mPressureSensor: Sensor? = null
    protected var mPressureSensorRegistered = false

    private var mScheduler: ScheduledExecutorService? = null
    private var mLastAltitude: Double = Double.NaN
    private var mAscentDescentAnchorAltitude: Double = Double.NaN
    private var mLastStepTimestampMs: Long = 0L
    private var mStandstillStartTimestampMs: Long = 0L
    private var mLastDemUpdateTimestampMs: Long = 0L
    private var mLastGpsUpdateTimestampMs: Long = 0L
    private val mBaroBucket = BarometricBucketDispersion()
    private var mFirstBaroTimestampMs: Long = -1L
    private var mLastBaroEmitTimestampMs: Long = -1L
    private var mLastBaroBucketTimestampMs: Long = -1L
    private var mEffectiveRBaro: Double = KinematicAltitudeSlopeEkf.DEFAULT_VAR_BARO
    private var mLastObservedCarriageMode: PhoneCarriageMode = PhoneCarriageMode.UNKNOWN

    @Volatile
    var isEffectiveBarometer: Boolean = true
        private set

    @Volatile
    var isEffectiveDem: Boolean = false
        private set

    @Volatile
    var isEffectiveGps: Boolean = false
        private set

    @Volatile
    var isEffectiveSpeed: Boolean = true
        private set

    @VisibleForTesting
    var lastDemUpdateTimestampMs: Long
        get() = mLastDemUpdateTimestampMs
        set(value) { mLastDemUpdateTimestampMs = value }

    @VisibleForTesting
    var lastGpsUpdateTimestampMs: Long
        get() = mLastGpsUpdateTimestampMs
        set(value) { mLastGpsUpdateTimestampMs = value }

    private val mPrefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key.startsWith("pref_att_fused_alt_")) {
            refreshCachedPreferences()
        }
    }

    init {
        val db = DevicesDatabaseManager.getInstance(mContext)
        mDeviceId = db.altitudeAndSlopeAttFusedDeviceId
        mHardwareSensorManager = mContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(mContext)
            prefs?.registerOnSharedPreferenceChangeListener(mPrefChangeListener)
        } catch (e: Exception) {
            Log.d(TAG, "Could not register SharedPreferences listener: ${e.message}")
        }

        refreshCachedPreferences()
        registerSensors()
        registerPressureSensor()

        if (autoStartScheduler) {
            val scheduler = Executors.newSingleThreadScheduledExecutor()
            mScheduler = scheduler
            scheduler.scheduleWithFixedDelay(::calculateMetrics, 1, 1, TimeUnit.SECONDS)
        }
    }

    override fun addSensors() {
        val alt = MySensor<Number>(this, SensorType.ALTITUDE)
        val slope = MySensor<Int>(this, SensorType.SLOPE)
        val vam = MySensor<Int>(this, SensorType.VERTICAL_SPEED)
        val ascent = MyDoubleAccumulatorSensor(this, SensorType.ASCENT, true)
        val descent = MyDoubleAccumulatorSensor(this, SensorType.DESCENT, true)

        mAltitudeSensor = alt
        mSlopeSensor = slope
        mVerticalSpeedSensor = vam
        mAscentSensor = ascent
        mDescentSensor = descent

        addSensor(alt)
        addSensor(slope)
        addSensor(vam)
        addSensor(ascent)
        addSensor(descent)
    }

    override fun getName(): String = TECHNICAL_DEVICE_NAME

    @Synchronized
    override fun shutDown() {
        super.shutDown()
        mScheduler?.let {
            if (!it.isShutdown) it.shutdown()
        }
        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(mContext)
            prefs?.unregisterOnSharedPreferenceChangeListener(mPrefChangeListener)
        } catch (e: Exception) {
            Log.d(TAG, "Could not unregister SharedPreferences listener: ${e.message}")
        }
        mHardwareSensorManager?.unregisterListener(this)
        mPressureSensor = null
        mPressureSensorRegistered = false
        baroFilter.reset()
        ekf.reset()
        mLastAltitude = Double.NaN
        mAscentDescentAnchorAltitude = Double.NaN
        mLastStepTimestampMs = 0L
        mStandstillStartTimestampMs = 0L
        mLastDemUpdateTimestampMs = 0L
        mLastGpsUpdateTimestampMs = 0L
        mBaroBucket.reset()
        mFirstBaroTimestampMs = -1L
        mLastBaroEmitTimestampMs = -1L
        mLastBaroBucketTimestampMs = -1L
        mEffectiveRBaro = KinematicAltitudeSlopeEkf.DEFAULT_VAR_BARO
        mLastObservedCarriageMode = PhoneCarriageMode.UNKNOWN
    }

    @Synchronized
    fun registerPressureSensor() {
        if (mPressureSensorRegistered) return
        val sm = mHardwareSensorManager ?: return
        if (!isEffectiveBarometer) return
        try {
            val pressure = sm.getDefaultSensor(Sensor.TYPE_PRESSURE)
            if (pressure != null) {
                sm.registerListener(this, pressure, SensorManager.SENSOR_DELAY_NORMAL)
                mPressureSensor = pressure
                mPressureSensorRegistered = true
                setLastActive()
                if (DEBUG) Log.i(TAG, "Hardware pressure listener registered")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register pressure listener: ${e.message}")
        }
    }

    @Synchronized
    fun unregisterPressureSensor() {
        if (!mPressureSensorRegistered) return
        mHardwareSensorManager?.unregisterListener(this)
        mPressureSensor = null
        mPressureSensorRegistered = false
        if (DEBUG) Log.i(TAG, "Hardware pressure listener unregistered")
    }

    /**
     * Refreshes effective device availability and preferences into in-memory cached flags.
     * Invoked upon creation, preference broadcasts, and on the 1-Hz background scheduler thread.
     */
    @Synchronized
    fun refreshCachedPreferences() {
        val state = AttFusedAltitudePreferences.getEffectiveState(mContext)
        isEffectiveBarometer = state.isBarometerActive
        isEffectiveDem = state.isDemActive
        isEffectiveGps = state.isGpsActive
        isEffectiveSpeed = state.isSpeedActive

        ekf.setMode(state.isDemActive || state.isGpsActive)

        if (isEffectiveBarometer && !mPressureSensorRegistered) {
            registerPressureSensor()
        } else if (!isEffectiveBarometer && mPressureSensorRegistered) {
            unregisterPressureSensor()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_PRESSURE) return
        if (!isEffectiveBarometer) return
        handlePressureMeasurement(event.values[0], System.currentTimeMillis())
    }

    /**
     * Ingests a raw barometric pressure sample (REQ-FIL-026.1, REQ-FIL-040.1).
     * Immediately initializes EKF on the first sample, then aggregates sub-second events via
     * [BarometricBucketDispersion] (calm 1-Hz median vs multi-second trimmed mean + dispersion
     * variance inflation under pocket-compression aliasing) before invoking
     * [BarometricAltitudeFilter.filterSlewOnly] and [KinematicAltitudeSlopeEkf.updateBarometer].
     */
    @Synchronized
    @VisibleForTesting
    fun handlePressureMeasurement(pressureHpa: Float, timestampMs: Long = System.currentTimeMillis()) {
        val rawAlt = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressureHpa).toDouble()
        if (!rawAlt.isFinite()) return

        if (mFirstBaroTimestampMs < 0L || timestampMs < mFirstBaroTimestampMs) {
            mFirstBaroTimestampMs = timestampMs
        }

        if (mLastBaroEmitTimestampMs < 0L || !ekf.isInitialized || timestampMs < mLastBaroEmitTimestampMs) {
            mBaroBucket.reset()
            mBaroBucket.addSample(rawAlt, timestampMs)
            val flushed = mBaroBucket.flushBucket(timestampMs, getCurrentSpeed())
            mLastBaroEmitTimestampMs = timestampMs
            mLastBaroBucketTimestampMs = timestampMs
            if (flushed != null) {
                ingestFilteredBaroAltitude(flushed.representativeAltitudeM, timestampMs, flushed.dispersionVarM2)
            }
            return
        }

        if (timestampMs - mLastBaroEmitTimestampMs > BarometricBucketDispersion.MULTI_SECOND_WINDOW_MS) {
            // Pause/resume gap: discard stale pre-pause dispersion/window state (REQ-FIL-040.1)
            mBaroBucket.reset()
        }

        mBaroBucket.addSample(rawAlt, timestampMs)
        mLastBaroBucketTimestampMs = timestampMs
        if (timestampMs - mLastBaroEmitTimestampMs >= BARO_AGGREGATION_WINDOW_MS) {
            val flushed = mBaroBucket.flushBucket(timestampMs, getCurrentSpeed())
            mLastBaroEmitTimestampMs = timestampMs
            if (flushed != null) {
                ingestFilteredBaroAltitude(flushed.representativeAltitudeM, timestampMs, flushed.dispersionVarM2)
            }
        }
    }

    private fun resolveCurrentCarriageMode(): PhoneCarriageMode {
        val modeSensor = try {
            mMySensorManager.getSensorForDeviceType(
                SensorType.ACTIVITY_AND_CARRIAGE_MODE,
                DeviceType.SPEED_AND_LOCATION_ATT_FUSED
            ) ?: mMySensorManager.getSensor(SensorType.ACTIVITY_AND_CARRIAGE_MODE)
        } catch (_: Throwable) {
            null
        }
        val code = (modeSensor?.value as? Number)?.toInt() ?: return PhoneCarriageMode.UNKNOWN
        return PhoneCarriageMode.fromCode(code)
    }

    private fun syncAdaptiveCarriageParameters(): PhoneCarriageMode {
        val mode = resolveCurrentCarriageMode()
        val profile = mode.profile
        mLastObservedCarriageMode = mode
        val alpha = 0.35
        mEffectiveRBaro += alpha * (profile.rBaroM2 - mEffectiveRBaro)
        if (abs(profile.rBaroM2 - mEffectiveRBaro) < 0.01) {
            mEffectiveRBaro = profile.rBaroM2
        }
        mEffectiveRBaro = max(0.1, mEffectiveRBaro)
        baroFilter.setFilterParameters(profile.baroMaxSlewRateMps, profile.baroMedianWindowSize)
        return mode
    }

    @VisibleForTesting
    internal fun effectiveRBaroForTest(): Double = mEffectiveRBaro

    @VisibleForTesting
    internal fun resolvedCarriageModeForTest(): PhoneCarriageMode = mLastObservedCarriageMode

    @VisibleForTesting
    internal fun baroDispersionVarForTest(): Double = mBaroBucket.dispersionVarM2

    private fun ingestFilteredBaroAltitude(
        rawAltM: Double,
        timestampMs: Long,
        dispersionVarM2: Double = 0.0
    ) {
        val mode = syncAdaptiveCarriageParameters()
        val speed = getCurrentSpeed()
        val slewSpeed = if (dispersionVarM2 > 0.0) 0.0 else speed
        val slewAlt = baroFilter.filterSlewOnly(rawAltM, timestampMs, slewSpeed)
        val isStandstill = mode.profile.forceStationaryPinning || speed < ekf.standstillSpeedThresholdMps
        val varBaro = mEffectiveRBaro + max(0.0, dispersionVarM2)

        ekf.setMode(isEffectiveDem || isEffectiveGps)
        ekf.updateBarometer(slewAlt, varBaro = varBaro, isStandstill = isStandstill, speedMps = speed)
        publishAltitudeAndMetrics(speed, timestampMs)
    }

    @Synchronized
    @VisibleForTesting
    fun handleReferenceAltitude(
        refAltM: Double,
        isDem: Boolean = true,
        nowMs: Long = System.currentTimeMillis()
    ) {
        ekf.setMode(isEffectiveDem || isEffectiveGps)
        // REQ-FIL-037.3: Retuned DEM (25 m^2) / GPS (64 m^2) variance + post-anchor step-discrepancy gating
        val reanchored = ekf.updateReferenceAltitude(
            refAltM,
            if (isDem) KinematicAltitudeSlopeEkf.DEFAULT_VAR_DEM else KinematicAltitudeSlopeEkf.DEFAULT_VAR_GPS
        )
        if (reanchored) {
            resetAscentDescentAnchor()
        }
        publishAltitudeAndMetrics(getCurrentSpeed(), nowMs)
    }

    @Synchronized
    @VisibleForTesting
    fun calculateMetrics(nowMs: Long = System.currentTimeMillis()) {
        if (TrainingApplication.isPaused() || TrainingApplication.isAutoPaused()) {
            mLastStepTimestampMs = nowMs
            return
        }

        val now = nowMs
        val dtRaw = if (mLastStepTimestampMs > 0L) (now - mLastStepTimestampMs) / 1000.0 else 1.0
        mLastStepTimestampMs = now
        val dtSec = dtRaw.coerceIn(0.0, KinematicAltitudeSlopeEkf.MAX_DT_SEC)

        // Background refresh of preferences and pairings from SQLite (1-Hz background thread, NOT UI thread)
        refreshCachedPreferences()

        if (!mBaroBucket.isBucketEmpty &&
            mLastBaroEmitTimestampMs >= 0L &&
            nowMs - mLastBaroEmitTimestampMs >= BARO_AGGREGATION_WINDOW_MS
        ) {
            val flushTimestampMs = mLastBaroBucketTimestampMs
            if (nowMs - mLastBaroBucketTimestampMs > BarometricBucketDispersion.MULTI_SECOND_WINDOW_MS) {
                mBaroBucket.reset()
                mLastBaroEmitTimestampMs = nowMs
            } else {
                val flushed = mBaroBucket.flushBucket(flushTimestampMs, getCurrentSpeed())
                mLastBaroEmitTimestampMs = nowMs
                if (flushed != null) {
                    ingestFilteredBaroAltitude(flushed.representativeAltitudeM, flushTimestampMs, flushed.dispersionVarM2)
                }
            }
        }

        val mode = syncAdaptiveCarriageParameters()
        val speed = if (mode.profile.forceStationaryPinning) 0.0 else getCurrentSpeed()
        checkReferenceAltitudeFromSensors(nowMs)

        ekf.predict(dtSec, speed)
        publishAltitudeAndMetrics(speed, nowMs)
    }

    private fun checkReferenceAltitudeFromSensors(nowMs: Long) {
        var demConsumed = false
        if (isEffectiveDem) {
            val demSensor = mMySensorManager.getSensorForDeviceType(
                SensorType.ALTITUDE,
                DeviceType.ALTITUDE_FROM_POSITION
            )
            if (demSensor != null && demSensor.lastUpdateTimestamp > mLastDemUpdateTimestampMs) {
                val demVal = (demSensor.value as? Number)?.toDouble()
                if (demVal != null && !demVal.isNaN()) {
                    mLastDemUpdateTimestampMs = demSensor.lastUpdateTimestamp
                    if (ekf.updateReferenceAltitude(demVal, KinematicAltitudeSlopeEkf.DEFAULT_VAR_DEM)) {
                        resetAscentDescentAnchor()
                    }
                    demConsumed = true
                }
            } else if (demSensor != null && (nowMs - demSensor.lastUpdateTimestamp) <= DEM_STALENESS_THRESHOLD_MS) {
                demConsumed = true
            }
        }

        // REQ-FIL-020.1: DEM reference precedence over unnormalized WGS84 GPS altitude
        if (isEffectiveGps && !demConsumed) {
            val gpsSensor = mMySensorManager.getSensorForDeviceType(
                SensorType.ALTITUDE,
                DeviceType.SPEED_AND_LOCATION_GPS
            ) ?: mMySensorManager.getSensorForDeviceType(
                SensorType.ALTITUDE,
                DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED
            ) ?: mMySensorManager.getSensorForDeviceType(
                SensorType.ALTITUDE,
                DeviceType.SPEED_AND_LOCATION_ATT_FUSED
            )
            if (gpsSensor != null && gpsSensor.lastUpdateTimestamp > mLastGpsUpdateTimestampMs) {
                val gpsVal = (gpsSensor.value as? Number)?.toDouble()
                if (gpsVal != null && !gpsVal.isNaN()) {
                    mLastGpsUpdateTimestampMs = gpsSensor.lastUpdateTimestamp
                    if (ekf.updateReferenceAltitude(gpsVal, KinematicAltitudeSlopeEkf.DEFAULT_VAR_GPS)) {
                        resetAscentDescentAnchor()
                    }
                }
            }
        }
    }

    @Synchronized
    fun getCurrentSpeed(): Double {
        if (!isEffectiveSpeed) return 0.0
        val speedSensor = mMySensorManager.getSensor(SensorType.SPEED_mps)
        val speedVal = (speedSensor?.value as? Number)?.toDouble() ?: 0.0
        return max(0.0, speedVal)
    }

    /**
     * Silently re-anchors the live EKF and ascent/descent hysteresis anchor to [lastAltitudeM]
     * upon mid-workout crash/service resume without introducing an artificial altitude step or
     * phantom ascent/descent increment (`REQ-STB-025.3`).
     */
    @Synchronized
    fun reanchorOnResume(lastAltitudeM: Double) {
        if (!lastAltitudeM.isFinite() || lastAltitudeM <= -500.0 || lastAltitudeM >= 9000.0) return
        mBaroBucket.reset()
        mLastBaroEmitTimestampMs = -1L
        mLastBaroBucketTimestampMs = -1L
        ekf.setMode(isEffectiveDem || isEffectiveGps)
        ekf.reanchorOnResume(lastAltitudeM)
        mAscentDescentAnchorAltitude = lastAltitudeM
        mLastAltitude = lastAltitudeM
        mAltitudeSensor?.newValue(lastAltitudeM)
    }

    @Synchronized
    private fun resetAscentDescentAnchor() {
        mAscentDescentAnchorAltitude = Double.NaN
        mLastAltitude = Double.NaN
    }

    @Synchronized
    private fun publishAltitudeAndMetrics(speedMps: Double, nowMs: Long = System.currentTimeMillis()) {
        if (!ekf.isInitialized) return

        // REQ-FIL-040.3: Defer unanchored 3-State publication during initial STARTUP_REFERENCE_GRACE_MS
        // so raw QNH pressure offsets never emit a 35+ m startup cliff before DEM/GPS lock arrives.
        if (ekf.is3StateMode &&
            !ekf.hasReferenceAnchor &&
            mFirstBaroTimestampMs >= 0L &&
            (nowMs - mFirstBaroTimestampMs) in 0L until STARTUP_REFERENCE_GRACE_MS
        ) {
            return
        }

        val currentAlt = ekf.altitude
        mAltitudeSensor?.newValue(currentAlt)

        // REQ-FIL-020.2: 2.0 m anchor-based elevation hysteresis deadband for cumulative ascent/descent
        if (mAscentDescentAnchorAltitude.isNaN()) {
            mAscentDescentAnchorAltitude = currentAlt
        } else {
            val deltaFromAnchor = currentAlt - mAscentDescentAnchorAltitude
            if (deltaFromAnchor >= ASCENT_DESCENT_HYSTERESIS_METERS) {
                mAscentSensor?.increment(deltaFromAnchor)
                mAscentDescentAnchorAltitude = currentAlt
            } else if (deltaFromAnchor <= -ASCENT_DESCENT_HYSTERESIS_METERS) {
                mDescentSensor?.increment(-deltaFromAnchor)
                mAscentDescentAnchorAltitude = currentAlt
            }
        }
        mLastAltitude = currentAlt

        val minSpeed: Double = try {
            SettingsDataStoreJavaHelper.getSlopeMinSpeed(mContext).toDouble()
        } catch (e: Exception) {
            Log.d(TAG, "Using default min speed: ${e.message}")
            DEFAULT_MIN_SPEED_MPS
        }

        if (speedMps >= minSpeed) {
            mStandstillStartTimestampMs = 0L
            mSlopeSensor?.newValue(Math.round(ekf.slopePercent).toInt())
        } else {
            if (mStandstillStartTimestampMs == 0L) {
                mStandstillStartTimestampMs = nowMs
            }
            val standstillMs = max(0L, nowMs - mStandstillStartTimestampMs)
            // REQ-FIL-023.2: Publish decaying/cleared 0% slope on sustained standstill instead of freezing pre-stop grade
            if (standstillMs >= STANDSTILL_SLOPE_CLEAR_DELAY_MS) {
                mSlopeSensor?.newValue(0)
            } else if (ekf.standstillDurationSec >= KinematicAltitudeSlopeEkf.STANDSTILL_SLOPE_GRACE_SEC) {
                mSlopeSensor?.newValue(Math.round(ekf.slopePercent).toInt())
            }
        }

        val vam = ekf.getVamMph(speedMps)
        val clampedVam = Math.max(-3000.0, Math.min(3000.0, vam))
        mVerticalSpeedSensor?.newValue(Math.round(clampedVam).toInt())
    }
}
