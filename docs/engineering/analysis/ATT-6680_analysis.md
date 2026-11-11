# Stage 1 Analysis: ATT-6680 - SupporterScreen (PioneerStatusBanner): Add transition sentence inviting voluntary financial support below the Pioneer unlock notice

**Ticket**: [ATT-6680](https://rainerblind.atlassian.net/browse/ATT-6680)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6680`  
**Author**: AI Agent 1 (Implementer - Pro)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
In `ATT-6290` (`REQ-UI-505`), `pioneer_thank_you_body` on `PioneerStatusBanner` (`SupporterScreen.kt`) was updated to thank Pioneer athletes and confirm that unlimited sensors and all future features are permanently unlocked.
However, because Supporter subscription tier cards (`Bronze`, `Silver`, `Gold`) appear immediately beneath `PioneerStatusBanner` on `SupporterScreen`, Pioneer users lack clear context explaining why subscription cards are presented when their app is already fully unlocked.

**Target**: Append a modest, appreciative transition sentence inviting voluntary financial support for ongoing development to `pioneer_thank_you_body` across all 9 locales (`en`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification**:
  * **Call-Site Audit**:
    * `SupporterComponents.kt`: Renders `stringResource(R.string.pioneer_thank_you_body)` inside `PioneerStatusBanner`.
    * `values*/strings.xml`: Currently ends with confirmation of permanent unlock without contextualizing the supporter tier cards below.
    * `SupporterCopyContractTest.kt`: Tests non-blank, zero format specifiers, zero emojis, absence of "ANT+", and exact matches for German and English.
  * **Hypothesis Validated**: Appending a respectful transition sentence bridges the visual gap between the unlock notice and the supporter cards below without marketing buzzwords or coercive phrasing.

---

## 3. Scope Grounding & Non-Goals
* **In-Scope Goals**:
  * Append the voluntary support transition sentence to `pioneer_thank_you_body` across all 9 locales (`en`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).
  * Update `SupporterCopyContractTest.kt` exact string matching assertions for German and English.
  * Verify absence of forbidden marketing buzzwords and emojis.
* **Out-of-Scope Non-Goals**:
  * Any logic changes to Pioneer entitlement detection (`PioneerStatusManager`).
  * Composable UI layout modifications in `SupporterScreen.kt` or `SupporterComponents.kt`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-505` (*Supporter Screen Pioneer Thank You Banner Copy Parity*, `ATT-6290`). Refined by **`REQ-UI-516`**.
* **Historical Origin & Commit Trace**: Commit `69e96e70` (`ATT-6290`).
* **Root Reason for Existing Formulation**: Prior update removed legacy "ANT+" references and thanked early users, but left the visual coexistence of tier cards unexplained.
* **Preservation of Core Invariants**: Pioneer permanent unlock remains 100% unconditional; financial contributions remain strictly voluntary; zero inline emojis (`ATT-5400`) and zero marketing buzzwords.

---

## 5. System Invariants & Risk Assessment
* **Core Invariants**:
  * Pioneer status must continue to unlock 100% of features and unlimited sensors permanently.
  * 9-language translation parity MUST be strictly preserved.
* **Risk Rating**: **LOW** (pure string resource and unit test refinement, zero behavioral or architectural changes).
