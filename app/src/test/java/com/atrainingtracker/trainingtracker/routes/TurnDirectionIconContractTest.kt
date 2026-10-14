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

package com.atrainingtracker.trainingtracker.routes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Contract verification test suite for Turn-by-Turn Navigation Vector Icons and Preview Gallery (REQ-UI-360 / TST-UI-323).
 *
 * Verifies XML schema, 24x24 viewport, theme-aware fillColor (?attr/colorPrimary),
 * safety comment headers, mathematical reflection symmetry (x' = 24 - x),
 * and Compose @Preview gallery declarations.
 */
class TurnDirectionIconContractTest {

    private val resDrawableDir = File("src/main/res/drawable").let {
        if (it.exists()) it else File("app/src/main/res/drawable")
    }

    private val turnPromptBannerFile = File("src/main/java/com/atrainingtracker/trainingtracker/ui/routes/TurnPromptBanner.kt").let {
        if (it.exists()) it else File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/TurnPromptBanner.kt")
    }

    @Test
    fun testAllTurnDirectionDrawablesExistAndHaveStandard24dpViewport() {
        assertTrue("Drawable directory must exist", resDrawableDir.exists())

        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()

        for (direction in TurnDirection.values()) {
            val fileName = when (direction) {
                TurnDirection.LEFT -> "ic_turn_left.xml"
                TurnDirection.RIGHT -> "ic_turn_right.xml"
                TurnDirection.SLIGHT_LEFT -> "ic_turn_slight_left.xml"
                TurnDirection.SLIGHT_RIGHT -> "ic_turn_slight_right.xml"
                TurnDirection.SHARP_LEFT -> "ic_turn_sharp_left.xml"
                TurnDirection.SHARP_RIGHT -> "ic_turn_sharp_right.xml"
                TurnDirection.STRAIGHT -> "ic_turn_straight.xml"
                TurnDirection.U_TURN -> "ic_turn_u_turn.xml"
            }

            val file = File(resDrawableDir, fileName)
            assertTrue("Drawable file $fileName must exist for $direction", file.exists())

            val doc = dBuilder.parse(file)
            val root = doc.documentElement
            assertEquals("Root element of $fileName must be vector", "vector", root.nodeName)

            assertEquals("24dp", root.getAttribute("android:width"))
            assertEquals("24dp", root.getAttribute("android:height"))
            assertEquals("24", root.getAttribute("android:viewportWidth"))
            assertEquals("24", root.getAttribute("android:viewportHeight"))

            val paths = root.getElementsByTagName("path")
            assertTrue("$fileName must contain at least one path node", paths.length >= 1)
            val pathElement = paths.item(0) as Element
            val pathData = pathElement.getAttribute("android:pathData")
            assertNotNull("pathData must not be null in $fileName", pathData)
            assertTrue("pathData must not be empty in $fileName", pathData.isNotBlank())
        }
    }

    @Test
    fun testSlightTurnIconsHaveStandardColorAndSafetyHeader() {
        val slightRightFile = File(resDrawableDir, "ic_turn_slight_right.xml")
        val slightLeftFile = File(resDrawableDir, "ic_turn_slight_left.xml")

        for (file in listOf(slightRightFile, slightLeftFile)) {
            val content = file.readText()
            assertTrue(
                "${file.name} must contain contract safety header comment",
                content.contains("DO NOT MODIFY PATH DATA WITHOUT UPDATING TST-UI-323")
            )

            val dbFactory = DocumentBuilderFactory.newInstance()
            val doc = dbFactory.newDocumentBuilder().parse(file)
            val path = doc.documentElement.getElementsByTagName("path").item(0) as Element
            val fillColor = path.getAttribute("android:fillColor")

            assertEquals(
                "${file.name} must use standard fillColor #FF1976D2 for Layoutlib/Compose preview compatibility",
                "#FF1976D2",
                fillColor
            )
        }
    }

    @Test
    fun testSlightTurnIconsGroundingAndStemWidth() {
        val rightFile = File(resDrawableDir, "ic_turn_slight_right.xml")
        val leftFile = File(resDrawableDir, "ic_turn_slight_left.xml")

        val rightContent = rightFile.readText()
        val leftContent = leftFile.readText()

        // Stem must be grounded at baseline y = 20
        assertTrue("Right turn icon must ground stem at V20", rightContent.contains("V20"))
        assertTrue("Left turn icon must ground stem at V20", leftContent.contains("V20"))

        // Stem width must be 2dp at baseline
        assertTrue("Right turn icon must have 2dp stem baseline cut h-2", rightContent.contains("h-2"))
        assertTrue("Left turn icon must have 2dp stem baseline cut h2", leftContent.contains("h2"))
    }

    @Test
    fun testTurnSlightLeftIsExactHorizontalReflectionOfTurnSlightRight() {
        val rightFile = File(resDrawableDir, "ic_turn_slight_right.xml")
        val leftFile = File(resDrawableDir, "ic_turn_slight_left.xml")

        val dbFactory = DocumentBuilderFactory.newInstance()
        val rightPath = (dbFactory.newDocumentBuilder().parse(rightFile)
            .documentElement.getElementsByTagName("path").item(0) as Element)
            .getAttribute("android:pathData")

        val leftPath = (dbFactory.newDocumentBuilder().parse(leftFile)
            .documentElement.getElementsByTagName("path").item(0) as Element)
            .getAttribute("android:pathData")

        val ptsRight = evaluateAbsoluteMilestones(rightPath)
        val ptsLeft = evaluateAbsoluteMilestones(leftPath)

        assertEquals("Both paths must have identical milestone vertex count", ptsRight.size, ptsLeft.size)

        for (i in ptsRight.indices) {
            val r = ptsRight[i]
            val l = ptsLeft[i]

            val sumX = r.first + l.first
            val diffY = kotlin.math.abs(r.second - l.second)

            assertEquals(
                "Milestone $i: x_R (${r.first}) + x_L (${l.first}) must equal 24.0 (midline reflection across x=12)",
                24.0,
                sumX,
                0.01
            )
            assertEquals(
                "Milestone $i: y_R (${r.second}) must equal y_L (${l.second})",
                0.0,
                diffY,
                0.01
            )
        }
    }

    @Test
    fun testTurnPromptBannerContainsPrivateGalleryAndDualThemePreviews() {
        assertTrue("TurnPromptBanner.kt must exist", turnPromptBannerFile.exists())
        val content = turnPromptBannerFile.readText()

        assertTrue(
            "TurnPromptBanner.kt must contain TurnCueIconsGallery composable",
            content.contains("fun TurnCueIconsGallery(")
        )
        assertTrue(
            "TurnCueIconsGallery must be private for R8 production stripping",
            content.contains("private fun TurnCueIconsGallery(")
        )
        assertTrue(
            "TurnPromptBanner.kt must declare Light theme preview",
            content.contains("@Preview(name = \"Turn Cue Icons Gallery - Light\"")
        )
        assertTrue(
            "TurnPromptBanner.kt must declare Dark theme preview with UI_MODE_NIGHT_YES",
            content.contains("UI_MODE_NIGHT_YES")
        )
    }

    @Test
    fun testSharpTurnIconsHaveStandardColorAndSafetyHeader() {
        val sharpRightFile = File(resDrawableDir, "ic_turn_sharp_right.xml")
        val sharpLeftFile = File(resDrawableDir, "ic_turn_sharp_left.xml")

        for (file in listOf(sharpRightFile, sharpLeftFile)) {
            val content = file.readText()
            assertTrue(
                "${file.name} must contain contract safety header comment",
                content.contains("DO NOT MODIFY PATH DATA WITHOUT UPDATING TST-UI-324")
            )

            val dbFactory = DocumentBuilderFactory.newInstance()
            val doc = dbFactory.newDocumentBuilder().parse(file)
            val path = doc.documentElement.getElementsByTagName("path").item(0) as Element
            val fillColor = path.getAttribute("android:fillColor")

            assertEquals(
                "${file.name} must use standard fillColor #FF1976D2",
                "#FF1976D2",
                fillColor
            )
        }
    }

    @Test
    fun testSharpTurnIconsGroundingAndStemWidth() {
        val rightFile = File(resDrawableDir, "ic_turn_sharp_right.xml")
        val leftFile = File(resDrawableDir, "ic_turn_sharp_left.xml")

        val rightContent = rightFile.readText()
        val leftContent = leftFile.readText()

        // Stem must be grounded at baseline y = 20
        assertTrue("Right turn sharp icon must start stem at y=20 (M6,20)", rightContent.contains("M6,20"))
        assertTrue("Right turn sharp icon must close stem at y=20 (V20z)", rightContent.contains("V20z"))
        assertTrue("Left turn sharp icon must start stem at y=20 (M18,20)", leftContent.contains("M18,20"))
        assertTrue("Left turn sharp icon must close stem at y=20 (V20z)", leftContent.contains("V20z"))

        // Stem width must be 2dp at baseline
        assertTrue("Right turn sharp icon must have 2dp stem baseline cut h2", rightContent.contains("h2"))
        assertTrue("Left turn sharp icon must have 2dp stem baseline cut h-2", leftContent.contains("h-2"))
    }

    @Test
    fun testTurnSharpLeftIsExactHorizontalReflectionOfTurnSharpRight() {
        val rightFile = File(resDrawableDir, "ic_turn_sharp_right.xml")
        val leftFile = File(resDrawableDir, "ic_turn_sharp_left.xml")

        val dbFactory = DocumentBuilderFactory.newInstance()
        val rightPath = (dbFactory.newDocumentBuilder().parse(rightFile)
            .documentElement.getElementsByTagName("path").item(0) as Element)
            .getAttribute("android:pathData")

        val leftPath = (dbFactory.newDocumentBuilder().parse(leftFile)
            .documentElement.getElementsByTagName("path").item(0) as Element)
            .getAttribute("android:pathData")

        val ptsRight = evaluateAbsoluteMilestones(rightPath)
        val ptsLeft = evaluateAbsoluteMilestones(leftPath)

        assertEquals("Both paths must have identical milestone vertex count", ptsRight.size, ptsLeft.size)

        for (i in ptsRight.indices) {
            val r = ptsRight[i]
            val l = ptsLeft[i]

            val sumX = r.first + l.first
            val diffY = kotlin.math.abs(r.second - l.second)

            assertEquals(
                "Milestone $i: x_R (${r.first}) + x_L (${l.first}) must equal 24.0 (midline reflection across x=12)",
                24.0,
                sumX,
                0.01
            )
            assertEquals(
                "Milestone $i: y_R (${r.second}) must equal y_L (${l.second})",
                0.0,
                diffY,
                0.01
            )
        }
    }

    private fun evaluateAbsoluteMilestones(pathData: String): List<Pair<Double, Double>> {
        val pattern = Pattern.compile("([A-Za-z]|[-+]?[0-9]*\\.?[0-9]+)")
        val matcher = pattern.matcher(pathData)
        val tokens = mutableListOf<String>()
        while (matcher.find()) {
            tokens.add(matcher.group())
        }

        val milestones = mutableListOf<Pair<Double, Double>>()
        var x = 0.0
        var y = 0.0
        var i = 0

        while (i < tokens.size) {
            when (val cmd = tokens[i++]) {
                "M" -> {
                    x = tokens[i++].toDouble()
                    y = tokens[i++].toDouble()
                    milestones.add(Pair(x, y))
                }
                "V" -> {
                    y = tokens[i++].toDouble()
                    milestones.add(Pair(x, y))
                }
                "v" -> {
                    y += tokens[i++].toDouble()
                    milestones.add(Pair(x, y))
                }
                "H" -> {
                    x = tokens[i++].toDouble()
                    milestones.add(Pair(x, y))
                }
                "h" -> {
                    x += tokens[i++].toDouble()
                    milestones.add(Pair(x, y))
                }
                "l" -> {
                    x += tokens[i++].toDouble()
                    y += tokens[i++].toDouble()
                    milestones.add(Pair(x, y))
                }
                "L" -> {
                    x = tokens[i++].toDouble()
                    y = tokens[i++].toDouble()
                    milestones.add(Pair(x, y))
                }
                "c" -> {
                    val dx1 = tokens[i++].toDouble()
                    val dy1 = tokens[i++].toDouble()
                    val dx2 = tokens[i++].toDouble()
                    val dy2 = tokens[i++].toDouble()
                    val dx = tokens[i++].toDouble()
                    val dy = tokens[i++].toDouble()

                    // Add both control points and curve endpoint
                    milestones.add(Pair(x + dx1, y + dy1))
                    milestones.add(Pair(x + dx2, y + dy2))
                    x += dx
                    y += dy
                    milestones.add(Pair(x, y))
                }
                "z", "Z" -> {
                    // Close loop
                }
            }
        }

        return milestones
    }
}
