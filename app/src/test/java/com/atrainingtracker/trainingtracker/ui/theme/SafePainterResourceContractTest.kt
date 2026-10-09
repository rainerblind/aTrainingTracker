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

package com.atrainingtracker.trainingtracker.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contract test enforcing REQ-UI-312 / TST-UI-272.2:
 * Guarded Compose Asset Resolution Contract Verification.
 */
class SafePainterResourceContractTest {

    @Test
    fun testSafePainterResourceFileExistsAndDefinesSafeFunction() {
        val projectDir = File(System.getProperty("user.dir") ?: ".")
        val sourceFile = when {
            File(projectDir, "src/main/java/com/atrainingtracker/trainingtracker/ui/theme/SafePainterResource.kt").exists() ->
                File(projectDir, "src/main/java/com/atrainingtracker/trainingtracker/ui/theme/SafePainterResource.kt")
            File(projectDir, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/theme/SafePainterResource.kt").exists() ->
                File(projectDir, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/theme/SafePainterResource.kt")
            else -> File(projectDir, "../app/src/main/java/com/atrainingtracker/trainingtracker/ui/theme/SafePainterResource.kt")
        }

        assertTrue("SafePainterResource.kt must exist: ${sourceFile.absolutePath}", sourceFile.exists())

        val content = sourceFile.readText()
        assertTrue("Must declare safePainterResource function", content.contains("fun safePainterResource("))
        assertTrue("Must handle Resources.NotFoundException defensively", content.contains("Resources.NotFoundException"))
        assertTrue("Must accept optional fallback ImageVector", content.contains("fallback: ImageVector? = null"))
        assertTrue("Must return Painter", content.contains("): Painter"))
    }
}
