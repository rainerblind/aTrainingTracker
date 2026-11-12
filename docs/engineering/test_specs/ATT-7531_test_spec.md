# Stage 2: Requirement & Test Specification - ATT-7531: Reorder Fitness Hub Tabs & Hide Inactive PMC Tab

**Ticket**: [ATT-7531](https://rainerblind.atlassian.net/browse/ATT-7531)  
**Parent Epic**: [ATT-6033](https://rainerblind.atlassian.net/browse/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring (OOP & FP)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5`  
**Requirement Mapping**: `REQ-UI-513.4` (*Unified Fitness & Physiology Analytical Hub*)  
**Test Spec ID**: `TST-UI-513.3` | **Branch**: `feature/ATT-7531`  
**Author**: AI Agent 2 (Implementer, `subagent_flash`) | **Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-UI-513.4)

### 1.1 Problem Statement & Rationale
In `AthleteProfileScreen.kt`, the hub currently orders tabs as (0) PMC, (1) HRV, (2) Body & Thresholds, (3) Cardio-Power. Opening from the drawer anchors on PMC rather than baseline inputs/thresholds, requiring extra navigation to access the top header `+` action button. Furthermore, disabling PMC leaves an inactive tab displaying an empty card, unlike Cardio-Power which cleanly omits the tab. Reordering to start with Body & Thresholds and omitting the PMC tab when disabled restores usability and design consistency.

### 1.2 Attributes
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit

### 1.3 Functional & Architectural Requirements
1. `AthleteProfileScreen.kt` SHALL resolve hub tabs in order:
   - 1: Body & Thresholds (`R.string.fitness_tab_body`) [Always present, default initial landing tab]
   - 2: Fitness & Fatigue / PMC (`R.string.fitness_tab_pmc`) [Conditional on `enablePmcModel`]
   - 3: HRV & Readiness (`R.string.fitness_tab_hrv`) [Always present]
   - 4: Cardio-Power (`R.string.fitness_tab_cardio_power`) [Conditional on `enableCardioPowerDynamics`]
2. Tab resolution logic SHALL be extracted into pure function `resolveFitnessHubTabs(enablePmcModel: Boolean, enableCardioPowerDynamics: Boolean): List<Int>`.
3. When `enablePmcModel = false`, the PMC tab header and page SHALL be omitted completely.
4. Dynamic contraction of tab count MUST safely clamp `pagerState.currentPage` to `0..tabTitleResIds.lastIndex`.

### 1.4 Acceptance Criteria (Given-When-Then)
* **AC1 (Default Tab Sequence & Initial Landing)**:
  * *Given* `enablePmcModel = true` and `enableCardioPowerDynamics = true`,
  * *When* opening Fitness & Physiology,
  * *Then* tabs are ordered `[Body, PMC, HRV, Cardio-Power]`, landing on `Body` with `+` action visible.
* **AC2 (PMC Inactive Suppression)**:
  * *Given* `enablePmcModel = false`,
  * *When* opening Fitness & Physiology,
  * *Then* PMC is absent from tabs, yielding `[Body, HRV]` (or `[Body, HRV, Cardio-Power]`).
* **AC3 (Unit Test & Contraction Parity)**:
  * *Given* all 4 permutations of `(enablePmcModel, enableCardioPowerDynamics)`,
  * *When* `resolveFitnessHubTabs` is invoked,
  * *Then* output matches the exact expected resource IDs and passes all contract tests.

### 1.5 System Invariants
- Zero modification to underlying PMC, HRV, or athlete profile calculation models or databases.
- 9-language localization parity preserved across all supported locales.

---

## 2. Test Specification (TST-UI-513.3)

### Test Case 1: Tab Resolution Permutations (`TST-UI-513.3a`)
* **Scope**: Unit Test (`AthleteProfileContractAndLocalizationTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/athleteprofile/AthleteProfileContractAndLocalizationTest.kt`
* **Precondition**: `resolveFitnessHubTabs` pure function available.
* **Action**: Evaluate all 4 boolean combinations of `(enablePmcModel, enableCardioPowerDynamics)`.
* **Expected Result**:
  - `(true, true)` -> `[fitness_tab_body, fitness_tab_pmc, fitness_tab_hrv, fitness_tab_cardio_power]`
  - `(false, true)` -> `[fitness_tab_body, fitness_tab_hrv, fitness_tab_cardio_power]`
  - `(true, false)` -> `[fitness_tab_body, fitness_tab_pmc, fitness_tab_hrv]`
  - `(false, false)` -> `[fitness_tab_body, fitness_tab_hrv]`

### Test Case 2: Hub Tab Header & Action Contract (`TST-UI-513.3b`)
* **Scope**: Contract Test (`AthleteProfileContractAndLocalizationTest.kt`)
* **Action**: Verify `AthleteProfileScreen.kt` source references `resolveFitnessHubTabs`, binds `initialTab = 0` to Body, and shows `+` Add Entry on `bodyTabIndex`.
* **Expected Result**: `bodyTabIndex == tabTitleResIds.indexOf(R.string.fitness_tab_body)` (index 0 by default).

### Test Case 3: Tier 1 Targeted Profile Suite (`TST-UI-513.5`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.athleteprofile.*"`
* **Expected Result**: 100% PASS across all athlete profile unit and contract tests.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-513.3a` | Unit | `AthleteProfileContractAndLocalizationTest.kt` | `REQ-UI-513.4` | Specified |
| `TST-UI-513.3b` | Contract | `AthleteProfileContractAndLocalizationTest.kt` | `REQ-UI-513.4` | Specified |
| `TST-UI-513.5` | Tier 1 Regr | `ui.athleteprofile.*` | `REQ-PRO-001` | Specified |
