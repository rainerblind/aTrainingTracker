# Stage 2: Requirement & Test Specification - ATT-4554: Remove obsolete BLE Pedal Power Balance correction checkboxes and honor BLE/ANT+ Left/Right reference flags automatically

**Ticket**: [ATT-4554](https://rainerblind.atlassian.net/browse/ATT-4554)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.5`  
**Requirement Mapping**: `REQ-CON-022` (*Automated BLE/ANT+ Pedal Power Balance Reference Normalization & Obsolete UI Checkbox Deprecation*)  
**Test Spec ID**: `TST-CON-022`  
**Branch**: `feature/ATT-4554`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-21  

---

## 1. Requirement Specification (REQ-CON-022)

### 1.1 Problem Statement & Rationale
* Modern BLE (`0x2A63`) and ANT+ dual-sided power meters encode Left/Right reference flags dynamically in telemetry packets.
* `BTLEBikePowerDevice` ignored Bit 1 (`PEDAL_POWER_BALANCE_REFERENCE_MASK`), and `ANTBikePowerDevice` ignored `boolean rightPedalIndicator`, relying instead on legacy manual UI correction checkboxes (`doublePowerBalanceValues`, `invertPowerBalanceValues`).
* In `REQ-UI-377.1`, telemetry must strictly represent Right pedal percentage ($P_R \in [0, 100]\%$). Manual overrides are obsolete, error-prone, and clutter device configuration UI.

### 1.2 Functional & Architectural Requirements
1. **Automated BLE Reference Normalization (`REQ-CON-022.1`)**: `BTLEBikePowerDevice` SHALL read raw balance with $0.5\%$ resolution (`rawBalance / 2.0`). If Bit 1 of `0x2A63` Flags is 1 (Left reference), the driver SHALL invert to Right pedal percentage: `100.0 - (rawBalance / 2.0)`.
2. **Automated ANT+ Reference Normalization (`REQ-CON-022.2`)**: `ANTBikePowerDevice.onNewPedalPowerBalance` SHALL evaluate `rightPedalIndicator`. If `false` (Left reference), the driver SHALL invert to Right percentage: `100 - pedalPowerPercentage`. Negative values continue to emit `null`.
3. **Deprecation of Manual Checkboxes (`REQ-CON-022.3`)**: `doublePowerBalanceValues` and `invertPowerBalanceValues` SHALL be removed from `EditDeviceDialog.kt` (`PowerMeterSection`), `EditDeviceViewModel.kt`, `DeviceData.kt`, `DeviceDataRepository.kt`, and `BikePowerSensorsHelper.java`.
4. **9-Language Pruning (`REQ-CON-022.4`)**: The 3 obsolete string keys SHALL be pruned across all 9 application locales.
5. **System Invariants (`REQ-CON-022.5`)**: Zero SQLite migrations, telemetry Right-pedal contract (`REQ-UI-377.1`) guaranteed, 100% clean-room test pass rate.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (BLE Left Reference)**: *Given* `0x2A63` packet with Bit 1 = 1 (Left reference) and raw balance 96 ($48\%$), *When* parsed, *Then* driver publishes `52.0%` Right.
* **AC-2 (BLE Right Reference)**: *Given* `0x2A63` packet with Bit 1 = 0 and raw balance 104 ($52\%$), *When* parsed, *Then* driver publishes `52.0%` Right.
* **AC-3 (ANT+ Left Reference)**: *Given* ANT+ balance event with `rightPedalIndicator == false` and raw 46 ($46\%$), *When* received, *Then* driver publishes `54` Right.
* **AC-4 (UI Simplification)**: *Given* athlete editing a power meter in `EditDeviceDialog`, *When* viewing capabilities, *Then* no manual balance checkboxes appear.

### 1.4 System Invariants
* $P_R \in [0.0, 100.0]\%$ Right-pedal contract strictly satisfied for downstream `PedalPowerBalanceMath`.
* Zero SQLite database migrations (`DevicesDbHelper` bitmask safely retains legacy bits).

---

## 2. Test Specification (TST-CON-022)

### Test Case 1: BLE Reference Flag Normalization (`TST-CON-022.1`)
* **Scope**: Unit Test (`BTLEBikePowerDeviceTest.kt` / `BTLEMeasurementParsingTest.kt`)
* **Preconditions**: Initialized mock `BTLEBikePowerDevice`.
* **Action**: Inject GATT characteristic packets with Bit 1 = 0 (Right ref) and Bit 1 = 1 (Left ref) across sample and boundary values.
* **Expected Result**: Right-ref emits `raw / 2.0`; Left-ref emits `100.0 - (raw / 2.0)`.

### Test Case 2: ANT+ Reference Indicator Normalization (`TST-CON-022.2`)
* **Scope**: Unit Test (`ANTBikePowerDeviceTest.kt`)
* **Preconditions**: Subscribed `IPedalPowerBalanceReceiver`.
* **Action**: Invoke `onNewPedalPowerBalance` with `rightPedalIndicator = true` and `false`.
* **Expected Result**: `true` emits `pct`; `false` emits `100 - pct`; negative values emit `null`.

### Test Case 3: EditDevice UI & Architecture Cleanliness (`TST-CON-022.3`)
* **Scope**: Contract / Unit Test (`EditDeviceViewModelTest.kt`)
* **Preconditions**: Power meter device snapshot in ViewModel.
* **Action**: Inspect `PowerFeatureDisplay` list and save pathways.
* **Expected Result**: Features display read-only; no obsolete checkbox properties or repository masking.

### Test Case 4: 9-Language Localization Pruning Audit (`TST-CON-022.4`)
* **Scope**: Localization Parity Test (`TranslationParityTest.kt`)
* **Action**: Verify `bike_power__pedal_power_balance_correction`, `bike_power__double_power_balance_values`, and `bike_power__invert_power_balance_values` are absent across all 9 locales.
* **Expected Result**: 100% parity, zero orphaned keys.

### Test Case 5: Clean-Room Full Suite Regression (`TST-CON-022.5`)
* **Command**: `./gradlew testDebugUnitTest`
* **Expected Result**: 100% pass rate with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-CON-022.1` | Unit | `BTLEBikePowerDevice` | `REQ-CON-022.1`, `REQ-UI-377.1` | Specified |
| `TST-CON-022.2` | Unit | `ANTBikePowerDevice` | `REQ-CON-022.2`, `REQ-UI-377.1` | Specified |
| `TST-CON-022.3` | Contract | `EditDeviceViewModel`, `DeviceData` | `REQ-CON-022.3` | Specified |
| `TST-CON-022.4` | Localization | `TranslationParityTest` | `REQ-CON-022.4` | Specified |
| `TST-CON-022.5` | Regression | `./gradlew testDebugUnitTest` | `REQ-CON-022.5`, `REQ-PRO-001` | Specified |
