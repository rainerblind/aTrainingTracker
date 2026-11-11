/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.migration

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural contract test verifying that [ImportBackupTabsScreen.kt] adheres to the
 * format-partitioned card architecture without Google Drive scan buttons (REQ-UI-293, REQ-UI-362, TST-UI-253, TST-UI-325, ATT-2623, ATT-3489).
 */
class ImportFormatCardsContractTest {

    @Test
    fun testImportTabContent_containsThreeFormatBlocksWithoutGoogleDriveScan() {
        val screenClass = Class.forName("com.atrainingtracker.trainingtracker.migration.ImportBackupTabsScreenKt")
        assertNotNull("ImportBackupTabsScreenKt must exist", screenClass)

        val methods = screenClass.declaredMethods.map { it.name }
        assertTrue("FormatImportCard composable must be defined", methods.any { it.startsWith("FormatImportCard") })

        val strings = LocalizationTestCache.getStrings("values")
        // Format title strings
        assertTrue(strings.containsKey("import_fit_title"))
        assertTrue(strings.containsKey("import_tcx_title"))
        assertTrue(strings.containsKey("import_gpx_title"))

        // Action triad buttons exist for each format (Local, Folder, Dropbox)
        assertTrue(strings.containsKey("import_fit_button"))
        assertTrue(strings.containsKey("import_fit_folder_button"))
        assertTrue(strings.containsKey("import_fit_dropbox_button"))
        assertTrue(strings.containsKey("import_tcx_button"))
        assertTrue(strings.containsKey("import_tcx_folder_button"))
        assertTrue(strings.containsKey("import_tcx_dropbox_button"))
        assertTrue(strings.containsKey("import_gpx_button"))
        assertTrue(strings.containsKey("import_gpx_folder_button"))
        assertTrue(strings.containsKey("import_gpx_dropbox_button"))

        // Google Drive scan buttons and state must be absent (REQ-UI-362 / ATT-3489)
        assertFalse(strings.containsKey("import_fit_gdrive_button"))
        assertFalse(strings.containsKey("import_tcx_gdrive_button"))
        assertFalse(strings.containsKey("import_gpx_gdrive_button"))

        // ViewModel methods exist
        val vmClass = BackupRestoreViewModel::class.java
        val vmMethods = vmClass.declaredMethods.map { it.name }
        assertTrue("ViewModel must define importWorkoutDirectory", vmMethods.contains("importWorkoutDirectory"))
        assertTrue("ViewModel must define bulkRecoverLegacyData", vmMethods.contains("bulkRecoverLegacyData"))
        assertTrue("ViewModel must define importLegacyFile", vmMethods.contains("importLegacyFile"))
    }

    @Test
    fun testImportTabContent_rendersStravaBulkExportCardWithActionTitle() {
        val screenClass = Class.forName("com.atrainingtracker.trainingtracker.migration.ImportBackupTabsScreenKt")
        assertNotNull("ImportBackupTabsScreenKt must exist", screenClass)
        val methods = screenClass.declaredMethods.map { it.name }
        assertTrue(
            "ImportBackupTabsScreen must declare StravaArchiveImportCard",
            methods.any { it.startsWith("StravaArchiveImportCard") }
        )

        val strings = LocalizationTestCache.getStrings("values")
        assertTrue(strings.containsKey("import_strava_archive_title"))
    }

    @Test
    fun testStravaBulkExportArchiveTitleContainsActionVerbAcrossAllNineLocales() {
        val expectedVerbsByLocale = mapOf(
            "values" to "Import",
            "values-de" to "importieren",
            "values-es" to "Importar",
            "values-fr" to "Importer",
            "values-it" to "Importa",
            "values-ja" to "インポート",
            "values-nl" to "importeren",
            "values-pl" to "Importuj",
            "values-pt" to "Importar"
        )

        for ((locale, expectedVerb) in expectedVerbsByLocale) {
            val strings = LocalizationTestCache.getStrings(locale)
            assertTrue("Locale '$locale' must define import_strava_archive_title", strings.containsKey("import_strava_archive_title"))
            val title = strings["import_strava_archive_title"]!!.trim()
            assertTrue(
                "Locale '$locale' title '$title' must contain action verb '$expectedVerb' (REQ-UI-458.1)",
                title.contains(expectedVerb)
            )
            // Prohibition of inline emojis (REQ-UI-458.3 / ATT-5400)
            assertFalse(
                "Locale '$locale' title '$title' must not contain emojis",
                title.any { Character.isSurrogate(it) || it.code in 0x2600..0x27BF }
            )
        }
    }
}
