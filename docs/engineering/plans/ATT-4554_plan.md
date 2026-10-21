# Stage 3: Implementation Plan - ATT-4554: Remove obsolete BLE Pedal Power Balance correction checkboxes and honor BLE/ANT+ Left/Right reference flags automatically

**Ticket**: [ATT-4554](https://rainerblind.atlassian.net/browse/ATT-4554)  
**Sub-task**: [ATT-4584](https://rainerblind.atlassian.net/browse/ATT-4584) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.5`  
**Requirement Mapping**: `REQ-CON-022`  
**Test Mapping**: `TST-CON-022`  
**Branch**: `feature/ATT-4554`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-21  

---

## 1. Problem Description & Background
* In `REQ-UI-377.1`, `PedalPowerBalanceMath` expects `SensorType.PEDAL_POWER_BALANCE` telemetry to represent **Right pedal percentage** ($P_R \in [0, 100]\%$, $P_L = 100 - P_R$).
* `BTLEBikePowerDevice` ignored Bit 1 of Flags (`PEDAL_POWER_BALANCE_REFERENCE_MASK = 1 << 1`), and `ANTBikePowerDevice` ignored `boolean rightPedalIndicator`, relying on obsolete manual checkboxes (`doublePowerBalanceValues`, `invertPowerBalanceValues`).
* Modern BLE CPS (`0x2A63`) and ANT+ power meters report reference metadata dynamically in every packet. Manual user checkboxes are obsolete and clutter the device configuration UI.

---

## 2. Traceability & Requirements Mapping
* **REQ-CON-022.1** $\rightarrow$ **TST-CON-022.1**: Automated BLE reference bit normalization in `BTLEBikePowerDevice`.
* **REQ-CON-022.2** $\rightarrow$ **TST-CON-022.2**: Automated ANT+ indicator normalization in `ANTBikePowerDevice`.
* **REQ-CON-022.3** $\rightarrow$ **TST-CON-022.3**: Deprecation of manual checkboxes in `EditDeviceDialog`, `EditDeviceViewModel`, `DeviceData`, `DeviceDataRepository`, and `BikePowerSensorsHelper`.
* **REQ-CON-022.4** $\rightarrow$ **TST-CON-022.4**: Clean pruning of 3 obsolete string keys across all 9 application locales.
* **REQ-CON-022.5** $\rightarrow$ **TST-CON-022.5**: System invariant preservation and full clean-room suite regression.

---

## 3. System Invariants & Preserved Behavior
1. **Telemetry Contract**: `mPowerBalanceSensor` always emits Right pedal percentage $\in [0, 100]\%$ (`REQ-UI-377.1`).
2. **Zero SQLite Migrations**: `BIKE_POWER_SENSOR_FLAGS_TABLE` integer column retains backward compatibility without schema changes.
3. **Localization Parity**: Zero new UI strings; 100% parity maintained across all 9 locales under `TranslationParityTest`.
4. **Code Lock & Decision Gate**: Zero production code edits until Gate 3 passes; parent completion reserved for Human Gate.

---

## 4. Proposed Architectural Changes & UI Consistency
* **BLE Driver (`BTLEBikePowerDevice.java`)**: Remove `mDoublePowerBalanceValues` and `mInvertPowerBalanceValues`. Always scale by $0.5\%$: `balance = rawBalance / 2.0`. If `(flags & PEDAL_POWER_BALANCE_REFERENCE_MASK) != 0` (Bit 1 == 1, Left reference), invert: `balance = 100.0 - balance`.
* **ANT+ Driver (`ANTBikePowerDevice.java`)**: Remove `mInvertPowerBalanceValues`. If `!rightPedalIndicator`, invert: `balance = 100 - pedalPowerPercentage`. Negative values continue emitting `null`.
* **Domain & Helper (`BikePowerSensorsHelper.java`, `DeviceData.kt`, `DeviceDataRepository.kt`)**: Remove bits 13 & 14 and helper methods; remove `doublePowerBalanceValues` and `invertPowerBalanceValues` from `BikePowerFeatures` and repository persistence.
* **UI & ViewModel (`EditDeviceDialog.kt`, `EditDeviceViewModel.kt`)**: Remove correction section from `PowerMeterSection`; section renders clean read-only features list. Remove ViewModel change handlers.
* **UI Consistency (Rule 23)**:
  * *Reference screen / component*: `EditDeviceDialog.kt` (`PowerMeterSection`).
  * *Reused components*: Existing `Column`, `Text` composables; read-only feature list.
  * *Theme tokens*: `MaterialTheme.typography.bodyMedium` / `bodySmall`, `colorScheme.onSurface` / `onSurfaceVariant`.
  * *New one-off styles & justification*: None. Checkbox rows are removed without adding new custom styles.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Automatic Protocol Reference Normalization in Drivers (`REQ-CON-022.1`, `REQ-CON-022.2`)
* Files: `BTLEBikePowerDevice.java`, `ANTBikePowerDevice.java`
* Changes: Remove legacy boolean flags. Invert Left-reference readings to Right-pedal percentage automatically.

### Step 2: Deprecate Obsolete Checkboxes, Repository Masking & String Resources (`REQ-CON-022.3`, `REQ-CON-022.4`)
* Files: `BikePowerSensorsHelper.java`, `DeviceData.kt`, `DeviceDataRepository.kt`, `EditDeviceViewModel.kt`, `EditDeviceDialog.kt`, `values*/strings.xml` (9 files)
* Changes: Remove obsolete fields, methods, UI checkbox rows, and delete 3 dead string keys in all 9 locales.

### Step 3: Targeted Unit & Localization Verification Tests (`TST-CON-022.1` – `TST-CON-022.4`)
* Files: `BTLEBikePowerDeviceTest.kt`, `ANTBikePowerDeviceTest.kt`, `EditDeviceViewModelTest.kt`
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.*" --tests "com.atrainingtracker.banalservice.ui.devices.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests in Stage 4, followed by full `./gradlew testDebugUnitTest` in Stage 5.
* **Rollback**: Clean git working branch allows instant `git checkout -- .` revert without affecting `develop`.
