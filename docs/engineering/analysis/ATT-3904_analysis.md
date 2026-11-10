# Stage 1 Analysis: ATT-3904 - 1st-Order Dynamical Power-HR Identification Engine, Expert Switch & Info Dialog

**Ticket**: [ATT-3904](https://rainerblind.atlassian.net/browse/ATT-3904)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-3903](https://rainerblind.atlassian.net/browse/ATT-3903) (*Dynamical Power-to-Heart-Rate System Identification*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.1`  
**Branch**: `feature/ATT-3904`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation

Cycling mechanical power $P(t)$ fluctuates instantaneously, whereas Heart Rate $\text{HR}(t)$ responds as a low-pass dynamical filter governed by cardiorespiratory kinetics ($\tau \approx 25\text{--}45\text{ s}$). Classical static indices ($\text{EF} = \text{NP} / \text{Avg HR}$ and $\text{Pw:Hr}$) fail on variable or hilly terrain due to phase lag and 4th-power NP inflation.
To resolve this, Epic `ATT-3903` introduces a 1st-Order Linear Dynamical System (Output-Error formulation):
$$\tau \frac{d\text{HR}(t)}{dt} + \text{HR}(t) = \text{HR}_0 + K \cdot P(t), \quad x_k(\tau) = e^{-\Delta t/\tau} x_{k-1}(\tau) + (1 - e^{-\Delta t/\tau}) P_k, \quad \widehat{\text{HR}}_k = \text{HR}_0 + K \cdot x_k(\tau)$$
`ATT-3904` provides the foundation: the mathematical identification engine, database persistence, import/live hooks, lazy backfill, the 5th Training School toggle in Advanced Settings, and an educational dialog.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Gap Analysis**: `PowerPhysiologyCalculator.kt` currently computes static indices ($\text{EF}$, Coggan metrics, $W'_{\text{bal}}$), but lacks dynamical time-constant identification, lag-compensated parameter extraction, and baseline/fatigue window drift tracking.
* **Independent Peer Review & Verification**:
  * *Mathematical Feasibility*: Grid search $\tau \in [8\text{ s}, 180\text{ s}]$ with OLS fitting of $(\text{HR}_0, K)$ across a 47-min calibration window (~2,820 samples) executes in $< 3\text{ ms}$, ensuring zero UI or background thread contention.
  * *Persistence Architecture*: Verified that adding a dedicated `cardio_power_dynamics` table in `WorkoutSummariesDatabaseManager.java` (DB v29) avoids EAV row explosion in `ExtremaValues` and accelerates longitudinal multi-workout SQL queries in `ATT-3906`.
  * *Concurrency & Lane Isolation*: Audited `TuningPreferencesDataStore.kt` and `PhysiologyModelsSection.kt`. Placing `enableCardioPowerDynamics` alongside existing physiological toggles avoids merge conflicts with concurrent sprint tickets (e.g., `ATT-3542` on Lane 3).
  * *Lifecycle Hooks*: Verified post-`ATT-6426` import routing in `LegacyImportEngine.kt` and `TrackerService.java`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. `PowerHrDynamicsCalculator.kt`: 1st-order OE solver for Fresh Window (active min 8–55), End Window (final 45 min), Drift Deltas ($\Delta\text{HR@200W}$, $\Delta\tau$, $\Delta\text{HR}_0$), and predicted trajectory $\text{HR}_{\text{fresh}}(t)$.
  2. Database & Repository: Add `cardio_power_dynamics` table in `WorkoutSummariesDatabaseManager.java` (DB v29) and CRUD in `WorkoutRepository.kt`.
  3. Automatic Finalization & Lazy Backfill: Evaluate in `TrackerService.java` and `LegacyImportEngine.kt`; lazily compute from `WorkoutSamplesDatabaseManager` on access.
  4. 5th Training School Toggle: `enableCardioPowerDynamics` in `TuningPreferencesDataStore.kt` & `PhysiologyModelsSection.kt`.
  5. UI Dialog: `PowerHrDynamicsInfoDialog.kt` (`AppModalBottomSheet`) with 9-language strings in `res/values*/strings_cardio_power.xml`.
  6. Living Documentation: Propose net-new `REQ-REV-009` and `TST-REV-010`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. Post-workout summary card & chart graph overlay (reserved for `ATT-3905`).
  2. Longitudinal season trend charts in Athlete Profile (reserved for `ATT-3906`).
  3. Real-time live cockpit sensor metric `LIVE_HR_DRIFT` (reserved for `ATT-3907`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* Net-new requirement only (`REQ-REV-009`). No existing requirements modified.

---

## 5. Architectural Strategy & High-Level Solution

* **Engine Layer (`physiology/PowerHrDynamicsCalculator.kt`)**: Implements discrete-time state propagation and least-squares regression minimizing RSS over candidate $\tau$.
* **Storage Layer (`WorkoutSummariesDatabaseManager.java`, `WorkoutRepository.kt`)**: DB version 29 introduces `cardio_power_dynamics` table keyed on `workout_id`.
* **Settings & UI Layer (`PhysiologyModelsSection.kt`, `PowerHrDynamicsInfoDialog.kt`)**: 5th accordion switch and explanatory bottom sheet partitioned across 9 locales (`strings_cardio_power.xml`).

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. *Calculate Always, Gate in UI* (`ATT-3929`): Engine computes for all qualifying workouts ($\ge 20\text{ min}$ paired Power+HR) regardless of UI toggle state.
  2. Database thread safety: DB writes confined to single-thread database dispatchers.
  3. 100% 9-locale parity for all newly added string resources.
* **Risk Rating**: **MEDIUM** — Cross-cutting database migration (v29) and import/service integration, mitigated by dedicated table isolation and targeted unit tests.
