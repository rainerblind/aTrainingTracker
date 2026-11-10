# Stage 5: Walkthrough & Verification - ATT-3556: Guided Morning Resting HRV Measurement Screen

**Ticket**: [ATT-3556](https://rainerblind.atlassian.net/browse/ATT-3556)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*) | **Story Points**: 3 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-3556`  
**Requirement Mapping**: `REQ-PER-032` (`REQ-PER-032.1`–`REQ-PER-032.5`) | **Test Mapping**: `TST-PER-040` (`TST-PER-040.1`–`TST-PER-040.6`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Executive Summary & Verification Overview
Engineered dedicated guided morning resting HRV acquisition protocol without launching GPS tracking or creating empty workouts:
* **Zero-Workout Sensor Streaming (`REQ-PER-032.3`)**: Exposed `addRrBeatListener`/`removeRrBeatListener` in `BANALService`, `DeviceManager`, and `BANALServiceRepository` using `ContextCompat.registerReceiver` for API backward compatibility.
* **State Machine & Sticky Posture (`REQ-PER-032.1`, `REQ-PER-032.2`)**: Implemented `MorningHrvCheckViewModel` with `READY` $\rightarrow$ `STABILIZING` $\rightarrow$ `MEASURING` $\rightarrow$ `COMPLETED` state machine, 15s stabilization default, 60s/120s duration selector, and sticky posture memory (`SP_HRV_LAST_POSTURE = "hrvLastPosture"`).
* **Post-Hoc Centered Filter Invariant (`REQ-PER-032.4`)**: Ingested and buffered raw `RrBeatEvent` stream during measurement; upon completion, computed metrics post hoc via `RrArtifactFilter.filterBidirectional` (`rmssdMs`, `lnRmssd`, `hrvScore100`, `sdnnMs`, `restingHrBpm`, `artifactRatePct`).
* **Guided UI & SWC Feedback (`REQ-PER-032.1`, `REQ-PER-032.5`)**: Built `MorningHrvCheckScreen` featuring waking protocol guidance card, countdown timer, pulsing live HR telemetry, amber warning banner for artifact rates $> 5.0\%$, SWC autonomic readiness results card, and Light/Dark/AMOLED `@Preview`s.
* **Navigation & Architecture Parity**: Wired `NavRoutes.MORNING_HRV`, drawer menu item under Training in `AppNavigationDrawer`, and updated SWE.2 allocations in `docs/architecture.md`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PER-032.1` | `TST-PER-040.1` | `MorningHrvCheckViewModelTest.testLifecycleStateMachine` | **PASSED** | `Verified` |
| `REQ-PER-032.2` | `TST-PER-040.2` | `MorningHrvCheckViewModelTest.testStickyPostureAndDurationConfiguration` | **PASSED** | `Verified` |
| `REQ-PER-032.3` | `TST-PER-040.3` | `MorningHrvCheckViewModelTest.testSensorIngestionAndZeroWorkoutInvariant` | **PASSED** | `Verified` |
| `REQ-PER-032.4` | `TST-PER-040.4` | `MorningHrvCheckViewModelTest.testPostHocCenteredFilterCalculation` | **PASSED** | `Verified` |
| `REQ-PER-032.5` | `TST-PER-040.5` | `MorningHrvCheckViewModelTest.testPersistenceAndArtifactWarning` | **PASSED** | `Verified` |
| `REQ-LOC-001` | `TST-PER-040.6` | `TranslationParityTest` (25 localized strings across 9 locales) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Test Scope (Rule 60)
* **Executed Scope**:
  `Tier 1 targeted: MorningHrvCheckViewModelTest, physiology.hrv.*, ui.aftermath.periods.*, ui.theme.ThemeColorCentralizationAuditTest, ui.components.core.*, localization.TranslationParityTest, database.hrv.*, build.*`
* **Execution Command**:
  ```bash
  python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew testDebugUnitTest \
    --tests "com.atrainingtracker.trainingtracker.physiology.hrv.*" \
    --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periods.*" \
    --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.components.core.*" \
    --tests "com.atrainingtracker.trainingtracker.build.*" \
    --tests "com.atrainingtracker.trainingtracker.ui.hrv.*" \
    --tests "com.atrainingtracker.trainingtracker.database.hrv.*" \
    --max-workers=2
  ```
* **Result**: `BUILD SUCCESSFUL` (100% PASS, 0 failures, 0 regressions in 46s).
* **Physical Device Check**: `adb devices` confirmed no physical device attached; emulator launch skipped per Rule 79.

---

## 4. Invariants & Preserved Behavior
1. **Zero Workout Generation**: Never interacts with `TrackerService` or creates rows in `WorkoutSummaries.db`.
2. **Post-Hoc Centered Filter Invariant**: Filtered metrics computed post hoc via `filterBidirectional`; live tiles provide visual pacing only.
3. **Posture Isolation**: Morning baseline SWC corridors strictly segregate `SUPINE`, `SEATED`, and `STANDING`.
4. **9-Language Localization Parity**: All 25 morning HRV strings verified across `values/` and 8 localized variants.
