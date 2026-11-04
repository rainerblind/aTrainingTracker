# Stage 1 Analysis: ATT-5400 - Remove Excessive Emojis from Gold and Diamond Garage Upgrade Nudge Texts Across All 9 Locales

**Ticket**: [ATT-5400](https://rainerblind.atlassian.net/browse/ATT-5400)  
**Sub-task**: [ATT-5408](https://rainerblind.atlassian.net/browse/ATT-5408) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.6` | **Branch**: `feature/ATT-5400`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-04  

---

## 1. Problem Statement & Motivation
Post-Sprint 2026-45.5 review user feedback (*"In dem Gold-Nudge und Diamond-Nudge sind mir zu viele Emojies drin."*) identified visual clutter in the N+1 Garage Upgrade dialogs. Currently, Gold and Diamond title and message strings contain multiple inline Unicode emojis (`🚴‍♂️`, `💎`, `✨`, `😉`), creating visual distraction and redundancy alongside the dedicated 32.dp Material vector header icon (`DirectionsBike` / `Diamond`) rendered by `GarageUpgradeNudgeDialog`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Excessive Emoji Clutter RCA**: During initial copywriting in `ATT-5299`, playful Unicode emojis were added directly to `garage_detector_dialog_title_gold`, `garage_detector_dialog_title_diamond`, `garage_detector_dialog_message_gold`, and `garage_detector_dialog_message_diamond` across all 9 locales.
* **Layout Redundancy**: `GarageUpgradeNudgeDialog.kt:94-101` explicitly renders a prominent top-centered vector icon slot. Having emojis in the title and body text duplicates iconography and violates the clean Material 3 design baseline.
* **Independent Peer Review & Verification**:
  * Audited all 9 `strings.xml` files: confirmed `🚴‍♂️` in Gold titles, `💎` in Diamond titles, `🚴‍♂️✨` in Gold messages, `💎✨` in Diamond messages, and trailing `😉` in German copy.
  * Verified exact format specifier indices (`%1$d`, `%2$d`, `%3$d`, `%4$d`, `%5$s`) across all 9 locales: stripping emojis leaves parameter order and specifier parity completely intact.
  * Verified `GarageUpgradeNudgeDialog.kt` and its invocation call sites (`EquipmentTabsScreen.kt:256`, `DevicesTabbedScreen.kt:659`, `SupporterDebugSection.kt:292,306`): string consumers expect standard localized text without formatting anomalies.
  * Verified `docs/design_guidelines.md`: Sections 4 and 5.5 lack an explicit rule prohibiting decorative emoji clutter in localized dialog text.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Strip inline emojis (`🚴‍♂️`, `💎`, `✨`, `😉`) from `garage_detector_dialog_*` title and message strings across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  * Preserve good-humored N+1 copy and strict indexed positional format specifiers (`%1$d`..`%5$s`).
  * Codify in `docs/design_guidelines.md` (Section 4 & Section 5.5) the standard requiring UI dialogs to rely on vector icons rather than inline Unicode emojis.
  * Add automated verification ensuring zero emojis in `garage_detector_dialog_*` strings.
* **Out-of-Scope Non-Goals**:
  * No modifications to qualification thresholds, monotonicity gating, or DataStore flags (`REQ-SET-088.1`..`REQ-SET-088.4`).
  * No layout or functional changes to `GarageUpgradeNudgeDialog` or navigation targets (`NavRoutes.SUPPORTER`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-SET-088.5` (*Humorous N+1 Dialog & Pre-Selected Navigation*), targeting `res/values*/strings.xml` and `GarageUpgradeNudgeDialog.kt`.
* **Historical Origin & Commit Trace**: Commit `3b4e0a20` (`ATT-5299`, Sprint `2026-45.4`).
* **Root Reason for Existing Formulation**: Emojis were informally introduced during initial copy drafting to emphasize playful tone before the vector header icon was added.
* **Preservation of Core Invariants**: Stripping emojis retains the witty cyclist/runner phrasing, dynamic fleet metrics, and vector header icon while aligning with the app's professional Material 3 visual standard.

---

## 5. Architectural Strategy & High-Level Solution
* **Localization Cleanup**: Clean all 4 `garage_detector_dialog_*` string resources across all 9 `strings.xml` files, removing emoji characters and adjacent redundant whitespace.
* **Design Standards Codification**: Update `docs/design_guidelines.md` (Sections 4 & 5.5) to formally proscribe inline Unicode emojis in dialog titles/messages when vector header icons are present.
* **Automated Audit**: Add contract/localization assertions in test suite to ensure `garage_detector_dialog_*` remain emoji-free across all 9 locales.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. 100% 9-language translation parity and positional format specifier integrity (`%1$d`..`%5$s`) strictly preserved.
  2. Dialog layout, dismissal, and navigation contracts remain 100% backward compatible.
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW** (Text resource cleanup and documentation update; zero logic or database risk).
