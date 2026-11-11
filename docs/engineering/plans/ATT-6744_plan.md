# Stage 3: Implementation Plan - ATT-6744: Detect Firmware-Smoothed & Synthetic RR Streams

**Ticket**: [ATT-6744](https://rainerblind.atlassian.net/browse/ATT-6744)  
**Sub-task**: [ATT-6774](https://rainerblind.atlassian.net/browse/ATT-6774) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*HRV Autonomic Metrics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-TRK-067` | **Test Mapping**: `TST-TRK-067`  
**Branch**: `feature/ATT-6744` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Description & Background
Budget dual-band ECG straps (e.g., Magene H64/H303, CooSpo) apply internal moving-average smoothing to beat intervals, collapsing $rMSSD$ (3–8 ms) while inflating DFA $\alpha_1$ (>1.35) into an artificial Brownian regime without triggering outlier flags. Low $rMSSD$ and high $\alpha_1$ are physiologically mutually exclusive in human sinus rhythm. Additionally, optical/budget sensors derive stepped plateaus from integer BPM ($RR = 60000 / \text{BPM}$). This plan details the construction of `HrvSignalQualityAnalyzer`, integration into `HRVMetricsDevice`, `MorningHrvCheckViewModel`, and `HrvPostWorkoutRecomputer`, and comprehensive DSP unit testing.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-TRK-067` (*Firmware-Smoothed & Quantized Synthetic RR Stream Detection*)
  * `REQ-TRK-067.1` & `REQ-TRK-067.2`: Pure classifier `HrvSignalQualityResult` and pre-gating ($N < 30$, artifacts $> 5\%$).
  * `REQ-TRK-067.3`: Smoothed stream detection ($rMSSD < 10\text{ ms}$ & $\alpha_1 > 1.35$, or $TPR < 0.20$ & $\alpha_1 > 1.30$).
  * `REQ-TRK-067.4`: Quantized synthetic detection ($>55\%$ pairs with $|\Delta RR| < 1.0\text{ ms}$ over $\ge 30$ beats).
  * `REQ-TRK-067.5`: Preservation of authentic rest ($rMSSD \approx 42\text{ ms}$, $\alpha_1 \approx 1.12$) and intense exercise ($rMSSD \approx 4.5\text{ ms}$, $\alpha_1 \approx 0.48$).
  * `REQ-TRK-067.6`: Telemetry and engine wiring (`HRVMetricsDevice`, `MorningHrvCheckViewModel`, `HrvPostWorkoutRecomputer`).
* **Test Mapping**: `TST-TRK-067` (`TST-TRK-067.1` to `TST-TRK-067.7`).

---

## 3. System Invariants & Preserved Behavior
1. **Zero False Positives on Authentic Physiology**: High-intensity exercise and resting ECG evaluate strictly to `VALID`.
2. **Rule 51 & 52 Invariants**: `HRVMetricsDevice` seeds default state upon `resetSession()` and gates telemetry prior to RR arrival.
3. **UI Consistency**: No visual or presentation changes in this ticket (UI warning banners partitioned to ATT-6745).
4. **Zero SQLite Schema Changes**: No database migrations or DDL mutations; state is held transiently in memory/StateFlow.

---

## 4. Proposed Architectural Changes
* **DSP Classifier Layer (`banalservice.devices.hrv`)**:
  * `HrvSignalQualityResult.kt`: Enum `HrvSignalQualityStatus` (`VALID`, `INSUFFICIENT_DATA`, `HIGH_ARTIFACTS`, `SMOOTHED_STREAM`, `QUANTIZED_SYNTHETIC`) and data class `HrvSignalQualityResult`.
  * `HrvSignalQualityAnalyzer.kt`: Pure, stateless object computing $TPR$, zero-diff rate, and evaluating discordance rules.
* **Virtual Device & Service Integration**:
  * `HRVMetricsDevice.kt`: Expose `@Volatile var signalQualityResult` updated on each 5s scheduled tick.
  * `MorningHrvCheckViewModel.kt`: Add `signalQualityStatus` and `signalQualityResult` to `MorningHrvUiState`, evaluated in `completeMeasurement()`.
  * `HrvPostWorkoutRecomputer.kt`: Add `analyzeSignalQuality(beats: List<FilteredRrBeat>): HrvSignalQualityResult` helper.
* **SWE.2 Architecture Update**: Allocate `HrvSignalQualityAnalyzer` and `HRVMetricsDevice` under Live Cockpit in `docs/architecture.md`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (Core DSP Classifier & Tests)**: Implement `HrvSignalQualityResult.kt` and `HrvSignalQualityAnalyzer.kt`. Author `HrvSignalQualityAnalyzerTest.kt` verifying Magene-smoothed, synthetic stepped, authentic rest, authentic exercise, artifact, and insufficient sample gates.
2. **Step 2 (HRVMetricsDevice 5s Tick Wiring)**: Wire `HrvSignalQualityAnalyzer` into `HRVMetricsDevice.kt` 5s tick; update `HRVMetricsDeviceTest.kt` for Rule 51/52 contract.
3. **Step 3 (Morning HRV & Post-Workout Recomputer Wiring)**: Update `MorningHrvCheckViewModel.kt` (`MorningHrvUiState`) and `HrvPostWorkoutRecomputer.kt` helper; update corresponding unit tests.
4. **Step 4 (SWE.2 Architecture Documentation Update)**: Synchronize `docs/architecture.md` (§2) component allocations.
5. **Step 5 (Targeted Test Execution & Code Lock Verification)**: Run clean-room suite:
   `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.hrv.*" --tests "com.atrainingtracker.trainingtracker.ui.hrv.*" --tests "com.atrainingtracker.trainingtracker.database.hrv.HrvPostWorkoutRecomputerTest"`

---

## 6. Verification & Rollback Plan
* **Verification**: 100% pass rate on `HrvSignalQualityAnalyzerTest`, `HRVMetricsDeviceTest`, and `MorningHrvCheckViewModelTest`.
* **Rollback**: Isolated branch `feature/ATT-6744` can be cleanly reverted without affecting develop.
