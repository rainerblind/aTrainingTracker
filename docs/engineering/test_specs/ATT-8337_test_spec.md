# Stage 2: Requirement & Test Specification - ATT-8337: Post-Workout ERTS Smoother GNSS Fix Gating & Origin Anchoring

**Ticket**: [ATT-8337](https://rainerblind.atlassian.net/browse/ATT-8337)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-47.2` | **Branch**: `feature/ATT-8337`  
**Requirement Mapping**: `REQ-FIL-019` (*Post-Workout ERTS Smoother GNSS Fix Gating, Origin Anchoring & Output Sanity Guard*)  
**Test Spec ID**: `TST-FIL-019`  
**Author**: AI Agent 2 (Implementer, Lane 1 Pro) | **Date**: 2026-11-16  

---

## 1. Requirement Specification (REQ-FIL-019)

### 1.1 Problem Statement & Rationale
When workouts begin before GNSS satellite lock is acquired, `WorkoutSamples.db` contains leading rows with `LATITUDE = NULL` and `LONGITUDE = NULL`. `ErtsPostWorkoutRecomputer.kt` coerced `null` coordinates to `0.0`, causing `ExtendedRtsOdometrySmoother.kt` to anchor its ENU tangent-plane reference origin at `(0.0, 0.0)` ("Null Island") and feed `(0.0, 0.0)` into `ekf.updateGnssPosition()`. Upon the first genuine fix (~5,500 km away), an instantaneous velocity shock (~477 km/s) and backward RTS spiral oscillation corrupted `WorkoutSamples.db`, `WorkoutSummaries.db` (e.g. 10,196 km total distance), exports, period metrics, and route clusters (`ATT-8337`, extending `REQ-FIL-017`).

### 1.2 Attributes
- **Priority**: Critical
- **Type**: Functional
- **Verification Method**: Unit
- **System Requirement(s)**: `SYSREQ-TRK-003`, `SYSREQ-TRK-004`
- **Implementation**: `ErtsPostWorkoutRecomputer.kt`, `ExtendedRtsOdometrySmoother.kt`, `WorkoutSamplesDatabaseManager.java`, `WorkoutSummariesDatabaseManager.java`

### 1.3 Functional & Architectural Requirements
1. **Preservation of Nullable GNSS Coordinates (`REQ-FIL-019.1`)**: `RawOdometrySample` SHALL declare `latitude: Double?` and `longitude: Double?` (defaulting to `null`). `ErtsPostWorkoutRecomputer` SHALL preserve SQLite `NULL` coordinates and SHALL NEVER coerce `null` to `0.0`. `WorkoutSamplesDatabaseManager` queries SHALL check `cursor.isNull(idx)`.
2. **First Valid GNSS Fix Origin & Fix Gating (`REQ-FIL-019.2`)**: `ExtendedRtsOdometrySmoother.smoothSamples()` SHALL select `refLat`/`refLon` strictly from the *first valid GNSS sample* (`abs(lat) > 1e-6 || abs(lon) > 1e-6`). If no valid GNSS fix exists, odometry smoothing SHALL be bypassed (returning empty list). Forward EKF SHALL ONLY invoke `ekf.updateGnssPosition()` on valid GNSS fixes, executing prediction + wheel/yaw updates during gaps without pulling toward `(0.0, 0.0)`.
3. **No-Fix Row Integrity & Polyline Exclusion (`REQ-FIL-019.3`)**: Leading/trailing rows lacking a valid GNSS fix SHALL remain `NULL` in `WorkoutSamples.db`. `updateWorkoutSummaries()` SHALL exclude missing/invalid fix points (`(0.0, 0.0)` / `null`) from `mapPolyline` generation (`PolyUtil.encode`) and start/end location extrema.
4. **Output Sanity Guard (`REQ-FIL-019.4`)**: Prior to SQLite persistence, `ErtsPostWorkoutRecomputer` MUST validate that smoothed total distance does not diverge by $> 3\times$ from raw distance (or exceed 1,000 km), peak speed is plausible ($< 100\text{ m/s}$), and coordinates are finite within geographic bounds. If sanity fails, the commit SHALL be aborted and raw live tracking data preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* *Given* a workout with leading `NULL` GNSS rows before the first valid fix at Schönaich (`48.6567°, 9.0545°`),
  * *When* `ErtsPostWorkoutRecomputer.recomputeWorkout()` runs,
  * *Then* `refLat`/`refLon` anchors to Schönaich, no `updateGnssPosition(0.0, 0.0)` occurs, leading rows remain `NULL` in `WorkoutSamples.db`, and total distance reflects the true ~2,030 m trajectory.
* *Given* a workout with mid-ride GNSS gaps,
  * *When* `ExtendedRtsOdometrySmoother.smoothSamples()` runs,
  * *Then* gap samples predict without pulling toward `(0.0, 0.0)` and backward RTS bridges the gap smoothly.
* *Given* smoother output with non-finite values or non-physical distance/speed divergence,
  * *When* `ErtsPostWorkoutRecomputer` validates the result,
  * *Then* the transaction aborts, live data in `WorkoutSamples.db` and `WorkoutSummaries.db` is preserved, and an error is logged.

### 1.5 System Invariants
Device-attributed columns in `WorkoutSamples.db` remain 100% untouched (`ALLOWED_CANONICAL_COLUMNS`); workouts lacking GNSS fixes gracefully skip odometry smoothing without crashing; zero Null Island `(0.0, 0.0)` entries in `mapPolyline` or SQLite; 100% test pass rate.

---

## 2. Test Specification (TST-FIL-019)

### Test Case 1: Nullable Coordinates & First Valid Fix Reference Origin (`TST-FIL-019.1`)
* **Scope**: Unit Test (`ExtendedRtsOdometrySmootherTest.kt`).
* **Assertions**: Leading `null`/`0.0` samples do not anchor `refLat`/`refLon`; filter anchors at first genuine GNSS fix; zero velocity shock; 100% null samples sequence returns safely without exception.

### Test Case 2: Mid-Workout GNSS Gap Predict-Only Ingestion (`TST-FIL-019.2`)
* **Scope**: Unit Test (`ExtendedRtsOdometrySmootherTest.kt`).
* **Assertions**: 15s GNSS dropout with 10 m/s wheel speed executes predict-only steps; no pull toward `(0.0, 0.0)`; backward RTS produces continuous smooth trajectory across gap.

### Test Case 3: Leading NULL Preservation & Polyline Exclusion (`TST-FIL-019.3`)
* **Scope**: Unit Test (`ErtsPostWorkoutRecomputerTest.kt`).
* **Assertions**: Recomputed `WorkoutSamples.db` retains SQLite `NULL` for leading pre-fix rows; `mapPolyline` contains zero `(0.0, 0.0)` points; `WorkoutSummaries.db` `START` extrema reflects first genuine fix.

### Test Case 4: Output Sanity Guard Divergence Abort (`TST-FIL-019.4`)
* **Scope**: Unit Test (`ErtsPostWorkoutRecomputerTest.kt`).
* **Assertions**: Simulated corrupted smoother output ($> 1,000\text{ km}$ distance or $> 100\text{ m/s}$ speed) triggers sanity abort; live database rows remain completely intact; error is logged.

### Test Case 5: Tier-1 Targeted Regression & Governance Verification (`TST-FIL-019.5`)
* **Scope**: Regression & Contract Test.
* **Command**: `./gradlew testDebugUnitTest --tests "*ExtendedRts*" --tests "*ErtsPostWorkout*" --tests "*WorkoutSamplesDatabaseManager*"` + `python3 tools/verify_requirement_governance.py`.
* **Assertions**: 100% test pass rate; zero requirement governance violations.

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-FIL-019.1` | Nullable coordinate preservation in `RawOdometrySample` | `TST-FIL-019.1`, `TST-FIL-019.3` | Unit Test |
| `REQ-FIL-019.2` | First valid GNSS fix origin & fix-gated EKF updates | `TST-FIL-019.1`, `TST-FIL-019.2` | Unit Test |
| `REQ-FIL-019.3` | Leading NULL DB preservation & polyline exclusion | `TST-FIL-019.3` | Unit Test |
| `REQ-FIL-019.4` | Output sanity guard & live data fallback | `TST-FIL-019.4` | Unit Test |
| `REQ-FIL-017` / Invariants | Zero regression across existing ERTS smoothing & governance | `TST-FIL-019.5` | Targeted Regression |
