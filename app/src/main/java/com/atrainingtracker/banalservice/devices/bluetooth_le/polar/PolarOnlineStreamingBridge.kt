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

@file:Suppress("PackageNaming")

package com.atrainingtracker.banalservice.devices.bluetooth_le.polar

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.MyRemoteDevice
import com.atrainingtracker.banalservice.devices.ProtocolType
import com.atrainingtracker.banalservice.devices.RrBeatEvent
import com.atrainingtracker.banalservice.devices.RrBeatListener
import com.atrainingtracker.banalservice.devices.RrBeatSource
import com.atrainingtracker.banalservice.sensor.MyIntegerAccumulatorSensor
import com.atrainingtracker.banalservice.sensor.MySensor
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.util.LifecycleUtils
import com.polar.androidcommunications.api.ble.model.DisInfo
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.model.PolarAccelerometerData
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarGyroData
import com.polar.sdk.api.model.PolarHealthThermometerData
import com.polar.sdk.api.model.PolarHrData
import com.polar.sdk.api.model.PolarSensorSetting
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * Persistent single-GATT bridge for Polar H10 (`ACC`) and Verity Sense (`ACC` + `GYRO`) live
 * PMD streaming (`ATT-8016`, `REQ-SET-112`).
 *
 * Replaces a parallel `MyBTLEDevice` (`0x180D`) client when live IMU is opted in on Android 13+,
 * feeds `HR`/`RR` into [MySensorManager] and [RrBeatSource], shares the persistent [PolarBleApi]
 * session with [PolarOfflineGattCoordinator], and gates `CADENCE`/`STRIDES` strictly on
 * [BSportType.RUN] (including walking) at lowest cadence priority.
 */
@Suppress("TooManyFunctions", "TooGenericExceptionCaught")
class PolarOnlineStreamingBridge @JvmOverloads constructor(
    context: Context,
    mySensorManager: MySensorManager?,
    deviceId: Long,
    private val macAddress: String,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val sdkIntProvider: () -> Int = { Build.VERSION.SDK_INT },
    private val sportTypeProvider: () -> BSportType = { BANALService.getActiveBSportType() },
    private val apiFactory: ((Context, Set<PolarBleApi.PolarBleSdkFeature>) -> PolarBleApi)? = null
) : MyRemoteDevice(context, mySensorManager, DeviceType.HRM, deviceId), RrBeatSource {

    /**
     * Listener interface for raw live 3-axis accelerometer and gyroscope frames from Polar PMD streams.
     */
    interface ImuFrameListener {
        fun onAccFrame(polarId: String, data: PolarAccelerometerData)
        fun onGyroFrame(polarId: String, data: PolarGyroData) {}
    }

    companion object {
        private const val TAG = "PolarOnlineImuBridge"
        private const val DRIFT_THRESHOLD_MS = 2000.0
        private const val NS_PER_MS = 1_000_000L
        private const val H10_DEFAULT_ACC_HZ = 100
        private const val H10_FALLBACK_ACC_HZ = 50
        private const val H10_HIGH_RATE_ACC_HZ = 200
        private const val VERITY_IMU_HZ = 52
    }

    private lateinit var mHeartRateSensor: MySensor<Int>
    private var mRrIntervalSensor: MySensor<Int>? = null
    private var mCadenceSensor: MySensor<Double>? = null
    private var mStridesSensor: MyIntegerAccumulatorSensor? = null
    private var mVerticalOscillationSensor: MySensor<Double>? = null
    private var mGroundContactTimeSensor: MySensor<Double>? = null

    private val rrBeatListeners = CopyOnWriteArrayList<RrBeatListener>()
    private val imuFrameListeners = CopyOnWriteArrayList<ImuFrameListener>()
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    private val isShutDown = AtomicBoolean(false)
    private val isConnectedFlag = AtomicBoolean(false)
    private val isAccStreamingActive = AtomicBoolean(false)
    private val isGyroStreamingActive = AtomicBoolean(false)

    @Volatile
    private var polarApi: PolarBleApi? = null

    @Volatile
    private var hrJob: Job? = null

    @Volatile
    private var accJob: Job? = null

    @Volatile
    private var gyroJob: Job? = null

    @Volatile
    var highRateRunningDynamicsActive: Boolean = false

    private var lastBeatTimestampMs = 0.0
    private var packetCounter = 0

    private val cadenceEstimatorLock = Any()
    private val cadenceEstimator: PolarLiveRunningCadenceEstimator by lazy {
        PolarLiveRunningCadenceEstimator(
            mode = if (isVeritySense) {
                PolarLiveRunningCadenceEstimator.Mode.VERITY_SENSE_ARM
            } else {
                PolarLiveRunningCadenceEstimator.Mode.H10_CHEST
            },
            onCadence = { cadenceSpm -> mCadenceSensor?.newValue(cadenceSpm) },
            onStride = { cumulativeStrides -> mStridesSensor?.accumulate(cumulativeStrides) }
        )
    }
    private val runningDynamicsEstimator: PolarH10RunningDynamicsEstimator by lazy {
        PolarH10RunningDynamicsEstimator(
            onVerticalOscillation = { voCm -> mVerticalOscillationSensor?.newValue(voCm) },
            onGroundContactTime = { gctMs -> mGroundContactTimeSensor?.newValue(gctMs) }
        )
    }

    private val deviceNameStr: String by lazy { getName() ?: macAddress }
    private val manufacturerStr: String by lazy { getManufacturerName() ?: "" }
    val polarId: String by lazy { PolarOfflineGattCoordinator.extractPolarId(deviceNameStr, macAddress) }
    val isVeritySense: Boolean by lazy {
        !PolarOfflineGattCoordinator.isPolarH10Name(deviceNameStr, manufacturerStr) &&
            PolarOfflineGattCoordinator.isPolarVeritySenseDevice(mContext, deviceNameStr, manufacturerStr, macAddress)
    }

    private val sportChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            syncCadenceSensorRegistration()
        }
    }

    init {
        try {
            ContextCompat.registerReceiver(
                mContext,
                sportChangedReceiver,
                IntentFilter(BANALService.SPORT_TYPE_CHANGED_BY_USER_INTENT),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (_: Throwable) {
            // Ignored in minimal unit test contexts without receiver registration.
        }
    }

    private val sdkCallback = object : PolarBleApiCallback() {
        override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
            isConnectedFlag.set(true)
            setLastActive()
            registerSensors()
            syncCadenceSensorRegistration()
            notifyStopSearching(true)
        }

        override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
            isConnectedFlag.set(false)
            isAccStreamingActive.set(false)
            isGyroStreamingActive.set(false)
            PolarOfflineGattCoordinator.releaseAccChannel(polarId, PolarOfflineGattCoordinator.ACC_OWNER_ONLINE)
            unregisterSensors()
        }

        override fun bleSdkFeatureReady(
            identifier: String,
            feature: PolarBleApi.PolarBleSdkFeature
        ) {
            if (!identifier.equals(polarId, ignoreCase = true) && identifier != polarId) return
            when (feature) {
                PolarBleApi.PolarBleSdkFeature.FEATURE_HR -> startHrCollection()
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING -> startImuCollection()
                else -> Unit
            }
        }

        override fun batteryLevelReceived(identifier: String, level: Int) {
            if ((identifier.equals(polarId, ignoreCase = true) || identifier == polarId) && level in 0..100) {
                setBatteryPercentage(level)
            }
        }

        override fun disInformationReceived(identifier: String, disInfo: DisInfo) {
            // No-op stub.
        }

        override fun htsNotificationReceived(identifier: String, data: PolarHealthThermometerData) {
            // No-op stub.
        }
    }

    override fun addSensors() {
        mHeartRateSensor = MySensor(this, SensorType.HR)
        addSensor(mHeartRateSensor)
    }

    override fun getProtocol(): Protocol = Protocol.BLUETOOTH_LE

    fun getAddress(): String = macAddress

    override fun isReceivingData(): Boolean = isConnectedFlag.get() && !isShutDown.get()

    // Runtime API >= 33 gate is enforced via sdkIntProvider() seam for unit testability.
    @SuppressLint("NewApi")
    override fun startSearching() {
        if (isShutDown.get() || sdkIntProvider() < Build.VERSION_CODES.TIRAMISU) {
            notifyStopSearching(false)
            return
        }
        notifyStartSearching()
        connectPersistentSession()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun connectPersistentSession() {
        if (polarApi != null) return
        try {
            val features = buildRequiredFeatures()
            val api = apiFactory?.invoke(mContext, features)
                ?: PolarBleApiDefaultImpl.defaultImplementation(mContext, features)
            polarApi = api
            api.setAutomaticReconnection(true)
            api.setApiCallback(sdkCallback)
            PolarOfflineGattCoordinator.registerActiveSession(polarId, api, sdkCallback)
            api.connectToDevice(polarId)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to start persistent Polar PMD session for $polarId: ${t.message}", t)
            notifyStopSearching(false)
        }
    }

    private fun buildRequiredFeatures(): Set<PolarBleApi.PolarBleSdkFeature> {
        val features = mutableSetOf(
            PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING,
            PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
            PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP
        )
        if (isVeritySense) {
            features.add(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING)
            features.add(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_TRAINING_DATA)
        } else {
            features.add(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING)
        }
        return features
    }

    private fun startHrCollection() {
        val api = polarApi ?: return
        if (isShutDown.get()) return
        hrJob?.cancel()
        hrJob = scope.launch {
            api.startHrStreaming(polarId)
                .catch { e -> Log.w(TAG, "HR stream error on $polarId: ${e.message}") }
                .collect { hrData ->
                    processHrData(hrData, System.currentTimeMillis())
                }
        }
    }

    private fun startImuCollection() {
        val api = polarApi ?: return
        if (isShutDown.get()) return
        startAccCollection(api)
        if (isVeritySense) {
            startGyroCollection(api)
        }
    }

    private fun startAccCollection(api: PolarBleApi) {
        if (!PolarOfflineGattCoordinator.tryAcquireAccChannel(polarId, PolarOfflineGattCoordinator.ACC_OWNER_ONLINE)) {
            Log.w(TAG, "Skipping online ACC stream on $polarId: ACC channel locked by offline recording.")
            return
        }
        highRateRunningDynamicsActive = !isVeritySense && isRunSportActive()
        accJob?.cancel()
        accJob = scope.launch {
            try {
                val setting = resolveAccSetting(api)
                isAccStreamingActive.set(true)
                api.startAccStreaming(polarId, setting)
                    .catch { e -> Log.w(TAG, "ACC stream error on $polarId: ${e.message}") }
                    .collect { accData ->
                        processAccData(accData)
                    }
            } finally {
                isAccStreamingActive.set(false)
                PolarOfflineGattCoordinator.releaseAccChannel(polarId, PolarOfflineGattCoordinator.ACC_OWNER_ONLINE)
            }
        }
    }

    private fun startGyroCollection(api: PolarBleApi) {
        gyroJob?.cancel()
        gyroJob = scope.launch {
            try {
                val setting = resolveGyroSetting(api)
                isGyroStreamingActive.set(true)
                api.startGyroStreaming(polarId, setting)
                    .catch { e -> Log.w(TAG, "GYRO stream error on $polarId: ${e.message}") }
                    .collect { gyroData ->
                        processGyroData(gyroData)
                    }
            } finally {
                isGyroStreamingActive.set(false)
            }
        }
    }

    private suspend fun resolveAccSetting(api: PolarBleApi): PolarSensorSetting {
        val available = api.requestStreamSettings(polarId, PolarBleApi.PolarDeviceDataType.ACC)
        val rates = available.settings[PolarSensorSetting.SettingType.SAMPLE_RATE].orEmpty()
        val desiredRate = when {
            isVeritySense -> VERITY_IMU_HZ
            highRateRunningDynamicsActive && rates.contains(H10_HIGH_RATE_ACC_HZ) -> H10_HIGH_RATE_ACC_HZ
            rates.contains(H10_DEFAULT_ACC_HZ) -> H10_DEFAULT_ACC_HZ
            rates.contains(H10_FALLBACK_ACC_HZ) -> H10_FALLBACK_ACC_HZ
            else -> null
        }
        return buildSettingWithRate(available, desiredRate)
    }

    private suspend fun resolveGyroSetting(api: PolarBleApi): PolarSensorSetting {
        val available = api.requestStreamSettings(polarId, PolarBleApi.PolarDeviceDataType.GYRO)
        val rates = available.settings[PolarSensorSetting.SettingType.SAMPLE_RATE].orEmpty()
        val desiredRate = if (rates.contains(VERITY_IMU_HZ)) VERITY_IMU_HZ else null
        return buildSettingWithRate(available, desiredRate)
    }

    private fun buildSettingWithRate(
        available: PolarSensorSetting,
        desiredRate: Int?
    ): PolarSensorSetting {
        if (desiredRate == null) {
            return available.maxSettings()
        }
        val selected = mutableMapOf<PolarSensorSetting.SettingType, Int>()
        available.settings.forEach { (type, values) ->
            if (values.isNotEmpty()) {
                selected[type] = if (type == PolarSensorSetting.SettingType.SAMPLE_RATE) {
                    desiredRate
                } else {
                    values.max()
                }
            }
        }
        return if (selected.isEmpty()) available.maxSettings() else PolarSensorSetting(selected)
    }

    /**
     * Processes a live [PolarHrData] packet into `SensorType.HR`, `SensorType.RR_INTERVAL`, and
     * [RrBeatSource] listeners using cumulative beat timestamps matching `BTLEHeartRateDevice`.
     */
    fun processHrData(data: PolarHrData, packetArrivalMs: Long) {
        if (isShutDown.get()) return
        for (sample in data.samples) {
            if (sample.hr > 0) {
                mHeartRateSensor.newValue(sample.hr)
            }
            if (sample.rrAvailable && sample.rrsMs.isNotEmpty()) {
                dispatchRrSamples(sample.rrsMs, packetArrivalMs)
            }
        }
    }

    private fun dispatchRrSamples(rrListMs: List<Int>, packetArrivalMs: Long) {
        if (mRrIntervalSensor == null) {
            val rrSensor = MySensor<Int>(this, SensorType.RR_INTERVAL)
            mRrIntervalSensor = rrSensor
            addAndRegisterSensor(rrSensor)
        }
        packetCounter++
        val count = rrListMs.size
        if (count == 0) return

        val totalDurationMs = rrListMs.sumOf { it.toDouble() }
        val isInitialOrDrifted = lastBeatTimestampMs == 0.0 ||
            abs(packetArrivalMs - (lastBeatTimestampMs + totalDurationMs)) > DRIFT_THRESHOLD_MS

        if (isInitialOrDrifted) {
            val gapBeats = computeGapBeats(totalDurationMs, count, packetArrivalMs)
            val timestamps = DoubleArray(count)
            timestamps[count - 1] = packetArrivalMs.toDouble()
            for (i in count - 2 downTo 0) {
                timestamps[i] = timestamps[i + 1] - rrListMs[i + 1].toDouble()
            }
            for (i in 0 until count) {
                val rrMs = rrListMs[i].toDouble()
                mRrIntervalSensor?.newValue(rrListMs[i])
                val beatGap = if (i == 0 && lastBeatTimestampMs != 0.0) gapBeats else 0
                val artifact = if (beatGap > 0) RrBeatEvent.ARTIFACT_GAP else RrBeatEvent.ARTIFACT_NORMAL
                dispatchRrBeat(
                    RrBeatEvent(
                        Math.round(timestamps[i]),
                        rrMs,
                        packetCounter,
                        ProtocolType.BLE,
                        beatGap,
                        artifact
                    )
                )
            }
            lastBeatTimestampMs = timestamps[count - 1]
        } else {
            var currentTs = lastBeatTimestampMs
            for (rr in rrListMs) {
                val rrMs = rr.toDouble()
                currentTs += rrMs
                mRrIntervalSensor?.newValue(rr)
                dispatchRrBeat(
                    RrBeatEvent(
                        Math.round(currentTs),
                        rrMs,
                        packetCounter,
                        ProtocolType.BLE,
                        0,
                        RrBeatEvent.ARTIFACT_NORMAL
                    )
                )
            }
            lastBeatTimestampMs = currentTs
        }
    }

    private fun computeGapBeats(totalDurationMs: Double, count: Int, packetArrivalMs: Long): Int {
        if (lastBeatTimestampMs == 0.0) return 0
        val elapsed = packetArrivalMs - lastBeatTimestampMs
        val avgRr = totalDurationMs / count
        return if (avgRr > 0.0 && elapsed > avgRr) {
            maxOf(0L, Math.round(elapsed / avgRr) - count).toInt()
        } else {
            0
        }
    }

    /**
     * Processes live [PolarAccelerometerData] frames, notifies raw IMU listeners, and emits
     * IMU-derived `CADENCE`/`STRIDES` strictly when [BSportType.RUN] is active (`REQ-SET-112.4`).
     */
    fun processAccData(data: PolarAccelerometerData) {
        if (isShutDown.get()) return
        for (listener in imuFrameListeners) {
            try {
                listener.onAccFrame(polarId, data)
            } catch (e: Exception) {
                Log.w(TAG, "Error in ImuFrameListener.onAccFrame: ${e.message}")
            }
        }
        val isRunSport = isRunSportActive()
        syncCadenceSensorRegistration(isRunSport)
        if (!isRunSport) return

        synchronized(cadenceEstimatorLock) {
            for (sample in data.samples) {
                val timestampMs = if (sample.timeStamp > 0L) sample.timeStamp / NS_PER_MS else 0L
                cadenceEstimator.onAccSample(sample.x, sample.y, sample.z, timestampMs)
                if (!isVeritySense) {
                    val cadenceStepsPerMin = cadenceEstimator.latestCadenceStridesPerMin * 2.0
                    runningDynamicsEstimator.setCurrentCadenceStepsPerMin(cadenceStepsPerMin)
                    runningDynamicsEstimator.onAccSample(sample.x, sample.y, sample.z, timestampMs)
                }
            }
        }
    }

    /**
     * Processes live [PolarGyroData] frames, notifies raw IMU listeners, and feeds Verity Sense
     * arm-swing gyroscope telemetry into [PolarLiveRunningCadenceEstimator] when [BSportType.RUN]
     * is active (`REQ-SET-114.3`).
     */
    fun processGyroData(data: PolarGyroData) {
        if (isShutDown.get()) return
        for (listener in imuFrameListeners) {
            try {
                listener.onGyroFrame(polarId, data)
            } catch (e: Exception) {
                Log.w(TAG, "Error in ImuFrameListener.onGyroFrame: ${e.message}")
            }
        }
        val isRunSport = isRunSportActive()
        syncCadenceSensorRegistration(isRunSport)
        if (!isRunSport || !isVeritySense) return

        synchronized(cadenceEstimatorLock) {
            for (sample in data.samples) {
                val timestampMs = if (sample.timeStamp > 0L) sample.timeStamp / NS_PER_MS else 0L
                cadenceEstimator.onGyroSample(sample.x, sample.y, sample.z, timestampMs)
            }
        }
    }

    /**
     * Synchronizes registration of IMU-derived [SensorType.CADENCE] and [SensorType.STRIDES]
     * strictly to [BSportType.RUN] (which covers both running and walking).
     */
    fun syncCadenceSensorRegistration(runActive: Boolean = isRunSportActive()) {
        if (isShutDown.get()) return
        highRateRunningDynamicsActive = !isVeritySense && runActive
        if (runActive) {
            if (mCadenceSensor == null) {
                val cadSensor = MySensor<Double>(this, SensorType.CADENCE)
                mCadenceSensor = cadSensor
                addAndRegisterSensor(cadSensor)
            }
            if (mStridesSensor == null) {
                val strideSensor = MyIntegerAccumulatorSensor(this, SensorType.STRIDES, true)
                mStridesSensor = strideSensor
                addAndRegisterSensor(strideSensor)
                // Seed baseline 0 so the first detected stride increments SensorType.STRIDES to 1.
                strideSensor.accumulate(0)
            }
            if (!isVeritySense) {
                if (mVerticalOscillationSensor == null) {
                    val voSensor = MySensor<Double>(this, SensorType.VERTICAL_OSCILLATION)
                    mVerticalOscillationSensor = voSensor
                    addAndRegisterSensor(voSensor)
                }
                if (mGroundContactTimeSensor == null) {
                    val gctSensor = MySensor<Double>(this, SensorType.GROUND_CONTACT_TIME)
                    mGroundContactTimeSensor = gctSensor
                    addAndRegisterSensor(gctSensor)
                }
            }
        } else {
            unregisterImuCadenceSensors()
        }
    }

    private fun unregisterImuCadenceSensors() {
        mCadenceSensor?.let { sensor ->
            mMySensorManager?.unregisterSensor(sensor)
            removeSensor(SensorType.CADENCE)
            mCadenceSensor = null
        }
        mStridesSensor?.let { sensor ->
            mMySensorManager?.unregisterSensor(sensor)
            removeSensor(SensorType.STRIDES)
            mStridesSensor = null
        }
        mVerticalOscillationSensor?.let { sensor ->
            mMySensorManager?.unregisterSensor(sensor)
            removeSensor(SensorType.VERTICAL_OSCILLATION)
            mVerticalOscillationSensor = null
        }
        mGroundContactTimeSensor?.let { sensor ->
            mMySensorManager?.unregisterSensor(sensor)
            removeSensor(SensorType.GROUND_CONTACT_TIME)
            mGroundContactTimeSensor = null
        }
        synchronized(cadenceEstimatorLock) {
            cadenceEstimator.reset()
            runningDynamicsEstimator.reset()
        }
    }

    private fun isRunSportActive(): Boolean = sportTypeProvider() == BSportType.RUN

    fun addImuFrameListener(listener: ImuFrameListener) {
        if (!imuFrameListeners.contains(listener)) {
            imuFrameListeners.add(listener)
        }
    }

    fun removeImuFrameListener(listener: ImuFrameListener) {
        imuFrameListeners.remove(listener)
    }

    override fun addRrBeatListener(listener: RrBeatListener) {
        if (!rrBeatListeners.contains(listener)) {
            rrBeatListeners.add(listener)
        }
    }

    override fun removeRrBeatListener(listener: RrBeatListener) {
        rrBeatListeners.remove(listener)
    }

    private fun dispatchRrBeat(event: RrBeatEvent) {
        for (listener in rrBeatListeners) {
            try {
                listener.onRrBeat(event)
            } catch (e: Exception) {
                Log.e(TAG, "Error in RrBeatListener callback", e)
            }
        }
    }

    @VisibleForTesting
    fun getSdkCallbackForTesting(): PolarBleApiCallback = sdkCallback

    @VisibleForTesting
    fun isAccStreamingForTesting(): Boolean = isAccStreamingActive.get()

    @VisibleForTesting
    fun isGyroStreamingForTesting(): Boolean = isGyroStreamingActive.get()

    override fun unregisterSensors() {
        unregisterImuCadenceSensors()
        mRrIntervalSensor?.let { sensor ->
            mMySensorManager?.unregisterSensor(sensor)
            removeSensor(SensorType.RR_INTERVAL)
            mRrIntervalSensor = null
        }
        super.unregisterSensors()
        lastBeatTimestampMs = 0.0
        packetCounter = 0
    }

    override fun shutDown() {
        if (!isShutDown.compareAndSet(false, true)) return
        isConnectedFlag.set(false)
        isAccStreamingActive.set(false)
        isGyroStreamingActive.set(false)

        LifecycleUtils.safeUnregisterReceiver(mContext, sportChangedReceiver, TAG)

        hrJob?.cancel()
        accJob?.cancel()
        gyroJob?.cancel()

        val api = polarApi
        polarApi = null
        if (api != null) {
            try {
                api.stopStreaming(polarId, PmdMeasurementType.ACC)
            } catch (_: Throwable) {
            }
            if (isVeritySense) {
                try {
                    api.stopStreaming(polarId, PmdMeasurementType.GYRO)
                } catch (_: Throwable) {
                }
            }
            PolarOfflineGattCoordinator.releaseAccChannel(polarId, PolarOfflineGattCoordinator.ACC_OWNER_ONLINE)
            PolarOfflineGattCoordinator.unregisterActiveSession(polarId, api)
            try {
                api.disconnectFromDevice(polarId)
            } catch (_: Throwable) {
            }
            if (apiFactory == null) {
                try {
                    api.cleanup()
                } catch (_: Throwable) {
                }
            }
        } else {
            PolarOfflineGattCoordinator.releaseAccChannel(polarId, PolarOfflineGattCoordinator.ACC_OWNER_ONLINE)
        }

        unregisterSensors()
        rrBeatListeners.clear()
        imuFrameListeners.clear()
        scope.cancel()
        super.shutDown()
    }
}
