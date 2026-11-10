# Stage 3: Implementation Plan - ATT-3904: 1st-Order Dynamical Power-HR Identification Engine & 5th School

**Ticket**: [ATT-3904](https://rainerblind.atlassian.net/browse/ATT-3904)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-3903](https://rainerblind.atlassian.net/browse/ATT-3903) (*Dynamical Power-HR System Identification*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-3904`  
**Requirement Mapping**: `REQ-REV-009` | **Test Mapping**: `TST-REV-010`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Background

Outdoor mechanical power $P(t)$ fluctuates instantaneously while heart rate $\text{HR}(t)$ follows a 1st-order low-pass response governed by cardiorespiratory O2 kinetics ($\tau \approx 25\text{--}45\text{ s}$). Static indices ($\text{EF} = \text{NP}/\text{Avg HR}$, $\text{Pw:Hr}$) fail on undulating terrain. We implement a 1st-order Output-Error (OE) dynamical model ($x_k(\tau) = e^{-\Delta t/\tau} x_{k-1}(\tau) + (1 - e^{-\Delta t/\tau}) P_k, \widehat{\text{HR}}_k = \text{HR}_0 + K x_k(\tau)$) identifying steady-state $\text{HR@200W} = \text{HR}_0 + 200 \cdot K$, lag $\tau$, slope $1/K$, and cardiac drift $\Delta\text{HR@200W}$, persisted in DB v29, wired across live/import/backfill, gated via the 5th school toggle in Advanced Settings, and explained in an in-app bottom sheet.

---

## 2. System Invariants & Preserved Behavior

1. **Calculate Always, Gate in UI (`ATT-3929`)**: OE identification executes for all workouts with $\ge 20\text{ min}$ paired Power+HR; the toggle only gates UI visibility.
2. **Database Schema Parity & Cascade**: DB v29 introduces `cardio_power_dynamics` table; workout deletion cascades cleanly; historical workouts lazily backfill on demand.
3. **Domain-Partitioned Strings & Localization (`Rule 9 REQ-PRO-041.3`)**: 100% 9-locale parity in `res/values*/strings_cardio_power.xml` without emojis.
4. **Lane 3 Concurrency Isolation**: DataStore keys added in the physiology block to prevent conflict with `ATT-3542`.

---

## 3. Proposed Architectural Changes (SWE.2)

* **Engine (`physiology/PowerHrDynamicsCalculator.kt`)**: 1st-order OE solver optimizing $\tau \in [8\text{s}, 180\text{s}]$ via OLS for Fresh Window (min 8–55), End Window (final 45 min), Drift Deltas, and simulated full $\text{HR}_{\text{fresh}}(t)$.
* **Database & Repository (`WorkoutSummariesDatabaseManager.java`, `WorkoutRepository.kt`)**: DB v29 adds `cardio_power_dynamics` (`workout_id`, `tau_start`, `hr0_start`, `k_start`, `hr200_start`, `r2_start`, `rmse_start`, `tau_end`, `hr0_end`, `k_end`, `hr200_end`, `r2_end`, `rmse_end`, `delta_hr200`, `delta_tau`, `delta_hr0`) with CRUD and lazy backfill.
* **Service & Import Integration (`TrackerService.java`, `LegacyImportEngine.kt`)**: Automatic computation on live session stop and TCX/GPX/FIT import.
* **Settings & UI (`TuningPreferencesDataStore.kt`, `PhysiologyModelsSection.kt`, `PowerHrDynamicsInfoDialog.kt`)**: 5th school switch and `AppModalBottomSheet` explanation sheet.
* **SWE.2 Maintenance (`docs/architecture.md`)**: Update component models with `PowerHrDynamicsCalculator` and `cardio_power_dynamics`.

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Output-Error Dynamical Engine & Model Unit Tests (`REQ-REV-009.1`, `TST-REV-010.1`)
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/physiology/PowerHrDynamicsCalculator.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/physiology/PowerHrDynamicsCalculatorTest.kt`.
* **Details**: Implement OE state propagation, grid-search OLS solver, physiological guards ($K > 0$, active power variance $> 1.0$), window slicing, drift deltas, and step-response unit tests.

### Step 2: Database v29 Schema Evolution & Repository Integration (`REQ-REV-009.2`, `REQ-REV-009.3`, `TST-REV-010.2`)
* **Files**: `WorkoutSummariesDatabaseManager.java`, `WorkoutRepository.kt`, `WorkoutSummariesDatabaseManagerTest.kt`, `WorkoutRepositoryCardioPowerTest.kt`.
* **Details**: Bump `DB_VERSION = 29`, create `cardio_power_dynamics` table in `onCreate`/`onUpgrade`, implement CRUD, cascade delete, and lazy backfill from `WorkoutSamplesDatabaseManager`.

### Step 3: Live Finalization & Import Pipeline Parity (`REQ-REV-009.2`, `TST-REV-010.3`)
* **Files**: `TrackerService.java`, `LegacyImportEngine.kt`, `LegacyImportEngineTest.kt`.
* **Details**: Wire automatic dynamics computation and persistence into `TrackerService.onWorkoutFinished` and `LegacyImportEngine.recalculateStats`.

### Step 4: 5th Training School Toggle & Educational Bottom Sheet (`REQ-REV-009.4`, `REQ-REV-009.5`, `TST-REV-010.4`, `TST-REV-010.5`)
* **Files**: `TuningPreferencesDataStore.kt`, `PhysiologyModelsSection.kt`, `PowerHrDynamicsInfoDialog.kt`, `res/values*/strings_cardio_power.xml`.
* **Details**: Add `enableCardioPowerDynamics` flow/preference, Compose 5th toggle with Info action, `AppModalBottomSheet` explanation dialog, and partitioned strings across all 9 locales.

### Step 5: Architecture Doc Update & Governance Verification (`Rule 8`, `TST-REV-010.6`)
* **Files**: `docs/architecture.md`, `docs/requirements/req_aftermath_reviews.md`, `docs/tests/tst_aftermath_reviews.md`.
* **Details**: Update living architecture spec and run targeted unit tests:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.physiology.*" --tests "com.atrainingtracker.trainingtracker.database.*" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
