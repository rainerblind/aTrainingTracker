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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Contract test suite verifying official third-party brand vector fidelity (`REQ-UI-524` / `TST-UI-524`).
 * Verifies official Google Drive and Android Health Connect vector geometry, paths, and brand color palettes.
 */
class BrandIconContractTest {

    private fun resolveResDir(): File {
        val projectDir = File(System.getProperty("user.dir") ?: ".")
        return when {
            File(projectDir, "src/main/res").exists() -> File(projectDir, "src/main/res")
            File(projectDir, "app/src/main/res").exists() -> File(projectDir, "app/src/main/res")
            else -> File(projectDir, "../app/src/main/res")
        }
    }

    private fun parseXmlDocument(file: File): Element {
        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()
        val doc = dBuilder.parse(file)
        doc.documentElement.normalize()
        return doc.documentElement
    }

    /**
     * TST-UI-524.1: Verify official 6-segment Google Drive vector geometry, 24dp sizing,
     * square 87.3x87.3 viewport, and official brand color palette (REQ-UI-524.1).
     */
    @Test
    fun testGoogleDriveVectorGeometryAndColors() {
        val drawableDir = File(resolveResDir(), "drawable")
        val xmlFile = File(drawableDir, "ic_google_drive.xml")
        assertTrue("ic_google_drive.xml must exist in res/drawable/", xmlFile.exists())

        val root = parseXmlDocument(xmlFile)
        assertEquals("Root element must be <vector>", "vector", root.nodeName)
        assertEquals("24dp", root.getAttribute("android:width"))
        assertEquals("24dp", root.getAttribute("android:height"))
        assertEquals("87.3", root.getAttribute("android:viewportWidth"))
        assertEquals("87.3", root.getAttribute("android:viewportHeight"))

        val pathNodes = root.getElementsByTagName("path")
        assertEquals("Google Drive icon must contain exactly 6 paths", 6, pathNodes.length)

        val fillColors = mutableListOf<String>()
        for (i in 0 until pathNodes.length) {
            val item = pathNodes.item(i) as Element
            fillColors.add(item.getAttribute("android:fillColor").uppercase())
        }

        val expectedColors = setOf(
            "#0066DA",
            "#00AC47",
            "#EA4335",
            "#00832D",
            "#2684FC",
            "#FFBA00"
        )
        assertEquals(
            "Google Drive paths must match official 6-color palette",
            expectedColors,
            fillColors.toSet()
        )
    }

    /**
     * TST-UI-524.2: Verify official Android Health Connect interlocking dual-loop geometry,
     * Dark Teal (#003144) and Google Blue (#4285F4) palettes, and absence of legacy snowman nodes (REQ-UI-524.2).
     */
    @Test
    fun testHealthConnectVectorGeometryAndColors() {
        val drawableDir = File(resolveResDir(), "drawable")
        val xmlFile = File(drawableDir, "ic_health_connect.xml")
        assertTrue("ic_health_connect.xml must exist in res/drawable/", xmlFile.exists())

        val root = parseXmlDocument(xmlFile)
        assertEquals("Root element must be <vector>", "vector", root.nodeName)
        assertEquals("24dp", root.getAttribute("android:width"))
        assertEquals("24dp", root.getAttribute("android:height"))
        assertEquals("88", root.getAttribute("android:viewportWidth"))
        assertEquals("88", root.getAttribute("android:viewportHeight"))

        val pathNodes = root.getElementsByTagName("path")
        assertTrue("Health Connect icon must define >= 3 paths", pathNodes.length >= 3)

        val fillColors = mutableListOf<String>()
        var hasEvenOdd = false
        for (i in 0 until pathNodes.length) {
            val item = pathNodes.item(i) as Element
            fillColors.add(item.getAttribute("android:fillColor").uppercase())
            if (item.getAttribute("android:fillType") == "evenOdd") {
                hasEvenOdd = true
            }
        }

        assertTrue("Interlocking loops must use fillType='evenOdd'", hasEvenOdd)
        assertTrue("Must contain official Dark Teal #003144", fillColors.contains("#003144"))
        assertTrue("Must contain official Google Blue #4285F4", fillColors.contains("#4285F4"))

        // Legacy snowman nodes (#FBBC05 yellow circle) must be eliminated
        assertFalse("Must NOT contain legacy yellow circle #FBBC05", fillColors.contains("#FBBC05"))
    }
}
