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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Build contract test verifying ASPICE SWE.1/2/4/6 audit hardening (REQ-PRO-034, TST-PRO-034, ATT-5431):
 * 1. docs/architecture.md completeness (layers, 9-domain shard allocation, 3 Mermaid dynamic models, invariants).
 * 2. Status header standardization across all 9 requirement and test domain shards.
 * 3. Master-to-shard table count parity in docs/requirements.md and docs/tests.md.
 * 4. Skill & rule governance contracts (universal read_file image audit, doc code-grounding, SWE.2 maintenance).
 */
class AspiceSweAuditContractTest {

    private fun findRepoRootFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
            File("../../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Could not locate $relativePath in working directory: ${File(".").absolutePath}")
    }

    @Test
    fun testArchitectureDocument_fulfillsAspiceSwe2() {
        val archFile = findRepoRootFile("docs/architecture.md")
        val content = archFile.readText()

        // BP.1 & BP.2: Layers & Subsystems
        assertTrue("Must specify Presentation Layer", content.contains("Presentation Layer"))
        assertTrue("Must specify Domain Layer", content.contains("Domain & Scientific Calculators"))
        assertTrue("Must specify Background Services", content.contains("Background Services"))
        assertTrue("Must specify Persistence Layer", content.contains("Persistence & Cloud Layer"))

        // BP.3: Requirement Allocation across all 9 shards
        val requiredShards = listOf(
            "Tracking & Live Sensor Cockpit",
            "Post-Workout Analytics & Periods",
            "Maps, Polylines & Navigation",
            "Persistence, Migration & Cloud Export",
            "Settings, Profiles & Localization",
            "UI Components & Design System",
            "Engineering Process & Stability",
            "Jubilee Milestones & App Reviews",
            "Project Governance & Store Presence"
        )
        for (shard in requiredShards) {
            assertTrue("Allocation table must reference shard '$shard'", content.contains(shard))
        }

        // BP.4: Dynamic Behavior (3 Mermaid models)
        assertTrue("Must include State Diagram for tracking", content.contains("stateDiagram-v2"))
        val sequenceCount = Regex("sequenceDiagram").findAll(content).count()
        assertTrue("Must include at least 2 sequence diagrams (Sensor failover & Post-workout)", sequenceCount >= 2)

        // BP.5: Concurrency, Threading & DB Invariants
        assertTrue("Must document dbDispatcher", content.contains("dbDispatcher"))
        assertTrue("Must document Dispatchers.Main and Dispatchers.IO", content.contains("Dispatchers.Main") && content.contains("Dispatchers.IO"))
        assertTrue("Must document foreground service types", content.contains("location|health"))
        assertTrue("Must document SQLite schema migration invariants", content.contains("onUpgrade"))
    }

    @Test
    fun testLivingDocShards_allHeadingsStandardized() {
        val reqDir = findRepoRootFile("docs/requirements")
        val testDir = findRepoRootFile("docs/tests")

        fun checkHeadings(dir: File, prefix: String) {
            val shardFiles = dir.listFiles { _, name -> name.endsWith(".md") } ?: emptyArray()
            assertTrue("Shard directory must contain markdown files", shardFiles.isNotEmpty())

            for (file in shardFiles) {
                file.forEachLine { line ->
                    val trimmed = line.trim()
                    if (Regex("^#{2,4}\\s+$prefix-[A-Z0-9_-]+:").containsMatchIn(trimmed)) {
                        val isStandard = trimmed.endsWith("[Verified]") || trimmed.endsWith("[Planned]")
                        assertTrue(
                            "Heading in ${file.name} must end with standard status [Verified] or [Planned]: '$trimmed'",
                            isStandard
                        )
                    }
                }
            }
        }

        checkHeadings(reqDir, "REQ")
        checkHeadings(testDir, "TST")
    }

    @Test
    fun testMasterTableParity_matchesDomainShards() {
        val requirementsMaster = findRepoRootFile("docs/requirements.md")
        val testsMaster = findRepoRootFile("docs/tests.md")
        val reqDir = findRepoRootFile("docs/requirements")
        val testDir = findRepoRootFile("docs/tests")

        fun verifyParity(masterFile: File, shardDir: File, prefix: String) {
            val shardCounts = mutableMapOf<String, Triple<Int, Int, Int>>()
            val shardFiles = shardDir.listFiles { _, name -> name.endsWith(".md") } ?: emptyArray()

            for (file in shardFiles) {
                var tot = 0
                var ver = 0
                var plan = 0
                file.forEachLine { line ->
                    if (Regex("^#{2,4}\\s+$prefix-[A-Z0-9_-]+:").containsMatchIn(line.trim())) {
                        tot++
                        if (line.contains("[Verified]")) ver++ else plan++
                    }
                }
                shardCounts[file.name] = Triple(tot, ver, plan)
            }

            var grandTotal = 0
            var checkedShards = 0
            val lineRegex = Regex("\\((?:requirements|tests)/((?:req|tst)_[^)]+\\.md)\\).*?\\|\\s*(\\d+)\\s*\\|\\s*(\\d+)\\s*\\|\\s*(\\d+)\\s*\\|")

            masterFile.forEachLine { line ->
                val match = lineRegex.find(line)
                if (match != null) {
                    val shardName = match.groupValues[1]
                    val total = match.groupValues[2].toInt()
                    val verified = match.groupValues[3].toInt()
                    val planned = match.groupValues[4].toInt()

                    val expected = shardCounts[shardName]
                    assertTrue("Master table references missing shard $shardName", expected != null)
                    assertEquals("Total mismatch in ${masterFile.name} for $shardName", expected!!.first, total)
                    assertEquals("Verified mismatch in ${masterFile.name} for $shardName", expected.second, verified)
                    assertEquals("Planned mismatch in ${masterFile.name} for $shardName", expected.third, planned)

                    grandTotal += total
                    checkedShards++
                }
            }

            assertEquals("All shards must be present in master table", shardCounts.size, checkedShards)
            val actualSum = shardCounts.values.sumOf { it.first }
            assertEquals("Grand total must match sum of shards", actualSum, grandTotal)
        }

        verifyParity(requirementsMaster, reqDir, "REQ")
        verifyParity(testsMaster, testDir, "TST")
    }

    @Test
    fun testGovernanceRulesAndSkills_containSweAndImageContracts() {
        val rulesFile = findRepoRootFile(".agents/rules/aspice_governance.md")
        val rulesText = rulesFile.readText()

        assertTrue("Rules must mandate universal read_file image inspection", rulesText.contains("read_file"))
        assertTrue("Rules must mandate documentation code-grounding", rulesText.contains("Documentation Code-Grounding") || rulesText.contains("code-grounding"))
        assertTrue("Rules must mandate continuous SWE.2 architecture maintenance", rulesText.contains("Architecture Maintenance") && rulesText.contains("SWE.2"))

        val stage1 = findRepoRootFile(".agents/skills/stage1-analysis/SKILL.md").readText()
        assertTrue("Stage 1 must include documentation code-grounding", stage1.contains("Documentation Code-Grounding"))

        val stage3 = findRepoRootFile(".agents/skills/stage3-impl-plan/SKILL.md").readText()
        assertTrue("Stage 3 must include continuous SWE.2 maintenance", stage3.contains("Continuous SWE.2 Maintenance"))

        val stage4 = findRepoRootFile(".agents/skills/stage4-implementation/SKILL.md").readText()
        assertTrue("Stage 4 must include universal read_file image audit", stage4.contains("Universal `read_file` Image Inspection"))

        val stage5 = findRepoRootFile(".agents/skills/stage5-verification/SKILL.md").readText()
        assertTrue("Stage 5 must include SWE.2 architecture parity", stage5.contains("SWE.2 Architecture Parity"))
    }
}
