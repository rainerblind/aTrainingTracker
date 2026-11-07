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
 * Validates the Layer 2 Real-World Android Jetpack Compose Reference Showcase,
 * adversarial review case studies (Metabolic Model, DFA alpha-1 HRV, Radar Bytecode),
 * and community launch articles with mandatory epigraph, dual attribution, and etymology invariants
 * (REQ-PRO-037, TST-PRO-037, ATT-5668).
 */
class HoechstmassShowcaseAndCommunityContractTest {

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
    fun testAndroidReferenceShowcase_standardsAndArtifactsExist() {
        val showcaseDir = findRepoRootFile("tools/hoechstmass/examples/android-jetpack-compose")
        assertTrue("Showcase directory must exist", showcaseDir.exists() && showcaseDir.isDirectory)

        val readme = File(showcaseDir, "README.md")
        assertTrue("Showcase README.md must exist", readme.exists() && readme.length() > 200L)

        val codingStandards = File(showcaseDir, "coding_standards.md")
        assertTrue("Showcase coding_standards.md must exist", codingStandards.exists() && codingStandards.length() > 500L)
        val codingText = codingStandards.readText()
        assertTrue("coding_standards must cover compose state hygiene", codingText.contains("Compose State Hygiene") || codingText.contains("State Hoisting"))
        assertTrue("coding_standards must cover canonical formatters or DRY", codingText.contains("Formatters") || codingText.contains("DRY"))

        val designGuidelines = File(showcaseDir, "design_guidelines.md")
        assertTrue("Showcase design_guidelines.md must exist", designGuidelines.exists() && designGuidelines.length() > 500L)
        val designText = designGuidelines.readText()
        assertTrue("design_guidelines must cover SingleChoiceSegmentedButtonRow", designText.contains("SingleChoiceSegmentedButtonRow"))
        assertTrue("design_guidelines must warn against FilterChip for binary choices", designText.contains("FilterChip"))

        val artifactsDir = File(showcaseDir, "artifacts")
        assertTrue("Showcase artifacts directory must exist", artifactsDir.exists() && artifactsDir.isDirectory)

        val expectedArtifacts = listOf(
            "ATT-5111_stage1_analysis.md",
            "ATT-5111_stage2_test_spec.md",
            "ATT-5111_stage3_impl_plan.md",
            "ATT-5111_stage4_impl_summary.md",
            "ATT-5111_stage5_walkthrough.md",
            "sprint_2026-44.1_retrospective.md"
        )

        for (artifactName in expectedArtifacts) {
            val artifactFile = File(artifactsDir, artifactName)
            assertTrue("Artifact $artifactName must exist and not be empty", artifactFile.exists() && artifactFile.length() > 100L)
        }
    }

    @Test
    fun testAdversarialCaseStudies_documentsThreeCoreReviews() {
        val caseStudiesFile = findRepoRootFile("tools/hoechstmass/examples/android-jetpack-compose/CASE_STUDIES.md")
        assertTrue("CASE_STUDIES.md must exist", caseStudiesFile.exists() && caseStudiesFile.length() > 1000L)

        val content = caseStudiesFile.readText()

        // 1. Mader-Heck Metabolic Model (ATT-4786 / ATT-4785)
        assertTrue("Must cover Mader-Heck metabolic model", content.contains("Mader-Heck"))
        assertTrue("Must reference ATT-4786 or ATT-4785", content.contains("ATT-4786") || content.contains("ATT-4785"))
        assertTrue("Must cover equation or simulation drift", content.contains("drift") || content.contains("floating-point"))

        // 2. Beat-to-Beat RR Intervals & DFA alpha-1 HRV (ATT-3539)
        assertTrue("Must cover DFA alpha-1 HRV", content.contains("DFA alpha-1") || content.contains("HRV"))
        assertTrue("Must reference ATT-3539", content.contains("ATT-3539"))
        assertTrue("Must cover memory allocation or buffer invariant", content.contains("buffer") || content.contains("allocation"))

        // 3. Cycling Rearview Radar Bytecode Auditing (ATT-1286 / ATT-5111 / ATT-5117)
        assertTrue("Must cover Radar bytecode audit", content.contains("Radar"))
        assertTrue("Must reference ATT-5111 or ATT-1286", content.contains("ATT-5111") || content.contains("ATT-1286"))
        assertTrue("Must cover javap bytecode verification", content.contains("javap") || content.contains("bytecode"))
        assertTrue("Must cover SDK hallucination defense", content.contains("Hallucination") || content.contains("hallucinat"))
    }

    @Test
    fun testCommunityLaunchArticles_containMandatoryInvariants() {
        val communityDir = findRepoRootFile("tools/hoechstmass/docs/community")
        assertTrue("Community directory must exist", communityDir.exists() && communityDir.isDirectory)

        val expectedArticles = listOf(
            "r_softwareengineering.md",
            "r_androiddev.md",
            "r_google_antigravity.md",
            "r_vibecoding.md",
            "hackernews_show_hn.md"
        )

        val epigraphQuote = "Power without control can become destructive. Power guided by intelligence becomes excellence."

        for (articleName in expectedArticles) {
            val articleFile = File(communityDir, articleName)
            assertTrue("Community article $articleName must exist", articleFile.exists() && articleFile.length() > 500L)

            val text = articleFile.readText()

            // 1. Opening Epigraph Quote
            assertTrue(
                "Article $articleName must feature the exact epigraph quote",
                text.contains(epigraphQuote)
            )

            // 2. Direct Attribution to ChatGPT
            assertTrue(
                "Article $articleName must explicitly attribute epigraph to ChatGPT",
                text.contains("ChatGPT")
            )

            // 3. Dual Attribution Rationale
            assertTrue(
                "Article $articleName must document Radical Attribution or Human/AI Separation",
                text.contains("Radical Attribution") || text.contains("Human/AI Separation")
            )
            assertTrue(
                "Article $articleName must document Cross-Model or Cross-Vendor review",
                text.contains("Cross-Model") || text.contains("Cross-Vendor")
            )

            // 4. German Etymology Callout Box
            assertTrue(
                "Article $articleName must explain Höchst·maß with correct orthography",
                text.contains("Höchst·maß") || text.contains("Höchstmaß")
            )
            assertTrue(
                "Article $articleName must include IPA pronunciation",
                text.contains("/ˈhøːçstˌmaːs/")
            )
            assertTrue(
                "Article $articleName must explain Höchst- prefix and -maß suffix",
                text.contains("Höchst-") && text.contains("-maß")
            )
        }
    }
}
