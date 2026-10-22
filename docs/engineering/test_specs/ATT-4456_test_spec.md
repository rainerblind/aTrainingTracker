# Stage 2: Requirement & Test Specification - ATT-4456: [Bug] [High] Stop-tracking coachmark immediately self-dismisses during scroll from Control tab to Cockpit

**Ticket**: [ATT-4456](https://rainerblind.atlassian.net/browse/ATT-4456)  
**Sub-task**: Pending Submission (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2617](https://rainerblind.atlassian.net/browse/ATT-2617) (*[Epic] Optimize first impression*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.4`  
**Requirement Mapping**: `REQ-UI-367` (*First-Time Stop Tracking Swipe and Tab Coachmark Banner on Cockpit Screen with Persistent Acknowledgement and Factory Reset Integration*)  
**Test Spec ID**: `TST-UI-330` (*First-Time Stop Tracking Swipe and Tab Coachmark Banner on Cockpit Screen Verification*)  
**Branch**: `feature/ATT-4456`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`)  
**Date**: 2026-10-21  

---

## 1. Requirement Specification (REQ-UI-367)

### 1.1 Problem Statement & Rationale
Identified during human verification of `ATT-3387` on a fresh application installation:
When an athlete taps **Start** on the `Control` tab (`Page 0`) and the pager automatically scrolls to the primary telemetry cockpit tab (`Page 1`), the first-time stop-tracking coachmark banner (`StopTrackingCoachmarkBanner`) is missing upon arriving at the cockpit screen.

Investigation revealed an asynchronous animation race condition in Jetpack Compose:
1. In Jetpack Compose `androidx.compose.foundation.pager.PagerState`, during an animated scroll (`animateScrollToPage(1)`) or user swipe from `Page 0` to `Page 1`, `currentPage` flips from `0` to `1` as soon as the motion crosses the 50% midpoint threshold.
2. However, `settledPage` **remains `0`** until the scroll animation has completely finished and velocity settles to zero (`isScrollInProgress == false`).
3. In `TrackingTabsScreen.kt`, `showStopTrackingCoachmark` currently checks only `currentPage > 0`, turning `true` at the 50% midpoint while the pager is still scrolling and `settledPage` is still `0`.
4. `LaunchedEffect(showStopTrackingCoachmark)` immediately triggers `trackingTabsViewModel.markCoachmarkRenderedInSession()`, setting `hasRenderedCoachmarkInSession = true` mid-flight.
5. On the very next recomposition frame (~16ms later, still mid-scroll), `LaunchedEffect(settledPage, ...)` re-evaluates `shouldAutoAcknowledgeStopTrackingCoachmark(settledPage = 0, hasRenderedCoachmarkInSession = true, ...)`. Because `settledPage` is still `0` and `hasRenderedCoachmarkInSession` is now `true`, the auto-acknowledgement predicate evaluates to `true`, instantly invoking `markCoachmarkDismissPending()` and dispatching `tuningDataStore.setHasSeenStopTrackingHint(true)`.
6. By the time the pager settles on `Page 1`, the banner has already been suppressed and permanently persisted as seen, denying first-time athletes critical onboarding guidance on how to pause or stop active workouts.

### 1.2 Functional & Architectural Requirements
The system SHALL refine `REQ-UI-367` with the following architectural clauses:

1. **Settled Cockpit Session Render Gating (`TrackingTabsScreen.kt`)**:
   - `TrackingTabsScreen.kt` SHALL gate invocations of `trackingTabsViewModel.markCoachmarkRenderedInSession()` strictly on settled cockpit state:
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
   - While scrolling away from `Page 0` toward `Page 1+` (`isScrollInProgress == true`), `hasRenderedCoachmarkInSession` SHALL strictly remain `false`.

2. **Hardened Pure Auto-Acknowledgement Predicate (`TrackingTabsViewModel.kt`)**:
   - `TrackingTabsViewModel.Companion.shouldAutoAcknowledgeStopTrackingCoachmark` SHALL accept `currentPage: Int`, `settledPage: Int`, and `isScrollInProgress: Boolean`:
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
   - The predicate SHALL return `true` iff:
     1. `hasSeenHint == false` (user has not previously acknowledged or dismissed the hint),
     2. `!isDismissPending` (no optimistic dismissal in progress),
     3. `hasRenderedCoachmarkInSession == true` (coachmark has previously rendered on a settled cockpit page during this session),
     4. `trackingMode == TrackingMode.TRACKING || trackingMode == TrackingMode.PAUSED` (active or paused workout),
     5. `currentPage == 0 && settledPage == 0` (pager is positioned at the Control tab),
     6. `!isScrollInProgress` (pager is stationary and fully settled).

3. **Synchronized Effect Keys and State Forwarding (`TrackingTabsScreen.kt`)**:
   - The auto-acknowledgement `LaunchedEffect` in `TrackingTabsScreen.kt` SHALL include `pagerState.currentPage`, `pagerState.settledPage`, and `pagerState.isScrollInProgress` in its key list:
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

### 1.3 Acceptance Criteria (Given-When-Then)

* **Criterion 1: Scroll-in-Flight Suppression from Page 0 to Page 1 (Midpoint)**:
  * *Given* a first-time athlete (`hasSeenHint == false`) starting tracking on `Page 0`,
  * *When* `pagerState.animateScrollToPage(1)` crosses the midpoint (`currentPage = 1`, `settledPage = 0`, `isScrollInProgress = true`),
  * *Then* `markCoachmarkRenderedInSession()` is NOT called, `hasRenderedCoachmarkInSession` remains `false`, and `shouldAutoAcknowledgeStopTrackingCoachmark` returns `false`.

* **Criterion 2: Settled Cockpit Display & Session Arming**:
  * *Given* the programmatic scroll arrives at `Page 1`,
  * *When* the animation finishes settling (`currentPage = 1`, `settledPage = 1`, `isScrollInProgress = false`),
  * *Then* `markCoachmarkRenderedInSession()` is executed, `hasRenderedCoachmarkInSession` becomes `true`, and `StopTrackingCoachmarkBanner` is visibly rendered at `Alignment.TopCenter`.

* **Criterion 3: Return Swipe In-Flight Non-Dismissal**:
  * *Given* the athlete has viewed the coachmark on `Page 1` (`hasRenderedCoachmarkInSession == true`),
  * *When* the athlete swipes back toward `Page 0` and is in mid-motion (`currentPage = 0`, `settledPage = 1`, `isScrollInProgress = true`),
  * *Then* `shouldAutoAcknowledgeStopTrackingCoachmark` returns `false`, preventing premature dismissal during swipe transitions.

* **Criterion 4: Fully Settled Return to Control Tab Auto-Acknowledgement**:
  * *Given* the coachmark has been viewed on `Page 1+` (`hasRenderedCoachmarkInSession == true`),
  * *When* the athlete's swipe settles completely on `Page 0` (`currentPage = 0`, `settledPage = 0`, `isScrollInProgress = false`),
  * *Then* `shouldAutoAcknowledgeStopTrackingCoachmark` returns `true`, triggering `markCoachmarkDismissPending()` and `tuningDataStore.setHasSeenStopTrackingHint(true)`.

* **Criterion 5: Manual Action Button and Close Icon Dismissal**:
  * *Given* the coachmark banner is displayed on `Page 1+`,
  * *When* the athlete taps the action button ("Show Control Tab") or close icon ("Dismiss"),
  * *Then* `markCoachmarkDismissPending()` immediately hides the banner and `tuningDataStore.setHasSeenStopTrackingHint(true)` permanently saves the preference.

* **Criterion 6: Factory Reset Restoration**:
  * *Given* an athlete who has previously acknowledged or dismissed the coachmark (`hasSeenHint == true`),
  * *When* factory defaults are restored in Advanced Settings (`tuningDataStore.resetToDefaults()`),
  * *Then* `KEY_HAS_SEEN_STOP_TRACKING_HINT` is removed from DataStore, restoring the hint to `false` for subsequent workouts.

### 1.4 System Invariants
1. **Zero Layout Shifts**: `HorizontalPager` height and scroll physics remain completely decoupled from the coachmark banner via the constant-weight `Box(modifier = Modifier.weight(1f).fillMaxWidth())` overlay container (`heightIn(max = 120.dp)`).
2. **Pure Domain Predicates**: `shouldShowStopTrackingCoachmark` and `shouldAutoAcknowledgeStopTrackingCoachmark` remain pure, deterministic Kotlin functions with zero external side effects.
3. **DataStore Key & Type Integrity**: `KEY_HAS_SEEN_STOP_TRACKING_HINT` remains a `booleanPreferencesKey("tuning_has_seen_stop_tracking_hint")` with default `false`.
4. **Clean-Room Test Regression**: 100% unit test pass rate across the full codebase (`./gradlew testDebugUnitTest`).

### 1.5. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-367` (*First-Time Stop Tracking Swipe and Tab Coachmark Banner on Cockpit Screen with Persistent Acknowledgement and Factory Reset Integration*). Targets `TrackingTabsScreen.kt`, `TrackingTabsViewModel.kt`, and `StopTrackingCoachmarkTest.kt`.
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-42.4 via `ATT-3387` (commit `141c12415ce3a77ea148be82c4961bd453bad188`). Hardened in Sprint 2026-43.4 via `ATT-4456`.
* **Root Reason for Existing Formulation**: `ATT-3387` introduced `shouldAutoAcknowledgeStopTrackingCoachmark` to automatically dismiss the coachmark when the athlete returns to the Control tab (`Tab 0`). The requirement used `hasRenderedCoachmarkInSession` to distinguish between being on `Tab 0` prior to starting tracking and subsequently returning to `Tab 0`. However, the formulation assumed `hasRenderedCoachmarkInSession` would only become `true` once the athlete viewed the settled cockpit, missing that `showStopTrackingCoachmark` flipped `true` mid-animation while `settledPage` remained `0`.
* **Preservation of Core Invariants**:
  1. The core functional invariant that returning to `Tab 0` auto-acknowledges the coachmark after being viewed is strictly preserved and made resilient against scroll animation transients.
  2. The onboarding goal (guiding novice athletes to Stop/Pause buttons) is fully restored.
  3. Manual dismissal via the action button or close icon remains unchanged.
  4. Factory reset via `tuningDataStore.resetToDefaults()` remains fully functional.
  5. Constant-weight zero-jump layout behavior of `Box` and `HorizontalPager` is strictly preserved.

---

## 2. Test Specification (TST-UI-330)

### Test Case 1: `shouldAutoAcknowledgeStopTrackingCoachmark` Scroll In-Flight Midpoint Suppression (`TST-UI-330.1`)
* **Scope**: Unit Test (`StopTrackingCoachmarkTest.kt`)
* **Target Method**: `TrackingTabsViewModel.Companion.shouldAutoAcknowledgeStopTrackingCoachmark`
* **Preconditions**:
  - `trackingMode = TrackingMode.TRACKING`
  - `hasSeenHint = false`
  - `hasRenderedCoachmarkInSession = true` (or `false`)
  - `isDismissPending = false`
* **Scenarios Evaluated**:
  1. Midpoint crossing during `animateScrollToPage(1)`: `currentPage = 1, settledPage = 0, isScrollInProgress = true`
  2. Pre-midpoint crossing: `currentPage = 0, settledPage = 0, isScrollInProgress = true`
  3. Settled on Cockpit: `currentPage = 1, settledPage = 1, isScrollInProgress = false`
* **Expected Result**: Assert `false` for all three scenarios.

### Test Case 2: `shouldAutoAcknowledgeStopTrackingCoachmark` In-Flight Return Swipe Suppression (`TST-UI-330.2`)
* **Scope**: Unit Test (`StopTrackingCoachmarkTest.kt`)
* **Target Method**: `TrackingTabsViewModel.Companion.shouldAutoAcknowledgeStopTrackingCoachmark`
* **Preconditions**:
  - `trackingMode = TrackingMode.TRACKING`
  - `hasSeenHint = false`
  - `hasRenderedCoachmarkInSession = true`
  - `isDismissPending = false`
* **Scenarios Evaluated**:
  1. Return swipe past midpoint toward Control: `currentPage = 0, settledPage = 1, isScrollInProgress = true`
  2. Return swipe pre-midpoint: `currentPage = 1, settledPage = 1, isScrollInProgress = true`
* **Expected Result**: Assert `false` for both scenarios.

### Test Case 3: `shouldAutoAcknowledgeStopTrackingCoachmark` Settled Return on Control Tab Auto-Acknowledgement (`TST-UI-330.3`)
* **Scope**: Unit Test (`StopTrackingCoachmarkTest.kt`)
* **Target Method**: `TrackingTabsViewModel.Companion.shouldAutoAcknowledgeStopTrackingCoachmark`
* **Preconditions**:
  - `hasSeenHint = false`
  - `hasRenderedCoachmarkInSession = true`
  - `isDismissPending = false`
  - `currentPage = 0`
  - `settledPage = 0`
  - `isScrollInProgress = false`
* **Scenarios Evaluated**:
  1. Active tracking: `trackingMode = TrackingMode.TRACKING` $\rightarrow$ Assert `true`
  2. Paused tracking: `trackingMode = TrackingMode.PAUSED` $\rightarrow$ Assert `true`
  3. Ready mode: `trackingMode = TrackingMode.READY` $\rightarrow$ Assert `false`
  4. Session not rendered: `hasRenderedCoachmarkInSession = false` $\rightarrow$ Assert `false`
  5. Already seen: `hasSeenHint = true` $\rightarrow$ Assert `false`
  6. DataStore loading: `hasSeenHint = null` $\rightarrow$ Assert `false`
  7. Dismiss pending: `isDismissPending = true` $\rightarrow$ Assert `false`

### Test Case 4: Synchronous Optimistic Dismissal Preservation (`TST-UI-330.4`)
* **Scope**: Unit Test (`StopTrackingCoachmarkTest.kt`)
* **Target Method**: `TrackingTabsViewModel.markCoachmarkDismissPending()` & predicate integration
* **Preconditions**: ViewModel initialized with `TRACKING` mode.
* **Action**:
  - `markCoachmarkRenderedInSession()`
  - Verify `hasRenderedCoachmarkInSession == true`
  - `markCoachmarkDismissPending()`
  - Verify `isCoachmarkDismissPending == true`
* **Expected Result**:
  - `shouldShowStopTrackingCoachmark` immediately returns `false`.
  - `shouldAutoAcknowledgeStopTrackingCoachmark` returns `false`.
  - Survives rapid `READY -> TRACKING` re-entry.

### Test Case 5: `TrackingTabsScreen` Settled Gating and Forwarding Architectural Contract Tests (`TST-UI-330.5`)
* **Scope**: Architectural Contract Test (`StopTrackingCoachmarkTest.kt`)
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt`
* **Assertions**:
  1. Verify `TrackingTabsScreen.kt` gates `markCoachmarkRenderedInSession()` on `pagerState.settledPage > 0 && !pagerState.isScrollInProgress && showStopTrackingCoachmark`.
  2. Verify `TrackingTabsScreen.kt` passes `currentPage = pagerState.currentPage`, `settledPage = pagerState.settledPage`, and `isScrollInProgress = pagerState.isScrollInProgress` into `shouldAutoAcknowledgeStopTrackingCoachmark`.
  3. Verify `HorizontalPager` is hosted in `Box(modifier = Modifier.weight(1f).fillMaxWidth())` with `Alignment.TopCenter` coachmark banner overlay.

### Test Case 6: 9-Language Localization Parity Audit (`TST-UI-330.6`)
* **Scope**: Localization Parity Test (`StopTrackingCoachmarkTest.kt` / `TranslationParityTest.kt`)
* **Target Strings**:
  - `coachmark_stop_tracking_hint`
  - `coachmark_stop_tracking_show_tab`
  - `coachmark_stop_tracking_dismiss`
* **Locales Verified**: `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
* **Expected Result**: 100% parity across all 9 locales, zero missing strings, zero empty values.

### Test Case 7: Clean-Room Full Suite Regression Execution (`TST-UI-330.7`)
* **Scope**: Full Unit Test Suite
* **Command**: `./gradlew testDebugUnitTest`
* **Expected Result**: 100% pass rate with zero failures and zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test / Subject | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-330.1]` | Unit | `TrackingTabsViewModel.shouldAutoAcknowledgeStopTrackingCoachmark` | `REQ-UI-367.3` | Specified |
| `[TST-UI-330.2]` | Unit | `TrackingTabsViewModel.shouldAutoAcknowledgeStopTrackingCoachmark` | `REQ-UI-367.3` | Specified |
| `[TST-UI-330.3]` | Unit | `TrackingTabsViewModel.shouldAutoAcknowledgeStopTrackingCoachmark` | `REQ-UI-367.3` | Specified |
| `[TST-UI-330.4]` | Unit | `TrackingTabsViewModel.markCoachmarkDismissPending` | `REQ-UI-367.2`, `REQ-UI-367.3` | Specified |
| `[TST-UI-330.5]` | Contract | `TrackingTabsScreen.kt` Settled Gating & State Forwarding | `REQ-UI-367.3`, `REQ-UI-367.4` | Specified |
| `[TST-UI-330.6]` | Localization | Translation Parity (`coachmark_stop_tracking_*`) | `REQ-UI-367.5`, `REQ-UI-106` | Specified |
| `[TST-UI-330.7]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
