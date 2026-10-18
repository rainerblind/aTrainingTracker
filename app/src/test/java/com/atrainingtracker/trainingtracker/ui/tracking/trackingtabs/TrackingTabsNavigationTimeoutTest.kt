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

package com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs

import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for Cockpit tab navigation timeout resilience and lifecycle/context safety
 * (REQ-UI-410 / TST-UI-373 / ATT-4000).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrackingTabsNavigationTimeoutTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    /**
     * TST-UI-373.1: Verifies that when a `NavigateTo` event targets an out-of-range page index
     * and the 2000 ms timeout elapses without `pageCount` exceeding `target`:
     * 1. `navigateToPagerTarget` returns `false` without throwing `TimeoutCancellationException`.
     * 2. `lastKnownPage` is NOT mutated to the invalid target index.
     * 3. The parent `navigationEvent.collect` coroutine remains active and processes subsequent
     *    valid `NavigateTo` events normally.
     */
    @Test
    fun `navigateToPagerTarget survives page-count timeout and processes subsequent navigation events`() = runTest {
        val events = MutableSharedFlow<TabNavigationEvent>(extraBufferCapacity = 4)
        val pageCountState = MutableStateFlow(2) // Valid pages: 0 (Control), 1 (Tab 0)
        var lastKnownPage = 0
        val animatedTargets = mutableListOf<Int>()
        val fallbackTargets = mutableListOf<Int>()
        val results = mutableListOf<Boolean>()

        val collectorJob = launch {
            events.collect { event ->
                if (event is TabNavigationEvent.NavigateTo) {
                    val didNavigate = navigateToPagerTarget(
                        tabIndex = event.index,
                        screenMode = ScreenMode.TRACKING,
                        currentPageCount = { pageCountState.value },
                        pageCountFlow = pageCountState,
                        onUpdateLastKnownPage = { lastKnownPage = it },
                        animateScrollToPage = { animatedTargets.add(it) },
                        scrollToPage = { fallbackTargets.add(it) },
                        timeoutMs = 2000L
                    )
                    results.add(didNavigate)
                }
            }
        }
        runCurrent()

        // 1. Emit out-of-bounds NavigateTo(index = 4) -> target = 5 while pageCount remains 2
        events.tryEmit(TabNavigationEvent.NavigateTo(index = 4))
        runCurrent()

        // Advance past the 2000 ms readiness wait
        advanceTimeBy(2001L)
        runCurrent()

        // Collector must still be alive, navigation must have returned false, and lastKnownPage must still be 0
        assertTrue("Collector coroutine must remain active after timeout", collectorJob.isActive)
        assertEquals(listOf(false), results)
        assertEquals("lastKnownPage must not be updated on timeout", 0, lastKnownPage)
        assertTrue(animatedTargets.isEmpty())
        assertTrue(fallbackTargets.isEmpty())

        // 2. Emit valid NavigateTo(index = 0) -> target = 1 (in 0 until 2)
        events.tryEmit(TabNavigationEvent.NavigateTo(index = 0))
        runCurrent()

        assertEquals(listOf(false, true), results)
        assertEquals("lastKnownPage must update to valid target 1", 1, lastKnownPage)
        assertEquals(listOf(1), animatedTargets)

        collectorJob.cancel()
    }

    /**
     * TST-UI-373.1 (delayed readiness): Verifies that when `pageCount` initially lags behind `target`
     * but grows to `> target` before the 2000 ms timeout expires, `navigateToPagerTarget` immediately
     * resumes, updates `lastKnownPage`, and scrolls to `target`.
     */
    @Test
    fun `navigateToPagerTarget scrolls as soon as pageCount grows beyond target within timeout`() = runTest {
        val pageCountState = MutableStateFlow(1)
        var lastKnownPage = 0
        val animatedTargets = mutableListOf<Int>()

        val navJob = launch {
            val result = navigateToPagerTarget(
                tabIndex = 1, // ScreenMode.CONFIGURATION -> offset = 0 -> target = 1
                screenMode = ScreenMode.CONFIGURATION,
                currentPageCount = { pageCountState.value },
                pageCountFlow = pageCountState,
                onUpdateLastKnownPage = { lastKnownPage = it },
                animateScrollToPage = { animatedTargets.add(it) },
                scrollToPage = {},
                timeoutMs = 2000L
            )
            assertTrue(result)
        }
        runCurrent()

        // Still waiting because pageCount (1) <= target (1)
        assertEquals(0, lastKnownPage)
        assertTrue(animatedTargets.isEmpty())

        // Simulate new tab loaded after 500 ms
        advanceTimeBy(500L)
        pageCountState.value = 2
        runCurrent()

        assertTrue(navJob.isCompleted)
        assertEquals(1, lastKnownPage)
        assertEquals(listOf(1), animatedTargets)
    }

    /**
     * TST-UI-373.2: Verifies `canShowDialogFragments()` returns `false` when the activity is null,
     * finishing, destroyed, or when `supportFragmentManager.isStateSaved` is `true`, and `true`
     * only when the activity is active and fragment state is not saved.
     */
    @Test
    fun `canShowDialogFragments guards against null, finishing, destroyed, and state-saved activities`() {
        val nullActivity: AppCompatActivity? = null
        assertFalse(nullActivity.canShowDialogFragments())

        val fragmentManager = mockk<FragmentManager>()
        val activity = mockk<AppCompatActivity>()
        every { activity.supportFragmentManager } returns fragmentManager

        // Healthy active state
        every { activity.isFinishing } returns false
        every { activity.isDestroyed } returns false
        every { fragmentManager.isStateSaved } returns false
        assertTrue(activity.canShowDialogFragments())

        // State already saved (ON_STOP / background)
        every { fragmentManager.isStateSaved } returns true
        assertFalse(activity.canShowDialogFragments())

        // Activity finishing
        every { fragmentManager.isStateSaved } returns false
        every { activity.isFinishing } returns true
        assertFalse(activity.canShowDialogFragments())

        // Activity destroyed
        every { activity.isFinishing } returns false
        every { activity.isDestroyed } returns true
        assertFalse(activity.canShowDialogFragments())
    }

    /**
     * TST-UI-373.3: Verifies `Context.findActivity()` safely unwraps nested `ContextWrapper`
     * instances to locate an `AppCompatActivity` and returns `null` without throwing
     * `ClassCastException` when the root `Context` is not an `AppCompatActivity`.
     */
    @Test
    fun `findActivity unwraps nested ContextWrappers and returns null for non-activity Context`() {
        val mockActivity = mockk<AppCompatActivity>()
        val wrapperLevel1 = mockk<ContextWrapper> {
            every { baseContext } returns mockActivity
        }
        val wrapperLevel2 = mockk<ContextWrapper> {
            every { baseContext } returns wrapperLevel1
        }

        assertSame(mockActivity, mockActivity.findActivity())
        assertSame(mockActivity, wrapperLevel1.findActivity())
        assertSame(mockActivity, wrapperLevel2.findActivity())

        val appContext = mockk<Context>()
        val nonActivityWrapper = mockk<ContextWrapper> {
            every { baseContext } returns appContext
        }
        assertNull(appContext.findActivity())
        assertNull(nonActivityWrapper.findActivity())
    }
}
