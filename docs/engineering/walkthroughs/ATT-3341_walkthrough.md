# Stage 5: Walkthrough - ATT-3341: Reopen dismissed LiveSegment and Climb bottom sheets on bottom swipe-up gesture

**Ticket**: [ATT-3341](https://rainerblind.atlassian.net/browse/ATT-3341)  
**Sub-task**: [ATT-3447](https://rainerblind.atlassian.net/browse/ATT-3447) (`[Test]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-359`  
**Test Mapping**: `TST-UI-320`  
**Branch**: `feature/ATT-3341`  
**Author**: AI Agent 1 (Verification & Quality Engineer)  
**Date**: 2026-10-13  

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - Amends `REQ-UI-343` (*Live Segment and Climb Cockpit BottomSheet Default Fully Expanded Presentation*, Sprint 2026-41.9 / Sprint 2026-42.1, ATT-3176 & ATT-3319) and `REQ-UI-327` (*Tracking Tab Live Climb and Segment Cockpit BottomSheet Active-Tab Gating & State Synchronization*, Sprint 2026-41.7, ATT-2945).
   - Formulated as `REQ-UI-359` (*Live Segment and Climb Bottom Sheet Gesture Re-Expansion and Peek Tap-to-Expand*).
   - Targets `SensorGridScreen.kt` and `SensorGridBottomSheetGestureTest.kt`.
2. **Historical Origin & Commit Trace**:
   - Sprint 2026-41.7 (`ATT-2945`): Established the bottom sheet scaffold with active tab gating.
   - Sprint 2026-41.9 / 42.1 (`ATT-3176` / `ATT-3319`): Updated initial value and reactive launched effect to default to `SheetValue.Expanded` upon entity activation.
3. **Root Reason for Existing Formulation**:
   - In `ATT-3319`, automatic pop-up was implemented so athletes wouldn't have to manually pull the sheet open on every climb. However, when an athlete manually dismissed or collapsed the sheet to inspect underlying sensor grid tiles (power, cadence, lap timer), `activeEntityKey` remained constant, leaving no reactive or gestural mechanism to re-expand the sheet for the rest of the climb/segment.
4. **Preservation of Core Invariants**:
   - **Invariant 1 (Initial Auto-Expansion Invariant)**: When a new climb or segment becomes active, the sheet unconditionally expands to `SheetValue.Expanded` via `LaunchedEffect(shouldShowBottomSheet, activeEntityKey)` (`REQ-UI-343`).
   - **Invariant 2 (Touch Neutrality Invariant)**: When `shouldShowBottomSheet == false` (or when inactive/different tab/screen mode), zero bottom gesture interceptors are composed, and background map panning, sensor grid scrolling, and cockpit interactions receive 100% of touches without latency (`REQ-UI-359.3`).
   - **Invariant 3 (Design System & Styling Invariant)**: Sheet container shape, elevation, and contour styling per `BottomSheetDesign` remain intact.
   - **Invariant 4 (Full-Suite Pass Rate Invariant)**: 100% clean-room test pass rate across the full test suite with zero regressions.

---

## 1. Executive Summary

This deliverable documents Stage 5 verification and walkthrough for **ATT-3341**. When a Strava Live Segment or Route Climb activated during workout tracking, the bottom sheet opened fully per `REQ-UI-343`. However, if the athlete swiped down to dismiss or partially collapse the sheet to check underlying cockpit metrics, there was no intuitive way to pull the sheet back up, because `activeEntityKey` did not change and the standard drag handle was hidden.

ATT-3341 resolves this limitation by adding bottom-edge swipe-up gesture detection and peek header tap-to-expand in `SensorGridScreen.kt` in full compliance with `REQ-UI-359`:
1. **Coroutine Scope Instantiation**: Instantiated `val coroutineScope = rememberCoroutineScope()` in `SensorGridScreen.kt`.
2. **Peek Header Tap-to-Expand**:
   - Added `Modifier.clickable` to the LiveSegment and LiveClimb header boxes when `scaffoldState.bottomSheetState.currentValue == SheetValue.PartiallyExpanded`, launching `scaffoldState.bottomSheetState.expand()`.
3. **Bottom Swipe-Up Gesture Overlay**:
   - Added an overlay `Box` inside `BoxWithConstraints` aligned to `Alignment.BottomCenter` with height `BottomSheetDesign.PeekHeightLiveSegment + navBarHeight`, gated strictly on `shouldShowBottomSheet && scaffoldState.bottomSheetState.currentValue != SheetValue.Expanded`.
   - Attached `pointerInput(shouldShowBottomSheet)` with `detectVerticalDragGestures` to detect upward swipe gestures (`totalDragY < -30f`), triggering `scaffoldState.bottomSheetState.expand()`.
   - Also attached `.clickable` for direct tap-to-expand.
4. **Touch Neutrality**:
   - When no sheet is active (`shouldShowBottomSheet == false`), the overlay is completely omitted from the Compose hierarchy, ensuring zero interference with map panning or sensor scrolling.

---

## 2. Verification Results

### 2.1 Targeted Unit & Contract Testing

#### `SensorGridBottomSheetGestureTest`
- `testSensorGridScreen_declaresCoroutineScopeForSheetExpansion`: **PASSED** (Verifies `val coroutineScope = rememberCoroutineScope()` presence).
- `testSensorGridScreen_bottomSwipeGestureDetector_gatedOnShouldShowBottomSheetAndNotExpanded`: **PASSED** (Verifies condition `if (shouldShowBottomSheet && scaffoldState.bottomSheetState.currentValue != SheetValue.Expanded)` and height calculation).
- `testSensorGridScreen_bottomSwipeGestureDetector_triggersExpandOnUpwardDrag`: **PASSED** (Verifies `detectVerticalDragGestures` and `expand()` call).
- `testSensorGridScreen_peekHeader_triggersExpandOnClick`: **PASSED** (Verifies tap-to-expand modifier when `PartiallyExpanded`).
- `testSensorGridScreen_inactiveState_suppressesGestureInterceptors`: **PASSED** (Verifies touch neutrality and zero peek height when inactive).

#### `SensorGridScreenClimbSuppressionTest` & `SensorGridScreenLayoutTest`
- `SensorGridScreenClimbSuppressionTest`: **PASSED**.
- `SensorGridScreenLayoutTest`: **PASSED**.

#### `TranslationParityTest`
- `TranslationParityTest`: **PASSED** (100% localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT; 0 new strings needed).

### 2.2 Clean-Room Test Suite Execution
- **Command**: `./gradlew testDebugUnitTest`
- **Result**: **BUILD SUCCESSFUL** (100% pass rate across entire suite, zero regressions).

---

## 3. Living Documentation Status

- **`docs/requirements.md`**: `REQ-UI-359` updated to `Verified`.
- **`docs/tests.md`**: `TST-UI-320` updated to `Verified`.
- **Governance Check**: `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-42.1` -> Clean pass.

---

## 4. Subtask & Ticket Advancement Plan

1. Stage 5 subtask **ATT-3447** (`[Test]`) description updated with this walkthrough.
2. Subtask transitioned to `In Überprüfung` and Gate 5 audit initiated via `python3 tools/review_agent.py audit ATT-3447`.
3. Upon Gate 5 sign-off (`RECOMMEND PASS`), subtask moved to `Erledigt` via transition `freigabe`.
4. Parent ticket **ATT-3341** transitioned to `Final Review (Human)`.
5. Changes committed to `feature/ATT-3341`.
6. Branch `feature/ATT-3341` merged into `sprint/2026-42.1` with `--no-ff`, and `feature/ATT-3341` deleted.
