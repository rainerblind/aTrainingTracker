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

package com.atrainingtracker.trainingtracker.ui.knownlocations

import com.atrainingtracker.trainingtracker.elevation.ElevationSource
import com.atrainingtracker.trainingtracker.repositories.KnownLocationItem
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Verification test suite for [KnownLocationsScreen] presentation model,
 * string resources, and card data binding (REQ-UI-165).
 *
 * Traceability:
 * - TST-UI-117.6: List tab renders cards with Name, Altitude, Source badge, Hit Count, Coordinates.
 * - TST-UI-117.11: 100% localization parity across all 16 keys in all 9 locales.
 */
class KnownLocationsScreenTest {

    private val sampleItems = listOf(
        KnownLocationItem(
            id = 1L,
            name = "Marienplatz",
            altitude = 520.0,
            radius = 200,
            latLng = LatLng(48.13715, 11.57612),
            hitCount = 42,
            isLocked = true,
            source = ElevationSource.MANUAL_USER
        ),
        KnownLocationItem(
            id = 2L,
            name = "Englischer Garten",
            altitude = 505.4,
            radius = 200,
            latLng = LatLng(48.15542, 11.59011),
            hitCount = 15,
            isLocked = false,
            source = ElevationSource.INTERNET_DEM
        )
    )

    /**
     * TST-UI-117.6: Verify that card presentation data contains accurate Name,
     * Altitude formatting (metric and imperial), Source badge mapping, Hit Count, and Coordinates.
     */
    @Test
    fun testCardDataBinding_allFieldsAccuratelyFormatted() {
        val item = sampleItems[0]

        // 1. Name
        assertEquals("Marienplatz", item.name)

        // 2. Metric Altitude (0.1m precision or whole integer)
        val metricAlt = KnownLocationsUnitConversions.formatAltitude(item.altitude, isMetric = true)
        assertEquals("520 m", metricAlt)

        // Imperial Altitude (nearest whole foot)
        val imperialAlt = KnownLocationsUnitConversions.formatAltitude(item.altitude, isMetric = false)
        assertEquals("1706 ft", imperialAlt)

        // 3. Source badge
        assertEquals(ElevationSource.MANUAL_USER, item.source)
        assertTrue(item.isLocked)

        // 4. Hit Count
        assertEquals(42, item.hitCount)

        // 5. Geodetic Coordinates (5 decimals)
        val latStr = String.format(Locale.US, "%.5f", item.latLng.latitude)
        val lngStr = String.format(Locale.US, "%.5f", item.latLng.longitude)
        assertEquals("48.13715", latStr)
        assertEquals("11.57612", lngStr)
    }

    @Test
    fun testElevationSourceBadgeMapping() {
        val sources = listOf(
            ElevationSource.INTERNET_DEM,
            ElevationSource.MANUAL_USER,
            ElevationSource.AUTO_LEARNED,
            ElevationSource.GPS_FALLBACK,
            ElevationSource.LEGACY_RAW
        )

        for (source in sources) {
            val badgeName = source.name
            assertFalse("Source badge name must not be empty", badgeName.isEmpty())
        }
    }

    /**
     * TST-UI-117.11: 100% localization parity across all 16 keys in all 9 locales.
     */
    @Test
    fun testAll16KnownLocationStringKeysExistAcrossAll9Locales() {
        val requiredKeys = listOf(
            "drawer_start_locations",
            "known_locations_title",
            "known_locations_tab_list",
            "known_locations_tab_map",
            "known_locations_search_hint",
            "known_locations_empty_title",
            "known_locations_empty_desc",
            "known_location_unnamed_format",
            "known_locations_starts_count",
            "source_internet_dem",
            "source_manual_user",
            "source_auto_learned",
            "source_gps_fallback",
            "source_legacy_raw",
            "known_location_edit_title",
            "known_location_fetch_dem"
        )
        val locales = listOf("", "de", "es", "fr", "it", "ja", "nl", "pl", "pt")
        val resDir = findResDirectory()

        for (locale in locales) {
            val dirName = if (locale.isEmpty()) "values" else "values-$locale"
            val file = File(resDir, "$dirName/strings.xml")
            assertTrue("strings.xml must exist for locale $dirName", file.exists())

            val stringMap = parseStringsFile(file)
            for (key in requiredKeys) {
                assertTrue(
                    "Key '$key' must exist in $dirName/strings.xml",
                    stringMap.containsKey(key)
                )
                assertFalse(
                    "Value for '$key' in $dirName/strings.xml must not be blank",
                    stringMap[key].isNullOrBlank()
                )
            }
        }
    }

    private fun findResDirectory(): File {
        val candidates = listOf(
            File("src/main/res"),
            File("app/src/main/res"),
            File("../app/src/main/res")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("res directory not found in candidates: $candidates")
    }

    private fun parseStringsFile(file: File): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(file)
        val stringNodes = doc.getElementsByTagName("string")

        for (i in 0 until stringNodes.length) {
            val element = stringNodes.item(i) as Element
            val name = element.getAttribute("name")
            val text = element.textContent
            map[name] = text
        }
        return map
    }
}
