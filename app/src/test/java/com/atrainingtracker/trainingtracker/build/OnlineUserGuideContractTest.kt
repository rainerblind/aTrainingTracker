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
 * Validates the online User Guide infrastructure, chapter parity, image assets,
 * and Jekyll exclusion hygiene on GitHub Pages (REQ-PRO-033, TST-PRO-033, ATT-2275, ATT-5418).
 */
class OnlineUserGuideContractTest {

    private fun findRepoRootFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
            File("../../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Could not locate $relativePath in working directory: ${File(".").absolutePath}")
    }

    private val expectedChapterFiles = listOf(
        "01-quick-start.md",
        "02-sensors-dual-protocol.md",
        "03-equipment-sensor-matrix.md",
        "04-live-cockpit-tabs.md",
        "05-maps-routes-climbs-segments.md",
        "06-automatic-route-clustering.md",
        "07-physiology-training-zones.md",
        "08-workout-aftermath-periods.md",
        "09-export-cloud-sync.md"
    )

    @Test
    fun testJekyllConfig_excludesInternalDeveloperDocs() {
        val configFile = findRepoRootFile("docs/_config.yml")
        val content = configFile.readText()

        val mandatoryExclusions = listOf(
            "ci-secrets.md",
            "project_protocol.md",
            "coding_standards.md",
            "design_guidelines.md",
            "engineering/",
            "requirements/",
            "tests/"
        )

        for (exclusion in mandatoryExclusions) {
            assertTrue(
                "docs/_config.yml must exclude internal developer path: '$exclusion'",
                content.contains(exclusion)
            )
        }
    }

    @Test
    fun testLandingPage_existsAndLinksToBothLanguages() {
        val indexFile = findRepoRootFile("docs/index.md")
        val content = indexFile.readText()

        assertTrue("Landing page must link to German guide", content.contains("guide/de/"))
        assertTrue("Landing page must link to English guide", content.contains("guide/en/"))
        assertTrue("Landing page must reference Quick Start chapter", content.contains("01-quick-start.md"))
        assertTrue("Landing page must reference Export chapter", content.contains("09-export-cloud-sync.md"))
    }

    @Test
    fun testChapterFiles_existForBothLocales() {
        val deDir = findRepoRootFile("docs/guide/de")
        val enDir = findRepoRootFile("docs/guide/en")

        assertTrue("docs/guide/de/index.md must exist", File(deDir, "index.md").exists())
        assertTrue("docs/guide/en/index.md must exist", File(enDir, "index.md").exists())

        for (chapter in expectedChapterFiles) {
            val deFile = File(deDir, chapter)
            val enFile = File(enDir, chapter)

            assertTrue("Missing German chapter: ${deFile.path}", deFile.exists() && deFile.length() > 100)
            assertTrue("Missing English chapter: ${enFile.path}", enFile.exists() && enFile.length() > 100)
        }
    }

    @Test
    fun testImageReferences_resolveToExistingAssets() {
        val guideDir = findRepoRootFile("docs/guide")
        val markdownRegex = Regex("""!\[.*?\]\((.*?)\)""")
        val htmlImgRegex = Regex("""<img\s+[^>]*src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)

        val markdownFiles = guideDir.walkTopDown().filter { it.extension == "md" }.toList()
        assertTrue("Expected markdown files in docs/guide/", markdownFiles.isNotEmpty())

        var imageReferencesChecked = 0
        for (mdFile in markdownFiles) {
            val content = mdFile.readText()
            val mdMatches = markdownRegex.findAll(content).map { it.groupValues[1] }
            val htmlMatches = htmlImgRegex.findAll(content).map { it.groupValues[1] }
            val allMatches = (mdMatches + htmlMatches).toList()

            for (imagePath in allMatches) {
                val resolvedFile = File(mdFile.parentFile, imagePath).canonicalFile

                assertTrue(
                    "Image referenced in ${mdFile.name} ($imagePath) does not exist: ${resolvedFile.path}",
                    resolvedFile.exists() && resolvedFile.length() > 0
                )
                imageReferencesChecked++
            }
        }

        assertTrue(
            "Expected at least 20 verified image references across user guide chapters, found $imageReferencesChecked",
            imageReferencesChecked >= 20
        )
    }

    @Test
    fun testAnchorParity_betweenGermanAndEnglishChapters() {
        val deDir = findRepoRootFile("docs/guide/de")
        val enDir = findRepoRootFile("docs/guide/en")
        val anchorRegex = Regex("""<a id="(.*?)"></a>""")

        for (chapter in expectedChapterFiles) {
            val deContent = File(deDir, chapter).readText()
            val enContent = File(enDir, chapter).readText()

            val deAnchors = anchorRegex.findAll(deContent).map { it.groupValues[1] }.toSet()
            val enAnchors = anchorRegex.findAll(enContent).map { it.groupValues[1] }.toSet()

            assertTrue(
                "Chapter $chapter should define at least 2 section anchors (found DE: ${deAnchors.size})",
                deAnchors.size >= 2
            )
            assertEquals(
                "Section anchors in $chapter must match 1:1 between DE and EN for stable URL linking",
                deAnchors,
                enAnchors
            )
        }
    }

    @Test
    fun testGermanGuide_usesInformalDuPronouns() {
        val deDir = findRepoRootFile("docs/guide/de")
        val formalPronounsRegex = Regex("""\b(Sie|Ihnen|Ihre|Ihrem|Ihren|Ihres)\b""")

        val germanMarkdownFiles = deDir.walkTopDown().filter { it.extension == "md" }.toList()
        assertTrue("Expected German markdown files in docs/guide/de/", germanMarkdownFiles.isNotEmpty())

        val violations = mutableListOf<String>()
        for (file in germanMarkdownFiles) {
            val lines = file.readLines()
            for ((index, line) in lines.withIndex()) {
                val match = formalPronounsRegex.find(line)
                if (match != null) {
                    violations.add("${file.name}:${index + 1}: found formal '${match.value}' in \"${line.trim()}\"")
                }
            }
        }

        assertTrue(
            "German user guide must consistently use informal 'Du' address. Violations found:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun testGuide_forbidsHallucinatedFeatures() {
        val guideDir = findRepoRootFile("docs/guide")
        val forbiddenTerms = listOf(
            "TrainingPeaks",
            "ACTIVITY_RECOGNITION",
            "Sprachansagen",
            "Text-to-Speech",
            "text-to-speech",
            "1x, 2x, 3x",
            "1x/2x/3x",
            "Eigengewicht des Fahrrads",
            "bike tare weight"
        )

        val markdownFiles = guideDir.walkTopDown().filter { it.extension == "md" }.toList()
        val violations = mutableListOf<String>()

        for (file in markdownFiles) {
            val content = file.readText()
            for (term in forbiddenTerms) {
                if (content.contains(term, ignoreCase = true)) {
                    violations.add("${file.name} contains forbidden hallucinated feature: '$term'")
                }
            }
        }

        assertTrue(
            "User guide must not contain hallucinated features. Violations:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun testImageAssets_fileSizesWithinLimit() {
        val imgDir = findRepoRootFile("docs/guide/img")
        val images = imgDir.listFiles { file -> file.isFile && (file.extension == "png" || file.extension == "jpg") }
            ?: emptyArray()

        assertTrue("Expected image assets in docs/guide/img", images.isNotEmpty())

        val oversizedImages = images.filter { it.length() > 1_000_000 }
        assertTrue(
            "Image assets must be under 1 MB to prevent layout bloating. Oversized: ${oversizedImages.map { "${it.name}: ${it.length()} bytes" }}",
            oversizedImages.isEmpty()
        )
    }
}
