# Stage 2: Requirement & Test Specification - ATT-131: 4-State Odometry EKF Engine

**Ticket**: [ATT-131](https://rainerblind.atlassian.net/browse/ATT-131)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.6` | **Branch**: `feat/ATT-131`  
**Requirement Mapping**: `REQ-FIL-007`, `REQ-FIL-008`, `REQ-FIL-009`, `REQ-FIL-010`  
**Test Spec IDs**: `TST-FUSION-001`, `TST-FUSION-002`, `TST-FUSION-003`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-14  

---

## 1. Requirement Specification

### 1.1 Scope & Attributes
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit
- **Components**: `QuaternionGyroProjector.kt`, `WheelCircumferenceCalibrator.kt`, `UnicycleOdometryEkf.kt` in `com.atrainingtracker.banalservice.odometry`.

### 1.2 Functional & Architectural Requirements
1. **REQ-FIL-007 (Unicycle Odometry EKF)**: System SHALL maintain state $x = [E, N, v, \psi, b_{\omega_z}]^T$. Predict via unicycle motion driven by $\omega_{ENU,z} - b_{\omega_z}$. Update speed sequentially across all active sources (Front Wheel, Rear Wheel, GNSS Doppler) with Huber innovation down-weighting. Update 2D position $(E, N)$ via GNSS covariance $R_{pos} = \sigma_{acc}^2 I_2$ and heading $\psi$ via Doppler course-over-ground ($v > 3\text{ m/s}$). Expose forward state, covariance, and Jacobian snapshots for RTS smoothing.
2. **REQ-FIL-008 (Quaternion Gyro Projection)**: System SHALL rotate body angular rate $\vec{\omega}_{body}$ into ENU world frame via $q \otimes \vec{\omega}_{body} \otimes q^*$ using `Sensor.TYPE_GAME_ROTATION_VECTOR` quaternions. Extract vertical world yaw-rate $\omega_{ENU,z}$. Linear acceleration double-integration is strictly prohibited.
3. **REQ-FIL-009 (Dead Reckoning Navigation)**: During GNSS denial, system SHALL propagate trajectory and speed using wheel speed and gyro yaw-rate with accumulated distance error $< 1.0\%$ across a 45 s curved tunnel at 8 m/s without teleportation spikes upon exit.
4. **REQ-FIL-010 (Wheel Auto-Calibration)**: Gated scalar Kalman filter SHALL estimate $C_{wheel}$ when GNSS accuracy $\le 6\text{ m}$, speed $\ge 4\text{ m/s}$, and $|\omega_{ENU,z}| < 3^\circ/\text{s}$, converging within $< 0.3\%$ of ground truth within 1,000 m on straight segments.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Pose Invariance)**: *Given* arbitrary 3D mounting orientations (pitch/roll up to $90^\circ$), *When* horizontal $90^\circ$ and $180^\circ$ turns are executed, *Then* integrated $\omega_{ENU,z}$ matches true heading change within $< 2^\circ$.
* **Criterion 2 (Auto-Calibration)**: *Given* straight qualified riding segments, *When* wheel revolutions and 3D GNSS displacement are ingested, *Then* estimated circumference converges within $< 0.3\%$ of ground truth.
* **Criterion 3 (Tunnel Dead Reckoning)**: *Given* a 45 s curved tunnel GNSS outage at 8 m/s, *When* wheel speed and gyro yaw-rate continue, *Then* trajectory is maintained without corner-cutting and exits smoothly.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-FIL-007`..`REQ-FIL-010` in `docs/requirements/req_tracking_cockpit.md`.
* **Historical Origin & Commit Trace**: 2026-06-27 Jira scoping proposing EJML 3D IMU strapdown double-integration; backfilled in commit `79ac0b23` (ATT-6127).
* **Root Reason for Existing Formulation**: Early draft assumed aerospace-style strapdown IMU navigation, which suffers severe vibration rectification (5–50 Hz) and gravity leakage on bicycle handlebars.
* **Preservation of Core Invariants**: Maintain zero external matrix dependencies (zero EJML/numpy/scipy); implement closed-form 5x5 matrix equations in pure Kotlin; preserve `IndirectTpmsEstimator` invariants; enforce magnetometer-free orientation via `Sensor.TYPE_GAME_ROTATION_VECTOR`.

---

## 2. Test Specification

### Test Case 1: `QuaternionGyroProjectorTest` (`TST-FUSION-001.1`)
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/odometry/QuaternionGyroProjectorTest.kt`
* **Checks**: Identity orientation, $45^\circ$ handlebar pitch, $90^\circ$ landscape roll, pocket tilts, and integrated $90^\circ$/$180^\circ$ turns ($\le 2^\circ$ error). Zero linear acceleration input.

### Test Case 2: `WheelCircumferenceCalibratorTest` (`TST-FUSION-003.1`)
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/odometry/WheelCircumferenceCalibratorTest.kt`
* **Checks**: Gating rejection (poor accuracy $> 6\text{ m}$, low speed $< 4\text{ m/s}$, sharp turns $\ge 3^\circ/\text{s}$), convergence to true circumference ($2.105\text{ m}$) within $< 0.3\%$ across 1 km, noise resilience.

### Test Case 3: `UnicycleOdometryEkfTest` & `OdometrySimulationTest` (`TST-FUSION-001.2`, `TST-FUSION-002.1`)
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/odometry/UnicycleOdometryEkfTest.kt`, `OdometrySimulationTest.kt`
* **Checks**: Multi-speed fusion (Front + Rear + GNSS Doppler), wheel slip Huber rejection, curved tunnel outage dead-reckoning, heading anchor convergence, snapshot API matrix dimensions.

### Test Case 4: Localization Parity & Governance Audit (`TST-FUSION-001.3`)
* **Checks**: Zero UI string changes in ATT-131 (`TranslationParityTest` 100% green); `verify_requirement_governance.py` 100% green.

---

## 3. Traceability Matrix

| Test ID | Scope | Target Class Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-FUSION-001.1` | Unit | `QuaternionGyroProjector` | `REQ-FIL-008` | Specified |
| `TST-FUSION-001.2` | Unit | `UnicycleOdometryEkf` | `REQ-FIL-007` | Specified |
| `TST-FUSION-001.3` | Governance | `verify_requirement_governance.py` | `REQ-PRO-073`, `REQ-PRO-077` | Specified |
| `TST-FUSION-002.1` | Simulation | `UnicycleOdometryEkf` | `REQ-FIL-009` | Specified |
| `TST-FUSION-003.1` | Unit | `WheelCircumferenceCalibrator` | `REQ-FIL-010` | Specified |
