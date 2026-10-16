# Stage 3 Architecture & Implementation Plan: ATT-3778

## 1. Architectural Overview (SWE.2)
- **Ticket**: `ATT-3778` — Use growing 1..29s rolling window during initial 30s warm-up for live Normalized Power, Intensity Factor, and TSS
- **Layer**: Service / Device Layer (`com.atrainingtracker.banalservice.devices.CyclingPowerMetricsDevice`)
- **Threading / Dispatchers**: Synchronized 1-Hz scheduled execution on `mScheduler` (`Executors.newSingleThreadScheduledExecutor()`).

### 1.1 Mathematical Justification & Derivative ($C^1$) Step-Response Proof
- **Constant Power Step Response ($C^0$ and $C^1$ Exactness)**:
  - For any constant power input $P(t) = P_0$, the growing window average at tick $t \in [1, 29]$ is $\overline{P}_k(t) = \frac{1}{k}\sum_{i=1}^{k} P_0 = P_0$, and at $t \ge 30$ is $\overline{P}_{30}(t) = \frac{1}{30}\sum_{i=1}^{30} P_0 = P_0$.
  - Consequently, $(\overline{P}_k(t))^4 = P_0^4$ for all $t \ge 1$, the Welford running mean is identically `mMean4thPower` $= P_0^4$, and $\text{NP}(t) = P_0$ for all $t \ge 1$.
  - Therefore, at the $t = 29 \to t = 30$ boundary:
    $$|\text{NP}(30) - \text{NP}(29)| = |P_0 - P_0| = 0 < \epsilon$$
    $$\Delta \text{NP}(30) - \Delta \text{NP}(29) = 0$$
    There is zero spike, zero oscillation, and zero derivative discontinuity.
- **Stochastic / Time-Varying Input Known Characteristic**:
  - For non-constant power $P(t)$, switching from the growing prefix window ($k = 1 \dots 29$, where no old sample is evicted) to the fixed 30-sample sliding window ($k = 30$, where $P(t-30)$ is evicted via `pollFirst()`) changes the finite-difference increment of $\overline{P}_k(t)$ by bounded order $O\left(\frac{|P(t) - P(t-30)|}{30}\right)$, which is damped by an additional factor of $1 / T = 1 / 30$ inside Welford's cumulative mean. This bounded $C^1$ slope transition is an inherent, documented mathematical property of finite-horizon sliding windows with prefix warm-up.

### 1.2 Cumulative Session Weighting & `mCount4thPower` Semantics
- **Cumulative Session NP vs. Instantaneous NP**:
  - This implementation calculates **Cumulative Session Normalized Power**, **Intensity Factor**, and **Training Stress Score** in strict compliance with the explicit specification in parent ticket `ATT-3778` ("Option 1") and Governance Rule 40 (`docs/coding_standards.md` Section 1.6).
- **Explicit `mCount4thPower` Definition**:
  - `mCount4thPower` represents the **total number of active 1-Hz rolling-average evaluation ticks** across the workout session (incrementing by `1` on every valid 1-Hz tick where `!mPowerWindow30s.isEmpty()`, both when $1 \le k < 30$ and when $k = 30$).
  - Each active second $t$ contributes exactly one 1-second time-slice rolling average $\overline{P}_k(t)^4$ with equal unit temporal weight ($1\text{ s}$) to `mMean4thPower`, maintaining exact 1:1 temporal alignment with `mActivePowerDurationSec`.
  - When a long pause (`> 30s`) flushes `mPowerWindow30s`, `mCount4thPower` and `mMean4thPower` are retained as cumulative session accumulators, and each post-pause tick ($k = 1 \dots 29$) enters the cumulative Welford mean with the same 1-second temporal weight (`1 / mCount4thPower`).

### 1.3 Code Implementation in `CyclingPowerMetricsDevice.calculateMetrics()`
```java
if (!mPowerWindow30s.isEmpty()) {
    double sum = 0.0;
    for (double p : mPowerWindow30s) {
        sum += p;
    }
    double pRolling = sum / (double) mPowerWindow30s.size();
    double pRollingPow4 = Math.pow(pRolling, 4.0);

    // Welford's online running mean of 4th powers (1 tick = 1 active second)
    mCount4thPower++;
    mMean4thPower += (pRollingPow4 - mMean4thPower) / (double) mCount4thPower;
    mLastCalculatedNp = Math.pow(mMean4thPower, 0.25);
}
```

## 2. Atomic Step Breakdown

### Step 1: Update `CyclingPowerMetricsDevice.java`
- **Target File**: `app/src/main/java/com/atrainingtracker/banalservice/devices/CyclingPowerMetricsDevice.java`
- **Changes**:
  - Update class JavaDoc and `calculateMetrics()` to compute `pRolling = sum / (double) mPowerWindow30s.size()` whenever `!mPowerWindow30s.isEmpty()`, updating Welford's online running mean (`mCount4thPower`, `mMean4thPower`, `mLastCalculatedNp`) from tick 1 (`REQ-TRK-023` / Rule 40).

### Step 2: Update Unit Test Suites & Boundary Stability Verification
- **Target Files**:
  - `app/src/test/java/com/atrainingtracker/banalservice/devices/NormalizedPowerMathTest.kt`:
    - `testWarmupWindow_growing1To29Samples_emitsValidNpAndIfFromFirstSample`: Verify ticks `1..29` emit valid NP and IF.
    - `test_NP_Transition_Boundary_Stability`: Explicitly assert `abs(NP(30) - NP(29)) < 1e-6` and zero oscillation across $t = 25 \dots 35$ for constant power input, plus bounded transition across $t = 29 \to 30$ for ramp/variable power input.
    - Verify `count4thPower == 18000L` after 18,000 samples (5 hours).
  - `app/src/test/java/com/atrainingtracker/banalservice/devices/MechanicalWorkAndTssMathTest.kt`:
    - Verify `getTss()` emits valid TSS from second 1 (`t / 36.0`) and long-pause window flushing updates NP cleanly over the growing post-pause window.
  - `app/src/test/java/com/atrainingtracker/banalservice/devices/CyclingPowerMetricsDeviceTest.kt`:
    - Verify `normalizedPowerSensor`, `intensityFactorSensor`, and `tssSensor` emit non-null values from tick 1 and across pause/resume boundaries.
- **Verification Command**:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.*"`

## 3. Non-Negotiable Invariants & Rollback Safety
- **Rule 4 Gate Pre-Check**: `python3 tools/jira_util.py check-gate <Impl-Plan-Key>` MUST exit 0 (`Erledigt`) before modifying `CyclingPowerMetricsDevice.java`.
- **Welford Precision & Pause Invariants**: 64-bit `double` Welford online mean, recording suspension gating, long-pause (`> 30s`) buffer flushing while preserving cumulative session accumulators, and unconfigured FTP (`null`/`<= 0`) handling are 100% preserved.
- **Rollback Strategy**: `git checkout -- app/src/main/java/com/atrainingtracker/banalservice/devices/CyclingPowerMetricsDevice.java`.
