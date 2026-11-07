# Stage 1 Analysis: ATT-5659 - SupporterScreen & Dialogs: App development support copy without buzzwords

**Ticket**: [ATT-5659](https://rainerblind.atlassian.net/browse/ATT-5659)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5659`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Statement & Motivation
During user review of ATT-5467, feedback noted that marketing buzzwords in financial support copy (*„15+ Jahre Indie-Entwicklung“*, *„seit 2011“*, *„unabhängiges Fitnesstracking“*, *„dem Datenschutz verpflichtet“*) felt overly verbose and defensive.
Instead, user-facing copy on `SupporterScreen` and `FirstJubileeCelebrationDialog` must be simple, appreciative, and directly ask for support of ongoing app development (*Weiterentwicklung unterstützen*), aligned with the upcoming `ATT-5658` reorganization across all 9 supported locales.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic RCA**:
  * Originating in `ATT-51` and `ATT-2308`, support strings reflected early attempts to justify subscription tiers with historical pedigree.
  * Five string keys contain these phrases: `supporter_screen_subtitle`, `supporter_active_tier_thank_you`, `supporter_core_invariant_body`, `jubilee_supporter_appeal_title`, and `jubilee_supporter_appeal_message`.
* **Independent Peer Review & Call Site Audit (4-Augen-Prinzip)**:
  * `SupporterScreen.kt`: L114 (`supporter_screen_subtitle`), L178 (`supporter_core_invariant_body`).
  * `SupporterComponents.kt`: L148 (`supporter_active_tier_thank_you`).
  * `FirstJubileeCelebrationDialog.kt`: L119 (`jubilee_supporter_appeal_title`), L125 (`jubilee_supporter_appeal_message`).
  * Verification across strings files: Confirmed that no other user-facing strings contain "15+ Jahre", "Indie", or "seit 2011". All 5 keys have zero format arguments across all 9 languages.
  * Harmonization with `ATT-5658`: `jubilee_supporter_appeal_message` will be formulated as *"Außerdem kannst du die Weiterentwicklung von aTrainingTracker auch finanziell unterstützen:"* (EN: *"You can also financially support the ongoing development of aTrainingTracker:"*), matching ATT-5658 exactly.

---

## 3. User Scope Grounding (ATT-1250) & Side-Effect Audit (Rule 68)
* **In-Scope Goals**:
  * Update 5 string resource keys across all 9 locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
  * Ensure zero inline decorative emojis (`ATT-5400`).
  * Extend contract tests (`SupporterScreenContractTest` or `FirstJubileeCelebrationDialogTest`) to assert string presence and absence of forbidden buzzwords.
  * Synchronize living documentation (`REQ-SET-086`, `REQ-REV-003`).
* **Out-of-Scope Non-Goals**:
  * Zero mutations to Compose layout hierarchy or button actions (layout reorganization of jubilee dialog is strictly reserved for `ATT-5658`).
  * Zero changes to billing client lifecycle, tier overrides, or subscription products.
* **Complexity Rating**: **1 SP (Trivial)**.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-SET-086` (`REQ-SET-086.5`) in `req_settings_profiles.md` and `REQ-REV-003` in `req_aftermath_reviews.md`.
* **Historical Origin & Commit Trace**: `ATT-51` (initial supporter model), `ATT-2308` (first jubilee dialog), `ATT-5371` (`100%` percent sign fix).
* **Root Reason for Existing Formulation**: Historical marketing copy highlighted developer pedigree and privacy commitments to differentiate from corporate trackers.
* **Preservation of Core Invariants**: 100% free core invariant body intact; tier cards, prices, and Google Play billing mechanisms 100% preserved.

---

## 5. Architectural Strategy & High-Level Solution
* Update the 5 string keys in all 9 locale directories to simple, appreciative development-support phrasing.
* Add contract assertions verifying that strings exist in all 9 locales, contain no emojis (`ATT-5400`), and contain none of the forbidden legacy buzzwords.
* Validate with `TranslationParityTest` and `verify_requirement_governance.py`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Identical resource key identifiers maintained.
  2. Zero format specifiers required across all 5 strings.
  3. No emojis (`ATT-5400`); zero logic, layout, or billing changes.
* **Risk Rating**: **LOW** (Resource string refinement across 9 locales; zero schema or runtime risk).
