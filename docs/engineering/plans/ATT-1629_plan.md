# Stage 3: Implementation Plan - ATT-1629: [Feature] [Cockpit/Grid] Pick & Place Sensor Tile Reordering and Swapping Mode

**Ticket**: [ATT-1629](https://atrainingtracker.atlassian.net/browse/ATT-1629)  
**Sub-task**: [ATT-1701](https://atrainingtracker.atlassian.net/browse/ATT-1701) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.5`  
**Requirement Mapping**: `REQ-UI-200`  
**Test Mapping**: `TST-UI-154`  
**Branch**: `feature/ATT-1629`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Problem Description & Background
Currently, customizing the layout of sensor tiles in the tracking cockpit requires deleting and recreating fields from scratch if an athlete wants to change their position or swap two metrics (e.g. moving Heart Rate to the top row or swapping Pace and Speed). Continuous touch drag-and-drop previously failed due to Jetpack Compose pointer-capture locks and scroll gesture competition.

This plan specifies the implementation of an accessible, tactile "Pick & Place" (Tap-to-Move / Tap-to-Swap) reordering architecture that eliminates gesture friction and configuration loss.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-200` (*Cockpit Sensor Grid Pick & Place Reordering & Swapping Architecture*)
* **Test Mapping**: `TST-UI-154` (*Cockpit Sensor Grid Pick & Place Reordering & Swapping Verification*)
  - `TST-UI-154.1`: Database tile swapping & coordinate invariants.
  - `TST-UI-154.2`: Database tile moving & grid normalization.
  - `TST-UI-154.3`: ViewModel move mode state & dispatch.
  - `TST-UI-154.4`: Compose grid UI interaction & accessibility.
  - `TST-UI-154.5`: 9-language localization & specifier audit.
  - `TST-UI-154.6`: Full clean-room regression suite.

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Standard tracking mode (`ScreenMode.TRACKING`) clicks, long-clicks, telemetry subscriptions, and display formatting MUST NOT be altered.
2. **Thread Safety & Dispatcher Affinity**: Database operations in `TrackingViewsDatabaseManager` execute within SQLite transactions on `Dispatchers.IO`. UI notifications execute on `Dispatchers.Main`.
3. **Layout Continuity**: Grid coordinates `(rowNr, colNr)` in `TrackingViewsDbHelper.ROWS_TABLE` MUST remain contiguous starting at 1, with zero empty row gaps or duplicate coordinate collisions.
4. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate review via transition `freigabe`.
5. **Parent Human Gate Invariance**: Terminal transition of the parent ticket ATT-1629 is strictly reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: Database & Repository Layer (`TrackingViewsDatabaseManager.java`, `TrackingViewsRepository.kt`)
- `TrackingViewsDatabaseManager.java`:
  - `swapSensorFields(long fieldIdA, long fieldIdB)`: atomically exchanges `(rowNr, colNr)` in a transaction.
  - `moveSensorField(long fieldId, int targetRow, int targetCol)`: parks the field at `(-1,-1)`, compacts origin row columns, shifts rows if origin row emptied, opens slot at `(targetRow, targetCol)`, assigns target coordinates, and calls `normalizeGrid`.
  - `normalizeGrid(SQLiteDatabase db, long tabViewId)`: iterates through distinct sorted rows and re-indexes contiguous row numbers `1..R`, and within each row re-indexes contiguous column numbers `1..C`.
- `TrackingViewsRepository.kt`:
  - Adds suspend functions `swapSensorFields(fieldIdA: Long, fieldIdB: Long)` and `moveSensorField(fieldId: Long, targetRow: Int, targetCol: Int)` executing on `Dispatchers.IO` and incrementing `configUpdateTrigger` on `Dispatchers.Main`.

### Component 2: ViewModel & State Layer (`TrackingViewModel.kt`)
- `TrackingViewModel.kt`:
  - Introduces `val selectedFieldForMove: StateFlow<SensorFieldState?>`.
  - Adds `onSelectFieldForMove(fieldState: SensorFieldState)`, `onCancelMove()`, `onSwapFields(sourceFieldId: Long, targetFieldId: Long)`, and `onMoveField(sourceFieldId: Long, targetRow: Int, targetCol: Int)`.

### Component 3: UI & Interaction Layer (`SensorGridScreen.kt`, `SensorFieldView.kt`, `TrackingTabGridContent.kt`)
- `GridActions`:
  - Extended with move selection, swapping, moving, and cancellation callbacks.
- `SensorFieldView.kt`:
  - Adds `isSelectedForMove: Boolean = false` and `onStartMove: () -> Unit = {}`.
  - Applies `BorderStroke(2.dp, MaterialTheme.colorScheme.primary)` when selected.
  - In `ScreenMode.CONFIGURATION`: long-click starts move mode; adds a reorder icon button (`Icons.Default.SwapHoriz`) next to the delete button.
- `SensorGridScreen.kt`:
  - Collects `selectedFieldForMove`.
  - Renders floating/pinned guidance banner at the top of the grid when `selectedFieldForMove != null` with instruction text and a Cancel button.
  - When `selectedFieldForMove != null`:
    - Tapping another tile triggers `onSwapFields(selected.id, target.id)`.
    - Tapping the same tile triggers `onCancelMove()`.
    - Tapping a `RowAdder` triggers `onMoveField(selected.id, targetRow, -1)`.
    - Tapping a `ColAdder` triggers `onMoveField(selected.id, targetRow, targetCol)`.
    - Tapping Cancel button on the banner triggers `onCancelMove()`.

### Component 4: Localization (9 Locales)
- `move_tile_banner_instruction` & `move_tile_cancel` externalized across all 9 application locales.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Database Operations in `TrackingViewsDatabaseManager.java`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/database/TrackingViewsDatabaseManager.java`
* Changes: Implement `swapSensorFields(long, long)`, `moveSensorField(long, int, int)`, and `normalizeGrid(SQLiteDatabase, long)`.

### Step 2: Repository Bridge in `TrackingViewsRepository.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/TrackingViewsRepository.kt`
* Changes: Add `swapSensorFields` and `moveSensorField` delegating to database manager with UI trigger invalidation.

### Step 3: ViewModel State Management in `TrackingViewModel.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingViewModel.kt`
* Changes: Add `selectedFieldForMove` StateFlow, `onSelectFieldForMove`, `onCancelMove`, `onSwapFields`, and `onMoveField`.

### Step 4: Sensor Field View Styling & Affordance in `SensorFieldView.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldView.kt`
* Changes: Add `isSelectedForMove`, long-click move trigger in configuration mode, move icon button next to delete button, and primary border highlighting.

### Step 5: Grid Compose Integration & Guidance Banner in `SensorGridScreen.kt` & `TrackingTabGridContent.kt`
* Files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingTabGridContent.kt`
* Changes: Extend `GridActions`, render guidance banner, route tile and adder taps during move mode.

### Step 6: 9-Locale Resource String Addition
* Files:
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-de/strings.xml`
  - `app/src/main/res/values-es/strings.xml`
  - `app/src/main/res/values-fr/strings.xml`
  - `app/src/main/res/values-it/strings.xml`
  - `app/src/main/res/values-ja/strings.xml`
  - `app/src/main/res/values-nl/strings.xml`
  - `app/src/main/res/values-pl/strings.xml`
  - `app/src/main/res/values-pt/strings.xml`
* Changes: Add `move_tile_banner_instruction` and `move_tile_cancel` across all 9 supported locales.

### Step 7: Unit & Localization Tests
* Files:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/database/TrackingViewsDatabaseManagerTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingViewModelGridTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenReorderTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/localization/SensorGridLocalizationTest.kt`
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManagerTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.*" --tests "com.atrainingtracker.trainingtracker.localization.SensorGridLocalizationTest"`

---

## 6. Verification & Rollback Plan

* **Verification**: Execute targeted unit and localization tests during Stage 4 construction, followed by a full clean-room test regression (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback Plan**: All work is isolated on `feature/ATT-1629`. If unexpected issues arise, checking out `sprint/2026-40.5` completely rolls back all modifications without affecting sprint stability.
