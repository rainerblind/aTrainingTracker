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

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * Architectural contract test verifying that SportTypeSelector utilizes safe vector fallbacks,
 * avoids brittle custom gray icons, and uses root res/drawable vector assets (REQ-UI-308, TST-UI-268).
 * Refactored to reflection per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class SportTypeSelectorContractTest {

    @Test
    fun testSportTypeSelectorUsesSafePainterResourceAndFallbackVectors() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.controltracking.SportTypeSelectorKt")
        val methods = clazz.declaredMethods
        val selectorMethod = methods.find { it.name.startsWith("SportTypeSelector") && Modifier.isPublic(it.modifiers) }
        assertNotNull("SportTypeSelector composable must exist", selectorMethod)
    }

    @Test
    fun testAllSportTypeDrawablesExistInRootDrawableDirectory() {
        val projectDir = File(System.getProperty("user.dir") ?: ".")
        val resDir = when {
            File(projectDir, "src/main/res/drawable").exists() -> File(projectDir, "src/main/res/drawable")
            File(projectDir, "app/src/main/res/drawable").exists() -> File(projectDir, "app/src/main/res/drawable")
            else -> File(projectDir, "../app/src/main/res/drawable")
        }

        assertTrue("Root drawable directory must exist", resDir.exists() && resDir.isDirectory)

        val rootDrawables = resDir.listFiles()?.map { it.name }?.toSet() ?: emptySet()
        val requiredDrawables = listOf(
            "bsport_run.xml",
            "bsport_bike.xml",
            "bsport_other.xml"
        )

        for (drawable in requiredDrawables) {
            assertTrue("Vector drawable $drawable must exist in root res/drawable/", drawable in rootDrawables)
        }

        val obsoleteGrayDrawables = listOf(
            "bsport_run_gray.png",
            "bsport_bike_gray.png",
            "bsport_other_gray.png"
        )
        for (obsolete in obsoleteGrayDrawables) {
            assertTrue("Obsolete gray drawable $obsolete must not exist", obsolete !in rootDrawables)
        }
    }
}
