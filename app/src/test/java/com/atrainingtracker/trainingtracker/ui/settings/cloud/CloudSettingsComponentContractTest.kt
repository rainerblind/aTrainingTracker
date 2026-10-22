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

package com.atrainingtracker.trainingtracker.ui.settings.cloud

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural contract test verifying shared cloud component structure,
 * delegation contracts, and multi-provider symmetry (REQ-UI-430, TST-UI-395.3).
 */
class CloudSettingsComponentContractTest {

    private fun resolveSourceFile(relativePath: String): File {
        val projectDir = File(System.getProperty("user.dir") ?: ".")
        val candidates = listOf(
            File(projectDir, "src/main/java/$relativePath"),
            File(projectDir, "app/src/main/java/$relativePath"),
            File(projectDir, "../app/src/main/java/$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: File(projectDir, "app/src/main/java/$relativePath")
    }

    @Test
    fun testCloudComponents_existAndExposeExpectedComposables() {
        val headerFile = resolveSourceFile("com/atrainingtracker/trainingtracker/ui/settings/cloud/CloudConnectionHeader.kt")
        assertTrue("CloudConnectionHeader.kt must exist", headerFile.exists())
        val headerContent = headerFile.readText()
        assertTrue("Must declare CloudConnectionHeader composable", headerContent.contains("fun CloudConnectionHeader("))

        val contentFile = resolveSourceFile("com/atrainingtracker/trainingtracker/ui/settings/cloud/CloudProviderSettingsContent.kt")
        assertTrue("CloudProviderSettingsContent.kt must exist", contentFile.exists())
        val content = contentFile.readText()
        assertTrue("Must declare CloudProviderSettingsContent composable", content.contains("fun CloudProviderSettingsContent("))
    }

    @Test
    fun testProviderHeaders_delegateToCloudConnectionHeader() {
        val dropboxHeader = resolveSourceFile("com/atrainingtracker/trainingtracker/ui/settings/dropbox/DropboxConnectionHeader.kt")
        assertTrue("DropboxConnectionHeader.kt must exist", dropboxHeader.exists())
        assertTrue("DropboxConnectionHeader must delegate to CloudConnectionHeader", dropboxHeader.readText().contains("CloudConnectionHeader("))

        val googleDriveHeader = resolveSourceFile("com/atrainingtracker/trainingtracker/ui/settings/googledrive/GoogleDriveConnectionHeader.kt")
        assertTrue("GoogleDriveConnectionHeader.kt must exist", googleDriveHeader.exists())
        assertTrue("GoogleDriveConnectionHeader must delegate to CloudConnectionHeader", googleDriveHeader.readText().contains("CloudConnectionHeader("))
    }

    @Test
    fun testProviderDialogs_delegateToCloudProviderSettingsContent() {
        val dropboxDialog = resolveSourceFile("com/atrainingtracker/trainingtracker/ui/settings/dropbox/DropboxSettingsDialog.kt")
        assertTrue("DropboxSettingsDialog.kt must exist", dropboxDialog.exists())
        assertTrue("DropboxSettingsDialog must use CloudProviderSettingsContent", dropboxDialog.readText().contains("CloudProviderSettingsContent("))

        val googleDriveDialog = resolveSourceFile("com/atrainingtracker/trainingtracker/ui/settings/googledrive/GoogleDriveSettingsDialog.kt")
        assertTrue("GoogleDriveSettingsDialog.kt must exist", googleDriveDialog.exists())
        assertTrue("GoogleDriveSettingsDialog must use CloudProviderSettingsContent", googleDriveDialog.readText().contains("CloudProviderSettingsContent("))
    }
}
