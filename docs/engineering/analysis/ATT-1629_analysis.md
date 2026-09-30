# Stage 1 Analysis: ATT-1629 - [Feature] [Cockpit/Grid] Pick & Place Sensor Tile Reordering and Swapping Mode

**Ticket**: [ATT-1629](https://atrainingtracker.atlassian.net/browse/ATT-1629)  
**Sub-task**: [ATT-1699](https://atrainingtracker.atlassian.net/browse/ATT-1699) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.5`  
**Branch**: `feature/ATT-1629`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Problem Statement & Motivation

### Current State
In the `aTrainingTracker` cockpit (`SensorGridScreen.kt`), athletes customize tracking tabs by adding, editing, or deleting sensor fields organized in rows and columns (`TrackingViewsDatabaseManager.ROWS_TABLE`). 

However, there is currently **no mechanism to reorder, move, or swap existing sensor tiles**. If an athlete wishes to adjust their visual hierarchy (for example, moving an experimentally added Heart Rate or Power tile from the bottom of the grid to the prominent top row, or swapping Speed and Cadence):
1. The user must manually delete existing fields.
2. The user must manually recreate each field using the `+` row/column adders.
3. The user must reconfigure sensor types, source devices, display text sizes, and filter parameters (e.g. moving average constants) from scratch.

This friction leads to athlete frustration and accidental configuration loss.

### Prior Failed Approaches (Drag & Drop Pitfall)
Prior explorations into continuous touch drag-and-drop (`detectDragGesturesAfterLongPress`) encountered severe technical limitations in Jetpack Compose:
- **Pointer-Capture Lock**: In Compose, when a child composable captures a drag gesture, parent containers (`Modifier.verticalScroll()`) and sibling drop-targets cannot reliably receive hover or drop events without fragile global offset coordinate tracking.
- **Scroll Competition**: Long-press drags continuously conflict with vertical page scrolling, resulting in stutter, frozen tiles, or dropped frames during active workout configuration.

### Expected Behavior
An intuitive, accessible, and tactile **"Pick & Place" (Tap-to-Move / Tap-to-Swap)** interaction mode:
1. In `ScreenMode.CONFIGURATION`, an athlete selects a tile to move via a long-press on the tile or by tapping a dedicated move button.
2. The selected tile enters an active "Move Mode", indicated by a highlighted primary accent border, subtle background tint, and haptic feedback.
3. A floating, dismissible guidance banner appears at the top of the grid with clear instructions and a cancel button:
   *"Kachel ausgewählt: Tippe auf eine andere Kachel zum Tauschen oder auf ein '+' zum Verschieben. [Abbrechen]"* (with 100% 9-language localization parity).
4. Tapping another existing tile cleanly exchanges (`swap`) the two tiles' positions in the grid layout and SQLite database.
5. Tapping an existing `RowAdder` or `ColAdder` (`+` button) moves the selected tile to that new slot, shifting adjacent indices cleanly without leaving empty rows.
6. Tapping the selected tile again or tapping "[Abbrechen]" cancels move mode with zero side-effects.

---

## 2. Root Cause Analysis (Forensic Investigation)

### Technical Architecture Inspection

1. **Grid Representation (`SensorGridScreen.kt`)**:
   - `SensorGridScreen` receives `TrackingScreenState.fields: List<SensorFieldState>`.
   - Fields are grouped by `fieldState.rowNr` and sorted by `fieldState.colNr`:
     ```kotlin
     val fieldsByRow = state.fields.groupBy { it.rowNr }
     val sortedRows = fieldsByRow.keys.sorted()
     ...
     val fieldsInThisRow = fieldsByRow[rowNr]?.sortedBy { it.colNr } ?: emptyList()
     ```
   - In `ScreenMode.CONFIGURATION`, `RowAdder` composables appear above rows and below the last row (`onAddRow(beforeRow)`). `ColAdder` composables appear before columns and after the last column in each row (`onAddCol(atRow, beforeCol)`).
   - Currently, `SensorFieldView` only exposes `onEdit` (triggered on regular click in `CONFIGURATION` or long-click in `TRACKING`) and `onDelete` (trash icon). It lacks any selection state or move callback.

2. **Action Dispatch Boundary (`GridActions` & `TrackingTabGridContent.kt`)**:
   - `GridActions` interface defines:
     ```kotlin
     interface GridActions {
         fun onEditField(fieldState: SensorFieldState)
         fun onDeleteField(fieldState: SensorFieldState)
         fun onAddRow(beforeRow: Int)
         fun onAddCol(atRow: Int, beforeCol: Int)
     }
     ```
   - Currently, `GridActions` does not provide `onSelectFieldForMove(fieldState)`, `onSwapFields(sourceFieldId, targetFieldId)`, or `onMoveField(sourceFieldId, targetRow, targetCol)`.

3. **Database Architecture (`TrackingViewsDatabaseManager.java`)**:
   - Database table: `TrackingViewsDbHelper.ROWS_TABLE`.
   - Fields are stored with `ROW_ID` (primary key `_id`), `VIEW_ID` (`tabViewId`), `ROW_NR` (1-indexed int), and `COL_NR` (1-indexed int).
   - `insertSensorFieldAt(tabViewId, rowNr, colNr, ...)` shifts existing rows or columns using SQL updates, but there are no atomic methods for:
     - Swapping two fields: `swapSensorFields(long fieldIdA, long fieldIdB)`.
     - Moving an existing field to a new slot with compaction: `moveSensorField(long fieldId, int targetRow, int targetCol)`.
     - Normalizing layout coordinates: `normalizeGrid(SQLiteDatabase db, long tabViewId)`.

4. **UI State & Repository Synchronization (`TrackingViewModel.kt`, `TrackingViewsRepository.kt`)**:
   - `TrackingViewsRepository` exposes `configUpdateTrigger: MutableStateFlow<Int>`. Updating database records and incrementing `configUpdateTrigger` causes `TrackingViewModel` to automatically re-read the layout via `getTrackingViewsFlow()` and push a fresh `TrackingScreenState` to Compose.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Add `swapSensorFields(long fieldIdA, long fieldIdB)` and `moveSensorField(long fieldId, int targetRow, int targetCol)` to `TrackingViewsDatabaseManager.java` wrapped in an atomic SQLite transaction with layout normalization.
  2. Expose `swapSensorFields` and `moveSensorField` across `TrackingViewsRepository.kt` and `TrackingViewModel.kt`.
  3. Introduce move selection state in `TrackingViewModel.kt` (`selectedFieldForMove: StateFlow<SensorFieldState?>`, `onSelectFieldForMove`, `onCancelMove`).
  4. Update `SensorFieldView.kt`:
     - In `ScreenMode.CONFIGURATION`: long-press selects the tile for move; add a reorder icon button next to the delete button.
     - Add visual state indication: primary accent border (`BorderStroke(2.dp, MaterialTheme.colorScheme.primary)`) and subtle surface elevation/container tint when selected.
  5. Update `SensorGridScreen.kt`:
     - Display a top guidance banner when `selectedFieldForMove != null` with localized instructions and a Cancel button.
     - When a tile is selected: tapping another tile triggers swap; tapping `RowAdder` or `ColAdder` moves the selected tile to that position instead of opening the add sensor dialog.
     - Tapping the selected tile again cancels selection.
  6. Provide 100% 9-language localization parity for all instruction banner strings (`move_tile_banner_instruction`, `move_tile_cancel`).
  7. Add comprehensive unit tests in `TrackingViewsDatabaseManagerTest.kt`, `TrackingViewModelGridTest.kt`, and `SensorGridScreenReorderTest.kt`.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. No continuous pointer drag-and-drop implementation (deliberately avoided due to Compose pointer-capture and scroll container conflicts).
  2. No changes to sensor telemetry, filtering math, or background BLE collection.
  3. No cross-tab moving (moving sensor tiles between different tracking tabs is out of scope).
  4. No schema migration in `TrackingViewsDbHelper` (`ROW_NR` and `COL_NR` already exist).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Audit Statement**:
  - **Net-new requirement only**: **`REQ-UI-200`** (*Cockpit Sensor Grid Pick & Place Reordering & Swapping Architecture*).
  - No existing requirements in `docs/requirements.md` are modified or regressed.
  - Complements and honors:
    - `REQ-UI-103`: *Customizable Cockpits* (preserves full freedom to customize visualization dashboards).
    - `REQ-UI-111`: *Adaptive layout for tab configuration settings*.
    - `REQ-UI-181`: *Ultra-Large Cockpit Typography Extensions* (preserves all `ViewSize` properties during swap/move).
    - `REQ-UI-189` / `REQ-UI-196`: *BottomSheet and Drag Handle styling*.
    - `REQ-UI-106` / `REQ-UI-122`: *Global Localization & Format Compliance*.

---

## 5. Architectural Strategy & High-Level Solution

### Component Diagram & Flow

```
[Athlete in CONFIGURATION Mode]
       │
       ├─ Long-press on SensorTile (or tap Move icon)
       │
       ▼
[TrackingViewModel] ──> selectedFieldForMove = fieldState
       │
       ├─ Emits selectedFieldForMove StateFlow
       ▼
[SensorGridScreen]
       ├─ Renders Guidance Banner ("Kachel ausgewählt...")
       ├─ Highlights Selected Tile (Primary Accent Border)
       │
       ├── Case A: Tap another Tile B ──────> viewModel.onSwapFields(selected.id, target.id)
       ├── Case B: Tap RowAdder / ColAdder ─> viewModel.onMoveField(selected.id, row, col)
       └── Case C: Tap Cancel / Same Tile ──> viewModel.onCancelMove()
                                                      │
                                                      ▼
                                        [TrackingViewsRepository]
                                                      │
                                                      ▼
                                       [TrackingViewsDatabaseManager]
                                                      │
                                            (Atomic SQLite Tx)
                                       - swapSensorFields / moveSensorField
                                       - normalizeGrid(viewId)
                                                      │
                                                      ▼
                                       configUpdateTrigger++ ──> Recompose Grid
```

### Key Technical Details

1. **Atomic Swapping (`TrackingViewsDatabaseManager.java`)**:
   ```java
   public void swapSensorFields(long fieldIdA, long fieldIdB) { ... }
   ```
   Exchanges `(ROW_NR, COL_NR)` between `fieldIdA` and `fieldIdB` within an active transaction.

2. **Atomic Move & Grid Normalization (`TrackingViewsDatabaseManager.java`)**:
   ```java
   public void moveSensorField(long fieldId, int targetRow, int targetCol) { ... }
   ```
   Parks the field, compacts the origin row, removes empty rows, opens space at target `(targetRow, targetCol)`, assigns target coordinates, and calls `normalizeGrid(db, tabViewId)` to guarantee contiguous row numbers (`1..R`) and contiguous column numbers (`1..C`).

3. **Compose UI Integration (`SensorGridScreen.kt` & `SensorFieldView.kt`)**:
   - `SensorFieldView` gains `isSelectedForMove: Boolean = false`, `onStartMove: () -> Unit = {}`.
   - In `ScreenMode.CONFIGURATION`, when `selectedFieldForMove != null`:
     - Clicking a tile calls `if (field == selected) onCancelMove() else onSwapFields(selected.id, field.id)`.
     - Clicking `RowAdder` calls `onMoveField(selected.id, targetRow, -1)`.
     - Clicking `ColAdder` calls `onMoveField(selected.id, targetRow, targetCol)`.
     - When `selectedFieldForMove == null`, existing behavior (`onEdit`, `onAddRow`, `onAddCol`) is 100% preserved.

4. **Localization (9 Locales)**:
   - `move_tile_banner_instruction`:
     - EN: "Tile selected: Tap another tile to swap, or tap '+' to move."
     - DE: "Kachel ausgewählt: Tippe auf eine andere Kachel zum Tauschen oder auf '+' zum Verschieben."
     - ES: "Casilla seleccionada: Toca otra casilla para intercambiar o '+' para mover."
     - FR: "Tuile sélectionnée : Touchez une autre tuile pour échanger ou '+' pour déplacer."
     - IT: "Riquadro selezionato: Tocca un altro riquadro per scambiare o '+' per spostare."
     - JA: "タイルを選択中: 別のタイルをタップして入れ替え、または「+」をタップして移動します。"
     - NL: "Tegel geselecteerd: Tik op een andere tegel om te ruilen of op '+' om te verplaatsen."
     - PL: "Wybrano kafel: Dotknij innego kafla, aby zamienić, lub '+' aby przenieść."
     - PT: "Mosaico selecionado: Toque em outro mosaico para trocar ou em '+' para mover."
   - `move_tile_cancel`:
     - EN: "Cancel" / DE: "Abbrechen" / ES: "Cancelar" / FR: "Annuler" / IT: "Annulla" / JA: "キャンセル" / NL: "Annuleren" / PL: "Anuluj" / PT: "Cancelar"

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing sensor addition, deletion, and editing workflows.
  2. Single-thread SQLite concurrency in `TrackingViewsDatabaseManager` preserved.
  3. No empty row gaps or duplicate coordinates permitted in `ROWS_TABLE`.
  4. Normal active tracking mode (`ScreenMode.TRACKING`) interaction MUST remain 100% untouched.
  5. 9-language localization parity maintained across all new strings.

* **Risk Rating**: **LOW**
  - All modifications are strictly additive within `ScreenMode.CONFIGURATION`.
  - Database schema is unaltered.
  - Transactions and coordinate normalization eliminate layout corruption.
