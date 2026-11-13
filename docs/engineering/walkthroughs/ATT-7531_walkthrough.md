# Stage 5: Walkthrough & Verification - ATT-7531: Reorder Fitness Hub Tabs & Hide Inactive PMC Tab

**Ticket**: [ATT-7531](https://rainerblind.atlassian.net/browse/ATT-7531)  
**Parent Epic**: [ATT-6033](https://rainerblind.atlassian.net/browse/ATT-6033) (*Code Architecture, Deduplication & Clean Code Refactoring (OOP & FP)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5`  
**Requirement Mapping**: `REQ-UI-513.4` (*Unified Fitness & Physiology Analytical Hub*)  
**Test Mapping**: `TST-UI-513.3` (`TST-UI-513.3a`, `TST-UI-513.3b`, `TST-UI-513.5`)  
**Branch**: `feature/ATT-7531` | **Author**: AI Agent 2 (Implementer, `subagent_flash`) | **Date**: 2026-11-13  

---

## 1. Executive Summary & Verification Overview
In `AthleteProfileScreen.kt` and `AthleteProfileViewModel.kt`, Fitness & Physiology hub tabs were reordered to follow natural physiological progression: (1) Body & Thresholds (default initial landing tab `initialTab = 0`), (2) Banister PMC (conditional on `enablePmcModel`), (3) HRV & Readiness, (4) Cardio-Power Dynamics (conditional on `enableCardioPowerDynamics`). Opening from the drawer immediately surfaces baseline inputs with the top header `+` (Add Entry) action visible. The pure helper `resolveFitnessHubTabs` was extracted, the dead `pmc_disabled_guidance` fallback card removed from the PMC page, and `pagerState.currentPage` safely clamped via `LaunchedEffect(tabTitleResIds.size)` on dynamic tab contraction.

Following Stage 6 Cold Carbon Review (BOUNCE 1/3), commit `073a5555` healed all 3 findings: (1) `selectedTabIndex` in `PrimaryScrollableTabRow` is explicitly clamped using `pagerState.currentPage.coerceIn(0, (tabTitleResIds.size - 1).coerceAtLeast(0))` to prevent `IndexOutOfBoundsException` during dynamic tab count contraction, (2) `AthleteProfileContent` KDoc was updated to accurately document the physiological tab hierarchy, and (3) `AthleteProfileContractAndLocalizationTest` added a contract assertion verifying `selectedTabIndex` coercion.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-513.4` | `TST-UI-513.3a` | Unit (`AthleteProfileContractAndLocalizationTest.kt`) | **PASSED** | `Verified` |
| `REQ-UI-513.4` | `TST-UI-513.3b` | Contract (`AthleteProfileContractAndLocalizationTest.kt`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-513.5` | Tier 1 Regr (`ui.athleteprofile.*`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Regression Scope
Executed command:
`python3 tools/parallel_sprint_lock.py -- ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.athleteprofile.*"`
- **Result**: `BUILD SUCCESSFUL` (100% PASS across all 36 athlete profile unit and contract tests).
- `TST-UI-513.3a`: Verified all 4 boolean combinations of `(enablePmcModel, enableCardioPowerDynamics)` in `resolveFitnessHubTabs`.
- `TST-UI-513.3b`: Verified `AthleteProfileScreen` binds `resolveFitnessHubTabs`, lands on Body with `+` action visible, clamps pager on size changes, clamps `selectedTabIndex` in `PrimaryScrollableTabRow`, and omits dead `pmc_disabled_guidance` placeholder.

### Requirement Governance Verification
Executed command:
`python3 tools/verify_requirement_governance.py`
- **Result**: `PASS` (zero Composable islands, zero attribute violations, zero repository leaks, 100% System-to-Software parity).

---

## 4. UI Consistency & Visual Verification (Rule 23)
* **Reference Component**: Fitness & Physiology Hub (`AthleteProfileScreen.kt`, `PrimaryScrollableTabRow`, `HorizontalPager`).
* **Design Guidelines (§5)**: Preserves standard M3 surface container tokens, `LayoutConstants.HEADER_TITLE_ROW_HEIGHT`, and `statusBarsPadding()`.
* **Preserved System Invariants**: Zero schema/DB migrations; all underlying PMC, HRV, and Cardio-Power calculation engines remain 100% intact; 9-language localization parity preserved across all supported locales.

---

## 5. Invariant & Governance Verification
1. **Zero Production Regressions**: Complete `ui.athleteprofile.*` test suite passed with 100% success under `parallel_sprint_lock.py`.
2. **Living Documentation Synchronized**: `REQ-UI-513.4` in `docs/requirements/req_ui_design_system.md` and `TST-UI-513.3` in `docs/tests/tst_ui_design_system.md` verified.
3. **Stage Progression**: Submitted via composite `submit-stage` for Gate 5 sign-off and squash merge by Pro Agent.

---

## 6. Stage 6 BOUNCE 1/3 Healing Audit & Resolution (Commit `073a5555`)
- **Finding 1 (Tab Row Index Out-of-Bounds Protection)**: In `AthleteProfileScreen.kt` line 281, `selectedTabIndex` in `PrimaryScrollableTabRow` was updated to `pagerState.currentPage.coerceIn(0, (tabTitleResIds.size - 1).coerceAtLeast(0))`. This guarantees that if `tabTitleResIds` contracts dynamically (e.g. PMC or Cardio-Power disabled in Expert Settings), the tab row selection never indexes out-of-bounds prior to pager recomposition.
- **Finding 2 (KDoc Hierarchy Parity)**: In `AthleteProfileScreen.kt` lines 167–172, the KDoc for `AthleteProfileContent` was updated to reflect the new physiological hierarchy: Page 0: Body & Thresholds, Page 1: PMC (conditional), Page 2: HRV & Readiness, Page 3: Cardio-Power (conditional).
- **Finding 3 (Contract Test Coverage)**: In `AthleteProfileContractAndLocalizationTest.kt`, added an assertion verifying that `PrimaryScrollableTabRow` contains `selectedTabIndex = pagerState.currentPage.coerceIn(0, (tabTitleResIds.size - 1).coerceAtLeast(0))`.
- **Gate 4 Verdict**: `PASS` recorded on subtask `ATT-7625` by `agent2`.
