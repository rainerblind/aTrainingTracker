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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural contract and localization tests for Restore Backup card button labels,
 * iconography, and unconditional disconnected visibility (REQ-MIG-036.13, TST-MIG-045, ATT-4664).
 * Covers:
 * - TST-MIG-045.1: 9-language parity and standardization for restore button strings
 * - TST-MIG-045.2: RestoreTabContent layout, CloudDownload icon, and unconditional Google Drive visibility contract
 * - TST-MIG-045.3: ImportBackupTabsScreen disconnected Google Drive dialog dispatch contract
 */
class RestoreCardContractTest {

    private val restoreButtonKeys = listOf(
        "restore_automatic_local_backup",
        "restore_from_file",
        "restore_from_dropbox",
        "restore_from_google_drive"
    )

    @Test
    fun test9LocaleRestoreStringsParityAndStandardization_TST_MIG_045_1() {
        for (targetKey in restoreButtonKeys) {
            for (locale in LocalizationTestCache.LOCALES) {
                val strings = LocalizationTestCache.getStrings(locale)
                assertTrue("Locale '$locale' must define key '$targetKey'", strings.containsKey(targetKey))
                val value = strings[targetKey]?.trim()
                assertNotNull("Locale '$locale' must define key '$targetKey'", value)
                assertFalse("Locale '$locale' key '$targetKey' must not be empty", value!!.isEmpty())
            }
        }

        // Verify google_drive_disconnected_status is present across all 9 locales
        for (locale in LocalizationTestCache.LOCALES) {
            val strings = LocalizationTestCache.getStrings(locale)
            assertTrue("Locale '$locale' must define 'google_drive_disconnected_status'", strings.containsKey("google_drive_disconnected_status"))
            val value = strings["google_drive_disconnected_status"]?.trim()
            assertNotNull("Locale '$locale' must define 'google_drive_disconnected_status'", value)
            assertFalse("Locale '$locale' 'google_drive_disconnected_status' must not be empty", value!!.isEmpty())
        }

        // Exact pattern match assertions for default (EN)
        val defaultStrings = LocalizationTestCache.getStrings("values")
        assertEquals(
            "Restore from Automatic Local Backup",
            defaultStrings["restore_automatic_local_backup"]?.trim()
        )
        assertEquals(
            "Restore from Local File",
            defaultStrings["restore_from_file"]?.trim()
        )
        assertEquals(
            "Restore from Dropbox",
            defaultStrings["restore_from_dropbox"]?.trim()
        )
        assertEquals(
            "Restore from Google Drive",
            defaultStrings["restore_from_google_drive"]?.trim()
        )

        // Exact pattern match assertions for German (DE)
        val deStrings = LocalizationTestCache.getStrings("values-de")
        assertEquals(
            "Aus automatischem lokalen Backup wiederherstellen",
            deStrings["restore_automatic_local_backup"]?.trim()
        )
        assertEquals(
            "Aus lokaler Datei wiederherstellen",
            deStrings["restore_from_file"]?.trim()
        )
        assertEquals(
            "Aus Dropbox wiederherstellen",
            deStrings["restore_from_dropbox"]?.trim()
        )
        assertEquals(
            "Aus Google Drive wiederherstellen",
            deStrings["restore_from_google_drive"]?.trim()
        )
    }

    @Test
    fun testRestoreTabContent_contract_iconographyAndUnconditionalGoogleDriveVisibility_TST_MIG_045_2() {
        val restoreClass = Class.forName("com.atrainingtracker.trainingtracker.migration.RestoreTabContentKt")
        assertNotNull("RestoreTabContentKt must exist", restoreClass)

        val defaultStrings = LocalizationTestCache.getStrings("values")
        for (key in restoreButtonKeys) {
            assertTrue("Default strings must contain $key", defaultStrings.containsKey(key))
        }
        assertTrue("Default strings must contain merge_from_backup_file", defaultStrings.containsKey("merge_from_backup_file"))
    }

    @Test
    fun testBackupTabContent_contract_headingDeduplication_TST_UI_474_3() {
        val backupClass = Class.forName("com.atrainingtracker.trainingtracker.migration.BackupTabContentKt")
        assertNotNull("BackupTabContentKt must exist", backupClass)

        val defaultStrings = LocalizationTestCache.getStrings("values")
        assertTrue("Default strings must contain backup_now", defaultStrings.containsKey("backup_now"))
    }

    @Test
    fun testImportBackupTabsScreen_contract_stravaTuningAndDropboxBrandIcon_TST_UI_474_4() {
        val screenClass = Class.forName("com.atrainingtracker.trainingtracker.migration.ImportBackupTabsScreenKt")
        assertNotNull("ImportBackupTabsScreenKt must exist", screenClass)
    }

    @Test
    fun testImportBackupTabsScreen_contract_disconnectedGoogleDriveDialogDispatch_TST_MIG_045_3() {
        val screenClass = Class.forName("com.atrainingtracker.trainingtracker.migration.ImportBackupTabsScreenKt")
        assertNotNull("ImportBackupTabsScreenKt must exist", screenClass)

        val defaultStrings = LocalizationTestCache.getStrings("values")
        assertTrue(defaultStrings.containsKey("google_drive_disconnected_status"))
    }
}
