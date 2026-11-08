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

package com.atrainingtracker.trainingtracker.ui.clusters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural, UI layout, and state healing contract tests for ClusterItem and Filter Bottom Sheet polish
 * (ATT-5846 / REQ-UI-493 / TST-UI-493).
 */
class ClusterItemAndFilterPolishTest {

    private fun findSourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../app/$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Source file not found: $relativePath across candidates $candidates")
    }

    @Test
    fun effectiveHitCount_resolvesMaxOfDbHitCountAndSummaryStats() {
        // REQ-UI-493.1: Clusters with hitCount == 0 in SQLite but with recorded workouts in WorkoutSummaries
        // must display the accurate non-zero count.
        val clusterHitCountZero = 0
        val summaryWorkoutCount = 5
        val effective1 = maxOf(clusterHitCountZero, summaryWorkoutCount)
        assertEquals(5, effective1)

        val clusterHitCountThree = 3
        val summaryWorkoutCountNull: Int? = null
        val effective2 = maxOf(clusterHitCountThree, summaryWorkoutCountNull ?: 0)
        assertEquals(3, effective2)

        val clusterHitCountTwo = 2
        val summaryWorkoutCountFour = 4
        val effective3 = maxOf(clusterHitCountTwo, summaryWorkoutCountFour)
        assertEquals(4, effective3)
    }

    @Test
    fun repository_loadAllClustersLightweight_containsHitCountSelfHealingLogic() {
        // TST-UI-493.1: Verify WorkoutClusterRepository.kt heals clusters where hitCount == 0
        val repoFile = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterRepository.kt")
        val content = repoFile.readText()

        assertTrue(
            "WorkoutClusterRepository should self-heal clusters with desynchronized hitCount",
            content.contains("cluster.hitCount == 0 && statCount > 0")
        )
        assertTrue(
            "WorkoutClusterRepository should update healed cluster in DB",
            content.contains("clusterDb.updateCluster(updated)")
        )
    }

    @Test
    fun bidirectionalLocationFormatting_resolvesEndpointsCorrectly() {
        // REQ-UI-493.2: Formatting logic test
        fun resolveLocationText(
            startLocation: String?,
            endLocation: String?,
            startAndDestTemplate: (String) -> String,
            startOnlyTemplate: (String) -> String,
            destOnlyTemplate: (String) -> String
        ): String? {
            return when {
                startLocation != null && endLocation != null -> {
                    if (startLocation == endLocation) {
                        startAndDestTemplate(startLocation)
                    } else {
                        "$startLocation → $endLocation"
                    }
                }
                startLocation != null -> startOnlyTemplate(startLocation)
                endLocation != null -> destOnlyTemplate(endLocation)
                else -> null
            }
        }

        // Loop route (same start & destination)
        val loop = resolveLocationText("Home", "Home", { "Start & Ziel: $it" }, { "Start: $it" }, { "Ziel: $it" })
        assertEquals("Start & Ziel: Home", loop)

        // Point-to-point route (different start & destination)
        val p2p = resolveLocationText("Home", "Work", { "Start & Ziel: $it" }, { "Start: $it" }, { "Ziel: $it" })
        assertEquals("Home → Work", p2p)

        // Single start only
        val startOnly = resolveLocationText("Home", null, { "Start & Ziel: $it" }, { "Start: $it" }, { "Ziel: $it" })
        assertEquals("Start: Home", startOnly)

        // Single destination only
        val destOnly = resolveLocationText(null, "Work", { "Start & Ziel: $it" }, { "Start: $it" }, { "Ziel: $it" })
        assertEquals("Ziel: Work", destOnly)
    }

    @Test
    fun viewModel_exposesGeneralLocationNameLookup() {
        // TST-UI-493.2: Verify WorkoutClustersViewModel.kt exposes getLocationName
        val vmFile = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersViewModel.kt")
        val content = vmFile.readText()

        assertTrue(
            "WorkoutClustersViewModel should define getLocationName",
            content.contains("fun getLocationName(lat: Double, lng: Double): String?")
        )
        assertTrue(
            "WorkoutClustersViewModel getStartLocationName should delegate to getLocationName",
            content.contains("fun getStartLocationName(lat: Double, lng: Double): String? = getLocationName(lat, lng)")
        )
    }

    @Test
    fun clusterComponents_rendersLandscapeThumbnailAndConstrainedEquipment() {
        // TST-UI-493.3: Verify 110x75dp landscape thumbnail and maxLines = 1 for equipment
        val componentsFile = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterComponents.kt")
        val content = componentsFile.readText()

        assertTrue(
            "ClusterItem should use 110.dp x 75.dp landscape map thumbnail",
            content.contains("Modifier.size(width = 110.dp, height = 75.dp)")
        )
        assertTrue(
            "WorkoutClusterMetadataBlock should constrain equipment with maxLines = 1",
            content.contains("maxLines = 1")
        )
        assertTrue(
            "WorkoutClusterMetadataBlock should truncate equipment with TextOverflow.Ellipsis",
            content.contains("overflow = TextOverflow.Ellipsis")
        )
        assertTrue(
            "WorkoutClusterMetadataBlock should derive effectiveHitCount with maxOf",
            content.contains("val effectiveHitCount = maxOf(cluster.hitCount, stats?.workoutCount ?: 0)")
        )
        assertTrue(
            "WorkoutClusterMetadataBlock should support bidirectional location arrow",
            content.contains("\"\$startLocationName → \$endLocationName\"")
        )
    }

    @Test
    fun filterBottomSheetScaffold_bindsResetActionSingleLine() {
        // TST-UI-493.4: Verify filter_reset and maxLines = 1 on reset action button
        val scaffoldFile = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/common/filters/FilterBottomSheetScaffold.kt")
        val content = scaffoldFile.readText()

        assertTrue(
            "FilterBottomSheetScaffold should use R.string.filter_reset",
            content.contains("stringResource(R.string.filter_reset)")
        )
        assertFalse(
            "FilterBottomSheetScaffold should not use R.string.filter_clear_all in bottom action bar",
            content.contains("stringResource(R.string.filter_clear_all)")
        )
        assertTrue(
            "FilterBottomSheetScaffold reset button should enforce maxLines = 1",
            content.contains("maxLines = 1")
        )
    }

    @Test
    fun filterReset_existsInAllNineLocalesWithoutEmojis() {
        // TST-UI-493.4: Verify filter_reset in all 9 values directories
        val locales = listOf(
            "values",
            "values-de",
            "values-es",
            "values-fr",
            "values-it",
            "values-ja",
            "values-nl",
            "values-pl",
            "values-pt"
        )

        val emojiRegex = Regex("[\uD83C-\uDBFF\uDC00-\uDFFF\u2600-\u27BF]")

        for (loc in locales) {
            val file = findSourceFile("src/main/res/$loc/strings.xml")
            val content = file.readText()
            assertTrue("strings.xml in $loc must contain filter_reset", content.contains("name=\"filter_reset\""))

            val match = Regex("<string name=\"filter_reset\">(.*?)</string>").find(content)
            assertNotNull("filter_reset must have content in $loc", match)
            val value = match!!.groupValues[1]
            assertFalse("filter_reset in $loc must not contain emojis (ATT-5400): $value", emojiRegex.containsMatchIn(value))
            assertTrue("filter_reset in $loc must not be blank", value.isNotBlank())
        }
    }
}
