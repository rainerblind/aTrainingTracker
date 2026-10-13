# Stage 3: Implementation Plan - ATT-3316: Scale LiveClimbSheet elevation profile height adaptively based on climb elevation gain

**Ticket**: [ATT-3316](https://rainerblind.atlassian.net/browse/ATT-3316)  
**Sub-task**: [ATT-3394](https://rainerblind.atlassian.net/browse/ATT-3394) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2582](https://rainerblind.atlassian.net/browse/ATT-2582) (*Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-352` (Clause 1 refinement)  
**Test Mapping**: `TST-UI-312` (Clause 3 refinement)  
**Branch**: `feature/ATT-3316`  
**Author**: AI Agent 1 (Software Construction Architect)  
**Date**: 2026-10-13  

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - `REQ-UI-352` (*LiveClimbSheet Design System Harmonization, Shared ElevationProfile Integration & Degenerate Geometry Safeguards*, `ATT-3264`), targeting `LiveClimbSheet.kt`, `ElevationProfile.kt`, and `LiveClimbSheetLayoutTest.kt`.
2. **Historical Origin & Commit Trace**:
   - Sprint 2026-40.14 (`ATT-1281`) introduced `ClimbProfileCanvas` with a fixed height (`68.dp`).
   - Sprint 2026-41.10 (`ATT-3264`, commit `04ad935c`) refactored `LiveClimbSheet` to reuse `ElevationProfile.kt` and retained `fixedHeight = 68.dp` for layout parity.
3. **Root Reason for Existing Formulation**:
   - `fixedHeight = 68.dp` was retained to prevent visual changes to bottom sheet height during the refactoring.
   - However, during Sprint 2026-41.10 Joint Review, the athlete mandated:
     *"The height of the elevation profile should also depend on the height of the climb. No exceptions here."* (Documented in `Sprint_Review_and_Retro_ATT-3318.md` under Topic 3 & Rule 36, and codified in `docs/design_guidelines.md` §2.3).
   - Constraining all climbs to 68 dp squashed gradient and altitude perception on significant climbs (500–1200 m gain), making them look identical to minor 20 m rollers.
4. **Preservation of Core Invariants**:
   - Canonical Adaptive Height: Utilize `calculateElevationProfileHeight(range)` ($70\text{dp} \dots 200\text{dp}$) from `ElevationProfile.kt` per `REQ-UI-346`.
   - Gradient Shading: Preserve `climbGradientShading = true` and `getClimbGradeColor` color mapping per `REQ-UI-352`.
   - Telemetry HUD & Badges: Preserve 32dp terrain icon, titleLarge climb name, labelLarge route counter, headlineMedium monospace grade, bold remaining distance/elevation, and `MetricBadge` delegation per `REQ-UI-328` and `REQ-UI-352`.
   - Multi-State Tooling: Preserve all Compose `@Preview` composables (`LiveClimbSheet_OnClimb_Preview`, `LiveClimbSheet_OnClimb_Dark_Preview`, `LiveClimbSheet_Approaching_Preview`, `LiveClimbSheet_Finished_Preview`) per `REQ-UI-341`.
   - Clean-Room Test Pass Rate: 100% test pass rate across all unit tests and layout contracts.

---

## 1. Architectural Decomposition (SWE.2)

### 1.1 Architecture & Component Boundaries
- **UI Layer (`com.atrainingtracker.trainingtracker.ui.climbs`)**:
  - `LiveClimbSheet.kt`: Removes `fixedHeight = 68.dp` when composing `ElevationProfile`. Passes `pathPoints = normalizedPoints`, `currentDistance = currentDistance`, `showZoomControls = false`, `climbGradientShading = true`, and `modifier = Modifier.fillMaxWidth()`.
- **Core Map / Profile Engine (`com.atrainingtracker.trainingtracker.ui.map`)**:
  - `ElevationProfile.kt`: Retains optional parameter `fixedHeight: Dp? = null`. When `fixedHeight == null`, `totalCanvasHeight` automatically evaluates to `cachedData.adaptiveHeight`, computed via `calculateElevationProfileHeight(range)` from the vertical range of the climb's path points.
- **Scaffold Container (`com.atrainingtracker.trainingtracker.ui.tracking.tracking`)**:
  - `SensorGridScreen.kt`: `BottomSheetScaffold` already wraps sheet content with `wrapContentHeight()`, dynamically expanding to accommodate adaptive climb profile heights ($70\text{dp} \dots 200\text{dp}$) without layout clipping.

### 1.2 Threading & Dispatcher Model
- All changes are purely declarative Jetpack Compose UI layout updates and mathematical evaluations running on the main UI thread. No asynchronous dispatchers, background workers, or SQLite operations are required.

---

## 2. UI Consistency (Rule 23)

- **Design Guidelines Reference**:
  - `docs/design_guidelines.md` §2.3 (*Elevation Profile Sizing & Adaptive Height Invariant*):
    - *Formula*: $\text{height} = 70 + (\text{range} / 1000)^{0.6} \times (200 - 70)$
    - *Minimum Height*: $70\text{dp}$ ($\Delta H \approx 0\text{ m}$)
    - *Maximum Height*: $200\text{dp}$ ($\Delta H \ge 1000\text{ m}$)
    - *Prohibition*: "Hardcoding arbitrary compact fixed heights (e.g. `68.dp`) in bottom sheets, cards, or dialogs is strictly prohibited (ATT-3264 / ATT-3316 / Retro 2026-41.10)."
- **Reused Components**:
  - `ElevationProfile.kt` (`ui/map/`)
  - `MetricBadge.kt` (`ui/components/core/`)
- **Theme Tokens**:
  - Elevation bounds: `calculateElevationBounds` with minimum 1.0 m span protection.
  - Colors: Standard slope grade bands via `getClimbGradeColor(grade)`.
  - Spacing: Standard `Modifier.padding(horizontal = 16.dp, vertical = 8.dp)` wrapping column.
- **Justification for Style**:
  - Direct compliance with canonical adaptive height scaling mandated by Rule 36 and Design Guidelines §2.3. Zero bespoke styles introduced.

---

## 3. Atomic Step Sequencing

### Step 1: Remove Hardcoded Fixed Height from `LiveClimbSheet.kt`
- **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/climbs/LiveClimbSheet.kt`
- **Action**:
  Remove `fixedHeight = 68.dp,` from `ElevationProfile` invocation:
  ```kotlin
  ElevationProfile(
      pathPoints = normalizedPoints,
      currentDistance = currentDistance,
      showZoomControls = false,
      climbGradientShading = true,
      modifier = Modifier.fillMaxWidth()
  )
  ```
- **Validation**: Verify file compiles cleanly without syntax errors.

### Step 2: Update `LiveClimbSheetLayoutTest.kt` Contract Test
- **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/climbs/LiveClimbSheetLayoutTest.kt`
- **Action**:
  Update `testLiveClimbSheet_elevationProfileConfiguredWithRefinedHeightAndGradientShading()`:
  - Assert that `LiveClimbSheet.kt` does NOT contain `"fixedHeight"` (verifying no hardcoded height override exists per Rule 36).
  - Assert that `LiveClimbSheet.kt` configures `ElevationProfile` with `climbGradientShading = true`.
  - Assert that `LiveClimbSheet.kt` delegates chips and badges to `MetricBadge`.
- **Validation**: Run `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.climbs.LiveClimbSheetLayoutTest`.

### Step 3: Implement `LiveClimbAdaptiveHeightTest.kt`
- **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/climbs/LiveClimbAdaptiveHeightTest.kt`
- **Action**:
  Create dedicated unit tests verifying:
  1. `calculateElevationProfileHeight(range)` outputs expected canonical Dp heights across climb categories:
     - 0 m -> 70 dp
     - 20 m -> 77 dp
     - 60 m -> 93 dp (Cat 4)
     - 150 m -> 112 dp (Cat 3)
     - 300 m -> 133 dp (Cat 2)
     - 600 m -> 166 dp (Cat 1)
     - 1000 m -> 200 dp (HC)
     - 1500 m -> 200 dp (HC ceiling)
  2. Contract check: `LiveClimbSheet.kt` contains `ElevationProfile(` and does not specify `fixedHeight`.
- **Validation**: Run `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.climbs.LiveClimbAdaptiveHeightTest`.

### Step 4: Clean-Room Full Suite Regression Execution
- **Command**: `./gradlew testDebugUnitTest`
- **Criteria**: 100% test pass rate across all 2,521+ tests with 0 failures.

---

## 4. Invariants & Risk Assessment

1. **Rule 36 Non-Negotiable Invariant**:
   - Profile heights must scale dynamically based on vertical elevation span $\Delta H = H_{\max} - H_{\min}$ using canonical domain formulas (`calculateElevationProfileHeight(range)`).
2. **Backward Compatibility**:
   - `ElevationProfile.kt` maintains `fixedHeight: Dp? = null` parameter so other potential callers requiring fixed constraints remain unaffected.
3. **Defensive Layout Bounds**:
   - `calculateElevationBounds` enforces minimum 1.0 m span protection, preventing division-by-zero or `NaN` coordinate projections on flat climbs.
4. **Clean-Room Test Pass Rate**:
   - 100% pass rate across the full test suite.
