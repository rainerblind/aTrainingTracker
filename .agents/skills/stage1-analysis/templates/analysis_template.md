# Stage 1 Analysis: [TICKET_KEY] - [SUMMARY]

**Ticket**: [[TICKET_KEY]](https://rainerblind.atlassian.net/browse/[TICKET_KEY])  
**Sub-task**: [[SUBTASK_KEY]](https://rainerblind.atlassian.net/browse/[SUBTASK_KEY]) (`[Analysis]`)  
**Parent Epic**: [[EPIC_KEY]](https://rainerblind.atlassian.net/browse/[EPIC_KEY]) (*[EPIC_NAME]*)  
**Target Release**: `[TARGET_RELEASE]`  
**Active Sprint**: `[ACTIVE_SPRINT]`  
**Branch**: `[feature|bugfix]/[TICKET_KEY]`  
**Author**: AI Agent 1 (Implementer)  
**Date**: [YYYY-MM-DD]  

---

## 1. Problem Statement & Motivation

<!-- Clear, concise description of the bug, requested improvement, or new feature. -->
<!-- Explain the current behavior versus expected behavior. -->

---

## 2. Root Cause Analysis (Forensic Investigation)

<!-- For bugs: Detailed technical explanation of failure mechanism, stacktraces, race conditions, or OS lifecycle quirks. -->
<!-- For features/improvements: Architectural investigation of current state and gap analysis. -->

---

## 3. User Scope Grounding (ATT-1250)

<!-- Explicit verification of what the user requested vs out-of-scope adjacent work. -->
* **In-Scope Goals**:
  * [Goal 1]
  * [Goal 2]
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * [Non-Goal 1]

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

<!-- Required if any existing requirement in docs/requirements.md is to be modified (REQ-PRO-022). -->
<!-- If no existing requirement is modified, state: "Net-new requirement only (REQ-XXX). No existing requirements modified." -->

* **Original Requirement ID & Target**: [e.g. REQ-XXX]
* **Historical Origin & Commit Trace**: [Commit hash, PR/Ticket reference]
* **Root Reason for Existing Formulation**: [Why was this constraint created?]
* **Preservation of Core Invariants**: [Why is it safe to alter now while preserving system stability?]

---

## 5. Architectural Strategy & High-Level Solution

<!-- High-level design approach, affected modules/classes, database schema impacts, thread models. -->

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing features and unit tests.
  2. Thread safety on database/sensor single-thread dispatchers.
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **[LOW | MEDIUM | HIGH]**
<!-- Justification of risk rating -->
