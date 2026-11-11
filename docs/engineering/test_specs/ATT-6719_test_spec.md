# Stage 2 Requirement & Test Specification: ATT-6719 - Localize Morning HRV, Cold-Start HR Search, Pre-Save Readiness & Lifecycle Scoping

**Ticket**: [ATT-6719](https://rainerblind.atlassian.net/browse/ATT-6719)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PER-032` (`REQ-PER-032.1`, `REQ-PER-032.3`, `REQ-PER-032.4`, `REQ-PER-032.5`, `REQ-PER-032.6`)  
**Test Spec ID**: `TST-PER-040` (`TST-PER-040.1`..`TST-PER-040.7`)  
**Branch**: `feature/ATT-6719` | **Author**: AI Agent 2 (Reviewer / Specifier, `subagent_flash`) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-PER-032)

### 1.1 Problem Statement & User Scope
* In `ATT-3556` (commit `dc034c08`), `MorningHrvCheckScreen.kt` and `MorningHrvCheckViewModel.kt` retained hardcoded English strings and raw `Color.White` literals, violating 9-locale parity and design token centralization.
* Entering Morning HRV immediately after cold app launch only binds `BANALService` passively without calling `startSearchingForPairedDevices()`; paired sensors stay disconnected until Tracking is opened.
* Artifact warnings evaluate only post-hoc; loose/dry straps do not trigger live warnings during 60–120s countdowns.
* `completeMeasurement()` queries SQLite before `saveReading()`, evaluating readiness against stale records rather than previewing today's candidate reading in-memory.
* `ATrainingTrackerApp.kt:647` scopes `MorningHrvCheckViewModel` to `activity` instead of `NavBackStackEntry`, and `onCleared()` unbinds the shared singleton `BANALServiceRepository`.
* Ergonomics: Duration uses binary `FilterChip` instead of `SingleChoiceSegmentedButtonRow`; column lacks `navigationBarsPadding()`; display dims mid-countdown.

### 1.2 Functional & Architectural Requirements
1. **Cold-Start Sensor Search & Safe Lifecycle (`MorningHrvCheckViewModel.kt`, `ATrainingTrackerApp.kt`)**: In `init`, invoke `banalServiceRepository.startSearchingForPairedDevices()`. Scope ViewModel to `NavRoutes.MORNING_HRV`. In `onCleared()`, detach `rrBeatListener` and cancel timers without unbinding `BANALServiceRepository`.
2. **Live Causal Artifact Warning (`MorningHrvCheckViewModel.kt`)**: In `handleRrBeat`, buffer incoming beats and run `RrArtifactFilter.filterLiveCausal` during `STABILIZING` and `MEASURING`; surface live artifact rate and warning when $> 5.0\%$.
3. **In-Memory Pre-Save Readiness Preview (`MorningHrvCheckViewModel.kt`)**: In `completeMeasurement()`, compute preview readiness via `HrvReadinessCalculator.calculateReadiness(dateIso, posture, existingEntries + entry)` before user taps save.
4. **UI & Screen Awake Ergonomics (`MorningHrvCheckScreen.kt`)**: Replace FilterChips with `SingleChoiceSegmentedButtonRow(height = 28.dp)`; add `Modifier.navigationBarsPadding()`; keep screen awake via `keepScreenOn` during active countdowns.
5. **Localization & Theme Centralization (`strings_hrv.xml`, `MorningHrvCheckScreen.kt`)**: Localize all 7 UI strings across 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with locale-aware format specifiers; replace `Color.White` with `TTColor.ReadinessOnStatus`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC 1 (Cold-Start HR Search & Lifecycle)**: *Given* cold app launch entering Morning HRV, *When* screen initializes, *Then* paired HR strap connects via `startSearchingForPairedDevices()`, and exiting screen detaches listener without unbinding tracking service.
* **AC 2 (Live Artifact Warning)**: *Given* poor electrode contact during countdown yielding $> 5.0\%$ artifacts, *When* beats arrive, *Then* amber warning is displayed live prior to completion.
* **AC 3 (Pre-Save Readiness Preview)**: *Given* measurement concluding, *When* summary screen renders before saving, *Then* today's readiness status is evaluated in-memory and previewed immediately.
* **AC 4 (Display Awakeness & Ergonomics)**: *Given* active stabilization or measurement countdown, *When* resting, *Then* display remains awake, duration uses segmented button row, and column has navigation bar padding.
* **AC 5 (Localization & Token Parity)**: *Given* any of the 9 supported locales, *When* viewing Morning HRV, *Then* zero raw English strings or `Color.White` literals are rendered.

### 1.4 System Invariants
* Zero workout rows created in `WorkoutSummaries.db`.
* Post-hoc centered filter (`RrArtifactFilter.filterBidirectional`) remains single source of truth for persisted metrics.
* Strict posture baseline isolation in SWC math; 100% clean-room test pass rate.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: `REQ-PER-032` (`REQ-PER-032.1`, `REQ-PER-032.3`, `REQ-PER-032.4`, `REQ-PER-032.5`, `REQ-PER-032.6`). Targets `MorningHrvCheckScreen.kt`, `MorningHrvCheckViewModel.kt`, `ATrainingTrackerApp.kt`, and `strings_hrv.xml`.
2. *Historical Origin & Commit Trace*: Introduced in `ATT-3556` (commit `dc034c08`); refined in `ATT-6719` under Epic `ATT-3539` (*Heart Rate Variability*).
3. *Root Reason for Existing Formulation*: Passive binding avoided duplicate searches; live filtering was deferred; ViewModel was activity-scoped out of convenience during prototype navigation.
4. *Preservation of Core Invariants*: Post-hoc centered filter accuracy, zero workout pollution, posture isolation in SWC baselines, and 100% 9-language translation parity are strictly preserved.

---

## 3. Test Specification (TST-PER-040: Guided Morning Resting HRV Suite)

### Test Case 1: Cold-Start Sensor Search & Lifecycle Scoping (`TST-PER-040.3`)
* **Scope**: `MorningHrvCheckViewModelTest.kt`. Verify `startSearchingForPairedDevices()` called on init; verify `onCleared()` detaches listener without calling `unbindFromBANALService()`.

### Test Case 2: Live Causal Artifact Warning (`TST-PER-040.4`)
* **Scope**: `MorningHrvCheckViewModelTest.kt`. Verify `handleRrBeat` evaluates `filterLiveCausal` live and triggers `highArtifactWarning` when artifact rate exceeds 5.0%.

### Test Case 3: Pre-Save In-Memory Readiness Preview (`TST-PER-040.5`)
* **Scope**: `MorningHrvCheckViewModelTest.kt`. Verify completion state previews `ReadinessStatus` calculated in-memory with candidate entry prior to `saveReading()`.

### Test Case 4: UI Ergonomics & Keep Screen On (`TST-PER-040.1`)
* **Scope**: `MorningHrvCheckScreenTest.kt` / Compose UI. Verify `SingleChoiceSegmentedButtonRow`, navigation bar padding, and `keepScreenOn` during active countdowns.

### Test Case 5: 9-Language Localization & Color Audit (`TST-PER-040.6`)
* **Scope**: `TranslationParityTest.kt`, `ThemeColorCentralizationAuditTest.kt`. Verify all 9 locales match format specifiers and zero `Color.White` literals exist on status labels.

### Test Case 6: Tier 1 Targeted Regression Suite (`TST-PER-040.7`)
* **Scope**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.hrv.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`.

---

## 4. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-PER-040.3]` | Unit / Lifecycle | `MorningHrvCheckViewModelTest`: Cold-start search & safe unbind | `REQ-PER-032.3` | Specified |
| `[TST-PER-040.4]` | Unit | `MorningHrvCheckViewModelTest`: Live causal artifact warning | `REQ-PER-032.4` | Specified |
| `[TST-PER-040.5]` | Unit | `MorningHrvCheckViewModelTest`: In-memory pre-save readiness preview | `REQ-PER-032.5` | Specified |
| `[TST-PER-040.1]` | Compose UI | `MorningHrvCheckScreenTest`: Segmented buttons, padding, screen awake | `REQ-PER-032.1` | Specified |
| `[TST-PER-040.6]` | Localization / Audit | `TranslationParityTest`, `ThemeColorCentralizationAuditTest`: 9 locales & TTColor | `REQ-PER-032.6`, `REQ-UI-106` | Specified |
| `[TST-PER-040.7]` | Regression | Tier 1 test execution across modified packages | `REQ-PRO-001` | Specified |
