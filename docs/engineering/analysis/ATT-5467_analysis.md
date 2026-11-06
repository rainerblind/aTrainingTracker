# Stage 1 Analysis: ATT-5467 - FirstJubileeCelebrationDialog: Add Transitional Prompt Text Before Google Play Rating and Email Feedback Buttons

**Ticket**: [ATT-5467](https://rainerblind.atlassian.net/browse/ATT-5467)  
**Parent Epic**: Standalone Ticket (None)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5467`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Statement & Motivation
During Sprint 2026-45.6 review of ATT-5401, the user noticed that `FirstJubileeCelebrationDialog` abruptly displays the two feedback action buttons (*'Bei Google Play bewerten'* and *'Feedback per E-Mail an Rainer'*) directly beneath the milestone congratulation / supporter card without any transitional or inviting introductory copy:
> *"Das funktioniert jetzt. Allerdings gibt es keinen überleitenden Text von dem Glückwunsch zum Feedback via Google Play Bewertung oder E-Mail."*

The missing transitional text breaks conversational flow and emotional tone by presenting immediate rating/email demands without explaining how user feedback actively supports ongoing indie app development.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Missing Conversational Bridge**: In `FirstJubileeCelebrationDialog.kt:98-172`, `jubilee_dialog_message` only celebrates the athletic milestone (*"Du hast %1$d %2$s-Einheiten mit aTrainingTracker absolviert! Das ist ein beeindruckender Meilenstein."*). Directly following (or under the optional supporter card), `Button` (*"Bei Google Play bewerten"*) and `OutlinedButton` (*"Feedback per E-Mail an Rainer"*) appear without introductory context.
* **Independent Peer Review & Verification (Rules 69 & 70, ATT-5431)**:
  * Audited `FirstJubileeCelebrationDialog.kt`: Confirmed absence of intermediate prompt text between body/card and action buttons.
  * Audited Supported Locales (Rule 68 / ATT-5431 Code-Grounding): The ticket description cited SV (Swedish), but codebase inspection (`TranslationParityTest.kt:39`, `res/values-*`) verifies that the 9 actual supported locales are: default (EN), DE, ES, FR, IT, JA (Japanese), NL, PL, and PT. Swedish is not a project locale.
  * Audited German Addressing: Informal 'Du' must be used across all German strings pursuant to project tone guidelines.

---

## 3. User Scope Grounding (ATT-1250) & Side-Effect Audit (Rule 68)
* **In-Scope Goals**:
  * Introduce string resource `jubilee_feedback_prompt` across all 9 authentic locales (`values*/strings.xml`).
  * Render `jubilee_feedback_prompt` as centered `MaterialTheme.typography.bodyMedium` text directly preceding the rating and email feedback buttons in `FirstJubileeCelebrationDialog.kt`.
  * Update `FirstJubileeCelebrationDialogTest.kt` source contract assertions and verify `TranslationParityTest`.
* **Out-of-Scope Non-Goals**:
  * No changes to In-App Review launch logic, fallback handling (`FirstJubileeActionHelper`), or email intent parameters (`REQ-REV-003`).
  * No changes to milestone detection (`MilestoneAutoNamingManager.kt`) or single-prompt persistence (`REQ-REV-004`).
* **Complexity Rating**: **1 SP (Trivial)** (Single composable copy addition + 9-locale string parity).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: `REQ-REV-003` (*First-Jubilee Celebration Dialog & Multi-Channel Engagement*), targeting `FirstJubileeCelebrationDialog.kt` and `res/values*/strings.xml`.
* **Historical Origin & Commit Trace**: `ATT-2308` (`d5d7bde5`, initial jubilee dialog), `ATT-5399` (`ce6b99e3`, bottom sheet migration), and `ATT-5401` (`3118792d`, visible review fallback).
* **Root Reason for Existing Formulation**: Earlier tickets focused on bottom sheet layout and review manager fallback, leaving milestone congratulation immediately adjacent to action buttons.
* **Preservation of Core Invariants**: The single-prompt invariant (`REQ-REV-004`), Free vs. Pro supporter card gating (`!isProActive`), and review/email callback triggers remain 100% intact.

---

## 5. Architectural Strategy & High-Level Solution
* **Composable Layout**: In `FirstJubileeCelebrationDialog.kt`, insert `Text(text = stringResource(R.string.jubilee_feedback_prompt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)` immediately before the `Button(onClick = onRequestInAppReview)` block.
* **Localization Parity**: Add `jubilee_feedback_prompt` to `values/strings.xml` and the 8 localized `strings.xml` files (`de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).
* **Cross-Caller Impact (Rule 68)**: Callers in `ATrainingTrackerApp.kt` and `SupporterDebugSection.kt` pass the same callback closures; no signature mutations required.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. 9-language translation parity verified via `TranslationParityTest`.
  2. Non-intrusive single-prompt lifetime invariant (`SettingsDataStore.hasShownFirstJubileePrompt`) preserved.
* **Risk Rating**: **LOW** (Declarative UI copy and localization addition; zero business logic or concurrency risk).
