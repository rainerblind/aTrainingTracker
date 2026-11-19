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

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.DeviceManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.MyRemoteDevice
import com.atrainingtracker.banalservice.devices.bluetooth_le.MyBTLEDevice
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.polar.androidcommunications.api.ble.model.DisInfo
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiCallbackProvider
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarHealthThermometerData
import com.polar.sdk.api.model.PolarHrData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Shared GATT arbitration, Polar ID resolution, device discovery helpers, and SDK feature
 * synchronization reused across [PolarH10OfflineController], [PolarVeritySenseOfflineController],
 * and [PolarOnlineStreamingBridge] (`ATT-7923`, `ATT-8016`).
 */
@Suppress("TooGenericExceptionCaught")
object PolarOfflineGattCoordinator {

    private const val TAG = "PolarOfflineGattCoord"
    const val FEATURE_READY_TIMEOUT_MS = 10_000L
    const val STOP_CONNECTION_TIMEOUT_MS = 5_000L
    const val ACC_OWNER_ONLINE = "ONLINE"
    const val ACC_OWNER_OFFLINE = "OFFLINE"
    private val POLAR_ID_REGEX = Regex("""\b([0-9A-Fa-f]{8})\b""")
    private val VERITY_MODEL_TOKENS = listOf(
        "polar sense",
        "verity sense",
        "polar verity",
        "polar oh1"
    )

    /**
     * Superset of all Polar BLE SDK features required across live streaming, H10 offline exercise
     * recording, Verity Sense offline recording, and training session sync (`REQ-SET-118.1`).
     */
    val ALL_POLAR_FEATURES: Set<PolarBleApi.PolarBleSdkFeature> = setOf(
        PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
        PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING,
        PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
        PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO,
        PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP,
        PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING,
        PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING,
        PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_TRAINING_DATA
    )

    private data class CallbackRegistration(
        val targetIdentifiers: Set<String>,
        val callback: PolarBleApiCallbackProvider
    )

    private val apiLock = Any()

    @Volatile
    private var sharedApi: PolarBleApi? = null
    private var activeLeaseCount: Int = 0

    @VisibleForTesting
    internal var apiFactoryForTesting: ((Context, Set<PolarBleApi.PolarBleSdkFeature>) -> PolarBleApi)? = null

    @VisibleForTesting
    internal var deviceManagerProvider: () -> DeviceManager? = { BANALService.getDeviceManager() }

    private val activeSessions = ConcurrentHashMap<String, PolarBleApi>()
    private val activeSessionCallbacks = ConcurrentHashMap<String, PolarBleApiCallbackProvider>()
    private val callbackListeners = CopyOnWriteArraySet<CallbackRegistration>()
    private val deviceAliases = ConcurrentHashMap<String, MutableSet<String>>()
    private val connectedDeviceIds = ConcurrentHashMap.newKeySet<String>()
    private val activeAccOwners = ConcurrentHashMap<String, String>()

    private fun normalizeId(polarId: String): String = polarId.trim().uppercase(Locale.ROOT)

    /**
     * Registers bidirectional aliases (e.g., MAC address <-> 8-hex Polar device ID) so SDK
     * callbacks and session lookups resolve identically regardless of identifier format (`REQ-SET-118.2`).
     */
    @JvmStatic
    fun registerDeviceAliases(vararg identifiers: String?) {
        val normalized = identifiers.mapNotNull { id ->
            id?.let(::normalizeId)?.takeIf { it.isNotEmpty() }
        }.toSet()
        if (normalized.size < 2) return

        val combined = ConcurrentHashMap.newKeySet<String>()
        combined.addAll(normalized)
        for (id in normalized) {
            deviceAliases[id]?.let { combined.addAll(it) }
        }
        for (id in combined) {
            val set = deviceAliases.computeIfAbsent(id) { ConcurrentHashMap.newKeySet() }
            set.addAll(combined)
        }
    }

    /**
     * Returns true if [targetId] and [incomingId] match directly (case-insensitive) or via
     * registered bidirectional `MAC` <-> `8-hex Polar ID` aliases (`REQ-SET-118.2`).
     */
    @JvmStatic
    fun matchesDeviceIdentifier(targetId: String?, incomingId: String?): Boolean {
        val normTarget = targetId?.let(::normalizeId)?.takeIf { it.isNotEmpty() } ?: return false
        val normIncoming = incomingId?.let(::normalizeId)?.takeIf { it.isNotEmpty() } ?: return false
        if (normTarget == normIncoming) return true
        if (deviceAliases[normTarget]?.contains(normIncoming) == true) return true
        return deviceAliases[normIncoming]?.contains(normTarget) == true
    }

    /**
     * Returns true if [polarDeviceInfo] matches either [polarId] or [macAddress] (including aliases)
     * and records the observed `deviceId` <-> `address` alias pair (`REQ-SET-118.2`).
     */
    @JvmStatic
    fun matchesDeviceInfo(
        polarId: String?,
        macAddress: String?,
        polarDeviceInfo: PolarDeviceInfo
    ): Boolean {
        registerDeviceAliases(polarDeviceInfo.deviceId, polarDeviceInfo.address)
        return matchesDeviceIdentifier(polarId, polarDeviceInfo.deviceId) ||
            matchesDeviceIdentifier(polarId, polarDeviceInfo.address) ||
            matchesDeviceIdentifier(macAddress, polarDeviceInfo.deviceId) ||
            matchesDeviceIdentifier(macAddress, polarDeviceInfo.address)
    }

    private fun CallbackRegistration.matchesIdentifier(incomingId: String?): Boolean {
        if (targetIdentifiers.isEmpty()) return true
        return targetIdentifiers.any { matchesDeviceIdentifier(it, incomingId) }
    }

    private fun CallbackRegistration.matchesInfo(polarDeviceInfo: PolarDeviceInfo): Boolean {
        if (targetIdentifiers.isEmpty()) return true
        return targetIdentifiers.any { target ->
            matchesDeviceIdentifier(target, polarDeviceInfo.deviceId) ||
                matchesDeviceIdentifier(target, polarDeviceInfo.address)
        }
    }

    private val multiplexerCallback = object : NoOpPolarCallback() {
        override fun blePowerStateChanged(powered: Boolean) {
            if (!powered) {
                connectedDeviceIds.clear()
            }
            for (entry in callbackListeners) {
                entry.callback.blePowerStateChanged(powered)
            }
        }

        override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
            registerDeviceAliases(polarDeviceInfo.deviceId, polarDeviceInfo.address)
            for (entry in callbackListeners) {
                if (entry.matchesInfo(polarDeviceInfo)) {
                    entry.callback.deviceConnecting(polarDeviceInfo)
                }
            }
        }

        override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
            registerDeviceAliases(polarDeviceInfo.deviceId, polarDeviceInfo.address)
            polarDeviceInfo.deviceId.takeIf { it.isNotBlank() }?.let { connectedDeviceIds.add(normalizeId(it)) }
            polarDeviceInfo.address.takeIf { it.isNotBlank() }?.let { connectedDeviceIds.add(normalizeId(it)) }
            for (entry in callbackListeners) {
                if (entry.matchesInfo(polarDeviceInfo)) {
                    entry.callback.deviceConnected(polarDeviceInfo)
                }
            }
        }

        override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
            registerDeviceAliases(polarDeviceInfo.deviceId, polarDeviceInfo.address)
            polarDeviceInfo.deviceId.takeIf { it.isNotBlank() }?.let { id ->
                val norm = normalizeId(id)
                connectedDeviceIds.remove(norm)
                deviceAliases[norm]?.forEach { connectedDeviceIds.remove(it) }
            }
            polarDeviceInfo.address.takeIf { it.isNotBlank() }?.let { addr ->
                val norm = normalizeId(addr)
                connectedDeviceIds.remove(norm)
                deviceAliases[norm]?.forEach { connectedDeviceIds.remove(it) }
            }
            for (entry in callbackListeners) {
                if (entry.matchesInfo(polarDeviceInfo)) {
                    entry.callback.deviceDisconnected(polarDeviceInfo)
                }
            }
        }

        override fun bleSdkFeatureReady(
            identifier: String,
            feature: PolarBleApi.PolarBleSdkFeature
        ) {
            for (entry in callbackListeners) {
                if (entry.matchesIdentifier(identifier)) {
                    entry.callback.bleSdkFeatureReady(identifier, feature)
                }
            }
        }

        override fun batteryLevelReceived(identifier: String, level: Int) {
            for (entry in callbackListeners) {
                if (entry.matchesIdentifier(identifier)) {
                    entry.callback.batteryLevelReceived(identifier, level)
                }
            }
        }

        override fun hrNotificationReceived(identifier: String, data: PolarHrData.PolarHrSample) {
            for (entry in callbackListeners) {
                if (entry.matchesIdentifier(identifier)) {
                    entry.callback.hrNotificationReceived(identifier, data)
                }
            }
        }

        override fun disInformationReceived(identifier: String, uuid: UUID, value: String) {
            for (entry in callbackListeners) {
                if (entry.matchesIdentifier(identifier)) {
                    entry.callback.disInformationReceived(identifier, uuid, value)
                }
            }
        }

        override fun disInformationReceived(identifier: String, disInfo: DisInfo) {
            for (entry in callbackListeners) {
                if (entry.matchesIdentifier(identifier)) {
                    entry.callback.disInformationReceived(identifier, disInfo)
                }
            }
        }

        override fun htsNotificationReceived(identifier: String, data: PolarHealthThermometerData) {
            for (entry in callbackListeners) {
                if (entry.matchesIdentifier(identifier)) {
                    entry.callback.htsNotificationReceived(identifier, data)
                }
            }
        }
    }

    /**
     * Acquires a ref-counted lease on the process-wide [PolarBleApi] singleton initialized with
     * [ALL_POLAR_FEATURES], installing the global callback multiplexer on initial creation (`REQ-SET-118.1`, `.3`).
     */
    @JvmStatic
    fun acquirePolarApi(
        context: Context,
        apiFactory: ((Context, Set<PolarBleApi.PolarBleSdkFeature>) -> PolarBleApi)? = null
    ): PolarBleApi {
        var newlyCreatedApi: PolarBleApi? = null
        val api = synchronized(apiLock) {
            val existing = sharedApi
            if (existing != null) {
                activeLeaseCount++
                existing
            } else {
                val factory = apiFactory ?: apiFactoryForTesting
                val created = factory?.invoke(context.applicationContext, ALL_POLAR_FEATURES)
                    ?: PolarBleApiDefaultImpl.defaultImplementation(context.applicationContext, ALL_POLAR_FEATURES)
                sharedApi = created
                activeLeaseCount = 1
                newlyCreatedApi = created
                created
            }
        }
        // Install global multiplexer outside apiLock to prevent lock-ordering inversion when
        // BDBleApiImpl.setApiCallback() synchronously fires blePowerStateChanged().
        if (newlyCreatedApi != null) {
            try {
                newlyCreatedApi.setApiCallback(multiplexerCallback)
            } catch (t: Throwable) {
                Log.w(TAG, "Error setting multiplexed PolarBleApi callback: ${t.message}")
            }
        }
        return api
    }

    /**
     * Releases a ref-counted lease on [api]; shuts down the singleton strictly when `activeLeaseCount`
     * drops to zero, never calling `api.cleanup()` (`REQ-SET-118.3`).
     */
    @JvmStatic
    fun releasePolarApi(api: PolarBleApi) {
        val apiToShutdown = synchronized(apiLock) {
            if (api !== sharedApi) return
            if (activeLeaseCount > 0) {
                activeLeaseCount--
            }
            if (activeLeaseCount == 0) {
                val closing = sharedApi
                sharedApi = null
                closing
            } else {
                null
            }
        }
        if (apiToShutdown != null) {
            try {
                apiToShutdown.shutDown()
            } catch (t: Throwable) {
                Log.w(TAG, "Error shutting down PolarBleApi singleton: ${t.message}")
            }
        }
    }

    /**
     * Acquires a persistent [PolarBleApi] lease and registers [polarId]/[macAddress] and [callback]
     * with the coordinator's active-session registry and callback multiplexer (`REQ-SET-118.1`..`.3`).
     */
    @JvmStatic
    fun acquirePersistentSession(
        context: Context,
        polarId: String,
        macAddress: String? = null,
        callback: PolarBleApiCallbackProvider,
        apiFactory: ((Context, Set<PolarBleApi.PolarBleSdkFeature>) -> PolarBleApi)? = null
    ): PolarBleApi {
        registerDeviceAliases(polarId, macAddress)
        val api = acquirePolarApi(context, apiFactory)
        registerActiveSession(polarId, api, callback, macAddress)
        return api
    }

    /**
     * Unregisters a persistent bridge session and releases its ref-counted [PolarBleApi] lease (`REQ-SET-118.3`).
     */
    @JvmStatic
    fun releasePersistentSession(
        polarId: String,
        macAddress: String? = null,
        api: PolarBleApi,
        callback: PolarBleApiCallbackProvider? = null
    ) {
        unregisterActiveSession(polarId, api, macAddress, callback)
        releasePolarApi(api)
    }

    private fun removeCallbackListeners(callback: PolarBleApiCallbackProvider) {
        val toRemove = callbackListeners.filter { it.callback === callback }
        if (toRemove.isNotEmpty()) {
            callbackListeners.removeAll(toRemove)
        }
    }

    /**
     * Registers a persistent [PolarBleApi] session (e.g. from [PolarOnlineStreamingBridge]) so
     * offline PFTP operations reuse the single GATT connection instead of opening a competing client.
     */
    @JvmStatic
    @JvmOverloads
    fun registerActiveSession(
        polarId: String,
        api: PolarBleApi,
        callback: PolarBleApiCallbackProvider? = null,
        macAddress: String? = null
    ) {
        registerDeviceAliases(polarId, macAddress)
        val key = normalizeId(polarId)
        activeSessions[key] = api
        if (callback != null) {
            activeSessionCallbacks[key] = callback
            val ids = listOfNotNull(polarId, macAddress)
                .map(::normalizeId)
                .filter { it.isNotEmpty() }
                .toSet()
            removeCallbackListeners(callback)
            callbackListeners.add(CallbackRegistration(ids, callback))
        }
    }

    /**
     * Unregisters an active [PolarBleApi] session on shutdown (`ATT-8016`).
     */
    @JvmStatic
    @JvmOverloads
    fun unregisterActiveSession(
        polarId: String,
        api: PolarBleApi? = null,
        macAddress: String? = null,
        callback: PolarBleApiCallbackProvider? = null
    ) {
        val key = normalizeId(polarId)
        val macKey = macAddress?.let(::normalizeId)?.takeIf { it.isNotEmpty() }
        if (api == null) {
            activeSessions.remove(key)
            val removedCb = activeSessionCallbacks.remove(key)
            (callback ?: removedCb)?.let(::removeCallbackListeners)
        } else {
            val removed = activeSessions.remove(key, api)
            if (removed) {
                val removedCb = activeSessionCallbacks.remove(key)
                (callback ?: removedCb)?.let(::removeCallbackListeners)
            } else if (callback != null) {
                removeCallbackListeners(callback)
            }
            if (macKey != null) {
                activeSessions.remove(macKey, api)
                activeSessionCallbacks.remove(macKey)
            }
            deviceAliases[key]?.forEach { alias ->
                if (activeSessions.remove(alias, api)) {
                    activeSessionCallbacks.remove(alias)
                }
            }
            macKey?.let { deviceAliases[it] }?.forEach { alias ->
                if (activeSessions.remove(alias, api)) {
                    activeSessionCallbacks.remove(alias)
                }
            }
        }
    }

    /**
     * Returns true if the shared [PolarBleApi] multiplexer has observed an active connection
     * (`SESSION_OPEN`) for [polarId] or any of its registered MAC/8-hex aliases (`REQ-CON-050.2`).
     */
    @JvmStatic
    fun isDeviceConnected(polarId: String?): Boolean {
        val key = polarId?.let(::normalizeId)?.takeIf { it.isNotEmpty() } ?: return false
        if (connectedDeviceIds.contains(key)) return true
        return deviceAliases[key]?.any { connectedDeviceIds.contains(it) } == true
    }

    /**
     * Returns `true` strictly when [DeviceManager] is active, tracks a matching BLE HRM for
     * [polarId]/[macAddress], and confirms it is NOT receiving data while the shared
     * [PolarBleApi] multiplexer also reports it disconnected (`REQ-SET-119.3`).
     *
     * Returns `false` when [DeviceManager] is unavailable or tracks no matching device so unit
     * tests and unrecognized devices fall through to bounded connection attempts.
     */
    @JvmStatic
    fun isTargetConfirmedDisconnected(polarId: String?, macAddress: String?): Boolean {
        if (isDeviceConnected(polarId) || isDeviceConnected(macAddress)) {
            return false
        }
        val deviceManager = try {
            deviceManagerProvider.invoke()
        } catch (_: Throwable) {
            null
        } ?: return false
        val matchingDevices = deviceManager.remoteDeviceList.orEmpty().filter { device ->
            device.deviceType == DeviceType.HRM &&
                device.protocol == Protocol.BLUETOOTH_LE &&
                matchesRemoteDevice(device, polarId, macAddress)
        }
        if (matchingDevices.isEmpty()) {
            return false
        }
        return matchingDevices.none { it.isReceivingData }
    }

    private fun matchesRemoteDevice(
        device: MyRemoteDevice,
        polarId: String?,
        macAddress: String?
    ): Boolean {
        return when (device) {
            is PolarOnlineStreamingBridge -> {
                matchesDeviceIdentifier(device.polarId, polarId) ||
                    matchesDeviceIdentifier(device.polarId, macAddress) ||
                    matchesDeviceIdentifier(device.getAddress(), macAddress) ||
                    matchesDeviceIdentifier(device.getAddress(), polarId)
            }
            is MyBTLEDevice -> {
                val addr = device.address
                matchesDeviceIdentifier(addr, macAddress) || matchesDeviceIdentifier(addr, polarId)
            }
            else -> false
        }
    }

    /**
     * Connects to [polarId] and awaits GATT connection within [timeoutMs], returning `false`
     * on timeout rather than blocking stop finalization for the full PFTP transfer window
     * (`REQ-SET-119.3`).
     */
    suspend fun connectWithinTimeout(
        api: PolarBleApi,
        polarId: String,
        timeoutMs: Long
    ): Boolean {
        return withTimeoutOrNull(timeoutMs) {
            api.connectToDevice(polarId)
            api.waitForConnection(polarId)
            true
        } ?: false
    }

    /**
     * Returns the persistent [PolarBleApi] session for [polarId] (or any registered MAC/8-hex alias),
     * or `null` if none is active.
     */
    @JvmStatic
    fun getActiveSession(polarId: String): PolarBleApi? {
        if (polarId.isBlank()) return null
        val key = normalizeId(polarId)
        activeSessions[key]?.let { return it }
        deviceAliases[key]?.forEach { alias ->
            activeSessions[alias]?.let { return it }
        }
        return null
    }

    /**
     * Acquires exclusive ownership of the Verity Sense/Polar `ACC` channel for [owner]
     * (`ACC_OWNER_ONLINE` or `ACC_OWNER_OFFLINE`), enforcing per-channel mutual exclusion (`REQ-SET-112.3`).
     */
    @JvmStatic
    fun tryAcquireAccChannel(polarId: String, owner: String): Boolean {
        val key = normalizeId(polarId)
        val existing = activeAccOwners.putIfAbsent(key, owner)
        return existing == null || existing == owner
    }

    /**
     * Releases `ACC` channel ownership if currently held by [owner].
     */
    @JvmStatic
    fun releaseAccChannel(polarId: String, owner: String) {
        activeAccOwners.remove(normalizeId(polarId), owner)
    }

    /**
     * Returns the current owner of the `ACC` channel for [polarId], or `null` if free.
     */
    @JvmStatic
    fun getAccChannelOwner(polarId: String): String? = activeAccOwners[normalizeId(polarId)]

    @VisibleForTesting
    fun getActiveLeaseCountForTesting(): Int = synchronized(apiLock) { activeLeaseCount }

    @VisibleForTesting
    fun getSharedApiForTesting(): PolarBleApi? = synchronized(apiLock) { sharedApi }

    @VisibleForTesting
    fun getMultiplexerCallbackForTesting(): PolarBleApiCallback = multiplexerCallback

    @VisibleForTesting
    fun resetForTesting() {
        synchronized(apiLock) {
            sharedApi = null
            activeLeaseCount = 0
            apiFactoryForTesting = null
        }
        deviceManagerProvider = { BANALService.getDeviceManager() }
        activeSessions.clear()
        activeSessionCallbacks.clear()
        callbackListeners.clear()
        deviceAliases.clear()
        connectedDeviceIds.clear()
        activeAccOwners.clear()
    }

    /**
     * Resolved target Polar HRM device entry from [DevicesDatabaseManager].
     */
    data class PolarTargetDevice(
        val deviceId: Long,
        val name: String,
        val macAddress: String,
        val polarId: String
    )

    /**
     * Extracts the 8-hex-character Polar device ID from [deviceName] when present, falling back to [macAddress].
     */
    @JvmStatic
    fun extractPolarId(deviceName: String, macAddress: String): String {
        return extractPolarId(null, deviceName, macAddress)
    }

    /**
     * Resolves the canonical Polar ID from persisted DIS/SDK hardware metadata ([PolarOfflineCapabilityStore]),
     * falling back to `POLAR_ID_REGEX` on un-reconnected rows and finally [macAddress] (`REQ-CON-049.4`).
     */
    @JvmStatic
    fun extractPolarId(context: Context?, deviceName: String, macAddress: String): String {
        val persistedId = PolarOfflineCapabilityStore.getPolarDeviceId(context, macAddress)
        if (!persistedId.isNullOrBlank()) {
            registerDeviceAliases(persistedId, macAddress)
            return persistedId
        }
        val regexId = POLAR_ID_REGEX.find(deviceName)?.value
        if (!regexId.isNullOrBlank()) {
            registerDeviceAliases(regexId, macAddress)
            return regexId
        }
        return macAddress
    }

    /**
     * Returns true if [name] / [manufacturer] identifies a Polar H10 chest strap.
     */
    @JvmStatic
    @JvmOverloads
    fun isPolarH10Name(name: String, manufacturer: String = ""): Boolean {
        val lowerName = name.lowercase(Locale.ROOT)
        val lowerMfg = manufacturer.lowercase(Locale.ROOT)
        return lowerName.contains("polar h10") ||
            (lowerName.contains("h10") && (lowerName.contains("polar") || lowerMfg.contains("polar")))
    }

    /**
     * Returns true when persisted DIS/SDK hardware metadata identifies a Polar H10, or (strictly when
     * hardware type is `UNKNOWN` on un-reconnected rows) legacy name heuristics match (`REQ-CON-049.4`).
     */
    @JvmStatic
    @JvmOverloads
    fun isPolarH10Device(
        context: Context?,
        name: String,
        manufacturer: String = "",
        macAddress: String? = null
    ): Boolean {
        return when (PolarOfflineCapabilityStore.getDeviceType(context, macAddress)) {
            PolarOfflineCapabilityStore.PolarDeviceType.H10 -> true
            PolarOfflineCapabilityStore.PolarDeviceType.VERITY_SENSE -> false
            PolarOfflineCapabilityStore.PolarDeviceType.UNKNOWN -> isPolarH10Name(name, manufacturer)
        }
    }

    /**
     * Returns true if [name] / [manufacturer] identifies a Polar optical sensor using `PolarOfflineRecordingApi`.
     */
    @JvmStatic
    @JvmOverloads
    fun isPolarVeritySenseName(name: String, manufacturer: String = ""): Boolean {
        val lowerName = name.lowercase(Locale.ROOT)
        val lowerMfg = manufacturer.lowercase(Locale.ROOT)
        val isPolarBrand = lowerName.contains("polar") || lowerMfg.contains("polar")
        val matchesKnownModel = VERITY_MODEL_TOKENS.any { lowerName.contains(it) }
        val matchesOpticalSuffix = isPolarBrand && (lowerName.contains("verity") || lowerName.contains("oh1"))
        return matchesKnownModel || matchesOpticalSuffix
    }

    /**
     * Returns true for Polar Verity Sense / OH1 sensors confirmed via DIS/SDK hardware metadata, or
     * (strictly when hardware type is `UNKNOWN` on un-reconnected rows) legacy name/offline capability
     * heuristics (`REQ-CON-049.4`).
     */
    @JvmStatic
    @JvmOverloads
    fun isPolarVeritySenseDevice(
        context: Context?,
        name: String,
        manufacturer: String = "",
        macAddress: String? = null
    ): Boolean {
        val storedType = PolarOfflineCapabilityStore.getDeviceType(context, macAddress)
        if (storedType == PolarOfflineCapabilityStore.PolarDeviceType.VERITY_SENSE) return true
        if (storedType == PolarOfflineCapabilityStore.PolarDeviceType.H10) return false
        if (isPolarH10Name(name, manufacturer)) return false
        val isKnownModel = isPolarVeritySenseName(name, manufacturer)
        val hasCap = PolarOfflineCapabilityStore.hasOfflineRecordingCapability(context, macAddress)
        return isKnownModel || (hasCap && name.isNotEmpty())
    }

    /**
     * Returns true if the device is classified as either Polar H10 or Polar Verity Sense / OH1 (`REQ-CON-049.4`).
     */
    @JvmStatic
    @JvmOverloads
    fun isPolarOfflineCapableDevice(
        context: Context?,
        name: String,
        manufacturer: String = "",
        macAddress: String? = null
    ): Boolean {
        return isPolarH10Device(context, name, manufacturer, macAddress) ||
            isPolarVeritySenseDevice(context, name, manufacturer, macAddress) ||
            PolarOfflineCapabilityStore.hasPolarServiceCapability(context, macAddress)
    }

    /**
     * Finds the first paired BLE HRM device matching [namePredicate] that has offline backup recording enabled.
     *
     * When [BANALService.getDeviceManager] reports at least one actively receiving HRM (`isReceivingData`),
     * unworn/disconnected secondary Polar devices are skipped to prevent scan/GATT contention (`REQ-SET-118.4`).
     * Preserves fallback behavior when `DeviceManager` is null or reports no active HRMs (including unit tests).
     */
    fun findEnabledPolarDevice(
        context: Context,
        namePredicate: (name: String, macAddress: String) -> Boolean
    ): PolarTargetDevice? {
        val db = DevicesDatabaseManager.getInstance(context)
        val devices = db.allDevicesForCache ?: return null
        val activeReceivingHrms = try {
            BANALService.getDeviceManager()
                ?.remoteDeviceList
                ?.filter { it.deviceType == DeviceType.HRM && it.isReceivingData }
                .orEmpty()
        } catch (_: Throwable) {
            emptyList()
        }
        return devices.asSequence()
            .filter { it.type == DeviceType.HRM && it.protocol == Protocol.BLUETOOTH_LE }
            .mapNotNull { dev -> buildTargetIfEnabled(context, db, dev.id, dev.name ?: "", namePredicate) }
            .filter { target ->
                activeReceivingHrms.isEmpty() || activeReceivingHrms.any { active ->
                    active.deviceId == target.deviceId ||
                        (active is MyBTLEDevice && active.address.equals(target.macAddress, ignoreCase = true)) ||
                        (active is PolarOnlineStreamingBridge &&
                            (matchesDeviceIdentifier(active.polarId, target.polarId) ||
                                matchesDeviceIdentifier(active.getAddress(), target.macAddress)))
                }
            }
            .firstOrNull()
    }

    /**
     * Resolves the paired Bluetooth MAC address for a stored Polar 8-hex [polarId] or MAC string (`ATT-7924`, `REQ-CON-049.4`).
     */
    fun findMacAddressForPolarId(context: Context, polarId: String): String? {
        if (polarId.isBlank()) return null
        val db = DevicesDatabaseManager.getInstance(context)
        return db.allDevicesForCache
            ?.asSequence()
            ?.filter { it.type == DeviceType.HRM && it.protocol == Protocol.BLUETOOTH_LE }
            ?.mapNotNull { dev ->
                val mac = db.getBluetoothMACAddress(dev.id)
                val name = dev.name ?: ""
                val storedPolarId = PolarOfflineCapabilityStore.getPolarDeviceId(context, mac)
                val matches = mac != null &&
                    (matchesDeviceIdentifier(mac, polarId) ||
                        matchesDeviceIdentifier(storedPolarId, polarId) ||
                        name.contains(polarId, ignoreCase = true))
                if (matches) mac else null
            }
            ?.firstOrNull()
    }

    private fun buildTargetIfEnabled(
        context: Context,
        db: DevicesDatabaseManager,
        deviceId: Long,
        name: String,
        namePredicate: (name: String, macAddress: String) -> Boolean
    ): PolarTargetDevice? {
        val mac = db.getBluetoothMACAddress(deviceId)
        val valid = !mac.isNullOrEmpty() &&
            namePredicate(name, mac) &&
            db.isPolarH10OfflineRecordingEnabled(deviceId)
        return if (valid && mac != null) {
            val resolvedPolarId = extractPolarId(context, name, mac)
            registerDeviceAliases(resolvedPolarId, mac)
            PolarTargetDevice(
                deviceId = deviceId,
                name = name,
                macAddress = mac,
                polarId = resolvedPolarId
            )
        } else {
            null
        }
    }

    /**
     * Suspends until every feature in [requiredFeatures] is ready on [api] for [polarId], or times out.
     */
    suspend fun awaitFeaturesReady(
        api: PolarBleApi,
        polarId: String,
        requiredFeatures: Set<PolarBleApi.PolarBleSdkFeature>,
        timeoutMs: Long = FEATURE_READY_TIMEOUT_MS
    ): Boolean {
        if (requiredFeatures.isEmpty() || requiredFeatures.all { api.isFeatureReady(polarId, it) }) {
            return true
        }
        return awaitFeaturesViaCallback(api, polarId, requiredFeatures, timeoutMs)
    }

    private fun isManagedApi(api: PolarBleApi): Boolean {
        val currentShared = synchronized(apiLock) { sharedApi }
        return api === currentShared || activeSessions.values.any { it === api }
    }

    private suspend fun awaitFeaturesViaCallback(
        api: PolarBleApi,
        polarId: String,
        requiredFeatures: Set<PolarBleApi.PolarBleSdkFeature>,
        timeoutMs: Long
    ): Boolean {
        val readyFeatures = ConcurrentHashMap.newKeySet<PolarBleApi.PolarBleSdkFeature>()
        requiredFeatures.filterTo(readyFeatures) { api.isFeatureReady(polarId, it) }

        val deferred = CompletableDeferred<Boolean>()
        if (isManagedApi(api)) {
            val transientListener = object : NoOpPolarCallback() {
                override fun bleSdkFeatureReady(
                    identifier: String,
                    feature: PolarBleApi.PolarBleSdkFeature
                ) {
                    if (matchesDeviceIdentifier(polarId, identifier)) {
                        readyFeatures.add(feature)
                        if (readyFeatures.containsAll(requiredFeatures)) {
                            deferred.complete(true)
                        }
                    }
                }
            }
            val registration = CallbackRegistration(setOf(normalizeId(polarId)), transientListener)
            callbackListeners.add(registration)
            return try {
                requiredFeatures.filterTo(readyFeatures) { api.isFeatureReady(polarId, it) }
                if (readyFeatures.containsAll(requiredFeatures)) {
                    true
                } else {
                    withTimeoutOrNull(timeoutMs) { deferred.await() } ?: false
                }
            } finally {
                callbackListeners.remove(registration)
            }
        }

        // Legacy isolated apiOverride path for unit tests that stub api.setApiCallback() directly.
        val existingCallback = activeSessionCallbacks[normalizeId(polarId)]
        val callback = object : NoOpPolarCallback() {
            override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                existingCallback?.deviceConnected(polarDeviceInfo)
            }

            override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
                existingCallback?.deviceConnecting(polarDeviceInfo)
            }

            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                existingCallback?.deviceDisconnected(polarDeviceInfo)
            }

            override fun bleSdkFeatureReady(
                identifier: String,
                feature: PolarBleApi.PolarBleSdkFeature
            ) {
                existingCallback?.bleSdkFeatureReady(identifier, feature)
                if (matchesDeviceIdentifier(polarId, identifier)) {
                    readyFeatures.add(feature)
                    if (readyFeatures.containsAll(requiredFeatures)) {
                        deferred.complete(true)
                    }
                }
            }

            override fun batteryLevelReceived(identifier: String, level: Int) {
                existingCallback?.batteryLevelReceived(identifier, level)
            }

            override fun hrNotificationReceived(identifier: String, data: PolarHrData.PolarHrSample) {
                existingCallback?.hrNotificationReceived(identifier, data)
            }

            override fun disInformationReceived(identifier: String, uuid: UUID, value: String) {
                existingCallback?.disInformationReceived(identifier, uuid, value)
            }
        }

        api.setApiCallback(callback)
        return try {
            requiredFeatures.filterTo(readyFeatures) { api.isFeatureReady(polarId, it) }
            if (readyFeatures.containsAll(requiredFeatures)) {
                true
            } else {
                withTimeoutOrNull(timeoutMs) { deferred.await() } ?: false
            }
        } finally {
            try {
                api.setApiCallback(existingCallback ?: NoOpPolarCallback())
            } catch (_: Throwable) {
                // Ignore SDK callback reset failure during teardown.
            }
        }
    }

    private open class NoOpPolarCallback : PolarBleApiCallback() {
        override fun disInformationReceived(identifier: String, disInfo: DisInfo) {
            // No-op callback stub for feature readiness awaiting.
        }

        override fun htsNotificationReceived(identifier: String, data: PolarHealthThermometerData) {
            // No-op callback stub for feature readiness awaiting.
        }
    }

    /**
     * Yields standard `MyBTLEDevice` GATT operations for [macAddress], reuses the process-wide
     * ref-counted [PolarBleApi] singleton (or an active bridge session / [apiOverride]),
     * executes [block] bounded by [timeoutMs], releases its ref-counted lease without calling
     * `api.cleanup()`, and ALWAYS resumes GATT (`REQ-SET-118.1`, `.3`).
     */
    suspend fun <T> withGattYieldAndPolarApi(
        context: Context,
        macAddress: String?,
        polarId: String,
        features: Set<PolarBleApi.PolarBleSdkFeature>,
        timeoutMs: Long,
        apiOverride: PolarBleApi? = null,
        disconnectOnExit: Boolean = true,
        block: suspend (PolarBleApi) -> T
    ): T? {
        registerDeviceAliases(polarId, macAddress)
        val deviceManager = BANALService.getDeviceManager()
        val btleDevice = if (!macAddress.isNullOrEmpty()) {
            deviceManager?.getBtleDeviceByAddress(macAddress)
        } else {
            null
        }

        val activeBridgeSession = if (apiOverride == null) getActiveSession(polarId) else null
        val isSharedPersistentSession = activeBridgeSession != null
        btleDevice?.pauseGattOperations()
        return try {
            withTimeoutOrNull(timeoutMs) {
                var acquiredSharedApi = false
                val api = when {
                    apiOverride != null -> apiOverride
                    activeBridgeSession != null -> {
                        if (activeBridgeSession === synchronized(apiLock) { sharedApi }) {
                            acquiredSharedApi = true
                            acquirePolarApi(context)
                        } else {
                            activeBridgeSession
                        }
                    }
                    else -> {
                        acquiredSharedApi = true
                        acquirePolarApi(context)
                    }
                }
                try {
                    block(api)
                } finally {
                    if (!isSharedPersistentSession && disconnectOnExit) {
                        try {
                            api.disconnectFromDevice(polarId)
                        } catch (_: Exception) {
                            // Best-effort disconnect after PFTP exchange.
                        }
                    }
                    if (acquiredSharedApi) {
                        releasePolarApi(api)
                    }
                }
            }
        } finally {
            btleDevice?.resumeGattOperations()
        }
    }

    /**
     * Patches missing HR samples into `WorkoutSamples.db` when [baseFileName] and [hrPairs] are
     * non-empty, and idempotently records [SensorType.HR] into `AccumulatedSensors` strictly
     * when at least one row was patched (`REQ-TRK-084.4`).
     */
    fun patchMissingHeartRateAndRecordSensorType(
        appContext: Context,
        workoutId: Long,
        baseFileName: String?,
        anchorMs: Long,
        hrPairs: List<android.util.Pair<Long, Int>>
    ): Int {
        if (baseFileName.isNullOrEmpty() || hrPairs.isEmpty()) return 0
        val samplesManager = WorkoutSamplesDatabaseManager.getInstance(appContext)
        val patchedCount = samplesManager.patchMissingHeartRateSamples(baseFileName, anchorMs, hrPairs)
        if (patchedCount > 0) {
            recordBackfilledSensorTypes(appContext, workoutId, SensorType.HR)
        }
        return patchedCount
    }

    /**
     * Idempotently records [sensorTypes] into `AccumulatedSensors` after offline Polar backfill
     * completes so backfilled workouts retain accurate sensor metadata (`REQ-TRK-084.4`).
     */
    fun recordBackfilledSensorTypes(context: Context, workoutId: Long, vararg sensorTypes: SensorType) {
        if (workoutId <= 0L || sensorTypes.isEmpty()) return
        try {
            WorkoutRepository.getInstance(context).addAccumulatedSensorTypes(workoutId, sensorTypes.toSet())
        } catch (t: Throwable) {
            Log.w(TAG, "Non-fatal error recording backfilled sensor types for workout $workoutId: ${t.message}")
        }
    }
}
