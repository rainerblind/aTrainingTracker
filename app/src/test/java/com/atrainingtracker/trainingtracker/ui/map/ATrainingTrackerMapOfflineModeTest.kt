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

package com.atrainingtracker.trainingtracker.ui.map

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.trainingtracker.ui.map.offline.MapBaseSource
import com.atrainingtracker.trainingtracker.ui.map.offline.OFFLINE_OSM_ATTRIBUTION_TEST_TAG
import com.atrainingtracker.trainingtracker.ui.map.offline.OfflineOsmAttributionBadge
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.google.maps.android.compose.MapType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Behavioral Compose UI & unit verification suite for `ATrainingTrackerMap` offline base-map mode,
 * z-order invariants, graceful fallback, and OSM attribution badge (`REQ-MAP-071.4`, `TST-MAP-074.3`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ATrainingTrackerMapOfflineModeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun isOfflineOsmActive_requiresBothOfflineSourceAndAtLeastOneMapFile() {
        assertFalse(isOfflineOsmActive(MapBaseSource.GOOGLE, 0))
        assertFalse(isOfflineOsmActive(MapBaseSource.GOOGLE, 2))
        assertFalse(isOfflineOsmActive(MapBaseSource.OFFLINE_OSM, 0))
        assertTrue(isOfflineOsmActive(MapBaseSource.OFFLINE_OSM, 1))
        assertTrue(isOfflineOsmActive(MapBaseSource.OFFLINE_OSM, 3))
    }

    @Test
    fun resolveOfflineAwareMapProperties_activatesMapTypeNoneAndFallsBackCleanly() {
        // Active OFFLINE_OSM + >= 1 installed map -> MapType.NONE regardless of dark mode or mapTypeOverride
        val offlineLight = resolveOfflineAwareMapProperties(
            baseSource = MapBaseSource.OFFLINE_OSM,
            installedMapCount = 1,
            isDark = false,
            context = context,
            mapTypeOverride = MapType.SATELLITE
        )
        assertEquals(MapType.NONE, offlineLight.mapType)
        assertNull(offlineLight.mapStyleOptions)

        val offlineDark = resolveOfflineAwareMapProperties(
            baseSource = MapBaseSource.OFFLINE_OSM,
            installedMapCount = 2,
            isDark = true,
            context = context
        )
        assertEquals(MapType.NONE, offlineDark.mapType)
        assertNull(offlineDark.mapStyleOptions)

        // Zero installed maps -> graceful fallback to configured Google base map
        val fallbackSatellite = resolveOfflineAwareMapProperties(
            baseSource = MapBaseSource.OFFLINE_OSM,
            installedMapCount = 0,
            isDark = false,
            context = context,
            mapTypeOverride = MapType.SATELLITE
        )
        assertEquals(MapType.SATELLITE, fallbackSatellite.mapType)

        val fallbackDark = resolveOfflineAwareMapProperties(
            baseSource = MapBaseSource.OFFLINE_OSM,
            installedMapCount = 0,
            isDark = true,
            context = context
        )
        assertEquals(MapType.NORMAL, fallbackDark.mapType)
        assertNotNull(fallbackDark.mapStyleOptions)

        // Switching back to GOOGLE restores explicit WorkoutBaseMapType / Light TERRAIN
        val restoredGoogleTerrain = resolveOfflineAwareMapProperties(
            baseSource = MapBaseSource.GOOGLE,
            installedMapCount = 2,
            isDark = false,
            context = context
        )
        assertEquals(MapType.TERRAIN, restoredGoogleTerrain.mapType)
        assertNull(restoredGoogleTerrain.mapStyleOptions)
    }

    @Test
    fun offlineBaseTileZIndex_sitsStrictlyBeneathAllMapOverlays() {
        val defaultStyle = MapStyle()
        assertEquals(-100f, OFFLINE_BASE_TILE_Z_INDEX)
        assertTrue("Offline tiles must render below heatmaps (z=0f)", OFFLINE_BASE_TILE_Z_INDEX < 0f)
        assertTrue("Offline tiles must render below live track (z=5f)", OFFLINE_BASE_TILE_Z_INDEX < 5f)
        assertTrue("Offline tiles must render below tracks", OFFLINE_BASE_TILE_Z_INDEX < defaultStyle.trackBaseZIndex)
        assertTrue("Offline tiles must render below routes", OFFLINE_BASE_TILE_Z_INDEX < defaultStyle.routeUnselectedZIndex)
        assertTrue("Offline tiles must render below climbs", OFFLINE_BASE_TILE_Z_INDEX < defaultStyle.climbZIndex)
        assertTrue("Offline tiles must render below segments", OFFLINE_BASE_TILE_Z_INDEX < defaultStyle.segmentZIndex)
        assertTrue("Offline tiles must render below user location", OFFLINE_BASE_TILE_Z_INDEX < defaultStyle.userLocationZIndex)
    }

    @Test
    fun offlineOsmAttributionBadge_rendersOdblCreditAndTogglesConditionallyInCompose() {
        var source by mutableStateOf(MapBaseSource.GOOGLE)
        var installedCount by mutableStateOf(1)

        composeRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                Box(modifier = Modifier.size(360.dp, 240.dp)) {
                    if (isOfflineOsmActive(source, installedCount)) {
                        OfflineOsmAttributionBadge(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(PaddingValues(bottom = 16.dp))
                                .padding(end = 8.dp, bottom = 8.dp)
                        )
                    }
                }
            }
        }

        // Hidden under GOOGLE
        composeRule.onNodeWithTag(OFFLINE_OSM_ATTRIBUTION_TEST_TAG).assertDoesNotExist()

        // Visible under OFFLINE_OSM + >= 1 map
        composeRule.runOnIdle { source = MapBaseSource.OFFLINE_OSM }
        composeRule.onNodeWithTag(OFFLINE_OSM_ATTRIBUTION_TEST_TAG)
            .assertIsDisplayed()
            .assertTextEquals("© OpenStreetMap contributors")

        // Hidden when 0 installed maps trigger fallback to Google
        composeRule.runOnIdle { installedCount = 0 }
        composeRule.onNodeWithTag(OFFLINE_OSM_ATTRIBUTION_TEST_TAG).assertDoesNotExist()
    }
}
