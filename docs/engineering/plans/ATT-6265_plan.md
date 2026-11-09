# Stage 3: Implementation Plan - ATT-6265: Barometer Altitude Pocket-Compression Spike Rejection & DB Sync

**Ticket**: [ATT-6265](https://rainerblind.atlassian.net/browse/ATT-6265)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6265`  
**Requirement Mapping**: `REQ-TRK-057` (`REQ-TRK-057.1`..`REQ-TRK-057.3`) | **Test Mapping**: `TST-TRK-057` (`TST-TRK-057.1`..`TST-TRK-057.5`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Background
In pocket carry mode, body motion and fabric compression generate 1-second barometric transients of $\pm 20\text{--}96\text{ m}$, inducing massive false elevation gain (e.g. +1,538 m on flat 2 km ride) and corrupting start calibration offsets. Additionally, retroactive altimeter calibration in `WorkoutSamplesDatabaseManager.applyAltitudeDriftCorrection()` updates only canonical `ALTITUDE`, desynchronizing source-attributed columns (e.g. `"ALTITUDE (Höhe aus Luftdruck)"`).

---

## 2. Traceability & Requirements Mapping
* **`REQ-TRK-057.1`**: Causal non-linear filter (`BarometricAltitudeFilter`: 7-sample sliding median + physical vertical slew-rate limiter at $1.2\text{ m/s}$ + EMA $\alpha = 0.18$) in `AltitudeFromPressureDevice`.
* **`REQ-TRK-057.2`**: Baseline stabilization in `setAltitudeCorrection()` and `calibrate()` anchored to filtered baseline `mFilteredRawAltitude`.
* **`REQ-TRK-057.3`**: Sourced-column synchronization in `WorkoutSamplesDatabaseManager.applyAltitudeDriftCorrection()` across all `^ALTITUDE($| \(.*\)$)` columns.
* **Test Mapping**: `TST-TRK-057.1` (Pulse Rejection), `TST-TRK-057.2` (Climb Preservation), `TST-TRK-057.3` (Calibration Spike Immunity), `TST-TRK-057.4` (Sourced DB Sync), `TST-TRK-057.5` (Targeted Suite).

---

## 3. System Invariants & Preserved Behavior
1. **Mountain Climbing Fidelity**: Real ascents up to 3,000 m/h ($0.83\text{ m/s}$) pass with zero slope attenuation and $< 0.5\text{ m}$ steady-state lag.
2. **IPC & Calibration Contracts**: `ALTITUDE_CORRECTION_INTENT` structure and the 500 m sanity limit (`REQ-CON-017`) remain unchanged.
3. **Database Transaction Safety**: Multi-column drift updates execute within existing atomic SQLite transaction boundaries (`db.beginTransaction()`).
4. **UI Consistency**: Zero UI modifications (`docs/design_guidelines.md` §5). Pure backend telemetry and database fix.

---

## 4. Proposed Architectural Changes
* **`BarometricAltitudeFilter.kt`** (New in `com.atrainingtracker.banalservice.devices`):
  * Maintains 7-sample circular buffer for sliding median outlier rejection.
  * Clamps single-step delta to $|\Delta h| \le 1.2\text{ m/s} \times \Delta t$.
  * Smooths residual noise via EMA ($\alpha = 0.18$).
* **`AltitudeFromPressureDevice.java`**:
  * Feeds raw pressure altitude through `BarometricAltitudeFilter` in `handlePressureMeasurement()`.
  * Computes `mAltitudeCorrection = referenceAltitude - mFilteredRawAltitude` in `setAltitudeCorrection()`.
  * Publishes `mFilteredRawAltitude + mAltitudeCorrection` to `mAltitudeSensor`.
* **`WorkoutSamplesDatabaseManager.java`**:
  * In `applyAltitudeDriftCorrection()`, inspects table columns via `getTableColumnNames(db, samplesTable)` and applies uniform (Case A) and piecewise-linear (Case B) SQL updates to every column matching `^ALTITUDE($| \(.*\)$)`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Implementation Gate Verification
* Verify Gate 3 sign-off: `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>`.

### Step 2: Implement `BarometricAltitudeFilter.kt`
* Implement 3-stage causal pipeline: Sliding Median (size 7) $\to$ Slew-Rate Limiter ($1.2\text{ m/s}$) $\to$ EMA ($\alpha = 0.18$).
* Provide `filter(rawAltitude: Double, timestampMs: Long): Double` and `reset()`.

### Step 3: Wire Filter into `AltitudeFromPressureDevice.java`
* Filter incoming raw altitude in `handlePressureMeasurement()`.
* Anchor `setAltitudeCorrection()` and `calibrate()` to `mFilteredRawAltitude`.

### Step 4: Synchronize Sourced Columns in `WorkoutSamplesDatabaseManager.java`
* Update `applyAltitudeDriftCorrection()` to query column names and update all matched `ALTITUDE` and `ALTITUDE (*)` columns.

### Step 5: Implement Automated Unit Tests
* `BarometricAltitudeFilterTest.kt`: Test 50m pocket spike rejection (`TST-TRK-057.1`) and 0.80 m/s mountain climb preservation (`TST-TRK-057.2`).
* `AltitudeFromPressureDeviceTest.kt`: Test calibration spike immunity during +40m transient (`TST-TRK-057.3`).
* `WorkoutSamplesDatabaseManagerTest.kt`: Test multi-column drift updates (`TST-TRK-057.4`).

### Step 6: Targeted Unit Test Execution
* `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.*" --tests "com.atrainingtracker.trainingtracker.database.*" --max-workers=2`.
