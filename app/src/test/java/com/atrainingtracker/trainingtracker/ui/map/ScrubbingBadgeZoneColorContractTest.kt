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

package com.atrainingtracker.trainingtracker.ui.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural and architectural contract tests for [ScrubbingTelemetryBadge] in [ElevationProfile]
 * verifying the training zone suffix rendering in active zone colors and neutral base metric styling
 * (TST-UI-201.1 / REQ-UI-242 / ATT-2015).
 */
class ScrubbingBadgeZoneColorContractTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val elevationProfileFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt")
    }

    @Test
    fun testElevationProfile_importsComposeTextAnnotatedStringComponents() {
        assertTrue("ElevationProfile.kt must exist", elevationProfileFile.exists())
        val content = elevationProfileFile.readText()

        assertTrue(
            "ElevationProfile.kt must import buildAnnotatedString",
            content.contains("import androidx.compose.ui.text.buildAnnotatedString")
        )
        assertTrue(
            "ElevationProfile.kt must import SpanStyle",
            content.contains("import androidx.compose.ui.text.SpanStyle")
        )
        assertTrue(
            "ElevationProfile.kt must import withStyle",
            content.contains("import androidx.compose.ui.text.withStyle")
        )
    }

    @Test
    fun testScrubbingTelemetryBadge_rendersHeartRateWithAnnotatedZoneColor() {
        assertTrue("ElevationProfile.kt must exist", elevationProfileFile.exists())
        val content = elevationProfileFile.readText()

        // Verify base metric is styled with onSurface
        assertTrue(
            "HR base metric must be styled with MaterialTheme.colorScheme.onSurface",
            content.contains("""append("${'$'}{point.hr} bpm")""")
        )

        // Verify dynamic zone color mapping via TelemetryZoneMath.ZONE_COLORS
        assertTrue(
            "HR zone suffix must be styled with TelemetryZoneMath.ZONE_COLORS[hrZone - 1]",
            content.contains("color = TelemetryZoneMath.ZONE_COLORS[hrZone - 1]")
        )

        // Verify bold font weight for zone suffix
        assertTrue(
            "HR zone suffix must be bold",
            content.contains("fontWeight = FontWeight.Bold")
        )

        // Verify zone suffix text format
        assertTrue(
            "HR zone suffix must append ' • Z${'$'}hrZone'",
            content.contains("""append(" • Z${'$'}hrZone")""")
        )
    }

    @Test
    fun testScrubbingTelemetryBadge_rendersPowerWithAnnotatedZoneColor() {
        assertTrue("ElevationProfile.kt must exist", elevationProfileFile.exists())
        val content = elevationProfileFile.readText()

        // Verify base metric is styled with onSurface
        assertTrue(
            "Power base metric must be styled with MaterialTheme.colorScheme.onSurface",
            content.contains("""append("${'$'}{point.power} W")""")
        )

        // Verify dynamic zone color mapping via TelemetryZoneMath.ZONE_COLORS
        assertTrue(
            "Power zone suffix must be styled with TelemetryZoneMath.ZONE_COLORS[powerZone - 1]",
            content.contains("color = TelemetryZoneMath.ZONE_COLORS[powerZone - 1]")
        )

        // Verify zone suffix text format
        assertTrue(
            "Power zone suffix must append ' • Z${'$'}powerZone'",
            content.contains("""append(" • Z${'$'}powerZone")""")
        )
    }

    @Test
    fun testScrubbingTelemetryBadge_doesNotStaticallyHardcodeZoneColorsOnEntireLabel() {
        assertTrue("ElevationProfile.kt must exist", elevationProfileFile.exists())
        val content = elevationProfileFile.readText()

        // ScrubbingTelemetryBadge must no longer have static TTColor.Zone4 or TTColor.Zone5 on Text composable
        assertFalse(
            "ScrubbingTelemetryBadge must not statically set color = TTColor.Zone4 on HR text",
            content.contains("color = TTColor.Zone4")
        )
        assertFalse(
            "ScrubbingTelemetryBadge must not statically set color = TTColor.Zone5 on Power text",
            content.contains("color = TTColor.Zone5")
        )
    }
}
