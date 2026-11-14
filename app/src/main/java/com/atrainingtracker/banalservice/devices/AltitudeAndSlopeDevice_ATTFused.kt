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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.odometry.KinematicAltitudeSlopeEkf
import com.atrainingtracker.banalservice.sensor.MyDoubleAccumulatorSensor
import com.atrainingtracker.banalservice.sensor.MySensor
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
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
    private var mLastStepTimestampMs: Long = 0L

    init {
        val db = DevicesDatabaseManager.getInstance(mContext)
        mDeviceId = db.altitudeAndSlopeAttFusedDeviceId
        mHardwareSensorManager = mContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
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

    override fun getName(): String =
        mContext.getString(R.string.devices_altitude_slope_ATT_fused)

    override fun shutDown() {
        super.shutDown()
        mScheduler?.let {
            if (!it.isShutdown) it.shutdown()
        }
        mHardwareSensorManager?.unregisterListener(this)
        mPressureSensorRegistered = false
        baroFilter.reset()
        ekf.reset()
        mLastAltitude = Double.NaN
        mLastStepTimestampMs = 0L
    }

    @Synchronized
    fun registerPressureSensor() {
        if (mPressureSensorRegistered) return
        val sm = mHardwareSensorManager ?: return
        if (!AttFusedAltitudePreferences.isEffectiveBarometerActive(mContext)) return
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

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_PRESSURE) return
        if (!AttFusedAltitudePreferences.isEffectiveBarometerActive(mContext)) return
        handlePressureMeasurement(event.values[0], System.currentTimeMillis())
    }

    @VisibleForTesting
    fun handlePressureMeasurement(pressureHpa: Float, timestampMs: Long = System.currentTimeMillis()) {
        val rawAlt = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressureHpa).toDouble()
        val slewAlt = baroFilter.filterSlewOnly(rawAlt, timestampMs)
        val speed = getCurrentSpeed()
        val isStandstill = speed < ekf.standstillSpeedThresholdMps

        val useDem = AttFusedAltitudePreferences.isEffectiveDemActive(mContext)
        val useGps = AttFusedAltitudePreferences.isEffectiveGpsActive(mContext)
        ekf.setMode(useDem || useGps)

        ekf.updateBarometer(slewAlt, isStandstill = isStandstill)
        publishAltitudeAndMetrics(speed)
    }

    @VisibleForTesting
    fun handleReferenceAltitude(refAltM: Double, isDem: Boolean = true) {
        val useDem = AttFusedAltitudePreferences.isEffectiveDemActive(mContext)
        val useGps = AttFusedAltitudePreferences.isEffectiveGpsActive(mContext)
        ekf.setMode(useDem || useGps)
        ekf.updateReferenceAltitude(
            refAltM,
            if (isDem) KinematicAltitudeSlopeEkf.DEFAULT_VAR_DEM else KinematicAltitudeSlopeEkf.DEFAULT_VAR_GPS
        )
        publishAltitudeAndMetrics(getCurrentSpeed())
    }

    @VisibleForTesting
    fun calculateMetrics(nowMs: Long = System.currentTimeMillis()) {
        if (TrainingApplication.isPaused() || TrainingApplication.isAutoPaused()) return

        val now = nowMs
        val dtSec = if (mLastStepTimestampMs > 0L) (now - mLastStepTimestampMs) / 1000.0 else 1.0
        mLastStepTimestampMs = now

        val speed = getCurrentSpeed()
        checkReferenceAltitudeFromSensors()

        ekf.predict(dtSec, speed)
        publishAltitudeAndMetrics(speed)
    }

    private fun checkReferenceAltitudeFromSensors() {
        if (AttFusedAltitudePreferences.isEffectiveDemActive(mContext)) {
            val demSensor = mMySensorManager.getSensorForDeviceType(
                SensorType.ALTITUDE,
                DeviceType.ALTITUDE_FROM_POSITION
            )
            val demVal = (demSensor?.value as? Number)?.toDouble()
            if (demVal != null && !demVal.isNaN()) {
                ekf.updateReferenceAltitude(demVal, KinematicAltitudeSlopeEkf.DEFAULT_VAR_DEM)
            }
        }
    }

    fun getCurrentSpeed(): Double {
        if (!AttFusedAltitudePreferences.isEffectiveSpeedActive(mContext)) return 0.0
        val speedSensor = mMySensorManager.getSensor(SensorType.SPEED_mps)
        val speedVal = (speedSensor?.value as? Number)?.toDouble() ?: 0.0
        return max(0.0, speedVal)
    }

    private fun publishAltitudeAndMetrics(speedMps: Double) {
        if (!ekf.isInitialized) return

        val currentAlt = ekf.altitude
        mAltitudeSensor?.newValue(currentAlt)

        if (!mLastAltitude.isNaN()) {
            val deltaAlt = currentAlt - mLastAltitude
            if (deltaAlt > 0.0) {
                mAscentSensor?.increment(deltaAlt)
            } else if (deltaAlt < 0.0) {
                mDescentSensor?.increment(-deltaAlt)
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
            mSlopeSensor?.newValue(Math.round(ekf.slopePercent).toInt())
        }

        val vam = ekf.getVamMph(speedMps)
        val clampedVam = Math.max(-3000.0, Math.min(3000.0, vam))
        mVerticalSpeedSensor?.newValue(Math.round(clampedVam).toInt())
    }
}
