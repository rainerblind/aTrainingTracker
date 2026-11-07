# Stage 1 Analysis: ATT-5658 - FirstJubileeCelebrationDialog: Neuordnung, Anzeige auch für Pioniere & bodenständiges Wording

**Ticket**: [ATT-5658](https://rainerblind.atlassian.net/browse/ATT-5658)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5658`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Statement & Motivation
During user review of ATT-5467 / ATT-5659, user feedback requested restructuring `FirstJubileeCelebrationDialog`:
1. **Grounded Milestone Copy (`jubilee_dialog_message`)**: Replace self-aggrandizing wording (*„Das ist ein beeindruckender Meilenstein.“*) with motivating, down-to-earth phrasing: *„Du hast %1$d %2$s-Einheiten mit aTrainingTracker absolviert – ein schöner Meilenstein! Da kommen sicherlich noch ein paar dazu.“*
2. **Reordering & Dedicated Transitions**: Restructure vertical flow (Top to Bottom):
   - *Section 1 (Top)*: Personal Feedback via Email (*„Wenn dir die App gefällt oder du Ideen hast, wie sie noch besser werden kann, würde ich mich sehr über dein Feedback freuen:“* + `jubilee_action_email_feedback`).
   - *Section 2 (Middle)*: Google Play Store Review (*„Gefällt dir aTrainingTracker? Dann freue ich mich insbesondere über eine positive Bewertung bei Google Play:“* + `jubilee_action_review`).
   - *Section 3 (Bottom)*: Financial Support Card (*„Außerdem kannst du die Weiterentwicklung von aTrainingTracker auch finanziell unterstützen:“* + `jubilee_action_upgrade_pro`).
3. **Pioneer Tier Support Visibility**: Display the financial support card for both `SupporterTier.FREE` AND `SupporterTier.PIONEER` (whenever the user has no active paid recurring subscription).
4. **9-Language Localization Parity**: Update and add strings across all 9 supported locales.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic RCA**:
  - Gating logic previously passed `isProActive: Boolean`. Since `SupporterTier.PIONEER.isPro == true`, Pioneers were treated as paid subscribers and the support card was hidden.
  - Previous layout placed the supporter card at the top, which felt commercially intrusive before relationship-building feedback and store review options.
* **Independent Peer Review & Call Site Audit (4-Augen-Prinzip)**:
  - `FirstJubileeCelebrationDialog.kt`: Accepts tier or explicit support flag (`supporterTier: SupporterTier` or `showFinancialSupport: Boolean = supporterTier in listOf(FREE, PIONEER)`).
  - Callers: `ATrainingTrackerApp.kt` (L737) and `SupporterDebugSection.kt` (L253) already have `proFeatureManager.effectiveTier: StateFlow<SupporterTier>`.
  - Tests: `FirstJubileeCelebrationDialogTest.kt` (L67 checks `if (!isProActive)`) must be updated to the new gating contract. `SupporterCopyContractTest.kt` asserts on `jubilee_supporter_appeal_*` which remain 100% identical and compatible.

---

## 3. User Scope Grounding (ATT-1250) & Side-Effect Audit (Rule 68)
* **In-Scope Goals**:
  - Reorder `FirstJubileeCelebrationDialog` sections: Message $\rightarrow$ Email $\rightarrow$ Play Store $\rightarrow$ Financial Support at bottom.
  - Gate Section 3 for both `FREE` and `PIONEER` tiers.
  - Update `jubilee_dialog_message` and add Section 1/2 lead-ins across all 9 locales (`values*/strings.xml`).
  - Update `FirstJubileeCelebrationDialogTest.kt` and `req_aftermath_reviews.md`.
* **Out-of-Scope Non-Goals**:
  - No changes to `ReviewManagerFactory` flow, fallback store intent, or email intent dispatch (`FirstJubileeActionHelper`).
  - No changes to single-prompt persistence (`REQ-REV-004`) or `SupporterScreen.kt`.
* **Complexity Rating**: **2 SP (Standard)**.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-REV-003` (Clauses 4 & 5) in `req_aftermath_reviews.md`.
* **Historical Origin & Commit Trace**: `ATT-2308` (initial dialog), `ATT-5467` (transitional feedback prompt), `ATT-5659` (anti-buzzword copy).
* **Root Reason for Existing Formulation**: Supporter card was placed at top to optimize monetization; Pioneers were categorized under `isProActive` feature flag.
* **Preservation of Core Invariants**: `REQ-REV-004` (lifetime single prompt), `REQ-REV-005` (debug preview trigger), and Rule 73 stacked button layout remain 100% preserved.

---

## 5. Architectural Strategy & High-Level Solution
* Composable parameter: `supporterTier: SupporterTier` (or `showFinancialSupport: Boolean`), showing Section 3 if `supporterTier == SupporterTier.FREE || supporterTier == SupporterTier.PIONEER`.
* Layout structure: Stacked Material 3 column inside `AppModalBottomSheet` following Rule 73:
  1. Milestone message (`jubilee_dialog_message`)
  2. Section 1: Lead-in `jubilee_email_feedback_prompt` + `OutlinedButton` (`jubilee_action_email_feedback`)
  3. Section 2: Lead-in `jubilee_review_prompt` + `Button` (`jubilee_action_review`)
  4. Section 3 (if eligible): `Card` with `jubilee_supporter_appeal_message` + `FilledTonalButton` (`jubilee_action_upgrade_pro`)
  5. Action slot: `TextButton` (`jubilee_action_dismiss`)
* All copy verified across 9 locales without decorative emojis (`ATT-5400`) and matching `SupporterCopyContractTest`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero decorative emojis (`ATT-5400`).
  2. 100% localization parity across all 9 locales.
  3. Single-prompt milestone trigger invariant maintained (`REQ-REV-004`).
* **Risk Rating**: **LOW** (UI layout reordering, resource string updates across 9 locales; zero schema or background service risk).
