# Stage 2: Requirement & Test Specification - ATT-9344: Fix Mid-Workout Crash Recovery Dropping Lap 1, Abandoning Polar Raw Sidecar .tmp, and Barometer Reset Slope Spike

**Ticket**: `ATT-9344` (`2 SP`, `Bug`, `severity-major`) | **Parent Epic**: `ATT-235` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-20_03_Nacht` (Sprint 467) | **Requirement Mapping**: `REQ-STB-025` | **Test Spec ID**: `TST-STB-025` | **Status**: `[Planned]`  
**Branch**: `feature/ATT-9344` | **Sub-task**: `[Req & Test Spec]` | **Author**: AI Agent 1 (`--as agent1`, Neon) | **Date**: 2026-11-21

---

## 1. Requirement Specification (`REQ-STB-025`)

### 1.1 Problem Statement & Rationale
Mid-workout crash recovery (`RESUME_SERVICE_RECREATION`) saved Pixel 10 run `2026-11-20_173833` (`t = 919 -> 920`) but left three data-integrity bugs: (1) `MyAccumulatorSensor` double-counted `mZeroValue = 1` for `SensorType.LAP_NR`, jumping `1 -> 2` without saving Lap 1 so `Laps.db` mixed full duration (`2742 s`) with Lap-2-only distance (`3508.73 m`); (2) `TrackerService.applyResumedState()` never resumed `PolarRawTelemetryRecorder` (whose `start()` also deletes `.tmp`), leaving `3.33 MB` abandoned in `.polar_raw.csv.gz.tmp`; and (3) uncalibrated restarted barometers (`+20.2 m` step) were turned into a 17 s `+1.2 m/s` ramp by `BarometricAltitudeFilter.filterSlewOnly()`, causing a `+35.0%` ERTS slope spike at `t = 928..937`.

### 1.2 Attributes
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit / Integration
- **`SYS.2` / `SYS.3` Impact (`REQ-PRO-131`)**: None (`STB` is `SWE_DERIVED_PREFIXES`; external BLE/ANT+/HAL interfaces unchanged; reinforces `SYSREQ-TRK-001`, `SYSREQ-TRK-003`, `SYSREQ-TRK-009`, `SYSREQ-DEX-005`).
- **9-Language Localization**: `Localization Parity: N/A - no UI strings`.

### 1.3 Functional & Architectural Requirements
1. **`REQ-STB-025.1` (Seamless `LAP_NR` Continuity Across Crash & User Resume)**: `MyAccumulatorSensor` SHALL normalize its constructor (`mInitialValue = sub(initialVal, zeroValue)`) and `setInitialValue(value)` (`mInitialValue = sub(value, mZeroValue)`) so accumulators with non-zero `mZeroValue` (specifically `SensorType.LAP_NR`, `mZeroValue = 1`) report exact restored `lapNr = N` (never `N + 1`) across both `RESUME_SERVICE_RECREATION` and `RESUME_BY_USER`, while preserving identical math for `mZeroValue == 0` accumulators, `newLap()`, `reset()`, and `resetTo()`. `WorkoutRepository.getResumedWorkoutState()` MUST clamp restored `lapNr >= BANALService.INIT_LAP_NR` (`1`).
2. **`REQ-STB-025.2` (Truncated `.polar_raw.csv.gz.tmp` Salvage, Seamless Resume & Export Fallback)**: `PolarRawSidecarRecovery` / `PolarRawTelemetryRecorder.resumeOrStartActiveSession()` SHALL salvage all complete valid CSV rows (validating each line via `RawTelemetryRow.fromCsvLine(line) != null` to discard crash-truncated partial trailing lines and catching `EOFException`/`ZipException` on truncated GZIP tails) from an existing `.tmp` (or prior `.polar_raw.csv.gz`) via atomic `.salvage` rename into the resumed GZIP stream while preserving original `# metadata:` and original workout start epoch (`now - timeTotal_s * 1000L`). `TrackerService.applyResumedState()` SHALL invoke `resumeOrStartActiveSession()`, and `RawSensorFileExporter.doExport()` SHALL run a defensive `.tmp` salvage fallback when finalized `.polar_raw.csv.gz` is absent.
3. **`REQ-STB-025.3` (Silent Live Barometer Re-Anchoring on Resume)**: `ResumedWorkoutState` SHALL carry optional last persisted finite altitude (`lastAltitudeM = Double.NaN` default, preserving all existing Java constructor overloads). `TrackerService.applyResumedState()` SHALL invoke `BANALService.reanchorBarometricAltitudeOnResume(lastAltitudeM)` to silently re-anchor `AltitudeFromPressureDevice` (using a dedicated silent-pending flag/field when called before the first `TYPE_PRESSURE` event so `mPressureSensorInitialized = true` without calling `calibrate()` or broadcasting `ALTITUDE_CORRECTION_INTENT`) and `AltitudeAndSlopeDevice_ATTFused.ekf` without triggering retroactive DB shifts or ascent/descent hysteresis jumps.
4. **`REQ-STB-025.4` (Post-Workout ERTS Sustained Barometer Bias-Step Detection & Contracts)**: `ErtsPostWorkoutRecomputer` SHALL detect sustained barometric calibration/reset steps (`>= 3` shifted samples exceeding physical climb bounds) on raw un-slewed barometer values BEFORE `BarometricAltitudeFilter.filterSlewOnly()` converts steps into linear ramps, signaling `ExtendedRtsAltitudeSlopeSmoother` to absorb the step into `b_baro` while keeping `h` and `slopePercent` continuous (preserving transient `<= 3`-sample pocket-pulse rejection `REQ-TRK-057.1` and alpine climb slew `REQ-FIL-033.2`). `TrackerService.java <= 3,559` LoC and `WorkoutRepository.kt <= 5,117` LoC MUST hold.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
- **Original Requirement ID & Target**: Net-new `REQ-STB-025` (`REQ-STB-025.1`–`.4`) in `req_process_stability_part2.md`, refining `REQ-TRK-026`, `REQ-TRK-036`, `REQ-TRK-078.4`, `REQ-SET-117`, `REQ-EXP-044`, `REQ-CON-017`, `REQ-TRK-057`, `REQ-FIL-033`, `REQ-FIL-037`.
- **Historical Origin & Commit Trace**: `MyAccumulatorSensor` (`REQ-TRK-078.4`), `PolarRawTelemetryRecorder` (`ATT-8019`, `e8735fcb`), `ExtendedRtsAltitudeSlopeSmoother`/`ErtsPostWorkoutRecomputer` (`ATT-7750`, `ATT-9063`).
- **Root Reason for Existing Formulation**: Prior accumulators assumed `mZeroValue == 0`; raw recorder assumed single-process sessions; `BarometricAltitudeFilter` assumed all jumps were transient pocket spikes.
- **Preservation of Core Invariants**: `mZeroValue == 0` accumulators, `reset()`/`resetTo()`, empty-session sidecar discard (`REQ-SET-117.1`), `ResumedWorkoutState` legacy constructors, `REQ-CON-017` non-retroactive silent resume, `REQ-TRK-057.1` pocket-spike rejection, `REQ-FIL-033.2` alpine climbs, and `TrackerService`/`WorkoutRepository` LoC baselines preserved.

---

## 3. Acceptance Criteria (Given-When-Then) & Invariants
- **AC-1 (`LAP_NR` Continuity)**: *Given* a workout in `lapNr = 1` (or `lapNr = 3`) resuming via `RESUME_SERVICE_RECREATION` or `RESUME_BY_USER`, *When* `applyResumedState()` seeds `LAP_NR`, *Then* `ClockDevice.mLapSensor.value == lapNr` (never `lapNr + 1`), subsequent `newLap()` increments cleanly to `lapNr + 1`, and final `Laps.db` preserves accurate duration, distance, and speed for every lap.
- **AC-2 (Truncated `.tmp` Sidecar Salvage & Export)**: *Given* a crash-truncated `.polar_raw.csv.gz.tmp` (missing GZIP footer + truncated final line) for `fileBaseName`, *When* `resumeOrStartActiveSession()` runs, appends post-crash rows, and `stop()` finishes (OR when `RawSensorFileExporter.doExport()` encounters an abandoned `.tmp`), *Then* a valid `.polar_raw.csv.gz` is produced containing all complete pre-crash rows + post-crash rows (dropping only the partial trailing line) and `PolarRawTelemetryReader.readFile()` parses it cleanly.
- **AC-3 (Silent Live Barometer Re-Anchor)**: *Given* a restarted `AltitudeFromPressureDevice` (both before and after first `TYPE_PRESSURE` event) and `AltitudeAndSlopeDevice_ATTFused` re-anchored via `lastAltitudeM = 430.6 m` while raw ISA pressure reads `450.8 m`, *When* pressure arrives, *Then* both publish `~430.6 m` without `+20.2 m` step, zero `ALTITUDE_CORRECTION_INTENT` broadcast is emitted, and ascent/descent accumulators do not spike.
- **AC-4 (ERTS Step Immunity & Pocket/Alpine Preservation)**: *Given* a raw barometer series with a sustained `+20.2 m` reset step at `t = 920` (with or without a `4 s` restart gap), *When* `ErtsPostWorkoutRecomputer` + `ExtendedRtsAltitudeSlopeSmoother` run, *Then* `|slopePercent| < 5%` across `t = 915..945` (zero `+35%` spike), while a 2-sample `+25 m` pocket spike is still rejected (`REQ-TRK-057.1`) and a genuine `10%` climb is preserved (`REQ-FIL-033.2`).

---

## 4. Test Specification (`TST-STB-025`)
- **`TST-STB-025.1` (`MyAccumulatorSensorTest`, `TrackerServiceCrashRecoveryCharacterisationTest`)**: Verify `MyIntegerAccumulatorSensor(LAP_NR, zeroValue = 1)` constructor inheritance and `setInitialValue(1)`/`setInitialValue(3)` report exact `1`/`3` (never `2`/`4`), `newLap()` -> `2`/`4`, `reset()` -> `1`, `zeroValue = 0` unchanged, and `applyResumedState` covers both `RESUME_SERVICE_RECREATION` and `RESUME_BY_USER` with `lapNr = 0` clamped to `1`.
- **`TST-STB-025.2` (`PolarRawTelemetryRecorderTest`, `RawSensorFileExporterTest`)**: Verify crash-truncated `.polar_raw.csv.gz.tmp` (chopped mid-deflate + partial trailing CSV line) is salvaged by `resumeOrStartActiveSession()` preserving `# metadata:` + all complete pre-crash rows + post-crash rows into valid `.polar_raw.csv.gz`, empty/corrupt `.tmp` leaves zero orphan files, and `RawSensorFileExporter.doExport()` salvages abandoned `.tmp` cleanly.
- **`TST-STB-025.3` (`AltitudeFromPressureDeviceTest`, `AltitudeAndSlopeDevice_ATTFusedTest`)**: Verify `reanchorSilently(lastAltM)` before and after first `TYPE_PRESSURE` event aligns `AltitudeFromPressureDevice` to `lastAltM` with zero `ALTITUDE_CORRECTION_INTENT` broadcast, and `AltitudeAndSlopeDevice_ATTFused.reanchorOnResume(lastAltM)` aligns `ekf` without `+20 m` step or ascent/descent jump.
- **`TST-STB-025.4` (`ErtsPostWorkoutRecomputerTest`, `ExtendedRtsAltitudeSlopeSmootherTest`, `ArchitectureConformanceContractTest`, `InterfaceSignatureDriftContractTest`)**: Replay the Pixel 10 `+20.2 m` step (`t = 919 -> 920`, `4 s` gap) through `resolveCarriageModesAndFilterBaro()` + `ExtendedRtsAltitudeSlopeSmoother.smooth()` asserting `|slopePercent| < 5%` (no `+35%` spike), verify transient 2-sample `+25 m` pocket spike stays rejected & genuine `10%` climb unattenuated, and verify `TrackerService.java <= 3,559` LoC & `WorkoutRepository.kt <= 5,117` LoC.

---

## 5. Traceability Matrix

| Requirement | Description | Test Case | Target Suites | Status |
| :--- | :--- | :--- | :--- | :---: |
| `REQ-STB-025.1` | `MyAccumulatorSensor` `mZeroValue` normalization & `LAP_NR` resume continuity | `TST-STB-025.1` | `MyAccumulatorSensorTest`, `TrackerServiceCrashRecoveryCharacterisationTest` | `[Planned]` |
| `REQ-STB-025.2` | Truncated `.polar_raw.csv.gz.tmp` salvage, resume & export fallback | `TST-STB-025.2` | `PolarRawTelemetryRecorderTest`, `RawSensorFileExporterTest` | `[Planned]` |
| `REQ-STB-025.3` | Silent live barometer re-anchoring on resume (no retroactive broadcast) | `TST-STB-025.3` | `AltitudeFromPressureDeviceTest`, `AltitudeAndSlopeDevice_ATTFusedTest` | `[Planned]` |
| `REQ-STB-025.4` | Pre-slew sustained barometer step detection, ERTS `b_baro` re-anchor & LoC contracts | `TST-STB-025.4` | `ErtsPostWorkoutRecomputerTest`, `ExtendedRtsAltitudeSlopeSmootherTest`, `ArchitectureConformanceContractTest`, `InterfaceSignatureDriftContractTest` | `[Planned]` |
