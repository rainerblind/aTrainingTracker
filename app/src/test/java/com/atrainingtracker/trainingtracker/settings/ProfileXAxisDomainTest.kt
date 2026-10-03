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
 * Unit test for independent ProfileXAxisDomain preferences, default constants,
 * and deserialization safety (REQ-UI-201, REQ-UI-233, TST-UI-155.2, TST-UI-192).
 */
class ProfileXAxisDomainTest {

    @Test
    fun testDefaultProfileXAxisDomain() {
        val config = TuningConfig()
        // Primary decoupled domains (REQ-UI-233)
        assertEquals(ProfileXAxisDomain.DISTANCE, config.elevationXAxisDomain)
        assertEquals(ProfileXAxisDomain.TIME, config.telemetryXAxisDomain)
        assertEquals(ProfileXAxisDomain.DISTANCE, TuningPreferencesDefaults.ELEVATION_X_AXIS_DOMAIN)
        assertEquals(ProfileXAxisDomain.TIME, TuningPreferencesDefaults.TELEMETRY_X_AXIS_DOMAIN)

        // Deprecated backward-compatibility bridge
        assertEquals(ProfileXAxisDomain.DISTANCE, config.profileXAxisDomain)
        assertEquals(ProfileXAxisDomain.DISTANCE, TuningPreferencesDefaults.PROFILE_X_AXIS_DOMAIN)
    }

    @Test
    fun testProfileXAxisDomainCustomConfiguration() {
        // Independent custom configuration
        val config1 = TuningConfig(
            elevationXAxisDomain = ProfileXAxisDomain.TIME,
            telemetryXAxisDomain = ProfileXAxisDomain.DISTANCE
        )
        assertEquals(ProfileXAxisDomain.TIME, config1.elevationXAxisDomain)
        assertEquals(ProfileXAxisDomain.DISTANCE, config1.telemetryXAxisDomain)

        // Legacy single-property constructor compatibility
        val legacyConfig = TuningConfig(profileXAxisDomain = ProfileXAxisDomain.TIME)
        assertEquals(ProfileXAxisDomain.TIME, legacyConfig.elevationXAxisDomain)
        assertEquals(ProfileXAxisDomain.TIME, legacyConfig.telemetryXAxisDomain)
        assertEquals(ProfileXAxisDomain.TIME, legacyConfig.profileXAxisDomain)
    }

    @Test
    fun testDataStoreKeyDefinitions() {
        assertEquals("tuning_elevation_x_axis_domain", TuningPreferencesDataStore.KEY_ELEVATION_X_AXIS_DOMAIN.name)
        assertEquals("tuning_telemetry_x_axis_domain", TuningPreferencesDataStore.KEY_TELEMETRY_X_AXIS_DOMAIN.name)
        assertEquals("tuning_profile_x_axis_domain", TuningPreferencesDataStore.KEY_PROFILE_X_AXIS_DOMAIN.name)
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
