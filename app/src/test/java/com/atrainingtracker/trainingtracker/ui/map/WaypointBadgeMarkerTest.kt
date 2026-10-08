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

import com.atrainingtracker.trainingtracker.routes.WaypointType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Automated unit and contract tests verifying category color mapping, Maki icon resource linkage,
 * and high-contrast badge marker generation for waypoints (REQ-MAP-033, TST-MAP-035, ATT-2461).
 */
class WaypointBadgeMarkerTest {

    @Test
    fun testResolveWaypointCategoryColor_allTypesCoveredWithDistinctVibrantColors() {
        for (type in WaypointType.values()) {
            val color = resolveWaypointCategoryColor(type)
            assertNotEquals("Color for $type must not be 0", 0, color)
        }

        assertEquals("Summit must be Deep Amber", 0xFFE65100.toInt(), resolveWaypointCategoryColor(WaypointType.POI_SUMMIT))
        assertEquals("Water must be Azure Blue", 0xFF0288D1.toInt(), resolveWaypointCategoryColor(WaypointType.POI_WATER))
        assertEquals("Bench must be Forest Green", 0xFF2E7D32.toInt(), resolveWaypointCategoryColor(WaypointType.POI_BENCH))
        assertEquals("Shelter must be Pine Teal", 0xFF00695C.toInt(), resolveWaypointCategoryColor(WaypointType.POI_SHELTER))
        assertEquals("Food must be Coral Orange", 0xFFEF6C00.toInt(), resolveWaypointCategoryColor(WaypointType.POI_FOOD))
        assertEquals("Viewpoint must be Royal Purple", 0xFF6A1B9A.toInt(), resolveWaypointCategoryColor(WaypointType.POI_VIEWPOINT))
        assertEquals("Danger must be Warning Red", 0xFFD32F2F.toInt(), resolveWaypointCategoryColor(WaypointType.POI_DANGER))
        assertEquals("First Aid must be Crimson", 0xFFC62828.toInt(), resolveWaypointCategoryColor(WaypointType.POI_FIRST_AID))
        assertEquals("Turn Left must be Cobalt Blue", 0xFF1565C0.toInt(), resolveWaypointCategoryColor(WaypointType.TURN_LEFT))
        assertEquals("Turn Right must be Cobalt Blue", 0xFF1565C0.toInt(), resolveWaypointCategoryColor(WaypointType.TURN_RIGHT))
        assertEquals("Turn Straight must be Cobalt Blue", 0xFF1565C0.toInt(), resolveWaypointCategoryColor(WaypointType.TURN_STRAIGHT))
        assertEquals("Generic must be Slate", 0xFF546E7A.toInt(), resolveWaypointCategoryColor(WaypointType.GENERIC))
    }

    @Test
    fun testWaypointType_allTypesHaveValidIconResource() {
        for (type in WaypointType.values()) {
            assertTrue("WaypointType $type must define a positive iconResId", type.iconResId > 0)
        }
    }

    @Test
    fun testMapUtils_defaultMarkerSizeConstant_is22Dp() {
        assertEquals("DEFAULT_WAYPOINT_MARKER_SIZE_DP must be 22", 22, DEFAULT_WAYPOINT_MARKER_SIZE_DP)
    }

    @Test
    fun testMapUtils_createWaypointBadgeMarkerContract() {
        val rootDir = findProjectRoot()
        val mapUtilsFile = File(rootDir, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapUtils.kt")
        assertTrue("MapUtils.kt must exist", mapUtilsFile.exists())
        val content = mapUtilsFile.readText()

        assertTrue("Must declare createWaypointBadgeMarker", content.contains("fun createWaypointBadgeMarker("))
        assertTrue("Must default to DEFAULT_WAYPOINT_MARKER_SIZE_DP", content.contains("sizeDp: Int = DEFAULT_WAYPOINT_MARKER_SIZE_DP"))
        assertTrue("Must scale strokes proportionally (scale = sizeDp / 32f)", content.contains("val scale = sizeDp / 32f"))
        assertTrue("Must include outer dark contrast stroke with min 0.75dp clamping", content.contains("0.75f * density"))
        assertTrue("Must include crisp white halo ring with min 1.25dp clamping", content.contains("1.25f * density"))
        assertTrue("Must include category vibrant disc", content.contains("resolveWaypointCategoryColor(type)"))
        assertTrue("Must tint Maki glyph to white", content.contains("setTint(android.graphics.Color.WHITE)"))
    }

    private fun findProjectRoot(): File {
        var dir: File = File(".").canonicalFile
        while (dir.parentFile != null) {
            if (File(dir, "gradlew").exists() && File(dir, "app").exists()) {
                return dir
            }
            dir = dir.parentFile!!
        }
        return File(".").canonicalFile
    }
}
