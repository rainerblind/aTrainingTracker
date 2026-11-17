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

package com.atrainingtracker.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Automated Strategy Contract Test for Java Core Services Architecture Decision Record
 * and Prioritized Migration Strategy (ADR-001, REQ-ARC-005, TST-ARC-005, ATT-7566).
 *
 * Validates:
 * 1. ADR existence & structural integrity (TST-ARC-005.1, REQ-ARC-005.1).
 * 2. Class inventory & decision coverage across all 149 Java classes and top 10 anchors (TST-ARC-005.2, REQ-ARC-005.2).
 * 3. Atomic child ticket sizing (1..3 SP) and Epic ATT-431 traceability (TST-ARC-005.3, REQ-ARC-005.5).
 * 4. System architecture document integration in docs/architecture.md (TST-ARC-005.4, REQ-ARC-005.6).
 */
// Linked test specs: TST-ARC-006
class JavaCoreMigrationStrategyContractTest {

    companion object {
        private const val ADR_RELATIVE_PATH = "docs/architecture/adr_001_java_core_services_migration.md"
        private const val ARCHITECTURE_DOC_PATH = "docs/architecture.md"

        val TOP_10_JAVA_ANCHORS = listOf(
            "TrackerService",
            "WorkoutSummariesDatabaseManager",
            "TrackingViewsDatabaseManager",
            "TrainingApplication",
            "DevicesDatabaseManager",
            "DeviceManager",
            "BTLERadarDevice",
            "MyBTLEDevice",
            "WorkoutSamplesDatabaseManager",
            "FitFileWriter"
        )

        val EXPECTED_12_SUBSYSTEMS = listOf(
            "1. Background Tracking Core",
            "2. BLE & Sensor Ingestion Core",
            "3. BLE Peripheral Drivers",
            "4. ANT+ Peripheral Drivers",
            "5. Common Peripheral Devices",
            "6. Leaf Sensor Formatters",
            "7. Pure Mathematical Signal Filters",
            "8. SQLite Database Managers",
            "9. File Exporters",
            "10. Segments & Live Navigation",
            "11. Legacy UI Views",
            "12. Obsolete Smartwatch & 3rd-Party"
        )

        val REGISTERED_CHILD_TICKETS = listOf(
            ChildTicketContract("ATT-7903", 2, 1),
            ChildTicketContract("ATT-7905", 2, 1),
            ChildTicketContract("ATT-7906", 3, 2)
        )
    }

    data class ChildTicketContract(val key: String, val storyPoints: Int, val tier: Int)

    private fun findRepoRootFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
            File("../../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Could not locate $relativePath from working directory: ${File(".").absolutePath}")
    }

    private fun findMainSourceDir(): File {
        val relSubPath = "src" + "/main/java"
        val candidates = listOf(
            File("app", relSubPath),
            File(relSubPath),
            File("../app", relSubPath),
            File("../../app", relSubPath)
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("Could not locate main Java source directory from: ${File(".").absolutePath}")
    }

    @Test
    fun testAdrExistenceAndStructuralIntegrity_TST_ARC_005_1() {
        val adrFile = findRepoRootFile(ADR_RELATIVE_PATH)
        assertTrue("ADR-001 file must exist at $ADR_RELATIVE_PATH (REQ-ARC-005.1)", adrFile.exists() && adrFile.isFile)
        val content = adrFile.readText()
        assertTrue("ADR-001 must not be empty", content.length > 5000)

        // Metadata & Governance (REQ-PRO-120: agent-authored docs default to Reviewed until human sign-off)
        val hasControlledStatus = content.contains("Status**: Reviewed") ||
            content.contains("Status: Reviewed") ||
            content.contains("Status**: Approved") ||
            content.contains("Status: Approved")
        assertTrue("ADR-001 must have controlled Status: Reviewed or Approved", hasControlledStatus)
        assertTrue("ADR-001 must reference REQ-ARC-005", content.contains("REQ-ARC-005"))
        assertTrue("ADR-001 must reference TST-ARC-005", content.contains("TST-ARC-005"))
        assertTrue("ADR-001 must reference parent epic ATT-431", content.contains("ATT-431"))
        assertTrue("ADR-001 must reference ticket ATT-7566", content.contains("ATT-7566"))

        // Mandatory Sections (REQ-ARC-005.1 .. REQ-ARC-005.5)
        assertTrue(
            "ADR must contain Section 1 (Context & Problem Statement)",
            content.contains("## 1. Context & Problem Statement")
        )
        assertTrue("ADR must describe ASPICE finding F9", content.contains("ASPICE evaluation finding F9"))
        assertTrue("ADR must describe platform types nullability hazards", content.contains("Platform Types (`T!`)"))
        assertTrue(
            "ADR must contain Section 2 (Decision Drivers & Evaluated Alternatives)",
            content.contains("## 2. Decision Drivers & Evaluated Alternatives")
        )
        assertTrue("ADR must evaluate Alternative 1 (Big-Bang)", content.contains("Alternative 1: Big-Bang"))
        assertTrue(
            "ADR must evaluate Alternative 2 (Status Quo)",
            content.contains("Alternative 2: Permanent Status Quo")
        )
        assertTrue(
            "ADR must evaluate Alternative 3 (Phased Bottom-Up)",
            content.contains("Alternative 3: Phased, Risk-Tiered Bottom-Up Migration")
        )
        assertTrue(
            "ADR must contain Section 3 (Decision Outcome & Strategy Matrix)",
            content.contains("## 3. Decision Outcome & Strategy Matrix")
        )
        assertTrue(
            "ADR must contain Section 4 (Quantitative Java Inventory)",
            content.contains("## 4. Quantitative Java Inventory")
        )
        assertTrue(
            "ADR must contain Section 5 (Phased Bottom-Up Migration Sequencing)",
            content.contains("## 5. Phased Bottom-Up Migration Sequencing")
        )
        assertTrue(
            "ADR must contain Section 6 (Mandatory Migration Quality Safeguards)",
            content.contains("## 6. Mandatory Migration Quality Safeguards")
        )
        assertTrue(
            "ADR must contain Section 7 (Atomic Backlog Child Tickets)",
            content.contains("## 7. Atomic Backlog Child Tickets")
        )

        // Quality Safeguards check (REQ-ARC-005.4)
        assertTrue("Must mandate Characterisation Tests First", content.contains("Characterisation Tests First"))
        assertTrue("Must mandate Strict Refactoring Invariance", content.contains("Refactoring Invariance"))
        assertTrue("Must mandate Platform Type Nullability Shielding", content.contains("Nullability Shielding"))
        assertTrue("Must mandate Clean-Room Test Pass Rate", content.contains("Clean-Room Test Pass Rate"))
        assertTrue("Must mandate Atomic Ticket Sizing <= 3 SP", content.contains("3 Story Points"))
    }

    @Test
    fun testClassInventoryAndDecisionCoverage_TST_ARC_005_2() {
        val adrFile = findRepoRootFile(ADR_RELATIVE_PATH)
        val content = adrFile.readText()

        // Verify all 10 top anchors are present with explicit decisions (REQ-ARC-005.2)
        TOP_10_JAVA_ANCHORS.forEach { anchor ->
            assertTrue(
                "ADR-001 must explicitly account for top anchor class: `$anchor`",
                content.contains("`$anchor`")
            )
        }

        // Verify BANALService is explicitly cataloged
        assertTrue("ADR-001 must account for BANALService", content.contains("`BANALService`"))

        // Verify 12 architectural subsystems are represented in ADR-001
        EXPECTED_12_SUBSYSTEMS.forEach { subsysPrefix ->
            assertTrue(
                "ADR-001 must contain subsystem section matching prefix: $subsysPrefix",
                content.contains(subsysPrefix)
            )
        }

        // Scan main source directory and verify every .java file is cataloged in ADR-001
        val mainDir = findMainSourceDir()
        val javaFiles = mainDir.walkTopDown().filter { it.isFile && it.name.endsWith(".java") }.toList()
        assertTrue(
            "Expected <= 149 Java classes in main production sources (REQ-ARC-005.1, REQ-ARC-006.3), " +
                "found: ${javaFiles.size}",
            javaFiles.size <= 149
        )

        val missingFromAdr = mutableListOf<String>()
        javaFiles.forEach { file ->
            val className = file.nameWithoutExtension
            if (!content.contains("`$className`")) {
                missingFromAdr.add(className)
            }
        }
        assertTrue(
            "Every Java class in main production sources must be cataloged in ADR-001 inventory table. Missing:\n" +
                missingFromAdr.joinToString(", "),
            missingFromAdr.isEmpty()
        )
    }

    @Test
    fun testChildTicketSizingAndTraceability_TST_ARC_005_3() {
        val adrFile = findRepoRootFile(ADR_RELATIVE_PATH)
        val content = adrFile.readText()

        assertEquals("Expected exactly 3 child tickets registered (REQ-ARC-005.5)", 3, REGISTERED_CHILD_TICKETS.size)

        REGISTERED_CHILD_TICKETS.forEach { ticket ->
            // Key must be mentioned
            assertTrue("ADR-001 must reference child ticket ${ticket.key}", content.contains(ticket.key))

            // Story points must be between 1 and 3 SP
            assertTrue("Child ticket ${ticket.key} must be sized <= 3 SP (REQ-ARC-005.5)", ticket.storyPoints in 1..3)
            assertTrue(
                "ADR-001 must specify ${ticket.storyPoints} SP for ${ticket.key}",
                content.contains("${ticket.storyPoints} SP")
            )

            // Tier must be valid (0..6)
            assertTrue("Tier must be between 0 and 6 (REQ-ARC-005.3)", ticket.tier in 0..6)
            assertTrue("ADR-001 must reference Tier ${ticket.tier}", content.contains("Tier ${ticket.tier}"))
        }
    }

    @Test
    fun testArchitectureDocIntegration_TST_ARC_005_4() {
        val archFile = findRepoRootFile(ARCHITECTURE_DOC_PATH)
        val content = archFile.readText()

        // §2 allocation
        assertTrue(
            "docs/architecture.md §2 must allocate REQ-ARC-005 to Engineering Process & Stability",
            content.contains("REQ-ARC-005")
        )
        assertTrue(
            "docs/architecture.md §2 must link adr_001_java_core_services_migration.md",
            content.contains("adr_001_java_core_services_migration.md")
        )

        // §5.3 referencing ADR-001
        assertTrue(
            "docs/architecture.md must contain Section 5.3 (Java Core Services Migration Strategy)",
            content.contains("### 5.3 Java Core Services Migration Strategy")
        )
        assertTrue(
            "docs/architecture.md §5.3 must reference ADR-001",
            content.contains("ADR-001")
        )
    }
}
