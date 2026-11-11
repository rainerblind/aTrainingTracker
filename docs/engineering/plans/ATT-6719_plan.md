# Stage 3: Implementation Plan - ATT-6719: Localize Morning HRV, Cold-Start HR Search, Pre-Save Readiness & Lifecycle Scoping

**Ticket**: [ATT-6719](https://rainerblind.atlassian.net/browse/ATT-6719)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PER-032` (`REQ-PER-032.1`, `REQ-PER-032.3`, `REQ-PER-032.4`, `REQ-PER-032.5`, `REQ-PER-032.6`)  
**Test Mapping**: `TST-PER-040` (`TST-PER-040.1`, `TST-PER-040.3`, `TST-PER-040.4`, `TST-PER-040.5`, `TST-PER-040.6`, `TST-PER-040.7`)  
**Branch**: `feature/ATT-6719` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Description & Background
In `MorningHrvCheckScreen.kt` and `MorningHrvCheckViewModel.kt`, hardcoded English strings and `Color.White` literals violate 9-locale parity and theme centralization. Entering the screen on cold start leaves paired HR straps idle without sensor search. Live artifact warnings do not fire during countdown. Readiness is queried before today's entry is saved (yielding stale pre-save previews). The ViewModel is scoped to `activity` and unbinds the shared `BANALServiceRepository` upon exit.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-PER-032.1` (Awake & Ergonomics), `REQ-PER-032.3` (Cold-Start Search & Safe Lifecycle), `REQ-PER-032.4` (Live Causal Artifacts), `REQ-PER-032.5` (Pre-Save In-Memory Preview), `REQ-PER-032.6` (Localization & Theme Tokens).
* **Test Mapping**: `TST-PER-040.1` (UI Ergonomics), `TST-PER-040.3` (Search & Safe Lifecycle), `TST-PER-040.4` (Live Causal Artifacts), `TST-PER-040.5` (Pre-Save Preview), `TST-PER-040.6` (Translation & Theme Audit), `TST-PER-040.7` (Regression).

---

## 3. System Invariants & Preserved Behavior
1. **Zero Workout Generation Invariant**: `TrackerService` and `WorkoutSummaries.db` remain untouched.
2. **Post-Hoc Centered Filter Invariant**: Bidirectional centered filtering (`filterBidirectionalBeats`) remains the sole authority for persisted metrics.
3. **Strict Posture Isolation**: SWC baselines compute strictly per posture.
4. **Terminal Human Decision Gate**: Advances to `Final Review (Human)`.

---

## 4. Proposed Architectural Changes
* **Strings & Color Tokens (`strings_hrv.xml`, `Color.kt`)**: Add 8 localized strings across all 9 locales (`morning_hrv_error_no_rr`, `morning_hrv_beat_count`, `morning_hrv_status_*`, `morning_hrv_summary_sdnn`, `morning_hrv_summary_artifact_rate`, `morning_hrv_btn_saved`). Define `TTColor.ReadinessOnStatus = Color(0xFFFFFFFF)`.
* **ViewModel Layer (`MorningHrvCheckViewModel.kt`)**: Call `startSearchingForPairedDevices()` in `init`. In `handleRrBeat`, run `artifactFilter.filterLiveCausal` and set `highArtifactWarning` when artifact rate $> 5.0\%$. In `completeMeasurement`, evaluate in-memory readiness against `existingEntries + entry`. In `onCleared`, remove listener without calling `unbindFromBANALService()`.
* **Presentation & Navigation Layer (`MorningHrvCheckScreen.kt`, `ATrainingTrackerApp.kt`)**: Scope ViewModel to `NavRoutes.MORNING_HRV`. In Compose, use `SingleChoiceSegmentedButtonRow(height = 28.dp)`, `Modifier.navigationBarsPadding()`, and `view.keepScreenOn` during countdown.
* **UI Consistency (Rule 23)**:
  * Reference screen: `PeriodBarGraph.kt` (SingleChoiceSegmentedButtonRow), `TrackingScreen.kt` (keepScreenOn).
  * Reused components: `SingleChoiceSegmentedButtonRow`, `SegmentedButton`, `Card`, `TopAppBar`.
  * Theme tokens: `TTColor.ReadinessOnStatus`, `TTColor.Readiness*`, `MaterialTheme.colorScheme.surface`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Localization & Semantic Tokens (`strings_hrv.xml`, `Color.kt`)
* Add localized keys across 9 directories (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
* Add `TTColor.ReadinessOnStatus` in `Color.kt`.

### Step 2: Cold-Start Search, Live Causal Artifacts & Pre-Save Preview (`MorningHrvCheckViewModel.kt`)
* In `init`, add `banalServiceRepository.startSearchingForPairedDevices()`.
* In `handleRrBeat`, buffer and evaluate `artifactFilter.filterLiveCausal(bufferedBeats.toList())` to trigger `highArtifactWarning` live.
* In `completeMeasurement`, evaluate `HrvReadinessCalculator.calculateReadiness(dateIso, posture, existingEntries.filter { it.dateIso != dateIso } + entry)` for preview.
* In `onCleared`, remove `rrBeatListener` and cancel timer; delete `unbindFromBANALService()`.

### Step 3: Navigation Scoping & Screen Ergonomics (`ATrainingTrackerApp.kt`, `MorningHrvCheckScreen.kt`)
* Scope ViewModel to `backStackEntry` in `ATrainingTrackerApp.kt`.
* Replace duration chips with `SingleChoiceSegmentedButtonRow(height = 28.dp)`.
* Apply `DisposableEffect` for `LocalView.current.keepScreenOn` during `STABILIZING` and `MEASURING`.
* Add `Modifier.navigationBarsPadding()` and replace hardcoded strings/colors with resource IDs and `TTColor.ReadinessOnStatus`.

### Step 4: Unit Testing & Verification Execution
* Add test cases in `MorningHrvCheckViewModelTest.kt` verifying cold-start search, safe unbind, live causal warning, and pre-save preview.
* Run targeted verification: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.hrv.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted unit, localization, and theme centralization tests.
* **Rollback**: Discard uncommitted branch changes via `git checkout sprint/2026-46.2`.
