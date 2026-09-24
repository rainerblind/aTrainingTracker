# Test Specification - ATT-1340: Cockpit Tab Navigation on Tracking Start

**Ticket**: [ATT-1340](https://rainerblind.atlassian.net/browse/ATT-1340)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-39.2`  
**Components**: 
* `com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabsScreen.kt`
* `com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabsViewModel.kt`
* `com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabsViewModelTest.kt`
**Requirement**: `REQ-UI-163`  
**Test Spec**: `TST-UI-115`  
**Branch**: `bugfix/ATT-1340`  

---

## 1. Overview & Verification Strategy

This test specification defines unit, integration, and manual acceptance test criteria to verify that when tracking is started (transition from `READY` to `TRACKING`), the application automatically and smoothly navigates the pager from Page 0 (Control tab) to Page 1 (first cockpit tab).

Verification addresses:
1. **Rising-edge state transition detection** in `TrackingTabsViewModel`.
2. **Unified `SharedFlow<TabNavigationEvent>` event dispatching** replacing broken `SingleLiveEvent<Unit>`.
3. **Repeated start/stop cycle reliability** ensuring events are never dropped or suppressed by stale state.
4. **Pager state stability & synchronous `lastKnownPage` synchronization** preventing unwanted snapbacks to Page 0 during recomposition.
5. **Clean-room full-suite regression** ensuring zero regressions across all 630+ tests.
6. **Physical device validation** confirming smooth animated scroll to the cockpit tab on hardware.

---

## 2. Test Cases & Verification Matrix

### Test Case 1: `testTrackingStartNavigatesToFirstCockpitTab` (Unit Test)
* **Goal**: Verify that when `trackingMode` transitions from `READY` to `TRACKING`, `TrackingTabsViewModel` emits `TabNavigationEvent.NavigateTo(0)` onto its `navigationEvent` SharedFlow.
* **Preconditions**: `TrackingTabsViewModel` initialized; initial `trackingMode` is `TrackingMode.READY`.
* **Action**: Emit `TrackingMode.TRACKING` on `banalServiceRepository.trackingMode`.
* **Expected Result**:
  - `navigationEvent` emits `TabNavigationEvent.NavigateTo(0)`.
  - In `TrackingTabsScreen`, page offset calculation (`offset = if (screenMode == ScreenMode.TRACKING) 1 else 0`) computes target page index `1`.

### Test Case 2: `testColdStartInTrackingModeDoesNotTriggerSpuriousNavigation` (Unit Test)
* **Goal**: Verify that if the ViewModel initializes when `trackingMode` is already `TRACKING` (e.g. Activity recreation, process restore), no navigation event is emitted.
* **Preconditions**: Initial `trackingMode` emitted at start is `TrackingMode.TRACKING`.
* **Action**: Collect `navigationEvent` during and immediately following initialization.
* **Expected Result**:
  - Zero `TabNavigationEvent.NavigateTo` events are emitted. The athlete's current page is preserved.

### Test Case 3: `testPauseAndResumeDoesNotDisruptActivePage` (Unit Test)
* **Goal**: Verify that transitioning from `TRACKING` to `PAUSED` and back from `PAUSED` to `TRACKING` does NOT emit cockpit navigation.
* **Preconditions**: Active session in `TRACKING` mode.
* **Action**: Transition `trackingMode` to `PAUSED`, then back to `TRACKING`.
* **Expected Result**:
  - Zero navigation events emitted. The athlete remains on their active cockpit tab (or map).

### Test Case 4: `testMultipleStartStopCyclesReliability` (Unit Test)
* **Goal**: Verify that across repeated tracking start and stop cycles, navigation events are emitted on every start.
* **Preconditions**: Active ViewModel.
* **Action**:
  1. `READY` -> `TRACKING` -> verify event #1 received.
  2. `TRACKING` -> `READY` (stop tracking).
  3. `READY` -> `TRACKING` -> verify event #2 received.
* **Expected Result**:
  - Exactly 1 event per start transition; no event suppression or sticky state deadlocks.

### Test Case 5: `testPagerOffsetAndLastKnownPageSynchronization` (Unit Test)
* **Goal**: Verify that receiving `TabNavigationEvent.NavigateTo(0)` in `ScreenMode.TRACKING` calculates `target = 1` and updates `lastKnownPage = 1` before launching `animateScrollToPage(1)`.
* **Preconditions**: `screenMode == ScreenMode.TRACKING`, `trackingViews.size >= 1`.
* **Action**: Simulate event handling dispatch.
* **Expected Result**:
  - Target index equals `1`.
  - `lastKnownPage` is set to `1` synchronously to prevent pager recreation from resetting to 0.

### Test Case 6: Clean-Room Full Suite Regression (Automated)
* **Command**: `./gradlew testDebugUnitTest`
* **Success Criteria**: BUILD SUCCESSFUL, 0 failures, 0 skipped/error regressions across all modules.

### Test Case 7: Physical Device Verification (Manual)
* **Procedure**:
  1. Launch app on physical device; navigate to main tracking screen (`START_TRACKING`).
  2. Verify Page 0 is displayed ("Start" tab with green start button).
  3. Tap "Start Tracking".
  4. Verify the pager smoothly and automatically animates to Page 1 (first cockpit tab).
  5. Swipe left back to Page 0; tap "Pause", then "Resume". Verify the page does NOT jump.
  6. Tap "Stop". Start tracking again. Verify it automatically navigates to Page 1 again.

---

## 3. Requirement Traceability Matrix

| Test Case | Target Requirement | Description | Status |
| :--- | :--- | :--- | :--- |
| `TC-1` | `REQ-UI-163` | Rising-edge start navigation emission (`NavigateTo(0)`) | Draft |
| `TC-2` | `REQ-UI-163` | Cold start / resume immunity without spurious scroll | Draft |
| `TC-3` | `REQ-UI-163` | Pause/Resume neutrality | Draft |
| `TC-4` | `REQ-UI-163` | Multiple start/stop cycle immunity | Draft |
| `TC-5` | `REQ-UI-163` | `lastKnownPage` & PagerState synchronization | Draft |
| `TC-6` | `REQ-UI-159` / `REQ-UI-163` | Full regression pass across all unit tests | Draft |
| `TC-7` | `REQ-UI-163` | Physical device smooth scroll confirmation | Draft |
