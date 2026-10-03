# Stage 2 Requirement & Test Specification: ATT-1621

**Ticket**: [ATT-1621](https://atrainingtracker.atlassian.net/browse/ATT-1621)  
**Sub-task**: [ATT-1690](https://atrainingtracker.atlassian.net/browse/ATT-1690) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.5`  
**Branch**: `feature/ATT-1621`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Traceability Matrix

| Requirement ID | Test Case ID | Scope Summary | Verification Method | Status |
|:---|:---|:---|:---|:---|
| **REQ-FIL-012** | **TST-FIL-004** | Stabilized VAM (Vertical Speed) Metric, 15-second Linear Regression Slope, Stationary Gating, and Noise Deadband in `VerticalSpeedAndSlopeDevice.java` | Unit Tests (`VerticalSpeedAndSlopeDeviceTest.kt`) & Clean-Room Regression | Specified |

---

## 2. Formal Requirement Specification

### `REQ-FIL-012`: Stabilized VAM (Vertical Speed) Metric & Stationary Suppression Architecture

The system SHALL calculate the vertical ascent/descent rate (VAM / Vertical Speed in m/h) in `VerticalSpeedAndSlopeDevice` using linear regression slope smoothing over a sliding window, suppress stationary indoor/outdoor drift, enforce a noise deadband, and reject outlier anomalies (ATT-1621):

1. **Sliding Window Linear Regression Derivative ($N = 15$ seconds)**:
   - The system SHALL maintain an equidistant 1-second circular buffer of up to $N = 15$ recent altitude samples.
   - For a full buffer of $N = 15$ samples, the ascent rate slope $\hat{\beta}$ (in m/s) SHALL be evaluated using least-squares linear regression:
     $$\hat{\beta} = \sum_{k=0}^{N-1} w_k \cdot a_k$$
     where $w_k = \frac{k - \frac{N-1}{2}}{\sum_{j=0}^{N-1} (j - \frac{N-1}{2})^2}$, minimizing total squared error across all samples in the window.
   - For an initial buffer with $M$ samples ($2 \le M < 15$), the system SHALL evaluate the least-squares slope dynamically over the available $M$ points, enabling immediate responsiveness during warm-up without artificial startup delay.
   - The unconstrained vertical speed in m/h SHALL be computed as $\text{raw\_vam} = \hat{\beta} \times 3600$.

2. **Stationary Gating & Indoor Drift Suppression**:
   - The system SHALL query horizontal speed `speed_mps` from `speedFilteredSensorData` and the dynamic minimum speed threshold `minSpeed = SettingsDataStoreJavaHelper.getSlopeMinSpeed(mContext)` (default `0.5` m/s).
   - If the athlete is stationary (`speed_mps < minSpeed` or `speed_mps < 0.5` m/s):
     - If the calculated vertical ascent rate is within typical stationary sensor noise ($|\text{raw\_vam}| < 150\text{ m/h}$, corresponding to $|\Delta h| < 0.2\text{ m}$ over 5s), the system SHALL clamp vertical speed to `0 m/h`.
     - If genuine vertical displacement occurs while stationary in the horizontal plane ($|\text{raw\_vam}| \ge 150\text{ m/h}$, such as an elevator, ski lift, or vertical climbing wall), the system SHALL broadcast the measured vertical speed.

3. **Flat Terrain Noise Deadband**:
   - When the athlete is moving horizontally (`speed_mps \ge minSpeed`), if $|\text{raw\_vam}| < 35\text{ m/h}$, the system SHALL clamp vertical speed to `0 m/h` to eliminate barometric ripple on flat terrain.

4. **Outlier & Physical Boundary Clamping**:
   - The calculated vertical speed SHALL be clamped to the range $[-3000, 3000]\text{ m/h}$ to prevent single-tick GPS multipath altitude jumps from corrupting downstream recording.

5. **Preservation of Invariants**:
   - `mVerticalSpeedSensor` SHALL broadcast integer values in m/h at 1-second intervals.
   - Accumulator sensors `SensorType.ASCENT` and `SensorType.DESCENT` using `cAltitudeSuperFilter` SHALL remain completely unaffected.
   - `SLOPE` calculation using `getSlopeMinSpeed(mContext)` SHALL remain completely unaffected.

---

## 3. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: `REQ-FIL-003` (*Calculate derived metrics: VAM, slope, pace*), implemented in `VerticalSpeedAndSlopeDevice.java`.
2. **Historical Origin & Commit Trace**: Initial repository commit.
3. **Root Reason for Existing Formulation**: Simple differencing between consecutive 1-second samples of a moving average was initially chosen for simplicity. However, differencing a moving average mathematically cancels intermediate points, reducing to $(a_t - a_{t-21})/21 \times 3600$, amplifying single-point noise into large swings and inverted ghost spikes.
4. **Refinement Reason**: Eliminating erratic $\pm 300\dots 1800\text{ m/h}$ swings while stationary or climbing, aligning VAM calculation with Garmin/Wahoo 15–30s linear regression standards and harmonizing with the 15s tile default in ATT-1624.
5. **Preservation of Core Invariants**: 1-second emission periodicity, integer m/h units, accumulator integration, and slope calculation invariants are 100% preserved.

---

## 4. Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Stationary Stability)**:
  - *Given* an athlete stationary indoors, in a basement, or stopped at a traffic light (`speed < 0.5 m/s`),
  - *When* GPS or barometric altitude fluctuates within typical stationary noise margin ($\pm 0.5\text{ m}$),
  - *Then* `mVerticalSpeedSensor` SHALL broadcast `0 m/h` without jumping to phantom positive or negative values.

* **Criterion 2 (Steady Climbing Convergence & Stability)**:
  - *Given* an athlete climbing steadily at 800 m/h ($0.222\text{ m/s}$ vertical speed) with horizontal speed $> 0.5\text{ m/s}$,
  - *When* VAM updates,
  - *Then* calculated VAM SHALL smoothly converge to $800 \pm 15\text{ m/h}$ and maintain stability without erratic second-by-second swings exceeding $\pm 20\text{ m/h}$.

* **Criterion 3 (Elevator / Vertical Movement Preservation)**:
  - *Given* an athlete in an elevator or ski lift moving vertically at $\ge 150\text{ m/h}$ while horizontal speed is $< 0.5\text{ m/s}$,
  - *When* VAM updates,
  - *Then* the vertical rate SHALL NOT be suppressed to 0, but accurately reflect the vertical movement.

* **Criterion 4 (Flat Road Deadband)**:
  - *Given* an athlete riding on a flat road where minor road vibration or barometric ripple produces $|\text{raw\_vam}| < 35\text{ m/h}$,
  - *When* VAM updates,
  - *Then* `mVerticalSpeedSensor` SHALL output `0 m/h`.

* **Criterion 5 (GPS Glitch Clamping)**:
  - *Given* a sudden isolated GPS altitude step anomaly (e.g. +30m jump in 1 second),
  - *When* VAM updates,
  - *Then* the calculated VAM SHALL be safely clamped to $\le 3000\text{ m/h}$.

---

## 5. Detailed Test Specification

### `TST-FIL-004`: Stabilized VAM (Vertical Speed) Metric & Stationary Suppression Verification

#### 5.1 Unit Test Coverage (`VerticalSpeedAndSlopeDeviceTest.kt`)
1. **Stationary Indoor Drift Suppression**:
   - Mock context and sensor manager. Inject altitude stream oscillating with noise while `speed = 0.0 m/s`.
   - Verify `mVerticalSpeedSensor.getValue() == 0`.
2. **Steady Climbing Convergence**:
   - Feed steady altitude ramp corresponding to 800 m/h at 5.0 m/s horizontal speed.
   - Verify convergence to 800 m/h within $\pm 15\text{ m/h}$.
3. **Elevator / Lift Vertical Movement**:
   - Feed steady vertical climb of 500 m/h while `speed = 0.0 m/s`.
   - Verify output is $\approx 500\text{ m/h}$ (not zeroed).
4. **Flat Road Deadband**:
   - Feed horizontal speed 8.0 m/s with micro-altitude oscillations ($|\text{VAM}| = 20\text{ m/h}$).
   - Verify output is `0 m/h`.
5. **Steady Descent**:
   - Feed descent ramp corresponding to -600 m/h at 6.0 m/s horizontal speed.
   - Verify output converges to $-600 \pm 15\text{ m/h}$.
6. **Glitch Outlier Clamping**:
   - Feed steady altitude and inject a sudden +40m jump.
   - Verify output is clamped to $\le 3000\text{ m/h}$.

#### 5.2 Clean-Room Full Suite Regression
- Execute `./gradlew testDebugUnitTest` across all modules to ensure zero regressions.
