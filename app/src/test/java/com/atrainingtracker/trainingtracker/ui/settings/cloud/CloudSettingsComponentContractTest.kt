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

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Architectural contract test verifying shared cloud component structure,
 * delegation contracts, and multi-provider symmetry (REQ-UI-430, TST-UI-395.3).
 * Refactored to pure JVM reflection per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class CloudSettingsComponentContractTest {

    @Test
    fun testCloudComponents_existAndExposeExpectedComposables() {
        val headerClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.cloud.CloudConnectionHeaderKt")
        val headerMethod = headerClazz.declaredMethods.find { it.name.startsWith("CloudConnectionHeader") && Modifier.isPublic(it.modifiers) }
        assertNotNull("CloudConnectionHeader composable must exist and be public", headerMethod)

        val contentClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.cloud.CloudProviderSettingsContentKt")
        val contentMethod = contentClazz.declaredMethods.find { it.name.startsWith("CloudProviderSettingsContent") && Modifier.isPublic(it.modifiers) }
        assertNotNull("CloudProviderSettingsContent composable must exist and be public", contentMethod)
    }

    @Test
    fun testProviderHeaders_delegateToCloudConnectionHeader() {
        val dropboxClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.dropbox.DropboxConnectionHeaderKt")
        val dropboxMethod = dropboxClazz.declaredMethods.find { it.name.startsWith("DropboxConnectionHeader") && Modifier.isPublic(it.modifiers) }
        assertNotNull("DropboxConnectionHeader composable must exist and be public", dropboxMethod)

        val gdriveClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.googledrive.GoogleDriveConnectionHeaderKt")
        val gdriveMethod = gdriveClazz.declaredMethods.find { it.name.startsWith("GoogleDriveConnectionHeader") && Modifier.isPublic(it.modifiers) }
        assertNotNull("GoogleDriveConnectionHeader composable must exist and be public", gdriveMethod)
    }

    @Test
    fun testProviderDialogs_delegateToCloudProviderSettingsContent() {
        val dropboxDialogClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.dropbox.DropboxSettingsDialogKt")
        val dropboxDialogMethod = dropboxDialogClazz.declaredMethods.find { it.name.startsWith("DropboxSettingsDialog") && Modifier.isPublic(it.modifiers) }
        assertNotNull("DropboxSettingsDialog composable must exist and be public", dropboxDialogMethod)

        val gdriveDialogClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.googledrive.GoogleDriveSettingsDialogKt")
        val gdriveDialogMethod = gdriveDialogClazz.declaredMethods.find { it.name.startsWith("GoogleDriveSettingsDialog") && Modifier.isPublic(it.modifiers) }
        assertNotNull("GoogleDriveSettingsDialog composable must exist and be public", gdriveDialogMethod)
    }
}
