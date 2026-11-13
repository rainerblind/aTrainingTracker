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

package com.atrainingtracker.trainingtracker.ui.settings.tuning

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural contract test enforcing docs/design_guidelines.md §1 across
 * all category composables under ui/settings/tuning/categories/ (REQ-UI-262.5, REQ-CLM-010.3 / TST-UI-221.6, TST-CLM-010.6, ATT-7539).
 *
 * Verifies that:
 * 1. No category composable uses raw FilterChip for binary or discrete choices (allowlisting only
 *    developer tier overrides in SupporterDebugSection.kt).
 * 2. No category composable uses raw unscaled Switch( instead of TuningToggleItem / SubordinateTuningToggleItem.
 * 3. No category composable nests ElevatedCard( inside accordion sections.
 * 4. Poison pill tests prove the audit engine catches regressions.
 */
class TuningDesignGuidelinesContractTest {

    companion object {
        private const val CATEGORIES_REL_PATH =
            "app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories"

        private val ALLOWLISTED_FILTER_CHIP_FILES = setOf(
            "SupporterDebugSection.kt"
        )
    }

    /**
     * Scans source content for design guideline anti-patterns.
     * Strips comments before evaluation to prevent false positives in docstrings.
     */
    fun auditFileContent(content: String, fileName: String): List<String> {
        val violations = mutableListOf<String>()

        val stripped = content
            .replace(Regex("""//.*"""), "")
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")

        val isFilterChipAllowlisted = ALLOWLISTED_FILTER_CHIP_FILES.contains(fileName)

        if (!isFilterChipAllowlisted) {
            if (stripped.contains("FilterChip(") || stripped.contains("import androidx.compose.material3.FilterChip")) {
                violations.add(
                    "$fileName: Prohibited FilterChip usage. Binary exclusive and 2–4 option mode selectors " +
                            "MUST use TuningSegmentedButtonRow per docs/design_guidelines.md §1.1 & §1.4 (REQ-UI-262.5)."
                )
            }
        }

        if (stripped.contains("Switch(") || stripped.contains("androidx.compose.material3.Switch(")) {
            violations.add(
                "$fileName: Prohibited raw Switch usage. Toggles in tuning categories MUST use " +
                        "TuningToggleItem or SubordinateTuningToggleItem per docs/design_guidelines.md §1.4 (REQ-UI-262.5)."
            )
        }

        if (stripped.contains("ElevatedCard(") || stripped.contains("import androidx.compose.material3.ElevatedCard")) {
            violations.add(
                "$fileName: Prohibited nested ElevatedCard usage. Tuning categories reside in elevated accordion bodies; " +
                        "subsections must use HorizontalDivider and typography headers per docs/design_guidelines.md §1.4 (REQ-UI-262.5)."
            )
        }

        return violations
    }

    @Test
    fun testAllCategoryComposables_complyWithDesignGuidelines() {
        val currentDir = File(System.getProperty("user.dir") ?: ".")
        val rootDir = when {
            File(currentDir, "app/src/main").exists() -> currentDir
            File(currentDir, "src/main").exists() -> currentDir.parentFile ?: currentDir
            else -> File(currentDir, "..")
        }

        val categoriesDir = File(rootDir, CATEGORIES_REL_PATH)
        assertTrue(
            "Categories directory must exist at: ${categoriesDir.absolutePath}",
            categoriesDir.exists() && categoriesDir.isDirectory
        )

        val categoryFiles = categoriesDir.listFiles { file -> file.isFile && file.extension == "kt" }
            ?: emptyArray()

        assertTrue(
            "Expected category Kotlin files in ${categoriesDir.path}, found none",
            categoryFiles.isNotEmpty()
        )

        val allViolations = mutableListOf<String>()
        for (file in categoryFiles) {
            val fileViolations = auditFileContent(file.readText(), file.name)
            allViolations.addAll(fileViolations)
        }

        assertTrue(
            "Design guidelines contract violations detected in tuning categories:\n" +
                    allViolations.joinToString("\n"),
            allViolations.isEmpty()
        )
    }

    // =========================================================================
    // Poison Pill Tests (proving the audit engine detects anti-patterns)
    // =========================================================================

    @Test
    fun testPoisonPill_filterChipViolationDetected() {
        val snippet = """
            package com.atrainingtracker.trainingtracker.ui.settings.tuning.categories
            import androidx.compose.material3.FilterChip
            @Composable
            fun BadSection() {
                FilterChip(selected = true, onClick = {}, label = { Text("A") })
            }
        """.trimIndent()

        val violations = auditFileContent(snippet, "BadSection.kt")
        assertFalse("Poison pill for FilterChip must report violations", violations.isEmpty())
        assertTrue(violations.any { it.contains("Prohibited FilterChip usage") })
    }

    @Test
    fun testPoisonPill_rawSwitchViolationDetected() {
        val snippet = """
            package com.atrainingtracker.trainingtracker.ui.settings.tuning.categories
            @Composable
            fun BadSwitchSection() {
                Switch(checked = true, onCheckedChange = {})
            }
        """.trimIndent()

        val violations = auditFileContent(snippet, "BadSwitchSection.kt")
        assertFalse("Poison pill for raw Switch must report violations", violations.isEmpty())
        assertTrue(violations.any { it.contains("Prohibited raw Switch usage") })
    }

    @Test
    fun testPoisonPill_elevatedCardViolationDetected() {
        val snippet = """
            package com.atrainingtracker.trainingtracker.ui.settings.tuning.categories
            import androidx.compose.material3.ElevatedCard
            @Composable
            fun BadCardSection() {
                ElevatedCard { }
            }
        """.trimIndent()

        val violations = auditFileContent(snippet, "BadCardSection.kt")
        assertFalse("Poison pill for ElevatedCard must report violations", violations.isEmpty())
        assertTrue(violations.any { it.contains("Prohibited nested ElevatedCard usage") })
    }

    @Test
    fun testPoisonPill_allowlistedSupporterDebugSectionPasses() {
        val snippet = """
            package com.atrainingtracker.trainingtracker.ui.settings.tuning.categories
            import androidx.compose.material3.FilterChip
            @Composable
            fun SupporterDebugSection() {
                FilterChip(selected = true, onClick = {}, label = { Text("Tier") })
            }
        """.trimIndent()

        val violations = auditFileContent(snippet, "SupporterDebugSection.kt")
        assertTrue(
            "SupporterDebugSection.kt must be allowlisted for developer tier override chips, but got: $violations",
            violations.isEmpty()
        )
    }
}
