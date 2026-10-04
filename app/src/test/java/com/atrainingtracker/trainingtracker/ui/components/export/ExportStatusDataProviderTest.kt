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

import android.content.Context
import android.content.res.Resources
import com.atrainingtracker.trainingtracker.exporter.ExportStatus
import com.atrainingtracker.trainingtracker.exporter.ExportType
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.exporter.db.ExportStatusDatabaseManager
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying ExportStatusDataProvider missing format synthesis (FIT with UNWANTED)
 * and detailed status reporting (REQ-EXP-018, TST-EXP-015.3, TST-EXP-015.4, ATT-2310).
 */
class ExportStatusDataProviderTest {

    private lateinit var mockContext: Context
    private lateinit var mockResources: Resources
    private lateinit var mockDbManager: ExportStatusDatabaseManager
    private lateinit var provider: ExportStatusDataProvider

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        mockResources = mockk(relaxed = true)
        mockDbManager = mockk(relaxed = true)

        every { mockContext.applicationContext } returns mockContext
        every { mockContext.resources } returns mockResources
        every { mockContext.getString(any()) } answers { "String_${firstArg<Int>()}" }
        every { mockResources.getQuantityString(any(), any(), *anyVararg()) } answers { "Quantity_${firstArg<Int>()}" }

        ExportStatusDatabaseManager.resetForTesting(mockDbManager)
        provider = ExportStatusDataProvider(mockContext)
    }

    @After
    fun tearDown() {
        ExportStatusDatabaseManager.resetForTesting(null)
        unmockkAll()
    }

    @Test
    fun testCreateGroupData_whenLegacyRowsMissingFit_synthesizesFitWithUnwantedStatus() {
        val fileBaseName = "2026-10-04-12-00-00"

        // Mock database returning legacy formats only (missing FIT)
        val legacyRows = listOf(
            ExportStatusDatabaseManager.ExportRow(
                ExportType.FILE,
                FileFormat.GPX,
                ExportStatus.FINISHED_SUCCESS,
                "OK"
            ),
            ExportStatusDatabaseManager.ExportRow(
                ExportType.FILE,
                FileFormat.TCX,
                ExportStatus.WAITING,
                null
            )
        )
        every { mockDbManager.getExportRows(fileBaseName) } returns legacyRows

        val groupData = provider.createGroupData(fileBaseName, ExportType.FILE)

        assertTrue("Group data must have content", groupData.hasContent)
        assertNotNull("Details must not be null", groupData.details)

        // Find synthesized FIT entry
        val fitDetail = groupData.details.firstOrNull { it.formatName == "String_${FileFormat.FIT.uiNameId}" }
        assertNotNull("FIT detail must be synthesized when absent from legacy database rows", fitDetail)
        assertEquals("FIT status must be UNWANTED", "String_${ExportStatus.UNWANTED.uiNameId}", fitDetail?.status)

        // Verify order corresponds to ExportType.FILE.exportToFileFormats
        val expectedFormatOrder = ExportType.FILE.exportToFileFormats.map { "String_${it.uiNameId}" }
        val actualFormatOrder = groupData.details.map { it.formatName }
        assertEquals("Export details must follow ExportType.FILE.exportToFileFormats canonical order",
            expectedFormatOrder, actualFormatOrder)
    }

    @Test
    fun testCreateGroupData_whenFitRowPresent_usesActualDatabaseStatus() {
        val fileBaseName = "2026-10-04-12-00-00"

        val rowsWithFit = listOf(
            ExportStatusDatabaseManager.ExportRow(
                ExportType.FILE,
                FileFormat.FIT,
                ExportStatus.FINISHED_SUCCESS,
                "Exported 4096 bytes"
            ),
            ExportStatusDatabaseManager.ExportRow(
                ExportType.FILE,
                FileFormat.GPX,
                ExportStatus.WAITING,
                null
            )
        )
        every { mockDbManager.getExportRows(fileBaseName) } returns rowsWithFit

        val groupData = provider.createGroupData(fileBaseName, ExportType.FILE)

        assertTrue("Group data must have content", groupData.hasContent)
        val fitDetail = groupData.details.firstOrNull { it.formatName == "String_${FileFormat.FIT.uiNameId}" }
        assertNotNull("FIT detail must be present", fitDetail)
        assertEquals("FIT status must reflect DB status", "String_${ExportStatus.FINISHED_SUCCESS.uiNameId}", fitDetail?.status)
        assertEquals("FIT answer must match DB answer", "Exported 4096 bytes", fitDetail?.answer)
    }

    @Test
    fun testCreateGroupData_whenNoRowsForExportType_returnsHasContentFalse() {
        val fileBaseName = "2026-10-04-12-00-00"
        every { mockDbManager.getExportRows(fileBaseName) } returns emptyList()

        val groupData = provider.createGroupData(fileBaseName, ExportType.FILE)

        assertFalse("Group data must have no content when no rows exist", groupData.hasContent)
    }
}
