# Stage 3: Implementation Plan - ATT-4456: [Bug] [High] Stop-tracking coachmark immediately self-dismisses during scroll from Control tab to Cockpit

**Ticket**: [ATT-4456](https://rainerblind.atlassian.net/browse/ATT-4456)  
**Sub-task**: [ATT-4495](https://rainerblind.atlassian.net/browse/ATT-4495) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2617](https://rainerblind.atlassian.net/browse/ATT-2617) (*[Epic] Optimize first impression*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.4`  
**Requirement Mapping**: `REQ-UI-367` (*First-Time Stop Tracking Swipe and Tab Coachmark Banner on Cockpit Screen with Persistent Acknowledgement and Factory Reset Integration*)  
**Test Mapping**: `TST-UI-330` (*First-Time Stop Tracking Swipe and Tab Coachmark Banner on Cockpit Screen Verification*)  
**Branch**: `feature/ATT-4456`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-10-21  

---

## 1. Problem Description & Background

During human verification of `ATT-3387` on a clean install, a race condition was identified in the first-time Stop Tracking Coachmark banner onboarding flow:
When starting an active workout from the `Control` tab (`Page 0`), `TrackingTabsViewModel` emits a navigation event that programmatically scrolls the `HorizontalPager` to the primary telemetry cockpit tab (`Page 1`). 

Under Jetpack Compose `PagerState`:
1. `currentPage` flips from `0` to `1` as soon as the scroll animation crosses the 50% midpoint threshold.
2. `settledPage` remains `0` while `isScrollInProgress == true` until the animation completely finishes.
3. Because `showStopTrackingCoachmark` turned `true` at the 50% midpoint (`currentPage > 0`), `LaunchedEffect(showStopTrackingCoachmark)` executed `markCoachmarkRenderedInSession()` mid-animation.
4. On the subsequent recomposition frame (~16ms later, still mid-scroll), `LaunchedEffect(settledPage, ...)` evaluated `shouldAutoAcknowledgeStopTrackingCoachmark`. Since `hasRenderedCoachmarkInSession == true` and `settledPage == 0`, it evaluated to `true`, instantly triggering optimistic dismissal and saving `hasSeenStopTrackingHint = true` to `DataStore` before the athlete ever reached `Page 1`.

This plan details the construction of a two-layer defense that gates session-rendered state on a settled cockpit page and hardens the auto-acknowledgement predicate to require stationary settlement on `Page 0` (`currentPage == 0 && settledPage == 0 && !isScrollInProgress`).

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-367` (*First-Time Stop Tracking Swipe and Tab Coachmark Banner on Cockpit Screen with Persistent Acknowledgement and Factory Reset Integration*)
  - Clause 1: Decoupled DataStore Persistence & Factory Reset (`TuningPreferencesDataStore.kt`)
  - Clause 2: ViewModel Session State & Configuration-Change Survival (`TrackingTabsViewModel.kt`)
  - Clause 3: Settled Gating & Race-Free Auto-Acknowledgement (`TrackingTabsScreen.kt`, `TrackingTabsViewModel.kt`)
  - Clause 4: Zero-Jump Stable Overlay & Small-Screen Ergonomics (`StopTrackingCoachmarkBanner.kt`, `TrackingTabsScreen.kt`)
  - Clause 5: 9-Language Localization Parity (`strings.xml`)
* **Test Mapping**: `TST-UI-330` (*First-Time Stop Tracking Swipe and Tab Coachmark Banner on Cockpit Screen Verification*)
  - `TST-UI-330.1`: Midpoint in-flight scroll suppression (`currentPage = 1, settledPage = 0, isScrollInProgress = true`)
  - `TST-UI-330.2`: In-flight return swipe suppression (`currentPage = 0, settledPage = 1, isScrollInProgress = true`)
  - `TST-UI-330.3`: Fully settled return to Control tab auto-acknowledgement (`currentPage = 0, settledPage = 0, !isScrollInProgress`)
  - `TST-UI-330.4`: Synchronous optimistic dismissal preservation (`isDismissPending`)
  - `TST-UI-330.5`: `TrackingTabsScreen.kt` settled gating & parameter forwarding contract tests
  - `TST-UI-330.6`: 9-language localization parity verification
  - `TST-UI-330.7`: Clean-room full unit test suite regression (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: All existing unit tests and contracts must continue to pass cleanly.
2. **Zero Layout Shifts**: `HorizontalPager` is hosted in a constant-weight `Box(modifier = Modifier.weight(1f).fillMaxWidth())` with `StopTrackingCoachmarkBanner` overlaid at `Alignment.TopCenter` (`zIndex = 2f`, `heightIn(max = 120.dp)`), guaranteeing no layout jumps or size fluctuations.
3. **Pure Domain Predicates**: `shouldShowStopTrackingCoachmark` and `shouldAutoAcknowledgeStopTrackingCoachmark` remain pure, deterministic Kotlin functions with zero external side effects.
4. **DataStore Key & Type Integrity**: `KEY_HAS_SEEN_STOP_TRACKING_HINT` remains a `booleanPreferencesKey("tuning_has_seen_stop_tracking_hint")` with default `false` registered in `ALL_KEYS` for factory reset.
5. **Human Decision Gate**: Parent ticket progression remains strictly governed by human approval at `Final Review (Human)`.
6. **One Atomic Commit per Ticket**: No intermediate git commits during Stages 1–4; squashed commit created only upon verified Stage 5 completion.

---

## 4. Proposed Architectural Changes

### Component 1: `TrackingTabsViewModel.kt` (Presentation / State Management)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsViewModel.kt`
* **Changes**:
  - Update `shouldAutoAcknowledgeStopTrackingCoachmark` to expand the method signature:
    ```kotlin
    fun shouldAutoAcknowledgeStopTrackingCoachmark(
        trackingMode: TrackingMode,
        currentPage: Int,
        settledPage: Int,
        isScrollInProgress: Boolean,
        hasSeenHint: Boolean?,
        hasRenderedCoachmarkInSession: Boolean,
        isDismissPending: Boolean = false
    ): Boolean {
        if (hasSeenHint != false || !hasRenderedCoachmarkInSession || isDismissPending) return false
        val isActiveWorkout = trackingMode == TrackingMode.TRACKING || trackingMode == TrackingMode.PAUSED
        return isActiveWorkout && currentPage == 0 && settledPage == 0 && !isScrollInProgress
    }
    ```
  - This ensures auto-acknowledgement can only evaluate to `true` when the athlete has genuinely returned to `Page 0`, the animation has settled (`settledPage == 0`), the pager is stationary (`!isScrollInProgress`), and `currentPage == 0`.

### Component 2: `TrackingTabsScreen.kt` (UI Layer / Jetpack Compose)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt`
* **Changes**:
  1. Gate `markCoachmarkRenderedInSession()` on settled cockpit state:
     ```kotlin
     LaunchedEffect(
         pagerState.settledPage,
         pagerState.isScrollInProgress,
         showStopTrackingCoachmark
     ) {
         if (showStopTrackingCoachmark && pagerState.settledPage > 0 && !pagerState.isScrollInProgress) {
             trackingTabsViewModel.markCoachmarkRenderedInSession()
         }
     }
     ```
     This prevents `hasRenderedCoachmarkInSession` from arming while scrolling away from `Page 0`.
  2. Update the auto-acknowledgement `LaunchedEffect` to observe all relevant `pagerState` properties and pass them into `shouldAutoAcknowledgeStopTrackingCoachmark`:
     ```kotlin
     LaunchedEffect(
         pagerState.currentPage,
         pagerState.settledPage,
         pagerState.isScrollInProgress,
         trackingMode,
         hasSeenStopTrackingHint,
         hasRenderedCoachmarkInSession,
         isCoachmarkDismissPending
     ) {
         if (TrackingTabsViewModel.shouldAutoAcknowledgeStopTrackingCoachmark(
                 trackingMode = trackingMode,
                 currentPage = pagerState.currentPage,
                 settledPage = pagerState.settledPage,
                 isScrollInProgress = pagerState.isScrollInProgress,
                 hasSeenHint = hasSeenStopTrackingHint,
                 hasRenderedCoachmarkInSession = hasRenderedCoachmarkInSession,
                 isDismissPending = isCoachmarkDismissPending
             )
         ) {
             trackingTabsViewModel.markCoachmarkDismissPending()
             tuningDataStore.setHasSeenStopTrackingHint(true)
         }
     }
     ```

### Component 3: `StopTrackingCoachmarkTest.kt` (Test Suite)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/StopTrackingCoachmarkTest.kt`
* **Changes**:
  - Update all existing calls to `shouldAutoAcknowledgeStopTrackingCoachmark` to provide `currentPage`, `settledPage`, and `isScrollInProgress`.
  - Add exhaustive unit tests covering:
    - Midpoint in-flight scroll suppression (`currentPage = 1, settledPage = 0, isScrollInProgress = true`)
    - Pre-midpoint in-flight scroll suppression (`currentPage = 0, settledPage = 0, isScrollInProgress = true`)
    - Return swipe in-flight suppression (`currentPage = 0, settledPage = 1, isScrollInProgress = true`)
    - Settled cockpit non-acknowledgement (`currentPage = 1, settledPage = 1, isScrollInProgress = false`)
    - Fully settled return to Control tab (`currentPage = 0, settledPage = 0, isScrollInProgress = false, hasRendered = true`)
  - Update architectural contract test asserting `TrackingTabsScreen.kt` contains the settled gating condition and passes all state parameters into the predicate.

### UI Consistency (Rule 23 — Mandatory Section)
* **Reference screen / component**: `TrackingTabsScreen.kt` / `StopTrackingCoachmarkBanner.kt`
* **Reused components**: `StopTrackingCoachmarkBanner`, `HorizontalPager`, `Box`
* **Theme tokens**: Material 3 `secondaryContainer` fill, `secondary` border outline (40% alpha), `onSecondaryContainer` typography, `RoundedCornerShape(12.dp)`, minimum touch target size `Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)`.
* **New one-off styles & justification**: None. The visual appearance and layout of `StopTrackingCoachmarkBanner` and `TrackingTabsScreen` are completely unchanged; this change strictly hardens the Compose event lifecycle and pure state evaluation.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Harden Pure Auto-Acknowledgement Predicate in `TrackingTabsViewModel.kt`
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsViewModel.kt`
* **Action**:
  - Update `shouldAutoAcknowledgeStopTrackingCoachmark` method signature to accept `currentPage: Int`, `settledPage: Int`, and `isScrollInProgress: Boolean`.
  - Update implementation to enforce `currentPage == 0 && settledPage == 0 && !isScrollInProgress`.

### Step 2: Implement Settled Gating and State Forwarding in `TrackingTabsScreen.kt`
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt`
* **Action**:
  - Replace `LaunchedEffect(showStopTrackingCoachmark)` with `LaunchedEffect(pagerState.settledPage, pagerState.isScrollInProgress, showStopTrackingCoachmark)` and wrap the invocation of `markCoachmarkRenderedInSession()` in `if (showStopTrackingCoachmark && pagerState.settledPage > 0 && !pagerState.isScrollInProgress)`.
  - Update the auto-acknowledgement `LaunchedEffect` keys to include `pagerState.currentPage`, `pagerState.settledPage`, and `pagerState.isScrollInProgress`.
  - Pass `currentPage = pagerState.currentPage`, `settledPage = pagerState.settledPage`, and `isScrollInProgress = pagerState.isScrollInProgress` into `shouldAutoAcknowledgeStopTrackingCoachmark`.

### Step 3: Update and Expand Unit & Contract Tests in `StopTrackingCoachmarkTest.kt`
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/StopTrackingCoachmarkTest.kt`
* **Action**:
  - Update existing test invocations of `shouldAutoAcknowledgeStopTrackingCoachmark`.
  - Add test methods verifying in-flight animation states (`isScrollInProgress == true`), midpoint crossings, and settled returns.
  - Update contract test asserting `TrackingTabsScreen.kt` checks `pagerState.settledPage > 0 && !pagerState.isScrollInProgress && showStopTrackingCoachmark` and forwards `currentPage`, `settledPage`, `isScrollInProgress`.

### Step 4: Targeted Test Execution
* **Target Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.StopTrackingCoachmarkTest"
  ```
* **Expected Result**: 100% test pass rate with zero failures.

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Targeted unit and contract execution: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.StopTrackingCoachmarkTest"`
  - Full test suite execution: `./gradlew testDebugUnitTest`
  - Requirement governance verification: `python3 tools/verify_requirement_governance.py`
* **Rollback Plan**:
  - All changes reside on the isolated branch `feature/ATT-4456`.
  - If any unexpected regression occurs, the branch can be cleanly reset via `git checkout develop && git branch -D feature/ATT-4456` without impacting any production or release branches.
