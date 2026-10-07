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

package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Verification unit test suite for [ResearchButton] and universal vector fallback (REQ-UI-286 / TST-UI-246).
 *
 * Validates:
 * 1. TST-UI-246.1: Compose modernization using [Icons.Default.Refresh], removing [painterResource] fragility.
 * 2. TST-UI-246.2: Universal vector drawable fallback `res/drawable/research_icon.xml` validity.
 * 3. TST-UI-246.3: 100% localization parity across all 9 application locales for `@string/research`.
 */
class ResearchButtonTest {

    private fun findProjectFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../$relativePath"),
            File("../app/$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Could not find file '$relativePath' in candidates: $candidates")
    }

    private fun findResDirectory(): File {
        val candidates = listOf(
            File("app/src/main/res"),
            File("src/main/res"),
            File("../app/src/main/res")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("Could not find res directory in candidates: $candidates")
    }

    private fun parseStringsFile(file: File): Map<String, String> {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(file)
        val stringNodes = doc.getElementsByTagName("string")
        val result = mutableMapOf<String, String>()
        for (i in 0 until stringNodes.length) {
            val node = stringNodes.item(i) as Element
            val name = node.getAttribute("name")
            val text = node.textContent
            result[name] = text
        }
        return result
    }

    /**
     * TST-UI-246.1: Verify ResearchButton uses Compose vector Icons.Default.Refresh
     * and does NOT use painterResource(R.drawable.research_icon), eliminating Resources$NotFoundException.
     */
    @Test
    fun testResearchButton_usesVectorRefreshAndNoPainterResource() {
        val file = findProjectFile("src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ResearchButton.kt")
        val content = file.readText()

        // 1. Must use Icons.Default.Refresh
        assertTrue(
            "ResearchButton must use Icons.Default.Refresh",
            content.contains("imageVector = Icons.Default.Refresh")
        )

        // 2. Must import Refresh icon
        assertTrue(
            "ResearchButton must import Icons.Default.Refresh",
            content.contains("import androidx.compose.material.icons.filled.Refresh")
        )

        // 3. Must NOT call painterResource(id = R.drawable.research_icon)
        assertFalse(
            "ResearchButton must not use painterResource to load research_icon",
            content.contains("painterResource")
        )

        // 4. Must supply accessibility contentDescription with @string/research
        assertTrue(
            "ResearchButton Icon must supply stringResource(id = R.string.research) as contentDescription",
            content.contains("contentDescription = stringResource(id = R.string.research)")
        )

        // 5. Must retain 48dp sizing
        assertTrue(
            "ResearchButton must retain 48.dp icon sizing",
            content.contains("modifier = Modifier.size(48.dp)")
        )
    }

    /**
     * TST-UI-246.2: Verify universal vector fallback drawable res/drawable/research_icon.xml exists and is valid.
     */
    @Test
    fun testResearchIconVectorDrawable_existsAndValid() {
        val resDir = findResDirectory()
        val drawableFile = File(resDir, "drawable/research_icon.xml")
        assertTrue("research_icon.xml must exist in res/drawable/", drawableFile.exists())

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(drawableFile)
        val root = doc.documentElement

        assertEquals("Root element of research_icon.xml must be <vector>", "vector", root.tagName)
        assertEquals("Width must be 24dp", "24dp", root.getAttribute("android:width"))
        assertEquals("Height must be 24dp", "24dp", root.getAttribute("android:height"))

        val paths = doc.getElementsByTagName("path")
        assertTrue("research_icon.xml must contain at least one <path>", paths.length > 0)
        val path = paths.item(0) as Element
        assertNotNull("Path must have android:pathData", path.getAttribute("android:pathData"))
        assertTrue("Path data must not be empty", path.getAttribute("android:pathData").isNotBlank())
    }

    /**
     * TST-UI-246.3: Verify 9-language localization parity for @string/research.
     */
    @Test
    fun testResearchString_parityAcrossAll9Locales() {
        val resDir = findResDirectory()
        val locales = listOf(
            "values",       // English (default)
            "values-de",    // German
            "values-es",    // Spanish
            "values-fr",    // French
            "values-it",    // Italian
            "values-ja",    // Japanese
            "values-nl",    // Dutch
            "values-pl",    // Polish
            "values-pt"     // Portuguese
        )

        for (dirName in locales) {
            val file = File(resDir, "$dirName/strings.xml")
            assertTrue("strings.xml must exist for locale $dirName", file.exists())
            val stringMap = parseStringsFile(file)
            assertTrue(
                "Locale $dirName must define key 'research'",
                stringMap.containsKey("research")
            )
            val value = stringMap["research"]
            assertFalse(
                "Value for 'research' in $dirName/strings.xml must not be blank",
                value.isNullOrBlank()
            )
        }
    }
}
