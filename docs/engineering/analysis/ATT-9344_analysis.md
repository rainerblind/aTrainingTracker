# Stage 1 Analysis: ATT-9344 - Fix Mid-Workout Crash Recovery Dropping Lap 1, Abandoning Polar Raw Sidecar .tmp, and Barometer Reset Slope Spike

**Ticket**: `ATT-9344` (`2 SP`, `Bug`, `severity-major`) | **Parent Epic**: `ATT-235` (*No Crashes & App Stability*) | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-20_03_Nacht` (Sprint 467) | **Branch**: `feature/ATT-9344` (off `b3ea47f6`) | **Author**: AI Agent 1 (`--as agent1`, Neon) | **Date**: 2026-11-21

---

## 1. Problem Statement & Motivation
During Pixel 10 run `Kurze Runde var 2 #54` (`2026-11-20_173833`, `_id = 2422`), `TrackerService` recovered in `4 s` (`17:53:55 -> 17:53:59`, `t = 919 -> 920`) after an OOM crash (`RESUME_SERVICE_RECREATION`), saving the workout but introducing three data-integrity defects: (1) `LAP_NR` jumped `1 -> 2` without saving Lap 1 while `Laps.db` recorded only `lapNr = 2` with full duration (`2742 s`) and post-crash-only distance (`3508.73 m`, `1.28 m/s`); (2) `2026-11-20_173833.polar_raw.csv.gz.tmp` (`3.33 MB`) was left unfinalized/unattached so `RawSensorFileExporter` skipped export; and (3) barometer altitude jumped `+20.2 m` (`430.6 -> 450.8 m`, `att_fused_altitude_slope` reset to `487.3 m`), causing post-workout ERTS `SLOPE` to spike to `+35.0%` at `t = 928..937`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
1. **Bug 1 — `LAP_NR` Off-By-One (`mZeroValue` Double-Count) on Resume (`MyAccumulatorSensor.java:141-158`)**:
   - `ClockDevice.java:106` constructs `mLapSensor` (`SensorType.LAP_NR`) with `zeroValue = BANALService.INIT_LAP_NR = 1`.
   - `MyAccumulatorSensor` constructor sets `mInitialValue = initialVal` (`1`) AND calls `newValue(zeroValue)` (`mValue = 1`), and `setInitialValue(N)` sets `mInitialValue = value` (`N`). Because `getValue() = add(mInitialValue, mValue)`, normal starts (`START_NORMAL`) mask this via `reset()` (`mInitialValue = sub(1, 1) = 0`), whereas `RESUME_SERVICE_RECREATION` (`TrackerService.java:1909`) calls `BANALService.setInitialSensorValue(LAP_NR, state.lapNr = 1)` without `reset()` -> `getValue() = 1 + 1 = 2` (`N -> N + 1`!).
   - Thus CSV rows switched `LAP_NR = 1` (`t = 0..919`) -> `LAP_NR = 2` (`t = 920..2742`) without `newLap()`, `createNewLap()` persisted only `lapNr = 2` (`2742 s`), and `ErtsPostWorkoutRecomputer.kt:760-773` grouped `deltaDistanceMeters` by `row.lapNr` and overwrote `Laps.db` `lapNr = 2` with Lap-2-only distance (`3508.73 m` / `2742 s = 1.28 m/s`).
2. **Bug 2 — Abandoned `.polar_raw.csv.gz.tmp` (`TrackerService.java:1267, 1867`, `PolarRawTelemetryRecorder.kt:115, 338`)**:
   - `PolarRawTelemetryRecorder.startActiveSession()` is invoked ONLY in `START_NORMAL`, never in `applyResumedState()` / `RESUME_SERVICE_RECREATION`. Even worse, `PolarRawTelemetryRecorder.start()` (`:115-117`) unconditionally deletes an existing `tempFile` if naively restarted! Consequently post-crash raw IMU/RR was lost, `.tmp` stayed truncated without a GZIP footer, and `RawSensorFileExporter.kt:27` skipped export.
3. **Bug 3 — Barometer Calibration Reset (`+20.2 m`) -> Slew Ramp (`+1.2 m/s`) -> ERTS `+35.0%` Slope Spike**:
   - **Live Recovery**: Fresh `AltitudeFromPressureDevice` (`:76`) starts with `mAltitudeCorrection = 0.0` outside the home Known Location geofence (`430.6 -> 450.8 m`), and fresh `AltitudeAndSlopeDevice_ATTFused` (`:77, 284`) initializes `KinematicAltitudeSlopeEkf` to uncalibrated ISA (`487.3 m`) because `ResumedWorkoutState` / `applyResumedState()` never passes the last known workout altitude to re-anchor live barometers silently (without retroactive broadcast).
   - **Post-Workout ERTS (`ErtsPostWorkoutRecomputer.kt:403-408`, `ExtendedRtsAltitudeSlopeSmoother.kt:167-272`)**: `BarometricAltitudeFilter.filterSlewOnly()` converts the permanent `+20.2 m` step into a `17 s` linear `+1.2 m/s` ramp (after 4-sample median delay -> peaking right at `t = 928..937` at `1.2 / 3.4 ≈ +35.3%` -> clamped to `MAX_PHYSICAL_SLOPE = +35.0%`), and `ExtendedRtsAltitudeSlopeSmoother` has tight converged `p[2][2]` (`b_baro` variance) with zero step-discontinuity re-anchoring.
* **Independent Peer Review & Verification**: Confirmed all 3 mechanisms directly in `sprint/2026-11-20_03_Nacht` (`b3ea47f6`): `MyAccumulatorSensorTest.kt` never tested `zeroValue = 1`; `PolarRawTelemetryRecorder.start()` deletes `.tmp` (`:116`); and `TrackerService.java` sits at `3,537 / 3,559` LoC (`+22` LoC ceiling), requiring logic extraction to Kotlin helpers. Complexity confirmed at **`2 SP`** (no split needed per `REQ-PRO-029`).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Fix `MyAccumulatorSensor` `mZeroValue` double-counting (`LAP_NR` stays `N`, not `N + 1`, across resume/recreation) + `laps >= 1` clamp in `WorkoutRepository.getResumedWorkoutState()`.
  * Salvage truncated `.polar_raw.csv.gz.tmp` (and resume recording seamlessly on `applyResumedState()` with original session epoch) plus export-time recovery fallback in `RawSensorFileExporter` / `PolarRawTelemetryRecorder`.
  * Re-anchor live `AltitudeFromPressureDevice` & `AltitudeAndSlopeDevice_ATTFused` silently on resume from last persisted finite altitude, and detect/absorb sustained barometer bias steps in `ErtsPostWorkoutRecomputer` / `ExtendedRtsAltitudeSlopeSmoother` without suppressing transient pocket-spike rejection (`REQ-TRK-057.1`) or real alpine climbs (`REQ-FIL-033.2`).
* **Out-of-Scope Non-Goals**: No UI/strings/DB schema changes; no refactoring of `TrackerService` beyond `<= +10` lines; no modification of `ATT-9343` heap/backpressure fixes (rebase cleanly onto `ATT-9343` before Stage 4 if merged first).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-STB-024` (to be reserved in `STB` / `req_process_stability_part2.md` in Stage 2), refining crash-recovery continuity for `REQ-TRK-026`, `REQ-TRK-036`, `REQ-TRK-078.4`, `REQ-SET-117`, `REQ-EXP-044`, `REQ-CON-017`, `REQ-TRK-057`, `REQ-FIL-033`, and `REQ-FIL-037`.
* **Historical Origin & Commit Trace**: `MyAccumulatorSensor` initial-value inheritance (`REQ-TRK-078.4`); `PolarRawTelemetryRecorder` (`ATT-8019`, `e8735fcb`); `ExtendedRtsAltitudeSlopeSmoother` & `ErtsPostWorkoutRecomputer` (`ATT-7750`, `ATT-9063`).
* **Root Reason for Existing Formulation**: `MyAccumulatorSensor` assumed `mZeroValue == 0`; `PolarRawTelemetryRecorder` assumed single-process workout lifetime; `BarometricAltitudeFilter` treated every jump as a transient pocket pulse.
* **Preservation of Core Invariants**: `zeroValue == 0` accumulators (`DISTANCE_m`, `TIME_*`, `ASCENT`, `DESCENT`) remain mathematically identical; `reset()` & `resetTo()` (`REQ-FIL-031.2`) unchanged; empty-session `.polar_raw.csv.gz` discard (`REQ-SET-117.1`) preserved; transient 1–3 s pocket compression pulses still rejected (`REQ-TRK-057.1`); alpine climb/descent slew unchanged (`REQ-FIL-033.2`).

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-STB-024` (`REQ-STB-024.1`–`REQ-STB-024.4`) in `req_process_stability_part2.md`; zero `SYS.2`/`SYS.3` changes (`STB` is `SWE_DERIVED_PREFIXES`; external BLE/ANT+/HAL interfaces unchanged; reinforces `SYSREQ-TRK-001`, `SYSREQ-TRK-003`, `SYSREQ-TRK-009`, `SYSREQ-DEX-005`).
* **Affected Architecture Elements**: `MyAccumulatorSensor.java`, `WorkoutRepository.kt` (`4,953/5,117` LoC), `TrackerService.java` (`3,537/3,559` LoC), `BANALService.java`, `PolarRawTelemetryRecorder.kt` (+ new `PolarRawSidecarRecovery.kt` helper), `RawSensorFileExporter.kt`, `AltitudeFromPressureDevice.java`, `AltitudeAndSlopeDevice_ATTFused.kt`, `KinematicAltitudeSlopeEkf.kt`, `ErtsPostWorkoutRecomputer.kt`, `ExtendedRtsAltitudeSlopeSmoother.kt`.
* **Verification Impact & Test Surface**: Net-new `TST-STB-024` (`TST-STB-024.1`–`TST-STB-024.4`) extending `MyAccumulatorSensorTest`, `TrackerServiceCrashRecoveryCharacterisationTest`, `PolarRawTelemetryRecorderTest`, `RawSensorFileExporterTest`, `AltitudeFromPressureDeviceTest`, `AltitudeAndSlopeDevice_ATTFusedTest`, `ErtsPostWorkoutRecomputerTest`, `ExtendedRtsAltitudeSlopeSmootherTest`, plus `ArchitectureConformanceContractTest` & `InterfaceSignatureDriftContractTest`.
* **Risk Assessment & Invariants**: **MEDIUM** — touches live crash-recovery accumulators, GZIP sidecar I/O, and ERTS barometer step detection. Mitigated by preserving `zeroValue == 0` identity, atomic `.salvage` rename preventing data loss, non-broadcasting silent barometer re-anchor on resume (preventing retroactive shift over pre-crash samples), and distinguishing sustained bias steps (`>= 3` shifted samples) from transient pocket spikes (`<= 3` samples).

---

## 6. Architectural Strategy & High-Level Solution
1. **Lap Continuity**: Fix `MyAccumulatorSensor` constructor (`mInitialValue = sub(initialVal, zeroValue)`) and `setInitialValue` (`mInitialValue = sub(value, mZeroValue)`); clamp `laps >= BANALService.INIT_LAP_NR` in `WorkoutRepository.getResumedWorkoutState()`.
2. **Polar Raw Sidecar Salvage & Resume**: Extract `PolarRawSidecarRecovery.kt` to salvage complete lines from truncated `.tmp` via `GZIPInputStream` (catching `EOFException`/`ZipException`), wire `PolarRawTelemetryRecorder.resumeOrStartActiveSession(...)` in `TrackerService.applyResumedState()` (original start epoch `now - timeTotal_s * 1000L`), and add export-time salvage fallback in `RawSensorFileExporter.doExport()`.
3. **Barometer Re-Anchoring & ERTS Step Resilience**: Extend `ResumedWorkoutState` (preserving existing `@JvmOverloads`/Java constructors!) with optional `lastAltitudeM` (`Double.NaN` default) -> call `BANALService.reanchorBarometricAltitudeOnResume(lastAltM)` silently anchoring `AltitudeFromPressureDevice` and `AltitudeAndSlopeDevice_ATTFused.ekf`; add sustained-step barometer bias detection/re-anchoring in `ErtsPostWorkoutRecomputer` + `ExtendedRtsAltitudeSlopeSmoother` (absorbing step into `b_baro` while keeping `h` & `slope` smooth).

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: `TrackerService.java <= 3,559` LoC; `WorkoutRepository.kt <= 5,117` LoC; `ResumedWorkoutState` existing Java constructor overloads preserved; `REQ-CON-017` no retroactive broadcast on resume re-anchor; `REQ-TRK-057.1` transient pocket pulses still rejected; `Localization Parity: N/A - no UI strings`; Rule 3 Human Gate enforced.
* **Risk Rating**: **MEDIUM** — localized across three independent crash-recovery subsystems with deterministic unit test coverage and zero UI/schema blast radius.
