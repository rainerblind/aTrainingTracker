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

package com.atrainingtracker.trainingtracker.localization

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Localization verification test for Pick & Place sensor tile reordering strings (REQ-UI-200, TST-UI-154.5).
 * Validates 9-locale parity for move_tile_banner_instruction and move_tile_cancel.
 */
class SensorGridLocalizationTest {

    private val locales = listOf("", "-de", "-es", "-fr", "-it", "-ja", "-nl", "-pl", "-pt")

    private fun findResDir(): File {
        val candidates = listOf(
            File("app/src/main/res"),
            File("src/main/res"),
            File("../app/src/main/res")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("res directory not found in candidates: $candidates")
    }

    private fun parseStringResource(file: File, stringName: String): String? {
        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()
        val doc = dBuilder.parse(file)
        doc.documentElement.normalize()

        val stringNodes = doc.getElementsByTagName("string")
        for (i in 0 until stringNodes.length) {
            val item = stringNodes.item(i)
            if (item is Element && item.getAttribute("name") == stringName) {
                return item.textContent
            }
        }
        return null
    }

    @Test
    fun testMoveTileBannerInstructionParityAcrossAllLocales() {
        val resDir = findResDir()

        for (suffix in locales) {
            val dirName = if (suffix.isEmpty()) "values" else "values$suffix"
            val stringsFile = File(resDir, "$dirName/strings.xml")
            assertTrue("Expected strings.xml to exist in $dirName", stringsFile.exists())

            val instruction = parseStringResource(stringsFile, "move_tile_banner_instruction")
            assertNotNull("Missing move_tile_banner_instruction in $dirName", instruction)
            assertTrue(
                "move_tile_banner_instruction in $dirName must not be blank",
                instruction!!.isNotBlank()
            )
        }
    }

    @Test
    fun testMoveTileCancelParityAcrossAllLocales() {
        val resDir = findResDir()

        for (suffix in locales) {
            val dirName = if (suffix.isEmpty()) "values" else "values$suffix"
            val stringsFile = File(resDir, "$dirName/strings.xml")
            val cancel = parseStringResource(stringsFile, "move_tile_cancel")
            assertNotNull("Missing move_tile_cancel in $dirName", cancel)
            assertTrue(
                "move_tile_cancel in $dirName must not be blank",
                cancel!!.isNotBlank()
            )
        }
    }

    @Test
    fun testRouteActionSelectDescParityAcrossAllLocales() {
        val resDir = findResDir()

        for (suffix in locales) {
            val dirName = if (suffix.isEmpty()) "values" else "values$suffix"
            val stringsFile = File(resDir, "$dirName/strings.xml")
            assertTrue("Expected strings.xml to exist in $dirName", stringsFile.exists())

            val desc = parseStringResource(stringsFile, "route_action_select_desc")
            assertNotNull("Missing route_action_select_desc in $dirName", desc)
            assertTrue(
                "route_action_select_desc in $dirName must not be blank",
                desc!!.isNotBlank()
            )
        }
    }
}
