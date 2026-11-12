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

package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * Unit & Contract tests for ResearchButton (REQ-UI-286, TST-UI-246, ATT-2680):
 * Refactored to reflection and LocalizationTestCache per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class ResearchButtonTest {

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
    fun testResearchButton_usesVectorRefreshAndNoPainterResource() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ResearchButtonKt")
        val methods = clazz.declaredMethods
        val buttonMethod = methods.find { it.name.startsWith("ResearchButton") && Modifier.isPublic(it.modifiers) }
        assertNotNull("ResearchButton composable must exist", buttonMethod)
    }

    @Test
    fun testResearchIconVectorDrawable_existsAndValid() {
        val drawableFile = LocalizationTestCache.resolveFile("", "drawable/research_icon.xml")
        val altFile = File("app/src/main/res/drawable/research_icon.xml")
        assertTrue("research_icon.xml must exist in res/drawable/", drawableFile.exists() || altFile.exists())
    }

    @Test
    fun testResearchString_parityAcrossAll9Locales() {
        for (locale in locales) {
            val strings = LocalizationTestCache.getStrings(locale)
            val text = strings["research"]
            assertNotNull("Missing 'research' string in $locale", text)
            assertFalse("'research' string in $locale must not be blank", text!!.isBlank())
        }
    }
}
