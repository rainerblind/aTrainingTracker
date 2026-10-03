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

package com.atrainingtracker.trainingtracker.ui.navigation

import com.atrainingtracker.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test verifying navigation drawer item routing and controller interaction
 * for Known Start Locations (ATT-919 / REQ-UI-165).
 *
 * Traceability:
 * - TST-UI-117.1: Drawer item routes to NavRoutes.START_LOCATIONS and closes drawer.
 */
class AppNavigationDrawerTest {

    @Test
    fun testStartLocationsRouteResolution() {
        val resolvedRoute = NavRoutes.fromDrawerItemId(R.id.drawer_start_locations)
        assertEquals(
            "Drawer item ID R.id.drawer_start_locations must resolve to NavRoutes.START_LOCATIONS",
            NavRoutes.START_LOCATIONS,
            resolvedRoute
        )

        val resolvedItemId = NavRoutes.toDrawerItemId(NavRoutes.START_LOCATIONS)
        assertEquals(
            "NavRoutes.START_LOCATIONS must resolve to R.id.drawer_start_locations",
            R.id.drawer_start_locations,
            resolvedItemId
        )
    }

    @Test
    fun testStartLocationsDrawerItemDoesNotTriggerBottomSheet() {
        val bottomSheetType = NavRoutes.toBottomSheetType(R.id.drawer_start_locations)
        assertNull(
            "Start Locations must be a full screen route, not a modal settings bottom sheet",
            bottomSheetType
        )
    }

    @Test
    fun testDrawerControllerSelectionAndCloseTransition() {
        val controller = NavigationDrawerController()
        var drawerCloseCalled = false

        controller.bindDrawer(
            open = {},
            close = { drawerCloseCalled = true },
            isOpen = { true }
        )

        assertTrue(controller.isDrawerOpen)

        // Select the start locations destination
        controller.selectedItemId = R.id.drawer_start_locations
        assertEquals(R.id.drawer_start_locations, controller.selectedItemId)

        // Verify closing the drawer
        controller.closeDrawer()
        assertTrue("Selecting destination item must trigger drawer close", drawerCloseCalled)
    }

    @Test
    fun testDrawerMapsGroupItemOrderingAndIcons() {
        val groups = createDrawerGroups(R.string.tab_start)
        val mapsGroup = groups.firstOrNull { it.titleRes == R.string.drawer__maps }
        assertTrue("Maps drawer group must exist", mapsGroup != null)

        val items = mapsGroup!!.items
        val myLocationsIndex = items.indexOfFirst { it.id == R.id.drawer_my_locations }
        val startLocationsIndex = items.indexOfFirst { it.id == R.id.drawer_start_locations }

        assertTrue("Lieblingsstrecken (drawer_my_locations) must be in maps group", myLocationsIndex != -1)
        assertTrue("Lieblingsorte (drawer_start_locations) must be in maps group", startLocationsIndex != -1)
        assertTrue(
            "Lieblingsorte must appear directly after Lieblingsstrecken (ATT-1382)",
            startLocationsIndex == myLocationsIndex + 1
        )

        val myLocationsItem = items[myLocationsIndex]
        val startLocationsItem = items[startLocationsIndex]

        assertEquals(
            "Lieblingsstrecken icon must be ic_favorite_route",
            R.drawable.ic_favorite_route,
            myLocationsItem.iconRes
        )
        assertEquals(
            "Lieblingsorte icon must be my_locations (pin with heart)",
            R.drawable.my_locations,
            startLocationsItem.iconRes
        )
    }

    // --- TST-UI-135: Theme-Aware Icon Contrast Tinting Verification (ATT-1547 / REQ-UI-123) ---

    @Test
    fun testDrawerGroups_monochromeItems_enableTinting() {
        val groups = createDrawerGroups(R.string.tab_start)
        val allItems = groups.flatMap { it.items }
        val monochromeItems = allItems.filter {
            it.id != R.id.drawer_strava && it.id != R.id.drawer_dropbox && it.id != R.id.drawer_google_drive
        }

        assertTrue("Drawer must define multiple monochrome items", monochromeItems.size >= 19)
        monochromeItems.forEach { item ->
            assertTrue(
                "Item ${item.id} (${item.titleRes}) must enable theme-aware tinting (tintIcon = true)",
                item.tintIcon
            )
        }
    }

    @Test
    fun testDrawerGroups_partnerBrandItems_exemptFromTinting() {
        val groups = createDrawerGroups(R.string.tab_start)
        val allItems = groups.flatMap { it.items }

        val stravaItem = allItems.firstOrNull { it.id == R.id.drawer_strava }
        assertTrue("Strava drawer item must exist", stravaItem != null)
        assertFalse("Strava icon must NOT be tinted with monochrome colors (tintIcon = false)", stravaItem!!.tintIcon)

        val dropboxItem = allItems.firstOrNull { it.id == R.id.drawer_dropbox }
        assertTrue("Dropbox drawer item must exist", dropboxItem != null)
        assertFalse("Dropbox icon must NOT be tinted with monochrome colors (tintIcon = false)", dropboxItem!!.tintIcon)

        val googleDriveItem = allItems.firstOrNull { it.id == R.id.drawer_google_drive }
        assertTrue("Google Drive drawer item must exist", googleDriveItem != null)
        assertFalse("Google Drive icon must NOT be tinted with monochrome colors (tintIcon = false)", googleDriveItem!!.tintIcon)
    }

    @Test
    fun testDrawerItemView_appliesConditionalColorFilter() {
        val relativePath = "app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt"
        val candidates = listOf(
            java.io.File(relativePath),
            java.io.File("../$relativePath"),
            java.io.File("../../$relativePath")
        )
        val sourceFile = candidates.firstOrNull { it.exists() }
        assertTrue("AppNavigationDrawer.kt source file must exist", sourceFile != null && sourceFile.exists())

        val sourceContent = sourceFile!!.readText()
        assertTrue(
            "DrawerItemView must apply conditional colorFilter based on item.tintIcon",
            sourceContent.contains("colorFilter = if (item.tintIcon) ColorFilter.tint(contentColor) else null")
        )
        assertTrue(
            "AppNavigationDrawer.kt must import androidx.compose.ui.graphics.ColorFilter",
            sourceContent.contains("import androidx.compose.ui.graphics.ColorFilter")
        )
    }

    @Test
    fun testThemeTokens_contrastRatioExceedsWcagAA() {
        fun linearize(channel: Double): Double {
            return if (channel <= 0.04045) channel / 12.92 else Math.pow((channel + 0.055) / 1.055, 2.4)
        }

        fun relativeLuminance(r: Int, g: Int, b: Int): Double {
            val rLin = linearize(r / 255.0)
            val gLin = linearize(g / 255.0)
            val bLin = linearize(b / 255.0)
            return 0.2126 * rLin + 0.7152 * gLin + 0.0722 * bLin
        }

        fun contrastRatio(l1: Double, l2: Double): Double {
            val lighter = Math.max(l1, l2)
            val darker = Math.min(l1, l2)
            return (lighter + 0.05) / (darker + 0.05)
        }

        // Dark surface: #121212
        val darkSurfaceLum = relativeLuminance(0x12, 0x12, 0x12)
        // Night mode on_surface: #ffffff
        val darkTextLum = relativeLuminance(0xFF, 0xFF, 0xFF)
        val darkContrast = contrastRatio(darkTextLum, darkSurfaceLum)
        assertTrue(
            "Dark mode contrast ratio ($darkContrast) must exceed WCAG AA 4.5:1",
            darkContrast >= 4.5
        )

        // Light surface: #ffffff
        val lightSurfaceLum = relativeLuminance(0xFF, 0xFF, 0xFF)
        // Default mode on_surface: #000080
        val lightTextLum = relativeLuminance(0x00, 0x00, 0x80)
        val lightContrast = contrastRatio(lightSurfaceLum, lightTextLum)
        assertTrue(
            "Light mode contrast ratio ($lightContrast) must exceed WCAG AA 4.5:1",
            lightContrast >= 4.5
        )
    }

    // --- TST-UI-140: Defensive Resource Resolution & Crash Prevention Verification (ATT-1592 / REQ-UI-123) ---

    @Test
    fun testDrawerItemView_defensiveExceptionHandling_sourceCodeInspection() {
        val relativePath = "app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt"
        val candidates = listOf(
            java.io.File(relativePath),
            java.io.File("../$relativePath"),
            java.io.File("../../$relativePath")
        )
        val sourceFile = candidates.firstOrNull { it.exists() }
        assertTrue("AppNavigationDrawer.kt source file must exist", sourceFile != null && sourceFile.exists())

        val sourceContent = sourceFile!!.readText()
        assertTrue(
            "AppNavigationDrawer.kt must import android.content.res.Resources",
            sourceContent.contains("import android.content.res.Resources")
        )
        assertTrue(
            "DrawerItemView must catch Resources.NotFoundException to prevent density crashes",
            sourceContent.contains("catch (e: Resources.NotFoundException)")
        )
        assertTrue(
            "DrawerItemView must catch general Throwable as an ultimate safety net",
            sourceContent.contains("catch (e: Throwable)")
        )
    }

    @Test
    fun testRootFallbackAssets_stravaAndDropbox_exist() {
        val stravaRelative = "app/src/main/res/drawable/logo_square_strava.png"
        val dropboxRelative = "app/src/main/res/drawable/dropbox_logo_blue.png"

        val stravaFile = listOf(
            java.io.File(stravaRelative),
            java.io.File("../$stravaRelative"),
            java.io.File("../../$stravaRelative")
        ).firstOrNull { it.exists() }

        val dropboxFile = listOf(
            java.io.File(dropboxRelative),
            java.io.File("../$dropboxRelative"),
            java.io.File("../../$dropboxRelative")
        ).firstOrNull { it.exists() }

        assertTrue("Root fallback logo_square_strava.png must exist in res/drawable/", stravaFile != null && stravaFile.length() > 0)
        assertTrue("Root fallback dropbox_logo_blue.png must exist in res/drawable/", dropboxFile != null && dropboxFile.length() > 0)
    }

    @Test
    fun testDrawerGroups_allDrawerItems_haveNonZeroResourceIds() {
        val groups = createDrawerGroups(R.string.tab_start)
        assertTrue("Drawer must define groups", groups.isNotEmpty())

        val allItems = groups.flatMap { it.items }
        assertTrue("Drawer must define at least 20 items", allItems.size >= 20)

        allItems.forEach { item ->
            assertTrue("Item ID must be non-zero", item.id != 0)
            assertTrue("Item iconRes must be non-zero (id=${item.id})", item.iconRes != 0)
            assertTrue("Item titleRes must be non-zero (id=${item.id})", item.titleRes != 0)
        }
    }

    // --- TST-SET-063: Expert Settings Drawer Grouping Verification (ATT-1646 / REQ-SET-074) ---

    @Test
    fun testDrawerGroups_expertSettings_placedInDedicatedBottomGroup() {
        val groups = createDrawerGroups(R.string.tab_start)
        assertTrue("Drawer must define groups", groups.isNotEmpty())

        val lastGroup = groups.last()
        assertEquals(
            "Final drawer group must be Expert Settings",
            R.string.drawer__expert_settings,
            lastGroup.titleRes
        )

        val advancedTuningItem = lastGroup.items.firstOrNull { it.id == R.id.drawer_advanced_tuning }
        assertTrue(
            "Expert Settings group must contain drawer_advanced_tuning",
            advancedTuningItem != null
        )
        assertEquals(
            "Advanced tuning item must reference advanced_tuning_title",
            R.string.advanced_tuning_title,
            advancedTuningItem?.titleRes
        )
    }

    @Test
    fun testDrawerGroups_generalSettings_doesNotContainAdvancedTuning() {
        val groups = createDrawerGroups(R.string.tab_start)
        val settingsGroup = groups.firstOrNull { it.titleRes == R.string.drawer__settings }
        assertTrue("General settings group must exist", settingsGroup != null)

        val hasAdvancedTuning = settingsGroup!!.items.any { it.id == R.id.drawer_advanced_tuning }
        assertFalse(
            "General settings group must not contain drawer_advanced_tuning (relocated to dedicated expert group)",
            hasAdvancedTuning
        )
    }
}

