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

package com.atrainingtracker.trainingtracker.ui.ant

import android.util.Log
import com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit and structural contract test verifying persistent suppression of the missing ANT adapter dialog
 * and Advanced Settings warning toggle (REQ-UI-353 / TST-UI-313 / ATT-3220).
 */
class AntMissingAdapterDialogSuppressionTest {

    private fun resolveSourceFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.exists()) return direct
        val fromRepoRoot = File(System.getProperty("user.dir", "."), relativePath)
        if (fromRepoRoot.exists()) return fromRepoRoot
        val parent = File("..", relativePath)
        if (parent.exists()) return parent
        throw IllegalStateException("Cannot resolve source file: $relativePath from ${File(".").absolutePath}")
    }

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun createMockActivity(
        suppressed: Boolean,
        loaded: Boolean = true,
        initialDialogState: AntDialogState? = null,
        showStatusSheet: Boolean = false
    ): Pair<MainActivityWithNavigation, () -> AntDialogState?> {
        val activity = mockk<MainActivityWithNavigation>(relaxed = true)
        every { activity.showANTAdapterMissingDialog() } answers { callOriginal() }
        every { activity.onAntMissingAdapterDialogDismissed() } answers { callOriginal() }
        every { activity.applyAntMissingAdapterSuppressionState(any()) } answers { callOriginal() }

        setBooleanField(activity, "isAntMissingAdapterDialogSuppressed", suppressed)
        setBooleanField(activity, "isAntMissingAdapterSuppressionLoaded", loaded)
        setBooleanField(activity, "hasPendingAntAdapterMissingBroadcast", false)
        setBooleanField(activity, "isPersistingAntMissingAdapterSuppression", false)

        var dialogState: AntDialogState? = initialDialogState
        every { activity.antDialogState } answers { dialogState }
        every { activity.antDialogState = any() } answers { dialogState = firstArg() }
        every { activity.showAntStatusSheet } returns showStatusSheet

        return activity to { dialogState }
    }

    private fun setBooleanField(target: Any, name: String, value: Boolean) {
        val field = MainActivityWithNavigation::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.setBoolean(target, value)
    }

    private fun getBooleanField(target: Any, name: String): Boolean {
        val field = MainActivityWithNavigation::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.getBoolean(target)
    }

    @Test
    fun testShowANTAdapterMissingDialog_whenUnsuppressed_showsMissingAdapterDialog() {
        val (activity, getDialogState) = createMockActivity(suppressed = false, loaded = true)

        activity.showANTAdapterMissingDialog()

        assertEquals(AntDialogState.MissingAdapter, getDialogState())
    }

    @Test
    fun testShowANTAdapterMissingDialog_whenSuppressed_discardsBroadcast() {
        val (activity, getDialogState) = createMockActivity(suppressed = true, loaded = true)

        activity.showANTAdapterMissingDialog()

        assertNull("antDialogState must remain null when suppressed", getDialogState())
    }

    @Test
    fun testShowANTAdapterMissingDialog_whenAlreadyShowing_doesNotOverwriteExistingState() {
        val existingState = AntDialogState.MissingDependency("ANT Radio Service", "com.dsi.ant.service.socket")
        val (activity, getDialogState) = createMockActivity(
            suppressed = false,
            loaded = true,
            initialDialogState = existingState
        )

        activity.showANTAdapterMissingDialog()

        assertEquals(existingState, getDialogState())
    }

    @Test
    fun testOnAntMissingAdapterDialogDismissed_blocksImmediateRepeatedBroadcastsBeforeDataStoreCompletes() {
        val (activity, getDialogState) = createMockActivity(
            suppressed = false,
            loaded = true,
            initialDialogState = AntDialogState.MissingAdapter
        )

        activity.onAntMissingAdapterDialogDismissed()

        assertNull("Dismissal must close MissingAdapter immediately", getDialogState())
        assertTrue(
            "Dismissal must synchronously mark dialog suppressed",
            getBooleanField(activity, "isAntMissingAdapterDialogSuppressed")
        )

        // Simulate immediate back-to-back ADAPTER_NOT_DETECTED broadcasts before DataStore write finishes
        repeat(3) {
            activity.showANTAdapterMissingDialog()
            assertNull("Back-to-back broadcast #$it must not reopen dismissed dialog", getDialogState())
        }

        // Simulate stale in-flight `false` emission from DataStore arriving before write commits `true`
        activity.applyAntMissingAdapterSuppressionState(false)
        assertTrue(
            "Stale in-flight false emission must not clear pending dismissal suppression",
            getBooleanField(activity, "isAntMissingAdapterDialogSuppressed")
        )
        activity.showANTAdapterMissingDialog()
        assertNull("Broadcast after stale false emission must still be suppressed", getDialogState())

        // Once DataStore emits `true`, pending write flag clears and Tuning re-enablement works
        activity.applyAntMissingAdapterSuppressionState(true)
        assertFalse(getBooleanField(activity, "isPersistingAntMissingAdapterSuppression"))
        activity.applyAntMissingAdapterSuppressionState(false)
        assertFalse(getBooleanField(activity, "isAntMissingAdapterDialogSuppressed"))
        activity.showANTAdapterMissingDialog()
        assertEquals(AntDialogState.MissingAdapter, getDialogState())
    }

    @Test
    fun testColdStartBroadcast_beforeHydration_respectsPersistedTrueSuppression() {
        val (activity, getDialogState) = createMockActivity(suppressed = false, loaded = false)

        activity.showANTAdapterMissingDialog()
        assertNull("Pre-hydration broadcast must not show dialog prematurely", getDialogState())
        assertTrue(getBooleanField(activity, "hasPendingAntAdapterMissingBroadcast"))

        activity.applyAntMissingAdapterSuppressionState(true)
        assertNull("Persisted true suppression must discard deferred cold-start broadcast", getDialogState())
        assertFalse(getBooleanField(activity, "hasPendingAntAdapterMissingBroadcast"))
    }

    @Test
    fun testColdStartBroadcast_beforeHydration_flushesWhenPersistedFalse() {
        val (activity, getDialogState) = createMockActivity(suppressed = false, loaded = false)

        activity.showANTAdapterMissingDialog()
        assertNull("Pre-hydration broadcast must wait for initial DataStore value", getDialogState())

        activity.applyAntMissingAdapterSuppressionState(false)
        assertEquals(
            "Persisted false must flush deferred cold-start broadcast once hydrated",
            AntDialogState.MissingAdapter,
            getDialogState()
        )
    }

    @Test
    fun testMainActivityWithNavigation_structuralSuppressionWiring() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt")
        val content = file.readText()

        assertTrue(
            "MainActivityWithNavigation must declare isAntMissingAdapterDialogSuppressed",
            content.contains("var isAntMissingAdapterDialogSuppressed")
        )
        assertTrue(
            "MainActivityWithNavigation must collect suppressAntMissingAdapterDialogFlow",
            content.contains("suppressAntMissingAdapterDialogFlow.collect")
        )
        assertTrue(
            "showANTAdapterMissingDialog must guard with isAntMissingAdapterDialogSuppressed",
            content.contains("isAntMissingAdapterDialogSuppressed")
        )
    }

    @Test
    fun testATrainingTrackerApp_structuralDismissalPersistenceWiring() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/ATrainingTrackerApp.kt")
        val content = file.readText()

        assertTrue(
            "ATrainingTrackerApp must setSuppressAntMissingAdapterDialog(true) on dismiss",
            content.contains("setSuppressAntMissingAdapterDialog(true)")
        )
        val missingAdapterBranch = content.substringAfter("is AntDialogState.MissingAdapter ->")
            .substringBefore("is AntDialogState.MissingDependency ->")
        assertFalse(
            "MissingAdapter branch must NOT create a branch-local rememberCoroutineScope() that cancels on dismiss",
            missingAdapterBranch.contains("rememberCoroutineScope")
        )
    }

    @Test
    fun testSensorSearchTuningSection_structuralToggleWiring() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/SensorSearchTuningSection.kt")
        val content = file.readText()

        assertTrue(
            "SensorSearchTuningSection must declare showAntMissingAdapterWarnings parameter",
            content.contains("showAntMissingAdapterWarnings: Boolean")
        )
        assertTrue(
            "SensorSearchTuningSection must declare onShowAntMissingAdapterWarningsChange callback",
            content.contains("onShowAntMissingAdapterWarningsChange: (Boolean) -> Unit")
        )
        assertTrue(
            "SensorSearchTuningSection must bind tuning_show_ant_missing_adapter_warnings_title",
            content.contains("R.string.tuning_show_ant_missing_adapter_warnings_title")
        )
        assertTrue(
            "SensorSearchTuningSection must bind tuning_show_ant_missing_adapter_warnings_desc",
            content.contains("R.string.tuning_show_ant_missing_adapter_warnings_desc")
        )
    }

    @Test
    fun testSensorsGpsFilterSection_toggleRemoved() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/SensorsGpsFilterSection.kt")
        val content = file.readText()

        assertFalse(
            "SensorsGpsFilterSection must not declare showAntMissingAdapterWarnings (relocated to SensorSearchTuningSection)",
            content.contains("showAntMissingAdapterWarnings")
        )
        assertFalse(
            "SensorsGpsFilterSection must not bind tuning_show_ant_missing_adapter_warnings_title",
            content.contains("tuning_show_ant_missing_adapter_warnings_title")
        )
    }

    @Test
    fun testAdvancedTuningHosts_structuralBindingWiring() {
        val screenFile = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningScreen.kt")
        val screenContent = screenFile.readText()

        assertTrue(
            "AdvancedTuningScreen must hold showAntMissingAdapterWarnings state",
            screenContent.contains("var showAntMissingAdapterWarnings by remember")
        )
        assertTrue(
            "AdvancedTuningScreen must load showAntMissingAdapterWarnings from tuningConfig",
            screenContent.contains("config.showAntMissingAdapterWarnings")
        )
        assertTrue(
            "AdvancedTuningScreen must save inverted value to suppressAntMissingAdapterDialog",
            screenContent.contains("suppressAntMissingAdapterDialog = !showAntMissingAdapterWarnings")
        )
        assertTrue(
            "AdvancedTuningScreen must route showAntMissingAdapterWarnings to SensorSearchTuningSection",
            screenContent.contains("SensorSearchTuningSection(") &&
                screenContent.contains("showAntMissingAdapterWarnings = showAntMissingAdapterWarnings")
        )
    }
}
