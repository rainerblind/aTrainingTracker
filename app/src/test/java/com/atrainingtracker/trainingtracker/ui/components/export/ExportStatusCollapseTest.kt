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

package com.atrainingtracker.trainingtracker.ui.components.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit and contract verification suite for `ATT-3938` (`REQ-UI-407`, `TST-UI-370`):
 * Smart auto-collapse (`isExpanded = false`) and inline expansion of `ExportStatus`.
 */
class ExportStatusCollapseTest {

    private fun resolveProjectFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.exists()) return direct
        val fromAppParent = File("..", relativePath)
        if (fromAppParent.exists()) return fromAppParent
        return direct
    }

    @Test
    fun classificationProperties_identifyFullySucceededAndAttentionGroups() {
        val succeededGroup = ExportStatusGroupData(
            hasContent = true,
            groupTitle = "Dropbox",
            succeededLine = "✅ Successfully uploaded the TCX and FIT files to Dropbox."
        )
        assertTrue(succeededGroup.isFullySucceeded)
        assertFalse(succeededGroup.needsAttentionOrInProgress)

        val mixedFailedGroup = ExportStatusGroupData(
            hasContent = true,
            groupTitle = "Communities",
            succeededLine = "✅ Successfully uploaded to Runkeeper.",
            failedLine = "❌ Uploading to Strava failed."
        )
        assertFalse(mixedFailedGroup.isFullySucceeded)
        assertTrue(mixedFailedGroup.needsAttentionOrInProgress)

        val waitingGroup = ExportStatusGroupData(
            hasContent = true,
            groupTitle = "Google Drive",
            waitingLine = "⏳ Waiting to upload the FIT file to Google Drive."
        )
        assertFalse(waitingGroup.isFullySucceeded)
        assertTrue(waitingGroup.needsAttentionOrInProgress)

        val runningGroup = ExportStatusGroupData(
            hasContent = true,
            groupTitle = "File",
            runningLine = "💾 Writing the GPX file."
        )
        assertFalse(runningGroup.isFullySucceeded)
        assertTrue(runningGroup.needsAttentionOrInProgress)

        val emptyGroup = ExportStatusGroupData(hasContent = false)
        assertFalse(emptyGroup.isFullySucceeded)
        assertFalse(emptyGroup.needsAttentionOrInProgress)
    }

    @Test
    fun formatCollapsedSucceededSummary_and_filterAttentionGroups_handleAllCases() {
        val fileOk = ExportStatusGroupData(
            hasContent = true,
            groupTitle = "File",
            succeededLine = "✅ Successfully wrote the CSV, GPX, TCX, and FIT files."
        )
        val dropboxOk = ExportStatusGroupData(
            hasContent = true,
            groupTitle = "Dropbox",
            succeededLine = "✅ Successfully uploaded the CSV, GPX, TCX, and FIT files to Dropbox."
        )
        val communitiesOk = ExportStatusGroupData(
            hasContent = true,
            groupTitle = "Communities",
            succeededLine = "✅ Successfully uploaded to Strava."
        )

        // Case 1: All active export groups succeeded -> 1-line summary, zero attention groups
        val allSucceeded = listOf(fileOk, dropboxOk, communitiesOk)
        assertEquals(
            "✅ File, Dropbox, Communities",
            formatCollapsedSucceededSummary(allSucceeded)
        )
        assertTrue(filterAttentionGroups(allSucceeded).isEmpty())

        // Case 2: File & Dropbox succeeded, Communities failed -> partial summary + attention group surfaced
        val communitiesFailed = ExportStatusGroupData(
            hasContent = true,
            groupTitle = "Communities",
            failedLine = "❌ Uploading to Strava failed."
        )
        val mixedList = listOf(fileOk, dropboxOk, communitiesFailed)
        assertEquals(
            "✅ File, Dropbox",
            formatCollapsedSucceededSummary(mixedList)
        )
        val attention = filterAttentionGroups(mixedList)
        assertEquals(1, attention.size)
        assertEquals("Communities", attention.first().groupTitle)

        // Case 3: No group fully succeeded yet -> summary is null, all in-progress groups surfaced
        assertNull(formatCollapsedSucceededSummary(listOf(communitiesFailed)))
    }

    @Test
    fun exportStatusGroupKt_containsSmartCollapseAndAnimatedVisibilityContract() {
        val file = resolveProjectFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/export/ExportStatusGroup.kt")
        assertTrue("ExportStatusGroup.kt must exist", file.exists())
        val content = file.readText()

        assertTrue("Must use AnimatedVisibility for inline expansion", content.contains("AnimatedVisibility(visible = isExpanded)"))
        assertTrue("Must use formatCollapsedSucceededSummary", content.contains("formatCollapsedSucceededSummary(activeExports)"))
        assertTrue("Must use filterAttentionGroups", content.contains("filterAttentionGroups(activeExports)"))
        assertTrue("Must use ExpandMore and ExpandLess chevrons", content.contains("Icons.Default.ExpandMore") && content.contains("Icons.Default.ExpandLess"))
        assertTrue("Must preserve ExportDetailsDialog", content.contains("fun ExportDetailsDialog("))
    }
}
