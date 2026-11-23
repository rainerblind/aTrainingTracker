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
import com.atrainingtracker.banalservice.devices.bluetooth_le.BleHrmSilentConnectionWatchdog
import com.atrainingtracker.banalservice.devices.hrv.HrvSignalQualityResult
import com.atrainingtracker.banalservice.devices.hrv.HrvTelemetryProvider
import com.atrainingtracker.banalservice.devices.hrv.PerDeviceHrvController
import com.atrainingtracker.banalservice.sensor.HrHealthTier
import com.atrainingtracker.banalservice.sensor.HrModality
import com.atrainingtracker.banalservice.sensor.HrSensorHealthProvider
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
) : MyRemoteDevice(context, mySensorManager, DeviceType.HRM, deviceId),
    RrBeatSource,
    HrvTelemetryProvider,
    HrSensorHealthProvider {

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
        internal const val DEFAULT_CONNECTING_HANDSHAKE_TIMEOUT_MS = 15_000L

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
    private val reconnectLock = Any()
    private val gattActionLock = Any()
    private val streamCoordinator = PolarPmdStreamCoordinator(
        scope = scope,
        deviceId = deviceId,
        deviceNameProvider = { deviceNameStr },
        polarIdProvider = { polarId },
        macAddress = macAddress,
        isConnectedProvider = { isConnectedFlag.get() },
        isShutDownProvider = { isShutDown.get() }
    )

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

    private val ppiStreamProcessor = PolarPpiStreamProcessor()

    @Volatile
    var highRateRunningDynamicsActive: Boolean = false

    @Volatile
    private var availableAccRates: Set<Int> = emptySet()

    @Volatile
    private var activeAccSampleRateHz: Int? = null

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
            syncVerityPpiForTrackingMode()
        }
    }

    init {
        PolarOfflineCapabilityStore.getPolarDeviceId(mContext, macAddress)?.let { persistedPolarId ->
            PolarOfflineGattCoordinator.registerDeviceAliases(persistedPolarId, macAddress)
        }
        try {
            val sportFilter = IntentFilter(BANALService.SPORT_TYPE_CHANGED_BY_USER_INTENT).apply {
                addAction(BANALService.MULTISPORT_TRANSITION_INTENT)
                addAction(TrainingApplication.TRACKING_STATE_CHANGED)
            }
            ContextCompat.registerReceiver(mContext, sportChangedReceiver, sportFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } catch (_: Throwable) {
            // Ignored in minimal unit test contexts without receiver registration.
        }
    }

    private val searchTimeoutLock = Any()
    @Volatile private var searchTimeoutJob: Job? = null
    private var searchAttemptGeneration: Long = 0L
    private var handshakeGraceExtendedInAttempt: Boolean = false
    @VisibleForTesting internal var connectingHandshakeTimeoutMs: Long = DEFAULT_CONNECTING_HANDSHAKE_TIMEOUT_MS
    private val resumeSearchAfterOfflineSync = AtomicBoolean(false)
    private val resumeSilentWatchdogAfterOfflineSync = AtomicBoolean(false)
    private val resumeAccAfterOfflineSync = AtomicBoolean(false)

    private val silentHrWatchdog = BleHrmSilentConnectionWatchdog()
    private val silentHrWatchdogTimer = PolarPmdStreamCoordinator.OneShotTimer(scope)
    @VisibleForTesting internal var silentHrWatchdogTimeoutMs: Long = BleHrmSilentConnectionWatchdog.DEFAULT_WATCHDOG_TIMEOUT_MS

    private val hrHealthMonitor = PolarHrStreamHealthMonitor(isOpticalSensorProvider = { isVeritySense })
    private val hrStalenessTimer = PolarPmdStreamCoordinator.OneShotTimer(scope)

    override fun getHrHealthTier(nowMs: Long): HrHealthTier = hrHealthMonitor.getHrHealthTier(nowMs)

    override fun getHrModality(): HrModality = if (isVeritySense) HrModality.OPTICAL_PPG else HrModality.ECG

    private fun matchesBridgeIdentifier(candidate: String?): Boolean =
        PolarOfflineGattCoordinator.matchesDeviceIdentifier(polarId, candidate) ||
            PolarOfflineGattCoordinator.matchesDeviceIdentifier(macAddress, candidate) ||
            PolarOfflineGattCoordinator.matchesDeviceIdentifier(activeSdkStreamId, candidate) ||
            PolarOfflineGattCoordinator.matchesDeviceIdentifier(coordinatorSessionKey, candidate)

    private val offlineYieldListener = object : PolarOfflineGattCoordinator.OfflineYieldListener {
        override fun matchesDevice(polarId: String?, macAddress: String?): Boolean =
            matchesBridgeIdentifier(polarId) || matchesBridgeIdentifier(macAddress)
        override fun onOfflineYieldStarted() = pauseForOfflineSync()
        override fun onOfflineYieldFinished() = resumeAfterOfflineSync()
    }

    init { PolarOfflineGattCoordinator.registerOfflineYieldListener(offlineYieldListener) }

    internal fun isOfflineGattYielded(): Boolean =
        PolarOfflineGattCoordinator.isOfflineGattYieldActive(polarId, macAddress, activeSdkStreamId, coordinatorSessionKey)

    /** Pauses reconnect, search-timeout teardown, and silent-HR watchdog during offline PFTP sync (`ATT-9175`, `REQ-SET-120.2`). */
    internal fun pauseForOfflineSync() {
        if (isShutDown.get() || !isOfflineGattYielded()) return
        if (isSearching) resumeSearchAfterOfflineSync.set(true)
        if (isConnectedFlag.get() && silentHrWatchdog.getState() != BleHrmSilentConnectionWatchdog.State.DISARMED) {
            resumeSilentWatchdogAfterOfflineSync.set(true)
        }
        cancelReconnectJob()
        cancelSearchTimeoutWatchdog()
        cancelSilentHrWatchdog(resetState = true)
        cancelHrStalenessTimer()
        if (PolarPmdStreamCoordinator.StreamSlot.ACC in streamCoordinator.cancelAllRenegotiations()) {
            resumeAccAfterOfflineSync.set(true)
        }
        synchronized(gattActionLock) {} // Linearizable quiescence barrier for in-flight GATT teardowns/connects (`REQ-SET-129.1`).
    }

    /** Resumes session sync, silent-HR watchdog, or reconnect cleanly after the final offline GATT lease releases (`REQ-SET-120.2`). */
    internal fun resumeAfterOfflineSync() {
        if (isShutDown.get() || isOfflineGattYielded()) return
        if (isConnectedFlag.get()) {
            val needsWatchdog = resumeSilentWatchdogAfterOfflineSync.getAndSet(false) ||
                silentHrWatchdogTimer.currentJob() == null
            if (needsWatchdog && silentHrWatchdog.getState() != BleHrmSilentConnectionWatchdog.State.DISARMED) {
                armSilentHrWatchdog()
            }
            if (resumeAccAfterOfflineSync.getAndSet(false)) {
                if (!streamCoordinator.isActive(PolarPmdStreamCoordinator.StreamSlot.ACC)) polarApi?.let(::startAccCollection)
                else syncCadenceSensorRegistration()
            }
            scheduleNextHrStalenessCheck(System.currentTimeMillis())
            return
        }
        resumeSilentWatchdogAfterOfflineSync.set(false)
        resumeAccAfterOfflineSync.set(false)
        polarApi?.let { syncAlreadyOpenSessionIfReady(it) }
        if (isConnectedFlag.get()) {
            resumeSearchAfterOfflineSync.set(false)
            if (silentHrWatchdog.getState() != BleHrmSilentConnectionWatchdog.State.DISARMED &&
                silentHrWatchdogTimer.currentJob() == null
            ) armSilentHrWatchdog()
            scheduleNextHrStalenessCheck(System.currentTimeMillis())
            return
        }
        if (resumeSearchAfterOfflineSync.getAndSet(false)) {
            startSearching()
        } else if (bleEnabled.get() && (isMidSessionOrWorkoutActive() || SensorSearchSettings.isPeriodicResearchEnabled(mContext))) {
            scheduleBackoffReconnect()
        }
    }

    private fun detachBridgeSessionState() {
        sessionOpenPendingFeatures.set(false)
        resumeAccAfterOfflineSync.set(false)
        isConnectedFlag.set(false)
        cancelHrStalenessTimer()
        activeBridges.remove(this@PolarOnlineStreamingBridge)
        cancelActiveStreamJobs()
        streamCoordinator.resetSessionWarningState(polarId, activeSdkStreamId, macAddress)
        unregisterSensors()
        PolarRawTelemetryRecorder.getActiveRecorder()?.let { recorder ->
            removeImuFrameListener(recorder)
            removeRrBeatListener(recorder)
        }
    }

    private fun scheduleNextHrStalenessCheck(nowMs: Long) {
        hrHealthMonitor.nextCheckDelayMs(nowMs)?.let(::scheduleHrStalenessTimer)
    }

    private fun scheduleHrStalenessTimer(delayMs: Long) {
        hrStalenessTimer.schedule(
            delayMs = delayMs,
            shouldArm = { !isShutDown.get() && isConnectedFlag.get() && !isOfflineGattYielded() },
            onElapsed = { evaluateHrStaleness(System.currentTimeMillis()) }
        )
    }

    private fun cancelHrStalenessTimer() = hrStalenessTimer.cancel()

    private fun effectiveOpticalArtifactRatePercent(mode: TrackingMode): Double? =
        if (isVeritySense && !PolarHrStreamHealthMonitor.shouldStreamVerityPpi(true, mode)) null else latestArtifactRatePercent

    private fun evaluateHrStaleness(nowMs: Long) {
        if (isShutDown.get() || !isConnectedFlag.get() || isOfflineGattYielded()) return
        val mode = TrainingApplication.getTrackingMode()
        val decision = hrHealthMonitor.evaluateStaleness(
            nowMs = nowMs,
            isWorkoutActive = PolarHrStreamHealthMonitor.isWorkoutActive(mode),
            opticalArtifactRatePercent = effectiveOpticalArtifactRatePercent(mode)
        )
        if (decision.shouldClearHr) mHeartRateSensor.newValue(null)
        if (decision.shouldClearRr) mRrIntervalSensor?.newValue(null)
        if (decision.healthTierChanged) mMySensorManager?.reevaluatePrimaryHrSensor()
        if (decision.shouldTriggerRecovery) recoverHrStream()
        if (!isShutDown.get() && isConnectedFlag.get() && !isOfflineGattYielded()) {
            scheduleNextHrStalenessCheck(nowMs)
        }
    }

    private fun recoverHrStream() {
        if (isShutDown.get() || !isConnectedFlag.get() || isOfflineGattYielded()) return
        Log.w(TAG, "Recovering degraded/stale Polar HR stream on $sdkSessionId")
        if (isVeritySense) stopVerityPpiAndClearLatchedRr(forceStopFirmwarePpi = true)
        streamCoordinator.cancelSlot(PolarPmdStreamCoordinator.StreamSlot.HR)
        if (isShutDown.get() || !isConnectedFlag.get() || isOfflineGattYielded()) return
        startHrCollection()
    }

    @VisibleForTesting
    internal fun triggerHrStalenessCheckForTesting(nowMs: Long) = evaluateHrStaleness(nowMs)

    @VisibleForTesting
    internal fun getHrStalenessJobForTesting(): Job? = hrStalenessTimer.currentJob()

    @VisibleForTesting
    internal fun syncVerityPpiForTrackingModeForTesting() = syncVerityPpiForTrackingMode()

    @VisibleForTesting
    internal fun getSilentHrWatchdogGenerationForTesting(): Long = silentHrWatchdog.getGeneration()

    @VisibleForTesting
    internal fun getSilentHrWatchdogJobForTesting(): Job? = silentHrWatchdogTimer.currentJob()

    @VisibleForTesting
    internal fun triggerSilentHrWatchdogTimeoutForTesting(expectedGeneration: Long) {
        onSilentHrWatchdogTimeout(expectedGeneration)
    }

    private fun armSilentHrWatchdog() {
        val generation = silentHrWatchdog.armOnConnected()
        scheduleSilentHrWatchdogTimer(generation)
    }

    private fun scheduleSilentHrWatchdogTimer(expectedGeneration: Long) {
        if (isShutDown.get() || !isConnectedFlag.get() || silentHrWatchdog.getGeneration() != expectedGeneration) return
        if (isOfflineGattYielded()) {
            resumeSilentWatchdogAfterOfflineSync.set(true)
            return
        }
        silentHrWatchdogTimer.schedule(
            delayMs = silentHrWatchdogTimeoutMs,
            shouldArm = {
                !isShutDown.get() && isConnectedFlag.get() && !isOfflineGattYielded() &&
                    silentHrWatchdog.getGeneration() == expectedGeneration
            },
            onElapsed = { onSilentHrWatchdogTimeout(expectedGeneration) }
        )
    }

    private fun cancelSilentHrWatchdog(resetState: Boolean = true) {
        silentHrWatchdogTimer.cancel { if (resetState) silentHrWatchdog.reset() }
    }

    private fun onSilentHrWatchdogTimeout(expectedGeneration: Long) {
        if (isShutDown.get() || !isConnectedFlag.get() || isOfflineGattYielded() || silentHrWatchdog.getGeneration() != expectedGeneration) return
        when (silentHrWatchdog.onWatchdogElapsed(expectedGeneration)) {
            BleHrmSilentConnectionWatchdog.Action.REENABLE_CCCD -> {
                if (isShutDown.get() || !isConnectedFlag.get() || silentHrWatchdog.getGeneration() != expectedGeneration) return
                Log.w(TAG, "Silent Polar HR connection on $sdkSessionId (${silentHrWatchdogTimeoutMs}ms without HR packets); re-subscribing HR stream")
                streamCoordinator.cancelSlot(PolarPmdStreamCoordinator.StreamSlot.HR)
                if (isShutDown.get() || !isConnectedFlag.get() || silentHrWatchdog.getGeneration() != expectedGeneration) return
                startHrCollection()
                scheduleSilentHrWatchdogTimer(expectedGeneration)
            }
            BleHrmSilentConnectionWatchdog.Action.DISCONNECT_AND_RECONNECT -> {
                val disconnected = synchronized(gattActionLock) {
                    if (isShutDown.get() || !isConnectedFlag.get() || silentHrWatchdog.getGeneration() != expectedGeneration) return
                    if (isOfflineGattYielded()) { resumeSilentWatchdogAfterOfflineSync.set(true); return }
                    Log.w(TAG, "Persistent silent Polar HR connection on $sdkSessionId after HR re-subscribe; disconnecting aliases and reconnecting")
                    cancelSilentHrWatchdog(resetState = true); detachBridgeSessionState()
                    val sessionId = sdkSessionId
                    PolarOfflineGattCoordinator.registerDeviceAliases(coordinatorSessionKey, activeSdkStreamId, polarId, macAddress)
                    PolarOfflineGattCoordinator.forceDisconnectDevice(mContext, macAddress, sessionId, clearRegistrations = false)
                    true
                }
                if (disconnected && !isShutDown.get() && !isOfflineGattYielded()) scheduleBackoffReconnect()
            }
            BleHrmSilentConnectionWatchdog.Action.NONE -> Unit
        }
    }

    private fun armSearchTimeoutWatchdog(
        timeoutMs: Long = searchTimeoutMsProvider(),
        resetHandshakeGrace: Boolean = true
    ) {
        val previous = synchronized(searchTimeoutLock) {
            val existing = searchTimeoutJob
            val generation = ++searchAttemptGeneration
            if (isShutDown.get() || isOfflineGattYielded() || isConnectedFlag.get()) {
                searchTimeoutJob = null
                handshakeGraceExtendedInAttempt = false
            } else {
                if (resetHandshakeGrace) handshakeGraceExtendedInAttempt = false
                searchTimeoutJob = scope.launch {
                    try {
                        delay(timeoutMs)
                        synchronized(searchTimeoutLock) {
                            if (coroutineContext[Job] === searchTimeoutJob) searchTimeoutJob = null
                        }
                        onSearchTimeout(generation, timeoutMs)
                    } finally {
                        synchronized(searchTimeoutLock) {
                            if (coroutineContext[Job] === searchTimeoutJob) searchTimeoutJob = null
                        }
                    }
                }
            }
            existing
        }
        previous?.cancel()
    }

    private fun cancelSearchTimeoutWatchdog() {
        val previous = synchronized(searchTimeoutLock) {
            val existing = searchTimeoutJob
            searchTimeoutJob = null
            searchAttemptGeneration++
            handshakeGraceExtendedInAttempt = false
            existing
        }
        previous?.cancel()
    }

    private fun cancelReconnectJob() {
        val previous = synchronized(reconnectLock) {
            val existing = reconnectJob
            reconnectJob = null
            existing
        }
        previous?.cancel()
    }

    private fun isMidSessionOrWorkoutActive(): Boolean {
        val mode = TrainingApplication.getTrackingMode()
        return hasConnectedInSession.get() ||
            mode == TrackingMode.TRACKING ||
            mode == TrackingMode.PAUSED
    }

    override fun shouldKeepAliveOnSearchTimeout(): Boolean =
        !isShutDown.get() && (isMidSessionOrWorkoutActive() || isOfflineGattYielded())

    @SuppressLint("NewApi")
    private fun scheduleBackoffReconnect() {
        if (isShutDown.get() || !bleEnabled.get() || isOfflineGattYielded() || sdkIntProvider() < Build.VERSION_CODES.TIRAMISU) return
        if (!isMidSessionOrWorkoutActive() && !SensorSearchSettings.isPeriodicResearchEnabled(mContext)) return
        synchronized(reconnectLock) {
            if (isShutDown.get() || !bleEnabled.get() || isOfflineGattYielded() || isReceivingData()) return
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
                    if (isShutDown.get() || !bleEnabled.get() || isOfflineGattYielded() || isReceivingData()) return@launch
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

    private fun onSearchTimeout(expectedGeneration: Long, timeoutMs: Long) {
        var yieldBailedWhileSearching = false
        val timedOut = synchronized(gattActionLock) {
            if (isShutDown.get()) return
            if (isOfflineGattYielded()) { yieldBailedWhileSearching = isSearching; false } else {
                if (!synchronized(searchTimeoutLock) { searchAttemptGeneration == expectedGeneration } || isReceivingData()) return
                val sessionId = sdkSessionId
                Log.i(TAG, "Search timed out for Polar sensor $sessionId after $timeoutMs ms")
                try { polarApi?.disconnectFromDevice(sessionId) } catch (t: Throwable) {
                    Log.w(TAG, "Error disconnecting Polar API on search timeout for $sessionId: ${t.message}")
                }
                PolarOfflineGattCoordinator.clearConnectingState(sessionId, macAddress)
                true
            }
        }
        if (yieldBailedWhileSearching) { resumeSearchAfterOfflineSync.set(true); if (isSearching) notifyStopSearching(false); return }
        if (!timedOut) return
        if (isSearching) notifyStopSearching(false)
        if (isMidSessionOrWorkoutActive() && !isOfflineGattYielded()) scheduleBackoffReconnect()
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

    private fun cancelActiveStreamJobs() {
        activeAccSampleRateHz = null
        availableAccRates = emptySet()
        streamCoordinator.cancelAllStreams { releaseOnlineAccChannels() }
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
            streamCoordinator.resetSessionWarningState(polarId, activeSdkStreamId, macAddress)
            registerSensors()
            armSilentHrWatchdog()
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

    private fun matchesBridgeDeviceInfo(info: PolarDeviceInfo): Boolean =
        PolarOfflineGattCoordinator.matchesDeviceInfo(polarId, macAddress, info) ||
            PolarOfflineGattCoordinator.matchesDeviceInfo(activeSdkStreamId, macAddress, info) ||
            PolarOfflineGattCoordinator.matchesDeviceInfo(coordinatorSessionKey, macAddress, info)

    private fun recordPolarDeviceCapability(type: PolarOfflineCapabilityStore.PolarDeviceType, identifier: String) {
        PolarOfflineCapabilityStore.recordGattServiceCapabilities(
            mContext,
            macAddress,
            hasPmdService = PolarOfflineCapabilityStore.hasPmdService(mContext, macAddress),
            hasPftpService = true
        )
        PolarOfflineCapabilityStore.recordSdkDeviceType(
            mContext,
            macAddress,
            type,
            identifier.takeIf { PolarOfflineCapabilityStore.isValidCanonicalPolarId(it) }
        )
    }

    private val sdkCallback = object : PolarBleApiCallback() {
        override fun blePowerStateChanged(powered: Boolean) {
            bleEnabled.set(powered)
            if (!powered) {
                cancelReconnectJob()
                cancelSearchTimeoutWatchdog()
                cancelSilentHrWatchdog(resetState = true)
                detachBridgeSessionState()
                if (isSearching) notifyStopSearching(false)
            } else if (!isShutDown.get() && !isReceivingData() && !isOfflineGattYielded() && isMidSessionOrWorkoutActive()) {
                scheduleBackoffReconnect()
            }
        }

        override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
            if (!matchesBridgeDeviceInfo(polarDeviceInfo) || isShutDown.get() || isConnectedFlag.get() || isOfflineGattYielded()) return
            val shouldExtend = synchronized(searchTimeoutLock) {
                if (searchTimeoutJob?.isActive == true && !handshakeGraceExtendedInAttempt) {
                    handshakeGraceExtendedInAttempt = true
                    true
                } else {
                    false
                }
            }
            if (shouldExtend) {
                val graceMs = maxOf(searchTimeoutMsProvider(), connectingHandshakeTimeoutMs)
                Log.i(TAG, "Polar sensor ${polarDeviceInfo.deviceId} connecting; extending handshake timeout to ${graceMs}ms")
                armSearchTimeoutWatchdog(timeoutMs = graceMs, resetHandshakeGrace = false)
            }
        }

        override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
            if (!matchesBridgeDeviceInfo(polarDeviceInfo)) return
            PolarOfflineCapabilityStore.recordDisMetadata(
                mContext,
                macAddress,
                serialNumber = polarDeviceInfo.deviceId
            )
            markBridgeConnected(notifySearchSuccess = true)
        }

        override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
            if (!matchesBridgeDeviceInfo(polarDeviceInfo)) return
            cancelSilentHrWatchdog(resetState = true)
            detachBridgeSessionState()
            if (!isShutDown.get() && !isOfflineGattYielded() && isMidSessionOrWorkoutActive()) {
                scheduleBackoffReconnect()
            }
        }

        override fun bleSdkFeatureReady(
            identifier: String,
            feature: PolarBleApi.PolarBleSdkFeature
        ) {
            if (!matchesBridgeIdentifier(identifier)) return
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
                    recordPolarDeviceCapability(PolarOfflineCapabilityStore.PolarDeviceType.H10, identifier)
                    if (isConnectedFlag.get()) syncCadenceSensorRegistration()
                }
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING -> {
                    recordPolarDeviceCapability(PolarOfflineCapabilityStore.PolarDeviceType.VERITY_SENSE, identifier)
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
            if (!matchesBridgeIdentifier(identifier)) return
            handleDisCharacteristic(uuid.toString(), value)
        }

        override fun disInformationReceived(identifier: String, disInfo: DisInfo) {
            if (!matchesBridgeIdentifier(identifier)) return
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
        if (isOfflineGattYielded()) {
            resumeSearchAfterOfflineSync.set(true)
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
            var yieldBailed = false
            val connectedIssued = synchronized(gattActionLock) {
                if (isShutDown.get()) return
                if (isOfflineGattYielded()) { yieldBailed = true; false } else {
                    sessionOpenPendingFeatures.set(true); api.connectToDevice(streamId); true
                }
            }
            if (yieldBailed) { if (isSearching) { resumeSearchAfterOfflineSync.set(true); notifyStopSearching(false) }; return }
            if (connectedIssued) syncAlreadyOpenSessionIfReady(api)
        } catch (t: Throwable) {
            sessionOpenPendingFeatures.set(false)
            Log.w(TAG, "Failed to start persistent Polar PMD session for $polarId: ${t.message}", t)
            cancelSearchTimeoutWatchdog()
            if (isSearching) {
                notifyStopSearching(false)
            }
            if (!isShutDown.get() && bleEnabled.get() && !isOfflineGattYielded() && isMidSessionOrWorkoutActive()) {
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

        h10OfflineReadyId?.let { recordPolarDeviceCapability(PolarOfflineCapabilityStore.PolarDeviceType.H10, it) }
        verityOfflineReadyId?.let { recordPolarDeviceCapability(PolarOfflineCapabilityStore.PolarDeviceType.VERITY_SENSE, it) }

        markBridgeConnected(notifySearchSuccess = true)

        if (hrReadyId != null) {
            startHrCollection()
        }
        if (streamingReadyId != null) {
            PolarOfflineCapabilityStore.recordGattServiceCapabilities(
                mContext,
                macAddress,
                hasPmdService = true,
                hasPftpService = PolarOfflineCapabilityStore.hasPftpService(mContext, macAddress)
            )
            startImuCollection()
        }
    }

    private fun startHrCollection() {
        val api = polarApi ?: return
        val sessionId = sdkSessionId
        streamCoordinator.launchStream(PolarPmdStreamCoordinator.StreamSlot.HR) {
            streamCoordinator.collectBounded(api.startHrStreaming(sessionId)) { hrData ->
                processHrData(hrData, System.currentTimeMillis())
            }
        }
    }

    private fun handleDisCharacteristic(key: String, value: String) {
        if (PolarPmdStreamCoordinator.recordDisCharacteristic(mContext, macAddress, key, value) && isConnectedFlag.get()) {
            syncCadenceSensorRegistration()
            maybeStartVerityStreamsIfReady()
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
        startGyroCollection(api)
        startPpiCollection(api)
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

    /** Starts Verity Sense optical PPI streaming only in resting modes (`REQ-HRV-051.2`, `REQ-CON-053`, `REQ-SET-126.1`). */
    private fun startPpiCollection(api: PolarBleApi) {
        if (!isVeritySense) return
        if (!PolarHrStreamHealthMonitor.shouldStreamVerityPpi(true, TrainingApplication.getTrackingMode())) {
            // Explicitly stop any orphaned firmware PPI stream left running across a crash/reconnect during workout.
            stopVerityPpiAndClearLatchedRr(forceStopFirmwarePpi = true)
            return
        }
        val sessionId = sdkSessionId
        streamCoordinator.launchStream(
            slot = PolarPmdStreamCoordinator.StreamSlot.PPI,
            preAcquire = { PolarHrStreamHealthMonitor.shouldStreamVerityPpi(isVeritySense, TrainingApplication.getTrackingMode()) }
        ) {
            if (!api.getAvailableOnlineStreamDataTypes(sessionId).contains(PolarBleApi.PolarDeviceDataType.PPI)) return@launchStream
            if (!PolarHrStreamHealthMonitor.shouldStreamVerityPpi(isVeritySense, TrainingApplication.getTrackingMode())) return@launchStream
            streamCoordinator.collectPmdWithAlreadyInStateRecovery(
                pmdType = PmdMeasurementType.PPI,
                sessionId = sessionId,
                stopStreaming = { api.stopStreaming(sessionId, PmdMeasurementType.PPI) },
                streamFactory = { api.startPpiStreaming(sessionId) },
                onSample = { processPpiData(it, System.currentTimeMillis()) }
            )
        }
    }

    private fun syncVerityPpiForTrackingMode() {
        if (isShutDown.get() || !isConnectedFlag.get() || !isVeritySense) return
        if (PolarHrStreamHealthMonitor.shouldStreamVerityPpi(true, TrainingApplication.getTrackingMode())) {
            if (!isOfflineGattYielded()) maybeStartVerityStreamsIfReady()
        } else {
            stopVerityPpiAndClearLatchedRr()
        }
    }

    private fun stopVerityPpiAndClearLatchedRr(forceStopFirmwarePpi: Boolean = false) {
        val wasStreamingPpi = streamCoordinator.isActive(PolarPmdStreamCoordinator.StreamSlot.PPI) ||
            streamCoordinator.isJobActive(PolarPmdStreamCoordinator.StreamSlot.PPI)
        streamCoordinator.cancelSlot(PolarPmdStreamCoordinator.StreamSlot.PPI)
        if (wasStreamingPpi || forceStopFirmwarePpi) {
            polarApi?.let { PolarPmdStreamCoordinator.stopPmdStreamsQuietly(it, sdkSessionId, PmdMeasurementType.PPI) }
        }
        mRrIntervalSensor?.newValue(null)
        ppiStreamProcessor.reset()
        perDeviceHrvController.onAccumulatorsReset()
        hrHealthMonitor.resetRrAndOpticalArtifactState()
        mMySensorManager?.reevaluatePrimaryHrSensor()
    }

    private fun tryAcquireOnlineAccChannel(accOwnerKey: String): Boolean {
        if (isShutDown.get() || !isConnectedFlag.get()) return false
        if (isOfflineGattYielded()) {
            resumeAccAfterOfflineSync.set(true)
            if (isOfflineGattYielded()) return false
        }
        resumeAccAfterOfflineSync.set(false)
        val acquired = PolarOfflineGattCoordinator.tryAcquireAccChannel(accOwnerKey, PolarOfflineGattCoordinator.ACC_OWNER_ONLINE)
        if (!acquired) Log.w(TAG, "Skipping online ACC stream on $accOwnerKey: ACC channel locked by offline recording.")
        return acquired
    }

    private fun startAccCollection(api: PolarBleApi) {
        val accOwnerKey = polarId
        val sessionId = sdkSessionId
        streamCoordinator.launchStream(
            slot = PolarPmdStreamCoordinator.StreamSlot.ACC,
            preAcquire = { tryAcquireOnlineAccChannel(accOwnerKey) },
            onFinalCleanup = {
                activeAccSampleRateHz = null
                PolarOfflineGattCoordinator.releaseAccChannel(accOwnerKey, PolarOfflineGattCoordinator.ACC_OWNER_ONLINE)
            }
        ) {
            highRateRunningDynamicsActive = !isVeritySense && isRunSportActive()
            val setting = resolveAccSetting(api, sessionId)
            streamCoordinator.collectPmdWithAlreadyInStateRecovery(
                pmdType = PmdMeasurementType.ACC,
                sessionId = sessionId,
                stopStreaming = { api.stopStreaming(sessionId, PmdMeasurementType.ACC) },
                streamFactory = { api.startAccStreaming(sessionId, setting) },
                onSample = { processAccData(it) }
            )
        }
    }

    private fun startGyroCollection(api: PolarBleApi) {
        val sessionId = sdkSessionId
        streamCoordinator.launchStream(PolarPmdStreamCoordinator.StreamSlot.GYRO) {
            val setting = PolarPmdStreamCoordinator.resolveGyroSetting(api, sessionId)
            streamCoordinator.collectPmdWithAlreadyInStateRecovery(
                pmdType = PmdMeasurementType.GYRO,
                sessionId = sessionId,
                stopStreaming = { api.stopStreaming(sessionId, PmdMeasurementType.GYRO) },
                streamFactory = { api.startGyroStreaming(sessionId, setting) },
                onSample = { processGyroData(it) }
            )
        }
    }

    private suspend fun resolveAccSetting(api: PolarBleApi, sessionId: String): PolarSensorSetting {
        highRateRunningDynamicsActive = !isVeritySense && isRunSportActive()
        val (setting, rates) = PolarPmdStreamCoordinator.resolveAccSetting(api, sessionId, isVeritySense, highRateRunningDynamicsActive)
        availableAccRates = rates
        activeAccSampleRateHz = setting.settings[PolarSensorSetting.SettingType.SAMPLE_RATE]?.firstOrNull()
        return setting
    }

    /**
     * Processes a live [PolarHrData] packet into `SensorType.HR`, `SensorType.RR_INTERVAL`, and
     * [RrBeatSource] listeners, applying stream-health validation and flatline/invalid clearing (`REQ-SET-126.2`).
     */
    fun processHrData(data: PolarHrData, packetArrivalMs: Long) {
        if (isShutDown.get()) return
        val mode = TrainingApplication.getTrackingMode()
        val isWorkoutActive = PolarHrStreamHealthMonitor.isWorkoutActive(mode)
        val artifactRate = effectiveOpticalArtifactRatePercent(mode)
        for (sample in data.samples) {
            val decision = hrHealthMonitor.onHrSample(
                hrBpm = sample.hr,
                contactSupported = sample.contactStatusSupported,
                contactStatus = sample.contactStatus,
                nowMs = packetArrivalMs,
                isWorkoutActive = isWorkoutActive,
                opticalArtifactRatePercent = artifactRate
            )
            if (decision.acceptForSensor) {
                if (silentHrWatchdog.onHrPacketReceived()) cancelSilentHrWatchdog(resetState = false)
                mHeartRateSensor.newValue(sample.hr)
                notifyImuListeners("onHrSample") { it.onHrSample(polarId, sample.hr, packetArrivalMs * NS_PER_MS) }
            } else {
                if (decision.shouldClearHr) mHeartRateSensor.newValue(null)
                if (decision.shouldClearRr) mRrIntervalSensor?.newValue(null)
            }
            scheduleNextHrStalenessCheck(packetArrivalMs)
            if (decision.healthTierChanged) mMySensorManager?.reevaluatePrimaryHrSensor()
            if (decision.shouldTriggerRecovery) recoverHrStream()
            if (decision.acceptForSensor && sample.rrAvailable && sample.rrsMs.isNotEmpty()) {
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

    /** Processes Verity Sense optical [PolarPpiData] packets via [PolarPpiStreamProcessor] (`REQ-HRV-051.1`, `REQ-SET-126.2`). */
    fun processPpiData(data: PolarPpiData, packetArrivalMs: Long) {
        if (isShutDown.get() || !PolarHrStreamHealthMonitor.shouldStreamVerityPpi(true, TrainingApplication.getTrackingMode())) return
        val events = ppiStreamProcessor.processSamples(data.samples, packetArrivalMs)
        for (event in events) {
            val isClean = event.artifactFlag == RrBeatEvent.ARTIFACT_NORMAL
            if (isClean) {
                val rrSensor = mRrIntervalSensor ?: MySensor<Int>(this, SensorType.RR_INTERVAL).also {
                    mRrIntervalSensor = it
                    addAndRegisterSensor(it)
                }
                rrSensor.newValue(Math.round(event.rrMs).toInt())
            }
            dispatchRrBeat(event)
            val artifactRate = effectiveOpticalArtifactRatePercent(TrainingApplication.getTrackingMode())
            val rrDecision = hrHealthMonitor.onRrSample(isClean, packetArrivalMs, artifactRate)
            if (rrDecision.shouldClearRr) mRrIntervalSensor?.newValue(null)
            if (rrDecision.healthTierChanged) mMySensorManager?.reevaluatePrimaryHrSensor()
        }
    }

    private inline fun notifyImuListeners(eventName: String, invoke: (ImuFrameListener) -> Unit) {
        for (listener in imuFrameListeners) {
            try {
                invoke(listener)
            } catch (e: Exception) {
                Log.w(TAG, "Error in ImuFrameListener.$eventName: ${e.message}")
            }
        }
    }

    /**
     * Processes live [PolarAccelerometerData] frames, notifies raw IMU listeners, and emits
     * IMU-derived `CADENCE`/`STRIDES` strictly when [BSportType.RUN] is active (`REQ-SET-112.4`).
     */
    fun processAccData(data: PolarAccelerometerData) {
        if (isShutDown.get()) return
        notifyImuListeners("onAccFrame") { it.onAccFrame(polarId, data) }
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
        notifyImuListeners("onGyroFrame") { it.onGyroFrame(polarId, data) }
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
        maybeRenegotiateAccRate(runActive)
    }

    private fun maybeRenegotiateAccRate(runActive: Boolean) {
        val api = polarApi ?: return
        if (isVeritySense || !isConnectedFlag.get() || isOfflineGattYielded() || !streamCoordinator.isActive(PolarPmdStreamCoordinator.StreamSlot.ACC)) return
        val currentRate = activeAccSampleRateHz ?: return
        val desiredRate = PolarPmdStreamCoordinator.computeDesiredAccRateHz(availableAccRates, isVeritySense = false, highRateRunningDynamicsActive = runActive) ?: return
        if (desiredRate == currentRate) return
        val sessionId = sdkSessionId
        streamCoordinator.renegotiateSlot(
            slot = PolarPmdStreamCoordinator.StreamSlot.ACC,
            isAllowedToRelaunch = { !isShutDown.get() && isConnectedFlag.get() && !isOfflineGattYielded() },
            onOldSlotCancelled = { activeAccSampleRateHz = null; releaseOnlineAccChannels() },
            stopStreaming = { api.stopStreaming(sessionId, PmdMeasurementType.ACC) },
            beforeRelaunch = { synchronized(cadenceEstimatorLock) { runningDynamicsEstimator.reset() } },
            onAborted = { onAccRenegotiationAborted() },
            relaunch = { startAccCollection(api) }
        )
    }

    private fun onAccRenegotiationAborted() {
        if (isShutDown.get() || !isConnectedFlag.get()) return
        if (isOfflineGattYielded()) {
            resumeAccAfterOfflineSync.set(true)
            if (isOfflineGattYielded()) return
        }
        if (!streamCoordinator.isActive(PolarPmdStreamCoordinator.StreamSlot.ACC) && !streamCoordinator.isRenegotiating(PolarPmdStreamCoordinator.StreamSlot.ACC)) {
            resumeAccAfterOfflineSync.set(false)
            polarApi?.let(::startAccCollection)
        }
    }

    private fun unregisterAndRemoveSensor(sensor: MySensor<*>?, sensorType: SensorType) {
        if (sensor == null) return
        sensor.deactivateSensor()
        mMySensorManager?.unregisterSensor(sensor)
        removeSensor(sensorType)
    }

    private fun unregisterImuCadenceSensors() {
        unregisterAndRemoveSensor(mCadenceSensor, SensorType.CADENCE); mCadenceSensor = null
        unregisterAndRemoveSensor(mStridesSensor, SensorType.STRIDES); mStridesSensor = null
        unregisterAndRemoveSensor(mVerticalOscillationSensor, SensorType.VERTICAL_OSCILLATION); mVerticalOscillationSensor = null
        unregisterAndRemoveSensor(mGroundContactTimeSensor, SensorType.GROUND_CONTACT_TIME); mGroundContactTimeSensor = null
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
    fun isAccStreamingForTesting(): Boolean = streamCoordinator.isActive(PolarPmdStreamCoordinator.StreamSlot.ACC)

    @VisibleForTesting
    fun isGyroStreamingForTesting(): Boolean = streamCoordinator.isActive(PolarPmdStreamCoordinator.StreamSlot.GYRO)

    @VisibleForTesting
    fun isPpiStreamingForTesting(): Boolean = streamCoordinator.isActive(PolarPmdStreamCoordinator.StreamSlot.PPI)

    @VisibleForTesting
    fun getSearchTimeoutJobForTesting(): Job? = synchronized(searchTimeoutLock) { searchTimeoutJob }

    @VisibleForTesting
    fun getReconnectJobForTesting(): Job? = reconnectJob

    @VisibleForTesting
    fun getCurrentReconnectDelayMsForTesting(): Long = currentReconnectDelayMs

    @VisibleForTesting
    fun getPolarBleSessionIdForTesting(): String? = activeSdkStreamId

    @VisibleForTesting
    fun getCoordinatorSessionKeyForTesting(): String? = coordinatorSessionKey

    @VisibleForTesting
    fun isHrJobActiveForTesting(): Boolean = streamCoordinator.isJobActive(PolarPmdStreamCoordinator.StreamSlot.HR)

    @VisibleForTesting
    fun isAccJobActiveForTesting(): Boolean = streamCoordinator.isJobActive(PolarPmdStreamCoordinator.StreamSlot.ACC)

    @VisibleForTesting
    fun isGyroJobActiveForTesting(): Boolean = streamCoordinator.isJobActive(PolarPmdStreamCoordinator.StreamSlot.GYRO)

    @VisibleForTesting
    fun isPpiJobActiveForTesting(): Boolean = streamCoordinator.isJobActive(PolarPmdStreamCoordinator.StreamSlot.PPI)

    override fun onAccumulatorsReset() {
        super.onAccumulatorsReset()
        ppiStreamProcessor.reset()
        perDeviceHrvController.onAccumulatorsReset()
    }

    override fun unregisterSensors() {
        cancelHrStalenessTimer()
        hrHealthMonitor.reset()
        perDeviceHrvController.onDisconnect()
        unregisterImuCadenceSensors()
        unregisterAndRemoveSensor(mRrIntervalSensor, SensorType.RR_INTERVAL); mRrIntervalSensor = null
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
        PolarOfflineGattCoordinator.unregisterOfflineYieldListener(offlineYieldListener)
        resumeSearchAfterOfflineSync.set(false)
        resumeSilentWatchdogAfterOfflineSync.set(false)
        resumeAccAfterOfflineSync.set(false)
        sessionOpenPendingFeatures.set(false)
        hasConnectedInSession.set(false)
        cancelSearchTimeoutWatchdog()
        cancelReconnectJob()
        cancelSilentHrWatchdog(resetState = true)
        cancelHrStalenessTimer()
        hrHealthMonitor.reset()
        isConnectedFlag.set(false)

        LifecycleUtils.safeUnregisterReceiver(mContext, sportChangedReceiver, TAG)

        cancelActiveStreamJobs()

        val streamId = sdkSessionId
        val coordKey = coordinatorSessionKey ?: streamId
        streamCoordinator.resetSessionWarningState(polarId, streamId, coordKey, macAddress)
        activeSdkStreamId = null
        coordinatorSessionKey = null
        val api = polarApi
        polarApi = null
        if (api != null) {
            val pmdTypes = if (isVeritySense) {
                arrayOf(PmdMeasurementType.ACC, PmdMeasurementType.GYRO, PmdMeasurementType.PPI)
            } else {
                arrayOf(PmdMeasurementType.ACC)
            }
            PolarPmdStreamCoordinator.stopPmdStreamsQuietly(api, streamId, *pmdTypes)
            PolarOfflineGattCoordinator.registerDeviceAliases(coordKey, streamId, polarId, macAddress)
        }
        releaseOnlineAccChannels()
        PolarOfflineGattCoordinator.releaseAccChannel(streamId, PolarOfflineGattCoordinator.ACC_OWNER_ONLINE)
        PolarOfflineGattCoordinator.releaseAccChannel(coordKey, PolarOfflineGattCoordinator.ACC_OWNER_ONLINE)
        PolarOfflineGattCoordinator.forceDisconnectDevice(
            context = mContext,
            macAddress = macAddress,
            polarId = streamId,
            clearRegistrations = true
        )
        if (api != null) {
            PolarOfflineGattCoordinator.releasePersistentSession(
                polarId = coordKey,
                macAddress = macAddress,
                api = api,
                callback = sdkCallback
            )
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
