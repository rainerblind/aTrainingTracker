# Stage 1 Analysis: ATT-6042 - Shared CscRevolutionTracker & Event-Time Rollover Preservation

**Ticket**: [ATT-6042](https://issue-tracker/ATT-6042) | **Parent Epic**: [ATT-6033](https://issue-tracker/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring (OOP & FP)*)  
**Target Release**: `V5.0.0` (`Rule 8.3`, active unreleased version) | **Sprint**: `2026-11-21_01_Früh` (Sprint 468)  
**Branch**: `feature/ATT-6042` (from `sprint/2026-11-21_01_Früh` @ `2b63fc73`) | **Author**: Lane 2 Author (`--as agent1`) | **Date**: 2026-11-21

---

## 1. Problem Statement & Motivation
* **Current Behavior**: `BTLEBikeDevice.java` (`236` LoC) and `BTLEBikePowerDevice.java` (`482` LoC) duplicate `9` revolution/time tracking fields, `MAX_IDENTICAL = 4`, `unregisterSensors()` resets (`ATT-6162`), and `~90` LoC of wheel/crank delta evaluation. Moreover, both guard deltas with `if (eventTime > mLastEventTime)`, causing every `UINT16` timer wrap (`32 s` CPS wheel @ `2048 Hz`; `64 s` CSC wheel/crank @ `1024 Hz`) to fall into `mIdentical*Time++` and drop one valid sample.
* **Expected Behavior**: Extract a shared, pure-JVM `CscRevolutionTracker(wheelTimeResolution, crankTimeResolution = 1024, maxIdenticalTicks = 4)` delegating from both drivers, using modular `(eventTime - lastEventTime) & 0xFFFFL` so `UINT16` timer rollovers evaluate normally (`timeDiff == 0` -> identical-time coasting) while preserving all `ATT-6162` guarantees and `0` regressions.

---

## 2. Independent Peer Review & Live Codebase Audit (`2b63fc73`)
* **Prerequisite `ATT-6162` (`REQ-CON-024`) Verified Merged**: Crank `(cumulativeCrankRevolutions - mLastCrankRevolutions) & 0xFFFFL`, float cadence division `(60.0 * revDiff * 1024.0) / timeDiff`, `0.0..250.0` rpm plausibility gate, and `unregisterSensors()` overrides (`BTLEBikeDevice.java:90-97`, `BTLEBikePowerDevice.java:289-296`, invoked from `MyBTLEDevice.java:610` on `STATE_DISCONNECTED` and `MyDevice.java:270` on `shutDown()`) are already in place and tested (`BTLEMeasurementParsingTest.kt:544-680`).
* **Duplication & Rollover Drop Verified**:
  * **Wheel**: `BTLEBikeDevice.java:130-177` (`1024 Hz`) vs `BTLEBikePowerDevice.java:371-413` (`2048 Hz`); both mask `UINT32` revolutions (`& 0xFFFFFFFFL`) and `UINT16` event time (`& 0xFFFFL`), guard `if (revDiff != 0)` before `mPaceSensor.newValue(1 / speed)` (avoiding `1 / 0.0 = Infinity`), accumulate `mDistanceSensor`/`mLapDistanceSensor`, and check `wheelEventTime > mLastWheelEventTime` (`:140`, `:380`) which drops the wrap sample.
  * **Crank**: `BTLEBikeDevice.java:192-227` vs `BTLEBikePowerDevice.java:434-468` (both `1024 Hz`, `UINT16` revs & time), checking `crankEventTime > mLastCrankEventTime` (`:201`, `:442`) which drops the wrap sample.
* **Subtle Divergences & Caller Guards Verified**:
  1. **Null-Sensor Guards (`BTLEBikeDevice:139, 200`)**: Subclasses `BTLEBikeSpeedDevice` (`:44`) and `BTLEBikeCadenceDevice` (`:44`) register only speed/distance OR cadence (`BTLEBikeSpeedAndCadenceDevice` registers both); `BTLEBikePowerDevice` uses `mIsWheelRevolutionDataSupported` / `mIsCrankRevolutionDataSupported` (`:366, :429`). Both drivers MUST keep null-safe/support-gated sensor emission.
  2. **`notifyWheelRevolutions` Side Effect**: `BTLEBikeDevice:159-161` invokes `mMySensorManager.notifyWheelRevolutions(revDiff, cumulativeWheelRevolutions, System.currentTimeMillis())` on non-zero-`timeDiff` wheel updates; `BTLEBikePowerDevice` does not — preserve exact per-driver parity.
  3. **Test Subclasses & Contracts**: `BTLEMeasurementParsingTest` and `HardwareHalIngestionIntegrationTest` subclass `BTLEBikePowerDevice`/`BTLEBikeSpeedAndCadenceDevice` accessing `mPowerSensor`, `mPowerBalanceSensor`, `mCadenceSensor`, `mSpeedSensor`, `mDistanceSensor`, `registerSensors()`, `unregisterSensors()`, `measurementCharacteristicUpdate()` (keep all intact; `0` callers outside these 2 drivers reference `mLast*`/`mIdentical*`). Neither class is in `InterfaceSignatureDriftContractTest.OWNER_FQCNS`.

---

## 3. Scope Grounding & Non-Goals (`2 SP`)
* **In-Scope**: Extract `CscRevolutionTracker` (pure JVM `.kt` or `.java` in `banalservice/devices/bluetooth_le/`), compute modular `timeDiff = (eventTime - lastEventTime) & 0xFFFFL`, delegate wheel/crank evaluation and `unregisterSensors()` -> `reset()` from `BTLEBikeDevice` & `BTLEBikePowerDevice`, preserve null-sensor guards, and add unit tests (`CscRevolutionTrackerTest` + `BTLEMeasurementParsingTest`).
* **Out-of-Scope**: `BTLERunSpeedDevice`, `BTLEHeartRateDevice`, ANT+ bike devices (`ANTBike*`), `newLap()` logic beyond existing `mLapDistanceSensor.reset()`, or `values*/strings*.xml` edits.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
* **Original Requirement IDs & Target**: `REQ-CON-024` (`req_tracking_cockpit_part1.md:3047`, `ATT-6162`) and `REQ-CON-002`/`REQ-CON-014` targeting `BTLEBikeDevice.java` & `BTLEBikePowerDevice.java`.
* **Root Reason & Invariants Preserved**: `REQ-CON-024` introduced `& 0xFFFFL` crank masking, float division, `<= 250.0` rpm plausibility guard, and `unregisterSensors()` state reset; null-sensor guards in `BTLEBikeDevice` protect single-sensor subclasses (`BTLEBikeSpeedDevice`, `BTLEBikeCadenceDevice`). `REQ-CON-024` & `TST-CON-024` remain unmodified `[Verified]`; net-new `REQ-SET-*` / `TST-SET-*` go into roomy shards **`docs/requirements/req_settings_profiles.md`** (`2,917/4,000`) & **`docs/tests/tst_settings_profiles.md`** (`1,640/4,000`), avoiding `tst_tracking_cockpit.md` (`3,992/4,000`).

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-SET-*` (`docs/requirements/req_settings_profiles.md`) & `TST-SET-*` (`docs/tests/tst_settings_profiles.md`), linked to `SYSREQ-TRK-002`; preserves `REQ-CON-002`, `REQ-CON-014`, `REQ-CON-024`. `SYS.2`/`SYS.3`: None (internal calculation deduplication + existing `UINT16` timer wrap preservation; zero external GATT/HAL/API changes). Localization: `N/A - 0 string changes`.
* **Affected Architecture Elements**: New `banalservice/devices/bluetooth_le/CscRevolutionTracker.kt` (pure JVM, zero `android.*` deps), refactored `BTLEBikeDevice.java` & `BTLEBikePowerDevice.java` (both `<= 482` LoC). Zero `docs/architecture.md` / `detailed_design/` changes (`Rule 8.8`, `REQ-PRO-114`).
* **Verification Impact & Test Surface**: Existing `BTLEMeasurementParsingTest` (incl. `TST-CON-024.1–.4`), `HardwareHalIngestionIntegrationTest`, `ArchitectureConformanceContractTest`, `InterfaceSignatureDriftContractTest`, plus new `CscRevolutionTracker` unit tests and `BTLEMeasurementParsingTest` cases for `UINT16` wheel/crank event-time rollover (`lastEventTime = 65000 -> eventTime = 500`, `timeDiff = 1036`), `timeDiff == 0` identical-time coasting (`MAX_IDENTICAL = 4`), null-sensor safety, and `reset()`.
* **Risk Assessment & Invariants**: **LOW–MEDIUM** (`2 SP`) — risk of breaking `SpeedDevice`/`CadenceDevice` null-sensor safety or `notifyWheelRevolutions` callback mitigated by explicit per-driver delegation and full `BTLEMeasurementParsingTest` + `HardwareHalIngestionIntegrationTest` execution.

---

## 6. System Invariants & Risk Rating
* **Core Invariants**: `wheelTimeResolution` (`1024` CSC vs `2048` CPS) & `crankTimeResolution = 1024`; `UINT16` crank rollover + `0.0..250.0` rpm guard; `revDiff != 0` pace guard (no `Infinity`); `mLast*` baseline updated even on `timeDiff == 0` or discarded cadence; `unregisterSensors()` resets tracker state; `BTLEBikeDevice` `notifyWheelRevolutions` & null-sensor guards preserved; `0` `strings*.xml` edits.
* **Risk Rating**: **LOW–MEDIUM** — localized BLE bike-sensor refactoring backed by existing `ATT-6162` & `TST-CON-005` test suites.
