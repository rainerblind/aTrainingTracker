# Stage 2: Requirement & Test Specification - ATT-7745: Auto-name workout, trigger Warmup/Cooldown lap splits, and stop recording on Guided HRV Test completion

**Ticket**: [ATT-7745](https://rainerblind.atlassian.net/browse/ATT-7745)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*HRV: Real-Time Autonomic Metrics, Aerobic Threshold Guidance & Readiness Analytics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.6` | **Branch**: `feature/ATT-7745`  
**Requirement Mapping**: `REQ-TRK-072.8` (*Lifecycle Automation: Auto-Naming, Lap Splits & Automated Stop*)  
**Test Spec ID**: `TST-TRK-072.10`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-14  

---

## 1. Requirement Specification (REQ-TRK-072.8)

### 1.1 Problem Statement & Rationale
Completing a Guided DFA α1 Threshold Test currently leaves tracking active indefinitely, generates zero lap splits between Warmup, Active Testing, and Cooldown phases, and fails to persist a descriptive localized workout name (leaving it as a raw timestamp or letting route cluster / location auto-naming in `TrackerService.finalizeLiveSession()` overwrite it). The system SHALL automate phase lap splits, persist localized protocol titles, guard against cluster auto-naming overwrites, and automatically stop tracking upon test completion.

### 1.2 Attributes
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit

### 1.3 Functional & Architectural Requirements
1. **Phase Lap Splits (`REQ-TRK-072.8a`)**: In `HrvRampTestViewModel.processTick()`, upon `WARMUP -> TESTING` countdown reaching 0, the system SHALL broadcast `TrainingApplication.REQUEST_NEW_LAP`. Upon transition into `COOLDOWN` (`transitionToCooldown()`), the system SHALL broadcast `TrainingApplication.REQUEST_NEW_LAP`. No intermediate laps are triggered between individual steps during `GUIDED_STEP` (Option A: 3 clean laps: Warmup, Active Test, Cooldown).
2. **Protocol Auto-Naming & Overwrite Guard (`REQ-TRK-072.8b`)**: In `tagActiveWorkoutWithProtocol()`, the system SHALL persist localized `WorkoutSummaries.WORKOUT_NAME` (`hrv_workout_name_step` / `hrv_workout_name_ramp`). In `TrackerService.finalizeLiveSession()`, if `method` is `"GUIDED_STEP"` or `"GUIDED_RAMP"` (or if workout name is already set to a guided test title), the system SHALL preserve the protocol title and suppress cluster and known-location auto-naming overwrites.
3. **Automated Stop on Test Completion (`REQ-TRK-072.8c`)**: When cooldown expires (`COOLDOWN -> FINISHED` in `processTick()`) or `finishTest()` is invoked while tracking is active (`TrainingApplication.isTracking() == true`), `HrvRampTestViewModel` SHALL broadcast `TrainingApplication.REQUEST_STOP_TRACKING`.
4. **9-Language Localization Parity (`REQ-TRK-072.8d`)**: `hrv_workout_name_step` and `hrv_workout_name_ramp` SHALL exist across all 9 supported locales in `strings_hrv.xml`.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Warmup Lap Split)**: *Given* a test in `WARMUP`, *When* warmup countdown reaches 0, *Then* `REQUEST_NEW_LAP` is broadcast to close Warmup and start Active Test lap.
* **Criterion 2 (Cooldown Lap Split)**: *Given* a test in `TESTING` or `EARLY_COMPLETION`, *When* transitioning to `COOLDOWN`, *Then* `REQUEST_NEW_LAP` is broadcast to close Active Test and start Cooldown lap.
* **Criterion 3 (Automated Stop)**: *Given* an active guided test recording, *When* cooldown countdown reaches 0 or `finishTest()` is called, *Then* `REQUEST_STOP_TRACKING` is broadcast to stop recording.
* **Criterion 4 (Auto-Naming Preservation)**: *Given* a workout tagged with `method == "GUIDED_STEP"` or `"GUIDED_RAMP"`, *When* `finalizeLiveSession()` executes, *Then* `WORKOUT_NAME` retains the localized protocol title and is not overwritten by cluster or location auto-naming.

### 1.5 System Invariants
Zero regressions in manual lap or stop controls; free workout cluster and location auto-naming preserved; 100% 9-language localization parity.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-TRK-072` (*Guided DFA a1 Step & Ramp Test Protocol Screen*)
* **Historical Origin & Commit Trace**: Added in `ATT-4969` (commit `424580a2`), updated in `ATT-7317` (`fce6777b`), refined in `ATT-7529`, extended in `ATT-7745`.
* **Root Reason for Existing Formulation**: Initial implementation focused on protocol state machine, pre-flight sensor checks, and UI pacing guidance. Workout lifecycle synchronization (laps and stop) was left manual.
* **Preservation of Core Invariants**: Manual tracking stop remains fully functional; pause/resume lap neutrality (`ATT-896`) is untouched; free workout auto-naming and clustering logic are completely preserved.

---

## 2. Test Specification (TST-TRK-072.10)

### 2.1 Unit & Integration Test Suite (`HrvRampTestViewModelTest.kt`, `TrackerServiceAutoNamingTest.kt`)
* **TST-TRK-072.10a (Lap Broadcasts)**: Assert `REQUEST_NEW_LAP` broadcast on `WARMUP -> TESTING` and on `transitionToCooldown()` (`HrvRampTestViewModelTest.kt`).
* **TST-TRK-072.10b (Stop Broadcast)**: Assert `REQUEST_STOP_TRACKING` broadcast when `COOLDOWN` timer reaches 0 and on `finishTest()` (`HrvRampTestViewModelTest.kt`).
* **TST-TRK-072.10c (Auto-Naming Tagging)**: Assert `tagActiveWorkoutWithProtocol()` writes `WORKOUT_NAME` with localized string (`HrvRampTestViewModelTest.kt`).
* **TST-TRK-072.10d (TrackerService Overwrite Guard)**: Assert `finalizeLiveSession()` skips cluster/location naming when `method == "GUIDED_STEP"` or `"GUIDED_RAMP"` (`TrackerServiceAutoNamingTest.kt`).
* **TST-TRK-072.10e (9-Language Parity)**: Assert `hrv_workout_name_step` and `hrv_workout_name_ramp` exist across all 9 locales in `strings_hrv.xml` (`TranslationParityTest.kt`).
* **TST-TRK-072.10f (Targeted Regression)**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.hrv.*" --tests "com.atrainingtracker.trainingtracker.tracker.*" --tests "com.atrainingtracker.trainingtracker.localization.*"`.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-072.10a` | Unit | `HrvRampTestViewModel.processTick` & `transitionToCooldown` | `REQ-TRK-072.8a` | Specified |
| `TST-TRK-072.10b` | Unit | `HrvRampTestViewModel.processTick` & `finishTest` | `REQ-TRK-072.8c` | Specified |
| `TST-TRK-072.10c` | Unit | `HrvRampTestViewModel.tagActiveWorkoutWithProtocol` | `REQ-TRK-072.8b` | Specified |
| `TST-TRK-072.10d` | Unit | `TrackerService.finalizeLiveSession` | `REQ-TRK-072.8b` | Specified |
| `TST-TRK-072.10e` | Localization | `TranslationParityTest` (9 locales) | `REQ-TRK-072.8d` | Specified |
| `TST-TRK-072.10f` | Regression | Tier 1 HRV & Tracker Unit Tests | `REQ-PRO-001` | Specified |
