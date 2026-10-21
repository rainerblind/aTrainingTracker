# Stage 5: Walkthrough & Verification - ATT-4554: Remove obsolete BLE Pedal Power Balance correction checkboxes and honor BLE/ANT+ Left/Right reference flags automatically

**Ticket**: [ATT-4554](https://rainerblind.atlassian.net/browse/ATT-4554)  
**Sub-task**: [ATT-4586](https://rainerblind.atlassian.net/browse/ATT-4586) (`[Test]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.5`  
**Requirement Mapping**: `REQ-CON-022`  
**Test Mapping**: `TST-CON-022`  
**Branch**: `feature/ATT-4554`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-21  

---

## 1. Executive Summary & Verification Overview

* **Automatic Telemetry Normalization**: `BTLEBikePowerDevice` now checks Bit 1 of `0x2A63` Flags (`PEDAL_POWER_BALANCE_REFERENCE_MASK`); Left reference packets are automatically inverted to Right pedal percentage ($100.0 - P$), strictly preserving `REQ-UI-377.1`. `ANTBikePowerDevice` evaluates `rightPedalIndicator` and inverts Left reference readings likewise.
* **Obsolete Checkbox Deprecation**: Removed `doublePowerBalanceValues` and `invertPowerBalanceValues` from `EditDeviceDialog`, `EditDeviceViewModel`, `DeviceData`, `DeviceDataRepository`, and `BikePowerSensorsHelper`.
* **Localization Parity**: Cleanly pruned 3 obsolete string keys across all 9 supported application locales.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-CON-022.1` | `TST-CON-022.1` | `BTLEMeasurementParsingTest` (Right/Left ref, boundaries) | **PASSED** | `Verified` |
| `REQ-CON-022.2` | `TST-CON-022.2` | `ANTBikePowerDeviceTest` (Indicator true/false, boundaries, null) | **PASSED** | `Verified` |
| `REQ-CON-022.3` | `TST-CON-022.3` | `EditDeviceViewModelTest` (Read-only features, pruned handlers) | **PASSED** | `Verified` |
| `REQ-CON-022.4` | `TST-CON-022.4` | `TranslationParityTest` (3 keys deleted across 9 locales) | **PASSED** | `Verified` |
| `REQ-CON-022.5` | `TST-CON-022.5` | Tier 1 Targeted Unit & Contract Suite | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Unit & Contract Tests
```text
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 4m 15s
32 actionable tasks: 32 executed
Passed: BTLEMeasurementParsingTest (5 balance tests), ANTBikePowerDeviceTest (4 tests),
        EditDeviceViewModelTest, TranslationParityTest (100% parity across 9 locales)
```

### Requirement Governance
```text
python3 tools/verify_requirement_governance.py
Net-new requirement(s) detected: REQ-CON-022. Bypassing archaeology check cleanly.
0 errors detected.
```

---

## 4. Hardware / Physical Verification (Pixel 10)

* **UI Verification**: `PowerMeterSection` in `EditDeviceDialog` rendered as clean read-only feature list; obsolete correction checkboxes removed without layout shifts.
* **Visual Consistency (Rule 23)**: No new UI tokens or styles added; pruned existing checkbox rows within standard `Column` container.

---

## 5. Invariant & Governance Verification

1. **Telemetry Contract**: `mPowerBalanceSensor` consistently outputs Right pedal percentage $\in [0, 100]\%$ (`REQ-UI-377.1`).
2. **Zero SQLite Migrations**: `BIKE_POWER_SENSOR_FLAGS_TABLE` integer column retains backward compatibility without schema modifications.
3. **Living Documentation Synchronized**: `REQ-CON-022` and `TST-CON-022` recorded as `Verified` in `docs/requirements.md` and `docs/tests.md`.
4. **Subtask Transition**: Stage 5 subtask `ATT-4586` submitted for Gate 5 audit via `submit-stage`.
