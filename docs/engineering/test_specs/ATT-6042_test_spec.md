# Stage 2: Requirement & Test Specification - ATT-6042: Shared CscRevolutionTracker & Event-Time Rollover Preservation

**Ticket**: [ATT-6042](https://issue-tracker/ATT-6042) | **Sub-task**: Pending creation (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-6033](https://issue-tracker/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring (OOP & FP)*)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-21_01_Früh` (Sprint 468)  
**Requirement Mapping**: `REQ-SET-124` (`REQ-SET-124.1`–`REQ-SET-124.4`) | **Test Spec ID**: `TST-SET-126` (`TST-SET-126.1`–`TST-SET-126.3`)  
**Branch**: `feature/ATT-6042` | **Author**: Lane 2 Author (`--as agent1`) | **Date**: 2026-11-21

---

## 1. Requirement Specification (`REQ-SET-124`)

### 1.1 Attributes
* **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit, Integration
* **Localization Parity**: `N/A - 0 string changes` (`0` edits across all `9` locales `res/values*/strings*.xml`)

### 1.2 Functional & Architectural Requirements
1. **Shared Pure-JVM `CscRevolutionTracker` (`REQ-SET-124.1`)**: Extract `CscRevolutionTracker(wheelTimeResolution: Int, crankTimeResolution: Int = 1024, maxIdenticalTicks: Int = 4)` (`0` `android.*` deps) holding wheel/crank baseline state (`last*Valid`, `initWheelRevs`, `last*Revs`, `last*EventTime`, `identical*Time`) and `reset()`. `BTLEBikeDevice` uses `wheelTimeResolution = 1024`; `BTLEBikePowerDevice` uses `wheelTimeResolution = 2048`; both use `crankTimeResolution = 1024` and `maxIdenticalTicks = 4`.
2. **Modular `UINT16` Event-Time Rollover & Counter Wrap (`REQ-SET-124.2`)**: Compute `timeDiff = (eventTime - lastEventTime) & 0xFFFFL` for both wheel and crank so `UINT16` timer rollovers (`65000 -> 500`, `timeDiff = 1036`) evaluate normally instead of dropping a sample, while `timeDiff == 0L` increments `identical*Time` and zeroes speed (`pace = null`) or cadence once `identical*Time >= maxIdenticalTicks` (`4`). Preserve `UINT32` wheel delta (`& 0xFFFFFFFFL`), `revDiff != 0` pace guard (no `Infinity`), `ATT-6162` crank `UINT16` masking + float division + `0.0..250.0` rpm plausibility gate, and unconditional baseline updates.
3. **Driver Delegation & Null-Sensor Guards (`REQ-SET-124.3`)**: Both drivers delegate wheel/crank evaluation to `CscRevolutionTracker` and invoke `tracker.reset()` inside `unregisterSensors()`. `BTLEBikeDevice` preserves null-sensor guards (`mSpeedSensor != null`, `mCadenceSensor != null` for `BTLEBikeSpeedDevice` / `BTLEBikeCadenceDevice`) and `notifyWheelRevolutions` on `timeDiff > 0` wheel updates (`BTLEBikePowerDevice` keeps `mIs*Supported` gates and omits `notifyWheelRevolutions`).
4. **Preserved Contracts (`REQ-SET-124.4`)**: Keep `ATT-6162` tests, `BTLEMeasurementParsingTest`, `HardwareHalIngestionIntegrationTest`, and protected driver fields/signatures intact (`0` `strings*.xml` edits).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (`TST-SET-126.1`)**: *Given* `CscRevolutionTracker` with `lastEventTime = 65000` (or `0xFF00`), *When* `eventTime = 500` (or `0x0100`) arrives, *Then* `timeDiff = 1036` (or `512`) is computed positively for `1024 Hz` and `2048 Hz`, `UINT32` wheel / `UINT16` crank wraps evaluate without spikes, `MAX_IDENTICAL = 4` zeroes coasting, `> 250 rpm` rejects while updating baseline, and `reset()` clears state.
* **Criterion 2 (`TST-SET-126.2`, `TST-SET-126.3`)**: *Given* `BTLEBikeDevice` (`1024 Hz`) and `BTLEBikePowerDevice` (`2048 Hz`), *When* wheel/crank rollover packets, single-sensor subsets, `unregisterSensors()`, and `ATT-6162` tests execute, *Then* `100%` pass with `notifyWheelRevolutions` preserved on `BTLEBikeDevice` only.

---

## 2. Test Specification (`TST-SET-126`)

### Test Case 1: Pure-JVM `CscRevolutionTrackerTest` (`TST-SET-126.1`)
* **Scope**: `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/CscRevolutionTrackerTest.kt`
* **Action**: Exercise `UINT16` event-time wraps (`65000 -> 500` -> `1036`, `0xFF00 -> 0x0100` -> `512`) at `1024 Hz` & `2048 Hz`, `UINT32` wheel wrap, `UINT16` crank wrap (`65535 -> 1`), `MAX_IDENTICAL = 4` zeroing (`speed = 0.0`, `pace = null`, `cadence = 0.0`), `revDiff == 0` pace omission, `> 250 rpm` discard + baseline update, and `reset()`.
* **Expected Result**: Exact floating-point speeds, distances, and cadences with zero dropped rollover samples.

### Test Case 2: `BTLEMeasurementParsingTest` Driver Integration (`TST-SET-126.2`)
* **Scope**: `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/BTLEMeasurementParsingTest.kt`
* **Action**: Re-run all `ATT-6162` & existing parsing tests plus new CSC (`1024 Hz`) and CPS (`2048 Hz`) `UINT16` wheel/crank event-time rollover tests (`65000 -> 500`), `MAX_IDENTICAL = 4` identical-time zeroing, `BTLEBikeSpeedDevice`/`BTLEBikeCadenceDevice` null-sensor safety, and `notifyWheelRevolutions` parity.
* **Expected Result**: All existing + new driver tests pass without regression.

### Test Case 3: Targeted Regression & Governance (`TST-SET-126.3`)
* **Command**: `./gradlew :app:testDebugUnitTest --rerun --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*" --tests "com.atrainingtracker.trainingtracker.integration.HardwareHalIngestionIntegrationTest" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest" --tests "com.atrainingtracker.trainingtracker.build.InterfaceSignatureDriftContractTest" --tests "com.atrainingtracker.trainingtracker.build.DeadCodeEliminationContractTest"`
* **Expected Result**: `100%` pass; `verify_requirement_governance.py` exits `0`.

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-SET-124.1` | Pure-JVM `CscRevolutionTracker` & `1024`/`2048 Hz` config | `TST-SET-126.1` | Unit Test |
| `REQ-SET-124.2` | `UINT16` event-time rollover, counter wraps, `MAX_IDENTICAL = 4`, guards | `TST-SET-126.1`, `TST-SET-126.2` | Unit Test |
| `REQ-SET-124.3` | Driver delegation, null-sensor guards, `notifyWheelRevolutions`, `reset()` | `TST-SET-126.2` | Unit Test |
| `REQ-SET-124.4` | `ATT-6162` & contract parity, `0` string edits | `TST-SET-126.3` | Integration & Contract Test |
