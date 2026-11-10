# Stage 2: Requirement & Test Specification - ATT-3556: Morning HRV Check Screen

**Ticket**: [ATT-3556](https://rainerblind.atlassian.net/browse/ATT-3556)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-3556`  
**Requirement Mapping**: `REQ-PER-032` | **Test Spec ID**: `TST-PER-040`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-PER-032)

### 1.1 Problem Statement & Rationale
Athletes need a dedicated, standardized protocol to measure morning resting HRV without starting a full GPS workout. The system SHALL provide a guided Jetpack Compose screen (`MorningHrvCheckScreen`) and ViewModel (`MorningHrvCheckViewModel`) implementing a standardized waking protocol (60s standard per Esco & Flatt 2014, 120s extended), 15–30s stabilization phase, sticky posture memory, live artifact feedback, post-hoc centered filter computation, and persistence to `HrvReadinessRepository`.

### 1.2 Functional & Architectural Requirements
1. **Protocol Lifecycle & State Machine (`REQ-PER-032.1`)**: Orchestrate states: `READY` $\rightarrow$ `STABILIZING` (15–30s countdown) $\rightarrow$ `MEASURING` (60s/120s countdown) $\rightarrow$ `COMPLETED`. Allow cancellation/reset to `READY`.
2. **Posture Stickiness & Standardization (`REQ-PER-032.2`)**: Offer `SUPINE`, `SEATED`, and `STANDING` posture selection, persisting the last choice in `SP_HRV_LAST_POSTURE = "hrvLastPosture"` across sessions.
3. **Sensor Ingestion & Zero-Workout Invariant (`REQ-PER-032.3`)**: Stream raw RR intervals via `BANALServiceRepository` without launching `TrackerService` or creating rows in `WorkoutSummaries.db`.
4. **Post-Hoc Centered Filter Invariant (`REQ-PER-032.4`)**: Buffer raw RR intervals during measurement and apply `RrArtifactFilter.filterBidirectional` upon completion to compute `rmssdMs`, `lnRmssd`, `hrvScore100`, `sdnnMs`, `restingHrBpm`, and `artifactRatePct`. Live metrics provide real-time visual pacing only.
5. **Persistence, Quality Gating & SWC Feedback (`REQ-PER-032.5`)**: Persist entry to `HrvReadinessRepository`. Display an amber warning badge if `artifactRatePct > 5.0%`. Display `ReadinessStatus` compared against the posture 7d/60d SWC baseline.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* an athlete launching `MorningHrvCheckScreen`,
  * *When* the screen initializes,
  * *Then* it displays the sticky last-used posture (default `SUPINE`), 60s default duration, and waking instructions.
* *Given* a 60s measurement session completing with 75 RR intervals,
  * *When* the measurement phase ends,
  * *Then* `filterBidirectional` evaluates the full window post hoc, computing `rmssdMs`, `lnRmssd`, `sdnnMs`, and `artifactRatePct`.
* *Given* an artifact rate $> 5.0\%$,
  * *When* viewing the measurement summary,
  * *Then* an artifact warning badge is displayed with electrode moistening advice.
* *Given* user taps "Save Reading",
  * *When* saved,
  * *Then* entry persists into `HrvReadiness.db` with zero rows added to `WorkoutSummaries.db`.

---

## 2. Test Specification (TST-PER-040)

### Test Case 1: Lifecycle State Machine Unit Test (`TST-PER-040.1`)
* **Scope**: Unit Test (`MorningHrvCheckViewModelTest.kt`).
* **Assertions**: Asserts state transitions `READY` $\rightarrow$ `STABILIZING` $\rightarrow$ `MEASURING` $\rightarrow$ `COMPLETED`; asserts cancel/reset returns to `READY`.

### Test Case 2: Sticky Posture & Duration Configuration Test (`TST-PER-040.2`)
* **Scope**: Unit Test (`MorningHrvCheckViewModelTest.kt`).
* **Assertions**: Asserts posture changes persist to `SP_HRV_LAST_POSTURE`; asserts 60s and 120s durations configure timers correctly.

### Test Case 3: Sensor Ingestion & Zero-Workout Invariant (`TST-PER-040.3`)
* **Scope**: Unit Test (`MorningHrvCheckViewModelTest.kt`).
* **Assertions**: Asserts ViewModel subscribes to `RrBeatListener`; asserts zero `TrackerService` interactions and zero workout rows created.

### Test Case 4: Post-Hoc Full-Window Centered Filter Calculation Test (`TST-PER-040.4`)
* **Scope**: Unit Test (`MorningHrvCheckViewModelTest.kt`).
* **Assertions**: Asserts post-hoc metrics match `filterBidirectional` output over full buffered RR intervals.

### Test Case 5: Repository Persistence & SWC Feedback Test (`TST-PER-040.5`)
* **Scope**: Unit Test (`MorningHrvCheckViewModelTest.kt`).
* **Assertions**: Asserts saving persists to `HrvReadinessRepository`; asserts artifact warning trigger at $> 5.0\%$; asserts `ReadinessStatus` calculation.

### Test Case 6: Tier 1 Targeted Regression & Localization Parity (`TST-PER-040.6`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.database.hrv.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Assertions**: 100% clean-room pass across HRV, localization, and build contract tests.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-PER-040.1]` | Unit | Lifecycle state machine transitions & reset | `REQ-PER-032.1` | Planned |
| `[TST-PER-040.2]` | Unit | Sticky posture preference & duration config | `REQ-PER-032.2` | Planned |
| `[TST-PER-040.3]` | Unit | Zero-workout invariant & sensor subscription | `REQ-PER-032.3` | Planned |
| `[TST-PER-040.4]` | Unit | Post-hoc centered filter calculation | `REQ-PER-032.4` | Planned |
| `[TST-PER-040.5]` | Unit | Persistence, artifact warning, SWC feedback | `REQ-PER-032.5` | Planned |
| `[TST-PER-040.6]` | Regression | Tier 1 targeted regression & 9-locale parity | `REQ-LOC-001`, `REQ-PER-032.1`..`REQ-PER-032.5` | Planned |
