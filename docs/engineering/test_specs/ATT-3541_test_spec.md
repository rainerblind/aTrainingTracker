# Stage 2: Requirement & Test Specification - ATT-3541: Lipponen Artifact Correction, Real-Time HRV Engine & Golden Oracles

**Ticket**: [ATT-3541](https://atrainingtracker.atlassian.net/browse/ATT-3541) | **Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-3539](https://atrainingtracker.atlassian.net/browse/ATT-3539) (*HRV & Autonomic Metrics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-3541`  
**Requirement Mapping**: `REQ-TRK-060` (`REQ-TRK-060.1`..`REQ-TRK-060.6`) | **Test Spec ID**: `TST-TRK-060` (`TST-TRK-060.1`..`TST-TRK-060.6`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (`REQ-TRK-060`)

### 1.1 Problem Statement & Rationale
Real-time autonomic pacing and recovery guidance require Kubios-grade Beat-to-Beat HRV telemetry ($rMSSD$, DFA $\alpha_1$). Sensor artifacts (ectopic beats, missed triggers, RF dropouts) corrupt raw intervals. Downward interpolation bias distorts $rMSSD$ while artifact-adjacent noise breaks fractal DFA scaling. The system SHALL provide causal Mode A (live) and centered Mode B (post-hoc) Lipponen & Tarvainen (2019) artifact filters, artifact pair-exclusion for $rMSSD$, 120s DFA $\alpha_1$ scaling ($n \in [4, 16]$), and automated golden validation against pinned NeuroKit2 0.2.10.

### 1.2 Functional & Architectural Requirements
* **REQ-TRK-060.1** (*HRV SensorTypes & Domain Localization Partitioning*): The system SHALL activate `SensorType.HRV_RMSSD` (ms, `DOUBLE`), `SensorType.HRV_DFA_A1` (`DOUBLE`), and `SensorType.HRV_ARTIFACT_RATE` (`%`, `DOUBLE`) in category `HEART_RATE_AND_ENERGY`. String definitions SHALL be partitioned into `res/values*/strings_hrv.xml` across all 9 locales (`REQ-PRO-041.3`).
* **REQ-TRK-060.2** (*Lipponen & Tarvainen 2019 Artifact Filter*): `RrArtifactFilter` SHALL implement the quartile subspace algorithm ($c_1 = c_2 = 5.2$):
  * **Mode A (Live Causal Streaming)**: 5-beat look-ahead buffer + 90-beat trailing quartile window.
  * **Mode B (Post-Hoc Centered)**: 91-beat symmetric centered window for post-workout recomputation and morning checks.
* **REQ-TRK-060.3** (*Bias-Free Rolling rMSSD Engine*): `HRVMetricsDevice` SHALL compute $rMSSD$ over a 60s sliding window. Successive interval differences $(i, i+1)$ SHALL exclude artifact-adjacent pairs where beat $i$ or $i+1$ is flagged. Requires $\ge 10$ valid pairs.
* **REQ-TRK-060.4** (*120s Rolling DFA $\alpha_1$ Calculator & Artifact Gate*): Every 5 seconds, `DfaCalculator` SHALL evaluate DFA $\alpha_1$ across scale range $n \in [4, 16]$ on the corrected/interpolated 120s NN window. If window artifact rate $> 5.0\%$, the system SHALL suppress $\alpha_1$ emission (`null`).
* **REQ-TRK-060.5** (*Virtual Device Lifecycle & State Invariants*): `HRVMetricsDevice` SHALL implement `RrBeatListener`. Constructors SHALL seed identical baseline state as `resetSession()` (`Rule 51`). Telemetry publication to `MySensor` SHALL remain gated until physical RR packets are received (`Rule 52`).
* **REQ-TRK-060.6** (*NeuroKit2 0.2.10 Golden Oracle Parity*): Automated unit tests SHALL verify:
  * $rMSSD$ matches NeuroKit2 within $|\Delta rMSSD| \le 0.5\text{ ms}$.
  * DFA $\alpha_1$ matches NeuroKit2 within $|\Delta\alpha_1| \le 0.02$ on clean/pre-corrected windows and $|\Delta\alpha_1| \le 0.03$ end-to-end.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Lipponen Artifact Classification)**: *Given* an RR stream with ectopic, missed, or short beats, *When* filtered by `RrArtifactFilter`, *Then* artifacts are correctly classified and corrected series generated.
* **AC-2 (Bias-Free rMSSD)**: *Given* an RR stream containing artifact beats, *When* $rMSSD$ computes, *Then* artifact-adjacent differences are excluded, preventing downward interpolation bias.
* **AC-3 (DFA $\alpha_1$ Parity)**: *Given* synthetic and benchmark RR recordings, *When* evaluated by `DfaCalculator`, *Then* output matches NeuroKit2 within $|\Delta\alpha_1| \le 0.02$.
* **AC-4 (High Artifact Suppression Gate)**: *Given* a 120s window with $>5\%$ artifact beats, *When* evaluated, *Then* `HRV_DFA_A1` emits `null`.
* **AC-5 (Rule 51 & 52 Invariants)**: *Given* a session with no connected RR sensor, *When* tracking runs, *Then* zero phantom HRV sensor values are published.

---

## 2. Test Specification & Traceability Matrix (`TST-TRK-060`)

| Req ID | Test Case | Target Component | Verification Scope |
| :--- | :--- | :--- | :--- |
| `REQ-TRK-060.1` | `TST-TRK-060.1` | `SensorTypeHrvTest.kt`, `TranslationParityTest.kt` | `HRV_RMSSD`, `HRV_DFA_A1`, `HRV_ARTIFACT_RATE` properties & 9-locale parity |
| `REQ-TRK-060.2` | `TST-TRK-060.2` | `RrArtifactFilterTest.kt` | Mode A causal look-ahead and Mode B centered classification and correction |
| `REQ-TRK-060.3` | `TST-TRK-060.3` | `HRVMetricsDeviceTest.kt` | 60s $rMSSD$ rolling window, artifact pair exclusion, min sample guard |
| `REQ-TRK-060.4` | `TST-TRK-060.4` | `DfaCalculatorTest.kt` | 120s DFA $\alpha_1$ calculation, scale $n \in [4, 16]$, $>5\%$ artifact gate suppression |
| `REQ-TRK-060.5` | `TST-TRK-060.5` | `HRVMetricsDeviceTest.kt` | Rule 51 constructor state parity and Rule 52 physical sensor presence gating |
| `REQ-TRK-060.6` | `TST-TRK-060.6` | `HrvGoldenOracleTest.kt` | NeuroKit2 0.2.10 golden benchmarks: $|\Delta rMSSD| \le 0.5\text{ ms}$, $|\Delta\alpha_1| \le 0.02 / 0.03$ |
