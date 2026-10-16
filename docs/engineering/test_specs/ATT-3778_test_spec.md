# Stage 2 Requirement & Test Specification: ATT-3778

## 1. Requirement Specification (`REQ-TRK-023`)

### `REQ-TRK-023`: Growing 1..29s Warm-Up Rolling Window for Live Normalized Power, Intensity Factor, and TSS (Rule 40)
- **Parent Ticket**: `ATT-3778`
- **Governing Standard**: `docs/coding_standards.md` Section 1.6 (*Growing Warm-Up Window Over Blank Delay for Rolling Telemetry Metrics — Rule 40*)
- **Requirement Statement**:
  1. **Growing Rolling Window Evaluation (`REQ-TRK-023.1`)**:
     - During the initial warm-up phase and after a long-pause window flush (`1 <= mPowerWindow30s.size() < 30`), `CyclingPowerMetricsDevice.calculateMetrics()` SHALL compute the rolling power average over the currently accumulated $k = \text{mPowerWindow30s.size()}$ samples (`double pRolling = sum / (double) mPowerWindow30s.size()`), update Welford's online 4th-power running mean (`mCount4thPower++`, `mMean4thPower += (pRollingPow4 - mMean4thPower) / (double) mCount4thPower`), and compute `mLastCalculatedNp = Math.pow(mMean4thPower, 0.25)`.
  2. **Immediate Live Telemetry Emission From Second 1 (`REQ-TRK-023.2`)**:
     - Starting from the first valid power sample (`k = 1`), `CyclingPowerMetricsDevice` SHALL publish non-null live `NORMALIZED_POWER` (`mLastCalculatedNp`) and, when `ftp != null && ftp > 0`, non-null live `INTENSITY_FACTOR` (`NP / FTP`) and `TSS` (`(mActivePowerDurationSec * NP * IF) / (FTP * 36.0)`).
  3. **Seamless Transition to Full 30s Window & Empty Guard (`REQ-TRK-023.3`)**:
     - From second 30 onward (`mPowerWindow30s.size() == 30`), `pRolling` SHALL evaluate over the full 30-sample FIFO sliding window (`sum / 30.0`).
     - Only when `mPowerWindow30s.isEmpty()` (or when instantaneous input `power` is `null`, `NaN`, infinite, or `< 0.0`) SHALL `NORMALIZED_POWER`, `INTENSITY_FACTOR`, and `TSS` emit `null`.

### Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
1. **Original Requirement ID & Target**:
   - Net-new requirement `REQ-TRK-023`, amending the initial 29-second warm-up behavior of `REQ-TRK-020` (`ATT-1290`) and `REQ-TRK-021` (`ATT-1291`). Targets `CyclingPowerMetricsDevice.java`, `NormalizedPowerMathTest.kt`, `MechanicalWorkAndTssMathTest.kt`, and `CyclingPowerMetricsDeviceTest.kt`.
2. **Historical Origin & Commit Trace**:
   - Sprint 2026-42.4 (`ATT-1290` commit `6e7d4512`, `ATT-1291` commit `f81ba92a`).
3. **Root Reason for Existing Formulation**:
   - The original implementation waited for 30 full samples before emitting NP/IF/TSS, following offline post-processing conventions, which caused a misleading 29-second `"--"` display on live cockpit tiles.
4. **Preservation of Core Invariants**:
   - Welford's online 4th-power running mean, 0W coasting inclusion, recording suspension gating, long-pause (`> 30s`) buffer flushing, and unconfigured FTP (`null`/`<= 0`) handling remain 100% preserved.

### Acceptance Criteria (Given-When-Then)
- **Given** an active cycling workout with valid power samples (`250.0 W`) and configured FTP (`250 W`),
- **When** `calculateMetrics()` runs during seconds `1..29` (`1 <= mPowerWindow30s.size() < 30`),
- **Then** `NORMALIZED_POWER` emits `250.0`, `INTENSITY_FACTOR` emits `1.0`, and `TSS` emits `(t * 250 * 1.0) / (250 * 36.0)` from second 1 without emitting `null`.
- **Given** a long pause (`> 30s`) that flushes `mPowerWindow30s`,
- **When** recording resumes with a new valid power sample (`100.0 W`),
- **Then** `mPowerWindow30s` computes its post-pause rolling average over the growing `1..29s` post-pause window (`sum / k`) and updates Welford's online running mean seamlessly.

## 2. Test Specification (`TST-TRK-015`)

### `TST-TRK-015`: Growing Warm-Up Window Verification Suite (`NormalizedPowerMathTest.kt`, `MechanicalWorkAndTssMathTest.kt`, `CyclingPowerMetricsDeviceTest.kt`)
1. **`NormalizedPowerMathTest.kt`**:
   - `testWarmupWindow_growing1To29Samples_emitsValidNpAndIfFromFirstSample`: Verify that for ticks `1..29`, `NP` and `IF` are non-null and equal the growing window rolling average ($200.0\text{ W}$, $\text{IF} = 0.80$).
   - Verify `count4thPower == 18000L` after 18,000 samples (5 hours) since sample 1 is included.
2. **`MechanicalWorkAndTssMathTest.kt`**:
   - `testTss_growingWarmupWindow_emitsValidTssFromFirstSecond`: Verify that during ticks `1..29` at 250W (`FTP = 250`), `getTss()` is non-null from tick 1 (`t / 36.0`).
   - Verify `testLongPause_windowFlushed_staleSamplesPrevented` updates NP immediately on tick 1 after resume using the growing post-pause window (`k = 1..29`) without retaining stale pre-pause window samples.
3. **`CyclingPowerMetricsDeviceTest.kt`**:
   - `testCalculateMetrics_npWarmupWindow_emitsValidGrowingWindowValuesFromFirstSecond`: Verify `normalizedPowerSensor`, `intensityFactorSensor`, and `tssSensor` emit non-null values on ticks `1..29`.
   - Update `testCalculateMetrics_pauseGating_freezesNpAndIf` and `testCalculateMetrics_tss_calculatesCorrectly` and `testCalculateMetrics_longPause_flushesRollingWindowAndRetainsNpContinuity` to reflect growing window calculation from tick 1.

## 3. Localization Audit
- Pure telemetry calculation in `CyclingPowerMetricsDevice.java`; no string resources added or modified.

## 4. Traceability Matrix

| Requirement ID | Test ID | Test Classes | Status |
| :--- | :--- | :--- | :--- |
| `REQ-TRK-023` | `TST-TRK-015` | `NormalizedPowerMathTest.kt`, `MechanicalWorkAndTssMathTest.kt`, `CyclingPowerMetricsDeviceTest.kt` | Planned |
