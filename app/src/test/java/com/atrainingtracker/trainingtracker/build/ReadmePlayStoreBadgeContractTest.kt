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
 * Validates root README.md Google Play Store badge integration, official vector asset
 * resilience, link integrity, screenshot showcase, trademark-free terminology, and Online
 * User Guide navigation (REQ-STP-005, TST-STP-005, ATT-2371, ATT-6096).
 */
class ReadmePlayStoreBadgeContractTest {

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
    fun testGooglePlayBadgeAsset_existsAndHasValidSvgStructure() {
        val badgeFile = findRepoRootFile("images/google-play-badge.svg")
        assertTrue("Badge asset must exist", badgeFile.exists())
        assertTrue("Badge asset must have non-zero size", badgeFile.length() > 500L)

        val content = badgeFile.readText()
        assertTrue("Badge must be an SVG file", content.contains("<svg") && content.contains("</svg>"))
        assertTrue("Badge must contain Google Play branding text", content.contains("Google Play"))
        assertTrue("Badge must contain GET IT ON call-to-action", content.contains("GET IT ON"))
        assertTrue("Badge must contain official color definitions", content.contains("cyanGrad") && content.contains("greenGrad"))
    }

    @Test
    fun testReadme_embedsClickablePlayStoreBadgeWithCanonicalUrl() {
        val readmeFile = findRepoRootFile("README.md")
        val content = readmeFile.readText()

        // Verify badge image reference
        assertTrue(
            "README.md must reference images/google-play-badge.svg",
            content.contains("images/google-play-badge.svg")
        )

        // Verify canonical Google Play Store URL
        val canonicalUrl = "https://play.google.com/store/apps/details?id=com.atrainingtracker"
        assertTrue(
            "README.md must link directly to canonical store URL: $canonicalUrl",
            content.contains(canonicalUrl)
        )

        // Explicitly assert absence of broken .trainingtracker suffix
        assertFalse(
            "README.md must NOT link to broken package ID com.atrainingtracker.trainingtracker",
            content.contains("com.atrainingtracker.trainingtracker")
        )
    }

    @Test
    fun testReadme_embedsAppLogoAndResolvesOnDisk() {
        val readmeFile = findRepoRootFile("README.md")
        val content = readmeFile.readText()

        assertTrue(
            "README.md must reference images/logo_512.png",
            content.contains("images/logo_512.png")
        )

        val logoFile = findRepoRootFile("images/logo_512.png")
        assertTrue("Logo file images/logo_512.png must exist on disk", logoFile.exists())
        assertTrue("Logo file images/logo_512.png must not be empty", logoFile.length() > 1000L)
    }

    @Test
    fun testReadme_containsValidUserGuideNavigationLinks() {
        val readmeFile = findRepoRootFile("README.md")
        val content = readmeFile.readText()

        val requiredDocLinks = listOf(
            "docs/index.md",
            "docs/guide/de/index.md",
            "docs/guide/en/index.md"
        )

        for (docPath in requiredDocLinks) {
            assertTrue(
                "README.md must link to user guide document: $docPath",
                content.contains(docPath)
            )
            val docFile = findRepoRootFile(docPath)
            assertTrue("Referenced doc file $docPath must exist on disk", docFile.exists())
        }
    }

    @Test
    fun testReadme_embedsScreenshotShowcaseResolvingOnDisk() {
        val readmeFile = findRepoRootFile("README.md")
        val content = readmeFile.readText()

        val expectedScreenshots = listOf(
            "docs/guide/img/01_cockpit_standard.png",
            "docs/guide/img/08_workout_detail.png",
            "docs/guide/img/06_cluster_expanded.png",
            "docs/guide/img/07_telemetry_charts.png"
        )

        // Verify that README embeds all 4 screenshots
        for (shotPath in expectedScreenshots) {
            assertTrue(
                "README.md must reference screenshot asset: $shotPath",
                content.contains(shotPath)
            )
            val shotFile = findRepoRootFile(shotPath)
            assertTrue("Screenshot file $shotPath must exist on disk", shotFile.exists())
            assertTrue("Screenshot file $shotPath must not be empty", shotFile.length() > 5000L)

            // Verify valid PNG header (magic bytes: 0x89, 'P', 'N', 'G')
            val bytes = shotFile.inputStream().use { stream ->
                val buffer = ByteArray(4)
                stream.read(buffer)
                buffer
            }
            assertTrue(
                "Screenshot $shotPath must be a valid PNG file",
                bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                    bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
            )
        }
    }

    @Test
    fun testReadme_containsZeroPeakswareTrademarksAndAccurateFeatures() {
        val filesToCheck = listOfNotNull(
            try { findRepoRootFile("README.md") } catch (e: Exception) { null },
            try { findRepoRootFile(".reviews/README.md") } catch (e: Exception) { null }
        )

        assertTrue("At least README.md must exist", filesToCheck.isNotEmpty())

        val forbiddenPeakswareTrademarks = listOf(
            "Normalized Power",
            "Intensity Factor",
            "Training Stress Score",
            "Performance Management Chart"
        )

        val forbiddenRegexes = listOf(
            Regex("""\bNP\b"""),
            Regex("""\bIF\b"""),
            Regex("""\bTSS\b"""),
            Regex("""\bPMC\b"""),
            Regex("""\bCTL\b"""),
            Regex("""\bATL\b"""),
            Regex("""\bTSB\b"""),
            Regex("""\b\.gc\b"""),
            Regex("""\bGolden Cheetah formats\b""", RegexOption.IGNORE_CASE),
            Regex("""\bWKO\+\b""")
        )

        val mandatoryFeatureTokens = listOf(
            "Weighted Power",
            "WP",
            "Relative Intensity",
            "RI",
            "Training Load",
            "VI",
            "Fitness 42d",
            "Fatigue 7d",
            "Form",
            "0x1818",
            "Pedal Power Balance",
            "Strava Bulk Archive (.zip)",
            "ZIP Backup & Restore",
            ".attbackup",
            "Lieblingsstrecken",
            "Live Segments",
            "Climb Detection",
            "Lieblingsorte",
            "Intervals.icu",
            "GoldenCheetah"
        )

        for (file in filesToCheck) {
            val content = file.readText()

            // 1. Assert zero occurrences of forbidden trademarks and removed legacy terms
            for (trademark in forbiddenPeakswareTrademarks) {
                assertFalse(
                    "${file.name} must NOT contain Peaksware trademark: '$trademark'",
                    content.contains(trademark, ignoreCase = true)
                )
            }

            for (pattern in forbiddenRegexes) {
                assertFalse(
                    "${file.name} must NOT contain forbidden token matching pattern: '${pattern.pattern}'",
                    pattern.containsMatchIn(content)
                )
            }

            // 2. Assert positive presence of modern capability tokens
            for (featureToken in mandatoryFeatureTokens) {
                assertTrue(
                    "${file.name} must contain required modern capability token: '$featureToken'",
                    content.contains(featureToken)
                )
            }
        }
    }
}
