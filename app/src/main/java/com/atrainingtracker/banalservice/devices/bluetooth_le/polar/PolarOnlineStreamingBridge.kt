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
import com.atrainingtracker.banalservice.devices.RrBeatNormalization
import com.atrainingtracker.banalservice.devices.RrBeatSource
import com.atrainingtracker.banalservice.devices.SensorReconnectBackoffPolicy
import com.atrainingtracker.banalservice.devices.bluetooth_le.BluetoothConstants
import com.atrainingtracker.banalservice.devices.hrv.HrvSignalQualityResult
import com.atrainingtracker.banalservice.devices.hrv.HrvTelemetryProvider
import com.atrainingtracker.banalservice.devices.hrv.PerDeviceHrvController
import com.atrainingtracker.banalservice.sensor.MyIntegerAccumulatorSensor
import com.atrainingtracker.banalservice.sensor.MySensor
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.settings.SensorSearchSettings
import com.atrainingtracker.util.LifecycleUtils
import com.polar.androidcommunications.api.ble.model.DisInfo
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.model.PolarAccelerometerData
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarGyroData
import com.polar.sdk.api.model.PolarHealthThermometerData
import com.polar.sdk.api.model.PolarHrData
import com.polar.sdk.api.model.PolarPpiData
import com.polar.sdk.api.model.PolarSensorSetting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Persistent single-GATT bridge for Polar H10 (`ACC`) and Verity Sense (`ACC` + `GYRO`) live
 * PMD streaming (`ATT-8016`, `REQ-SET-112`, `ATT-8451`, `REQ-CON-045`).
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
    private val apiFactory: ((Context, Set<PolarBleApi.PolarBleSdkFeature>) -> PolarBleApi)? = null,
    private val searchTimeoutMsProvider: () -> Long = { TrainingApplication.getBtleSearchPeriodMs() }
) : MyRemoteDevice(context, mySensorManager, DeviceType.HRM, deviceId), RrBeatSource, HrvTelemetryProvider {

    /**
     * Listener interface for raw live 3-axis accelerometer and gyroscope frames from Polar PMD streams.
     */
    interface ImuFrameListener {
        fun onAccFrame(polarId: String, data: PolarAccelerometerData)
        fun onGyroFrame(polarId: String, data: PolarGyroData) {}
        fun onHrSample(polarId: String, hrBpm: Int, timestampNs: Long) {}
    }

    companion object {
        private const val TAG = "PolarOnlineImuBridge"
        private const val NS_PER_MS = 1_000_000L
        private const val H10_DEFAULT_ACC_HZ = 100
        private const val H10_FALLBACK_ACC_HZ = 50
        private const val H10_HIGH_RATE_ACC_HZ = 200
        private const val VERITY_IMU_HZ = 52

        private val activeBridges = CopyOnWriteArrayList<PolarOnlineStreamingBridge>()

        @JvmStatic
        fun attachActiveRecorder(recorder: PolarRawTelemetryRecorder) {
            for (bridge in activeBridges) {
                bridge.addImuFrameListener(recorder)
                bridge.addRrBeatListener(recorder)
            }
        }

        @JvmStatic
        fun detachActiveRecorder(recorder: PolarRawTelemetryRecorder) {
            for (bridge in activeBridges) {
                bridge.removeImuFrameListener(recorder)
                bridge.removeRrBeatListener(recorder)
            }
        }

        @VisibleForTesting
        fun getActiveBridgesForTesting(): List<PolarOnlineStreamingBridge> = activeBridges
    }

    private lateinit var mHeartRateSensor: MySensor<Int>
    private var mRrIntervalSensor: MySensor<Int>? = null
    private var mCadenceSensor: MySensor<Double>? = null
    private var mStridesSensor: MyIntegerAccumulatorSensor? = null
    private var mVerticalOscillationSensor: MySensor<Double>? = null
    private var mGroundContactTimeSensor: MySensor<Double>? = null

    private val rrBeatListeners = CopyOnWriteArrayList<RrBeatListener>()
    private val perDeviceHrvController: PerDeviceHrvController = PerDeviceHrvController(
        context = context,
        hostDevice = this,
        hostCallbacks = object : PerDeviceHrvController.HostCallbacks {
            @Suppress("UNCHECKED_CAST")
            override fun addAndRegisterSensors(sensors: List<MySensor<*>>) {
                if (mMySensorManager != null) {
                    this@PolarOnlineStreamingBridge.addAndRegisterSensors(ArrayList(sensors) as List<MySensor<*>>)
                } else {
                    for (sensor in sensors) {
                        addSensor(sensor)
                        sensor.activateSensor()
                    }
                    notifySensorsChanged()
                }
            }

            override fun unregisterSensor(sensor: MySensor<*>) {
                mMySensorManager?.unregisterSensor(sensor)
            }

            override fun removeSensor(sensorType: SensorType) {
                this@PolarOnlineStreamingBridge.removeSensor(sensorType)
            }
        }
    )

    override val signalQualityResult: HrvSignalQualityResult
        get() = perDeviceHrvController.signalQualityResult

    override val latestArtifactRatePercent: Double?
        get() = perDeviceHrvController.latestArtifactRatePercent

    @VisibleForTesting
    fun getPerDeviceHrvController(): PerDeviceHrvController = perDeviceHrvController

    private val imuFrameListeners = CopyOnWriteArrayList<ImuFrameListener>()
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    private val isShutDown = AtomicBoolean(false)
    private val bleEnabled = AtomicBoolean(true)
    private val isConnectedFlag = AtomicBoolean(false)
    private val hasConnectedInSession = AtomicBoolean(false)
    private val sessionOpenPendingFeatures = AtomicBoolean(false)
    private val isAccStreamingActive = AtomicBoolean(false)
    private val isGyroStreamingActive = AtomicBoolean(false)
    private val isPpiStreamingActive = AtomicBoolean(false)
    private val ppiLifecycleLock = Any()
    private val reconnectLock = Any()

    @Volatile
    private var currentReconnectDelayMs: Long = SensorReconnectBackoffPolicy.INITIAL_DELAY_MS

    @Volatile
    private var reconnectJob: Job? = null

    @Volatile
    private var polarApi: PolarBleApi? = null

    @Volatile
    private var coordinatorSessionKey: String? = null

    @Volatile
    private var activeSdkStreamId: String? = null

    private val sdkSessionId: String
        get() = activeSdkStreamId ?: coordinatorSessionKey ?: polarId

    @Volatile
    private var hrJob: Job? = null

    @Volatile
    private var accJob: Job? = null

    @Volatile
    private var gyroJob: Job? = null

    @Volatile
    private var ppiJob: Job? = null

    private val ppiStreamProcessor = PolarPpiStreamProcessor()

    @Volatile
    var highRateRunningDynamicsActive: Boolean = false

    private var lastBeatTimestampMs = 0.0
    private var lastNormalRrMs = RrBeatNormalization.DEFAULT_REFERENCE_RR_MS
    private var packetCounter = 0

    private val cadenceEstimatorLock = Any()
    private var cadenceEstimatorInstance: PolarLiveRunningCadenceEstimator? = null
    private var cadenceEstimatorMode: PolarLiveRunningCadenceEstimator.Mode? = null

    private fun getCadenceEstimator(): PolarLiveRunningCadenceEstimator {
        val desiredMode = if (isVeritySense) {
            PolarLiveRunningCadenceEstimator.Mode.VERITY_SENSE_ARM
        } else {
            PolarLiveRunningCadenceEstimator.Mode.H10_CHEST
        }
        val current = cadenceEstimatorInstance
        if (current != null && cadenceEstimatorMode == desiredMode) {
            return current
        }
        val created = PolarLiveRunningCadenceEstimator(
            mode = desiredMode,
            onCadence = { cadenceSpm -> mCadenceSensor?.newValue(cadenceSpm) },
            onStride = { cumulativeStrides -> mStridesSensor?.accumulate(cumulativeStrides) }
        )
        cadenceEstimatorInstance = created
        cadenceEstimatorMode = desiredMode
        return created
    }
    private val runningDynamicsEstimator: PolarH10RunningDynamicsEstimator by lazy {
        PolarH10RunningDynamicsEstimator(
            onVerticalOscillation = { voCm -> mVerticalOscillationSensor?.newValue(voCm) },
            onGroundContactTime = { gctMs -> mGroundContactTimeSensor?.newValue(gctMs) }
        )
    }

    private val deviceNameStr: String by lazy { getName() ?: macAddress }
    private val manufacturerStr: String by lazy { getManufacturerName() ?: "" }
    val polarId: String
        get() = PolarOfflineGattCoordinator.extractPolarId(mContext, deviceNameStr, macAddress)
    val isVeritySense: Boolean
        get() = PolarOfflineGattCoordinator.isPolarVeritySenseDevice(
            mContext,
            deviceNameStr,
            manufacturerStr,
            macAddress
        )

    private val sportChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            syncCadenceSensorRegistration()
        }
    }

    init {
        PolarOfflineCapabilityStore.getPolarDeviceId(mContext, macAddress)?.let { persistedPolarId ->
            PolarOfflineGattCoordinator.registerDeviceAliases(persistedPolarId, macAddress)
        }
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

    private var searchTimeoutJob: Job? = null

    private fun armSearchTimeoutWatchdog() {
        cancelSearchTimeoutWatchdog()
        val timeoutMs = searchTimeoutMsProvider()
        searchTimeoutJob = scope.launch {
            delay(timeoutMs)
            onSearchTimeout()
        }
    }

    private fun cancelSearchTimeoutWatchdog() {
        searchTimeoutJob?.cancel()
        searchTimeoutJob = null
    }

    private fun cancelReconnectJob() {
        synchronized(reconnectLock) {
            reconnectJob?.cancel()
            reconnectJob = null
        }
    }

    private fun isMidSessionOrWorkoutActive(): Boolean {
        val mode = TrainingApplication.getTrackingMode()
        return hasConnectedInSession.get() ||
            mode == TrackingMode.TRACKING ||
            mode == TrackingMode.PAUSED
    }

    override fun shouldKeepAliveOnSearchTimeout(): Boolean =
        !isShutDown.get() && isMidSessionOrWorkoutActive()

    @SuppressLint("NewApi")
    private fun scheduleBackoffReconnect() {
        if (isShutDown.get() || !bleEnabled.get() || sdkIntProvider() < Build.VERSION_CODES.TIRAMISU) return
        if (!isMidSessionOrWorkoutActive() && !SensorSearchSettings.isPeriodicResearchEnabled(mContext)) return
        synchronized(reconnectLock) {
            if (isShutDown.get() || !bleEnabled.get() || isReceivingData()) return
            // Keep an already-armed reconnect timer intact (prevents double-advance on timeout disconnect echo).
            if (reconnectJob?.isActive == true) return
            val delayMs = SensorReconnectBackoffPolicy.effectiveDelayMs(currentReconnectDelayMs, mContext)
            currentReconnectDelayMs = SensorReconnectBackoffPolicy.nextDelayMs(delayMs, mContext)
            reconnectJob = scope.launch {
                try {
                    delay(delayMs)
                    synchronized(reconnectLock) {
                        if (coroutineContext[Job] === reconnectJob) {
                            reconnectJob = null
                        }
                    }
                    if (isShutDown.get() || !bleEnabled.get() || isReceivingData()) return@launch
                    Log.i(TAG, "Polar background reconnect firing for $polarId after ${delayMs}ms backoff")
                    PolarOfflineGattCoordinator.registerDeviceAliases(polarId, macAddress)
                    armSearchTimeoutWatchdog()
                    connectPersistentSession()
                } finally {
                    synchronized(reconnectLock) {
                        if (coroutineContext[Job] === reconnectJob) {
                            reconnectJob = null
                        }
                    }
                }
            }
        }
    }

    private fun onSearchTimeout() {
        if (isShutDown.get()) return
        if (!isReceivingData()) {
            val sessionId = sdkSessionId
            Log.i(TAG, "Search timed out for Polar sensor $sessionId after ${searchTimeoutMsProvider()} ms")
            try {
                polarApi?.disconnectFromDevice(sessionId)
            } catch (t: Throwable) {
                Log.w(TAG, "Error disconnecting Polar API on search timeout for $sessionId: ${t.message}")
            }
            if (isSearching) {
                notifyStopSearching(false)
            }
            if (isMidSessionOrWorkoutActive()) {
                scheduleBackoffReconnect()
            }
        }
    }

    private fun releaseOnlineAccChannels() {
        PolarOfflineGattCoordinator.releaseAccChannel(polarId, PolarOfflineGattCoordinator.ACC_OWNER_ONLINE)
        activeSdkStreamId?.takeIf { !it.equals(polarId, ignoreCase = true) }?.let { sessionId ->
            PolarOfflineGattCoordinator.releaseAccChannel(sessionId, PolarOfflineGattCoordinator.ACC_OWNER_ONLINE)
        }
        coordinatorSessionKey?.takeIf { !it.equals(polarId, ignoreCase = true) }?.let { coordKey ->
            PolarOfflineGattCoordinator.releaseAccChannel(coordKey, PolarOfflineGattCoordinator.ACC_OWNER_ONLINE)
        }
    }

    private fun cancelPpiJob() {
        val toCancel = synchronized(ppiLifecycleLock) { ppiJob.also { ppiJob = null; isPpiStreamingActive.set(false) } }
        toCancel?.cancel()
    }

    private fun cancelActiveStreamJobs() {
        hrJob?.cancel()
        hrJob = null
        accJob?.cancel()
        accJob = null
        gyroJob?.cancel()
        gyroJob = null
        cancelPpiJob()
    }

    private fun markBridgeConnected(notifySearchSuccess: Boolean) {
        sessionOpenPendingFeatures.set(false)
        cancelSearchTimeoutWatchdog()
        cancelReconnectJob()
        hasConnectedInSession.set(true)
        currentReconnectDelayMs = SensorReconnectBackoffPolicy.INITIAL_DELAY_MS
        val wasConnected = isConnectedFlag.getAndSet(true)
        setLastActive()
        if (!wasConnected) {
            registerSensors()
        }
        syncCadenceSensorRegistration()
        activeBridges.addIfAbsent(this@PolarOnlineStreamingBridge)
        PolarRawTelemetryRecorder.getActiveRecorder()?.let { recorder ->
            addImuFrameListener(recorder)
            addRrBeatListener(recorder)
        }
        if (notifySearchSuccess && (!wasConnected || isSearching)) {
            notifyStopSearching(true)
        }
    }

    private val sdkCallback = object : PolarBleApiCallback() {
        override fun blePowerStateChanged(powered: Boolean) {
            bleEnabled.set(powered)
            if (!powered) {
                sessionOpenPendingFeatures.set(false)
                cancelReconnectJob()
                cancelSearchTimeoutWatchdog()
                isConnectedFlag.set(false)
                activeBridges.remove(this@PolarOnlineStreamingBridge)
                isAccStreamingActive.set(false)
                isGyroStreamingActive.set(false)
                cancelActiveStreamJobs()
                releaseOnlineAccChannels()
                unregisterSensors()
                PolarRawTelemetryRecorder.getActiveRecorder()?.let { recorder ->
                    removeImuFrameListener(recorder)
                    removeRrBeatListener(recorder)
                }
                if (isSearching) {
                    notifyStopSearching(false)
                }
            } else if (!isShutDown.get() && !isReceivingData() && isMidSessionOrWorkoutActive()) {
                scheduleBackoffReconnect()
            }
        }

        override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
            if (!PolarOfflineGattCoordinator.matchesDeviceInfo(polarId, macAddress, polarDeviceInfo) &&
                !PolarOfflineGattCoordinator.matchesDeviceInfo(activeSdkStreamId, macAddress, polarDeviceInfo) &&
                !PolarOfflineGattCoordinator.matchesDeviceInfo(coordinatorSessionKey, macAddress, polarDeviceInfo)
            ) {
                return
            }
            PolarOfflineCapabilityStore.recordDisMetadata(
                mContext,
                macAddress,
                serialNumber = polarDeviceInfo.deviceId
            )
            markBridgeConnected(notifySearchSuccess = true)
        }

        override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
            if (!PolarOfflineGattCoordinator.matchesDeviceInfo(polarId, macAddress, polarDeviceInfo) &&
                !PolarOfflineGattCoordinator.matchesDeviceInfo(activeSdkStreamId, macAddress, polarDeviceInfo) &&
                !PolarOfflineGattCoordinator.matchesDeviceInfo(coordinatorSessionKey, macAddress, polarDeviceInfo)
            ) {
                return
            }
            sessionOpenPendingFeatures.set(false)
            isConnectedFlag.set(false)
            activeBridges.remove(this@PolarOnlineStreamingBridge)
            isAccStreamingActive.set(false)
            isGyroStreamingActive.set(false)
            cancelActiveStreamJobs()
            releaseOnlineAccChannels()
            unregisterSensors()
            PolarRawTelemetryRecorder.getActiveRecorder()?.let { recorder ->
                removeImuFrameListener(recorder)
                removeRrBeatListener(recorder)
            }
            if (!isShutDown.get() && isMidSessionOrWorkoutActive()) {
                scheduleBackoffReconnect()
            }
        }

        override fun bleSdkFeatureReady(
            identifier: String,
            feature: PolarBleApi.PolarBleSdkFeature
        ) {
            if (!PolarOfflineGattCoordinator.matchesDeviceIdentifier(polarId, identifier) &&
                !PolarOfflineGattCoordinator.matchesDeviceIdentifier(activeSdkStreamId, identifier) &&
                !PolarOfflineGattCoordinator.matchesDeviceIdentifier(coordinatorSessionKey, identifier) &&
                !PolarOfflineGattCoordinator.matchesDeviceIdentifier(macAddress, identifier)
            ) {
                return
            }
            if (!isConnectedFlag.get() && sessionOpenPendingFeatures.get()) {
                val currentStreamId = activeSdkStreamId
                if (currentStreamId == null ||
                    (!currentStreamId.equals(identifier, ignoreCase = true) &&
                        isFeatureReadySafe(polarApi, identifier, feature) &&
                        !isFeatureReadySafe(polarApi, currentStreamId, feature))
                ) {
                    activeSdkStreamId = identifier
                    PolarOfflineGattCoordinator.registerDeviceAliases(identifier, macAddress)
                }
                markBridgeConnected(notifySearchSuccess = true)
            }
            when (feature) {
                PolarBleApi.PolarBleSdkFeature.FEATURE_HR -> startHrCollection()
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING -> {
                    PolarOfflineCapabilityStore.recordGattServiceCapabilities(
                        mContext,
                        macAddress,
                        hasPmdService = true,
                        hasPftpService = PolarOfflineCapabilityStore.hasPftpService(mContext, macAddress)
                    )
                    startImuCollection()
                }
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING -> {
                    PolarOfflineCapabilityStore.recordGattServiceCapabilities(
                        mContext,
                        macAddress,
                        hasPmdService = PolarOfflineCapabilityStore.hasPmdService(mContext, macAddress),
                        hasPftpService = true
                    )
                    PolarOfflineCapabilityStore.recordSdkDeviceType(
                        mContext,
                        macAddress,
                        PolarOfflineCapabilityStore.PolarDeviceType.H10,
                        identifier.takeIf { PolarOfflineCapabilityStore.isValidCanonicalPolarId(it) }
                    )
                    if (isConnectedFlag.get()) {
                        syncCadenceSensorRegistration()
                    }
                }
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING -> {
                    PolarOfflineCapabilityStore.recordGattServiceCapabilities(
                        mContext,
                        macAddress,
                        hasPmdService = PolarOfflineCapabilityStore.hasPmdService(mContext, macAddress),
                        hasPftpService = true
                    )
                    PolarOfflineCapabilityStore.recordSdkDeviceType(
                        mContext,
                        macAddress,
                        PolarOfflineCapabilityStore.PolarDeviceType.VERITY_SENSE,
                        identifier.takeIf { PolarOfflineCapabilityStore.isValidCanonicalPolarId(it) }
                    )
                    if (isConnectedFlag.get()) {
                        syncCadenceSensorRegistration()
                        maybeStartVerityStreamsIfReady()
                    }
                }
                else -> Unit
            }
        }

        override fun batteryLevelReceived(identifier: String, level: Int) {
            val matches = PolarOfflineGattCoordinator.matchesDeviceIdentifier(polarId, identifier) ||
                PolarOfflineGattCoordinator.matchesDeviceIdentifier(macAddress, identifier)
            if (matches && level in 0..100) {
                setBatteryPercentage(level)
            }
        }

        override fun disInformationReceived(identifier: String, uuid: UUID, value: String) {
            if (!PolarOfflineGattCoordinator.matchesDeviceIdentifier(polarId, identifier) &&
                !PolarOfflineGattCoordinator.matchesDeviceIdentifier(macAddress, identifier)
            ) {
                return
            }
            handleDisCharacteristic(uuid.toString(), value)
        }

        override fun disInformationReceived(identifier: String, disInfo: DisInfo) {
            if (!PolarOfflineGattCoordinator.matchesDeviceIdentifier(polarId, identifier) &&
                !PolarOfflineGattCoordinator.matchesDeviceIdentifier(macAddress, identifier)
            ) {
                return
            }
            handleDisCharacteristic(disInfo.key, disInfo.value)
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
        if (isReceivingData()) {
            notifyStopSearching(true)
            return
        }
        cancelReconnectJob()
        notifyStartSearching()
        PolarOfflineGattCoordinator.registerDeviceAliases(polarId, macAddress)
        armSearchTimeoutWatchdog()
        connectPersistentSession()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun connectPersistentSession() {
        try {
            val coordKey = coordinatorSessionKey ?: polarId.also { coordinatorSessionKey = it }
            val streamId = activeSdkStreamId ?: coordKey.also { activeSdkStreamId = it }
            var api = polarApi
            if (api == null) {
                api = PolarOfflineGattCoordinator.acquirePersistentSession(
                    context = mContext,
                    polarId = coordKey,
                    macAddress = macAddress,
                    callback = sdkCallback,
                    apiFactory = apiFactory
                )
                polarApi = api
                api.setAutomaticReconnection(true)
            }
            sessionOpenPendingFeatures.set(true)
            api.connectToDevice(streamId)
            syncAlreadyOpenSessionIfReady(api)
        } catch (t: Throwable) {
            sessionOpenPendingFeatures.set(false)
            Log.w(TAG, "Failed to start persistent Polar PMD session for $polarId: ${t.message}", t)
            cancelSearchTimeoutWatchdog()
            if (isSearching) {
                notifyStopSearching(false)
            }
            if (!isShutDown.get() && bleEnabled.get() && isMidSessionOrWorkoutActive()) {
                scheduleBackoffReconnect()
            }
        }
    }

    private fun isFeatureReadySafe(
        api: PolarBleApi?,
        identifier: String,
        feature: PolarBleApi.PolarBleSdkFeature
    ): Boolean {
        if (api == null || identifier.isBlank()) return false
        return try {
            api.isFeatureReady(identifier, feature)
        } catch (_: Throwable) {
            false
        }
    }

    private fun findReadySessionIdentifier(
        api: PolarBleApi,
        feature: PolarBleApi.PolarBleSdkFeature
    ): String? {
        val candidates = linkedSetOf<String>().apply {
            activeSdkStreamId?.takeIf { it.isNotBlank() }?.let(::add)
            coordinatorSessionKey?.takeIf { it.isNotBlank() }?.let(::add)
            polarId.takeIf { it.isNotBlank() }?.let(::add)
            macAddress.takeIf { it.isNotBlank() }?.let(::add)
        }
        for (candidate in candidates) {
            if (isFeatureReadySafe(api, candidate, feature)) return candidate
        }
        return null
    }

    /**
     * Synchronizes connection state and starts already-ready HR/IMU streams when attaching to an
     * already-open (`SESSION_OPEN`) session on the shared [PolarBleApi] singleton (`REQ-CON-050.2`).
     */
    private fun syncAlreadyOpenSessionIfReady(api: PolarBleApi) {
        if (isShutDown.get()) return
        val hrReadyId = findReadySessionIdentifier(api, PolarBleApi.PolarBleSdkFeature.FEATURE_HR)
        val streamingReadyId = findReadySessionIdentifier(api, PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING)
        val h10OfflineReadyId = findReadySessionIdentifier(api, PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING)
        val verityOfflineReadyId = findReadySessionIdentifier(api, PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING)

        val resolvedReadyId = hrReadyId ?: streamingReadyId ?: h10OfflineReadyId ?: verityOfflineReadyId
        val coordinatorConnected = PolarOfflineGattCoordinator.isDeviceConnected(coordinatorSessionKey) ||
            PolarOfflineGattCoordinator.isDeviceConnected(polarId) ||
            PolarOfflineGattCoordinator.isDeviceConnected(macAddress)

        if (resolvedReadyId == null && !coordinatorConnected) {
            return
        }

        if (resolvedReadyId != null && !resolvedReadyId.equals(activeSdkStreamId, ignoreCase = true)) {
            activeSdkStreamId = resolvedReadyId
            PolarOfflineGattCoordinator.registerDeviceAliases(resolvedReadyId, macAddress)
        }

        if (h10OfflineReadyId != null) {
            PolarOfflineCapabilityStore.recordGattServiceCapabilities(
                mContext,
                macAddress,
                hasPmdService = PolarOfflineCapabilityStore.hasPmdService(mContext, macAddress),
                hasPftpService = true
            )
            PolarOfflineCapabilityStore.recordSdkDeviceType(
                mContext,
                macAddress,
                PolarOfflineCapabilityStore.PolarDeviceType.H10,
                h10OfflineReadyId.takeIf { PolarOfflineCapabilityStore.isValidCanonicalPolarId(it) }
            )
        }
        if (verityOfflineReadyId != null) {
            PolarOfflineCapabilityStore.recordGattServiceCapabilities(
                mContext,
                macAddress,
                hasPmdService = PolarOfflineCapabilityStore.hasPmdService(mContext, macAddress),
                hasPftpService = true
            )
            PolarOfflineCapabilityStore.recordSdkDeviceType(
                mContext,
                macAddress,
                PolarOfflineCapabilityStore.PolarDeviceType.VERITY_SENSE,
                verityOfflineReadyId.takeIf { PolarOfflineCapabilityStore.isValidCanonicalPolarId(it) }
            )
        }

        markBridgeConnected(notifySearchSuccess = true)

        if (hrReadyId != null && hrJob?.isActive != true) {
            startHrCollection()
        }
        if (streamingReadyId != null) {
            PolarOfflineCapabilityStore.recordGattServiceCapabilities(
                mContext,
                macAddress,
                hasPmdService = true,
                hasPftpService = PolarOfflineCapabilityStore.hasPftpService(mContext, macAddress)
            )
            if (accJob?.isActive != true) {
                startImuCollection()
            } else if (isVeritySense && gyroJob?.isActive != true) {
                startGyroCollection(api)
            }
            if (isVeritySense && !isPpiStreamingActive.get() && ppiJob?.isActive != true) {
                startPpiCollection(api)
            }
        }
    }

    private fun startHrCollection() {
        val api = polarApi ?: return
        if (isShutDown.get()) return
        val sessionId = sdkSessionId
        hrJob?.cancel()
        hrJob = scope.launch {
            api.startHrStreaming(sessionId)
                .catch { e -> Log.w(TAG, "HR stream error on $sessionId: ${e.message}") }
                .collect { hrData ->
                    processHrData(hrData, System.currentTimeMillis())
                }
        }
    }

    private fun handleDisCharacteristic(key: String, value: String) {
        val lowerKey = key.trim().lowercase(Locale.ROOT)
        val trimmedValue = value.trim()
        if (trimmedValue.isEmpty()) return
        when {
            lowerKey == BluetoothConstants.UUID_CHARACTERISTIC_MODEL_NUMBER.toString() ||
                lowerKey.contains("2a24") ||
                lowerKey.contains("model") -> {
                PolarOfflineCapabilityStore.recordDisMetadata(mContext, macAddress, modelNumber = trimmedValue)
                if (isConnectedFlag.get()) {
                    syncCadenceSensorRegistration()
                    maybeStartVerityStreamsIfReady()
                }
            }
            lowerKey == BluetoothConstants.UUID_CHARACTERISTIC_SERIAL_NUMBER.toString() ||
                lowerKey.contains("2a25") ||
                lowerKey.contains("serial") -> {
                if (PolarOfflineCapabilityStore.isValidCanonicalPolarId(trimmedValue)) {
                    PolarOfflineCapabilityStore.recordDisMetadata(mContext, macAddress, serialNumber = trimmedValue)
                }
            }
        }
    }

    private fun maybeStartVerityStreamsIfReady() {
        val api = polarApi ?: return
        if (isShutDown.get() || !isVeritySense) return
        val streamingReady = findReadySessionIdentifier(
            api,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING
        ) != null
        if (!streamingReady) return
        if (!isGyroStreamingActive.get()) startGyroCollection(api)
        if (!isPpiStreamingActive.get()) startPpiCollection(api)
    }

    private fun startImuCollection() {
        val api = polarApi ?: return
        if (isShutDown.get()) return
        startAccCollection(api)
        if (isVeritySense) {
            startGyroCollection(api)
            startPpiCollection(api)
        }
    }

    /** Starts Verity Sense optical PPI streaming with atomic CAS re-entrancy & job-identity cleanup (`REQ-HRV-051.2`). */
    private fun startPpiCollection(api: PolarBleApi) {
        val sessionId = sdkSessionId
        val launchedJob = synchronized(ppiLifecycleLock) {
            if (isShutDown.get() || !isVeritySense || !isPpiStreamingActive.compareAndSet(false, true)) return
            scope.launch(start = CoroutineStart.LAZY) {
                val myJob = coroutineContext[Job]
                try {
                    if (!api.getAvailableOnlineStreamDataTypes(sessionId).contains(PolarBleApi.PolarDeviceDataType.PPI)) return@launch
                    api.startPpiStreaming(sessionId)
                        .catch { e -> Log.w(TAG, "PPI stream error on $sessionId: ${e.message}") }
                        .collect { processPpiData(it, System.currentTimeMillis()) }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to start/collect PPI stream on $sessionId: ${t.message}")
                } finally {
                    synchronized(ppiLifecycleLock) {
                        if (ppiJob === myJob) { ppiJob = null; isPpiStreamingActive.set(false) }
                    }
                }
            }.also { ppiJob = it }
        }
        launchedJob.start()
    }

    private fun startAccCollection(api: PolarBleApi) {
        val accOwnerKey = polarId
        if (!PolarOfflineGattCoordinator.tryAcquireAccChannel(accOwnerKey, PolarOfflineGattCoordinator.ACC_OWNER_ONLINE)) {
            Log.w(TAG, "Skipping online ACC stream on $accOwnerKey: ACC channel locked by offline recording.")
            return
        }
        val sessionId = sdkSessionId
        highRateRunningDynamicsActive = !isVeritySense && isRunSportActive()
        accJob?.cancel()
        accJob = scope.launch {
            try {
                val setting = resolveAccSetting(api, sessionId)
                isAccStreamingActive.set(true)
                api.startAccStreaming(sessionId, setting)
                    .catch { e -> Log.w(TAG, "ACC stream error on $sessionId: ${e.message}") }
                    .collect { accData ->
                        processAccData(accData)
                    }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to start/collect ACC stream on $sessionId: ${t.message}")
            } finally {
                isAccStreamingActive.set(false)
                PolarOfflineGattCoordinator.releaseAccChannel(accOwnerKey, PolarOfflineGattCoordinator.ACC_OWNER_ONLINE)
            }
        }
    }

    private fun startGyroCollection(api: PolarBleApi) {
        val sessionId = sdkSessionId
        gyroJob?.cancel()
        gyroJob = scope.launch {
            try {
                val setting = resolveGyroSetting(api, sessionId)
                isGyroStreamingActive.set(true)
                api.startGyroStreaming(sessionId, setting)
                    .catch { e -> Log.w(TAG, "GYRO stream error on $sessionId: ${e.message}") }
                    .collect { gyroData ->
                        processGyroData(gyroData)
                    }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to start/collect GYRO stream on $sessionId: ${t.message}")
            } finally {
                isGyroStreamingActive.set(false)
            }
        }
    }

    private suspend fun resolveAccSetting(api: PolarBleApi, sessionId: String): PolarSensorSetting {
        val available = api.requestStreamSettings(sessionId, PolarBleApi.PolarDeviceDataType.ACC)
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

    private suspend fun resolveGyroSetting(api: PolarBleApi, sessionId: String): PolarSensorSetting {
        val available = api.requestStreamSettings(sessionId, PolarBleApi.PolarDeviceDataType.GYRO)
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
                for (listener in imuFrameListeners) {
                    try {
                        listener.onHrSample(polarId, sample.hr, packetArrivalMs * NS_PER_MS)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error in ImuFrameListener.onHrSample: ${e.message}")
                    }
                }
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
        if (rrListMs.isEmpty()) return

        val bundle = RrBeatNormalization.reconstructBundle(
            rrListMs = rrListMs.map { it.toDouble() },
            packetArrivalMs = packetArrivalMs,
            previousBeatTimestampMs = lastBeatTimestampMs,
            previousNormalRrMs = lastNormalRrMs,
            packetCounter = packetCounter,
            protocol = ProtocolType.BLE
        )
        for (event in bundle.events) {
            mRrIntervalSensor?.newValue(Math.round(event.rrMs).toInt())
            dispatchRrBeat(event)
        }
        lastBeatTimestampMs = bundle.lastBeatTimestampMs
        lastNormalRrMs = bundle.lastNormalRrMs
    }

    /** Processes Verity Sense optical [PolarPpiData] packets via [PolarPpiStreamProcessor] (`REQ-HRV-051.1`). */
    fun processPpiData(data: PolarPpiData, packetArrivalMs: Long) {
        if (isShutDown.get()) return
        val events = ppiStreamProcessor.processSamples(data.samples, packetArrivalMs)
        for (event in events) {
            if (event.artifactFlag == RrBeatEvent.ARTIFACT_NORMAL) {
                val rrSensor = mRrIntervalSensor ?: MySensor<Int>(this, SensorType.RR_INTERVAL).also {
                    mRrIntervalSensor = it
                    addAndRegisterSensor(it)
                }
                rrSensor.newValue(Math.round(event.rrMs).toInt())
            }
            dispatchRrBeat(event)
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
            val estimator = getCadenceEstimator()
            for (sample in data.samples) {
                val timestampMs = if (sample.timeStamp > 0L) sample.timeStamp / NS_PER_MS else 0L
                estimator.onAccSample(sample.x, sample.y, sample.z, timestampMs)
                if (!isVeritySense) {
                    val cadenceStepsPerMin = estimator.latestCadenceStridesPerMin * 2.0
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
            val estimator = getCadenceEstimator()
            for (sample in data.samples) {
                val timestampMs = if (sample.timeStamp > 0L) sample.timeStamp / NS_PER_MS else 0L
                estimator.onGyroSample(sample.x, sample.y, sample.z, timestampMs)
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
            sensor.deactivateSensor()
            mMySensorManager?.unregisterSensor(sensor)
            removeSensor(SensorType.CADENCE)
            mCadenceSensor = null
        }
        mStridesSensor?.let { sensor ->
            sensor.deactivateSensor()
            mMySensorManager?.unregisterSensor(sensor)
            removeSensor(SensorType.STRIDES)
            mStridesSensor = null
        }
        mVerticalOscillationSensor?.let { sensor ->
            sensor.deactivateSensor()
            mMySensorManager?.unregisterSensor(sensor)
            removeSensor(SensorType.VERTICAL_OSCILLATION)
            mVerticalOscillationSensor = null
        }
        mGroundContactTimeSensor?.let { sensor ->
            sensor.deactivateSensor()
            mMySensorManager?.unregisterSensor(sensor)
            removeSensor(SensorType.GROUND_CONTACT_TIME)
            mGroundContactTimeSensor = null
        }
        synchronized(cadenceEstimatorLock) {
            cadenceEstimatorInstance?.reset()
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
        perDeviceHrvController.onRrBeat(event)
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

    @VisibleForTesting
    fun isPpiStreamingForTesting(): Boolean = isPpiStreamingActive.get()

    @VisibleForTesting
    fun getSearchTimeoutJobForTesting(): Job? = searchTimeoutJob

    @VisibleForTesting
    fun getReconnectJobForTesting(): Job? = reconnectJob

    @VisibleForTesting
    fun getCurrentReconnectDelayMsForTesting(): Long = currentReconnectDelayMs

    @VisibleForTesting
    fun getPolarBleSessionIdForTesting(): String? = activeSdkStreamId

    @VisibleForTesting
    fun getCoordinatorSessionKeyForTesting(): String? = coordinatorSessionKey

    @VisibleForTesting
    fun isHrJobActiveForTesting(): Boolean = hrJob?.isActive == true

    @VisibleForTesting
    fun isAccJobActiveForTesting(): Boolean = accJob?.isActive == true

    @VisibleForTesting
    fun isGyroJobActiveForTesting(): Boolean = gyroJob?.isActive == true

    @VisibleForTesting
    fun isPpiJobActiveForTesting(): Boolean = ppiJob?.isActive == true

    override fun onAccumulatorsReset() {
        super.onAccumulatorsReset()
        ppiStreamProcessor.reset()
        perDeviceHrvController.onAccumulatorsReset()
    }

    override fun unregisterSensors() {
        perDeviceHrvController.onDisconnect()
        unregisterImuCadenceSensors()
        mRrIntervalSensor?.let { sensor ->
            sensor.deactivateSensor()
            mMySensorManager?.unregisterSensor(sensor)
            removeSensor(SensorType.RR_INTERVAL)
            mRrIntervalSensor = null
        }
        super.unregisterSensors()
        lastBeatTimestampMs = 0.0
        lastNormalRrMs = RrBeatNormalization.DEFAULT_REFERENCE_RR_MS
        packetCounter = 0
        ppiStreamProcessor.reset()
    }

    override fun shutDown() {
        shutDown(null)
    }

    override fun shutDown(onTeardownComplete: Runnable?) {
        if (!isShutDown.compareAndSet(false, true)) {
            onTeardownComplete?.run()
            return
        }
        sessionOpenPendingFeatures.set(false)
        hasConnectedInSession.set(false)
        cancelSearchTimeoutWatchdog()
        cancelReconnectJob()
        isConnectedFlag.set(false)
        isAccStreamingActive.set(false)
        isGyroStreamingActive.set(false)

        LifecycleUtils.safeUnregisterReceiver(mContext, sportChangedReceiver, TAG)

        cancelActiveStreamJobs()

        val streamId = sdkSessionId
        val coordKey = coordinatorSessionKey ?: streamId
        activeSdkStreamId = null
        coordinatorSessionKey = null
        val api = polarApi
        polarApi = null
        if (api != null) {
            try {
                api.stopStreaming(streamId, PmdMeasurementType.ACC)
            } catch (_: Throwable) {
            }
            if (isVeritySense) {
                try {
                    api.stopStreaming(streamId, PmdMeasurementType.GYRO)
                } catch (_: Throwable) {
                }
                try {
                    api.stopStreaming(streamId, PmdMeasurementType.PPI)
                } catch (_: Throwable) {
                }
            }
            releaseOnlineAccChannels()
            PolarOfflineGattCoordinator.releaseAccChannel(streamId, PolarOfflineGattCoordinator.ACC_OWNER_ONLINE)
            PolarOfflineGattCoordinator.releaseAccChannel(coordKey, PolarOfflineGattCoordinator.ACC_OWNER_ONLINE)
            try {
                api.disconnectFromDevice(streamId)
            } catch (_: Throwable) {
            }
            PolarOfflineGattCoordinator.releasePersistentSession(
                polarId = coordKey,
                macAddress = macAddress,
                api = api,
                callback = sdkCallback
            )
        } else {
            releaseOnlineAccChannels()
            PolarOfflineGattCoordinator.releaseAccChannel(streamId, PolarOfflineGattCoordinator.ACC_OWNER_ONLINE)
            PolarOfflineGattCoordinator.releaseAccChannel(coordKey, PolarOfflineGattCoordinator.ACC_OWNER_ONLINE)
        }

        activeBridges.remove(this)
        perDeviceHrvController.shutDown()
        unregisterSensors()
        rrBeatListeners.clear()
        imuFrameListeners.clear()
        scope.cancel()
        super.shutDown()
        onTeardownComplete?.run()
    }
}
