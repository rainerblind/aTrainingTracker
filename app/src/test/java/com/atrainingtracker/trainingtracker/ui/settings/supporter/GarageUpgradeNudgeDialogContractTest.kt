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

package com.atrainingtracker.trainingtracker.ui.settings.supporter

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Architectural and contract test for GarageUpgradeNudgeDialog (REQ-SET-088.7, TST-SET-088.7, ATT-5468).
 *
 * Verifies via programmatic reflection (strictly adhering to docs/coding_standards.md §5.4):
 * 1. GarageUpgradeNudgeDialogKt class exists and exposes a public composable function.
 * 2. Composable accepts GarageNudgeEvent and the required callback lambdas (onUpgrade, onDismiss).
 * 3. GarageDetectorDialog alias exists and preserves identical parameter contracts.
 */
class GarageUpgradeNudgeDialogContractTest {

    @Test
    fun testGarageUpgradeNudgeDialog_composableFunctionContract() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.supporter.GarageUpgradeNudgeDialogKt")
        assertNotNull("GarageUpgradeNudgeDialogKt class must exist", clazz)

        val methods = clazz.declaredMethods.filter {
            it.name.startsWith("GarageUpgradeNudgeDialog") && Modifier.isPublic(it.modifiers)
        }
        assertTrue("GarageUpgradeNudgeDialog composable function must exist and be public", methods.isNotEmpty())

        val method = methods.first()
        val paramTypes = method.parameterTypes

        // 1. Qualification event parameter
        val eventClass = Class.forName("com.atrainingtracker.trainingtracker.monetization.GarageNudgeEvent")
        assertTrue(
            "Must accept event parameter of type GarageNudgeEvent",
            paramTypes.any { it == eventClass }
        )

        // 2. Callbacks: onUpgrade (planId -> Unit), onDismiss (() -> Unit)
        val functionTypes = paramTypes.filter { it.name.startsWith("kotlin.jvm.functions.Function") }
        assertTrue(
            "Must accept at least 2 callback functions (onUpgrade, onDismiss)",
            functionTypes.size >= 2
        )
    }

    @Test
    fun testGarageDetectorDialog_aliasComposableContract() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.supporter.GarageUpgradeNudgeDialogKt")
        assertNotNull("GarageUpgradeNudgeDialogKt class must exist", clazz)

        val methods = clazz.declaredMethods.filter {
            it.name.startsWith("GarageDetectorDialog") && Modifier.isPublic(it.modifiers)
        }
        assertTrue("GarageDetectorDialog composable function alias must exist and be public", methods.isNotEmpty())

        val method = methods.first()
        val paramTypes = method.parameterTypes

        val eventClass = Class.forName("com.atrainingtracker.trainingtracker.monetization.GarageNudgeEvent")
        assertTrue(
            "Must accept event parameter of type GarageNudgeEvent",
            paramTypes.any { it == eventClass }
        )

        val functionTypes = paramTypes.filter { it.name.startsWith("kotlin.jvm.functions.Function") }
        assertTrue(
            "Must accept at least 2 callback functions (onUpgrade, onDismiss)",
            functionTypes.size >= 2
        )
    }
}
