# Stage 1 Analysis: [TICKET_KEY] - [SUMMARY]

**Ticket**: [[TICKET_KEY]](https://issue-tracker/[TICKET_KEY])  
**Sub-task**: [[SUBTASK_KEY]](https://issue-tracker/[SUBTASK_KEY]) (`[Analysis]`)  
**Parent Epic**: [[EPIC_KEY]](https://issue-tracker/[EPIC_KEY]) (*[EPIC_NAME]*)  
**Target Release**: `[TARGET_RELEASE]`  
**Active Sprint**: `[ACTIVE_SPRINT]`  
**Branch**: `[feature|bugfix]/[TICKET_KEY]`  
**Author**: AI Agent 1 (Implementer)  
**Date**: [YYYY-MM-DD]  

---

## 1. Problem Statement & Motivation
<!-- Clear description of the bug, requested improvement, or new feature. -->
<!-- Explain current behavior versus expected behavior. -->

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
<!-- For defects: Technical failure mechanism, logs, race conditions, or lifecycle quirks. -->
<!-- For features: Architectural investigation and gap analysis. -->
* **Independent Peer Review & Verification**:
  * [Verification of ticket hypothesis against live codebase, checking cited files/lines and blind spots]

---

## 3. Scope Grounding & Non-Goals
* **In-Scope Goals**:
  * [Goal 1]
  * [Goal 2]
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * [Non-Goal 1]

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
<!-- Required if any existing requirement or core rule is modified. If net-new, state "Net-new requirement only." -->
* **Original Requirement ID & Target**: [e.g. REQ-XXX]
* **Historical Origin & Commit Trace**: [Commit hash, PR/Ticket reference]
* **Root Reason for Existing Formulation**: [Why was this constraint created?]
* **Preservation of Core Invariants**: [Why is it safe to alter now while preserving system stability?]

---

## 5. Change-Request Impact Analysis (SUP.10)
<!-- Required for explicit impact analysis under ASPICE SUP.10 (REQ-PRO-064). -->
* **Affected Requirements**: [REQ-XXX or Net-new requirement(s)]
* **Affected Architecture Elements**: [Classes, modules, database schemas, CLI tools, scripts]
* **Verification Impact & Test Surface**: [TST-XXX, targeted test suites, regression test scope]
* **Risk Assessment & Invariants**: [Risks, mitigations, core system invariants preserved]

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: [Existing behavior, APIs, or data schemas that MUST NOT change]
* **Risk Rating**: [LOW | MEDIUM | HIGH] with technical rationale.
