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

package com.atrainingtracker.trainingtracker.zones

import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore.ZoneType
import com.atrainingtracker.trainingtracker.ui.settings.HrMethod
import com.atrainingtracker.trainingtracker.ui.settings.ZoneCalculatorStateHolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UI contract tests for the Zone Calculator dialog, verifying baseline pre-fill, reactive validation,
 * destructive overwrite modal flow, and UI mutation lock.
 *
 * Covers [TST-UI-336.6].
 */
class ZoneCalculatorDialogContractTest {

    @Test
    fun initialState_populatesStoredFtpOrFallback200W() {
        // Stored FTP present
        val holderWithFtp = ZoneCalculatorStateHolder(
            zoneType = ZoneType.PWR_BIKE,
            initialCustomized = false,
            initialFtp = 275
        )
        assertEquals("275", holderWithFtp.state.ftpInput)
        assertEquals(listOf(151, 206, 248, 289, 330, 413), holderWithFtp.state.calculatedZones)
        assertTrue(holderWithFtp.state.canApply)

        // Stored FTP absent -> defaults to 200W
        val holderNoFtp = ZoneCalculatorStateHolder(
            zoneType = ZoneType.PWR_BIKE,
            initialCustomized = false,
            initialFtp = null
        )
        assertEquals("200", holderNoFtp.state.ftpInput)
        assertEquals(listOf(110, 150, 180, 210, 240, 300), holderNoFtp.state.calculatedZones)
        assertTrue(holderNoFtp.state.canApply)
    }

    @Test
    fun onApplyClicked_whenCustomized_triggersOverwriteWarningModal() {
        val holder = ZoneCalculatorStateHolder(
            zoneType = ZoneType.PWR_BIKE,
            initialCustomized = true,
            initialFtp = 250
        )

        var saveInvoked = false
        holder.onApplyClicked { saveInvoked = true }

        // Must show overwrite warning modal and NOT execute save yet
        assertTrue(holder.state.showOverwriteWarning)
        assertFalse(saveInvoked)
        assertFalse(holder.state.isSaving)

        // Dismissing warning cancels overwrite modal and does not invoke save
        holder.onDismissOverwriteWarning()
        assertFalse(holder.state.showOverwriteWarning)
        assertFalse(saveInvoked)

        // Confirming overwrite invokes save and engages mutation lock
        holder.onConfirmOverwrite { saveInvoked = true }
        assertTrue(saveInvoked)
        assertTrue(holder.state.isSaving)
        assertFalse(holder.state.showOverwriteWarning)
        assertFalse(holder.state.canApply) // Locked while saving

        // Completing save unlocks and resets customized
        holder.onSaveCompleted()
        assertFalse(holder.state.isSaving)
        assertFalse(holder.state.isCustomized)
    }

    @Test
    fun onApplyClicked_whenNotCustomized_savesDirectlyWithoutModal() {
        val holder = ZoneCalculatorStateHolder(
            zoneType = ZoneType.PWR_BIKE,
            initialCustomized = false,
            initialFtp = 250
        )

        var savedZones: List<Int>? = null
        holder.onApplyClicked { savedZones = it }

        assertFalse(holder.state.showOverwriteWarning)
        assertTrue(holder.state.isSaving)
        assertEquals(listOf(138, 188, 225, 263, 300, 375), savedZones)
    }

    @Test
    fun reactiveValidation_karvonenSafetyInvariants() {
        val holder = ZoneCalculatorStateHolder(
            zoneType = ZoneType.HR_BIKE,
            initialCustomized = false,
            initialHrMax = 190,
            initialHrRest = 50,
            initialHrMethod = HrMethod.KARVONEN
        )
        assertTrue(holder.state.isValid)
        assertNotNull(holder.state.calculatedZones)

        // Karvonen delta < 20 bpm (e.g. max 95, rest 80 -> delta 15)
        holder.onHrMaxChanged("95")
        holder.onHrRestChanged("80")
        assertEquals(R.string.zone_calc_error_karvonen_delta, holder.state.hrGeneralErrorResId)
        assertFalse(holder.state.canApply)
        assertNull(holder.state.calculatedZones)

        // HRmax <= HRrest (e.g. max 80, rest 80)
        holder.onHrMaxChanged("80")
        holder.onHrRestChanged("80")
        assertEquals(R.string.zone_calc_error_hr_max_greater_rest, holder.state.hrGeneralErrorResId)
        assertFalse(holder.state.canApply)

        // Switch to % HRmax method -> removes HRrest requirement and restores calculation
        holder.onHrMethodChanged(HrMethod.MAX_HR)
        holder.onHrMaxChanged("180")
        assertNull(holder.state.hrGeneralErrorResId)
        assertTrue(holder.state.canApply)
        assertEquals(listOf(106, 124, 142, 160), holder.state.calculatedZones)
    }

    @Test
    fun mutationLock_disablesCanApplyWhileSaving() {
        val holder = ZoneCalculatorStateHolder(
            zoneType = ZoneType.PWR_BIKE,
            initialCustomized = false,
            initialFtp = 250
        )
        assertTrue(holder.state.canApply)

        holder.onApplyClicked { }
        assertTrue(holder.state.isSaving)
        assertFalse(holder.state.canApply)
    }
}
