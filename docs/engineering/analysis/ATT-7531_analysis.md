# Stage 1 Analysis: ATT-7531 - Reorder Fitness Hub Tabs & Hide Inactive PMC Tab

**Ticket**: [ATT-7531](https://rainerblind.atlassian.net/browse/ATT-7531)  
**Parent Epic**: [ATT-6033](https://rainerblind.atlassian.net/browse/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring (OOP & FP)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5`  
**Branch**: `feature/ATT-7531` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation
The unified **Fitness & Physiologie** hub (`AthleteProfileScreen.kt:202-211`, `REQ-UI-513.4`) currently exhibits two usability flaws:
1. *Suboptimal Initial Tab & Progression*: Hub tabs are ordered as (0) *Fitness & Ermüdung* (PMC), (1) *HRV & Erholung*, (2) *Körper & Schwellen*, (3) *Cardio-Power*. Opening the hub from the drawer lands on PMC (Tab 0) instead of core physiological baseline data (Body & Thresholds). Furthermore, the header `+` (Add Entry) button is only displayed on *Körper & Schwellen* (`bodyTabIndex = 2`), requiring extra taps to access.
2. *Asymmetric Tab Visibility*: When *Cardio-Power Dynamics* is disabled, its tab is omitted. When *Fitness & Ermüdung (PMC)* is disabled in Expert Settings (`enablePmcModel = false`), its tab remains visible and displays an empty placeholder card (`R.string.pmc_disabled_guidance`).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic Investigation**:
  1. *Hardcoded Incomplete Dependency*: `tabTitleResIds` in `AthleteProfileScreen.kt:202` only remembers `uiState.enableCardioPowerDynamics`. `uiState.enablePmcModel` is completely ignored during tab list composition.
  2. *Dead Placeholder Rendering*: Lines 326-350 render a fallback `Card` containing `pmc_disabled_guidance` rather than suppressing the tab header at source.
  3. *Unsynchronized Navigation Default*: Opening `AthleteProfileScreen` defaults to `initialTab = 0`, anchoring on derived PMC analytics rather than primary anthropometric/threshold parameters.
* **Independent Peer Review & Verification**:
  * Verified in `AthleteProfileScreen.kt#L202-L218`: `tabTitleResIds` statically inserts `R.string.fitness_tab_pmc` at index 0. `bodyTabIndex` resolves to index 2, suppressing the `+` action button on initial screen load.
  * Verified in `AthleteProfileViewModel.kt#L128-L130`: `enablePmcModel` is available in `AthleteProfileUiState` but unconsumed by tab resolution.
  * Tab contraction guardrail: If `tabTitleResIds.size` decreases dynamically (e.g., PMC toggled off in background settings), `pagerState.currentPage` must safely coerce to `0..tabTitleResIds.lastIndex`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Extract pure helper `resolveFitnessHubTabs(enablePmcModel: Boolean, enableCardioPowerDynamics: Boolean): List<Int>` in `AthleteProfileViewModel.kt`.
  2. Reorder hub tabs in `AthleteProfileScreen.kt` to: (1) `fitness_tab_body`, (2) conditional `fitness_tab_pmc` (`enablePmcModel`), (3) `fitness_tab_hrv`, (4) conditional `fitness_tab_cardio_power` (`enableCardioPowerDynamics`).
  3. Safely clamp `pagerState.currentPage` if tab count shrinks dynamically.
  4. Remove dead `pmc_disabled_guidance` card from PMC page.
  5. Add unit tests for `resolveFitnessHubTabs` across all 4 boolean combinations in `AthleteProfileContractAndLocalizationTest.kt`.
* **Out-of-Scope Non-Goals**:
  * Modifying underlying PMC calculations, SQLite tables, or default `TuningConfig` values.
  * Altering drawer routing or other drawer destinations.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Refines `REQ-UI-513.4` (*Unified Fitness & Physiology Analytical Hub*) in `docs/requirements/req_ui_design_system.md`.
* **Historical Origin & Commit Trace**: Commit `83e5f529` (`ATT-6720`, Sprint 2026-46.3).
* **Root Reason for Existing Formulation**: ATT-6720 consolidated PMC, HRV, and Body into one screen. PMC was initially placed first as the primary high-level fitness chart, with disabled guidance added to steer users to Expert Settings.
* **Preservation of Core Invariants**: All 4 analytical tab views, data series, charts, and editing flows remain 100% intact. String resources across all 9 locales remain preserved.

---

## 5. Change Impact Analysis (SUP.10)
* **Affected Requirements**: Refinement of `REQ-UI-513.4` in `docs/requirements/req_ui_design_system.md`.
* **Affected Architecture Elements**: `AthleteProfileScreen.kt`, `AthleteProfileViewModel.kt`, `AthleteProfileContractAndLocalizationTest.kt`.
* **Verification Impact & Test Surface**: Unit tests verifying all 4 tab resolution states; contract assertions checking drawer alignment, tab ordering, and top-bar action visibility.
* **Risk Assessment & Invariants**: Low risk. Pure UI layout and helper refactoring with zero database, calculation, or lifecycle side effects.

---

## 6. Architectural Strategy & High-Level Solution
1. **Helper Extraction (`AthleteProfileViewModel.kt`)**: Implement pure function `resolveFitnessHubTabs(enablePmcModel: Boolean, enableCardioPowerDynamics: Boolean): List<Int>`.
2. **UI Integration (`AthleteProfileScreen.kt`)**: Bind `tabTitleResIds` to `remember(uiState.enablePmcModel, uiState.enableCardioPowerDynamics) { resolveFitnessHubTabs(...) }`.
3. **Pager State Clamping**: Add `LaunchedEffect(tabTitleResIds.size)` to clamp `pagerState.currentPage` within `0..tabTitleResIds.lastIndex`.
4. **Clean LazyColumn**: Simplify PMC page to render `PerformanceManagementChartCard` when active, dropping `pmc_disabled_guidance` card.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Default entry from drawer immediately presents *Körper & Schwellen* with `+` action button.
  2. Pure helper is 100% deterministic and unit-tested for all 4 permutations.
  3. Localization parity across all 9 languages is preserved.
* **Risk Rating**: **LOW** (Isolated UI presentation logic with complete unit test coverage).
