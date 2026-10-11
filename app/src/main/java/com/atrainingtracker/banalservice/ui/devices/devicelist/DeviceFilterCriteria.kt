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

package com.atrainingtracker.banalservice.ui.devices.devicelist

import androidx.compose.runtime.Immutable
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import org.json.JSONArray
import org.json.JSONObject

/**
 * Filter mode for peripheral and smartphone sensor battery levels.
 */
enum class BatteryFilter {
    ALL,
    CRITICAL_ONLY, // Battery percentage in 0..20%
    NORMAL         // Battery percentage > 20%
}

/**
 * Immutable domain criteria model representing multi-dimensional sensor filtering.
 *
 * Supports filtering by wireless protocol (ANT+, Bluetooth LE), sensor categories (Set of DeviceType),
 * and battery level (All, Critical <= 20%, Normal > 20%).
 */
@Immutable
data class DeviceFilterCriteria(
    val protocol: Protocol? = null,
    val deviceTypes: Set<DeviceType> = emptySet(),
    val batteryFilter: BatteryFilter = BatteryFilter.ALL
) {
    /**
     * Total count of distinct active filter dimensions.
     */
    val activeFilterCount: Int
        get() {
            var count = 0
            if (protocol != null && protocol != Protocol.ALL) count++
            if (deviceTypes.isNotEmpty()) count++
            if (batteryFilter != BatteryFilter.ALL) count++
            return count
        }

    /**
     * Whether no filter criteria are currently active.
     */
    val isEmpty: Boolean
        get() = activeFilterCount == 0

    /**
     * Whether at least one filter criterion is active.
     */
    val isNotEmpty: Boolean
        get() = !isEmpty

    /**
     * Evaluates whether a given [DeviceUiData] satisfies all active filter criteria.
     */
    fun matches(device: DeviceUiData): Boolean {
        // 1. Protocol match
        if (protocol != null && protocol != Protocol.ALL) {
            if (device.protocol != protocol) return false
        }

        // 2. Category / DeviceType match
        if (deviceTypes.isNotEmpty()) {
            if (!deviceTypes.contains(device.deviceType)) return false
        }

        // 3. Battery level match
        when (batteryFilter) {
            BatteryFilter.ALL -> {}
            BatteryFilter.CRITICAL_ONLY -> {
                // Must be in 0..20; unknown battery (-1) is rejected
                if (device.batteryPercentage !in 0..20) return false
            }
            BatteryFilter.NORMAL -> {
                // Normal requires battery > 20
                if (device.batteryPercentage <= 20) return false
            }
        }

        return true
    }

    /**
     * Serializes criteria instance to a compact JSON string.
     */
    fun toJson(): String {
        val json = JSONObject()
        protocol?.let { if (it != Protocol.ALL) json.put("protocol", it.name) }
        if (deviceTypes.isNotEmpty()) {
            val array = JSONArray()
            deviceTypes.forEach { array.put(it.name) }
            json.put("deviceTypes", array)
        }
        if (batteryFilter != BatteryFilter.ALL) {
            json.put("batteryFilter", batteryFilter.name)
        }
        return json.toString()
    }

    companion object {
        /**
         * Reconstructs [DeviceFilterCriteria] from JSON string representation.
         */
        fun fromJson(jsonStr: String?): DeviceFilterCriteria {
            if (jsonStr.isNullOrBlank()) return DeviceFilterCriteria()
            return try {
                val json = JSONObject(jsonStr)
                val proto = if (json.has("protocol")) {
                    runCatching { Protocol.valueOf(json.getString("protocol")) }.getOrNull()
                } else null

                val types = mutableSetOf<DeviceType>()
                if (json.has("deviceTypes")) {
                    val array = json.getJSONArray("deviceTypes")
                    for (i in 0 until array.length()) {
                        runCatching { DeviceType.valueOf(array.getString(i)) }.getOrNull()?.let {
                            types.add(it)
                        }
                    }
                }

                val bat = if (json.has("batteryFilter")) {
                    runCatching { BatteryFilter.valueOf(json.getString("batteryFilter")) }.getOrDefault(BatteryFilter.ALL)
                } else BatteryFilter.ALL

                DeviceFilterCriteria(
                    protocol = proto,
                    deviceTypes = types,
                    batteryFilter = bat
                )
            } catch (_: Exception) {
                DeviceFilterCriteria()
            }
        }
    }
}
