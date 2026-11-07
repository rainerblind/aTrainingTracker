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

package com.atrainingtracker.banalservice.devices

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Architectural unit verification for [DeviceType] enum declaration order invariants
 * (REQ-TRK-024.3, REQ-SEN-027.1, TST-TRK-016.4, TST-SEN-027.1, ATT-4375, ATT-5492).
 *
 * Verifies that the declaration order (natural ordinal ordering) places
 * [DeviceType.SPEED_AND_LOCATION_GPS] before [DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED],
 * and confirms that obsolete [SPEED_AND_LOCATION_NETWORK] is completely excised.
 */
class DeviceTypeEnumOrderTest {

    @Test
    fun locationEnumOrder_gpsPrecedesGoogleFused_andNetworkExcised() {
        val gpsOrdinal = DeviceType.SPEED_AND_LOCATION_GPS.ordinal
        val fusedOrdinal = DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED.ordinal

        assertTrue(
            "SPEED_AND_LOCATION_GPS ($gpsOrdinal) must precede SPEED_AND_LOCATION_GOOGLE_FUSED ($fusedOrdinal)",
            gpsOrdinal < fusedOrdinal
        )
        assertTrue(
            "SPEED_AND_LOCATION_NETWORK must be completely excised from DeviceType",
            DeviceType.values().none { it.name == "SPEED_AND_LOCATION_NETWORK" }
        )
    }
}
