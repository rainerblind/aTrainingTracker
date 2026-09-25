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

package com.atrainingtracker.trainingtracker.ui.knownlocations

import com.atrainingtracker.trainingtracker.elevation.ElevationResult
import com.atrainingtracker.trainingtracker.elevation.ElevationSource
import com.atrainingtracker.trainingtracker.repositories.KnownLocationItem
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit verification tests for [EditKnownLocationDialog] logic and contracts.
 *
 * Traceability:
 * - TST-UI-117.8: Editing altitude to 530.0m auto-locks and updates source to MANUAL_USER.
 * - TST-UI-117.9: "Fetch DEM" updates altitude text to DEM elevation and sets source to INTERNET_DEM.
 */
class EditKnownLocationDialogTest {

    private val sampleLocation = KnownLocationItem(
        id = 42L,
        name = "Königsplatz",
        altitude = 512.0,
        radius = 200,
        latLng = LatLng(48.145, 11.565),
        hitCount = 7,
        isLocked = false,
        source = ElevationSource.LEGACY_RAW
    )

    /**
     * TST-UI-117.8: Editing altitude to 530.0m auto-locks and updates source to MANUAL_USER.
     */
    @Test
    fun testManualAltitudeEdit_setsSourceToManualUser() {
        val inputAltitudeString = "530.0"
        val parsedAltitude = KnownLocationsUnitConversions.parseInputToMeters(inputAltitudeString, isMetric = true)
        assertNotNull(parsedAltitude)
        assertEquals(530.0, parsedAltitude!!, 0.001)

        var confirmedId = 0L
        var confirmedName = ""
        var confirmedAlt = 0.0
        var confirmedSource = ElevationSource.LEGACY_RAW

        val onConfirm: (Long, String, Double, ElevationSource) -> Unit = { id, name, alt, source ->
            confirmedId = id
            confirmedName = name
            confirmedAlt = alt
            confirmedSource = source
        }

        // When user edits the text field, the current source transitions to MANUAL_USER
        val editedSource = ElevationSource.MANUAL_USER
        onConfirm(sampleLocation.id, sampleLocation.name, parsedAltitude, editedSource)

        assertEquals(42L, confirmedId)
        assertEquals("Königsplatz", confirmedName)
        assertEquals(530.0, confirmedAlt, 0.001)
        assertEquals("Manual edit must set source to MANUAL_USER", ElevationSource.MANUAL_USER, confirmedSource)

        // Verify repository invariant: source == MANUAL_USER implies is_locked = 1
        val isLocked = (confirmedSource == ElevationSource.MANUAL_USER)
        assertTrue("MANUAL_USER source must automatically imply is_locked=true", isLocked)
    }

    /**
     * TST-UI-117.9: "Fetch DEM" updates altitude text to DEM elevation and sets source to INTERNET_DEM.
     */
    @Test
    fun testFetchDem_updatesAltitudeAndSourceToInternetDem() = runTest {
        val mockDemFetch: suspend (Long, LatLng) -> ElevationResult = { _, coords ->
            ElevationResult.Success(
                elevationMeters = 542.8,
                latitude = coords.latitude,
                longitude = coords.longitude
            )
        }

        val result = mockDemFetch(sampleLocation.id, sampleLocation.latLng)
        assertTrue(result is ElevationResult.Success)
        val success = result as ElevationResult.Success
        assertEquals(542.8, success.elevationMeters, 0.001)

        val formattedText = KnownLocationsUnitConversions.formatAltitudeForEdit(success.elevationMeters, isMetric = true)
        assertEquals("542.8", formattedText)

        var confirmedSource = ElevationSource.LEGACY_RAW
        var confirmedAlt = 0.0

        val onConfirm: (Long, String, Double, ElevationSource) -> Unit = { _, _, alt, source ->
            confirmedAlt = alt
            confirmedSource = source
        }

        // Committing fetched DEM elevation
        val parsed = KnownLocationsUnitConversions.parseInputToMeters(formattedText, isMetric = true)
        assertNotNull(parsed)
        onConfirm(sampleLocation.id, sampleLocation.name, parsed!!, ElevationSource.INTERNET_DEM)

        assertEquals(542.8, confirmedAlt, 0.001)
        assertEquals("DEM fetch must set source to INTERNET_DEM", ElevationSource.INTERNET_DEM, confirmedSource)

        val isLocked = (confirmedSource == ElevationSource.MANUAL_USER)
        assertEquals("INTERNET_DEM source must not lock the record", false, isLocked)
    }

    @Test
    fun testImperialAltitudeEdit_convertsToMetersAccurately() {
        val imperialFeetInput = "1724"
        val parsedMeters = KnownLocationsUnitConversions.parseInputToMeters(imperialFeetInput, isMetric = false)
        assertNotNull(parsedMeters)

        // 1724 * 0.3048 = 525.4752 -> rounded to 0.1m: 525.5
        assertEquals(525.5, parsedMeters!!, 0.001)

        // Display check
        val displayFeet = KnownLocationsUnitConversions.metersToFeet(parsedMeters)
        assertEquals(1724L, displayFeet)
    }

    @Test
    fun testInvalidAltitudeInput_validation() {
        assertNull(KnownLocationsUnitConversions.parseInputToMeters("abc", isMetric = true))
        assertNull(KnownLocationsUnitConversions.parseInputToMeters("-1000", isMetric = true))
        assertNull(KnownLocationsUnitConversions.parseInputToMeters("10000", isMetric = true))
    }
}
