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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.regex.Pattern

/**
 * Validates Google Play Store presence listings across all 9 supported locales:
 * DE, EN, ES, FR, IT, JA, NL, PL, PT (REQ-STP-001, REQ-STP-002, REQ-STP-003, TST-STP-001, TST-STP-003, ATT-4853, ATT-490).
 *
 * Asserts strict Google Play character bounds:
 * - App Title: <= 30 UTF-16 code units
 * - Short Description: <= 80 UTF-16 code units
 * - Full Description: <= 4000 UTF-16 code units
 *
 * Also validates V5.0 core capability messaging and HTML tag hygiene.
 */
class PlayStoreListingContractTest {

    private fun findRepoRootFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
            File("../../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Could not locate $relativePath in working directory: ${File(".").absolutePath}")
    }

    private val supportedLocales = listOf("de", "en", "es", "fr", "it", "ja", "nl", "pl", "pt")

    private val titlePattern = Pattern.compile(
        """\*\*App[- ](?:Title|Titel|Título|Titulo)\*\*:\s*([^\n\(]+?)(?:\s*\(\d+/\d+\))?$""",
        Pattern.MULTILINE or Pattern.CASE_INSENSITIVE
    )

    private val shortDescPattern = Pattern.compile(
        """\*\*(?:Short Description|Kurzbeschreibung|Descripción corta|Description courte|Descrizione breve|簡単な説明|Korte beschrijving|Krótki opis|Descrição curta)\*\*:\s*\n?([^\n\(]+?)(?:\s*\(\d+/\d+\))?$""",
        Pattern.MULTILINE or Pattern.CASE_INSENSITIVE
    )

    private val fullDescPattern = Pattern.compile(
        """\*\*(?:Full Description|Vollständige Beschreibung|Descripción completa|Description complète|Descrizione completa|完全な説明|Volledige beschrijving|Pełny opis|Descrição completa)\*\*:\s*\n(.*)""",
        Pattern.DOTALL or Pattern.CASE_INSENSITIVE
    )

    data class StoreListing(
        val locale: String,
        val file: File,
        val title: String,
        val shortDescription: String,
        val fullDescription: String
    )

    private fun loadListing(locale: String): StoreListing {
        val file = findRepoRootFile("docs/store_presence/$locale.md")
        assertTrue("Store presence file for locale '$locale' must exist: ${file.path}", file.isFile)
        val text = file.readText(Charsets.UTF_8)

        val titleMatch = titlePattern.matcher(text)
        assertTrue("App Title pattern not found in docs/store_presence/$locale.md", titleMatch.find())
        val title = titleMatch.group(1)!!.trim()

        val shortMatch = shortDescPattern.matcher(text)
        assertTrue("Short Description pattern not found in docs/store_presence/$locale.md", shortMatch.find())
        val shortDesc = shortMatch.group(1)!!.trim()

        val fullMatch = fullDescPattern.matcher(text)
        assertTrue("Full Description pattern not found in docs/store_presence/$locale.md", fullMatch.find())
        val fullDesc = fullMatch.group(1)!!.trim()

        return StoreListing(locale, file, title, shortDesc, fullDesc)
    }

    @Test
    fun allSupportedLanguageFilesExist() {
        for (locale in supportedLocales) {
            val file = findRepoRootFile("docs/store_presence/$locale.md")
            assertTrue("Store presence file for locale '$locale' must exist", file.exists() && file.isFile)
        }
    }

    @Test
    fun allTitlesDoNotExceedGooglePlayCharacterLimit() {
        for (locale in supportedLocales) {
            val listing = loadListing(locale)
            assertTrue("Title in locale '$locale' cannot be blank", listing.title.isNotBlank())
            assertTrue(
                "Title in docs/store_presence/${locale}.md exceeds Google Play limit (30 chars): " +
                        "'${listing.title}' has length ${listing.title.length}",
                listing.title.length <= 30
            )
        }
    }

    @Test
    fun allShortDescriptionsDoNotExceedGooglePlayCharacterLimit() {
        for (locale in supportedLocales) {
            val listing = loadListing(locale)
            assertTrue("Short description in locale '$locale' cannot be blank", listing.shortDescription.isNotBlank())
            assertTrue(
                "Short description in docs/store_presence/${locale}.md exceeds Google Play limit (80 chars): " +
                        "'${listing.shortDescription}' has length ${listing.shortDescription.length}",
                listing.shortDescription.length <= 80
            )
        }
    }

    @Test
    fun allFullDescriptionsDoNotExceedGooglePlayCharacterLimit() {
        for (locale in supportedLocales) {
            val listing = loadListing(locale)
            assertTrue("Full description in locale '$locale' cannot be blank", listing.fullDescription.isNotBlank())
            assertTrue(
                "Full description in docs/store_presence/${locale}.md exceeds Google Play limit (4000 chars): " +
                        "locale '$locale' has length ${listing.fullDescription.length} (ATT-490)",
                listing.fullDescription.length <= 4000
            )
        }
    }

    @Test
    fun fullDescriptionsContainMandatoryV5Features() {
        for (locale in supportedLocales) {
            val listing = loadListing(locale)
            val full = listing.fullDescription

            assertTrue("Locale '$locale' must mention .FIT format (ATT-1117)", full.contains(".FIT", ignoreCase = true))
            assertTrue("Locale '$locale' must mention ANT+ protocol", full.contains("ANT+"))
            assertTrue("Locale '$locale' must mention Bluetooth/BLE", full.contains("BLE") || full.contains("Bluetooth", ignoreCase = true))
            assertTrue("Locale '$locale' must mention Strava", full.contains("Strava"))
            assertTrue("Locale '$locale' must mention Google Drive cloud backup", full.contains("Drive", ignoreCase = true))

            val hasPrivacy = full.contains("priva", ignoreCase = true) ||
                    full.contains("datenschutz", ignoreCase = true) ||
                    full.contains("privé", ignoreCase = true) ||
                    full.contains("prywatnoś", ignoreCase = true) ||
                    full.contains("プライバシー")
            assertTrue("Locale '$locale' must emphasize privacy / local data sovereignty", hasPrivacy)
        }
    }

    @Test
    fun htmlFormattingTagsAreBalanced() {
        for (locale in supportedLocales) {
            val listing = loadListing(locale)
            val full = listing.fullDescription

            val openBCount = countOccurrences(full, "<b>")
            val closeBCount = countOccurrences(full, "</b>")
            assertEquals("Mismatched <b> and </b> tags in locale '$locale'", openBCount, closeBCount)

            val openICount = countOccurrences(full, "<i>")
            val closeICount = countOccurrences(full, "</i>")
            assertEquals("Mismatched <i> and </i> tags in locale '$locale'", openICount, closeICount)
        }
    }

    @Test
    fun storeListingsDoNotContainUnimplementedBacklogFeatures() {
        val bannedKeywords = listOf("radar", "mader", "glykogen", "glycogen", "stoffwechselmodell", "レーダー")
        for (locale in supportedLocales) {
            val listing = loadListing(locale)
            val fullLower = listing.fullDescription.lowercase()
            val shortLower = listing.shortDescription.lowercase()
            val titleLower = listing.title.lowercase()

            for (kw in bannedKeywords) {
                assertTrue(
                    "Store copy in locale '$locale' contains banned unimplemented backlog keyword '$kw' (Rule 71)",
                    !fullLower.contains(kw) && !shortLower.contains(kw) && !titleLower.contains(kw)
                )
            }
        }
    }

    @Test
    fun storeScreenshotsAreUniqueAndValid() {
        val screenshotNames = listOf(
            "01_cockpit_tracking.png",
            "02_sensor_management.png",
            "03_live_map.png",
            "04_lieblingsstrecken.png",
            "05_physiology_zones.png",
            "06_periods_analytics.png"
        )
        val screenshotDir = findRepoRootFile("docs/store_presence/screenshots")
        assertTrue("Screenshots directory must exist", screenshotDir.isDirectory)

        val hashes = mutableSetOf<String>()
        val md = MessageDigest.getInstance("SHA-256")

        for (name in screenshotNames) {
            val file = File(screenshotDir, name)
            assertTrue("Screenshot '$name' must exist in docs/store_presence/screenshots/", file.exists() && file.isFile)
            assertTrue("Screenshot '$name' must have size > 50 KB (actual: ${file.length()} bytes)", file.length() > 50_000)

            val bytes = file.readBytes()
            val hash = md.digest(bytes).joinToString("") { "%02x".format(it) }
            assertTrue("Screenshot '$name' duplicate hash detected ($hash). All screenshots must be unique (Rule 71)", hashes.add(hash))

            val (width, height) = getPngDimensions(file)
            assertEquals("Screenshot '$name' width must be 1080 px", 1080, width)
            assertTrue("Screenshot '$name' height must be 2400 or 2424 px (actual: $height)", height in listOf(2400, 2424))
        }

        assertEquals("Must have exactly 6 distinct screenshot hashes", 6, hashes.size)
    }

    private fun getPngDimensions(file: File): Pair<Int, Int> {
        file.inputStream().use { input ->
            val header = ByteArray(24)
            val read = input.read(header)
            require(read == 24) { "File too short for PNG header: ${file.name}" }
            require(
                header[0] == 0x89.toByte() && header[1] == 0x50.toByte() &&
                        header[2] == 0x4E.toByte() && header[3] == 0x47.toByte()
            ) { "Not a valid PNG file: ${file.name}" }
            val width = ((header[16].toInt() and 0xFF) shl 24) or
                    ((header[17].toInt() and 0xFF) shl 16) or
                    ((header[18].toInt() and 0xFF) shl 8) or
                    (header[19].toInt() and 0xFF)
            val height = ((header[20].toInt() and 0xFF) shl 24) or
                    ((header[21].toInt() and 0xFF) shl 16) or
                    ((header[22].toInt() and 0xFF) shl 8) or
                    (header[23].toInt() and 0xFF)
            return Pair(width, height)
        }
    }

    private fun countOccurrences(text: String, sub: String): Int {
        var count = 0
        var idx = 0
        while (true) {
            val found = text.indexOf(sub, idx)
            if (found == -1) break
            count++
            idx = found + sub.length
        }
        return count
    }
}
