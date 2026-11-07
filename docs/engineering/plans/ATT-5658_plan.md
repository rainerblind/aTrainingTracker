# Stage 3: Implementation Plan - ATT-5658: FirstJubileeCelebrationDialog Reordering & Grounded Copy

**Ticket**: [ATT-5658](https://rainerblind.atlassian.net/browse/ATT-5658)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5658`  
**Requirement Mapping**: `REQ-REV-003`, `REQ-REV-004`, `REQ-REV-005` | **Test Mapping**: `TST-REV-003`, `TST-REV-005`, `TST-REV-006`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Description & Background
User review of ATT-5467 requested restructuring `FirstJubileeCelebrationDialog`:
1. Grounded milestone copy (`jubilee_dialog_message`) celebrating achievements without self-aggrandizing wording.
2. Vertical layout reordered top-to-bottom: Section 1 (Personal Email Feedback) $\rightarrow$ Section 2 (Google Play Review) $\rightarrow$ Section 3 (Financial Support Card at the bottom).
3. Section 3 visibility extended to both `SupporterTier.FREE` AND `SupporterTier.PIONEER` (whenever no paid subscription is active).
4. 9-language localization parity across all new and updated strings.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-REV-003` (Clauses 2, 3, 4, 5: Grounded wording, vertical reordering, Pioneer visibility, 9-language parity), `REQ-REV-004` (Single-prompt lifetime invariant), `REQ-REV-005` (Debug preview).
* **Test Mapping**: `TST-REV-003` (Reordering & Pioneer Gating Contract), `TST-REV-005` (9-Locale Translation Parity), `TST-REV-006` (Preview Trigger Contract).

---

## 3. System Invariants & Preserved Behavior
1. **Lifetime Invariant (`REQ-REV-004`)**: `has_shown_first_jubilee_prompt` persisted on first action; non-intrusive on subsequent jubilees.
2. **Review & Email Helpers**: `FirstJubileeActionHelper.launchInAppReviewOrStore` (with `< 300ms` fallback) and `launchFeedbackEmail` remain 100% untouched.
3. **SupporterCopy Compatibility**: Preserves exact strings and keys asserted by `SupporterCopyContractTest` (`jubilee_supporter_appeal_*`).
4. **Emoji Prohibition (`ATT-5400`)**: Zero decorative inline Unicode emojis.
5. **Rule 73 Button Layout**: Stacked full-width buttons inside `AppModalBottomSheet` content; single dismiss `TextButton` in `actions`.

---

## 4. UI Consistency & Architectural Strategy
* **Reference Screen**: `FirstJubileeCelebrationDialog.kt`, `SwapOrUpgradeDialog.kt`.
* **Reused Components**: `AppModalBottomSheet`, `Card`, `Button`, `OutlinedButton`, `FilledTonalButton`, `TextButton`.
* **Theme Tokens**: `MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)`, `bodyMedium`, `bodySmall`, `onSurfaceVariant`.
* **New One-Off Styles**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Strings Across All 9 Locales (`REQ-REV-003`, `TST-REV-005`)
* **Files**: `app/src/main/res/values*/strings.xml` (9 locales)
* **Changes**: Update `jubilee_dialog_message` with `%1$d %2$s`. Add `jubilee_email_feedback_prompt` (Section 1) and `jubilee_review_prompt` (Section 2). Preserve existing keys.

### Step 2: Refactor Dialog Hierarchy & Gating (`REQ-REV-003`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/dialogs/FirstJubileeCelebrationDialog.kt`
* **Changes**: Accept `supporterTier: SupporterTier`. Compute `showFinancialSupport = supporterTier == SupporterTier.FREE || supporterTier == SupporterTier.PIONEER`. Reorder column: Message $\rightarrow$ Email Feedback $\rightarrow$ Play Store Review $\rightarrow$ Financial Support Card at bottom.

### Step 3: Update Call Sites in App & Settings (`REQ-REV-003`, `REQ-REV-005`)
* **Files**: `ATrainingTrackerApp.kt` (L737), `SupporterDebugSection.kt` (L253)
* **Changes**: Pass `supporterTier = effectiveTier`.

### Step 4: Update Contract & Unit Tests (`TST-REV-003`, `TST-REV-005`, `TST-REV-006`)
* **File**: `FirstJubileeCelebrationDialogTest.kt`
* **Changes**: Update contract checks to verify new tier gating (`FREE` + `PIONEER`), new prompt resources, and vertical reordering.

### Step 5: Verification Suite Execution (`TST-REV-003`, `TST-REV-005`)
* **Commands**:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.dialogs.FirstJubileeCelebrationDialogTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.supporter.SupporterCopyContractTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
  * `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted unit/contract test suites and ensure governance validation passes with 0 errors.
* **Rollback**: Clean git restore / branch reset on `feature/ATT-5658` prior to squash merge onto `sprint/2026-45.9`.
