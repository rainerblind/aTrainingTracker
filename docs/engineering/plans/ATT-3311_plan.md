# Stage 3: Implementation Plan - ATT-3311: [Bug] Restore visibility of sole elevation profile on tracking screen without map

**Ticket**: [ATT-3311](https://atrainingtracker.atlassian.net/browse/ATT-3311)  
**Sub-task**: [ATT-3592](https://atrainingtracker.atlassian.net/browse/ATT-3592) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `v1.0.0-rc1`  
**Active Sprint**: `2026-42.4`  
**Requirement Mapping**: `REQ-UI-369` (*Cockpit Layout Inset Padding & Elevation Profile Bottom Sheet Clearance*)  
**Test Mapping**: `TST-UI-332` (*Cockpit Layout Bottom Inset Padding & Elevation Profile Bottom Sheet Clearance Verification*)  
**Branch**: `bugfix/ATT-3311`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Problem Description & Background

During Sprint Review `2026-41.10` on Google Pixel 10 (`ATT-3173`), the reviewer reported that the sole elevation profile on tracking tabs without a map was no longer shown at all. Forensic analysis identified that:
1. `BoxWithConstraints` in `SensorGridScreen.kt` historically omitted `paddingValues.calculateBottomPadding()` to allow the Google Map to draw under the bottom sheet.
2. In `ATT-3173`, `Modifier.weight(1f)` was removed from the elevation profile to restore its canonical adaptive height ($70\text{dp} \dots 200\text{dp}$).
3. When the elevation profile renders at its canonical adaptive height at the bottom of the screen, the $\approx 104\text{dp}$ Bottom Sheet Peek Bar sits directly on top of it, completely occluding the elevation path, baseline, and labels.
4. On map-less tabs, the unweighted elevation profile is pushed into this occluded zone. On map tabs with elevation profiles, only empty top headroom is visible while the ink/baseline is buried behind the sheet.

This implementation plan defines the exact modifications to `SensorGridScreen.kt` and `SensorGridScreenLayoutTest.kt` to ensure complete bottom sheet clearance and 100% chart visibility.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-369` (*Cockpit Layout Inset Padding & Elevation Profile Bottom Sheet Clearance*)
  - Amends `REQ-UI-346` (*Canonical Adaptive Height & Weight Decoupling for Sole Elevation Profile on Tracking Screen*)
  - Aligns with `REQ-UI-358` (*Dynamic Cockpit Viewport Allocation, Asymmetric Map Weight Expansion & Bounded Sensor Grid Containment*)
* **Test Mapping**: `TST-UI-332` (*Cockpit Layout Bottom Inset Padding & Elevation Profile Bottom Sheet Clearance Verification*)
  - `TST-UI-332.1`: Non-map cockpit Column bottom inset padding contract verification.
  - `TST-UI-332.2`: Map-enabled elevation profile Surface bottom inset padding contract verification.
  - `TST-UI-332.3`: Layout geometry and bottom sheet clearance simulation.
  - `TST-UI-332.4`: Zero sheet peek height boundary verification.
  - `TST-UI-332.5`: Full clean-room regression test suite.

---

## 3. System Invariants & Preserved Behavior

1. **Canonical Adaptive Height Preservation (`REQ-UI-346`)**: `ElevationProfile` continues to use `calculateElevationProfileHeight(range)` ($70\text{dp} \dots 200\text{dp}$) without artificial `Modifier.weight(1f)` or `fillParentHeight = true`.
2. **Sensor Grid Scrollability & Containment (`REQ-UI-358`)**: Sensor grid preserves `.weight(1f, fill = false)` when map is hidden and `.heightIn(max = availableHeight * 0.5f)` when map is visible.
3. **Map Flexible Expansion (`REQ-UI-358`)**: When the map is visible, it retains `.weight(1f, fill = true)` to consume all vertical space remaining between the sensor grid and the elevation profile.
4. **Recomposition Barrier Isolation (`REQ-UI-326`)**: `TrackingMapContainer` remains an `@Immutable` recomposition barrier skipping redundant recompositions.
5. **Zero Dead Space**: When `sheetPeekHeight == 0.dp`, `paddingValues.calculateBottomPadding()` is $0\text{dp}$, introducing zero redundant padding or negative layout shifts.
6. **Subtask & Gate Invariance**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`. Parent ticket terminates at `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: Non-Map Cockpit Layout Bottom Inset in `SensorGridScreen.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`
* **Target Line**: $\approx 540$
* **Modification**: Apply conditional bottom padding to the stationary cockpit `Column`:
  ```kotlin
  Column(
      modifier = Modifier
          .fillMaxSize()
          .then(
              if (!state.mapState.showMap) {
                  Modifier.padding(bottom = paddingValues.calculateBottomPadding())
              } else {
                  Modifier
              }
          )
  )
  ```
* **Rationale**: Because no map exists on map-less tabs, applying `paddingValues.calculateBottomPadding()` to the `Column` ensures that both multi-row sensor grids and the sole elevation profile are lifted above the bottom sheet peek bar.

### Component 2: Elevation Profile Surface Inset Under Map in `SensorGridScreen.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`
* **Target Line**: $\approx 625$
* **Modification**: Apply bottom padding to the elevation profile `Surface` when `state.mapState.showMap == true`:
  ```kotlin
  if (state.showElevationProfile && resolvedProfile.pathPoints.size >= 2) {
      Surface(
          modifier = Modifier
              .fillMaxWidth()
              .padding(
                  top = 8.dp,
                  bottom = if (state.mapState.showMap) paddingValues.calculateBottomPadding() else 0.dp
              )
      ) {
          ElevationProfile(
              pathPoints = resolvedProfile.pathPoints,
              currentDistance = resolvedProfile.markerDistance,
              climbs = resolvedProfile.climbs,
              modifier = Modifier.fillMaxWidth()
          )
      }
  }
  ```
* **Rationale**: Symmetrical coordination: on map tabs, the `Column` has no bottom padding so the map can expand under `weight(1f, fill = true)` while the elevation profile below it is padded above the bottom sheet peek bar. On non-map tabs, the `Column` already has bottom padding, so the `Surface` uses `bottom = 0.dp` to prevent duplicate padding.

### Component 3: Unit Tests in `SensorGridScreenLayoutTest.kt`
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenLayoutTest.kt`
* **Modifications**:
  1. Add `testLayoutStructure_nonMapCockpitAppliesBottomPadding()` asserting `Column` applies `padding(bottom = paddingValues.calculateBottomPadding())` when `!state.mapState.showMap`.
  2. Add `testLayoutStructure_elevationProfileSurfaceAppliesBottomPadding()` asserting `Surface` applies bottom padding when `state.mapState.showMap`.
  3. Add `testLayoutGeometry_elevationProfileClearsBottomSheet()` verifying geometric non-occlusion ($y_{\text{profile\_bottom}} \le y_{\text{sheet\_top}}$) across both map-less and map-enabled configurations.
  4. Add `testLayoutGeometry_zeroSheetPeekHeightBoundaryVerification()` verifying zero dead space when peek height is $0\text{dp}$.

### UI Consistency (Rule 23)
* **Reference screen / component**: `SensorGridScreen.kt` / `ElevationProfile.kt`
* **Reused components**: Existing `ElevationProfile`, `Surface`, `Column`
* **Theme tokens**: Standard `paddingValues.calculateBottomPadding()`, `top = 8.dp`
* **New one-off styles & justification**: None. Reuses canonical Compose scaffold insets.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `SensorGridScreen.kt` Cockpit Layout Insets
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`
* Changes:
  1. Apply `Modifier.then(if (!state.mapState.showMap) Modifier.padding(bottom = paddingValues.calculateBottomPadding()) else Modifier)` to the cockpit `Column`.
  2. Apply `bottom = if (state.mapState.showMap) paddingValues.calculateBottomPadding() else 0.dp` to the elevation profile `Surface`.

### Step 2: Implement Unit Tests in `SensorGridScreenLayoutTest.kt`
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenLayoutTest.kt`
* Changes:
  1. Implement `testLayoutStructure_nonMapCockpitAppliesBottomPadding` (`TST-UI-332.1`).
  2. Implement `testLayoutStructure_elevationProfileSurfaceAppliesBottomPadding` (`TST-UI-332.2`).
  3. Implement `testLayoutGeometry_elevationProfileClearsBottomSheet` (`TST-UI-332.3`).
  4. Implement `testLayoutGeometry_zeroSheetPeekHeightBoundaryVerification` (`TST-UI-332.4`).

### Step 3: Run Targeted Unit Tests
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenLayoutTest"`

### Step 4: Run Full Clean-Room Test Suite
* Command: `./gradlew testDebugUnitTest`

---

## 6. Verification & Rollback Plan

* **Verification**: Verify that contract tests and geometric simulations pass 100%, and ensure zero regressions across existing tracking, map, and elevation profile tests.
* **Rollback**: Branch isolation (`bugfix/ATT-3311`) enables clean git rollback (`git revert` or branch deletion) without impacting `sprint/2026-42.4`.
