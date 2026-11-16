# Stage 1 Analysis: ATT-8337 - Post-workout ERTS smoother coerces leading NULL GNSS coordinates to Null Island (0.0, 0.0)

**Ticket**: [ATT-8337](https://jira.example.com/browse/ATT-8337)  
**Parent Epic**: [ATT-166](https://jira.example.com/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-47.2`  
**Branch**: `feature/ATT-8337`  
**Author**: AI Agent 1 (Implementer, Lane 1 Flash)  
**Date**: 2026-11-16  

---

## 1. Problem Statement & Motivation
In real-world workout tracking with `tuning_post_workout_erts_smoothing = true`, recording often begins seconds before the GNSS chipset acquires its first satellite fix. In `WorkoutSamples.db`, leading rows have `LATITUDE = NULL` and `LONGITUDE = NULL`.
* **Current Behavior**: `ErtsPostWorkoutRecomputer.kt` coerces `null` coordinates to `0.0`. `ExtendedRtsOdometrySmoother.kt` anchors the ENU reference origin to `samples.first()` (`0.0, 0.0`, Null Island in the Gulf of Guinea) and calls `ekf.updateGnssPosition(0.0, 0.0)`. Upon the first genuine fix (~5,500 km away), an instantaneous velocity shock (~477 km/s) destabilizes the EKF and backward RTS pass. This overwrites `WorkoutSamples.db` and `WorkoutSummaries.db` with a corrupted track (e.g. 10,196 km total distance, 720,735 m/s peak speed), which poisons exports, `PeriodSummaries.db`, and `WorkoutClusterEngine`.
* **Expected Behavior**: ERTS odometry smoother ignores missing/uninitialized fixes when setting reference origin, anchors `refLat`/`refLon` to the first valid GNSS fix, skips `updateGnssPosition` during missing fix intervals, backwards-extrapolates pre-fix samples near the start location, and enforces an output sanity guard before committing to SQLite.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
1. **Null-to-Zero Coercion**: `ErtsPostWorkoutRecomputer.kt:220-221` maps `row.latitude ?: 0.0` and `row.longitude ?: 0.0` into `RawOdometrySample`, masking missing fixes as valid coordinates at Null Island.
2. **Arbitrary Reference Origin & Unconditional GNSS Ingestion**: `ExtendedRtsOdometrySmoother.kt:230-231` unconditionally selects `refLat`/`refLon` from `samples.first()`. At lines 267-269, it unconditionally invokes `ekf.updateGnssPosition(eastM, northM, acc, ...)`, forcing the EKF to track `(0.0, 0.0)` until the genuine fix at $i = 2$ triggers a 5,500 km displacement shock.
3. **Absence of Output Invariant Guard**: `ErtsPostWorkoutRecomputer.kt:130-137` lacks an integrity gate; non-physical smoothed distance/speed metrics overwrite SQLite unconditionally.
* **Independent Peer Review & Verification**:
  * Audited `ErtsPostWorkoutRecomputer.kt`, `ExtendedRtsOdometrySmoother.kt`, and `TrackerService.java`. Confirmed that `readWorkoutSamples()` correctly parses `null` via `!cursor.isNull(latIdx)`, but `runOdometrySmoothing()` discards nullability via `?: 0.0`.
  * Verified downstream blast radius: `WorkoutRepository.streamSamples()` reads smoothed coordinates; `ExportManager` exports corrupted FIT/GPX/TCX; `TrackerService.endWorkout()` dispatches `TRACKING_FINISHED_INTENT` which triggers `PeriodsRepository.onWorkoutFinished()` and `WorkoutClusterEngine.onWorkoutFinished()` to absorb corrupted distances.

---

## 3. Scope Grounding & Non-Goals
* **In-Scope Goals**:
  1. Support nullable coordinates in `RawOdometrySample` and treat `null`, `(0.0, 0.0)`, and `NaN` as missing GNSS fixes.
  2. Anchor `refLat`/`refLon` to the *first valid GNSS sample*; bypass odometry smoothing if no valid fix exists.
  3. Gate `ekf.updateGnssPosition()` strictly on valid fixes; execute prediction and wheel/yaw updates during gaps.
  4. Implement an output sanity guard in `ErtsPostWorkoutRecomputer` aborting commit if distance or speed diverges non-physically.
* **Out-of-Scope Non-Goals**:
  1. Modifying live causal filters (`UnicycleOdometryEkf.kt`, `KinematicAltitudeSlopeEkf.kt`).
  2. Altering raw source-attributed device columns (`LATITUDE (GPS)`, `SPEED_mps (att_fused)`).
  3. Modifying `WorkoutClusterEngine` clustering heuristics or `PeriodSummaries` aggregations.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-FIL-017` (*Post-Workout ERTS Zero-Phase Smoothing Engine*).
* **Historical Origin & Commit Trace**: Introduced in `ATT-7750` (Sprint 2026-46.6, commit `d56b001a`) extending `REQ-FIL-007`..`REQ-FIL-010`.
* **Root Reason for Existing Formulation**: `ATT-7750` assumed continuous GNSS lock across the entire sample window and used `samples.first()` as the local tangent-plane origin without guarding for cold-start GNSS acquisition latency.
* **Preservation of Core Invariants**: Retains strict column scope invariant (`ALLOWED_CANONICAL_COLUMNS`, device columns untouched), backward RTS recursion math, and opt-in Expert Settings gating (`KEY_POST_WORKOUT_ERTS_SMOOTHING`).

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-FIL-017` / Net-new `REQ-FIL-019` (*Robust Post-Workout ERTS Odometry Fix Gating & Sanity Guard*, refined from `SYSREQ-TRK-003`, `SYSREQ-TRK-004`).
* **Affected Architecture Elements**: `ErtsPostWorkoutRecomputer.kt`, `ExtendedRtsOdometrySmoother.kt`, `RawOdometrySample`.
* **Verification Impact & Test Surface**: Net-new `TST-FIL-019` unit test suite covering leading null fixes, mid-workout GNSS gaps, and divergent output abort guard.
* **Risk Assessment & Invariants**: Low risk; pure algorithmic fix localized to post-workout recomputation. Bounded by existing `postWorkoutErtsSmoothingEnabled` toggle.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Device-attributed columns in `WorkoutSamples.db` remain 100% untouched.
  2. Standstill segments and valid fixes retain zero-phase ERTS smoothing accuracy.
  3. Workouts lacking GNSS fixes gracefully skip odometry smoothing without crashing.
* **Risk Rating**: `LOW` — localized mathematical fix with fallback to live raw data on anomaly.
