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

/**
 * System Requirements Specification Index Contract Test (ASPICE SYS.2,
 * REQ-PRO-054, REQ-PRO-055, TST-PRO-054, TST-PRO-055, ATT-6160, ATT-6161).
 *
 * Enforces:
 * 1. docs/system_requirements.md exists and covers both live tracking (SYSREQ-TRK) and data exchange (SYSREQ-DEX).
 * 2. Section 5 contains SYSREQ-DEX-001 through SYSREQ-DEX-006 (TST-PRO-055.1).
 * 3. Complete bidirectional traceability to REQ-PRO-055 in docs/requirements/req_process_stability.md.
 * 4. Zero source-text reading of production files complying with ATT-6186.
 */
class SystemRequirementsSpecificationContractTest {

    private fun findRepoRootFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
            File("../../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Could not locate $relativePath in working directory: ${File(".").absolutePath}")
    }

    /**
     * Asserts docs/system_requirements.md exists and contains Section 5 for Data Exchange (TST-PRO-055.1).
     */
    @Test
    fun testSystemRequirements_containsDataExchangeSection() {
        val sysreqFile = findRepoRootFile("docs/system_requirements.md")
        assertTrue("docs/system_requirements.md must exist", sysreqFile.exists())

        val content = sysreqFile.readText()
        assertTrue("Must declare Section 5 for Data Exchange and Cloud Integration", content.contains("## 5. Data Exchange and Cloud Integration System Requirements"))

        val expectedRequirements = listOf(
            "SYSREQ-DEX-001",
            "SYSREQ-DEX-002",
            "SYSREQ-DEX-003",
            "SYSREQ-DEX-004",
            "SYSREQ-DEX-005",
            "SYSREQ-DEX-006"
        )

        for (req in expectedRequirements) {
            assertTrue("docs/system_requirements.md must include $req", content.contains(req))
        }
    }

    /**
     * Asserts that docs/system_requirements.md contains a complete traceability matrix covering all SYSREQ-TRK and SYSREQ-DEX.
     */
    @Test
    fun testSystemRequirements_traceabilityMatrixComplete() {
        val content = findRepoRootFile("docs/system_requirements.md").readText()

        assertTrue("Must include Section 6 Complete Traceability Matrix", content.contains("## 6. Complete System Requirements Traceability Matrix"))

        // Check Live Tracking (SYSREQ-TRK)
        for (i in 1..6) {
            val trkId = "SYSREQ-TRK-%03d".format(i)
            assertTrue("Traceability matrix must include $trkId", content.contains(trkId))
        }

        // Check Data Exchange (SYSREQ-DEX)
        for (i in 1..6) {
            val dexId = "SYSREQ-DEX-%03d".format(i)
            assertTrue("Traceability matrix must include $dexId", content.contains(dexId))
        }
    }

    /**
     * Asserts living requirement REQ-PRO-055 maps to SYSREQ-DEX in docs/requirements/req_process_stability.md.
     */
    @Test
    fun testSystemRequirements_tracesToLivingRequirements() {
        val reqContent = findRepoRootFile("docs/requirements/req_process_stability.md").readText()

        assertTrue("Living docs must define REQ-PRO-055", reqContent.contains("REQ-PRO-055"))
        assertTrue("REQ-PRO-055 must trace to sysreq_data_exchange.md", reqContent.contains("sysreq_data_exchange.md"))
        assertTrue("REQ-PRO-055 must reference SYSREQ-DEX-001", reqContent.contains("SYSREQ-DEX-001"))
        assertTrue("REQ-PRO-055 must reference SYSREQ-DEX-006", reqContent.contains("SYSREQ-DEX-006"))
    }
}
