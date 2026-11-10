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

package com.atrainingtracker.trainingtracker.segments

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural contract tests for [LiveSegmentsRepository] verifying dead code elimination
 * and code standards compliance (REQ-MAP-065.3, TST-MAP-067.3).
 */
class LiveSegmentsRepositoryContractTest {

    @Test
    fun testDeadCodeElimination_noTrailingDoubleCollect() {
        // Locate LiveSegmentsRepository.kt
        val candidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/segments/LiveSegmentsRepository.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/segments/LiveSegmentsRepository.kt")
        )
        val repoFile = candidates.firstOrNull { it.exists() }
        assertTrue("LiveSegmentsRepository.kt source file must exist", repoFile != null && repoFile.exists())

        val content = repoFile!!.readText()

        // Verify redundant .collect() after closing block is eliminated
        assertFalse(
            "LiveSegmentsRepository.kt must not contain redundant chained '}.collect()' on coroutine flow collectors",
            content.contains("}.collect()")
        )

        // Verify MutableStateFlow.update is imported and used
        assertTrue(
            "LiveSegmentsRepository.kt must import kotlinx.coroutines.flow.update",
            content.contains("import kotlinx.coroutines.flow.update")
        )
        assertTrue(
            "LiveSegmentsRepository.kt must use _liveSegments.update",
            content.contains("_liveSegments.update")
        )

        // Verify no direct assignment to _liveSegments.value
        assertFalse(
            "LiveSegmentsRepository.kt must not perform non-atomic direct assignments to _liveSegments.value",
            content.contains("_liveSegments.value =")
        )
    }
}
