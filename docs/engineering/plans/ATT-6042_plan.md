# Stage 3: Implementation Plan - ATT-6042: Shared CscRevolutionTracker & Event-Time Rollover Preservation

**Ticket**: [ATT-6042](https://issue-tracker/ATT-6042) | **Sub-task**: Pending creation (`[Impl-Plan]`)  
**Parent Epic**: [ATT-6033](https://issue-tracker/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring (OOP & FP)*)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-21_01_Früh` (Sprint 468)  
**Requirement Mapping**: `REQ-SET-124` (`REQ-SET-124.1`–`REQ-SET-124.4`) | **Test Mapping**: `TST-SET-126` (`TST-SET-126.1`–`TST-SET-126.3`)  
**Branch**: `feature/ATT-6042` | **Author**: Lane 2 Author (`--as agent1`) | **Date**: 2026-11-21

---

## 1. Problem Description & Traceability
* **Problem**: `BTLEBikeDevice.java` (`236` LoC) and `BTLEBikePowerDevice.java` (`482` LoC) duplicate `9` state fields, `MAX_IDENTICAL = 4`, `unregisterSensors()` resets (`ATT-6162`), and `~90` LoC of wheel/crank evaluation, and both gate on `eventTime > mLastEventTime`, dropping one valid sample per `UINT16` timer rollover (`32 s` CPS wheel / `64 s` CSC).
* **1:1 Traceability**:
  * `REQ-SET-124.1`, `.2` $\leftrightarrow$ `TST-SET-126.1` (Steps 1, 3)
  * `REQ-SET-124.2`, `.3` $\leftrightarrow$ `TST-SET-126.2` (Steps 2, 4)
  * `REQ-SET-124.4` $\leftrightarrow$ `TST-SET-126.3` (Steps 2–5)

---

## 2. System Invariants, Layering & `Rule 8.8` Scope
1. **Invariants**: `wheelTimeResolution` (`1024` CSC vs `2048` CPS), `crankTimeResolution = 1024`, `maxIdenticalTicks = 4`; `0.0..250.0` rpm plausibility gate; `revDiff != 0` pace guard (no `1 / 0.0 = Infinity`); unconditional baseline update on every packet; `BTLEBikeDevice` null-sensor guards (`BTLEBikeSpeedDevice` / `BTLEBikeCadenceDevice`) & `notifyWheelRevolutions(...)` strictly on `BTLEBikeDevice`; `BTLEBikePowerDevice` `mIs*Supported` gates; protected sensor fields & signatures preserved; `0` `strings*.xml` edits (`Localization Parity: N/A`).
2. **`Rule 8.8` & `REQ-PRO-114` Assessment**: Zero updates required to `docs/architecture.md` or `docs/architecture/detailed_design/` — `CscRevolutionTracker` is an internal driver helper inside the existing `bluetooth_le` peripheral driver package (no new subsystem, service, DB, repository, or GATT connection state-machine change).

---

## 3. Proposed Architectural Changes
### Component 1: `banalservice/devices/bluetooth_le/CscRevolutionTracker.kt` (New, Pure JVM)
* `@JvmOverloads constructor(private val wheelTimeResolution: Int, private val crankTimeResolution: Int = 1024, private val maxIdenticalTicks: Int = 4)` with `0` `android.*` imports and clean Java-interop result types:
  * `enum class WheelStatus { INITIAL_BASELINE, COMPUTED, IDENTICAL_TICK_ZEROED, NO_UPDATE }` + `data class WheelUpdate(val status: WheelStatus, val speedMps: Double = 0.0, val paceSpm: Double? = null, val distanceMeters: Double = 0.0, val revDiff: Long = 0L)`
  * `enum class CrankStatus { INITIAL_BASELINE, COMPUTED, IDENTICAL_TICK_ZEROED, IGNORED_OUT_OF_RANGE, NO_UPDATE }` + `data class CrankUpdate(val status: CrankStatus, val cadenceRpm: Double = 0.0)`
* **Rollover & Plausibility Math**:
  * `timeDiff = (eventTime - lastEventTime) and 0xFFFFL` (`0L` $\rightarrow$ `identical*Time++`, emit `IDENTICAL_TICK_ZEROED` when `>= maxIdenticalTicks` (`4`) else `NO_UPDATE`; `> 0L` $\rightarrow$ `identical*Time = 0` and compute).
  * **Wheel (`UINT32`)**: `revDiff = (revs - prevLastWheelRevs) and 0xFFFFFFFFL`; `speedMps = calibrationFactor * revDiff * wheelTimeResolution / timeDiff`; `paceSpm = if (revDiff != 0L) 1.0 / speedMps else null`; `distanceMeters = calibrationFactor * ((revs - initWheelRevs) and 0xFFFFFFFFL)`.
  * **Crank (`UINT16`)**: `revDiff = (revs - prevLastCrankRevs) and 0xFFFFL`; `cadenceRpm = (60.0 * revDiff * crankTimeResolution) / timeDiff`; emit `COMPUTED` iff `cadenceRpm in 0.0..250.0`, else `IGNORED_OUT_OF_RANGE`.
  * Unconditionally update `last*Revs`, `last*EventTime`, `last*Valid = true`; expose `reset()` clearing all `last*`, `initWheelRevs`, and `identical*` state.

### Component 2: `BTLEBikeDevice.java` & `BTLEBikePowerDevice.java`
* Replace the `9` duplicated tracking fields and `MAX_IDENTICAL` with `protected final CscRevolutionTracker mCscRevolutionTracker = new CscRevolutionTracker(1024);` (`BTLEBikeDevice`) and `new CscRevolutionTracker(2048);` (`BTLEBikePowerDevice`).
* `unregisterSensors()`: invoke `super.unregisterSensors(); mCscRevolutionTracker.reset();`.
* `measurementCharacteristicUpdate`:
  * **BTLEBikeDevice**: Guard `if (mSpeedSensor != null)` around `processWheel(...)` (`COMPUTED` $\rightarrow$ update `mSpeedSensor`, `if (revDiff != 0 && paceSpm != null) mPaceSensor.newValue(paceSpm)`, `mDistanceSensor`, `mLapDistanceSensor`, and `mMySensorManager.notifyWheelRevolutions(revDiff, cumulativeWheelRevs, System.currentTimeMillis())`; `IDENTICAL_TICK_ZEROED` $\rightarrow$ `mSpeedSensor.newValue(0.0); mPaceSensor.newValue(null)`). Guard `if (mCadenceSensor != null)` around `processCrank(...)` (`COMPUTED` $\rightarrow$ `mCadenceSensor.newValue(cadenceRpm)`; `IDENTICAL_TICK_ZEROED` $\rightarrow$ `mCadenceSensor.newValue(0.0)`; `IGNORED_OUT_OF_RANGE` $\rightarrow$ `Log.w`).
  * **BTLEBikePowerDevice**: Keep `mIsWheelRevolutionDataSupported` / `mIsCrankRevolutionDataSupported` gates (plus defensive sensor null checks), apply identical `WheelUpdate`/`CrankUpdate` dispatch without `notifyWheelRevolutions`.

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Pre-Check (`Rule 4.1`)**: Confirm `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` returns `0`.
2. **Step 1 (`REQ-SET-124.1`, `.2`)**: Create pure-JVM `CscRevolutionTracker.kt` in `banalservice/devices/bluetooth_le/`.
3. **Step 2 (`REQ-SET-124.3`, `.4`)**: Refactor `BTLEBikeDevice.java` (`1024 Hz`) and `BTLEBikePowerDevice.java` (`2048 Hz`) to delegate packet calculation and `unregisterSensors()` $\rightarrow$ `reset()`.
4. **Step 3 (`TST-SET-126.1`, `.2`)**: Author `CscRevolutionTrackerTest.kt` and expand `BTLEMeasurementParsingTest.kt` with `UINT16` event-time rollover (`65000 -> 500`, `0xFF00 -> 0x0100`), `UINT32` wheel wrap, `MAX_IDENTICAL = 4`, single-sensor null safety, and `notifyWheelRevolutions` parity.
5. **Step 4 (`TST-SET-126.3`)**: Run Tier-1 Gradle tests (`-Dkotlin.daemon.jvm.options=-Xmx3072m`), Python safety linters, and `verify_requirement_governance.py`.

---

## 5. Verification & Rollback Plan
* **Tier-1 Command (`Rule 7.5`)**: `./gradlew :app:testDebugUnitTest --rerun -Dkotlin.daemon.jvm.options=-Xmx3072m --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.CscRevolutionTrackerTest" --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.BTLEMeasurementParsingTest" --tests "com.atrainingtracker.trainingtracker.integration.HardwareHalIngestionIntegrationTest" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest" --tests "com.atrainingtracker.trainingtracker.build.InterfaceSignatureDriftContractTest" --tests "com.atrainingtracker.trainingtracker.build.DeadCodeEliminationContractTest"` + `python3 tools/lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py`.
* **Rollback**: Isolated on `feature/ATT-6042` until Stage 6 `PASS`.
