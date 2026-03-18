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
}