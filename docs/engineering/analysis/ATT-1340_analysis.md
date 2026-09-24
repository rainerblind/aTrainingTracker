# Forensic Root Cause Analysis - ATT-1340: Cockpit Tab Navigation Failure on Tracking Start

**Ticket**: [ATT-1340](https://rainerblind.atlassian.net/browse/ATT-1340)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-39.2`  
**Components**: 
* `com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabsScreen.kt`
* `com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabsViewModel.kt`
**Branch**: `bugfix/ATT-1340`  

---

## 1. Problem Statement & User Impact

When an athlete initiates workout tracking by tapping "Start Tracking" on the main tracking control screen (Page 0 of `HorizontalPager`), the tracking engine starts successfully, but the user interface remains stuck on the "Control" tab instead of automatically navigating to the first cockpit/sensor tab (Page 1).

### Expected Behavior (Historical Contract - Commit `babb4dd0` / `198edff3`):
1. In `ScreenMode.TRACKING`, Page 0 is the "Control" tab (showing the Start/Pause/Stop controls, connected sensors, and sport selector).
2. Pages 1..N are the athlete's configured "Cockpit" tabs displaying live telemetry grids (Speed, Cadence, Heart Rate, Elevation, Power, Map, etc.).
3. When tracking is started (transition from `READY` to `TRACKING`), the application MUST automatically and smoothly scroll the pager to the first cockpit tab (Page 1), so the athlete immediately sees their live workout metrics.

---

## 2. Forensic Investigation & Root Cause Identification

### Root Cause 1: Sticky State & Equality Suppression in `observeAsState()` on `SingleLiveEvent`
In `TrackingTabsViewModel.kt`, tracking initiation navigation was wired to an legacy `SingleLiveEvent<Unit>`:
```kotlin
val navigateToTrackingTab = SingleLiveEvent<Unit>()
```
In `TrackingTabsScreen.kt`:
```kotlin
val navigateTrigger by trackingTabsViewModel.navigateToTrackingTab.observeAsState()
LaunchedEffect(navigateTrigger) {
    if (navigateTrigger != null) {
        if (screenMode == ScreenMode.TRACKING) {
            pagerState.scrollToPage(1)
        }
    }
}
```
* In Jetpack Compose, `observeAsState()` wraps the LiveData in a `MutableState<T?>` with structural equality (`structuralEqualityPolicy`).
* When `navigateToTrackingTab.call()` is invoked, `value = Unit` is set.
* Once `navigateTrigger` receives `Unit`, its state value is `Unit` and is **never reset to null**.
* When tracking is started subsequent times (or if `SingleLiveEvent` was triggered during initial composition), assigning `Unit` to `state.value` is ignored by Compose because `Unit == Unit`.
* Furthermore, `LaunchedEffect(navigateTrigger)` is keyed on `navigateTrigger`. Because the key did not change (`Unit == Unit`), Compose **never relaunches the effect**, dropping the navigation event completely.

### Root Cause 2: Desynchronization of `lastKnownPage` and Pager Recreation Race
In `TrackingTabsScreen.kt`:
```kotlin
val initialPage = remember(trackingViews.size, screenMode) {
    lastKnownPage.coerceIn(0, ...)
}
val pagerState = key(trackingViews.size, screenMode) {
    rememberPagerState(initialPage = initialPage, pageCount = { pageCount })
}
LaunchedEffect(pagerState.currentPage) {
    lastKnownPage = pagerState.currentPage
}
```
* When tabs are added or deleted, `TabNavigationEvent.NavigateTo` explicitly synchronizes `lastKnownPage = target` before animating:
  ```kotlin
  lastKnownPage = target
  pagerState.animateScrollToPage(target)
  ```
* In contrast, the `navigateToTrackingTab` handler only called `pagerState.scrollToPage(1)` without synchronizing `lastKnownPage = 1`.
* When tracking starts, multiple state changes occur concurrently: `trackingMode` transitions to `TRACKING`, `mDrawerController.startTrackingTitleRes` updates, and telemetry flows emit.
* If any state update causes recomposition of `key(trackingViews.size, screenMode)`, `pagerState` is recreated with `initialPage = lastKnownPage`. Because `lastKnownPage` was still 0, the pager immediately snaps back to Page 0.

### Root Cause 3: Lack of Rising-Edge Transition Detection
In `TrackingTabsViewModel.kt`:
```kotlin
viewModelScope.launch {
    banalServiceRepository.trackingMode.asFlow().collect { mode ->
        if (mode == TrackingMode.TRACKING) {
            Log.i("TrackingTabsViewModel", "Tracking started...")
            navigateToTrackingTab.call()
        }
    }
}
```
* When `asFlow()` begins collection, it immediately emits the current value of `_trackingMode`.
* If the ViewModel is created while tracking is already running (e.g., resuming from background, process recovery, or navigating back from another screen), it emits `TRACKING` immediately. Without rising-edge detection (`previousMode != TRACKING && mode == TRACKING`), this causes erratic navigation jumps.
* Conversely, if `asFlow()` emissions coalesce or if `banalServiceRepository` binds asynchronously, events can be missed.

### Root Cause 4: Disjoint Architecture (Dual Event Dispatch Mechanisms)
* Modern tab navigation in `TrackingTabsViewModel` is already built on an idiomatic Kotlin Coroutines `SharedFlow`:
  ```kotlin
  sealed class TabNavigationEvent {
      data class NavigateTo(val index: Int) : TabNavigationEvent()
      data class EditDevice(val deviceId: Long) : TabNavigationEvent()
  }
  private val _navigationEvent = MutableSharedFlow<TabNavigationEvent>()
  val navigationEvent: SharedFlow<TabNavigationEvent> = _navigationEvent.asSharedFlow()
  ```
* In `TrackingTabsScreen.kt`, lines 162-181 already cleanly handle `TabNavigationEvent.NavigateTo`, computing `offset = if (screenMode == ScreenMode.TRACKING) 1 else 0`, setting `lastKnownPage = target`, and calling `pagerState.animateScrollToPage(target)` safely inside `try ... catch`.
* Maintaining a separate `SingleLiveEvent<Unit>` bypassed this unified navigation pipeline, reintroducing obsolete LiveData-to-Compose bridge friction.

---

## 3. Corrective Architecture & Proposed Solution

1. **Unify Tab Navigation on `TabNavigationEvent` (`SharedFlow`)**:
   - Retire `SingleLiveEvent<Unit>` (`navigateToTrackingTab`) from `TrackingTabsViewModel.kt`.
   - Dispatch cockpit navigation through the existing reactive `navigationEvent` channel:
     ```kotlin
     _navigationEvent.emit(TabNavigationEvent.NavigateTo(0))
     ```
   - In `TrackingTabsScreen.kt`, `TabNavigationEvent.NavigateTo(0)` is collected:
     `offset = 1` in `ScreenMode.TRACKING` -> `target = 0 + 1 = 1`.
     Sets `lastKnownPage = 1` and calls `pagerState.animateScrollToPage(1)` with exception handling.

2. **Implement Rising-Edge Tracking State Detection**:
   - In `TrackingTabsViewModel.kt`, track the previous tracking mode to trigger navigation strictly on the transition into `TRACKING`:
     ```kotlin
     viewModelScope.launch {
         var previousMode: TrackingMode? = null
         banalServiceRepository.trackingMode.asFlow().collect { mode ->
             if (mode == TrackingMode.TRACKING && previousMode != null && previousMode != TrackingMode.TRACKING) {
                 Log.i("TrackingTabsViewModel", "Tracking started (rising edge) -> navigating to cockpit tab")
                 _navigationEvent.emit(TabNavigationEvent.NavigateTo(0))
             }
             previousMode = mode
         }
     }
     ```
   - Guarding with `previousMode != null && previousMode != TrackingMode.TRACKING` guarantees that:
     1. Cold start / screen entry while already tracking does NOT hijack the pager.
     2. Transitioning from `READY` -> `TRACKING` reliably navigates to Cockpit (Page 1).
     3. Resuming from `PAUSED` -> `TRACKING` does not disrupt the athlete's current page.

3. **Smooth Animated Scrolling**:
   - Use `pagerState.animateScrollToPage(target)` instead of snapping `scrollToPage(1)`, matching the smooth scroll behavior of legacy ViewPager2 (`viewPager.setCurrentItem(1, true)`).

4. **Synchronous `lastKnownPage` Protection**:
   - Setting `lastKnownPage = target` prior to `animateScrollToPage` guarantees that if any concurrent recomposition or `key(...)` re-evaluation occurs during the animation, `initialPage` remains pinned to Page 1.

---

## 4. Requirement & ASPICE Traceability

* **Requirement**: `REQ-UI-163` (Cockpit Tab Navigation on Tracking Initiation).
* **Test Specification**: `TST-UI-115` (Automated & Physical Device Verification of Cockpit Navigation).
* **Invariants**:
  - `ScreenMode.CONFIGURATION` and `ScreenMode.PREVIEW` behavior remain completely unaffected.
  - Page 0 Control tab remains accessible via left-swipe or tapping the "Tracking" tab.
  - Tab addition and deletion flows continue to function via `TabNavigationEvent.NavigateTo`.
