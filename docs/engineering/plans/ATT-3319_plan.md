# Stage 3: Implementation Plan - ATT-3319: Ensure Segment and Climb bottom sheets always pop up to full height on new active entity

**Ticket**: [ATT-3319](https://rainerblind.atlassian.net/browse/ATT-3319)  
**Sub-task**: [ATT-3334](https://rainerblind.atlassian.net/browse/ATT-3334) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2582](https://rainerblind.atlassian.net/browse/ATT-2582) (*Segments: Live Tracking, Exploration & Route Integration*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-343`  
**Test Mapping**: `TST-UI-303`  
**Branch**: `feature/ATT-3319`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-12  

---

## 1. Problem Description & Background

In ticket ATT-3176, `initialSheetValue` was updated to `SheetValue.Expanded` to prevent athletes from needing to manually swipe up bottom sheets to see live climb/segment telemetry while riding or running. However, two issues in `SensorGridScreen.kt` prevent the bottom sheet from consistently expanding:

1. `LaunchedEffect(shouldShowBottomSheet)` previously guarded `expand()` with `if (scaffoldState.bottomSheetState.currentValue == SheetValue.Hidden)`. If an athlete previously swiped the sheet down to peek height (`SheetValue.PartiallyExpanded`), `currentValue` is not `Hidden`, causing the guard to fail and trapping the sheet in peek height.
2. `LaunchedEffect` was keyed solely on the boolean `shouldShowBottomSheet`. During route navigation with multiple climbs or segments, `shouldShowBottomSheet` remains `true` continuously across transitions between entities, failing to re-trigger expansion when a new entity activates.

This plan details the construction steps to key the reactive sheet controller on both bottom sheet visibility and a composite active entity key, and to invoke `expand()` unconditionally when `shouldShowBottomSheet == true`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-343` (*Live Segment and Climb Cockpit BottomSheet Default Fully Expanded Presentation*)
* **Test Mapping**: `TST-UI-303` (*Tracking Tab Live Segment & Climb BottomSheet Default Fully Expanded Presentation Contract Verification*)

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-343` (*Live Segment and Climb Cockpit BottomSheet Default Fully Expanded Presentation*), amending Clause 2.
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-41.8 (`ATT-3176`, commit `6b9d0315`), which amended `REQ-UI-327` (Sprint 2026-41.7, `ATT-2945`).
* **Root Reason for Existing Formulation**: ATT-3176 added `if (scaffoldState.bottomSheetState.currentValue == SheetValue.Hidden) expand()` with the intention of avoiding redundant calls to `expand()` if already expanded. However, it inadvertently blocked expansion when the sheet was in `SheetValue.PartiallyExpanded`, and keyed only on `shouldShowBottomSheet`.
* **Preservation of Core Invariants**: Manual swipe-down gesture ergonomics during an ongoing entity remain strictly preserved because `LaunchedEffect(shouldShowBottomSheet, activeEntityKey)` does not re-fire when the athlete swipes. Tab isolation (`isTabActive`), hidden state handling (`hide()`), and 100% full-suite unit test pass rate remain strictly preserved.

---

## 3. System Invariants & Preserved Behavior

1. **Zero Telemetry Occlusion on Disabled Tabs**: The bottom sheet must remain in `SheetValue.Hidden` with 0 dp peek height when `!shouldShowBottomSheet`.
2. **Interactive Manual Swipe Down Stability**: Athletes must retain the ability to swipe down to peek height during an ongoing segment or climb without the sheet bouncing back up while traversing the same entity.
3. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
4. **Clean-Room Test Pass Rate**: 100% test pass rate across all unit tests in `./gradlew testDebugUnitTest`.

---

## 4. Proposed Architectural Changes

### Component 1: Reactive Entity Key & Sheet Controller (`SensorGridScreen.kt`)
* Derive `activeEntityKey` combining:
  * For Live Segments: `"segment_${activeSegment.staticData.summary.stravaId}"`
  * For Live Climbs: `"climb_${activeLiveClimb?.climb?.id}_${activeLiveClimb?.routeIndex}_${activeLiveClimb?.climb?.name}"`
  * Fallback: `null`
* Update `LaunchedEffect`:
  ```kotlin
  LaunchedEffect(shouldShowBottomSheet, activeEntityKey) {
      if (!shouldShowBottomSheet) {
          if (scaffoldState.bottomSheetState.currentValue != SheetValue.Hidden) {
              scaffoldState.bottomSheetState.hide()
          }
      } else {
          // Unconditional expansion to full height when a sheet should show or a new active entity is presented
          scaffoldState.bottomSheetState.expand()
      }
  }
  ```

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* **Reference screen / component**: Existing `LiveSegmentSheet.kt` and `LiveClimbSheet.kt` in `SensorGridScreen.kt`.
* **Reused components**: `BottomSheetScaffold`, `rememberStandardBottomSheetState`, `BottomSheetDesign.SheetShape`, `BottomSheetDesign.PeekHeightLiveSegment`.
* **Theme tokens**: Shapes (`BottomSheetDesign.SheetShape` 16.dp top corners), Spacing (56.dp peek height), Colors (`MaterialTheme.colorScheme.surface`).
* **New one-off styles & justification**: None. Behavior strictly coordinates animation between existing standard Material 3 sheet values (`Hidden`, `PartiallyExpanded`, `Expanded`).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `SensorGridScreen.kt` Reactive Controller
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`
* **Changes**:
  1. Compute `activeEntityKey` below `shouldShowBottomSheet` definition around line 220.
  2. Update `LaunchedEffect(shouldShowBottomSheet, activeEntityKey)`.
  3. Replace the `if (scaffoldState.bottomSheetState.currentValue == SheetValue.Hidden)` guard with direct `scaffoldState.bottomSheetState.expand()`.

### Step 2: Update Architectural Contract Tests in `SensorGridScreenClimbSuppressionTest.kt`
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenClimbSuppressionTest.kt`
* **Changes**:
  1. Update `testSensorGridScreen_launchedEffect_drivesSheetStateHideAndExpand` to verify that `LaunchedEffect` is keyed on `(shouldShowBottomSheet, activeEntityKey)`.
  2. Add unit test `testSensorGridScreen_activeEntityKey_derivesUniqueKeyForSegmentsAndClimbs` verifying presence of `activeEntityKey` definition.
  3. Verify that `expand()` is called without the restricting `currentValue == SheetValue.Hidden` guard.

### Step 3: Execute Targeted Unit Tests
* **Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenClimbSuppressionTest"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**:
  1. Targeted execution of `SensorGridScreenClimbSuppressionTest`.
  2. Clean-room full regression suite `./gradlew testDebugUnitTest`.
  3. Physical deployment and sanity check on Pixel 10 test hardware (`66020DLCR002FL`).
* **Rollback Plan**:
  Git branch isolation on `feature/ATT-3319`. If any unexpected regressions occur, revert commit or reset branch to `sprint/2026-42.1`.
