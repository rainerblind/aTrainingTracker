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
import com.atrainingtracker.banalservice.ActivityType
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
        "bsport_bike_power",
        "bsport_run",
        "bsport_other",
        "bike_cad",
        "bike_pwr",
        "bike_spd",
        "bike_speed_and_cadence",
        "hr",
        "run_spd",
        "temp",
        "bike_radar",
        "bt_bike_cad",
        "bt_bike_pwr",
        "bt_bike_spd",
        "bt_bike_speed_and_cadence",
        "bt_hr",
        "bt_run",
        "bt_bike_radar"
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
     * TST-UI-338.2 & TST-UI-369.1: Verify multi-color palette, transparent stopwatch inner dial (REQ-UI-406),
     * and protocol badge differentiation from Xfig/XCF masters.
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

        val otherXmlFile = File(drawableDir, "bsport_other.xml")
        val otherXml = otherXmlFile.readText().uppercase()
        assertFalse("bsport_other.xml must not use opaque #F0F0F0 inner dial fill (REQ-UI-406)", otherXml.contains("#F0F0F0"))
        assertTrue("bsport_other.xml must preserve gold stopwatch ring (#FFC700)", otherXml.contains("#FFC700"))
        assertTrue("bsport_other.xml must preserve blue left button cap (#000090)", otherXml.contains("#000090"))
        assertTrue("bsport_other.xml must preserve red center button cap (#FF0000)", otherXml.contains("#FF0000"))
        assertTrue("bsport_other.xml must preserve green right button cap (#009000)", otherXml.contains("#009000"))

        val otherDoc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(otherXmlFile)
        val otherPaths = otherDoc.getElementsByTagName("path")
        assertTrue("bsport_other.xml must contain <path> elements", otherPaths.length >= 1)
        for (i in 0 until otherPaths.length) {
            val fillAttr = otherPaths.item(i).attributes?.getNamedItem("android:fillColor")?.nodeValue.orEmpty()
            assertEquals(
                "bsport_other.xml path[$i] must declare transparent fillColor (#00000000) per REQ-UI-406",
                "#00000000",
                fillAttr
            )
        }

        val antBadges = listOf("bike_cad", "bike_pwr", "bike_spd", "bike_speed_and_cadence", "hr", "run_spd", "temp", "bike_radar")
        for (badge in antBadges) {
            val xml = File(drawableDir, "$badge.xml").readText().uppercase()
            assertTrue("$badge.xml must have black badge background (#000000)", xml.contains("#000000"))
            assertTrue("$badge.xml must have white foreground glyph (#FFFFFF)", xml.contains("#FFFFFF"))
            assertTrue("$badge.xml must use evenOdd fillType for counter-holes", xml.contains("EVENODD"))
        }

        val bleBadges = listOf("bt_bike_cad", "bt_bike_pwr", "bt_bike_spd", "bt_bike_speed_and_cadence", "bt_hr", "bt_run", "bt_bike_radar")
        for (badge in bleBadges) {
            val xml = File(drawableDir, "$badge.xml").readText().uppercase()
            assertTrue("$badge.xml must have official Bluetooth badge background (#0A3D91)", xml.contains("#0A3D91"))
            assertTrue("$badge.xml must have white foreground glyph (#FFFFFF)", xml.contains("#FFFFFF"))
            assertTrue("$badge.xml must use evenOdd fillType for counter-holes (REQ-UI-442)", xml.contains("EVENODD"))
        }
    }

    /**
     * TST-UI-442.1: Verify all 6 BTLE sensor vector badges are full-bleed (0..144, r=22.0)
     * and strictly preserve the official Bluetooth badge color token (#0A3D91) without 10-unit inset margins.
     */
    @Test
    fun testBtleSensorBadgesAreFullBleedAndRetainNavyColor() {
        val drawableDir = File(resolveResDir(), "drawable")
        val bleBadges = listOf("bt_bike_cad", "bt_bike_pwr", "bt_bike_spd", "bt_bike_speed_and_cadence", "bt_hr", "bt_run", "bt_bike_radar")

        for (badge in bleBadges) {
            val xml = File(drawableDir, "$badge.xml").readText()
            assertTrue("$badge.xml must define full-bleed 0..144 badge path (REQ-UI-442.1)", xml.contains("144.0,22.0") && xml.contains("122.0,144.0"))
            assertTrue("$badge.xml must have official Bluetooth background (#0A3D91)", xml.contains("#0A3D91"))
            assertFalse("$badge.xml must purge legacy ICC-distorted #07377F", xml.contains("#07377F"))
            assertFalse("$badge.xml must eliminate legacy 10-unit inset margin (10.0..134.0)", xml.contains("10.0,23.5") || xml.contains("120.5,134.0"))
        }
    }

    /**
     * TST-UI-442.2: Verify all 6 BTLE sensor vector badges contain bold uppercase bottom abbreviation labels
     * with evenOdd counter-hole punch-outs and no legacy micro lowercase words.
     */
    @Test
    fun testBtleSensorBadgesContainBoldUppercaseLabelsWithEvenOdd() {
        val drawableDir = File(resolveResDir(), "drawable")
        val bleBadges = listOf("bt_bike_cad", "bt_bike_pwr", "bt_bike_spd", "bt_bike_speed_and_cadence", "bt_hr", "bt_run", "bt_bike_radar")

        for (badge in bleBadges) {
            val xml = File(drawableDir, "$badge.xml").readText()
            val contentWithoutComments = xml.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "").lowercase()
            assertTrue("$badge.xml must declare evenOdd fillType for text counters (REQ-UI-442.2)", xml.contains("evenOdd"))
            assertFalse("$badge.xml must purge lowercase 'power'", contentWithoutComments.contains("power"))
            assertFalse("$badge.xml must purge lowercase 'speed'", contentWithoutComments.contains("speed"))
            assertFalse("$badge.xml must purge lowercase 'cadence'", contentWithoutComments.contains("cadence"))
            assertFalse("$badge.xml must purge lowercase 's & c'", contentWithoutComments.contains("s & c"))
        }

        val pwrXml = File(drawableDir, "bt_bike_pwr.xml").readText()
        assertTrue("bt_bike_pwr must render uppercase PWR at y >= 113.25", pwrXml.contains("113.25") && pwrXml.contains("135.75"))

        val spdXml = File(drawableDir, "bt_bike_spd.xml").readText()
        assertTrue("bt_bike_spd must render uppercase SPD at y >= 113.25", spdXml.contains("113.25") && spdXml.contains("135.75"))

        val cadXml = File(drawableDir, "bt_bike_cad.xml").readText()
        assertTrue("bt_bike_cad must render uppercase CAD at y >= 113.25", cadXml.contains("113.25") && cadXml.contains("135.75"))

        val scXml = File(drawableDir, "bt_bike_speed_and_cadence.xml").readText()
        assertTrue("bt_bike_speed_and_cadence must render uppercase S&C at y >= 113.25", scXml.contains("113.25") && scXml.contains("135.75"))

        val hrXml = File(drawableDir, "bt_hr.xml").readText()
        assertTrue("bt_hr must render uppercase HR at y >= 113.25", hrXml.contains("113.25") && hrXml.contains("135.75"))

        val runXml = File(drawableDir, "bt_run.xml").readText()
        assertTrue("bt_run must render uppercase SPD at y >= 113.25", runXml.contains("113.25") && runXml.contains("135.75"))

        val radarXml = File(drawableDir, "bt_bike_radar.xml").readText()
        assertTrue("bt_bike_radar must render uppercase RDR at y >= 113.25", radarXml.contains("113.25") && radarXml.contains("135.75"))
    }

    /**
     * TST-UI-442.3: Verify bike BTLE drawables center the cyclist horizontally, contain canonical
     * top-left mini-icons (ic_power, ic_speed, ic_cadence), and all 6 drawables satisfy Rule 37
     * geometric constraints (<= 45 L commands, analytical curves).
     */
    @Test
    fun testBtleSensorBadgesContainTopRightMiniIconsAndRule37Prims() {
        val drawableDir = File(resolveResDir(), "drawable")
        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()
        val lineCmdRegex = Regex("""\bL\b""")
        val curveCmdRegex = Regex("""[ACQacq]""")

        val bikeBadges = listOf("bt_bike_pwr", "bt_bike_spd", "bt_bike_cad", "bt_bike_speed_and_cadence", "bt_bike_radar")
        for (badge in bikeBadges) {
            val xml = File(drawableDir, "$badge.xml").readText()
            assertTrue("$badge.xml must horizontally center cyclist with translateX=18.18", xml.contains("translateX=\"18.18\""))

            val doc = dBuilder.parse(File(drawableDir, "$badge.xml"))
            val pathNodes = doc.getElementsByTagName("path")
            assertTrue("$badge.xml must contain at least 20 paths", pathNodes.length >= 20)
        }

        // Verify canonical mini-icon path reuse (Rule 36) in top-left
        val pwrXml = File(drawableDir, "bt_bike_pwr.xml").readText()
        assertTrue("bt_bike_pwr must embed canonical ic_power path in top-left", pwrXml.contains("mini_icon_pwr") && pwrXml.contains("M280,880L440,580"))

        val spdXml = File(drawableDir, "bt_bike_spd.xml").readText()
        assertTrue("bt_bike_spd must embed canonical ic_speed path in top-left", spdXml.contains("mini_icon_spd") && spdXml.contains("M418,620Q442,644"))

        val cadXml = File(drawableDir, "bt_bike_cad.xml").readText()
        assertTrue("bt_bike_cad must embed canonical ic_cadence path in top-left", cadXml.contains("mini_icon_cad") && cadXml.contains("M480,560Q447,560"))

        val scXml = File(drawableDir, "bt_bike_speed_and_cadence.xml").readText()
        assertTrue("bt_bike_speed_and_cadence must embed canonical dual mini-icons in top-left", scXml.contains("mini_icon_spd") && scXml.contains("mini_icon_cad"))

        val radarXml = File(drawableDir, "bt_bike_radar.xml").readText()
        assertTrue("bt_bike_radar must embed canonical radar mini-icon in top-left", radarXml.contains("mini_icon_radar") && radarXml.contains("M 480,720"))

        val allBle = bikeBadges + listOf("bt_hr", "bt_run")
        for (badge in allBle) {
            val doc = dBuilder.parse(File(drawableDir, "$badge.xml"))
            val pathNodes = doc.getElementsByTagName("path")
            var hasCurve = false
            for (i in 0 until pathNodes.length) {
                val pathElem = pathNodes.item(i)
                val pathData = pathElem.attributes?.getNamedItem("android:pathData")?.nodeValue.orEmpty()
                val lCount = lineCmdRegex.findAll(pathData).count()
                assertTrue("$badge.xml path[$i] has $lCount 'L' commands (max 45 per Rule 37)", lCount <= 45)
                if (curveCmdRegex.containsMatchIn(pathData)) {
                    hasCurve = true
                }
            }
            assertTrue("$badge.xml must contain analytical curve commands per Rule 37", hasCurve)
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
        assertEquals(R.drawable.bike_radar, UIHelper.getIconId(DeviceType.BIKE_RADAR, Protocol.ANT_PLUS))
        assertEquals(R.drawable.temp, UIHelper.getIconId(DeviceType.ENVIRONMENT, Protocol.ANT_PLUS))

        assertEquals(R.drawable.bt_hr, UIHelper.getIconId(DeviceType.HRM, Protocol.BLUETOOTH_LE))
        assertEquals(R.drawable.bt_bike_spd, UIHelper.getIconId(DeviceType.BIKE_SPEED, Protocol.BLUETOOTH_LE))
        assertEquals(R.drawable.bt_bike_cad, UIHelper.getIconId(DeviceType.BIKE_CADENCE, Protocol.BLUETOOTH_LE))
        assertEquals(R.drawable.bt_bike_speed_and_cadence, UIHelper.getIconId(DeviceType.BIKE_SPEED_AND_CADENCE, Protocol.BLUETOOTH_LE))
        assertEquals(R.drawable.bt_bike_pwr, UIHelper.getIconId(DeviceType.BIKE_POWER, Protocol.BLUETOOTH_LE))
        assertEquals(R.drawable.bt_bike_radar, UIHelper.getIconId(DeviceType.BIKE_RADAR, Protocol.BLUETOOTH_LE))
        assertEquals(R.drawable.bt_run, UIHelper.getIconId(DeviceType.RUN_SPEED, Protocol.BLUETOOTH_LE))

        assertEquals(R.drawable.bike_radar, com.atrainingtracker.banalservice.ui.devices.devicedata.getIconId(DeviceType.BIKE_RADAR, Protocol.ANT_PLUS))
        assertEquals(R.drawable.bt_bike_radar, com.atrainingtracker.banalservice.ui.devices.devicedata.getIconId(DeviceType.BIKE_RADAR, Protocol.BLUETOOTH_LE))
    }

    /**
     * TST-UI-487.3: Verify distinct bike power vector asset and ActivityType.BIKE_POWER logo mapping.
     */
    @Test
    fun testActivityTypeBikePowerVectorAssetAndIconMapping() {
        val drawableDir = File(resolveResDir(), "drawable")
        val pwrBikeFile = File(drawableDir, "bsport_bike_power.xml")
        assertTrue("bsport_bike_power.xml must exist in res/drawable/", pwrBikeFile.exists())

        val content = pwrBikeFile.readText()
        assertTrue("bsport_bike_power.xml must contain power_bolt group", content.contains("power_bolt"))
        assertTrue("bsport_bike_power.xml must embed canonical lightning bolt path", content.contains("M 280,880 L 440,580"))

        assertEquals(
            "ActivityType.BIKE_POWER must bind R.drawable.bsport_bike_power",
            R.drawable.bsport_bike_power,
            ActivityType.BIKE_POWER.logoId
        )
        assertEquals(
            "ActivityType.BIKE_SPEED_AND_CADENCE must bind R.drawable.bsport_bike",
            R.drawable.bsport_bike,
            ActivityType.BIKE_SPEED_AND_CADENCE.logoId
        )
        assertNotEquals(
            "ActivityType.BIKE_POWER and BIKE_SPEED_AND_CADENCE must have distinct icons",
            ActivityType.BIKE_POWER.logoId,
            ActivityType.BIKE_SPEED_AND_CADENCE.logoId
        )
    }
}
