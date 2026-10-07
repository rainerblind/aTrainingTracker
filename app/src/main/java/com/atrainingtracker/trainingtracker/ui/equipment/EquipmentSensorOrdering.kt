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

package com.atrainingtracker.trainingtracker.ui.equipment

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager.SimpleSensorInfo
import com.atrainingtracker.banalservice.devices.DeviceType

/**
 * Pure domain ordering utility for equipment sensors (REQ-UI-283, TST-UI-243, ATT-2464).
 *
 * Enforces sport-specific sensor precedence:
 * - Bikes: Cycling-specific sensors first (Power -> Speed & Cadence -> Speed -> Cadence -> other bike),
 *   followed by shared/generic sensors (HRM -> Environment -> other shared).
 * - Shoes: Running-specific sensors first (Run Speed / Footpod -> other run),
 *   followed by shared/generic sensors (HRM -> Environment -> other shared).
 * - Secondary sort: Case-insensitive alphabetical by name.
 * - Tertiary sort: ID ascending.
 */
object EquipmentSensorOrdering {

    /**
     * Determines the integer priority rank for a given [DeviceType] within the context of [BSportType].
     * Lower numbers indicate higher priority (appear first).
     */
    @JvmStatic
    fun getPriorityRank(deviceType: DeviceType?, sportType: BSportType): Int {
        if (deviceType == null) return 200

        return when (sportType) {
            BSportType.BIKE -> when (deviceType) {
                DeviceType.BIKE_POWER -> 10
                DeviceType.BIKE_SPEED_AND_CADENCE -> 20
                DeviceType.BIKE_SPEED -> 30
                DeviceType.BIKE_CADENCE -> 40
                else -> if (DevicesDatabaseManager.isBikeSensor(deviceType)) {
                    50
                } else if (deviceType == DeviceType.HRM) {
                    100
                } else if (deviceType == DeviceType.ENVIRONMENT) {
                    110
                } else if (DevicesDatabaseManager.isSharedSensor(deviceType)) {
                    120
                } else {
                    200
                }
            }
            BSportType.RUN -> when (deviceType) {
                DeviceType.RUN_SPEED -> 10
                else -> if (DevicesDatabaseManager.isRunSensor(deviceType)) {
                    20
                } else if (deviceType == DeviceType.HRM) {
                    100
                } else if (deviceType == DeviceType.ENVIRONMENT) {
                    110
                } else if (DevicesDatabaseManager.isSharedSensor(deviceType)) {
                    120
                } else {
                    200
                }
            }
            else -> 200
        }
    }

    /**
     * Creates a deterministic [Comparator] for [SimpleSensorInfo] based on the requested [sportType].
     */
    @JvmStatic
    fun getComparator(sportType: BSportType): Comparator<SimpleSensorInfo> {
        return Comparator { s1, s2 ->
            if (s1 == null && s2 == null) return@Comparator 0
            if (s1 == null) return@Comparator 1
            if (s2 == null) return@Comparator -1

            val rank1 = getPriorityRank(s1.deviceType, sportType)
            val rank2 = getPriorityRank(s2.deviceType, sportType)
            if (rank1 != rank2) {
                return@Comparator rank1.compareTo(rank2)
            }

            val name1 = s1.name ?: ""
            val name2 = s2.name ?: ""
            val nameCompare = String.CASE_INSENSITIVE_ORDER.compare(name1, name2)
            if (nameCompare != 0) {
                return@Comparator nameCompare
            }

            s1.id.compareTo(s2.id)
        }
    }

    /**
     * Sorts the given list of sensors deterministically according to sport-specific priority rules.
     */
    @JvmStatic
    fun sortSensors(sensors: List<SimpleSensorInfo>, sportType: BSportType): List<SimpleSensorInfo> {
        return sensors.sortedWith(getComparator(sportType))
    }
}
