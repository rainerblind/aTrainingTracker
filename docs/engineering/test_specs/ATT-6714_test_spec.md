# Stage 2: Requirement & Test Specification - ATT-6714: Split/Merge Artifact Beats, NeuroKit2 DFA Parity & Stale HRV State Handling

**Ticket**: [ATT-6714](https://rainerblind.atlassian.net/browse/ATT-6714)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-TRK-060` (`REQ-TRK-060.2`, `REQ-TRK-060.4`, `REQ-TRK-060.5`, `REQ-TRK-060.6`)  
**Test Spec ID**: `TST-TRK-060` (`TST-TRK-060.2`, `TST-TRK-060.4`, `TST-TRK-060.5`, `TST-TRK-060.6`)  
**Branch**: `feature/ATT-6714` | **Author**: AI Agent 1 (Specifier, `subagent_flash`) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-TRK-060)

### 1.1 Problem Statement & Rationale
Sprint `2026-46.1` follow-up identified four defects in RR artifact processing and HRV telemetry: 1-for-1 substitution of missed beats and RF gaps distorts cumulative beat-index axes; extra short beats are not merged; `HRVMetricsDevice.kt` bypasses `filterLiveCausal()`; `DfaCalculator.kt` uses non-overlapping windows rather than 50% overlapping windows matching pinned `neurokit2 == 0.2.10`; `HRVMetricsDevice` freezes stale telemetry upon strap disconnection and fails to reset on `RESET_ACCUMULATORS_INTENT`; and the artifact gate threshold is hardcoded.

### 1.2 Functional & Architectural Requirements
1. **Missed Beat Split, Short Beat Merge & Gap Expansion (`REQ-TRK-060.2`)**:
   - Missed beats ($rr > med + th_2$ or $dRR > th_1 \land mRR > th_2$) SHALL be split into $k = \max(2, \text{round}(rr / med))$ equal intervals of duration $rr / k$.
   - Extra short beats ($rr < med - th_2$ or $dRR < -th_1 \land mRR < -th_2$) SHALL be merged with adjacent short intervals ($rr_1 + rr_2$).
   - RF Gaps (`ARTIFACT_GAP`) SHALL expand $gapBeats$ into $gapBeats + 1$ equal median intervals.
   - `filterLiveCausal()` SHALL return `FilterResult` (with `beats`, `artifactCount`, `totalCount`, and `artifactRatePercent`).
2. **DFA Overlap Alignment & Configurable Artifact Gate (`REQ-TRK-060.4`)**:
   - `DfaCalculator` SHALL evaluate DFA $\alpha_1$ across scales $n \in [4, 16]$ using 50% overlapping windows (`step = boxSize / 2`, matching NeuroKit2 0.2.10 `overlap=True`).
   - The artifact suppression threshold SHALL be configurable between 3.0% and 5.0% (default 5.0%) via `TuningPreferencesDataStore`. If artifact rate exceeds this threshold, `HRV_DFA_A1` SHALL emit `null`.
3. **Causal Device Wiring, Stale Metric Clearance & Reset Lifecycle (`REQ-TRK-060.5`)**:
   - `HRVMetricsDevice.calculateMetrics()` SHALL pass rolling beats to `artifactFilter.filterLiveCausal(allBeats)`.
   - When `mBeatQueue` drains after disconnect or window expiry, `calculateMetrics()` SHALL emit `null` to `mHrvRmssdSensor`, `mHrvDfaA1Sensor`, and `mHrvArtifactRateSensor`.
   - `HRVMetricsDevice` SHALL override `onAccumulatorsReset()` to invoke `resetSession()`.
4. **NeuroKit2 0.2.10 Golden Oracle Parity (`REQ-TRK-060.6`)**:
   - Unit tests SHALL verify DFA $\alpha_1$ parity against pinned NeuroKit2 0.2.10 reference vectors within $|\Delta\alpha_1| \le 0.02$ across White noise ($\approx 0.50$), Threshold ($\approx 0.75$), Pink noise ($\approx 1.00$), and Aerobic ($\approx 1.25\text{--}1.50$) series.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC 1 (Split/Merge/Gap & Causal Mode)**: *Given* missed/extra/gap beats, *When* filtered by `RrArtifactFilter` (causal or centered), *Then* missed beats split into $k = \max(2, \text{round}(rr/med))$ equal intervals, extra short beats merge, RF gaps expand by $gapBeats + 1$, and `HRVMetricsDevice` calls `filterLiveCausal()`.
* **AC 2 (NeuroKit2 50% Overlap Parity)**: *Given* deterministic golden vectors (white, threshold, pink, aerobic), *When* evaluated by `DfaCalculator`, *Then* $|\Delta\alpha_1| \le 0.02$ against pinned NeuroKit2 0.2.10.
* **AC 3 (Stale Clearance, Accumulator Reset & Configurable Gate)**: *Given* a disconnected strap, *When* `mBeatQueue` drains, *Then* HRV sensors clear to `null` (`"--"`); *When* accumulator resets, *Then* session state clears; *When* artifact rate exceeds configured gate (3–5%), *Then* `HRV_DFA_A1` emits `null`.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-TRK-060` (`REQ-TRK-060.2`, `REQ-TRK-060.4`, `REQ-TRK-060.5`, `REQ-TRK-060.6`), targeting `RrArtifactFilter.kt`, `HRVMetricsDevice.kt`, `DfaCalculator.kt`, `TuningPreferencesDataStore.kt`, `PhysiologyModelsSection.kt`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-3541` (commit `9b6a12df`), refined in Sprint `2026-46.2` (`ATT-6714`).
* **Root Reason for Existing Formulation**: 1-for-1 substitution distorted cumulative beat indices; `DfaCalculator` used non-overlapping segments from Kubios literature; `HRVMetricsDevice` omitted causal filter call and froze stale values on queue drain.
* **Preservation of Core Invariants**: Rules 51 & 52 strictly preserved; NeuroKit2 parity validated to $\pm 0.02$; zero emulator dependency; 100% clean-room test pass rate.

---

## 3. Test Specification (TST-TRK-060)

### Test Case 1: RrArtifactFilter Split/Merge & Gap Expansion (`TST-TRK-060.2`)
* **Scope**: `RrArtifactFilterTest.kt`. Verify missed beat split into $k = \max(2, \text{round}(rr/med))$ equal intervals, adjacent short beat merge ($rr_1 + rr_2$), RF gap expansion ($gapBeats + 1$), and `filterLiveCausal()` returning `FilterResult`.

### Test Case 2: DfaCalculator 50% Overlap & Pinned Golden Oracle Parity (`TST-TRK-060.4`, `TST-TRK-060.6`)
* **Scope**: `DfaCalculatorTest.kt` / `HrvGoldenOracleTest.kt`. Verify 50% overlap window stepping (`step = boxSize / 2`), and assert $|\Delta\alpha_1| \le 0.02$ against pinned NeuroKit2 0.2.10 across white noise ($\approx 0.50$), threshold ($\approx 0.75$), pink noise ($\approx 1.00$), and aerobic ($\approx 1.25\text{--}1.50$) series.

### Test Case 3: Stale Metric Clearing & Accumulators Reset Lifecycle (`TST-TRK-060.5`)
* **Scope**: `HRVMetricsDeviceTest.kt`. Verify that `calculateMetrics()` emits `null` to `HRV_RMSSD`, `HRV_DFA_A1`, and `HRV_ARTIFACT_RATE` when `mBeatQueue` drains, and verify `onAccumulatorsReset()` invokes `resetSession()`.

### Test Case 4: Configurable Artifact Gate Threshold & Localization Parity (`TST-TRK-060.4`, `TST-TRK-060.1`)
* **Scope**: `TuningPreferencesDataStoreTest.kt` / `TranslationParityTest.kt`. Verify artifact gate threshold preference (3.0%..5.0%, default 5.0%), dynamic gate suppression in `HRVMetricsDevice`, and 100% 9-language translation parity in `strings_hrv.xml`.

### Test Case 5: Tier 1 Targeted Regression Suite (`TST-TRK-060.7`)
* **Scope**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.hrv.*" --tests "com.atrainingtracker.trainingtracker.settings.*" --tests "com.atrainingtracker.trainingtracker.localization.*"`.

---

## 4. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-TRK-060.2]` | Unit | Beat split/merge/gap math and `filterLiveCausal()` result | `REQ-TRK-060.2` | Specified |
| `[TST-TRK-060.4]` | Unit | DFA 50% overlap & configurable artifact gate (3–5%) | `REQ-TRK-060.4` | Specified |
| `[TST-TRK-060.5]` | Unit / Lifecycle | Stale sensor clearance to `null` & `onAccumulatorsReset()` | `REQ-TRK-060.5` | Specified |
| `[TST-TRK-060.6]` | Golden Oracle | 4 pinned NeuroKit2 0.2.10 vectors ($|\Delta\alpha_1| \le 0.02$) | `REQ-TRK-060.6` | Specified |
| `[TST-TRK-060.1]` | Localization | 9-locale translation parity in `strings_hrv.xml` | `REQ-TRK-060.1` | Specified |
| `[TST-TRK-060.7]` | Regression | Tier 1 clean-room regression across modified packages | `REQ-PRO-001` | Specified |
