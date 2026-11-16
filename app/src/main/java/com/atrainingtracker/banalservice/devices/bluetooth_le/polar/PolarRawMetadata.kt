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

import org.json.JSONObject

/**
 * Metadata header for Polar raw telemetry sidecars (REQ-SET-117.2, ATT-8019).
 */
data class PolarRawMetadata(
    val schemaVersion: Int = 1,
    val appVersion: String = "5.0.0",
    val sensorModel: String = "",
    val firmwareVersion: String = "",
    val streamMode: String = "LIVE", // "LIVE" or "OFFLINE"
    val accRateHz: Int = 0,
    val gyroRateHz: Int = 0,
    val sportId: Int = 0,
    val poolLengthM: Float? = null
) {
    fun toJsonString(): String {
        val json = JSONObject()
        json.put("schemaVersion", schemaVersion)
        json.put("appVersion", appVersion)
        json.put("sensorModel", sensorModel)
        json.put("firmwareVersion", firmwareVersion)
        json.put("streamMode", streamMode)
        json.put("accRateHz", accRateHz)
        json.put("gyroRateHz", gyroRateHz)
        json.put("sportId", sportId)
        if (poolLengthM != null) {
            json.put("poolLength_m", poolLengthM.toDouble())
        }
        return json.toString()
    }

    companion object {
        fun fromJsonString(jsonStr: String): PolarRawMetadata {
            val json = JSONObject(jsonStr)
            return PolarRawMetadata(
                schemaVersion = json.optInt("schemaVersion", 1),
                appVersion = json.optString("appVersion", "5.0.0"),
                sensorModel = json.optString("sensorModel", ""),
                firmwareVersion = json.optString("firmwareVersion", ""),
                streamMode = json.optString("streamMode", "LIVE"),
                accRateHz = json.optInt("accRateHz", 0),
                gyroRateHz = json.optInt("gyroRateHz", 0),
                sportId = json.optInt("sportId", 0),
                poolLengthM = if (json.has("poolLength_m")) json.optDouble("poolLength_m").toFloat() else null
            )
        }
    }
}
