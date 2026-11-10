# Stage 2: Requirement & Test Specification - ATT-3904: 1st-Order Dynamical Power-HR Engine & 5th School

**Ticket**: [ATT-3904](https://rainerblind.atlassian.net/browse/ATT-3904)  
**Parent Epic**: [ATT-3903](https://rainerblind.atlassian.net/browse/ATT-3903) (*Dynamical Power-HR System Identification*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-3904`  
**Requirement Mapping**: `REQ-REV-009` | **Test Spec ID**: `TST-REV-010`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-REV-009: Dynamical Power-HR Identification Engine)

### 1.1 Problem Statement & Rationale
Static ratios ($\text{EF} = \text{NP}/\text{Avg HR}$, $\text{Pw:Hr}$) fail on variable terrain by ignoring cardiorespiratory response latency ($\tau \approx 25\text{--}45\text{ s}$). A 1st-order Output-Error (OE) dynamical model ($x_k(\tau) = e^{-\Delta t/\tau} x_{k-1}(\tau) + (1 - e^{-\Delta t/\tau}) P_k, \widehat{\text{HR}}_k = \text{HR}_0 + K x_k(\tau)$) extracts terrain-independent physiological parameters: steady-state reference $\text{HR@200W} = \text{HR}_0 + 200 \cdot K$, response lag $\tau$, aerobic gain $K$ (slope $1/K$), and cardiac drift $\Delta\text{HR@200W}$.

### 1.2 Functional & Architectural Requirements
1. **Output-Error Identification Engine (`PowerHrDynamicsCalculator.kt`)**:
   - For workouts with $\ge 20\text{ min}$ paired Power+HR, the system SHALL fit 1st-order OE parameters over:
     a. *Fresh Window* (active min 8–55): $\tau_{\text{start}}$, $\text{HR0}_{\text{start}}$, $K_{\text{start}}$, $\text{HR@200W}_{\text{start}}$, $R^2_{\text{start}}$, $\text{RMSE}_{\text{start}}$.
     b. *End Window* (final 45 min): $\tau_{\text{end}}$, $\text{HR0}_{\text{end}}$, $K_{\text{end}}$, $\text{HR@200W}_{\text{end}}$, $R^2_{\text{end}}$, $\text{RMSE}_{\text{end}}$.
     c. *Drift Deltas*: $\Delta\text{HR@200W} = \text{HR@200W}_{\text{end}} - \text{HR@200W}_{\text{start}}$, $\Delta\tau$, $\Delta\text{HR}_0$, and predicted trajectory $\text{HR}_{\text{fresh}}(t)$.
2. **Three-Way Parity (Live + Import + Lazy Backfill)**:
   - *Live Recording*: `TrackerService` SHALL finalize and persist dynamics on session stop.
   - *File Import*: `LegacyImportEngine` SHALL compute and persist dynamics for TCX, GPX, and FIT files.
   - *Lazy Backfill*: `WorkoutRepository` SHALL lazily compute and cache dynamics for historical workouts from `WorkoutSamplesDatabaseManager`.
3. **Database Schema Evolution & Repository CRUD**:
   - `WorkoutSummariesDatabaseManager.java` (DB v29) SHALL introduce table `cardio_power_dynamics` keyed on `workout_id` with foreign key cascade.
   - `WorkoutRepository.kt` SHALL expose thread-safe CRUD methods for cardio-power dynamics.
4. **5th Training School Toggle & Educational Dialog**:
   - `TuningPreferencesDataStore.kt` SHALL expose `enableCardioPowerDynamics` (default: `false` opt-in).
   - `PhysiologyModelsSection.kt` SHALL render the 5th accordion switch with summary and Info button.
   - `PowerHrDynamicsInfoDialog.kt` (`AppModalBottomSheet`) SHALL explain cardiorespiratory lag, $\text{HR@200W}$, and drift with 100% parity across all 9 locales in `res/values*/strings_cardio_power.xml`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC 1 (3-Way Identification)**: *Given* $\ge 20\text{ min}$ paired Power+HR telemetry, *When* processed via live, import, or backfill, *Then* OE parameters are identified and persisted in `cardio_power_dynamics`.
* **AC 2 (5th School Toggle)**: *Given* Advanced Settings -> *Physiology & Training Models*, *When* viewed, *Then* the 5th school toggle is rendered with Info action opening `PowerHrDynamicsInfoDialog`.
* **AC 3 (9-Locale Parity)**: *Given* all 9 app locales, *When* `TranslationParityTest` runs, *Then* all cardio-power string tokens in `strings_cardio_power.xml` exist with 100% parity.

---

## 2. Test Specification (TST-REV-010: Dynamical Power-HR Verification Suite)

### Test Case 1: Engine Unit & Step-Response Test (`TST-REV-010.1`)
* **Scope**: `PowerHrDynamicsCalculatorTest.kt`. Verify OE fitting ($\tau, \text{HR}_0, K$) on synthetic step responses within $\pm 2\%$, window slicing (min 8–55 vs final 45 min), < 20 min gating, and drift deltas.

### Test Case 2: Database Migration v29 & CRUD Contract Test (`TST-REV-010.2`)
* **Scope**: `WorkoutSummariesDatabaseManagerTest.kt`, `WorkoutRepositoryTest.kt`. Verify DB v28 -> v29 migration parity, table `cardio_power_dynamics` integrity, cascade deletion, and repository CRUD round-trip.

### Test Case 3: Three-Way Parity & Lazy Backfill Test (`TST-REV-010.3`)
* **Scope**: `LegacyImportEngineTest.kt`, `TrackerServiceFinalizationTest.kt`, `WorkoutRepositoryTest.kt`. Verify identical output across live recording, file import, and on-demand lazy backfill.

### Test Case 4: 5th Training School Toggle & Info Dialog Test (`TST-REV-010.4`)
* **Scope**: `PhysiologyModelsSectionTest.kt`, `PhysiologyModelsVisibilityContractTest.kt`. Verify toggle binding to DataStore, Info button launch, and UI state isolation.

### Test Case 5: 9-Locale Localization Parity Test (`TST-REV-010.5`)
* **Scope**: `TranslationParityTest.kt` verifying `strings_cardio_power.xml` across EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-REV-010.1]` | Unit | `PowerHrDynamicsCalculatorTest`: OE parameter fitting & windowing | `REQ-REV-009` | Specified |
| `[TST-REV-010.2]` | Database | `WorkoutSummariesDatabaseManagerTest`: Schema v29 migration & CRUD | `REQ-REV-009` | Specified |
| `[TST-REV-010.3]` | Integration | `LegacyImportEngineTest`: 3-way parity (live, import, lazy backfill) | `REQ-REV-009` | Specified |
| `[TST-REV-010.4]` | UI / Compose | `PhysiologyModelsSectionTest`: 5th school toggle & Info dialog | `REQ-REV-009` | Specified |
| `[TST-REV-010.5]` | Localization | `TranslationParityTest`: 9-locale parity for `strings_cardio_power.xml` | `REQ-REV-009`, `REQ-LOC-001` | Specified |
