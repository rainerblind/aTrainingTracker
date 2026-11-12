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

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural and contract tests verifying that secondary tabbed headers and settings/support screens
 * declare context-aware Hamburger Menu vs Back Arrow navigation parameters and comply with REQ-UI-518 / TST-UI-518.2, TST-UI-518.3.
 */
class SecondaryTabbedHeadersContractTest {

    private fun findProjectFile(relativePath: String): File {
        val userDir = File(System.getProperty("user.dir") ?: ".")
        val candidate = File(userDir, relativePath)
        if (candidate.exists()) return candidate

        val appCandidate = File(userDir, "app/$relativePath")
        if (appCandidate.exists()) return appCandidate

        val parentCandidate = File(userDir.parentFile, relativePath)
        if (parentCandidate.exists()) return parentCandidate

        val parentAppCandidate = File(userDir.parentFile, "app/$relativePath")
        if (parentAppCandidate.exists()) return parentAppCandidate

        throw IllegalStateException("Cannot locate $relativePath from ${userDir.absolutePath}")
    }

    @Test
    fun testDevicesTabbedScreen_headerNavigationContract() {
        val file = findProjectFile("src/main/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedScreen.kt")
        val content = file.readText()

        assertTrue("DevicesTabbedScreen must declare onMenuClick parameter", content.contains("onMenuClick: (() -> Unit)? = null"))
        assertTrue("DevicesTabbedScreen must declare onBackClick parameter", content.contains("onBackClick: (() -> Unit)? = null"))
        assertTrue("DevicesTabbedScreen must render Icons.Default.Menu", content.contains("Icons.Default.Menu"))
        assertTrue("DevicesTabbedScreen must render Icons.AutoMirrored.Filled.ArrowBack", content.contains("Icons.AutoMirrored.Filled.ArrowBack"))
        assertTrue("DevicesTabbedScreen must reference R.string.navigation_drawer_open", content.contains("R.string.navigation_drawer_open"))
        assertTrue("DevicesTabbedScreen must reference R.string.back", content.contains("R.string.back"))
        assertTrue("DevicesTabbedScreen must use 4.dp start padding for header title row", content.contains("padding(start = 4.dp, end = 16.dp)"))
    }

    @Test
    fun testEquipmentTabsScreen_headerNavigationContract() {
        val file = findProjectFile("src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentTabsScreen.kt")
        val content = file.readText()

        assertTrue("EquipmentTabsScreen must declare onMenuClick parameter", content.contains("onMenuClick: (() -> Unit)? = null"))
        assertTrue("EquipmentTabsScreen must declare onBackClick parameter", content.contains("onBackClick: (() -> Unit)? = null"))
        assertTrue("EquipmentTabsScreen must render Icons.Default.Menu", content.contains("Icons.Default.Menu"))
        assertTrue("EquipmentTabsScreen must render Icons.AutoMirrored.Filled.ArrowBack", content.contains("Icons.AutoMirrored.Filled.ArrowBack"))
        assertTrue("EquipmentTabsScreen must reference R.string.navigation_drawer_open", content.contains("R.string.navigation_drawer_open"))
        assertTrue("EquipmentTabsScreen must reference R.string.back", content.contains("R.string.back"))
        assertTrue("EquipmentTabsScreen must use 4.dp start padding for header title row", content.contains("padding(start = 4.dp, end = 16.dp)"))
    }

    @Test
    fun testImportBackupTabsScreen_headerNavigationContract() {
        val file = findProjectFile("src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt")
        val content = file.readText()

        assertTrue("ImportBackupTabsScreen must declare onMenuClick parameter", content.contains("onMenuClick: (() -> Unit)? = null"))
        assertTrue("ImportBackupTabsScreen must declare onBackClick parameter", content.contains("onBackClick: (() -> Unit)? = null"))
        assertTrue("ImportBackupTabsScreen must render Icons.Default.Menu", content.contains("Icons.Default.Menu"))
        assertTrue("ImportBackupTabsScreen must render Icons.AutoMirrored.Filled.ArrowBack", content.contains("Icons.AutoMirrored.Filled.ArrowBack"))
        assertTrue("ImportBackupTabsScreen must reference R.string.navigation_drawer_open", content.contains("R.string.navigation_drawer_open"))
        assertTrue("ImportBackupTabsScreen must reference R.string.back", content.contains("R.string.back"))
        assertTrue("ImportBackupTabsScreen must use 4.dp start padding for header title row", content.contains("padding(start = 4.dp, end = 16.dp)"))
    }

    @Test
    fun testSportTypesTabsScreen_headerNavigationContract() {
        val file = findProjectFile("src/main/java/com/atrainingtracker/banalservice/ui/sporttype/SportTypesTabsScreen.kt")
        val content = file.readText()

        assertTrue("SportTypesTabsScreen must declare onMenuClick parameter", content.contains("onMenuClick: (() -> Unit)? = null"))
        assertTrue("SportTypesTabsScreen must declare onBackClick parameter", content.contains("onBackClick: (() -> Unit)? = null"))
        assertTrue("SportTypesTabsScreen must render Icons.Default.Menu", content.contains("Icons.Default.Menu"))
        assertTrue("SportTypesTabsScreen must render Icons.AutoMirrored.Filled.ArrowBack", content.contains("Icons.AutoMirrored.Filled.ArrowBack"))
        assertTrue("SportTypesTabsScreen must reference R.string.navigation_drawer_open", content.contains("R.string.navigation_drawer_open"))
        assertTrue("SportTypesTabsScreen must reference R.string.back", content.contains("R.string.back"))
        assertTrue("SportTypesTabsScreen must use 4.dp start padding for header title row", content.contains("padding(start = 4.dp, end = 16.dp)"))
    }

    @Test
    fun testZoneSettingsScreen_contextAwareNavigationContract() {
        val file = findProjectFile("src/main/java/com/atrainingtracker/trainingtracker/activities/ZonesSettingsActivity.kt")
        val content = file.readText()

        assertTrue("ZoneSettingsScreen must support onOpenDrawer", content.contains("onOpenDrawer: (() -> Unit)? = null"))
        assertTrue("ZoneSettingsScreen must render Icons.Default.Menu when onOpenDrawer is provided", content.contains("imageVector = Icons.Default.Menu"))
        assertTrue("ZoneSettingsScreen must render Icons.AutoMirrored.Filled.ArrowBack when onOpenDrawer is null", content.contains("imageVector = Icons.AutoMirrored.Filled.ArrowBack"))
        assertTrue("ZoneSettingsScreen must reference R.string.navigation_drawer_open", content.contains("R.string.navigation_drawer_open"))
        assertTrue("ZoneSettingsScreen must reference R.string.back", content.contains("R.string.back"))
    }

    @Test
    fun testAdvancedTuningScreen_contextAwareNavigationContract() {
        val file = findProjectFile("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningScreen.kt")
        val content = file.readText()

        assertTrue("AdvancedTuningScreen must support onOpenDrawer", content.contains("onOpenDrawer: (() -> Unit)? = null"))
        assertTrue("AdvancedTuningScreen must render Icons.Default.Menu when onOpenDrawer is provided", content.contains("imageVector = Icons.Default.Menu"))
        assertTrue("AdvancedTuningScreen must render Icons.AutoMirrored.Filled.ArrowBack when onOpenDrawer is null", content.contains("imageVector = Icons.AutoMirrored.Filled.ArrowBack"))
        assertTrue("AdvancedTuningScreen must reference R.string.navigation_drawer_open", content.contains("R.string.navigation_drawer_open"))
    }

    @Test
    fun testSupporterScreen_contextAwareNavigationContract() {
        val file = findProjectFile("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/supporter/SupporterScreen.kt")
        val content = file.readText()

        assertTrue("SupporterScreen must support onOpenDrawer", content.contains("onOpenDrawer: (() -> Unit)? = null"))
        assertTrue("SupporterScreen must render Icons.Default.Menu when onOpenDrawer is provided", content.contains("imageVector = Icons.Default.Menu"))
        assertTrue("SupporterScreen must render Icons.AutoMirrored.Filled.ArrowBack when onOpenDrawer is null", content.contains("imageVector = Icons.AutoMirrored.Filled.ArrowBack"))
        assertTrue("SupporterScreen must reference R.string.navigation_drawer_open", content.contains("R.string.navigation_drawer_open"))
        assertTrue("SupporterScreen must reference R.string.back", content.contains("R.string.back"))
    }

    @Test
    fun testTipsGuideScreen_contextAwareNavigationContract() {
        val file = findProjectFile("src/main/java/com/atrainingtracker/trainingtracker/ui/help/TipsGuideScreen.kt")
        val content = file.readText()

        assertTrue("TipsGuideScreen must support onOpenDrawer", content.contains("onOpenDrawer: (() -> Unit)? = null"))
        assertTrue("TipsGuideScreen must render Icons.Default.Menu when onOpenDrawer is provided", content.contains("imageVector = Icons.Default.Menu"))
        assertTrue("TipsGuideScreen must render Icons.AutoMirrored.Filled.ArrowBack when onOpenDrawer is null", content.contains("imageVector = Icons.AutoMirrored.Filled.ArrowBack"))
        assertTrue("TipsGuideScreen must reference R.string.navigation_drawer_open", content.contains("R.string.navigation_drawer_open"))
        assertTrue("TipsGuideScreen must reference R.string.back", content.contains("R.string.back"))
    }
}
