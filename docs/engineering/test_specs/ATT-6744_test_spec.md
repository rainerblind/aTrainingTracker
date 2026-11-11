# Stage 2: Requirement & Test Specification - ATT-6744: Detect Firmware-Smoothed & Synthetic RR Streams

**Ticket**: [ATT-6744](https://rainerblind.atlassian.net/browse/ATT-6744) | **Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*HRV Autonomic Metrics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2` | **Branch**: `feature/ATT-6744`  
**Requirement Mapping**: `REQ-TRK-067` | **Test Spec ID**: `TST-TRK-067`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-TRK-067)

### 1.1 Problem Statement & Rationale
Budget dual-band ECG straps (e.g., Magene H64/H303, CooSpo) apply internal moving-average smoothing to beat-to-beat intervals, collapsing adjacent differences ($rMSSD$ 3–8 ms) while artificially inflating DFA $\alpha_1$ (>1.35) into a smooth Brownian regime without triggering outlier artifact flags. Low $rMSSD$ (<10 ms) and high $\alpha_1$ (>1.35) are physiologically mutually exclusive in human sinus rhythms. Furthermore, optical/budget sensors synthesize stepped RR intervals from integer BPM ($RR = 60000 / \text{BPM}$). The system must detect smoothed and quantized synthetic streams using Turning-Point Ratio ($TPR$), zero-diff rate, and discordance gates without falsely penalizing authentic exercise or rest ECG.

### 1.2 Functional & Architectural Requirements
* **REQ-TRK-067.1** (*Stateless DSP Classifier*): `HrvSignalQualityAnalyzer.kt` SHALL provide a pure, stateless evaluation function returning structured `HrvSignalQualityResult` with statuses `VALID`, `INSUFFICIENT_DATA`, `HIGH_ARTIFACTS`, `SMOOTHED_STREAM`, and `QUANTIZED_SYNTHETIC`.
* **REQ-TRK-067.2** (*Data Sufficiency & Artifact Pre-Gating*): If $N < 30$ beats, status SHALL be `INSUFFICIENT_DATA`; if artifact rate $> 5.0\%$, status SHALL be `HIGH_ARTIFACTS`.
* **REQ-TRK-067.3** (*Smoothed Stream Detection*): Flag `SMOOTHED_STREAM` if ($rMSSD < 10.0\text{ ms}$ AND DFA $\alpha_1 > 1.35$) OR ($TPR < 0.20$ AND DFA $\alpha_1 > 1.30$).
* **REQ-TRK-067.4** (*Quantized Synthetic RR Detection*): Flag `QUANTIZED_SYNTHETIC` if $>55\%$ of consecutive beat pairs satisfy $|\Delta RR_i| < 1.0\text{ ms}$ over $\ge 30$ beats.
* **REQ-TRK-067.5** (*Physiological Invariants Preservation*): Authentic rest ECG ($rMSSD \approx 42\text{ ms}$, $\alpha_1 \approx 1.12$) and high-intensity exercise ECG ($rMSSD \approx 4.5\text{ ms}$, $\alpha_1 \approx 0.48$) SHALL evaluate to `VALID` with zero false-positive flags.
* **REQ-TRK-067.6** (*Engine Telemetry & Service Wiring*): `HRVMetricsDevice` SHALL expose `@Volatile var signalQualityResult` updated on every 5s tick; `MorningHrvCheckViewModel` SHALL evaluate status into `MorningHrvUiState`; `HrvPostWorkoutRecomputer` SHALL provide session-wide evaluation.

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
* *Given* Magene-style smoothed series ($rMSSD \approx 3\text{--}8\text{ ms}$, $\alpha_1 > 1.35$ or $TPR < 0.20$), *When* analyzed, *Then* status is `SMOOTHED_STREAM`.
* *Given* stepped constant-BPM series ($>55\%$ $|\Delta RR| < 1.0\text{ ms}$), *When* analyzed, *Then* status is `QUANTIZED_SYNTHETIC`.
* *Given* authentic resting ECG or authentic intense exercise ECG, *When* analyzed, *Then* status is `VALID`.
* *Given* series with artifact rate $> 5.0\%$ or $< 30$ beats, *When* analyzed, *Then* status is `HIGH_ARTIFACTS` or `INSUFFICIENT_DATA`.
* *Invariants*: Zero false-positive flags during authentic exercise; Rule 51 (state seeding parity) & Rule 52 (presence gating before RR arrival) preserved; zero DB schema modifications; 100% test pass rate.

---

## 2. Test Specification (TST-TRK-067)

### Test Case 1: Empirical Smoothed Stream Classification (`TST-TRK-067.1`)
* **Scope**: Unit Test (`HrvSignalQualityAnalyzerTest.kt`)
* **Assertion**: Verify Magene-smoothed series ($rMSSD \approx 3.3\text{--}7.3\text{ ms}$, $\alpha_1 \approx 1.41\text{--}1.81$) triggers `SMOOTHED_STREAM` via discordance and $TPR < 0.20$ gates.

### Test Case 2: Quantized Synthetic Series Detection (`TST-TRK-067.2`)
* **Scope**: Unit Test (`HrvSignalQualityAnalyzerTest.kt`)
* **Assertion**: Verify stepped constant-BPM series with $>55\%$ $|\Delta RR| < 1.0\text{ ms}$ triggers `QUANTIZED_SYNTHETIC`.

### Test Case 3: Authentic Rest & Exercise Invariant Verification (`TST-TRK-067.3`)
* **Scope**: Unit Test (`HrvSignalQualityAnalyzerTest.kt`)
* **Assertion**: Verify authentic rest ($rMSSD \approx 42\text{ ms}$, $\alpha_1 \approx 1.12$) and exercise ($rMSSD \approx 4.5\text{ ms}$, $\alpha_1 \approx 0.48$) both evaluate strictly to `VALID`.

### Test Case 4: Data Sufficiency & Artifact Pre-Gating (`TST-TRK-067.4`)
* **Scope**: Unit Test (`HrvSignalQualityAnalyzerTest.kt`)
* **Assertion**: Verify $<30$ beats returns `INSUFFICIENT_DATA`; artifact rate $>5.0\%$ returns `HIGH_ARTIFACTS`.

### Test Case 5: HRVMetricsDevice Live Integration & Rule 51/52 Lifecycle (`TST-TRK-067.5`)
* **Scope**: Device Unit Test (`HRVMetricsDeviceTest.kt`)
* **Assertion**: Verify 5s evaluation tick updates `signalQualityResult`, smoothed streams reflect properly, and `resetSession()` resets state cleanly.

### Test Case 6: Morning HRV & Post-Workout Recompute Wiring (`TST-TRK-067.6`)
* **Scope**: Unit Tests (`MorningHrvCheckViewModelTest.kt`, `HrvPostWorkoutRecomputerTest.kt`)
* **Assertion**: Verify `MorningHrvCheckViewModel` populates `MorningHrvUiState`, and `HrvPostWorkoutRecomputer` exposes evaluation helper.

### Test Case 7: Clean-Room Targeted Test Suite Execution (`TST-TRK-067.7`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.hrv.*" --tests "com.atrainingtracker.banalservice.devices.*" --tests "com.atrainingtracker.trainingtracker.ui.morninghrv.*"`
* **Assertion**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-067.1` | Unit (DSP) | `HrvSignalQualityAnalyzer.kt` | `REQ-TRK-067.1`, `REQ-TRK-067.3` | Specified |
| `TST-TRK-067.2` | Unit (DSP) | `HrvSignalQualityAnalyzer.kt` | `REQ-TRK-067.1`, `REQ-TRK-067.4` | Specified |
| `TST-TRK-067.3` | Unit (DSP) | `HrvSignalQualityAnalyzer.kt` | `REQ-TRK-067.5` | Specified |
| `TST-TRK-067.4` | Unit (DSP) | `HrvSignalQualityAnalyzer.kt` | `REQ-TRK-067.2` | Specified |
| `TST-TRK-067.5` | Unit (Device) | `HRVMetricsDevice.kt` | `REQ-TRK-067.6` | Specified |
| `TST-TRK-067.6` | Unit (VM/Engine) | `MorningHrvCheckViewModel.kt`, `HrvPostWorkoutRecomputer.kt` | `REQ-TRK-067.6` | Specified |
| `TST-TRK-067.7` | Regression | `devices.hrv.*`, `devices.*`, `ui.morninghrv.*` | `REQ-PRO-001` | Specified |
