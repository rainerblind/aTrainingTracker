# Stage 2: Requirement & Test Specification - ATT-3341: Reopen dismissed LiveSegment and Climb bottom sheets on bottom swipe-up gesture

**Ticket**: [ATT-3341](https://rainerblind.atlassian.net/browse/ATT-3341)  
**Sub-task**: [ATT-3444](https://rainerblind.atlassian.net/browse/ATT-3444) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-359` (*Live Segment and Climb Bottom Sheet Gesture Re-Expansion and Peek Tap-to-Expand*)  
**Test Spec ID**: `TST-UI-320`  
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
   - `ATT-2945` (Sprint 2026-41.7): Introduced `BottomSheetScaffold` with active-tab gating and peek height `126.dp + navBarHeight`.
   - `ATT-3319` (Sprint 2026-42.1): Ensured initial expansion to `SheetValue.Expanded` whenever a new segment or climb becomes active via `LaunchedEffect(shouldShowBottomSheet, activeEntityKey)`.
3. **Root Reason for Existing Formulation**:
   - In `ATT-3319`, automatic pop-up was implemented so athletes wouldn't have to manually pull the sheet open on every climb. However, when an athlete manually dismissed or collapsed the sheet to inspect underlying sensor grid tiles (power, cadence, lap timer), `activeEntityKey` remained constant, leaving no reactive or gestural mechanism to re-expand the sheet for the rest of the climb/segment.
4. **Preservation of Core Invariants**:
   - **Invariant 1 (Initial Expansion Preserved)**: `LaunchedEffect(shouldShowBottomSheet, activeEntityKey)` must continue to unconditionally call `scaffoldState.bottomSheetState.expand()` whenever entering an active entity (`REQ-UI-343`).
   - **Invariant 2 (Inactive Touch Neutrality)**: When `shouldShowBottomSheet == false`, peek height must remain `0.dp`, sheet must remain `SheetValue.Hidden`, and zero gesture detectors may intercept touches over the map or sensor tiles (`REQ-UI-327`).
   - **Invariant 3 (Active Tab Gating)**: Bottom sheet and gesture detector are strictly gated on `isTabActive == true` and `screenMode == ScreenMode.TRACKING`.
   - **Invariant 4 (100% Full-Suite Pass Rate)**: Clean-room test suite execution must pass with zero failures.

---

## 1. Requirement Specification (REQ-UI-359)

### 1.1 Problem Statement & Rationale
During live tracking with an active Live Segment or Climb, athletes frequently swipe the bottom sheet down to inspect sensor metrics. Once swiped down or dismissed, the sheet currently cannot be reopened because touches land on background views that do not propagate gestures to the sheet state.

Providing an upward swipe gesture detector along the bottom edge, combined with tap-to-expand on the peek bar, guarantees that athletes can effortlessly toggle between sensor metrics and climb elevation telemetry at any point during an ongoing climb or segment.

### 1.2 Functional & Architectural Requirements
The system SHALL fulfill the following functional and architectural clauses:

1. **Bottom-Edge Swipe-Up Gesture Interceptor (`SensorGridScreen.kt`)**:
   - When `shouldShowBottomSheet == true` and `scaffoldState.bottomSheetState.currentValue != SheetValue.Expanded`:
     - The layout SHALL host a gesture detection area aligned at `Alignment.BottomCenter` covering the bottom edge and peek region (`BottomSheetDesign.PeekHeightLiveSegment + navBarHeight`).
     - The gesture detector SHALL monitor vertical drag gestures using `pointerInput(Unit)` and `detectVerticalDragGestures`.
     - When an upward drag is registered with accumulated vertical drag delta $\Delta y < -20\text{dp}$ (or negative upward velocity), the system SHALL execute `coroutineScope.launch { scaffoldState.bottomSheetState.expand() }`.
2. **Peek Area Tap-to-Expand Interaction**:
   - When `scaffoldState.bottomSheetState.currentValue == SheetValue.PartiallyExpanded`, tapping anywhere on the visible sheet header or gesture target SHALL invoke `coroutineScope.launch { scaffoldState.bottomSheetState.expand() }`.
3. **Touch Neutrality When Inactive**:
   - When `shouldShowBottomSheet == false` (or when `screenMode != ScreenMode.TRACKING` or `isTabActive == false`):
     - Zero bottom gesture strips or swipe interceptors SHALL be composed or active.
     - Background views (map panning, zoom controls, sensor grid vertical scrolling, and configuration interactions) SHALL receive 100% of touch events with zero interference or latency.
4. **Preservation of Invariants**:
   - The initial unconditional animation to `SheetValue.Expanded` on new entity activation (`REQ-UI-343`) SHALL remain fully operative.
   - The sheet container shape, elevation, and contour styling per `BottomSheetDesign` SHALL remain intact.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1: Upward Swipe Re-Expands Sheet**:
  * *Given* an active workout tracking session with an active Live Segment or Climb whose bottom sheet was swiped down
  * *When* the athlete swipes upward from the bottom of the screen
  * *Then* the bottom sheet smoothly re-expands to `SheetValue.Expanded`, displaying the full elevation profile and pacing HUD.
* **Criterion 2: Tap-to-Expand from Peek Height**:
  * *Given* the sheet is resting in `SheetValue.PartiallyExpanded`
  * *When* the athlete taps the visible header / peek area
  * *Then* the sheet smoothly animates to `SheetValue.Expanded`.
* **Criterion 3: Inactive Touch Neutrality**:
  * *Given* no live segment or climb is active (`shouldShowBottomSheet == false`)
  * *When* the athlete interacts with the lower portion of the screen
  * *Then* sensor grid scrolling and map gestures operate without interference.

---

## 2. Test Specification (TST-UI-320)

### TST-UI-320.1: Architectural & AST Layout Structure Tests (`SensorGridBottomSheetGestureTest.kt`)
* **Scope**: Unit & AST Structural Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridBottomSheetGestureTest.kt`
* **Assertions**:
  1. Verify `SensorGridScreen.kt` instantiates a `rememberCoroutineScope` for bottom sheet control.
  2. Verify bottom swipe gesture detector is conditionally active when `shouldShowBottomSheet == true` and `scaffoldState.bottomSheetState.currentValue != SheetValue.Expanded`.
  3. Verify upward swipe triggers `scaffoldState.bottomSheetState.expand()`.
  4. Verify tap-to-expand triggers `scaffoldState.bottomSheetState.expand()`.

### TST-UI-320.2: Touch Neutrality & Invariant Verification (`SensorGridBottomSheetGestureTest.kt`)
* **Scope**: Unit Test
* **Assertions**:
  1. Verify gesture detector is disabled / uncomposed when `shouldShowBottomSheet == false`.
  2. Verify `initialSheetValue` evaluates to `SheetValue.Expanded` when `shouldShowBottomSheet == true`.
  3. Verify `LaunchedEffect(shouldShowBottomSheet, activeEntityKey)` calls `expand()` unconditionally.

### TST-UI-320.3: 9-Language Localization Audit (`TranslationParityTest.kt`)
* **Scope**: Localization Parity Test
* **Assertions**: 100% translation parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### TST-UI-320.4: Clean-Room Full Suite Regression Execution
* **Command**: `./gradlew testDebugUnitTest`
* **Expected Result**: 100% tests pass with zero failures.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-320.1` | AST & Gesture Structure | `SensorGridScreen.kt` | `REQ-UI-359.1`, `REQ-UI-359.2` | Specified |
| `TST-UI-320.2` | Touch Neutrality & Invariants | `SensorGridScreen.kt` | `REQ-UI-359.3`, `REQ-UI-359.4` | Specified |
| `TST-UI-320.3` | Localization | `TranslationParityTest` | `REQ-UI-359.4` | Specified |
| `TST-UI-320.4` | Regression | Full Test Suite | `REQ-UI-359.4` | Specified |
