package com.atrainingtracker.trainingtracker.ui.routes

import com.atrainingtracker.trainingtracker.settings.TuningConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural contract tests verifying complete excision of corridor-based route grouping
 * and differentiating thumbnail zoom, and restoration of clean flat route list presentation
 * (TST-MAP-037 / REQ-MAP-035).
 */
class RouteTabbedCleanLayoutTest {

    @Test
    fun routeTabbedScreen_doesNotReferenceGatewayFilterChipsOrCorridorClassifier() {
        val file = File("src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteTabbedScreen.kt")
        assertTrue("RouteTabbedScreen.kt must exist", file.exists())
        val content = file.readText()

        assertFalse("RouteTabbedScreen must not reference GatewayFilterChipsRow", content.contains("GatewayFilterChipsRow"))
        assertFalse("RouteTabbedScreen must not reference GatewayDirection", content.contains("GatewayDirection"))
        assertFalse("RouteTabbedScreen must not reference RouteCorridorClassifier", content.contains("RouteCorridorClassifier"))
        assertFalse("RouteTabbedScreen must not compute gatewayChipsHeight", content.contains("gatewayChipsHeight"))
        assertFalse("RouteTabbedScreen must not contain hasGatewayChips", content.contains("hasGatewayChips"))
    }

    @Test
    fun routeList_doesNotAcceptOrForwardFocusedThumbnailZoom() {
        val file = File("src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteList.kt")
        assertTrue("RouteList.kt must exist", file.exists())
        val content = file.readText()

        assertFalse("RouteList must not contain focusedThumbnailZoomEnabled", content.contains("focusedThumbnailZoomEnabled"))
    }

    @Test
    fun routeItem_doesNotReferenceRouteBoundingBoxCalculator_andPassesNullTargetBounds() {
        val file = File("src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteItem.kt")
        assertTrue("RouteItem.kt must exist", file.exists())
        val content = file.readText()

        assertFalse("RouteItem must not reference RouteBoundingBoxCalculator", content.contains("RouteBoundingBoxCalculator"))
        assertFalse("RouteItem must not contain focusedThumbnailZoomEnabled", content.contains("focusedThumbnailZoomEnabled"))
        assertTrue("RouteItem must pass targetBounds = null for full route preview", content.contains("targetBounds = null"))
    }

    @Test
    fun obsoleteClasses_areCompletelyDeleted() {
        val corridorClassifier = File("src/main/java/com/atrainingtracker/trainingtracker/routes/RouteCorridorClassifier.kt")
        val boundingBoxCalc = File("src/main/java/com/atrainingtracker/trainingtracker/routes/RouteBoundingBoxCalculator.kt")
        val gatewayChipsRow = File("src/main/java/com/atrainingtracker/trainingtracker/ui/routes/GatewayFilterChipsRow.kt")

        assertFalse("RouteCorridorClassifier.kt must be deleted", corridorClassifier.exists())
        assertFalse("RouteBoundingBoxCalculator.kt must be deleted", boundingBoxCalc.exists())
        assertFalse("GatewayFilterChipsRow.kt must be deleted", gatewayChipsRow.exists())
    }

    @Test
    fun tuningConfig_hasCleanPropertiesWithoutCorridorOrThumbnailZoom() {
        // Verify TuningConfig reflection does not contain corridor or thumbnail zoom properties
        val fields = TuningConfig::class.java.declaredFields.map { it.name }
        assertFalse("TuningConfig must not contain corridorGroupingEnabled", fields.contains("corridorGroupingEnabled"))
        assertFalse("TuningConfig must not contain focusedThumbnailZoomEnabled", fields.contains("focusedThumbnailZoomEnabled"))
    }
}
