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

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.odometry.PhoneCarriageClassifier
import com.atrainingtracker.banalservice.odometry.PhoneCarriageMode
import com.atrainingtracker.banalservice.odometry.QuaternionGyroProjector
import com.atrainingtracker.banalservice.odometry.SpeedMeasurement
import com.atrainingtracker.banalservice.odometry.SportContextHint
import com.atrainingtracker.banalservice.odometry.UnicycleOdometryEkf
import com.atrainingtracker.banalservice.odometry.WheelCircumferenceCalibrator
import com.atrainingtracker.banalservice.sensor.MySensor
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.SensorType
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
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
        private const val CARRIAGE_CLASSIFY_INTERVAL_MS = 250L
        private const val MODE_SENSOR_EMISSION_INTERVAL_MS = 1_000L
        private const val MODE_ADAPTATION_TAU_SEC = 0.75
        private const val MAX_CHORD_AGE_SEC = 12.0
        private const val MAX_DISPLACEMENT_LOOKBACK_SEC = 15.0
        private const val MIN_DISPLACEMENT_ESCAPE_METERS = 3.5
        private const val DISPLACEMENT_ACCURACY_FACTOR = 0.85
        private const val CLUSTER_STOP_RADIUS_METERS = 1.5
        private const val CLUSTER_STOP_MIN_SPAN_SEC = 3.0
        private const val MAX_PLAUSIBLE_GNSS_SPEED_MPS = 70.0
    }

    private data class YawRateSample(val yawRateRadPerSec: Double, val dtSec: Double)
    private data class RawGnssFix(val lat: Double, val lon: Double, val accuracyMeters: Double, val timestampMs: Long)

    val ekf = UnicycleOdometryEkf()
    val gyroProjector = QuaternionGyroProjector()
    val wheelCalibrator = WheelCircumferenceCalibrator()
    val carriageClassifier = PhoneCarriageClassifier()

    var mWorldYawRateSensor: MySensor<Double>? = null
        protected set
    var mActivityAndCarriageModeSensor: MySensor<Int>? = null
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
    private var mEffectiveGyroNoiseScale: Double = 1.0
    private var mEffectiveYawWindowSec: Double = YAW_RATE_WINDOW_SEC
    private var mLastStableYawRateRadPerSec: Double = 0.0
    private var mCachedCarriageMode: PhoneCarriageMode = PhoneCarriageMode.UNKNOWN
    private var mLastEmittedCarriageMode: PhoneCarriageMode = PhoneCarriageMode.UNKNOWN
    private var mCachedTargetYawWindowSec: Double = YAW_RATE_WINDOW_SEC
    private var mLastClassifyTimestampMs: Long = Long.MIN_VALUE
    private var mLastModeEmissionTimestampMs: Long = Long.MIN_VALUE
    private var mLastAdaptationTimestampMs: Long = Long.MIN_VALUE
    private var mClassifyEvaluationCount: Int = 0
    private var mModeEmissionCount: Int = 0
    private var mLastChordEastM: Double? = null
    private var mLastChordNorthM: Double? = null
    private var mLastChordTimestampMs: Long = 0L
    private val mRecentRawGnssFixes = java.util.ArrayDeque<RawGnssFix>()
    private var mExplicitSportHintForTest: SportContextHint? = null

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

        val carriageMode = MySensor<Int>(this, SensorType.ACTIVITY_AND_CARRIAGE_MODE)
        carriageMode.newValue(PhoneCarriageMode.UNKNOWN.code)
        mActivityAndCarriageModeSensor = carriageMode
        addSensor(carriageMode)
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

    protected open fun hasActivityRecognitionPermission(): Boolean {
        val sdkInt = try {
            Build.VERSION.SDK_INT
        } catch (_: Throwable) {
            0
        }
        // Plain-JVM unit tests (SDK_INT == 0) safely skip Android runtime permission checks.
        if (sdkInt == 0) return false
        if (sdkInt < Build.VERSION_CODES.Q) return true
        return try {
            mContext.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
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
            val pressure = sm.getDefaultSensor(Sensor.TYPE_PRESSURE)
            if (pressure != null) {
                sm.registerListener(this, pressure, SensorManager.SENSOR_DELAY_NORMAL)
            }
            if (hasActivityRecognitionPermission()) {
                try {
                    val stepDetector = sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
                    if (stepDetector != null) {
                        sm.registerListener(this, stepDetector, SensorManager.SENSOR_DELAY_NORMAL)
                    }
                } catch (secEx: SecurityException) {
                    Log.w(TAG, "Step detector registration denied by platform policy: ${secEx.message}")
                }
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
        if (event == null || event.sensor == null) return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                super.onSensorChanged(event)
            }
            Sensor.TYPE_ROTATION_VECTOR,
            Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                if (event.values != null && event.values.size >= 4) {
                    System.arraycopy(event.values, 0, mRotationVector, 0, 4)
                    mHasRotationVector = true
                    carriageClassifier.ingestRotationVector(event.values, System.currentTimeMillis())
                }
            }
            Sensor.TYPE_PRESSURE -> {
                if (event.values != null && event.values.isNotEmpty()) {
                    val pressureHpa = event.values[0].toDouble()
                    if (pressureHpa.isFinite() && pressureHpa > 0.0) {
                        val altM = 44330.0 * (1.0 - (pressureHpa / 1013.25).pow(0.19029495718363465))
                        carriageClassifier.ingestBarometerAltitude(altM, System.currentTimeMillis())
                    }
                }
            }
            Sensor.TYPE_STEP_DETECTOR -> {
                carriageClassifier.ingestStepEvent(System.currentTimeMillis())
            }
            Sensor.TYPE_GYROSCOPE -> {
                if (!AttFusedPreferences.isEffectiveGyroActive(mContext)) return
                if (event.values == null || event.values.size < 3) return
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
                    val rawGyroMag = sqrt(
                        event.values[0].toDouble() * event.values[0].toDouble() +
                            event.values[1].toDouble() * event.values[1].toDouble() +
                            event.values[2].toDouble() * event.values[2].toDouble()
                    )
                    processWorldYawStep(worldYawRate, rawGyroMag, dtSec, System.currentTimeMillis())
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
        val gnssDisplacementSpeedMps = computeRawGnssDisplacementSpeedMps(lat, lon, acc, timestampMs)
        val validBearing = if (location.hasBearing() && !location.bearing.isNaN()) location.bearing.toDouble() else null
        val speedForClassifier = when {
            hasGpsSpeed -> gpsSpeed
            gnssDisplacementSpeedMps != null -> gnssDisplacementSpeedMps
            else -> synchronized(ekf) { ekf.speedMps }
        }
        carriageClassifier.ingestGnssSpeed(speedForClassifier, validBearing, acc, timestampMs)
        val activeCarriageMode = updateCarriageClassification(timestampMs, forceClassify = true)
        val activeProfile = activeCarriageMode.profile

        val lastWheelMs = mLastWheelMeasurementMs
        val lastWheelSpeed = mLastWheelSpeedMps
        val isWheelMoving = lastWheelMs > 0L &&
            abs(timestampMs - lastWheelMs) <= 5000L &&
            lastWheelSpeed > SPEED_ZERO_THRESHOLD
        val isGpsZero = hasGpsSpeed && gpsSpeed <= SPEED_ZERO_THRESHOLD
        val currentEkfSpeed = synchronized(ekf) { ekf.speedMps }
        val isMovingByGnss = (hasGpsSpeed && gpsSpeed > 0.25) ||
            (!hasGpsSpeed && (gnssDisplacementSpeedMps ?: 0.0) > 0.25)
        val isStationaryByClassifier = activeProfile.forceStationaryPinning &&
            !isWheelMoving &&
            !isMovingByGnss
        val isStationary = isStationaryByClassifier || (isGpsZero && !isWheelMoving && (
            isExternalSpeedSensorStationary(timestampMs) ||
                isAccelerometerAtRest(timestampMs) ||
                currentEkfSpeed <= SPEED_ZERO_THRESHOLD
            ))

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
            } else if (!isWheelMoving && gnssDisplacementSpeedMps != null) {
                val speedVar = max(1.0, (acc * 0.35).pow(2))
                ekf.updateSpeed(gnssDisplacementSpeedMps, speedVar, timestampMs)
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

        if (isStationary) {
            clearChordHeadingAnchor()
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
            if (!isStationary && gnssAccepted && acc <= 15.0) {
                updateChordHeadingAnchor(fusedEast, fusedNorth, timestampMs)
            } else {
                clearChordHeadingAnchor()
            }
        } else if (!isStationary &&
            gnssAccepted &&
            acc <= 15.0 &&
            (activeCarriageMode.isRunningMode() || activeCarriageMode == PhoneCarriageMode.WALKING_TRANSITION)
        ) {
            val prevChordE = mLastChordEastM
            val prevChordN = mLastChordNorthM
            if (prevChordE != null && prevChordN != null && mLastChordTimestampMs > 0L) {
                val dE = fusedEast - prevChordE
                val dN = fusedNorth - prevChordN
                val chordDist = hypot(dE, dN)
                val dtChordSec = (timestampMs - mLastChordTimestampMs) / 1000.0
                if (dtChordSec <= 0.0 || dtChordSec > MAX_CHORD_AGE_SEC) {
                    updateChordHeadingAnchor(fusedEast, fusedNorth, timestampMs)
                } else if (chordDist >= 5.0 && dtChordSec >= 1.0) {
                    if ((chordDist / dtChordSec) >= activeProfile.minChordHeadingSpeedMps) {
                        val chordHeadingRad = atan2(dN, dE)
                        synchronized(ekf) {
                            ekf.updateOfflineHeading(chordHeadingRad, activeProfile.chordHeadingVarianceRad2, timestampMs)
                        }
                    }
                    updateChordHeadingAnchor(fusedEast, fusedNorth, timestampMs)
                }
            } else {
                updateChordHeadingAnchor(fusedEast, fusedNorth, timestampMs)
            }
        } else if (!activeCarriageMode.isRunningMode() && activeCarriageMode != PhoneCarriageMode.WALKING_TRANSITION) {
            clearChordHeadingAnchor()
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

        if (clusterSnapped || (isStationaryByClassifier && gnssAccepted && !mIsStationaryPinned)) {
            // Synchronize base stationary anchor unconditionally on genuine cluster snap or initial classifier stop pin
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
        val wasMoving = mLastWheelSpeedMps > SPEED_ZERO_THRESHOLD
        mLastWheelMeasurementMs = timestampMs
        mLastWheelSpeedMps = speedMps
        carriageClassifier.ingestWheelSpeed(speedMps, timestampMs)
        val isNowMoving = speedMps > SPEED_ZERO_THRESHOLD
        updateCarriageClassification(timestampMs, forceClassify = (isNowMoving != wasMoving))
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

    private fun processWorldYawStep(
        worldYawRate: Double,
        rawGyroMagRadPerSec: Double,
        dtSec: Double,
        timestampMs: Long
    ) {
        carriageClassifier.ingestGyro(worldYawRate, rawGyroMagRadPerSec, timestampMs)
        val mode = updateCarriageClassification(timestampMs)
        val profile = mode.profile

        val isGlanceSpike = profile.enableGlanceGating &&
            carriageClassifier.isGlanceActive(timestampMs) &&
            abs(worldYawRate) > 0.35

        val yawForEkf: Double
        val gyroNoiseScale: Double
        synchronized(mYawRateLock) {
            val baseScale = mEffectiveGyroNoiseScale
            if (isGlanceSpike) {
                yawForEkf = mLastStableYawRateRadPerSec * 0.5
                gyroNoiseScale = max(baseScale, 40.0)
            } else {
                yawForEkf = worldYawRate
                gyroNoiseScale = baseScale
                mLastStableYawRateRadPerSec = worldYawRate
            }
        }

        synchronized(ekf) {
            ekf.predict(dtSec, yawForEkf, timestampMs, gyroNoiseScale)
        }
        if (!isGlanceSpike) {
            recordWorldYawRate(worldYawRate, dtSec)
        }
        setLastActive()
    }

    private fun updateCarriageClassification(
        timestampMs: Long,
        forceClassify: Boolean = false
    ): PhoneCarriageMode {
        val shouldClassify = synchronized(mYawRateLock) {
            forceClassify ||
                mLastClassifyTimestampMs == Long.MIN_VALUE ||
                timestampMs < mLastClassifyTimestampMs ||
                (timestampMs - mLastClassifyTimestampMs) >= CARRIAGE_CLASSIFY_INTERVAL_MS
        }

        val mode: PhoneCarriageMode
        val profile: com.atrainingtracker.banalservice.odometry.CarriageFilterProfile
        val targetYawWindowSec: Double
        if (shouldClassify) {
            val hint = mExplicitSportHintForTest ?: try {
                SportContextHint.fromBSportType(BANALService.getActiveBSportType())
            } catch (_: Throwable) {
                SportContextHint.UNKNOWN
            }
            carriageClassifier.setSportContext(hint)

            mode = carriageClassifier.classify(timestampMs)
            profile = mode.profile
            targetYawWindowSec = if (mode == PhoneCarriageMode.RUN_BICEP_ARM) {
                val fStep = carriageClassifier.currentStepFrequencyHz(timestampMs)
                if (fStep != null && fStep > 1.5) (2.0 / fStep).coerceIn(1.0, 2.0) else profile.yawWindowSec
            } else {
                profile.yawWindowSec
            }

            val shouldEmitSensor = synchronized(mYawRateLock) {
                mCachedCarriageMode = mode
                mCachedTargetYawWindowSec = targetYawWindowSec
                mLastClassifyTimestampMs = timestampMs
                mClassifyEvaluationCount++
                val emit = mode != mLastEmittedCarriageMode ||
                    mLastModeEmissionTimestampMs == Long.MIN_VALUE ||
                    timestampMs < mLastModeEmissionTimestampMs ||
                    (timestampMs - mLastModeEmissionTimestampMs) >= MODE_SENSOR_EMISSION_INTERVAL_MS
                if (emit) {
                    mLastEmittedCarriageMode = mode
                    mLastModeEmissionTimestampMs = timestampMs
                    mModeEmissionCount++
                }
                emit
            }
            if (shouldEmitSensor) {
                mActivityAndCarriageModeSensor?.newValue(mode.code)
            }
        } else {
            synchronized(mYawRateLock) {
                mode = mCachedCarriageMode
                profile = mode.profile
                targetYawWindowSec = mCachedTargetYawWindowSec
            }
        }

        synchronized(mYawRateLock) {
            val dtSec = if (mLastAdaptationTimestampMs == Long.MIN_VALUE || timestampMs <= mLastAdaptationTimestampMs) {
                mLastAdaptationTimestampMs = timestampMs
                0.0
            } else {
                val delta = (timestampMs - mLastAdaptationTimestampMs) / 1000.0
                mLastAdaptationTimestampMs = timestampMs
                delta.coerceIn(0.0, 5.0)
            }
            if (dtSec > 0.0) {
                val alpha = 1.0 - exp(-dtSec / MODE_ADAPTATION_TAU_SEC)
                mEffectiveGyroNoiseScale += alpha * (profile.gyroNoiseScale - mEffectiveGyroNoiseScale)
                if (abs(profile.gyroNoiseScale - mEffectiveGyroNoiseScale) < 1e-4) {
                    mEffectiveGyroNoiseScale = profile.gyroNoiseScale
                }
                mEffectiveYawWindowSec += alpha * (targetYawWindowSec - mEffectiveYawWindowSec)
                if (abs(targetYawWindowSec - mEffectiveYawWindowSec) < 1e-4) {
                    mEffectiveYawWindowSec = targetYawWindowSec
                }
            }
        }
        return mode
    }

    /**
     * Publishes a rolling time-weighted mean ENU yaw rate to [mWorldYawRateSensor]
     * (REQ-FIL-027.2, REQ-FIL-028.3), smoothly adapting window length per [PhoneCarriageMode]
     * while preserving immediate single-sample emission.
     */
    private fun recordWorldYawRate(worldYawRate: Double, dtSec: Double) {
        if (!worldYawRate.isFinite() || !dtSec.isFinite() || dtSec <= 0.0) return
        val averagedYawRate = synchronized(mYawRateLock) {
            val targetWindowSec = mEffectiveYawWindowSec.coerceAtLeast(0.1)
            mYawRateWindow.addLast(YawRateSample(worldYawRate, dtSec))
            mYawRateWindowDurationSec += dtSec
            mYawRateWeightedSum += worldYawRate * dtSec
            while (mYawRateWindow.size > 1 && mYawRateWindowDurationSec > targetWindowSec) {
                val oldest = mYawRateWindow.removeFirst()
                mYawRateWindowDurationSec = max(0.0, mYawRateWindowDurationSec - oldest.dtSec)
                mYawRateWeightedSum -= oldest.yawRateRadPerSec * oldest.dtSec
            }
            if (mYawRateWindowDurationSec > 0.0) mYawRateWeightedSum / mYawRateWindowDurationSec else worldYawRate
        }
        mWorldYawRateSensor?.newValue(averagedYawRate)
    }

    private fun updateChordHeadingAnchor(eastM: Double, northM: Double, timestampMs: Long) {
        mLastChordEastM = eastM
        mLastChordNorthM = northM
        mLastChordTimestampMs = timestampMs
    }

    private fun clearChordHeadingAnchor() {
        mLastChordEastM = null
        mLastChordNorthM = null
        mLastChordTimestampMs = 0L
    }

    private fun geodesicDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLatM = Math.toRadians(lat2 - lat1) * EARTH_RADIUS_METERS
        val dLonM = Math.toRadians(lon2 - lon1) * EARTH_RADIUS_METERS * cos(Math.toRadians((lat1 + lat2) * 0.5))
        return hypot(dLatM, dLonM)
    }

    /**
     * Computes genuine raw GNSS displacement speed when Doppler [Location.hasSpeed] is unavailable,
     * prioritizing a confirmed recent >= 3.0 s standstill cluster over older pre-stop fixes to prevent
     * 15-s ghost speed tails while avoiding premature ZUPT lockouts when movement genuinely resumes.
     */
    private fun computeRawGnssDisplacementSpeedMps(
        lat: Double,
        lon: Double,
        accMeters: Double,
        timestampMs: Long
    ): Double? {
        if (!lat.isFinite() || !lon.isFinite() || !accMeters.isFinite()) return null
        if (accMeters <= 0.0 || accMeters > UnicycleOdometryEkf.MAX_GNSS_ACCURACY_METERS) return null

        // Pass 1: Confirm whether the recent 3.0..4.5 s tail is tightly clustered at standstill
        // before scanning older pre-stop lookback fixes (prevents ghost speed after stopping).
        var maxRecentDistM = 0.0
        var hasRecent3sSpan = false
        var recentSampleCount = 0
        val recentIter = mRecentRawGnssFixes.descendingIterator()
        while (recentIter.hasNext()) {
            val prev = recentIter.next()
            val dtSec = (timestampMs - prev.timestampMs) / 1000.0
            if (dtSec < 0.5) continue
            if (dtSec > 4.5) break
            val distM = geodesicDistanceMeters(prev.lat, prev.lon, lat, lon)
            if (distM > maxRecentDistM) maxRecentDistM = distM
            if (dtSec >= CLUSTER_STOP_MIN_SPAN_SEC) hasRecent3sSpan = true
            recentSampleCount++
        }
        val confirmedStandstill = hasRecent3sSpan && recentSampleCount >= 2 &&
            maxRecentDistM <= CLUSTER_STOP_RADIUS_METERS

        var detectedSpeedMps: Double? = null
        if (!confirmedStandstill) {
            val iter = mRecentRawGnssFixes.descendingIterator()
            while (iter.hasNext()) {
                val prev = iter.next()
                val dtSec = (timestampMs - prev.timestampMs) / 1000.0
                if (dtSec < 0.5 || dtSec > MAX_DISPLACEMENT_LOOKBACK_SEC) continue
                val distM = geodesicDistanceMeters(prev.lat, prev.lon, lat, lon)
                val noiseFloorM = max(
                    MIN_DISPLACEMENT_ESCAPE_METERS,
                    DISPLACEMENT_ACCURACY_FACTOR * max(prev.accuracyMeters, accMeters)
                )
                val speedMps = distM / dtSec
                if (distM > noiseFloorM && speedMps <= MAX_PLAUSIBLE_GNSS_SPEED_MPS) {
                    detectedSpeedMps = speedMps
                    break
                }
            }
        }

        val last = mRecentRawGnssFixes.peekLast()
        if (last == null || timestampMs > last.timestampMs) {
            mRecentRawGnssFixes.addLast(RawGnssFix(lat, lon, accMeters, timestampMs))
        }
        val cutoffMs = timestampMs - (MAX_DISPLACEMENT_LOOKBACK_SEC * 1000.0).toLong()
        while (mRecentRawGnssFixes.size > 1 && mRecentRawGnssFixes.peekFirst()!!.timestampMs < cutoffMs) {
            mRecentRawGnssFixes.removeFirst()
        }

        return if (confirmedStandstill) 0.0 else detectedSpeedMps
    }

    private fun clearYawRateWindow() {
        synchronized(mYawRateLock) {
            mYawRateWindow.clear()
            mYawRateWindowDurationSec = 0.0
            mYawRateWeightedSum = 0.0
            mEffectiveGyroNoiseScale = 1.0
            mEffectiveYawWindowSec = YAW_RATE_WINDOW_SEC
            mLastStableYawRateRadPerSec = 0.0
            mCachedCarriageMode = PhoneCarriageMode.UNKNOWN
            mLastEmittedCarriageMode = PhoneCarriageMode.UNKNOWN
            mCachedTargetYawWindowSec = YAW_RATE_WINDOW_SEC
            mLastClassifyTimestampMs = Long.MIN_VALUE
            mLastModeEmissionTimestampMs = Long.MIN_VALUE
            mLastAdaptationTimestampMs = Long.MIN_VALUE
        }
    }

    @VisibleForTesting
    internal fun classifyEvaluationCountForTest(): Int =
        synchronized(mYawRateLock) { mClassifyEvaluationCount }

    @VisibleForTesting
    internal fun modeEmissionCountForTest(): Int =
        synchronized(mYawRateLock) { mModeEmissionCount }

    @VisibleForTesting
    internal fun yawRateWindowSizeForTest(): Int = synchronized(mYawRateLock) { mYawRateWindow.size }

    @VisibleForTesting
    internal fun effectiveGyroNoiseScaleForTest(): Double =
        synchronized(mYawRateLock) { mEffectiveGyroNoiseScale }

    @VisibleForTesting
    internal fun effectiveYawWindowSecForTest(): Double =
        synchronized(mYawRateLock) { mEffectiveYawWindowSec }

    @VisibleForTesting
    internal fun ingestBarometerAltitudeForTesting(baroAltitudeM: Double, timestampMs: Long) {
        carriageClassifier.ingestBarometerAltitude(baroAltitudeM, timestampMs)
    }

    @VisibleForTesting
    internal fun ingestStepEventForTesting(timestampMs: Long) {
        carriageClassifier.ingestStepEvent(timestampMs)
    }

    @VisibleForTesting
    internal fun setSportContextHintForTesting(hint: SportContextHint?) {
        mExplicitSportHintForTest = hint
    }

    @VisibleForTesting
    fun ingestImuMeasurement(
        gameRotationVector: FloatArray,
        gyroValues: FloatArray,
        dtSec: Double,
        timestampMs: Long = System.currentTimeMillis()
    ) {
        carriageClassifier.ingestRotationVector(gameRotationVector, timestampMs)
        val worldYawRate = gyroProjector.projectYawRate(gameRotationVector, gyroValues)
        val rawGyroMag = if (gyroValues.size >= 3) {
            sqrt(
                gyroValues[0].toDouble() * gyroValues[0].toDouble() +
                    gyroValues[1].toDouble() * gyroValues[1].toDouble() +
                    gyroValues[2].toDouble() * gyroValues[2].toDouble()
            )
        } else {
            abs(worldYawRate)
        }
        processWorldYawStep(worldYawRate, rawGyroMag, dtSec, timestampMs)
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
        clearChordHeadingAnchor()
        mRecentRawGnssFixes.clear()
        clearYawRateWindow()
        carriageClassifier.reset()
        mActivityAndCarriageModeSensor?.newValue(PhoneCarriageMode.UNKNOWN.code)
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
