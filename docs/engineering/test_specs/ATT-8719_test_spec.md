# Stage 2: Requirement & Test Specification - ATT-8719

**Ticket**: [ATT-8719](https://rainerblind.atlassian.net/browse/ATT-8719) (`2 SP`) | **Sub-task**: Pending (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values*)  
**Target Release**: `V5.0.0` | **Sprint**: `sprint/2026-11-17_03_Nacht` | **Branch**: `feature/ATT-8719`  
**Requirement Mapping**: `REQ-SEN-031` (refining `REQ-SEN-029`, `REQ-UI-149`) | **Test Spec ID**: `TST-SEN-031` | **Date**: 2026-11-18

---

## 1. Requirement Specification (`REQ-SEN-031` `[Planned]`)
* **Attributes**: Priority: `Medium` | Type: `Functional` | Verification Method: `Unit`
* **Implementation**: `DeviceData.kt`, `RawDeviceDataProvider.kt`, `DeviceDataRepository.kt`, `EditDeviceViewModel.kt`, `EditDeviceDialog.kt`, `WheelPositionSelectionDialog.kt`, `EquipmentRepository.kt`, `strings_equipment.xml`
* **REQ-SEN-031.1 (Hydration & Display in `EquipmentSection`)**: `DeviceRawData` and `DeviceUiData` SHALL expose `linkedEquipmentWheelPositions: Map<String, WheelPosition> = emptyMap()` hydrated from `EquipmentDbHelper`. For `BIKE_SPEED` and `BIKE_SPEED_AND_CADENCE`, `EditDeviceDialog.kt` (`EquipmentSection`) SHALL display each linked bike's localized `WheelPosition` (`Front` / `Rear` / `Unspecified` via 9-locale `strings_equipment.xml`) in both the summary field and bike-linking sheet.
* **REQ-SEN-031.2 (Reused `WheelPositionSelectionDialog`, Conflict Preview & Draft Isolation)**: Tapping or linking a bike in the `EquipmentSection` sheet for a wheel speed sensor SHALL open the reused stateless `WheelPositionSelectionDialog` (displaying a conflict indicator/swap preview if another wheel sensor on that bike already holds `FRONT`/`REAR`) and stage the chosen `WheelPosition` strictly inside `EditDeviceViewModel` draft state (`_deviceSnapshot`). Tapping `Cancel` on the sheet or `EditDeviceDialog` MUST discard all draft link/position edits with zero `Equipment.db` mutations.
* **REQ-SEN-031.3 (Atomic `Save` Persistence & `swapConflict = true`)**: On `Save`, `DeviceDataRepository.updateDevice()` SHALL detect differences in `linkedEquipment` or `linkedEquipmentWheelPositions`, unlink removed bikes, and persist linked wheel sensors via `EquipmentRepository.setWheelSensorLink(equipmentId, sensorId, position, swapConflict = true)`.
* **REQ-SEN-031.4 (Non-Wheel Sensor Isolation & 9-Locale Parity)**: Non-wheel sensors (`HRM`, `BIKE_POWER`, `BIKE_RADAR`, `BIKE_CADENCE`, `RUN_SPEED`, smartphone sensors) MUST strictly retain the existing simple checkbox selection UI. Existing 9-locale `strings_equipment.xml` keys (`equipment_wheel_position_*`) SHALL be reused, with 100% 9-locale parity (`values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`) for any new conflict/swap hint string.
* **Given-When-Then Acceptance Criteria**:
  * *Given* a `BIKE_SPEED` or `BIKE_SPEED_AND_CADENCE` sensor linked to Bike A (`FRONT`), *When* `EditDeviceDialog` opens, *Then* `EquipmentSection` summary and bike sheet render Bike A with its localized `Front` position.
  * *Given* Bike B already has another sensor at `REAR`, *When* the user taps Bike B in the sheet, previews the conflict/swap in `WheelPositionSelectionDialog`, selects `REAR`, and confirms sheet `OK` + dialog `Save`, *Then* `setWheelSensorLink(..., swapConflict = true)` commits `REAR` and swaps the conflicting sensor; *When* the user taps `Cancel` on sheet or dialog instead, *Then* zero DB writes occur.
  * *Given* `HRM`, `BIKE_POWER`, `BIKE_RADAR`, `BIKE_CADENCE`, or `RUN_SPEED`, *When* the sheet opens, *Then* simple checkboxes render with no `WheelPositionSelectionDialog`.
* **Chesterton's Fence Audit (`REQ-PRO-022`)**: Refines `REQ-SEN-029` (`4edcda86`, `ATT-7752`) and `REQ-UI-149`; `ATT-7752` added `WheelPosition` in `EquipmentSensorMatrixScreen` and preserved DB positions in `setEquipmentLinks()`, deferring `EditDeviceDialog`; preserves `SaveCancel` draft isolation (`Rule 68`), `swapConflict = true` guardrail, `equipmentLinksChanged` sync, and non-wheel simple checkboxes.

---

## 2. Test Specification (`TST-SEN-031` `[Proposed]`)
* **`TST-SEN-031.1` (`EditDeviceViewModelTest.kt`, `DeviceDataRepositoryEquipmentSyncTest.kt`)**: Unit tests verifying `EditDeviceViewModel` draft staging of `linkedEquipment` + `linkedEquipmentWheelPositions` (zero repository/DB calls before `saveChanges()`, clean discard on `Cancel`), and `DeviceDataRepository.updateDevice()` persisting position-only and link+position changes via `EquipmentRepository.setWheelSensorLink(..., swapConflict = true)` and unlinking removed bikes.
* **`TST-SEN-031.2` (`EditDeviceEquipmentWheelPositionTest.kt`)**: Strictly offline Robolectric `createComposeRule()` UI test (`Rule 5`, `REQ-PRO-049.1` zero `src/main` reads) for `BIKE_SPEED` and `BIKE_SPEED_AND_CADENCE`: verify localized `Front`/`Rear`/`Unspecified` summary + sheet labels, `WheelPositionSelectionDialog` opening on bike link/tap with conflict/swap preview, sheet `OK` updating draft state, and sheet `Cancel` discarding draft changes.
* **`TST-SEN-031.3` (`EditDeviceEquipmentWheelPositionTest.kt`)**: Strictly offline Compose UI test verifying `HRM`, `BIKE_POWER`, `BIKE_RADAR`, `BIKE_CADENCE`, and `RUN_SPEED` strictly render existing simple checkbox rows without wheel-position labels or `WheelPositionSelectionDialog`.
* **`TST-SEN-031.4` (`EditDeviceEquipmentWheelPositionTest.kt`, `TranslationParityTest.kt`)**: Light/Dark `@Preview` screenshot PNG capture & visual inspection (`Rule 8.5`), 9-locale `strings_equipment.xml` parity check, and targeted Gradle test execution (`./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.banalservice.ui.devices.editdevice.*" --tests "com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepositoryEquipmentSyncTest"`).

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SEN-031.1` | Unit / MockK | `EditDeviceViewModel`, `DeviceDataRepository` | `REQ-SEN-031.1`, `REQ-SEN-031.2`, `REQ-SEN-031.3` | Proposed |
| `TST-SEN-031.2` | Compose UI (Offline) | `EditDeviceDialog` (`EquipmentSection`), `WheelPositionSelectionDialog` | `REQ-SEN-031.1`, `REQ-SEN-031.2` | Proposed |
| `TST-SEN-031.3` | Compose UI (Offline) | `EditDeviceDialog` non-wheel sensors | `REQ-SEN-031.4` | Proposed |
| `TST-SEN-031.4` | Preview PNG + Parity | Light/Dark `@Preview` (`Rule 8.5`), 9-locale `strings_equipment.xml` | `REQ-SEN-031.1`, `REQ-SEN-031.4`, `REQ-PRO-049.1` | Proposed |
