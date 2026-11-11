# ATT-6722 Walkthrough: Lazy Cardio-Power Backfill, Physiological Bounds & Filter Warm-Up, and Accordion Subtitle

**Ticket**: [ATT-6722](https://rainerblind.atlassian.net/browse/ATT-6722)  
**Parent Epic**: [ATT-3903](https://rainerblind.atlassian.net/browse/ATT-3903) (*Cardio-Power Analytics*)  
**Sprint**: `2026-46.2` | **Target Release**: `V5.0.0` | **Status**: Verified  
**Requirement Mapping**: [`REQ-REV-009`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements/req_aftermath_reviews.md) | **Test Mapping**: [`TST-REV-010`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests/tst_aftermath_reviews.md)  
**Branch**: `feature/ATT-6722`  

---

## 1. Executive Summary

`ATT-6722` resolves critical data pipeline, numerical estimation, and UI summary regressions from `ATT-3904`:
- **Optimized Lazy Backfill & Memory Cache (`WorkoutRepository.kt`)**: Added column projection (`_ID`, `POWER`, `HR`, `TIME_ACTIVE`, `TIME_TOTAL`, `TIME`) ordered by `_ID ASC` to `getOrComputeCardioPowerDynamics`. Ineligible/non-qualifying workouts are recorded in `ineligibleCardioPowerWorkoutIds` to eliminate redundant SQLite queries. Added reactive `updateWorkoutInMemory` and bounded background migration `backfillHistoricalCardioPowerDynamics(limit = 20)`.
- **ViewModel Reachability Wiring (`TrackOnMapAftermathViewModel.kt`, `WorkoutSummariesViewModel.kt`)**: Connected single-session lazy calculation on workout detail opening and background historical backfill triggered when `enableCardioPowerDynamicsFlow` is active.
- **Filter Pre-Warming & Physiological Plausibility Gates (`PowerHrDynamicsCalculator.kt`)**: Pre-warmed discrete filter state $x_k(\tau)$ from $t = 0$ to $\text{startIndex} - 1$ instead of naive seeding at `startIndex`, eliminating warm-up transient step bias. Enforced biological plausibility gates ($K \in [0.01, 0.45]\,\text{bpm/W}$, $\text{HR}_0 \in [30, 125]\,\text{bpm}$, active pedaling $\ge 15\text{ min}$, $R^2_{\text{start}} \ge 0.25$) and supported non-uniform sampling timestamps `timestampsSec`.
- **Accordion Summary Parity (`AdvancedTuningAccordion.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`)**: Added `enableCardioPowerDynamics` to `formatPhysiologyModelsSubtitle`, appending localized `R.string.cardio_power_domain_label` ("Cardio-Power") across both screen and dialog with 100% 9-language translation parity.

---

## 2. Traceability Matrix

| Requirement | Test Specification | Production Component | Scope | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-REV-009.1d` | `TST-REV-010.7` | `PowerHrDynamicsCalculator.kt` | Enforce biological gates ($K, \text{HR}_0, R^2$, pedaling) | **Verified** |
| `REQ-REV-009.1e` | `TST-REV-010.6` | `PowerHrDynamicsCalculator.kt` | Pre-warm filter state $x_k(\tau)$ from $t = 0$ to $\text{startIndex}-1$ | **Verified** |
| `REQ-REV-009.2c` | `TST-REV-010.8` | `WorkoutRepository.kt`, `TrackOnMapAftermathViewModel.kt` | Column-projected query, ineligible cache & bounded backfill | **Verified** |
| `REQ-REV-009.4c` | `TST-REV-010.9` | `AdvancedTuningAccordion.kt`, `AdvancedTuningScreen.kt` | Accordion collapsed subtitle includes localized Cardio-Power | **Verified** |

---

## 3. Verification & Test Execution

- **Executed Test Scope (Rule 60)**:
  - Scope: `physiology.PowerHrDynamicsCalculatorTest`, `ui.settings.tuning.AdvancedTuningAccordionTest`, `ui.aftermath.WorkoutRepositoryCardioPowerBackfillTest`, `translation.TranslationParityTest`
  - Commands executed:
    - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.physiology.PowerHrDynamicsCalculatorTest"` (Passed)
    - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningAccordionTest"` (Passed)
    - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepositoryCardioPowerBackfillTest"` (Passed)
    - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.translation.TranslationParityTest"` (Passed)
    - `python3 tools/verify_requirement_governance.py` (Passed, 0 Composable islands, 0 leaky DB/cursor members, 0 architecture violations, 100% Chesterton's Fence compliance)
  - Result: **100% PASS** (all tests passed with 0 failures).
- **Device Policy**:
  - Android emulator permanently retired per explicit user directive (`Rule 79`). Physical on-device evaluation reserved for PO's real Pixel 10 during Ceremony 2 Joint Review.
