# Stage 3: Implementation Plan - ATT-131: 4-State Odometry EKF Engine

**Ticket**: [ATT-131](https://rainerblind.atlassian.net/browse/ATT-131)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.6` | **Branch**: `feat/ATT-131`  
**Requirement Mapping**: `REQ-FIL-007`, `REQ-FIL-008`, `REQ-FIL-009`, `REQ-FIL-010`  
**Test Mapping**: `TST-FUSION-001`, `TST-FUSION-002`, `TST-FUSION-003`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-14  

---

## 1. Problem Description & Background
Android's `FusedLocationProvider` lacks integration with external ANT+/BLE cycling wheel speed and footpod sensors. Under dense forest canopy, deep valleys, sharp switchbacks, or curved tunnels, GNSS fixes cut corners, lag, or drop out completely. A pure Kotlin sensor-fusion engine is required to fuse GNSS position and Doppler velocity, multi-source wheel speed revolutions (front and rear), and magnetometer-free quaternion-projected gyro yaw-rate into a 5-state unicycle EKF with online wheel circumference auto-calibration.

---

## 2. Traceability & Requirements Mapping
* **REQ-FIL-007** (Coupled Unicycle Odometry EKF) $\rightarrow$ `TST-FUSION-001` (`UnicycleOdometryEkfTest.kt`)
* **REQ-FIL-008** (Quaternion Gyro Yaw-Rate Projection) $\rightarrow$ `TST-FUSION-001` (`QuaternionGyroProjectorTest.kt`)
* **REQ-FIL-009** (GPS-Denied Dead Reckoning) $\rightarrow$ `TST-FUSION-002` (`OdometrySimulationTest.kt`)
* **REQ-FIL-010** (Gated Wheel Circumference Auto-Calibration) $\rightarrow$ `TST-FUSION-003` (`WheelCircumferenceCalibratorTest.kt`)

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-FIL-007`..`REQ-FIL-010` in `docs/requirements/req_tracking_cockpit.md`.
* **Historical Origin & Commit Trace**: 2026-06-27 Jira scoping proposing EJML 3D IMU strapdown double-integration; backfilled in commit `79ac0b23` (ATT-6127).
* **Root Reason for Existing Formulation**: Early draft assumed aerospace-style strapdown IMU navigation, which suffers severe vibration rectification (5–50 Hz) and gravity leakage on bicycle handlebars.
* **Preservation of Core Invariants**: Maintain zero external matrix dependencies (zero EJML/numpy/scipy); implement closed-form 5x5 matrix equations in pure Kotlin; preserve `IndirectTpmsEstimator` invariants; enforce magnetometer-free orientation via `Sensor.TYPE_GAME_ROTATION_VECTOR`.

---

## 3. System Invariants & Preserved Behavior
1. **Zero External Matrix Dependencies**: Closed-form matrix operations in pure Kotlin (zero EJML/numpy/scipy).
2. **Prohibition of Linear Acceleration Double-Integration**: Quaternions project angular rate only ($\vec{\omega}_{body} \rightarrow \omega_{ENU,z}$); no linear acceleration integration into position/velocity.
3. **Decoupled Engine**: Core mathematical models in `com.atrainingtracker.banalservice.odometry` have zero UI or DB dependencies in this ticket (device wiring and UI deferred to ATT-7751).
4. **Post-Workout Forward Snapshot API**: Forward states, covariances, and Jacobians ($F_k$) exposed cleanly for post-workout RTS backward smoothing (ATT-7750).
5. **Code Lock & Clean Verification**: No production source code created until Gate 3 is `Erledigt`.

---

## 4. Proposed Architectural Changes
* **Package**: `com.atrainingtracker.banalservice.odometry`
  - `OdometryState`: Data container for $[E, N, v, \psi, b_{\omega_z}]^T$, timestamp, and covariance matrix.
  - `OdometrySnapshot`: Prior/posterior state, covariance, and transition Jacobian $F_k$ for backward RTS smoothing.
  - `SpeedMeasurement`: Typed measurement data class (Front Wheel, Rear Wheel, GNSS Doppler) with source variance.
  - `QuaternionGyroProjector`: Closed-form quaternion rotation $\vec{\omega}_{ENU} = q \otimes \vec{\omega}_{body} \otimes q^*$, returning vertical yaw-rate $\omega_{ENU,z}$.
  - `WheelCircumferenceCalibrator`: Recursive scalar Kalman filter estimating $C_{wheel}$, gated by GNSS accuracy $\le 6\text{ m}$, speed $\ge 4\text{ m/s}$, and $|\omega_{ENU,z}| < 3^\circ/\text{s}$.
  - `UnicycleOdometryEkf`: 5-state unicycle EKF with Huber innovation gating for multi-speed updates and Doppler heading anchoring.
* **UI Consistency**: No UI changes in ATT-131 (device pairing UI and settings deferred to ATT-7751).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Core Data Models & State Containers
* Files: `app/src/main/java/com/atrainingtracker/banalservice/odometry/OdometryState.kt`, `OdometrySnapshot.kt`, `SpeedMeasurement.kt`
* Deliverable: Immutable data classes for state vectors ($E, N, v, \psi, b_{\omega_z}$), covariances, step snapshots, and multi-source speed measurements.

### Step 2: `QuaternionGyroProjector` Implementation & Unit Tests
* Files: `app/src/main/java/com/atrainingtracker/banalservice/odometry/QuaternionGyroProjector.kt`, `app/src/test/java/com/atrainingtracker/banalservice/odometry/QuaternionGyroProjectorTest.kt`
* Deliverable: Vectorized quaternion transformation with zero heap allocations in inner loop; unit test asserting turn-angle parity ($< 2^\circ$ error) across arbitrary phone pitch/roll orientations up to $90^\circ$.
* Test: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.odometry.QuaternionGyroProjectorTest"`

### Step 3: `WheelCircumferenceCalibrator` Implementation & Unit Tests
* Files: `app/src/main/java/com/atrainingtracker/banalservice/odometry/WheelCircumferenceCalibrator.kt`, `app/src/test/java/com/atrainingtracker/banalservice/odometry/WheelCircumferenceCalibratorTest.kt`
* Deliverable: Gated scalar Kalman estimator tracking $C_{wheel}$; unit test verifying gating rejection and convergence within $< 0.3\%$ of ground truth over straight segments.
* Test: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.odometry.WheelCircumferenceCalibratorTest"`

### Step 4: `UnicycleOdometryEkf` Implementation & Unit Tests
* Files: `app/src/main/java/com/atrainingtracker/banalservice/odometry/UnicycleOdometryEkf.kt`, `app/src/test/java/com/atrainingtracker/banalservice/odometry/UnicycleOdometryEkfTest.kt`
* Deliverable: 5-state unicycle prediction, multi-source speed sequential updates with Huber weighting, 2D GNSS position updates, Doppler course anchoring, and snapshot capture.
* Test: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.odometry.UnicycleOdometryEkfTest"`

### Step 5: High-Fidelity Simulation Test Suite
* Files: `app/src/test/java/com/atrainingtracker/banalservice/odometry/OdometrySimulationTest.kt`
* Deliverable: 45-second curved tunnel GNSS outage dead-reckoning test, sharp switchback cornering test, and rear-wheel sprint slip Huber down-weighting test.
* Test: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.odometry.OdometrySimulationTest"`

### Step 6: SWE.2 Architecture & Living Governance Sync
* Files: `docs/architecture.md`
* Deliverable: Update SWE.2 Component Model and Tracking Cockpit requirement allocation table with `QuaternionGyroProjector`, `WheelCircumferenceCalibrator`, `UnicycleOdometryEkf`. Run `python3 tools/verify_requirement_governance.py`.
* Test: `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification Scope**: Tier 1 targeted tests (`./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.odometry.*"`), full package tests, and living documentation governance script.
* **Rollback Plan**: All changes isolated on `feat/ATT-131`; branch can be reset or deleted without impacting `sprint/2026-46.6`.
