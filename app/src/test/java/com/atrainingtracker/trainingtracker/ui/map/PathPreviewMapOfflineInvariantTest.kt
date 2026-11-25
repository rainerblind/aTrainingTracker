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
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.trainingtracker.ui.map.offline.MapBaseSource
import com.atrainingtracker.trainingtracker.ui.map.offline.OfflineMapPreferences
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the `PathPreviewMap` Lite-Mode scope boundary (`REQ-MAP-071.5`, `TST-MAP-074.3`):
 * enabling `MapBaseSource.OFFLINE_OSM` never disables Lite Mode or alters `PathPreviewMap`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PathPreviewMapOfflineInvariantTest {

    @After
    fun tearDown() {
        OfflineMapPreferences.resetForTesting()
    }

    @Test
    fun pathPreviewMap_retainsLiteModeWhenOfflineOsmBaseSourceIsActive() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        OfflineMapPreferences.setBaseSource(context, MapBaseSource.OFFLINE_OSM)
        assertEquals(MapBaseSource.OFFLINE_OSM, OfflineMapPreferences.getBaseSource(context))

        val seededPosition = CameraPosition.fromLatLngZoom(LatLng(48.1351, 11.5820), 13f)
        val options = createLiteModeMapOptions(seededPosition)
        assertTrue("PathPreviewMap MUST stay in Lite Mode under OFFLINE_OSM", options.liteMode == true)
        assertEquals(seededPosition, options.camera)
    }
}
