# Stage 3: Implementation Plan - ATT-3341: Reopen dismissed LiveSegment and Climb bottom sheets on bottom swipe-up gesture

**Ticket**: [ATT-3341](https://rainerblind.atlassian.net/browse/ATT-3341)  
**Sub-task**: [ATT-3445](https://rainerblind.atlassian.net/browse/ATT-3445) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-359` (*Live Segment and Climb Bottom Sheet Gesture Re-Expansion and Peek Tap-to-Expand*)  
**Test Mapping**: `TST-UI-320` (*Live Segment and Climb Bottom Sheet Gesture Re-Expansion, Peek Tap-to-Expand, and Inactive Touch Neutrality Verification*)  
**Branch**: `feature/ATT-3341`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13  

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - Amends `REQ-UI-343` (*Live Segment and Climb Cockpit BottomSheet Default Fully Expanded Presentation*, Sprint 2026-41.9 / Sprint 2026-42.1, ATT-3176 & ATT-3319) and `REQ-UI-327` (*Tracking Tab Live Climb and Segment Cockpit BottomSheet Active-Tab Gating & State Synchronization*, Sprint 2026-41.7, ATT-2945).
   - Formulated as `REQ-UI-359` (*Live Segment and Climb Bottom Sheet Gesture Re-Expansion and Peek Tap-to-Expand*).
   - Targets `SensorGridScreen.kt` and `SensorGridBottomSheetGestureTest.kt`.
2. **Historical Origin & Commit Trace**:
   - `ATT-2945` established the bottom sheet scaffold with active tab gating and peek height `126.dp + navBarHeight`.
   - `ATT-3319` updated the initial value and reactive launched effect to default to `SheetValue.Expanded` upon entity activation.
3. **Root Reason for Existing Formulation**:
   - `ATT-3319` implemented automatic expansion to full height when entering an entity so athletes wouldn't need to swipe up initially. However, when an athlete manually dismissed or collapsed the sheet to inspect underlying sensor grid tiles (power, cadence, lap timer), `activeEntityKey` remained constant, leaving no reactive or gestural mechanism to re-expand the sheet for the rest of the climb/segment.
4. **Preservation of Core Invariants**:
   - **Invariant 1 (Initial Expansion Preserved)**: `LaunchedEffect(shouldShowBottomSheet, activeEntityKey)` must continue to unconditionally call `scaffoldState.bottomSheetState.expand()` whenever entering an active entity (`REQ-UI-343`).
   - **Invariant 2 (Inactive Touch Neutrality)**: When `shouldShowBottomSheet == false`, peek height must remain `0.dp`, sheet must remain `SheetValue.Hidden`, and zero gesture detectors may intercept touches over the map or sensor tiles (`REQ-UI-327`).
   - **Invariant 3 (Active Tab Gating)**: Bottom sheet and gesture detector are strictly gated on `isTabActive == true` and `screenMode == ScreenMode.TRACKING`.
   - **Invariant 4 (100% Full-Suite Pass Rate)**: Clean-room test suite execution must pass with zero failures.

---

## 1. Problem Description & Background

During live tracking with an active Live Segment or Climb, `BottomSheetScaffold` animates to full height (`SheetValue.Expanded`) per `REQ-UI-343` (ATT-3319). Athletes frequently swipe the sheet down to inspect sensor metrics. Once swiped down or dismissed, the sheet cannot be reopened because touches land on background views that do not propagate gestures to the sheet state.

This plan specifies the implementation of:
1. A bottom-edge upward swipe gesture detector that calls `scaffoldState.bottomSheetState.expand()` when upward drag is detected.
2. A tap-to-expand handler on the peek area and bottom strip that expands the sheet on single tap.
3. Strict conditional gating ensuring 100% touch neutrality when no segment or climb is active.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-359` (*Live Segment and Climb Bottom Sheet Gesture Re-Expansion and Peek Tap-to-Expand*)
  * `REQ-UI-359.1`: Bottom-edge swipe-up gesture detection calling `expand()`.
  * `REQ-UI-359.2`: Peek area tap-to-expand calling `expand()`.
  * `REQ-UI-359.3`: Inactive touch neutrality (`shouldShowBottomSheet == false`).
  * `REQ-UI-359.4`: System invariants, initial expansion, and test pass rate.
* **Test Mapping**: `TST-UI-320` (*Live Segment and Climb Bottom Sheet Gesture Re-Expansion, Peek Tap-to-Expand, and Inactive Touch Neutrality Verification*)
  * `TST-UI-320.1`: Architectural & AST layout structure tests (`SensorGridBottomSheetGestureTest.kt`).
  * `TST-UI-320.2`: Touch neutrality & invariant verification (`SensorGridBottomSheetGestureTest.kt`).
  * `TST-UI-320.3`: 9-language localization audit (`TranslationParityTest.kt`).
  * `TST-UI-320.4`: Clean-room full suite regression execution (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Automatic Initial Expansion (`REQ-UI-343`)**: Unconditional expansion on new active entity activation remains untouched.
2. **Touch Neutrality When Inactive (`REQ-UI-327`)**: Zero gesture interceptors active when `shouldShowBottomSheet == false`; background map and sensor grid interactions remain completely unimpeded.
3. **Active Tab & Mode Gating**: Sheet and gesture detector strictly gated on `isTabActive && screenMode == ScreenMode.TRACKING`.
4. **Clean-Room Test Stability**: 100% test pass rate across the full unit test suite.

---

## 4. Proposed Architectural Changes

### Component 1: `SensorGridScreen.kt`
- Add `val coroutineScope = rememberCoroutineScope()` for bottom sheet programmatic control.
- In `sheetContent`: Add `.clickable` to sheet wrapper when in `SheetValue.PartiallyExpanded` to trigger `coroutineScope.launch { scaffoldState.bottomSheetState.expand() }`.
- In `BottomSheetScaffold` body (within outer `BoxWithConstraints`):
  - When `shouldShowBottomSheet && scaffoldState.bottomSheetState.currentValue != SheetValue.Expanded`:
    - Add an overlay gesture area aligned at `Alignment.BottomCenter` with height `BottomSheetDesign.PeekHeightLiveSegment + navBarHeight`.
    - Apply `Modifier.pointerInput(shouldShowBottomSheet)` with `detectVerticalDragGestures`:
      - Accumulate `totalDragY`. When `totalDragY < -30f`, consume pointer and launch `scaffoldState.bottomSheetState.expand()`.
    - Apply `Modifier.clickable` to trigger `scaffoldState.bottomSheetState.expand()`.

### UI Consistency (Rule 23)
* **Reference screen / component**: `SensorGridScreen.kt` / `BottomSheetDesign.kt` (Tracking Cockpit bottom sheet).
* **Reused components**: `BottomSheetDesign.PeekHeightLiveSegment`, `BottomSheetDesign.SheetShape`, `BottomSheetDesign.SheetShadowElevation`.
* **Theme tokens**: `MaterialTheme.colorScheme.surface`, `navBarHeight`.
* **New one-off styles & justification**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Create Gesture & Contract Tests (`SensorGridBottomSheetGestureTest.kt`)
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridBottomSheetGestureTest.kt`.
* Implement `testSensorGridScreen_declaresCoroutineScopeForSheetExpansion`.
* Implement `testSensorGridScreen_bottomSwipeGestureDetector_gatedOnShouldShowBottomSheetAndNotExpanded`.
* Implement `testSensorGridScreen_bottomSwipeGestureDetector_triggersExpandOnUpwardDrag`.
* Implement `testSensorGridScreen_peekHeader_triggersExpandOnClick`.
* Implement `testSensorGridScreen_inactiveState_suppressesGestureInterceptors`.

### Step 2: Implement Bottom Swipe & Tap Re-Expansion in `SensorGridScreen.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`.
* Add `val coroutineScope = rememberCoroutineScope()`.
* Add tap-to-expand clickable on partially expanded sheet wrappers in `sheetContent`.
* Add bottom-edge upward swipe & tap gesture interceptor Box within `BoxWithConstraints`.

### Step 3: Run Targeted Unit Tests
* Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridBottomSheetGestureTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenClimbSuppressionTest"
  ```

### Step 4: Run Translation Parity & Full Suite Regression
* Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.TranslationParityTest"
  ./gradlew testDebugUnitTest
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit tests and clean-room full regression suite.
* **Rollback**: Clean git revert of `feature/ATT-3341` with zero data or schema impact.
