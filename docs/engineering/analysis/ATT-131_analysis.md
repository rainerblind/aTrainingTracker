# Stage 1 Analysis: ATT-131 - 4-State Odometry EKF Engine: GNSS + ANT+/BLE Wheel Speed + Quaternion-Projected Gyro Yaw-Rate Fusion & Wheel Auto-Calibration

**Ticket**: [ATT-131](https://rainerblind.atlassian.net/browse/ATT-131)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.6` | **Branch**: `feat/ATT-131`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-14  

---

## 1. Problem Statement & Motivation
Android's `FusedLocationProvider` (`SpeedAndLocationDevice_GoogleFused`) and standalone GPS have no access to external ANT+/BLE cycling wheel-speed revolutions or running footpod sensors. Under dense forest canopy, deep valleys, sharp switchbacks, or curved tunnels, GNSS fixes cut corners, lag, or drop out completely, resulting in speed spikes and inaccurate distance. Conversely, calibrated wheel revolutions provide drift-free forward distance $\Delta s_{wheel}$, and smartphone MEMS gyroscopes coupled with `Sensor.TYPE_GAME_ROTATION_VECTOR` provide mounting-invariant horizontal world yaw-rate $\omega_{ENU,z}$. A pure Kotlin mathematical fusion engine is required to combine GNSS, multi-source wheel speed, and gyro yaw-rate into a kinematically coupled unicycle EKF with online wheel circumference auto-calibration.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification**:
  - Live audit of `app/src/main/java/com/atrainingtracker/banalservice/devices/` confirms `SpeedAndLocationDevice_GoogleFused.java` and `SpeedAndLocationDevice_GPS.java` operate strictly as pass-throughs from Android location APIs with zero external speed sensor or IMU integration.
  - Inspection of `IndirectTpmsEstimator.kt` confirms effective straight-line 3D GNSS gating criteria (`accuracy <= 6m`, `speed >= 4m/s`, bearing rate $\le 3^\circ$/s, 3D slope-corrected distance) already exist and are proven in production; these criteria can be adapted for the online wheel circumference calibrator without interfering with iTPMS.
  - Hypothesis check: 3D linear accelerometer double-integration ($\iint R(q) a_{body} dt^2$) was originally proposed in 2026-06 Jira comments. Independent verification confirms that consumer smartphone MEMS accelerometers on bicycle handlebars suffer extreme 5–50 Hz road vibration rectification and pitch tilt errors (a $1^\circ$ pitch error projects $0.17\text{ m/s}^2$ gravity bias, causing $76\text{ m}$ drift in $30\text{ s}$). Thus, linear acceleration double-integration is strictly rejected; quaternions are used *strictly* to project angular velocity into horizontal world yaw-rate $\omega_{ENU,z}$.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals (Core Math Engine — ATT-131, 5 SP)**:
  - `QuaternionGyroProjector`: Rotate body angular rates into ENU world frame via `TYPE_GAME_ROTATION_VECTOR` ($q \otimes \vec{\omega}_{body} \otimes q^*$) and extract vertical world yaw-rate $\omega_{ENU,z}$ invariant to phone mounting pose.
  - `WheelCircumferenceCalibrator`: Recursive scalar Kalman estimator for wheel circumference $C_{wheel}$ gated by high-confidence straight-line 3D GNSS fixes.
  - `UnicycleOdometryEkf`: 5-state coupled EKF $[E, N, v, \psi, b_{\omega_z}]^T$ supporting multi-sensor speed fusion (Front Wheel, Rear Wheel, GNSS Doppler) with Huber outlier rejection, heading re-anchoring, and tunnel dead-reckoning.
  - Snapshot API: Expose prior/posterior states, covariances, and Jacobians ($F_k$) for backward RTS smoothing (ATT-7750).
  - Deterministic JVM simulation test suite covering 3D phone mounting orientations, straight-line calibration convergence, curved tunnel outages, and multi-speed slip rejection.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Registering `DeviceType.SPEED_AND_LOCATION_ATT_FUSED` or `Protocol.SMARTPHONE` device wiring and `EditDeviceDialog.kt` UI (deferred to companion ticket ATT-7751).
  - Post-workout forward-backward ERTS/RTS smoothing pass execution (deferred to ATT-7750).
  - Double-integrating linear accelerometers into position or speed.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: `REQ-FIL-007` (EKF Core), `REQ-FIL-008` (Body-to-World Transformation), `REQ-FIL-009` (Dead Reckoning), `REQ-FIL-010` (Auto-Calibration) in `docs/requirements/req_tracking_cockpit.md`.
* **Historical Origin & Commit Trace**: Added on 2026-06-27 proposing external EJML library and 3D IMU strapdown double-integration; backfilled in commit `79ac0b23` (ATT-6127).
* **Root Reason for Existing Formulation**: Early draft assumed aerospace-style strapdown IMU navigation. Subsequent empirical analysis demonstrated that handlebar vibration makes linear accelerometer integration non-viable.
* **Preservation of Core Invariants**: Maintain zero external matrix dependencies (zero EJML/numpy/scipy); implement closed-form 5x5 matrix equations in pure Kotlin; preserve `IndirectTpmsEstimator` invariants; enforce magnetometer-free orientation via `Sensor.TYPE_GAME_ROTATION_VECTOR`.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-FIL-007`, `REQ-FIL-008`, `REQ-FIL-009`, `REQ-FIL-010` in `docs/requirements/req_tracking_cockpit.md`.
* **Affected Architecture Elements**: New package `com.atrainingtracker.banalservice.odometry` containing `QuaternionGyroProjector.kt`, `WheelCircumferenceCalibrator.kt`, `UnicycleOdometryEkf.kt`, and data models `OdometryState.kt`, `OdometrySnapshot.kt`.
* **Verification Impact & Test Surface**: New unit test suite `app/src/test/java/com/atrainingtracker/banalservice/odometry/`: `QuaternionGyroProjectorTest.kt`, `WheelCircumferenceCalibratorTest.kt`, `UnicycleOdometryEkfTest.kt`, `OdometrySimulationTest.kt`.
* **Risk Assessment & Invariants**: **MEDIUM** risk. Algorithmic sensor-fusion code; completely decoupled from live sensor pipelines in this ticket; zero database migrations; zero UI modifications; 100% existing test pass rate preserved.

---

## 6. Architectural Strategy & High-Level Solution
1. **`QuaternionGyroProjector`**: Accepts quaternion $q = [x, y, z, w]$ from `TYPE_GAME_ROTATION_VECTOR` and body angular rates $[\omega_x, \omega_y, \omega_z]$ from `TYPE_GYROSCOPE`. Computes $\omega_{ENU,z} = 2(q_x q_z - q_w q_y)\omega_x + 2(q_y q_z + q_w q_x)\omega_y + (1 - 2(q_x^2 + q_y^2))\omega_z$. Zero trigonometric allocations.
2. **`WheelCircumferenceCalibrator`**: Tracks scalar circumference $C_{wheel}$ with variance $P_C$. Gates updates when GNSS accuracy $\le 6\text{ m}$, speed $\ge 4\text{ m/s}$, and $|\omega_{ENU,z}| < 3^\circ/\text{s}$. Updates $C_{wheel}$ using 3D slope-corrected ground displacement $\Delta s_{3D} / \Delta rev$.
3. **`UnicycleOdometryEkf`**: State $x = [E, N, v, \psi, b_{\omega_z}]^T$. Predicts via unicycle kinematics driven by $\omega_{ENU,z}$. Sequentially incorporates scalar speed measurements (Front Wheel, Rear Wheel, GNSS Doppler) with Huber-gated innovation weighting ($R_v \leftarrow R_v \cdot \text{scale}$). Performs 2D GNSS position update and Doppler heading anchoring ($v > 3\text{ m/s}$). Exposes step snapshots for ATT-7750 RTS smoothing.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Pure Kotlin implementation with zero third-party linear algebra libraries.
  2. Zero double-integration of raw linear acceleration.
  3. Decoupled engine: no direct device/service side-effects in ATT-131.
  4. 100% existing unit tests pass without regression.
* **Risk Rating**: **MEDIUM** (Advanced kinematics and numerical stability; mitigated by comprehensive JVM simulation tests with deterministic geometric ground truths).
