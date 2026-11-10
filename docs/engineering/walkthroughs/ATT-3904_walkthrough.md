# ATT-3904 Walkthrough: 1st-Order Dynamical Power-HR Identification Engine, DB v29 Persistence, 5th Physiological School & Educational Dialog

**Ticket**: [ATT-3904](https://rainerblind.atlassian.net/browse/ATT-3904)  
**Parent Epic**: [ATT-3903](https://rainerblind.atlassian.net/browse/ATT-3903) (*Cardio-Power Analytics*)  
**Sprint**: `2026-46.1` | **Target Release**: `V5.0.0` | **Status**: Verified  
**Requirement Mapping**: [`REQ-REV-009`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements/req_aftermath_reviews.md) | **Test Mapping**: [`TST-REV-010`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests/tst_aftermath_reviews.md)  
**Branch**: `feature/ATT-3904`

---

## 1. Executive Summary

Static efficiency factor (EF) and Pw:Hr ratios fail across hilly or surging terrain because they ignore physiological cardiorespiratory latency ($\tau \approx 25\text{--}45\text{ s}$) and 4th-power normalized power inflation. `ATT-3904` delivers a terrain-invariant Output-Error (OE) dynamical Power-HR identification engine and end-to-end user-facing pipeline:
- **Dynamical System Identification Engine (`PowerHrDynamicsCalculator.kt`, `DynamicalPowerHrEngine.kt`)**: Solves discrete 1st-order OE dynamics $x_k(\tau) = e^{-\Delta t/\tau} x_{k-1}(\tau) + (1 - e^{-\Delta t/\tau}) P_k, \widehat{\text{HR}}_k = \text{HR}_0 + K x_k(\tau)$ via golden/grid search over $\tau \in [15, 90]\,\text{s}$ with physiological plausibility bounds ($K \in [0.01, 0.40]\,\text{bpm/W}$, $\text{HR}_0 \in [35, 110]\,\text{bpm}$). Evaluates fresh calibration window (min 8–55) vs end window (final 45 min) to extract cardiac drift $\Delta\text{HR@200W}$ and fresh trajectory simulation.
- **Database v29 Persistence (`WorkoutSummariesDatabaseManager.java`)**: Bumped SQLite version to 29, created `cardio_power_dynamics` table with cascade deletion, `onCreate`/`onUpgrade` symmetry, and `CardioPowerDynamicsRecord` domain representation.
- **Three-Way Parity**: Symmetrically integrated into `TrackerService.java` (live finalization), `LegacyImportEngine.kt` (TCX/FIT import), and `WorkoutRepository.kt` (`getOrComputeCardioPowerDynamics` lazy backfill reading raw samples from `WorkoutSamplesDatabaseManager`).
- **5th Physiological School UI & Explanation Dialog**: Added `enableCardioPowerDynamics` & `showCardioPowerInWorkoutSummary` to `TuningPreferencesDataStore.kt`, integrated subordinate switch into `PhysiologyModelsSection.kt`, and created `PowerHrDynamicsInfoDialog.kt` (`AppModalBottomSheet`) backed by 100% 9-locale parity in `res/values*/strings_cardio_power.xml`.
- **Architecture Integrity**: Updated SWE.2 interfaces in `docs/architecture.md` and `docs/architecture/interfaces.md` (`IF-SWE-05`, `IF-SWE-06`).

---

## 2. Traceability Matrix

| Requirement | Test Specification | Production Component | Scope | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-REV-009.1` | `TST-REV-010.1` | `PowerHrDynamicsCalculator.kt` | 1st-order OE dynamics, $\tau$ grid-search, drift, plausibility | **Verified** |
| `REQ-REV-009.2` | `TST-REV-010.3` | `TrackerService`, `LegacyImportEngine`, `WorkoutRepository` | 3-way parity across live finalization, import, lazy backfill | **Verified** |
| `REQ-REV-009.3` | `TST-REV-010.2` | `WorkoutSummariesDatabaseManager.java` | SQLite DB v29 migration, table `cardio_power_dynamics`, CRUD | **Verified** |
| `REQ-REV-009.4` | `TST-REV-010.4` | `TuningPreferencesDataStore`, `PhysiologyModelsSection` | 5th school toggle, DataStore key, settings section | **Verified** |
| `REQ-REV-009.5` | `TST-REV-010.5` | `PowerHrDynamicsInfoDialog`, `strings_cardio_power.xml` | Educational bottom sheet, 100% 9-locale string parity | **Verified** |

---

## 3. Verification & Test Execution

- **Executed Test Scope (Rule 60)**:
  - Scope: `Tier 1 targeted: physiology.*, database.*, migration.*, ui.aftermath.*, ui.settings.*, localization.TranslationParityTest, ui.theme.ThemeColorCentralizationAuditTest, ui.components.core.*, build.*`
  - Command: `python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew testDebugUnitTest --max-workers=2 --tests "com.atrainingtracker.trainingtracker.physiology.*" --tests "com.atrainingtracker.trainingtracker.database.*" --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.ui.settings.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.ui.components.core.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
  - Result: **100% PASS** (1510+ tests completed, 0 failures, 0 errors, 0 regressions).
- **Physical Device Execution**:
  - Checked `adb devices` (no physical test hardware connected). UI settings components and dialogs verified via Compose previews, Robolectric/JUnit contract tests, and `TranslationParityTest` per Rule 60 / Rule 79.
