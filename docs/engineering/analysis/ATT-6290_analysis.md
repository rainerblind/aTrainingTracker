# Stage 1 Analysis: ATT-6290 - SupporterScreen (PioneerStatusBanner): Replace 'early ANT+ days' wording with neutral appreciation for tracking with an earlier version

**Ticket**: [ATT-6290](https://rainerblind.atlassian.net/browse/ATT-6290)  
**Sub-task**: [ATT-6324](https://rainerblind.atlassian.net/browse/ATT-6324) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.1`  
**Branch**: `feature/ATT-6290`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-09  

---

## 1. Problem Statement & Motivation

In `SupporterScreen.kt` (`PioneerStatusBanner` in `SupporterComponents.kt`), the Pioneer recognition banner displays `R.string.pioneer_thank_you_body`. Across all 9 locales, the copy specifically references "the early ANT+ days" (e.g. DE: *"Danke, dass du aTrainingTracker seit den frühen ANT+-Tagen unterstützt!..."*, EN: *"Thank you for supporting aTrainingTracker since the early ANT+ days!..."*).
Many historical athletes tracked workouts prior to V5.0.0 exclusively with Bluetooth LE (BTLE), GPS, or built-in phone sensors without ever using ANT+. Mentioning "ANT+" alienates non-ANT+ early adopters. A neutral, appreciative copy is required across all 9 locales prior to paywall activation in V5.0.0.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Root Cause**: `pioneer_thank_you_body` was authored in `ATT-5371` reflecting the app's historical subtitle ("ANT+ BTLE") rather than focusing on the user's early workout recording history.
* **Independent Peer Review & Verification**:
  * Confirmed in `SupporterComponents.kt` (lines 69–109): `PioneerStatusBanner` references `R.string.pioneer_thank_you_body`.
  * Audited all 9 `res/values*/strings.xml` files: `pioneer_thank_you_body` exists in all 9 locales and explicitly references "ANT+".
  * Audited test suite: `SupporterCopyContractTest.kt` verifies supporter screen copy but does not yet cover `pioneer_thank_you_body`. `TranslationParityTest.kt` verifies presence across locales.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Update `pioneer_thank_you_body` across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) to express appreciation for having recorded workouts with an earlier version of `aTrainingTracker`, completely removing "ANT+".
  * Extend `SupporterCopyContractTest.kt` to enforce that `pioneer_thank_you_body` contains zero references to "ANT+" and zero decorative emojis (ATT-5400).
  * Update living documentation shards `docs/requirements/req_ui_design_system.md` (`REQ-UI-502`) and `docs/tests/tst_ui_design_system.md` (`TST-UI-502`).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modification to Pioneer qualification logic (`WorkoutSummariesDatabaseManager.hasWorkoutBefore`, cutoff `2026-11-30 23:59:59`).
  * No alterations to banner visual layout, icons (`ic_supporter_emblem_pioneer`), or billing flows.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Amends `REQ-SET-085.3` / `REQ-UI-445.3` (`PioneerStatusBanner` presentation). Targets `res/values*/strings.xml` and `SupporterComponents.kt`.
* **Historical Origin & Commit Trace**: Commit `31da409a` (`ATT-5371`), which introduced dynamic Pioneer since-year badge formatting and extracted `pioneer_thank_you_body`.
* **Root Reason for Existing Formulation**: Early colloquial copy assumed early adopters were ANT+ enthusiasts due to the app's origin.
* **Preservation of Core Invariants**: Permanent lifetime Pro privileges (`isProActive() == true`), unlimited device pairing, dynamic joining year (`pioneerSinceYear`), full-color 36dp emblem, and 9-language parity with zero format specifier errors remain 100% preserved.

---

## 5. Architectural Strategy & High-Level Solution

* **String Resource Updates**: Replace `pioneer_thank_you_body` in all 9 `strings.xml` files with neutral appreciation:
  * **DE**: *"Danke, dass du bereits mit einer früheren Version von aTrainingTracker deine Trainings aufgezeichnet hast! Unbegrenzte Sensoren und alle zukünftigen Funktionen sind für dich dauerhaft freigeschaltet."*
  * **EN**: *"Thank you for recording your workouts with an earlier version of aTrainingTracker! Unlimited sensors and all future features are permanently unlocked for you."*
  * **ES/FR/IT/JA/NL/PL/PT**: Natural equivalent translations removing "ANT+".
* **Quality Assurance**: Extend `SupporterCopyContractTest.kt` to validate 9-locale parity, zero "ANT+", zero emojis, and zero format arguments.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in Pioneer eligibility, grandfathered Pro status, or dynamic year caching.
  2. 100% localization parity across all 9 supported locales without format specifier mismatches.
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW** — Purely copy and localization update in resource XML files with regression contract tests; zero procedural logic or schema changes.
