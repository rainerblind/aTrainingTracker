# Stage 1 Analysis: ATT-5661 - SwapOrUpgradeDialog & KeepTwoSensorsBottomSheet: Replace 'Free-Tier' / 'Pay what you want' wording with 'Basisversion' and 'Entwicklung unterstützen' button

**Ticket**: [ATT-5661](https://rainerblind.atlassian.net/browse/ATT-5661)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5661`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Statement & Motivation
In `SwapOrUpgradeDialog.kt` and `KeepTwoSensorsBottomSheet.kt`, dialog copy currently uses transactional SaaS phrasing (*"Im Free-Tier"*, *"Free-Plätze"*, *"im Free-Tarif"*, *"Pay what you want"*) and a hard paywall button label (*"Unbegrenzte Sensoren freischalten"*).
To align with an appreciative, open-source mindset, the copy must be modernized across all 9 supported locales:
* Use appreciative terminology: *"In der Basisversion"* / *"In the base version"* and *"2/2 Plätze"* / *"2/2 Slots"*.
* Frame unlocking sensors around supporting continuous development (*"... oder unterstütze die Weiterentwicklung der App und schalte damit unbegrenzt viele Sensoren frei."* / *"... or support the ongoing development of the app to unlock unlimited sensors."*).
* Update the secondary CTA button label to *"Entwicklung unterstützen"* / *"Support Development"*.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic RCA**:
  * Originating in `ATT-51` and carried through `ATT-5398` / `ATT-5470`, strings reflected early monetization terminology.
  * Five resource keys are affected: `swap_dialog_title`, `swap_dialog_message`, `swap_dialog_action_unlock`, `keep_two_sensors_action_unlock`, and `keep_two_sensors_message`.
* **Independent Peer Review & Call Site Audit (4-Augen-Prinzip)**:
  * `SwapOrUpgradeDialog.kt`: L64 (`swap_dialog_title`), L80 (`swap_dialog_message` taking `candidateDevice.deviceName`), L164 (`swap_dialog_action_unlock`).
  * `KeepTwoSensorsBottomSheet.kt`: L87 (`keep_two_sensors_action_unlock`), L101 (`keep_two_sensors_message`).
  * Verified call sites: Zero other production callers exist in `app/src/main/`.
  * Formatting: `swap_dialog_message` requires `%1$s` positional specifier across all 9 locales; all other 4 strings have zero format arguments.
  * Cross-Ticket Boundary: `ATT-5659` refines `SupporterScreen` & `FirstJubileeCelebrationDialog` (`jubilee_supporter_appeal_*`, `supporter_screen_subtitle`, etc.); `ATT-5661` is strictly isolated to the 5 keys above with zero merge conflicts.

---

## 3. User Scope Grounding (ATT-1250) & Side-Effect Audit (Rule 68)
* **In-Scope Goals**:
  * Update 5 string keys across all 9 locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
  * Preserve `%1$s` placeholder in `swap_dialog_message` across all 9 languages.
  * Ensure zero decorative emojis (`ATT-5400`).
  * Add unit/contract assertions for key presence and placeholder parity; verify `TranslationParityTest`.
  * Update living documentation (`docs/requirements/req_settings_profiles.md`, `docs/requirements/req_ui_design_system.md`, `docs/tests/tst_settings_profiles.md`, `docs/tests/tst_ui_design_system.md`).
* **Out-of-Scope Non-Goals**:
  * Zero mutations to Kotlin logic, Compose layouts, state flows, or quota limits (`MAX_FREE_PAIRED_DEVICES = 2`).
  * Zero edits to Supporter screen strings (scoped strictly to `ATT-5659`).
* **Complexity Rating**: **1 SP (Trivial)**.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-SET-084` (`REQ-SET-084.4`, `REQ-SET-084.8`) in `docs/requirements/req_settings_profiles.md` and `REQ-UI-149` in `docs/requirements/req_ui_design_system.md`.
* **Historical Origin & Commit Trace**: `ATT-51` (free quota & swap dialog), `ATT-5398` (`KeepTwoSensorsBottomSheet`), `ATT-5470` (Rule 73 stacked action buttons).
* **Root Reason for Existing Formulation**: Early phrasing adopted SaaS subscription conventions before moving toward community-supported voluntary contributions.
* **Preservation of Core Invariants**: Key names remain unchanged; 2-sensor quota enforcement unchanged; Rule 73 stacked button layout intact; SQLite sensor persistence unaffected.

---

## 5. Architectural Strategy & High-Level Solution
* Update 5 string keys across all 9 locale files in `app/src/main/res/values*/strings.xml`.
* Extend `SwapOrUpgradeDialogContractTest.kt` (or create a dedicated contract test) to verify string resource integrity and placeholder parity.
* Synchronize living requirements and test specifications.
* Validate with `TranslationParityTest` and `verify_requirement_governance.py`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Identical resource key names (`swap_dialog_title`, `swap_dialog_message`, `swap_dialog_action_unlock`, `keep_two_sensors_action_unlock`, `keep_two_sensors_message`).
  2. Single string placeholder `%1$s` preserved in `swap_dialog_message` across all 9 locales.
  3. No emojis (`ATT-5400`); zero logic or layout changes.
* **Risk Rating**: **LOW** (Resource string updates across 9 locales; zero schema or architectural risk).
