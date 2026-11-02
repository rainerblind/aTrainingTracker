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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.atrainingtracker.trainingtracker.location

import android.content.Context
import android.content.SharedPreferences
import android.location.Address
import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager
import com.google.android.gms.maps.model.LatLng
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * Unit tests for [RegionNameResolver] (ATT-4934 / REQ-PER-026 / TST-PER-033).
 * Verifies the 3-tier fallback hierarchy, coordinate quantization, and two-tier caching.
 */
class RegionNameResolverTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private val prefsStore = mutableMapOf<String, String>()

    @Before
    fun setUp() {
        RegionNameResolver.clearCacheForTesting()
        prefsStore.clear()

        mockEditor = mockk(relaxed = true) {
            every { putString(any(), any()) } answers {
                val key = firstArg<String>()
                val value = secondArg<String>()
                prefsStore[key] = value
                this@mockk
            }
            every { clear() } answers {
                prefsStore.clear()
                this@mockk
            }
        }

        mockPrefs = mockk(relaxed = true) {
            every { getString(any(), any()) } answers {
                val key = firstArg<String>()
                val defaultVal = secondArg<String?>()
                prefsStore[key] ?: defaultVal
            }
            every { edit() } returns mockEditor
        }

        mockContext = mockk(relaxed = true) {
            every { getSharedPreferences(RegionNameResolver.PREFS_FILE, Context.MODE_PRIVATE) } returns mockPrefs
        }
    }

    @After
    fun tearDown() {
        RegionNameResolver.clearCacheForTesting()
    }

    @Test
    fun testQuantizeCoordinate_nearbyPointsShareSameCacheKey() {
        // Points ~200m apart in Schönaich should quantize to identical cell (~4.4 km step)
        val lat1 = 48.6580
        val lng1 = 9.0620
        val lat2 = 48.6595
        val lng2 = 9.0635

        val key1 = RegionNameResolver.cacheKey(lat1, lng1)
        val key2 = RegionNameResolver.cacheKey(lat2, lng2)

        assertEquals(key1, key2)

        // Point in Berlin should be completely different
        val keyBerlin = RegionNameResolver.cacheKey(52.5200, 13.4050)
        assertNotEquals(key1, keyBerlin)
    }

    @Test
    fun testExtractMacroLocality_prefersLocalityOverSubAdmin() {
        val address = mockk<Address> {
            every { locality } returns "Schönaich"
            every { subLocality } returns "Kreisstadt"
            every { subAdminArea } returns "Landkreis Böblingen"
            every { adminArea } returns "Baden-Württemberg"
        }
        assertEquals("Schönaich", RegionNameResolver.extractMacroLocality(address))
    }

    @Test
    fun testExtractMacroLocality_fallsBackToSubLocalityWhenLocalityEmpty() {
        val address = mockk<Address> {
            every { locality } returns ""
            every { subLocality } returns "Dagersheim"
            every { subAdminArea } returns "Böblingen"
            every { adminArea } returns "Baden-Württemberg"
        }
        assertEquals("Dagersheim", RegionNameResolver.extractMacroLocality(address))
    }

    @Test
    fun testExtractMacroLocality_fallsBackToSubAdminArea() {
        val address = mockk<Address> {
            every { locality } returns null
            every { subLocality } returns null
            every { subAdminArea } returns "Landkreis Calw"
            every { adminArea } returns "Baden-Württemberg"
        }
        assertEquals("Landkreis Calw", RegionNameResolver.extractMacroLocality(address))
    }

    @Test
    fun testPriority1_knownLocationMatchesWithin10Km_bypassesGeocoder() = runBlocking {
        // TST-PER-033.1: Priority 1 Known Location overrides Geocoder
        var geocoderCalled = false
        RegionNameResolver.knownLocationOverride = { _, _ -> "Schönaich" }
        RegionNameResolver.geocoderOverride = { _, _, _ ->
            geocoderCalled = true
            "Unexpected Geocoder"
        }

        val result = RegionNameResolver.resolvePlaceName(mockContext, LatLng(48.658, 9.062))
        assertEquals("Schönaich", result)
        assertFalse("Geocoder must not be called when Known Location matches", geocoderCalled)
    }

    @Test
    fun testPriority2_geocoderResolvesMacroLocalityWhenNoKnownLocation() = runBlocking {
        // TST-PER-033.2: Priority 2 Geocoder macro-locality
        RegionNameResolver.knownLocationOverride = { _, _ -> null }
        RegionNameResolver.geocoderOverride = { _, _, _ -> "Böblingen" }

        val result = RegionNameResolver.resolvePlaceName(mockContext, LatLng(48.685, 9.015))
        assertEquals("Böblingen", result)
    }

    @Test
    fun testPriority3_fallbackToGenericRegionWhenAllResolversFail() = runBlocking {
        // TST-PER-033.3: Priority 3 Localized Fallback
        RegionNameResolver.knownLocationOverride = { _, _ -> null }
        RegionNameResolver.geocoderOverride = { _, _, _ -> null }

        val placeName = RegionNameResolver.resolvePlaceName(mockContext, LatLng(48.0, 9.0))
        assertNull(placeName)

        val regionLabel = RegionNameResolver.resolveRegionName(mockContext, LatLng(48.0, 9.0), index = 2)
        assertEquals("Region 2", regionLabel)
    }

    @Test
    fun testTwoTierCache_memoryCacheHitBypassesOverrides() = runBlocking {
        // TST-PER-033.4: In-memory cache hit
        var callCount = 0
        RegionNameResolver.knownLocationOverride = { _, _ ->
            callCount++
            "Herrenberg"
        }

        val center = LatLng(48.595, 8.868)
        val firstResult = RegionNameResolver.resolvePlaceName(mockContext, center)
        assertEquals("Herrenberg", firstResult)
        assertEquals(1, callCount)

        // Second lookup for same cell should hit Level 1 memory cache
        val secondResult = RegionNameResolver.resolvePlaceName(mockContext, center)
        assertEquals("Herrenberg", secondResult)
        assertEquals("Should not re-query known locations when memory cache hits", 1, callCount)
    }

    @Test
    fun testTwoTierCache_persistentSharedPreferencesHitBypassesOverrides() = runBlocking {
        // Seed SharedPreferences directly
        val center = LatLng(48.775, 9.182) // Stuttgart
        val key = RegionNameResolver.cacheKey(center.latitude, center.longitude)
        prefsStore[key] = "Stuttgart"

        // Overrides return null to prove SharedPreferences Level 2 cache was used
        RegionNameResolver.knownLocationOverride = { _, _ -> null }
        RegionNameResolver.geocoderOverride = { _, _, _ -> null }

        val result = RegionNameResolver.resolvePlaceName(mockContext, center)
        assertEquals("Stuttgart", result)
    }

    @Test
    fun testFindNearestKnownLocation_filtersOutPlaceholdersAndEnforces10KmRadius() {
        val mockManager = mockk<KnownLocationsDatabaseManager>()
        val mockLocValid = KnownLocationsDatabaseManager.MyLocation(
            1L, 48.658, 9.062, "Schönaich", 450.0, 200, 5
        )
        val mockLocPlaceholder = KnownLocationsDatabaseManager.MyLocation(
            2L, 48.659, 9.063, "Startort 1", 450.0, 200, 10
        )
        val mockLocFar = KnownLocationsDatabaseManager.MyLocation(
            3L, 49.000, 9.000, "Heilbronn", 200.0, 200, 20 // ~40 km away
        )

        every { mockManager.allLocations } returns listOf(mockLocPlaceholder, mockLocFar, mockLocValid)

        mockkStatic(KnownLocationsDatabaseManager::class)
        try {
            every { KnownLocationsDatabaseManager.getInstance(any()) } returns mockManager

            val center = LatLng(48.660, 9.065) // ~300m from Schönaich
            val result = RegionNameResolver.findNearestKnownLocationName(mockContext, center)
            assertEquals("Schönaich", result)

            // When only placeholder or far locations exist, returns null
            every { mockManager.allLocations } returns listOf(mockLocPlaceholder, mockLocFar)
            val noMatchResult = RegionNameResolver.findNearestKnownLocationName(mockContext, center)
            assertNull(noMatchResult)
        } finally {
            unmockkStatic(KnownLocationsDatabaseManager::class)
        }
    }
}
