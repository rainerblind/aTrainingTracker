# Implementation Plan - ATT-1340: Cockpit Tab Navigation on Tracking Start

**Ticket**: [ATT-1340](https://rainerblind.atlassian.net/browse/ATT-1340)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-39.2`  
**Components**: 
* `com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabsViewModel.kt`
* `com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabsScreen.kt`
* `com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabsViewModelTest.kt`
**Requirement**: `REQ-UI-163`  
**Test Spec**: `TST-UI-115`  
**Branch**: `bugfix/ATT-1340`  

---

## 1. Technical Architecture & Modifications

### 1. `TrackingTabsViewModel.kt`:
* **Retire Legacy `SingleLiveEvent`**:
  * Remove `val navigateToTrackingTab = SingleLiveEvent<Unit>()`.
  * Remove import `com.atrainingtracker.trainingtracker.ui.util.SingleLiveEvent` if no longer needed in this file.
* **Implement Rising-Edge Transition Detection with `SharedFlow` Emission**:
  * In `init`, update the observation of `banalServiceRepository.trackingMode.asFlow()`:
    ```kotlin
    viewModelScope.launch {
        var previousMode: TrackingMode? = null
        banalServiceRepository.trackingMode.asFlow().collect { mode ->
            if (mode == TrackingMode.TRACKING && 
                previousMode != null && 
                previousMode != TrackingMode.TRACKING && 
                previousMode != TrackingMode.PAUSED) {
                Log.i("TrackingTabsViewModel", "Tracking started (rising edge) -> navigating to cockpit tab")
                _navigationEvent.emit(TabNavigationEvent.NavigateTo(0))
            }
            previousMode = mode
        }
    }
    ```
  * **Guards & Behavior**:
    1. `previousMode != null`: Prevents firing on cold start or when the ViewModel is created while tracking is already running.
    2. `previousMode != TrackingMode.TRACKING`: Ensures only transitions into `TRACKING` can trigger navigation.
    3. `previousMode != TrackingMode.PAUSED`: Prevents disrupting the athlete's current page when resuming from a pause.
    4. Triggers exclusively when transitioning from `TrackingMode.READY` (or `IDLE`) to `TrackingMode.TRACKING`.

### 2. `TrackingTabsScreen.kt`:
* **Remove Broken LiveData Observation**:
  * Remove lines 196–204:
    ```kotlin
    // Move to first tab when tracking is started
    val navigateTrigger by trackingTabsViewModel.navigateToTrackingTab.observeAsState()
    LaunchedEffect(navigateTrigger) {
        if (navigateTrigger != null) {
            if (screenMode == ScreenMode.TRACKING) {
                pagerState.scrollToPage(1)
            }
        }
    }
    ```
* **Leverage Existing `navigationEvent` Pipeline**:
  * `navigationEvent` is already collected in lines 162–181:
    ```kotlin
    LaunchedEffect(Unit) {
        trackingTabsViewModel.navigationEvent.collect { tabNavigationEvent ->
            when (tabNavigationEvent) {
                is TabNavigationEvent.NavigateTo -> {
                    val offset = if (screenMode == ScreenMode.TRACKING) 1 else 0
                    val target = tabNavigationEvent.index + offset
                    scope.launch {
                        try {
                            lastKnownPage = target
                            pagerState.animateScrollToPage(target)
                        }
                        catch (e: Exception) {
                            Log.e(TAG, "Navigation failed: page $target not ready yet", e)
                        }
                    }
                }
                ...
            }
        }
    }
    ```
  * When `TabNavigationEvent.NavigateTo(0)` is collected:
    - In `ScreenMode.TRACKING`, `offset` is 1, so `target` is `0 + 1 = 1`.
    - Sets `lastKnownPage = 1` synchronously before animating.
    - Smoothly animates to Page 1 via `pagerState.animateScrollToPage(1)`.
    - Protected by `try ... catch` if pager is not yet laid out.

### 3. Automated Unit Testing (`TrackingTabsViewModelTest.kt`):
* Create a dedicated unit test suite for `TrackingTabsViewModel`:
  * `testTrackingStartNavigatesToFirstCockpitTab`: `READY -> TRACKING` emits `NavigateTo(0)`.
  * `testColdStartInTrackingModeDoesNotTriggerSpuriousNavigation`: Initial state `TRACKING` emits nothing.
  * `testPauseAndResumeDoesNotDisruptActivePage`: `TRACKING -> PAUSED -> TRACKING` emits nothing.
  * `testMultipleStartStopCyclesReliability`: Multiple start cycles emit `NavigateTo(0)` on every start.
  * `testPagerOffsetCalculation`: `NavigateTo(0)` with `ScreenMode.TRACKING` yields target page 1.

---

## 2. Invariant & Safety Analysis

1. **INV-UI-01**: Configuration mode (`ScreenMode.CONFIGURATION`) and Preview mode (`ScreenMode.PREVIEW`) back navigation and tab management remain completely unchanged.
2. **INV-UI-02**: Manual tab switching (by tapping tabs in `PrimaryScrollableTabRow` or swiping `HorizontalPager`) remains 100% intact.
3. **INV-UI-03**: The Page 0 "Control" tab remains accessible at all times during tracking by swiping left or tapping the "Tracking" tab in the tab row.
4. **INV-UI-04**: Adding and deleting tabs via `TabNavigationEvent.NavigateTo` retains existing offset calculation and animation.
5. **INV-UI-05**: Clean coroutine cancellation on rapid navigation events.

---

## 3. Step-by-Step Implementation Sequence

1. **Pre-Construction Check**:
   - Verify `python3 tools/jira_util.py check-gate ATT-1344` exits with code 0 before code edits.
2. **Code Construction**:
   - Update `TrackingTabsViewModel.kt`: implement rising-edge `_navigationEvent.emit(TabNavigationEvent.NavigateTo(0))` and remove `navigateToTrackingTab`.
   - Update `TrackingTabsScreen.kt`: remove obsolete `observeAsState()` block.
3. **Unit Test Construction**:
   - Add `TrackingTabsViewModelTest.kt` in `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/`.
4. **Regression & Verification**:
   - Run targeted unit test suite.
   - Run `./gradlew testDebugUnitTest` clean-room full suite regression.
5. **Stage 4 Review**:
   - Commit code, update Jira, audit Gate 4, and await human approval.
