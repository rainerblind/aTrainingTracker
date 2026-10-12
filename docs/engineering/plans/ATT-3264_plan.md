# Stage 3: Implementation Plan - ATT-3264: Refactor LiveClimbSheet to reuse LiveSegments layout and shared ElevationProfile

**Ticket**: [ATT-3264](https://atrainingtracker.atlassian.net/browse/ATT-3264)  
**Sub-task**: [ATT-3275](https://atrainingtracker.atlassian.net/browse/ATT-3275) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2582](https://atrainingtracker.atlassian.net/browse/ATT-2582) (*[Epic] Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.10`  
**Requirement Mapping**: `REQ-UI-352` (*LiveClimbSheet Design System Harmonization, Shared ElevationProfile Integration & Degenerate Geometry Safeguards*)  
**Test Mapping**: `TST-UI-312`  
**Branch**: `feature/ATT-3264`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Problem Description & Background

During initial ClimbPro development under Sprint 2026-40.14 (`ATT-1281`), `LiveClimbSheet.kt` was constructed rapidly to provide in-ride telemetry and visual gradient feedback for ascending route climbs. Several components were implemented in an ad-hoc manner rather than reusing the established design system:
1. `LiveClimbSheet.kt` contains a bespoke 170-line private composable `ClimbProfileCanvas` duplicating bounds calculation, coordinate projection, path building, and rider marker interpolation already maintained in the canonical `ElevationProfile.kt`.
2. `LiveClimbSheet.kt` introduces standalone, bespoke `ClimbCategoryChip` and `ClimbStatusBadge` composables wrapping raw `Surface` with hardcoded corner radii, duplicating `MetricBadge.kt`.
3. The two-row header and telemetry HUD layout duplicate the structure, typography, and visual hierarchy of `SegmentHeader.kt` and `SegmentLiveDetails.kt`.

This implementation plan refactors `LiveClimbSheet.kt` to harmonize with the design system, extends `ElevationProfile.kt` to support slope gradient color shading and compact fixed heights, extends `MetricBadge.kt` with customizable shapes, establishes mathematical safeguards against degenerate geometries, and removes the redundant `ClimbProfileCanvas`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-352` (*LiveClimbSheet Design System Harmonization, Shared ElevationProfile Integration & Degenerate Geometry Safeguards*, amending `REQ-UI-328` and `REQ-UI-341`, complementing `REQ-UI-015`)
* **Test Mapping**: `TST-UI-312` (*LiveClimbSheet Design System Harmonization, ElevationProfile Gradient Shading & Degenerate Geometry Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Exact Mathematical Parity**: Distance to summit, remaining elevation gain, instantaneous gradient percentage, and rider marker progress calculations remain 100% mathematically identical.
2. **Design System & Token Hierarchy**: Header typography (`titleLarge` bold), route climb counter (`labelLarge` bold primary), instantaneous gradient (`headlineMedium` monospace bold), and 0.5dp divider (`outlineVariant.copy(alpha = 0.5f)`) are strictly preserved.
3. **9-Language Localization Parity**: 100% translation parity across EN, DE, ES, FR, IT, JA, NL, PL, PT is strictly preserved.
4. **Degenerate Geometry Safety**: Zero exceptions when `pathPoints` is empty, contains 1 point, or has zero vertical altitude span (`altSpan == 0.0`). Vertical range is coerced to $\ge 1.0$, preventing division-by-zero or `NaN` coordinate projections.
5. **Multi-State Compose `@Preview` Tooling**: `LiveClimbSheet_OnClimb_Preview`, `LiveClimbSheet_OnClimb_Dark_Preview`, `LiveClimbSheet_Approaching_Preview`, and `LiveClimbSheet_Finished_Preview` remain functional.
6. **Caller Backward Compatibility**: Existing callers of `ElevationProfile.kt`, `MetricBadge.kt`, `SegmentHeader.kt`, and `SegmentLiveDetails.kt` compile and behave identically.

---

## 4. Proposed Architectural Changes

### Component 1: `MetricBadge.kt` (`com.atrainingtracker.trainingtracker.ui.components`)
* Add optional parameter `shape: Shape = RoundedCornerShape(4.dp)` to `MetricBadge`.
* Retain default `RoundedCornerShape(4.dp)`, ensuring 100% backward compatibility for all existing callers while allowing callers like `ClimbCategoryChip` (`6.dp`) and `ClimbStatusBadge` (`12.dp`) to supply custom corner radii.

### Component 2: `ElevationProfile.kt` (`com.atrainingtracker.trainingtracker.ui.map`)
* Add optional parameters:
  * `climbGradientShading: Boolean = false`
  * `segmentColorProvider: ((Double) -> Color)? = null`
  * `fixedHeight: Dp? = null`
* In `calculateElevationBounds`, enforce `range = (fallbackMax - fallbackMin).coerceAtLeast(1.0)` and for normal bounds enforce `range = (max - min).coerceAtLeast(1.0)` to eliminate division-by-zero.
* In `ElevationProfile`, update degenerate guard to:
  ```kotlin
  if (pathPoints.size < 2) return
  ```
* In `cachedData`, when segment colors are evaluated:
  ```kotlin
  val color = if (climbGradientShading) {
      segmentColorProvider?.invoke(grade) ?: getClimbGradeColor(grade)
  } else {
      when {
          grade < 2.0 -> TTColor.Zone1
          grade < 5.0 -> TTColor.Zone2
          grade < 10.0 -> TTColor.Zone3
          grade < 15.0 -> TTColor.Zone4
          grade < 20.0 -> TTColor.Zone5
          else -> Color.Black
      }
  }
  ```
* In `totalCanvasHeight` calculation:
  ```kotlin
  val totalCanvasHeight = fixedHeight ?: if (showZoomControls) cachedData.adaptiveHeight + 28.dp else cachedData.adaptiveHeight
  ```

### Component 3: Shared Climb Grade Colors (`com.atrainingtracker.trainingtracker.ui.climbs`)
* Expose public top-level function `getClimbGradeColor(gradePercent: Double): Color`:
  * `<3%` -> `Color(0xFF4CAF50)` (Green)
  * `3-6%` -> `Color(0xFF8BC34A)` (Light Green)
  * `6-9%` -> `Color(0xFFFFC107)` (Amber)
  * `9-12%` -> `Color(0xFFFF9800)` (Orange)
  * `12-20%` -> `Color(0xFFE53935)` (Red)
  * `>=20%` -> `Color(0xFF212121)` (Dark)

### Component 4: `LiveClimbSheet.kt` (`com.atrainingtracker.trainingtracker.ui.climbs`)
* Refactor `ClimbCategoryChip` to delegate to `MetricBadge`:
  ```kotlin
  @Composable
  fun ClimbCategoryChip(category: ClimbCategory, modifier: Modifier = Modifier) {
      val (bgColor, textColor, labelRes) = getClimbCategoryColors(category)
      MetricBadge(
          text = stringResource(labelRes),
          containerColor = bgColor,
          contentColor = textColor,
          shape = RoundedCornerShape(6.dp),
          modifier = modifier
      )
  }
  ```
* Refactor `ClimbStatusBadge` to delegate to `MetricBadge`:
  ```kotlin
  MetricBadge(
      text = label,
      containerColor = containerColor,
      contentColor = contentColor,
      shape = RoundedCornerShape(12.dp),
      modifier = modifier
  )
  ```
* Replace bespoke `ClimbProfileCanvas` with canonical `ElevationProfile`:
  ```kotlin
  val normalizedPoints = climb.normalizedPathPoints.ifEmpty { climb.pathPoints }
  val currentDistance = if (liveClimb.status == LiveClimbStatus.ON_CLIMB || liveClimb.status == LiveClimbStatus.FINISHED) {
      (liveClimb.currentProgressFraction.coerceIn(0f, 1f) * climb.distanceMeters).toDouble()
  } else null

  ElevationProfile(
      pathPoints = normalizedPoints,
      currentDistance = currentDistance,
      showZoomControls = false,
      climbGradientShading = true,
      fixedHeight = 68.dp,
      modifier = Modifier.fillMaxWidth()
  )
  ```
* Delete private `ClimbProfileCanvas` composable completely.

### UI Consistency (Rule 23)
* **Closest Reference Screen**: `LIveSegmentSheet.kt` (`ui/segments/`) and `SegmentHeader.kt` / `SegmentLiveDetails.kt`.
* **Reused Components**: `MetricBadge.kt` (`ui/components/MetricBadge.kt`) and canonical `ElevationProfile.kt` (`ui/map/ElevationProfile.kt`).
* **Theme Tokens**:
  * Shapes: `RoundedCornerShape(6.dp)` for category chips, `RoundedCornerShape(12.dp)` for status badge pills per Design Guidelines §5.3.
  * Spacing: `horizontal = 16.dp, vertical = 8.dp` container padding; `4.dp` vertical column item spacing per Design Guidelines §5.2.
  * Colors: `MaterialTheme.colorScheme.surface`, `primary`, `onSurfaceVariant`, `outlineVariant` (0.5f alpha divider).
  * Typography: `titleLarge` bold for climb title, `labelLarge` bold primary for counter, `headlineMedium` monospace bold for gradient per Design Guidelines §5.5.
* **Justification for Non-standard Styles**: None. This refactoring eliminates historical non-standard bespoke styles.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `MetricBadge.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/MetricBadge.kt`
* Changes: Add `shape: Shape = RoundedCornerShape(4.dp)` parameter to `MetricBadge` composable.

### Step 2: Extract `getClimbGradeColor` & Extend `ElevationProfile.kt`
* Files:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/climbs/LiveClimbSheet.kt` (or new shared climb utils)
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt`
* Changes:
  * Expose `getClimbGradeColor(gradePercent: Double): Color` as public top-level function.
  * Add `climbGradientShading: Boolean = false`, `segmentColorProvider: ((Double) -> Color)? = null`, `fixedHeight: Dp? = null` to `ElevationProfile`.
  * Guard `if (pathPoints.size < 2) return` in `ElevationProfile`.
  * Ensure `range = (max - min).coerceAtLeast(1.0)` in bounds calculations.
  * Apply `getClimbGradeColor` when `climbGradientShading == true`.
  * Apply `fixedHeight` override to `totalCanvasHeight`.

### Step 3: Refactor `LiveClimbSheet.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/climbs/LiveClimbSheet.kt`
* Changes:
  * Refactor `ClimbCategoryChip` and `ClimbStatusBadge` to delegate to `MetricBadge`.
  * Replace `ClimbProfileCanvas` call with `ElevationProfile` call.
  * Delete `ClimbProfileCanvas` composable.

### Step 4: Add New Unit Tests
* Files:
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/MetricBadgeShapeTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/climbs/ElevationProfileClimbShadingTest.kt`
* Changes:
  * Test `MetricBadge` custom shape and default shape.
  * Test `getClimbGradeColor` color bands.
  * Test `ElevationProfile` with degenerate inputs (`pathPoints.isEmpty()`, `pathPoints.size == 1`, flat climb).

### Step 5: Update `LiveClimbSheetLayoutTest.kt`
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/climbs/LiveClimbSheetLayoutTest.kt`
* Changes:
  * Update tests asserting `ElevationProfile` is called instead of `ClimbProfileCanvas`.
  * Assert `ClimbProfileCanvas` is absent.
  * Assert `MetricBadge` delegation.
  * Verify all preview composables remain present.

### Step 6: Execute Targeted Unit Tests
* Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.climbs.*" --tests "com.atrainingtracker.trainingtracker.ui.components.MetricBadge*" --tests "com.atrainingtracker.trainingtracker.ui.map.ElevationProfile*"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Execute targeted unit tests during Stage 4. Execute full clean-room test suite (`./gradlew testDebugUnitTest`) in Stage 5 verifying 100% pass rate.
* **Rollback**: Isolated branch `feature/ATT-3264` ensures clean git revert without impact on `sprint/2026-41.10`.
