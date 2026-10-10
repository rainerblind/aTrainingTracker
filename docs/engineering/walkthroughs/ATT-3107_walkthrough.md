# ATT-3107 Walkthrough: Display Elevation Profile with Adequate Vertical Height when Map is Disabled on Tracking Screen

## Overview
- **Issue**: [ATT-3107](https://atrainingtracker.atlassian.net/browse/ATT-3107)
- **Requirement**: `REQ-UI-339`
- **Test Specification**: `TST-UI-299`
- **Sprint**: `2026-41.8`
- **Status**: Verified

## Problem Addressed
When an athlete enabled the elevation profile on the live tracking screen (`SensorGridScreen` in `ScreenMode.TRACKING`) with the map disabled (`showMap == false` and `showElevationProfile == true`), the elevation profile rendered as an unusable thin slit with virtually zero vertical height (~15–20dp), displaying only the horizontal distance axis and clipping the actual elevation curve and gradient shading.

Forensic investigation revealed:
1. The sensor grid `Column` declared no vertical weight constraint (`weight(1f, fill = false)`), allowing it to consume nearly 100% of the display height when 4–6 rows of sensor fields were configured.
2. `TrackingMapContainer` declared `Modifier.weight(1f)`, but when `showMap == false`, no weighted container was emitted, discarding the flexible weight allocation.
3. `ElevationProfile` unconditionally applied `.height(totalCanvasHeight)` on its internal canvas, preventing it from expanding even if a parent container attempted to allocate flexible vertical space.

## Architectural Changes & Solution

1. **Flexible Sensor Grid Weight Constraint (`SensorGridScreen.kt`)**:
   - Applied `Modifier.weight(1f, fill = false)` to the sensor grid `Column`.
   - When configured with few sensor rows (1–2 rows), the column wraps naturally without stretching (`fill = false`).
   - When configured with many rows (4–6 rows), the column is bounded and scrolls smoothly without crowding out bottom cockpit views.

2. **Dynamic Weight & Explicit Expansion for Elevation Profile (`SensorGridScreen.kt`)**:
   - Conditionally assigned `Modifier.weight(1f)` to the elevation profile container `Surface` when `!state.mapState.showMap`.
   - Passed `fillParentHeight = !state.mapState.showMap` and `modifier = if (!state.mapState.showMap) Modifier.fillMaxSize() else Modifier.fillMaxWidth()`.
   - Guarded rendering with `state.pathPoints.size >= 2` to prevent empty canvas stretching artifacts.

3. **Decoupled Canvas Height in `ElevationProfile.kt`**:
   - Added parameter `fillParentHeight: Boolean = false` to both `ElevationProfile` overloads.
   - For all list previews and map detail sheets, `fillParentHeight == false` preserves `.height(totalCanvasHeight)` with 100% backward compatibility.
   - For weighted tracking layout (`fillParentHeight == true`), the canvas declares `Modifier.defaultMinSize(minHeight = totalCanvasHeight).fillMaxHeight()`, dynamically expanding to $\ge 120\text{dp}$.

4. **Contract & Regression Tests**:
   - **`SensorGridScreenLayoutTest.kt`**:
     - `testSensorGrid_appliesWeightConstraintWithFillFalse` (`TST-UI-299.1`)
     - `testElevationProfile_appliesDynamicWeightWhenMapDisabled` (`TST-UI-299.2`)
     - `testElevationProfile_passesFillParentHeightAndFillMaxSizeWhenMapDisabled` (`TST-UI-299.3`)
     - `testElevationProfile_guardedByPathPointsSize` (`TST-UI-299.4`)
   - **`ElevationProfileLayoutTest.kt`**:
     - `testElevationProfile_declaresFillParentHeightParameterAndDecoupledCanvasModifier` (`TST-UI-299.5`)
     - Verified list preview callers do not enable `fillParentHeight` (`TST-UI-299.6`).

## Verification & Test Results
- **Targeted Test Suite**:
  - `SensorGridScreenLayoutTest`: 100% pass across all 4 test cases.
  - `ElevationProfileLayoutTest`: 100% pass across all 7 test cases (`BUILD SUCCESSFUL in 44s`).
- **Clean-Room Full Suite Regression**:
  - `./gradlew testDebugUnitTest` executed with 100% pass rate across all modules (zero failures, zero regressions).

## ASPICE Traceability
- **Requirement**: `REQ-UI-339` in `docs/requirements.md` (Verified)
- **Test Specification**: `TST-UI-299` in `docs/tests.md` (Verified)
- **Implementation Plan**: `docs/engineering/plans/ATT-3107_plan.md`
- **Analysis**: `docs/engineering/analysis/ATT-3107_analysis.md`
