# Stage 1 Analysis: ATT-6744 - Detect Firmware-Smoothed & Synthetic RR Streams

**Ticket**: [ATT-6744](https://rainerblind.atlassian.net/browse/ATT-6744)  
**Sub-task**: [ATT-6772](https://rainerblind.atlassian.net/browse/ATT-6772) (`[Analysis]`)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability: Real-Time Autonomic Metrics, Aerobic Threshold Guidance & Readiness Analytics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6744` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
Budget dual-band ECG chest straps (e.g. Magene H64/H303, CooSpo) apply internal moving-average smoothing to beat-to-beat intervals in firmware before BLE `0x2A37` broadcast, while some optical/budget sensors synthesize stepped RR intervals from integer BPM ($RR = 60000 / \text{BPM}$).
Because smoothed streams lack abrupt spike/step outliers, the Lipponen & Tarvainen (2019) artifact detector (`RrArtifactFilter.kt`) reports 0.0% artifacts (green light).
However, adjacent interval differences collapse ($rMSSD$ 3–8 ms even at rest), while short-scale fluctuations $F(4)$ are erased and slow 16-beat drift $F(16)$ remains, inflating DFA $\alpha_1$ to 1.40–1.85 (smooth Brownian regime).
In real human physiology, $rMSSD < 10.0\text{ ms}$ and DFA $\alpha_1 > 1.35$ are mutually exclusive: vagal withdrawal during high-intensity exercise drives $\alpha_1$ toward white noise ($\alpha_1 < 0.75$, typically 0.40–0.50), never $> 1.35$.
Unchecked, smoothed streams deceive live aerobic pacing (falsely displaying green Zone 2) and corrupt longitudinal morning readiness baselines ($SWC$ in `HrvReadiness.db`).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Physiological Discordance Mechanism**: Healthy human sinus rhythm exhibits autonomic correlation: resting vagal tone produces high $rMSSD$ (25–100 ms) and $\alpha_1 \approx 0.9\text{--}1.2$. Sympathetic exercise suppresses $rMSSD$ (<10 ms) while collapsing fractal correlation ($\alpha_1 \to 0.5$). Moving-average filtering artificially damps high-frequency variance $\sum (RR_{i+1} - RR_i)^2 \implies rMSSD < 10\text{ ms}$, while creating artificial multi-beat persistence in integrated profiles $\implies \alpha_1 > 1.35$.
* **Independent Peer Review & Verification**:
  * Live inspection of `RrArtifactFilter.kt` confirms it tests median deviations $mRR$ and differences $dRR$ against distribution thresholds ($c_1=c_2=5.2$). Monotonic smoothed curves produce near-zero $dRR$, yielding 0% artifact flags.
  * Inspection of `HRVMetricsDevice.kt` (lines 146–167) confirms `mHrvDfaA1Sensor` gates solely on `artifactRate > 5.0%`. A smoothed stream with 0.0% artifacts completely bypasses gating and publishes deceptive $\alpha_1$.
  * Mathematical audit confirms Turning-Point Ratio ($TPR = \text{count(peaks/valleys)} / (N-2)$ of consecutive differences) for moving averages drops below 0.20 (vs. $2/3 \approx 0.67$ for stochastic noise and 0.50–0.70 for respiratory sinus arrhythmia).
  * Quantized synthetic verification: integer BPM derives stepped plateaus where $>55\%$ of consecutive differences satisfy $|\Delta RR_i| < 1.0\text{ ms}$ over $\ge 30$ beats.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Pure, stateless classifier `HrvSignalQualityAnalyzer.kt` in `com.atrainingtracker.banalservice.devices.hrv` returning `HrvSignalQualityResult` (`VALID`, `INSUFFICIENT_DATA`, `HIGH_ARTIFACTS`, `SMOOTHED_STREAM`, `QUANTIZED_SYNTHETIC`).
  * Implement detection rules: Smoothed stream ($rMSSD < 10.0\text{ ms}$ AND $\alpha_1 > 1.35$, OR $TPR < 0.20$ AND $\alpha_1 > 1.30$), Quantized synthetic ($>55\%$ pairs with $|\Delta RR| < 1.0\text{ ms}$ over $\ge 30$ beats), Artifact gate ($>5.0\%$), and Insufficient data gate ($<30$ beats).
  * Expose current `signalQualityResult` / `signalQualityStatus` from `HRVMetricsDevice` on each 5s tick.
  * Integrate evaluation into `MorningHrvCheckViewModel.kt` exposing status in `MorningHrvUiState`.
  * Provide helper evaluation in `HrvPostWorkoutRecomputer.kt`.
  * Comprehensive unit tests in `HrvSignalQualityAnalyzerTest.kt` verifying Magene-smoothed, synthetic stepped, authentic rest ECG, and authentic high-intensity exercise ECG.
* **Out-of-Scope Non-Goals (Reserved for ATT-6745)**:
  * UI warning cards in `MorningHrvCheckScreen`, `HrvInfoBottomSheet`, and `HrvAlpha1ScatterCard` (governed by ATT-6745).
  * Cockpit tile visual zone suppression and 9-language translation of UI warning strings (ATT-6745).
  * Altering SQLite schemas (`WorkoutRrIntervals`, `HrvReadiness.db`, `WorkoutSamples.db`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-TRK-060` (*Lipponen & Tarvainen RR Artifact Correction, Real-Time HRV Engine & NeuroKit2 Golden Parity*), adding net-new sub-requirement `REQ-TRK-060.7` (*Firmware-Smoothed & Synthetic RR Stream Signal Quality Detection*). Targets `HrvSignalQualityAnalyzer.kt`, `HRVMetricsDevice.kt`, `MorningHrvCheckViewModel.kt`, `HrvPostWorkoutRecomputer.kt`.
* **Historical Origin & Commit Trace**: Commit `4bad7272` (`ATT-3541`) introduced `REQ-TRK-060` with artifact gate ($>5.0\%$) and bias-free $rMSSD$.
* **Root Reason for Existing Formulation**: `ATT-3541` focused on physiological artifacts (ectopic spikes, missed beats, RF packet loss). Firmware moving-average pre-filtering in budget hardware was unanticipated.
* **Preservation of Core Invariants**:
  * *Authentic High-Intensity Exercise*: $rMSSD \approx 4.5\text{ ms}, \alpha_1 \approx 0.48, TPR \approx 0.60$ is correctly classified as `VALID` ($\alpha_1 \le 1.35$ and $\alpha_1 \le 1.30$ prevent false positives).
  * *Authentic Rest ECG*: $rMSSD \approx 42\text{ ms}, \alpha_1 \approx 1.12, TPR \approx 0.60$ is classified as `VALID`.
  * *Rule 51 & 52 Invariants*: `HRVMetricsDevice` resets signal quality state in `resetSession()` and emits zero phantom quality states before physical RR arrival.

---

## 5. Architectural Strategy & High-Level Solution
* **Pure DSP Classifier (`HrvSignalQualityAnalyzer.kt`)**: Stateless, allocation-conscious evaluation function taking beat intervals with pre-computed or on-the-fly $rMSSD$, $\alpha_1$, and artifact rate.
* **Virtual Device Telemetry Integration (`HRVMetricsDevice.kt`)**: Updates exposed `@Volatile var signalQualityResult: HrvSignalQualityResult` on every 5s scheduled tick.
* **Waking Protocol Guard (`MorningHrvCheckViewModel.kt`)**: Classifies post-hoc window beats in `completeMeasurement()` and holds status in `MorningHrvUiState`.
* **Post-Workout Recomputation (`HrvPostWorkoutRecomputer.kt`)**: Provides `analyzeSignalQuality(beats: List<FilteredRrBeat>): HrvSignalQualityResult` for session-wide verification.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero false positive classifications on authentic exercise ECG ($rMSSD < 10\text{ ms}$ with $\alpha_1 \le 1.35$) and authentic rest ECG.
  2. Rule 51 (state seeding parity) & Rule 52 (presence gating before RR arrival) preserved in `HRVMetricsDevice`.
  3. No breaking changes or regressions in existing HRV unit tests (`RrArtifactFilterTest`, `DfaCalculatorTest`, `HrvGoldenOracleTest`, `MorningHrvCheckViewModelTest`).
* **Risk Rating**: **LOW** (Pure mathematical analyzer with isolated integration; strictly bounded at 2 SP without database schema alterations).
