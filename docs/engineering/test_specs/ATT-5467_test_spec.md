# Stage 2: Requirement & Test Specification - ATT-5467: FirstJubileeCelebrationDialog Transitional Prompt

**Ticket**: [ATT-5467](https://rainerblind.atlassian.net/browse/ATT-5467)  
**Parent Epic**: Standalone Ticket (None)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5467`  
**Requirement Mapping**: `REQ-REV-003` (*First-Jubilee Celebration Dialog & Multi-Channel Engagement*)  
**Test Spec ID**: `TST-REV-003`, `TST-REV-005`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Requirement Specification (REQ-REV-003.5)

### 1.1 Problem Statement & Rationale
`FirstJubileeCelebrationDialog` abruptly presents the rating and email feedback action buttons beneath the milestone congratulation and supporter card without an inviting transitional sentence, causing conversational discontinuity.

### 1.2 Functional & Architectural Requirements
1. `FirstJubileeCelebrationDialog.kt` SHALL display `jubilee_feedback_prompt` as centered `MaterialTheme.typography.bodyMedium` text in `onSurfaceVariant` color directly preceding the review action `Button` and email feedback `OutlinedButton`.
2. The transitional string SHALL be defined across all 9 authentic locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) using informal 'Du' in German.
3. In accordance with Rule 57 and Section 4 of `docs/design_guidelines.md`, the string SHALL NOT contain decorative inline Unicode emojis.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Transitional Prompt Rendering)**:
  * *Given* `FirstJubileeCelebrationDialog` is displayed in Free or Pro mode,
  * *When* the dialog contents compose,
  * *Then* the transitional prompt `jubilee_feedback_prompt` appears immediately before the rating button.
* **Criterion 2 (9-Language Parity)**:
  * *Given* all 9 locale resource sets,
  * *When* `TranslationParityTest` executes,
  * *Then* `jubilee_feedback_prompt` exists in all 9 `strings.xml` files with 100% parity and no missing keys.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-REV-003` (*First-Jubilee Celebration Dialog & Multi-Channel Engagement*), targeting `FirstJubileeCelebrationDialog.kt` and `res/values*/strings.xml`.
* **Historical Origin & Commit Trace**: `ATT-2308` (`d5d7bde5`), `ATT-5399` (`ce6b99e3`), and `ATT-5401` (`3118792d`).
* **Root Reason for Existing Formulation**: Earlier iterations focused on bottom sheet dialog structure and Google Play Review fallback mechanisms, leaving the congratulatory message directly adjacent to action buttons without transitional guidance.
* **Preservation of Core Invariants**: The single-prompt invariant (`REQ-REV-004`), Free vs. Pro supporter tier gating (`!isProActive`), and review/feedback dispatch pathways remain completely preserved.

---

## 2. Test Specification (TST-REV-003, TST-REV-005)

### Test Case 1: `testFirstJubileeCelebrationDialog_sourceContractVerification` (`TST-REV-003.1`)
* **Scope**: Unit / Source Contract Test (`FirstJubileeCelebrationDialogTest.kt`)
* **Action**: Verify `FirstJubileeCelebrationDialog.kt` references `R.string.jubilee_feedback_prompt` and places it before `onRequestInAppReview`.
* **Expected Result**: Assertions pass.

### Test Case 2: 9-Locale Translation Parity & Emoji Prohibition (`TST-REV-005.1`)
* **Scope**: Localization Contract Test (`TranslationParityTest.kt`)
* **Action**: Verify `jubilee_feedback_prompt` is present in all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) without missing translations or decorative inline Unicode emojis.
* **Expected Result**: 100% parity across all 9 locales.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-REV-003.1` | Contract | `FirstJubileeCelebrationDialogTest.testFirstJubileeCelebrationDialog_sourceContractVerification` | `REQ-REV-003.5` | Specified |
| `TST-REV-005.1` | Localization | `TranslationParityTest` | `REQ-REV-003.5`, `REQ-UI-106` | Specified |
| `TST-REV-003.2` | Regression | `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.dialogs.FirstJubileeCelebrationDialogTest"` | `REQ-REV-003` | Specified |
