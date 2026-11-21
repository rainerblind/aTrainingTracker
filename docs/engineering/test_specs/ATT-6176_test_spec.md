# Stage 2: Requirement & Test Specification - ATT-6176

**Ticket**: `ATT-6176` (`1 SP`, `Bug`) | **Parent Epic**: `ATT-6033`  
**Target Release**: `V5.0.0` (`Rule 8.3`) | **Active Sprint**: `2026-11-21_02_Spät` (Sprint 469)  
**Requirement ID**: `REQ-STB-026` (`[Planned]`) | **Test Spec ID**: `TST-STB-026` (`[Planned]`)  
**Living Docs Shards**: `docs/requirements/req_process_stability_part2.md` (`v1.0.57`), `docs/tests/tst_process_stability_part2.md` (`v1.0.50`), `docs/requirements.md`, `docs/tests.md`  
**Localization Parity**: N/A - 0 string changes (`0` edits to `values*/strings*.xml`)  
**Branch**: `feature/ATT-6176` | **Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-21

---

## 1. Requirement Specification (`REQ-STB-026`)
* **Attributes**: **Priority**: `Low` | **Type**: `Functional` | **Verification Method**: `Unit` | **System Requirement(s)**: `Derived (SW-only)` — internal defensive null/zero guard in `ANTBikePowerDevice.onNewCalculatedWheelSpeed` without external ANT+ protocol, FGS, or API changes.
* **Normative Contract (`REQ-STB-026.1`–`.2`)**:
  1. `ANTBikePowerDevice.onNewCalculatedWheelSpeed` SHALL execute `if (calculatedWheelSpeed == null) return;` prior to calling `createWheelSpeedSensors()` and prior to dereferencing `calculatedWheelSpeed.doubleValue()`, preventing `NullPointerException` and premature wheel sensor / DB flag creation.
  2. For non-null `calculatedWheelSpeed`, `double speed_mps = calculatedWheelSpeed.doubleValue() / 3.6;` SHALL be published to `mSpeedSensor.newValue(speed_mps)`, and `mPaceSensor.newValue(speed_mps > 0.0 ? 1.0 / speed_mps : null)` SHALL emit finite `1.0 / speed_mps` when `speed_mps > 0.0` and `null` (never `Double.POSITIVE_INFINITY`) at standstill (`speed_mps <= 0.0`), preserving `ThresholdSensor` (`SPEED_THRESHOLD = 8`) behavior.

---

## 2. Given-When-Then Acceptance Criteria & Invariants
| ID | Given | When | Then |
|---|---|---|---|
| `AC-1` | `calculatedWheelSpeed == null` | `onNewCalculatedWheelSpeed` fires | Returns immediately; zero NPE, `mSpeedSensor == null`, `mPaceSensor == null`, `putBikePowerSensorFlags` never invoked. |
| `AC-2` | `calculatedWheelSpeed == BigDecimal.ZERO` (`speed_mps == 0.0`) initially or for `8` ticks after motion | `onNewCalculatedWheelSpeed` fires | `mSpeedSensor == 0.0` and `mPaceSensor == null` (never `Double.POSITIVE_INFINITY`), matching `ATT-6042` standstill parity. |
| `AC-3` | `calculatedWheelSpeed == 36.0` (`10.0 m/s`) | `onNewCalculatedWheelSpeed` fires | `mSpeedSensor == 10.0` and `mPaceSensor == 0.1` (`1.0 / 10.0`); `ANTBikePowerDevice.java <= 1,500` LoC; `0` string edits. |

* **System Invariants**: `ANTBikePowerDevice.java <= 1,500` LoC (`395`); `ThresholdSensor(..., SPEED_THRESHOLD = 8)` preserved; existing `onNewPedalPowerBalance` & `onNewPedalSmoothness` contracts untouched; `0` `strings*.xml` edits.

---

## 3. Verification Test Suite (`TST-STB-026`) & Traceability Matrix
| Test ID | Target File / Suite | Verification Scope | Linked REQ |
|---|---|---|---|
| `TST-STB-026.1` | `ANTBikePowerDeviceTest.kt` | `null` callback early-return (zero NPE, `mSpeedSensor == null`, `mPaceSensor == null`, `putBikePowerSensorFlags` never called) | `REQ-STB-026.1` |
| `TST-STB-026.2` | `ANTBikePowerDeviceTest.kt` | Initial `BigDecimal.ZERO` -> `pace == null`, `36.0 km/h` -> `speed == 10.0` & `pace == 0.1`, `8` standstill ticks -> `pace == null` (never `Infinity`) | `REQ-STB-026.2` |
| `TST-STB-026.3` | `ArchitectureConformanceContractTest.kt` | `ANTBikePowerDevice.java <= 1,500` LoC, existing `ANTBikePowerDeviceTest` regression parity, `0` string edits (`Localization Parity: N/A`) | `REQ-STB-026.1`, `.2` |
