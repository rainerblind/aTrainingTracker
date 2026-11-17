# Stage 1 Analysis: ATT-8577 - ErtsPostWorkoutRecomputer VERTICAL_SPEED m/h Unit Fix

**Ticket**: `ATT-8577` (`1 SP`, Epic `ATT-166`) | **Branch**: `feature/ATT-8577` | **Date**: 2026-11-17

## 1. Problem Statement & Motivation
* `SensorType.VERTICAL_SPEED` (`SensorType.java:381`) defines internal storage unit `"m/h"` (`SensorValueType.INTEGER`, `IntegerFormatter`, `R.string.units_vertical_speed_basic`), creating SQLite column `VERTICAL_SPEED int` (`WorkoutSamplesDatabaseManager.java:1454-1456`).
* Live producers emit rounded integer `m/h` clamped to `[-3000, 3000]`: `VerticalSpeedAndSlopeDevice.calculateVam()` (`:216-254`, `slopeMps * 3600.0`) and `AltitudeAndSlopeDevice_ATTFused.publishAltitudeAndMetrics()` (`:384-386`, `ekf.getVamMph(speedMps)`).
* `ErtsPostWorkoutRecomputer.bindColumnValue()` (`:376-378`) binds `alt.verticalSpeedMps` (`m/s`) directly via `stmt.bindDouble()`, producing a 3600x unit mismatch in `WorkoutSamples.db`.

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause (`ErtsPostWorkoutRecomputer.kt:376-378`, `ExtendedRtsAltitudeSlopeSmoother.kt:299-309`)**: `ExtendedRtsAltitudeSlopeSmoother.smooth()` outputs `SmoothedAltitudeSlopePoint.verticalSpeedMps` in `m/s` clamped to `[-3.0, 3.0]` m/s (`[-10800, 10800]` m/h). `ErtsPostWorkoutRecomputer.bindColumnValue()` binds `alt.verticalSpeedMps` raw as `Double` instead of converting `* 3600.0`, clamping to live bounds `[-3000.0, 3000.0]`, and binding rounded `Long` (`stmt.bindLong`).
* **Independent Peer Review & Verification (Live `fb0ace9c`)**:
  * **Caller & Extrema Audit**: Single production caller `TrackerService.endWorkout()` (`:2490`). `VERTICAL_SPEED` is absent from `TrackerService.SENSORS_TO_TRACK` (`:165-183`), `WorkoutRepository.calculateAndSaveExtrema()` (`:1473-1477`), and `ErtsPostWorkoutRecomputer.updateExtrema()` (`:463-518`)—so `WorkoutSummaries.db` `Extrema` rows are unaffected.
  * **Aftermath & Exporter Audit**: `WorkoutRepository` (`:2179-2217, 2659-2709`), `FitFileWriter.kt`, `TCXFileWriter.kt`, `GPXFileWriter.java`, and `GCFileWriter.java` do not read `VERTICAL_SPEED`. However, `CSVFileWriter.doExport()` (`:105-144`) inspects `SensorValueType.INTEGER` and calls `cursor.getInt()`, truncating ERTS-written `m/s` values (`-0.65..+0.35`) to `0` in CSV exports.
  * **Clamping & Sibling Audit**: `ExtendedRtsAltitudeSlopeSmoother` clamps `verticalSpeedMps` to `[-3.0, 3.0]` m/s (`[-10800, 10800]` m/h), wider than live `[-3000, 3000]` m/h (`VerticalSpeedAndSlopeDevice:252`, `AltitudeAndSlopeDevice_ATTFused:385`); clamping post-`* 3600.0` in `ErtsPostWorkoutRecomputer` aligns both paths without mutating `SmoothedAltitudeSlopePoint` API.
  * **Tests & `ATT-8474` / `ATT-8579`**: `ErtsPostWorkoutRecomputerTest.kt` (`:130, 264`) seeds `VERTICAL_SPEED` but never asserts recomputed `VERTICAL_SPEED`. `ATT-8474` is an unrelated manual field test (`0` repo refs). `SensorTypeUnitsContractTest.kt:213` asserts `"ATT-8577"` appears in `docs/architecture/sensor_units.md` (`:61, 84`; `SensorType.java:378`).

## 3. User Scope Grounding (ATT-1250)
* **In-Scope**: Convert `alt.verticalSpeedMps * 3600.0`, clamp to `[-3000.0, 3000.0]`, and bind `Math.round(...).toLong()` via `stmt.bindLong()` in `ErtsPostWorkoutRecomputer.bindColumnValue()`; update `ErtsPostWorkoutRecomputerTest.kt` assertions; update `ATT-8577` legacy note in `sensor_units.md` / `SensorType.java` (retaining `"ATT-8577"` token for `SensorTypeUnitsContractTest`).
* **Out-of-Scope**: Migrating historical workouts already recomputed in `WorkoutSamples.db`; changing `SmoothedAltitudeSlopePoint.verticalSpeedMps` unit (`m/s`), `ErtsPostWorkoutRecomputer` `SLOPE` binding (`:374`), or `ExtendedRtsAltitudeSlopeSmoother` math.

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-FIL-017.2` / `REQ-FIL-017.3` (`TST-FIL-017.3`, `docs/requirements/req_tracking_cockpit.md:5805-5824`), `REQ-FIL-012` (`:240`), `REQ-TRK-083` (`:6370-6404`). Targets `ErtsPostWorkoutRecomputer.kt` and `ErtsPostWorkoutRecomputerTest.kt`.
* **Historical Origin & Commit Trace**: `ed6acfee` (`ATT-7750`) introduced `ErtsPostWorkoutRecomputer.kt` and `ExtendedRtsAltitudeSlopeSmoother.kt`; `ed972bad` (`ATT-8337`) added sanity/null guards; `367a412f` (`ATT-8579`) formalized `VERTICAL_SPEED` `"m/h"` in `SensorType.java` and `sensor_units.md`.
* **Root Reason for Existing Formulation**: `ExtendedRtsAltitudeSlopeSmoother` works internally in SI `m/s` (`dh/dt`), and `ATT-7750` bound `alt.verticalSpeedMps` directly into `VERTICAL_SPEED` without consulting `SensorType.VERTICAL_SPEED`'s `m/h` `INTEGER` convention.
* **Preservation of Core Invariants**: `SmoothedAltitudeSlopePoint.verticalSpeedMps` (`m/s`) API, `ALLOWED_CANONICAL_COLUMNS` strict scope (`REQ-FIL-017.3`), device column immutability, `KEY_POST_WORKOUT_ERTS_SMOOTHING` opt-in (`false` default), and `SensorType.ordinal()`/`name()` remain unchanged.

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Refines `REQ-FIL-017.3` (`ErtsPostWorkoutRecomputer` `VERTICAL_SPEED` `m/h` integer conversion & `[-3000, 3000]` clamping) aligned with `REQ-FIL-012` & `REQ-TRK-083`; updates `TST-FIL-017.3` and `docs/architecture/sensor_units.md` (`ATT-8577` fixed status).
* **Affected Architecture Elements**: `ErtsPostWorkoutRecomputer.kt` (`bindColumnValue`); `SensorType.java:378` Javadoc & `docs/architecture/sensor_units.md:61,84` note; `ErtsPostWorkoutRecomputerTest.kt`.
* **Verification Impact & Test Surface**: `ErtsPostWorkoutRecomputerTest.kt` (`m/h` scaling, rounding, `[-3000, 3000]` clamping, negative descent, device-column invariance) and `SensorTypeUnitsContractTest.kt` + `ExtendedRtsAltitudeSlopeSmootherTest.kt`.
* **Risk Assessment & Invariants**: **LOW** (`1 SP`). Single-column binding fix; zero schema changes, zero `ANT-Android-SDKs` edits, zero `ExtendedRtsAltitudeSlopeSmoother` mathematical regression.

## 6. Solution Strategy & System Invariants
* In `ErtsPostWorkoutRecomputer.bindColumnValue()` (`:376-378`), compute `val vamMph = Math.round((alt.verticalSpeedMps * 3600.0).coerceIn(-3000.0, 3000.0))` and call `stmt.bindLong(bindIdx, vamMph)` (preserving `stmt.bindNull(bindIdx)` when `alt == null`).
* **Invariants**: Live `VerticalSpeedAndSlopeDevice` / `AltitudeAndSlopeDevice_ATTFused` `[-3000, 3000]` `m/h` contract matched; raw device-attributed columns 100% untouched; `SensorTypeUnitsContractTest` (`"ATT-8577"` token) preserved.
