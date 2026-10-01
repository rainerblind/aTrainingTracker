# Stage 2: Requirement & Test Specification - ATT-1890: Interactive Draggable Splitter to Resize Map and Telemetry Viewports in MapDetailLayout

**Ticket**: [ATT-1890](https://rainerblind.atlassian.net/browse/ATT-1890)  
**Sub-task**: [ATT-1908](https://rainerblind.atlassian.net/browse/ATT-1908) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.8`  
**Requirement Mapping**: `REQ-UI-223` (*Aftermath/Map: Interactive Draggable Splitter and Dynamic Viewport Resizing in MapDetailLayout*)  
**Test Spec ID**: `TST-UI-177`  
**Branch**: `feature/ATT-1890`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Requirement Specification (`REQ-UI-223`)

### 1.1 Problem Statement & Rationale
On the detailed post-workout inspection screen (`TrackOnMapScreen` / `MapDetailLayout`), the screen real estate is partitioned statically between the Google Map at the top (`weight(1f)`, with a hardcoded floor of `heightIn(min = 240.dp)`) and the lower scrollable graphs container (`weight(1.2f)`). Athletes have divergent workflows: examining winding trail geometry requires an expansive map (70–80% of screen height); analyzing continuous telemetry graphs (heart rate, pace, power) requires maximum vertical space for the charts. A static split limits both use cases. An interactive draggable splitter with double-tap reset and safe boundary clamping provides athletes with dynamic control over their viewport balance.

### 1.2 Functional & Architectural Requirements
The system SHALL provide an interactive draggable splitter bar to dynamically resize map and telemetry viewports on detailed inspection screens (`MapDetailLayout.kt` / `TrackOnMapScreen.kt`) (ATT-1890):

1. **Reusable SplitPaneDivider Component (`SplitPaneDivider.kt`)**:
   - The system SHALL implement a reusable composable `SplitPaneDivider(onDelta: (Float) -> Unit, onReset: () -> Unit, modifier: Modifier = Modifier)` in package `com.atrainingtracker.trainingtracker.ui.components.core`.
   - The divider SHALL provide a 24 dp vertical touch target (`DIVIDER_TOUCH_HEIGHT = 24.dp`) with a centered pill grip affordance (`GRIP_WIDTH = 32.dp`, `GRIP_HEIGHT = 4.dp`, `RoundedCornerShape(2.dp)`).
   - The divider SHALL handle vertical drag gestures via `draggable(orientation = Orientation.Vertical)` reporting $\Delta y$ pixel deltas.
   - The divider SHALL handle double-tap gestures via `pointerInput { detectTapGestures(onDoubleTap = { onReset() }) }` resetting to the default balanced ratio.
   - The divider SHALL provide an accessibility semantics content description (`map_splitter_content_description`) with 100% 9-language localization parity.

2. **Mathematical Clamping & Boundary Safety (`SplitPaneMath`)**:
   - The system SHALL encapsulate split calculations in pure helper `SplitPaneMath`:
     - `DEFAULT_SPLIT_FRACTION = 0.50f` (balanced 50/50 split).
     - `MIN_MAP_HEIGHT = 120.dp`: Map cannot shrink below 120 dp.
     - `MIN_LOWER_HEIGHT = 160.dp`: Lower container cannot shrink below 160 dp.
     - `updateFraction(currentFraction, deltaPx, availableHeightPx, minFraction, maxFraction)`: Clamps `splitFraction` strictly within `[minFraction, maxFraction]`, preventing either viewport from collapsing or producing graphical glitches.

3. **MapDetailLayout Dynamic Viewport Integration**:
   - In `MapDetailLayout.kt`, when `showMap == true` and `hasScrollableContent == true`, the layout SHALL manage `var splitFraction by rememberSaveable { mutableFloatStateOf(SplitPaneMath.DEFAULT_SPLIT_FRACTION) }`.
   - The Map Box SHALL receive `Modifier.weight(splitFraction).heightIn(min = SplitPaneMath.MIN_MAP_HEIGHT)`.
   - `SplitPaneDivider` SHALL be placed directly between the Map Box and the lower scroll container.
   - The lower container SHALL receive `Modifier.weight(1f - splitFraction).verticalScroll(rememberScrollState())`.
   - Double-tapping the divider SHALL reset `splitFraction` to `0.50f`.

4. **Preservation of Core Invariants**:
   - When `hasScrollableContent == false` (Routes & Segments), Map Box SHALL retain full `Modifier.weight(1f)` and lower section SHALL wrap its content (`wrapContentHeight()`), with zero divider displayed.
   - When `showMap == false` (LiveSegmentSheet), root Column retains compact `wrapContentHeight()`.
   - Map pan/zoom/compass gestures, multi-chart synchronized scrubbing, and snapshot sharing integrity remain 100% strictly preserved.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Downwards Drag Viewport Expansion)**:
  - *Given* an athlete viewing a completed workout on `MapDetailLayout`,
  - *When* dragging the horizontal splitter bar downwards,
  - *Then* the map viewport SHALL smoothly expand and the lower graph container SHALL shrink in real time.
* **Criterion 2 (Upwards Drag to Prioritize Graphs)**:
  - *Given* an athlete dragging the splitter bar upwards towards the top,
  - *When* reaching the upper limit,
  - *Then* the map SHALL clamp gracefully at 120 dp, giving maximum screen height to the scrollable graphs below.
* **Criterion 3 (Safe Boundary Clamping)**:
  - *Given* an extreme drag gesture upwards or downwards,
  - *When* reaching minimum or maximum limits,
  - *Then* the layout SHALL clamp gracefully without graphical glitches, negative sizes, or OpenGL crashes.
* **Criterion 4 (Double-Tap Reset to Default)**:
  - *Given* an altered map viewport ratio,
  - *When* the athlete double-taps the splitter bar,
  - *Then* the layout SHALL smoothly reset to the default balanced 50/50 split (`0.50f`).
* **Criterion 5 (Routes and Segments Invariance)**:
  - *Given* non-detailed screens where `hasScrollableContent == false` (e.g. routes or segments),
  - *When* rendered,
  - *Then* the splitter bar SHALL NOT be displayed and the map SHALL retain full available height.

### 1.4 System Invariants
1. Google Maps gesture isolation outside the scroll container remains intact.
2. Synchronized multi-chart scrubbing cursor parity is strictly maintained.
3. Composite workout summary snapshot generation remains 100% unimpacted.
4. 9-language localization parity across all supported locales.

---

## 2. Test Specification (`TST-UI-177`)

### Test Case 1: `SplitPaneMathTest` (`[TST-UI-177.1]`)
* **Scope**: Pure Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/SplitPaneMathTest.kt`
* **Test Procedures**:
  - `testCalculateAvailableHeight_subtractsDividerHeight()`: Verifies available height is non-negative and correctly subtracts divider height.
  - `testCalculateBounds_returnsClampedFractions()`: Verifies minFraction and maxFraction calculations for various screen heights (e.g. 800px, 1200px, 2400px).
  - `testUpdateFraction_normalDrag_adjustsFractionSmoothly()`: Verifies that downward delta increases fraction and upward delta decreases fraction.
  - `testUpdateFraction_extremeDeltas_clampsToSafeBounds()`: Verifies that extreme negative delta does not go below minFraction and extreme positive delta does not exceed maxFraction.
  - `testDefaultSplitFraction_isFiftyPercent()`: Verifies default is `0.50f`.

### Test Case 2: Visual & Structural Contract Tests (`[TST-UI-177.2]`)
* **Scope**: Structural Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/SplitPaneDividerVisualContractTest.kt`
* **Test Procedures**:
  - `testSplitPaneDivider_definesPillGripAndTouchTarget()`: Verifies 32dp x 4dp grip, 24dp touch height, and M3 styling.
  - `testSplitPaneDivider_handlesDragAndDoubleTap()`: Verifies presence of `draggable` and double-tap gesture handling.
  - `testMapDetailLayout_integratesSplitPaneDivider()`: Verifies that `MapDetailLayout.kt` references `SplitPaneDivider`, uses `rememberSaveable` for `splitFraction`, applies `heightIn(min = SplitPaneMath.MIN_MAP_HEIGHT)`, and conditionally renders the splitter only when `showMap && hasScrollableContent`.
  - `testMapDetailLayout_preservesRoutesAndSegmentsWithoutSplitter()`: Verifies that routes and segments omit the divider and retain full weight.

### Test Case 3: 9-Language Localization Audit (`[TST-UI-177.3]`)
* **Scope**: Localization Parity Test
* **Target Files**: `app/src/main/res/values*/strings.xml`
* **Goal**: Verify string resource `map_splitter_content_description` is defined across all 9 resource directories:
  - `values/` (EN)
  - `values-de/` (DE)
  - `values-es/` (ES)
  - `values-fr/` (FR)
  - `values-it/` (IT)
  - `values-ja/` (JA)
  - `values-nl/` (NL)
  - `values-pl/` (PL)
  - `values-pt/` (PT)
* **Expected Result**: 100% parity, zero missing entries.

### Test Case 4: Clean-Room Regression Suite (`[TST-UI-177.4]`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: 100% pass rate with zero regressions across all project modules.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-177.1]` | Unit | `SplitPaneMathTest.kt` | `REQ-UI-223` (item 2) | Specified |
| `[TST-UI-177.2]` | Contract | `SplitPaneDividerVisualContractTest.kt` | `REQ-UI-223` (item 1, 3, 4) | Specified |
| `[TST-UI-177.3]` | Localization | `values*/strings.xml` | `REQ-UI-223` (item 1), `REQ-UI-106` | Specified |
| `[TST-UI-177.4]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
