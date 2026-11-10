# Stage 3: Implementation Plan - ATT-3541: Lipponen RR Artifact Filter & Real-Time HRV Engine

**Ticket**: [ATT-3541](https://rainerblind.atlassian.net/browse/ATT-3541) | **Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*HRV & Autonomic Metrics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-3541`  
**Requirement Mapping**: `REQ-TRK-060` (`REQ-TRK-060.1`..`REQ-TRK-060.6`)  
**Test Mapping**: `TST-TRK-060` (`TST-TRK-060.1`..`TST-TRK-060.6`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Background
Real-time athletic autonomic monitoring requires validated HRV metrics ($rMSSD$, DFA $\alpha_1$). Ectopic beats, missed triggers, and RF dropouts distort these metrics. Standard cubic spline interpolation biases $rMSSD$ downward, while uncorrected noise breaks fractal DFA scaling. This plan details the architectural construction of `SensorType.HRV_RMSSD`, `HRV_DFA_A1`, and `HRV_ARTIFACT_RATE` (with domain-partitioned `strings_hrv.xml`), `RrArtifactFilter.kt` implementing Lipponen & Tarvainen (2019) Mode A (live causal) and Mode B (post-hoc centered), `DfaCalculator.kt` fractal scaling, and `HRVMetricsDevice.kt` stateful virtual device honoring Rules 51 & 52.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-TRK-060.1` (SensorTypes & 9-locale `strings_hrv.xml`), `REQ-TRK-060.2` (Lipponen & Tarvainen Filter), `REQ-TRK-060.3` (Bias-Free Rolling $rMSSD$), `REQ-TRK-060.4` (120s DFA $\alpha_1$ & 5% Artifact Gate), `REQ-TRK-060.5` (Device Lifecycle & Rules 51/52), `REQ-TRK-060.6` (NeuroKit2 0.2.10 Golden Parity).
* **Test Mapping**: `TST-TRK-060.1` (`SensorTypeHrvTest.kt`, `TranslationParityTest.kt`), `TST-TRK-060.2` (`RrArtifactFilterTest.kt`), `TST-TRK-060.3` & `TST-TRK-060.5` (`HRVMetricsDeviceTest.kt`), `TST-TRK-060.4` (`DfaCalculatorTest.kt`), `TST-TRK-060.6` (`HrvGoldenOracleTest.kt`).

---

## 3. System Invariants & Preserved Behavior
1. **Rule 51 Constructor State Seeding Parity**: Constructor seeds baseline capacity identically to `resetSession()`.
2. **Rule 52 Sensor-Presence Gating**: `MySensor.newValue(...)` is suppressed until valid `RrBeatEvent` telemetry arrives.
3. **Downward Bias Immunity**: $rMSSD$ excludes artifact-adjacent beat pairs without synthetic interpolation.
4. **Code Lock (Rule 4)**: Zero modifications to `app/src/main/` production code until Gate 3 is approved.
5. **UI Consistency**: Zero UI changes in this backend ticket (cockpit tiles deferred to `ATT-3542`).

---

## 4. Proposed Architectural Changes (SWE.2)
### Component 1: `SensorType.java` & Domain Partitioned `strings_hrv.xml`
* Declare `HRV_RMSSD` (ms, `DOUBLE`), `HRV_DFA_A1` (`DOUBLE`), `HRV_ARTIFACT_RATE` (`%`, `DOUBLE`) in `SensorCategory.HEART_RATE_AND_ENERGY`.
* Create `app/src/main/res/values*/strings_hrv.xml` across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) per `REQ-PRO-041.3`.

### Component 2: `RrArtifactFilter.kt` (`banalservice.devices.hrv`)
* Mode A (`filterLiveCausal`): 5-beat look-ahead buffer + 90-beat trailing quartile window ($c_1 = c_2 = 5.2$).
* Mode B (`filterBidirectional`): Symmetric 91-beat centered window for `ATT-3543` post-workout recomputation.
* Classifies ectopic, missed, extra beats, and RF gaps, generating corrected NN series.

### Component 3: `DfaCalculator.kt` (`banalservice.devices.hrv`)
* Pure function `computeDfaAlpha1(nnIntervalsMs: DoubleArray): Double` for box scales $n \in [4, 16]$.
* Fluctuation analysis $F(n)$ via least-squares linear detrending; $\alpha_1$ as ordinary least-squares slope of $\log_{10} F(n)$ vs $\log_{10} n$.

### Component 4: `HRVMetricsDevice.kt` & Integration (`banalservice.devices`)
* Virtual device extending `MyDevice`, implementing `RrBeatListener`.
* 60s sliding window for $rMSSD$ (excluding artifact pairs); 120s window for DFA $\alpha_1$ evaluated every 5s on background scheduler.
* Suppresses DFA $\alpha_1$ emission (`null`) when artifact rate exceeds 5.0%.
* Register `DeviceType.HRV_METRICS`, instantiate in `DeviceManager.java`, wire to active `RrBeatSource`s.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (SensorTypes & Strings)**: Update `SensorType.java` and create partitioned `strings_hrv.xml` (9 locales); verify `TranslationParityTest`.
2. **Step 2 (Lipponen Filter Engine)**: Implement `RrArtifactFilter.kt`; verify with `RrArtifactFilterTest.kt`.
3. **Step 3 (DFA $\alpha_1$ Calculator)**: Implement `DfaCalculator.kt`; verify with `DfaCalculatorTest.kt`.
4. **Step 4 (Virtual Device & Wiring)**: Implement `HRVMetricsDevice.kt`, wire in `DeviceManager.java` / `BANALService.java`; verify `HRVMetricsDeviceTest.kt`.
5. **Step 5 (NeuroKit2 Golden Oracle)**: Implement `HrvGoldenOracleTest.kt` validating $|\Delta rMSSD| \le 0.5\text{ ms}$ and $|\Delta\alpha_1| \le 0.02 / 0.03$.

---

## 6. Verification & Rollback Plan
* **Targeted Verification**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.hrv.*" --tests "com.atrainingtracker.banalservice.devices.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"
  ```
* **Rollback Plan**: Revert cleanly via `git checkout feature/ATT-3541 -- .`.
