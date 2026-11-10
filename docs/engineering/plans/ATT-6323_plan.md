# Stage 3: Implementation Plan - ATT-6323: SupporterPlanCard Layout Safeguards

**Ticket**: [ATT-6323](https://rainerblind.atlassian.net/browse/ATT-6323)  
**Sub-task**: [ATT-6368](https://rainerblind.atlassian.net/browse/ATT-6368) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.1`  
**Requirement Mapping**: `REQ-UI-504`  
**Test Mapping**: `TST-UI-504`  
**Branch**: `feature/ATT-6323`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-10  

---

## 1. Problem Description & Background

In `SupporterComponents.kt` (`SupporterPlanCard`), `OutlinedButton` and `Button` inherit default Material 3 horizontal content padding (`24.dp`), causing `"Wechseln"` (~108dp) to consume excessive horizontal space. On compact viewports, the German compound title *"Bronze-Unterstützer"* wraps before its trailing letter (*"Bronze-Unterstütze"* / *"r"*). We will apply compact horizontal button padding (`16.dp`) and enforce single-line title truncation with ellipsis.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-504` (*SupporterPlanCard: Compact Action Button Content Padding & Title Row Overflow Protection*)
* **Test Mapping**: `TST-UI-504` (*SupporterPlanCard Compact Button Padding & Title Truncation Verification Suite*)

---

## 3. System Invariants & Preserved Behavior

1. **Hierarchy & Presentation Invariants**: `PlanCardHierarchyState` resolution (`CURRENT_TIER`, `LOWER_TIER`, `UPGRADE_TIER`), 44dp emblems, recommendation badge gating on Silver+, and button click handlers remain 100% unaltered.
2. **Text Invariants**: Single-line button text without line wrapping (`Rule 38`) and 9-language translation parity are strictly preserved.
3. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

* **UI Layer (`SupporterComponents.kt`)**:
  * In `SupporterPlanCard`, define `val buttonContentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)` and pass to `OutlinedButton` (both `CURRENT_TIER` and `LOWER_TIER`) and `Button` (`UPGRADE_TIER`).
  * On the title `Text(text = stringResource(plan.tier.titleRes))`, specify `maxLines = 1` and `overflow = TextOverflow.Ellipsis`.
* **UI Consistency (Rule 23 & Rule 80)**:
  * Eliminates single-letter wrapping on compound German titles while maintaining standard 36dp touch height and visual symmetry.
* **Test Layer (`SupporterPlanCardHierarchyTest.kt`)**:
  * Add `testSupporterPlanCardButtonPaddingAndTitleOverflowContracts()` verifying source compliance.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: SupporterPlanCard Layout Tuning (`REQ-UI-504.1`, `REQ-UI-504.2`)
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/supporter/SupporterComponents.kt`.
* **Changes**: Add `buttonContentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)` to buttons; add `maxLines = 1, overflow = TextOverflow.Ellipsis` to the title `Text`.

### Step 2: Contract Test Verification Suite (`TST-UI-504.1`)
* **Files**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/supporter/SupporterPlanCardHierarchyTest.kt`.
* **Changes**: Add unit contract test asserting `contentPadding` and `maxLines = 1` on title composable in `SupporterComponents.kt`.

### Step 3: Targeted Unit & Governance Test Execution (`TST-UI-504.2`, `TST-UI-504.3`)
* **Commands**:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.supporter.*" --tests "com.atrainingtracker.trainingtracker.ui.branding.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
  * `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit tests in Stage 4; Tier 1 clean-room regression in Stage 5.
* **Rollback**: Branch isolation allows full git revert without affecting `sprint/2026-46.1`.
