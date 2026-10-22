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

package com.atrainingtracker.trainingtracker.localization

import com.atrainingtracker.banalservice.BANALService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TST-UI-394 / REQ-UI-429 (ATT-4026):
 * Verifies elimination of hardcoded literals, accessibility semantics coverage,
 * and imperial unit conversion correctness across migrated Compose screens and components.
 */
class HardcodedStringsAndUnitsContractTest {

    private fun resolveProjectRoot(): File {
        val userDir = File(System.getProperty("user.dir") ?: ".")
        return if (File(userDir, "app/src/main").exists()) {
            userDir
        } else {
            userDir.parentFile ?: userDir
        }
    }

    private fun loadSource(relativePath: String): String {
        val file = File(resolveProjectRoot(), relativePath)
        assertTrue("Source file must exist: $relativePath", file.exists())
        return file.readText()
    }

    @Test
    fun `imperial conversion constants match BANAL standards`() {
        assertEquals(1609.344, BANALService.METER_PER_MILE, 0.0001)
        assertEquals(0.3048, BANALService.METER_PER_FOOT, 0.0001)
    }

    @Test
    fun `CropWorkoutScreen has eliminated hardcoded German literals and uses formatters and string resources`() {
        val content = loadSource("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/crop/CropWorkoutScreen.kt")
        assertFalse("CropWorkoutScreen must not contain hardcoded 'Distanz:'", content.contains("\"Distanz:\""))
        assertFalse("CropWorkoutScreen must not contain hardcoded 'Dauer:'", content.contains("\"Dauer:\""))
        assertFalse("CropWorkoutScreen must not contain hardcoded 'Ø Geschwindigkeit:'", content.contains("\"Ø Geschwindigkeit:\""))

        assertTrue("CropWorkoutScreen must use crop_start_trim resource", content.contains("R.string.crop_start_trim"))
        assertTrue("CropWorkoutScreen must use crop_end_trim resource", content.contains("R.string.crop_end_trim"))
        assertTrue("CropWorkoutScreen must use crop_distance_label resource", content.contains("R.string.crop_distance_label"))
        assertTrue("CropWorkoutScreen must use crop_duration_label resource", content.contains("R.string.crop_duration_label"))
        assertTrue("CropWorkoutScreen must use crop_avg_speed_label resource", content.contains("R.string.crop_avg_speed_label"))
        assertTrue("CropWorkoutScreen must format distance with DistanceFormatter", content.contains("distanceFormatter.format_with_units"))
        assertTrue("CropWorkoutScreen must format speed with SpeedFormatter", content.contains("speedFormatter.format_with_units"))
    }

    @Test
    fun `WorkoutFilterBottomSheet supports imperial distance and elevation presets and parsing`() {
        val content = loadSource("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutFilterBottomSheet.kt")
        assertTrue("WorkoutFilterBottomSheet must check imperial unit preference", content.contains("MyUnits.IMPERIAL"))
        assertTrue("WorkoutFilterBottomSheet must use METER_PER_MILE conversion", content.contains("BANALService.METER_PER_MILE"))
        assertTrue("WorkoutFilterBottomSheet must use METER_PER_FOOT conversion", content.contains("BANALService.METER_PER_FOOT"))
        assertTrue("WorkoutFilterBottomSheet must define miles preset labels", content.contains("\"mi\""))
        assertTrue("WorkoutFilterBottomSheet must define feet preset labels", content.contains("\"ft\""))
    }

    @Test
    fun `ImportBackupTabsScreen uses string resources for title mapping and select route contentDescription`() {
        val content = loadSource("app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt")
        assertTrue("ImportBackupTabsScreen must use select_route resource", content.contains("R.string.select_route"))
        assertTrue("ImportBackupTabsScreen must use import_map_data_title resource", content.contains("R.string.import_map_data_title"))
        assertTrue("ImportBackupTabsScreen must use sport_types resource", content.contains("R.string.sport_types"))
        assertTrue("ImportBackupTabsScreen must use unknown resource", content.contains("R.string.unknown"))
        assertTrue("ImportBackupTabsScreen must use equipment resource", content.contains("R.string.equipment"))
        assertTrue("ImportBackupTabsScreen must use none resource", content.contains("R.string.none"))
    }

    @Test
    fun `MainActivityWithNavigation and ZoneCalculatorDialog use extracted string resources`() {
        val mainNav = loadSource("app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt")
        assertTrue("MainActivityWithNavigation must use destination_not_yet_implemented", mainNav.contains("R.string.destination_not_yet_implemented"))
        assertTrue("MainActivityWithNavigation must use sensor string resource fallback", mainNav.contains("R.string.sensor"))

        val zoneDialog = loadSource("app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/ZoneCalculatorDialog.kt")
        assertTrue("ZoneCalculatorDialog must use unit_watt resource", zoneDialog.contains("R.string.unit_watt"))
        assertTrue("ZoneCalculatorDialog must use units_heart_rate resource", zoneDialog.contains("R.string.units_heart_rate"))
    }

    @Test
    fun `LapEditBottomSheet and PeriodSummaryCard use string resources`() {
        val lapSheet = loadSource("app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/LapEditBottomSheet.kt")
        assertTrue("LapEditBottomSheet must use lap_placeholder_format", lapSheet.contains("R.string.lap_placeholder_format"))
        assertTrue("LapEditBottomSheet must use clear content description", lapSheet.contains("R.string.clear"))

        val periodCard = loadSource("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodSummaryCard.kt")
        assertTrue("PeriodSummaryCard must use period_no_gps_data", periodCard.contains("R.string.period_no_gps_data"))
    }

    @Test
    fun `Map screens use localized marker titles`() {
        val trackMap = loadSource("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt")
        assertTrue("TrackOnMapScreen must use marker_lap_start", trackMap.contains("R.string.marker_lap_start"))
        assertTrue("TrackOnMapScreen must use marker_lap_end", trackMap.contains("R.string.marker_lap_end"))

        val climbMap = loadSource("app/src/main/java/com/atrainingtracker/trainingtracker/ui/climbs/ClimbOnMapScreen.kt")
        assertTrue("ClimbOnMapScreen must use marker_start", climbMap.contains("R.string.marker_start"))
        assertTrue("ClimbOnMapScreen must use marker_summit", climbMap.contains("R.string.marker_summit"))

        val routeMap = loadSource("app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteOnMapScreen.kt")
        assertTrue("RouteOnMapScreen must use marker_start", routeMap.contains("R.string.marker_start"))
        assertTrue("RouteOnMapScreen must use marker_end", routeMap.contains("R.string.marker_end"))
    }

    @Test
    fun `Accessibility content descriptions and navigation icon buttons are localized`() {
        val lapSplit = loadSource("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/splits/LapSplitVisualizer.kt")
        assertTrue("LapSplitVisualizer must use cd_fastest_split", lapSplit.contains("R.string.cd_fastest_split"))

        val workoutActions = loadSource("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutListActions.kt")
        assertTrue("WorkoutListActions must use cd_switch_view_mode", workoutActions.contains("R.string.cd_switch_view_mode"))

        val stravaBtn = loadSource("app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/strava/ConnectWithStravaButton.kt")
        assertTrue("ConnectWithStravaButton must use connect_with_strava", stravaBtn.contains("R.string.connect_with_strava"))

        val elevation = loadSource("app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt")
        assertTrue("ElevationProfile must use legend string resource", elevation.contains("R.string.legend"))

        val sensorGrid = loadSource("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        assertTrue("SensorGridScreen must use cd_add_row", sensorGrid.contains("R.string.cd_add_row"))
        assertTrue("SensorGridScreen must use cd_add_column", sensorGrid.contains("R.string.cd_add_column"))

        val tabConfigHeader = loadSource("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabConfigHeader.kt")
        assertTrue("TrackingTabConfigHeader must use cd_add_before", tabConfigHeader.contains("R.string.cd_add_before"))
        assertTrue("TrackingTabConfigHeader must use cd_delete_tab", tabConfigHeader.contains("R.string.cd_delete_tab"))
        assertTrue("TrackingTabConfigHeader must use cd_add_after", tabConfigHeader.contains("R.string.cd_add_after"))

        val clusterTuning = loadSource("app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterTuningScreen.kt")
        assertTrue("ClusterTuningScreen must use back resource", clusterTuning.contains("R.string.back"))

        val manualCluster = loadSource("app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ManualClusterScreen.kt")
        assertTrue("ManualClusterScreen must use back resource", manualCluster.contains("R.string.back"))

        val workoutClusters = loadSource("app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersScreen.kt")
        assertTrue("WorkoutClustersScreen must use back resource", workoutClusters.contains("R.string.back"))

        val heatmap = loadSource("app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterHeatmapScreen.kt")
        assertTrue("WorkoutClusterHeatmapScreen must use close resource", heatmap.contains("R.string.close"))
    }

    @Test
    fun `TrainingApplication todo method is marked as Deprecated`() {
        val content = loadSource("app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java")
        assertTrue("todo method in TrainingApplication must be marked @Deprecated", content.contains("@Deprecated\n    public void todo"))
    }
}
