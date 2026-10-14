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
1. `BoxWithConstraints` in `SensorGridScreen.kt` intentionally discarded `paddingValues.calculateBottomPadding()` to allow the Google Map to draw under the bottom sheet.
2. In `ATT-3173`, `Modifier.weight(1f)` was removed from the elevation profile to restore its canonical adaptive height ($70\text{dp} \dots 200\text{dp}$).
3. When the elevation profile renders at its canonical adaptive height at the bottom of the screen, the $\approx 104\text{dp}$ Bottom Sheet Peek Bar sits directly on top of it, completely occluding the elevation path, baseline, and labels.
4. On map-less tabs, the unweighted elevation profile is pushed into this occluded zone. On map tabs with elevation profiles, only empty top headroom is visible while the ink/baseline is buried behind the sheet.

This implementation plan defines the exact modifications to `SensorGridScreen.kt` and `SensorGridScreenLayoutTest.kt` to ensure complete bottom sheet clearance and 100% chart visibility while strictly preventing modifier churn, layout jitter, and dead space.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-369` (*Cockpit Layout Inset Padding & Elevation Profile Bottom Sheet Clearance*)
  - Amends `REQ-UI-346` (*Canonical Adaptive Height & Weight Decoupling for Sole Elevation Profile on Tracking Screen*)
  - Aligns with `REQ-UI-358` (*Dynamic Cockpit Viewport Allocation, Asymmetric Map Weight Expansion & Bounded Sensor Grid Containment*)
* **Test Mapping**: `TST-UI-332` (*Cockpit Layout Bottom Inset Padding & Elevation Profile Bottom Sheet Clearance Verification*)
  - `TST-UI-332.1`: Elevation profile Surface bottom inset padding contract verification (`bottom = paddingValues.calculateBottomPadding()`).
  - `TST-UI-332.2`: Layout geometry and bottom sheet clearance simulation across map and non-map views.
  - `TST-UI-332.3`: Zero sheet peek height boundary verification (`sheetPeekHeight == 0.dp`).
  - `TST-UI-332.4`: Negative test & Zero Dead Space verification when `showElevationProfile == false`.
  - `TST-UI-332.5`: Full clean-room regression test suite (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Inset Stacking & Single Source of Truth (Rule 36)**:
   In `SensorGridScreen.kt`, line 282:
   ```kotlin
   sheetPeekHeight = if (shouldShowBottomSheet && screenMode == ScreenMode.TRACKING) 
       BottomSheetDesign.PeekHeightLiveSegment + navBarHeight else 0.dp
   ```
   In Material 3 `BottomSheetScaffold`, `paddingValues.calculateBottomPadding()` is equal to `sheetPeekHeight`. Because `sheetPeekHeight` explicitly incorporates `navBarHeight` (`PeekHeightLiveSegment + navBarHeight`), `calculateBottomPadding()` already provides the unified maximum inset encompassing both system bars and the sheet peek bar. In tracking mode, the stationary cockpit does not apply `.navigationBarsPadding()`. Applying `paddingValues.calculateBottomPadding()` directly to `Surface` guarantees zero double-padding and zero inset stacking conflicts.
2. **Jitter Mitigation & Static Sheet Peek Invariance**:
   In Compose Material 3 `BottomSheetScaffold`, `paddingValues.calculateBottomPadding()` represents the static scaffold peek height reservation. It remains constant during user drag gestures, touch interactions, or partial/full sheet expansion animations (the sheet slides in an independent overlay layer above the content). Consequently, `Surface(modifier = Modifier.padding(bottom = paddingValues.calculateBottomPadding()))` maintains a fixed, stable baseline throughout all sheet animations, eliminating layout jumps and UI jitter.
3. **Zero Modifier Churn & Separation of Concerns (SWE.2)**: 
   The parent cockpit `Column` modifier remains completely stationary and unconditional (`Modifier.fillMaxSize()`). Bottom inset handling is encapsulated strictly within the elevation profile `Surface` modifier, eliminating conditional branching on the parent container and preventing full-screen recomposition churn.
4. **Zero Dead Space when Profile is Disabled or Lacks Points**: 
   When `state.showElevationProfile == false` or `resolvedProfile.pathPoints.size < 2`, the elevation profile `Surface` is not composed. Because the parent `Column` has no unconditional bottom padding, the sensor grid and map expand edge-to-edge down to the screen boundary with $0\text{dp}$ residual whitespace.
5. **Fallback Safety & Robustness**:
   `paddingValues.calculateBottomPadding()` is a non-null `Dp` value guaranteed by Compose Material 3 `PaddingValues`. If the sheet is disabled or absent (e.g. landscape mode or preview), it safely evaluates to `0.dp`, introducing zero margin.
6. **Canonical Adaptive Height Preservation (`REQ-UI-346`)**: 
   `ElevationProfile` continues to use `calculateElevationProfileHeight(range)` ($70\text{dp} \dots 200\text{dp}$) without artificial `Modifier.weight(1f)` or `fillParentHeight = true`.
7. **Sensor Grid Scrollability & Containment (`REQ-UI-358`)**: 
   Sensor grid preserves `.weight(1f, fill = false)` when map is hidden and `.heightIn(max = availableHeight * 0.5f)` when map is visible.
8. **Map Flexible Expansion (`REQ-UI-358`)**: 
   When the map is visible, it retains `.weight(1f, fill = true)` to consume all vertical space remaining between the sensor grid and the elevation profile.
9. **Recomposition Barrier Isolation (`REQ-UI-326`)**: 
   `TrackingMapContainer` remains an `@Immutable` recomposition barrier skipping redundant recompositions.
10. **Subtask & Gate Invariance**: 
   Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`. Parent ticket terminates at `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: Encapsulated Elevation Profile Surface Inset in `SensorGridScreen.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`
* **Target Line**: $\approx 624$
* **Implementation**:
  ```kotlin
  if (state.showElevationProfile && resolvedProfile.pathPoints.size >= 2) {
      Surface(
          modifier = Modifier
              .fillMaxWidth()
              .padding(
                  top = 8.dp,
                  bottom = paddingValues.calculateBottomPadding()
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
* **Architectural Rationale**:
  1. *Decoupled Container Invariant*: The parent `Column` remains `Modifier.fillMaxSize()` without modifier mutation.
  2. *Universal Sheet Clearance*: Applying `bottom = paddingValues.calculateBottomPadding()` directly to the `Surface` lifts the elevation profile by exactly `sheetPeekHeight` (e.g. $104\text{dp}$) both when rendered alone on a map-less tab and when anchored below the map on a map tab.
  3. *Zero Dead Space Guarantee*: If `state.showElevationProfile == false` (or `pathPoints.size < 2`), the `Surface` is omitted entirely. The parent `Column` has no residual bottom padding, allowing the map (or sensor grid) to utilize 100% of the screen.
  4. *Zero Peek Inactive Invariant*: When `sheetPeekHeight == 0.dp`, `calculateBottomPadding()` returns `0.dp`, adding $0\text{dp}$ padding.

### Component 2: Unit & Geometry Simulation Tests in `SensorGridScreenLayoutTest.kt`
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenLayoutTest.kt`
* **Modifications**:
  1. `testElevationProfileSurface_appliesScaffoldBottomPadding()`: Verify `Surface` modifier includes `bottom = paddingValues.calculateBottomPadding()`.
  2. `testLayoutGeometry_elevationProfileClearsBottomSheet_mapAndNonMap()`: Mathematically verify that for both map and non-map configurations, $y_{\text{profile\_bottom}} \le y_{\text{sheet\_top}}$.
  3. `testLayoutGeometry_zeroSheetPeekHeightBoundaryVerification()`: Verify that when `sheetPeekHeight == 0f`, `bottomPadding == 0f`, total consumed height equals `availableHeight`, and `deadSpace == 0f`.
  4. `testLayoutGeometry_elevationProfileDisabled_zeroDeadSpace()`: Negative test verifying that when `showElevationProfile == false`, no bottom padding is applied to the layout, and map consumes 100% remaining space with $0\text{dp}$ dead space.

### UI Consistency (Rule 23)
* **Reference screen / component**: `SensorGridScreen.kt` / `ElevationProfile.kt`
* **Reused components**: Existing `ElevationProfile`, `Surface`, `Column`
* **Theme tokens**: Standard `paddingValues.calculateBottomPadding()`, `top = 8.dp`
* **New one-off styles & justification**: None. Reuses canonical Compose scaffold insets.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Apply Bottom Inset to Elevation Profile Surface in `SensorGridScreen.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`
* Change: Update the `Surface` modifier for `ElevationProfile` to include `bottom = paddingValues.calculateBottomPadding()`.

### Step 2: Implement Test Cases in `SensorGridScreenLayoutTest.kt`
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenLayoutTest.kt`
* Changes:
  1. Implement `testElevationProfileSurface_appliesScaffoldBottomPadding` (`TST-UI-332.1`).
  2. Implement `testLayoutGeometry_elevationProfileClearsBottomSheet_mapAndNonMap` (`TST-UI-332.2`).
  3. Implement `testLayoutGeometry_zeroSheetPeekHeightBoundaryVerification` (`TST-UI-332.3`).
  4. Implement `testLayoutGeometry_elevationProfileDisabled_zeroDeadSpace` (`TST-UI-332.4`).

### Step 3: Run Targeted Unit Tests
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenLayoutTest"`

### Step 4: Run Full Clean-Room Test Suite
* Command: `./gradlew testDebugUnitTest`

---

## 6. Verification & Rollback Plan

* **Verification**: Verify that contract tests and geometric simulations pass 100%, and ensure zero regressions across existing tracking, map, and elevation profile tests.
* **Rollback**: Branch isolation (`bugfix/ATT-3311`) enables clean git rollback (`git revert` or branch deletion) without impacting `sprint/2026-42.4`.
