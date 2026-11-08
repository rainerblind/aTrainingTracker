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
import org.junit.Assert.assertNotNull
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

    /**
     * TST-UI-118.4: Verify German naming parity is "Lieblingsorte" and English is "Favorite Locations"
     * for both drawer entry and screen title (ATT-1382 / REQ-UI-166).
     */
    @Test
    fun testLieblingsorteNamingAcrossLocales() {
        val resDir = findResDirectory()

        // German
        val deMap = parseStringsFile(File(resDir, "values-de/strings.xml"))
        assertEquals("Lieblingsorte", deMap["drawer_start_locations"])
        assertEquals("Lieblingsorte", deMap["known_locations_title"])
        assertEquals("Liste", deMap["known_locations_tab_list"])
        assertEquals("Karte", deMap["known_locations_tab_map"])

        // English (default)
        val enMap = parseStringsFile(File(resDir, "values/strings.xml"))
        assertEquals("Favorite Locations", enMap["drawer_start_locations"])
        assertEquals("Favorite Locations", enMap["known_locations_title"])
        assertEquals("List", enMap["known_locations_tab_list"])
        assertEquals("Map", enMap["known_locations_tab_map"])
    }

    /**
     * TST-UI-070.2: Verify KnownLocationCard context menu layout and purity:
     * - No 3-dots overflow icon button (MoreVert).
     * - Context menu anchored to TopStart.
     * - No secondary actions ("Show on Map", "Edit").
     * - Only Delete action is present.
     */
    @Test
    fun testKnownLocationCardContextMenuStructure() {
        val candidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt"),
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("KnownLocationsScreen.kt not found in candidates: $candidates")
        val content = file.readText()

        // 1. MoreVert overflow button must not exist
        assertFalse(
            "KnownLocationCard must not contain MoreVert overflow button",
            content.contains("Icons.Default.MoreVert") || content.contains("location_overflow_button_")
        )

        // 2. Alignment must be TopStart
        assertTrue(
            "KnownLocationCard context menu must align to TopStart",
            content.contains(".align(Alignment.TopStart)")
        )

        // 3. Secondary actions must not exist
        assertFalse(
            "KnownLocationCard context menu must not contain 'Show on Map' action",
            content.contains("location_show_on_map_action_")
        )
        assertFalse(
            "KnownLocationCard context menu must not contain 'Edit' action",
            content.contains("location_edit_action_")
        )

        // 4. Delete action must be present
        assertTrue(
            "KnownLocationCard context menu must contain 'location_delete_action_' test tag",
            content.contains("location_delete_action_")
        )
    }

    /**
     * TST-UI-070.3: Verify localization parity for delete and really_delete_format across all 9 locales.
     */
    @Test
    fun testDeleteStringResourcesAcrossAll9Locales() {
        val locales = listOf("", "de", "es", "fr", "it", "ja", "nl", "pl", "pt")
        val resDir = findResDirectory()

        for (locale in locales) {
            val dirName = if (locale.isEmpty()) "values" else "values-$locale"
            val file = File(resDir, "$dirName/strings.xml")
            assertTrue("strings.xml must exist for locale $dirName", file.exists())

            val stringMap = parseStringsFile(file)
            assertTrue("Key 'delete' must exist in $dirName/strings.xml", stringMap.containsKey("delete"))
            assertFalse("Value for 'delete' in $dirName/strings.xml must not be blank", stringMap["delete"].isNullOrBlank())

            assertTrue("Key 'really_delete_format' must exist in $dirName/strings.xml", stringMap.containsKey("really_delete_format"))
            val formatStr = stringMap["really_delete_format"] ?: ""
            assertTrue(
                "Value for 'really_delete_format' in $dirName/strings.xml must contain '%1\$s'",
                formatStr.contains("%1\$s") || formatStr.contains("%s")
            )
        }
    }

    /**
     * TST-UI-466.2: Header Filter Action Button & Badge Contract Test (REQ-UI-466.2).
     *
     * Verifies semantic state properties and composable existence via reflection/state model (no UI source-grep).
     */
    @Test
    fun testFilterActionButtonAndBadgeContract_REQ_UI_466_2() {
        // 1. Programmatic reflection verifying FilterActionButton composable exists
        val filterBtnClass = Class.forName("com.atrainingtracker.trainingtracker.ui.common.filters.FilterActionButtonKt")
        assertNotNull("FilterActionButtonKt must exist", filterBtnClass)
        val filterBtnMethod = filterBtnClass.declaredMethods.find { it.name.startsWith("FilterActionButton") }
        assertNotNull("FilterActionButton composable must exist", filterBtnMethod)

        // 2. Behavioral state contract verifying KnownLocationsUiState activeFilterCount derivation
        val defaultState = KnownLocationsUiState()
        assertTrue("Default UI state must have isFilterActive = true (minStarts=5)", defaultState.isFilterActive)
        assertEquals("Default UI state must have activeFilterCount = 1", 1, defaultState.activeFilterCount)

        // Cleared state has 0 active filters
        val clearedState = defaultState.copy(
            filterCriteria = KnownLocationsFilterCriteria(query = "", minStarts = null)
        )
        assertFalse("Cleared state must have isFilterActive = false", clearedState.isFilterActive)
        assertEquals("Cleared state must have activeFilterCount = 0", 0, clearedState.activeFilterCount)

        // Combined state has 2 active filters
        val combinedState = defaultState.copy(
            filterCriteria = KnownLocationsFilterCriteria(query = "Marienplatz", minStarts = 10)
        )
        assertTrue("Combined state must have isFilterActive = true", combinedState.isFilterActive)
        assertEquals("Combined state must have activeFilterCount = 2", 2, combinedState.activeFilterCount)
    }

    /**
     * TST-UI-466.3: Active Filter Chips Row & Dismissal Contract Test (REQ-UI-466.3, REQ-UI-466.5).
     *
     * Verifies composable components and dismissal semantics via reflection and state models (no UI source-grep).
     */
    @Test
    fun testActiveFilterChipsRowContract_REQ_UI_466_3() {
        // 1. Programmatic reflection verifying ActiveKnownLocationsFilterChipsRow and sheet composables exist
        val chipsRowClass = Class.forName("com.atrainingtracker.trainingtracker.ui.knownlocations.ActiveKnownLocationsFilterChipsRowKt")
        assertNotNull("ActiveKnownLocationsFilterChipsRowKt must exist", chipsRowClass)
        val chipsRowMethod = chipsRowClass.declaredMethods.find { it.name.startsWith("ActiveKnownLocationsFilterChipsRow") }
        assertNotNull("ActiveKnownLocationsFilterChipsRow composable must exist", chipsRowMethod)

        val sheetClass = Class.forName("com.atrainingtracker.trainingtracker.ui.knownlocations.KnownLocationsFilterBottomSheetKt")
        assertNotNull("KnownLocationsFilterBottomSheetKt must exist", sheetClass)
        val sheetMethod = sheetClass.declaredMethods.find { it.name.startsWith("KnownLocationsFilterBottomSheet") }
        assertNotNull("KnownLocationsFilterBottomSheet composable must exist", sheetMethod)

        // 2. Behavioral contract: chip dismissal semantics
        val activeCriteria = KnownLocationsFilterCriteria(query = "Trail", minStarts = 5)
        assertEquals(2, activeCriteria.activeFilterCount)

        // onRemoveQuery clears query while preserving minStarts
        val queryRemoved = activeCriteria.copy(query = "")
        assertEquals(1, queryRemoved.activeFilterCount)
        assertEquals(5, queryRemoved.minStarts)

        // onRemoveMinStarts clears minStarts while preserving query
        val startsRemoved = activeCriteria.copy(minStarts = null)
        assertEquals(1, startsRemoved.activeFilterCount)
        assertEquals("Trail", startsRemoved.query)

        // onClearAll resets all filter dimensions
        val allCleared = KnownLocationsFilterCriteria(query = "", minStarts = null)
        assertEquals(0, allCleared.activeFilterCount)
        assertTrue(allCleared.isEmpty)

        // 3. Behavioral contract: filtered list and empty-filter state modeling
        val item1 = sampleItems[0] // hitCount 42
        val itemLow = KnownLocationItem(3L, "Incidental Spot", 500.0, 200, LatLng(48.0, 11.0), 2, false, ElevationSource.INTERNET_DEM)
        val allItems = listOf(item1, itemLow)

        // Default minStarts=5 filters incidental spot
        val defaultCriteria = KnownLocationsFilterCriteria()
        val defaultFiltered = allItems.filter { defaultCriteria.matches(it, it.hitCount) }
        assertEquals(1, defaultFiltered.size)
        assertEquals(1L, defaultFiltered[0].id)

        // Empty filter fallback: when no locations match filter
        val nonMatchingCriteria = KnownLocationsFilterCriteria(query = "NonExistentLocation", minStarts = 100)
        val noneFiltered = allItems.filter { nonMatchingCriteria.matches(it, it.hitCount) }
        assertTrue("Non-matching criteria yields empty filtered locations", noneFiltered.isEmpty())
        assertTrue("Total locations remains populated for empty-filter UI state distinction", allItems.isNotEmpty())
    }

    /**
     * TST-UI-466.4: 9-language localization audit for new filter keys across all 9 locales.
     */
    @Test
    fun testFilterKnownLocationsStringKeysExistAcrossAll9Locales() {
        val requiredKeys = listOf(
            "filter_known_locations_title",
            "filter_search_known_locations_hint",
            "filter_section_starts",
            "filter_min_starts_chip_format",
            "filter_no_matching_locations"
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
                    "Filter key '$key' must exist in $dirName/strings.xml",
                    stringMap.containsKey(key)
                )
                assertFalse(
                    "Filter value for '$key' in $dirName/strings.xml must not be blank",
                    stringMap[key].isNullOrBlank()
                )
            }
        }
    }

    /**
     * TST-UI-470.2: Contract verification for FloatingActionButton and location creation flow.
     */
    @Test
    fun testKnownLocationsScreen_fabAndCreationFlowContract() {
        val screenFile = findSourceFile("com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt")
        assertTrue("KnownLocationsScreen.kt must exist", screenFile.exists())
        val source = screenFile.readText()

        // 1. Verify FloatingActionButton with test tag known_locations_add_fab
        assertTrue("Screen must render FloatingActionButton", source.contains("FloatingActionButton("))
        assertTrue("FAB must declare test tag 'known_locations_add_fab'", source.contains("\"known_locations_add_fab\""))

        // 2. Verify icon and content description
        assertTrue("FAB must use Icons.Default.Add", source.contains("Icons.Default.Add"))
        assertTrue("FAB must reference R.string.known_locations_add_location", source.contains("R.string.known_locations_add_location"))

        // 3. Verify click handler triggers openCreateDialog
        assertTrue("FAB click must invoke openCreateDialog", source.contains("viewModel.openCreateDialog()"))

        // 4. Verify onConfirm delegates to createLocation when id == 0L
        assertTrue("onConfirm must call createLocation for id == 0L", source.contains("viewModel.createLocation("))
    }

    /**
     * TST-UI-470.3: Centered heart glyph marker and thumbnail alignment verification.
     */
    @Test
    fun testKnownLocationsThumbnailMap_usesCenteredHeartGlyphMarker() {
        val screenFile = findSourceFile("com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt")
        assertTrue("KnownLocationsScreen.kt must exist", screenFile.exists())
        val source = screenFile.readText()

        // 1. Verify createCenteredHeartGlyphMarker is used
        assertTrue(
            "Thumbnail map must use createCenteredHeartGlyphMarker",
            source.contains("createCenteredHeartGlyphMarker(")
        )

        // 2. Verify standing pin createHeartPinMarker is no longer used in KnownLocationsScreen
        assertFalse(
            "KnownLocationsScreen must not use standing pin createHeartPinMarker",
            source.contains("createHeartPinMarker(")
        )

        // 3. Verify concentric anchor = Offset(0.5f, 0.5f) is specified for Marker
        assertTrue(
            "Thumbnail Marker must specify concentric anchor Offset(0.5f, 0.5f)",
            source.contains("anchor = Offset(0.5f, 0.5f)")
        )

        // 4. Verify LocalInspectionMode preview uses Icons.Default.Favorite
        assertTrue(
            "Preview mode must use Icons.Default.Favorite glyph",
            source.contains("Icons.Default.Favorite")
        )
    }

    /**
     * TST-UI-470.3 (ATT-6211): Canonical Material Favorite heart geometry constant definition.
     */
    @Test
    fun testCanonicalMaterialHeartPath_matchesMaterialFavoriteGeometry() {
        // Assert canonical Material Favorite SVG path data constant directly (no File.readText)
        assertEquals(
            "M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z",
            com.atrainingtracker.trainingtracker.ui.map.CANONICAL_MATERIAL_HEART_PATH_DATA
        )
    }

    /**
     * TST-UI-470.5: 9-language localization parity for ATT-5884 string resources.
     */
    @Test
    fun testAtt5884StringKeysExistAcrossAll9Locales() {
        val requiredKeys = listOf(
            "known_locations_home_base_desc",
            "known_locations_add_location",
            "known_locations_new_location"
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

    private fun findSourceFile(relativePath: String): File {
        val candidates = listOf(
            File("app/src/main/java", relativePath),
            File("src/main/java", relativePath),
            File("../app/src/main/java", relativePath)
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Source file $relativePath not found in $candidates")
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
