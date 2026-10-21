# Stage 1 Analysis: ATT-4456 - [Bug] [High] Stop-tracking coachmark immediately self-dismisses during scroll from Control tab to Cockpit

**Ticket**: [ATT-4456](https://rainerblind.atlassian.net/browse/ATT-4456)  
**Sub-task**: [ATT-4493](https://rainerblind.atlassian.net/browse/ATT-4493) (`[Analysis]`)  
**Parent Epic**: [ATT-2617](https://rainerblind.atlassian.net/browse/ATT-2617) (*[Epic] Optimize first impression*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.4`  
**Branch**: `feature/ATT-4456`  
**Requirement Mapping**: `REQ-UI-367`  
**Test Mapping**: `TST-UI-330`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-10-21  

---

## 1. Problem Statement & Motivation

During Sprint 2026-42.4 (`ATT-3387` / `REQ-UI-367`), the first-time Stop Tracking Coachmark banner (`StopTrackingCoachmarkBanner`) was introduced to solve a discoverability gap for novice athletes:
When starting an active workout from the `Control` tab (`Page 0`), `TrackingTabsViewModel` automatically initiates a scroll transition to the primary telemetry cockpit tab (`Page 1`). Because workout Stop and Pause controls reside exclusively on `Page 0`, first-time users need guidance on how to swipe right or tap the `Control` / `Aufzeichnung` tab to access Stop and Pause controls.

However, during human verification of `ATT-3387` on a fresh installation, the following defect was observed:
- When the athlete taps **Start** on `Page 0` (after granting required permissions) and the pager animates from `Page 0` to `Page 1`, the coachmark banner is **missing** upon reaching the cockpit tab.
- The coachmark banner self-dismisses and permanently acknowledges itself (`tuningDataStore.setHasSeenStopTrackingHint(true)`) during the 200–300ms scroll transition before the pager ever settles on `Page 1`. As a result, first-time users never see the onboarding guidance.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Jetpack Compose `PagerState` Animation Dynamics
In Jetpack Compose `androidx.compose.foundation.pager.PagerState`:
1. `currentPage`: An `Int` representing the page index closest to the current scroll offset / snap position. During a programmatic scroll (`animateScrollToPage(1)`) or user swipe from `Page 0` to `Page 1`, `currentPage` flips from `0` to `1` as soon as the scroll progress crosses the 50% midpoint threshold.
2. `settledPage`: An `Int` representing the page index where the pager has completely settled. Crucially, `settledPage` **remains `0`** for the entire duration of the animation until the motion fully stops and velocity reaches zero.
3. `isScrollInProgress`: A `Boolean` indicating whether the pager is actively in motion (via touch drag or fling/scroll animation).

### 2.2 Forensic Recomposition & State Race Timeline
In `TrackingTabsScreen.kt` (lines 667–699) and `TrackingTabsViewModel.kt` (lines 310–338), the coachmark display and auto-acknowledgement logic currently interacts as follows:

```kotlin
// TrackingTabsScreen.kt
val showStopTrackingCoachmark = TrackingTabsViewModel.shouldShowStopTrackingCoachmark(
    trackingMode = trackingMode,
    currentPage = pagerState.currentPage,
    screenMode = screenMode,
    hasSeenHint = hasSeenStopTrackingHint,
    isDismissPending = isCoachmarkDismissPending
)

LaunchedEffect(showStopTrackingCoachmark) {
    if (showStopTrackingCoachmark) {
        trackingTabsViewModel.markCoachmarkRenderedInSession()
    }
}

LaunchedEffect(
    pagerState.settledPage,
    trackingMode,
    hasSeenStopTrackingHint,
    hasRenderedCoachmarkInSession,
    isCoachmarkDismissPending
) {
    if (TrackingTabsViewModel.shouldAutoAcknowledgeStopTrackingCoachmark(
            trackingMode = trackingMode,
            settledPage = pagerState.settledPage,
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

```kotlin
// TrackingTabsViewModel.kt
fun shouldAutoAcknowledgeStopTrackingCoachmark(
    trackingMode: TrackingMode,
    settledPage: Int,
    hasSeenHint: Boolean?,
    hasRenderedCoachmarkInSession: Boolean,
    isDismissPending: Boolean = false
): Boolean {
    if (hasSeenHint != false || !hasRenderedCoachmarkInSession || isDismissPending) return false
    val isActiveWorkout = trackingMode == TrackingMode.TRACKING || trackingMode == TrackingMode.PAUSED
    return isActiveWorkout && settledPage == 0
}
```

The execution timeline that produces the bug:
1. **$t_0$ (Idle on Control tab)**: The athlete taps *Start* on `Page 0`.
   - `currentPage = 0`, `settledPage = 0`, `isScrollInProgress = false`.
   - `hasSeenStopTrackingHint = false`, `hasRenderedCoachmarkInSession = false`, `isCoachmarkDismissPending = false`.
   - `showStopTrackingCoachmark = false` (because `currentPage == 0`).
   - `shouldAutoAcknowledgeStopTrackingCoachmark = false` (because `!hasRenderedCoachmarkInSession`).
2. **$t_1$ (Tracking rising edge)**: `TrackingTabsViewModel` detects `TrackingMode.TRACKING` and emits `TabNavigationEvent.NavigateTo(0)` (`offset = 1`, target page 1).
3. **$t_2$ (Scroll animation starts)**: `TrackingTabsScreen` launches `pagerState.animateScrollToPage(1)`.
   - `isScrollInProgress` becomes `true`.
   - `settledPage` remains `0`.
4. **$t_3$ (~100ms, Midpoint crossing)**: The scroll animation crosses the 50% midpoint between `Page 0` and `Page 1`.
   - `pagerState.currentPage` flips from `0` to `1`.
   - `pagerState.settledPage` is **still `0`**!
   - `pagerState.isScrollInProgress` is **`true`**.
5. **$t_4$ (Premature arming)**: On the recomposition frame where `currentPage` flips to `1`:
   - `shouldShowStopTrackingCoachmark(currentPage = 1, ...)` evaluates to `true`.
   - `LaunchedEffect(showStopTrackingCoachmark)` observes `showStopTrackingCoachmark: false -> true` and executes:
     ```kotlin
     trackingTabsViewModel.markCoachmarkRenderedInSession()
     ```
   - `hasRenderedCoachmarkInSession` becomes `true` while the scroll animation is still in flight!
6. **$t_5$ (~16ms later, Recomposition mid-scroll)**:
   - Because `hasRenderedCoachmarkInSession` flipped to `true`, the auto-acknowledgement `LaunchedEffect(...)` immediately re-evaluates `shouldAutoAcknowledgeStopTrackingCoachmark`:
     - `hasSeenHint == false` (PASS)
     - `hasRenderedCoachmarkInSession == true` (PASS - prematurely armed!)
     - `isDismissPending == false` (PASS)
     - `isActiveWorkout == true` (PASS)
     - `settledPage == 0` (PASS - because the pager has not settled yet!)
   - `shouldAutoAcknowledgeStopTrackingCoachmark` returns `true`!
   - `trackingTabsViewModel.markCoachmarkDismissPending()` is called.
   - `tuningDataStore.setHasSeenStopTrackingHint(true)` is dispatched.
   - Optimistic dismissal immediately sets `isDismissPending = true`, which turns `showStopTrackingCoachmark` back to `false` on the very next frame.
7. **$t_6$ (~250ms, Scroll finishes)**: The pager finally settles on `Page 1` (`settledPage = 1`, `isScrollInProgress = false`).
   - By this time, the coachmark was already suppressed and permanently saved as seen in `DataStore`. The athlete never sees the banner.

### 2.3 Call-Site Audit
| Component / File | Role | Defect Mechanism |
| :--- | :--- | :--- |
| `TrackingTabsScreen.kt` (lines 675–679) | LaunchedEffect marking session rendered | Unconditionally invoked `markCoachmarkRenderedInSession()` on `showStopTrackingCoachmark`, which triggers as soon as `currentPage > 0` mid-scroll, before the pager actually settles on the cockpit tab. |
| `TrackingTabsScreen.kt` (lines 681–699) | LaunchedEffect auto-acknowledgement | Observed `pagerState.settledPage` without checking `pagerState.currentPage` or `pagerState.isScrollInProgress`, allowing auto-acknowledgement to trigger mid-scroll when `settledPage` is still `0`. |
| `TrackingTabsViewModel.kt` (lines 328–338) | Pure predicate `shouldAutoAcknowledgeStopTrackingCoachmark` | Only evaluated `settledPage == 0` without checking `currentPage == 0` and `!isScrollInProgress`. |
| `StopTrackingCoachmarkTest.kt` | Unit tests | Tested `settledPage == 0` in isolation without simulating in-flight animations (`currentPage = 1, settledPage = 0, isScrollInProgress = true`). |

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Gate `markCoachmarkRenderedInSession()` in `TrackingTabsScreen.kt` on settled cockpit state (`pagerState.settledPage > 0 && !pagerState.isScrollInProgress && showStopTrackingCoachmark`).
  * Harden `TrackingTabsViewModel.shouldAutoAcknowledgeStopTrackingCoachmark(...)` to require `currentPage == 0 && settledPage == 0 && !isScrollInProgress` in addition to `hasRenderedCoachmarkInSession == true`.
  * Update `LaunchedEffect` keys and argument forwarding in `TrackingTabsScreen.kt`.
  * Update and expand unit tests in `StopTrackingCoachmarkTest.kt` to explicitly verify scroll-in-flight midpoint states, settled states, and edge cases.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modifications to `StopTrackingCoachmarkBanner.kt` visual design, typography, buttons, or elevation.
  * No changes to `TuningPreferencesDataStore.kt` keys, default values, or factory reset logic.
  * No changes to string translations or resource files.
  * No alterations to unrelated tab navigation or workout recording logic.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-367` (*First-Time Stop Tracking Swipe and Tab Coachmark Banner on Cockpit Screen with Persistent Acknowledgement and Factory Reset Integration*). Targets `TrackingTabsScreen.kt`, `TrackingTabsViewModel.kt`, and `StopTrackingCoachmarkTest.kt`.
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-42.4 via `ATT-3387` (commit `141c12415ce3a77ea148be82c4961bd453bad188`).
* **Root Reason for Existing Formulation**: `ATT-3387` introduced `shouldAutoAcknowledgeStopTrackingCoachmark` to automatically dismiss the coachmark when the athlete returns to the Control tab (`Tab 0`). The requirement used `hasRenderedCoachmarkInSession` to distinguish between the athlete being on `Tab 0` prior to starting tracking and subsequently returning to `Tab 0`. However, the formulation assumed `hasRenderedCoachmarkInSession` would only become `true` once the athlete viewed the cockpit, missing that `showStopTrackingCoachmark` turned `true` mid-animation while `settledPage` remained `0`.
* **Preservation of Core Invariants**:
  1. The core functional invariant that returning to `Tab 0` auto-acknowledges the coachmark once viewed is strictly preserved and made robust against scroll animation transients.
  2. The onboarding goal (guiding novice athletes to Stop/Pause buttons) is fully restored.
  3. Manual dismissal via the action button or close icon remains unchanged.
  4. Factory reset via `tuningDataStore.resetToDefaults()` remains fully functional.
  5. Constant-weight zero-jump layout behavior of `Box` and `HorizontalPager` is strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Two-Layer Defense Architecture

To provide robust, fail-safe protection against race conditions during pager animations and manual swipes, a two-layer defense is implemented:

#### Layer 1: Gated Session Rendering in `TrackingTabsScreen.kt`
Only mark `hasRenderedCoachmarkInSession = true` when the pager has actually *settled* on a cockpit tab (`settledPage > 0`), the pager is stationary (`!isScrollInProgress`), and `showStopTrackingCoachmark == true`:
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
This guarantees that while scrolling away from `Page 0` toward `Page 1`, `hasRenderedCoachmarkInSession` remains `false` throughout the entire animation. It only becomes `true` after the athlete has arrived and settled on `Page 1+` with the coachmark visible.

#### Layer 2: Hardened Pure Predicate in `TrackingTabsViewModel.kt`
Extend `shouldAutoAcknowledgeStopTrackingCoachmark` to verify that the pager is fully stationary and settled on `Page 0`:
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
This guarantees that even if `hasRenderedCoachmarkInSession` is `true`, auto-acknowledgement cannot execute while scrolling or when `currentPage != 0`. It strictly requires `currentPage == 0 && settledPage == 0 && !isScrollInProgress`.

#### Layer 2 Call-Site in `TrackingTabsScreen.kt`
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

### 5.2 Unit Test Strategy in `StopTrackingCoachmarkTest.kt`
1. Update existing `shouldAutoAcknowledgeStopTrackingCoachmark` tests for the new signature (`currentPage`, `settledPage`, `isScrollInProgress`).
2. Add dedicated test cases simulating the in-flight scroll transition from `Page 0` to `Page 1`:
   - Midpoint scroll: `currentPage = 1`, `settledPage = 0`, `isScrollInProgress = true` $\rightarrow$ auto-acknowledge returns `false`.
   - Pre-midpoint scroll: `currentPage = 0`, `settledPage = 0`, `isScrollInProgress = true` $\rightarrow$ auto-acknowledge returns `false`.
   - Settled on Cockpit: `currentPage = 1`, `settledPage = 1`, `isScrollInProgress = false` $\rightarrow$ auto-acknowledge returns `false`.
   - In-flight return swipe to Control: `currentPage = 0`, `settledPage = 1`, `isScrollInProgress = true` $\rightarrow$ auto-acknowledge returns `false`.
   - Fully settled return to Control: `currentPage = 0`, `settledPage = 0`, `isScrollInProgress = false`, `hasRendered = true` $\rightarrow$ auto-acknowledge returns `true`.
3. Contract tests ensuring `TrackingTabsScreen.kt` contains the settled gating condition and passes all state parameters into the predicate.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. **Zero Regression**: All existing unit tests and contracts must continue to pass without regressions.
  2. **Thread Safety**: Pure domain predicates remain purely functional with zero side-effects. Compose state collection is confined to the Main dispatcher.
  3. **Human Decision Gate**: Parent ticket progression remains strictly governed by human approval at Final Review.
  4. **DataStore Integrity**: Preference persistence and factory reset contracts remain intact.
* **Risk Rating**: **LOW**
  - **Justification**: The defect is an isolated Compose animation race condition resolved by adding state gating (`!isScrollInProgress`, `settledPage > 0`, `currentPage == 0`). No database schemas, background services, IPC, or sensor pipelines are touched. The logic is verified through deterministic unit and contract tests.
