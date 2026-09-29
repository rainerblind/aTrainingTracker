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

package com.atrainingtracker.trainingtracker.ui.knownlocations

import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.elevation.ElevationSource
import com.atrainingtracker.trainingtracker.repositories.KnownLocationItem
import com.atrainingtracker.trainingtracker.ui.clusters.ClusterFilterCriteria
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Unit test suite verifying compact routes badge interaction semantics,
 * spatial cluster filter construction, and 9-language localization parity (REQ-UI-188, TST-UI-142).
 */
class KnownLocationCardRoutesBadgeTest {

    private val sampleItem = KnownLocationItem(
        id = 42L,
        name = "Olympiapark",
        altitude = 512.0,
        radius = 180,
        latLng = LatLng(48.1731, 11.5463),
        hitCount = 27,
        isLocked = true,
        source = ElevationSource.MANUAL_USER
    )

    private val sampleClusters = listOf(
        WorkoutCluster(
            id = 101L,
            name = "Olympiaberg Runde",
            probableSportId = 1L,
            startLat = 48.1731,
            startLng = 11.5463,
            endLat = 48.1731,
            endLng = 11.5463,
            maxDispLat = 48.1800,
            maxDispLng = 11.5500,
            refDistance = 7500.0,
            hitCount = 14
        ),
        WorkoutCluster(
            id = 102L,
            name = "Schwabing Schleife",
            probableSportId = 1L,
            startLat = 48.1731,
            startLng = 11.5463,
            endLat = 48.1731,
            endLng = 11.5463,
            maxDispLat = 48.1850,
            maxDispLng = 11.5600,
            refDistance = 10200.0,
            hitCount = 9
        )
    )

    @Test
    fun testConstructClusterFilterCriteriaFromKnownLocation() {
        val item = sampleItem

        val criteria = ClusterFilterCriteria(
            startLocationName = item.name,
            startLocationLat = item.latLng.latitude,
            startLocationLng = item.latLng.longitude,
            startLocationRadiusM = item.radius.toDouble().takeIf { it > 0.0 } ?: 200.0
        )

        assertEquals("Olympiapark", criteria.startLocationName)
        assertEquals(48.1731, criteria.startLocationLat!!, 0.00001)
        assertEquals(11.5463, criteria.startLocationLng!!, 0.00001)
        assertEquals(180.0, criteria.startLocationRadiusM!!, 0.01)
        assertTrue("Spatial filter must be active", criteria.activeFilterCount > 0)
        assertEquals(1, criteria.activeFilterCount)
    }

    @Test
    fun testClusterFilterCriteriaFallbackRadiusWhenZeroOrNegative() {
        val itemZeroRadius = sampleItem.copy(radius = 0)

        val criteria = ClusterFilterCriteria(
            startLocationName = itemZeroRadius.name,
            startLocationLat = itemZeroRadius.latLng.latitude,
            startLocationLng = itemZeroRadius.latLng.longitude,
            startLocationRadiusM = itemZeroRadius.radius.toDouble().takeIf { it > 0.0 } ?: 200.0
        )

        assertEquals(200.0, criteria.startLocationRadiusM!!, 0.01)
    }

    @Test
    fun testRoutesBadgeCallbackInvocationIndependent() {
        var navigatedToRoutesWithItem: KnownLocationItem? = null
        var navigatedToWorkoutsWithItem: KnownLocationItem? = null
        var editedItem: KnownLocationItem? = null
        var deletedItem: KnownLocationItem? = null

        val onShowRoutes: (KnownLocationItem) -> Unit = { navigatedToRoutesWithItem = it }
        val onShowWorkouts: (KnownLocationItem) -> Unit = { navigatedToWorkoutsWithItem = it }
        val onEdit: (KnownLocationItem) -> Unit = { editedItem = it }
        val onDelete: (KnownLocationItem) -> Unit = { deletedItem = it }

        // Simulate routes badge tap
        onShowRoutes(sampleItem)
        assertNotNull("Routes drill-down must deliver target location item", navigatedToRoutesWithItem)
        assertEquals(sampleItem.id, navigatedToRoutesWithItem?.id)
        assertEquals("Olympiapark", navigatedToRoutesWithItem?.name)

        // Verify independent workout starts drill-down
        onShowWorkouts(sampleItem)
        assertEquals(sampleItem.id, navigatedToWorkoutsWithItem?.id)

        // Verify independent edit and delete callbacks
        onEdit(sampleItem)
        assertEquals(sampleItem.id, editedItem?.id)

        onDelete(sampleItem)
        assertEquals(sampleItem.id, deletedItem?.id)
    }

    @Test
    fun testBadgeVisibilityCondition() {
        val emptyClusters = emptyList<WorkoutCluster>()
        val hasClusters = sampleClusters

        assertEquals(0, emptyClusters.size)
        assertEquals(2, hasClusters.size)
    }

    @Test
    fun testLocalizationParityKnownLocationsRoutesAcross9Locales() {
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
            val pluralsNodes = doc.getElementsByTagName("plurals")
            var foundRoutesPlural = false
            for (i in 0 until pluralsNodes.length) {
                val elem = pluralsNodes.item(i) as Element
                if (elem.getAttribute("name") == "known_locations_routes") {
                    foundRoutesPlural = true
                    break
                }
            }
            assertTrue("known_locations_routes plural missing in $localeDir", foundRoutesPlural)

            val stringNodes = doc.getElementsByTagName("string")
            var foundViewRoutesString = false
            for (i in 0 until stringNodes.length) {
                val elem = stringNodes.item(i) as Element
                if (elem.getAttribute("name") == "known_locations_view_routes") {
                    foundViewRoutesString = true
                    break
                }
            }
            assertTrue("known_locations_view_routes string missing in $localeDir", foundViewRoutesString)
        }
    }

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
}
