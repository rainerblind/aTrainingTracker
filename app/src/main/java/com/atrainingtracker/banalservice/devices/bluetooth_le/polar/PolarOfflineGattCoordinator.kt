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
import com.atrainingtracker.banalservice.devices.DeviceType
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

/**
 * Shared GATT arbitration, Polar ID resolution, device discovery helpers, and SDK feature
 * synchronization reused across [PolarH10OfflineController], [PolarVeritySenseOfflineController],
 * and [PolarOnlineStreamingBridge] (`ATT-7923`, `ATT-8016`).
 */
@Suppress("TooGenericExceptionCaught")
object PolarOfflineGattCoordinator {

    private const val TAG = "PolarOfflineGattCoord"
    const val FEATURE_READY_TIMEOUT_MS = 10_000L
    const val ACC_OWNER_ONLINE = "ONLINE"
    const val ACC_OWNER_OFFLINE = "OFFLINE"
    private val POLAR_ID_REGEX = Regex("""\b([0-9A-Fa-f]{8})\b""")
    private val VERITY_MODEL_TOKENS = listOf(
        "polar sense",
        "verity sense",
        "polar verity",
        "polar oh1"
    )

    private val activeSessions = ConcurrentHashMap<String, PolarBleApi>()
    private val activeSessionCallbacks = ConcurrentHashMap<String, PolarBleApiCallbackProvider>()
    private val activeAccOwners = ConcurrentHashMap<String, String>()

    private fun normalizeId(polarId: String): String = polarId.uppercase(Locale.ROOT)

    /**
     * Registers a persistent [PolarBleApi] session (e.g. from [PolarOnlineStreamingBridge]) so
     * offline PFTP operations reuse the single GATT connection instead of opening a competing client.
     */
    @JvmStatic
    fun registerActiveSession(
        polarId: String,
        api: PolarBleApi,
        callback: PolarBleApiCallbackProvider? = null
    ) {
        val key = normalizeId(polarId)
        activeSessions[key] = api
        if (callback != null) {
            activeSessionCallbacks[key] = callback
        }
    }

    /**
     * Unregisters an active [PolarBleApi] session on shutdown (`ATT-8016`).
     */
    @JvmStatic
    fun unregisterActiveSession(polarId: String, api: PolarBleApi? = null) {
        val key = normalizeId(polarId)
        if (api == null) {
            activeSessions.remove(key)
            activeSessionCallbacks.remove(key)
        } else {
            if (activeSessions.remove(key, api)) {
                activeSessionCallbacks.remove(key)
            }
        }
    }

    /**
     * Returns the persistent [PolarBleApi] session for [polarId], or `null` if none is active.
     */
    @JvmStatic
    fun getActiveSession(polarId: String): PolarBleApi? {
        if (polarId.isBlank()) return null
        return activeSessions[normalizeId(polarId)]
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
    fun resetForTesting() {
        activeSessions.clear()
        activeSessionCallbacks.clear()
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
        return POLAR_ID_REGEX.find(deviceName)?.value ?: macAddress
    }

    /**
     * Returns true if [name] / [manufacturer] identifies a Polar H10 chest strap.
     */
    @JvmStatic
    fun isPolarH10Name(name: String, manufacturer: String = ""): Boolean {
        val lowerName = name.lowercase(Locale.ROOT)
        val lowerMfg = manufacturer.lowercase(Locale.ROOT)
        return lowerName.contains("polar h10") ||
            (lowerName.contains("h10") && (lowerName.contains("polar") || lowerMfg.contains("polar")))
    }

    /**
     * Returns true if [name] / [manufacturer] identifies a Polar optical sensor using `PolarOfflineRecordingApi`.
     */
    @JvmStatic
    fun isPolarVeritySenseName(name: String, manufacturer: String = ""): Boolean {
        val lowerName = name.lowercase(Locale.ROOT)
        val lowerMfg = manufacturer.lowercase(Locale.ROOT)
        val isPolarBrand = lowerName.contains("polar") || lowerMfg.contains("polar")
        val matchesKnownModel = VERITY_MODEL_TOKENS.any { lowerName.contains(it) }
        val matchesOpticalSuffix = isPolarBrand && (lowerName.contains("verity") || lowerName.contains("oh1"))
        return matchesKnownModel || matchesOpticalSuffix
    }

    /**
     * Returns true for Polar Verity Sense / OH1 sensors or other non-H10 Polar HRMs confirmed to
     * expose `FEATURE_POLAR_OFFLINE_RECORDING` / PFTP in [PolarOfflineCapabilityStore].
     */
    @JvmStatic
    fun isPolarVeritySenseDevice(
        context: Context?,
        name: String,
        manufacturer: String = "",
        macAddress: String? = null
    ): Boolean {
        val isH10 = isPolarH10Name(name, manufacturer)
        val isKnownModel = isPolarVeritySenseName(name, manufacturer)
        val hasCap = PolarOfflineCapabilityStore.hasOfflineRecordingCapability(context, macAddress)
        return !isH10 && (isKnownModel || (hasCap && name.isNotEmpty()))
    }

    /**
     * Finds the first paired BLE HRM device matching [namePredicate] that has offline backup recording enabled.
     */
    fun findEnabledPolarDevice(
        context: Context,
        namePredicate: (name: String, macAddress: String) -> Boolean
    ): PolarTargetDevice? {
        val db = DevicesDatabaseManager.getInstance(context)
        val devices = db.allDevicesForCache ?: return null
        return devices.asSequence()
            .filter { it.type == DeviceType.HRM && it.protocol == Protocol.BLUETOOTH_LE }
            .mapNotNull { dev -> buildTargetIfEnabled(db, dev.id, dev.name ?: "", namePredicate) }
            .firstOrNull()
    }

    /**
     * Resolves the paired Bluetooth MAC address for a stored Polar 8-hex [polarId] or MAC string (`ATT-7924`).
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
                val matches = mac != null &&
                    (mac.equals(polarId, ignoreCase = true) || name.contains(polarId, ignoreCase = true))
                if (matches) mac else null
            }
            ?.firstOrNull()
    }

    private fun buildTargetIfEnabled(
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
            PolarTargetDevice(
                deviceId = deviceId,
                name = name,
                macAddress = mac,
                polarId = extractPolarId(name, mac)
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

    private suspend fun awaitFeaturesViaCallback(
        api: PolarBleApi,
        polarId: String,
        requiredFeatures: Set<PolarBleApi.PolarBleSdkFeature>,
        timeoutMs: Long
    ): Boolean {
        val readyFeatures = ConcurrentHashMap.newKeySet<PolarBleApi.PolarBleSdkFeature>()
        requiredFeatures.filterTo(readyFeatures) { api.isFeatureReady(polarId, it) }

        val existingCallback = activeSessionCallbacks[normalizeId(polarId)]
        val deferred = CompletableDeferred<Boolean>()
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
                if (identifier.equals(polarId, ignoreCase = true) || identifier == polarId) {
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
     * Yields standard `MyBTLEDevice` GATT operations for [macAddress], reuses an active persistent
     * [PolarBleApi] session when present (or opens a transient client / uses [apiOverride]),
     * executes [block] bounded by [timeoutMs], cleans up only transient sessions, and ALWAYS resumes GATT.
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
        val deviceManager = BANALService.getDeviceManager()
        val btleDevice = if (!macAddress.isNullOrEmpty()) {
            deviceManager?.getBtleDeviceByAddress(macAddress)
        } else {
            null
        }

        val sharedApi = if (apiOverride == null) getActiveSession(polarId) else null
        val isSharedPersistentSession = sharedApi != null
        btleDevice?.pauseGattOperations()
        return try {
            withTimeoutOrNull(timeoutMs) {
                val api = apiOverride ?: sharedApi ?: PolarBleApiDefaultImpl.defaultImplementation(context, features)
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
                    if (apiOverride == null && !isSharedPersistentSession) {
                        try {
                            api.cleanup()
                        } catch (t: Throwable) {
                            Log.w(TAG, "Error cleaning up PolarBleApi: ${t.message}")
                        }
                    }
                }
            }
        } finally {
            btleDevice?.resumeGattOperations()
        }
    }
}
