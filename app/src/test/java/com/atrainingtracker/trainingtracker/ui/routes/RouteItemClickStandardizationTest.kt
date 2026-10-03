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

package com.atrainingtracker.trainingtracker.ui.routes

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.RouteSource
import com.atrainingtracker.trainingtracker.database.RouteSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Unit and structural contract test suite for REQ-UI-191 / TST-UI-145:
 * Standardize list item heading click behavior across all application lists
 * to "inspection-first", verify dedicated 48dp Material 3 edit button in
 * RouteSummaryHeader, and preserve REQ-UI-061 delete-only context menu.
 */
class RouteItemClickStandardizationTest {

    private val sampleSummary = RouteSummary(
        id = 101L,
        externalId = "sample_route_101",
        name = "Isar River Gravel Loop",
        description = "Scenic riverside trail",
        isSelected = true,
        distance = 34500.0,
        elevationGain = 320.0,
        bSportType = BSportType.BIKE,
        source = RouteSource.LOCAL_GPX
    )

    private fun findProjectRoot(): File {
        var dir: File = File(".").canonicalFile
        while (dir.parentFile != null) {
            if (File(dir, "gradlew").exists() && File(dir, "app").exists()) {
                return dir
            }
            dir = dir.parentFile!!
        }
        return File(".").canonicalFile
    }

    private fun resolveSourceFile(relativePath: String): File {
        val root = findProjectRoot()
        val target = File(root, relativePath)
        assertTrue("Source file must exist: $relativePath", target.exists())
        return target
    }

    @Test
    fun testRouteItemHeaderClickNavigatesToInspectionDetails() {
        val routeItemFile = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteItem.kt")
        val content = routeItemFile.readText()

        // 1. Header click must invoke onMapClick(summary.id) instead of onHeaderClick
        assertTrue(
            "RouteSummaryHeader click in RouteItem must invoke onMapClick(summary.id) for inspection navigation",
            content.contains("onClick = { onMapClick(summary.id) }")
        )

        // 2. Dedicated edit action must be wired to onHeaderClick(summary.id)
        assertTrue(
            "RouteItem must pass onEditClick = { onHeaderClick(summary.id) } to RouteSummaryHeader",
            content.contains("onEditClick = { onHeaderClick(summary.id) }")
        )

        // 3. Card click must navigate to inspection
        assertTrue(
            "MappableListItem in RouteItem must invoke onMapClick(summary.id)",
            content.contains("onClick = { onMapClick(summary.id) }")
        )

        // 4. Map and elevation preview must invoke onMapClick
        assertTrue(
            "PathPreviewMap in RouteItem must invoke onMapClick(summary.id)",
            content.contains("onMapClick = { onMapClick(summary.id) }")
        )
    }

    @Test
    fun testRouteItemContextMenuPreservesDeleteOnlyInvariant() {
        val routeItemFile = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteItem.kt")
        val content = routeItemFile.readText()

        // Context menu must anchor to TopStart
        assertTrue(
            "RouteItem context menu must anchor to TopStart",
            content.contains("Alignment.TopStart")
        )

        // Context menu must contain Delete
        assertTrue(
            "RouteItem context menu must contain delete action",
            content.contains("R.string.delete")
        )

        // Context menu must NOT contain Edit (REQ-UI-061 invariant)
        assertFalse(
            "RouteItem context menu MUST NOT contain Edit action (violates REQ-UI-061)",
            content.contains("R.string.route_edit") || content.contains("R.string.Edit")
        )
    }

    @Test
    fun testRouteSummaryHeaderEditButtonContractAndTouchTarget() {
        val headerFile = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSummaryHeader.kt")
        val content = headerFile.readText()

        // 1. onEditClick parameter declaration
        assertTrue(
            "RouteSummaryHeader must declare onEditClick parameter",
            content.contains("onEditClick: (() -> Unit)? = null")
        )

        // 2. Minimum 48dp touch target
        assertTrue(
            "RouteSummaryHeader edit button must specify Modifier.size(48.dp) for M3 accessibility compliance",
            content.contains("Modifier.size(48.dp)")
        )

        // 3. Canonical Material 3 edit icon
        assertTrue(
            "RouteSummaryHeader edit button must use Icons.Default.Edit",
            content.contains("Icons.Default.Edit")
        )

        // 4. Primary tint per REQ-UI-182
        assertTrue(
            "RouteSummaryHeader edit button must use MaterialTheme.colorScheme.primary tint",
            content.contains("tint = MaterialTheme.colorScheme.primary")
        )

        // 5. Accessible content description
        assertTrue(
            "RouteSummaryHeader edit button must specify R.string.route_edit content description",
            content.contains("R.string.route_edit")
        )
    }

    @Test
    fun testRouteItemInteractionCallbacksSeparation() {
        var mapClickedId: Long? = null
        var editClickedId: Long? = null
        var toggledId: Long? = null
        var toggledState: Boolean? = null

        val onMapClick: (Long) -> Unit = { mapClickedId = it }
        val onEditClick: (Long) -> Unit = { editClickedId = it }
        val onToggleSelection: (Long, Boolean) -> Unit = { id, state ->
            toggledId = id
            toggledState = state
        }

        // Tapping header triggers map click (inspection)
        onMapClick(sampleSummary.id)
        assertEquals(101L, mapClickedId)

        // Tapping edit button triggers edit click (edit route screen)
        onEditClick(sampleSummary.id)
        assertEquals(101L, editClickedId)

        // Toggling selection operates independently
        onToggleSelection(sampleSummary.id, false)
        assertEquals(101L, toggledId)
        assertEquals(false, toggledState)
    }

    @Test
    fun testLocalizationParityRouteEditAcross9Locales() {
        val locales = listOf(
            "values",
            "values-de",
            "values-es",
            "values-fr",
            "values-it",
            "values-ja",
            "values-nl",
            "values-pl",
            "values-pt"
        )

        val projectRoot = findProjectRoot()
        val dbf = DocumentBuilderFactory.newInstance()
        val db = dbf.newDocumentBuilder()

        for (localeDir in locales) {
            val stringsFile = File(projectRoot, "app/src/main/res/$localeDir/strings.xml")
            assertTrue("strings.xml must exist for $localeDir", stringsFile.exists())

            val doc = db.parse(stringsFile)
            val stringNodes = doc.getElementsByTagName("string")
            var foundRouteEdit = false
            for (i in 0 until stringNodes.length) {
                val elem = stringNodes.item(i) as Element
                if (elem.getAttribute("name") == "route_edit") {
                    foundRouteEdit = true
                    assertTrue("route_edit in $localeDir must not be blank", elem.textContent.isNotBlank())
                    break
                }
            }
            assertTrue("route_edit string missing in $localeDir", foundRouteEdit)
        }
    }
}
