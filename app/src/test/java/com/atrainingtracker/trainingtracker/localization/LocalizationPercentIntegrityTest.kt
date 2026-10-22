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

package com.atrainingtracker.trainingtracker.localization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Automated repository-wide percent symbol integrity contract test (REQ-UI-418, TST-UI-381.2, TST-UI-381.3).
 *
 * Verifies that:
 * 1. No unformatted string resource contains "%%" without having active positional/typed format specifiers.
 * 2. The 5 Efficiency and W'bal keys have 100% 9-locale parity with single "%" and exact translations.
 */
class LocalizationPercentIntegrityTest {

    private val locales = listOf("", "-de", "-es", "-fr", "-it", "-ja", "-nl", "-pl", "-pt")
    private val resXmlFiles = listOf("strings.xml", "strings_devices.xml", "strings_filters.xml")

    // Matches standard Java/Android format specifiers excluding literal %% escape:
    // e.g. %s, %d, %1$d, %1$.1f, %2$.0f, %tT, etc.
    private val formatSpecifierPattern = Pattern.compile("%(\\d+\\\$)?([-#+ 0,(\\<]*)?(\\d+)?(\\.\\d+)?([tT])?([a-zA-Z])")

    private fun findResDir(): File {
        val candidates = listOf(
            File("app/src/main/res"),
            File("src/main/res"),
            File("../app/src/main/res")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("res directory not found in candidates: $candidates")
    }

    private fun parseStringResources(file: File): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()
        val doc = dBuilder.parse(file)
        doc.documentElement.normalize()

        val stringNodes = doc.getElementsByTagName("string")
        for (i in 0 until stringNodes.length) {
            val node = stringNodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val elem = node as Element
                val name = elem.getAttribute("name")
                result[name] = elem.textContent
            }
        }
        return result
    }

    /**
     * TST-UI-381.2 (REQ-UI-418.3, REQ-UI-418.4):
     * Scans every string resource across all 9 locales.
     * Any string containing "%%" MUST contain at least one format specifier token (%s, %d, %f, etc.).
     * Unformatted strings must never contain "%%".
     */
    @Test
    fun testNonFormattedStrings_doNotContainDoublePercent() {
        val resDir = findResDir()
        val violations = mutableListOf<String>()

        for (suffix in locales) {
            val dirName = if (suffix.isEmpty()) "values" else "values$suffix"
            val dir = File(resDir, dirName)
            if (!dir.exists() || !dir.isDirectory) continue

            for (xmlName in resXmlFiles) {
                val file = File(dir, xmlName)
                if (!file.exists()) continue

                val strings = parseStringResources(file)
                for ((key, text) in strings) {
                    if (text.contains("%%")) {
                        val hasFormatSpecifier = formatSpecifierPattern.matcher(text).find()
                        if (!hasFormatSpecifier) {
                            violations.add("[$dirName/$xmlName] Key '$key' contains '%%' but has no format specifiers: \"$text\"")
                        }
                    }
                }
            }
        }

        assertTrue(
            "Found ${violations.size} unformatted strings containing '%%':\n" + violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    /**
     * TST-UI-381.3 (REQ-UI-418.1, REQ-UI-418.2):
     * Validates 100% 9-language translation parity and single percent symbol for the 5 target keys:
     * - workout_decoupling_coupled
     * - workout_decoupling_drift
     * - workout_w_prime_min_pct_label
     * - workout_w_prime_empty_tank
     * - w_prime_warning_low_battery
     */
    @Test
    fun testTargetKeysParityAndSinglePercent() {
        val resDir = findResDir()

        val expectedTranslations = mapOf(
            "values" to mapOf(
                "workout_decoupling_coupled" to "Coupled (<5%)",
                "workout_decoupling_drift" to "High Drift (≥5%)",
                "workout_w_prime_min_pct_label" to "Min %",
                "workout_w_prime_empty_tank" to "Empty Tank (<10%)",
                "w_prime_warning_low_battery" to "W\\' Battery Low (<20%)"
            ),
            "values-de" to mapOf(
                "workout_decoupling_coupled" to "Stabil (<5%)",
                "workout_decoupling_drift" to "Hohe Drift (≥5%)",
                "workout_w_prime_min_pct_label" to "Min. %",
                "workout_w_prime_empty_tank" to "Tank leer (<10%)",
                "w_prime_warning_low_battery" to "W\\'-Akku schwach (<20%)"
            ),
            "values-es" to mapOf(
                "workout_decoupling_coupled" to "Acoplado (<5%)",
                "workout_decoupling_drift" to "Deriva alta (≥5%)",
                "workout_w_prime_min_pct_label" to "Mín. %",
                "workout_w_prime_empty_tank" to "Tanque vacío (<10%)",
                "w_prime_warning_low_battery" to "Batería W\\' baja (<20%)"
            ),
            "values-fr" to mapOf(
                "workout_decoupling_coupled" to "Couplé (<5%)",
                "workout_decoupling_drift" to "Dérive élevée (≥5%)",
                "workout_w_prime_min_pct_label" to "Min. %",
                "workout_w_prime_empty_tank" to "Réservoir vide (<10%)",
                "w_prime_warning_low_battery" to "Batterie W\\' faible (<20%)"
            ),
            "values-it" to mapOf(
                "workout_decoupling_coupled" to "Accoppiato (<5%)",
                "workout_decoupling_drift" to "Deriva elevata (≥5%)",
                "workout_w_prime_min_pct_label" to "Min. %",
                "workout_w_prime_empty_tank" to "Serbatoio vuoto (<10%)",
                "w_prime_warning_low_battery" to "Batteria W\\' scarica (<20%)"
            ),
            "values-ja" to mapOf(
                "workout_decoupling_coupled" to "安定 (<5%)",
                "workout_decoupling_drift" to "高ドリフト (≥5%)",
                "workout_w_prime_min_pct_label" to "最小 %",
                "workout_w_prime_empty_tank" to "タンク空 (<10%)",
                "w_prime_warning_low_battery" to "W\\'バッテリー残量低下 (<20%)"
            ),
            "values-nl" to mapOf(
                "workout_decoupling_coupled" to "Gekoppeld (<5%)",
                "workout_decoupling_drift" to "Hoge drift (≥5%)",
                "workout_w_prime_min_pct_label" to "Min. %",
                "workout_w_prime_empty_tank" to "Tank leeg (<10%)",
                "w_prime_warning_low_battery" to "W\\'-accu bijna leeg (<20%)"
            ),
            "values-pl" to mapOf(
                "workout_decoupling_coupled" to "Stabilne (<5%)",
                "workout_decoupling_drift" to "Wysoki dryf (≥5%)",
                "workout_w_prime_min_pct_label" to "Min. %",
                "workout_w_prime_empty_tank" to "Pusty bak (<10%)",
                "w_prime_warning_low_battery" to "Niski poziom baterii W\\' (<20%)"
            ),
            "values-pt" to mapOf(
                "workout_decoupling_coupled" to "Acoplado (<5%)",
                "workout_decoupling_drift" to "Deriva alta (≥5%)",
                "workout_w_prime_min_pct_label" to "Mín. %",
                "workout_w_prime_empty_tank" to "Tanque vazio (<10%)",
                "w_prime_warning_low_battery" to "Bateria W\\' fraca (<20%)"
            )
        )

        for ((dirName, expectedKeys) in expectedTranslations) {
            val stringsFile = File(resDir, "$dirName/strings.xml")
            assertTrue("Expected strings.xml to exist in $dirName", stringsFile.exists())

            val strings = parseStringResources(stringsFile)
            for ((key, expectedText) in expectedKeys) {
                val actual = strings[key]
                assertNotNull("Missing key '$key' in $dirName/strings.xml", actual)
                assertTrue(
                    "Key '$key' in $dirName/strings.xml must contain '%'",
                    actual!!.contains("%")
                )
                assertFalse(
                    "Key '$key' in $dirName/strings.xml must not contain '%%'",
                    actual.contains("%%")
                )
                assertEquals(
                    "Key '$key' in $dirName/strings.xml mismatch",
                    expectedText,
                    actual
                )
            }
        }
    }
}
