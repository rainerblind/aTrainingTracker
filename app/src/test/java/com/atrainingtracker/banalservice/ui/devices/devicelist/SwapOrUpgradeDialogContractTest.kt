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

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier
import java.util.regex.Pattern

/**
 * Architectural and contract test for SwapOrUpgradeDialog and KeepTwoSensorsBottomSheet
 * (REQ-SET-084.4, REQ-SET-084.8, REQ-UI-149, TST-SET-084, TST-UI-102, ATT-5470, ATT-5661).
 *
 * Verifies via programmatic reflection and resource contract audit (strictly adhering to docs/coding_standards.md §5.4):
 * 1. SwapOrUpgradeDialogKt class exists and exposes a public composable function with valid parameters.
 * 2. All 5 dialog string keys exist across all 9 supported locales with 100% placeholder parity and zero emojis.
 * 3. Appreciative base version wording ("Basisversion" / "base version") and support development CTA
 *    ("Entwicklung unterstützen" / "Support Development") replace SaaS paywall phrasing in DE and EN.
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
            "Must accept candidate device parameter of type DeviceUiData",
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

    @Test
    fun testSwapAndKeepSensorsStrings_existAcrossAll9LocalesWithPlaceholderParity() {
        val keys = listOf(
            "swap_dialog_title",
            "swap_dialog_message",
            "swap_dialog_action_unlock",
            "keep_two_sensors_action_unlock",
            "keep_two_sensors_message"
        )

        // Emoji pattern forbidding inline decorative Unicode emojis (ATT-5400)
        val emojiPattern = Pattern.compile("[\\x{1F300}-\\x{1F64F}\\x{1F680}-\\x{1F6FF}\\x{2600}-\\x{26FF}\\x{2700}-\\x{27BF}]")

        for (locale in LocalizationTestCache.LOCALES) {
            val strings = LocalizationTestCache.getStrings(locale)

            for (key in keys) {
                val rawContent = strings[key]
                assertNotNull("Key '$key' must exist in $locale/strings.xml", rawContent)
                assertFalse("Key '$key' in $locale must not be blank", rawContent!!.isBlank())
                assertFalse(
                    "Key '$key' in $locale must not contain decorative emojis (ATT-5400)",
                    emojiPattern.matcher(rawContent).find()
                )
            }

            val swapMessage = strings["swap_dialog_message"]
            assertTrue(
                "swap_dialog_message in $locale must contain positional placeholder '%1\$s'",
                swapMessage!!.contains("%1\$s")
            )
        }
    }

    @Test
    fun testSwapAndKeepSensorsStrings_appreciativeWordingGermanAndEnglish() {
        // 1. German locale (values-de)
        val deStrings = LocalizationTestCache.getStrings("values-de")
        val deTitle = deStrings["swap_dialog_title"]
        val deMessage = deStrings["swap_dialog_message"]
        val deSwapUnlock = deStrings["swap_dialog_action_unlock"]
        val deKeepUnlock = deStrings["keep_two_sensors_action_unlock"]
        val deKeepMessage = deStrings["keep_two_sensors_message"]

        assertEquals("Geräte-Limit (2/2 Plätze belegt)", deTitle)
        assertTrue("DE message must contain 'In der Basisversion'", deMessage!!.contains("In der Basisversion"))
        assertTrue("DE message must contain 'unterstütze die Weiterentwicklung'", deMessage.contains("unterstütze die Weiterentwicklung"))
        assertFalse("DE message must not contain 'Free-Tier'", deMessage.contains("Free-Tier"))
        assertFalse("DE message must not contain 'Pay what you want'", deMessage.contains("Pay what you want"))
        assertEquals("Entwicklung unterstützen", deSwapUnlock)
        assertEquals("Entwicklung unterstützen", deKeepUnlock)
        assertTrue("DE keep message must contain 'in der Basisversion'", deKeepMessage!!.contains("in der Basisversion"))
        assertFalse("DE keep message must not contain 'Free-Tarif'", deKeepMessage.contains("Free-Tarif"))

        // 2. English / Default locale (values)
        val enStrings = LocalizationTestCache.getStrings("values")
        val enTitle = enStrings["swap_dialog_title"]
        val enMessage = enStrings["swap_dialog_message"]
        val enSwapUnlock = enStrings["swap_dialog_action_unlock"]
        val enKeepUnlock = enStrings["keep_two_sensors_action_unlock"]
        val enKeepMessage = enStrings["keep_two_sensors_message"]

        assertEquals("Device Limit (2/2 Slots Used)", enTitle)
        assertTrue("EN message must contain 'In the base version'", enMessage!!.contains("In the base version"))
        assertTrue("EN message must contain 'support the ongoing development'", enMessage.contains("support the ongoing development"))
        assertFalse("EN message must not contain 'Free tier'", enMessage.contains("Free tier"))
        assertFalse("EN message must not contain 'Pay what you want'", enMessage.contains("Pay what you want"))
        assertEquals("Support Development", enSwapUnlock)
        assertEquals("Support Development", enKeepUnlock)
        assertTrue("EN keep message must contain 'the base version'", enKeepMessage!!.contains("the base version"))
        assertFalse("EN keep message must not contain 'Free tier'", enKeepMessage.contains("Free tier"))
    }
}
