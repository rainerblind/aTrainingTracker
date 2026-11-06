# Stage 3: Implementation Plan - ATT-5468: GarageUpgradeNudgeDialog Stacked Action Buttons

**Ticket**: [ATT-5468](https://rainerblind.atlassian.net/browse/ATT-5468)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5468`  
**Requirement Mapping**: `REQ-SET-088.7`, `REQ-UI-149.8` | **Test Mapping**: `TST-SET-088.7`, `TST-LOC-001`, `TST-UI-001`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Description & Background
In Sprint 2026-45.6 review of `ATT-5399`, `GarageUpgradeNudgeDialog.kt` displayed side-by-side action buttons inside `AppModalBottomSheet.actions`. Because localized German labels exceed 25 characters (*'Später / Weiter zum Training'*, *'Upgrade auf Gold Pro (50 € / Jahr)'*), the buttons wrap across 2–3 lines into distorted tall pills. ATT-5468 aligns `GarageUpgradeNudgeDialog` with Rule 73 stacked button layout modeled on `FirstJubileeCelebrationDialog.kt`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-SET-088.7` (*Rule 73 Stacked Action Buttons in GarageUpgradeNudgeDialog*), `REQ-UI-149.8` (*Design Guidelines Rule 73 Governance*).
* **Test Mapping**: `TST-SET-088.7` (*GarageUpgradeNudgeDialogContractTest*), `TST-LOC-001` (*9-Language Parity*), `TST-UI-001` (*ThemeColorCentralizationAuditTest*).

---

## 3. System Invariants & Preserved Behavior
1. **Zero Logic Regression**: Fleet thresholds (`REQ-SET-088.1`), monotonicity (`REQ-SET-088.2`), non-spam flags (`REQ-SET-088.4`), and target plan IDs (`gold-yearly`/`diamond-yearly`) remain 100% unchanged.
2. **Signature Parity**: Public composables `GarageUpgradeNudgeDialog` and `GarageDetectorDialog` retain identical parameters (`event`, `onUpgrade`, `onDismiss`).
3. **Coding Standards §5.4**: Contract verification must use programmatic reflection with zero source-file grepping.
4. **Parent Human Decision Gate**: Parent ticket transitions only to `Final Review (Human)`.

---

## 4. Proposed Architectural Changes
* **UI Layer (`GarageUpgradeNudgeDialog.kt`)**:
  * Move primary CTA `Button(onClick = { onUpgrade(event.recommendedPlanId) }, modifier = Modifier.fillMaxWidth())` into sheet `content` below the message with `Spacer(height = 12.dp)`.
  * Replace horizontal buttons in `actions` with a single full-width centered `TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth())`.
* **UI Consistency (Rule 23)**:
  * Reference screen: `FirstJubileeCelebrationDialog.kt` / `SwapOrUpgradeDialog.kt`.
  * Reused components: `AppModalBottomSheet`, `Button`, `TextButton`, `Text`.
  * Theme tokens: `MaterialTheme.typography.bodyMedium`, `MaterialTheme.colorScheme.onSurfaceVariant`, `Modifier.fillMaxWidth()`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Refactor `GarageUpgradeNudgeDialog.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/supporter/GarageUpgradeNudgeDialog.kt`
* Changes: Stack primary upgrade `Button` at the bottom of the content column; convert `actions` to a single full-width `TextButton` dismiss.

### Step 2: Create Programmatic Contract Test
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/supporter/GarageUpgradeNudgeDialogContractTest.kt`
* Changes: Add reflection test validating composable signatures and parameter types (§5.4 compliant).

### Step 3: Targeted Verification & Governance Suite
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.supporter.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* Governance: `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted tests (Step 3). Gate 5 clean-room run ensures zero regressions.
* **Rollback**: `git checkout sprint/2026-45.8 && git branch -D feature/ATT-5468`.
