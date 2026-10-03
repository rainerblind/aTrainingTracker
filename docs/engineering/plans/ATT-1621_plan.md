# Stage 3 Implementation Plan: ATT-1621

**Ticket**: [ATT-1621](https://atrainingtracker.atlassian.net/browse/ATT-1621)  
**Sub-task**: [ATT-1691](https://atrainingtracker.atlassian.net/browse/ATT-1691) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.5`  
**Branch**: `feature/ATT-1621`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Architecture Overview (SWE.2)

To stabilize the volatile VAM (Vertical Speed in m/h) metric in `VerticalSpeedAndSlopeDevice.java` without introducing artificial lag or disrupting existing accumulator telemetry, this plan introduces a multi-tier signal processing architecture:

```
[Altitude Stream] 
       │
       ▼ (1Hz ticks)
[Circular Ring Buffer] (N=15 samples)
       │
       ▼
[Least-Squares Linear Regression Engine] ───> rate of ascent β̂ (m/s)
       │                                        │
       ▼                                        ▼
[Stationary & Deadband Filter]             [Slope Calculation] (dh/dx = β̂ / speed * 100)
  • speed < minSpeed && |VAM| < 150 -> 0
  • speed >= minSpeed && |VAM| < 35 -> 0
  • |VAM| clamped to [-3000, 3000]
       │
       ▼
[mVerticalSpeedSensor.newValue((int) Math.round(vertical_speed))]
```

---

## 2. Mathematical Design

### 2.1 Least-Squares Linear Regression Slope
Given $M$ equidistant 1-second altitude samples $x_0, x_1, \dots, x_{M-1}$ ($2 \le M \le 15$):
The least-squares slope $\hat{\beta}$ (in m/s) is:
$$\hat{\beta} = \frac{\sum_{k=0}^{M-1} (k - \bar{k})(x_k - \bar{x})}{\sum_{k=0}^{M-1} (k - \bar{k})^2} = \frac{\sum_{k=0}^{M-1} \left(k - \frac{M-1}{2}\right) x_k}{\frac{M(M^2 - 1)}{12}}$$
- For full buffer ($M = 15$):
  - Denominator $= \frac{15 \times 224}{12} = 280.0$
  - Center $\bar{k} = 7.0$
  - Each sample contributes smoothly, weighting recent samples positively and older samples negatively.
- For initial warm-up ($2 \le M < 15$):
  - Evaluated dynamically over available $M$ samples, preventing initial output delay.
- When $M < 2$:
  - Returns $0.0\text{ m/s}$.
- Unconstrained ascent rate:
  $$\text{raw\_vam} = \hat{\beta} \times 3600.0$$

### 2.2 Stationary Gating & Noise Deadband
- Retrieve horizontal speed `speed_mps` from `speedFilteredSensorData` (default 0.0 if null).
- Retrieve `minSpeed = SettingsDataStoreJavaHelper.getSlopeMinSpeed(mContext)` (default 0.5 m/s).
- **Condition 1 (Stationary Athlete)**:
  - If $|speed\_mps| < minSpeed$:
    - If $|\text{raw\_vam}| < 150.0\text{ m/h}$ (stationary barometric/GPS drift), set `vertical_speed = 0.0`.
    - Else ($|\text{raw\_vam}| \ge 150.0\text{ m/h}$), preserve `vertical_speed = raw_vam` (e.g. elevator, lift).
- **Condition 2 (Moving Athlete)**:
  - If $|speed\_mps| \ge minSpeed$:
    - If $|\text{raw\_vam}| < 35.0\text{ m/h}$ (flat road surface ripple), set `vertical_speed = 0.0`.
    - Else, set `vertical_speed = raw_vam`.
- **Condition 3 (Outlier Boundary Clamping)**:
  - Clamp `vertical_speed` to $[-3000.0, 3000.0]\text{ m/h}$.

---

## 3. Atomic Implementation Steps

### Task 1: Refactor `VerticalSpeedAndSlopeDevice.java`
- **File**: `app/src/main/java/com/atrainingtracker/banalservice/devices/VerticalSpeedAndSlopeDevice.java`
- **Modifications**:
  1. Add ring buffer fields:
     - `private static final int VAM_WINDOW_SIZE = 15;`
     - `private final double[] mAltitudeHistory = new double[VAM_WINDOW_SIZE];`
     - `private int mAltitudeHistoryCount = 0;`
     - `private int mAltitudeHistoryIndex = 0;`
  2. Implement package-private static helper methods for regression and VAM resolution:
     - `static double calculateLinearRegressionSlope(double[] history, int count, int headIndex)`
     - `static int calculateVam(double[] history, int count, int headIndex, double speedMps, double minSpeed)`
  3. In `calculateMetrics()`:
     - Insert `altitudeFilteredSensorData.getValue()` into ring buffer.
     - Call `calculateVam(...)` and dispatch to `mVerticalSpeedSensor.newValue(vam)`.
     - Use regression slope $\hat{\beta}$ for `deltaAltitude_mps` in `SLOPE` calculation, stabilizing slope against discrete spikes.
     - Retain `cAltitudeSuperFilter` for `mAscentSensor` and `mDescentSensor` accumulation.

### Task 2: Author Unit Tests in `VerticalSpeedAndSlopeDeviceTest.kt`
- **File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/VerticalSpeedAndSlopeDeviceTest.kt`
- **Test Cases**:
  1. `testStationaryIndoorNoise_suppressedToZeroVam`: Verify oscillating altitude with speed 0.0 m/s produces 0 m/h.
  2. `testSteadyClimb_convergesToTrueAscentRate`: Verify 800 m/h climb converges to $800 \pm 15\text{ m/h}$.
  3. `testElevatorMotionWhileStationary_preservesAscentRate`: Verify 500 m/h vertical movement with speed 0.0 m/s is not zeroed.
  4. `testFlatRoadNoiseDeadband_clampsMicroFluctuationsToZero`: Verify 20 m/h ripple while riding at 8.0 m/s yields 0 m/h.
  5. `testSteadyDescent_convergesToNegativeVam`: Verify -600 m/h descent converges to $-600 \pm 15\text{ m/h}$.
  6. `testGlitchOutlierClamping_boundsExtremeJumps`: Verify single-sample +50m jump is clamped to $\le 3000\text{ m/h}$.
  7. `testWarmupSamples_computesImmediateDynamicSlope`: Verify $M=2, 3, 5$ samples compute valid slopes without startup crash.

### Task 3: Targeted Verification & Regression Suite
- Run targeted tests:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.VerticalSpeedAndSlopeDeviceTest"`
- Verify build success and pass rate.

---

## 4. Invariant Protection & Verification

1. **Periodic Emission Schedule**: `scheduleWithFixedDelay(this::calculateMetrics, 1, 1, TimeUnit.SECONDS)` is strictly maintained.
2. **Sensor Contract & Type Safety**: `mVerticalSpeedSensor` remains `MySensor<Integer>` with integer m/h units.
3. **Accumulator Decoupling**: Cumulative ascent and descent continue using `cAltitudeSuperFilter` without regression.
4. **Governance**: All subtasks tracked in Jira sprint ID `313`. Human decision gate preserved.
