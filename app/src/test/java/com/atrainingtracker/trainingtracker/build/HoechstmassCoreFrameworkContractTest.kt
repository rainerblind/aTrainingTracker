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
 * Validates the standalone "Höchstmaß — The Agile Agentic ASPICE (AAA) Skill Set" export scaffold,
 * branded README, project protocol, Swiss Cheese defect reduction model, generalized Document Triad,
 * 10 skills, and deliverable starter templates (REQ-PRO-035, TST-PRO-035, ATT-3922).
 */
class HoechstmassCoreFrameworkContractTest {

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
    fun testCoreFrameworkDocumentation_existsAndContainsMandatorySections() {
        val readme = findRepoRootFile("tools/hoechstmass/README.md")
        assertTrue("tools/hoechstmass/README.md must exist", readme.exists())
        assertTrue("tools/hoechstmass/README.md must have content", readme.length() > 500L)

        val protocol = findRepoRootFile("tools/hoechstmass/docs/project_protocol.md")
        assertTrue("tools/hoechstmass/docs/project_protocol.md must exist", protocol.exists())
        assertTrue("tools/hoechstmass/docs/project_protocol.md must have content", protocol.length() > 500L)

        val readmeContent = readme.readText()
        val protocolContent = protocol.readText()

        // 0. Primary Title & Subtitle
        assertTrue("README must have the title '# Höchstmaß — The AAA Coding Framework'",
            readmeContent.contains("# Höchstmaß — The AAA Coding Framework"))
        assertTrue("README must have subtitle referencing Autonomous AI Pairs",
            readmeContent.contains("Agile · Agentic · ASPICE — Precision Software Engineering with Autonomous AI Pairs"))

        // 1. Opening Epigraph Quote
        val epigraph = "Power without control can become destructive. Power guided by intelligence becomes excellence."
        assertTrue("README must feature the ChatGPT epigraph quote", readmeContent.contains(epigraph))
        assertTrue("README must explicitly attribute epigraph to ChatGPT", readmeContent.contains("ChatGPT"))
        assertTrue("Protocol must feature the ChatGPT epigraph quote", protocolContent.contains(epigraph))

        // 2. Etymology Callout Box
        assertTrue("README must contain etymology for Höchst·maß", readmeContent.contains("Höchst·maß"))
        assertTrue("README must contain phonetic pronunciation /ˈhøːçstˌmaːs/", readmeContent.contains("/ˈhøːçstˌmaːs/"))
        assertTrue("README must explain Höchst- prefix", readmeContent.contains("Höchst-"))
        assertTrue("README must explain -maß / Maßarbeit", readmeContent.contains("-maß") && readmeContent.contains("Maßarbeit"))
        assertTrue("README must preserve German special characters ö and ß", readmeContent.contains("ö") && readmeContent.contains("ß"))

        // 3. Inside-Out AAA Paradigm & Provenance
        assertTrue("README must present the AAA Paradigm from the inside out",
            readmeContent.contains("The AAA Paradigm: Where Agile, Agentic, and ASPICE Meet") &&
            readmeContent.contains("inside out"))
        assertTrue("README Section 1 must describe ASPICE, Agentic, and Agile concentric layers",
            readmeContent.contains("ASPICE (The Core Task Structure") &&
            readmeContent.contains("Agentic (The Execution Loop") &&
            readmeContent.contains("Agile (The Outer Cadence"))
        assertTrue("README must define transparent attribution for agent1, agent2, coordinator, human",
            readmeContent.contains("agent1") && readmeContent.contains("agent2") &&
            readmeContent.contains("coordinator") && readmeContent.contains("human"))
        assertTrue("README must discuss cross-model adversarial review breaking shared tunnel vision",
            readmeContent.contains("Cross-Model Adversarial Review") || readmeContent.contains("cross-model"))
        assertTrue("README must document Gemini and ChatGPT UI screenshot review example",
            readmeContent.contains("Gemini") && readmeContent.contains("ChatGPT") && readmeContent.contains("screenshot"))

        // 4. Swiss Cheese Multiplicative Defect Reduction Model
        assertTrue("README must formalize Swiss Cheese model", readmeContent.contains("Swiss Cheese"))
        assertTrue("README must include mathematical formula for defect probability product",
            readmeContent.contains("P(\\text{escaped defect})") || readmeContent.contains("p_1 \\cdot p_2"))
        assertTrue("README must embed Swiss Cheese SVG asset",
            readmeContent.contains("assets/swiss_cheese_layers.svg"))
        assertTrue("Swiss Cheese SVG asset must exist in tools/hoechstmass/assets/",
            findRepoRootFile("tools/hoechstmass/assets/swiss_cheese_layers.svg").exists())

        // 5 Layers & Decorrelation
        assertTrue("README must list Layer 1 (Executing Agent)", readmeContent.contains("Layer 1"))
        assertTrue("README must list Layer 2 (Reviewing Agent)", readmeContent.contains("Layer 2"))
        assertTrue("README must list Layer 3 (Ticket Verification)", readmeContent.contains("Layer 3"))
        assertTrue("README must list Layer 4 (Human Engineer)", readmeContent.contains("Layer 4"))
        assertTrue("README must list Layer 5 (Cross-Vendor Audits)", readmeContent.contains("Layer 5"))
        assertTrue("README must mention Claude", readmeContent.contains("Claude"))
        assertTrue("README must include 5-layer decorrelation product",
            readmeContent.contains("p_1 \\cdot p_2 \\cdot p_3 \\cdot p_4 \\cdot p_5"))

        // 5. 5-Stage Agile ASPICE Lifecycle
        assertTrue("README must document Stage 1 through Stage 5",
            readmeContent.contains("Stage 1") && readmeContent.contains("Stage 2") &&
            readmeContent.contains("Stage 3") && readmeContent.contains("Stage 4") &&
            readmeContent.contains("Stage 5"))

        // 6. Isolated In-Place Review Workflow (.reviews/) in Project Protocol
        assertTrue("Protocol must document .reviews/ in-place review workflow with Emacs",
            protocolContent.contains(".reviews/") && protocolContent.contains("Emacs"))

        // 7. Entry Barrier Chapter
        assertTrue("README must contain Chapter 5 Entry Barrier & Human Discipline",
            readmeContent.contains("Entry Barrier") && readmeContent.contains("Human Discipline"))
    }

    @Test
    fun testIsolatedReviewWorkflow_assetsAndHelperExist() {
        val rootReviewTool = findRepoRootFile("tools/review_tool.py")
        assertTrue("tools/review_tool.py must exist", rootReviewTool.exists())
        assertTrue("tools/review_tool.py must have content", rootReviewTool.length() > 500L)

        val hoechstmassReviewTool = findRepoRootFile("tools/hoechstmass/tools/review_tool.py")
        assertTrue("tools/hoechstmass/tools/review_tool.py must exist", hoechstmassReviewTool.exists())
        assertTrue("tools/hoechstmass/tools/review_tool.py must have content", hoechstmassReviewTool.length() > 500L)

        val svgAsset = findRepoRootFile("tools/hoechstmass/assets/swiss_cheese_layers.svg")
        assertTrue("tools/hoechstmass/assets/swiss_cheese_layers.svg must exist", svgAsset.exists())
        val svgContent = svgAsset.readText()
        assertTrue("SVG must define 5 layers", svgContent.contains("Layer 1") && svgContent.contains("Layer 5"))
        assertTrue("SVG must reference Claude & ChatGPT", svgContent.contains("Claude") && svgContent.contains("ChatGPT"))
        assertTrue("SVG must define 2-column layout with barrier plates L1..L5",
            svgContent.contains("L1") && svgContent.contains("L2") && svgContent.contains("L3") &&
            svgContent.contains("L4") && svgContent.contains("L5"))
        assertTrue("SVG must include blocked defect markers ✕", svgContent.contains("✕"))
    }

    @Test
    fun testGeneralizedDocumentTriadTemplates_existAndAreProjectAgnostic() {
        val rulesFile = findRepoRootFile("tools/hoechstmass/.agents/rules/aspice_governance.md")
        assertTrue("aspice_governance.md must exist", rulesFile.exists())
        val rulesContent = rulesFile.readText()

        // Must define universal 8 Core Rules
        for (i in 1..8) {
            assertTrue("aspice_governance.md must define Rule $i", rulesContent.contains("Rule $i"))
        }
        assertFalse("aspice_governance.md must not contain hardcoded aTrainingTracker specific package",
            rulesContent.contains("com.atrainingtracker"))

        val codingStandards = findRepoRootFile("tools/hoechstmass/docs/coding_standards.md")
        assertTrue("coding_standards.md must exist", codingStandards.exists())
        val codingContent = codingStandards.readText()
        assertTrue("coding_standards.md must contain §1.1 Reuse Before Create", codingContent.contains("Reuse Before Create"))
        assertTrue("coding_standards.md must contain §1.2 Chesterton's Fence for Empty Callbacks", codingContent.contains("Chesterton's Fence for Empty Callback"))
        assertTrue("coding_standards.md must contain §1.3 Prohibition of Sorting on Formatted Strings", codingContent.contains("Sorting and Ranking on Formatted Display Strings"))
        assertTrue("coding_standards.md must contain §1.4 Explicit Confirmation for Destructive Ops", codingContent.contains("Confirmation for Destructive"))
        assertTrue("coding_standards.md must contain §1.5 Reactive Stream Subscription", codingContent.contains("Reactive Stream Subscription Over Static"))

        val designGuidelines = findRepoRootFile("tools/hoechstmass/docs/design_guidelines.md")
        assertTrue("design_guidelines.md must exist", designGuidelines.exists())
        val designContent = designGuidelines.readText()
        assertTrue("design_guidelines.md must contain §1.1 Segmented Buttons", designContent.contains("Segmented Button"))
        assertTrue("design_guidelines.md must contain §1.2 Filter Chips with Other on far right", designContent.contains("Filter Chips") && designContent.contains("far right"))
        assertTrue("design_guidelines.md must contain §1.3 Large Option Sets Dropdowns", designContent.contains("Dropdown Menu"))
        assertTrue("design_guidelines.md must contain §1.4 Discrete Sliders", designContent.contains("Discrete Slider"))
        assertTrue("design_guidelines.md must contain §2.1 Dialog Action Hierarchy", designContent.contains("Dialog Action Hierarchy"))
    }

    @Test
    fun testGeneralizedSkillsAndDeliverableTemplates_existAndAreComplete() {
        val expectedSkills = listOf(
            "stage1-analysis",
            "stage2-req-test-spec",
            "stage3-impl-plan",
            "stage4-implementation",
            "stage5-verification",
            "sprint-planner",
            "jira-workflow",
            "regression-bisect",
            "brainstormer",
            "release-manager"
        )

        for (skillName in expectedSkills) {
            val skillFile = findRepoRootFile("tools/hoechstmass/.agents/skills/$skillName/SKILL.md")
            assertTrue("Skill $skillName/SKILL.md must exist", skillFile.exists())
            assertTrue("Skill $skillName/SKILL.md must not be empty", skillFile.length() > 200L)
            val content = skillFile.readText()
            assertTrue("Skill $skillName must declare its name in frontmatter", content.contains("name: $skillName"))
        }

        val expectedTemplates = listOf(
            "analysis_template.md",
            "test_spec_template.md",
            "plan_template.md",
            "walkthrough_template.md",
            "backlog_ticket_template.md",
            "sprint_retro_template.md"
        )

        for (templateName in expectedTemplates) {
            val templateFile = findRepoRootFile("tools/hoechstmass/templates/$templateName")
            assertTrue("Template $templateName must exist", templateFile.exists())
            assertTrue("Template $templateName must not be empty", templateFile.length() > 100L)
        }
    }

    @Test
    fun testAspiceCapabilityLevelMatrix_presentInReadmeAndProtocol() {
        val targets = listOf(
            "tools/hoechstmass/README.md",
            "tools/hoechstmass/docs/project_protocol.md",
            "docs/project_protocol.md"
        )
        val expectedPAs = listOf(
            "PA 1.1", "PA 2.1", "PA 2.2",
            "PA 3.1", "PA 3.2",
            "PA 4.1", "PA 4.2",
            "PA 5.1", "PA 5.2"
        )
        val expectedLevels = listOf(
            "Level 1: Performed",
            "Level 2: Managed",
            "Level 3: Established",
            "Level 4: Predictable",
            "Level 5: Innovating"
        )

        for (targetRel in targets) {
            val file = findRepoRootFile(targetRel)
            assertTrue("$targetRel must exist", file.exists())
            val text = file.readText()
            for (pa in expectedPAs) {
                assertTrue("$targetRel must contain $pa", text.contains(pa))
            }
            for (lvl in expectedLevels) {
                assertTrue("$targetRel must contain $lvl", text.contains(lvl))
            }
        }
    }

    @Test
    fun testSprintPlannerSkillAndRetroTemplate_containScorecardAndSpcCorrectiveTrigger() {
        val skillTargets = listOf(
            ".agents/skills/sprint-planner/SKILL.md",
            "tools/hoechstmass/.agents/skills/sprint-planner/SKILL.md"
        )
        for (st in skillTargets) {
            val f = findRepoRootFile(st)
            assertTrue("$st must exist", f.exists())
            val text = f.readText()
            assertTrue("$st must mandate sprint-metrics CLI execution",
                text.contains("sprint-metrics --record --post-to-retro"))
            assertTrue("$st must include PA 4.2 -> PA 5.2 closed-loop trigger",
                text.contains("PA 4.2") && text.contains("PA 5.2"))
        }

        val retroTemplate = findRepoRootFile("tools/hoechstmass/templates/sprint_retro_template.md")
        assertTrue("sprint_retro_template.md must exist", retroTemplate.exists())
        val retroText = retroTemplate.readText()
        assertTrue("sprint_retro_template.md must contain PA 4.1 & PA 4.2 section",
            retroText.contains("Quantitative Sprint Process Metrics (PA 4.1 & PA 4.2)"))
        assertTrue("sprint_retro_template.md must contain FTPR table",
            retroText.contains("FTPR"))
        assertTrue("sprint_retro_template.md must contain Swiss Cheese model",
            retroText.contains("Swiss Cheese"))
        assertTrue("sprint_retro_template.md must contain SPC control limits",
            retroText.contains("Statistical Process Control"))
        assertTrue("sprint_retro_template.md must contain PA 5.2 corrective action trigger",
            retroText.contains("PA 5.2"))
    }

    @Test
    fun testScaffoldDirectoriesForSubsequentParts_exist() {
        val toolsDir = findRepoRootFile("tools/hoechstmass/tools")
        assertTrue("tools/hoechstmass/tools directory must exist for Part 2", toolsDir.exists() && toolsDir.isDirectory)

        val examplesDir = findRepoRootFile("tools/hoechstmass/examples/android-jetpack-compose")
        assertTrue("tools/hoechstmass/examples/android-jetpack-compose directory must exist for Part 3", examplesDir.exists() && examplesDir.isDirectory)

        val communityDir = findRepoRootFile("tools/hoechstmass/docs/community")
        assertTrue("tools/hoechstmass/docs/community directory must exist for Part 3", communityDir.exists() && communityDir.isDirectory)
    }
}
