# Stage 3: Implementation Plan - ATT-5467: FirstJubileeCelebrationDialog Transitional Prompt

**Ticket**: [ATT-5467](https://rainerblind.atlassian.net/browse/ATT-5467)  
**Parent Epic**: Standalone Ticket (None)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5467`  
**Requirement Mapping**: `REQ-REV-003` (`REQ-REV-003.5`)  
**Test Mapping**: `TST-REV-003` (`TST-REV-003.1`), `TST-REV-005` (`TST-REV-005.1`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Description & Background
During Sprint 2026-45.6 review of ATT-5401, the user observed that `FirstJubileeCelebrationDialog` abruptly jumps from milestone congratulation / supporter card directly to Google Play rating and email feedback action buttons without an inviting transitional sentence. ATT-5467 introduces a warm, localized transitional prompt across all 9 authentic locales and renders it directly preceding the action buttons.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-REV-003.5` (Transitional feedback prompt rendering and 9-locale parity).
* **Test Mapping**: `TST-REV-003.1` (`FirstJubileeCelebrationDialogTest.testFirstJubileeCelebrationDialog_sourceContractVerification`), `TST-REV-005.1` (`TranslationParityTest`), `TST-UI-001` (`ThemeColorCentralizationAuditTest`).

---

## 3. System Invariants & Preserved Behavior
1. **Single-Prompt Invariant**: Dialog appearance remains strictly bounded by `SettingsDataStore.has_shown_first_jubilee_prompt` (`REQ-REV-004`).
2. **Supporter Tier Gating**: Free-tier supporter card (`!isProActive`) and upgrade routing remain intact.
3. **Intent Fallback Safety**: Google Play In-App Review and email dispatch helpers (`FirstJubileeActionHelper`) remain untouched.
4. **Clean Code Lock**: Production code modifications locked until Gate 3 audit passes (`Rule 4`).

---

## 4. Proposed Architectural Changes
* **String Resources (`res/values*/strings.xml`)**: Define `jubilee_feedback_prompt` across all 9 authentic locales (default EN, DE, ES, FR, IT, JA, NL, PL, PT) using informal 'Du' in German and zero decorative emojis.
* **Composable Refinement (`FirstJubileeCelebrationDialog.kt`)**: Insert `Text(text = stringResource(R.string.jubilee_feedback_prompt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)` immediately before the `Button` (review) and `OutlinedButton` (email) actions.
* **UI Consistency**: Reuses existing `MaterialTheme.typography.bodyMedium` and `onSurfaceVariant` tokens matching `jubilee_dialog_message`. Zero one-off styles.
* **SWE.2 Architecture**: No new subsystems, services, or DB tables introduced; living architecture documentation remains stable.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Add Localized Strings to All 9 Resource Sets
* Files: `app/src/main/res/values*/strings.xml` (EN, DE, ES, FR, IT, JA, NL, PL, PT)
* Add `jubilee_feedback_prompt` with 100% translation parity.

### Step 2: Render Transitional Text in `FirstJubileeCelebrationDialog.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/dialogs/FirstJubileeCelebrationDialog.kt`
* Place centered `Text` composable with `jubilee_feedback_prompt` directly above `onRequestInAppReview` `Button`.

### Step 3: Update Contract Test & Execute Test Suite
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/dialogs/FirstJubileeCelebrationDialogTest.kt`
* Add assertion verifying `R.string.jubilee_feedback_prompt` reference in `FirstJubileeCelebrationDialog.kt`.
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.dialogs.*" --tests "com.atrainingtracker.trainingtracker.localization.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" && python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests, 9-language translation parity verification, and Compose theme audit test.
* **Rollback**: Clean branch isolation on `feature/ATT-5467` allows instant git revert (`git checkout feature/ATT-5467 -- .`) without touching sprint branch.
