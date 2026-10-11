package com.atrainingtracker.trainingtracker.ui.tracking.tracking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * UI contract tests verifying SensorGridScreen elevation profile integration,
 * reactive resolution via TrackingElevationProfileResolver, and layout invariant preservation
 * (REQ-UI-355 / TST-UI-315 / ATT-3199).
 */
class SensorGridScreenElevationProfileTest {

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
        assertTrue("File must exist: $relativePath", target.exists())
        return target
    }

    @Test
    fun testSensorGridScreen_collectsCurrentLocationFlow() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        assertTrue(
            "SensorGridScreen must collect currentLocation from currentLocationFlow (TST-UI-315.2)",
            content.contains("val currentLocation by currentLocationFlow.collectAsState()")
        )
    }

    @Test
    fun testSensorGridScreen_wiresTrackingElevationProfileResolver() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        assertTrue(
            "SensorGridScreen must invoke TrackingElevationProfileResolver.resolve (TST-UI-315.1)",
            content.contains("TrackingElevationProfileResolver.resolve")
        )
        assertTrue(
            "SensorGridScreen must guard elevation profile rendering using resolvedProfile.pathPoints (TST-UI-315.3)",
            content.contains("state.showElevationProfile && resolvedProfile.pathPoints.size >= 2")
        )
    }

    @Test
    fun testSensorGridScreen_passesResolvedDataToElevationProfile() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        assertTrue(
            "ElevationProfile must receive resolvedProfile.pathPoints (TST-UI-315.1)",
            content.contains("pathPoints = resolvedProfile.pathPoints")
        )
        assertTrue(
            "ElevationProfile must receive resolvedProfile.markerDistance (TST-UI-315.2)",
            content.contains("currentDistance = resolvedProfile.markerDistance")
        )
        assertTrue(
            "ElevationProfile must receive resolvedProfile.climbs (TST-UI-315.1)",
            content.contains("climbs = resolvedProfile.climbs")
        )
    }

    @Test
    fun testSensorGridScreen_maintainsCanonicalSurfaceAndModifierInvariants() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        assertTrue(
            "ElevationProfile modifier must be fillMaxWidth (TST-UI-315.3)",
            content.contains("modifier = Modifier.fillMaxWidth()")
        )
        assertFalse(
            "ElevationProfile must not receive fillParentHeight (TST-UI-315.3)",
            content.contains("fillParentHeight = true")
        )
    }
}
