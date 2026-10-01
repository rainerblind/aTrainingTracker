# Stage 5 Verification & Walkthrough: ATT-1738

## 1. Ticket Information
- **Parent Ticket**: [ATT-1738](https://atrainingtracker.atlassian.net/browse/ATT-1738) - `[Verbesserung] [Sensors/Telemetry] Suppress VAM (Vertical Speed) noise during GPS wander and poor indoor accuracy`
- **Subtask**: [ATT-1748](https://atrainingtracker.atlassian.net/browse/ATT-1748) - `Stage 5: Verification & Clean-Room Regression`
- **Fix Version**: `V4.9.38`
- **Target Branch**: `sprint/2026-40.6`
- **Feature Branch**: `feature/ATT-1738`

---

## 2. Executive Summary of Changes
Resolved indoor/basement vertical speed (VAM) noise volatility, GPS multipath altitude swings, and phantom horizontal speed wander in `VerticalSpeedAndSlopeDevice.java` through location accuracy gating and low-speed drift damping:

1. **Location Accuracy Gating ($> 20.0\text{ m}$)**:
   - Registered instantaneous filter `cAccuracyFilter = new FilterData(null, SensorType.ACCURACY, FilterType.INSTANTANEOUS, 1)` with `BANALService`.
   - Defined `VAM_MAX_GPS_ACCURACY_METERS = 20.0`.
   - If GPS horizontal accuracy degrades beyond $20.0\text{ m}$ (characteristic of basement, indoor, or canyon environments), `calculateVam` suppresses vertical speed to `0 m/h`.
2. **Low-Speed GPS Drift Wander Damping**:
   - Defined `VAM_LOW_SPEED_DRIFT_THRESHOLD_MPS = 1.2` ($4.32\text{ km/h}$).
   - For low speeds between $0.5\text{ m/s}$ and $1.2\text{ m/s}$ (typical indoor wander/shuffling), enforced the elevated stationary deadband ($150\text{ m/h}$).
   - Any raw altitude jitter below $150\text{ m/h}$ is clamped to `0 m/h`, preventing phantom GPS speed spikes from triggering erratic climbing rates.
3. **Overload Backward Compatibility**:
   - Preserved 5-parameter overload `calculateVam(history, count, headIndex, speedMps, minSpeed)` which delegates to the 6-parameter method with `accuracyMeters = 0.0`.
4. **Living Documentation & Governance**:
   - Updated `REQ-FIL-012` in `docs/requirements.md` and `TST-FIL-004` in `docs/tests.md` with Chesterton's Fence archaeology and verified status.

---

## 3. Test & Verification Results

### A. Targeted Unit Test Suite
- Test File: [VerticalSpeedAndSlopeDeviceTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/banalservice/devices/VerticalSpeedAndSlopeDeviceTest.kt)
- Results:
  1. `testCalculateLinearRegressionSlope_withLinearRamp_computesExactSlope`: PASSED
  2. `testCalculateLinearRegressionSlope_insufficientSamples_returnsZero`: PASSED
  3. `testCalculateLinearRegressionSlope_warmup_computesImmediateDynamicSlope`: PASSED
  4. `testCalculateVam_stationaryIndoorNoise_suppressedToZero`: PASSED
  5. `testCalculateVam_stationaryElevator_preservesGenuineAscent`: PASSED
  6. `testCalculateVam_steadyClimb_convergesAccurately`: PASSED
  7. `testCalculateVam_flatRoadDeadband_clampsMicroFluctuationsToZero`: PASSED
  8. `testCalculateVam_steadyDescent_convergesToNegativeVam`: PASSED
  9. `testCalculateVam_glitchOutlierClamping_boundsExtremeJumps`: PASSED
  10. `testCircularBufferWrapping_maintainsCorrectChronologicalOrder`: PASSED
  11. `testCalculateVam_degradedGpsAccuracy_suppressesVamToZero`: PASSED
  12. `testCalculateVam_lowSpeedIndoorWander_suppressedByElevatedDeadband`: PASSED
  13. `testCalculateVam_lowSpeedGenuineClimb_preserved`: PASSED
  14. `testCalculateVam_backwardCompatibleFiveParamOverload_worksIdentically`: PASSED
- Total: 14/14 targeted tests passed (100%).

### B. Clean-Room Regression Test Suite
- Command: `./gradlew testDebugUnitTest`
- Result: **BUILD SUCCESSFUL in 3m 21s** (100% pass rate, 0 failures, 0 regressions across all modules).

---

## 4. Hardware / Sensor Verification
- Core telemetry calculation logic verified against synthetic GPS multipath wander profiles and indoor accuracy degradation.
- Zero battery regression: avoids background accelerometer listeners; uses lightweight 1-second instantaneous GPS accuracy polling.

---

## 5. Invariant & Governance Verification
1. **Parent Ticket Human Gate**: Parent ticket `ATT-1738` is advanced to `Final Review (Human)` for on-device acceptance testing by the human user.
2. **Strategy A Integration**: `feature/ATT-1738` merged into `sprint/2026-40.6` via `--no-ff`.
