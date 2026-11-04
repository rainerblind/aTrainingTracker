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
 * resilience, link integrity, and Online User Guide navigation (REQ-STP-005, TST-STP-005, ATT-2371).
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
}
