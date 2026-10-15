# Walkthrough: Real-time Mechanical Work (kJ) and Training Stress Score (TSS) Telemetry (ATT-1291)

## Fulfilling REQ-TRK-021 & TST-TRK-013

A complete real-time Mechanical Work (kJ) and Dr. Andrew Coggan Training Stress Score (TSS) telemetry pipeline was engineered across `BANALService`, `CyclingPowerMetricsDevice`, `SensorType`, `SensorFilterDefaults`, Compose Cockpit UI, and targeted unit test suites conforming to ASPICE SWE.3, SWE.4, and SWE.5 standards.

---

### Implemented Architecture & Changes

#### 1. Sensor Definitions, Activity Registration & Formatting
- **`SensorType.java`**:
  - Registered `WORK_KJ` (`fullNameId = R.string.work_kj`, `shortNameId = R.string.work_kj_short` ("kJ"), `unitId = R.string.units_work_kj` ("kJ"), `SensorValueType.DOUBLE`, `IntegerFormatter`, `isInstantaneous = false`).
  - Registered `TSS` (`fullNameId = R.string.tss`, `shortNameId = R.string.tss_short` ("TSS"), `unitId = R.string.units_none`, `SensorValueType.DOUBLE`, `IntegerFormatter`, `isInstantaneous = false`).
  - Mapped icons to `ic_power`: `case POWER, POWER_TO_WEIGHT, NORMALIZED_POWER, INTENSITY_FACTOR, WORK_KJ, TSS -> R.drawable.ic_power;`.
- **`ActivityType.java`**:
  - Registered both `SensorType.WORK_KJ` and `SensorType.TSS` in `ActivityType.BIKE_POWER`.
- **`SensorFilterDefaults.kt`**:
  - Registered `FilterType.INSTANTANEOUS` (1.0s direct) for both `WORK_KJ` and `TSS` as cumulative load telemetry does not require moving-average smoothing.

#### 2. Telemetry Engine & Mathematical Precision (`CyclingPowerMetricsDevice.java`)
- **1Hz Mechanical Work Integration**:
  - For each active second when recording is not suspended and power $P \ge 0.0\text{ W}$, integrates mechanical energy:
    $$W_{\text{joules}} \leftarrow W_{\text{joules}} + (P \times 1.0\text{ s})$$
    $$\text{Work (kJ)} = \frac{W_{\text{joules}}}{1000.0}$$
  - Coasting ($0\text{ W}$) adds $0\text{ J}$ of mechanical work.
- **Active Duration Counter**:
  - Increments active power duration $t$ by 1 second on each valid non-suspended tick.
- **Welford's Online Running Mean Algorithm for 4th Powers**:
  - Replaced unbounded raw sum with Welford recurrence:
    $$N \leftarrow N + 1$$
    $$\bar{Y}_N = \bar{Y}_{N-1} + \frac{y_N - \bar{Y}_{N-1}}{N}$$
    $$\text{NP} = \sqrt[4]{\bar{Y}_N}$$
  - Bounded accumulator value eliminates 53-bit IEEE 754 precision loss across multi-day ultra-endurance rides.
- **Real-Time Coggan Training Stress Score (TSS)**:
  - While warmup $t < 30\text{ s}$ or FTP $\le 0$, TSS emits `null` (formatted as `"--"`).
  - When $t \ge 30\text{ s}$ and FTP is valid:
    $$\text{TSS} = \frac{t \times \text{NP} \times \text{IF}}{\text{FTP} \times 3600.0} \times 100.0 = \frac{t \times \text{NP} \times \text{IF}}{\text{FTP} \times 36.0}$$
- **Pause Gating & Long-Pause Window Strategy**:
  - Recording suspension (manual pause or auto-pause) freezes work integration, active duration counter, and TSS updates.
  - Pauses longer than 30s (`pauseDurationMs > 30_000L`) flush the 30-sample rolling FIFO window (`mPowerWindow30s.clear()`) to decouple post-pause physiological efforts, while retaining cumulative session accumulators to maintain session NP/TSS continuity.
- **Session Reset**:
  - `resetSession()` cleanly resets $W_{\text{joules}} = 0.0$, $t = 0\text{ s}$, `mMean4thPower = 0.0`, `mCount4thPower = 0L`, and clears all sensors to `null`.

#### 3. Cockpit User Guidance & Dialogs
- **`EditSensorFieldDialog.kt`**:
  - Extended FTP baseline configuration prompt to trigger for both `SensorType.INTENSITY_FACTOR` and `SensorType.TSS`.
  - Informs the user when FTP is unconfigured and provides direct navigation to Zone Settings.

#### 4. 9-Language Localization
- Added string tokens `work_kj`, `work_kj_short`, `units_work_kj`, `tss`, `tss_short` across all 9 application locales:
  - English (`values/`)
  - German (`values-de/`)
  - Spanish (`values-es/`)
  - French (`values-fr/`)
  - Italian (`values-it/`)
  - Japanese (`values-ja/`)
  - Dutch (`values-nl/`)
  - Polish (`values-pl/`)
  - Portuguese (`values-pt/`)

---

### Verification Evidence (TST-TRK-013)

1. **Mechanical Work & TSS Mathematical Verification (`MechanicalWorkAndTssMathTest.kt`)**:
   - `testWorkIntegration_constantPower_accumulatesCorrectKilojoules`: PASS ($10\text{ s}$ at $200\text{ W} = 2.0\text{ kJ}$).
   - `testWorkIntegration_coastingZeroWatts_doesNotAddEnergy`: PASS ($0\text{ W}$ adds $0\text{ J}$).
   - `testWorkIntegration_negativeOrInvalidPower_ignored`: PASS.
   - `testWorkIntegration_pauseGating_freezesAccumulation`: PASS.
   - `testTss_warmupReturnsNull`: PASS ($t < 30\text{ s}$ returns `null`).
   - `testTss_canonicalOneHourAtFtp_equals100`: PASS ($3600\text{ s}$ at $250\text{ W}$ with $\text{FTP} = 250\text{ W} \rightarrow \text{TSS} = 100.0$, $\text{Work} = 900.0\text{ kJ}$).
   - `testTss_canonicalHalfHourAtFtp_equals50`: PASS ($1800\text{ s}$ at $250\text{ W}$ with $\text{FTP} = 250\text{ W} \rightarrow \text{TSS} = 50.0$, $\text{Work} = 450.0\text{ kJ}$).
   - `testTss_twoHoursSweetSpot`: PASS ($7200\text{ s}$ at $225\text{ W}$ ($0.90\text{ IF}$) $\rightarrow \text{TSS} \approx 162.0$, $\text{Work} = 1620.0\text{ kJ}$).
   - `testTss_unconfiguredFtp_returnsNull`: PASS.
   - `testTss_midRideFtpChange_reactsDynamically`: PASS.
   - `testLongRideStability_welfordAlgorithm24Hours`: PASS (24 hours at 400W: $\text{TSS} = 2400.0$, $\text{Work} = 34,560\text{ kJ}$, zero floating point drift).
   - `testLongPause_windowFlushed_staleSamplesPrevented`: PASS (15-minute pause flushes rolling window, retains pre-pause NP continuity).

2. **Cycling Power Metrics Engine & Sensor Verification (`CyclingPowerMetricsDeviceTest.kt`)**:
   - `testCalculateMetrics_workKj_accumulatesCorrectly`: PASS.
   - `testCalculateMetrics_tss_calculatesCorrectly`: PASS.
   - `testCalculateMetrics_tss_unconfiguredFtp_returnsNull`: PASS.
   - `testCalculateMetrics_pauseGating_freezesWorkAndTss`: PASS.
   - `testCalculateMetrics_longPause_flushesRollingWindowAndRetainsNpContinuity`: PASS.

3. **Sensor Filter Defaults Verification (`SensorFilterDefaultsTest.kt`)**:
   - `getDefaultFilterForType_workKj_returnsInstantaneous`: PASS.
   - `getDefaultFilterForType_tss_returnsInstantaneous`: PASS.

4. **9-Language String Parity Verification (`TranslationParityTest.kt`)**:
   - All translation parity tests: PASS.

5. **Clean-Room Full Suite Regression Execution**:
   - Command: `./gradlew testDebugUnitTest`
   - Result: 100% test pass rate with zero regressions across all unit tests in the codebase.
