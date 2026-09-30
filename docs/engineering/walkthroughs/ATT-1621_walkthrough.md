# Stage 5 Verification & Walkthrough: ATT-1621

## 1. Ticket Information
- **Parent Ticket**: [ATT-1621](https://atrainingtracker.atlassian.net/browse/ATT-1621) - `[Verbesserung] [Sensors/Telemetry] Stabilize Volatile VAM (Vertical Speed) Metric in VerticalSpeedAndSlopeDevice`
- **Subtask**: [ATT-1693](https://atrainingtracker.atlassian.net/browse/ATT-1693) - `Stage 5: Verification & Clean-Room Regression`
- **Fix Version**: `V4.9.38`
- **Target Branch**: `sprint/2026-40.5`
- **Feature Branch**: `feature/ATT-1621`

---

## 2. Executive Summary of Changes
Resolved severe vertical speed volatility, indoor phantom climbing drift, and erratic gradient swings in `VerticalSpeedAndSlopeDevice.java` by replacing noisy two-point moving average difference derivatives with a standard 15-second least-squares linear regression slope algorithm, complemented by intelligent stationary drift gating, flat-terrain deadbands, and physical outlier bounds:

1. **Sliding Window Linear Regression Derivative (N = 15 seconds)**:
   - Replaced flawed endpoint differencing $(a_t - a_{t-21})/21 \times 3600$ with a 15-sample circular ring buffer (`mAltitudeHistory`, `VAM_WINDOW_SIZE = 15`) storing equidistant 1-second altitude updates.
   - Implemented `calculateLinearRegressionSlope()` computing the true least-squares linear regression slope $\hat{\beta} = \frac{\sum (k - \bar{k}) x_k}{M(M^2 - 1)/12}$ across all samples in the buffer.
   - Dynamic slope evaluation during warm-up ($2 \le M < 15$) ensures immediate responsiveness without startup lag.
2. **Stationary Gating & Indoor Drift Suppression**:
   - Queries horizontal speed `speed_mps` from `speedFilteredSensorData` against dynamic threshold `SettingsDataStoreJavaHelper.getSlopeMinSpeed(mContext)` (default 0.5 m/s).
   - If athlete is stationary (`speed_mps < minSpeed`):
     - Suppresses ambient barometric noise ($|\text{raw\_vam}| < 150\text{ m/h}$) strictly to `0 m/h`.
     - Preserves genuine vertical displacement without horizontal movement ($|\text{raw\_vam}| \ge 150\text{ m/h}$, e.g. elevators, ski lifts, climbing walls).
3. **Flat Terrain Noise Deadband**:
   - When moving horizontally (`speed_mps \ge minSpeed`), micro-ripples and road vibrations ($|\text{raw\_vam}| < 35\text{ m/h}$) are clamped to `0 m/h`.
4. **Physical Outlier Boundary Clamping**:
   - VAM values are clamped to $[-3000, 3000]\text{ m/h}$ to reject GPS multipath step jumps.
5. **Preserved Invariants**:
   - Integer m/h emission periodicity every 1s preserved for `mVerticalSpeedSensor`.
   - Accumulator sensors `SensorType.ASCENT` and `SensorType.DESCENT` via `cAltitudeSuperFilter` remain completely untouched.
   - Slope calculation stabilized using smoothed linear regression derivative.

---

## 3. Test & Verification Results

### A. Targeted Unit Test Suite
- Test File: [VerticalSpeedAndSlopeDeviceTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/banalservice/devices/VerticalSpeedAndSlopeDeviceTest.kt)
- Results:
  1. `calculateLinearRegressionSlope_perfectAscent_returnsExactSlope`: PASSED
  2. `calculateLinearRegressionSlope_perfectDescent_returnsExactNegativeSlope`: PASSED
  3. `calculateLinearRegressionSlope_flatAltitude_returnsZeroSlope`: PASSED
  4. `calculateLinearRegressionSlope_fewerThanTwoSamples_returnsZero`: PASSED
  5. `calculateLinearRegressionSlope_warmupTwoSamples_calculatesImmediateSlope`: PASSED
  6. `calculateLinearRegressionSlope_circularBufferWrapsAround_maintainsCorrectChronologicalOrder`: PASSED
  7. `calculateVam_stationaryWithinNoiseThreshold_returnsZero`: PASSED
  8. `calculateVam_stationaryWithGenuineElevatorMovement_preservesVerticalRate`: PASSED
  9. `calculateVam_movingSteadyClimb_smoothlyConvergesToTargetVam`: PASSED
  10. `calculateVam_movingFlatRoadWithinDeadband_returnsZero`: PASSED
  11. `calculateVam_movingSteadyDescent_smoothlyConvergesToNegativeVam`: PASSED
  12. `calculateVam_gpsGlitchOutlier_clampedToThreeThousandBound`: PASSED
  13. `calculateVam_extremeDescentOutlier_clampedToNegativeThreeThousandBound`: PASSED
- Total: 13/13 targeted tests passed (100%).

### B. Clean-Room Regression Test Suite
- Command: `./gradlew testDebugUnitTest`
- Result: Clean-room regression test suite executed successfully with zero failures across all application modules (Build successful in 2m 52s).

---

## 4. Traceability & Living Documentation
- **Requirements**:
  - `REQ-FIL-012`: Stabilized VAM (Vertical Speed) Metric & Stationary Suppression Architecture.
  - Status in `docs/requirements.md`: **Verified**
- **Test Specifications**:
  - `TST-FIL-004`: Stabilized VAM (Vertical Speed) Metric & Stationary Suppression Verification.
  - Status in `docs/tests.md`: **Verified**
