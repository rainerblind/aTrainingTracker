# Stage 1 Problem Domain & Root Cause Analysis: ATT-3778

## 1. Forensic Root Cause Analysis (RCA)
- **Symptom / User Complaint**: During the Sprint 2026-42.5 live device review of `ATT-1290` and `ATT-1291`, the cockpit tiles for Normalized Power (`NORMALIZED_POWER`), Intensity Factor (`INTENSITY_FACTOR`), and Training Stress Score (`TSS`) displayed `"--"` (`null`) for the first 29 seconds of recording even though the cycling power meter was actively transmitting valid power readings. To the athlete, a 30-second blank delay looks like a broken sensor connection.
- **Root Cause**:
  - In `app/src/main/java/com/atrainingtracker/banalservice/devices/CyclingPowerMetricsDevice.java` (lines 308–324), when `mPowerWindow30s.size() < 30` and `mLastCalculatedNp == null`, `calculateMetrics()` explicitly emitted `null` for `mNormalizedPowerSensor`, `mIntensityFactorSensor`, and `mTssSensor` and returned early instead of computing the rolling average over the available $k = \text{mPowerWindow30s.size()} \in [1, 29]$ samples.
- **Rule 40 Mandate (`docs/coding_standards.md` Section 1.6)**:
  - Live rolling-window physiological metrics (30-second Coggan Normalized Power, Intensity Factor, and TSS) MUST compute over the growing window ($1 \le k < 30$) during the initial warm-up phase ($t < 30\text{ s}$) and post-long-pause window refill rather than emitting `null` (`"--"`). Only when zero valid samples have been collected (`mPowerWindow30s.isEmpty()`) should `null` be emitted.

## 2. Chesterton's Fence Archaeology (`REQ-PRO-022`)
1. **Original Requirement ID & Target**:
   - Amends the initial warm-up window behavior of `REQ-TRK-020` (*Normalized Power & Intensity Factor*, `ATT-1290`) and `REQ-TRK-021` (*Mechanical Work & Training Stress Score*, `ATT-1291`). Tracked as net-new requirement `REQ-TRK-023`.
2. **Historical Origin & Commit Trace**:
   - Introduced in Sprint 2026-42.4 (`ATT-1290` commit `6e7d4512`, `ATT-1291` commit `f81ba92a`).
3. **Root Reason for Existing Formulation**:
   - Offline post-workout analysis tools traditionally discard the first 29 seconds before the first full 30-second rolling window is populated. Applying that offline batch rule to a live 1-Hz cycling cockpit caused a 29-second `"--"` dead zone at workout start.
4. **Preservation of Core Invariants**:
   - Using `double pRolling = sum / (double) mPowerWindow30s.size()` for $1 \le k \le 30$ provides immediate live NP, IF, and TSS from second 1 (`k = 1..29`) and transitions seamlessly to the standard 30-second rolling window at `k = 30` (`size() == 30`), while preserving Welford's online 4th-power running mean, 0W coasting inclusion, pause gating, long-pause (`> 30s`) buffer flushing, and unconfigured FTP (`null`/`<= 0`) handling.

## 3. Scope Bounding (`ATT-1250`)
- **In Scope**:
  1. Update `CyclingPowerMetricsDevice.java` (`calculateMetrics()`) to compute the rolling average over `k = mPowerWindow30s.size()` (`sum / (double) mPowerWindow30s.size()`) whenever `!mPowerWindow30s.isEmpty()`, updating Welford's online 4th-power mean (`mCount4thPower`, `mMean4thPower`, `mLastCalculatedNp`) and publishing live `NP`, `IF`, and `TSS` from second 1 (`1 <= k <= 30`).
  2. Update `NormalizedPowerMathTest.kt`, `MechanicalWorkAndTssMathTest.kt`, and `CyclingPowerMetricsDeviceTest.kt` to verify growing warm-up window (`1..29s`) emission and seamless `30s+` steady/stochastic rolling window behavior.
- **Out of Scope**:
  - Changes to `PowerToWeight` (`REQ-TRK-019`) or `Work (kJ)` (`REQ-TRK-021.1`) formulas, which already emit from second 1.
