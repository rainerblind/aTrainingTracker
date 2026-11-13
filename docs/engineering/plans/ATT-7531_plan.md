# Stage 3: Implementation Plan - ATT-7531: Reorder Fitness Hub Tabs & Hide Inactive PMC Tab

**Ticket**: [ATT-7531](https://rainerblind.atlassian.net/browse/ATT-7531) | **Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-6033](https://rainerblind.atlassian.net/browse/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring (OOP & FP)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5`  
**Requirement Mapping**: `REQ-UI-513.4` (*Unified Fitness & Physiology Analytical Hub*)  
**Test Mapping**: `TST-UI-513.3` (`TST-UI-513.3a`, `TST-UI-513.3b`), `TST-UI-513.5`  
**Branch**: `feature/ATT-7531` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-12  

---

## 1. Problem Description & Background
In `AthleteProfileScreen.kt`, tabs are ordered as (0) PMC, (1) HRV, (2) Body & Thresholds, (3) Cardio-Power. Opening from the drawer anchors on PMC rather than baseline inputs/thresholds, hiding the top header `+` action button until navigating to Tab 2. Furthermore, disabling PMC leaves an inactive tab displaying an empty card (`pmc_disabled_guidance`), unlike Cardio-Power which cleanly omits its tab. Reordering to start with Body & Thresholds and omitting PMC when disabled restores usability and design consistency.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-513.4` (*Unified Fitness & Physiology Analytical Hub* in `docs/requirements/req_ui_design_system.md`)
* **Test Mapping**: `TST-UI-513.3` (`TST-UI-513.3a`, `TST-UI-513.3b`) and `TST-UI-513.5` in `docs/tests/tst_ui_design_system.md`

---

## 3. System Invariants & Preserved Behavior
1. **Zero Data or Schema Alterations**: SQLite persistence (`AthleteMetrics.db`, `HrvReadiness.db`) and `TuningPreferencesDataStore` remain untouched.
2. **Calculation & Visual Parity**: Underlying PMC curves, HRV trends, Body charts, and Cardio-Power analyses remain 100% identical.
3. **Top Header Add Action**: Displayed dynamically whenever `pagerState.currentPage == bodyTabIndex` (index 0 on default landing).
4. **Dynamic Pager Clamping**: On dynamic tab count contraction, `pagerState.currentPage` is safely clamped to `0..tabTitleResIds.lastIndex`.
5. **9-Language Parity**: All 9 language folders preserve string keys; zero localization deletions.

---

## 4. Proposed Architectural Changes

### Component 1: Pure Helper (`AthleteProfileViewModel.kt`)
* Implement pure function `resolveFitnessHubTabs(enablePmcModel: Boolean, enableCardioPowerDynamics: Boolean): List<Int>`.
* Returns `[fitness_tab_body, fitness_tab_pmc (if enablePmcModel), fitness_tab_hrv, fitness_tab_cardio_power (if enableCardioPowerDynamics)]`.

### Component 2: Hub Tab Row & Pager (`AthleteProfileScreen.kt`)
* In `AthleteProfileContent`, replace inline `buildList` with `resolveFitnessHubTabs(uiState.enablePmcModel, uiState.enableCardioPowerDynamics)`.
* In `rememberPagerState`, initialize `initialPage = initialTab.coerceIn(0, (tabTitleResIds.size - 1).coerceAtLeast(0))`.
* Add `LaunchedEffect(tabTitleResIds.size)` to clamp `pagerState.currentPage` to `tabTitleResIds.lastIndex` if tab count shrinks dynamically.
* In `HorizontalPager`, for `R.string.fitness_tab_pmc`, remove the dead `else` branch rendering `R.string.pmc_disabled_guidance`.

### UI Consistency (Rule 23)
* **Reference component**: `AthleteProfileScreen.kt` (Fitness & Physiology Hub).
* **Reused components**: `PrimaryScrollableTabRow`, `Tab`, `HorizontalPager`, `PerformanceManagementChartCard`.
* **Theme tokens**: `MaterialTheme.colorScheme.surfaceContainerHighest`, `LayoutConstants.HEADER_TITLE_ROW_HEIGHT`, `statusBarsPadding()`.
* **One-off styles**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pure Helper & Permutation Unit Tests (`AthleteProfileViewModel.kt`, `AthleteProfileContractAndLocalizationTest.kt`)
* Add `resolveFitnessHubTabs` pure function to `AthleteProfileViewModel.kt`.
* Implement `TST-UI-513.3a` in `AthleteProfileContractAndLocalizationTest.kt` verifying all 4 boolean combinations of `(enablePmcModel, enableCardioPowerDynamics)`.

### Step 2: Hub Tab Row, Pager Clamping & Dead Card Removal (`AthleteProfileScreen.kt`)
* Wire `tabTitleResIds` to `resolveFitnessHubTabs(uiState.enablePmcModel, uiState.enableCardioPowerDynamics)`.
* Add `LaunchedEffect(tabTitleResIds.size)` clamping and update KDoc.
* Simplify PMC pageLazyColumn by removing dead `else` card (`pmc_disabled_guidance`).

### Step 3: Contract Assertions & Regression Testing (`AthleteProfileContractAndLocalizationTest.kt`)
* Implement `TST-UI-513.3b` verifying screen contract, initial tab landing on Body, and `+` action button visibility.
* Execute targeted unit test suite: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.athleteprofile.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted unit test suites for `ui.athleteprofile.*` and `PhysiologyModelsVisibilityContractTest`, ensuring 100% PASS.
* **Rollback**: Branch isolation allows full revert without impacting `sprint/2026-46.5` or parallel feature lanes.
