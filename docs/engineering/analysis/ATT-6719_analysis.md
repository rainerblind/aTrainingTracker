# Stage 1 Analysis: ATT-6719 - Localize Morning HRV, Cold-Start HR Search, Pre-Save Readiness & Lifecycle Scoping

**Ticket**: [ATT-6719](https://rainerblind.atlassian.net/browse/ATT-6719)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6719` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
* In `ATT-3556` (commit `dc034c08`), `MorningHrvCheckScreen.kt` and `MorningHrvCheckViewModel.kt` retained hardcoded English strings (error messages, beat count, readiness status labels, metrics formatters, saved button text) and raw `Color.White` literals, violating 9-locale parity and theme token centralization.
* On cold app launch, entering `MorningHrvCheckScreen` straight away only calls `bindToBANALService()` without triggering `startSearchingForPairedDevices()`. Paired BLE/ANT+ HR sensors remain idle until the user opens Tracking.
* During measurement countdown, artifact warnings only evaluate post-hoc after completion; loose or dry chest straps are not flagged live during the 60-120s wait.
* `completeMeasurement()` queries `hrvReadinessRepository.calculateReadiness()` prior to `saveReading()`, calculating baseline status against the database *before* today's candidate reading is inserted, rendering an empty/stale preview.
* `ATrainingTrackerApp.kt:647` scopes `MorningHrvCheckViewModel` to `activity` rather than `NavBackStackEntry`, and `onCleared()` unbinds the shared `BANALServiceRepository`, disrupting live tracking if active.
* Ergonomics: Duration chips use binary `FilterChip` rather than `SingleChoiceSegmentedButtonRow`; column lacks `navigationBarsPadding()`; display dims mid-countdown.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Call Site & Code Audit**:
  * `MorningHrvCheckViewModel.kt:121-122`: Passive `bindToBANALService()` without `startSearchingForPairedDevices()`.
  * `MorningHrvCheckViewModel.kt:243-252`: `handleRrBeat` buffers raw beats without running live causal detection (`RrArtifactFilter.filterLiveCausal`).
  * `MorningHrvCheckViewModel.kt:324`: `calculateReadiness(dateIso, entry.posture)` queries SQLite which does not yet contain `entry`.
  * `MorningHrvCheckViewModel.kt:353`: Calls `banalServiceRepository.unbindFromBANALService()` in `onCleared()`.
  * `ATrainingTrackerApp.kt:647`: `viewModel(activity)` prevents ViewModel destruction on popBackStack.
  * `MorningHrvCheckScreen.kt:431, 475, 608-620, 664, 671, 761`: Hardcoded English strings and `Color.White`.
* **Independent Peer Review & Verification**:
  * *Cold-Start Search*: Calling `banalServiceRepository.startSearchingForPairedDevices()` activates BLE/ANT+ discovery immediately upon screen entry.
  * *Pre-Save Preview*: Evaluating `HrvReadinessCalculator.calculateReadiness(dateIso, posture, existingEntries + entry)` previews today's reading accurately without phantom DB side-effects.
  * *Lifecycle Safety*: Scoping to `NavBackStackEntry` and removing `unbindFromBANALService()` allows the ViewModel to clean up `rrBeatListener` without breaking shared service bindings.
  * *Screen Awakeness*: `LocalView.current.keepScreenOn` during `STABILIZING`/`MEASURING` prevents display timeout.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Localize all 7 hardcoded strings in `strings_hrv.xml` across all 9 supported locales; replace `Color.White` with `TTColor.ReadinessOnStatus`.
  2. Activate `startSearchingForPairedDevices()` in `MorningHrvCheckViewModel` upon screen entry.
  3. Run `RrArtifactFilter.filterLiveCausal` in `handleRrBeat` to surface live artifact warnings during countdown.
  4. Compute pre-save readiness preview against in-memory candidate `HrvReadinessEntry`.
  5. Scope `MorningHrvCheckViewModel` to `NavBackStackEntry` in `ATrainingTrackerApp.kt`; remove `unbindFromBANALService()`.
  6. Polish: `SingleChoiceSegmentedButtonRow`, `navigationBarsPadding()`, and `view.keepScreenOn` during measurement.
* **Out-of-Scope Non-Goals**:
  1. Modifying SQLite schema or table structures.
  2. Altering post-hoc `RrArtifactFilter.filterBidirectionalBeats` centered algorithm.
  3. Relocating Morning HRV to another tab.

---

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PER-032` (`REQ-PER-032.1`, `REQ-PER-032.3`, `REQ-PER-032.5`), targeting `MorningHrvCheckScreen.kt`, `MorningHrvCheckViewModel.kt`, and `ATrainingTrackerApp.kt`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-3556` (commit `dc034c08`).
* **Root Reason for Existing Formulation**: Passive binding was chosen to avoid duplicate search broadcasts; live artifact filtering was deferred to save CPU; ViewModel was assigned to `activity` out of convenience during navigation setup.
* **Preservation of Core Invariants**:
  1. Zero-workout generation invariant strictly preserved (zero `TrackerService` / `WorkoutSummaries.db` impact).
  2. Post-hoc centered filter invariant preserved as source of truth for persisted metrics.
  3. Posture stickiness (`SP_HRV_LAST_POSTURE`) and strict posture baseline isolation preserved.

---

## 5. Architectural Strategy & High-Level Solution
* **Sensor Discovery & Lifecycle (`MorningHrvCheckViewModel.kt`)**: In `init`, invoke `banalServiceRepository.startSearchingForPairedDevices()`. In `onCleared()`, remove `rrBeatListener` and cancel `timerJob`, retaining singleton service binding.
* **Live Artifact Warning (`MorningHrvCheckViewModel.kt`)**: In `handleRrBeat`, buffer events and evaluate `artifactFilter.filterLiveCausal(bufferedBeats.toList())`; update `artifactRatePct` and `highArtifactWarning` when rate $> 5\%$.
* **Pre-Save Readiness Preview (`MorningHrvCheckViewModel.kt`)**: Query existing entries from repository, combine with in-memory `entry`, and evaluate via `HrvReadinessCalculator.calculateReadiness`.
* **Navigation Scoping (`ATrainingTrackerApp.kt`)**: Replace `viewModel(activity)` with `viewModel()` in `composable(NavRoutes.MORNING_HRV)`.
* **UI Polish & Localization (`MorningHrvCheckScreen.kt`)**: Use `SingleChoiceSegmentedButtonRow`, `TTColor.ReadinessOnStatus`, localized strings (`R.string.morning_hrv_*`), `Modifier.navigationBarsPadding()`, and `DisposableEffect` for `keepScreenOn`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero regression in existing unit tests (`MorningHrvCheckViewModelTest`).
  2. 100% translation parity across 9 languages (`TranslationParityTest`).
  3. Zero hardcoded colors outside `Color.kt` (`ThemeColorCentralizationAuditTest`).
* **Risk Rating**: **LOW** — Isolated UI, lifecycle scoping, and localization fixes without database or IPC breaking changes (2 SP).
