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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Build contract test verifying ASPICE SWE.1/2/4/5/6 audit hardening (REQ-PRO-034, TST-PRO-034, REQ-PRO-043, TST-PRO-043, REQ-PRO-044, TST-PRO-044, REQ-PRO-045, TST-PRO-045, ATT-5431, ATT-6179, ATT-6180, ATT-6183):
 * 1. docs/architecture.md completeness (layers, 9-domain shard allocation, 3 Mermaid dynamic models, invariants).
 * 2. Authentic component nomenclature code-grounding & absence of drifted placeholders (REQ-PRO-043).
 * 3. Software interface specifications (SWE.2.BP2) for IF-SWE-01 through IF-SWE-08 (REQ-PRO-044).
 * 4. Status header standardization across all 9 requirement and test domain shards.
 * 5. Master-to-shard table count parity in docs/requirements.md and docs/tests.md.
 * 6. Skill & rule governance contracts (universal read_file image audit, doc code-grounding, SWE.2 maintenance).
 * 7. Software integration verification strategy (SWE.5.BP1) for INT-SWE-01 through INT-SWE-08 (REQ-PRO-045).
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
                        val isStandard = trimmed.endsWith("[Verified]") || trimmed.endsWith("[Planned]") || trimmed.endsWith("[Implemented]")
                        assertTrue(
                            "Heading in ${file.name} must end with standard status [Verified], [Planned], or [Implemented]: '$trimmed'",
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

            val isFeatureBranch = try {
                val p = ProcessBuilder("git", "status", "--porcelain", "-b").start()
                val header = p.inputStream.bufferedReader().readLine() ?: ""
                !header.contains("sprint/") && !header.contains("develop") && !header.contains("master")
            } catch (e: Exception) {
                false
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
                    if (isFeatureBranch) {
                        // Under parallel sprint execution (REQ-PRO-041.4), living documentation master
                        // tables are synchronized under lock at Stage 5 squash-merge. On feature branches,
                        // shards may contain in-progress Planned/Verified additions (expected >= table).
                        assertTrue(
                            "Verified in shard $shardName (${expected!!.second}) cannot be less than master table ($verified)",
                            expected.second >= verified
                        )
                        assertTrue(
                            "Total in shard $shardName (${expected.first}) cannot be less than master table ($total)",
                            expected.first >= total
                        )
                    } else {
                        assertEquals("Total mismatch in ${masterFile.name} for $shardName", expected!!.first, total)
                        assertEquals("Verified mismatch in ${masterFile.name} for $shardName", expected.second, verified)
                        assertEquals("Planned mismatch in ${masterFile.name} for $shardName", expected.third, planned)
                    }

                    grandTotal += total
                    checkedShards++
                }
            }

            assertEquals("All shards must be present in master table", shardCounts.size, checkedShards)
            if (!isFeatureBranch) {
                val actualSum = shardCounts.values.sumOf { it.first }
                assertEquals("Grand total must match sum of shards", actualSum, grandTotal)
            }
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

    /**
     * Asserts that docs/architecture.md references authentic production components and contains no drifted placeholders (REQ-PRO-043, TST-PRO-043).
     */
    @Test
    fun testArchitectureDocument_authenticComponentNomenclature() {
        val archFile = findRepoRootFile("docs/architecture.md")
        val content = archFile.readText()
        val interfacesContent = findRepoRootFile("docs/architecture/interfaces.md").readText()

        // TST-PRO-043.1: Authentic class presence
        val authenticComponents = listOf(
            "WorkoutClusterEngine",
            "GPXFileWriter",
            "TCXFileWriter",
            "WorkoutSamplesDatabaseManager",
            "MilestoneAutoNamingManager",
            "FirstJubileeActionHelper",
            "SettingsDataStore",
            "EquipmentDbHelper",
            "EquipmentRepository",
            "ProFeatureManager",
            "SupporterTier",
            "GarageLeagueDetector",
            "ATrainingTrackerTheme",
            "TelemetryMetricGraph",
            "PathPoint",
            "PlayStoreListingContractTest"
        )
        for (comp in authenticComponents) {
            val foundInDocs = content.contains(comp) || interfacesContent.contains(comp)
            assertTrue("Architecture docs must reference authentic component '$comp' (REQ-PRO-043.1)", foundInDocs)
        }

        // TST-PRO-043.2: Drifted placeholder absence (all 14 drifted tokens)
        val driftedComponents = listOf(
            "RouteLearningEngine",
            "GpxExporter",
            "TcxExporter",
            "WorkoutSamplesDb",
            "MilestoneDetector",
            "InAppReviewManager",
            "UserPreferences",
            "EquipmentDatabaseManager",
            "PrivacyZoneManager",
            "BillingManager",
            "TTTheme",
            "CustomLineChart",
            "ElevationPoint",
            "StoreMetadataContractTest"
        )
        for (drift in driftedComponents) {
            assertFalse("docs/architecture.md must not reference drifted placeholder '$drift' (REQ-PRO-043.1)", content.contains(drift))
            assertFalse("docs/architecture/interfaces.md must not reference drifted placeholder '$drift' (REQ-PRO-043.1)", interfacesContent.contains(drift))
        }

        // TST-PRO-043.3: Tracking state machine & dispatcher conformance
        assertTrue("Must model TrackingMode IDLE state", content.contains("IDLE"))
        assertTrue("Must model TrackingMode READY state", content.contains("READY"))
        assertTrue("Must model TrackingMode TRACKING state", content.contains("TRACKING"))
        assertTrue("Must model TrackingMode PAUSED state", content.contains("PAUSED"))
        assertTrue("Must document AutoPauseState sub-state delegation", content.contains("AutoPauseState"))
        assertTrue("Must document dbDispatcher", content.contains("dbDispatcher"))
        assertTrue("Must document Dispatchers.IO", content.contains("Dispatchers.IO"))
    }

    /**
     * Dynamically verifies that every software component allocated in docs/architecture.md and specified in
     * docs/architecture/interfaces.md actually exists in production source code, tools, or test suite (REQ-PRO-043.5, TST-PRO-043.4).
     */
    @Test
    fun testArchitectureDocuments_allAllocatedAndSpecifiedComponentsExistInCodebase() {
        val archFile = findRepoRootFile("docs/architecture.md")
        val archText = archFile.readText()
        val interfacesFile = findRepoRootFile("docs/architecture/interfaces.md")
        val interfacesText = interfacesFile.readText()

        // 1. Extract allocated components from docs/architecture.md Section 2 table
        val section2TableRegex = Regex("""## 2\. Requirement Allocation to Architecture.*?\| :--- \| :--- \| :--- \| :--- \|(.*?)\n---""", RegexOption.DOT_MATCHES_ALL)
        val tableMatch = section2TableRegex.find(archText)
            ?: throw IllegalStateException("Failed to locate Section 2 Requirement Allocation table in docs/architecture.md")
        val tableBody = tableMatch.groupValues[1]
        val backtickRegex = Regex("""`([^`]+)`""")

        val allocatedComponents = mutableSetOf<String>()
        tableBody.lines().map { it.trim() }.filter { it.startsWith("|") }.forEach { row ->
            val cols = row.split("|").map { it.trim() }.filter { it.isNotEmpty() }
            if (cols.size >= 3) {
                val compCol = cols[2]
                backtickRegex.findAll(compCol).forEach { m ->
                    allocatedComponents.add(m.groupValues[1])
                }
            }
        }
        assertTrue("Must extract allocated components from Section 2 table", allocatedComponents.size >= 50)

        // 2. Extract types from interfaces.md Provider, Consumer, and Data Payloads lines
        val interfaceTypes = mutableSetOf<String>()
        val typePattern = Regex("""`([A-Z][a-zA-Z0-9_]+)`""")
        interfacesText.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("* **Provider**:") ||
                trimmed.startsWith("* **Consumer**:") ||
                trimmed.startsWith("* **Data Payloads / Schemas**:")
            ) {
                typePattern.findAll(trimmed).forEach { match ->
                    interfaceTypes.add(match.groupValues[1])
                }
            }
        }

        // 3. Framework / SDK / Standard allowlist (not declared within project repo)
        val frameworkAllowlist = setOf(
            "Context", "File", "Location", "LocationListener", "BluetoothGattCallback",
            "AntPluginPcc", "IPluginAccessResultReceiver", "OutputStream", "Writer",
            "HealthConnectClient", "ExerciseSessionRecord", "ContentValues", "StateFlow",
            "Flow", "Intent", "Result", "Double", "Long", "Int", "Boolean", "Float",
            "String", "List", "Map", "Set", "Pair", "Unit", "WorkManager", "NavHost",
            "LatLng", "ProGuard", "OkHttpClient"
        )

        // 4. Index codebase files and declared symbols
        val srcMainDir = findRepoRootFile("app/src/main")
        val srcTestDir = findRepoRootFile("app/src/test")
        val toolsDir = findRepoRootFile("tools")

        val mainSources = srcMainDir.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.toList()
        val testSources = srcTestDir.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.toList()
        val toolFiles = toolsDir.listFiles { f -> f.isFile }?.toList() ?: emptyList()

        val allBasenames = (mainSources + testSources + toolFiles).map { it.name }.toSet()
        val allNamesNoExt = (mainSources + testSources + toolFiles).map { it.nameWithoutExtension }.toSet()

        val allSourceTexts = mainSources.map { it.readText() }

        val componentsToValidate = (allocatedComponents + interfaceTypes).filterNot { frameworkAllowlist.contains(it) }.toSet()
        assertTrue("Must have project components to validate", componentsToValidate.isNotEmpty())

        val ungroundedComponents = mutableListOf<String>()
        for (comp in componentsToValidate) {
            if (allBasenames.contains(comp) || allNamesNoExt.contains(comp)) {
                continue
            }
            // Check if declared as class, interface, enum class, object, typealias, or fun in main source files
            val declRegex = Regex("""\b(class|interface|enum\s+class|enum|object|typealias|fun)\s+$comp\b""")
            val isDeclared = allSourceTexts.any { declRegex.containsMatchIn(it) }
            if (!isDeclared) {
                ungroundedComponents.add(comp)
            }
        }

        assertTrue(
            "All software components referenced in docs/architecture.md and docs/architecture/interfaces.md must exist in the codebase. Ungrounded: $ungroundedComponents",
            ungroundedComponents.isEmpty()
        )
    }

    /**
     * Asserts that software interfaces are formally specified in accordance with ASPICE SWE.2.BP2 (REQ-PRO-044, TST-PRO-044).
     */
    @Test
    fun testArchitectureDocument_softwareInterfaceSpecifications() {
        val interfacesFile = findRepoRootFile("docs/architecture/interfaces.md")
        assertTrue("docs/architecture/interfaces.md must exist (REQ-PRO-044.1)", interfacesFile.exists())
        val interfacesContent = interfacesFile.readText()

        val archFile = findRepoRootFile("docs/architecture.md")
        val archContent = archFile.readText()

        val interfaceIds = (1..8).map { String.format("IF-SWE-%02d", it) }

        // TST-PRO-044.1: All 8 interface specifications present in interfaces.md
        for (ifId in interfaceIds) {
            assertTrue("interfaces.md must specify interface $ifId (TST-PRO-044.1)", interfacesContent.contains(ifId))
        }

        // TST-PRO-044.2: Mandatory interface attributes present in interfaces.md
        val mandatoryAttributes = listOf(
            "Provider",
            "Consumer",
            "Operations",
            "Threading",
            "Error Handling"
        )
        for (attr in mandatoryAttributes) {
            assertTrue("interfaces.md must document attribute '$attr' (TST-PRO-044.2)", interfacesContent.contains(attr))
        }

        // TST-PRO-044.3: Architecture document Section 5 integration & matrix
        assertTrue("architecture.md must contain Section 5 for software interfaces (TST-PRO-044.3)", archContent.contains("5. Software Interface Specifications"))
        assertTrue("architecture.md must link to interfaces.md (TST-PRO-044.3)", archContent.contains("interfaces.md"))
        for (ifId in interfaceIds) {
            assertTrue("architecture.md Section 5 must reference $ifId in the matrix (TST-PRO-044.3)", archContent.contains(ifId))
        }
    }

    /**
     * Asserts that all documented interface methods, classes, and DTOs in interfaces.md are 100% grounded in real production code (REQ-PRO-044.3, TST-PRO-044.4).
     */
    @Test
    fun testArchitectureDocument_softwareInterfaceGrounding() {
        val interfacesFile = findRepoRootFile("docs/architecture/interfaces.md")
        val interfacesContent = interfacesFile.readText()
        val archFile = findRepoRootFile("docs/architecture.md")
        val archContent = archFile.readText()

        // 1. Negative tripwires: No Retrofit, no hallucinated classes/DTOs
        assertFalse("interfaces.md must not reference Retrofit (REQ-PRO-044)", interfacesContent.contains("Retrofit"))
        assertFalse("architecture.md must not reference Retrofit (REQ-PRO-044)", archContent.contains("Retrofit"))
        val forbiddenTokens = listOf(
            "ClustersDatabaseManager",
            "TrackingUiState",
            "AftermathUiState",
            "LocationUiState",
            "ExtremaValues",
            "WorkoutDetailScreen"
        )
        for (token in forbiddenTokens) {
            assertFalse("interfaces.md must not contain hallucinated symbol '$token'", interfacesContent.contains(token))
        }
        assertFalse(
            "interfaces.md must not contain standalone AftermathViewModel (use TrackOnMapAftermathViewModel)",
            Regex("""\bAftermathViewModel\b""").containsMatchIn(interfacesContent)
        )
        assertFalse(
            "architecture.md must not contain standalone AftermathViewModel (use TrackOnMapAftermathViewModel)",
            Regex("""\bAftermathViewModel\b""").containsMatchIn(archContent)
        )

        // 2. Index all source files in app/src/main
        val srcMainDir = findRepoRootFile("app/src/main")
        val sourceFiles = srcMainDir.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.toList()
        assertTrue("app/src/main must contain production source files", sourceFiles.isNotEmpty())
        val allSources = sourceFiles.map { it.readText() }

        // 3. Dynamic Method Extraction: fun <name>(
        val methodPattern = Regex("""\bfun\s+([a-zA-Z0-9_]+)\s*\(""")
        val extractedMethods = methodPattern.findAll(interfacesContent).map { it.groupValues[1] }.toSet()
        assertTrue("Must extract documented interface methods from interfaces.md", extractedMethods.isNotEmpty())

        val ungroundedMethods = mutableListOf<String>()
        for (methodName in extractedMethods) {
            val methodRegex = Regex("""\b(fun\s+$methodName\s*[\(<]|(public|protected|private|static)\s+[\w<>,\s\[\]]+\s+$methodName\s*\()""")
            val found = allSources.any { methodRegex.containsMatchIn(it) }
            if (!found) {
                ungroundedMethods.add(methodName)
            }
        }
        assertTrue(
            "All documented interface methods must exist in app/src/main/. Ungrounded: $ungroundedMethods",
            ungroundedMethods.isEmpty()
        )

        // 4. Dynamic Component/DTO Type Extraction from Provider, Consumer, and Data Payloads lines
        val typePattern = Regex("""`([A-Z][a-zA-Z0-9_]+)`""")
        val frameworkAllowlist = setOf(
            "Context", "File", "Location", "LocationListener", "BluetoothGattCallback",
            "AntPluginPcc", "IPluginAccessResultReceiver", "OutputStream", "Writer",
            "HealthConnectClient", "ExerciseSessionRecord", "ContentValues", "StateFlow",
            "Flow", "Intent", "Result", "Double", "Long", "Int", "Boolean", "Float",
            "String", "List", "Map", "Set", "Pair", "Unit"
        )

        val extractedTypes = mutableSetOf<String>()
        interfacesContent.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("* **Provider**:") ||
                trimmed.startsWith("* **Consumer**:") ||
                trimmed.startsWith("* **Data Payloads / Schemas**:")
            ) {
                typePattern.findAll(trimmed).forEach { match ->
                    val typeName = match.groupValues[1]
                    if (!frameworkAllowlist.contains(typeName)) {
                        extractedTypes.add(typeName)
                    }
                }
            }
        }
        assertTrue("Must extract component and DTO types from interfaces.md", extractedTypes.isNotEmpty())

        val ungroundedTypes = mutableListOf<String>()
        for (typeName in extractedTypes) {
            val typeRegex = Regex("""\b(class|interface|enum\s+class|object|typealias|fun)\s+$typeName\b""")
            val found = allSources.any { typeRegex.containsMatchIn(it) }
            if (!found) {
                ungroundedTypes.add(typeName)
            }
        }
        assertTrue(
            "All documented component and DTO types must exist in app/src/main/. Ungrounded: $ungroundedTypes",
            ungroundedTypes.isEmpty()
        )
    }

    /**
     * Asserts that the software integration verification strategy is formally specified in accordance with ASPICE SWE.5.BP1 (REQ-PRO-045, TST-PRO-045).
     */
    @Test
    fun testArchitectureDocument_softwareIntegrationStrategy() {
        val strategyFile = findRepoRootFile("docs/architecture/integration_strategy.md")
        assertTrue("docs/architecture/integration_strategy.md must exist (REQ-PRO-045.1, TST-PRO-045.1)", strategyFile.exists())
        val strategyContent = strategyFile.readText()

        val archFile = findRepoRootFile("docs/architecture.md")
        val archContent = archFile.readText()

        // TST-PRO-045.1: Core sections completeness
        val requiredSections = listOf(
            "Integration Sequence",
            "Verification Levels",
            "Entry and Exit Criteria",
            "Regression Selection"
        )
        for (section in requiredSections) {
            assertTrue("integration_strategy.md must document section '$section' (TST-PRO-045.1)", strategyContent.contains(section))
        }

        // TST-PRO-045.2: Complete interface seam grounding (IF-SWE-01..08 and INT-SWE-01..08)
        for (i in 1..8) {
            val ifId = String.format("IF-SWE-%02d", i)
            val intId = String.format("INT-SWE-%02d", i)
            assertTrue("integration_strategy.md must reference interface $ifId (TST-PRO-045.2)", strategyContent.contains(ifId))
            assertTrue("integration_strategy.md must reference integration step $intId (TST-PRO-045.2)", strategyContent.contains(intId))
        }

        // TST-PRO-045.3: Architecture document Section 6 integration & matrix
        assertTrue("architecture.md must contain Section 6 for integration strategy (TST-PRO-045.3)", archContent.contains("6. Software Integration and Verification Strategy"))
        assertTrue("architecture.md must link to integration_strategy.md (TST-PRO-045.3)", archContent.contains("integration_strategy.md"))
        for (i in 1..8) {
            val ifId = String.format("IF-SWE-%02d", i)
            val intId = String.format("INT-SWE-%02d", i)
            assertTrue("architecture.md Section 6 must reference $ifId in integration sequence (TST-PRO-045.3)", archContent.contains(ifId))
            assertTrue("architecture.md Section 6 must reference $intId in integration sequence (TST-PRO-045.3)", archContent.contains(intId))
        }
    }
}
