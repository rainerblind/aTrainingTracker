# Stage 3: Implementation Plan - ATT-8719: EditDeviceDialog Wheel Mounting Position Display, Draft Selection & Conflict Swap

**Ticket**: [ATT-8719](https://rainerblind.atlassian.net/browse/ATT-8719) (`2 SP`) | **Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-17_03_Nacht` | **Branch**: `feature/ATT-8719`  
**Requirement Mapping**: `REQ-SEN-031` (`REQ-SEN-031.1`–`.4`, refining `REQ-SEN-029`, `REQ-UI-149`) | **Test Mapping**: `TST-SEN-031`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-18

---

## 1. Problem Description & Background
* `EditDeviceDialog.kt` (`EquipmentSection`, `:249-348`) displays only plain bike names and binary checkboxes for `DeviceType.BIKE_SPEED` and `DeviceType.BIKE_SPEED_AND_CADENCE`, omitting `WheelPosition` (`FRONT`/`REAR`/`UNSPECIFIED`) in both the summary field and bike-linking sheet. Meanwhile, `DeviceDataRepository.updateDevice()` (`:379-384`) delegates solely to `EquipmentDbHelper.setEquipmentLinks()` when `linkedEquipment` names change, missing position-only edits and `EquipmentRepository.setWheelSensorLink(..., swapConflict = true)` conflict resolution.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-SEN-031` (`REQ-SEN-031.1`–`REQ-SEN-031.4`) in `docs/requirements/req_tracking_cockpit.md`
* **Test Mapping**: `TST-SEN-031` (`TST-SEN-031.1`–`TST-SEN-031.4`) in `docs/tests/tst_tracking_cockpit.md`

---

## 3. System Invariants & Preserved Behavior
1. **Strict `SaveCancel` Draft Isolation**: Editing links or `WheelPosition` in `WheelPositionSelectionDialog` or the `EquipmentSection` sheet MUST stay in `EditDeviceViewModel` draft memory; tapping `Cancel` on either sheet or `EditDeviceDialog` MUST NOT mutate `Equipment.db`.
2. **Conflict Guardrail & Sync**: On `Save`, `EquipmentRepository.setWheelSensorLink(eqId, sensorId, pos, swapConflict = true)` swaps/reassigns conflicting sensors on the same bike and emits `equipmentLinksChanged`.
3. **Non-Wheel Sensor Isolation & Schema**: `HRM`, `BIKE_POWER`, `BIKE_RADAR`, `BIKE_CADENCE`, `RUN_SPEED`, and smartphone sensors retain the simple checkbox UI; `Equipment.db` v4 schema and `DeviceItem.kt` card rendering stay unchanged.
4. **Source Compatibility**: New properties on `DeviceRawData`/`DeviceUiData` and `WheelPositionSelectionDialog` default safely (`emptyMap()`, `null`) so existing callers and tests compile untouched.

---

## 4. Proposed Architectural Changes & UI Consistency (`docs/design_guidelines.md`)
* **Data/Repository Layer**: Add `linkedWheelPositions: Map<String, WheelPosition> = emptyMap()` and `wheelPositionConflicts: Map<String, Map<WheelPosition, String>> = emptyMap()` to `DeviceRawData` & `DeviceUiData` (`DeviceData.kt`), hydrated for wheel speed sensors in `RawDeviceDataProvider.kt` via `EquipmentDbHelper` + `DevicesDatabaseManager` on `Dispatchers.IO`. Update `DeviceDataRepository.updateDevice()` to commit link/position diffs on `Save` via `EquipmentRepository.setWheelSensorLink(..., swapConflict = true)` and unlink removed bikes.
* **ViewModel Layer**: Update `EditDeviceViewModel.onEquipmentChanged(newEquipment, newWheelPositions)` to stage both in `_deviceSnapshot` without DB writes until `saveChanges()`.
* **UI Consistency (`docs/design_guidelines.md` §§4, 5.1, 5.3, 5.8, 5.19, 5.20)**:
  * Gate wheel-position UI via `isWheelSpeedSensor(data.deviceType)` (`BIKE_SPEED`, `BIKE_SPEED_AND_CADENCE`).
  * Reuse `AppModalBottomSheet`, stateless `WheelPositionSelectionDialog` (adding optional `conflictHint: String? = null`), and existing 9-locale `strings_equipment.xml` keys (`equipment_wheel_position_*`); add 1 positional conflict-swap hint string (`%1$s`) across all 9 locales.
  * Preserve `Box(clickable) { OutlinedTextField(enabled = false) }` trigger (§5.19), independent state for chained `WheelPositionSelectionDialog` (§5.8/§5.19), `Modifier.weight(1f)` on row text columns (§5.20), and Light/Dark `@Preview` composables wrapped in `ATrainingTrackerTheme` (§4, `Rule 8.5`).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Hydrate `WheelPosition` & Conflict Metadata (`REQ-SEN-031.1`)
* Files: `DeviceData.kt`, `RawDeviceDataProvider.kt`
* Changes: Add `linkedWheelPositions` and `wheelPositionConflicts` (with `= emptyMap()` defaults) to `DeviceRawData`/`DeviceUiData` and map in `raw2UiDeviceData()`. In `RawDeviceDataProvider.getDeviceData()`, when `isWheelSpeedSensor(deviceType)`, resolve each linked/available bike's `WheelPosition` and other sensors' occupied `FRONT`/`REAR` names.

### Step 2: Draft Staging & Atomic `Save` Persistence (`REQ-SEN-031.2`, `REQ-SEN-031.3`)
* Files: `EditDeviceViewModel.kt`, `DeviceDataRepository.kt`
* Changes: Extend `EditDeviceViewModel.onEquipmentChanged()` to accept `linkedWheelPositions` (defaulting to current draft) and prune unlinked bikes. In `DeviceDataRepository.updateDevice()`, compare both `linkedEquipment` and `linkedWheelPositions`; for wheel speed sensors, unlink removed bikes via `EquipmentRepository.setDeviceLink(..., false)` and commit active bikes via `EquipmentRepository.setWheelSensorLink(eqId, id, pos, swapConflict = true)`.

### Step 3: `EditDeviceDialog` UI, Reused Dialog & 9-Locale Strings (`REQ-SEN-031.1`, `.2`, `.4`)
* Files: `EditDeviceDialog.kt`, `WheelPositionSelectionDialog.kt`, `app/src/main/res/values*/strings_equipment.xml` (9 locales)
* Changes: Format `EquipmentSection` summary with localized position for wheel speed sensors; render position label/badge in sheet rows (`Modifier.weight(1f)`) and open reused `WheelPositionSelectionDialog` (showing optional `conflictHint` when `FRONT`/`REAR` is occupied on that bike) updating sheet draft state only. Add Light/Dark `@Preview` composables and 9-locale conflict hint string.

### Step 4: Tests, Governance & Visual Verification (`TST-SEN-031.1`–`.4`, `Rule 5`, `Rule 8.5`)
* Files: `EditDeviceViewModelTest.kt`, `DeviceDataRepositoryEquipmentSyncTest.kt`, `EditDeviceEquipmentWheelPositionTest.kt`
* Changes: Add offline unit & Robolectric Compose UI tests (zero `src/main` reads per `REQ-PRO-049.1`), verify `TranslationParityTest`, and capture/inspect Light & Dark PNG (`docs/engineering/walkthroughs/img/ATT-8719_wheel_position_edit_device.png`).
* Command: `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.banalservice.ui.devices.editdevice.*" --tests "com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepositoryEquipmentSyncTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`

---

## 6. Verification & Rollback Plan
* **Verification**: `check-gate <Impl-Plan-Key>` before Stage 4 edits (`Rule 4.1`); Tier-1 targeted tests + `verify_requirement_governance.py` + `Rule 8.5` PNG inspection + `emulator-5554` smoke check in Stage 5.
* **Rollback**: Isolated on `feature/ATT-8719`; revertible without affecting `sprint/2026-11-17_03_Nacht` or `develop`.
