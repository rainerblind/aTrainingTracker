# Stage 1 Analysis: ATT-9605 — Guard `ANTBikeSpeedDevice` & `ANTBikeSpeedAndCadenceDevice` Against Null `calculatedSpeed` and Standstill `Infinity` Pace

**Ticket**: `ATT-9605` (`1 SP`, `Bug`, `Medium`, `severity-major`, `tier-a`; `1 SP` confirmed — no split per `REQ-PRO-029`) | **Parent Epic**: `ATT-6033`  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-23_03_Nacht` (Sprint 476) | **Branch**: `feature/ATT-9605` (`b7d66edb`) | **Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-23

---

## Context & Problem Statement
* **Current Behavior**:
  1. `ANTBikeSpeedDevice.onNewCalculatedSpeed` (`:112-117`) unconditionally dereferences `calculatedSpeed.doubleValue()` inside `myLog(...)` (`:113`), `mSpeedSensor.newValue(...)` (`:115`), and `mPaceSensor.newValue(1 / calculatedSpeed.doubleValue())` (`:116`). Because Java eagerly evaluates method arguments even when `MyRemoteDevice.DEBUG == false`, a `null` `calculatedSpeed` immediately throws `NullPointerException` at `:113`. At standstill (`0.0`), `:116` emits `Double.POSITIVE_INFINITY` to `mPaceSensor`.
  2. `ANTBikeSpeedAndCadenceDevice.onNewCalculatedSpeed` (`:148-151`) identically dereferences `calculatedSpeed.doubleValue()` without a `null` check (`:149`, throwing NPE) and computes `1 / calculatedSpeed.doubleValue()` (`:150`, emitting `Double.POSITIVE_INFINITY` at `0.0`).
* **Expected Behavior**: Return early if `calculatedSpeed == null` before logging or dereferencing, publish `speed` (`m/s`) to `mSpeedSensor`, and publish `speed > 0.0 ? 1.0 / speed : null` (`s/m`) to `mPaceSensor` (preserving `ThresholdSensor` standstill decay semantics, matching `ANTBikePowerDevice` (`ATT-6176`) and `BTLEBikeDevice` (`ATT-6042`)).

---

## Root Cause & Forensics
### Independent Peer Review & Verification (Live `sprint/2026-11-23_03_Nacht` @ `b7d66edb`)
Audited `ANTBikeSpeedDevice.java`, `ANTBikeSpeedAndCadenceDevice.java`, `ANTBikePowerDevice.java`, `BTLEBikeDevice.java`, `ThresholdSensor.java`, `MyDevice.java`, `MyRemoteDevice.java`, `ANTBikeSpeedAndCadenceDeviceTest.kt`, `ANTBikePowerDeviceTest.kt`, and `AntPluginLib_Javadoc_3-9-0.zip`:

| Hypothesis / Check | Verdict | Live Code Evidence (`b7d66edb`) |
| :--- | :---: | :--- |
| **1. Unchecked `calculatedSpeed` NPE in both callbacks** | **CONFIRMED** | `ANTBikeSpeedDevice.java:113-116` dereferences `calculatedSpeed.doubleValue()` inside `myLog(...)` before sensor updates; `ANTBikeSpeedAndCadenceDevice.java:149-150` dereferences directly. Neither guards `calculatedSpeed == null`. |
| **2. Standstill `1 / 0.0 = Infinity` & `ThresholdSensor` impact** | **CONFIRMED** | Both use `ThresholdSensor<Double>(this, SensorType.PACE_spm, SPEED_THRESHOLD = 8)` (`ThresholdSensor.java:37-48`). Passing `Double.POSITIVE_INFINITY` (non-null) resets `mNrZeros = 0` and immediately publishes `Infinity`; passing `null` when `speed <= 0.0` keeps initial `null` and decays prior pace to `null` after `8` standstill ticks. |
| **3. ANT+ SDK speed units (`m/s` vs `km/h`)** | **VERIFIED (`m/s`)** | `AntPluginLib_Javadoc_3-9-0.zip`: `AntPlusBikeSpeedDistancePcc.CalculatedSpeedReceiver.onNewCalculatedSpeed` documents `calculatedSpeed` in **Units: `m/s`** (unlike `AntPlusBikePowerPcc.CalculatedWheelSpeedReceiver`, which is `km/h` and divides by `3.6`). No `/ 3.6` scaling applies; `1.0 / speed` directly yields `PACE_spm` (`s/m`). |
| **4. Eager sensor creation vs `ANTBikePowerDevice`** | **VERIFIED** | Unlike `ANTBikePowerDevice` (lazy `createWheelSpeedSensors()`), both devices initialize `mSpeedSensor` and `mPaceSensor` eagerly in `addSensors()` during `MyDevice` construction (`MyDevice.java:85`). Early return on `null` leaves prior/initial sensor values untouched. |
| **5. Side effects & sibling callbacks (`Rule 68`)** | **DOCUMENTED (Out-of-Scope)** | `ANTBikeSpeedAndCadenceDevice.onNewCalculatedCadence` (`:178-180`), `ANTRunSpeedDevice.onNewInstantaneousSpeed` (`:137-146`), `BTLERunSpeedDevice.java:143`, and legacy commented block (`ANTBikeSpeedAndCadenceDevice.java:192-248`) share adjacent patterns; strictly out-of-scope per `ATT-1250` (`1 SP`). |
| **6. Testability & LoC ceiling** | **SAFE** | `ANTBikeSpeedDevice.java` (`145 <= 1,500` LoC) and `ANTBikeSpeedAndCadenceDevice.java` (`250 <= 1,500` LoC); `bikeSpeedDistancePcc`, `mSpeedSensor`, `mPaceSensor`, and `subscribeSpecificEvents()` are `protected`, enabling direct capture/inspection in JVM unit tests. |

---

## Requirements Archaeology (Chesterton's Fence — `REQ-PRO-022`)
* **Original Requirement ID & Target**: Net-new `REQ-CON-058` extending `REQ-CON-001` (*ANT+ Sensor Support*), `REQ-CON-025` (`ANTBikeSpeedAndCadenceDevice.java`), and aligning with `REQ-STB-026` (`ATT-6176`, `ANTBikePowerDevice.java`).
* **Historical Origin & Commit Trace**: Original ANT+ bike speed drivers left a stale inline `"correct?"` comment on `1 / calculatedSpeed.doubleValue()`; `ATT-6042` (`db8d50c1`) standardized `BTLEBikeDevice` standstill pace to `null`, and `ATT-6176` (`ccc04e9a`) applied the null + standstill guard to `ANTBikePowerDevice`.
* **Root Reason for Existing Formulation**: Early ANT+ driver assumed `CalculatedSpeedReceiver` only emitted non-null positive speeds and omitted defensive null/zero guards.
* **Preservation of Core Invariants**: (1) Eager `addSensors()` creation and `SPEED_THRESHOLD = 8` smoothing preserved; (2) direct `m/s` scaling (no `/ 3.6`) preserved; (3) `onNewCalculatedAccumulatedDistance` wheel-rev notifications and `ANTBikeSpeedAndCadenceDevice.releaseAccess()` (`REQ-CON-025.1`) untouched; (4) zero DB/UI/localization changes.

---

## Proposed Scope & Invariants
* **In-Scope**: (1) Early-return `if (calculatedSpeed == null) return;` (before `myLog` / dereferencing) and `speed > 0.0 ? 1.0 / speed : null` pace calculation in both `ANTBikeSpeedDevice.onNewCalculatedSpeed` (`:112-117`) and `ANTBikeSpeedAndCadenceDevice.onNewCalculatedSpeed` (`:148-151`); (2) new `ANTBikeSpeedDeviceTest.kt` and extended `ANTBikeSpeedAndCadenceDeviceTest.kt` covering `null` (initial & post-motion), `0.0` (initial & 8-tick threshold decay), negative, and positive speed values.
* **Out-of-Scope**: `onNewCalculatedCadence`, `ANTRunSpeedDevice`, `BTLERunSpeedDevice`, `ANTBikePowerDevice`, commented-out legacy block (`:192-248`), and `strings*.xml` (`0` edits).

---

## Change-Request Impact Analysis (`SUP.10` / `REQ-PRO-064`)
* **Affected Requirements**: Net-new `REQ-CON-058` / `TST-CON-058` (`[Planned]`) in disjoint roomy shards `docs/requirements/req_tracking_cockpit_part3.md` (`1,760 / 4,000` LoC) and `docs/tests/tst_tracking_cockpit_part2.md` (`2,242 / 4,000` LoC), linked to `SYSREQ-TRK-001`. **SYS.2/SYS.3 Impact (`REQ-PRO-131.4`)**: `None` — internal defensive callback guard; zero ANT+ protocol, FGS, or external API changes. `Localization Parity: N/A - 0 string edits`.
* **Affected Architecture Elements**: `ANTBikeSpeedDevice.java` (`145` LoC) and `ANTBikeSpeedAndCadenceDevice.java` (`250` LoC). No `docs/architecture.md` (`Rule 8.8`) or `docs/architecture/detailed_design/` (`REQ-PRO-114`) updates required.
* **Verification Impact & Test Surface**: Add `ANTBikeSpeedDeviceTest.kt` and extend `ANTBikeSpeedAndCadenceDeviceTest.kt` with `CalculatedSpeedReceiver` capture; run `ArchitectureConformanceContractTest` and `tools/auto_gate.py`.
* **Risk Assessment & Invariants**: **MEDIUM / `tier-a`** (ANT+ sensor driver per `Rule 8.3`, `1 SP`) — minimal, isolated 6-line guard per callback preserving `ThresholdSensor` smoothing, `m/s` units, and all existing `ANTBikeSpeedAndCadenceDeviceTest` assertions.
