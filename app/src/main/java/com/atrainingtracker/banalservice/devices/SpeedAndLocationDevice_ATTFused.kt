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

package com.atrainingtracker.banalservice.devices

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.odometry.QuaternionGyroProjector
import com.atrainingtracker.banalservice.odometry.SpeedMeasurement
import com.atrainingtracker.banalservice.odometry.UnicycleOdometryEkf
import com.atrainingtracker.banalservice.odometry.WheelCircumferenceCalibrator
import com.atrainingtracker.banalservice.sensor.MySensor
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.SensorType
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Android Device Driver for ATT Odometry Fusion Virtual Sensor (REQ-SEN-028, ATT-7751).
 *
 * Coordinates:
 * - [UnicycleOdometryEkf]: 5-state unicycle EKF with sub-second dead reckoning.
 * - [QuaternionGyroProjector]: Closed-form ENU yaw-rate projection from IMU angular rates.
 * - [WheelCircumferenceCalibrator]: Gated online wheel circumference calibration.
 *
 * Emits fused coordinates, speed, bearing, distance, and 1-Hz [SensorType.WORLD_YAW_RATE_radps].
 */
@Suppress("ClassNaming", "MagicNumber", "TooGenericExceptionCaught", "EmptyFunctionBlock", "ReturnCount")
open class SpeedAndLocationDevice_ATTFused(
    context: Context,
    mySensorManager: MySensorManager
) : SpeedAndLocationDevice(context, mySensorManager, DeviceType.SPEED_AND_LOCATION_ATT_FUSED),
    LocationListener,
    SensorEventListener,
    MySensorManager.WheelMeasurementListener {

    companion object {
        private const val TAG = "SpeedAndLocationDev_ATT"
        private val DEBUG = BANALService.getDebug(false)
        private const val EARTH_RADIUS_METERS = 6371000.0
        private const val YAW_RATE_WINDOW_SEC = 1.0
    }

    private data class YawRateSample(val yawRateRadPerSec: Double, val dtSec: Double)

    val ekf = UnicycleOdometryEkf()
    val gyroProjector = QuaternionGyroProjector()
    val wheelCalibrator = WheelCircumferenceCalibrator()

    var mWorldYawRateSensor: MySensor<Double>? = null
        protected set

    protected var mLocationManager: LocationManager? = null
    protected var mHardwareSensorManager: SensorManager? = null
    protected var mUpdatesRegistered = false
    protected var mSensorsRegistered = false
    protected var mWheelListenerRegistered = false

    private val mRotationVector = FloatArray(4)
    private var mHasRotationVector = false
    private var mLastGyroTimestampNs: Long = 0L
    private val mYawRateLock = Any()
    private val mYawRateWindow = java.util.ArrayDeque<YawRateSample>()
    private var mYawRateWindowDurationSec: Double = 0.0
    private var mYawRateWeightedSum: Double = 0.0

    // Tangent plane reference coordinates
    var refLat: Double? = null
    var refLon: Double? = null

    @Volatile
    var hasValidGnssAnchor: Boolean = false
        private set

    // Wheel odometry and auto-calibration state
    var currentTotalRevolutions: Double? = null
        private set
    @Volatile
    private var mLastWheelMeasurementMs: Long = 0L
    @Volatile
    private var mLastWheelSpeedMps: Double = 0.0
    private var mLastCalibLat: Double? = null
    private var mLastCalibLon: Double? = null
    private var mLastCalibAlt: Double? = null
    private var mLastCalibRevolutions: Double? = null

    init {
        val devicesDatabaseManager = DevicesDatabaseManager.getInstance(mContext)
        mDeviceId = devicesDatabaseManager.speedAndLocationAttFusedDeviceId
        mLocationManager = mContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        mHardwareSensorManager = mContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        registerUpdates()
    }

    override fun addSensors() {
        super.addSensors()
        val yawRate = MySensor<Double>(this, SensorType.WORLD_YAW_RATE_radps)
        mWorldYawRateSensor = yawRate
        addSensor(yawRate)
    }

    override fun getName(): String = "att_fused"

    @Synchronized
    fun registerUpdates() {
        registerLocationUpdates()
        registerImuSensors()
        registerWheelMeasurementListener()
    }

    @Synchronized
    fun registerWheelMeasurementListener() {
        if (mWheelListenerRegistered) return
        mMySensorManager?.addWheelMeasurementListener(this)
        mWheelListenerRegistered = true
        setLastActive()
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    fun registerLocationUpdates() {
        if (mUpdatesRegistered) return
        val lm = mLocationManager ?: return
        if (!AttFusedPreferences.isEffectiveGnssActive(mContext)) return
        try {
            if (lm.getProvider(LocationManager.GPS_PROVIDER) != null) {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    SAMPLING_TIME.toLong(),
                    MIN_DISTANCE.toFloat(),
                    this
                )
                mUpdatesRegistered = true
                setLastActive()
                if (DEBUG) Log.i(TAG, "GPS location updates registered")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register GPS location updates: ${e.message}")
        }
    }

    @Synchronized
    fun registerImuSensors() {
        if (mSensorsRegistered) return
        val sm = mHardwareSensorManager ?: return
        if (!AttFusedPreferences.isEffectiveGyroActive(mContext)) return
        try {
            val gyro = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
            if (gyro != null) {
                sm.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME)
            }
            val rotVector = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
                ?: sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            if (rotVector != null) {
                sm.registerListener(this, rotVector, SensorManager.SENSOR_DELAY_GAME)
            }
            mSensorsRegistered = true
            setLastActive()
            if (DEBUG) Log.i(TAG, "IMU sensor listeners registered")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register IMU sensors: ${e.message}")
        }
    }

    @Synchronized
    override fun checkOrReRegisterLocationUpdates() {
        if (!mUpdatesRegistered) {
            registerLocationUpdates()
        }
        if (!mSensorsRegistered) {
            registerImuSensors()
        }
        if (!mWheelListenerRegistered) {
            registerWheelMeasurementListener()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR,
            Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                if (event.values.size >= 4) {
                    System.arraycopy(event.values, 0, mRotationVector, 0, 4)
                    mHasRotationVector = true
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                if (!AttFusedPreferences.isEffectiveGyroActive(mContext)) return
                val nowNs = event.timestamp
                val dtSec = if (mLastGyroTimestampNs > 0L) {
                    (nowNs - mLastGyroTimestampNs) / 1_000_000_000.0
                } else {
                    0.02
                }
                mLastGyroTimestampNs = nowNs
                if (dtSec in 0.0001..0.5) {
                    val worldYawRate = if (mHasRotationVector) {
                        gyroProjector.projectYawRate(mRotationVector, event.values)
                    } else {
                        event.values[2].toDouble()
                    }
                    val timestampMs = System.currentTimeMillis()
                    synchronized(ekf) {
                        ekf.predict(dtSec, worldYawRate, timestampMs)
                    }
                    recordWorldYawRate(worldYawRate, dtSec)
                    setLastActive()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op for interface compliance
    }

    override fun onLocationChanged(location: Location) {
        if (!AttFusedPreferences.isEffectiveGnssActive(mContext)) return

        val lat = location.latitude
        val lon = location.longitude
        val acc = if (location.hasAccuracy() && !location.accuracy.isNaN()) location.accuracy.toDouble() else 10.0
        val timestampMs = if (location.time > 0L) location.time else System.currentTimeMillis()

        val hasGpsSpeed = location.hasSpeed() && !location.speed.isNaN() && location.speed >= 0.0f
        val gpsSpeed = if (hasGpsSpeed) location.speed.toDouble() else Double.NaN
        val lastWheelMs = mLastWheelMeasurementMs
        val lastWheelSpeed = mLastWheelSpeedMps
        val isWheelMoving = lastWheelMs > 0L &&
            abs(timestampMs - lastWheelMs) <= 5000L &&
            lastWheelSpeed > SPEED_ZERO_THRESHOLD
        val isGpsZero = hasGpsSpeed && gpsSpeed <= SPEED_ZERO_THRESHOLD
        val currentEkfSpeed = synchronized(ekf) { ekf.speedMps }
        val isStationary = isGpsZero && !isWheelMoving && (
            isExternalSpeedSensorStationary(timestampMs) ||
                isAccelerometerAtRest(timestampMs) ||
                currentEkfSpeed <= SPEED_ZERO_THRESHOLD
            )

        // REQ-FIL-023.1: Defer tangent-plane origin anchoring until a valid GNSS accuracy fix arrives
        val rLat: Double
        val rLon: Double
        synchronized(ekf) {
            if (!hasValidGnssAnchor || refLat == null || refLon == null) {
                if (acc <= 0.0 || acc > UnicycleOdometryEkf.MAX_GNSS_ACCURACY_METERS) {
                    if (isStationary) {
                        ekf.applyZeroVelocityUpdate(timestampMs)
                    }
                    return
                }
                refLat = lat
                refLon = lon
                hasValidGnssAnchor = true
                // Atomically re-anchor pre-fix dead-reckoning coordinates to (0, 0) at origin lock
                ekf.reanchorPosition(0.0, 0.0)
            }
            rLat = refLat!!
            rLon = refLon!!
        }

        val dLatRad = Math.toRadians(lat - rLat)
        val dLonRad = Math.toRadians(lon - rLon)
        val northMeters = dLatRad * EARTH_RADIUS_METERS
        val eastMeters = dLonRad * EARTH_RADIUS_METERS * cos(Math.toRadians(rLat))

        // REQ-FIL-023.1: Apply speed / ZUPT before GNSS position update to block velocity-leak spikes
        val gnssAccepted: Boolean
        val clusterSnapped: Boolean
        val fusedEast: Double
        val fusedNorth: Double
        val fusedSpeed: Double
        val fusedBearing: Double
        val fusedPosVar: Double
        synchronized(ekf) {
            if (isStationary) {
                ekf.applyZeroVelocityUpdate(timestampMs)
            } else if (hasGpsSpeed) {
                val speedVar = if (location.hasAccuracy() && !location.accuracy.isNaN()) {
                    max(0.5, (location.accuracy.toDouble() * 0.25).pow(2))
                } else {
                    1.0
                }
                ekf.updateSpeed(gpsSpeed, speedVar, timestampMs)
            }

            gnssAccepted = ekf.updateGnssPosition(
                eastMeters = eastMeters,
                northMeters = northMeters,
                accuracyMeters = acc,
                timestampMs = timestampMs,
                isStationary = isStationary
            )
            clusterSnapped = ekf.consumeStationaryClusterSnapped()
            fusedEast = ekf.eastMeters
            fusedNorth = ekf.northMeters
            fusedSpeed = ekf.speedMps
            fusedBearing = ekf.bearingDeg
            fusedPosVar = ekf.positionVariance
        }

        if (!gnssAccepted && (isStationary || fusedSpeed <= SPEED_ZERO_THRESHOLD)) {
            return
        }

        if (location.hasSpeed() && location.hasBearing() &&
            location.speed > UnicycleOdometryEkf.MIN_DOPPLER_SPEED_MPS
        ) {
            val cogDeg = location.bearing.toDouble()
            val enuHeadingRad = Math.toRadians((450.0 - cogDeg) % 360.0)
            synchronized(ekf) {
                ekf.updateDopplerHeading(enuHeadingRad, Math.toRadians(5.0) * Math.toRadians(5.0), timestampMs)
            }
        }

        // Finding 2: Feed distance & revolutions into auto-calibration step
        if (AttFusedPreferences.isEffectiveAutoCalibActive(mContext)) {
            val pLat = mLastCalibLat
            val pLon = mLastCalibLon
            val pAlt = mLastCalibAlt
            val pRevs = mLastCalibRevolutions
            val currentRevs = currentTotalRevolutions

            if (pLat != null && pLon != null && pRevs != null && currentRevs != null) {
                val deltaRevs = currentRevs - pRevs
                if (deltaRevs > 0.0) {
                    val stepDLatRad = Math.toRadians(lat - pLat)
                    val stepDLonRad = Math.toRadians(lon - pLon)
                    val stepNorth = stepDLatRad * EARTH_RADIUS_METERS
                    val stepEast = stepDLonRad * EARTH_RADIUS_METERS * cos(Math.toRadians(pLat))
                    val dist2d = sqrt(stepNorth * stepNorth + stepEast * stepEast)
                    val dist3d = if (pAlt != null && location.hasAltitude() && !location.altitude.isNaN()) {
                        val dh = location.altitude - pAlt
                        sqrt(dist2d * dist2d + dh * dh)
                    } else {
                        dist2d
                    }
                    val yawRate = mWorldYawRateSensor?.value ?: 0.0
                    val speed = if (location.hasSpeed() && !location.speed.isNaN()) location.speed.toDouble() else fusedSpeed
                    processWheelCalibrationStep(
                        deltaDistance3d = dist3d,
                        deltaRevolutions = deltaRevs,
                        gnssAccuracyMeters = acc,
                        speedMps = speed,
                        yawRateRadPerSec = yawRate
                    )
                }
            }
            mLastCalibLat = lat
            mLastCalibLon = lon
            mLastCalibAlt = if (location.hasAltitude() && !location.altitude.isNaN()) location.altitude else null
            mLastCalibRevolutions = currentRevs
        }

        val fusedLat = rLat + Math.toDegrees(fusedNorth / EARTH_RADIUS_METERS)
        val fusedLon = rLon + Math.toDegrees(fusedEast / (EARTH_RADIUS_METERS * cos(Math.toRadians(rLat))))

        location.latitude = fusedLat
        location.longitude = fusedLon
        location.speed = fusedSpeed.toFloat()
        location.bearing = fusedBearing.toFloat()
        // REQ-FIL-023.1: Preserve GNSS uncertainty floor so SpeedAndLocationDevice escapeRadius does not shrink falsely
        location.accuracy = max(acc, sqrt(fusedPosVar)).toFloat()

        if (clusterSnapped) {
            // Synchronize base stationary anchor unconditionally (even if not yet pinned) on genuine cluster snap
            mStationaryAnchor = location
            mIsStationaryPinned = true
            mStationaryHysteresisCount = 0
            mFilteredLatitude = fusedLat
            mFilteredLongitude = fusedLon
        }

        onNewLocation(location)
    }

    override fun onWheelMeasurement(
        speedMps: Double,
        variance: Double,
        timestampMs: Long
    ) {
        if (!AttFusedPreferences.isEffectiveWheelActive(mContext)) return
        mLastWheelMeasurementMs = timestampMs
        mLastWheelSpeedMps = speedMps
        synchronized(ekf) {
            ekf.updateSpeed(speedMps, variance, timestampMs)
        }
        setLastActive()
    }

    fun onWheelMeasurement(
        speedMps: Double,
        variance: Double = 0.05
    ) {
        onWheelMeasurement(speedMps, variance, System.currentTimeMillis())
    }

    override fun onWheelDistance(accumulatedDistanceMeters: Double, timestampMs: Long) {
        val initialCircumference = wheelCalibrator.circumferenceMeters
        if (initialCircumference > 0.0) {
            val totalRevs = accumulatedDistanceMeters / initialCircumference
            onWheelRevolutions(0.0, totalRevs, timestampMs)
        }
    }

    override fun onWheelRevolutions(deltaRevolutions: Double, totalRevolutions: Double, timestampMs: Long) {
        currentTotalRevolutions = totalRevolutions
        if (deltaRevolutions > 0.0 && mLastCalibRevolutions == null) {
            mLastCalibRevolutions = totalRevolutions - deltaRevolutions
        }
    }

    fun processWheelCalibrationStep(
        deltaDistance3d: Double,
        deltaRevolutions: Double,
        gnssAccuracyMeters: Double,
        speedMps: Double,
        yawRateRadPerSec: Double
    ): Boolean {
        if (!AttFusedPreferences.isEffectiveAutoCalibActive(mContext)) return false
        return wheelCalibrator.processStep(
            deltaDistance3d,
            deltaRevolutions,
            gnssAccuracyMeters,
            speedMps,
            yawRateRadPerSec
        )
    }

    /**
     * Publishes a rolling 1.0-second time-weighted mean ENU yaw rate to [mWorldYawRateSensor]
     * (REQ-FIL-027.2), preserving immediate single-sample emission while canceling cyclic pedaling oscillations.
     */
    private fun recordWorldYawRate(worldYawRate: Double, dtSec: Double) {
        if (!worldYawRate.isFinite() || !dtSec.isFinite() || dtSec <= 0.0) return
        val averagedYawRate = synchronized(mYawRateLock) {
            mYawRateWindow.addLast(YawRateSample(worldYawRate, dtSec))
            mYawRateWindowDurationSec += dtSec
            mYawRateWeightedSum += worldYawRate * dtSec
            while (mYawRateWindow.size > 1 && mYawRateWindowDurationSec > YAW_RATE_WINDOW_SEC) {
                val oldest = mYawRateWindow.removeFirst()
                mYawRateWindowDurationSec -= oldest.dtSec
                mYawRateWeightedSum -= oldest.yawRateRadPerSec * oldest.dtSec
            }
            if (mYawRateWindowDurationSec > 0.0) mYawRateWeightedSum / mYawRateWindowDurationSec else worldYawRate
        }
        mWorldYawRateSensor?.newValue(averagedYawRate)
    }

    private fun clearYawRateWindow() {
        synchronized(mYawRateLock) {
            mYawRateWindow.clear()
            mYawRateWindowDurationSec = 0.0
            mYawRateWeightedSum = 0.0
        }
    }

    @VisibleForTesting
    internal fun yawRateWindowSizeForTest(): Int = synchronized(mYawRateLock) { mYawRateWindow.size }

    @VisibleForTesting
    fun ingestImuMeasurement(
        gameRotationVector: FloatArray,
        gyroValues: FloatArray,
        dtSec: Double,
        timestampMs: Long = System.currentTimeMillis()
    ) {
        val worldYawRate = gyroProjector.projectYawRate(gameRotationVector, gyroValues)
        synchronized(ekf) {
            ekf.predict(dtSec, worldYawRate, timestampMs)
        }
        recordWorldYawRate(worldYawRate, dtSec)
        setLastActive()
    }

    @Synchronized
    override fun onAccumulatorsReset() {
        super.onAccumulatorsReset()
        resetOriginAndEkf()
    }

    @Synchronized
    private fun resetOriginAndEkf() {
        refLat = null
        refLon = null
        hasValidGnssAnchor = false
        mHasRotationVector = false
        mLastGyroTimestampNs = 0L
        clearYawRateWindow()
        mLastWheelMeasurementMs = 0L
        mLastWheelSpeedMps = 0.0
        mLastCalibLat = null
        mLastCalibLon = null
        mLastCalibAlt = null
        mLastCalibRevolutions = null
        synchronized(ekf) {
            ekf.reset()
        }
    }

    @Synchronized
    override fun shutDown() {
        if (mWheelListenerRegistered) {
            mMySensorManager?.removeWheelMeasurementListener(this)
            mWheelListenerRegistered = false
        }

        if (mLocationManager != null) {
            try {
                mLocationManager?.removeUpdates(this)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove location updates: ${e.message}")
            }
        }
        mUpdatesRegistered = false

        if (mHardwareSensorManager != null) {
            try {
                mHardwareSensorManager?.unregisterListener(this)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister IMU listener: ${e.message}")
            }
        }
        mSensorsRegistered = false
        resetOriginAndEkf()

        super.shutDown()
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        // No-op for API compatibility
    }

    override fun onProviderEnabled(provider: String) {
        if (LocationManager.GPS_PROVIDER == provider && !mUpdatesRegistered) {
            registerLocationUpdates()
        }
    }

    override fun onProviderDisabled(provider: String) {
        // No-op for API compatibility
    }
}
