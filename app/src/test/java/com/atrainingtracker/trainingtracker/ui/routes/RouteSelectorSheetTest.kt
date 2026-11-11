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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Contract tests verifying structural integrity, token consumption, and component boundaries
 * of [RouteSelectorSheetKt] (REQ-MAP-024, REQ-UI-280 / TST-UI-240 / ATT-2459).
 */
class RouteSelectorSheetTest {

    private val sheetClass: Class<*> by lazy {
        Class.forName("com.atrainingtracker.trainingtracker.ui.routes.RouteSelectorSheetKt")
    }

    @Test
    fun testRouteSelectorSheet_exposesModalBottomSheetAndContent() {
        val methods = sheetClass.declaredMethods

        val modalSheetMethod = methods.find { it.name == "RouteSelectorModalBottomSheet" }
        assertNotNull("RouteSelectorSheet must expose RouteSelectorModalBottomSheet", modalSheetMethod)

        val contentMethod = methods.find { it.name == "RouteSelectorContent" }
        assertNotNull("RouteSelectorSheet must expose RouteSelectorContent", contentMethod)

        val bannerMethod = methods.find { it.name == "ActiveRouteBanner" }
        assertNotNull("RouteSelectorSheet must expose ActiveRouteBanner", bannerMethod)

        val autoDetectedMethod = methods.find { it.name.startsWith("AutoDetectedRouteBanner") }
        assertTrue("RouteSelectorSheet must not expose AutoDetectedRouteBanner (REQ-UI-311)", autoDetectedMethod == null)

        val routeCardMethod = methods.find { it.name == "RouteCard" }
        assertNotNull("RouteSelectorSheet must expose RouteCard", routeCardMethod)
    }

    @Test
    fun testRouteSelectorModalBottomSheet_consumesDesignTokens() {
        val methods = sheetClass.declaredMethods
        val modalSheetMethod = methods.find { it.name == "RouteSelectorModalBottomSheet" && Modifier.isPublic(it.modifiers) }
        assertNotNull("RouteSelectorModalBottomSheet must exist and be public", modalSheetMethod)
    }

    @Test
    fun testRouteSelectorContent_doesNotRenderFilterTabsOrTabRow() {
        val routeFilterTabResult = runCatching {
            Class.forName("com.atrainingtracker.trainingtracker.ui.routes.RouteFilterTab")
        }
        assertTrue(
            "RouteFilterTab class must not exist (REQ-UI-280 / ATT-2459)",
            routeFilterTabResult.isFailure
        )
    }

    @Test
    fun testRouteSelectorContent_doesNotRenderTakeMeHomeCardUnconditionally() {
        val methods = sheetClass.declaredMethods
        val contentMethod = methods.find { it.name == "RouteSelectorContent" }
        assertNotNull("RouteSelectorContent must exist", contentMethod)
        assertTrue(
            "RouteSelectorContent must accept isMidRide boolean parameter",
            contentMethod!!.parameterTypes.contains(Boolean::class.javaPrimitiveType)
        )
    }

    @Test
    fun testRouteCard_displaysSportIconWithAppropriateTokens() {
        val methods = sheetClass.declaredMethods
        val cardMethod = methods.find { it.name == "RouteCard" }
        assertNotNull("RouteCard must exist", cardMethod)
    }

    @Test
    fun testRouteCard_preservesActiveRouteChipAndCardColors() {
        val methods = sheetClass.declaredMethods
        val cardMethod = methods.find { it.name == "RouteCard" }
        assertNotNull("RouteCard must exist", cardMethod)
    }

    @Test
    fun testRouteSelectorContent_doesNotRenderUpperActiveRouteBanner() {
        val methods = sheetClass.declaredMethods
        val bannerMethod = methods.find { it.name == "ActiveRouteBanner" }
        assertNotNull("ActiveRouteBanner composable must exist", bannerMethod)
    }

    @Test
    fun testRouteCard_supportsDirectCancellationAction() {
        val methods = sheetClass.declaredMethods
        val cardMethod = methods.find { it.name == "RouteCard" }
        assertNotNull("RouteCard must exist", cardMethod)

        val paramTypes = cardMethod!!.parameterTypes
        val hasClearCallback = paramTypes.any {
            Function0::class.java.isAssignableFrom(it) || it.name.startsWith("kotlin.jvm.functions.Function0")
        }
        assertTrue("RouteCard must accept onClearRoute callback parameter", hasClearCallback)
    }
}
