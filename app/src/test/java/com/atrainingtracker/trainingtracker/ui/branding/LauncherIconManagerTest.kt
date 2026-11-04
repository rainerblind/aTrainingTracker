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

package com.atrainingtracker.trainingtracker.ui.branding

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.LifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.trainingtracker.monetization.SupporterTier
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class LauncherIconManagerTest {

    private lateinit var context: Context
    private lateinit var packageManager: PackageManager
    private lateinit var settingsDataStore: SettingsDataStore
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        packageManager = context.packageManager
        settingsDataStore = SettingsDataStore(context)
    }

    @Test
    fun testUnlockHierarchy() {
        // STANDARD is always unlocked for all tiers
        assertTrue(LauncherIconVariant.STANDARD.isUnlocked(SupporterTier.FREE, isPioneer = false))
        assertTrue(LauncherIconVariant.STANDARD.isUnlocked(SupporterTier.BRONZE, isPioneer = false))

        // PIONEER unlocked only for Pioneers or Diamond
        assertFalse(LauncherIconVariant.PIONEER.isUnlocked(SupporterTier.FREE, isPioneer = false))
        assertFalse(LauncherIconVariant.PIONEER.isUnlocked(SupporterTier.GOLD, isPioneer = false))
        assertTrue(LauncherIconVariant.PIONEER.isUnlocked(SupporterTier.FREE, isPioneer = true))
        assertTrue(LauncherIconVariant.PIONEER.isUnlocked(SupporterTier.PIONEER, isPioneer = false))
        assertTrue(LauncherIconVariant.PIONEER.isUnlocked(SupporterTier.DIAMOND, isPioneer = false))

        // Tiered icons unlock based on rank >= tier.rank
        assertFalse(LauncherIconVariant.BRONZE.isUnlocked(SupporterTier.FREE, isPioneer = false))
        assertTrue(LauncherIconVariant.BRONZE.isUnlocked(SupporterTier.BRONZE, isPioneer = false))
        assertTrue(LauncherIconVariant.BRONZE.isUnlocked(SupporterTier.SILVER, isPioneer = false))

        assertFalse(LauncherIconVariant.GOLD.isUnlocked(SupporterTier.SILVER, isPioneer = false))
        assertTrue(LauncherIconVariant.GOLD.isUnlocked(SupporterTier.GOLD, isPioneer = false))
        assertTrue(LauncherIconVariant.GOLD.isUnlocked(SupporterTier.DIAMOND, isPioneer = false))

        assertFalse(LauncherIconVariant.DIAMOND.isUnlocked(SupporterTier.GOLD, isPioneer = false))
        assertTrue(LauncherIconVariant.DIAMOND.isUnlocked(SupporterTier.DIAMOND, isPioneer = false))
    }

    @Test
    fun testApplyLauncherIconEnablesTargetAndDisablesOthersWithDontKillApp() {
        val mockPm = mockk<PackageManager>(relaxed = true)
        val mockContext = mockk<Context>()
        every { mockContext.packageManager } returns mockPm
        every { mockContext.packageName } returns "com.atrainingtracker"

        every { mockPm.getComponentEnabledSetting(any()) } returns PackageManager.COMPONENT_ENABLED_STATE_DEFAULT

        val manager = LauncherIconManager(
            context = mockContext,
            settingsDataStore = settingsDataStore,
            coroutineScope = testScope,
            ioDispatcher = testDispatcher,
            isTrackingProvider = { false }
        )

        val changed = manager.applyLauncherIcon(LauncherIconVariant.GOLD)
        assertTrue("State should report changed", changed)

        // Verify GOLD is enabled with DONT_KILL_APP
        val goldComponent = ComponentName("com.atrainingtracker", LauncherIconVariant.GOLD.aliasName)
        verify {
            mockPm.setComponentEnabledSetting(
                goldComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        }

        // Verify STANDARD is disabled with DONT_KILL_APP
        val standardComponent = ComponentName("com.atrainingtracker", LauncherIconVariant.STANDARD.aliasName)
        verify {
            mockPm.setComponentEnabledSetting(
                standardComponent,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    @Test
    fun testDeferredActivationSuppressedDuringActiveTracking() = runTest(testDispatcher) {
        var isTracking = true
        val mockPm = mockk<PackageManager>(relaxed = true)
        val mockContext = mockk<Context>()
        every { mockContext.packageManager } returns mockPm
        every { mockContext.packageName } returns "com.atrainingtracker"

        val mockSettingsDataStore = mockk<SettingsDataStore>()
        every { mockSettingsDataStore.selectedLauncherIcon } returns kotlinx.coroutines.flow.flowOf("gold")

        val manager = LauncherIconManager(
            context = mockContext,
            settingsDataStore = mockSettingsDataStore,
            coroutineScope = this,
            ioDispatcher = testDispatcher,
            isTrackingProvider = { isTracking }
        )

        val owner = mockk<LifecycleOwner>()

        // When tracking is active, onStop should NOT dispatch component updates
        manager.onStop(owner)
        testScheduler.advanceUntilIdle()

        verify(exactly = 0) {
            mockPm.setComponentEnabledSetting(any(), any(), any())
        }

        // When tracking is stopped, onStop triggers component updates
        isTracking = false
        every { mockPm.getComponentEnabledSetting(any()) } returns PackageManager.COMPONENT_ENABLED_STATE_DEFAULT

        manager.onStop(owner)
        testScheduler.advanceUntilIdle()

        verify(atLeast = 1) {
            mockPm.setComponentEnabledSetting(any(), any(), eq(PackageManager.DONT_KILL_APP))
        }
    }
}
