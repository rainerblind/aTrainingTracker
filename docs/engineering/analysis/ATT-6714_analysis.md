# Stage 1 Analysis: ATT-6714 - Split/Merge Artifact Beats, NeuroKit2 DFA Parity & Stale HRV State Handling

**Ticket**: [ATT-6714](https://rainerblind.atlassian.net/browse/ATT-6714)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6714` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
Sprint `2026-46.1` follow-up of ATT-3541 identified four defects and architectural divergences in RR artifact correction and HRV telemetry:
1. **1-for-1 Artifact Substitution Breaks Beat-Index Alignment**: `RrArtifactFilter.kt` substitutes missed beats (`~1600 ms` vs `800 ms` median) and RF gaps (`ARTIFACT_GAP`) 1-for-1 with a single median `med` interval, distorting cumulative beat indices and time axes. Extra short beats (~400 ms) are also kept 1-for-1 rather than being merged with adjacent beats to restore the true heartbeat stream.
2. **Missing Live Causal Filter Wiring**: `HRVMetricsDevice.kt:155` calls `filterBidirectionalBeats` instead of `filterLiveCausal`, leaving `filterLiveCausal` without callers in `app/src/main/` and evaluating newest beats without the causal 5-beat look-ahead buffer.
3. **DFA Non-Overlapping Window Divergence**: `DfaCalculator.kt` steps non-overlapping $2K$ segments (`overlap=False`), diverging from pinned `neurokit2 == 0.2.10` (`overlap=True`). `HrvGoldenOracleTest.kt` verifies only a single harmonic sinusoid rather than pinned physiological series across exercise regimes.
4. **Stale HRV Telemetry on Disconnect & Missing Reset Wiring**: When `mBeatQueue` empties after strap disconnection, `HRVMetricsDevice.calculateMetrics()` early-returns without emitting `null` (`"--"`), freezing stale values indefinitely (`Coding Standards §1.7`). Furthermore, `HRVMetricsDevice.resetSession()` is not wired to `RESET_ACCUMULATORS_INTENT`, and artifact suppression is hardcoded to 5.0%.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RrArtifactFilter.kt:108-138 & 163-194**: Gaps and missed beats are handled via single `FilteredRrBeat` insertions without expanding `gapBeats` into `gapBeats + 1` intervals or splitting missed intervals into $k = \max(2, \text{round}(rr / med))$ equal intervals. Short beats are not merged with adjacent paired beats.
* **HRVMetricsDevice.kt:155**: Calls `artifactFilter.filterBidirectionalBeats(allBeats)` instead of `artifactFilter.filterLiveCausal(allBeats)`.
* **Independent Peer Review & Verification (4-Augen-Prinzip)**:
  * *Ticket Hypothesis Audit on DFA Windowing*: The ticket description hypothesized "overlapping stride-1 windows (`overlap=True`)". Code audit of `neurokit2==0.2.10` (`fractal_dfa.py:313`) and empirical validation against `nk.fractal_dfa` prove that `overlap=True` uses a **50% overlap** (`step = window // 2`), NOT stride 1. Stride 1 produces $|\Delta\alpha_1| = 0.038$ (violating the $\pm 0.02$ oracle gate), whereas `step = window // 2` matches `nk.fractal_dfa` to float precision ($|\Delta\alpha_1| < 10^{-12}$). `DfaCalculator.kt` MUST implement `step = boxSize / 2`.
* **HRVMetricsDevice.kt:150**: Early-return when `mBeatQueue.isEmpty()` leaves `mHrvRmssdSensor`, `mHrvDfaA1Sensor`, and `mHrvArtifactRateSensor` frozen. Needs `newValue(null)` emission once active data ceases.
* **Device Lifecycle**: `HRVMetricsDevice` fails to override `MyDevice.onAccumulatorsReset()`, preventing `resetSession()` from triggering on `BANALService.RESET_ACCUMULATORS_INTENT`.

---

## 3. Scope Grounding & Non-Goals (ATT-1250)
* **In-Scope Goals**:
  * Implement missed beat splitting into $\max(2, \text{round}(rr / med))$ equal intervals, adjacent short beat merging, and `gapBeats` expansion in `RrArtifactFilter.kt`.
  * Return `FilterResult` from `filterLiveCausal` and wire into `HRVMetricsDevice.calculateMetrics()`.
  * Align `DfaCalculator.kt` with NeuroKit2 0.2.10 (`overlap=True`, `step = boxSize / 2`, `scale = 4..16`).
  * Add 4 pinned golden vectors in `HrvGoldenOracleTest.kt`: White noise ($\alpha_1 \approx 0.50$), Threshold ($\approx 0.75$), Pink noise ($\approx 1.00$), and Aerobic ($\approx 1.25\text{--}1.50$) within $\pm 0.02$.
  * Clear stale HRV sensors to `null` after window expiry; wire `onAccumulatorsReset()` to `resetSession()`.
  * Expose configurable artifact gate threshold (3–5%, default 5.0%) via `TuningPreferencesDataStore`.
* **Out-of-Scope Non-Goals**:
  * Modifying FIT file export/import codecs or database table schemas.
  * Adding Android emulator tests (Emulator is permanently retired; JVM unit/Robolectric tests only).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID**: `REQ-TRK-060` (`REQ-TRK-060.2`, `REQ-TRK-060.4`, `REQ-TRK-060.5`, `REQ-TRK-060.6`) in `docs/requirements/req_tracking_cockpit.md`.
* **Historical Origin**: Sprint `2026-46.1` (`ATT-3541`, commit `9b6a12df`).
* **Root Reason for Existing Formulation**: ATT-3541 established initial filter and metric pipelines. 1-for-1 replacement was an initial placeholder; `DfaCalculator` used non-overlapping segments from Kubios equations; stale value clearing on queue drain was overlooked.
* **Preservation of Core Invariants**: Rule 51 (constructor state parity with `resetSession()`) and Rule 52 (zero telemetry rows emitted before physical RR data arrives) remain strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution
1. **RrArtifactFilter**: Split missed beats into $\max(2, \text{round}(rr/med))$ equal sub-intervals. Merge adjacent short beats ($rr_1 + rr_2$). Expand RF gaps to $gapBeats + 1$ beats. Update `filterLiveCausal` to return `FilterResult`.
2. **HRVMetricsDevice**: Call `filterLiveCausal(allBeats)`. Emit `null` to sensors when queue drains after active reception. Override `onAccumulatorsReset()` to invoke `resetSession()`. Read configurable artifact gate threshold.
3. **DfaCalculator**: Step overlapping windows with `step = boxSize / 2`.
4. **Golden Parity**: Pin deterministic golden reference vectors matching NeuroKit2 0.2.10 within $\pm 0.02$.
5. **Preferences**: Expose `hrvArtifactGateThresholdPercent` (3–5%, default 5.0%) in `TuningPreferencesDataStore` and `PhysiologyModelsSection.kt`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Rule 51 & 52 adherence; zero Main thread stalls; 100% clean-room test pass rate under `/tmp/att_build_merge.lock`.
* **Risk Rating**: **MEDIUM**. Alters live/post-hoc RR interval math and DFA scaling loops; mitigated by comprehensive pinned golden test oracles and strict boundary clamping.
