# Stage 1 Analysis: ATT-8719 - EditDeviceDialog (Bike Wheel Speed Sensor): Display and Prompt for Front/Rear Wheel Mounting Position

**Ticket**: [ATT-8719](https://rainerblind.atlassian.net/browse/ATT-8719) (`2 SP`)  
**Sub-task**: Assigned upon Stage 1 submission (`[Analysis]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-11-17_03_Nacht`  
**Branch**: `feature/ATT-8719`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-18

---

## 1. Problem Statement & Motivation
* `ATT-7752` (`REQ-SEN-029`) added `WheelPosition` (`FRONT`, `REAR`, `UNSPECIFIED`) to `Equipment.db` (`Links.wheel_position`) and `EquipmentSensorMatrixScreen.kt`, but `EditDeviceDialog.kt` (`EquipmentSection`, `:249-348`) was only updated with `WheelSensorGuidanceCard` (`:212-214`).
* When editing a `DeviceType.BIKE_SPEED` or `DeviceType.BIKE_SPEED_AND_CADENCE` sensor in `EditDeviceDialog`:
  1. The `EquipmentSection` summary field (`:255-259`) renders `data.linkedEquipment.joinToString(", ")` with no wheel position indicator.
  2. The bike-linking bottom sheet (`:299-347`) renders plain `Checkbox` + name rows without displaying each bike's current `WheelPosition` or prompting/allowing `FRONT` / `REAR` / `UNSPECIFIED` selection and conflict swapping.

---

## 2. Gap Analysis & Independent Peer Review (ATT-3534)
* **Independent Peer Review & Verification**:
  * **Line drift & existing UI**: `EquipmentSection` resides at `EditDeviceDialog.kt:249-348` (ticket cited 244–341). Summary text (`:255-259`) and sheet rows (`:323-343`) indeed omit `WheelPosition`.
  * **Data model blind spot (`RawDeviceDataProvider.kt:70-75`, `DeviceData.kt:35-90`)**: `DeviceRawData` and `DeviceUiData` carry only `linkedEquipment: List<String>` and `availableEquipment: List<String>` (`EquipmentDbHelper.getLinkedEquipmentFromDeviceId` / `getEquipment`), omitting `WheelPosition` per bike. Any new fields added to `DeviceRawData`/`DeviceUiData` MUST have default values (`= emptyMap()`) so existing callers/tests (`DeviceDataRepositoryEquipmentSyncTest.kt:96-145`, `DeviceItem.kt:195-208`) stay source-compatible.
  * **Persistence & `Cancel` Draft Semantics Trap (`EditDeviceDialog.kt:111-118`, `DeviceDataRepository.kt:357-384`, `EquipmentDbHelper.java:834-886`)**:
    * `EditDeviceDialog` uses `AppDialogActions.SaveCancel` (`:111-118`), keeping all edits as in-memory drafts in `EditDeviceViewModel._deviceSnapshot` (`EditDeviceViewModel.kt:279-300`) until `saveChanges()` calls `devicesRepository.updateDevice(finalState)` (`:345-351`).
    * Calling `EquipmentRepository.setWheelSensorLink()` immediately from `WheelPositionSelectionDialog` (as `EquipmentSensorMatrixScreen.kt:111-130` does) would **prematurely mutate `Equipment.db`** even if the user taps **Cancel** on the Equipment sheet or **Cancel** on `EditDeviceDialog`!
    * Furthermore, `DeviceDataRepository.updateDevice()` (`:379-384`) checks `originalState.linkedEquipment != finalState.linkedEquipment` and calls `EquipmentDbHelper.setEquipmentLinks()` (`:834-886`), which preserves *old* DB `wheel_position` values and does NOT trigger when only a bike's `WheelPosition` changes (`linkedEquipment` names unchanged), nor does it invoke `EquipmentRepository.setWheelSensorLink()` conflict/swap logic (`EquipmentRepository.kt:108-134`).
  * **Existing Shared Component & 9-Locale String Reuse**:
    * `WheelPositionSelectionDialog.kt:50-144` is already stateless and decoupled from `EquipmentViewModel` — directly reusable in `EditDeviceDialog.kt`!
    * All 9 locales (`values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`) in `strings_equipment.xml:3-9` ALREADY define `equipment_wheel_position_title`, `_front`, `_rear`, `_unspecified`, `_unlink`, `_front_badge`, and `_rear_badge`. No redundant duplicate strings needed.
  * **Non-Wheel Sensor Isolation**: Gate wheel-position display/selection strictly on `DeviceType.BIKE_SPEED` and `DeviceType.BIKE_SPEED_AND_CADENCE` (matching `EquipmentSensorMatrixScreen.kt:523`). `HRM`, `BIKE_POWER`, `BIKE_RADAR`, `BIKE_CADENCE`, `RUN_SPEED`, and smartphone sensors MUST keep the simple bike/shoe checkbox UI.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals (`2 SP` — Standard)**:
  * Hydrate per-bike `WheelPosition` for the edited sensor into `DeviceRawData`/`DeviceUiData` (`Map<String, WheelPosition>`).
  * Update `EditDeviceDialog.kt` (`EquipmentSection`) + `EditDeviceViewModel.kt` so `BIKE_SPEED` and `BIKE_SPEED_AND_CADENCE` display assigned `WheelPosition` in the summary field and bike sheet, prompt via `WheelPositionSelectionDialog` when linking/editing a bike, and commit draft links + wheel positions (with `swapConflict = true` parity via `EquipmentRepository.setWheelSensorLink`) **only on `Save`**.
  * Preserve simple checkbox UI for all non-wheel sensors and ensure 9-locale parity if any label is added.
* **Out-of-Scope Non-Goals**: Changing `Equipment.db` schema, altering `EquipmentSensorMatrixScreen.kt` or `DeviceItem.kt` card formatting, or modifying `WheelCircumferenceHistory` / iTPMS estimators.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-SEN-029` (`REQ-SEN-029.1`–`.4`, `docs/requirements/req_tracking_cockpit.md:5907-5960`), `REQ-UI-149` (`docs/requirements/req_ui_design_system.md:1434-1472`), `TST-SEN-029` (`docs/tests/tst_tracking_cockpit.md:2900`). Targets `EditDeviceDialog.kt`, `EditDeviceViewModel.kt`, `DeviceData.kt`, `RawDeviceDataProvider.kt`, `DeviceDataRepository.kt`.
* **Historical Origin & Commit Trace**: `4edcda86` (`ATT-7752`, introduced `WheelPosition`, `WheelPositionSelectionDialog`, and `WheelSensorGuidanceCard` in `EditDeviceDialog`), `a5665f9a` (`ATT-7753`, dual-wheel circumference history & iTPMS).
* **Root Reason for Existing Formulation**: `ATT-7752` added wheel-position selection to `EquipmentSensorMatrixScreen.kt` and added `setEquipmentLinks()` preservation in `EquipmentDbHelper.java:839-876`, deferring `EditDeviceDialog.kt` `EquipmentSection` parity.
* **Preservation of Core Invariants**: `SaveCancel` draft isolation (`Cancel` leaves `Equipment.db` untouched), `EquipmentRepository.setWheelSensorLink(..., swapConflict = true)` single-`FRONT`/single-`REAR` per-bike guardrail, `EquipmentRepository.equipmentLinksChanged` reactive sync, and simple checkbox UI for non-wheel sensors.

---

## Change Impact Analysis
* **Affected Requirements**: Refinement under `REQ-SEN-029` (`REQ-SEN-029.3`/`.5` in `docs/requirements/req_tracking_cockpit.md`) and `TST-SEN-029` (`docs/tests/tst_tracking_cockpit.md`).
* **Affected Architecture Elements**: `DeviceData.kt` (`DeviceRawData`, `DeviceUiData`), `RawDeviceDataProvider.kt`, `DeviceDataRepository.kt` (`updateDevice`), `EditDeviceViewModel.kt`, `EditDeviceDialog.kt` (`EquipmentSection`), and `EquipmentDbHelper.java` / `EquipmentRepository.kt` (read/commit path).
* **Verification Impact & Test Surface**: Targeted unit tests in `EditDeviceViewModelTest.kt`, `EditDeviceDialogWheelGuidanceTest.kt` (or new `EditDeviceEquipmentWheelPositionTest.kt`), `DeviceDataRepositoryEquipmentSyncTest.kt`; `TranslationParityTest` if strings added; emulator smoke verification of `EditDeviceDialog` on `emulator-5554` (`Rule 7.6`).
* **Risk Assessment & Invariants**: **LOW** (`2 SP`). Strict draft-until-Save semantics prevent accidental DB corruption on `Cancel`; atomic `EquipmentRepository.setWheelSensorLink` on `Save` preserves conflict swap & `equipmentLinksChanged` notifications without schema changes.

---

## 6. Architectural Strategy & System Invariants
1. **Draft State Hydration**: Populate `linkedWheelPositions: Map<String, WheelPosition> = emptyMap()` in `RawDeviceDataProvider` (resolving linked equipment names to `WheelPosition` via `EquipmentDbHelper`) -> `DeviceRawData` -> `DeviceUiData`.
2. **UI & Dialog Flow (`EditDeviceDialog.kt`)**: For `isWheelSpeedSensor(data.deviceType)` (`BIKE_SPEED`, `BIKE_SPEED_AND_CADENCE`), format linked bike names in `EquipmentSection` summary with localized position (`(Vorderrad)`/`(Hinterrad)` via existing `R.string.equipment_wheel_position_*`), show current position + click-to-configure in the Equipment bottom sheet via reused `WheelPositionSelectionDialog`, and keep all edits in draft state (`onEquipmentChanged(newEquipment, newPositions)`).
3. **Atomic Save & Conflict Swap (`DeviceDataRepository.updateDevice`)**: Compare both `linkedEquipment` AND `linkedWheelPositions` against `originalState`. On `Save`, unlink removed bikes and apply linked bikes' `WheelPosition` via `EquipmentRepository.setWheelSensorLink(eqId, sensorId, pos, swapConflict = true)` (or transactional `EquipmentDbHelper` helper) so `Cancel` never mutates SQLite and conflicts swap cleanly.
