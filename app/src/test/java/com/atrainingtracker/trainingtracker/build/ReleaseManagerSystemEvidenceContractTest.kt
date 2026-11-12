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
 * Release Manager System Verification Evidence Integration Contract Test
 * (ASPICE SYS.4, SYS.5, SUP.8, REQ-PRO-072, TST-PRO-072, ATT-6169).
 *
 * Enforces:
 * 1. .agents/skills/release-manager/SKILL.md mandates SYS.4 and SYS.5 system verification evidence (TST-PRO-072.1).
 * 2. Release checklist and procedures explicitly block release candidate tag creation if system evidence is missing or failing (TST-PRO-072.2).
 * 3. docs/releases/ README.md and TEMPLATE.md exist and specify release evidence dossier layout and sign-off criteria (TST-PRO-072.3).
 * 4. docs/architecture.md Section 6 references release process gating and system evidence (TST-PRO-072.4).
 * 5. Compliance with ATT-6186: zero source-text reading of production files.
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
     * Asserts .agents/skills/release-manager/SKILL.md exists and specifies mandatory system verification gates (TST-PRO-072.1).
     */
    @Test
    fun testReleaseManagerSkill_specifiesMandatorySystemEvidenceGates() {
        val skillFile = findRepoRootFile(".agents/skills/release-manager/SKILL.md")
        assertTrue(".agents/skills/release-manager/SKILL.md must exist (REQ-PRO-072.1)", skillFile.exists())

        val content = skillFile.readText()
        assertTrue("Skill must not be empty", content.isNotBlank())
        assertTrue("Must reference ASPICE SYS.4", content.contains("SYS.4"))
        assertTrue("Must reference ASPICE SYS.5", content.contains("SYS.5"))
        assertTrue("Must reference REQ-PRO-072", content.contains("REQ-PRO-072"))

        // Must mandate both SYS.4 and SYS.5 artifact paths under docs/releases/<tag>/
        assertTrue("Must mandate system_integration_results.json artifact", content.contains("system_integration_results.json"))
        assertTrue("Must mandate sys_verification_record.md artifact", content.contains("sys_verification_record.md"))
        assertTrue("Must specify release evidence directory under docs/releases/", content.contains("docs/releases/"))

        // Must specify execution of automated integration test runner
        assertTrue("Must cite run_system_integration_tests.py execution", content.contains("run_system_integration_tests.py"))
        // Must cite system verification protocol
        assertTrue("Must cite sys_verification_protocol.md", content.contains("sys_verification_protocol.md"))
    }

    /**
     * Asserts release procedure explicitly defines blocking gate on tag creation and Play Store distribution (TST-PRO-072.2).
     */
    @Test
    fun testReleaseManagerSkill_explicitlyBlocksTagCreationOnMissingOrFailingEvidence() {
        val skillFile = findRepoRootFile(".agents/skills/release-manager/SKILL.md")
        val content = skillFile.readText()

        // Verify blocking rules in checklist and detailed procedures
        assertTrue("Must declare tag creation blocked if evidence missing/failing",
            content.contains("BLOCKED") && content.contains("tag"))
        assertTrue("Must specify blocking on Play Store upload",
            content.contains("Play Store") && content.contains("blocked", ignoreCase = true))
        assertTrue("Must check pre-tagging verification in Phase 4",
            content.contains("Pre-Tagging System Evidence Verification") ||
            content.contains("Pre-Tagging Gate Verification"))
    }

    /**
     * Asserts docs/releases/ README.md and TEMPLATE.md exist and define release dossier layout (TST-PRO-072.3).
     */
    @Test
    fun testReleaseEvidenceTemplates_existAndDefineDossierLayout() {
        val readmeFile = findRepoRootFile("docs/releases/README.md")
        assertTrue("docs/releases/README.md must exist (REQ-PRO-072.3)", readmeFile.exists())

        val readmeContent = readmeFile.readText()
        assertTrue("README must reference ASPICE SUP.8", readmeContent.contains("SUP.8"))
        assertTrue("README must reference ASPICE SYS.4", readmeContent.contains("SYS.4"))
        assertTrue("README must reference ASPICE SYS.5", readmeContent.contains("SYS.5"))
        assertTrue("README must document system_integration_results.json", readmeContent.contains("system_integration_results.json"))
        assertTrue("README must document sys_verification_record.md", readmeContent.contains("sys_verification_record.md"))
        assertTrue("README must state blocking enforcement", readmeContent.contains("BLOCKED"))

        val templateFile = findRepoRootFile("docs/releases/TEMPLATE.md")
        assertTrue("docs/releases/TEMPLATE.md must exist (REQ-PRO-072.3)", templateFile.exists())

        val templateContent = templateFile.readText()
        assertTrue("Template must contain release dossier header", templateContent.contains("Release Qualification Dossier"))
        assertTrue("Template must include Gate 3 System Integration Verification (SYS.4)", templateContent.contains("SYS.4"))
        assertTrue("Template must include Gate 4 System Verification Protocol (SYS.5)", templateContent.contains("SYS.5"))
        assertTrue("Template must reference system_integration_results.json", templateContent.contains("system_integration_results.json"))
        assertTrue("Template must reference sys_verification_record.md", templateContent.contains("sys_verification_record.md"))
        assertTrue("Template must include sign-off signatures", templateContent.contains("Sign-Off") || templateContent.contains("Formal Sign-Off"))
    }

    /**
     * Asserts docs/architecture.md Section 6 links release gating to system verification evidence (TST-PRO-072.4).
     */
    @Test
    fun testArchitecture_linksReleaseGatingToSystemEvidence() {
        val archFile = findRepoRootFile("docs/architecture.md")
        assertTrue("docs/architecture.md must exist", archFile.exists())

        val content = archFile.readText()
        assertTrue("docs/architecture.md must have Section 6.4", content.contains("### 6.4"))
        assertTrue("Section 6.4 must reference REQ-PRO-072", content.contains("REQ-PRO-072"))
        assertTrue("Section 6.4 must reference TST-PRO-072", content.contains("TST-PRO-072"))
        assertTrue("Section 6.4 must reference release-manager skill", content.contains("release-manager/SKILL.md"))
        assertTrue("Section 6.4 must reference docs/releases/", content.contains("docs/releases/"))
    }
}
