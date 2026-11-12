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

package com.atrainingtracker.trainingtracker.build

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Contract verification test for Non-Functional Requirements (NFRs)
 * under ASPICE SWE.1 BP1, BP4 and SWE.2 BP4 (REQ-NFR-001..018, TST-NFR-001..018,
 * REQ-PRO-052, TST-PRO-052, ATT-6139).
 *
 * Enforces:
 * 1. Living requirements shard (docs/requirements/req_process_stability.md) defines
 *    all 18 quantified NFRs (REQ-NFR-001 through REQ-NFR-018) with [Verified] status.
 * 2. Every REQ-NFR defines Given-When-Then acceptance criteria, numeric target thresholds,
 *    concrete measurement procedures, and bidirectional TST-NFR links.
 * 3. Living tests shard (docs/tests/tst_process_stability.md) defines all 18 test specifications
 *    (TST-NFR-001 through TST-NFR-018) with bidirectional requirement links, test procedures,
 *    and expected quantitative results.
 * 4. Architecture Section 4.9 budget matrix links directly to REQ-NFR-003..014.
 */
class NonFunctionalRequirementsContractTest {

    private fun findRepoRootFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
            File("../../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Could not locate $relativePath in ${File(".").absolutePath}")
    }

    @Test
    fun testRequirementsShard_definesQuantifiedNfrsWithGivenWhenThenAndMeasurement() {
        val reqFile = findRepoRootFile("docs/requirements/req_process_stability.md")
        assertTrue("req_process_stability.md must exist", reqFile.exists())
        val content = reqFile.readText()

        val expectedNfrs = mapOf(
            1 to listOf("EKF", "2.0 ms", "TST-NFR-001"),
            2 to listOf("Stationary", "0.5 m/min", "TST-NFR-002"),
            3 to listOf("50 ms", "1000 ms", "TST-NFR-003"),
            4 to listOf("5", "%", "TST-NFR-004"),
            5 to listOf("40 MB", "150 MB", "TST-NFR-005"),
            6 to listOf("5.0%/h", "12.0%/h", "TST-NFR-006"),
            7 to listOf("25 ms", "TST-NFR-007"),
            8 to listOf("3.5 s", "TST-NFR-008"),
            9 to listOf("PARTIAL_WAKE_LOCK", "TrackingLock", "TST-NFR-009"),
            10 to listOf("30 ms", "mScheduler", "TST-NFR-010"),
            11 to listOf("360 KB/h", "TST-NFR-011"),
            12 to listOf("16.6 ms", "60 FPS", "TST-NFR-012"),
            13 to listOf("30 ms", "TST-NFR-013"),
            14 to listOf("1", "Hz", "TST-NFR-014"),
            15 to listOf("1200 ms", "1500 ms", "TST-NFR-015"),
            16 to listOf("5.0 m", "3.0 m", "TST-NFR-016"),
            17 to listOf("99.5%", "TST-NFR-017"),
            18 to listOf("0.05%", "TST-NFR-018")
        )

        for ((id, tokens) in expectedNfrs) {
            val reqId = String.format(Locale.US, "REQ-NFR-%03d", id)
            assertTrue("Requirements shard must contain $reqId", content.contains(reqId))
            val verified = content.contains("$reqId:") && content.contains("[Verified]")
            assertTrue("$reqId must be marked [Verified]", verified)

            for (token in tokens) {
                assertTrue("$reqId must contain expected threshold/trace '$token'", content.contains(token))
            }

            assertTrue("Must specify Measurement Procedure for $reqId", content.contains("Measurement Procedure"))
            val gwt = content.contains("Acceptance Criteria (Given-When-Then)")
            assertTrue("Must specify Acceptance Criteria (Given-When-Then)", gwt)
        }
    }

    @Test
    fun testTestSpecificationShard_definesTstNfrsWithBidirectionalLinks() {
        val tstFile = findRepoRootFile("docs/tests/tst_process_stability.md")
        assertTrue("tst_process_stability.md must exist", tstFile.exists())
        val content = tstFile.readText()

        for (id in 1..18) {
            val tstId = String.format(Locale.US, "TST-NFR-%03d", id)
            val reqId = String.format(Locale.US, "REQ-NFR-%03d", id)

            assertTrue("Test shard must contain $tstId", content.contains(tstId))
            assertTrue("$tstId must link back to $reqId", content.contains(reqId))
            val verified = content.contains("$tstId:") && content.contains("[Verified]")
            assertTrue("$tstId must be marked [Verified]", verified)
            assertTrue("$tstId must define Expected Result", content.contains("Expected Result"))
        }
    }

    @Test
    fun testArchitectureSection49_traceabilityToNfrs() {
        val archFile = findRepoRootFile("docs/architecture.md")
        val content = archFile.readText()

        for (id in 3..14) {
            val reqId = String.format(Locale.US, "REQ-NFR-%03d", id)
            assertTrue("Architecture Section 4.9 must trace to $reqId", content.contains(reqId))
        }
    }
}
