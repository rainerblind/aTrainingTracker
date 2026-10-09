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

package com.atrainingtracker.trainingtracker.ui.routes

import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.routes.ForkBranchOption
import com.atrainingtracker.trainingtracker.routes.ForkDecisionState
import com.atrainingtracker.trainingtracker.routes.ForkDirection
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Contract, layout, and state verification for ForkDecisionCard (REQ-MAP-031, REQ-UI-330 / TST-UI-290).
 */
class ForkDecisionCardTest {

    private val locales = listOf("", "de", "es", "fr", "it", "ja", "nl", "pl", "pt")

    private val forkAlertKeys = listOf(
        "fork_alert_title",
        "fork_approaching_in_m",
        "fork_dismiss",
        "fork_direction_left",
        "fork_direction_straight",
        "fork_direction_right"
    )

    @Test
    fun forkDirection_hasValidStringResources() {
        assertEquals(R.string.fork_direction_left, ForkDirection.LEFT.labelRes)
        assertEquals(R.string.fork_direction_straight, ForkDirection.STRAIGHT.labelRes)
        assertEquals(R.string.fork_direction_right, ForkDirection.RIGHT.labelRes)
    }

    @Test
    fun forkDecisionState_containsAllBranchDetails() {
        val branchA = ForkBranchOption(
            routeId = 101L,
            routeName = "Waldenbuch Scenic Loop",
            totalDistanceMeters = 38400.0,
            totalElevationMeters = 420.0,
            direction = ForkDirection.LEFT,
            bearingDiffDegrees = -35.0
        )
        val branchB = ForkBranchOption(
            routeId = 102L,
            routeName = "Dettenhausen Fast Ridge",
            totalDistanceMeters = 54200.0,
            totalElevationMeters = 680.0,
            direction = ForkDirection.RIGHT,
            bearingDiffDegrees = 40.0
        )

        val state = ForkDecisionState(
            divergenceCoordinate = LatLng(48.5100, 9.0000),
            distanceToForkMeters = 245.0,
            branches = listOf(branchA, branchB),
            isApproaching = true
        )

        assertNotNull(state)
        assertEquals(2, state.branches.size)
        assertEquals(245.0, state.distanceToForkMeters, 0.1)
        assertTrue(state.isApproaching)

        val firstBranch = state.branches[0]
        assertEquals(101L, firstBranch.routeId)
        assertEquals("Waldenbuch Scenic Loop", firstBranch.routeName)
        assertEquals(38400.0, firstBranch.totalDistanceMeters, 0.1)
        assertEquals(420.0, firstBranch.totalElevationMeters, 0.1)
        assertEquals(ForkDirection.LEFT, firstBranch.direction)

        val secondBranch = state.branches[1]
        assertEquals(102L, secondBranch.routeId)
        assertEquals("Dettenhausen Fast Ridge", secondBranch.routeName)
        assertEquals(ForkDirection.RIGHT, secondBranch.direction)
    }

    @Test
    fun forkDirectionGrouping_groupsBranchesByDirectionCorrectly() {
        val branchStraight1 = ForkBranchOption(1L, "Route A", 10000.0, 100.0, ForkDirection.STRAIGHT, 0.0)
        val branchStraight2 = ForkBranchOption(2L, "Route B", 12000.0, 150.0, ForkDirection.STRAIGHT, 2.0)
        val branchRight = ForkBranchOption(3L, "Route C", 15000.0, 200.0, ForkDirection.RIGHT, 45.0)

        val state = ForkDecisionState(
            divergenceCoordinate = LatLng(48.5000, 9.0000),
            distanceToForkMeters = 150.0,
            branches = listOf(branchStraight1, branchStraight2, branchRight),
            isApproaching = true
        )

        val grouped = state.branches.groupBy { it.direction }
        assertEquals(2, grouped.size)
        assertTrue(grouped.containsKey(ForkDirection.STRAIGHT))
        assertTrue(grouped.containsKey(ForkDirection.RIGHT))
        assertEquals(2, grouped[ForkDirection.STRAIGHT]?.size)
        assertEquals(1, grouped[ForkDirection.RIGHT]?.size)
        assertEquals("Route A", grouped[ForkDirection.STRAIGHT]?.get(0)?.routeName)
        assertEquals("Route B", grouped[ForkDirection.STRAIGHT]?.get(1)?.routeName)
    }

    @Test
    fun forkAlertStrings_existAcrossAll9Languages() {
        val resDir = findResDirectory()

        for (locale in locales) {
            val dirName = if (locale.isEmpty()) "values" else "values-$locale"
            val file = File(resDir, "$dirName/strings.xml")
            assertTrue("strings.xml must exist for locale $dirName", file.exists())

            val stringMap = parseStringsFile(file)
            for (key in forkAlertKeys) {
                assertTrue(
                    "Key '$key' must exist in $dirName/strings.xml",
                    stringMap.containsKey(key)
                )
                assertFalse(
                    "Value for '$key' in $dirName/strings.xml must not be blank",
                    stringMap[key]?.isBlank() ?: true
                )
            }
        }
    }

    @Test
    fun forkDecisionCardLayout_verifiesM3TokensAndStructure() {
        val sourceFile = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/ForkDecisionCard.kt")
        assertTrue("ForkDecisionCard.kt source must exist", sourceFile.exists())
        val content = sourceFile.readText()

        // Verify clean surface styling
        assertTrue("ForkDecisionCard must use surface color scheme", content.contains("MaterialTheme.colorScheme.surface"))
        assertTrue("ForkDecisionCard must use outlineVariant border", content.contains("outlineVariant"))

        // Verify direction grouping
        assertTrue("ForkDecisionCard must group branches by direction", content.contains(".groupBy { it.direction }"))
        assertTrue("ForkDecisionCard must declare ForkDirectionGroup", content.contains("fun ForkDirectionGroup"))

        // Verify tap hint removed
        assertFalse("ForkDecisionCard must NOT render fork_select_hint", content.contains("R.string.fork_select_hint"))
    }

    private fun findResDirectory(): File {
        val candidates = listOf(
            File("app/src/main/res"),
            File("../app/src/main/res"),
            File("../../app/src/main/res")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: throw IllegalStateException("Could not find app/src/main/res directory")
    }

    private fun resolveSourceFile(path: String): File {
        val candidates = listOf(
            File(path),
            File("../$path"),
            File("../../$path")
        )
        return candidates.firstOrNull { it.exists() }
            ?: File(path)
    }

    private fun parseStringsFile(file: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val stringNodes = doc.getElementsByTagName("string")
        val result = mutableMapOf<String, String>()
        for (i in 0 until stringNodes.length) {
            val item = stringNodes.item(i) as Element
            val name = item.getAttribute("name")
            result[name] = item.textContent
        }
        return result
    }
}
