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

import android.app.Application
import android.util.Log
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.MutableLiveData
import com.atrainingtracker.banalservice.ActivityType
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewsRepository
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Unit, architectural contract, small-screen telemetry clearance, and 9-locale parity tests
 * for the first-time stop tracking swipe and tab coachmark banner on the cockpit screen
 * (REQ-UI-367 / TST-UI-330 / ATT-3387).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StopTrackingCoachmarkTest {

    private val testDispatcher = StandardTestDispatcher()
    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockTrackingViewsRepo = mockk<TrackingViewsRepository>(relaxed = true)
    private val mockBanalRepo = mockk<BANALServiceRepository>(relaxed = true)
    private val mockDevicesRepo = mockk<DeviceDataRepository>(relaxed = true)

    private val trackingModeLiveData = MutableLiveData<TrackingMode>()
    private val activityTypeFlow = MutableStateFlow(ActivityType.getDefaultActivityType())

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        every { mockBanalRepo.trackingMode } returns trackingModeLiveData
        every { mockBanalRepo.activityType } returns activityTypeFlow
        every { mockBanalRepo.bindToBANALService() } just Runs
        every { mockTrackingViewsRepo.getTrackingViewsFlow(any()) } returns flowOf(emptyList())
        every { mockDevicesRepo.allDevices } returns MutableStateFlow(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        ArchTaskExecutor.getInstance().setDelegate(null)
        unmockkAll()
    }

    private fun createViewModel(): TrackingTabsViewModel {
        return TrackingTabsViewModel(
            application = mockApplication,
            trackingViewsRepository = mockTrackingViewsRepo,
            banalServiceRepository = mockBanalRepo,
            devicesRepository = mockDevicesRepo
        )
    }

    @Test
    fun `shouldShowStopTrackingCoachmark returns false while DataStore is still loading (null)`() {
        assertFalse(
            "Coachmark must remain hidden while DataStore has not yet emitted its initial preference (null)",
            TrackingTabsViewModel.shouldShowStopTrackingCoachmark(
                trackingMode = TrackingMode.TRACKING,
                currentPage = 1,
                screenMode = ScreenMode.TRACKING,
                hasSeenHint = null
            )
        )
        assertFalse(
            "Auto-acknowledge must not fire while DataStore is still loading (null)",
            TrackingTabsViewModel.shouldAutoAcknowledgeStopTrackingCoachmark(
                trackingMode = TrackingMode.TRACKING,
                settledPage = 0,
                hasSeenHint = null,
                hasRenderedCoachmarkInSession = true
            )
        )
    }

    @Test
    fun `shouldShowStopTrackingCoachmark evaluates visibility matrix accurately`() {
        // Active TRACKING on telemetry page 1, first-time user -> true
        assertTrue(
            TrackingTabsViewModel.shouldShowStopTrackingCoachmark(
                trackingMode = TrackingMode.TRACKING,
                currentPage = 1,
                screenMode = ScreenMode.TRACKING,
                hasSeenHint = false
            )
        )
        // Active PAUSED on telemetry page 2, first-time user -> true
        assertTrue(
            TrackingTabsViewModel.shouldShowStopTrackingCoachmark(
                trackingMode = TrackingMode.PAUSED,
                currentPage = 2,
                screenMode = ScreenMode.TRACKING,
                hasSeenHint = false
            )
        )
        // READY mode -> false
        assertFalse(
            TrackingTabsViewModel.shouldShowStopTrackingCoachmark(
                trackingMode = TrackingMode.READY,
                currentPage = 1,
                screenMode = ScreenMode.TRACKING,
                hasSeenHint = false
            )
        )
        // Control tab (Page 0) -> false
        assertFalse(
            TrackingTabsViewModel.shouldShowStopTrackingCoachmark(
                trackingMode = TrackingMode.TRACKING,
                currentPage = 0,
                screenMode = ScreenMode.TRACKING,
                hasSeenHint = false
            )
        )
        // CONFIGURATION or PREVIEW screenMode -> false
        assertFalse(
            TrackingTabsViewModel.shouldShowStopTrackingCoachmark(
                trackingMode = TrackingMode.TRACKING,
                currentPage = 1,
                screenMode = ScreenMode.CONFIGURATION,
                hasSeenHint = false
            )
        )
        assertFalse(
            TrackingTabsViewModel.shouldShowStopTrackingCoachmark(
                trackingMode = TrackingMode.TRACKING,
                currentPage = 1,
                screenMode = ScreenMode.PREVIEW,
                hasSeenHint = false
            )
        )
        // Already seen -> false
        assertFalse(
            TrackingTabsViewModel.shouldShowStopTrackingCoachmark(
                trackingMode = TrackingMode.TRACKING,
                currentPage = 1,
                screenMode = ScreenMode.TRACKING,
                hasSeenHint = true
            )
        )
    }

    @Test
    fun `shouldAutoAcknowledgeStopTrackingCoachmark prevents premature dismissal on READY to TRACKING transition`() {
        // Before banner has rendered on Page >= 1 (e.g. pager still at Page 0 when READY -> TRACKING fires) -> false
        assertFalse(
            TrackingTabsViewModel.shouldAutoAcknowledgeStopTrackingCoachmark(
                trackingMode = TrackingMode.TRACKING,
                settledPage = 0,
                hasSeenHint = false,
                hasRenderedCoachmarkInSession = false
            )
        )

        // After banner has rendered on Page >= 1 and user swipes back to Page 0 -> true
        assertTrue(
            TrackingTabsViewModel.shouldAutoAcknowledgeStopTrackingCoachmark(
                trackingMode = TrackingMode.TRACKING,
                settledPage = 0,
                hasSeenHint = false,
                hasRenderedCoachmarkInSession = true
            )
        )
        assertTrue(
            TrackingTabsViewModel.shouldAutoAcknowledgeStopTrackingCoachmark(
                trackingMode = TrackingMode.PAUSED,
                settledPage = 0,
                hasSeenHint = false,
                hasRenderedCoachmarkInSession = true
            )
        )

        // Still on Page 1 -> false
        assertFalse(
            TrackingTabsViewModel.shouldAutoAcknowledgeStopTrackingCoachmark(
                trackingMode = TrackingMode.TRACKING,
                settledPage = 1,
                hasSeenHint = false,
                hasRenderedCoachmarkInSession = true
            )
        )
    }

    @Test
    fun `synchronous optimistic dismissal suppresses coachmark immediately even when DataStore write is delayed or interrupted`() = runTest(testDispatcher) {
        trackingModeLiveData.value = TrackingMode.TRACKING
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        viewModel.markCoachmarkRenderedInSession()
        assertTrue(viewModel.hasRenderedCoachmarkInSession.value)
        assertFalse(viewModel.isCoachmarkDismissPending.value)

        // Simulate user clicking Dismiss while DataStore write is delayed/hanging (hasSeenHint still false)
        viewModel.markCoachmarkDismissPending()
        assertTrue(viewModel.isCoachmarkDismissPending.value)

        assertFalse(
            "Coachmark must be immediately hidden via optimistic dismissal even while DataStore still reports hasSeenHint == false",
            TrackingTabsViewModel.shouldShowStopTrackingCoachmark(
                trackingMode = TrackingMode.TRACKING,
                currentPage = 1,
                screenMode = ScreenMode.TRACKING,
                hasSeenHint = false,
                isDismissPending = viewModel.isCoachmarkDismissPending.value
            )
        )
        assertFalse(
            "Duplicate auto-acknowledgement must be suppressed while dismiss is already pending",
            TrackingTabsViewModel.shouldAutoAcknowledgeStopTrackingCoachmark(
                trackingMode = TrackingMode.TRACKING,
                settledPage = 0,
                hasSeenHint = false,
                hasRenderedCoachmarkInSession = viewModel.hasRenderedCoachmarkInSession.value,
                isDismissPending = viewModel.isCoachmarkDismissPending.value
            )
        )

        // Simulate rapid interrupted transition READY -> TRACKING before DataStore write finishes:
        // isCoachmarkDismissPending remains true so the banner never flickers back
        trackingModeLiveData.value = TrackingMode.READY
        testScheduler.advanceUntilIdle()
        trackingModeLiveData.value = TrackingMode.TRACKING
        testScheduler.advanceUntilIdle()

        assertTrue(
            "Optimistic dismissal must survive rapid READY -> TRACKING transitions while DataStore I/O completes",
            viewModel.isCoachmarkDismissPending.value
        )
        assertFalse(
            TrackingTabsViewModel.shouldShowStopTrackingCoachmark(
                trackingMode = TrackingMode.TRACKING,
                currentPage = 1,
                screenMode = ScreenMode.TRACKING,
                hasSeenHint = false,
                isDismissPending = viewModel.isCoachmarkDismissPending.value
            )
        )
    }

    @Test
    fun `ViewModel session state survives configuration changes and resets on READY transition`() = runTest(testDispatcher) {
        trackingModeLiveData.value = TrackingMode.TRACKING
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.hasRenderedCoachmarkInSession.value)
        viewModel.markCoachmarkRenderedInSession()
        assertTrue(
            "hasRenderedCoachmarkInSession must remain true across Activity configuration changes on the same ViewModel",
            viewModel.hasRenderedCoachmarkInSession.value
        )

        // Transitioning back to READY resets hasRenderedCoachmarkInSession for the next workout if still unseen
        trackingModeLiveData.value = TrackingMode.READY
        testScheduler.advanceUntilIdle()
        assertFalse(
            "hasRenderedCoachmarkInSession must reset to false when workout returns to READY",
            viewModel.hasRenderedCoachmarkInSession.value
        )
    }

    @Test
    fun `TuningPreferencesDataStore registers KEY_HAS_SEEN_STOP_TRACKING_HINT in ALL_KEYS for factory reset`() {
        assertEquals(false, TuningPreferencesDefaults.DEFAULT_HAS_SEEN_STOP_TRACKING_HINT)
        assertEquals("tuning_has_seen_stop_tracking_hint", TuningPreferencesDataStore.KEY_HAS_SEEN_STOP_TRACKING_HINT.name)
        assertTrue(
            "KEY_HAS_SEEN_STOP_TRACKING_HINT must be included in ALL_KEYS so resetToDefaults() clears it",
            TuningPreferencesDataStore.ALL_KEYS.contains(TuningPreferencesDataStore.KEY_HAS_SEEN_STOP_TRACKING_HINT)
        )
    }

    @Test
    fun `StopTrackingCoachmarkBanner and TrackingTabsScreen enforce zero-jump overlay, 4-inch telemetry clearance, and 48dp touch targets`() {
        val bannerFile = File(
            projectRoot,
            "app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/StopTrackingCoachmarkBanner.kt"
        )
        val screenFile = File(
            projectRoot,
            "app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt"
        )
        assertTrue("StopTrackingCoachmarkBanner.kt must exist", bannerFile.exists())
        assertTrue("TrackingTabsScreen.kt must exist", screenFile.exists())

        val bannerContent = bannerFile.readText()
        val screenContent = screenFile.readText()

        // 1. Bounded height & 4-inch small-screen telemetry clearance
        assertTrue(
            "StopTrackingCoachmarkBanner must enforce heightIn(max = 120.dp)",
            bannerContent.contains(".heightIn(max = 120.dp)")
        )
        assertTrue(
            "StopTrackingCoachmarkBanner must enforce maxLines = 2 and TextOverflow.Ellipsis for 4-inch small-screen telemetry clearance",
            bannerContent.contains("maxLines = 2") && bannerContent.contains("overflow = TextOverflow.Ellipsis")
        )
        assertTrue(
            "StopTrackingCoachmarkBanner must place hint text in a weighted Column(modifier = Modifier.weight(1f)) so buttons never overlap",
            bannerContent.contains("Modifier.weight(1f)")
        )
        assertTrue(
            "StopTrackingCoachmarkBanner must enforce 48dp minimum touch targets on action and dismiss buttons",
            bannerContent.contains("Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)")
        )
        assertTrue(
            "StopTrackingCoachmarkBanner must enforce tonalElevation = 0.dp",
            bannerContent.contains("tonalElevation = 0.dp")
        )

        // 2. Zero-jump constant-weight Box overlay & initial = null Loading state in TrackingTabsScreen
        assertTrue(
            "TrackingTabsScreen must collect hasSeenStopTrackingHintFlow with initial = null",
            screenContent.contains("tuningDataStore.hasSeenStopTrackingHintFlow.collectAsState(initial = null)")
        )
        assertTrue(
            "TrackingTabsScreen must wrap HorizontalPager and StopTrackingCoachmarkBanner in Box(modifier = Modifier.weight(1f).fillMaxWidth())",
            screenContent.contains("Box(modifier = Modifier.weight(1f).fillMaxWidth())") &&
                screenContent.contains(".align(Alignment.TopCenter)") &&
                screenContent.contains("StopTrackingCoachmarkBanner(")
        )
        assertTrue(
            "TrackingTabsScreen must synchronously invoke markCoachmarkDismissPending() before async DataStore persistence",
            screenContent.contains("trackingTabsViewModel.markCoachmarkDismissPending()")
        )
    }

    @Test
    fun `all 9 supported locales define non-empty stop tracking coachmark strings`() {
        val locales = listOf(
            "values",
            "values-de",
            "values-es",
            "values-fr",
            "values-it",
            "values-ja",
            "values-nl",
            "values-pl",
            "values-pt"
        )
        val requiredKeys = listOf(
            "coachmark_stop_tracking_hint",
            "coachmark_stop_tracking_show_tab",
            "coachmark_stop_tracking_dismiss"
        )

        val dbf = DocumentBuilderFactory.newInstance()
        for (locale in locales) {
            val stringsFile = File(projectRoot, "app/src/main/res/$locale/strings.xml")
            assertTrue("Missing strings.xml for locale $locale", stringsFile.exists())
            val doc = dbf.newDocumentBuilder().parse(stringsFile)
            val nodes = doc.getElementsByTagName("string")
            val foundMap = mutableMapOf<String, String>()
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                val nameAttr = node.attributes?.getNamedItem("name")?.nodeValue
                if (nameAttr != null) {
                    foundMap[nameAttr] = node.textContent ?: ""
                }
            }
            for (key in requiredKeys) {
                assertTrue("Locale $locale is missing string key '$key'", foundMap.containsKey(key))
                assertTrue("Locale $locale has blank value for string key '$key'", foundMap[key]!!.isNotBlank())
            }
        }
    }
}
