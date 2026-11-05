# Stage 3: Implementation Plan - ATT-5400: Remove Excessive Emojis from Gold and Diamond Garage Upgrade Nudge Texts

**Ticket**: [ATT-5400](https://rainerblind.atlassian.net/browse/ATT-5400)  
**Sub-task**: [ATT-5410](https://rainerblind.atlassian.net/browse/ATT-5410) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.6` | **Branch**: `feature/ATT-5400`  
**Requirement Mapping**: `REQ-SET-088.5` (*Humorous N+1 Dialog & Pre-Selected Navigation*)  
**Test Mapping**: `TST-SET-088.7` (*9-Locale Emoji Audit & Specifier Parity*), `TST-LOC-001` (*9-Language Parity*)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-04  

---

## 1. Problem Description & Background
User feedback from the Post-Sprint 2026-45.5 review (*"In dem Gold-Nudge und Diamond-Nudge sind mir zu viele Emojies drin."*) noted excessive inline Unicode emojis (`🚴‍♂️`, `💎`, `✨`, `😉`) in the Gold and Diamond N+1 garage dialog strings (`garage_detector_dialog_title_gold`, `garage_detector_dialog_title_diamond`, `garage_detector_dialog_message_gold`, `garage_detector_dialog_message_diamond`). Because `GarageUpgradeNudgeDialog.kt` already renders a dedicated 32.dp Material vector icon slot (`DirectionsBike` / `Diamond`), inline emojis produce visual redundancy and clutter.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-SET-088.5` (*Humorous N+1 Dialog & Pre-Selected Navigation*)
* **Test Mapping**: `TST-SET-088.7` (9-locale emoji-free and positional specifier audit), `TST-LOC-001` (`TranslationParityTest`), `TST-SET-088.REG` (monetization regression suite)

---

## 3. System Invariants & Preserved Behavior
1. **Positional Specifiers**: Exact indexed specifiers (`%1$d`, `%2$d`, `%3$d`, `%4$d`, `%5$s`) and substitution semantics remain 100% intact across all 9 locales.
2. **Copy Tone & Dynamic Fleet Metrics**: Witty cyclist/runner copy and dynamic fleet counts (bikes, shoes, sensors, power meters) remain preserved.
3. **Dialog Navigation & Gating**: `GarageUpgradeNudgeDialog` layout, buttons, actions, and navigation to `NavRoutes.SUPPORTER` are completely unaffected.
4. **Code Lock (Rule 4)**: No production code or string files modified until Gate 3 is approved.

---

## 4. Proposed Architectural Changes
### Component 1: Localized XML Resources (`app/src/main/res/values*/strings.xml`)
* Strip inline emojis (`🚴‍♂️`, `💎`, `✨`, `😉`) from the 4 string keys across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`), removing adjoining whitespace and keeping punctuation clean.

### Component 2: Design Guidelines (`docs/design_guidelines.md`)
* Add explicit rule in Sections 4 and 5.5 specifying that dialog and bottom sheet titles and body texts must rely on Material / vector header icons rather than inline Unicode emojis.

### Component 3: Localization & Contract Audit Test (`GarageUpgradeNudgeStringsAuditTest.kt`)
* Author `app/src/test/java/com/atrainingtracker/trainingtracker/monetization/GarageUpgradeNudgeStringsAuditTest.kt` to parse all 9 `strings.xml` files, asserting:
  1. All 4 `garage_detector_dialog_*` keys exist in every locale.
  2. Zero Unicode emoji code points (e.g. `\uD83C-\uDBFF\uDC00-\uDFFF`, `\u2728`, `\u2705`, `\uD83D`) in those keys.
  3. 100% presence of positional specifiers `%1$d`..`%5$s` in the message strings.

### UI Consistency (Rule 23)
* **Reference component**: `GarageUpgradeNudgeDialog.kt`
* **Reused components**: Material 3 `AlertDialog`, vector icons `DirectionsBike` and `Diamond`
* **Theme tokens**: Preserves `MaterialTheme.typography.titleLarge` and `bodyMedium`; `MaterialTheme.colorScheme.primary`
* **New one-off styles**: None

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Author Contract Test (`TST-SET-088.7`)
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/monetization/GarageUpgradeNudgeStringsAuditTest.kt`
* Write assertions for absence of emojis and preservation of positional specifiers across all 9 locales.

### Step 2: Strip Emojis Across All 9 Locales (`REQ-SET-088.5`)
* Files: `app/src/main/res/values*/strings.xml` (9 files)
* Remove `🚴‍♂️`, `💎`, `✨`, `😉` and redundant adjacent spaces.

### Step 3: Codify Rule in Design Guidelines
* File: `docs/design_guidelines.md` (Sections 4 & 5.5)
* Document vector icon preference over inline Unicode emojis in dialog copy.

### Step 4: Targeted Verification & Governance
* Run targeted tests:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.monetization.GarageUpgradeNudgeStringsAuditTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"
  python3 tools/verify_requirement_governance.py
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: `GarageUpgradeNudgeStringsAuditTest`, `TranslationParityTest`, and full monetization unit suite.
* **Rollback**: `git checkout feature/ATT-5400 -- .` cleanly reverts branch changes without affecting `develop`.
