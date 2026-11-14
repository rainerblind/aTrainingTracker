# Stage 2: Requirement & Test Specification - ATT-7740: Morning HRV Cutoff Hour Default to 08:00 and Upper Bound Extension

**Ticket**: [ATT-7740](https://rainerblind.atlassian.net/browse/ATT-7740)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.6`  
**Requirement Mapping**: `REQ-UI-525` / `REQ-UI-525.1`  
**Test Spec ID**: `TST-UI-525` / `TST-UI-525.1`  
**Branch**: `feat/ATT-7740` | **Author**: Pro Agent (`agent1` / `agent2`) | **Date**: 2026-11-14  

---

## 1. Requirement Specification (REQ-UI-525.1 Refinement)

### 1.1 Problem Statement & Rationale
The default morning check cutoff hour of 11:00 AM causes the start-screen CTA button to linger past morning hours, while the maximum bound of 15:00 prevents afternoon/evening readiness testing.

### 1.2 Attributes
- **Priority**: Medium | **Type**: Functional / Configuration | **Verification Method**: Unit Test

### 1.3 Functional & Architectural Requirements
1. `TuningPreferencesDefaults.DEFAULT_MORNING_CHECK_CUTOFF_HOUR` SHALL be set to `8` (08:00 AM).
2. `TuningPreferencesDefaults.MAX_MORNING_CHECK_CUTOFF_HOUR` SHALL be extended to `23` (11:00 PM), with `MIN_MORNING_CHECK_CUTOFF_HOUR` remaining `6` (06:00 AM).
3. In `PhysiologyModelsSection.kt`, the cutoff slider SHALL span `6.0f..23.0f` with `steps = 16` and display 2-digit zero-padded hour formatting (`%02d:00`).
4. `ControlTrackingViewModel` SHALL evaluate `timeProvider().hour < tuning.morningCheckCutoffHour` against the new default and bounds.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Default Cutoff Hour 8)**:
  * *Given* a fresh install or default configuration,
  * *When* `TuningConfig` is initialized,
  * *Then* `morningCheckCutoffHour` equals `8`.
* **Criterion 2 (Extended Range 6..23)**:
  * *Given* `PhysiologyModelsSection.kt` or `TuningPreferencesDataStore.kt`,
  * *When* an athlete selects any hour between 6 and 23,
  * *Then* the cutoff is persisted without clamping to 15, and values above 23 are clamped to 23.
* **Criterion 3 (Start Screen Visibility at 08:00 AM)**:
  * *Given* an athlete with paired HRM and default preferences,
  * *When* local time is 07:59, *Then* the CTA button is visible;
  * *When* local time is 08:00, *Then* the CTA button is hidden.

### 1.5 System Invariants
- Zero DB schema changes; zero modifications to morning check scoring.

---

## 2. Test Specification (TST-UI-525.1)

### Test Case 1: `defaultsConstants_morningCheckCutoffHourParity` (`TST-UI-525.1a`)
* **Scope**: Unit Test (`TuningConfigTest.kt`)
* **Assertions**:
  - `assertEquals(8, config.morningCheckCutoffHour)`
  - `assertEquals(8, TuningPreferencesDefaults.DEFAULT_MORNING_CHECK_CUTOFF_HOUR)`
  - `assertEquals(23, TuningPreferencesDefaults.MAX_MORNING_CHECK_CUTOFF_HOUR)`

### Test Case 2: `isMorningHrvCheckVisible_defaultCutoffHour8` (`TST-UI-525.1b`)
* **Scope**: Unit Test (`ControlTrackingViewModelTest.kt`)
* **Assertions**:
  - Assert button emits `true` at 07:30 AM with default config.
  - Assert button emits `false` at 08:00 AM with default config.

### Test Case 3: Tier 1 Targeted Test Suite (`TST-UI-525.1c`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.settings.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.*"`

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-525.1a]` | Unit | `TuningConfigTest.kt` | `REQ-UI-525.1` | Verified |
| `[TST-UI-525.1b]` | Unit | `ControlTrackingViewModelTest.kt` | `REQ-UI-525.1` | Verified |
| `[TST-UI-525.1c]` | Tier 1 Targeted | Full settings & control tracking suite | `REQ-UI-525` | Verified |
