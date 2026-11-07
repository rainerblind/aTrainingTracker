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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural contract test verifying that [ImportBackupTabsScreen.kt] adheres to the
 * format-partitioned card architecture without Google Drive scan buttons (REQ-UI-293, REQ-UI-362, TST-UI-253, TST-UI-325, ATT-2623, ATT-3489).
 */
class ImportFormatCardsContractTest {

    private fun findSourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../$relativePath"),
            File("../../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Source file not found in candidates: $relativePath")
    }

    @Test
    fun testImportTabContent_containsThreeFormatBlocksWithoutGoogleDriveScan() {
        val file = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt")
        val content = file.readText()

        // 1. Reusable FormatImportCard composable exists
        assertTrue(
            "FormatImportCard composable must be defined",
            content.contains("fun FormatImportCard(")
        )

        // 2. ImportTabContent partitions into FIT, TCX, and GPX blocks
        assertTrue(
            "ImportTabContent must render FIT card with import_fit_title",
            content.contains("R.string.import_fit_title")
        )
        assertTrue(
            "ImportTabContent must render TCX card with import_tcx_title",
            content.contains("R.string.import_tcx_title")
        )
        assertTrue(
            "ImportTabContent must render GPX card with import_gpx_title",
            content.contains("R.string.import_gpx_title")
        )

        // 3. Action triad buttons exist for each format (Local, Folder, Dropbox)
        assertTrue(
            "FIT block must contain local file button",
            content.contains("R.string.import_fit_button")
        )
        assertTrue(
            "FIT block must contain folder scan button",
            content.contains("R.string.import_fit_folder_button")
        )
        assertTrue(
            "FIT block must contain Dropbox scan button",
            content.contains("R.string.import_fit_dropbox_button")
        )
        assertTrue(
            "TCX block must contain local file button",
            content.contains("R.string.import_tcx_button")
        )
        assertTrue(
            "TCX block must contain folder scan button",
            content.contains("R.string.import_tcx_folder_button")
        )
        assertTrue(
            "TCX block must contain Dropbox scan button",
            content.contains("R.string.import_tcx_dropbox_button")
        )
        assertTrue(
            "GPX block must contain local file button",
            content.contains("R.string.import_gpx_button")
        )
        assertTrue(
            "GPX block must contain folder scan button",
            content.contains("R.string.import_gpx_folder_button")
        )
        assertTrue(
            "GPX block must contain Dropbox scan button",
            content.contains("R.string.import_gpx_dropbox_button")
        )

        // 4. Folder scan integration & launcher contract
        assertTrue(
            "FormatImportCard must define folderButtonText parameter",
            content.contains("folderButtonText: String")
        )
        assertTrue(
            "FormatImportCard must define onFolderClick parameter",
            content.contains("onFolderClick: () -> Unit")
        )
        assertTrue(
            "ImportTabContent must define onFolderImportClick parameter",
            content.contains("onFolderImportClick: (format: String) -> Unit")
        )
        assertTrue(
            "Screen must trigger viewModel.importWorkoutDirectory",
            content.contains("viewModel.importWorkoutDirectory(context, uri, pendingDirectoryFormat)")
        )

        // 5. Google Drive scan buttons and state must be absent (REQ-UI-362 / ATT-3489)
        assertFalse(
            "FIT block must NOT contain Google Drive scan button",
            content.contains("R.string.import_fit_gdrive_button")
        )
        assertFalse(
            "TCX block must NOT contain Google Drive scan button",
            content.contains("R.string.import_tcx_gdrive_button")
        )
        assertFalse(
            "GPX block must NOT contain Google Drive scan button",
            content.contains("R.string.import_gpx_gdrive_button")
        )
        assertFalse(
            "Screen must NOT maintain pendingGoogleDriveFormat",
            content.contains("pendingGoogleDriveFormat")
        )
        assertFalse(
            "Screen must NOT maintain showTuningDialogForGoogleDrive",
            content.contains("showTuningDialogForGoogleDrive")
        )
        // Note: showGoogleDriveDisconnectedDialog is used by RestoreTabContent (REQ-MIG-036.13d, ATT-4664),
        // but ImportTabContent must not contain any Google Drive scan integration.
        val importTabContent = content.substringAfter("private fun ImportTabContent(")
        assertFalse(
            "ImportTabContent must NOT maintain or reference showGoogleDriveDisconnectedDialog",
            importTabContent.contains("showGoogleDriveDisconnectedDialog")
        )

        // 6. Cloud recovery confirmation passes scoped format for Dropbox and single legacy
        assertTrue(
            "Bulk recovery from Dropbox must pass pendingDropboxFormat",
            content.contains("viewModel.bulkRecoverLegacyData(context, pendingDropboxFormat)")
        )
        assertTrue(
            "Single legacy file import must pass pendingSingleLegacyFormat",
            content.contains("viewModel.importLegacyFile(context, uri, pendingSingleLegacyFormat)")
        )
    }

    @Test
    fun testImportTabContent_rendersStravaBulkExportCardWithActionTitle() {
        val file = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt")
        val content = file.readText()

        assertTrue(
            "ImportTabContent must render Strava card with import_strava_archive_title (REQ-UI-458, TST-UI-458)",
            content.contains("R.string.import_strava_archive_title")
        )
        assertTrue(
            "ImportTabContent must invoke StravaArchiveImportCard",
            content.contains("StravaArchiveImportCard(")
        )
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
        val regex = Regex("""<string name="import_strava_archive_title">(.*?)</string>""")

        for ((locale, expectedVerb) in expectedVerbsByLocale) {
            val file = findSourceFile("src/main/res/$locale/strings.xml")
            val content = file.readText()
            val match = regex.find(content)
            assertTrue("Locale '$locale' must define import_strava_archive_title", match != null)
            val title = match!!.groupValues[1].trim()
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
