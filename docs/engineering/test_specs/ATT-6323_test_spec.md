# Stage 2: Requirement & Test Specification - ATT-6323: SupporterPlanCard Layout Safeguards

**Ticket**: [ATT-6323](https://rainerblind.atlassian.net/browse/ATT-6323)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-46.1` | **Branch**: `feature/ATT-6323`  
**Requirement Mapping**: `REQ-UI-504` (`docs/requirements/req_ui_design_system.md`)  
**Test Spec ID**: `TST-UI-504` (`docs/tests/tst_ui_design_system.md`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-UI-504)

### 1.1 Problem Statement & Rationale
In `SupporterComponents.kt` (`SupporterPlanCard`), `OutlinedButton` and `Button` inherit default Material 3 horizontal padding (`24.dp`), causing `"Wechseln"` (~108dp) to consume excessive horizontal space. On compact viewports, the German compound title *"Bronze-Unterstützer"* wraps before its trailing letter (*"Bronze-Unterstütze"* / *"r"*). The card SHALL apply compact horizontal button padding (`16.dp`) and enforce single-line title truncation with ellipsis.

### 1.2 Functional & Architectural Requirements
* **REQ-UI-504.1** (*Compact Action Button Padding*): In `SupporterComponents.kt` (`SupporterPlanCard`), all action buttons (`OutlinedButton` for `CURRENT_TIER` and `LOWER_TIER`, `Button` for `UPGRADE_TIER`) SHALL specify `contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)` instead of inheriting default M3 padding (`24.dp`), reclaiming 16dp of horizontal width.
* **REQ-UI-504.2** (*Single-Line Title & Truncation Safeguard*): In `SupporterComponents.kt` (`SupporterPlanCard`), the plan tier title `Text` composable SHALL configure `maxLines = 1` and `overflow = TextOverflow.Ellipsis`.
* **REQ-UI-504.3** (*Preservation of Core Invariants & Hierarchy*): 44dp tier emblems (`REQ-UI-445.4`), single-line button text without wrapping (`Rule 38`), visual hierarchy states (`REQ-UI-501`), click handlers, and 9-locale parity SHALL remain 100% preserved.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Amends `REQ-UI-501.1` (`SupporterPlanCard` presentation). Targets `SupporterComponents.kt`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-6286` (commit `7d72425c`), which introduced `PlanCardHierarchyState.LOWER_TIER` with `"Wechseln"` `OutlinedButton`.
3. *Root Reason for Existing Formulation*: Default M3 button padding was retained during initial hierarchy implementation without verifying compound German string width budgets.
4. *Preservation of Core Invariants*: Emblems, click handling, and hierarchy states remain unchanged.

### 1.4 Acceptance Criteria (Given-When-Then) & Invariants
* *Given* `SupporterPlanCard` rendered in German for Bronze with active Silver subscription,
* *When* inspecting the card title and `"Wechseln"` button,
* *Then* the button uses `16.dp` horizontal padding and the title *"Bronze-Unterstützer"* renders without awkward line wrapping.
* *Invariants*: 44dp emblem size; single-line button text (`Rule 38`); 100% unit test pass rate.

---

## 2. Test Specification (TST-UI-504)

### Test Case 1: Button Padding & Title Truncation Contract Test (`TST-UI-504.1`)
* **Scope**: Automated Unit Contract Test (`SupporterPlanCardHierarchyTest.kt`)
* **Assertion**: Verify in `SupporterComponents.kt` that `SupporterPlanCard` defines `contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)` for action buttons and passes `maxLines = 1, overflow = TextOverflow.Ellipsis` to the card title `Text`.

### Test Case 2: Visual Hierarchy & Label Integrity Test (`TST-UI-504.2`)
* **Scope**: Unit Test (`SupporterPlanCardHierarchyTest.kt`)
* **Assertion**: Verify state resolution (`CURRENT_TIER`, `LOWER_TIER`, `UPGRADE_TIER`), badge gating, and localized string mappings remain 100% functional.

### Test Case 3: Tier 1 Targeted Regression Suite (`TST-UI-504.3`)
* **Scope**: Targeted Unit Test Suite
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.supporter.*" --tests "com.atrainingtracker.trainingtracker.ui.branding.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Assertion**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-504.1` | Contract | `SupporterPlanCardHierarchyTest.kt` | `REQ-UI-504.1`, `REQ-UI-504.2` | Specified |
| `TST-UI-504.2` | Hierarchy | `SupporterPlanCardHierarchyTest.kt` | `REQ-UI-504.3` | Specified |
| `TST-UI-504.3` | Regression | `ui.settings.supporter.*`, `build.*` | `REQ-PRO-001` | Specified |
