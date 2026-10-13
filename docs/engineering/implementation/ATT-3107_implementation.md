# Stage 4 Implementation Report: ATT-3107 - Display elevation profile with adequate vertical height when map is disabled on tracking screen

**Ticket**: [ATT-3107](https://atrainingtracker.atlassian.net/browse/ATT-3107)  
**Sub-task**: [ATT-3151](https://atrainingtracker.atlassian.net/browse/ATT-3151) (`[Implementation]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.8`  
**Branch**: `feature/ATT-3107`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Implementation Summary

Stage 4 (Software Construction & Implementation) for [ATT-3107](https://atrainingtracker.atlassian.net/browse/ATT-3107) has been completed in strict accordance with the approved Stage 3 Implementation Plan and `REQ-UI-339`.

### 1.1 Key Modifications

1. **`ElevationProfile.kt` - Decoupled Flexible Canvas Height**:
   - Added parameter `fillParentHeight: Boolean = false` to both `ElevationProfile` composables.
   - Preserved default `fillParentHeight = false` across all existing list previews (`WorkoutSummary.kt`, `RouteItem.kt`, `SegmentItem.kt`) and map detail sheets, maintaining `.height(totalCanvasHeight)` with 100% backward compatibility.
   - When `fillParentHeight == true`, the canvas applies `Modifier.fillMaxWidth().defaultMinSize(minHeight = totalCanvasHeight).fillMaxHeight()`, dynamically expanding to fill the available weighted space ($\ge 120\text{dp}$).

2. **`SensorGridScreen.kt` - Dynamic Layout Weighting**:
   - Added `Modifier.weight(1f, fill = false)` to the sensor grid `Column`. This ensures the sensor grid wraps its natural height when small (1–2 rows), but prevents it from crowding out bottom cockpit views when many rows (4–6) are configured.
   - When `!state.mapState.showMap` and `state.showElevationProfile == true`, conditionally assigned `Modifier.weight(1f)` to the elevation profile container `Surface`.
   - Forwarded `fillParentHeight = !state.mapState.showMap` and `modifier = if (!state.mapState.showMap) Modifier.fillMaxSize() else Modifier.fillMaxWidth()` to `ElevationProfile`.
   - Guarded elevation profile rendering with `state.pathPoints.size >= 2` to prevent empty/sparse canvas stretching artifacts.

3. **`SensorGridScreenLayoutTest.kt` - Contract Tests**:
   - Added contract tests verifying:
     - `testSensorGrid_appliesWeightConstraintWithFillFalse` (`TST-UI-299.1`)
     - `testElevationProfile_appliesDynamicWeightWhenMapDisabled` (`TST-UI-299.2`)
     - `testElevationProfile_passesFillParentHeightAndFillMaxSizeWhenMapDisabled` (`TST-UI-299.3`)
     - `testElevationProfile_guardedByPathPointsSize` (`TST-UI-299.4`)

4. **`ElevationProfileLayoutTest.kt` - Decoupled Modifier Tests**:
   - Added `testElevationProfile_declaresFillParentHeightParameterAndDecoupledCanvasModifier` (`TST-UI-299.5`).
   - Verified both overloads declare `fillParentHeight: Boolean = false`.
   - Verified conditional attachment of `.height(totalCanvasHeight)` vs `.defaultMinSize(minHeight = totalCanvasHeight).fillMaxHeight()`.

---

## 2. Test Execution

* Executed targeted tests via `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.ElevationProfileLayoutTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenLayoutTest"`:
  ```
  BUILD SUCCESSFUL in 44s
  32 actionable tasks: 12 executed, 20 up-to-date
  ```
  All tests passed cleanly with 0 failures and 0 errors.

---

## 3. Invariants & Governance Verification

- Minimum vertical height $\ge 120\text{dp}$ guaranteed when map is disabled.
- List previews and map detail layout retain their exact natural heights without regressions.
- Sensor grid scrollability preserved.
- Ambient `NavigationHintsOverlay` positioning when `showMap == false` preserved.
