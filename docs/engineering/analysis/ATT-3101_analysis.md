# Stage 1 Analysis: ATT-3101 - Long clicking sensor field on tracking screen does not open sensor configuration dialog

**Ticket**: [ATT-3101](https://atrainingtracker.atlassian.net/browse/ATT-3101)  
**Sub-task**: [ATT-3128](https://atrainingtracker.atlassian.net/browse/ATT-3128) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.8`  
**Branch**: `feature/ATT-3101`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Problem Statement & Motivation

During active workout tracking on `SensorGridScreen` in `ScreenMode.TRACKING`, long-clicking any sensor field card historically opened a modal configuration dialog (`EditSensorFieldDialog`), allowing the athlete to dynamically adjust HUD metrics (sensor type, view/font size, smoothing filters) directly during a ride or run without needing to interrupt the session or switch modes.

Currently, performing a long click on any sensor field tile produces no action. The long-press gesture is ignored, leaving the athlete unable to configure or tune their HUD metrics directly from the live tracking cockpit.

---

## 2. Root Cause Analysis (Forensic Investigation)

Forensic investigation and commit archaeology using `tools/sprint_bisect.py` and `git log` identified the exact commit where this functionality regressed:
- **Culprit Commit**: `e5033b5c` (*feat(tracking): implement unified scrollable configuration container and slotting (ATT-2620)*), authored on 2026-10-08 during Sprint 2026-41.2.

### Failure Mechanism
In `SensorFieldView.kt` (lines 205–219), gesture handling is designed as:
```kotlin
.combinedClickable(
    onClick = {
        if (screenMode == ScreenMode.CONFIGURATION) {
            onEdit()
        }
    },
    onLongClick = {
        if (screenMode == ScreenMode.CONFIGURATION) {
            onStartMove()
        } else if (screenMode == ScreenMode.TRACKING) {
            onEdit()
        }
    }
)
```
- In `ScreenMode.TRACKING`, single tap (`onClick`) is an intentional no-op to prevent accidental disruption during vigorous movement.
- On long press (`onLongClick`), `SensorFieldView` delegates to `onEdit()`.

Prior to commit `e5033b5c`, `SensorGridScreen.kt` had a single unified rendering block for `SensorFieldView`, where `onEdit` was passed as:
```kotlin
onEdit = {
    if (screenMode == ScreenMode.CONFIGURATION && selectedFieldForMove != null) {
        ...
    } else {
        gridActions.onEditField(fieldState)
    }
}
```
When `e5033b5c` restructured `SensorGridScreen.kt` to separate configuration mode (`if (screenMode == ScreenMode.CONFIGURATION)`) from tracking/preview mode (`else`), the developer placed empty no-op lambdas for tracking mode in lines 504–514:
```kotlin
SensorFieldView(
    fieldState = fieldState,
    screenMode = screenMode,
    isSelectedForMove = false,
    shape = effectiveShape,
    cardElevation = effectiveElevation,
    border = effectiveBorder,
    onStartMove = {},
    onEdit = {},
    onDelete = {}
)
```
Because `onEdit = {}` is passed as an empty lambda, long-clicking a sensor field in tracking mode executes `{}` and nothing happens.

In `TrackingTabGridContent.kt` (lines 63–66 and 135–160), `gridActions.onEditField(fieldState)` forwards to `viewModel.onEditField(fieldState)`, which sets `_editingFieldId.value = fieldState.sensorFieldId`. `TrackingTabGridContent` is already fully wired to render `EditSensorFieldDialog` whenever `editingFieldId != null`. Thus, once `onEdit` in `SensorGridScreen.kt` invokes `gridActions.onEditField(fieldState)`, the dialog immediately renders and operates normally.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Restore the live tracking long-click gesture in `SensorGridScreen.kt` by wiring `onEdit = { gridActions.onEditField(fieldState) }` in tracking mode.
  2. Guarantee single-tap during live tracking remains an intentional no-op (no accidental dialogs or interruptions during riding/running).
  3. Ensure tile moving (`onStartMove = {}`) and tile deleting (`onDelete = {}`) remain strictly disabled during live tracking (`ScreenMode.TRACKING`) and preview (`ScreenMode.PREVIEW`).
  4. Author unit/contract tests asserting that long-click invokes `gridActions.onEditField(fieldState)` while single-click does not in `ScreenMode.TRACKING`.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. Modifying the internals of `EditSensorFieldDialog` or `EditSensorFieldViewModel`.
  2. Modifying the gesture logic inside `SensorFieldView.kt`.
  3. Altering `ScreenMode.CONFIGURATION` Pick & Place move or delete workflows (`REQ-UI-200`).
  4. Modifying preview mode (`ScreenMode.PREVIEW`) interaction behavior.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement `REQ-UI-336` (*Cockpit Sensor Field In-Ride Direct Editing via Long-Press Gesture*), extending `REQ-UI-103` (*Customizable Cockpits*) and complementing `REQ-UI-200` (*Cockpit Sensor Grid Pick & Place Reordering & Swapping Architecture*).
* **Historical Origin & Commit Trace**: Commit `e5033b5c` (ATT-2620, Sprint 2026-41.2).
* **Root Reason for Existing Formulation**: When isolating `ScreenMode.CONFIGURATION` for scrollable WYSIWYG editing, empty lambdas `onStartMove = {}, onEdit = {}, onDelete = {}` were placed in the tracking branch under the mistaken assumption that all editing callbacks should be suppressed during tracking. However, `SensorFieldView` already internally distinguishes between single click and long click, ensuring that single taps do nothing while long clicks safely trigger field editing.
* **Preservation of Core Invariants**: Gating `onEdit` in tracking mode via long-press preserves the core safety invariant that workouts are not interrupted by accidental taps, while restoring intentional on-the-fly metric adjustments. `onStartMove` and `onDelete` remain empty no-ops in tracking mode.

---

## 5. Architectural Strategy & High-Level Solution

1. **`SensorGridScreen.kt` (Tracking & Preview branch)**:
   In the tracking/preview rendering loop for `SensorFieldView`:
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
2. **Behavior Verification**:
   - `ScreenMode.TRACKING`: Single tap -> No-op. Long press -> `onEdit()` -> `gridActions.onEditField(fieldState)` -> `_editingFieldId.value = fieldState.sensorFieldId` -> `EditSensorFieldDialog` is shown.
   - `ScreenMode.CONFIGURATION`: Single tap -> `onEdit()` -> edit dialog. Long press -> `onStartMove()` -> Pick & Place move mode (`REQ-UI-200`).
   - `ScreenMode.PREVIEW`: Single tap -> No-op. Long press -> No-op.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. *Zero Accidental Trigger Invariant*: Single taps on sensor field cards in live tracking MUST NOT trigger any dialogs or mode changes.
  2. *Pick & Place Invariant*: Moving/dragging tiles is strictly reserved for `ScreenMode.CONFIGURATION` (`REQ-UI-200`); `onStartMove` MUST remain disabled in live tracking.
  3. *Zero Telemetry Displacement*: Opening and closing the dialog does not alter telemetry stream processing or accumulated metrics.
  4. *Clean-Room Full Suite Regression*: All unit tests pass with 100% success rate.
