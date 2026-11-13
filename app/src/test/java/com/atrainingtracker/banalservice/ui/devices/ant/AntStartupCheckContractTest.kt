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

package com.atrainingtracker.banalservice.ui.devices.ant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Contract test verifying cold-start elimination of ANT+ installation checks
 * and dialog popups (REQ-UI-296, TST-UI-256, ATT-2513).
 */
class AntStartupCheckContractTest {

    @Test
    fun testColdStartDoesNotPerformAntInstallationCheck() {
        val activityClass = Class.forName("com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation")
        assertNotNull("MainActivityWithNavigation class must exist", activityClass)

        val methodNames = activityClass.declaredMethods.map { it.name }
        org.junit.Assert.assertTrue(
            "MainActivityWithNavigation must declare observeNavigationEvents()",
            methodNames.contains("observeNavigationEvents")
        )
        assertFalse(
            "MainActivityWithNavigation must not declare checkANTInstallation()",
            methodNames.contains("checkANTInstallation")
        )
        assertFalse(
            "MainActivityWithNavigation must not declare showInstallANTShitDialog()",
            methodNames.contains("showInstallANTShitDialog")
        )
    }
}
