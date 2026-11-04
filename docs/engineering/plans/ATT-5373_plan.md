# Stage 3: Implementation Plan - ATT-5373: Prevent Initial DESCENT and SLOPE Jump on Altitude Source Switch

**Ticket**: [ATT-5373](https://rainerblind.atlassian.net/browse/ATT-5373)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.5` | **Branch**: `feature/ATT-5373`  
**Requirement Mapping**: `REQ-FIL-012.5`, `REQ-FIL-012.6`  
**Test Mapping**: `TST-FIL-004.4`, `TST-FIL-004.5`, `TST-REG-001`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-04  

---

## 1. Problem Description & Background
In Workout #455, switching from GPS WGS84 ellipsoid height (492.0 m) to Open-Meteo DEM elevation (438.0 m) during warmup triggered:
1. `+22.54 m` artificial `DESCENT` at second 3 (`distance = 0.0 m`) due to un-synchronized 300s `cAltitudeSuperFilter`.
2. `-59.0 %` to `-18.1 %` artificial `SLOPE` and `-3.0 m/s` `VERTICAL_SPEED` drop during the first 20m of riding (seconds 11–16) while `mAltitudeHistory` (15s ring buffer) flushed stale 492 m samples.
3. Latent barometric recalibration flaw: `mAltitudeCorrectionReceiver` in `VerticalSpeedAndSlopeDevice` shifted local variables but never shifted `cAltitudeFilter` or `cAltitudeSuperFilter` in `FilterManager`.

---

## 2. Traceability & Requirements Mapping
* **`REQ-FIL-012.5` (Active Altitude Source Transition Synchronization & Re-seeding)** $\leftrightarrow$ `TST-FIL-004.4`: Re-seed `cAltitudeFilter`, `cAltitudeSuperFilter`, `mLastAltitudeSuperFiltered`, and `mAltitudeHistory` on altitude source device switch.
* **`REQ-FIL-012.6` (Filter Shift & Barometric Calibration Parity)** $\leftrightarrow$ `TST-FIL-004.5`: Synchronize `TimedMovingAverageFilter` sample translation on `ALTITUDE_CORRECTION_INTENT`.
* **`REQ-PRO-001` (Clean-Room Regression)** $\leftrightarrow$ `TST-REG-001`: 100% test pass rate with zero regressions.

---

## 3. System Invariants & Preserved Behavior
1. **Vertical Transport Preservation**: Ski-lift, cable car, and elevator motion ($|\text{raw\_vam}| \ge 150\text{ m/h}$) at low horizontal speed remains 100% active.
2. **Mathematical Slope Invariance**: Linear regression slope is strictly invariant under uniform vertical sample shifts $\Delta$.
3. **Filter Thread Safety**: Monitor synchronization on filter instances is preserved across concurrent background sensor ingestion threads.

---

## 4. Proposed Architectural Changes
* **Filter Pipeline (`TimedMovingAverageFilter.java`, `FilterManager.java`, `BANALService.java`)**:
  - Add `synchronized void shiftValues(double delta)` and `synchronized void reseed(Number value)` to `TimedMovingAverageFilter`.
  - Expose `shiftFilter(FilterData, double)` and `reseedFilter(FilterData, Number)` in `FilterManager` and static bridge methods in `BANALService`.
* **Sensor Driver (`VerticalSpeedAndSlopeDevice.java`)**:
  - Unwrap `ProxySensor.getBestSensor()` from `mMySensorManager.getSensor(SensorType.ALTITUDE)` to track `mLastAltitudeSourceDevice` (Rule 68).
  - On source device change, retrieve the new source's raw altitude, re-seed `cAltitudeFilter` and `cAltitudeSuperFilter`, update `mLastAltitudeSuperFiltered`, and fill `mAltitudeHistory` uniformly.
  - In `mAltitudeCorrectionReceiver`, shift `cAltitudeFilter` and `cAltitudeSuperFilter` by `deltaOffset`.
* **UI Consistency (Rule 23)**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Filter Shift & Re-seed Extensions (`TimedMovingAverageFilter.java`, `FilterManager.java`, `BANALService.java`)
* Files: `TimedMovingAverageFilter.java`, `FilterManager.java`, `BANALService.java`
* Add `shiftValues(double delta)` and `reseed(Number value)` to `TimedMovingAverageFilter`; expose delegators in `FilterManager` and `BANALService`.

### Step 2: Source Switch Detection & Re-seeding (`VerticalSpeedAndSlopeDevice.java`)
* Files: `VerticalSpeedAndSlopeDevice.java`
* Track `mLastAltitudeSourceDevice` via unwrapped `ProxySensor.getBestSensor()`.
* On source transition, invoke `reseedFilter` on `cAltitudeFilter`/`cAltitudeSuperFilter`, update `mLastAltitudeSuperFiltered`, and fill `mAltitudeHistory` uniformly.
* In `mAltitudeCorrectionReceiver`, invoke `shiftFilter` on both filters.

### Step 3: Comprehensive Unit Test Suite (`VerticalSpeedAndSlopeDeviceTest.kt`)
* Files: `VerticalSpeedAndSlopeDeviceTest.kt`
* Add `testStandstillAltitudeSourceTransition_suppressesDescentAndSlopeArtifacts` replicating Workout #455 trace ($492\text{ m} \rightarrow 438\text{ m}$).
* Add `testAltitudeCorrectionIntent_shiftsFiltersWithoutSpuriousDescent`.
* Verify low-speed vertical transport invariants (`TST-FIL-004.1`).

### Step 4: Targeted Verification & Governance Audit
* Run: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.VerticalSpeedAndSlopeDeviceTest" --tests "com.atrainingtracker.banalservice.filters.*"`
* Run: `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit test execution and full regression suite.
* **Rollback**: Clean `git revert` of `feature/ATT-5373`; zero schema migrations or serialized state mutations.
