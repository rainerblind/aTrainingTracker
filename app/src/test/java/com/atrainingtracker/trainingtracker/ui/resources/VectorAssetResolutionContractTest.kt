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

package com.atrainingtracker.trainingtracker.ui.resources

import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.helpers.UIHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Contract and verification test suite for ATT-2859 (`REQ-UI-375` / `TST-UI-338`):
 * Ensures all 16 sport and sensor icons are migrated to high-precision analytical XML VectorDrawables
 * constructed from true geometric primitives (Rule 37), preserving exact multi-color palettes and
 * purging all 106 superseded raster `.png` density variants.
 */
class VectorAssetResolutionContractTest {

    private val migratedVectorNames = listOf(
        "bsport_bike",
        "bsport_run",
        "bsport_other",
        "bike_cad",
        "bike_pwr",
        "bike_spd",
        "bike_speed_and_cadence",
        "hr",
        "run_spd",
        "temp",
        "bt_bike_cad",
        "bt_bike_pwr",
        "bt_bike_spd",
        "bt_bike_speed_and_cadence",
        "bt_hr",
        "bt_run"
    )

    private val purgedOnlyRasterNames = listOf(
        "bsport_bike_gray",
        "bsport_run_gray",
        "bsport_other_gray",
        "btle_hr"
    )

    private fun resolveResDir(): File {
        val projectDir = File(System.getProperty("user.dir") ?: ".")
        return when {
            File(projectDir, "src/main/res").exists() -> File(projectDir, "src/main/res")
            File(projectDir, "app/src/main/res").exists() -> File(projectDir, "app/src/main/res")
            else -> File(projectDir, "../app/src/main/res")
        }
    }

    /**
     * TST-UI-338.1: Verify all 16 migrated XML VectorDrawables exist in `res/drawable/`, parse as valid
     * `<vector>` documents with positive viewport/intrinsic dimensions, and contain curved analytical
     * SVG commands (`A` elliptical arcs or `C` cubic Beziers) per Rule 37.
     */
    @Test
    fun testAll16MigratedVectorsAreValidAndUseGeometricCurvedPrimitives() {
        val resDir = resolveResDir()
        val drawableDir = File(resDir, "drawable")
        assertTrue("drawable directory must exist", drawableDir.exists() && drawableDir.isDirectory)

        val dbf = DocumentBuilderFactory.newInstance()
        val curvedPrimitiveRegex = Regex("[ACac]")

        for (name in migratedVectorNames) {
            val xmlFile = File(drawableDir, "$name.xml")
            assertTrue("Vector XML $name.xml must exist in res/drawable/", xmlFile.exists())

            val doc = dbf.newDocumentBuilder().parse(xmlFile)
            val root = doc.documentElement
            assertEquals("Root element of $name.xml must be <vector>", "vector", root.tagName)

            val width = root.getAttribute("android:width")
            val height = root.getAttribute("android:height")
            val viewportWidth = root.getAttribute("android:viewportWidth").toFloatOrNull() ?: 0f
            val viewportHeight = root.getAttribute("android:viewportHeight").toFloatOrNull() ?: 0f

            assertTrue("$name.xml must specify android:width in dp", width.endsWith("dp"))
            assertTrue("$name.xml must specify android:height in dp", height.endsWith("dp"))
            assertTrue("$name.xml must have positive viewportWidth", viewportWidth > 0f)
            assertTrue("$name.xml must have positive viewportHeight", viewportHeight > 0f)

            val paths = root.getElementsByTagName("path")
            assertTrue("$name.xml must contain at least one <path>", paths.length > 0)

            var hasCurvedPrimitives = false
            for (i in 0 until paths.length) {
                val pathEl = paths.item(i)
                val pathData = pathEl.attributes?.getNamedItem("android:pathData")?.nodeValue.orEmpty()
                assertTrue("$name.xml path #$i must have non-empty android:pathData", pathData.isNotBlank())
                if (curvedPrimitiveRegex.containsMatchIn(pathData)) {
                    hasCurvedPrimitives = true
                }
            }
            assertTrue(
                "Rule 37 violation: $name.xml must contain curved geometric primitives (A arcs or C Beziers), not pure polygon tracing",
                hasCurvedPrimitives
            )
        }
    }

    /**
     * TST-UI-338.2: Verify multi-color palette fidelity across sport and protocol badges.
     */
    @Test
    fun testMultiColorAndBrandPaletteFidelity() {
        val drawableDir = File(resolveResDir(), "drawable")

        val bikeXml = File(drawableDir, "bsport_bike.xml").readText().uppercase()
        assertTrue("bsport_bike.xml must contain black wheel layer (#000000)", bikeXml.contains("#000000"))
        assertTrue("bsport_bike.xml must contain red frame layer (#FF0000)", bikeXml.contains("#FF0000"))
        assertTrue("bsport_bike.xml must contain blue rider layer (#0000FF)", bikeXml.contains("#0000FF"))

        val runXml = File(drawableDir, "bsport_run.xml").readText().uppercase()
        assertTrue("bsport_run.xml must contain blue runner (#0000FF)", runXml.contains("#0000FF"))

        val otherXml = File(drawableDir, "bsport_other.xml").readText().uppercase()
        assertTrue("bsport_other.xml must contain gold bezel (#FFC700)", otherXml.contains("#FFC700"))
        assertTrue("bsport_other.xml must contain red top crown (#FF0000)", otherXml.contains("#FF0000"))
        assertTrue("bsport_other.xml must contain dark blue left button (#00008F)", otherXml.contains("#00008F"))
        assertTrue("bsport_other.xml must contain green right button (#008F00)", otherXml.contains("#008F00"))

        val antBadges = listOf("bike_cad", "bike_pwr", "bike_spd", "bike_speed_and_cadence", "hr", "run_spd", "temp")
        for (badge in antBadges) {
            val xml = File(drawableDir, "$badge.xml").readText().uppercase()
            assertTrue("$badge.xml must have black badge background (#000000)", xml.contains("#000000"))
            assertTrue("$badge.xml must have white foreground glyph (#FFFFFF)", xml.contains("#FFFFFF"))
        }

        val bleBadges = listOf("bt_bike_cad", "bt_bike_pwr", "bt_bike_spd", "bt_bike_speed_and_cadence", "bt_hr", "bt_run")
        for (badge in bleBadges) {
            val xml = File(drawableDir, "$badge.xml").readText().uppercase()
            assertTrue("$badge.xml must have Bluetooth navy badge background (#0A3D91)", xml.contains("#0A3D91"))
            assertTrue("$badge.xml must have white foreground glyph (#FFFFFF)", xml.contains("#FFFFFF"))
        }
    }

    /**
     * TST-UI-338.3: Verify zero remaining `.png` copies for all 16 migrated icons and 4 obsolete raster families
     * across all `res/drawable*` density folders.
     */
    @Test
    fun testAllSupersededAndDeadRasterPngsArePurgedAcrossAllDensities() {
        val resDir = resolveResDir()
        val drawableFolders = resDir.listFiles { file ->
            file.isDirectory && file.name.startsWith("drawable")
        }.orEmpty()

        assertTrue("Must discover drawable* directories", drawableFolders.isNotEmpty())

        val allTargetNames = migratedVectorNames + purgedOnlyRasterNames
        for (folder in drawableFolders) {
            for (name in allTargetNames) {
                val pngFile = File(folder, "$name.png")
                assertFalse(
                    "Superseded raster file ${folder.name}/$name.png must be deleted",
                    pngFile.exists()
                )
            }
        }
    }

    /**
     * TST-UI-338.4: Verify `BSportType` and `UIHelper` mappings resolve to valid non-zero R.drawable resource IDs.
     */
    @Test
    @Suppress("DEPRECATION")
    fun testSportAndSensorDrawableMappingsResolveValidResourceIds() {
        for (sport in BSportType.entries) {
            val resId = sport.iconResId
            assertNotEquals("Sport icon for $sport must be non-zero", 0, resId)
        }
        assertEquals(R.drawable.bsport_run, BSportType.RUN.iconResId)
        assertEquals(R.drawable.bsport_bike, BSportType.BIKE.iconResId)
        assertEquals(R.drawable.bsport_other, BSportType.UNKNOWN.iconResId)
        assertEquals(R.drawable.bsport_other, BSportType.CONFLICT.iconResId)

        assertEquals(R.drawable.hr, UIHelper.getIconId(DeviceType.HRM, Protocol.ANT_PLUS))
        assertEquals(R.drawable.bike_spd, UIHelper.getIconId(DeviceType.BIKE_SPEED, Protocol.ANT_PLUS))
        assertEquals(R.drawable.bike_cad, UIHelper.getIconId(DeviceType.BIKE_CADENCE, Protocol.ANT_PLUS))
        assertEquals(R.drawable.bike_speed_and_cadence, UIHelper.getIconId(DeviceType.BIKE_SPEED_AND_CADENCE, Protocol.ANT_PLUS))
        assertEquals(R.drawable.bike_pwr, UIHelper.getIconId(DeviceType.BIKE_POWER, Protocol.ANT_PLUS))
        assertEquals(R.drawable.run_spd, UIHelper.getIconId(DeviceType.RUN_SPEED, Protocol.ANT_PLUS))
        assertEquals(R.drawable.temp, UIHelper.getIconId(DeviceType.ENVIRONMENT, Protocol.ANT_PLUS))

        assertEquals(R.drawable.bt_hr, UIHelper.getIconId(DeviceType.HRM, Protocol.BLUETOOTH_LE))
        assertEquals(R.drawable.bt_bike_spd, UIHelper.getIconId(DeviceType.BIKE_SPEED, Protocol.BLUETOOTH_LE))
        assertEquals(R.drawable.bt_bike_cad, UIHelper.getIconId(DeviceType.BIKE_CADENCE, Protocol.BLUETOOTH_LE))
        assertEquals(R.drawable.bt_bike_speed_and_cadence, UIHelper.getIconId(DeviceType.BIKE_SPEED_AND_CADENCE, Protocol.BLUETOOTH_LE))
        assertEquals(R.drawable.bt_bike_pwr, UIHelper.getIconId(DeviceType.BIKE_POWER, Protocol.BLUETOOTH_LE))
        assertEquals(R.drawable.bt_run, UIHelper.getIconId(DeviceType.RUN_SPEED, Protocol.BLUETOOTH_LE))
    }
}
