# ATT-3101 Walkthrough: Cockpit Sensor Field In-Ride Direct Editing via Long-Press Gesture

## Overview
- **Issue**: [ATT-3101](https://atrainingtracker.atlassian.net/browse/ATT-3101)
- **Requirement**: `REQ-UI-336`
- **Test Specification**: `TST-UI-296`
- **Sprint**: `2026-41.8`
- **Status**: Verified

## Problem Addressed
During active workout tracking on `SensorGridScreen` in `ScreenMode.TRACKING`, long-clicking any sensor field tile historically opened `EditSensorFieldDialog`, allowing the athlete to dynamically adjust HUD metrics (sensor type, view size, smoothing filters) directly during a ride or run without needing to interrupt the session or switch modes.

In Sprint 2026-41.2 (`e5033b5c`, ATT-2620), the layout of `SensorGridScreen.kt` was restructured to separate configuration mode from tracking/preview mode. In the tracking branch (lines 504–514), `onEdit = {}` was passed as an empty lambda under the mistaken assumption that all edit callbacks should be suppressed during tracking. Because `SensorFieldView` delegates long clicks to `onEdit()` when in `ScreenMode.TRACKING`, passing an empty lambda caused the long-press gesture to be completely ignored.

## Architectural Changes & Solution
1. **SensorGridScreen Tracking Mode Wiring (`SensorGridScreen.kt`)**:
   - In `SensorGridScreen.kt` (lines 504–514), wired `onEdit = { gridActions.onEditField(fieldState) }` in the tracking/preview sensor field rendering loop:
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
2. **Gesture Separation & Accidental Tap Protection**:
   - `SensorFieldView` internally distinguishes between single click and long click:
     - `onClick`: Triggers `onEdit()` exclusively in `ScreenMode.CONFIGURATION`. In `ScreenMode.TRACKING`, single tap remains an intentional no-op, preventing accidental popups during vigorous movement.
     - `onLongClick`: Triggers `onStartMove()` in `ScreenMode.CONFIGURATION` (Pick & Place move per `REQ-UI-200`) and triggers `onEdit()` in `ScreenMode.TRACKING`, opening `EditSensorFieldDialog`.
3. **Tile Movement & Deletion Gating**:
   - `onStartMove = {}` and `onDelete = {}` remain empty no-ops in `ScreenMode.TRACKING` and `ScreenMode.PREVIEW`, ensuring tiles cannot be accidentally dragged or deleted during an active workout.
4. **Architectural & Gesture Contract Tests**:
   - Created `SensorGridScreenContractTest.kt` verifying wiring of `onEdit` and move/delete no-op gating (`TST-UI-296.1`, `TST-UI-296.2`).
   - Created `SensorFieldViewGestureContractTest.kt` verifying gesture dispatch across `ScreenMode.TRACKING`, `ScreenMode.CONFIGURATION`, and `ScreenMode.PREVIEW` (`TST-UI-296.3`).

## Verification & Test Results
- **Targeted Contract Tests**:
  - `SensorGridScreenContractTest` passed 100%.
  - `SensorFieldViewGestureContractTest` passed 100%.
- **Clean-Room Full Suite Regression**:
  - `./gradlew testDebugUnitTest` executed with 100% pass rate across all modules (zero failures, zero regressions).

## ASPICE Traceability
- **Requirement**: `REQ-UI-336` in `docs/requirements.md` (Verified)
- **Test Specification**: `TST-UI-296` in `docs/tests.md` (Verified)
- **Implementation Plan**: `docs/engineering/plans/ATT-3101_plan.md`
- **Analysis**: `docs/engineering/analysis/ATT-3101_analysis.md`
