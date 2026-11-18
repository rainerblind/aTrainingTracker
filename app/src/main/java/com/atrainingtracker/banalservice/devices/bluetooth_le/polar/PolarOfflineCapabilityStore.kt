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
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight SharedPreferences-backed store persisting Polar BLE GATT Service UUID capabilities
 * (`UUID_SERVICE_POLAR_PMD`, `UUID_SERVICE_POLAR_PFTP`), DIS (`0x180A`) hardware metadata
 * (`0x2A24` Model Number, `0x2A25` Serial Number / `PolarDeviceInfo.deviceId`), and derived
 * [PolarDeviceType] keyed by normalized BLE MAC address without requiring a SQLite schema bump
 * (`ATT-7923`, `ATT-8717`, `REQ-CON-049`).
 */
object PolarOfflineCapabilityStore {

    /**
     * Hardware-grounded Polar sensor classification persisted in [PolarOfflineCapabilityStore].
     */
    enum class PolarDeviceType {
        H10,
        VERITY_SENSE,
        UNKNOWN
    }

    private const val PREFS_NAME = "polar_offline_capabilities"
    private const val KEY_PREFIX = "offline_rec_"
    private const val KEY_PMD_PREFIX = "pmd_svc_"
    private const val KEY_PFTP_PREFIX = "pftp_svc_"
    private const val KEY_MODEL_PREFIX = "model_num_"
    private const val KEY_POLAR_ID_PREFIX = "polar_id_"
    private const val KEY_TYPE_PREFIX = "polar_type_"
    private val CANONICAL_POLAR_ID_REGEX = Regex("^[0-9A-Fa-f]{8}$")
    private val H10_TOKEN_REGEX = Regex("""\bh10\b""")
    private val OH1_TOKEN_REGEX = Regex("""\boh1\b""")

    private val memoryCache = ConcurrentHashMap<String, Boolean>()
    private val pmdCache = ConcurrentHashMap<String, Boolean>()
    private val pftpCache = ConcurrentHashMap<String, Boolean>()
    private val modelNumberCache = ConcurrentHashMap<String, String>()
    private val polarIdCache = ConcurrentHashMap<String, String>()
    private val deviceTypeCache = ConcurrentHashMap<String, PolarDeviceType>()

    /**
     * Legacy marker indicating [macAddress] confirmed Polar optical offline recording (`ATT-7923`).
     */
    @JvmStatic
    fun markOfflineRecordingCapable(context: Context?, macAddress: String?) {
        val normalized = normalizeMac(macAddress) ?: return
        memoryCache[normalized] = true
        getPrefs(context)?.edit()?.putBoolean(KEY_PREFIX + normalized, true)?.apply()
    }

    /**
     * Returns true if [macAddress] has legacy `offline_rec_` confirmation in store.
     */
    @JvmStatic
    fun hasOfflineRecordingCapability(context: Context?, macAddress: String?): Boolean {
        val normalized = normalizeMac(macAddress) ?: return false
        return memoryCache[normalized] == true || readStoredBoolean(context, KEY_PREFIX, normalized, memoryCache)
    }

    /**
     * Records observed Polar GATT service UUID presence (`PMD` / `PFTP`) independently of `offline_rec_`.
     */
    @JvmStatic
    fun recordGattServiceCapabilities(
        context: Context?,
        macAddress: String?,
        hasPmdService: Boolean,
        hasPftpService: Boolean
    ) {
        val normalized = normalizeMac(macAddress) ?: return
        pmdCache[normalized] = hasPmdService
        pftpCache[normalized] = hasPftpService
        getPrefs(context)?.edit()
            ?.putBoolean(KEY_PMD_PREFIX + normalized, hasPmdService)
            ?.putBoolean(KEY_PFTP_PREFIX + normalized, hasPftpService)
            ?.apply()
    }

    /**
     * Returns true if [macAddress] was observed exposing `UUID_SERVICE_POLAR_PMD`.
     */
    @JvmStatic
    fun hasPmdService(context: Context?, macAddress: String?): Boolean {
        val normalized = normalizeMac(macAddress) ?: return false
        return pmdCache[normalized] == true || readStoredBoolean(context, KEY_PMD_PREFIX, normalized, pmdCache)
    }

    /**
     * Returns true if [macAddress] was observed exposing `UUID_SERVICE_POLAR_PFTP`.
     */
    @JvmStatic
    fun hasPftpService(context: Context?, macAddress: String?): Boolean {
        val normalized = normalizeMac(macAddress) ?: return false
        return pftpCache[normalized] == true || readStoredBoolean(context, KEY_PFTP_PREFIX, normalized, pftpCache)
    }

    /**
     * Returns true if [macAddress] was observed exposing either Polar PMD or Polar PFTP GATT services.
     */
    @JvmStatic
    fun hasPolarServiceCapability(context: Context?, macAddress: String?): Boolean {
        return hasPmdService(context, macAddress) || hasPftpService(context, macAddress)
    }

    /**
     * Returns true when GATT discovery for [macAddress] explicitly recorded `hasPmdService = false`
     * and `hasPftpService = false` (confirmed non-Polar BLE HRM).
     */
    @JvmStatic
    fun isConfirmedNonPolarGatt(context: Context?, macAddress: String?): Boolean {
        val normalized = normalizeMac(macAddress) ?: return false
        val cachedPmd = pmdCache[normalized]
        val cachedPftp = pftpCache[normalized]
        if (cachedPmd == false && cachedPftp == false) {
            return true
        }
        val prefs = getPrefs(context) ?: return false
        val hasKeys = prefs.contains(KEY_PMD_PREFIX + normalized) && prefs.contains(KEY_PFTP_PREFIX + normalized)
        if (!hasKeys) return false
        val storedPmd = prefs.getBoolean(KEY_PMD_PREFIX + normalized, false).also { pmdCache[normalized] = it }
        val storedPftp = prefs.getBoolean(KEY_PFTP_PREFIX + normalized, false).also { pftpCache[normalized] = it }
        return !storedPmd && !storedPftp
    }

    /**
     * Returns true when [candidate] matches Polar's canonical 8-hex device identifier format (`^[0-9A-Fa-f]{8}$`).
     */
    @JvmStatic
    fun isValidCanonicalPolarId(candidate: String?): Boolean {
        val trimmed = candidate?.trim() ?: return false
        return CANONICAL_POLAR_ID_REGEX.matches(trimmed)
    }

    /**
     * Persists DIS (`0x180A`) hardware metadata ([modelNumber] `0x2A24` and/or [serialNumber] `0x2A25`)
     * for [macAddress] and derives [PolarDeviceType] when [modelNumber] identifies H10 or Verity Sense/OH1.
     *
     * Non-8-hex DIS serial strings are rejected and never overwrite an already-confirmed canonical 8-hex ID.
     */
    @JvmStatic
    @JvmOverloads
    fun recordDisMetadata(
        context: Context?,
        macAddress: String?,
        modelNumber: String? = null,
        serialNumber: String? = null
    ) {
        val normalized = normalizeMac(macAddress) ?: return
        val trimmedModel = modelNumber?.trim()?.takeIf { it.isNotEmpty() }
        val canonicalSerial = serialNumber?.trim()?.uppercase(Locale.ROOT)
            ?.takeIf { CANONICAL_POLAR_ID_REGEX.matches(it) }
        if (trimmedModel == null && canonicalSerial == null) return

        val editor = getPrefs(context)?.edit()
        if (trimmedModel != null) {
            modelNumberCache[normalized] = trimmedModel
            editor?.putString(KEY_MODEL_PREFIX + normalized, trimmedModel)
            val derived = classifyModelNumber(trimmedModel)
            if (derived != PolarDeviceType.UNKNOWN) {
                deviceTypeCache[normalized] = derived
                editor?.putString(KEY_TYPE_PREFIX + normalized, derived.name)
            }
        }
        if (canonicalSerial != null) {
            polarIdCache[normalized] = canonicalSerial
            editor?.putString(KEY_POLAR_ID_PREFIX + normalized, canonicalSerial)
            PolarOfflineGattCoordinator.registerDeviceAliases(canonicalSerial, normalized)
        }
        editor?.apply()
    }

    /**
     * Persists an SDK-confirmed [PolarDeviceType] (e.g. from `FEATURE_POLAR_H10_EXERCISE_RECORDING`
     * vs. `FEATURE_POLAR_OFFLINE_RECORDING`) and optional canonical 8-hex [polarDeviceId] for [macAddress].
     */
    @JvmStatic
    @JvmOverloads
    fun recordSdkDeviceType(
        context: Context?,
        macAddress: String?,
        deviceType: PolarDeviceType,
        polarDeviceId: String? = null
    ) {
        val normalized = normalizeMac(macAddress) ?: return
        val editor = getPrefs(context)?.edit()
        if (deviceType != PolarDeviceType.UNKNOWN) {
            deviceTypeCache[normalized] = deviceType
            editor?.putString(KEY_TYPE_PREFIX + normalized, deviceType.name)
            if (deviceType == PolarDeviceType.VERITY_SENSE) {
                memoryCache[normalized] = true
                editor?.putBoolean(KEY_PREFIX + normalized, true)
            }
        }
        val normalizedPolarId = polarDeviceId?.trim()?.uppercase(Locale.ROOT)
            ?.takeIf { CANONICAL_POLAR_ID_REGEX.matches(it) }
        if (normalizedPolarId != null) {
            polarIdCache[normalized] = normalizedPolarId
            editor?.putString(KEY_POLAR_ID_PREFIX + normalized, normalizedPolarId)
            PolarOfflineGattCoordinator.registerDeviceAliases(normalizedPolarId, normalized)
        }
        editor?.apply()
    }

    /**
     * Returns the persisted DIS `0x2A24` model number string for [macAddress], or `null` if unknown.
     */
    @JvmStatic
    fun getModelNumber(context: Context?, macAddress: String?): String? {
        val normalized = normalizeMac(macAddress) ?: return null
        modelNumberCache[normalized]?.let { return it }
        val stored = getPrefs(context)?.getString(KEY_MODEL_PREFIX + normalized, null)
            ?.trim()?.takeIf { it.isNotEmpty() }
        if (stored != null) {
            modelNumberCache[normalized] = stored
        }
        return stored
    }

    /**
     * Returns the persisted canonical Polar device ID (`0x2A25` / `PolarDeviceInfo.deviceId`)
     * for [macAddress], or `null` if not yet hydrated.
     */
    @JvmStatic
    fun getPolarDeviceId(context: Context?, macAddress: String?): String? {
        val normalized = normalizeMac(macAddress) ?: return null
        polarIdCache[normalized]?.takeIf { CANONICAL_POLAR_ID_REGEX.matches(it) }?.let { return it }
        val stored = getPrefs(context)?.getString(KEY_POLAR_ID_PREFIX + normalized, null)
            ?.trim()?.uppercase(Locale.ROOT)?.takeIf { CANONICAL_POLAR_ID_REGEX.matches(it) }
        if (stored != null) {
            polarIdCache[normalized] = stored
        }
        return stored
    }

    /**
     * Returns the hardware-confirmed [PolarDeviceType] for [macAddress], deriving from persisted
     * `0x2A24` model number when explicit type has not yet been written, or [PolarDeviceType.UNKNOWN].
     */
    @JvmStatic
    fun getDeviceType(context: Context?, macAddress: String?): PolarDeviceType {
        val normalized = normalizeMac(macAddress) ?: return PolarDeviceType.UNKNOWN
        if (isConfirmedNonPolarGatt(context, normalized)) {
            return PolarDeviceType.UNKNOWN
        }
        deviceTypeCache[normalized]?.let { return it }
        val storedTypeName = getPrefs(context)?.getString(KEY_TYPE_PREFIX + normalized, null)
        if (!storedTypeName.isNullOrBlank()) {
            val parsed = try {
                PolarDeviceType.valueOf(storedTypeName)
            } catch (_: IllegalArgumentException) {
                PolarDeviceType.UNKNOWN
            }
            if (parsed != PolarDeviceType.UNKNOWN) {
                deviceTypeCache[normalized] = parsed
                return parsed
            }
        }
        val model = getModelNumber(context, normalized)
        if (model != null) {
            val derived = classifyModelNumber(model)
            if (derived != PolarDeviceType.UNKNOWN) {
                deviceTypeCache[normalized] = derived
                return derived
            }
        }
        return PolarDeviceType.UNKNOWN
    }

    /**
     * Classifies a DIS `0x2A24` Model Number string into [PolarDeviceType].
     *
     * Requires explicit Polar or unambiguous model tokens (`"polar h10"` / `"h10"`,
     * `"verity"` / `"polar sense"` / `"oh1"`) and never matches bare `"sense"` (e.g., `"CardioSense"`).
     */
    @JvmStatic
    fun classifyModelNumber(modelNumber: String?): PolarDeviceType {
        val lower = modelNumber?.trim()?.lowercase(Locale.ROOT) ?: return PolarDeviceType.UNKNOWN
        if (lower.isEmpty()) return PolarDeviceType.UNKNOWN
        return when {
            lower.contains("polar h10") || H10_TOKEN_REGEX.containsMatchIn(lower) -> PolarDeviceType.H10
            lower.contains("verity") ||
                lower.contains("polar sense") ||
                OH1_TOKEN_REGEX.containsMatchIn(lower) -> PolarDeviceType.VERITY_SENSE
            else -> PolarDeviceType.UNKNOWN
        }
    }

    private fun readStoredBoolean(
        context: Context?,
        prefix: String,
        normalizedMac: String,
        cache: ConcurrentHashMap<String, Boolean>
    ): Boolean {
        val prefs = getPrefs(context) ?: return false
        if (!prefs.contains(prefix + normalizedMac)) return false
        val stored = prefs.getBoolean(prefix + normalizedMac, false)
        cache[normalizedMac] = stored
        return stored
    }

    private fun getPrefs(context: Context?): SharedPreferences? {
        val appContext = context?.applicationContext ?: return null
        return appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @VisibleForTesting
    fun resetForTesting() {
        memoryCache.clear()
        pmdCache.clear()
        pftpCache.clear()
        modelNumberCache.clear()
        polarIdCache.clear()
        deviceTypeCache.clear()
    }

    private fun normalizeMac(macAddress: String?): String? {
        val trimmed = macAddress?.trim()?.uppercase(Locale.ROOT)
        return if (trimmed.isNullOrEmpty()) null else trimmed
    }
}
