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
 * Contract test suite verifying `REQ-UI-375` (`TST-UI-338`):
 * Mathematical VectorDrawable Migration from Xfig/SVG/XCF Masters & Legacy Raster Purge.
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
     * TST-UI-338.1: Verify all 16 vector XML files exist in `res/drawable/`, parse as valid `<vector>`
     * DOM trees, contain curve primitives (`A`, `C`, or `Q`), and have <= 45 `L` commands per `<path>`
     * (enforcing Rule 37 prohibition on naive polygon auto-tracing).
     */
    @Test
    fun testAll16MigratedVectorXmlsAreWellFormedAndUseTrueGeometricPrimitives() {
        val drawableDir = File(resolveResDir(), "drawable")
        assertTrue("Root drawable directory must exist", drawableDir.exists() && drawableDir.isDirectory)

        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()
        val lineCmdRegex = Regex("""\bL\b""")
        val curveCmdRegex = Regex("""[ACQacq]""")

        for (name in migratedVectorNames) {
            val xmlFile = File(drawableDir, "$name.xml")
            assertTrue("Vector XML file $name.xml must exist in res/drawable/", xmlFile.exists())

            val doc = dBuilder.parse(xmlFile)
            doc.documentElement.normalize()
            assertEquals("Root element of $name.xml must be <vector>", "vector", doc.documentElement.nodeName)

            val widthAttr = doc.documentElement.getAttribute("android:width")
            val heightAttr = doc.documentElement.getAttribute("android:height")
            val vpWidth = doc.documentElement.getAttribute("android:viewportWidth").toFloatOrNull() ?: 0f
            val vpHeight = doc.documentElement.getAttribute("android:viewportHeight").toFloatOrNull() ?: 0f

            assertTrue("$name.xml must define android:width in dp", widthAttr.endsWith("dp"))
            assertTrue("$name.xml must define android:height in dp", heightAttr.endsWith("dp"))
            assertTrue("$name.xml must have positive viewportWidth", vpWidth > 0f)
            assertTrue("$name.xml must have positive viewportHeight", vpHeight > 0f)

            val pathNodes = doc.getElementsByTagName("path")
            assertTrue("$name.xml must contain at least one <path> element", pathNodes.length >= 1)

            var hasCurveCommand = false
            for (i in 0 until pathNodes.length) {
                val pathElem = pathNodes.item(i)
                val pathData = pathElem.attributes?.getNamedItem("android:pathData")?.nodeValue.orEmpty()
                assertTrue("$name.xml path[$i] must have non-empty android:pathData", pathData.isNotBlank())

                val lCount = lineCmdRegex.findAll(pathData).count()
                assertTrue(
                    "$name.xml path[$i] has $lCount 'L' commands (max allowed is 45 per Rule 37)",
                    lCount <= 45
                )
                if (curveCmdRegex.containsMatchIn(pathData)) {
                    hasCurveCommand = true
                }
            }
            assertTrue("$name.xml must use analytical curve commands (A, C, or Q) per Rule 37", hasCurveCommand)
        }
    }

    /**
     * TST-UI-338.2: Verify multi-color palette and protocol badge differentiation from Xfig/XCF masters.
     */
    @Test
    fun testMultiColorAndProtocolBadgeVisualFidelityContracts() {
        val drawableDir = File(resolveResDir(), "drawable")

        val bikeXml = File(drawableDir, "bsport_bike.xml").readText().uppercase()
        assertTrue("bsport_bike.xml must preserve black wheels/saddle (#000000)", bikeXml.contains("#000000"))
        assertTrue("bsport_bike.xml must preserve red frame (#FF0000)", bikeXml.contains("#FF0000"))
        assertTrue("bsport_bike.xml must preserve blue rider (#0000FF)", bikeXml.contains("#0000FF"))

        val runXml = File(drawableDir, "bsport_run.xml").readText().uppercase()
        assertTrue("bsport_run.xml must preserve blue runner (#0000FF)", runXml.contains("#0000FF"))

        val otherXml = File(drawableDir, "bsport_other.xml").readText().uppercase()
        assertTrue("bsport_other.xml must preserve light gray stopwatch dial (#F0F0F0)", otherXml.contains("#F0F0F0"))
        assertTrue("bsport_other.xml must preserve gold stopwatch ring (#FFC700)", otherXml.contains("#FFC700"))
        assertTrue("bsport_other.xml must preserve blue left button cap (#000090)", otherXml.contains("#000090"))
        assertTrue("bsport_other.xml must preserve red center button cap (#FF0000)", otherXml.contains("#FF0000"))
        assertTrue("bsport_other.xml must preserve green right button cap (#009000)", otherXml.contains("#009000"))

        val antBadges = listOf("bike_cad", "bike_pwr", "bike_spd", "bike_speed_and_cadence", "hr", "run_spd", "temp")
        for (badge in antBadges) {
            val xml = File(drawableDir, "$badge.xml").readText().uppercase()
            assertTrue("$badge.xml must have black badge background (#000000)", xml.contains("#000000"))
            assertTrue("$badge.xml must have white foreground glyph (#FFFFFF)", xml.contains("#FFFFFF"))
            assertTrue("$badge.xml must use evenOdd fillType for counter-holes", xml.contains("EVENODD"))
        }

        val bleBadges = listOf("bt_bike_cad", "bt_bike_pwr", "bt_bike_spd", "bt_bike_speed_and_cadence", "bt_hr", "bt_run")
        for (badge in bleBadges) {
            val xml = File(drawableDir, "$badge.xml").readText().uppercase()
            assertTrue("$badge.xml must have Bluetooth navy badge background (#07377F)", xml.contains("#07377F"))
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
