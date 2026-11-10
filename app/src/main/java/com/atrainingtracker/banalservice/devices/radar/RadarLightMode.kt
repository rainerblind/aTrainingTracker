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

package com.atrainingtracker.banalservice.devices.radar

import androidx.annotation.StringRes
import com.atrainingtracker.R

/**
 * Operating modes for Garmin Varia BLE radar tail lights (REQ-CON-031.2).
 *
 * @property commandByte Raw 1-byte command payload transmitted over characteristic 6a4e3202.
 * @property titleResId Localized string resource describing the light mode.
 * @property isStvzoCompliant Whether the mode is legal under German StVZO road regulations.
 */
enum class RadarLightMode(
    val commandByte: Byte,
    @StringRes val titleResId: Int,
    val isStvzoCompliant: Boolean
) {
    /** Tail light extinguished (radar tracking active only). */
    OFF(0x00, R.string.radar_light_mode_off, true),

    /** High-intensity day flash (up to 100 lm, non-StVZO). */
    DAY_FLASH(0x07, R.string.radar_light_mode_day_flash, false),

    /** Low-intensity night flash (approx 40 lm, non-StVZO). */
    NIGHT_FLASH(0x06, R.string.radar_light_mode_night_flash, false),

    /** Steady continuous red beam (approx 25 lm, StVZO compliant). */
    SOLID(0x01, R.string.radar_light_mode_solid, true),

    /** Low-intensity steady/pulse mode for group peloton riding (approx 8 lm, non-StVZO). */
    PELOTON(0x05, R.string.radar_light_mode_peloton, false);

    companion object {
        /**
         * Resolves a [RadarLightMode] from its raw BLE command byte.
         */
        @JvmStatic
        fun fromCommandByte(byte: Byte): RadarLightMode {
            return entries.firstOrNull { it.commandByte == byte } ?: OFF
        }

        /**
         * Returns the list of permitted modes, pruning flashing modes when [isStvzo] is active.
         */
        @JvmStatic
        fun getAvailableModes(isStvzo: Boolean): List<RadarLightMode> {
            return if (isStvzo) {
                entries.filter { it.isStvzoCompliant }
            } else {
                entries
            }
        }
    }
}
