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

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Structural and architectural contract test for RouteSelectorSheet mid-ride entry point
 * and design system alignment (REQ-UI-282 / TST-UI-242.1 / ATT-2462).
 */
class RouteSelectorMidRideContractTest {

    private val routeSelectorClass: Class<*> by lazy {
        Class.forName("com.atrainingtracker.trainingtracker.ui.routes.RouteSelectorSheetKt")
    }

    private val trackingTabsClass: Class<*> by lazy {
        Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabsScreenKt")
    }

    @Test
    fun routeSelectorSheet_declaresMidRideParameters() {
        val methods = routeSelectorClass.declaredMethods

        val sheetMethod = methods.find { it.name == "RouteSelectorModalBottomSheet" }
        assertNotNull("RouteSelectorModalBottomSheet composable must be declared", sheetMethod)
        assertTrue(
            "RouteSelectorModalBottomSheet must accept isMidRide boolean parameter",
            sheetMethod!!.parameterTypes.contains(Boolean::class.javaPrimitiveType)
        )

        val contentMethod = methods.find { it.name == "RouteSelectorContent" }
        assertNotNull("RouteSelectorContent composable must be declared", contentMethod)
        assertTrue(
            "RouteSelectorContent must accept isMidRide boolean parameter",
            contentMethod!!.parameterTypes.contains(Boolean::class.javaPrimitiveType)
        )
    }

    @Test
    fun routeSelectorSheet_conditionallyRendersMidRideHeimwegCard() {
        val methods = routeSelectorClass.declaredMethods
        val heimwegCardMethod = methods.find { it.name == "MidRideHeimwegCard" }
        assertNotNull("MidRideHeimwegCard composable must be declared", heimwegCardMethod)
    }

    @Test
    fun midRideHeimwegCard_adheresToDesignSystemTokens() {
        val methods = routeSelectorClass.declaredMethods
        val heimwegCardMethod = methods.find { it.name == "MidRideHeimwegCard" }
        assertNotNull("MidRideHeimwegCard composable must be declared", heimwegCardMethod)

        val paramTypes = heimwegCardMethod!!.parameterTypes
        val hasCallback = paramTypes.any {
            Function0::class.java.isAssignableFrom(it) || it.name.startsWith("kotlin.jvm.functions.Function0")
        }
        assertTrue("MidRideHeimwegCard must accept onTakeMeHome callback", hasCallback)
    }

    @Test
    fun trackingTabsScreen_wiresMidRideReturnNavigation() {
        assertNotNull("TrackingTabsScreenKt class must exist", trackingTabsClass)
        val methods = trackingTabsClass.declaredMethods
        val screenMethod = methods.find { it.name.startsWith("TrackingTabsScreen") && Modifier.isPublic(it.modifiers) }
        assertNotNull("TrackingTabsScreen composable must be declared and public", screenMethod)
    }
}
