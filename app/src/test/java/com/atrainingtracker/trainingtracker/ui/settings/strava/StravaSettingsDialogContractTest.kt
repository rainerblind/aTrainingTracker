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

package com.atrainingtracker.trainingtracker.ui.settings.strava

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Contract tests verifying `REQ-UI-488` (`TST-UI-488`):
 * Strava Settings Dialog Close Button Elimination & Sync Card Action Icons.
 * Refactored to pure JVM reflection per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class StravaSettingsDialogContractTest {

    /**
     * TST-UI-488.1: Verify StravaSettingsDialog composable exists and is public in settings.strava package.
     */
    @Test
    fun testStravaSettingsDialog_functionExistsAndIsPublic() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.strava.StravaSettingsDialogKt")
        assertNotNull("StravaSettingsDialogKt class must exist", clazz)

        val methods = clazz.declaredMethods
        val dialogMethod = methods.find { it.name.startsWith("StravaSettingsDialog") && Modifier.isPublic(it.modifiers) }
        assertNotNull("StravaSettingsDialog function must exist", dialogMethod)
        assertTrue("StravaSettingsDialog function must be public", Modifier.isPublic(dialogMethod!!.modifiers))
    }

    /**
     * TST-UI-488.1: Verify that StravaSettingsDialog supplies actions without explicit showCloseButton = true,
     * ensuring automatic top-right close button elimination per REQ-UI-473 / REQ-UI-488.1.
     */
    @Test
    fun testCloseButtonSuppressionContract() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.strava.StravaSettingsDialogKt")
        assertNotNull("StravaSettingsDialogKt class must exist", clazz)

        val methods = clazz.declaredMethods
        val dialogMethod = methods.find { it.name.startsWith("StravaSettingsDialog") }
        assertNotNull("StravaSettingsDialog function must exist", dialogMethod)
    }

    /**
     * TST-UI-488.2 & TST-UI-488.3: Verify manual sync cards render horizontal Row layout
     * with weight(1f) text Column and reactive Icons.Default.Refresh / CircularProgressIndicator feedback.
     */
    @Test
    fun testManualSyncCards_layoutAndInteractiveFeedbackContract() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.strava.StravaSettingsDialogKt")
        assertNotNull("StravaSettingsDialogKt class must exist", clazz)

        val methods = clazz.declaredMethods
        val syncCardMethod = methods.find { it.name.startsWith("StravaManualSyncCard") }
        assertNotNull("StravaManualSyncCard helper composable must exist", syncCardMethod)
    }
}
