# Stage 1 Analysis: Lipponen & Tarvainen RR Artifact Correction & Real-Time HRV Engine (ATT-3541)

**Ticket**: [ATT-3541](https://rainerblind.atlassian.net/browse/ATT-3541) | **Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*HRV & Autonomic Metrics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-3541`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
Real-time athletic autonomic guidance requires robust Beat-to-Beat HRV metrics: $rMSSD$ (parasympathetic autonomic recovery) and DFA $\alpha_1$ (fractal correlation / aerobic threshold pacing). Physical heart rate straps transmit ectopic beats, missed triggers, and RF dropouts that severely corrupt both metrics. Conventional cubic spline interpolation introduces downward bias into $rMSSD$, while uncorrected series break DFA scaling assumptions. The system requires a validated Lipponen & Tarvainen (2019) artifact filter (causal 5-beat look-ahead for live tracking, centered 91-beat for post-workout recomputation), a 60s $rMSSD$ and 120s DFA $\alpha_1$ calculation engine, and automated golden tests against pinned NeuroKit2 (`0.2.10`).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Call-Site Audit**: `ATT-3540` implemented `RrBeatEvent` and `RrBeatSource` across BLE/ANT+ drivers. No physiological HRV calculation or artifact correction currently exists in `com.atrainingtracker.banalservice`.
* **Lipponen & Tarvainen (2019) Quartile Subspace Algorithm**: Computes differences $dRR_i = RR_i - RR_{i-1}$, centered 11-beat median deviation $mRR_i = RR_i - \text{median}(RR_{i-5\dots i+5})$, and adaptive quartile thresholds $th_1 = c_1 \cdot QD(dRR)$ and $th_2 = c_2 \cdot QD(mRR)$ ($c_1 = c_2 = 5.2$):
  * **Mode A (Live Causal Streaming)**: 5-beat look-ahead buffer (~2–4s latency, fitting within 5s update cadence) + 90-beat trailing quartile window.
  * **Mode B (Post-Hoc Centered)**: Symmetric 91-beat centered window for `ATT-3543` post-workout recomputation and `ATT-3556` morning HRV.
* **Metric Routing & Bias Prevention**:
  * **$rMSSD$ (60s window)**: Excludes artifact-adjacent beat pairs $(i, i+1)$ if $i$ or $i+1$ is flagged, eliminating downward interpolation bias.
  * **DFA $\alpha_1$ (120s window, 5s cadence)**: Detrended Fluctuation Analysis ($n \in [4, 16]$ beats) evaluates interpolated/split/merged NN series. Reusable pure function `computeDfaAlpha1(nnIntervalsMs)` matches NeuroKit2 within $|\Delta\alpha_1| \le 0.02$ (pre-corrected) and $|\Delta\alpha_1| \le 0.03$ (end-to-end).
  * **Artifact Gate**: If window artifact rate $> 5.0\%$ (configurable $3.0\text{--}5.0\%$), suppresses DFA $\alpha_1$ emission (`null`).
* **Rule 51 & 52 Invariants**: Baseline state seeded in constructor; virtual sensors gated on physical RR sensor presence (`hasReceivedRrData`).

---

## 3. User Scope Grounding (ATT-1250) & Complexity Split Guardrail
* **In-Scope Goals**:
  * Activate `SensorType.HRV_RMSSD`, `SensorType.HRV_DFA_A1`, `SensorType.HRV_ARTIFACT_RATE` with 9-language strings in `res/values*/strings_hrv.xml` (Rule 9 `REQ-PRO-041.3`).
  * Implement `RrArtifactFilter.kt` (Mode A live streaming and Mode B bidirectional centered).
  * Implement `DfaCalculator.kt` and `HRVMetricsDevice.kt` consuming `RrBeatEvent` stream with 5s periodic evaluation.
  * Register `HRV_METRICS` in `DeviceType.java`, wire `HRVMetricsDevice` in `DeviceManager.java` and `BANALService.java`.
  * Implement automated golden unit tests verifying `rMSSD` ($|\Delta| \le 0.5\text{ ms}$) and `DFA a1` ($|\Delta| \le 0.02/0.03$) against pinned NeuroKit2 0.2.10 benchmarks.
  * Propose net-new living doc specifications `REQ-TRK-060` and `TST-TRK-060`.
* **Out-of-Scope Non-Goals**:
  * Cockpit UI metric widgets and threshold indicators (isolated in `ATT-3542`).
  * SQLite `WorkoutRrIntervals` persistence schema and DAO (isolated in `ATT-3543`).
  * Post-workout recomputation engine and scatter plots (`ATT-3543`, `ATT-3544`).
* **Complexity Rating**: **3 SP (Complex)**. Advanced physiological signal processing, fractal math, and golden-vector verification.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-TRK-060` and `TST-TRK-060` in `docs/requirements/req_tracking_cockpit.md` and `docs/tests/tst_tracking_cockpit.md`, extending `REQ-CON-027` (RR Acquisition) and `REQ-TRK-019` (Virtual Metric Devices). Targets `RrArtifactFilter.kt`, `HRVMetricsDevice.kt`, `SensorType.java`, `DeviceManager.java`.
* **Historical Origin & Commit Trace**: `ATT-3540` (`98df60c9`) added lossless RR streaming; `ATT-1450` / `ATT-4283` established stateful virtual device patterns (`CyclingPowerMetricsDevice.java`).
* **Root Reason for Existing Formulation**: Prior app versions lacked RR acquisition and high-frequency physiological signal processing.
* **Preservation of Core Invariants**:
  1. Sensor presence gating: zero phantom HRV rows when heart rate strap is not paired (Rule 52).
  2. Constructor state parity with `resetSession()` (Rule 51).
  3. Real-time background execution without Main thread blocking.

---

## 5. Architectural Strategy & High-Level Solution
* **`RrArtifactFilter.kt`**: Modular filter engine with `filterLiveCausal(buffer)` and `filterBidirectional(series)`.
* **`DfaCalculator.kt`**: Pure function calculating Detrended Fluctuation Analysis ($n = 4\dots 16$) via least-squares box detrending and log-log slope.
* **`HRVMetricsDevice.kt`**: Virtual device implementing `RrBeatListener`, buffered 60s/120s queues, 5s scheduled evaluator, gating sensor publication on active beat data.
* **Localization Partitioning**: Partition string definitions into `strings_hrv.xml` across all 9 locales per `REQ-PRO-041.3`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: 100% NeuroKit2 golden test parity; zero phantom database rows; zero Main thread stalls; clean-room test pass rate.
* **Risk Rating**: **MEDIUM** (Complex mathematical algorithms; mitigated by pinned Python golden test vectors and isolated pure mathematical unit tests).
