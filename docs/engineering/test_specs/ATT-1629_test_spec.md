# Stage 2: Requirement & Test Specification - ATT-1629: [Feature] [Cockpit/Grid] Pick & Place Sensor Tile Reordering and Swapping Mode

**Ticket**: [ATT-1629](https://atrainingtracker.atlassian.net/browse/ATT-1629)  
**Sub-task**: [ATT-1700](https://atrainingtracker.atlassian.net/browse/ATT-1700) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.5`  
**Requirement Mapping**: `REQ-UI-200` (*Cockpit Sensor Grid Pick & Place Reordering & Swapping Architecture*)  
**Test Spec ID**: `TST-UI-154`  
**Branch**: `feature/ATT-1629`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Requirement Specification (REQ-UI-200)

### 1.1 Problem Statement & Rationale
Currently, reorganizing sensor tiles in the tracking cockpit requires deleting and re-creating fields from scratch if an athlete wants to adjust their layout hierarchy (such as moving an experimentally added Heart Rate or Power tile from the bottom of the grid to the prominent top row, or swapping Speed and Cadence). Prior explorations into continuous touch drag-and-drop failed due to pointer-capture lockouts in Jetpack Compose and gesture competition with the outer `verticalScroll()` container.

An accessible, touchscreen-optimized "Pick & Place" (Tap-to-Move / Tap-to-Swap) mode provides intuitive reordering without gesture conflicts, coordinate-tracking complexity, or configuration loss.

### 1.2 Functional & Architectural Requirements

1. **Selection State (Pick)**:
   - In cockpit configuration mode (`ScreenMode.CONFIGURATION`), long-pressing any sensor tile or tapping its dedicated move affordance icon button SHALL select the tile and transition the cockpit into Move Mode (`selectedFieldForMove != null`).
   - The selected tile SHALL display prominent visual feedback (`BorderStroke(2.dp, MaterialTheme.colorScheme.primary)`) and trigger tactile haptic confirmation (`HapticFeedbackType.LongPress`).

2. **Floating Guidance Banner**:
   - While a tile is selected, a floating, dismissible guidance banner SHALL be displayed at the top of the grid containing instruction text `R.string.move_tile_banner_instruction` and a cancel action button `R.string.move_tile_cancel`.

3. **Tile Swapping (Place)**:
   - Tapping another existing sensor tile while in Move Mode SHALL atomically exchange (`swap`) the two tiles' `(rowNr, colNr)` coordinates in the database (`TrackingViewsDatabaseManager.swapSensorFields`), update the UI state, and exit Move Mode.

4. **Move to Adder Slot & Layout Normalization**:
   - Tapping an existing `RowAdder` or `ColAdder` (`+` button) while in Move Mode SHALL reposition the selected tile to that designated new slot (`TrackingViewsDatabaseManager.moveSensorField`), compact the origin row, eliminate empty rows, normalize grid coordinates, and exit Move Mode without opening the new-sensor creation dialog.

5. **Cancellation Mechanics**:
   - Tapping the selected tile a second time, tapping the banner cancel button, pressing the Android Back button, or exiting configuration mode SHALL cleanly exit Move Mode with zero layout changes.

6. **100% 9-Language Localization Parity**:
   - String resources `move_tile_banner_instruction` and `move_tile_cancel` SHALL be defined across all 9 application locales (values, values-de, values-es, values-fr, values-it, values-ja, values-nl, values-pl, values-pt).

### 1.3 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Tile Selection & Guidance)**:
  * *Given* an athlete viewing the cockpit in configuration mode (`ScreenMode.CONFIGURATION`),
  * *When* long-pressing a sensor tile or tapping its move icon,
  * *Then* the tile SHALL highlight with a primary accent border, and the guidance banner SHALL appear at the top.
* **Criterion 2 (Tile Swapping)**:
  * *Given* an active Move Mode with tile A selected,
  * *When* tapping another sensor tile B,
  * *Then* tile A and tile B SHALL cleanly swap positions in the grid layout and SQLite database, and Move Mode SHALL exit.
* **Criterion 3 (Move to Adder Slot)**:
  * *Given* an active Move Mode with tile A selected,
  * *When* tapping a `RowAdder` or `ColAdder`,
  * *Then* tile A SHALL move to the designated row/column slot, the origin row SHALL compact, grid coordinates SHALL be normalized, and Move Mode SHALL exit.
* **Criterion 4 (Cancellation)**:
  * *Given* an active Move Mode,
  * *When* tapping the cancel button or tapping the selected tile again,
  * *Then* Move Mode SHALL exit without modifying any tile positions.

### 1.4 System Invariants
1. Normal active tracking mode (`ScreenMode.TRACKING`) interaction, values rendering, and sensor telemetry MUST remain 100% intact.
2. Single-thread SQLite concurrency in `TrackingViewsDatabaseManager` MUST be maintained.
3. No duplicate `(rowNr, colNr)` coordinates or empty row gaps are permitted in `ROWS_TABLE`.
4. Database schema version remains unchanged (fields `ROW_NR` and `COL_NR` already exist in SQLite).
5. 9-language localization parity MUST be strictly enforced.

### 1.5 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement only (`REQ-UI-200`), extending and complementing `REQ-UI-103` (*Customizable Cockpits*).
* **Historical Origin & Commit Trace**: Ticket `ATT-1629`, Sprint `2026-40.5`.
* **Root Reason for Existing Formulation**: Previously, reordering required deleting and re-creating fields from scratch due to pointer-capture lockouts when attempting continuous drag-and-drop within a scrollable column.
* **Preservation of Core Invariants**: Touch target accessibility, edge-to-edge window insets (`REQ-UI-148`), SQLite single-thread confinement, and zero schema migration are preserved.

---

## 2. Test Specification (TST-UI-154)

### Test Case 1: Database Tile Swapping (`[TST-UI-154.1]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/database/TrackingViewsDatabaseManagerTest.kt`
* **Preconditions**: SQLite test database seeded with a tab containing 4 sensor fields: Tile 1 at (1,1), Tile 2 at (1,2), Tile 3 at (2,1), Tile 4 at (3,1).
* **Action**:
  - Invoke `swapSensorFields(tile1Id, tile3Id)`.
* **Expected Result**:
  - Tile 1 has `row = 2, col = 1`.
  - Tile 3 has `row = 1, col = 1`.
  - Tile 2 remains at (1,2) and Tile 4 remains at (3,1).
  - Invoking `swapSensorFields(tile1Id, tile1Id)` (same tile) is a clean no-op with zero changes.

### Test Case 2: Database Tile Moving & Grid Normalization (`[TST-UI-154.2]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/database/TrackingViewsDatabaseManagerTest.kt`
* **Preconditions**: Tab seeded with Tile 1 at (1,1), Tile 2 at (1,2), Tile 3 at (2,1).
* **Action A**:
  - Move Tile 2 to a new row: `moveSensorField(tile2Id, targetRow = 1, targetCol = -1)`.
* **Expected Result A**:
  - Tile 2 becomes the only field in row 1.
  - Tile 1 becomes row 2.
  - Tile 3 becomes row 3.
  - Rows are contiguous 1..3 with no empty gaps.
* **Action B**:
  - Move Tile 3 (sole field in row 3) to row 1 column 2: `moveSensorField(tile3Id, targetRow = 1, targetCol = 2)`.
* **Expected Result B**:
  - Row 3 is eliminated.
  - Row 1 contains Tile 2 (1,1) and Tile 3 (1,2).
  - Row 2 contains Tile 1 (2,1).
  - Row numbers remain contiguous 1..2.

### Test Case 3: ViewModel Move Mode State & Dispatch (`[TST-UI-154.3]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingViewModelGridTest.kt`
* **Preconditions**: `TrackingViewModel` initialized with mocked `TrackingViewsRepository`.
* **Action & Expected Results**:
  1. Call `onSelectFieldForMove(fieldState)`: verify `selectedFieldForMove.value` matches `fieldState`.
  2. Call `onSwapFields(selectedId, targetId)`: verify `trackingViewsRepository.swapSensorFields(selectedId, targetId)` is called, and `selectedFieldForMove.value` is cleared to `null`.
  3. Call `onMoveField(selectedId, targetRow, targetCol)`: verify `trackingViewsRepository.moveSensorField(selectedId, targetRow, targetCol)` is called, and `selectedFieldForMove.value` is cleared to `null`.
  4. Call `onCancelMove()`: verify `selectedFieldForMove.value` is cleared to `null` with no repository calls.

### Test Case 4: Compose Grid UI Interaction & Accessibility (`[TST-UI-154.4]`)
* **Scope**: Unit / Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenReorderTest.kt`
* **Preconditions**: `SensorGridScreen` and `SensorFieldView` loaded.
* **Action & Expected Results**:
  1. Verify `SensorFieldView` supports `isSelectedForMove` parameter, rendering primary accent border when true.
  2. Verify `SensorFieldView` displays a move icon button in configuration mode.
  3. Verify `SensorGridScreen` displays the guidance banner when `selectedFieldForMove != null`.
  4. Verify banner contains instruction text and dismiss/cancel button.

### Test Case 5: 9-Language Localization & Specifier Audit (`[TST-UI-154.5]`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/SensorGridLocalizationTest.kt`
* **Goal**: Verify string presence across all 9 locales:
  - `move_tile_banner_instruction`
  - `move_tile_cancel`
* **Expected Result**: 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT; zero missing entries.

### Test Case 6: Clean-Room Regression Suite (`[TST-UI-154.6]`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with 0 regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Class / Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-154.1]` | Unit | `TrackingViewsDatabaseManager.swapSensorFields` | `REQ-UI-200` | Specified |
| `[TST-UI-154.2]` | Unit | `TrackingViewsDatabaseManager.moveSensorField` | `REQ-UI-200` | Specified |
| `[TST-UI-154.3]` | Unit | `TrackingViewModel` (Move Mode State Flow) | `REQ-UI-200` | Specified |
| `[TST-UI-154.4]` | Contract | `SensorGridScreen` & `SensorFieldView` (Banner & Tile Highlight) | `REQ-UI-200` | Specified |
| `[TST-UI-154.5]` | Localization | `SensorGridLocalizationTest` | `REQ-UI-200`, `REQ-UI-106` | Specified |
| `[TST-UI-154.6]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
