# Stage 1 Analysis: ATT-3341 - Reopen dismissed LiveSegment and Climb bottom sheets on bottom swipe-up gesture

**Ticket**: [ATT-3341](https://rainerblind.atlassian.net/browse/ATT-3341)  
**Sub-task**: [ATT-3443](https://rainerblind.atlassian.net/browse/ATT-3443) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Branch**: `feature/ATT-3341`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13  

---

## 1. Problem Statement & Motivation

When an athlete tracks an activity and enters a Strava Live Segment or Route Climb, `SensorGridScreen.kt` automatically pops up a bottom sheet (`BottomSheetScaffold`) to the fully expanded state (`SheetValue.Expanded`) per `REQ-UI-343` (ATT-3319), displaying real-time elevation profile telemetry, gradient pacing, and delta times.

During a ride or run, athletes frequently swipe the sheet down to inspect underlying sensor grid tiles (power, heart rate, cadence, lap timers). However, once the athlete has dismissed or swiped the sheet down, there is no way to bring it back. When the athlete later wants to inspect their climb progress or segment pacing, swiping up from the bottom of the screen has zero effect—the sheet remains trapped down and inaccessible for the entire remaining duration of the active entity.

Providing a responsive bottom swipe-up gesture and tap-to-expand on the peek bar restores complete athlete control, allowing seamless hands-free toggling between full cockpit sensor metrics and climb elevation telemetry.

---

## 2. Root Cause Analysis (Forensic Investigation)

Forensic inspection of `SensorGridScreen.kt` (lines 220–305) reveals three structural factors preventing the sheet from reappearing after dismissal:

1. **Complete Off-Screen Displacement in `SheetValue.Hidden`**:
   - `rememberStandardBottomSheetState(initialValue = initialSheetValue, skipHiddenState = false)` allows downward swipes to transition past peek height into `SheetValue.Hidden`.
   - In `SheetValue.Hidden`, the bottom sheet layout is translated 100% off-screen at the bottom edge. Because the sheet is not rendered within the viewport, user touch gestures land on background views (sensor grid or map), which do not forward drag events to the bottom sheet state.
2. **Absence of Bottom-Edge Gesture Interceptor**:
   - `SensorGridScreen` does not provide a bottom-edge touch interception layer or swipe handler to detect upward dragging when an active entity exists but its sheet is collapsed or hidden.
3. **Static `LaunchedEffect` Key During Ongoing Entity**:
   - `LaunchedEffect(shouldShowBottomSheet, activeEntityKey)` drives programmatic sheet expansion exclusively when `activeEntityKey` changes (i.e. transitioning into a new segment or climb).
   - While riding the same segment, `activeEntityKey` remains constant. Therefore, after a manual dismissal, no reactive trigger re-expands the sheet.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Implement bottom-edge upward swipe gesture detection in `SensorGridScreen.kt` when `shouldShowBottomSheet == true` and `scaffoldState.bottomSheetState.currentValue != SheetValue.Expanded`, smoothly calling `scaffoldState.bottomSheetState.expand()`.
  * Support tap-to-expand on the peek header when the sheet is at peek height (`SheetValue.PartiallyExpanded`).
  * Ensure the sheet rests cleanly at peek height (or provides an intuitive bottom handle/affordance if hidden) so athletes always have an accessible target to bring it back.
  * Add comprehensive unit and gesture contract tests in `SensorGridBottomSheetGestureTest.kt`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Redesigning the visual styling or elevation profile rendering inside `LiveSegmentSheet.kt` or `LiveClimbSheet.kt`.
  * Altering climb detection or segment matching heuristics in `LiveClimbsRepository` or `LiveSegmentsRepository`.
  * Changing bottom sheet behavior on inactive tabs or when no live entity is active (`shouldShowBottomSheet == false`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**:
  - Amends `REQ-UI-343` (*Live Segment and Climb Cockpit BottomSheet Default Fully Expanded Presentation*, Sprint 2026-41.9 / Sprint 2026-42.1, ATT-3176 & ATT-3319) and `REQ-UI-327` (*Tracking Tab Live Climb and Segment Cockpit BottomSheet Active-Tab Gating & State Synchronization*, Sprint 2026-41.7, ATT-2945).
  - Formulated as `REQ-UI-359` (*Live Segment and Climb Bottom Sheet Gesture Re-Expansion and Peek Tap-to-Expand*).
  - Targets `SensorGridScreen.kt` and `SensorGridBottomSheetGestureTest.kt`.
* **Historical Origin & Commit Trace**:
  - `ATT-2945` established the bottom sheet scaffold with active tab gating.
  - `ATT-3319` updated the initial value and reactive launched effect to default to `SheetValue.Expanded` upon entity activation.
* **Root Reason for Existing Formulation**:
  - In `ATT-3319`, the team focused on ensuring the sheet automatically popped up to full height on new entities so athletes did not have to swipe up initially. However, the manual re-opening path after dismissal was overlooked.
* **Preservation of Core Invariants**:
  - **Invariant 1 (Default Full Height Expansion)**: When a new entity activates, it must still expand automatically (`REQ-UI-343`).
  - **Invariant 2 (Inactive Suppression)**: When no entity is active (`shouldShowBottomSheet == false`), peek height must remain `0.dp`, sheet must remain `Hidden`, and zero swipe interceptors may interfere with map or sensor grid gestures.
  - **Invariant 3 (Active Tab Gating)**: The sheet and gesture detector must only activate when `isTabActive == true` and `screenMode == ScreenMode.TRACKING`.
  - **Invariant 4 (100% Clean-Room Test Suite Pass)**: Zero regressions across existing tracking tests.

---

## 5. Architectural Strategy & High-Level Solution

1. **Bottom Swipe-Up Gesture Interceptor**:
   - In `SensorGridScreen.kt`, within the `BottomSheetScaffold` body:
   - When `shouldShowBottomSheet == true` and `scaffoldState.bottomSheetState.currentValue != SheetValue.Expanded`:
     - Render an overlay gesture target at `Alignment.BottomCenter` (height `BottomSheetDesign.PeekHeightLiveSegment + navBarHeight` or at least `56.dp`).
     - Attach `Modifier.pointerInput(shouldShowBottomSheet)` using `detectVerticalDragGestures`:
       - Track accumulated upward drag delta $\Delta y$.
       - When $\Delta y < -20\text{dp}$ (or velocity upward), invoke `coroutineScope.launch { scaffoldState.bottomSheetState.expand() }`.
2. **Tap-to-Expand Handler**:
   - On the sheet content container or peek overlay:
     - When `scaffoldState.bottomSheetState.currentValue == SheetValue.PartiallyExpanded`, tapping anywhere on the visible header invokes `coroutineScope.launch { scaffoldState.bottomSheetState.expand() }`.
3. **Controlled Peek vs Hidden States**:
   - Ensure the sheet smoothly collapses to `PartiallyExpanded` when dragged down, and if hidden, the bottom gesture strip remains active to capture the upward swipe.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. No interference with sensor grid scrolling or map gestures when `shouldShowBottomSheet == false`.
  2. Automatic expansion to full height on new active entities preserved (`REQ-UI-343`).
  3. Strict active-tab gating maintained (`isTabActive`).
  4. 100% unit test pass rate across entire project suite.
* **Risk Rating**: **LOW**
  - The changes are strictly localized to `SensorGridScreen.kt` touch/gesture handling and only active while a climb or segment is active.
