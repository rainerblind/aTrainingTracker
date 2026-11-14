# Stage 5: Walkthrough & Verification - ATT-131: 4-State Odometry EKF Engine: GNSS + ANT+/BLE Wheel Speed + Quaternion-Projected Gyro Yaw-Rate Fusion & Wheel Auto-Calibration

**Ticket**: [ATT-131](https://rainerblind.atlassian.net/browse/ATT-131)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.6`  
**Requirement Mapping**: `REQ-FIL-007`, `REQ-FIL-008`, `REQ-FIL-009`, `REQ-FIL-010`  
**Test Mapping**: `TST-FUSION-001` (`TST-FUSION-001.1`, `TST-FUSION-001.2`, `TST-FUSION-001.3`), `TST-FUSION-002` (`TST-FUSION-002.1`), `TST-FUSION-003` (`TST-FUSION-003.1`)  
**Branch**: `feat/ATT-131`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Executive Summary & Verification Overview

ATT-131 constructs the mathematical foundation for coupled sensor fusion in `com.atrainingtracker.banalservice.odometry`:
1. **Core Data Models (`odometry/`)**: `SpeedMeasurement.kt` (supporting front wheel, rear wheel, GNSS Doppler sources), `OdometryState.kt` (5-state container $[E, N, v, \psi, b_{\omega_z}]^T$, geographic bearing, standard deviations), and `OdometrySnapshot.kt` (prior/posterior state, covariance, and transition Jacobian $F_k$ for offline RTS smoothing).
2. **Quaternion Gyro Projection (`QuaternionGyroProjector.kt`)**: Closed-form quaternion rotation $\vec{\omega}_{ENU} = q \otimes \vec{\omega}_{body} \otimes q^*$ projecting `Sensor.TYPE_GAME_ROTATION_VECTOR` and `Sensor.TYPE_GYROSCOPE` into horizontal world yaw-rate $\omega_{ENU,z}$. Features zero trigonometric heap allocations in the inner loop and $< 2^\circ$ integrated turn error across pitch/roll tilts up to $90^\circ$. Strictly prohibits linear acceleration double-integration.
3. **Gated Wheel Circumference Auto-Calibration (`WheelCircumferenceCalibrator.kt`)**: Scalar Kalman filter estimating effective circumference $C_{wheel}$ gated by GNSS accuracy $\le 6.0\text{ m}$, speed $\ge 4.0\text{ m/s}$, $|\omega_{ENU,z}| < 3.0^\circ/\text{s}$, and $\Delta s_{3D} \ge 20\text{ m}$, converging within $< 0.3\%$ of ground truth over 1,000m of straight riding without interfering with `IndirectTpmsEstimator`.
4. **5-State Unicycle EKF Engine (`UnicycleOdometryEkf.kt`)**: Kinematically coupled 5-state unicycle filter operating on tangent ENU coordinates. Propagates state via midpoint turn rate $(\omega_{ENU,z} - b_{\omega_z})$. Features sequential scalar speed updates with Huber innovation down-weighting ($\gamma = 2.5$) for sprint slip / brake lockup immunity, 2D GNSS position updates gated by accuracy $\le 20.0\text{ m}$, and Doppler course-over-ground heading anchoring ($v > 3.0\text{ m/s}$).
5. **Deterministic High-Fidelity Simulation (`OdometrySimulationTest.kt`)**: Validates 45-second curved tunnel GNSS outage dead-reckoning ($< 1.0\%$ distance error, smooth exit transition), dense-canopy switchback immunity (gating $\sigma_{acc} = 30\text{ m}$ fixes), and sprint slip / brake lockup Huber outlier rejection.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-FIL-007` | `TST-FUSION-001.2` | Unit Test (`UnicycleOdometryEkfTest`) | **PASSED** | `Verified` |
| `REQ-FIL-008` | `TST-FUSION-001.1` | Unit Test (`QuaternionGyroProjectorTest`) | **PASSED** | `Verified` |
| `REQ-FIL-009` | `TST-FUSION-002.1` | Simulation Test (`OdometrySimulationTest`) | **PASSED** | `Verified` |
| `REQ-FIL-010` | `TST-FUSION-003.1` | Unit Test (`WheelCircumferenceCalibratorTest`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Regression Scope
- Command: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.odometry.*" --max-workers=2`
- Test Suites:
  - `QuaternionGyroProjectorTest`: 8 tests passed (identity, 45° pitch, 90° vertical pitch, 90° landscape roll, 90° & 180° integrated turns $< 2^\circ$, array overloads, 3D buffer).
  - `WheelCircumferenceCalibratorTest`: 6 tests passed (accuracy gating, speed gating, yaw rate gating, 20m interval threshold, $< 0.3\%$ convergence over 1km, reset).
  - `UnicycleOdometryEkfTest`: 8 tests passed (initialization, straight prediction, curved prediction, sequential speed fusion, Huber slip rejection, GPS position gating, Doppler heading anchoring, snapshot capture).
  - `OdometrySimulationTest`: 3 tests passed (45s curved tunnel outage $< 1.0\%$ error, dense canopy switchbacks, sprint slip & brake lockup).
- Total: 25 tests executed, 25 passed, 0 failures, 0 skipped.
- Requirement Governance: `python3 tools/verify_requirement_governance.py` passed with 100% parity.

---

## 4. Hardware / Physical Verification & Invariants

- Non-UI pure JVM algorithmic sensor fusion engine.
- Zero third-party linear algebra or matrix dependencies (closed-form pure Kotlin).
- Magnetometer-free orientation via `Sensor.TYPE_GAME_ROTATION_VECTOR`.
- Strictly no raw linear accelerometer double-integration into velocity or position.
- Target Release Governance: Aligned to active release `V5.0.0` (Core Rule 8, Rule 66).
