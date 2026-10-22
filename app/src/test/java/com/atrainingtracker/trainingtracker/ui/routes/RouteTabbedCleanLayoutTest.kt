package com.atrainingtracker.trainingtracker.ui.routes

import com.atrainingtracker.trainingtracker.settings.TuningConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural contract tests verifying complete excision of corridor-based route grouping
 * and differentiating thumbnail zoom, and restoration of clean flat route list presentation
 * (TST-MAP-037 / REQ-MAP-035, TST-TST-002 / REQ-TST-002).
 */
class RouteTabbedCleanLayoutTest {

    @Test
    fun routeTabbedScreen_doesNotReferenceGatewayFilterChipsOrCorridorClassifier() {
        // Programmatic classpath reflection: verify deleted classes cannot be resolved by classloader
        val obsoleteClassNames = listOf(
            "com.atrainingtracker.trainingtracker.routes.RouteCorridorClassifier",
            "com.atrainingtracker.trainingtracker.routes.RouteBoundingBoxCalculator",
            "com.atrainingtracker.trainingtracker.ui.routes.GatewayFilterChipsRowKt",
            "com.atrainingtracker.trainingtracker.ui.routes.GatewayDirection"
        )
        for (className in obsoleteClassNames) {
            val classFound = try {
                Class.forName(className)
                true
            } catch (_: ClassNotFoundException) {
                false
            }
            assertFalse("Obsolete class '$className' must not exist in classpath", classFound)
        }

        // Verify RouteTabbedScreenKt does not declare corridor or gateway methods
        val tabbedScreenClass = Class.forName("com.atrainingtracker.trainingtracker.ui.routes.RouteTabbedScreenKt")
        val methods = tabbedScreenClass.declaredMethods.map { it.name }
        assertFalse("RouteTabbedScreenKt must not contain gatewayChipsHeight", methods.any { it.contains("gatewayChipsHeight") })
        assertFalse("RouteTabbedScreenKt must not contain hasGatewayChips", methods.any { it.contains("hasGatewayChips") })
    }

    @Test
    fun routeList_doesNotAcceptOrForwardFocusedThumbnailZoom() {
        // Verify RouteListKt declared methods do not contain focusedThumbnailZoom parameters
        val routeListClass = Class.forName("com.atrainingtracker.trainingtracker.ui.routes.RouteListKt")
        val methods = routeListClass.declaredMethods
        for (method in methods) {
            for (paramType in method.parameterTypes) {
                assertFalse(
                    "RouteList method '${method.name}' must not accept corridor or thumbnail zoom parameter type",
                    paramType.name.contains("ThumbnailZoom") || paramType.name.contains("Corridor")
                )
            }
        }
    }

    @Test
    fun routeItem_doesNotReferenceRouteBoundingBoxCalculator() {
        // Verify RouteItemKt declared methods and fields do not reference RouteBoundingBoxCalculator
        val routeItemClass = Class.forName("com.atrainingtracker.trainingtracker.ui.routes.RouteItemKt")
        val methods = routeItemClass.declaredMethods
        for (method in methods) {
            for (paramType in method.parameterTypes) {
                assertFalse(
                    "RouteItem method '${method.name}' must not accept RouteBoundingBoxCalculator",
                    paramType.name.contains("RouteBoundingBoxCalculator")
                )
            }
        }
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
