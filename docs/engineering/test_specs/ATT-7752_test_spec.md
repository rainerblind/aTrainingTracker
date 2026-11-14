# Stage 2: Requirement & Test Specification - ATT-7752: Equipment-sensor matrix & DB: Track Front/Rear wheel sensor mounting position with selection dialog & dual-sensor user guidance

**Ticket**: [ATT-7752](https://rainerblind.atlassian.net/browse/ATT-7752)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.6`  
**Requirement Mapping**: `REQ-SEN-029` (*Front/Rear Wheel Speed Sensor Mounting Position Tracking, Selection Dialog & Dual-Sensor Guidance*)  
**Test Spec ID**: `TST-SEN-029`  
**Branch**: `feat/ATT-7752`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Requirement Specification (REQ-SEN-029)

### 1.1 Problem Statement & Rationale
`EquipmentDbHelper.LINKS` and `EquipmentSensorMatrixScreen.kt` currently record only binary checkbox links. Dual-speed odometry fusion (ATT-131 / ATT-7751) and dual-wheel slow-puncture detection (iTPMS, ATT-7753) require knowing which sensor is on the front wheel (`FRONT`) and which is on the rear wheel (`REAR`). Contextual guidance in the matrix and device dialog must also educate users on the physical advantages of 1 vs. 2 wheel sensors over smartphone GPS.

### 1.2 Attributes
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit

### 1.3 Functional & Architectural Requirements
1. `EquipmentDbHelper` SHALL increment `DB_VERSION` to 4, declare `WheelPosition` enum (`FRONT`, `REAR`, `UNSPECIFIED`), and execute `ALTER TABLE Links ADD COLUMN wheel_position TEXT DEFAULT 'UNSPECIFIED'` in `onUpgrade(3, 4)`. Existing links SHALL retain 100% data integrity defaulting to `UNSPECIFIED`.
2. `EquipmentRepository` and `EquipmentViewModel` SHALL expose typed wheel position read/write APIs, emitting updates via `equipmentLinksChanged`.
3. *Bike Guardrail*: If bike $B$ already links a sensor with position `FRONT` and a second sensor on $B$ is set to `FRONT`, the system SHALL offer a clean swap or reassign the existing sensor to `REAR`/`UNSPECIFIED` so two sensors on the same bike are never simultaneously marked `FRONT` (and similarly for `REAR`).
4. In `EquipmentSensorMatrixScreen.kt`, tapping a wheel speed sensor (`BIKE_SPEED`, `BIKE_SPEED_AND_CADENCE`) cell SHALL present an interactive selection dialog (Front, Rear, Unspecified, Unlink) and render a compact badge (`V`/`H` or `F`/`R`) within `SENSOR_COLUMN_WIDTH` (88 dp, `Rule 84`). Non-wheel sensors SHALL retain standard 1-tap checkbox toggles.
5. In-app guidance card/dialog in `EquipmentSensorMatrixScreen.kt` and `EditDeviceDialog.kt` plus `docs/guide/{en,de}/03-equipment-sensor-matrix.md` SHALL explain 1 vs. 2 wheel sensor benefits with 100% 9-language localization parity.

### 1.4 Acceptance Criteria (Given-When-Then)
* *Given* SQLite database `Equipment.db` at version 3, *When* upgraded to version 4, *Then* existing links return `UNSPECIFIED` and new links with `FRONT`/`REAR` persist correctly.
* *Given* `EquipmentSensorMatrixScreen.kt`, *When* a wheel speed sensor cell is tapped, *Then* a mounting position selection dialog opens, and upon selection a non-clipping badge (`V`/`H` / `F`/`R`) renders in the cell.
* *Given* a bike with a `FRONT` wheel sensor, *When* user assigns `FRONT` to a second wheel speed sensor on the same bike, *Then* the system detects the conflict and offers to swap or reassign cleanly.
* *Given* the sensor matrix or `EditDeviceDialog.kt`, *When* info guidance is opened, *Then* localized text across all 9 languages explains 1 vs. 2 wheel sensor advantages.

### 1.5 System Invariants
Zero regression for non-wheel sensors (HRM, Cadence, Radar, Power); 100% backward compatibility of existing links; 100% 9-language localization parity; 100% unit test pass rate.

---

## 2. Test Specification (TST-SEN-029)

### Test Case 1: `testDbUpgrade_v3ToV4_preservesLinksAndAddsWheelPosition` (`TST-SEN-029.1`)
* **Scope**: Unit / SQLite Migration Test (`EquipmentDbHelperMigrationTest.kt`)
* **Preconditions**: Create temp SQLite DB with v3 schema and legacy `LINKS` rows.
* **Action**: Run `EquipmentDbHelper.onUpgrade(db, 3, 4)`.
* **Expected Result**: Column `wheel_position` exists; legacy rows evaluate as `UNSPECIFIED`; inserts with `FRONT`/`REAR` persist.

### Test Case 2: `testWheelPosition_duplicateGuardrail_swapsCorrectly` (`TST-SEN-029.2`)
* **Scope**: Unit / Repository Test (`EquipmentRepositoryWheelPositionTest.kt`)
* **Preconditions**: Bike A linked to sensor 1 as `FRONT`.
* **Action**: Link sensor 2 to bike A as `FRONT` with swap enabled.
* **Expected Result**: Sensor 2 is `FRONT`, sensor 1 is reassigned to `REAR`/`UNSPECIFIED`, and `equipmentLinksChanged` emits.

### Test Case 3: `testMatrix_wheelPositionDialogAndBadgeRendering` (`TST-SEN-029.3`)
* **Scope**: Unit / Compose UI Test (`EquipmentSensorMatrixWheelPositionTest.kt`)
* **Preconditions**: Render `EquipmentSensorMatrixScreen` with bike and `BIKE_SPEED` sensor.
* **Action**: Click cell, select *Vorderrad*.
* **Expected Result**: Dialog opens and closes; badge `V`/`F` is rendered without text clipping within 88 dp (`Rule 84`).

### Test Case 4: 9-Language Localization & Format Specifier Parity (`TST-SEN-029.4`)
* **Scope**: Localization Parity Test (`TranslationParityTest.kt`)
* **Expected Result**: 100% parity across all 9 locales for `equipment_wheel_position_*` and `equipment_guidance_*` strings.

### Test Case 5: Tier 1 Targeted Regression Suite (`TST-SEN-029.5`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.Equipment*" --tests "com.atrainingtracker.trainingtracker.ui.equipment.*"`
* **Expected Result**: 100% pass rate with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SEN-029.1` | Unit (DB) | `EquipmentDbHelper.onUpgrade` | `REQ-SEN-029.1` | Specified |
| `TST-SEN-029.2` | Unit (Repo) | `EquipmentRepository.setDeviceLink` | `REQ-SEN-029.2` | Specified |
| `TST-SEN-029.3` | Unit (UI) | `EquipmentSensorMatrixScreen` | `REQ-SEN-029.3` | Specified |
| `TST-SEN-029.4` | Localization | `TranslationParityTest` | `REQ-SEN-029.4`, `REQ-LOC-001` | Specified |
| `TST-SEN-029.5` | Regression | Targeted Gradle Suite | `REQ-PRO-001` | Specified |
