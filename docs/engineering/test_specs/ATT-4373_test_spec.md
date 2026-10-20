# Stage 2: Requirement & Test Specification - ATT-4373: [Bug] Do not navigate to first Cockpit tab when Auto-Pause ends

**Ticket**: [ATT-4373](https://rainerblind.atlassian.net/browse/ATT-4373)  
**Sub-task**: [ATT-4388](https://rainerblind.atlassian.net/browse/ATT-4388) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.2`  
**Requirement Mapping**: `REQ-UI-163` (*Cockpit Tab Navigation on Tracking Initiation & Origin-Aware Resume Neutrality*)  
**Test Spec ID**: `TST-UI-115`  
**Branch**: `feature/ATT-4373`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Requirement Specification (`REQ-UI-163`)

### 1.1 Problem Statement & Rationale
During an active workout session with Auto-Pause enabled, an athlete decelerating below the auto-pause speed threshold (e.g. at an intersection, traffic light, or trail overlook) transitions into the `PAUSED` state. While stopped or moving slowly, athletes routinely swipe to inspect secondary telemetry data tabs (Page 2+) or the Map tab for live route navigation and turn cues.

Under the current implementation in `TrackingTabsViewModel.kt:243-249`, the collector observing `banalServiceRepository.trackingMode` detects every rising edge from `PAUSED -> TRACKING` and unconditionally emits `_navigationEvent.send(TabNavigationEvent.NavigateTo(0))`. In `TrackingTabsScreen.kt`, this event forces the horizontal pager to animate scroll back to Page 1 (first Cockpit telemetry tab). Consequently, every single time the athlete resumes movement above the auto-pause threshold (or manually taps "Resume" on the `AutoPauseHudBanner`), the app involuntarily jerks them away from their chosen tab (e.g. Map navigation) and snaps back to Page 1.

Furthermore, `TrainingApplication.java:1678-1692` clears `mIsAutoPaused` via `setAutoPaused(false, this)` *before* transitioning `cTrackingMode` to `TRACKING` and broadcasting `TRACKING_STATE_CHANGED`, wiping the Auto-Pause provenance before downstream observers (like `BANALServiceRepository` or ViewModels) can inspect it.

Navigating to the first Cockpit tab (`NavigateTo(0)`) is strictly intended when:
1. Starting an initial tracking session from the "Start Tracking" / Control tab (`READY -> TRACKING`).
2. Resuming an active workout from a **manual pause** while actively viewing the Control tab (`Page 0`).

Resuming from **Auto-Pause** or resuming a manual pause while viewing any telemetry or map tab (`Page >= 1`) MUST NOT scroll the pager away from the athlete's active tab.

### 1.2 Functional & Architectural Requirements
The system SHALL satisfy the following functional and technical requirements:

1. **Origin-Aware Resume State Tracking (`REQ-UI-163.1`)**:
   * In `TrainingApplication.resumeFromPaused()`, the system SHALL capture `boolean wasAuto = mIsAutoPaused.get()`, store it in a thread-safe static field `private static final AtomicBoolean mWasAutoPaused = new AtomicBoolean(false)` via `mWasAutoPaused.set(wasAuto)` prior to clearing pause locks, broadcast `EXTRA_WAS_AUTO_PAUSED` in `TRACKING_STATE_CHANGED`, and expose public static getter `TrainingApplication.wasAutoPaused()`.
   * In `BANALServiceRepository.kt`, the repository SHALL expose `val wasAutoPaused: StateFlow<Boolean>` updated from `intent.getBooleanExtra(TrainingApplication.EXTRA_WAS_AUTO_PAUSED, false)` upon receiving `TRACKING_STATE_CHANGED`.
   * In `pauseTracking()`, `stopTracking()`, `startTracking()`, `resetTrackingStateOnAbort()`, and `onTrackingAborted()`, `mWasAutoPaused.set(false)` SHALL be reset.

2. **Active Page Pager State Synchronization (`REQ-UI-163.2`)**:
   * `TrackingTabsViewModel` SHALL track the active pager page position via `val currentPage: StateFlow<Int>` and public callback `fun onPageSelected(page: Int)`.
   * In `TrackingTabsScreen.kt`, `LaunchedEffect(pagerState.currentPage)` SHALL notify `trackingTabsViewModel.onPageSelected(pagerState.currentPage)`.

3. **Origin-Aware Navigation Decision Logic (`REQ-UI-163.3`)**:
   * In `TrackingTabsViewModel.kt`, upon observing a rising edge to `TrackingMode.TRACKING` (`previousMode != null && previousMode != TrackingMode.TRACKING && mode == TrackingMode.TRACKING`), the ViewModel SHALL evaluate:
     ```kotlin
     val wasAuto = banalServiceRepository.wasAutoPaused.value
     val isControlTab = _currentPage.value == 0
     val shouldNavigate = when (previousMode) {
         TrackingMode.READY -> true
         TrackingMode.PAUSED -> !wasAuto && isControlTab
         else -> false
     }
     ```
   * When `shouldNavigate == true`, `TrackingTabsViewModel` SHALL emit `TabNavigationEvent.NavigateTo(0)` via `Channel<TabNavigationEvent>(Channel.BUFFERED)` (`REQ-UI-412`).
   * When `shouldNavigate == false`, navigation SHALL be suppressed with zero events emitted.

4. **Auto-Pause Resume Tab Preservation (`REQ-UI-163.4`)**:
   * Resuming from Auto-Pause (via speed recovery above threshold or tapping "Resume" on `AutoPauseHudBanner`) SHALL evaluate `wasAutoPaused == true`, suppressing `NavigateTo(0)` and leaving the athlete uninterrupted on their active tab (telemetry tabs, Map tab, or Control tab).

5. **Telemetry & Map Tab Manual Resume Preservation (`REQ-UI-163.5`)**:
   * Resuming a manually paused session while viewing `currentPage >= 1` (e.g. via smartwatch action, Pebble companion app, or notification action) SHALL evaluate `currentPage != 0`, suppressing `NavigateTo(0)` and preserving the active tab.

6. **Recomposition, Rotation & Gesture Invariants (`REQ-UI-163.6`)**:
   * Cold starts, process recreation, and device rotations while in `TRACKING` mode SHALL emit zero navigation events.
   * Pager scroll animations SHALL respect manual swipe interruptions (`CancellationException`) without hard snapping (`REQ-UI-412.3`).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Auto-Pause Resume Tab Preservation)**:
  * *Given* an active workout with Auto-Pause enabled and the athlete viewing Page 2+ (e.g. Map tab or secondary metrics),
  * *When* Auto-Pause ends via movement acceleration or tapping "Resume" on the HUD banner,
  * *Then* the pager SHALL NOT scroll and the athlete SHALL remain on their active tab (`NavigateTo(0)` suppressed).
* **Criterion 2 (Manual Resume from Control Tab)**:
  * *Given* an athlete viewing the Control tab (`Page 0`) in `PAUSED` mode following a manual pause,
  * *When* the athlete taps "Resume",
  * *Then* the pager SHALL automatically and smoothly animate to Page 1 (first cockpit tab).
* **Criterion 3 (Manual Resume from Telemetry/Map Tab)**:
  * *Given* an athlete viewing Page 2+ in `PAUSED` mode following a manual pause,
  * *When* tracking is resumed (e.g. via smartwatch or notification action),
  * *Then* the pager SHALL NOT scroll and the athlete SHALL remain on Page 2+ (`NavigateTo(0)` suppressed).
* **Criterion 4 (Initial Tracking Start Navigation)**:
  * *Given* an athlete on the Start / Control tab (`Page 0`) in `TrackingMode.READY`,
  * *When* the athlete taps "Start Tracking",
  * *Then* the pager SHALL automatically and smoothly animate to Page 1.
* **Criterion 5 (Cold Start & Screen Rotation Neutrality)**:
  * *Given* the athlete is actively tracking and rotates the device or recomposes the screen,
  * *Then* zero navigation events SHALL be emitted and the active tab SHALL NOT be changed.

### 1.4 System Invariants
* **Initial Start Navigation**: Starting tracking (`READY -> TRACKING`) MUST ALWAYS navigate the pager from Page 0 to Page 1 (`NavigateTo(0)`).
* **Control Tab Manual Resume**: Resuming a manually paused session from the Control tab (`Page 0`) MUST navigate to Page 1 (`NavigateTo(0)`), preserving the core behavior specified in `ATT-1340`.
* **Auto-Pause Neutrality**: Ending Auto-Pause (via speed recovery or tapping Resume on the HUD banner) MUST NEVER scroll the pager or disrupt the athlete's active tab.
* **Telemetry Tab Manual Resume Neutrality**: Resuming a manually paused session while on `Page >= 1` (e.g. via smartwatch or notification) MUST NOT force navigation back to Page 1.
* **Cold Start & Recreation Immunity**: App launch, process restoration, or screen rotation while in `TRACKING` mode MUST emit zero navigation events.
* **Thread Safety**: `mWasAutoPaused` is guarded by `AtomicBoolean`; `BANALServiceRepository` flows run on main dispatcher and post safely.
* **Channel Buffering & Gesture Safety**: All navigation emissions utilize `Channel<TabNavigationEvent>(Channel.BUFFERED)` (`REQ-UI-412`), and pager scroll animations respect manual swipe interruptions (`CancellationException`) without hard-snap fallback (`REQ-UI-412.3`).

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-163` (*Cockpit Tab Navigation on Tracking Initiation*), implemented under `ATT-1340` (`commit e253c312` and `commit 26cf352c`). Targets `TrainingApplication.java`, `BANALServiceRepository.kt`, `TrackingTabsViewModel.kt`, `TrackingTabsScreen.kt`, and `TrackingTabsViewModelTest.kt`.
* **Historical Origin & Commit Trace**: Ticket `ATT-1340`. Commit `e253c312` originally guarded rising-edge navigation with `previousMode != PAUSED`. Rainer observed that after pausing manually on the Control tab (Page 0), tapping "Resume" left the athlete stranded on Page 0 instead of animating to the metrics screen. 15 minutes later, commit `26cf352c` removed `previousMode != PAUSED` so resuming from `PAUSED` would scroll to Page 1. Refined in `ATT-4097` (`REQ-UI-412`) for channel buffering. Amending under `ATT-4373` (`Sprint 2026-43.2`).
* **Root Reason for Existing Formulation**: Commit `26cf352c` removed the `PAUSED` guard as an expedient fix to support Control tab manual resume without plumbing pager page state into `TrackingTabsViewModel` or distinguishing Auto-Pause from manual pause. This had the unintended side effect of causing every Auto-Pause resumption (and manual resume from Page >= 1) to yank the athlete away from secondary metrics or Map navigation back to Page 1.
* **Preservation of Core Invariants**: Initial tracking start (`READY -> TRACKING`) and Control tab manual resume (`PAUSED -> TRACKING` on `Page 0`) continue to navigate smoothly to Page 1. Resuming from Auto-Pause and resuming from Page >= 1 now preserves the active tab. Channel buffering (`REQ-UI-412`), swipe gesture cancellation resilience (`REQ-UI-412.3`), and 100% clean-room test pass rate remain strictly preserved.

---

## 2. Test Specification (`TST-UI-115`)

### 2.1 Unit Tests (`TrackingTabsViewModelTest.kt`)

#### Test Case 1: `testTrackingStartNavigatesToFirstCockpitTab` (`[TST-UI-115.1]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsViewModelTest.kt`
* **Preconditions**: `TrackingTabsViewModel` initialized with `trackingModeLiveData = TrackingMode.READY`.
* **Action**: Transition `trackingModeLiveData.value = TrackingMode.TRACKING`.
* **Expected Result**: `navigationEvent` emits exactly 1 event: `TabNavigationEvent.NavigateTo(0)`.

#### Test Case 2: `testAutoPauseResumePreservesCurrentTab` (`[TST-UI-115.2]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsViewModelTest.kt`
* **Preconditions**:
  * `TrackingTabsViewModel` running in `TrackingMode.TRACKING`.
  * User is on Page 2 (`viewModel.onPageSelected(2)`).
  * Auto-pause triggered: `trackingModeLiveData.value = TrackingMode.PAUSED`.
  * `mockBanalRepo.wasAutoPaused` emits `true`.
* **Action**: Transition `trackingModeLiveData.value = TrackingMode.TRACKING` (Auto-Pause ends).
* **Expected Result**: `navigationEvent` emits zero events (`events.isEmpty()`), preserving Page 2.

#### Test Case 3: `testManualResumeFromControlTabNavigatesToFirstCockpitTab` (`[TST-UI-115.3]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsViewModelTest.kt`
* **Preconditions**:
  * `TrackingTabsViewModel` running in `TrackingMode.TRACKING`.
  * User is on Control tab (`viewModel.onPageSelected(0)`).
  * Manual pause triggered: `trackingModeLiveData.value = TrackingMode.PAUSED`.
  * `mockBanalRepo.wasAutoPaused` emits `false`.
* **Action**: Transition `trackingModeLiveData.value = TrackingMode.TRACKING` (Manual Resume).
* **Expected Result**: `navigationEvent` emits `TabNavigationEvent.NavigateTo(0)`, smoothly navigating back to Cockpit Page 1.

#### Test Case 4: `testManualResumeFromTelemetryTabPreservesCurrentTab` (`[TST-UI-115.4]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsViewModelTest.kt`
* **Preconditions**:
  * `TrackingTabsViewModel` running in `TrackingMode.TRACKING`.
  * User is on Map / Telemetry tab (`viewModel.onPageSelected(2)`).
  * Manual pause triggered: `trackingModeLiveData.value = TrackingMode.PAUSED`.
  * `mockBanalRepo.wasAutoPaused` emits `false`.
* **Action**: Transition `trackingModeLiveData.value = TrackingMode.TRACKING` (e.g. resumed via smartwatch).
* **Expected Result**: `navigationEvent` emits zero events, strictly preserving Page 2.

#### Test Case 5: `testColdStartInTrackingModeDoesNotTriggerSpuriousNavigation` (`[TST-UI-115.5]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsViewModelTest.kt`
* **Preconditions**: `TrackingTabsViewModel` initialized while `trackingModeLiveData.value = TrackingMode.TRACKING`.
* **Action**: Collect `navigationEvent` and advance scheduler.
* **Expected Result**: `events.isEmpty()`.

#### Test Case 6: `testMultipleStartStopCyclesReliability` (`[TST-UI-115.6]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsViewModelTest.kt`
* **Preconditions**: `TrackingTabsViewModel` initialized in `READY`.
* **Action**: Execute 3 cycles of `READY -> TRACKING -> READY`.
* **Expected Result**: Exactly 3 `NavigateTo(0)` events emitted across the 3 cycles without suppression.

#### Test Case 7: `testPagerOffsetCalculationForScreenMode` (`[TST-UI-115.7]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsViewModelTest.kt`
* **Preconditions**: Target index = 0.
* **Action**: Calculate target page with offset for `ScreenMode.TRACKING`, `ScreenMode.CONFIGURATION`, and `ScreenMode.PREVIEW`.
* **Expected Result**: `TRACKING -> 1`, `CONFIGURATION -> 0`, `PREVIEW -> 0`.

### 2.2 Localization & Parity Audit (`[TST-UI-115.8]`)
* **Scope**: Localization Parity Test
* **Target**: `TranslationParityTest.kt`
* **Goal**: Verify 100% translation parity and matching format specifiers across all 9 locales:
  * EN, DE, ES, FR, IT, JA, NL, PL, PT
* **Expected Result**: Zero missing keys, zero format specifier mismatches.

### 2.3 Clean-Room Regression Suite (`[TST-UI-115.9]`)
* **Scope**: Full Clean-Room Unit Test Suite
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite (3,193+ tests).
* **Expected Result**: BUILD SUCCESSFUL, 0 failures, 0 regressions.

---

## 3. Requirement to Test Traceability Matrix

| Requirement Clause | Test Case | Target Class / File | Verification Scope | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-163.1` (Origin-Aware Resume State) | `TST-UI-115.2`, `TST-UI-115.3` | `TrackingTabsViewModelTest.kt` | Unit test verifying `wasAutoPaused` propagation | Specified |
| `REQ-UI-163.2` (Active Page Synchronization) | `TST-UI-115.2`, `TST-UI-115.4` | `TrackingTabsViewModelTest.kt` | Unit test verifying `currentPage` tracking in ViewModel | Specified |
| `REQ-UI-163.3` (Origin-Aware Navigation Decision) | `TST-UI-115.1` - `TST-UI-115.4` | `TrackingTabsViewModelTest.kt` | Unit test verifying `shouldNavigate` decision matrix | Specified |
| `REQ-UI-163.4` (Auto-Pause Resume Tab Preservation) | `TST-UI-115.2` | `TrackingTabsViewModelTest.kt` | Unit test asserting 0 events on Auto-Pause resume | Specified |
| `REQ-UI-163.5` (Telemetry/Map Tab Resume Preservation) | `TST-UI-115.4` | `TrackingTabsViewModelTest.kt` | Unit test asserting 0 events on manual resume from Page >= 1 | Specified |
| `REQ-UI-163.6` (Recomposition & Cold Start Invariants) | `TST-UI-115.5`, `TST-UI-115.6` | `TrackingTabsViewModelTest.kt` | Unit test asserting cold start neutrality & start reliability | Specified |
| `REQ-UI-106` (9-Language Parity) | `TST-UI-115.8` | `TranslationParityTest` | 9-locale string parity verification | Specified |
| `REQ-PRO-001` (Clean-Room Full Suite) | `TST-UI-115.9` | `./gradlew testDebugUnitTest` | Full regression suite execution | Specified |
