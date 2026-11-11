/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.migration

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test verifying 9-language localization parity and non-empty content
 * for post-import navigation action `action_view_workout` (REQ-MIG-032, TST-MIG-029, ATT-2337).
 */
class ImportNavigationLocalizationTest {

    private val locales = listOf(
        "values",
        "values-de",
        "values-es",
        "values-fr",
        "values-it",
        "values-ja",
        "values-nl",
        "values-pl",
        "values-pt"
    )

    @Test
    fun testActionViewWorkoutExistsInAllNineLocales() {
        val targetKey = "action_view_workout"

        for (locale in locales) {
            val strings = LocalizationTestCache.getStrings(locale)
            assertTrue("Target string key '$targetKey' missing in $locale", strings.containsKey(targetKey))

            val text = strings[targetKey]?.trim()
            assertNotNull("Target string key '$targetKey' must not be null in $locale", text)
            assertFalse("Target string key '$targetKey' is empty in $locale", text!!.isEmpty())
        }
    }
}
