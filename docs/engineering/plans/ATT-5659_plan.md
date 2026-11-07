# Stage 3: Implementation Plan - ATT-5659: App development support copy without buzzwords

**Ticket**: [ATT-5659](https://rainerblind.atlassian.net/browse/ATT-5659)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5659`  
**Requirement Mapping**: `REQ-SET-086`, `REQ-REV-003` | **Test Mapping**: `TST-SET-086`, `TST-REV-003`, `TST-REV-005`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Description & Background
In `SupporterScreen.kt` and `FirstJubileeCelebrationDialog.kt`, support appeals previously relied on defensive marketing buzzwords (*"15+ Jahre Indie-Entwicklung"*, *"seit 2011"*, *"unabhängiges Fitnesstracking"*, *"dem Datenschutz verpflichtet"*). These strings must be replaced across all 9 supported locales with plain, appreciative copy inviting athletes to support ongoing app development, directly aligning with upcoming `ATT-5658` reorganization requirements.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-SET-086` (`REQ-SET-086.5` 9-Language Localization), `REQ-REV-003` (Clause 4, Appreciative Development Appeal)
* **Test Mapping**: `TST-SET-086` (Visual Copy), `TST-REV-003` (Jubilee Appeal Contract), `TST-REV-005` (9-Locale Parity)

---

## 3. System Invariants & Preserved Behavior
1. **Resource Key Stability**: Key names `supporter_screen_subtitle`, `supporter_active_tier_thank_you`, `supporter_core_invariant_body`, `jubilee_supporter_appeal_title`, and `jubilee_supporter_appeal_message` MUST NOT change.
2. **Zero Formatted Arguments**: All 5 string resources require 0 dynamic format specifiers.
3. **100% 9-Language Parity**: All 9 supported locales maintain complete translations without missing entries.
4. **Emoji Prohibition (`ATT-5400`)**: Zero decorative inline Unicode emojis in strings.
5. **No Buzzwords**: Complete elimination of *"15+ Jahre"*, *"Indie"*, *"seit 2011"*, and *"Datenschutz"* from support appeals.
6. **Zero Logic or Layout Mutations**: Compose layouts, billing plans, and jubilee trigger logic remain untouched.

---

## 4. UI Consistency & Architectural Strategy
* **Reference Screens**: `SupporterScreen.kt` and `FirstJubileeCelebrationDialog.kt`.
* **Reused Components**: Existing `AppModalBottomSheet`, `Card`, and `Text` typography.
* **Theme Tokens**: Standard typography and colors (`MaterialTheme.colorScheme.onTertiaryContainer`).
* **New One-Off Styles**: None. Pure string resource and test assertion updates.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update 5 String Keys Across All 9 Locales (`REQ-SET-086`, `REQ-REV-003`)
* **Files**: `app/src/main/res/values*/strings.xml` (9 files)
* **Changes**: Update `supporter_screen_subtitle`, `supporter_active_tier_thank_you`, `supporter_core_invariant_body`, `jubilee_supporter_appeal_title`, and `jubilee_supporter_appeal_message` per the Stage 2 translation matrix.
* **Harmonization with ATT-5658**: `jubilee_supporter_appeal_message` set to *"Außerdem kannst du die Weiterentwicklung von aTrainingTracker auch finanziell unterstützen:"* (EN: *"You can also financially support the ongoing development of aTrainingTracker:"*).

### Step 2: Contract Unit Test Creation (`TST-SET-086`, `TST-REV-003`, `TST-REV-005`)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/supporter/SupporterCopyContractTest.kt`
* **Changes**: Add contract assertions verifying that the 5 keys exist in all 9 locales, contain no emojis, contain zero forbidden buzzwords, and match appreciative copy in DE and EN.

### Step 3: Targeted Verification Suite (`TST-SET-086`, `TST-REV-005`)
* **Commands**:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.supporter.SupporterCopyContractTest" --tests "com.atrainingtracker.trainingtracker.ui.dialogs.FirstJubileeCelebrationDialogTest"`
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
  * `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted unit test suite and governance checks; verify with Gradle runner.
* **Rollback**: Clean git branch revert on `feature/ATT-5659` with zero impact on `sprint/2026-45.9`.
