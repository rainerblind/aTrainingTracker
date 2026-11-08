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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural contract and localization tests for Restore Backup card button labels,
 * iconography, and unconditional disconnected visibility (REQ-MIG-036.13, TST-MIG-045, ATT-4664).
 * Covers:
 * - TST-MIG-045.1: 9-language parity and standardization for restore button strings
 * - TST-MIG-045.2: RestoreTabContent layout, CloudDownload icon, and unconditional Google Drive visibility contract
 * - TST-MIG-045.3: ImportBackupTabsScreen disconnected Google Drive dialog dispatch contract
 */
class RestoreCardContractTest {

    private val locales = listOf(
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

    private val restoreButtonKeys = listOf(
        "restore_automatic_local_backup",
        "restore_from_file",
        "restore_from_dropbox",
        "restore_from_google_drive"
    )

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

    private fun extractStringResource(content: String, key: String): String? {
        val regex = Regex("""<string name="$key">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        return regex.find(content)?.groupValues?.get(1)?.trim()
    }

    @Test
    fun test9LocaleRestoreStringsParityAndStandardization_TST_MIG_045_1() {
        for (targetKey in restoreButtonKeys) {
            for (locale in locales) {
                val file = findSourceFile("src/main/res/$locale/strings.xml")
                val content = file.readText()
                val value = extractStringResource(content, targetKey)

                assertNotNull("Locale '$locale' must define key '$targetKey'", value)
                assertFalse("Locale '$locale' key '$targetKey' must not be empty", value!!.isEmpty())
            }
        }

        // Verify google_drive_disconnected_status is present across all 9 locales
        for (locale in locales) {
            val file = findSourceFile("src/main/res/$locale/strings.xml")
            val content = file.readText()
            val value = extractStringResource(content, "google_drive_disconnected_status")

            assertNotNull("Locale '$locale' must define 'google_drive_disconnected_status'", value)
            assertFalse("Locale '$locale' 'google_drive_disconnected_status' must not be empty", value!!.isEmpty())
        }

        // Exact pattern match assertions for default (EN)
        val defaultContent = findSourceFile("src/main/res/values/strings.xml").readText()
        assertEquals(
            "Restore from Automatic Local Backup",
            extractStringResource(defaultContent, "restore_automatic_local_backup")
        )
        assertEquals(
            "Restore from Local File",
            extractStringResource(defaultContent, "restore_from_file")
        )
        assertEquals(
            "Restore from Dropbox",
            extractStringResource(defaultContent, "restore_from_dropbox")
        )
        assertEquals(
            "Restore from Google Drive",
            extractStringResource(defaultContent, "restore_from_google_drive")
        )

        // Exact pattern match assertions for German (DE)
        val deContent = findSourceFile("src/main/res/values-de/strings.xml").readText()
        assertEquals(
            "Aus automatischem lokalen Backup wiederherstellen",
            extractStringResource(deContent, "restore_automatic_local_backup")
        )
        assertEquals(
            "Aus lokaler Datei wiederherstellen",
            extractStringResource(deContent, "restore_from_file")
        )
        assertEquals(
            "Aus Dropbox wiederherstellen",
            extractStringResource(deContent, "restore_from_dropbox")
        )
        assertEquals(
            "Aus Google Drive wiederherstellen",
            extractStringResource(deContent, "restore_from_google_drive")
        )
    }

    @Test
    fun testRestoreTabContent_contract_iconographyAndUnconditionalGoogleDriveVisibility_TST_MIG_045_2() {
        val file = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/migration/RestoreTabContent.kt")
        val content = file.readText()

        // 1. All 4 standardized string resources referenced
        for (key in restoreButtonKeys) {
            assertTrue(
                "RestoreTabContent must reference R.string.$key",
                content.contains("R.string.$key")
            )
        }

        // 2. Dropbox restore uses authentic R.drawable.dropbox_logo_blue and not Icons.Default.CloudUpload
        assertFalse(
            "RestoreTabContent must NOT use Icons.Default.CloudUpload",
            content.contains("Icons.Default.CloudUpload")
        )

        // Check Dropbox button block has dropbox_logo_blue
        val dropboxBlock = content.substringAfter("// 3. Dropbox Restore").substringBefore("// 4. Google Drive Restore")
        assertTrue(
            "Dropbox restore block must use R.drawable.dropbox_logo_blue",
            dropboxBlock.contains("R.drawable.dropbox_logo_blue")
        )
        assertTrue(
            "Dropbox restore block must reference restore_from_dropbox",
            dropboxBlock.contains("R.string.restore_from_dropbox")
        )

        // 3. Google Drive restore button is rendered unconditionally (no `if (isGoogleDriveConnected)` wrapping the button)
        val gdriveBlock = content.substringAfter("// 4. Google Drive Restore")
        assertFalse(
            "Google Drive restore button must NOT be guarded by `if (isGoogleDriveConnected)`",
            gdriveBlock.contains("if (isGoogleDriveConnected)")
        )
        assertTrue(
            "Google Drive restore block must use googleDriveRestoreColors",
            gdriveBlock.contains("colors = googleDriveRestoreColors")
        )
        assertTrue(
            "Google Drive restore block must use googleDriveRestoreBorder",
            gdriveBlock.contains("border = googleDriveRestoreBorder")
        )
        assertTrue(
            "Google Drive restore block must use R.drawable.ic_google_drive",
            gdriveBlock.contains("R.drawable.ic_google_drive")
        )
        assertTrue(
            "Google Drive restore block must reference restore_from_google_drive",
            gdriveBlock.contains("R.string.restore_from_google_drive")
        )

        // 4. Incremental Import block uses Icons.Default.CallMerge and merge_from_backup_file (REQ-UI-474.2 / REQ-UI-474.3)
        val incrementalBlock = content.substringAfter("// --- Import Card").substringBefore("// --- Restore Card")
        assertTrue(
            "Incremental import block must use Icons.Default.CallMerge",
            incrementalBlock.contains("Icons.Default.CallMerge")
        )
        assertTrue(
            "Incremental import block must reference merge_from_backup_file",
            incrementalBlock.contains("R.string.merge_from_backup_file")
        )
    }

    @Test
    fun testBackupTabContent_contract_headingDeduplication_TST_UI_474_3() {
        val file = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/migration/BackupTabContent.kt")
        val content = file.readText()

        assertTrue(
            "BackupTabContent must use R.string.backup_now on backup button to deduplicate from create_backup card title",
            content.contains("R.string.backup_now")
        )
    }

    @Test
    fun testImportBackupTabsScreen_contract_stravaTuningAndDropboxBrandIcon_TST_UI_474_4() {
        val file = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt")
        val content = file.readText()

        assertTrue(
            "ImportBackupTabsScreen must declare pendingStravaArchiveUri state",
            content.contains("var pendingStravaArchiveUri by remember { mutableStateOf<Uri?>(null) }")
        )
        assertTrue(
            "FormatImportCard must render R.drawable.dropbox_logo_blue for Dropbox scan button",
            content.contains("R.drawable.dropbox_logo_blue")
        )
        assertTrue(
            "PreImportTuningBottomSheet must support showStravaUploadOption parameter",
            content.contains("showStravaUploadOption: Boolean = true")
        )
    }

    @Test
    fun testImportBackupTabsScreen_contract_disconnectedGoogleDriveDialogDispatch_TST_MIG_045_3() {
        val file = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt")
        val content = file.readText()

        // 1. showGoogleDriveDisconnectedDialog state exists
        assertTrue(
            "ImportBackupTabsScreen must declare showGoogleDriveDisconnectedDialog state",
            content.contains("var showGoogleDriveDisconnectedDialog by remember { mutableStateOf(false) }")
        )

        // 2. onGoogleDriveRestoreClick checks isGoogleDriveConnected and dispatches dialogs
        val callbackRegex = Regex(
            """onGoogleDriveRestoreClick\s*=\s*\{\s*if\s*\(\s*isGoogleDriveConnected\s*\)\s*\{\s*showGoogleDriveRestoreConfirm\s*=\s*true\s*\}\s*else\s*\{\s*showGoogleDriveDisconnectedDialog\s*=\s*true\s*\}\s*\}""",
            RegexOption.DOT_MATCHES_ALL
        )
        assertTrue(
            "onGoogleDriveRestoreClick must branch on isGoogleDriveConnected to set confirm or disconnected dialog",
            callbackRegex.containsMatchIn(content)
        )

        // 3. showGoogleDriveDisconnectedDialog AlertDialog renders google_drive_disconnected_status
        val dialogRegex = Regex(
            """if\s*\(\s*showGoogleDriveDisconnectedDialog\s*\)\s*\{.*?AlertDialog\(.*?R\.string\.google_drive_disconnected_status""",
            RegexOption.DOT_MATCHES_ALL
        )
        assertTrue(
            "ImportBackupTabsScreen must render AlertDialog with google_drive_disconnected_status when showGoogleDriveDisconnectedDialog is true",
            dialogRegex.containsMatchIn(content)
        )
    }
}
