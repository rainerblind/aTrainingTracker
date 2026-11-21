# Stage 3: Implementation Plan - ATT-9344: Fix Mid-Workout Crash Recovery Dropping Lap 1, Abandoning Polar Raw Sidecar .tmp, and Barometer Reset Slope Spike

**Ticket**: `ATT-9344` (`2 SP`, `Bug`, `MEDIUM` risk, `severity-major`) | **Parent Epic**: `ATT-235` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-20_03_Nacht` (Sprint 467) | **Requirement Mapping**: `REQ-STB-025` (`REQ-STB-025.1`–`.4`) | **Test Spec**: `TST-STB-025` (`TST-STB-025.1`–`.4`)  
**Branch**: `feature/ATT-9344` | **Sub-task**: `[Impl-Plan]` | **Author**: AI Agent 1 (`--as agent1`, Neon) | **Date**: 2026-11-21

---

## 1. Architecture & Layering Overview (SWE.2 / SWE.3)
- **Branch Integration Prerequisite**: Before starting Stage 4 edits, rebase `feature/ATT-9344` cleanly onto latest `sprint/2026-11-20_03_Nacht` (incorporating `ATT-9343` `PolarRawTelemetryRecorder.kt` updates if merged) so `PolarRawTelemetryRecorder.kt` has zero conflicts.
- **Layering & Threading**: Pure-JVM `MyAccumulatorSensor` (`synchronized (this)`), `PolarRawSidecarRecovery` (`Dispatchers.IO`), `RawSensorFileExporter` (export worker thread), `AltitudeFromPressureDevice` / `AltitudeAndSlopeDevice_ATTFused` (`synchronized` sensor/1-Hz scheduler locks), `ErtsPostWorkoutRecomputer` / `ExtendedRtsAltitudeSlopeSmoother` (`Dispatchers.Default` post-workout). Zero UI/Compose/DB schema/strings changes (`UI Consistency: N/A`, `Localization Parity: N/A - no UI strings`).
- **LoC Ceilings**: `TrackerService.java` currently `3,537 / 3,559` LoC (`<= +8` net lines via Kotlin helper calls); `WorkoutRepository.kt` currently `4,953 / 5,117` LoC (`<= +16` net lines).

---

## 2. Atomic Implementation Steps

### Step 1: Seamless `LAP_NR` Continuity & Resumed Altitude Extraction (`REQ-STB-025.1`, `REQ-STB-025.3`)
- **`app/src/main/java/com/atrainingtracker/banalservice/sensor/MyAccumulatorSensor.java`**:
  - Constructor (`:141-142`): change `mInitialValue = initialVal;` -> `mInitialValue = sub(initialVal, zeroValue);` before `newValue(zeroValue);`.
  - `setInitialValue(N value)` (`:149`): change `mInitialValue = value;` -> `mInitialValue = sub(value, mZeroValue);`.
  - Preserves exact identity when `mZeroValue == 0` while fixing `+1` off-by-one for `SensorType.LAP_NR` (`mZeroValue = 1`) across both `RESUME_SERVICE_RECREATION` and `RESUME_BY_USER`.
- **`app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`** & **`WorkoutRepository.kt`**:
  - Add optional `public final double lastAltitudeM` field (`Double.NaN` default) to `ResumedWorkoutState` with a new 12-arg constructor while keeping BOTH existing 7-arg and 11-arg Java constructors delegating unchanged.
  - In `WorkoutRepository.getResumedWorkoutState()`: clamp `laps = cursor.getInt(lapsCol).coerceAtLeast(BANALService.INIT_LAP_NR)` and query last finite `ALTITUDE` from `WorkoutSamplesDatabaseManager` for `baseFileName` (best-effort `try/catch`, fallback `Double.NaN`).

### Step 2: Truncated `.polar_raw.csv.gz.tmp` Salvage, Seamless Resume & Export Fallback (`REQ-STB-025.2`)
- **New `app/src/main/java/com/atrainingtracker/banalservice/devices/bluetooth_le/polar/PolarRawSidecarRecovery.kt`**:
  - Pure helper providing `salvageIntoWriter(sourceFile, writer): SalvageResult(metadata, salvagedRows)` and `recoverAbandonedTmpToFinal(context, fileBaseName): Boolean`:
    - Atomically renames existing `.tmp` (or prior `.polar_raw.csv.gz`) to `.salvage`.
    - Stream-reads via `GZIPInputStream` + `BufferedReader(UTF-8)`, preserving first valid `# metadata:` JSON line, validating every CSV data row via `RawTelemetryRow.fromCsvLine(line) != null` (dropping any crash-truncated partial trailing line), and gracefully catching `EOFException`/`ZipException` at the truncated crash tail.
    - Writes recovered header + valid rows into target writer (or creates final `.polar_raw.csv.gz` when recovering abandoned `.tmp` offline), deletes `.salvage`, and leaves zero orphans if `0` valid rows exist (`REQ-SET-117.1`).
- **`app/src/main/java/com/atrainingtracker/banalservice/devices/bluetooth_le/polar/PolarRawTelemetryRecorder.kt`**:
  - Add `resumeExisting: Boolean = false` constructor flag (`@JvmOverloads` preserved) and `@JvmStatic fun resumeOrStartActiveSession(context, fileBaseName, sessionStartTimeMs, sportId): PolarRawTelemetryRecorder?`.
  - In `start()`, when `resumeExisting == true`, salvage existing `tempFile` (or `targetFile`) via `PolarRawSidecarRecovery` BEFORE opening fresh `tempFile` (never deleting un-salvaged `.tmp`), preserve original `# metadata:`, increment `rowCount` by salvaged rows, then drain `channel`.
- **`TrackerService.java` (`applyResumedState`, `+4` lines)** & **`RawSensorFileExporter.kt`**:
  - In `applyResumedState()` (both `RESUME_SERVICE_RECREATION` and `RESUME_BY_USER` when `!PolarRawTelemetryRecorder.isSessionActive()`), call `PolarRawTelemetryRecorder.resumeOrStartActiveSession(getApplicationContext(), mBaseFileName, originalEpochMs, (int) getSportTypeId())` where `originalEpochMs = Math.max(0L, System.currentTimeMillis() - mTimeTotal_s * 1000L)`.
  - In `RawSensorFileExporter.doExport()`: if `!sidecarFile.exists() || sidecarFile.length() == 0L`, invoke `PolarRawSidecarRecovery.recoverAbandonedTmpToFinal(mContext, exportInfo.fileBaseName)` before graceful skip.

### Step 3: Silent Live Barometer Re-Anchoring & Pre-Slew ERTS Step Absorption (`REQ-STB-025.3`, `REQ-STB-025.4`)
- **`AltitudeFromPressureDevice.java`**: Add `private double mPendingSilentAnchorAltitude = Double.NaN;` and `public synchronized void reanchorSilently(double lastAltitudeM)`. If `baseAltitude` is available, set `mAltitudeCorrection = lastAltitudeM - baseAltitude`, `mPressureSensorInitialized = true`, `mIsCalibrated = true`, publish immediately (ZERO `ALTITUDE_CORRECTION_INTENT` broadcast!). If called before first `TYPE_PRESSURE` event, store `mPendingSilentAnchorAltitude`; apply silently inside `handlePressureMeasurement()` before `initPressureSensor()`. Clear in `shutDown()` and `resetCalibrationState()`.
- **`KinematicAltitudeSlopeEkf.kt`** & **`AltitudeAndSlopeDevice_ATTFused.kt`**: Add `ekf.reanchorOnResume(lastAltitudeM)` (aligns `altitude = lastAltitudeM`, `baroBias = lastBaroAltM - lastAltitudeM` if `hasBarometerSample`, else sets `hasReferenceAnchor = true` so first barometer sample absorbs bias via lines `295-304`) and `AltitudeAndSlopeDevice_ATTFused.reanchorOnResume(lastAltitudeM)` (updates `ekf`, sets `mAscentDescentAnchorAltitude = lastAltitudeM`, publishes `lastAltitudeM`).
- **`BANALService.java`** & **`TrackerService.java` (`+2` lines)**: Add `@JvmStatic`/`public static void reanchorBarometricAltitudeOnResume(double lastAltitudeM)` iterating `cDeviceManager` for `AltitudeFromPressureDevice` and `AltitudeAndSlopeDevice_ATTFused`; invoke from `TrackerService.applyResumedState()` when `Double.isFinite(state.lastAltitudeM)`.
- **`ErtsPostWorkoutRecomputer.kt`** & **`ExtendedRtsAltitudeSlopeSmoother.kt`**:
  - Add pre-slew raw barometer step detector in `ErtsPostWorkoutRecomputer.resolveCarriageModesAndFilterBaro()` running BEFORE `BarometricAltitudeFilter.filterSlewOnly()`: detects jumps `|Δz| >= max(6.0, 0.35 * max(v, 2.0) * dt + 3.0)` confirmed sustained across `>= 3` of the next `4` samples (median shift matches sign & magnitude -> removes cumulative offset prior to slew filter and flags `baroResetStep = true`), leaving transient `<= 3`-sample pocket pulses untouched for `BarometricAltitudeFilter` (`REQ-TRK-057.1`) and leaving climbs `<= 1.2 m/s` untouched (`REQ-FIL-033.2`).
  - Add `baroResetStep: Boolean = false` on `RawAltitudeSample` + fallback sustained-step detector in `ExtendedRtsAltitudeSlopeSmoother.smooth()`, absorbing reset steps directly into `x[2]` (`b_baro`) with `p[2][2] = max(p[2][2], 25.0)` while keeping `x[0]` (`h`) and `x[1]` (`slope`) smooth.

### Step 4: SWE.2 / SWE.3 Docs & Targeted Unit Tests (`Rule 8.8`, `REQ-PRO-114`, `TST-STB-025`)
- Update `docs/architecture.md` (§2.3 & §3.3.3 adding `PolarRawSidecarRecovery` and crash-recovery `.tmp` salvage) and `docs/architecture/detailed_design/tracker_service.md` (`applyResumedState` sidecar resume + silent barometer re-anchor).
- Add targeted unit tests tagged `TST-STB-025.1`–`TST-STB-025.4` across `MyAccumulatorSensorTest`, `TrackerServiceCrashRecoveryCharacterisationTest`, `PolarRawTelemetryRecorderTest`, `RawSensorFileExporterTest`, `AltitudeFromPressureDeviceTest`, `AltitudeAndSlopeDevice_ATTFusedTest`, `ErtsPostWorkoutRecomputerTest`, `ExtendedRtsAltitudeSlopeSmootherTest`, plus `ArchitectureConformanceContractTest`, `InterfaceSignatureDriftContractTest`, and `DetailedDesignParityContractTest`.

---

## 3. Invariants & Verification Command
- **Invariants**: `TrackerService.java <= 3,559` LoC; `WorkoutRepository.kt <= 5,117` LoC; `ResumedWorkoutState` existing constructors intact; `0` retroactive `ALTITUDE_CORRECTION_INTENT` on resume; `REQ-TRK-057.1` & `REQ-FIL-033.2` preserved; `Rule 4` code lock respected (zero code edits in Stage 3).
- **Tier 1 Verification (`Rule 7.5`, `--rerun --tests`, never `--rerun-tasks`)**: Run all 11 targeted unit & contract suites + `verify_requirement_governance.py` + 4 safety linters in Stage 4.
