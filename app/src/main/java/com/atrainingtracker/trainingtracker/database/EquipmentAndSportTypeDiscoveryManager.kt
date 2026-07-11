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

package com.atrainingtracker.trainingtracker.database

import android.content.Context
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager

/**
 * High-level manager to coordinate lookups across multiple database helpers.
 */
class EquipmentAndSportTypeDiscoveryManager private constructor(context: Context) {

    val activeDevicesHelper = ActiveDevicesDbHelper(context)
    val equipmentDbHelper = EquipmentDbHelper(context)
    val sportTypeEquipmentLinkHelper = SportTypeEquipmentLinkManager.getInstance(context)
    val sportTypeManager = SportTypeDatabaseManager.getInstance(context)
    val workoutSummariesManager = WorkoutSummariesDatabaseManager.getInstance(context)

    data class InferredIdentity(
        val sportId: Long,
        val bSportType: BSportType,
        val equipmentId: Long,
        val stravaSportName: String?,
        val uploadToStrava: Int,
        val isHighConfidence: Boolean
    )

    companion object {
        @Volatile
        private var INSTANCE: EquipmentAndSportTypeDiscoveryManager? = null

        @JvmStatic
        fun getInstance(context: Context): EquipmentAndSportTypeDiscoveryManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: EquipmentAndSportTypeDiscoveryManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    /**
     * Returns all linked equipment ids for a given set of active devices
     */
    fun getLinkedEquipmentIds(activeDeviceIds: Set<Long>): Set<Long> {
        if (activeDeviceIds.isEmpty()) return emptySet()

        return activeDeviceIds.flatMap { deviceId ->
            equipmentDbHelper.getLinkedEquipmentIdsFromDeviceId(deviceId)
        }.toSet()
    }

    /**
     *  Returns the names of all equipment linked to the active devices.
     */
    fun getLinkedEquipmentNames(activeDeviceIds: Set<Long>): Set<String> {
        val equipmentIds = getLinkedEquipmentIds(activeDeviceIds)
        if (equipmentIds.isEmpty()) return emptySet()

        return equipmentIds.mapNotNull { id ->
            equipmentDbHelper.getEquipmentNameFromId(id)
        }.toSet()
    }

    /**
     * Returns all linked equipment ids for a workoutId
     */
    fun getLinkedEquipmentIds(workoutId: Long): Set<Long> {
        val activeDeviceIds = activeDevicesHelper.getDatabaseIdsOfActiveDevices(workoutId)
        return getLinkedEquipmentIds(activeDeviceIds.toSet())
    }

    /**
     * Helper for workout-based lookup of equipment names
     */
    fun getLinkedEquipmentNames(workoutId: Long): Set<String> {
        val activeDeviceIds = activeDevicesHelper.getDatabaseIdsOfActiveDevices(workoutId).toSet()
        return getLinkedEquipmentNames(activeDeviceIds)
    }

    /**
     * Returns all SportType IDs linked to a set of active devices.
     * Logic: Devices -> Equipment -> SportTypes
     */
    fun getLinkedSportTypeIds(activeDeviceIds: Set<Long>): Set<Long> {
        val linkedEquipmentIds = getLinkedEquipmentIds(activeDeviceIds)
        if (linkedEquipmentIds.isEmpty()) return emptySet()

        return linkedEquipmentIds.flatMap { equipmentId ->
            sportTypeEquipmentLinkHelper.getSportTypeIdsForEquipment(equipmentId)
        }.toSet()
    }

    /**
    * Returns the names of all SportTypes linked to the active devices.
    */
    fun getLinkedSportTypeNames(activeDeviceIds: Set<Long>): Set<String> {
        val sportTypeIds = getLinkedSportTypeIds(activeDeviceIds)
        if (sportTypeIds.isEmpty()) return emptySet()

        return sportTypeIds.mapNotNull { id ->
            sportTypeManager.getUIName(id)
        }.toSet()
    }

    /**
     * Returns all SportType IDs linked to a specific workout.
     */
    fun getLinkedSportTypeIds(workoutId: Long): Set<Long> {
        val activeDeviceIds = activeDevicesHelper.getDatabaseIdsOfActiveDevices(workoutId)
        return getLinkedSportTypeIds(activeDeviceIds.toSet())
    }

    /**
     * Helper for workout-based lookup of SportType names
     */
    fun getLinkedSportTypeNames(workoutId: Long): Set<String> {
        val activeDeviceIds = activeDevicesHelper.getDatabaseIdsOfActiveDevices(workoutId).toSet()
        if (activeDeviceIds.isEmpty()) return emptySet()

        return getLinkedSportTypeNames(activeDeviceIds)
    }


    /**
     * Returns all SportType IDs that match to a given BSportType and average speed.
     */
    fun getSpeedBasedSportTypeIds(bSportType: BSportType, averageSpeed: Double): Set<Long> {
        return sportTypeManager.getSportTypesIdList(bSportType, averageSpeed).toSet()
    }

    fun getSpeedBasedSportTypeNames(bSportType: BSportType?, averageSpeed: Double): Set<String> {
        if (bSportType == null) return emptySet()

        val sportTypeIds = getSpeedBasedSportTypeIds(bSportType, averageSpeed)
        if (sportTypeIds.isEmpty()) return emptySet()

        return sportTypeIds.mapNotNull { id ->
            sportTypeManager.getUIName(id)
        }.toSet()
    }

    fun getEquipmentNamesForSport(sportName: String): Set<String> {
        val sportId = sportTypeManager.getSportTypeIdFromUIName(sportName)
        if (sportId == -1L) return emptySet()

        val equipmentIds = sportTypeEquipmentLinkHelper.getEquipmentIdsForSport(sportId)
        return equipmentIds.mapNotNull { id ->
            equipmentDbHelper.getEquipmentNameFromId(id)
        }.toSet()
    }

    fun getEquipmentNamesForSports(sportNames: Set<String>): Set<String> {
        if (sportNames.isEmpty()) return emptySet()

        return sportNames.mapNotNull { sportName ->
            getEquipmentNamesForSport(sportName)
        }.flatten().toSet()
    }



    fun getSportNamesForEquipment(equipmentName: String): Set<String> {
        val equipmentId = equipmentDbHelper.getEquipmentId(equipmentName)
        if (equipmentId == -1L) return emptySet()

        val sportTypeIds = sportTypeEquipmentLinkHelper.getSportTypeIdsForEquipment(equipmentId)
        return sportTypeIds.mapNotNull { id ->
            sportTypeManager.getUIName(id)
        }.toSet()
    }

    /**
     * Returns the names of all SportTypes linked to a list of equipment names.
     */
    fun getSportNamesForEquipmentList(equipmentNames: List<String>): List<String> {
        return equipmentNames.flatMap { getSportNamesForEquipment(it) }.distinct()
    }


    /**
     * Resolves the "Best" SportType ID by cross-referencing sensors and speed.
     */
    fun resolveSportType(
        activeDeviceIds: Set<Long>,
        bSportType: BSportType,
        averageSpeed: Double
    ): Long {
        val candidatesFromDevices = getLinkedSportTypeIds(activeDeviceIds)

        // first, the most simple case:  Everything is fine and the sport type is unique
        if (candidatesFromDevices.size == 1) {
            return candidatesFromDevices.first()
        }

        val candidatesFromAverageSpeed = getSpeedBasedSportTypeIds(bSportType, averageSpeed)

        // 1. Get the intersection of devices and speed
        val candidates = candidatesFromDevices intersect candidatesFromAverageSpeed

        return when {
            // Perfect match: Both sensors and speed-based guess point to the same sport(s)
            candidates.isNotEmpty() -> candidates.first()

            // Disagreement: Sensors found something, but speed doesn't match
            // Trust the hardware sensors over the speed-based guess
            candidatesFromDevices.isNotEmpty() -> candidatesFromDevices.first()

            // Fallback: No hardware links, use the speed-based guess
            candidatesFromAverageSpeed.isNotEmpty() -> candidatesFromAverageSpeed.first()

            // Absolute Fallback: First of BSportType based
            else -> sportTypeManager.getSportTypesIdList(bSportType).first()
        }
    }

    /**
     * Return the first SportType ID that is linked to a set of active device or the first of the given BSportType.
     */
    fun resolveSportType(activeDeviceIds: Set<Long>, bSportType: BSportType): Long {
        if (getLinkedSportTypeIds(activeDeviceIds).isNotEmpty()) {
            return getLinkedSportTypeIds(activeDeviceIds).first()
        }
        else {
            return sportTypeManager.getSportTypesIdList(bSportType).first()
        }
    }

    /**
     * Resolves identity using in-memory active devices for immediate accuracy in TrackerService.
     */
    fun resolveIdentity(deviceIds: Set<Long>, bSportType: BSportType, averageSpeed: Double): InferredIdentity {
        val linkedEquipmentIds = getLinkedEquipmentIds(deviceIds)

        var sportId: Long = -1
        var equipmentId: Long = -1
        var isHighConfidence = false

        if (linkedEquipmentIds.size == 1) {
            equipmentId = linkedEquipmentIds.first()
            isHighConfidence = true
            val linkedSportIds = sportTypeEquipmentLinkHelper.getSportTypeIdsForEquipment(equipmentId)
            sportId = if (linkedSportIds.size == 1) {
                linkedSportIds.first()
            } else {
                val candidatesFromSpeed = getSpeedBasedSportTypeIds(bSportType, averageSpeed)
                val intersect = linkedSportIds intersect candidatesFromSpeed
                if (intersect.isNotEmpty()) intersect.first() else linkedSportIds.first()
            }
        } else {
            sportId = resolveSportType(deviceIds, bSportType, averageSpeed)
            val gearCandidates = sportTypeEquipmentLinkHelper.getEquipmentIdsForSport(sportId)
            if (gearCandidates.size == 1) {
                equipmentId = gearCandidates.first()
            }
        }

        val stravaSportName = sportTypeManager.getStravaName(sportId)

        return InferredIdentity(
            sportId = sportId,
            bSportType = sportTypeManager.getBSportType(sportId),
            equipmentId = equipmentId,
            stravaSportName = stravaSportName,
            uploadToStrava = if (stravaSportName != null) 1 else 0,
            isHighConfidence = isHighConfidence
        )
    }

    /**
     *  Determines the best identity (Sport, Equipment, Strava) for a workout.
     *  Implements the arbitration between hardware sensors and route clusters (SCRUM-200).
     */
    fun resolveIdentity(workoutId: Long, bSportType: BSportType, averageSpeed: Double): InferredIdentity {
        val activeDeviceIds = activeDevicesHelper.getDatabaseIdsOfActiveDevices(workoutId).toSet()
        return resolveIdentity(activeDeviceIds, bSportType, averageSpeed)
    }

    /**
     * Helper to infer identity purely from a sportId (e.g. during manual edit/dialog).
     */
    fun inferIdentityFromSport(sportId: Long): InferredIdentity {
        val sportName = sportTypeManager.getUIName(sportId)
        val linkedEquipment = getEquipmentNamesForSport(sportName)
        val bestEquipmentName = if (linkedEquipment.size == 1) linkedEquipment.first() else null
        val bestEquipmentId = if (bestEquipmentName != null) equipmentDbHelper.getEquipmentId(bestEquipmentName) else -1L

        val stravaSportName = sportTypeManager.getStravaName(sportId)

        return InferredIdentity(
            sportId = sportId,
            bSportType = sportTypeManager.getBSportType(sportId),
            equipmentId = bestEquipmentId,
            stravaSportName = stravaSportName,
            uploadToStrava = if (stravaSportName != null) 1 else 0,
            isHighConfidence = false
        )
    }
}
