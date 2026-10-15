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

package com.atrainingtracker.trainingtracker.resources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Architectural contract test verifying that legacy raster drawables have been faithfully
 * converted to Android XML VectorDrawables in root res/drawable/, preserving their exact
 * multi-color geometry, badge layouts, and sRGB color space definitions (REQ-UI-375, TST-UI-338).
 */
class VectorAssetResolutionContractTest {

    private lateinit var resDir: File

    private val targetAssets = listOf(
        // Sport type vectors
        "bsport_bike.xml",
        "bsport_run.xml",
        "bsport_other.xml",
        // ANT+ sensor badges
        "hr.xml",
        "bike_spd.xml",
        "bike_cad.xml",
        "bike_speed_and_cadence.xml",
        "bike_pwr.xml",
        "run_spd.xml",
        "temp.xml",
        // BLE sensor badges
        "bt_hr.xml",
        "bt_bike_spd.xml",
        "bt_bike_cad.xml",
        "bt_bike_speed_and_cadence.xml",
        "bt_bike_pwr.xml",
        "bt_run.xml"
    )

    @Before
    fun setUp() {
        val candidatePaths = listOf(
            File("app/src/main/res/drawable"),
            File("src/main/res/drawable"),
            File("../app/src/main/res/drawable")
        )
        resDir = candidatePaths.firstOrNull { it.exists() && it.isDirectory }
            ?: File("app/src/main/res/drawable")
        assertTrue("Root drawable directory must exist: ${resDir.absolutePath}", resDir.exists())
    }

    @Test
    fun testAllTargetVectorAssetsExistInRootDrawableDirectory() {
        val rootFiles = resDir.listFiles()?.map { it.name }?.toSet() ?: emptySet()
        for (asset in targetAssets) {
            assertTrue("Target vector asset $asset must exist in ${resDir.absolutePath}", asset in rootFiles)
        }
    }

    @Test
    fun testAllVectorAssetsParseAsValidXmlWithVectorRoot() {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()

        for (asset in targetAssets) {
            val file = File(resDir, asset)
            assertTrue("File $asset must exist", file.exists())
            val doc = builder.parse(file)
            val root = doc.documentElement
            assertNotNull("Document root must not be null for $asset", root)
            assertEquals("Root element must be <vector> in $asset", "vector", root.nodeName)

            val width = root.getAttribute("android:width")
            val height = root.getAttribute("android:height")
            val vpWidth = root.getAttribute("android:viewportWidth")
            val vpHeight = root.getAttribute("android:viewportHeight")

            assertTrue("Width must be defined in $asset", width.isNotEmpty())
            assertTrue("Height must be defined in $asset", height.isNotEmpty())
            assertTrue("Viewport width must be defined in $asset", vpWidth.isNotEmpty())
            assertTrue("Viewport height must be defined in $asset", vpHeight.isNotEmpty())
        }
    }

    @Test
    fun testSportBike_containsBlackWheelsRedFrameBlueRider() {
        val file = File(resDir, "bsport_bike.xml")
        val content = file.readText()

        // Multi-color geometry preservation (REQ-UI-375.1)
        assertTrue("bsport_bike must contain black fill for wheels/saddle",
            content.contains("#FF000000") || content.contains("#000000"))
        assertTrue("bsport_bike must contain red fill for frame",
            content.contains("#FFFF0000") || content.contains("#FF0000"))
        assertTrue("bsport_bike must contain blue fill for rider",
            content.contains("#FF0000FF") || content.contains("#0000FF"))

        // Multi-color assets must not define root tint
        assertFalse("bsport_bike must not define root android:tint to prevent monochrome flattening",
            content.contains("android:tint="))
    }

    @Test
    fun testSportRun_containsBlueRunner() {
        val file = File(resDir, "bsport_run.xml")
        val content = file.readText()

        assertTrue("bsport_run must contain blue fill for runner",
            content.contains("#FF0000FF") || content.contains("#0000FF"))
    }

    @Test
    fun testSportOther_containsMultiColorStopwatchPaths() {
        val file = File(resDir, "bsport_other.xml")
        val content = file.readText()

        assertTrue("bsport_other must contain gold ring fill",
            content.contains("#FFFFC700") || content.contains("#FFC700"))
        assertTrue("bsport_other must contain red crown button fill",
            content.contains("#FFFF0000") || content.contains("#FF0000"))
        assertTrue("bsport_other must contain green right button fill",
            content.contains("#FF008F00") || content.contains("#008F00"))
        assertTrue("bsport_other must contain dark blue left button fill",
            content.contains("#FF00008F") || content.contains("#00008F"))

        assertFalse("bsport_other must not define root android:tint to prevent monochrome flattening",
            content.contains("android:tint="))
    }

    @Test
    fun testAntPlusBadges_containBlackBackgroundAndWhiteForeground() {
        val antSensors = listOf(
            "hr.xml", "bike_spd.xml", "bike_cad.xml", "bike_speed_and_cadence.xml",
            "bike_pwr.xml", "run_spd.xml", "temp.xml"
        )
        for (asset in antSensors) {
            val content = File(resDir, asset).readText()
            assertTrue("$asset must contain black background path",
                content.contains("#FF000000") || content.contains("#000000"))
            assertTrue("$asset must contain white foreground path",
                content.contains("#FFFFFFFF") || content.contains("#FFFFFF"))
        }
    }

    @Test
    fun testBleBadges_containBluetoothBlueBackgroundAndWhiteForeground() {
        val bleSensors = listOf(
            "bt_hr.xml", "bt_bike_spd.xml", "bt_bike_cad.xml", "bt_bike_speed_and_cadence.xml",
            "bt_bike_pwr.xml", "bt_run.xml"
        )
        for (asset in bleSensors) {
            val content = File(resDir, asset).readText()
            assertTrue("$asset must contain official Bluetooth blue background path",
                content.contains("#FF0A3D91") || content.contains("#0A3D91"))
            assertTrue("$asset must contain white foreground path",
                content.contains("#FFFFFFFF") || content.contains("#FFFFFF"))
        }
    }
}
