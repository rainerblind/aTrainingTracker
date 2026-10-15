# Stage 2: Requirement & Test Specification - ATT-3391: Extend elevation profile slope shading and legend to use full 7-color zone palette

**Ticket**: [ATT-3391](https://rainerblind.atlassian.net/browse/ATT-3391)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*[Epic] Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.4`  
**Requirement Mapping**: `REQ-UI-371` (*Elevation Profile 7-Zone Gradient Shading and Harmonized Legend*)  
**Test Mapping**: `TST-UI-334` (*Elevation Profile 7-Zone Gradient Shading and Legend Verification*)  
**Branch**: `feature/ATT-3391`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. System Requirements Specification (SWE.1)

### REQ-UI-371: Elevation Profile 7-Zone Gradient Shading and Harmonized Legend
The system SHALL harmonize elevation profile slope gradient shading and the interactive `GradeLegend` with the application's unified 7-zone design system (`TTColor.Zone1..Zone7`) established in `REQ-UI-370` (ATT-3184):

1. **7-Zone Slope Gradient Scale & Exact Half-Open Intervals (`REQ-UI-371.1`)**:
   Terrain incline SHALL be categorized into 7 discrete slope gradient intervals following strict half-open interval logic $[x_{n-1}, x_n)$ mapped deterministically to `TTColor`:
   - **Zone 1 (`TTColor.Zone1` Chartreuse `#7FFF00`)**: $\text{Grade} \in [-\infty, 2.0\%)$ (Flat, rolling terrain, descents, and non-finite values).
   - **Zone 2 (`TTColor.Zone2` Green `#00E676`)**: $\text{Grade} \in [2.0\%, 5.0\%)$ (Gentle incline).
   - **Zone 3 (`TTColor.Zone3` Orange `#FFA500`)**: $\text{Grade} \in [5.0\%, 10.0\%)$ (Moderate climb).
   - **Zone 4 (`TTColor.Zone4` Red `#FF0000`)**: $\text{Grade} \in [10.0\%, 15.0\%)$ (Steep climb).
   - **Zone 5 (`TTColor.Zone5` Dark Violet `#8A2BE2`)**: $\text{Grade} \in [15.0\%, 20.0\%)$ (Very steep climb).
   - **Zone 6 (`TTColor.Zone6` Dark Chestnut Brown `#4E342E`)**: $\text{Grade} \in [20.0\%, 25.0\%)$ (Extreme ramp; $20.0\%$ inclusive, $25.0\%$ exclusive).
   - **Zone 7 (`TTColor.Zone7` Black `#000000`)**: $\text{Grade} \in [25.0\%, +\infty)$ (Monster grade / Wall; $25.0\%$ inclusive).

2. **Deterministic Top-Level Utility Function & Non-Finite Safeguards (`REQ-UI-371.2`)**:
   `ElevationProfile.kt` SHALL expose a public top-level utility function `getElevationGradeColor(gradePercent: Double): Color` executing monotonic half-open interval evaluation $[x_{n-1}, x_n)$.
   - If `gradePercent.isNaN()` or `!gradePercent.isFinite()`, the function SHALL safely return `TTColor.Zone1`, strictly preventing UI crashes, unhandled exceptions, or undefined rendering on GPS telemetry drops.

3. **Explicit Descent & Negative Slope Handling (`REQ-UI-371.3`)**:
   All downhill slopes and descents ($\text{Grade} < 0.0\%$) and flat terrain ($0.0\% \le \text{Grade} < 2.0\%$) SHALL resolve to `TTColor.Zone1`. Negative gradients represent gravitational assistance with zero upward climbing resistance and are intentionally assigned the baseline recovery zone color.

4. **GradeLegend Harmonization (`REQ-UI-371.4`)**:
   The interactive `GradeLegend` popup in `ElevationProfile.kt` SHALL display exactly 7 legend items in sequence:
   - `< 2%` with swatch `TTColor.Zone1`
   - `2 - 5%` with swatch `TTColor.Zone2`
   - `5 - 10%` with swatch `TTColor.Zone3`
   - `10 - 15%` with swatch `TTColor.Zone4`
   - `15 - 20%` with swatch `TTColor.Zone5`
   - `20 - 25%` with swatch `TTColor.Zone6`
   - `> 25%` with swatch `TTColor.Zone7`
   Legend width constraint SHALL be configured to `widthIn(max = 300.dp)` to prevent text truncation on wrapped multi-row layouts.

5. **Accessibility & Contrast Compliance (`REQ-UI-371.5`)**:
   - Swatch labels in `GradeLegendItem` SHALL be rendered adjacent to swatches using `MaterialTheme.colorScheme.onSurface` on `MaterialTheme.colorScheme.surface`, providing a contrast ratio $\ge 12:1$ in both light and dark themes (complying with WCAG 2.1 AA $\ge 4.5:1$).
   - For `TTColor.Zone6` (`#4E342E`) and `TTColor.Zone7` (`#000000`), `GradeLegendItem` SHALL render a border outline `BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)` around the swatch to maintain visible boundaries against dark surfaces with edge contrast $\ge 3:1$ (WCAG 2.1 Non-Text Contrast 1.4.11).

6. **Historical Data Strategy (`REQ-UI-371.6`)**:
   Elevation profiles in `aTrainingTracker` are rendered purely on-the-fly from raw GPS sample coordinates in `WorkoutSamples`. Re-rendering historical workouts with the unified 7-zone scale is an intentional visual enhancement: athletes viewing historical rides immediately benefit from the richer differentiation between 20-25% ramps and >25% walls without requiring any database migrations or data mutations.

7. **Preservation of Existing Invariants (`REQ-UI-371.7`)**:
   `getClimbGradeColor` in `LiveClimbSheet.kt` (`REQ-UI-352`) and its specific 6-band cycling climb classification ($<3\%, 3-6\%, 6-9\%, 9-12\%, 12-20\%, \ge 20\%$) SHALL remain unaltered, preserving full backward compatibility with `ElevationProfileClimbShadingTest.kt`.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: Amends `REQ-UI-352` (*Real-Time Live Climb Sheet & Slope Shading*, Sprint 2026-42.1, ATT-3343) and complements `REQ-UI-370` (*7-Zone Coggan Cycling Power Model & Palette*, Sprint 2026-42.4, ATT-3184). Targets `ElevationProfile.kt`.
2. **Historical Origin & Commit Trace**: Sprint 2026-42.1 (`ATT-3343`, commit `fca58372`) introduced climb slope shading with 6 bands, while default elevation profile slope shading in `ElevationProfile.kt` was restricted to 6 bands due to legacy 5-zone palette limitations.
3. **Root Reason for Existing Formulation**: Prior to ATT-3184, only 5 training zone colors and generic `Color.Black` existed in the design system, forcing all slopes $\ge 20\%$ to be lumped into Black without distinction for extreme ramps.
4. **Preservation of Core Invariants & Safety of Modification**: Live climb gradient shading (`REQ-UI-352`) remains untouched; dividing $[20\%, \infty)$ into $[20\%, 25\%)$ (`TTColor.Zone6`) and $[25\%, \infty)$ (`TTColor.Zone7`) enhances visual clarity on steep terrain while preserving 100% mathematical determinism, zero SQLite schema mutations, and 100% clean-room test pass rate.

---

## 3. Acceptance Criteria (Given-When-Then)

- **Scenario 1 (Gentle & Moderate Incline)**:
  - *Given* an elevation segment with grade $3.5\%$
  - *When* resolving color via `getElevationGradeColor`
  - *Then* the resolved color is `TTColor.Zone2`.
- **Scenario 2 (Steep Ramp 20% - 25% in Zone 6)**:
  - *Given* an elevation segment with grade $22.4\%$
  - *When* resolving color via `getElevationGradeColor`
  - *Then* the resolved color is `TTColor.Zone6` (Dark Chestnut Brown `#4E342E`).
- **Scenario 3 (Monster Grade Wall >= 25% in Zone 7)**:
  - *Given* an elevation segment with grade $26.8\%$
  - *When* resolving color via `getElevationGradeColor`
  - *Then* the resolved color is `TTColor.Zone7` (Black `#000000`).
- **Scenario 4 (Descent and Negative Grade Handling)**:
  - *Given* a descent segment with grade $-8.5\%$
  - *When* resolving color via `getElevationGradeColor`
  - *Then* the resolved color is `TTColor.Zone1` (Chartreuse `#7FFF00`).
- **Scenario 5 (Exact Boundary Conditions)**:
  - *Given* exact boundary values `2.0%`, `5.0%`, `10.0%`, `15.0%`, `20.0%`, and `25.0%`
  - *When* evaluating `getElevationGradeColor`
  - *Then* `2.0%` maps to Zone 2, `5.0%` maps to Zone 3, `10.0%` maps to Zone 4, `15.0%` maps to Zone 5, `20.0%` maps to Zone 6, and `25.0%` maps to Zone 7.
- **Scenario 6 (Non-Finite & Degenerate Inputs)**:
  - *Given* input values `Double.NaN`, `Double.POSITIVE_INFINITY`, or `Double.NEGATIVE_INFINITY`
  - *When* evaluating `getElevationGradeColor`
  - *Then* the function safely returns `TTColor.Zone1` without throwing exceptions.
- **Scenario 7 (GradeLegend Rendering & Accessibility)**:
  - *Given* an athlete opens the `GradeLegend` popup in dark mode
  - *When* inspecting the legend content
  - *Then* exactly 7 items are displayed with swatches and non-truncated labels, and Zone 6 / Zone 7 swatches have outline borders.

---

## 4. Test Specification (SWE.4 / SWE.5)

### TST-UI-334: Elevation Profile 7-Zone Gradient Shading and Legend Verification

1. **Unit Test Suite (`ElevationProfileGradeColorTest.kt`)**:
   - `testElevationGradeColor_allZonesAndExactBoundaries`:
     - Zone 1: `-15.0`, `-5.0`, `0.0`, `1.0`, `1.99`, `1.999999` $\to$ `TTColor.Zone1`.
     - Zone 2: `2.0`, `3.5`, `4.99`, `4.999999` $\to$ `TTColor.Zone2`.
     - Zone 3: `5.0`, `7.5`, `9.99`, `9.999999` $\to$ `TTColor.Zone3`.
     - Zone 4: `10.0`, `12.5`, `14.99`, `14.999999` $\to$ `TTColor.Zone4`.
     - Zone 5: `15.0`, `17.5`, `19.99`, `19.999999` $\to$ `TTColor.Zone5`.
     - Zone 6: `20.0`, `22.5`, `24.99`, `24.999999` $\to$ `TTColor.Zone6`.
     - Zone 7: `25.0`, `30.0`, `45.0` $\to$ `TTColor.Zone7`.
   - `testElevationGradeColor_nonFiniteAndDegenerateValues`:
     - Verify `Double.NaN` returns `TTColor.Zone1`.
     - Verify `Double.POSITIVE_INFINITY` returns `TTColor.Zone1`.
     - Verify `Double.NEGATIVE_INFINITY` returns `TTColor.Zone1`.
     - Verify extreme positive grades (e.g. `100.0%`) map to Zone 7.
     - Verify extreme negative grades (e.g. `-50.0%`) map to Zone 1.

2. **ElevationProfile Contract & Legend Test (`ElevationProfileGradeColorTest.kt`)**:
   - Verify `ElevationProfile.kt` delegates to `getElevationGradeColor(grade)` when `climbGradientShading == false`.
   - Verify `GradeLegend` contains all 7 labels (`< 2%`, `2 - 5%`, `5 - 10%`, `10 - 15%`, `15 - 20%`, `20 - 25%`, `> 25%`).
   - Verify `GradeLegendItem` specifies `BorderStroke` for outline contrast on dark swatches.

3. **Regression Test Pass (`ElevationProfileClimbShadingTest.kt`)**:
   - Verify that `ElevationProfileClimbShadingTest.kt` passes with 100% assertions satisfied, guaranteeing zero regressions on `REQ-UI-352`.

4. **Clean-Room Full Suite Regression Execution**:
   - Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.
