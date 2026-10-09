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

package com.atrainingtracker.trainingtracker.ui.climbs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.lang.reflect.Modifier
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Unit and contract tests verifying [LiveClimbSheet] composable structure, contracts,
 * visual tokens, and alignment with the Live Segment design system (REQ-MAP-027, REQ-UI-328, TST-UI-288).
 */
class LiveClimbSheetLayoutTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val liveClimbSheetFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/climbs/LiveClimbSheet.kt")
    }

    @Test
    fun testLiveClimbSheet_composableExistsAndIsPublic() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.climbs.LiveClimbSheetKt")
        assertNotNull("LiveClimbSheetKt class must exist", clazz)

        val methods = clazz.declaredMethods
        val sheetMethod = methods.find { it.name.startsWith("LiveClimbSheet") && Modifier.isPublic(it.modifiers) }
        assertNotNull("LiveClimbSheet composable function must exist and be public", sheetMethod)
        assertTrue(Modifier.isPublic(sheetMethod!!.modifiers))
    }

    @Test
    fun testLiveClimbSheet_structuralDesignAndTelemetryContracts() {
        assertTrue("LiveClimbSheet.kt must exist", liveClimbSheetFile.exists())
        val content = liveClimbSheetFile.readText()

        assertTrue(
            "LiveClimbSheet must use unified MaterialTheme surface background",
            content.contains("MaterialTheme.colorScheme.surface")
        )
        assertTrue(
            "LiveClimbSheet must include ClimbProfileCanvas for colored grade elevation profile",
            content.contains("ClimbProfileCanvas(")
        )
        assertTrue(
            "LiveClimbSheet must render ClimbCategoryChip badge",
            content.contains("ClimbCategoryChip(")
        )
        assertTrue(
            "LiveClimbSheet must render ClimbStatusBadge",
            content.contains("ClimbStatusBadge(")
        )
        assertTrue(
            "LiveClimbSheet must render HUD metrics for remaining distance, elevation, and grade",
            content.contains("climb_remaining_dist") &&
            content.contains("climb_remaining_elevation") &&
            content.contains("climb_grade")
        )
    }

    @Test
    fun testLiveClimbSheet_harmonizedHeaderTokensAndTypography() {
        val content = liveClimbSheetFile.readText()

        assertTrue(
            "LiveClimbSheet must render 32dp Terrain icon in top header row",
            content.contains("Icons.Default.Terrain") && content.contains("32.dp")
        )
        assertTrue(
            "LiveClimbSheet must render climb title in MaterialTheme.typography.titleLarge with bold weight",
            content.contains("MaterialTheme.typography.titleLarge") && content.contains("FontWeight.Bold")
        )
        assertTrue(
            "LiveClimbSheet must place route climb counter in subtitle row with labelLarge",
            content.contains("climb_route_counter") && content.contains("MaterialTheme.typography.labelLarge")
        )
    }

    @Test
    fun testLiveClimbSheet_asymmetricTelemetryHudTokens() {
        val content = liveClimbSheetFile.readText()

        assertTrue(
            "LiveClimbSheet must render instantaneous grade in headlineMedium monospace font",
            content.contains("MaterialTheme.typography.headlineMedium") &&
                content.contains("FontFamily.Monospace")
        )
        assertTrue(
            "LiveClimbSheet must render remaining distance in titleLarge bold",
            content.contains("MaterialTheme.typography.titleLarge")
        )
        assertTrue(
            "LiveClimbSheet must render remaining elevation in bodyLarge bold",
            content.contains("MaterialTheme.typography.bodyLarge")
        )
    }

    @Test
    fun testLiveClimbSheet_localizationParityAcrossAll9Locales() {
        val locales = listOf("", "de", "es", "fr", "it", "ja", "nl", "pl", "pt")
        val expectedKeys = listOf(
            "climb_title",
            "climb_approaching",
            "climb_on_climb",
            "climb_summit",
            "climb_remaining_dist",
            "climb_remaining_elevation",
            "climb_grade",
            "climb_route_counter"
        )

        for (locale in locales) {
            val dirName = if (locale.isEmpty()) "values" else "values-$locale"
            val file = File(projectRoot, "app/src/main/res/$dirName/strings.xml")
            assertTrue("strings.xml must exist for locale $dirName", file.exists())

            val stringMap = parseStringsFile(file)
            for (key in expectedKeys) {
                assertTrue(
                    "Key '$key' must exist in $dirName/strings.xml",
                    stringMap.containsKey(key)
                )
                assertFalse(
                    "Value for '$key' in $dirName/strings.xml must not be blank",
                    stringMap[key].isNullOrBlank()
                )
            }
        }
    }

    private fun parseStringsFile(file: File): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(file)
        val stringNodes = doc.getElementsByTagName("string")

        for (i in 0 until stringNodes.length) {
            val element = stringNodes.item(i) as Element
            val name = element.getAttribute("name")
            val text = element.textContent
            map[name] = text
        }
        return map
    }
}
