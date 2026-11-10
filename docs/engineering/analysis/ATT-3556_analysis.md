# Stage 1 Analysis: ATT-3556 - Implement Guided 60-120s Morning Resting HRV Measurement Screen

**Ticket**: [ATT-3556](https://rainerblind.atlassian.net/browse/ATT-3556)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-3556`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  
**Requirement Mapping**: `REQ-PER-032` (`docs/requirements/req_aftermath_periods.md`)  
**Test Mapping**: `TST-PER-040` (`docs/tests/tst_aftermath_periods.md`)  

---

## 1. Problem Statement & Motivation
* **Context**: Accurate autonomic readiness tracking requires waking resting HRV measurements recorded under standardized physiological conditions.
* **Gap**: Athletes currently have no standalone mechanism in *aTrainingTracker* to record morning resting HRV without starting a full GPS workout, cluttering history with empty workout records.
* **Objective**: Implement a dedicated, guided Jetpack Compose measurement screen (`MorningHrvCheckScreen`) and ViewModel (`MorningHrvCheckViewModel`) offering a standardized waking protocol (60s default, validated per Esco & Flatt 2014; 120s selectable), 15–30s stabilization phase, sticky posture selection, live artifact feedback, and post-hoc centered-filter persistence into `HrvReadinessRepository`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**:
  * Sensor streaming was historically coupled to `TrackerService` workout lifecycles.
  * Live rolling tiles (`SensorType.HRV_RMSSD`) evaluate a causal 60s window. Using live rolling snapshots for 120s tests or persistence introduces causal truncation bias.
* **Independent Peer Review & Verification (ATT-3534)**:
  * *Standardized Protocol & Posture Stickiness*: Autonomic tone is highly posture-dependent. Remembering the athlete's last-used posture (`SUPINE`, `SEATED`, `STANDING`) prevents accidental orthostatic contamination of longitudinal baselines.
  * *Post-Hoc Full-Window Calculation Invariant*: During measurement, raw `RrBeatEvent` packets are buffered in memory. Upon completion, metrics (`rmssd_ms`, `ln_rmssd`, `sdnn_ms`, `resting_hr_bpm`, `artifact_rate_pct`) MUST be computed post hoc over the entire window using `RrArtifactFilter.filterBidirectional` (Lipponen & Tarvainen 2019 centered filter) with artifact-adjacent pairs excluded from `rMSSD`. Live metrics provide real-time visual feedback only.
  * *Artifact Quality Gating*: High artifact rates (> 5.0%) or poor strap contact trigger an amber warning badge to instruct the athlete to moisten strap electrodes.
  * *Zero Workout Generation*: The session connects to sensors via `BANALServiceRepository` without launching `TrackerService` or modifying `WorkoutSummaries`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope**:
  * `MorningHrvCheckScreen.kt` & `MorningHrvCheckViewModel.kt` with stabilization (15–30s), measurement (60s / 120s), protocol checklist, and arrhythmia disclaimer.
  * Live feedback: HR, beat stability pulse, artifact percentage badge, and sticky posture selector.
  * Post-hoc full-window computation via `RrArtifactFilter.filterBidirectional` and persistence to `HrvReadinessRepository`.
  * Post-measurement summary displaying `ReadinessStatus` card compared against posture 7d/60d SWC baseline.
  * 9-language localization in `app/src/main/res/values*/strings_hrv.xml`.
  * Unit tests for measurement state machine, post-hoc calculation, and persistence.
* **Out-of-Scope**: Longitudinal trend history charts (`ATT-3557`); live cockpit DFA $\alpha_1$ pacing (`ATT-3542`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022, Rule 68)
* **Status**: Net-new requirement (`REQ-PER-032`) and test specification (`TST-PER-040`).
* **Chesterton's Fence & Side-Effects (Rule 68)**: Leverages existing `BANALServiceRepository`, `RrBeatSource`, `RrArtifactFilter`, and `HrvReadinessRepository` without altering `WorkoutSummaries` schema or existing workout recording paths.

---

## 5. Architectural Strategy & High-Level Solution
* **Sensor Ingestion Bridge**: Expose `RrBeatListener` subscription in `BANALServiceRepository` bound to `BANALService` without `TrackerService`.
* **State Machine**: `MorningHrvCheckViewModel` orchestrates states: `READY` $\rightarrow$ `STABILIZING` $\rightarrow$ `MEASURING` $\rightarrow$ `COMPLETED` / `SAVED`.
* **Persistence & Feedback**: Saves entry via `HrvReadinessRepository.insertEntry` and evaluates `HrvReadinessCalculator.calculateReadiness`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero workout rows: Morning checks never touch `WorkoutSummaries` or start `TrackerService`.
  2. Post-hoc centered calculation: Persisted readiness metrics strictly computed post hoc via `filterBidirectional`.
  3. Posture consistency: Sticky posture preference; baselines strictly partitioned per posture.
* **Risk Rating**: **LOW** — Self-contained Compose UI workflow leveraging verified database and filtering components.
