# Stage 1 Analysis: ATT-6176 — Guard `ANTBikePowerDevice.onNewCalculatedWheelSpeed` Against Null and Standstill `Infinity` Pace

**Ticket**: `ATT-6176` (`1 SP`, `Bug`, `Low`, `1 SP` confirmed — no split per `REQ-PRO-029`) | **Parent Epic**: `ATT-6033`  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-21_02_Spät` (Sprint 469) | **Branch**: `feature/ATT-6176` (`a55af6ac`) | **Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-21

---

## Context & Problem Statement
* **Current Behavior**: In `ANTBikePowerDevice.onNewCalculatedWheelSpeed` (`:297-310`), `if (mSpeedSensor == null) createWheelSpeedSensors()` (`:300-302`) and `calculatedWheelSpeed.doubleValue() / 3.6` (`:304`) execute *before* `if (calculatedWheelSpeed != null)` (`:307`). A `null` ANT+ callback creates `SPEED_mps`/`PACE_spm` sensors (and mutates `DevicesDatabaseManager` power flags) then throws `NullPointerException`. Additionally, when `calculatedWheelSpeed` is `0` (`speed_mps <= 0.0`), `mPaceSensor.newValue(1 / speed_mps)` (`:308`) passes `Double.POSITIVE_INFINITY`.
* **Expected Behavior**: Return immediately if `calculatedWheelSpeed == null` *before* `createWheelSpeedSensors()`, and pass `1.0 / speed_mps` to `mPaceSensor` only when `speed_mps > 0.0`, otherwise passing `null` (matching `BTLEBikeDevice.applyWheelSample` `IDENTICAL_TICK_ZEROED` `:215-221`).

---

## Root Cause & Forensics
### Independent Peer Review & Verification (Live `sprint/2026-11-21_02_Spät` @ `a55af6ac`)
Audited `ANTBikePowerDevice.java`, `ThresholdSensor.java`, `BTLEBikeDevice.java`, `ANTBikeSpeedDevice.java`, `ANTBikeSpeedAndCadenceDevice.java`, `ANTBikePowerDeviceTest.kt`, and `ArchitectureConformanceContractTest.kt`:

| Hypothesis / Check | Verdict | Live Code Evidence (`a55af6ac`) |
| :--- | :---: | :--- |
| **1. Post-dereference null check & premature sensor init** | **CONFIRMED** (shifted lines) | `ANTBikePowerDevice.java:297-310` (legacy `:300-316`): `:300-302` calls `createWheelSpeedSensors()`, `:304` dereferences `calculatedWheelSpeed.doubleValue()`, and `:307` checks `calculatedWheelSpeed != null` too late. |
| **2. Standstill `1 / 0.0 = Infinity` & `ThresholdSensor` behavior** | **CONFIRMED** | `:308` computes `1 / speed_mps` (`Double.POSITIVE_INFINITY` at `0.0`). Because `mPaceSensor` is `ThresholdSensor<>(..., SPEED_THRESHOLD = 8)` (`:56, :142`, `ThresholdSensor.java:37-48`), passing `null` when `speed_mps <= 0.0` preserves `null` initially, never emits `Infinity`, and decays prior pace to `null` after `8` standstill updates (matching `BTLEBikeDevice.java:217-220`). |
| **3. Sibling callbacks & ANT+ speed devices (`Rule 68`)** | **DOCUMENTED (Out-of-Scope)** | `ANTBikePowerDevice` (`onNewCalculatedPower :220`, `CrankCadence :230-238`, `Torque :265-271`, `WheelDistance :277-289`), `ANTBikeSpeedDevice.java:112-117`, and `ANTBikeSpeedAndCadenceDevice.java:148-151` exhibit similar patterns; strictly out-of-scope per `ATT-1250` (`1 SP`). |
| **4. Test hook & LoC ceiling** | **SAFE (`395` LoC)** | `ANTBikePowerDevice.java` (`395 <= 1,500` LoC); `mSpeedSensor` and `mPaceSensor` (`:63-64`) are `protected`, enabling direct inspection in `ANTBikePowerDeviceTest.TestANTBikePowerDevice`. |

---

## Requirements Archaeology (Chesterton's Fence)
* **Original Requirement ID & Target**: `REQ-CON-022.2` & `REQ-CON-025.3`/`.4` (`ANTBikePowerDevice.java`, `ANTBikePowerDeviceTest.kt`); `REQ-CON-020.4` (`ATT-6046` ANT+ sibling cleanup).
* **Historical Origin & Commit Trace**: `ANTBikePowerDevice` lazily registers auxiliary wheel speed/distance/cadence sensors via `DevicesDatabaseManager.putBikePowerSensorFlags` only when real data arrives; `ATT-6042` (`db8d50c1`) standardized `BTLEBikeDevice` standstill pace to `null`.
* **Root Reason for Existing Formulation**: Early ANT+ callback wrapper checked `calculatedWheelSpeed != null` around `mPaceSensor.newValue(1 / speed_mps)` only, overlooking the preceding `createWheelSpeedSensors()` and `doubleValue()` calls and zero-speed division.
* **Preservation of Core Invariants**: (1) Lazy creation of `mSpeedSensor` & `mPaceSensor` + `BikePowerSensorsHelper.addWheelSpeedDataFlag` DB persistence on first non-null sample remains untouched; (2) `ThresholdSensor(..., SPEED_THRESHOLD = 8)` smoothing preserved; (3) `onNewPedalPowerBalance`, `onNewPedalSmoothness`, and `ANTBikePowerDeviceTest.kt` existing tests remain untouched.

---

## Proposed Scope & Invariants
* **In-Scope**: (1) Add `if (calculatedWheelSpeed == null) return;` at entry of `ANTBikePowerDevice.onNewCalculatedWheelSpeed` (`:298`, before `createWheelSpeedSensors()`); (2) replace `:307-309` with `if (speed_mps > 0.0) { mPaceSensor.newValue(1.0 / speed_mps); } else { mPaceSensor.newValue(null); }`; (3) add unit tests in `ANTBikePowerDeviceTest.kt` capturing `subscribeCalculatedWheelSpeedEvent` for `null` (no NPE, sensors remain `null`), `BigDecimal.ZERO` (`speed == 0.0`, `pace == null`, never `Infinity`, including decay after `SPEED_THRESHOLD` ticks), and positive speed (`36.0 km/h -> 10.0 m/s`, `pace == 0.1 s/m`).
* **Out-of-Scope**: Sibling `ANTBikePowerDevice` callbacks, `ANTBikeSpeedDevice`, `ANTBikeSpeedAndCadenceDevice`, `BTLEBikeDevice`, `strings*.xml` (`0` strings).

---

## Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-STB-*` / `TST-STB-*` in disjoint roomy shards **`docs/requirements/req_process_stability_part2.md`** (`2,728 / 4,000` LoC, `+1,272` headroom) and **`docs/tests/tst_process_stability_part2.md`** (`1,737 / 4,000` LoC, `+2,263` headroom) — avoiding Lane 1's `settings_profiles` shards and near-cap `tst_tracking_cockpit.md` (`3,992/4,000`) / `req_tracking_cockpit_part2.md` (`3,969/4,000`). **SYS.2/SYS.3 Impact (`REQ-PRO-131.4`)**: `None` (internal null/zero defensive guard in `ANTBikePowerDevice.onNewCalculatedWheelSpeed`; zero ANT+ protocol, FGS, or external API changes). `Localization Parity: N/A - 0 string edits`.
* **Affected Architecture Elements**: `app/src/main/java/com/atrainingtracker/banalservice/devices/ant_plus/ANTBikePowerDevice.java` (`395 <= 1,500` LoC). No `docs/architecture.md` or `detailed_design/` changes (`REQ-PRO-114`).
* **Verification Impact & Test Surface**: Extend `ANTBikePowerDeviceTest.kt` with `CalculatedWheelSpeedReceiver` capture slot covering `null`, `0`, positive, and post-motion standstill transitions; run `ArchitectureConformanceContractTest`.
* **Risk Assessment & Invariants**: **LOW** (`1 SP`) — isolated 5-line callback guard in `ANTBikePowerDevice.java`; preserves lazy sensor creation, `ThresholdSensor` threshold, and all existing `ANTBikePowerDeviceTest` assertions.
