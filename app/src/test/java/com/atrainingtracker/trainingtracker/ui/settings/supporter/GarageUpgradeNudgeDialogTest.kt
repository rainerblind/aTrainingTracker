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

package com.atrainingtracker.trainingtracker.ui.settings.supporter

import com.atrainingtracker.trainingtracker.monetization.GarageUpgradeNudgeStringsAuditTest
import org.junit.Test

/**
 * UI / Supporter Dialog verification suite ensuring GarageUpgradeNudgeDialog strings
 * comply with design guidelines and contract constraints (TST-SET-088.7).
 */
class GarageUpgradeNudgeDialogTest {

    private val auditTest = GarageUpgradeNudgeStringsAuditTest()

    @Test
    fun testGarageDialogStrings_zeroEmojisAcrossAllLocales() {
        auditTest.setUp()
        auditTest.testGarageUpgradeNudgeStrings_zeroEmojisAcrossAllNineLocales()
    }

    @Test
    fun testGarageDialogStrings_formatSpecifiersIntact() {
        auditTest.setUp()
        auditTest.testGarageUpgradeNudgeMessages_preservePositionalFormatSpecifiersAcrossAllNineLocales()
    }
}
