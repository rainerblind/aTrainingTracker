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

package com.atrainingtracker.trainingtracker.ui.settings.tuning

import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * TST-FIL-017.5: Expert Settings UI & 9-language localization contract test (REQ-FIL-017.5, ATT-7750).
 */
class AdvancedTuningErtsContractTest {

    @Test
    fun testTuningPreferencesDataStore_defaultIsFalse() {
        assertFalse(
            "Default post-workout ERTS smoothing must be false",
            TuningPreferencesDefaults.DEFAULT_POST_WORKOUT_ERTS_SMOOTHING_ENABLED
        )
        assertFalse(
            "TuningConfig default must be false",
            TuningConfig().postWorkoutErtsSmoothingEnabled
        )
        assertEquals(
            "tuning_post_workout_erts_smoothing",
            TuningPreferencesDataStore.KEY_POST_WORKOUT_ERTS_SMOOTHING.name
        )
    }

    @Test
    fun testNineLanguageLocalizationParity() {
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

        val resDir = File("src/main/res").takeIf { it.exists() }
            ?: File("app/src/main/res").takeIf { it.exists() }
            ?: File("../app/src/main/res")

        assertTrue("resDir should exist: ${resDir.absolutePath}", resDir.exists())

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()

        for (localeDirName in targetLocales) {
            val stringsFile = File(resDir, "$localeDirName/strings.xml")
            assertTrue("strings.xml must exist for $localeDirName", stringsFile.exists())

            val doc = builder.parse(stringsFile)
            val stringNodes = doc.getElementsByTagName("string")

            var foundTitle: String? = null
            var foundDesc: String? = null

            for (i in 0 until stringNodes.length) {
                val node = stringNodes.item(i)
                val nameAttr = node.attributes.getNamedItem("name")?.nodeValue
                if (nameAttr == "tuning_post_workout_erts_smoothing_title") {
                    foundTitle = node.textContent.trim()
                } else if (nameAttr == "tuning_post_workout_erts_smoothing_desc") {
                    foundDesc = node.textContent.trim()
                }
            }

            assertNotNull("Missing tuning_post_workout_erts_smoothing_title in $localeDirName", foundTitle)
            assertTrue("Empty tuning_post_workout_erts_smoothing_title in $localeDirName", foundTitle!!.isNotEmpty())

            assertNotNull("Missing tuning_post_workout_erts_smoothing_desc in $localeDirName", foundDesc)
            assertTrue("Empty tuning_post_workout_erts_smoothing_desc in $localeDirName", foundDesc!!.isNotEmpty())
        }
    }
}
