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

package com.atrainingtracker.trainingtracker.ui.components.workoutheader

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural and visual contract tests verifying WorkoutHeader export menu FIT support
 * and 9-language localization parity (REQ-EXP-018, TST-EXP-015.5, TST-EXP-015.6, ATT-2310).
 */
class WorkoutHeaderExportMenuContractTest {

    private val headerSourceFile: File by lazy {
        listOf(
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt"),
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt")
        ).firstOrNull { it.exists() } ?: File("src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt")
    }

    private val resDir: File by lazy {
        listOf(
            File("src/main/res"),
            File("app/src/main/res"),
            File("../app/src/main/res")
        ).firstOrNull { it.exists() } ?: File("src/main/res")
    }

    @Test
    fun testWorkoutHeader_standardFormatsIncludesFit() {
        assertTrue("WorkoutHeader.kt source file must exist", headerSourceFile.exists())
        val content = headerSourceFile.readText()

        assertTrue(
            "WorkoutHeader standardFormats must register FileFormat.FIT to R.string.fitWrite",
            content.contains("FileFormat.FIT to R.string.fitWrite")
        )
    }

    @Test
    fun testFitWrite_localizationParityAcrossAll9Locales() {
        val targetLocales = listOf(
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

        for (locale in targetLocales) {
            val stringsFile = File(resDir, "$locale/strings.xml")
            assertTrue("strings.xml for locale '$locale' must exist at ${stringsFile.path}", stringsFile.exists())

            val content = stringsFile.readText()
            assertTrue(
                "Locale '$locale' must define string resource 'fitWrite'",
                content.contains("name=\"fitWrite\"")
            )
            // Ensure not empty
            val pattern = """<string name="fitWrite">([^<]+)</string>""".toRegex()
            val match = pattern.find(content)
            assertTrue("Locale '$locale' must have non-blank content for fitWrite", match != null && match.groupValues[1].isNotBlank())
        }
    }
}
