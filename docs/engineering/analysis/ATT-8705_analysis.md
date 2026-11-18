# Stage 1 Analysis: ATT-8705 - Post-Workout ERTS Odometry & Altitude Collapse

**Ticket**: [ATT-8705](https://rainerblind.atlassian.net/browse/ATT-8705) (`3 SP`, `severity-major`) | **Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) | **Target Release**: `V5.0.0` | **Sprint**: `sprint/2026-11-17_03_Nacht`  
**Branch**: `feature/ATT-8705` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-18

---

## 1. Problem Statement & Motivation
* During Pixel 10 run `Kurze Runde var 2 #52` (`2026-11-17_173305`, `workoutID=2188`, `2,783 s`), post-workout ERTS smoothing (`ErtsPostWorkoutRecomputer` / `ExtendedRtsOdometrySmoother`) lost **1,009 m (-18.4%)** distance (`4,462.91 m` vs live `att_fused` `5,471.91 m`), clamped `SPEED_mps` to `0.00 m/s` for `508 s` with `7.25 m/s` spikes, drifted `BEARING` >90° (`6,318.29 m` haversine zigzag), inflated ERTS ascent/descent (`+87.35/-89.18 m` vs `+76.53/-75.32 m`), and left `Laps.db` (`5,473.32 m`) desynced from `WorkoutSummaries.db` (`4,462.91 m`).

## 2. Root Cause Analysis & Independent Peer Review
* **Independent Peer Review & Verification (`60fd63a8`)**:
  1. **Doppler Gate & Live EKF Safety (`ATT-8576` Chesterton's Fence)**: `UnicycleOdometryEkf.MIN_DOPPLER_SPEED_MPS = 3.0` (`:63, 617`) blocks `updateDopplerHeading()` at running paces (`~2.2 m/s`, `2,782/2,783 s`), while `ExtendedRtsOdometrySmoother.smoothSamples()` (`:245, 261`) hardcodes `initialHeadingRad = PI / 2.0`. **Critical Safety Finding**: Live `SpeedAndLocationDevice_ATTFused.kt:258-337` and `UnicycleOdometryEkfTest.kt:180-186` (`REQ-FIL-023.1`) rely on `MIN_DOPPLER_SPEED_MPS = 3.0` + `applyZeroVelocityUpdate()` (`:340-347`) + `isStationary` cluster snapping (`:423-485`) so noisy GNSS Doppler bearings never spin stationary/walking headings. Lowering `MIN_DOPPLER_SPEED_MPS` globally would destabilize live low-speed tracking; offline ERTS (or non-stationary GNSS chord bearings over multi-second displacements) must anchor heading without weakening live stationary/walking gates.
  2. **1-Hz Yaw-Rate Aliasing & Negative Speed Clamping**: Live `SpeedAndLocationDevice_ATTFused.kt:220-231` integrates 50-Hz gyro into `ekf.predict()` and logs a 1-Hz snapshot to `WORLD_YAW_RATE_radps`. `ExtendedRtsOdometrySmoother.kt:269-274` multiplies that un-integrated 1-Hz snapshot (arm/torso swing noise) by `dtSec ~ 1.0 s`, driving heading backwards (>90°). With backwards heading, `ekf.updateGnssPosition()` (`UnicycleOdometryEkf.kt:205,209,572-577`) pushes `x[2]` negative via `p[0..1][2]` cross-covariance; `ExtendedRtsOdometrySmoother.kt:192,219` clamps `max(0.0, smoothedStates[k][2])` -> `0.0 m/s` -> `deltaDistanceMeters = 0.0`.
  3. **Altitude Collateral Coupling**: `ErtsPostWorkoutRecomputer.kt:254,259` passes `smoothedOdometry[idx].speedMps` to `ExtendedRtsAltitudeSlopeSmoother.kt:54,75,130,295-318`, where `speed < 0.3 m/s` (`STANDSTILL_SPEED_THRESHOLD_MPS`) triggers `dh=0, Qg=0` standstill clamping during false `0 m/s` freezes and step-rebounds after.
  4. **One-Sided Sanity Guard & `Laps.db` Desync**: `ErtsPostWorkoutRecomputer.isOdometrySane()` (`:80, 574-588`) only rejects `cumulativeDist > approxRawDist * 3.0` (no lower bound; note `approxRawDist = rawAvgSpeed * durationSec` must handle pauses carefully). `TrackerService.java:2221,2490` saves `Laps.db` before ERTS runs, and `ErtsPostWorkoutRecomputer.updateWorkoutSummaries()` (`:396-466`) updates `WorkoutSummaries.db` without syncing `LapsDatabaseManager` (`Laps.TABLE`).

## 3. User Scope Grounding (`ATT-1250`)
* **In-Scope**: (1) Offline ERTS heading initialization from first valid GNSS displacement/bearing and low-speed GNSS displacement/speed-scaled heading anchoring without destabilizing live `ATT-8576` stationary/walking EKF; (2) Mitigating 1-Hz yaw-rate aliasing in `ExtendedRtsOdometrySmoother` and preventing negative speed collapse (`x[2] >= 0` / RTS speed protection); (3) Symmetric lower/upper distance divergence checks in `ErtsPostWorkoutRecomputer.isOdometrySane()` preserving live `att_fused` on collapse; (4) Syncing `Laps.db` (`LapsDatabaseManager`) when ERTS updates `WorkoutSummaries.db`.
* **Out-of-Scope**: Altering `SpeedAndLocationDevice_ATTFused` live `ATT-8576` stationary/ZUPT gating, raw device-attributed columns (`REQ-FIL-017.3`), DB schema versions, or `ANT-Android-SDKs`.

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-FIL-007` (`docs/requirements/req_tracking_cockpit.md:954-963`), `REQ-FIL-017` (`:5791-5845`), `REQ-FIL-019.4` (`:6072-6075`), `REQ-FIL-023.1` (`:6549-6552`).
* **Historical Origin & Commit Trace**: `bc4a4109` (`ATT-131`), `d56b001a` (`ATT-7750`), `ed972bad` (`ATT-8337`), `eba66320`/`f6715226` (`ATT-8576`).
* **Root Reason for Existing Formulation**: `REQ-FIL-007` set `v > 3 m/s` Doppler gate for cycling and noisy low-speed GNSS bearing; `REQ-FIL-019.4` guarded only against `> 3x` Null-Island distance blowups.
* **Preservation of Core Invariants**: Preserves live `UnicycleOdometryEkf` stationary/ZUPT gating (`REQ-FIL-023.1` & `UnicycleOdometryEkfTest`), `REQ-FIL-009` tunnel dead reckoning, `REQ-FIL-017.3` device-column immutability, and `KEY_POST_WORKOUT_ERTS_SMOOTHING = false` default.

## Change Impact Analysis
* **Affected Requirements**: Refines `REQ-FIL-007`, `REQ-FIL-017`, `REQ-FIL-019.4` (net-new `REQ-FIL-025` for ERTS running-pace heading anchoring, yaw-rate anti-aliasing, symmetric sanity bounds, and `Laps.db` sync).
* **Affected Architecture Elements**: `ExtendedRtsOdometrySmoother.kt`, `UnicycleOdometryEkf.kt` (backward-compatible offline/non-stationary APIs), `ErtsPostWorkoutRecomputer.kt`, `LapsDatabaseManager.java` (lap distance/speed sync helper).
* **Verification Impact & Test Surface**: `ExtendedRtsOdometrySmootherTest.kt`, `UnicycleOdometryEkfTest.kt`, `ErtsPostWorkoutRecomputerTest.kt`, `SpeedAndLocationDevice_ATTFusedTest.kt`, `OdometrySimulationTest.kt` (`N/A - no UI strings`).
* **Risk Assessment & Invariants**: **MEDIUM (`3 SP`)**. Must preserve live stationary/low-speed GNSS stability (`ATT-8576`), handle workouts with pauses in symmetric sanity bounds, and keep `Laps.db` consistent with `WorkoutSummaries.db` on `WorkoutSamples.db` `LAP_NR` boundaries.

## 6. Architectural Strategy & High-Level Solution
1. **Heading Init & Low-Speed Anchoring (`ExtendedRtsOdometrySmoother` / `UnicycleOdometryEkf`)**: Initialize `initialHeadingRad` from the first valid GNSS movement chord (`>= 5 m` displacement or moving bearing) instead of `PI / 2.0`; in offline ERTS replay (when `speed >= 0.8 m/s` and GNSS displacement chord confirms motion), anchor heading using GNSS displacement-chord bearing (and/or speed-weighted Doppler heading in `ExtendedRtsOdometrySmoother`) while keeping `UnicycleOdometryEkf.MIN_DOPPLER_SPEED_MPS = 3.0` intact for live `updateDopplerHeading()`.
2. **1-Hz Yaw-Rate Guard & Non-Negative Speed**: In `ExtendedRtsOdometrySmoother`, down-weight or attenuate noisy 1-Hz instantaneous `worldYawRateRadPerSec` (higher gyro process noise `Q_GYRO` / yaw-rate clamping when GNSS heading is active, or position-velocity decoupling if heading diverges) and prevent `x[2] < 0` in forward/backward RTS rather than zero-clamping collapsed velocities.
3. **Symmetric Sanity Guard & `Laps.db` Sync (`ErtsPostWorkoutRecomputer` + `LapsDatabaseManager`)**: Compute valid raw reference distance (from `DISTANCE_m` or non-paused GNSS/speed integration) and reject smoothed distance diverging below e.g. `0.85x` (or `< 0.5x`) as well as `> 3.0x`; upon commit, recalculate/sync `Laps.db` lap distances and average speeds from smoothed `WorkoutSamples.db` `LAP_NR` segments (or proportional scaling fallback).

## 7. System Invariants & Risk Assessment
* **Core Invariants**: Live `att_fused` stationary/walking behavior (`ATT-8576`) unchanged; raw device columns untouched (`REQ-FIL-017.3`); single-transaction SQLite consistency across `WorkoutSamples.db`, `WorkoutSummaries.db`, and `Laps.db`.
* **Risk Rating**: **MEDIUM** — touches shared `UnicycleOdometryEkf` / ERTS math and multi-DB post-workout finalization, mitigated by keeping live EKF thresholds isolated and enforcing symmetric sanity rollback.
