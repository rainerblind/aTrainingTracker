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

package com.atrainingtracker.trainingtracker.dialogs

import android.content.Context
import androidx.fragment.app.DialogFragment
import com.atrainingtracker.trainingtracker.interfaces.StartOrResumeInterface
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Contract tests verifying structural requirements, tag integrity, and callback enforcement
 * in [StartOrResumeDialog] (REQ-STB-012, REQ-STB-003, ATT-2079).
 */
class StartOrResumeDialogContractTest {

    @Test
    @Suppress("USELESS_IS_CHECK")
    fun testStartOrResumeDialog_inheritsDialogFragment() {
        val dialog = StartOrResumeDialog()
        assertTrue("StartOrResumeDialog must extend DialogFragment", dialog is DialogFragment)
        assertNotNull(dialog)
    }

    @Test
    fun testStartOrResumeDialog_tagMatchesClassName() {
        assertEquals(StartOrResumeDialog::class.java.name, StartOrResumeDialog.TAG)
    }

    @Test
    fun testAttachInterface_throwsClassCastExceptionWhenInterfaceNotImplemented() {
        val dialog = StartOrResumeDialog()
        val nonCompliantContext = mockk<Context>()

        try {
            dialog.attachInterface(nonCompliantContext)
            fail("Expected ClassCastException when context does not implement StartOrResumeInterface")
        } catch (e: ClassCastException) {
            assertTrue(e.message?.contains("must implement StartOrResumeInterface") == true)
        }
    }

    @Test
    fun testAttachInterface_succeedsWhenInterfaceImplemented() {
        val dialog = StartOrResumeDialog()
        val compliantContext = mockk<Context>(moreInterfaces = arrayOf(StartOrResumeInterface::class))

        dialog.attachInterface(compliantContext)
        assertNotNull(dialog.startOrResumeInterface)
    }
}
