# Stage 3: Implementation Plan - ATT-5851: MapDetailLayout: Prevent floating map buttons (Share & overlay FABs) from colliding with system status bar when header collapses

**Ticket**: [ATT-5851](https://rainerblind.atlassian.net/browse/ATT-5851)  
**Sub-task**: [ATT-6025](https://rainerblind.atlassian.net/browse/ATT-6025) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Requirement Mapping**: `REQ-UI-491` (`REQ-UI-491.1`, `REQ-UI-491.2`)  
**Test Mapping**: `TST-UI-491` (`TST-UI-491.1`–`TST-UI-491.4`)  
**Branch**: `feature/ATT-5851`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Problem Description & Background
In full-screen detail views utilizing `MapDetailLayout.kt` with `useStatusBarsPadding == true` (Favorite Routes, Route Details, Segment Details, Climbs), scrolling collapses the top header offscreen, reducing `currentTopPaddingPx` to `0`. While `ATrainingTrackerMap` bleeds edge-to-edge behind the status bar, the floating Share button and top-aligned `overlay()` FABs slide up to `y = 16.dp`, colliding directly with Android system status bar icons (clock, 5G, battery).

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-491` (*MapDetailLayout Status Bar Inset Clearance for Collapsed Header Floating Controls*)
  * `REQ-UI-491.1`: Dynamic clearance offset `(statusBarHeightPx - currentTopPaddingPx).coerceAtLeast(0)`.
  * `REQ-UI-491.2`: Inset padding applied to floating controls container enclosing `overlay()` and Share button.
* **Test Mapping**: `TST-UI-491` (*MapDetailLayout Floating Controls Status Bar Clearance Verification Suite*)
  * `TST-UI-491.1`: Dynamic clearance calculation contract test in `MapDetailLayoutCollapsingHeaderContractTest`.
  * `TST-UI-491.2`: Floating controls container padding contract test in `MapDetailLayoutCollapsingHeaderContractTest`.
  * `TST-UI-491.3`: Pure clearance math unit test (expanded = `0.dp`, collapsed = `statusBarHeightDp`, sheet = `0.dp`).
  * `TST-UI-491.4`: Tier 1 targeted regression suite (`ui.map.*`, `ThemeColorCentralizationAuditTest`, `build.*`).

---

## 3. System Invariants & Preserved Behavior
1. **Edge-to-Edge Bleed**: `ATrainingTrackerMap` remains unpadded, rendering edge-to-edge behind the status bar.
2. **Expanded State Spacing**: When the header is expanded, buttons maintain standard `16.dp` spacing below the header.
3. **Bottom Sheet Mode**: When `useStatusBarsPadding == false`, `statusBarHeightPx` evaluates to `0` (offset = `0.dp`).
4. **Scope Isolation**: No modifications to bottom-sheet drag handle or header click behavior (reserved for `ATT-5835`).

---

## 4. Proposed Architectural Changes & UI Consistency
* **`MapDetailLayout.kt`**:
  * Compute `val statusBarHeightPx = if (useStatusBarsPadding) WindowInsets.statusBars.getTop(density) else 0`.
  * Compute `val floatingControlsTopOffsetDp = with(density) { (statusBarHeightPx - currentTopPaddingPx).coerceAtLeast(0).toDp() }`.
  * Update `mapBox` signature to accept `controlsTopOffsetDp: Dp`.
  * Wrap `overlay()` and `if (showFloatingShareButton)` in `Box(Modifier.fillMaxSize().padding(top = controlsTopOffsetDp))`.
  * Pass `floatingControlsTopOffsetDp` to `mapBox` at lines 815, 900, and 957.
* **UI Consistency**:
  * Reference component: Existing `MapDetailLayout.kt` floating controls.
  * Reused components: `Box`, `WindowInsets.statusBars`, `Modifier.padding()`.
  * Theme tokens: Existing `16.dp` FAB padding and semantic tokens preserved. No new one-off styles.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1: Production Code Updates (`MapDetailLayout.kt`)**:
   - Compute `statusBarHeightPx` and `floatingControlsTopOffsetDp` inside `BoxWithConstraints`.
   - Update `mapBox` to enclose `overlay()` and Share FAB inside `Box(Modifier.fillMaxSize().padding(top = controlsTopOffsetDp))`.
   - Pass `floatingControlsTopOffsetDp` to all three `mapBox` invocations.
2. **Step 2: Automated Contract & Unit Tests (`MapDetailLayoutCollapsingHeaderContractTest.kt`)**:
   - Add `testMapDetailLayout_computesDynamicStatusBarClearance` (`TST-UI-491.1`).
   - Add `testMapDetailLayout_appliesTopOffsetToFloatingControlsContainer` (`TST-UI-491.2`).
   - Add `testMapDetailLayout_floatingControlsOffsetCalculationLogic` (`TST-UI-491.3`).
3. **Step 3: Verification Execution**:
   - Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
   - Run `python3 tools/verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit & contract test suite execution; full suite in Stage 5.
* **Rollback Plan**: Atomic commit on `feature/ATT-5851`; clean revert via `git reset --hard` if tests fail.
