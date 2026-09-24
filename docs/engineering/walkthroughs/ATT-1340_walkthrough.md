# Walkthrough - ATT-1340: Cockpit Tab Navigation on Tracking Start

## 1. Executive Summary
Under **ATT-1340**, the defect preventing automatic navigation to the first Cockpit tab when workout tracking is initiated was resolved. Two distinct failure mechanisms were uncovered and resolved:
1. **Broken Event Architecture & Legacy Trigger**: The previous implementation utilized an unreset `SingleLiveEvent<Unit>` consumed via Compose `observeAsState()`. Because `SingleLiveEvent.call()` assigns `Unit`, subsequent start cycles evaluated `Unit == Unit`, suppressing `LaunchedEffect(navigateTrigger)`.
2. **Stale PagerState Closure**: In `TrackingTabsScreen.kt`, `pagerState` is created via `key(trackingViews.size, screenMode) { rememberPagerState(...) }`. Because `trackingViews` loads asynchronously from the database, `key` recreates `pagerState` once the views load. A long-lived `LaunchedEffect(Unit)` collecting `navigationEvent` captured the *initial* (now detached/orphaned) `pagerState` by value. When tracking started, scrolling operations were executed on the dead pager instance, while the active `HorizontalPager` on screen remained on Page 0.

By binding `currentPagerState` and `currentScreenMode` via `rememberUpdatedState`, adding buffer capacity to `MutableSharedFlow`, ensuring `pageCount` readiness, and providing an immediate fallback, navigation reliably animates to Page 1 every time.

---

## 2. Changes Implemented

### A. View Model (`TrackingTabsViewModel.kt`)
* **Unified Navigation Pipeline with Buffer Capacity**: Removed legacy `val navigateToTrackingTab = SingleLiveEvent<Unit>()`. Configured `_navigationEvent = MutableSharedFlow<TabNavigationEvent>(extraBufferCapacity = 64)` to ensure navigation events are never dropped during UI recomposition.
* **Rising-Edge Transition Detection**: Implemented state-tracking collector on `banalServiceRepository.trackingMode.asFlow()`:
  ```kotlin
  var previousMode: TrackingMode? = null
  banalServiceRepository.trackingMode.asFlow().collect { mode ->
      if (mode == TrackingMode.TRACKING &&
          previousMode != null &&
          previousMode != TrackingMode.TRACKING
      ) {
          Log.i("TrackingTabsViewModel", "Tracking started (rising edge) -> navigating to cockpit tab")
          _navigationEvent.emit(TabNavigationEvent.NavigateTo(0))
      }
      previousMode = mode
  }
  ```
* **Guard Conditions**:
  - `previousMode != null`: Prevents cold start emissions when restoring process or rotating device while already in `TRACKING` mode.
  - `previousMode != TrackingMode.TRACKING`: Ignores redundant repeat state emissions while tracking.
  - Transition from `READY -> TRACKING` or `PAUSED -> TRACKING`: Navigates to first cockpit tab (Page 1) as requested.

### B. UI Layer (`TrackingTabsScreen.kt`)
* **Removed Obsolete Listener**: Removed lines 196–204 (`val navigateTrigger by trackingTabsViewModel.navigateToTrackingTab.observeAsState()` and `LaunchedEffect(navigateTrigger)`).
* **`rememberUpdatedState` Integration**:
  ```kotlin
  val currentPagerState by rememberUpdatedState(pagerState)
  val currentScreenMode by rememberUpdatedState(screenMode)
  ```
  Guarantees that `LaunchedEffect(Unit)` always interacts with the live, active `PagerState` currently attached to `HorizontalPager`, even after `key(trackingViews.size, screenMode)` recreates the state.
* **Robust Navigation Execution**:
  - Computes `offset = if (currentScreenMode == ScreenMode.TRACKING) 1 else 0` -> target index `1`.
  - Sets `lastKnownPage = target` to synchronize state with pager recreation.
  - Awaits `pageCount > target` readiness if the pager is still loading views.
  - Executes `currentPagerState.animateScrollToPage(target)` with automated fallback to `currentPagerState.scrollToPage(target)`.

### C. Automated Unit Test Suite (`TrackingTabsViewModelTest.kt`)
Created comprehensive test suite verifying:
1. `testTrackingStartNavigatesToFirstCockpitTab`: Transition from `READY` to `TRACKING` emits `TabNavigationEvent.NavigateTo(0)`.
2. `testColdStartInTrackingModeDoesNotTriggerSpuriousNavigation`: Initialization while already in `TRACKING` emits 0 navigation events.
3. `testResumeFromPausedNavigatesToFirstCockpitTab`: Transition from `PAUSED` to `TRACKING` emits `TabNavigationEvent.NavigateTo(0)`.
4. `testMultipleStartStopCyclesReliability`: Multiple successive start/stop tracking cycles emit `NavigateTo(0)` reliably on every start without deadlocks.
5. `testPagerOffsetCalculationForScreenMode`: Invariant verification for screen mode offset calculation.

---

## 3. Verification & Evidence
* **Component Unit Tests**:
  - `com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabsViewModelTest`: 5/5 PASSED.
* **Full-Suite Regression**:
  - `./gradlew testDebugUnitTest`: BUILD SUCCESSFUL (all unit test suites passing across all modules, 0 failures, 0 regressions).
* **Device Installation & Streamed APK Verification**:
  - Compiled and installed debug build (`app-debug.apk`) directly to connected test hardware (`66020DLCR002FL`).
