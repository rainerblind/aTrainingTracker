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

package com.atrainingtracker.trainingtracker.settings

import java.io.Serializable

/**
 * Data class representing the full configurable threshold state and display options
 * for an athletic training zone profile (REQ-UI-428).
 */
data class ZoneProfileState(
    val type: SettingsDataStore.ZoneType,
    val z1: Int,
    val z2: Int,
    val z3: Int,
    val z4: Int,
    val z5: Int = 0,
    val z6: Int = 0,
    val displayOptions: ZoneDisplayOptions = ZoneDisplayOptions()
) : Serializable {

    /**
     * Returns true if all threshold cutoffs in this profile strictly increase monotonically (REQ-UI-428.2).
     */
    fun isMonotonic(): Boolean {
        return if (type == SettingsDataStore.ZoneType.PWR_BIKE) {
            z1 > 0 && z2 > z1 && z3 > z2 && z4 > z3 && z5 > z4 && z6 > z5
        } else {
            z1 > 0 && z2 > z1 && z3 > z2 && z4 > z3
        }
    }

    /**
     * Extracts boundaries as an immutable list matching the zone type's boundary count (REQ-SET-082.1).
     */
    fun toBoundariesList(): List<Int> = when (type) {
        SettingsDataStore.ZoneType.PWR_BIKE -> listOf(z1, z2, z3, z4, z5, z6)
        else -> listOf(z1, z2, z3, z4)
    }

    companion object {
        fun fromBoundaries(
            type: SettingsDataStore.ZoneType,
            boundaries: List<Int>,
            displayOptions: ZoneDisplayOptions = ZoneDisplayOptions()
        ): ZoneProfileState {
            return when (type) {
                SettingsDataStore.ZoneType.PWR_BIKE -> ZoneProfileState(
                    type = type,
                    z1 = boundaries.getOrElse(0) { 0 },
                    z2 = boundaries.getOrElse(1) { 0 },
                    z3 = boundaries.getOrElse(2) { 0 },
                    z4 = boundaries.getOrElse(3) { 0 },
                    z5 = boundaries.getOrElse(4) { 0 },
                    z6 = boundaries.getOrElse(5) { 0 },
                    displayOptions = displayOptions
                )
                else -> ZoneProfileState(
                    type = type,
                    z1 = boundaries.getOrElse(0) { 0 },
                    z2 = boundaries.getOrElse(1) { 0 },
                    z3 = boundaries.getOrElse(2) { 0 },
                    z4 = boundaries.getOrElse(3) { 0 },
                    displayOptions = displayOptions
                )
            }
        }
    }
}
