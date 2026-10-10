# Stage 3: Implementation Plan - ATT-3101: Long clicking sensor field on tracking screen does not open sensor configuration dialog

**Ticket**: [ATT-3101](https://atrainingtracker.atlassian.net/browse/ATT-3101)  
**Sub-task**: [ATT-3130](https://atrainingtracker.atlassian.net/browse/ATT-3130) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.8`  
**Requirement Mapping**: `REQ-UI-336` (*Cockpit Sensor Field In-Ride Direct Editing via Long-Press Gesture*)  
**Test Mapping**: `TST-UI-296` (*Cockpit Sensor Field In-Ride Direct Editing via Long-Press Gesture Verification*)  
**Branch**: `feature/ATT-3101`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Problem Description & Background

During active workout tracking on `SensorGridScreen` in `ScreenMode.TRACKING`, long-clicking any sensor field tile historically opened `EditSensorFieldDialog`, enabling in-ride customization of metrics, typography, and smoothing filters.

In Sprint 2026-41.2 (`e5033b5c`, ATT-2620), the layout of `SensorGridScreen.kt` was separated into configuration mode and tracking/preview mode branches. In the tracking branch (lines 504–514), `onEdit = {}` was passed as an empty lambda, inadvertently breaking the long-click edit trigger.

This plan details the precise atomic changes and contract tests required to re-wire `onEdit = { gridActions.onEditField(fieldState) }` in `SensorGridScreen.kt` while preserving all system invariants.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-336` (*Cockpit Sensor Field In-Ride Direct Editing via Long-Press Gesture*)
* **Test Mapping**: `TST-UI-296` (*Cockpit Sensor Field In-Ride Direct Editing via Long-Press Gesture Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Accidental Trigger Invariant**: Single-tap (`onClick`) on sensor field cards during live workout tracking (`ScreenMode.TRACKING`) MUST remain an intentional no-op to prevent disruptive dialog popups during vigorous movement.
2. **Tile Movement & Deletion Gating Invariant**: Moving tiles (`onStartMove = {}`) and deleting tiles (`onDelete = {}`) MUST remain strictly disabled in `ScreenMode.TRACKING` and `ScreenMode.PREVIEW`. Tile reordering is strictly restricted to `ScreenMode.CONFIGURATION` (`REQ-UI-200`).
3. **Configuration Mode Intactness**: In `ScreenMode.CONFIGURATION`, single-tap triggers `onEdit()` and long-press triggers `onStartMove()`.
4. **Zero Telemetry Displacement**: Opening and closing `EditSensorFieldDialog` does not disrupt continuous background telemetry collection, distance accumulation, or sensor streaming.
5. **Sub-Task Self-Sufficiency**: Sub-tasks transition autonomously to `Erledigt` upon passing automated Gate audits (`freigabe`).
6. **Parent Human Gate Invariance**: Final completion of parent ticket `ATT-3101` is reserved exclusively for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `SensorGridScreen.kt` (Tracking Mode Wiring)
* In `SensorGridScreen.kt` (lines 504–514), update the tracking/preview rendering loop for `SensorFieldView`:
  ```kotlin
  SensorFieldView(
      fieldState = fieldState,
      screenMode = screenMode,
      isSelectedForMove = false,
      shape = effectiveShape,
      cardElevation = effectiveElevation,
      border = effectiveBorder,
      onStartMove = {},
      onEdit = { gridActions.onEditField(fieldState) },
      onDelete = {}
  )
  ```
* This ensures that when `SensorFieldView` delegates long clicks to `onEdit()` in `ScreenMode.TRACKING`, `gridActions.onEditField(fieldState)` is invoked, opening `EditSensorFieldDialog`.

### Component 2: Contract Tests
* Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenContractTest.kt` verifying:
  - In `SensorGridScreen.kt`, `SensorFieldView` in the tracking branch delegates `onEdit` to `gridActions.onEditField(fieldState)`.
  - `onStartMove` and `onDelete` remain no-ops in the tracking branch.
* Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldViewGestureContractTest.kt` verifying:
  - In `ScreenMode.TRACKING`, single tap does not trigger edit/move; long press triggers `onEdit()`.
  - In `ScreenMode.CONFIGURATION`, single tap triggers `onEdit()`; long press triggers `onStartMove()`.

### UI Consistency (Rule 23)
* **Reference screen / component**: Existing `SensorGridScreen` HUD and `EditSensorFieldDialog`.
* **Reused components**: `SensorFieldView`, `EditSensorFieldDialog`, `SensorGridActions`.
* **Theme tokens**: Standard typography and shape tokens from `MaterialTheme.colorScheme` and `CardDefaults`.
* **New one-off styles & justification**: None. Reusing existing components and theme tokens exclusively.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Gate Verification
* Run `python3 tools/jira_util.py check-gate ATT-3130` to confirm Gate 3 passage before code modification.

### Step 2: Implement Production Code Change
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`
* Change: Replace `onEdit = {}` with `onEdit = { gridActions.onEditField(fieldState) }` in line 512.

### Step 3: Implement Architectural & Gesture Contract Tests
* File 1: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenContractTest.kt` (`TST-UI-296.1`, `TST-UI-296.2`)
* File 2: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldViewGestureContractTest.kt` (`TST-UI-296.3`)

### Step 4: Targeted Unit Test Execution
* Execute:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenContractTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldViewGestureContractTest"`
* Confirm 100% pass rate.

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit tests in Stage 4, followed by complete clean-room test suite `./gradlew testDebugUnitTest` in Stage 5.
* **Rollback Plan**: In the event of an unresolvable defect, `git checkout feature/ATT-3101` and `git reset --hard HEAD~1` restores the previous commit cleanly without impacting `sprint/2026-41.8` or `develop`.
