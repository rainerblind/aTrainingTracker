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

/**
 * Common client interface for radar tail light hardware peripherals (REQ-CON-033.4, ATT-5103).
 *
 * Implemented by [com.atrainingtracker.banalservice.devices.bluetooth_le.BTLERadarDevice]
 * and [com.atrainingtracker.banalservice.devices.ant_plus.ANTBikeLightDevice].
 */
interface RadarLightDeviceClient {
    /**
     * Dispatches a light mode change to the peripheral.
     *
     * @param mode Desired tail light operating mode.
     * @return `true` if the command was successfully queued/transmitted.
     */
    fun setLightMode(mode: RadarLightMode): Boolean
}
