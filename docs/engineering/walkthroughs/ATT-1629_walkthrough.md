# Stage 5 Verification & Walkthrough: ATT-1629

## 1. Ticket Information
- **Parent Ticket**: [ATT-1629](https://atrainingtracker.atlassian.net/browse/ATT-1629) - `[Feature] [Cockpit/Grid] Pick & Place Sensor Tile Reordering and Swapping Mode`
- **Subtask**: [ATT-1703](https://atrainingtracker.atlassian.net/browse/ATT-1703) - `Stage 5: Verification & Clean-Room Regression`
- **Fix Version**: `V4.9.38`
- **Target Branch**: `sprint/2026-40.5`
- **Feature Branch**: `feature/ATT-1629`
- **Requirements Traceability**: `REQ-UI-200`
- **Test Traceability**: `TST-UI-154`

---

## 2. Executive Summary of Changes
Implemented a tactile, accessible "Pick & Place" (Tap-to-Move / Tap-to-Swap) reordering architecture in the tracking cockpit configuration mode (`ScreenMode.CONFIGURATION`), eliminating the drag-and-drop pointer-capture conflicts and configuration loss:

1. **Database Layer (`TrackingViewsDatabaseManager.java`)**:
   - `swapSensorFields(long fieldIdA, long fieldIdB)`: Atomically exchanges row and column coordinates in `TrackingViewsDbHelper.ROWS_TABLE` within a single SQLite transaction. Self-cancels if fieldIdA == fieldIdB.
   - `moveSensorField(long sensorFieldId, int targetRow, int targetCol)`: Temporarily parks the field at `(-999, -999)`, compacts remaining fields in the old row, shifts remaining rows down if the origin row becomes empty, opens an insertion slot at the target row/col (or creates a new row when `targetCol == -1`), updates coordinates, and calls `normalizeGrid`.
   - `normalizeGrid(SQLiteDatabase db, long tabViewId)`: Iterates through distinct sorted rows and contiguous columns, normalizing indices `1..R` and `1..C` with negative offsets to avoid unique constraint collisions. Added automatic self-healing normalization to `deleteSensorField`.
2. **Repository Layer (`TrackingViewsRepository.kt`)**:
   - Added `swapSensorFields` and `moveSensorField` suspend functions executing on `Dispatchers.IO` and triggering reactive UI invalidation via `configUpdateTrigger` on `Dispatchers.Main`.
3. **ViewModel Layer (`TrackingViewModel.kt`)**:
   - Introduced `selectedFieldForMove: StateFlow<SensorFieldState?>` state tracking.
   - Added `onSelectFieldForMove`, `onCancelMove`, `onSwapFields`, and `onMoveField`.
   - Bound lifecycle safety invariants: transitioning screen mode to `ScreenMode.TRACKING` or deleting the currently selected field automatically clears move mode.
4. **Sensor Field View (`SensorFieldView.kt`)**:
   - Added `isSelectedForMove: Boolean = false` parameter applying `BorderStroke(2.dp, MaterialTheme.colorScheme.primary)` visual emphasis.
   - In `ScreenMode.CONFIGURATION`: long-press triggers move mode (`onStartMove`), and added a dedicated move/swap icon button (`Icons.Default.SwapHoriz`) next to the delete button.
5. **Sensor Grid Screen & Integration (`SensorGridScreen.kt`, `TrackingTabGridContent.kt`)**:
   - Extended `GridActions` with move selection, swap, move, and cancel callbacks with default implementations to ensure backwards compatibility.
   - Pinned guidance banner at top of the grid when `selectedFieldForMove != null` with localized instruction text `R.string.move_tile_banner_instruction` and a cancel button `R.string.move_tile_cancel`.
   - Routed tile clicks to swap with the selected tile (or cancel if tapping the same tile).
   - Routed `RowAdder` and `ColAdder` clicks to reposition the tile into that slot without opening the new-sensor creation dialog.
6. **9-Language Localization**:
   - Added `move_tile_banner_instruction` and `move_tile_cancel` across all 9 application locales: English (values), German (values-de), Spanish (values-es), French (values-fr), Italian (values-it), Japanese (values-ja), Dutch (values-nl), Polish (values-pl), Portuguese (values-pt).

---

## 3. Test & Verification Results

### A. Targeted Unit Test Suite
- Test Files:
  - [TrackingViewsDatabaseManagerTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/database/TrackingViewsDatabaseManagerTest.kt) (`TST-UI-154.1`, `TST-UI-154.2`)
  - [TrackingViewModelGridTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingViewModelGridTest.kt) (`TST-UI-154.3`)
  - [SensorGridScreenReorderTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenReorderTest.kt) (`TST-UI-154.4`)
  - [SensorGridLocalizationTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/localization/SensorGridLocalizationTest.kt) (`TST-UI-154.5`)
- Results:
  - `TrackingViewsDatabaseManagerTest`:
    - `testSwapSensorFields_sameId_isNoOp`: PASSED
    - `testSwapSensorFields_distinctIds_swapsCoordinatesAtomically`: PASSED
    - `testMoveSensorField_parksCompactsAndNormalizes`: PASSED
    - `testMoveSensorField_newRowRequested_createsRowAndNormalizes`: PASSED
  - `TrackingViewModelGridTest`:
    - `testSelectFieldForMove_andCancelMove`: PASSED
    - `testSwapFields_dispatchesToRepositoryAndClearsSelection`: PASSED
    - `testMoveField_dispatchesToRepositoryAndClearsSelection`: PASSED
    - `testScreenModeChangeToTracking_cancelsActiveMoveSelection`: PASSED
    - `testDeleteSensorField_clearsMoveSelectionIfSameFieldDeleted`: PASSED
  - `SensorGridScreenReorderTest`:
    - `testGridActions_defaultImplementationsDoNotThrow`: PASSED
    - `testGridActions_customImplementationsTrackCalls`: PASSED
  - `SensorGridLocalizationTest`:
    - `testMoveTileBannerInstructionParityAcrossAllLocales`: PASSED (100% across 9 locales)
    - `testMoveTileCancelParityAcrossAllLocales`: PASSED (100% across 9 locales)

### B. Clean-Room Full Suite Regression
- Command: `./gradlew testDebugUnitTest`
- Outcome: **BUILD SUCCESSFUL in 2m 51s**
- Pass Rate: **100%** (0 errors, 0 regressions across all modules).

---

## 4. Traceability Matrix

| Requirement | Test Specification | Verification Status | Deliverable / Test Target |
| :--- | :--- | :--- | :--- |
| `REQ-UI-200` | `TST-UI-154.1` | **Verified** | `TrackingViewsDatabaseManagerTest.kt` |
| `REQ-UI-200` | `TST-UI-154.2` | **Verified** | `TrackingViewsDatabaseManagerTest.kt` |
| `REQ-UI-200` | `TST-UI-154.3` | **Verified** | `TrackingViewModelGridTest.kt` |
| `REQ-UI-200` | `TST-UI-154.4` | **Verified** | `SensorGridScreenReorderTest.kt` |
| `REQ-UI-200` | `TST-UI-154.5` | **Verified** | `SensorGridLocalizationTest.kt` |
| `REQ-UI-200` | `TST-UI-154.6` | **Verified** | Clean-room full test suite regression |

---

## 5. Invariants & Safety Review
- **Thread Safety**: All database updates run inside SQLite transactions on `Dispatchers.IO`. UI notifications emit on `Dispatchers.Main`.
- **Telemetry Immutability**: Normal active tracking mode (`ScreenMode.TRACKING`) clicks, long-clicks, and background telemetry subscriptions remain completely untouched.
- **Zero Schema Migration**: Grid reordering operates entirely within existing columns `(RowNr, ColNr)` in `TrackingViewsDbHelper.ROWS_TABLE`.
