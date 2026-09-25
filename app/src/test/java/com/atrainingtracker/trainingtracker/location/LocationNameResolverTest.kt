/*
 * aTrainingTracker
 * Copyright (C) 2013-2026 Rainer Blind <rainer.blind@gmail.com>
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.atrainingtracker.trainingtracker.location

import android.content.Context
import android.location.Address
import com.atrainingtracker.R
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/**
 * ATT-919 / TST-UI-117.4: Unit tests for [LocationNameResolver].
 */
class LocationNameResolverTest {

    @Test
    fun testFormatAddressName_featureNameAndLocality() {
        val address = mockk<Address> {
            every { featureName } returns "Englischer Garten"
            every { thoroughfare } returns null
            every { subLocality } returns null
            every { locality } returns "München"
            every { subAdminArea } returns null
            every { adminArea } returns null
        }

        val result = LocationNameResolver.formatAddressName(address)
        assertEquals("Englischer Garten, München", result)
    }

    @Test
    fun testFormatAddressName_numericFeatureNumber_usesThoroughfareAndPlace() {
        val address = mockk<Address> {
            every { featureName } returns "42"
            every { thoroughfare } returns "Leopoldstraße"
            every { subLocality } returns "Schwabing"
            every { locality } returns "München"
            every { subAdminArea } returns null
            every { adminArea } returns null
        }

        val result = LocationNameResolver.formatAddressName(address)
        assertEquals("Leopoldstraße, Schwabing", result)
    }

    @Test
    fun testFormatAddressName_subLocalityAndLocality() {
        val address = mockk<Address> {
            every { featureName } returns null
            every { thoroughfare } returns null
            every { subLocality } returns "Schwabing"
            every { locality } returns "München"
            every { subAdminArea } returns null
            every { adminArea } returns null
        }

        val result = LocationNameResolver.formatAddressName(address)
        assertEquals("Schwabing, München", result)
    }

    @Test
    fun testFormatAddressName_onlyLocality() {
        val address = mockk<Address> {
            every { featureName } returns null
            every { thoroughfare } returns null
            every { subLocality } returns null
            every { locality } returns "München"
            every { subAdminArea } returns null
            every { adminArea } returns null
        }

        val result = LocationNameResolver.formatAddressName(address)
        assertEquals("München", result)
    }

    @Test
    fun testFormatAddressName_empty_returnsNull() {
        val address = mockk<Address> {
            every { featureName } returns null
            every { thoroughfare } returns null
            every { subLocality } returns null
            every { locality } returns null
            every { subAdminArea } returns null
            every { adminArea } returns null
        }

        val result = LocationNameResolver.formatAddressName(address)
        assertNull(result)
    }

    @Test
    fun testFormatFallback_formatsCoordinates() {
        val context = mockk<Context>()
        every {
            context.getString(R.string.known_location_unnamed_format, 48.13715, 11.57612)
        } returns "Start Location (48.13715, 11.57612)"

        val result = LocationNameResolver.formatFallback(context, 48.13715, 11.57612)
        assertEquals("Start Location (48.13715, 11.57612)", result)
    }
}
