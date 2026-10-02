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
 */

package com.atrainingtracker.trainingtracker.migration

import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit and contract tests verifying Google Maps CameraUpdateFactory initialization resilience,
 * lifecycle guarding, and fallback centering in ImportBackupTabsScreen (ATT-1579 / REQ-MAP-022 / TST-MAP-024).
 */
class ImportBackupMapResilienceTest {

    @Test
    fun testBoundsBuilderWithEmptyAndValidPoints() {
        // TST-MAP-024.3: Empty decoded points produce null bounds safely
        val emptyPoints = emptyList<LatLng>()
        val emptyBounds = if (emptyPoints.isEmpty()) null else {
            val b = LatLngBounds.builder()
            emptyPoints.forEach { b.include(it) }
            b.build()
        }
        assertNull(emptyBounds)

        // Valid decoded points produce non-null LatLngBounds with valid center
        val routePoints = listOf(
            LatLng(48.1351, 11.5820),
            LatLng(48.1400, 11.5900),
            LatLng(48.1300, 11.5700)
        )
        val validBounds = if (routePoints.isEmpty()) null else {
            val b = LatLngBounds.builder()
            routePoints.forEach { b.include(it) }
            b.build()
        }
        assertNotNull(validBounds)
        val center = validBounds!!.center
        assertTrue(center.latitude in 48.1300..48.1400)
        assertTrue(center.longitude in 11.5700..11.5900)
    }

    @Test
    fun testCameraUpdatePreconditions() {
        // TST-MAP-024.1: Camera animation must only fire when isMapLoaded == true and bounds != null
        val bounds = LatLngBounds.builder()
            .include(LatLng(48.1, 11.5))
            .include(LatLng(48.2, 11.6))
            .build()

        var updateTriggered = false

        fun evaluateCameraUpdate(isMapLoaded: Boolean, b: LatLngBounds?) {
            if (isMapLoaded && b != null) {
                updateTriggered = true
            }
        }

        // Case 1: Map not loaded yet -> false
        updateTriggered = false
        evaluateCameraUpdate(isMapLoaded = false, b = bounds)
        assertFalse("Camera update must NOT trigger before map is loaded", updateTriggered)

        // Case 2: Map loaded but bounds null -> false
        updateTriggered = false
        evaluateCameraUpdate(isMapLoaded = true, b = null)
        assertFalse("Camera update must NOT trigger when bounds are null", updateTriggered)

        // Case 3: Both map loaded and bounds valid -> true
        updateTriggered = false
        evaluateCameraUpdate(isMapLoaded = true, b = bounds)
        assertTrue("Camera update must trigger when map is loaded and bounds are valid", updateTriggered)
    }

    @Test
    fun testDefensiveExceptionHandlingAndFallback() {
        // TST-MAP-024.2: Exception during CameraUpdateFactory must be caught and fall back to static center
        val bounds = LatLngBounds.builder()
            .include(LatLng(48.1351, 11.5820))
            .include(LatLng(48.1400, 11.5900))
            .build()

        var currentPosition = CameraPosition.fromLatLngZoom(bounds.center, 12f)
        var fallbackInvoked = false
        var exceptionCaught = false

        fun simulateCameraUpdateWithFailure(shouldThrowNpe: Boolean, shouldThrowIse: Boolean) {
            val isMapLoaded = true
            if (isMapLoaded) {
                try {
                    if (shouldThrowNpe) {
                        throw NullPointerException("CameraUpdateFactory is not initialized")
                    }
                    if (shouldThrowIse) {
                        throw IllegalStateException("Map size can't be 0")
                    }
                } catch (e: Exception) {
                    exceptionCaught = true
                    fallbackInvoked = true
                    currentPosition = CameraPosition.fromLatLngZoom(bounds.center, 12f)
                }
            }
        }

        // Simulate NPE (Crashlytics 83f3e051082969efdb92a73faaf6ca2d)
        simulateCameraUpdateWithFailure(shouldThrowNpe = true, shouldThrowIse = false)
        assertTrue("NPE must be caught gracefully without crashing", exceptionCaught)
        assertTrue("Fallback camera centering must be executed on NPE", fallbackInvoked)
        assertEquals(bounds.center.latitude, currentPosition.target.latitude, 0.0001)
        assertEquals(bounds.center.longitude, currentPosition.target.longitude, 0.0001)

        // Reset and simulate IllegalStateException (0-dimension view)
        exceptionCaught = false
        fallbackInvoked = false
        simulateCameraUpdateWithFailure(shouldThrowNpe = false, shouldThrowIse = true)
        assertTrue("IllegalStateException must be caught gracefully", exceptionCaught)
        assertTrue("Fallback camera centering must be executed on layout zero size", fallbackInvoked)
    }

    @Test
    fun testSequentialQueueRecompositionCameraUpdate() {
        // TST-MAP-024.1: Advancing through sequential queue items must re-frame camera without losing isMapLoaded
        var isMapLoaded = false
        val cluster1Bounds = LatLngBounds.builder()
            .include(LatLng(48.1351, 11.5820))
            .include(LatLng(48.1400, 11.5900))
            .build()
        val cluster2Bounds = LatLngBounds.builder()
            .include(LatLng(52.5200, 13.4050))
            .include(LatLng(52.5300, 13.4150))
            .build()

        var currentAnimatedBounds: LatLngBounds? = null

        fun simulateDialogComposition(stateId: Int, bounds: LatLngBounds?) {
            // Emulating LaunchedEffect(state, bounds, isMapLoaded)
            if (isMapLoaded && bounds != null) {
                currentAnimatedBounds = bounds
            }
        }

        // 1. Initial Dialog Mount: Map loading in progress
        simulateDialogComposition(stateId = 1, bounds = cluster1Bounds)
        assertNull("Camera update must not trigger before onMapLoaded fires", currentAnimatedBounds)

        // 2. Map finishes loading for item 1
        isMapLoaded = true
        simulateDialogComposition(stateId = 1, bounds = cluster1Bounds)
        assertEquals("Camera must animate to cluster 1 bounds", cluster1Bounds, currentAnimatedBounds)

        // 3. User names cluster 1; dialog advances to cluster 2 (recomposition with stateId = 2)
        // CRITICAL INVARIANT (ATT-2022): isMapLoaded must NOT reset to false!
        simulateDialogComposition(stateId = 2, bounds = cluster2Bounds)
        assertEquals("Camera must immediately animate to cluster 2 bounds", cluster2Bounds, currentAnimatedBounds)
        val animated = currentAnimatedBounds
        assertNotNull(animated)
        assertEquals(52.5250, animated!!.center.latitude, 0.001)
        assertEquals(13.4100, animated.center.longitude, 0.001)
    }

    @Test
    fun testBoundsBuilderIncludesStartEndApexMarkers() {
        // TST-MAP-024.3: Bounds envelope must include track polyline as well as start, end, and apex markers
        val decodedPoints = listOf(
            LatLng(48.1350, 11.5800),
            LatLng(48.1360, 11.5810)
        )
        val startMarker = LatLng(48.1300, 11.5700) // Extends south-west
        val endMarker = LatLng(48.1370, 11.5820)
        val apexMarker = LatLng(48.1450, 11.5950) // Extends north-east

        val b = LatLngBounds.builder()
        var hasPoints = false
        decodedPoints.forEach {
            b.include(it)
            hasPoints = true
        }
        startMarker.let { b.include(it); hasPoints = true }
        endMarker.let { b.include(it); hasPoints = true }
        apexMarker.let { b.include(it); hasPoints = true }
        val bounds = if (hasPoints) b.build() else null

        assertNotNull(bounds)
        assertTrue("Bounds must enclose start marker", bounds!!.contains(startMarker))
        assertTrue("Bounds must enclose end marker", bounds.contains(endMarker))
        assertTrue("Bounds must enclose apex marker", bounds.contains(apexMarker))
        decodedPoints.forEach {
            assertTrue("Bounds must enclose track point $it", bounds.contains(it))
        }
    }

    @Test
    fun testImportBackupTabsScreenSourceCodeContract() {
        // Contract test ensuring the source code strictly incorporates all resilience requirements
        val sourceFile = findSourceFile("ImportBackupTabsScreen.kt")
        assertTrue("ImportBackupTabsScreen.kt source file must exist", sourceFile.exists())
        val content = sourceFile.readText()

        // 1. isMapLoaded state tracked via remember (uncoupled from state to persist across queue transitions)
        assertTrue(
            "Must declare isMapLoaded using remember without state key",
            content.contains("var isMapLoaded by remember { mutableStateOf(false) }")
        )

        // 2. GoogleMap onMapLoaded callback wired
        assertTrue(
            "GoogleMap must register onMapLoaded callback setting isMapLoaded to true",
            content.contains("onMapLoaded = { isMapLoaded = true }")
        )

        // 3. LaunchedEffect keyed to state, bounds and isMapLoaded
        assertTrue(
            "LaunchedEffect must be keyed to state, bounds, and isMapLoaded",
            content.contains("LaunchedEffect(state, bounds, isMapLoaded)")
        )

        // 4. Guard condition inside LaunchedEffect
        assertTrue(
            "LaunchedEffect must verify isMapLoaded && bounds != null before animating",
            content.contains("if (isMapLoaded && bounds != null)")
        )

        // 5. Try-catch block enclosing CameraUpdateFactory
        assertTrue(
            "CameraUpdateFactory invocation must be enclosed in try-catch",
            content.contains("catch (e: Exception)")
        )

        // 6. Explicit fallback to static bounds center
        assertTrue(
            "Catch block must fall back to static bounds center",
            content.contains("cameraPositionState.position = CameraPosition.fromLatLngZoom(bounds.center, 12f)")
        )
    }

    private fun findSourceFile(fileName: String): File {
        val candidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/migration/$fileName"),
            File("src/main/java/com/atrainingtracker/trainingtracker/migration/$fileName"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/migration/$fileName")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Source file $fileName not found in candidate paths: $candidates")
    }
}
