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
 * Contract verification test suite for Turn-by-Turn Navigation Vector Icons and Preview Gallery
 * (REQ-UI-360 / TST-UI-323, REQ-UI-361 / TST-UI-324, REQ-UI-389 / TST-UI-352).
 *
 * Verifies XML schema, 24x24 viewport, theme-aware fillColor (#FF1976D2),
 * safety comment headers, mathematical reflection symmetry (x' = 24 - x),
 * unified 8x4dp filled right-isosceles triangle arrowhead across all 8 icons,
 * y=20 baseline grounding, and Turn Left/Right vertical arrowhead centering on y=10.
 */
class TurnDirectionIconContractTest {

    private val resDrawableDir = File("src/main/res/drawable").let {
        if (it.exists()) it else File("app/src/main/res/drawable")
    }

    private val turnPromptBannerFile = File("src/main/java/com/atrainingtracker/trainingtracker/ui/routes/TurnPromptBanner.kt").let {
        if (it.exists()) it else File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/TurnPromptBanner.kt")
    }

    private fun fileNameFor(direction: TurnDirection): String = when (direction) {
        TurnDirection.LEFT -> "ic_turn_left.xml"
        TurnDirection.RIGHT -> "ic_turn_right.xml"
        TurnDirection.SLIGHT_LEFT -> "ic_turn_slight_left.xml"
        TurnDirection.SLIGHT_RIGHT -> "ic_turn_slight_right.xml"
        TurnDirection.SHARP_LEFT -> "ic_turn_sharp_left.xml"
        TurnDirection.SHARP_RIGHT -> "ic_turn_sharp_right.xml"
        TurnDirection.STRAIGHT -> "ic_turn_straight.xml"
        TurnDirection.U_TURN -> "ic_turn_u_turn.xml"
    }

    @Test
    fun testAllTurnDirectionDrawablesExistAndHaveStandard24dpViewport() {
        assertTrue("Drawable directory must exist", resDrawableDir.exists())

        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()

        for (direction in TurnDirection.values()) {
            val fileName = fileNameFor(direction)
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
            assertEquals("$fileName must contain exactly one path node", 1, paths.length)
            val pathElement = paths.item(0) as Element
            val pathData = pathElement.getAttribute("android:pathData")
            assertNotNull("pathData must not be null in $fileName", pathData)
            assertTrue("pathData must not be empty in $fileName", pathData.isNotBlank())
        }
    }

    @Test
    fun testAllTurnIconsAreSingleSubpathGroundedAtY20With2dpStemAndMarginClearance() {
        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()

        for (direction in TurnDirection.values()) {
            val fileName = fileNameFor(direction)
            val file = File(resDrawableDir, fileName)
            val doc = dBuilder.parse(file)
            val pathElement = doc.documentElement.getElementsByTagName("path").item(0) as Element
            val pathData = pathElement.getAttribute("android:pathData")

            val moveCount = pathData.count { it == 'M' || it == 'm' }
            assertEquals("$fileName must be a single continuous closed subpath (1 Move command)", 1, moveCount)

            assertTrue("$fileName must close stem at y=20 (V20z)", pathData.endsWith("V20z"))
            assertTrue(
                "$fileName must have a 2dp horizontal stem cut at y=20 (h2 or h-2)",
                pathData.contains("20h2") || pathData.contains("20h-2")
            )

            val milestones = evaluateAbsoluteMilestones(pathData)
            assertEquals("$fileName must start at y=20.0", 20.0, milestones.first().second, 0.01)
            assertEquals("$fileName must end at y=20.0 before closing", 20.0, milestones.last().second, 0.01)

            for ((idx, pt) in milestones.withIndex()) {
                assertTrue(
                    "$fileName milestone $idx x=${pt.first} must stay within [3.0, 21.0]",
                    pt.first in 2.99..21.01
                )
                assertTrue(
                    "$fileName milestone $idx y=${pt.second} must stay within [3.0, 20.0]",
                    pt.second in 2.99..20.01
                )
            }
        }
    }

    @Test
    fun testAllTurnIconsUseUnified8x4FilledRightIsoscelesArrowhead() {
        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()

        // Orthogonal icons: wing overhang = 3dp (v3 / v-3 / h3 / h-3), tip legs = (+/-4, +/-4) -> base W = 8dp, depth D = 4dp
        val orthogonalIcons = mapOf(
            "ic_turn_left.xml" to "v3l-4,-4l4,-4v3",
            "ic_turn_right.xml" to "v3l4,-4l-4,-4v3",
            "ic_turn_straight.xml" to "h3l-4,-4l-4,4h3",
            "ic_turn_u_turn.xml" to "h3l-4,4l-4,-4h3"
        )

        for ((fileName, expectedArrowheadSegment) in orthogonalIcons) {
            val file = File(resDrawableDir, fileName)
            val pathElement = dBuilder.parse(file).documentElement.getElementsByTagName("path").item(0) as Element
            val pathData = pathElement.getAttribute("android:pathData")
            assertTrue(
                "$fileName must contain unified 8x4dp orthogonal arrowhead segment '$expectedArrowheadSegment', found: $pathData",
                pathData.contains(expectedArrowheadSegment)
            )
        }

        // Diagonal 45-degree icons: wing overhang = 3dp (2.12, 2.12), tip legs = 5.66 along axes -> base W = 8dp, depth D = 4dp
        val diagonalIcons = mapOf(
            "ic_turn_slight_left.xml" to "l2.12,-2.12l-5.66,0l0,5.66l2.12,-2.12",
            "ic_turn_slight_right.xml" to "l-2.12,-2.12l5.66,0l0,5.66l-2.12,-2.12",
            "ic_turn_sharp_left.xml" to "l2.12,2.12l-5.66,0l0,-5.66l2.12,2.12",
            "ic_turn_sharp_right.xml" to "l-2.12,2.12l5.66,0l0,-5.66l-2.12,2.12"
        )

        for ((fileName, expectedArrowheadSegment) in diagonalIcons) {
            val file = File(resDrawableDir, fileName)
            val pathElement = dBuilder.parse(file).documentElement.getElementsByTagName("path").item(0) as Element
            val pathData = pathElement.getAttribute("android:pathData")
            assertTrue(
                "$fileName must contain unified 8x4dp diagonal arrowhead segment '$expectedArrowheadSegment', found: $pathData",
                pathData.contains(expectedArrowheadSegment)
            )
        }
    }

    @Test
    fun testDiagonalTurnIconsUseExactTrigonometricArrowheadConstants() {
        // Wing overhang o = 3.0dp rotated by 45 degrees -> 3 / sqrt(2) = 2.12132...
        val exactWingComponent = 3.0 / kotlin.math.sqrt(2.0)
        assertEquals("2.12 must match 3/sqrt(2) within 0.005dp", exactWingComponent, 2.12, 0.005)

        // Hypotenuse leg L = sqrt(4^2 + 4^2) = 4 * sqrt(2) = 5.65685...
        val exactLegLength = 4.0 * kotlin.math.sqrt(2.0)
        assertEquals("5.66 must match 4*sqrt(2) within 0.005dp", exactLegLength, 5.66, 0.005)

        // Resulting base width W = 2 * 2.12 * sqrt(2) + 2.0 = 8.0dp, tip depth D = 4.0dp
        val effectiveBaseWidth = 2.0 + 2.0 * (2.12 * kotlin.math.sqrt(2.0))
        val effectiveTipDepth = 5.66 / kotlin.math.sqrt(2.0)
        assertEquals("Effective diagonal arrowhead base width W must equal 8.0dp", 8.0, effectiveBaseWidth, 0.01)
        assertEquals("Effective diagonal arrowhead tip depth D must equal 4.0dp", 4.0, effectiveTipDepth, 0.01)
    }

    @Test
    fun testTurnLeftAndRightArrowheadsAreVerticallyCenteredAtY10AndSymmetric() {
        val leftFile = File(resDrawableDir, "ic_turn_left.xml")
        val rightFile = File(resDrawableDir, "ic_turn_right.xml")

        val dbFactory = DocumentBuilderFactory.newInstance()
        val leftPath = (dbFactory.newDocumentBuilder().parse(leftFile)
            .documentElement.getElementsByTagName("path").item(0) as Element)
            .getAttribute("android:pathData")
        val rightPath = (dbFactory.newDocumentBuilder().parse(rightFile)
            .documentElement.getElementsByTagName("path").item(0) as Element)
            .getAttribute("android:pathData")

        val ptsLeft = evaluateAbsoluteMilestones(leftPath)
        val ptsRight = evaluateAbsoluteMilestones(rightPath)

        assertEquals("Turn Left and Turn Right must have identical milestone vertex count", ptsRight.size, ptsLeft.size)

        // Verify horizontal reflection symmetry across x = 12.0
        for (i in ptsRight.indices) {
            val r = ptsRight[i]
            val l = ptsLeft[i]
            assertEquals(
                "Milestone $i: x_R (${r.first}) + x_L (${l.first}) must equal 24.0",
                24.0,
                r.first + l.first,
                0.01
            )
            assertEquals(
                "Milestone $i: y_R (${r.second}) must equal y_L (${l.second})",
                r.second,
                l.second,
                0.01
            )
        }

        // Verify Turn Left arrowhead vertices: lower wing (9, 14), tip (5, 10), upper wing (9, 6)
        assertTrue("Turn Left must contain lower wing vertex (9.0, 14.0)", ptsLeft.any { kotlin.math.abs(it.first - 9.0) < 0.01 && kotlin.math.abs(it.second - 14.0) < 0.01 })
        assertTrue("Turn Left must contain centered tip vertex (5.0, 10.0)", ptsLeft.any { kotlin.math.abs(it.first - 5.0) < 0.01 && kotlin.math.abs(it.second - 10.0) < 0.01 })
        assertTrue("Turn Left must contain upper wing vertex (9.0, 6.0)", ptsLeft.any { kotlin.math.abs(it.first - 9.0) < 0.01 && kotlin.math.abs(it.second - 6.0) < 0.01 })

        // Verify Turn Right arrowhead vertices: lower wing (15, 14), tip (19, 10), upper wing (15, 6)
        assertTrue("Turn Right must contain lower wing vertex (15.0, 14.0)", ptsRight.any { kotlin.math.abs(it.first - 15.0) < 0.01 && kotlin.math.abs(it.second - 14.0) < 0.01 })
        assertTrue("Turn Right must contain centered tip vertex (19.0, 10.0)", ptsRight.any { kotlin.math.abs(it.first - 19.0) < 0.01 && kotlin.math.abs(it.second - 10.0) < 0.01 })
        assertTrue("Turn Right must contain upper wing vertex (15.0, 6.0)", ptsRight.any { kotlin.math.abs(it.first - 15.0) < 0.01 && kotlin.math.abs(it.second - 6.0) < 0.01 })
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
            when (tokens[i++]) {
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
                "C" -> {
                    val x1 = tokens[i++].toDouble()
                    val y1 = tokens[i++].toDouble()
                    val x2 = tokens[i++].toDouble()
                    val y2 = tokens[i++].toDouble()
                    x = tokens[i++].toDouble()
                    y = tokens[i++].toDouble()

                    milestones.add(Pair(x1, y1))
                    milestones.add(Pair(x2, y2))
                    milestones.add(Pair(x, y))
                }
                "s" -> {
                    val dx2 = tokens[i++].toDouble()
                    val dy2 = tokens[i++].toDouble()
                    val dx = tokens[i++].toDouble()
                    val dy = tokens[i++].toDouble()

                    milestones.add(Pair(x + dx2, y + dy2))
                    x += dx
                    y += dy
                    milestones.add(Pair(x, y))
                }
                "S" -> {
                    val x2 = tokens[i++].toDouble()
                    val y2 = tokens[i++].toDouble()
                    x = tokens[i++].toDouble()
                    y = tokens[i++].toDouble()

                    milestones.add(Pair(x2, y2))
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
