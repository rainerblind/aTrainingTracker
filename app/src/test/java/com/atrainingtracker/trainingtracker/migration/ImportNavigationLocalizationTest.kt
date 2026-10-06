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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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

    private fun findFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../$relativePath"),
            File("../../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("File not found in candidates: $relativePath")
    }

    @Test
    fun testActionViewWorkoutExistsInAllNineLocales() {
        val targetKey = "action_view_workout"
        val regex = Regex("""<string name="$targetKey">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)

        for (locale in locales) {
            val file = findFile("src/main/res/$locale/strings.xml")
            assertTrue("strings.xml does not exist for $locale", file.exists())

            val content = file.readText()
            val match = regex.find(content)
            assertTrue("Target string key '$targetKey' missing in $locale", match != null)

            val text = match!!.groupValues[1].trim()
            assertFalse("Target string key '$targetKey' is empty in $locale", text.isEmpty())
        }
    }
}
