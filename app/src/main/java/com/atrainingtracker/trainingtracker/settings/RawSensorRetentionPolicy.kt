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

/**
 * Retention policy for raw Polar high-frequency sensor sidecars (REQ-SET-117.3, ATT-8019).
 *
 * Configures how long compressed `.polar_raw.csv.gz` files are retained before being pruned.
 * [days] indicates the age threshold in days, with `-1` meaning keep indefinitely and `0` meaning off.
 */
@Suppress("MagicNumber")
enum class RawSensorRetentionPolicy(val days: Int) {
    DAYS_30(30),
    KEEP_ALL(-1),
    DAYS_7(7),
    OFF(0);

    companion object {
        const val RETENTION_DAYS_30 = 30
        const val RETENTION_KEEP_ALL = -1
        const val RETENTION_DAYS_7 = 7
        const val RETENTION_OFF = 0
    }
}
