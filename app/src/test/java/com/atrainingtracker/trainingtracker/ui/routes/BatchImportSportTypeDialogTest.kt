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

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Component and architectural contract tests for [BatchImportSportTypeDialog]
 * (REQ-MAP-053.6, TST-MAP-056.4).
 */
class BatchImportSportTypeDialogTest {

    private val dialogClass: Class<*> by lazy {
        Class.forName("com.atrainingtracker.trainingtracker.ui.routes.BatchImportSportTypeDialogKt")
    }

    @Test
    fun testBatchSportModeEnum_containsExpectedVariants() {
        val values = BatchSportMode.values().map { it.name }
        assertEquals(2, values.size)
        assertTrue("BatchSportMode must contain ALL", values.contains("ALL"))
        assertTrue("BatchSportMode must contain INDIVIDUAL", values.contains("INDIVIDUAL"))
    }

    @Test
    fun testBatchImportSportTypeDialog_sourceArchitectureAndTokens() {
        assertNotNull("BatchImportSportTypeDialogKt class must exist", dialogClass)
        val methods = dialogClass.declaredMethods

        val dialogMethod = methods.find {
            it.name.startsWith("BatchImportSportTypeDialog") && Modifier.isPublic(it.modifiers)
        }
        assertNotNull("BatchImportSportTypeDialog composable must exist and be public", dialogMethod)

        val paramTypes = dialogMethod!!.parameterTypes
        assertTrue(
            "BatchImportSportTypeDialog must accept routeCount integer parameter",
            paramTypes.contains(Int::class.javaPrimitiveType)
        )

        val hasConfirmCallback = paramTypes.any {
            Function2::class.java.isAssignableFrom(it) || it.name.startsWith("kotlin.jvm.functions.Function2")
        }
        assertTrue(
            "BatchImportSportTypeDialog must accept onConfirm callback",
            hasConfirmCallback
        )

        val hasDismissCallback = paramTypes.any {
            Function0::class.java.isAssignableFrom(it) || it.name.startsWith("kotlin.jvm.functions.Function0")
        }
        assertTrue(
            "BatchImportSportTypeDialog must accept onDismiss callback",
            hasDismissCallback
        )

        // Verify preview methods exist
        val previewMethods = methods.filter { it.name.contains("Preview") }
        assertTrue("BatchImportSportTypeDialog must provide preview composables", previewMethods.isNotEmpty())
    }

    @Test
    fun testAllRequiredBatchImportStrings_existInValues() {
        val expectedKeys = listOf(
            "batch_import_dialog_title_format",
            "batch_import_mode_all",
            "batch_import_mode_individual",
            "batch_import_individual_hint",
            "batch_import_action_import",
            "batch_import_action_continue",
            "batch_import_individual_progress_format",
            "batch_import_skip_route"
        )

        for (locale in LocalizationTestCache.LOCALES) {
            val strings = LocalizationTestCache.getStrings(locale)
            for (key in expectedKeys) {
                val value = strings[key]
                assertNotNull("Key '$key' must exist in locale $locale", value)
                assertTrue("Key '$key' must not be blank in locale $locale", value!!.isNotBlank())
            }
        }
    }
}
