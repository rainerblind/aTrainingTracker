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

package com.atrainingtracker.banalservice.ui.devices.devicelist

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Architectural and contract test for SwapOrUpgradeDialog (REQ-SET-084.8, TST-SET-084.8, ATT-5470).
 *
 * Verifies via programmatic reflection (strictly adhering to docs/coding_standards.md §5.4):
 * 1. SwapOrUpgradeDialogKt class exists and exposes a public composable function.
 * 2. Composable accepts DeviceUiData, List<DeviceUiData>, Long, and the required callback lambdas.
 */
class SwapOrUpgradeDialogContractTest {

    @Test
    fun testSwapOrUpgradeDialog_composableFunctionContract() {
        val clazz = Class.forName("com.atrainingtracker.banalservice.ui.devices.devicelist.SwapOrUpgradeDialogKt")
        assertNotNull("SwapOrUpgradeDialogKt class must exist", clazz)

        val methods = clazz.declaredMethods.filter {
            it.name.startsWith("SwapOrUpgradeDialog") && Modifier.isPublic(it.modifiers)
        }
        assertTrue("SwapOrUpgradeDialog composable function must exist and be public", methods.isNotEmpty())

        val method = methods.first()
        val paramTypes = method.parameterTypes

        // 1. Candidate device
        val deviceUiDataClass = Class.forName("com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData")
        assertTrue(
            "Must accept candidateDevice parameter of type DeviceUiData",
            paramTypes.any { it == deviceUiDataClass }
        )

        // 2. Currently paired devices list
        assertTrue(
            "Must accept currentlyPairedDevices parameter of type List",
            paramTypes.any { it == List::class.java }
        )

        // 3. Recommended device id
        assertTrue(
            "Must accept recommendedUnpairDeviceId parameter of type Long",
            paramTypes.any { it == Long::class.javaPrimitiveType || it == Long::class.javaObjectType }
        )

        // 4. Callbacks: onSwapConfirmed, onNavigateToSupporter, onDismiss
        val functionTypes = paramTypes.filter { it.name.startsWith("kotlin.jvm.functions.Function") }
        assertTrue(
            "Must accept at least 3 callback functions (onSwapConfirmed, onNavigateToSupporter, onDismiss)",
            functionTypes.size >= 3
        )
    }
}
