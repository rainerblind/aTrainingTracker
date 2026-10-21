# Stage 1 Analysis: ATT-4554 - Remove obsolete BLE Pedal Power Balance correction checkboxes and honor BLE/ANT+ Left/Right reference flags automatically

**Ticket**: [ATT-4554](https://rainerblind.atlassian.net/browse/ATT-4554)  
**Sub-task**: [ATT-4582](https://rainerblind.atlassian.net/browse/ATT-4582) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.5`  
**Branch**: `feature/ATT-4554`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-21  

---

## 1. Problem Statement & Motivation
* `EditDeviceDialog.kt` exposes two manual BLE checkboxes: `doublePowerBalanceValues` (legacy workaround for non-compliant power meters from >10 years ago) and `invertPowerBalanceValues` (manual swap of left/right balance).
* `PedalPowerBalanceMath` (`REQ-UI-377.1`) strictly expects `SensorType.PEDAL_POWER_BALANCE` telemetry to represent **Right pedal contribution percentage** ($P_R \in [0, 100]\%$, $P_L = 100 - P_R$).
* `BTLEBikePowerDevice.java` defined `PEDAL_POWER_BALANCE_REFERENCE_MASK = 1 << 1` but never evaluated it, and `ANTBikePowerDevice.java` ignored `boolean rightPedalIndicator` in `onNewPedalPowerBalance(...)`.
* Modern BLE and ANT+ power meters report reference metadata in every packet. Manual user checkboxes are obsolete, error-prone, and clutter device configuration UI.

---

## 2. Root Cause Analysis (Forensic Investigation)
* **BLE Reference Neglect**: In `BTLEBikePowerDevice.java` (L326–332), raw balance is scaled by `0.5%` (`rawBalance / 2.0`), but Bit 1 of `0x2A63` Flags (`PEDAL_POWER_BALANCE_REFERENCE_MASK`) is ignored. Per Bluetooth SIG CPS: Bit 1 == 0 denotes Unknown/Right; Bit 1 == 1 denotes **Left** reference. Emitting unadjusted Left-reference telemetry directly inverts Left/Right in `PedalPowerBalanceMath`.
* **ANT+ Indicator Neglect**: In `ANTBikePowerDevice.java` (L322), `onNewPedalPowerBalance(..., boolean rightPedalIndicator, int pedalPowerPercentage)` ignored `rightPedalIndicator`. When `rightPedalIndicator == false` (Left reference), `pedalPowerPercentage` was forwarded without converting to Right percentage ($100 - P$).
* **Obsolete UI & Storage State**: `BikePowerSensorsHelper.java` (bits 13 & 14), `DeviceData.kt` (`BikePowerFeatures`), `EditDeviceViewModel.kt`, and `DeviceDataRepository.kt` unnecessarily persist and expose manual overrides that modern protocols handle autonomously.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Auto-normalize BLE balance in `BTLEBikePowerDevice.java`: when `(flags & PEDAL_POWER_BALANCE_REFERENCE_MASK) != 0` (Left reference), emit $100.0 - \text{balanceValue}$.
  * Auto-normalize ANT+ balance in `ANTBikePowerDevice.java`: when `!rightPedalIndicator`, emit $100.0 - \text{pedalPowerPercentage}$.
  * Remove `doublePowerBalanceValues` and `invertPowerBalanceValues` from `BikePowerSensorsHelper`, `DeviceData`, `DeviceDataRepository`, `EditDeviceDialog`, and `EditDeviceViewModel`.
  * Remove dead string resources (`bike_power__pedal_power_balance_correction`, `bike_power__double_power_balance_values`, `bike_power__invert_power_balance_values`) across all 9 application locales.
  * Author unit tests verifying protocol reference flag decoding for both BLE and ANT+.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modifications to `PedalPowerBalanceMath.kt` or `PedalPowerBalanceBar.kt` UI rendering logic.
  * No SQLite schema migrations (`DevicesDbHelper` integer bitmask preserves unused bits harmlessly).
  * No alterations to unrelated bike power metrics (cadence, torque, pedal smoothness).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Net-New Requirement**: `REQ-CON-022` (*Automated BLE/ANT+ Pedal Power Balance Reference Normalization & Obsolete UI Checkbox Deprecation*).
* **Chesterton's Fence Audit**:
  * *Original Target*: Workaround checkboxes added in initial ANT+/BLE power meter integration (~2014–2018) when BLE firmware was immature.
  * *Root Reason for Existing Formulation*: Power meters lacked consistent reference flags or omitted 0.5% scaling; manual checkboxes let athletes correct inverted/halved readouts.
  * *Preservation of Core Invariants*: Preserves `REQ-UI-377.1` telemetry contract ($P_R \in [0, 100]\%$ Right pedal share) automatically without user intervention.

---

## 5. Architectural Strategy & High-Level Solution
* **Driver Normalization**:
  * `BTLEBikePowerDevice`: Check `(flags & PEDAL_POWER_BALANCE_REFERENCE_MASK) != 0`. If true, `balance = 100.0 - (rawBalance / 2.0)`; otherwise `balance = rawBalance / 2.0`.
  * `ANTBikePowerDevice`: Check `rightPedalIndicator`. If true, `balance = pedalPowerPercentage`; otherwise `balance = 100 - pedalPowerPercentage`.
* **UI & Domain Cleanup**:
  * Delete `PowerMeterSection` correction block from `EditDeviceDialog.kt`; `PowerMeterSection` becomes a clean read-only list of detected hardware features.
  * Clean up `EditDeviceViewModel.kt`, `DeviceData.kt`, `DeviceDataRepository.kt`, and `BikePowerSensorsHelper.java`.
  * Maintain 9-language parity by cleanly pruning the 3 obsolete keys across all `strings.xml` files.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. `SensorType.PEDAL_POWER_BALANCE` always emits Right pedal percentage $\in [0, 100]\%$ (`REQ-UI-377.1`).
  2. Zero SQLite schema changes or database migrations.
  3. `TranslationParityTest` passes cleanly across all 9 supported locales.
* **Risk Rating**: **LOW**. Localized to packet parsing in two device drivers and removing dead UI checkboxes. Existing test suite and live sensor flow remain intact.
