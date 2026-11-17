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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Release Manager System Verification Evidence Integration Contract Test
 * (ASPICE SYS.5, SWE.6, SUP.8, REQ-PRO-072, TST-PRO-072, REQ-PRO-112, TST-PRO-112, ATT-6169, ATT-8157).
 *
 * Enforces:
 * 1. .agents/skills/release-manager/SKILL.md mandates SYS.5 verification evidence and verification_summary.md (TST-PRO-072.1).
 * 2. SKILL.md does NOT mandate Python simulator system_integration_results.json or broken --output-json as a release gate (TST-PRO-112.2).
 * 3. Release checklist and procedures block release candidate tag creation and Play Store upload on missing/failing SYS.5/summary evidence (TST-PRO-072.2).
 * 4. docs/releases/ README.md and TEMPLATE.md define release dossier layout without fake SYS.4 gating (TST-PRO-072.3).
 * 5. docs/architecture.md Section 6 links release gating to SYS.5 evidence and discloses simulator boundaries (TST-PRO-072.4).
 */
class ReleaseManagerSystemEvidenceContractTest {

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
     * Asserts .agents/skills/release-manager/SKILL.md enforces SYS.5 + verification_summary.md without fake simulator gating (TST-PRO-072.1 / TST-PRO-112.2).
     */
    @Test
    fun testReleaseManagerSkill_specifiesHonestSystemEvidenceGates() {
        val skillFile = findRepoRootFile(".agents/skills/release-manager/SKILL.md")
        assertTrue(".agents/skills/release-manager/SKILL.md must exist (REQ-PRO-072.1)", skillFile.exists())

        val content = skillFile.readText()
        assertTrue("Skill must not be empty", content.isNotBlank())
        assertTrue("Must reference ASPICE SYS.5", content.contains("SYS.5"))
        assertTrue("Must reference REQ-PRO-072", content.contains("REQ-PRO-072"))
        assertTrue("Must reference REQ-PRO-112", content.contains("REQ-PRO-112"))

        assertTrue("Must mandate sys_verification_record.md artifact", content.contains("sys_verification_record.md"))
        assertTrue("Must mandate verification_summary.md artifact", content.contains("verification_summary.md"))
        assertTrue("Must specify release evidence directory under docs/releases/", content.contains("docs/releases/"))
        assertTrue("Must cite sys_verification_protocol.md", content.contains("sys_verification_protocol.md"))
        assertTrue("Must reference INT-SWE-01..08 real integration boundary", content.contains("INT-SWE-01..08"))

        assertFalse("Must NOT cite broken --output-json flag", content.contains("--output-json"))
        assertFalse("Must NOT cite retired run_system_integration_tests.py", content.contains("run_system_integration_tests.py"))
        assertFalse("Must NOT mandate system_integration_results.json in Phase 2.5/4",
            content.contains("system_integration_results.json"))
    }

    /**
     * Asserts release procedure explicitly defines blocking gate on tag creation and Play Store distribution (TST-PRO-072.2).
     */
    @Test
    fun testReleaseManagerSkill_explicitlyBlocksTagCreationOnMissingOrFailingEvidence() {
        val skillFile = findRepoRootFile(".agents/skills/release-manager/SKILL.md")
        val content = skillFile.readText()

        assertTrue("Must declare tag creation blocked if evidence missing/failing",
            content.contains("BLOCKED") && content.contains("tag"))
        assertTrue("Must specify blocking on Play Store upload",
            content.contains("Play Store") && content.contains("blocked", ignoreCase = true))
        assertTrue("Must check pre-tagging verification in Phase 4",
            content.contains("Pre-Tagging System Evidence Verification") ||
            content.contains("Pre-Tagging Gate Verification"))
    }

    /**
     * Asserts docs/releases/ README.md and TEMPLATE.md define release dossier layout without fake SYS.4 gating (TST-PRO-072.3 / TST-PRO-112.2).
     */
    @Test
    fun testReleaseEvidenceTemplates_existAndDefineHonestDossierLayout() {
        val readmeFile = findRepoRootFile("docs/releases/README.md")
        assertTrue("docs/releases/README.md must exist (REQ-PRO-072.3)", readmeFile.exists())

        val readmeContent = readmeFile.readText()
        assertTrue("README must reference ASPICE SUP.8", readmeContent.contains("SUP.8"))
        assertTrue("README must reference ASPICE SYS.5", readmeContent.contains("SYS.5"))
        assertTrue("README must document sys_verification_record.md", readmeContent.contains("sys_verification_record.md"))
        assertTrue("README must document verification_summary.md", readmeContent.contains("verification_summary.md"))
        assertTrue("README must state blocking enforcement", readmeContent.contains("BLOCKED"))
        assertTrue("README must state system_integration_results.json is retired as a blocking release gate",
            readmeContent.contains("retired as a blocking release gate"))
        assertFalse("README must NOT cite broken --output-json flag", readmeContent.contains("--output-json"))

        val templateFile = findRepoRootFile("docs/releases/TEMPLATE.md")
        assertTrue("docs/releases/TEMPLATE.md must exist (REQ-PRO-072.3)", templateFile.exists())

        val templateContent = templateFile.readText()
        assertTrue("Template must contain release dossier header", templateContent.contains("Release Qualification Dossier"))
        assertTrue("Template must include Gate 4 System Verification Protocol (SYS.5)", templateContent.contains("SYS.5"))
        assertTrue("Template must reference sys_verification_record.md", templateContent.contains("sys_verification_record.md"))
        assertTrue("Template must reference verification_summary.md", templateContent.contains("verification_summary.md"))
        assertTrue("Template must include sign-off signatures", templateContent.contains("Sign-Off") || templateContent.contains("Formal Sign-Off"))
        assertFalse("Template must NOT require system_integration_results.json", templateContent.contains("system_integration_results.json"))
        assertFalse("Template must NOT cite retired run_system_integration_tests.py", templateContent.contains("run_system_integration_tests.py"))
    }

    /**
     * Asserts docs/architecture.md Section 6 links release gating to SYS.5 evidence and discloses simulator scope (TST-PRO-072.4 / TST-PRO-112.3).
     */
    @Test
    fun testArchitecture_linksReleaseGatingToSystemEvidence() {
        val archFile = findRepoRootFile("docs/architecture.md")
        assertTrue("docs/architecture.md must exist", archFile.exists())

        val content = archFile.readText()
        assertTrue("docs/architecture.md must have Section 6.2", content.contains("### 6.2"))
        assertTrue("docs/architecture.md must have Section 6.4", content.contains("### 6.4"))
        assertTrue("Section 6.4 must reference REQ-PRO-072", content.contains("REQ-PRO-072"))
        assertTrue("Section 6.4 must reference TST-PRO-072", content.contains("TST-PRO-072"))
        assertTrue("Section 6.4 must reference REQ-PRO-112", content.contains("REQ-PRO-112"))
        assertTrue("Section 6.4 must reference release-manager skill", content.contains("release-manager/SKILL.md"))
        assertTrue("Section 6.4 must reference docs/releases/", content.contains("docs/releases/"))
        assertFalse("docs/architecture.md must NOT cite retired run_system_integration_tests.py",
            content.contains("run_system_integration_tests.py"))
    }
}
