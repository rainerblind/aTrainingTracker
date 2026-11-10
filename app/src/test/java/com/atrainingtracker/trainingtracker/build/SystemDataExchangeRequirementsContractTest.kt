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

import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.migration.BackupManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * System Requirements Specification Contract Test for Data Exchange and Cloud Integration
 * (ASPICE SYS.2 BP1-BP3, REQ-PRO-055, TST-PRO-055, ATT-6161).
 *
 * Enforces:
 * 1. docs/system/sysreq_data_exchange.md exists and contains SYSREQ-DEX-001..SYSREQ-DEX-006 (TST-PRO-055.1).
 * 2. Every system requirement defines Statement, Rationale, Priority, Verification Method, and Traced Software Components.
 * 3. 100% code grounding parity against FileFormat enum and BackupManager constants (TST-PRO-055.2).
 * 4. Zero source-text reading of production files complying with ATT-6186.
 */
class SystemDataExchangeRequirementsContractTest {

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
     * Asserts docs/system/sysreq_data_exchange.md exists and contains all 6 data exchange requirements (TST-PRO-055.1).
     */
    @Test
    fun testSysreqDataExchange_fileExistsAndContainsAllRequirements() {
        val sysreqFile = findRepoRootFile("docs/system/sysreq_data_exchange.md")
        assertTrue("docs/system/sysreq_data_exchange.md must exist (REQ-PRO-055.1)", sysreqFile.exists())

        val content = sysreqFile.readText()
        assertTrue("docs/system/sysreq_data_exchange.md must not be empty", content.isNotBlank())
        assertTrue("Must declare ASPICE SYS.2 BP1-BP3 process scope", content.contains("SYS.2"))

        val expectedRequirements = listOf(
            "SYSREQ-DEX-001",
            "SYSREQ-DEX-002",
            "SYSREQ-DEX-003",
            "SYSREQ-DEX-004",
            "SYSREQ-DEX-005",
            "SYSREQ-DEX-006"
        )

        for (req in expectedRequirements) {
            assertTrue("Document must contain requirement $req", content.contains("### $req:"))
        }
    }

    /**
     * Asserts that all requirements specify mandatory attributes per ASPICE SYS.2 BP1-BP3 (TST-PRO-055.1).
     */
    @Test
    fun testSysreqDataExchange_allRequirementsHaveMandatorySections() {
        val content = findRepoRootFile("docs/system/sysreq_data_exchange.md").readText()

        val expectedRequirements = listOf(
            "SYSREQ-DEX-001",
            "SYSREQ-DEX-002",
            "SYSREQ-DEX-003",
            "SYSREQ-DEX-004",
            "SYSREQ-DEX-005",
            "SYSREQ-DEX-006"
        )

        for (req in expectedRequirements) {
            val sectionStart = content.indexOf("### $req:")
            assertTrue("Requirement $req header not found", sectionStart >= 0)

            val sectionEnd = content.indexOf("### SYSREQ-DEX-", sectionStart + 1).let {
                if (it == -1) content.indexOf("## 3.", sectionStart) else it
            }.let {
                if (it == -1) content.length else it
            }

            val reqSection = content.substring(sectionStart, sectionEnd)

            assertTrue("$req must have a Title", reqSection.contains("* **Title**:"))
            assertTrue("$req must have a Statement", reqSection.contains("* **Statement**:"))
            assertTrue("$req Statement must use SHALL or MUST", reqSection.contains("SHALL") || reqSection.contains("MUST"))
            assertTrue("$req must have Rationale", reqSection.contains("* **Rationale**:"))
            assertTrue("$req must have Priority", reqSection.contains("* **Priority**:"))
            assertTrue("$req must have Verification Method", reqSection.contains("* **Verification Method**:"))
            assertTrue("$req must have Traced Software Components", reqSection.contains("* **Traced Software Components**:"))
        }
    }

    /**
     * Asserts code grounding parity between docs/system/sysreq_data_exchange.md and production constants (TST-PRO-055.2).
     */
    @Test
    fun testSysreqDataExchange_codeGroundingParity() {
        val content = findRepoRootFile("docs/system/sysreq_data_exchange.md").readText()

        // 1. Export format grounding against FileFormat enum
        assertEquals(".fit", FileFormat.FIT.fileEnding)
        assertEquals(".tcx", FileFormat.TCX.fileEnding)
        assertEquals(".gpx", FileFormat.GPX.fileEnding)
        assertEquals(".csv", FileFormat.CSV.fileEnding)

        assertTrue("Must document FIT format", content.contains("FIT") && content.contains(".fit"))
        assertTrue("Must document TCX format", content.contains("TCX") && content.contains(".tcx"))
        assertTrue("Must document GPX format", content.contains("GPX") && content.contains(".gpx"))
        assertTrue("Must document CSV format", content.contains("CSV") && content.contains(".csv"))

        // 2. Full application state backup filename grounding
        assertEquals("aTrainingTracker_backup.attbackup", BackupManager.BACKUP_FILENAME)
        assertTrue("Must document canonical backup filename", content.contains(BackupManager.BACKUP_FILENAME))
        assertTrue("Must document 1.5x free space requirement", content.contains("1.5x"))
        assertTrue("Must document Zip Slip protection via SecureZipExtractor", content.contains("SecureZipExtractor"))

        // 3. Cloud synchronization services grounding
        assertTrue("Must document Strava OAuth2 activity synchronization", content.contains("Strava"))
        assertTrue("Must document Dropbox API v2 backup sync", content.contains("Dropbox") && content.contains("/Backups/"))
        assertTrue("Must document Google Drive REST API v3 sync", content.contains("Google Drive"))

        // 4. Security, privacy, and decommissioned platform boundaries
        assertTrue("Must document auth_credentials_prefs exclusion", content.contains("auth_credentials_prefs.xml"))
        assertTrue("Must document Health Connect consent", content.contains("Health Connect"))
        assertTrue("Must document Runkeeper as decommissioned", content.contains("Runkeeper"))
        assertTrue("Must document TrainingPeaks as decommissioned", content.contains("TrainingPeaks"))
    }
}
