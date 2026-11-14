# Stage 3: Implementation Plan - ATT-7752: Equipment-sensor matrix & DB: Track Front/Rear wheel sensor mounting position with selection dialog & dual-sensor user guidance

**Ticket**: [ATT-7752](https://rainerblind.atlassian.net/browse/ATT-7752)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.6`  
**Requirement Mapping**: `REQ-SEN-029`  
**Test Mapping**: `TST-SEN-029`  
**Branch**: `feat/ATT-7752`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Description & Background
`EquipmentDbHelper.LINKS` and `EquipmentSensorMatrixScreen.kt` only support binary links between bikes and sensors. To support multi-sensor speed fusion (ATT-131 / ATT-7751), per-wheel circumference tracking, and dual-wheel iTPMS slow-puncture detection (ATT-7753), the database and UI must record which sensor is mounted on the front wheel (`FRONT`) and rear wheel (`REAR`). Contextual in-app guidance and documentation must also clarify why 1 or 2 wheel sensors complement GPS.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-SEN-029` (*Front/Rear Wheel Speed Sensor Mounting Position Tracking, Selection Dialog & Dual-Sensor Guidance*)
* **Test Mapping**: `TST-SEN-029` (`TST-SEN-029.1`..`TST-SEN-029.6`)

---

## 3. System Invariants & Preserved Behavior
1. **Backward Compatibility**: Existing database links default to `WheelPosition.UNSPECIFIED`; no link data is dropped.
2. **Non-Wheel Sensor Invariance**: Heart rate, cadence, power, and radar sensors retain standard 1-tap checkbox toggles.
3. **No Database Leaks**: Repositories expose immutable models; raw SQLite cursors are never leaked.
4. **Visual Layout Invariance**: Badges fit strictly within `SENSOR_COLUMN_WIDTH` (88 dp) with zero text clipping (`Rule 84`).

---

## 4. Proposed Architectural Changes
* **Database Layer (`EquipmentDbHelper.java`)**: Bump `DB_VERSION = 4`. Non-destructive `onUpgrade(3, 4)`: `ALTER TABLE Links ADD COLUMN wheel_position TEXT DEFAULT 'UNSPECIFIED'`. Introduce typed `WheelPosition` enum (`FRONT`, `REAR`, `UNSPECIFIED`).
* **Repository & ViewModel Layer (`EquipmentRepository.kt`, `EquipmentViewModel.kt`)**: Map `wheel_position` to `EquipmentItem.linkedDeviceWheelPositions`. Provide bike duplicate guardrails to swap or reassign when two sensors on the same bike are assigned the same wheel position.
* **UI Layer (`EquipmentSensorMatrixScreen.kt`)**: Tapping a wheel speed sensor (`BIKE_SPEED`, `BIKE_SPEED_AND_CADENCE`) triggers `WheelPositionSelectionDialog`. Display localized badge (`V`/`H` or `F`/`R`) alongside checkbox. Display collapsible guidance card in matrix and device dialogs.
* **UI Consistency (`docs/design_guidelines.md`)**:
  * Reference Screen: `EquipmentSensorMatrixScreen.kt`, `EditDeviceDialog.kt`.
  * Dialog: Material 3 `AlertDialog` / `ModalBottomSheet` with localized action items.
  * Tokens: `MaterialTheme.colorScheme.primaryContainer`, `labelSmall`, 88 dp column constraints.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Database Schema & Migration (`REQ-SEN-029.1`, `TST-SEN-029.1`)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/database/WheelPosition.kt`, `EquipmentDbHelper.java`
* Changes: Define `WheelPosition` enum. Bump `DB_VERSION = 4`. Update `CREATE_LINKS_TABLE` and `onUpgrade`. Add helper methods `getDeviceWheelPositionsForEquipment` and `setDeviceLink(..., wheelPosition)`.

### Step 2: Repository & ViewModel Guardrails (`REQ-SEN-029.2`, `TST-SEN-029.2`)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/repositories/EquipmentRepository.kt`, `EquipmentViewModel.kt`
* Changes: Populate `EquipmentItem.linkedDeviceWheelPositions`. Add `setWheelSensorLink` with bike duplicate swap/reassign guardrail.

### Step 3: Interactive Dialog & Badge Rendering (`REQ-SEN-029.3`, `TST-SEN-029.3`)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentSensorMatrixScreen.kt`, `WheelPositionSelectionDialog.kt`
* Changes: Launch selection dialog on wheel speed cell tap; render compact `V`/`H` (`F`/`R`) badge within 88 dp.

### Step 4: Guidance Cards & 9-Language Localization (`REQ-SEN-029.4`, `TST-SEN-029.4`)
* Files: `app/src/main/res/values*/strings_equipment.xml`, `WheelSensorGuidanceDialog.kt`, `docs/guide/{en,de}/03-equipment-sensor-matrix.md`
* Changes: Add localized explanations for 1 vs 2 wheel sensors; update user guides.

### Step 5: Verification & Unit Tests (`TST-SEN-029.5`, `TST-SEN-029.6`)
* Run: `python3 tools/parallel_sprint_lock.py -- ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.Equipment*" --tests "com.atrainingtracker.trainingtracker.ui.equipment.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`
* Verify 100% pass rate.

---

## 6. Verification & Rollback Plan
* **Verification**: Real SQLite migration tests, repository guardrail tests, translation parity, and full unit test suite.
* **Rollback**: Atomic revert of `feat/ATT-7752` branch before sprint squash-merge.
