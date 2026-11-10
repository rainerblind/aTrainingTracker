# Stage 3: Implementation Plan - ATT-3556: Morning HRV Check Screen

**Ticket**: [ATT-3556](https://rainerblind.atlassian.net/browse/ATT-3556)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-3556`  
**Requirement Mapping**: `REQ-PER-032` (`REQ-PER-032.1`..`REQ-PER-032.5`)  
**Test Mapping**: `TST-PER-040` (`TST-PER-040.1`..`TST-PER-040.6`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Background
Athletes need a dedicated, guided waking resting HRV measurement screen without launching full GPS tracking or creating empty rows in `WorkoutSummaries.db`. Standardizing morning acquisition requires a 15–30s stabilization phase, 60s standard measurement (Esco & Flatt 2014) with 120s selectable, sticky posture selection, live artifact feedback (> 5.0% amber warning), and post-hoc full-window centered filtering via `RrArtifactFilter.filterBidirectional` before persisting into `HrvReadinessRepository`.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-PER-032.1` (State machine lifecycle), `REQ-PER-032.2` (Posture stickiness & standardization), `REQ-PER-032.3` (Sensor streaming & zero-workout invariant), `REQ-PER-032.4` (Post-hoc centered calculation invariant), `REQ-PER-032.5` (Repository persistence & SWC feedback).
* **Test Mapping**: `TST-PER-040.1` (Lifecycle state machine), `TST-PER-040.2` (Sticky posture & duration), `TST-PER-040.3` (Sensor ingestion & zero-workout invariant), `TST-PER-040.4` (Post-hoc centered filter calculation), `TST-PER-040.5` (Persistence, artifact warning & SWC feedback), `TST-PER-040.6` (Tier 1 targeted regression & translation parity).

---

## 3. System Invariants & Preserved Behavior
1. **Zero Workout Generation**: Never interacts with `TrackerService` or creates rows in `WorkoutSummaries.db`.
2. **Post-Hoc Centered Filter Invariant**: Stored metrics are computed post hoc over the full beat buffer via `filterBidirectional`; live rolling tiles provide visual feedback only.
3. **Posture Baseline Segregation**: Sticky posture defaults to previous session; SWC baselines strictly isolate postures.
4. **Clean Code Lock**: Production code modifications remain locked until Gate 3 passes (`Rule 4`).

---

## 4. Proposed Architectural Changes & UI Consistency
* **Sensor Bridge (`BANALServiceRepository.kt`, `DeviceManager.java`)**: Expose `addRrBeatListener`/`removeRrBeatListener` allowing ViewModels to consume `RrBeatEvent` stream without starting `TrackerService`.
* **State Machine & Logic (`MorningHrvCheckViewModel.kt`)**: Manages `READY` $\rightarrow$ `STABILIZING` $\rightarrow$ `MEASURING` $\rightarrow$ `COMPLETED`, buffers RR beats, executes post-hoc `filterBidirectional`, persists to `HrvReadinessRepository`, and stores `SP_HRV_LAST_POSTURE = "hrvLastPosture"`.
* **Guided UI (`MorningHrvCheckScreen.kt`)**: Jetpack Compose screen with waking checklist card, medical disclaimer dialog, posture selector, live HR & amber artifact warning banner, circular countdown timer, and `ReadinessStatus` SWC result card.
* **UI Consistency**: Reuses `MetricBadge`, `MetricItem`, and `MaterialTheme.colorScheme` (Light, Dark, AMOLED), `16.dp` content padding, `12.dp` card corners per Section 5. Previews wrap in `TrainingTrackerTheme`.
* **SWE.2 Maintenance (`docs/architecture.md`)**: Allocate `MorningHrvCheckScreen` and `MorningHrvCheckViewModel` under UI Layer and HRV Readiness Subsystem.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Expose RrBeatListener in BANALServiceRepository & DeviceManager
* Files: `DeviceManager.java`, `BANALService.java`, `BANALServiceRepository.kt`
* Expose listener registration and dispatch `RrBeatEvent` to registered listeners.

### Step 2: Implement MorningHrvCheckViewModel
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/hrv/MorningHrvCheckViewModel.kt`
* Implement state machine, sticky posture preference, beat buffering, post-hoc centered computation, and repository persistence.

### Step 3: Implement MorningHrvCheckScreen Composable & Previews
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/hrv/MorningHrvCheckScreen.kt`
* Build UI with checklist card, disclaimer dialog, posture chips, countdown, artifact alert, result card, and Light/Dark/AMOLED previews.

### Step 4: Add Navigation Entry Point & Update SWE.2 Living Architecture
* Files: `PeriodsScreen.kt` / `MainActivityWithNavigation.kt`, `docs/architecture.md`, `req_aftermath_periods.md`, `tst_aftermath_periods.md`
* Add entry point to open morning check, update architecture allocations and living docs.

### Step 5: Unit & Targeted Contract Tests Execution
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/hrv/MorningHrvCheckViewModelTest.kt`
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.hrv.*" --tests "com.atrainingtracker.trainingtracker.database.hrv.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.build.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Unit tests covering state transitions, sticky posture, zero workout creation, post-hoc centered filter calculation, and 9-language translation parity.
* **Rollback**: Clean branch revert on `feature/ATT-3556` leaves `sprint/2026-46.1` completely unaffected.
