# Stage 1 Analysis: ATT-1621

**Ticket**: [ATT-1621](https://atrainingtracker.atlassian.net/browse/ATT-1621)  
**Sub-task**: [ATT-1689](https://atrainingtracker.atlassian.net/browse/ATT-1689) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.5`  
**Branch**: `feature/ATT-1621`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Executive Summary & Problem Domain

### 1.1 Context & Traceability
In `aTrainingTracker`, the VAM (Vertical Speed / *Vertikale Geschwindigkeit* in m/h) and Slope metrics are computed in `VerticalSpeedAndSlopeDevice.java` via a 1-second scheduled task (`calculateMetrics()`). The derived metrics are broadcast via `mVerticalSpeedSensor` (`SensorType.VERTICAL_SPEED`) and `mSlopeSensor` (`SensorType.SLOPE`), which feed real-time cockpit displays, live workout statistics, and workout recording.

* **Existing Requirements Mapping**:
  - `REQ-FIL-003`: Calculate derived metrics (VAM, slope, pace) in `VerticalSpeedAndSlopeDevice.java` (verified by `TST-UNT-002`).
  - `REQ-SET-073`: Dynamic preference thresholds via `SettingsDataStoreJavaHelper.getSlopeMinSpeed()` and `getAltitudeFilterWindow()`.
  - `REQ-UI-198`: SensorType-specific default filter presets establishing a 15-second moving average cockpit tile default for `SensorType.VERTICAL_SPEED`.
* **Proposed Requirement Extension**:
  - `REQ-FIL-004`: Stabilized VAM (Vertical Speed) Metric & Stationary Suppression Architecture.

### 1.2 Problem Statement & Athletic Impact
Athletes testing VAM experience extreme volatility, large erratic swings, and nonsensical climbing rates:
1. **Stationary / Indoor Jitter**: When standing completely still indoors or in basements with poor/no GPS reception, the displayed VAM continuously jumps between $+600\text{ m/h}$ and $-1200\text{ m/h}$, reporting thousands of meters of ascent while stationary.
2. **Pedal Stroke & Telemetry Spikes**: During steady climbing on the road, consecutive 1-second readings fluctuate wildly by $\pm 300\text{ m/h}$ to $\pm 600\text{ m/h}$ instead of displaying a smooth, actionable ascent rate (e.g. 750–850 m/h).
3. **Lag vs. Noise Dilemma**: The device driver currently relies on a hardcoded 21-second moving average (`cAltitudeFilter`), which creates a sluggish 10–15s response delay when initiating a climb, yet still fails to eliminate derivative noise.

---

## 2. Forensic Root Cause Analysis (RCA)

Investigation of `VerticalSpeedAndSlopeDevice.java` (lines 106–136) identified four interrelated mathematical and architectural root causes:

### 2.1 Aggressive Extrapolation (`* 3600`) on 1-Second Discrete Deltas
In `calculateMetrics()`:
```java
double deltaAltitude_mps = altitudeFilteredSensorData.getValue() - mLastAltitude;
mLastAltitude = altitudeFilteredSensorData.getValue();
double vertical_speed = deltaAltitude_mps * 60 * 60;
mVerticalSpeedSensor.newValue((int) Math.round(vertical_speed));
```
Vertical speed is derived every 1 second by taking the 1-second discrete difference `deltaAltitude_mps` and multiplying by 3600 (`* 60 * 60`).
- Any minor noise of just $\pm 0.1\text{ m}$ between consecutive 1-second samples translates directly into a $\pm 360\text{ m/h}$ swing.
- A $\pm 0.5\text{ m}$ fluctuation produces a massive $\pm 1800\text{ m/h}$ jump.

### 2.2 The "Discrete Derivative of a Moving Average" Mathematical Flaw
To reduce this noise, `VerticalSpeedAndSlopeDevice` passes raw altitude through `cAltitudeFilter`:
```java
private static final FilterData cAltitudeFilter = new FilterData(null, SensorType.ALTITUDE, FilterType.MOVING_AVERAGE_TIME, 21);
```
Where `cAltitudeFilter` is an unweighted moving average over window $W = 21$ seconds ($A_t = \frac{1}{W} \sum_{i=0}^{W-1} a_{t-i}$).
Subtracting consecutive 1-second moving average samples yields:
$$\Delta A_t = A_t - A_{t-1} = \frac{a_t - a_{t-W}}{W}$$
Multiplying by $3600$:
$$\text{vertical\_speed} = \frac{3600}{W} (a_t - a_{t-W})$$

**Critical Architectural Defect**: Every intermediate sample between $t-1$ and $t-W+1$ cancels out completely! The formula does **not** average the ascent rate over 21 seconds. Instead, it measures the raw difference between two isolated points ($a_t$ and $a_{t-W}$) scaled by $\frac{3600}{21} \approx 171.4$.
- A 1-meter noise spike in raw altitude $a_t$ instantly creates a $+171\text{ m/h}$ spike.
- Exactly 21 seconds later, when that spike drops out of the moving average window as $a_{t-W}$, it generates an equal and opposite $-171\text{ m/h}$ ghost spike!

### 2.3 Absence of Stationary Gating & Speed Suppression
While `SLOPE` calculation explicitly guards against low speed using `SettingsDataStoreJavaHelper.getSlopeMinSpeed(mContext)` (`if (abs(speed_mps) > minSpeed)`), `VERTICAL_SPEED` has **zero** stationary gating. When an athlete stops at a traffic signal, rests, or stands indoors/in a basement, horizontal speed is 0 m/s, but GPS multipath drift and ambient air currents cause altitude to drift by $0.2 - 1.0\text{ m}$. This noise is immediately multiplied by $3600$, outputting chaotic ascent rates while standing completely still.

### 2.4 Absence of Noise Deadband
1Hz barometric pressure sensors have an intrinsic quantization and thermal noise floor of $\approx \pm 0.05 - 0.1\text{ m}$. At 1Hz discrete differencing, $\pm 0.05\text{ m/s} \times 3600 = \pm 180\text{ m/h}$. Without a noise deadband on altitude deltas or derived vertical speed, low-amplitude sensor noise is continuously broadcast.

---

## 3. Chesterton's Fence & Requirement Archaeology

### 3.1 Historical Archaeology
* **Existing Requirements**:
  - `REQ-FIL-003` (*Calculate derived metrics: VAM, slope, pace*): Implemented in `VerticalSpeedAndSlopeDevice.java`, verified by `TST-UNT-002`.
  - `REQ-SET-073` (*Dedicated Advanced Tuning Preferences Screen*): Exposes `SettingsDataStoreJavaHelper.getAltitudeFilterWindow()` and `getSlopeMinSpeed()`.
  - `REQ-UI-198` (ATT-1624): Established default 15s tile smoothing for `SensorType.VERTICAL_SPEED` in cockpit views.
* **Why the Code was Written This Way**:
  - `VerticalSpeedAndSlopeDevice` was originally written as a minimal, lightweight device driver. Simple differencing and multiplying by 3600 was the simplest initial implementation.
  - The 21-second moving average was introduced as a crude band-aid to reduce derivative swings, but without understanding that differencing a moving average only samples the window endpoints.
* **Preservation of Invariants**:
  - `SensorType.ASCENT` and `SensorType.DESCENT` accumulator integration using `cAltitudeSuperFilter` (5 minutes) MUST remain completely untouched.
  - `SLOPE` calculation (`deltaAltitude_mps / speed_mps * 100`) MUST remain valid and continue using `getSlopeMinSpeed()`.
  - Public API contract, 1-second emission periodicity, and integer m/h return types MUST be preserved.

---

## 4. Proposed Architectural Solution

### 4.1 Linear Regression Moving Window (15–20 Seconds)
In accordance with industry standards (Garmin, Wahoo 15–30s VAM smoothing) and aligned with the parent ticket scope, replace discrete endpoint differencing with a **Least-Squares Linear Regression** over an equidistant sliding window buffer of altitude samples ($N = 15$ seconds):
Given 1-second equidistant altitude samples $a_0, a_1, \dots, a_{N-1}$ ($N=15$):
$$\hat{\beta} = \sum_{k=0}^{N-1} w_k \cdot a_k$$
Where $w_k = \frac{k - \frac{N-1}{2}}{\sum_{j=0}^{N-1} (j - \frac{N-1}{2})^2}$ are precomputed compile-time constants.
- **Benefits**:
  1. Utilizes **every single point** in the window rather than just the two endpoints, minimizing total squared error against sensor noise.
  2. The 15-second window perfectly matches Garmin/Wahoo standards and harmonizes with the 15s tile default established in ATT-1624.
  3. Computes the true, smoothed rate of altitude change $\hat{\beta}$ in m/s, yielding $\text{raw\_vam} = \hat{\beta} \times 3600$.

### 4.2 Stationary Gating & Noise Deadband
1. **Stationary Gating**:
   - Query filtered horizontal speed `speed_mps` from `speedFilteredSensorData`.
   - Retrieve dynamic minimum speed threshold `minSpeed = SettingsDataStoreJavaHelper.getSlopeMinSpeed(mContext)` (default `0.5` m/s).
   - If the athlete is stationary (`speed_mps < minSpeed` or `speed_mps < 0.5 m/s`):
     - Check altitude delta over the window: if the altitude change rate is within the stationary noise deadband ($|\text{raw\_vam}| < 150\text{ m/h}$, corresponding to $|\Delta h| < 0.2\text{ m}$ over 5s), force `vertical_speed = 0 m/h`.
     - If genuine vertical movement occurs while horizontal speed is low (e.g. elevator, ski lift, vertical ladder with $|\text{raw\_vam}| \ge 150\text{ m/h}$), preserve the measured ascent rate.
2. **Noise Deadband for Moving Athletes**:
   - If moving on flat terrain and $|\text{raw\_vam}| < 35\text{ m/h}$, clamp to `0 m/h` to eliminate barometric ripple on flat roads.
3. **Outlier Clamping**:
   - Clamp calculated vertical speed to $\pm 3000\text{ m/h}$ to prevent single-tick GPS multipath anomalies (e.g. 50m jump) from corrupting telemetry.

---

## 5. Scope Bounding

### In-Scope:
- Refactor VAM calculation in `VerticalSpeedAndSlopeDevice.java` to implement 15-second linear regression smoothing, stationary gating, noise deadband, and outlier clamping.
- Author comprehensive unit tests in `VerticalSpeedAndSlopeDeviceTest.kt` validating stationary stability, steady climbing accuracy, descent, and glitch suppression.
- Formulate requirement `REQ-FIL-004` and test specification `TST-FIL-001` in living documentation.

### Out-of-Scope:
- Modifying `SensorType.ASCENT` / `SensorType.DESCENT` accumulator logic (`cAltitudeSuperFilter`).
- Modifying UI composables or database schemas (already decoupled via ATT-1624).
