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

package com.atrainingtracker.trainingtracker.ui.help

import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.navigation.NavRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural contract and unit test for [TipsGuideScreen] and [TipsGuideData].
 *
 * Fulfills verification requirement TST-UI-397.3 (ATT-2276 / REQ-UI-432).
 */
class TipsGuideScreenContractTest {

    @Test
    fun testTipsCountAndIntegrity() {
        val tips = TipsGuideData.TIPS
        assertEquals("Tips guide must contain exactly 7 curated power tips", 7, tips.size)

        val uniqueIds = tips.map { it.id }.toSet()
        assertEquals("All 7 tips must have distinct identifiers", 7, uniqueIds.size)

        tips.forEach { tip ->
            assertNotNull("Tip id cannot be null", tip.id)
            assertTrue("Tip title resource must be valid", tip.titleRes != 0)
            assertTrue("Tip subtitle resource must be valid", tip.subtitleRes != 0)
            assertTrue("Tip description resource must be valid", tip.descriptionRes != 0)
            assertTrue("Tip action resource must be valid", tip.actionRes != 0)
            assertTrue("Tip icon resource must be valid", tip.iconRes != 0)
            assertTrue("Tip targetRoute cannot be empty", tip.targetRoute.isNotBlank())
        }
    }

    @Test
    fun testCuratedTipsContentAndRouteMapping() {
        val tipsMap = TipsGuideData.TIPS.associateBy { it.id }

        // Tip 1: Favorite Routes & Clustering -> NavRoutes.LOCATIONS
        val tipRoutes = tipsMap["routes_clustering"]
        assertNotNull(tipRoutes)
        assertEquals(R.string.tip_routes_clustering_title, tipRoutes?.titleRes)
        assertEquals(R.string.tip_routes_clustering_subtitle, tipRoutes?.subtitleRes)
        assertEquals(R.string.tip_routes_clustering_desc, tipRoutes?.descriptionRes)
        assertEquals(R.string.tip_routes_clustering_action, tipRoutes?.actionRes)
        assertEquals(R.drawable.ic_favorite_route, tipRoutes?.iconRes)
        assertEquals(NavRoutes.LOCATIONS, tipRoutes?.targetRoute)

        // Tip 2: Multi-Protocol Sensors & Failover -> NavRoutes.SENSORS
        val tipSensors = tipsMap["sensors_failover"]
        assertNotNull(tipSensors)
        assertEquals(R.string.tip_sensors_failover_title, tipSensors?.titleRes)
        assertEquals(R.string.tip_sensors_failover_subtitle, tipSensors?.subtitleRes)
        assertEquals(R.string.tip_sensors_failover_desc, tipSensors?.descriptionRes)
        assertEquals(R.string.tip_sensors_failover_action, tipSensors?.actionRes)
        assertEquals(R.drawable.ic_my_paired_devices, tipSensors?.iconRes)
        assertEquals(NavRoutes.SENSORS, tipSensors?.targetRoute)

        // Tip 3: Cockpit Displays & Bike Bell -> NavRoutes.SPORT_TYPES
        val tipCockpit = tipsMap["cockpit_customization"]
        assertNotNull(tipCockpit)
        assertEquals(R.string.tip_cockpit_customization_title, tipCockpit?.titleRes)
        assertEquals(R.string.tip_cockpit_customization_subtitle, tipCockpit?.subtitleRes)
        assertEquals(R.string.tip_cockpit_customization_desc, tipCockpit?.descriptionRes)
        assertEquals(R.string.tip_cockpit_customization_action, tipCockpit?.actionRes)
        assertEquals(R.drawable.ic_display_settings, tipCockpit?.iconRes)
        assertEquals(NavRoutes.SPORT_TYPES, tipCockpit?.targetRoute)

        // Tip 4: Strava Live Segments -> NavRoutes.SEGMENTS
        val tipSegments = tipsMap["live_segments"]
        assertNotNull(tipSegments)
        assertEquals(R.string.tip_live_segments_title, tipSegments?.titleRes)
        assertEquals(R.string.tip_live_segments_subtitle, tipSegments?.subtitleRes)
        assertEquals(R.string.tip_live_segments_desc, tipSegments?.descriptionRes)
        assertEquals(R.string.tip_live_segments_action, tipSegments?.actionRes)
        assertEquals(R.drawable.ic_segment, tipSegments?.iconRes)
        assertEquals(NavRoutes.SEGMENTS, tipSegments?.targetRoute)

        // Tip 5: Automated Gear & Equipment -> NavRoutes.EQUIPMENT
        val tipGear = tipsMap["gear_tracking"]
        assertNotNull(tipGear)
        assertEquals(R.string.tip_gear_tracking_title, tipGear?.titleRes)
        assertEquals(R.string.tip_gear_tracking_subtitle, tipGear?.subtitleRes)
        assertEquals(R.string.tip_gear_tracking_desc, tipGear?.descriptionRes)
        assertEquals(R.string.tip_gear_tracking_action, tipGear?.actionRes)
        assertEquals(R.drawable.ic_equipment_combined, tipGear?.iconRes)
        assertEquals(NavRoutes.EQUIPMENT, tipGear?.targetRoute)

        // Tip 6: Physiology & Training Zones -> NavRoutes.trainingZones()
        val tipZones = tipsMap["training_zones"]
        assertNotNull(tipZones)
        assertEquals(R.string.tip_training_zones_title, tipZones?.titleRes)
        assertEquals(R.string.tip_training_zones_subtitle, tipZones?.subtitleRes)
        assertEquals(R.string.tip_training_zones_desc, tipZones?.descriptionRes)
        assertEquals(R.string.tip_training_zones_action, tipZones?.actionRes)
        assertEquals(R.drawable.ic_cardiology, tipZones?.iconRes)
        assertEquals(NavRoutes.trainingZones(), tipZones?.targetRoute)

        // Tip 7: Data Sovereignty & Offline Backup -> NavRoutes.BACKUP_RESTORE
        val tipBackup = tipsMap["data_backup"]
        assertNotNull(tipBackup)
        assertEquals(R.string.tip_data_backup_title, tipBackup?.titleRes)
        assertEquals(R.string.tip_data_backup_subtitle, tipBackup?.subtitleRes)
        assertEquals(R.string.tip_data_backup_desc, tipBackup?.descriptionRes)
        assertEquals(R.string.tip_data_backup_action, tipBackup?.actionRes)
        assertEquals(R.drawable.ic_save_to_disc, tipBackup?.iconRes)
        assertEquals(NavRoutes.BACKUP_RESTORE, tipBackup?.targetRoute)
    }

    @Test
    fun testDeepLinkDispatchContract() {
        val dispatchedRoutes = mutableListOf<String>()
        val onNavigate: (String) -> Unit = { route -> dispatchedRoutes.add(route) }

        TipsGuideData.TIPS.forEach { tip ->
            onNavigate(tip.targetRoute)
        }

        assertEquals(7, dispatchedRoutes.size)
        assertEquals(
            listOf(
                NavRoutes.LOCATIONS,
                NavRoutes.SENSORS,
                NavRoutes.SPORT_TYPES,
                NavRoutes.SEGMENTS,
                NavRoutes.EQUIPMENT,
                NavRoutes.trainingZones(),
                NavRoutes.BACKUP_RESTORE
            ),
            dispatchedRoutes
        )
    }

    @Test
    fun testZeroRasterAssetBloatEnforcement() {
        val candidates = listOf(
            File("app/src/main/res/drawable"),
            File("src/main/res/drawable"),
            File("../app/src/main/res/drawable")
        )
        val drawableDir = candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: throw IllegalStateException("drawable directory not found")

        val expectedVectorNames = listOf(
            "ic_favorite_route.xml",
            "ic_my_paired_devices.xml",
            "ic_display_settings.xml",
            "ic_segment.xml",
            "ic_equipment_combined.xml",
            "ic_cardiology.xml",
            "ic_save_to_disc.xml",
            "ic_tips_and_updates.xml"
        )

        expectedVectorNames.forEach { xmlName ->
            val xmlFile = File(drawableDir, xmlName)
            assertTrue("Vector drawable $xmlName must exist", xmlFile.exists())
            val content = xmlFile.readText()
            assertTrue("Drawable $xmlName must be an XML drawable", content.contains("<vector") || content.contains("<layer-list"))

            val baseName = xmlName.removeSuffix(".xml")
            val rasterPng = File(drawableDir, "$baseName.png")
            val rasterJpg = File(drawableDir, "$baseName.jpg")
            val rasterWebp = File(drawableDir, "$baseName.webp")
            assertFalse("Raster PNG must not exist for $baseName", rasterPng.exists())
            assertFalse("Raster JPG must not exist for $baseName", rasterJpg.exists())
            assertFalse("Raster WEBP must not exist for $baseName", rasterWebp.exists())
        }
    }
}
