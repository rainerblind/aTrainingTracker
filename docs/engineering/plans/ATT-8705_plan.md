# Stage 3: Implementation Plan - ATT-8705: Post-Workout ERTS Odometry & Altitude Collapse

**Ticket**: [ATT-8705](https://rainerblind.atlassian.net/browse/ATT-8705) (`3 SP`, `severity-major`) | **Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) | **Target Release**: `V5.0.0` | **Sprint**: `sprint/2026-11-17_03_Nacht`  
**Requirement Mapping**: `REQ-FIL-025` (`REQ-FIL-025.1`..`REQ-FIL-025.4`) | **Test Mapping**: `TST-FIL-025` (`TST-FIL-025.1`..`TST-FIL-025.5`)  
**Branch**: `feature/ATT-8705` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-18

---

## 1. Problem Description & Background
Post-workout ERTS smoothing (`ExtendedRtsOdometrySmoother`, `ErtsPostWorkoutRecomputer`) loses `18.4%` distance (`-1,009 m`), freezes speed at `0.0 m/s` (`508 s`), flips heading backwards during `~2.2 m/s` running, triggers false altitude standstill clamping in `ExtendedRtsAltitudeSlopeSmoother`, passes a one-sided `> 3.0x` sanity guard, and leaves `Laps.db` desynchronized from `WorkoutSummaries.db`.

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-FIL-025` (*Post-Workout ERTS Running-Pace Heading Anchoring, 1-Hz Yaw-Rate Anti-Aliasing, Symmetric Sanity Bounds & Laps.db Sync*)
* **Test Mapping**: `TST-FIL-025` (`ExtendedRtsOdometrySmootherTest.kt`, `UnicycleOdometryEkfTest.kt`, `ErtsPostWorkoutRecomputerTest.kt`, `SpeedAndLocationDevice_ATTFusedTest.kt`)

## 3. System Invariants & Preserved Behavior
1. **Live `ATT-8576` EKF Safety (`REQ-FIL-023.1`)**: `UnicycleOdometryEkf.MIN_DOPPLER_SPEED_MPS = 3.0` MUST remain strictly enforced for live `updateDopplerHeading()` (`SpeedAndLocationDevice_ATTFused.kt`), preserving stationary/ZUPT gating.
2. **Altitude Standstill Gating (`REQ-FIL-017.2`)**: `ExtendedRtsAltitudeSlopeSmoother.STANDSTILL_SPEED_THRESHOLD_MPS = 0.3` remains unchanged; accurate ERTS speeds prevent false clamping.
3. **Strict Column & Toggle Scope (`REQ-FIL-017.3`, `REQ-FIL-017.5`)**: Device-attributed columns (`" ("`) remain 100% untouched; `KEY_POST_WORKOUT_ERTS_SMOOTHING = false` default and DB schemas (`DB_VERSION`) remain unchanged.
4. **Parent Human Gate**: Parent `ATT-8705` ends at `Final Review (Human)`.

## 4. Proposed Architectural Changes
* **Component 1 — `ExtendedRtsOdometrySmoother.kt` & `UnicycleOdometryEkf.kt` (`banalservice/odometry`, pure Kotlin)**:
  * Add an offline heading/covariance helper on `UnicycleOdometryEkf` (e.g. `updateOfflineHeading()` / non-negative `x[2] = max(0.0, x[2])` option or speed floor on moving GNSS updates) and/or explicit `allowLowSpeedHeading` / GNSS displacement-chord bearing updates (`atan2(dN, dE)`) when `speed >= 0.8 m/s` and displacement chord `>= 5.0 m`, leaving live `updateDopplerHeading()` gated at `MIN_DOPPLER_SPEED_MPS = 3.0`.
  * Initialize `ExtendedRtsOdometrySmoother.smoothSamples()` heading from the first valid GNSS movement chord (or moving GNSS bearing) rather than hardcoded `PI / 2.0`.
  * Mitigate 1-Hz `WORLD_YAW_RATE_radps` snapshot aliasing in `smoothSamples()` by bounding/attenuating high-frequency swing rates and decoupling excessive 1-Hz gyro torque when GNSS chord/bearing is available, and prevent negative velocity drift (`x[2] < 0`) during forward/backward updates rather than post-hoc zero clamping.
* **Component 2 — `ErtsPostWorkoutRecomputer.kt` & `LapsDatabaseManager.java` (`trainingtracker/database`, `Dispatchers.IO`)**:
  * Read optional `DISTANCE_m` and `LAP_NR` columns in `readWorkoutSamples()`; tighten `isOdometrySane()` with pause-aware symmetric lower (`e.g. 0.5x` or `0.7x` vs valid raw integrated/live distance) and upper (`3.0x`) divergence bounds, aborting SQLite writes on failure to preserve live `att_fused` columns, `WorkoutSummaries.db`, and `Laps.db`.
  * Add transactional `syncLapDistancesAndSpeeds(...)` to `LapsDatabaseManager.java` (updating only `DISTANCE_TOTAL_m` and `SPEED_AVERAGE_mps` per `LAP_NR`, preserving `TIME_TOTAL_s`, `TIME_START`, `NAME`, `DESCRIPTION`) and invoke it from `ErtsPostWorkoutRecomputer.updateWorkoutSummaries()` using smoothed `LAP_NR` segments (with proportional fallback).
* **UI Consistency**: No UI changes (`N/A - no UI strings`).

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (`REQ-FIL-025.1`, `REQ-FIL-025.2` — Offline Heading & Anti-Aliasing)**:
   * Files: `UnicycleOdometryEkf.kt`, `ExtendedRtsOdometrySmoother.kt`
   * Changes: Add offline heading update API + non-negative velocity guard without altering `MIN_DOPPLER_SPEED_MPS = 3.0` live gating; implement first-chord `initialHeadingRad` resolution, GNSS displacement-chord bearing updates at running speeds, and 1-Hz `worldYawRateRadPerSec` snapshot attenuation/gyro-noise adaptation in `smoothSamples()`.
2. **Step 2 (`REQ-FIL-025.3`, `REQ-FIL-025.4` — Sanity Guard & `Laps.db` Sync)**:
   * Files: `ErtsPostWorkoutRecomputer.kt`, `LapsDatabaseManager.java`
   * Changes: Extend `RawSampleRow` with `rawDistanceM` and `lapNr`; implement pause-aware symmetric lower/upper divergence check in `isOdometrySane()`; add transactional `LapsDatabaseManager.syncLapDistancesAndSpeeds()` and call from `ErtsPostWorkoutRecomputer.updateWorkoutSummaries()`.
3. **Step 3 (`TST-FIL-025.1`..`.5` — Offline JUnit/MockK/Robolectric Tests)**:
   * Files: `ExtendedRtsOdometrySmootherTest.kt`, `UnicycleOdometryEkfTest.kt`, `ErtsPostWorkoutRecomputerTest.kt`
   * Changes: Add `~2.2 m/s` (`8:29/km`) multi-lap running test with `±1.2 rad/s` 1-Hz gyro swing noise (asserting monotonic heading, zero `0.0 m/s` speed collapse, accurate distance & `ExtendedRtsAltitudeSlopeSmoother` elevation/ascent), symmetric sanity abort test (paused vs collapsed), multi-lap `Laps.db` sync test, and live `MIN_DOPPLER_SPEED_MPS = 3.0` regression check (`REQ-PRO-049.1` zero `src/main` reads).
   * Command: `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.banalservice.odometry.*" --tests "com.atrainingtracker.banalservice.devices.SpeedAndLocationDevice_ATTFusedTest" --tests "com.atrainingtracker.trainingtracker.database.ErtsPostWorkoutRecomputerTest"`

## 6. Verification & Rollback Plan
* **Verification**: Run Tier-1 targeted JUnit/MockK/Robolectric suites above in Stage 4/5 (`Rule 7.5`); run `python3 tools/verify_requirement_governance.py`.
* **Rollback**: Branch isolation on `feature/ATT-8705` enables clean revert without affecting `sprint/2026-11-17_03_Nacht`.
