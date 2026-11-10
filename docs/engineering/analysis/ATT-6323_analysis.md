# Stage 1 Analysis: ATT-6323 - SupporterPlanCard: Prevent single-letter line wrap on 'Bronze-Unterstützer'

**Ticket**: [ATT-6323](https://rainerblind.atlassian.net/browse/ATT-6323)  
**Sub-task**: [ATT-6361](https://rainerblind.atlassian.net/browse/ATT-6361) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.1`  
**Branch**: `feature/ATT-6323`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation

During Sprint Review for `ATT-6286`, visual inspection of `SupporterScreen` (`SupporterPlanCard` in `SupporterComponents.kt`) revealed that when a user has an active Silver subscription, lower Bronze tier cards render the new `"Wechseln"` `OutlinedButton`. The German compound title *"Bronze-Unterstützer"* wraps before its trailing letter (*"Bronze-Unterstütze"* / *"r"*), causing an awkward single-letter orphan line.
Default Material 3 button padding (`horizontal = 24.dp`) causes `"Wechseln"` to consume excessive horizontal width (~108dp), starving the title column of necessary width on compact viewports.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Root Cause**: `OutlinedButton` and `Button` in `SupporterPlanCard` use default Material 3 content padding (`PaddingValues(horizontal = 24.dp, vertical = 8.dp)`). The `"Wechseln"` label (8 chars) combined with `48.dp` total horizontal padding consumes ~16dp more than `"Fördern"`, leaving insufficient space for the 18-character string `"Bronze-Unterstützer"` in the middle column (`Modifier.weight(1f, fill = false)`). In addition, the title `Text` lacks `maxLines = 1` and `overflow = TextOverflow.Ellipsis`.
* **Independent Peer Review & Verification**:
  * Confirmed in `SupporterComponents.kt` (lines 244–248): `Text(text = stringResource(plan.tier.titleRes))` has no `maxLines` or `overflow` set.
  * Confirmed in `SupporterComponents.kt` (lines 263–301): `OutlinedButton` and `Button` instances omit explicit `contentPadding`.
  * Verified against `docs/coding_standards.md` (`Rule 80`): explicitly mandates auditing title row budget when replacing badges with text buttons.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * In `SupporterComponents.kt` (`SupporterPlanCard`), set `contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)` on all plan action buttons.
  * Add `maxLines = 1` and `overflow = TextOverflow.Ellipsis` to the card title `Text` composable.
  * Update `SupporterPlanCardHierarchyTest.kt` with structural contract tests verifying button content padding and title text truncation safeguards.
  * Update living docs: `docs/requirements/req_ui_design_system.md` (`REQ-UI-501.4`) and `docs/tests/tst_ui_design_system.md`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modification to tier hierarchy logic (`getPlanCardHierarchyState`), pricing text, or emblem sizing.
  * No alteration of billing flows or string resource translations.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Amends `REQ-UI-501.1` (`SupporterPlanCard` visual hierarchy). Targets `SupporterComponents.kt`.
* **Historical Origin & Commit Trace**: Ticket `ATT-6286` (commit `7d72425c`), which introduced `PlanCardHierarchyState.LOWER_TIER` with `"Wechseln"` `OutlinedButton`.
* **Root Reason for Existing Formulation**: Default M3 button padding was retained during initial hierarchy implementation without verifying compound German string width budgets.
* **Preservation of Core Invariants**: Visual states (`LOWER_TIER`, `CURRENT_TIER`, `UPGRADE_TIER`), 44dp emblems, single-line button text (`Rule 38`), and click handlers remain 100% preserved.

---

## 5. Architectural Strategy & High-Level Solution

* **UI Layer (`SupporterComponents.kt`)**:
  * Define `buttonPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)` and pass to `OutlinedButton` and `Button`.
  * Add `maxLines = 1, overflow = TextOverflow.Ellipsis` to `Text(stringResource(plan.tier.titleRes))`.
* **Test Layer (`SupporterPlanCardHierarchyTest.kt`)**:
  * Add contract test verifying `contentPadding` and single-line title parameters in `SupporterComponents.kt`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Button click targets remain accessible and visually balanced.
  2. Plan hierarchy states and purchase/navigation actions remain unaltered.
  3. Parent ticket Human Decision Gate strictly enforced.
* **Risk Rating**: **LOW** — Pure layout parameter tuning in Compose component with zero state or schema impact.
