# Stage 5 Walkthrough & Verification Report: ATT-3778

## 1. Executive Summary
- **Ticket**: `ATT-3778` — *Use growing 1..29s rolling window during initial 30s warm-up for live Normalized Power, Intensity Factor, and TSS*
- **Requirement ID**: `REQ-TRK-023` (amending `REQ-TRK-020` and `REQ-TRK-021`, enforcing Rule 40)
- **Test ID**: `TST-TRK-015`
- **Objective**: Replace the hard 30-sample warm-up gate (`mPowerWindow30s.size() < 30`) in `CyclingPowerMetricsDevice.calculateMetrics()` with a growing `1..29s` rolling window (`sum / mPowerWindow30s.size()`) so live Normalized Power (`NORMALIZED_POWER`), Intensity Factor (`INTENSITY_FACTOR`), and Training Stress Score (`TSS`) are emitted from the very first active second (`t = 1s`) rather than showing `"--"` for 29 seconds.

---

## 2. Summary of Technical Changes

### 2.1 Production Code (`CyclingPowerMetricsDevice.java`)
- Removed the `if (mPowerWindow30s.size() < 30)` branch that emitted `null` for `mNormalizedPowerSensor`, `mIntensityFactorSensor`, and `mTssSensor` during the first 29 active seconds.
- Replaced it with an `if (!mPowerWindow30s.isEmpty())` block that computes the rolling average over the current window size $k = \text{mPowerWindow30s.size()} \in [1, 30]$ (`double pRolling = sum / (double) mPowerWindow30s.size()`) and updates Welford's online 4th-power running mean (`mCount4thPower++`, `mMean4thPower += (pRollingPow4 - mMean4thPower) / (double) mCount4thPower`, `mLastCalculatedNp = Math.pow(mMean4thPower, 0.25)`) on every valid 1-Hz tick.
- Preserved all existing invariants:
  - `mPowerWindow30s` FIFO eviction at 30 samples (`if (mPowerWindow30s.size() == 30) mPowerWindow30s.pollFirst()`).
  - Recording suspension gating (`isRecordingSuspended()`).
  - Long-pause (`> 30,000 ms`) buffer flush (`mPowerWindow30s.clear()`), followed by a growing `1..29s` post-pause rolling window that blends smoothly into the cumulative session Welford mean.
  - Unconfigured FTP (`ftp == null || ftp <= 0`) handling.

### 2.2 Unit Test Suites (`TST-TRK-015`)
- **`NormalizedPowerMathTest.kt`**:
  - Updated `NormalizedPowerEngine.ingestSample` to compute `pRolling = sum / window30s.size.toDouble()`.
  - Added `testWarmupWindow_growing1To29Samples_emitsValidNpAndIfFromFirstSample` verifying non-null `NP = 200.0 W` and `IF = 0.80` on ticks `1..29`.
  - Added `test_NP_Transition_Boundary_Stability` verifying $C^0$ and $C^1$ step-response continuity (`abs(NP(30) - NP(29)) < 1e-6` for constant power and bounded smooth step transition `< 1.5 W` for alternating variable power) across the $k = 29 \to k = 30 \to k = 31$ transition boundary.
  - Updated `testLongDurationStability_simulated5Hours_accumulatesWithoutPrecisionLoss` to assert `count4thPower == 18000L` across 18,000 active seconds.
- **`MechanicalWorkAndTssMathTest.kt`**:
  - Updated `TestTelemetryEngine.ingestSample` to compute `pRolling = sum / window.size.toDouble()`.
  - Updated `testTss_growingWarmupWindow_emitsValidTssFromFirstSecond` verifying live `TSS = t / 36.0` on ticks `1..29` at 250W (`FTP = 250`).
  - Updated `testLongPause_windowFlushed_staleSamplesPrevented` verifying post-pause growing window behavior.
- **`CyclingPowerMetricsDeviceTest.kt`**:
  - Updated `testCalculateMetrics_npWarmupWindow_emitsValidFromFirstSecond`, `testCalculateMetrics_pauseGating_freezesNpAndIf`, and `testCalculateMetrics_tss_calculatesCorrectly`.

---

## 3. Verification & Clean-Room Regression Results

### 3.1 Targeted Unit Tests
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.NormalizedPowerMathTest" --tests "com.atrainingtracker.banalservice.devices.MechanicalWorkAndTssMathTest" --tests "com.atrainingtracker.banalservice.devices.CyclingPowerMetricsDeviceTest"
```
- **Result**: `BUILD SUCCESSFUL` (100% pass rate across all test methods in `NormalizedPowerMathTest`, `MechanicalWorkAndTssMathTest`, and `CyclingPowerMetricsDeviceTest`).

### 3.2 Full Clean-Room Regression Suite
```bash
./gradlew testDebugUnitTest
```
- **Result**: `BUILD SUCCESSFUL` (100% pass rate across all project unit tests, 0 failures, 0 regressions).

---

## 4. Bidirectional Traceability Matrix

| Requirement ID | Test Case ID | Production Files | Verification Files | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-023` | `TST-TRK-015` | `CyclingPowerMetricsDevice.java` | `NormalizedPowerMathTest.kt`, `MechanicalWorkAndTssMathTest.kt`, `CyclingPowerMetricsDeviceTest.kt` | **Verified** |
