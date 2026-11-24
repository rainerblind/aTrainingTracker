# Stage 2: Requirement & Test Specification - ATT-9605

**Ticket**: `ATT-9605` (`1 SP`, `Bug`, `tier-a`) | **Parent Epic**: `ATT-6033`  
**Target Release**: `V5.0.0` (`Rule 8.3`) | **Active Sprint**: `2026-11-23_03_Nacht` (Sprint 476)  
**Requirement ID**: `REQ-CON-058` (`[Planned]`) | **Test Spec ID**: `TST-CON-058` (`[Planned]`)  
**Living Docs Shards**: `docs/requirements/req_tracking_cockpit_part3.md` (`v1.0.20`), `docs/tests/tst_tracking_cockpit_part2.md` (`v1.0.22`), `docs/requirements.md`, `docs/tests.md`  
**Localization Parity**: `N/A - 0 string changes` (`0` edits to `values*/strings*.xml`)  
**Branch**: `feature/ATT-9605` | **Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-23

---

## 1. Requirement Specification (`REQ-CON-058`)
* **Attributes**: **Priority**: `Medium` | **Type**: `Functional` | **Verification Method**: `Unit` | **System Requirement(s)**: `SYSREQ-TRK-001` — Refines ANT+ Bike Speed and Speed & Cadence telemetry acquisition safety (`ANTBikeSpeedDevice.java`, `ANTBikeSpeedAndCadenceDevice.java`) without external ANT+ protocol, FGS, or API changes.
* **Normative Contract (`REQ-CON-058.1`–`.2`)**:
  1. *Pre-Dereference Null Guard (`REQ-CON-058.1`)*: Both `ANTBikeSpeedDevice.onNewCalculatedSpeed` and `ANTBikeSpeedAndCadenceDevice.onNewCalculatedSpeed` SHALL check `if (calculatedSpeed == null) return;` prior to any logging (`myLog(...)`) or `calculatedSpeed.doubleValue()` dereference, preventing `NullPointerException` and leaving `mSpeedSensor` and `mPaceSensor` values undisturbed (initial `null` remains `null`; active readings remain unchanged).
  2. *Direct `m/s` Speed Publication & Standstill `null` Pace (`REQ-CON-058.2`)*: For non-null `calculatedSpeed` (delivered in `m/s` by `AntPlusBikeSpeedDistancePcc.CalculatedSpeedReceiver`), both callbacks SHALL publish `double speed = calculatedSpeed.doubleValue();` directly to `mSpeedSensor.newValue(speed)` (no `/ 3.6` scaling) and SHALL publish `mPaceSensor.newValue(speed > 0.0 ? 1.0 / speed : null)`, emitting finite `1.0 / speed` (`s/m`) when `speed > 0.0` and `null` (never `Double.POSITIVE_INFINITY` or negative pace) when `speed <= 0.0`, preserving `ThresholdSensor` (`SPEED_THRESHOLD = 8`) initial `null` and 8-tick standstill decay semantics.

---

## 2. Given-When-Then Acceptance Criteria & Invariants
| ID | Given | When | Then |
| :--- | :--- | :--- | :--- |
| `AC-1` | `ANTBikeSpeedDevice` or `ANTBikeSpeedAndCadenceDevice` subscribed (initial `null` OR after valid `10.0 m/s` reading) | `onNewCalculatedSpeed(..., null)` fires | Returns immediately with zero NPE (including `myLog` path); `mSpeedSensor` and `mPaceSensor` values remain untouched. |
| `AC-2` | Freshly subscribed device (`mSpeedSensor == null`, `mPaceSensor == null`) | `onNewCalculatedSpeed(..., BigDecimal.ZERO)` fires | `mSpeedSensor.value == 0.0` and `mPaceSensor.value == null` (never `Double.POSITIVE_INFINITY`). |
| `AC-3` | Subscribed device receiving positive speed `BigDecimal("10.0")` (`m/s`) | `onNewCalculatedSpeed` fires | `mSpeedSensor.value == 10.0` and `mPaceSensor.value == 0.1` (`1.0 / 10.0` finite `s/m`). |
| `AC-4` | Subscribed device after positive motion (`10.0 m/s`, `pace == 0.1 s/m`) | `8` (`SPEED_THRESHOLD`) consecutive `BigDecimal.ZERO` ticks arrive (or negative speed) | Ticks `1..7` hold `pace == 0.1` while `speed == 0.0`; tick `8` decays `mPaceSensor.value` cleanly to `null` (never `Double.POSITIVE_INFINITY`); negative speed also feeds `null` pace. |

* **System Invariants**: `ANTBikeSpeedDevice.java <= 1,500` LoC (`145`); `ANTBikeSpeedAndCadenceDevice.java <= 1,500` LoC (`250`); `ThresholdSensor(..., SPEED_THRESHOLD = 8)` preserved; direct `m/s` scaling preserved; existing `ANTBikeSpeedAndCadenceDeviceTest` (`TST-CON-025.1`/`.2`) untouched; `0` `strings*.xml` edits.

---

## 3. Verification Test Suite (`TST-CON-058`) & Traceability Matrix
| Test ID | Target File / Suite | Verification Scope | Linked REQ |
| :--- | :--- | :--- | :--- |
| `TST-CON-058.1` | `ANTBikeSpeedDeviceTest.kt` (new) | `null` callback (initial & post-motion, zero NPE incl. `myLog`), initial `BigDecimal.ZERO` -> `speed == 0.0` & `pace == null`, `BigDecimal("10.0")` -> `speed == 10.0` & `pace == 0.1`, `8` standstill ticks -> `pace == null` (never `Infinity`), negative speed -> `null` pace | `REQ-CON-058.1`, `.2` |
| `TST-CON-058.2` | `ANTBikeSpeedAndCadenceDeviceTest.kt` (extended) | Identical `CalculatedSpeedReceiver` verification matrix (`null` initial & post-motion, initial `0.0`, `10.0 m/s -> 0.1 s/m`, `8`-tick standstill decay to `null`, negative speed -> `null` pace) alongside existing `TST-CON-025.1`/`.2` tests | `REQ-CON-058.1`, `.2` |
| `TST-CON-058.3` | `ArchitectureConformanceContractTest.kt` | `ANTBikeSpeedDevice.java <= 1,500` LoC, `ANTBikeSpeedAndCadenceDevice.java <= 1,500` LoC, `0` string edits (`Localization Parity: N/A - 0 string changes`) | `REQ-CON-058.1`, `.2` |
