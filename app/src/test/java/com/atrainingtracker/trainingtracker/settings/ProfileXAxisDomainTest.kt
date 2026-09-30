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

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit test for ProfileXAxisDomain preference, default constants, and deserialization safety (REQ-UI-201, TST-UI-155.2).
 */
class ProfileXAxisDomainTest {

    @Test
    fun testDefaultProfileXAxisDomain() {
        val config = TuningConfig()
        assertEquals(ProfileXAxisDomain.DISTANCE, config.profileXAxisDomain)
        assertEquals(ProfileXAxisDomain.DISTANCE, TuningPreferencesDefaults.PROFILE_X_AXIS_DOMAIN)
    }

    @Test
    fun testProfileXAxisDomainCustomConfiguration() {
        val config = TuningConfig(profileXAxisDomain = ProfileXAxisDomain.TIME)
        assertEquals(ProfileXAxisDomain.TIME, config.profileXAxisDomain)
    }

    @Test
    fun testProfileXAxisDomainEnumSerializationAndFallback() {
        // Serialization to name
        val distanceName = ProfileXAxisDomain.DISTANCE.name
        val timeName = ProfileXAxisDomain.TIME.name
        assertEquals("DISTANCE", distanceName)
        assertEquals("TIME", timeName)

        // Deserialization matching TuningPreferencesDataStore logic
        val parsedDistance = runCatching { ProfileXAxisDomain.valueOf(distanceName) }
            .getOrDefault(ProfileXAxisDomain.DISTANCE)
        assertEquals(ProfileXAxisDomain.DISTANCE, parsedDistance)

        val parsedTime = runCatching { ProfileXAxisDomain.valueOf(timeName) }
            .getOrDefault(ProfileXAxisDomain.DISTANCE)
        assertEquals(ProfileXAxisDomain.TIME, parsedTime)

        // Corrupt or legacy string fallback
        val parsedCorrupted = runCatching { ProfileXAxisDomain.valueOf("UNKNOWN_AXIS") }
            .getOrDefault(ProfileXAxisDomain.DISTANCE)
        assertEquals(ProfileXAxisDomain.DISTANCE, parsedCorrupted)
    }
}
