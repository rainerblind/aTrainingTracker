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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.atrainingtracker.testing

import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * Shared thread-safe in-memory cache for Android XML localization resources during test execution (REQ-PRO-025, TST-PRO-018).
 *
 * Prevents redundant filesystem I/O across hundreds of localization unit tests by reading and parsing
 * strings files at most once per worker JVM lifecycle. The cache naturally refreshes whenever
 * Gradle recycles the worker JVM process (`forkEvery = 80`).
 */
object LocalizationTestCache {

    private val parsedStringsCache = ConcurrentHashMap<String, Map<String, String>>()
    private val rawContentCache = ConcurrentHashMap<String, String>()

    /**
     * All 9 supported language resource directory names in aTrainingTracker.
     */
    val LOCALES = listOf(
        "values",
        "values-de",
        "values-es",
        "values-fr",
        "values-it",
        "values-ja",
        "values-nl",
        "values-pl",
        "values-pt"
    )

    /**
     * Resolves the filesystem [File] for a given locale directory and resource file.
     * Supports execution from both the repository root or the subproject `app/` directory.
     *
     * @param localeDir The directory name (e.g., "values", "values-de").
     * @param filename The resource XML filename (default: "strings.xml").
     * @return The resolved [File] handle.
     */
    fun resolveFile(localeDir: String, filename: String = "strings.xml"): File {
        val relPath = "src/main/res/$localeDir/$filename"
        val file = File(relPath)
        return if (file.exists()) {
            file
        } else {
            val appFile = File("app/$relPath")
            if (appFile.exists()) appFile else file
        }
    }

    /**
     * Retrieves the raw text content of an XML resource file, caching the string in memory.
     *
     * @param localeDir The directory name (e.g., "values", "values-de").
     * @param filename The resource XML filename (default: "strings.xml").
     * @return The raw UTF-8 string content of the resource file.
     */
    fun getRawContent(localeDir: String, filename: String = "strings.xml"): String {
        val cacheKey = "$localeDir/$filename"
        return rawContentCache.computeIfAbsent(cacheKey) {
            val file = resolveFile(localeDir, filename)
            if (file.exists()) {
                file.readText()
            } else {
                ""
            }
        }
    }

    /**
     * Retrieves a parsed, thread-safe, immutable key-value map of string resources for the specified locale.
     *
     * @param localeDir The directory name (e.g., "values", "values-de").
     * @param filename The resource XML filename (default: "strings.xml").
     * @return An unmodifiable map of string resource name to string value.
     */
    fun getStrings(localeDir: String = "values", filename: String = "strings.xml"): Map<String, String> {
        val cacheKey = "$localeDir/$filename"
        return parsedStringsCache.computeIfAbsent(cacheKey) {
            val content = getRawContent(localeDir, filename)
            val map = HashMap<String, String>()
            if (content.isNotEmpty()) {
                val pattern = Pattern.compile("<string\\s+name=\"([^\"]+)\"[^>]*>(.*?)</string>", Pattern.DOTALL)
                val matcher = pattern.matcher(content)
                while (matcher.find()) {
                    val key = matcher.group(1)
                    val value = matcher.group(2)
                    if (key != null && value != null) {
                        map[key] = value
                    }
                }
            }
            Collections.unmodifiableMap(map)
        }
    }

    /**
     * Clears all in-memory caches. Intended primarily for testing lifecycle and benchmarking.
     */
    fun clear() {
        parsedStringsCache.clear()
        rawContentCache.clear()
    }
}
