# Stage 3: Implementation Plan - ATT-5470: SwapOrUpgradeDialog Stacked Full-Width Action Buttons

**Ticket**: [ATT-5470](https://rainerblind.atlassian.net/browse/ATT-5470)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5470`  
**Requirement Mapping**: `REQ-SET-084` (`REQ-SET-084.8`), `REQ-UI-149.8`  
**Test Mapping**: `TST-SET-084` (`TST-SET-084.8`), `TST-LOC-001`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Description & Background
Sprint 2026-45.6 on-device review of ATT-5399 on German locale (`device_limit_popup_buttons.png`) revealed broken 3-button horizontal wrapping in `SwapOrUpgradeDialog.kt`. Squeezing three buttons (*'Abbrechen'*, *'Unbegrenzte Sensoren freischalten'*, *'Tauschen & Koppeln'*) into `AppModalBottomSheet.actions` forces ~90–110dp widths, breaking words mid-syllable (*'Abbreche / n'*, *'Tausc / hen & / Koppel / n'*). ATT-5470 refactors `SwapOrUpgradeDialog.kt` to use the Rule 73 stacked full-width button layout modeled after `FirstJubileeCelebrationDialog.kt`.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-SET-084.8` (Stacked full-width buttons in `SwapOrUpgradeDialog`), `REQ-UI-149.8` (`AppModalBottomSheet` Rule 73 governance).
* **Test Mapping**: `TST-SET-084.8` (`SwapOrUpgradeDialogContractTest`), `TST-LOC-001` (`TranslationParityTest`), `TST-UI-001` (`ThemeColorCentralizationAuditTest`).

---

## 3. System Invariants & Preserved Behavior
1. **Device Quota & Atomic Swap Integrity**: Atomic swap execution (`onSwapConfirmed`), unpair recommendation preselection, and navigation to SupporterScreen remain 100% identical.
2. **Material 3 Tokens**: Uses semantic tokens (`fillMaxWidth()`, `spacedBy(8.dp)`, `Button`, `FilledTonalButton`, `TextButton`).
3. **9-Language Parity**: Zero string resource alterations; existing translated strings are reused across all 9 locales.
4. **Coding Standards §5.4 Compliance**: Contract test validates architecture without brittle design-token text-grepping.

---

## 4. Proposed Architectural Changes
* **Content Stack (`SwapOrUpgradeDialog.kt`)**: Move primary action (`Button` "Tauschen & Koppeln") and secondary action (`FilledTonalButton` "Unbegrenzte Sensoren freischalten") to the bottom of the `content` body with `Modifier.fillMaxWidth()` and `8.dp` spacing.
* **Dismiss Slot (`SwapOrUpgradeDialog.kt`)**: Retain only a single full-width `TextButton(Modifier.fillMaxWidth())` ("Abbrechen") in `AppModalBottomSheet.actions`.
* **Governance Documentation (`AppModalBottomSheet.kt`)**: Update class and parameter KDoc documenting Rule 73 prohibitions on 3-button horizontal rows.
* **UI Consistency**: Strictly conforms to `docs/design_guidelines.md` §5.3 Rule 73, matching `FirstJubileeCelebrationDialog.kt`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Refactor `SwapOrUpgradeDialog.kt` to Stacked Button Layout
* Files: `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicelist/SwapOrUpgradeDialog.kt`
* Stack `Button` and `FilledTonalButton` in `content` with `fillMaxWidth()`; set single `TextButton(Modifier.fillMaxWidth())` in `actions`.

### Step 2: Document Rule 73 in `AppModalBottomSheet.kt` KDoc
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/AppModalBottomSheet.kt`
* Add architectural guidelines documenting Rule 73 stacked button layout for actions.

### Step 3: Implement `SwapOrUpgradeDialogContractTest.kt`
* Files: `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/devicelist/SwapOrUpgradeDialogContractTest.kt`
* Verify composable signature, parameter types, absence of 3-button row in `actions`, and presence of full-width buttons.

### Step 4: Unit Test Execution & Verification
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.ui.devices.devicelist.*" --tests "com.atrainingtracker.trainingtracker.build.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" && python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit & contract tests during Stage 4; ADB screenshot inspection in Stage 5.
* **Rollback**: Clean branch isolation on `feature/ATT-5470` allows instant revert without affecting `sprint/2026-45.8`.
