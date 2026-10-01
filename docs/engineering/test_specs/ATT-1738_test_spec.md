# Stage 2: Requirement & Test Specification - ATT-1738: Suppress VAM (Vertical Speed) Noise During GPS Wander and Poor Indoor Accuracy

**Ticket**: [ATT-1738](https://atrainingtracker.atlassian.net/browse/ATT-1738)  
**Sub-task**: [ATT-1745](https://atrainingtracker.atlassian.net/browse/ATT-1745) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-235](https://atrainingtracker.atlassian.net/browse/ATT-235) (*Sensors & Telemetry Engine*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.6`  
**Requirement Mapping**: `REQ-FIL-012` (*Stabilized VAM (Vertical Speed) Metric, Location Accuracy Gating & Indoor Drift Suppression Architecture*)  
**Test Spec ID**: `TST-FIL-004`  
**Branch**: `feature/ATT-1738`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Requirement Specification (`REQ-FIL-012`)

### 1.1 Problem Statement & Rationale
During Sprint Review 2026-40.5 testing of ATT-1621 in an indoor/basement environment, the user observed that the vertical speed (VAM) metric continues to display volatile, erratic swings (e.g. $+800\dots 2400\text{ m/h}$) while stationary. 
Forensic investigation revealed that GPS multipath wander indoors produces phantom horizontal speeds ($0.6\text{–}1.5\text{ m/s}$), which bypass the stationary gating threshold (`speed_mps < 0.5 m/s`), while degraded GPS accuracy ($> 20\text{ m}$) amplifies vertical noise ramps.

### 1.2 Functional & Architectural Requirements
The system SHALL calculate the vertical ascent/descent rate (VAM / Vertical Speed in m/h) in `VerticalSpeedAndSlopeDevice` using linear regression slope smoothing over a sliding window, suppress indoor GPS wander and stationary drift, enforce location accuracy gating, maintain a noise deadband, and reject outlier anomalies (ATT-1621, ATT-1738):

1. **Sliding Window Linear Regression Derivative ($N = 15$ seconds)**:
   - The system SHALL maintain an equidistant 1-second circular buffer of up to $N = 15$ recent altitude samples.
   - For a full buffer of $N = 15$ samples, the ascent rate slope $\hat{\beta}$ (in m/s) SHALL be evaluated using least-squares linear regression:
     $$\hat{\beta} = \sum_{k=0}^{N-1} w_k \cdot a_k \quad \text{where} \quad w_k = \frac{k - \frac{N-1}{2}}{\sum_{j=0}^{N-1} \left(j - \frac{N-1}{2}\right)^2}$$
   - For an initial buffer with $M$ samples ($2 \le M < 15$), the system SHALL evaluate the least-squares slope dynamically over available points.
   - The unconstrained vertical speed in m/h SHALL be computed as $\text{raw\_vam} = \hat{\beta} \times 3600$.

2. **Location Accuracy Gating ($> 20.0\text{ m}$)**:
   - In `VerticalSpeedAndSlopeDevice.java`, the system SHALL query horizontal accuracy (`SensorType.ACCURACY`) via `cAccuracyFilter = new FilterData(null, SensorType.ACCURACY, FilterType.INSTANTANEOUS, 1)`.
   - If GPS accuracy is degraded ($> 20.0\text{ m}$), the system SHALL suppress the calculated vertical speed to `0 m/h` to reject multipath altitude jitter in indoor, basement, or canyon environments.

3. **Stationary Gating & Low-Speed Drift Damping**:
   - The system SHALL query horizontal speed `speed_mps` from `speedFilteredSensorData` and the dynamic minimum speed threshold `minSpeed = SettingsDataStoreJavaHelper.getSlopeMinSpeed(mContext)` (default `0.5` m/s).
   - If stationary (`speed_mps < minSpeed` or `speed_mps < 0.5` m/s):
     - If $|\text{raw\_vam}| < 150\text{ m/h}$, clamp vertical speed to `0 m/h`.
     - If genuine vertical displacement occurs while stationary in the horizontal plane ($|\text{raw\_vam}| \ge 150\text{ m/h}$, such as an elevator or ski lift), broadcast measured vertical speed.
   - If moving at low drift/walking speeds ($0.5\text{ m/s} \le \text{speed\_mps} < 1.2\text{ m/s}$):
     - The system SHALL enforce an elevated drift deadband ($|\text{raw\_vam}| < 150\text{ m/h}$ clamped to `0 m/h`), preventing phantom GPS wander speeds from triggering false climbing rates.

4. **Flat Terrain Athletic Noise Deadband**:
   - When moving horizontally at athletic speed ($\text{speed\_mps} \ge 1.2\text{ m/s}$) with valid GPS accuracy ($\le 20.0\text{ m}$), if $|\text{raw\_vam}| < 35\text{ m/h}$, the system SHALL clamp vertical speed to `0 m/h`.

5. **Outlier & Physical Boundary Clamping**:
   - Clamped to $[-3000, 3000]\text{ m/h}$ to reject single-tick step glitches.

6. **Preservation of Invariants**:
   - `mVerticalSpeedSensor` broadcasts integer values in m/h at 1-second intervals.
   - `ASCENT`, `DESCENT`, and `SLOPE` calculation remain completely unaffected.

---

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-FIL-003` (*Calculate derived metrics: VAM, slope, pace*), implemented in `VerticalSpeedAndSlopeDevice.java`.
* **Historical Origin & Commit Trace**: Sprint 2026-40.5 (ATT-1621), refined in Sprint 2026-40.6 (ATT-1738).
* **Root Reason for Existing Formulation**: Stationary gating in ATT-1621 assumed horizontal speed drops below $0.5\text{ m/s}$ when stationary. However, in indoor/basement testing, GPS multipath wander was found to generate phantom horizontal speed ($0.6\text{–}1.5\text{ m/s}$) and large altitude jumps ($\pm 5\text{–}15\text{ m}$), bypassing the stationary check and amplifying noise into erratic $\pm 800\dots 2400\text{ m/h}$ swings.
* **Preservation of Core Invariants**: 1-second emission periodicity, integer m/h units, accumulator integration, and slope calculation invariants are 100% preserved.

---

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Degraded Accuracy Gating)**:
  * *Given* an athlete in a basement or indoor setting where GPS accuracy is degraded ($> 20.0\text{ m}$, e.g. 28.0m)
  * *When* GPS altitude fluctuates or drifts
  * *Then* `calculateVam` SHALL return `0 m/h`.
* **Criterion 2 (Low-Speed Drift Wander Damping)**:
  * *Given* an athlete indoors where GPS multipath wander reports phantom horizontal speed ($0.8\text{ m/s}$) and minor vertical jitter ($|\text{raw\_vam}| = 110\text{ m/h}$)
  * *When* VAM updates
  * *Then* `calculateVam` SHALL return `0 m/h` via the low-speed drift deadband.
* **Criterion 3 (Steady Climbing Under Good Accuracy)**:
  * *Given* an athlete climbing steadily at $800\text{ m/h}$ at $5.0\text{ m/s}$ horizontal speed with good GPS accuracy ($5.0\text{ m}$)
  * *When* VAM updates
  * *Then* `calculateVam` SHALL return $800 \pm 15\text{ m/h}$.
* **Criterion 4 (Stationary Elevator / Ski Lift Preservation)**:
  * *Given* an athlete in an elevator ascending at $1800\text{ m/h}$ ($0.5\text{ m/s}$) while horizontal speed is $0.0\text{ m/s}$ with accuracy $\le 20.0\text{ m}$ (or 0.0m)
  * *When* VAM updates
  * *Then* `calculateVam` SHALL return $1800\text{ m/h}$.
* **Criterion 5 (Overload Signature Backward Compatibility)**:
  * *Given* existing unit tests and callers invoking `calculateVam(history, count, headIndex, speedMps, minSpeed)` without accuracy parameter
  * *When* invoked
  * *Then* the method executes transparently using default acceptable accuracy ($0.0\text{ m}$), maintaining 100% backward compatibility.

---

## 2. Test Specification (`TST-FIL-004`)

### Test Case 1: Location Accuracy Gating Verification (`[TST-FIL-004.1]`)
* **Scope**: Unit Test (`VerticalSpeedAndSlopeDeviceTest.kt`)
* **Preconditions**: Altitude history with 10m ascent over 15s (raw slope $\approx 2400\text{ m/h}$), horizontal speed $1.0\text{ m/s}$, accuracy $25.0\text{ m}$ ($> 20.0\text{ m}$).
* **Action**: `VerticalSpeedAndSlopeDevice.calculateVam(history, windowSize, 0, 1.0, 0.5, 25.0)`
* **Expected Result**: Returns `0` (gated due to poor accuracy).

### Test Case 2: Low-Speed Indoor GPS Wander Damping (`[TST-FIL-004.2]`)
* **Scope**: Unit Test (`VerticalSpeedAndSlopeDeviceTest.kt`)
* **Preconditions**: Altitude history with minor jitter ($|\text{raw\_vam}| = 110\text{ m/h}$), horizontal phantom speed $0.8\text{ m/s}$ ($0.5 \le \text{speed} < 1.2\text{ m/s}$), accuracy $12.0\text{ m}$ ($\le 20.0\text{ m}$).
* **Action**: `VerticalSpeedAndSlopeDevice.calculateVam(history, windowSize, 0, 0.8, 0.5, 12.0)`
* **Expected Result**: Returns `0` (suppressed by low-speed drift deadband).

### Test Case 3: Steady Climbing with Valid Accuracy (`[TST-FIL-004.3]`)
* **Scope**: Unit Test (`VerticalSpeedAndSlopeDeviceTest.kt`)
* **Preconditions**: Linear ramp at $800\text{ m/h}$, horizontal speed $5.0\text{ m/s}$, accuracy $5.0\text{ m}$.
* **Action**: `VerticalSpeedAndSlopeDevice.calculateVam(history, windowSize, 0, 5.0, 0.5, 5.0)`
* **Expected Result**: Returns `800`.

### Test Case 4: Backward Compatibility Overload Call (`[TST-FIL-004.4]`)
* **Scope**: Unit Test (`VerticalSpeedAndSlopeDeviceTest.kt`)
* **Preconditions**: Call 5-parameter overload without accuracy.
* **Action**: `VerticalSpeedAndSlopeDevice.calculateVam(history, windowSize, 0, 5.0, 0.5)`
* **Expected Result**: Returns `800` (defaults to zero/acceptable accuracy).

### Test Case 5: Clean-Room Regression Suite (`[TST-FIL-004.5]`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the complete test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-FIL-004.1]` | Unit | `VerticalSpeedAndSlopeDevice.calculateVam` | `REQ-FIL-012` | Specified |
| `[TST-FIL-004.2]` | Unit | `VerticalSpeedAndSlopeDevice.calculateVam` | `REQ-FIL-012` | Specified |
| `[TST-FIL-004.3]` | Unit | `VerticalSpeedAndSlopeDevice.calculateVam` | `REQ-FIL-012` | Specified |
| `[TST-FIL-004.4]` | Unit | `VerticalSpeedAndSlopeDevice.calculateVam` | `REQ-FIL-012` | Specified |
| `[TST-FIL-004.5]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
