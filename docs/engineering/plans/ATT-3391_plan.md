# Stage 3: Implementation Plan - ATT-3391: Extend elevation profile slope shading and legend to use full 7-color zone palette

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

## 1. Architectural Design & SWE.2 Specification

### 1.1 Structural Decomposition & Modularity
- **Component**: `ElevationProfile.kt` (`com.atrainingtracker.trainingtracker.ui.map`).
- **Goal**: Harmonize slope gradient shading with the application's unified 7-zone palette (`TTColor.Zone1..Zone7`) established in ATT-3184 (`REQ-UI-370`).
- **Key Invariants**:
  1. `getClimbGradeColor` in `LiveClimbSheet.kt` (`REQ-UI-352`) remains untouched.
  2. Sequential half-open interval evaluation $[x_{n-1}, x_n)$ with non-finite safety (`Double.isFinite()`).
  3. Descents and flat terrain ($< 2.0\%$) strictly map to `TTColor.Zone1`.
  4. Swatch outline border for dark colors (`TTColor.Zone6`, `TTColor.Zone7`) for WCAG 2.1 Non-Text Contrast (1.4.11) with $\ge 3:1$ edge contrast.
  5. Zero persistent disk/database schema mutations.

### 1.2 Performance & Zero-Allocation Invariant
`getElevationGradeColor` performs pure primitive IEEE 754 `Double` comparisons returning value-type `Color` primitives without heap allocations, string conversions, or collection instantiations. In the Compose `Canvas` rendering loop iterating over thousands of elevation segments, execution overhead is $O(1)$ constant time ($< 1\text{ ns}$ per segment), guaranteeing zero GC pressure and maintaining fluid 60fps/120fps scrubbing.

---

## 2. Step-by-Step Implementation Roadmap (Bite-Sized Atomic Steps)

### Step 1: Implement `getElevationGradeColor` Top-Level Function (`ElevationProfile.kt`)
- **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt`
- **Location**: Top-level function in `ElevationProfile.kt`.
- **Implementation**:
  ```kotlin
  /**
   * Resolves standard 7-zone slope gradient color bands (REQ-UI-371):
   * - < 2%: Zone 1 (Chartreuse) - flat, rolling, descents, non-finite values (NaN/Inf)
   * - 2% - 5%: Zone 2 (Green) - gentle incline [2.0, 5.0)
   * - 5% - 10%: Zone 3 (Orange) - moderate climb [5.0, 10.0)
   * - 10% - 15%: Zone 4 (Red) - steep climb [10.0, 15.0)
   * - 15% - 20%: Zone 5 (Dark Violet) - very steep climb [15.0, 20.0)
   * - 20% - 25%: Zone 6 (Dark Chestnut Brown) - extreme ramp [20.0, 25.0)
   * - >= 25%: Zone 7 (Black) - monster grade / wall [25.0, +inf)
   */
  fun getElevationGradeColor(gradePercent: Double): Color {
      if (!gradePercent.isFinite()) return TTColor.Zone1
      return when {
          gradePercent < 2.0 -> TTColor.Zone1
          gradePercent < 5.0 -> TTColor.Zone2
          gradePercent < 10.0 -> TTColor.Zone3
          gradePercent < 15.0 -> TTColor.Zone4
          gradePercent < 20.0 -> TTColor.Zone5
          gradePercent < 25.0 -> TTColor.Zone6
          else -> TTColor.Zone7
      }
  }
  ```

### Step 2: Delegate Segment Color Resolution (`ElevationProfile.kt`)
- **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt`
- **Location**: Inside `remember(pathPoints, ...)` segment calculation block.
- **Change**: Replace the hardcoded 6-branch `when` block with delegation to `segmentColorProvider?.invoke(grade) ?: getElevationGradeColor(grade)`.

### Step 3: Expand `GradeLegend` to 7 Items with Outline Contrast (`ElevationProfile.kt`)
- **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt`
- **Location**: `GradeLegend` and `GradeLegendItem` composables.
- **Changes**:
  - Update `widthIn(max = 300.dp)` on legend `Surface`.
  - Add `GradeLegendItem(TTColor.Zone6, "20 - 25%")` and update the final item to `GradeLegendItem(TTColor.Zone7, "> 25%")`.
  - In `GradeLegendItem`, add `BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)` when `color == TTColor.Zone6 || color == TTColor.Zone7 || color == Color.Black` to achieve $\ge 3:1$ edge contrast against dark theme backgrounds.

### Step 4: Author Unit Tests (`ElevationProfileGradeColorTest.kt`)
- **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileGradeColorTest.kt`
- **Tests**:
  1. `testElevationGradeColor_allZonesAndExactBoundaries`:
     - Test all 7 zones and exact boundary conditions:
       - `1.99` $\to$ Zone 1, `2.00` $\to$ Zone 2
       - `4.99` $\to$ Zone 2, `5.00` $\to$ Zone 3
       - `9.99` $\to$ Zone 3, `10.00` $\to$ Zone 4
       - `14.99` $\to$ Zone 4, `15.00` $\to$ Zone 5
       - `19.99` $\to$ Zone 5, `20.00` $\to$ Zone 6
       - `24.99` $\to$ Zone 6, `25.00` $\to$ Zone 7
  2. `testElevationGradeColor_descentsAndNonFiniteValues`:
     - Negative slopes (`-50.0`, `-15.0`, `-5.0`, `-0.1`, `0.0`) map strictly to Zone 1.
     - Non-finite inputs (`Double.NaN`, `Double.POSITIVE_INFINITY`, `Double.NEGATIVE_INFINITY`) map safely to Zone 1.
  3. `testElevationProfile_contractAndLegendSevenZones`:
     - Verify contract: `ElevationProfile.kt` contains `getElevationGradeColor(grade)`.
     - Verify all 7 legend labels exist in `ElevationProfile.kt`.
     - Verify outline border logic is declared.

### Step 5: Verify Legacy Compatibility & Non-Regression
- Run `ElevationProfileClimbShadingTest.kt` as an invariant legacy regression suite to verify `REQ-UI-352` climb gradient shading remains 100% compliant.
- Run full suite: `./gradlew testDebugUnitTest`.

---

## 3. Risk Assessment & Failure Containment

| Risk | Likelihood | Impact | Mitigation Strategy |
| :--- | :--- | :--- | :--- |
| GPS noise causing NaN or Inf slope | Low | High | `!gradePercent.isFinite()` guard returns `TTColor.Zone1` immediately without throwing. |
| Inadvertent regression on climb sheet | Medium | High | `climbGradientShading == true` branch retains `getClimbGradeColor(grade)`; verified by `ElevationProfileClimbShadingTest.kt`. |
| Black swatch invisible on dark AMOLED screens | Medium | Medium | Swatch border `BorderStroke(1.dp, outlineVariant)` ensures visible silhouette ($\ge 3:1$ contrast). |
| Text truncation in legend popup | Low | Low | Expanded legend container width to `widthIn(max = 300.dp)` with `softWrap = false` on items. |
| Negative gradient ambiguity | Low | Medium | Documented explicitly: all slopes $< 2.0\%$ (including all descents) map to Zone 1 (recovery). |
| Render loop allocation overhead | Low | High | Zero heap allocations in `getElevationGradeColor`; purely primitive branch evaluations. |
