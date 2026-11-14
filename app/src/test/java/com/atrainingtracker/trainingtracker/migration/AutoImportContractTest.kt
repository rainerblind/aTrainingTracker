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

package com.atrainingtracker.trainingtracker.migration

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural contract tests for the 4-tab ImportBackupTabsScreen layout,
 * Auto-Import tab ordering, controls, and ViewModel integration (REQ-MIG-052.1, REQ-MIG-052.2, TST-MIG-050.1).
 */
class AutoImportContractTest {

    private val tabKeys = listOf(
        "tab_import",
        "tab_auto_import",
        "tab_backup",
        "tab_restore"
    )

    private fun getMergedStrings(locale: String): Map<String, String> {
        val base = LocalizationTestCache.getStrings(locale, "strings.xml")
        val autoImport = LocalizationTestCache.getStrings(locale, "strings_auto_import.xml")
        return base + autoImport
    }

    @Test
    fun testAutoImportTabLayoutAndOrder_TST_MIG_050_1() {
        val screenClass = Class.forName("com.atrainingtracker.trainingtracker.migration.ImportBackupTabsScreenKt")
        assertNotNull("ImportBackupTabsScreenKt must exist", screenClass)

        val tabContentClass = Class.forName("com.atrainingtracker.trainingtracker.migration.AutoImportTabContentKt")
        assertNotNull("AutoImportTabContentKt must exist", tabContentClass)

        // 1. Verify all 4 tab labels exist across all 9 locales
        for (key in tabKeys) {
            for (locale in LocalizationTestCache.LOCALES) {
                val strings = getMergedStrings(locale)
                assertTrue("Locale '$locale' must define tab key '$key'", strings.containsKey(key))
                val value = strings[key]?.trim()
                assertNotNull("Locale '$locale' tab key '$key' must not be null", value)
                assertTrue("Locale '$locale' tab key '$key' must not be empty", value!!.isNotEmpty())
            }
        }

        // 2. Exact contract match for default EN
        val defaultStrings = getMergedStrings("values")
        assertEquals("Import", defaultStrings["tab_import"]?.trim())
        assertEquals("Auto-Import", defaultStrings["tab_auto_import"]?.trim())
        assertEquals("Backup", defaultStrings["tab_backup"]?.trim())
        assertEquals("Restore", defaultStrings["tab_restore"]?.trim())

        // 3. Exact contract match for German DE
        val deStrings = getMergedStrings("values-de")
        assertEquals("Import", deStrings["tab_import"]?.trim())
        assertEquals("Auto-Import", deStrings["tab_auto_import"]?.trim())
        assertEquals("Backup", deStrings["tab_backup"]?.trim())
        assertEquals("Wiederherstellen", deStrings["tab_restore"]?.trim())
    }

    @Test
    fun testAutoImportControlsAndFormatsContract_TST_MIG_050_1() {
        val defaultStrings = getMergedStrings("values")
        val controlKeys = listOf(
            "auto_import_title",
            "auto_import_master_switch",
            "auto_import_cloud_sources_title",
            "auto_import_format_title",
            "auto_import_relays_title",
            "auto_import_sync_now",
            "auto_import_format_fit",
            "auto_import_format_tcx",
            "auto_import_format_gpx",
            "auto_import_format_all"
        )
        for (key in controlKeys) {
            assertTrue("Default strings must contain key '$key'", defaultStrings.containsKey(key))
        }
    }

    @Test
    fun testAutoImportViewModelPropertiesContract() {
        val vmClass = BackupRestoreViewModel::class.java

        // Verify property getters exist
        assertNotNull(vmClass.getMethod("getAutoImportEnabled"))
        assertNotNull(vmClass.getMethod("getAutoImportDropbox"))
        assertNotNull(vmClass.getMethod("getAutoImportGoogleDrive"))
        assertNotNull(vmClass.getMethod("getAutoImportFormat"))
        assertNotNull(vmClass.getMethod("getSyncImportedToHealthConnect"))
        assertNotNull(vmClass.getMethod("getAutoImportLastSyncTime"))
        assertNotNull(vmClass.getMethod("getAutoImportLastSyncResult"))
        assertNotNull(vmClass.getMethod("isAutoImportSyncing"))

        // Verify mutation methods exist
        assertNotNull(vmClass.getMethod("updateAutoImportEnabled", Boolean::class.javaPrimitiveType))
        assertNotNull(vmClass.getMethod("updateAutoImportDropbox", Boolean::class.javaPrimitiveType))
        assertNotNull(vmClass.getMethod("updateAutoImportGoogleDrive", Boolean::class.javaPrimitiveType))
        assertNotNull(vmClass.getMethod("updateAutoImportFormat", String::class.java))
        assertNotNull(vmClass.getMethod("updateSyncImportedToHealthConnect", Boolean::class.javaPrimitiveType))
        assertNotNull(vmClass.getMethod("triggerAutoImportSync", android.content.Context::class.java))
    }
}
