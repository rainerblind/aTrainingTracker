# Stage 2: Requirement & Test Specification - ATT-5911

**Ticket**: [ATT-5911](https://rainerblind.atlassian.net/browse/ATT-5911)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-4509](https://rainerblind.atlassian.net/browse/ATT-4509) (*Engineering Process, Developer Tooling & ASPICE Governance*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Requirement Mapping**: `REQ-PRO-040` (*Project-Wide Epic Lifecycle Governance, 'list-epics' / 'close-epic' CLI Commands*)  
**Test Spec ID**: `TST-PRO-040`  
**Branch**: `feature/ATT-5911`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Requirement Specification (REQ-PRO-040)

### 1.1 Problem Statement & Rationale
Agents currently lack CLI visibility into project Epics and cannot close completed Milestone Epics. While `REQ-PRO-016` enforces an inviolable Human Decision Gate blocking agents from completing standard in-sprint tickets, long-lived Milestone Epics need a governed closure mechanism with explicit human confirmation. `tools/jira_util.py` (and mirror `tools/hoechstmass/tools/jira_util.py`) must add `list-epics` and `close-epic` subcommands with a triple guardrail, alongside canonical Epic taxonomy updates across agent skills.

### 1.2 Functional & Architectural Requirements
* `REQ-PRO-040.1` (`list-epics`): Query all project Epics via JQL, aggregating Done and Open child tickets across Cloud (`parent = ...`) and Data Center (`cf[10014] = ...`), rendering a structured table.
* `REQ-PRO-040.2` (`close-epic`): Enforce a triple guardrail prior to closing an Epic to `Erledigt`:
  1. *Role Guard*: Restricted strictly to `--as coordinator` or `human` (rejects `agent1`, `agent2`).
  2. *Issue Type Guard*: Verifies issue is an `Epic` (rejects standard tickets to preserve Human Gate).
  3. *Completeness Guard*: Verifies 0 open child tickets (aborts with unfinished ticket keys if any remain).
* `REQ-PRO-040.3` (Canonical Epic Taxonomy): Update `.agents/skills/{brainstormer,sprint-planner,jira-workflow}/SKILL.md` (and hoechstmass mirrors) distinguishing Evergreen vs. Milestone Epics and empowering agents to create/close Epics only with human confirmation.
* `REQ-PRO-040.4` (Tooling DRY Parity & Tests): Maintain 100% logic synchronization with `tools/hoechstmass/` and add automated test suites in `tools/test_jira_epics.py` and `tools/hoechstmass/tools/test_hoechstmass_tools.py`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (List Epics)**: *Given* project Epics and child tickets, *When* `jira_util.py list-epics` runs, *Then* output reports each Epic with summary, status, and done/open child counts.
* **Criterion 2 (Triple Guardrail Enforcement)**: *Given* non-coordinator role, non-Epic key, or open child tickets, *When* `close-epic` executes, *Then* it aborts with exit code 1 and a descriptive error message.
* **Criterion 3 (Guarded Epic Transition)**: *Given* coordinator role, an Epic key, 0 open children, and human approval, *When* `close-epic` executes, *Then* the Epic transitions to `Erledigt`.

### 1.4 System Invariants
* Zero Android runtime changes in `app/src/main/`. Human Decision Gate on standard tickets (`Feature`, `Bug`, `Verbesserung`) remains 100% inviolable.

---

## 2. Test Specification (TST-PRO-040)

### Test Case 1: Epic Inspection & Aggregation (`TST-PRO-040.1`)
* **Scope**: Unit Test (`tools/test_jira_epics.py`)
* **Preconditions**: Mock JQL responses for Cloud and Data Center Epics and linked children.
* **Action**: Execute `list-epics` command handler.
* **Expected Result**: Done and open child tickets aggregated accurately; formatted table emitted.

### Test Case 2: Close Epic Triple Guardrail (`TST-PRO-040.2`)
* **Scope**: Unit Test (`tools/test_jira_epics.py`)
* **Preconditions**: Mock issues for role violations, non-Epic issues, open child tickets, and 0-open-child Epic.
* **Action**: Execute `close-epic` under each condition.
* **Expected Result**: Aborts with exit 1 for failed guards; transitions to `Erledigt` only when all 3 pass.

### Test Case 3: Hoechstmass Mirror Parity (`TST-PRO-040.3`)
* **Scope**: Integration Test (`tools/hoechstmass/tools/test_hoechstmass_tools.py`)
* **Action**: Assert parity of `list-epics` and `close-epic` implementation across tooling roots.
* **Expected Result**: 100% behavioral and structural equivalence.

### Test Case 4: Governance & Skill Taxonomy Audit (`TST-PRO-040.4`)
* **Scope**: Static Audit & Governance
* **Action**: Run `python3 tools/verify_requirement_governance.py` and audit skill markdown files.
* **Expected Result**: 100% parity across living doc tables; Evergreen vs. Milestone taxonomy documented.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Test Class / Procedure | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-040.1` | Unit | `test_jira_epics.py:test_list_epics` | `REQ-PRO-040.1` | Specified |
| `TST-PRO-040.2` | Unit | `test_jira_epics.py:test_close_epic_guardrails` | `REQ-PRO-040.2` | Specified |
| `TST-PRO-040.3` | Parity | `test_hoechstmass_tools.py:test_epic_commands` | `REQ-PRO-040.4` | Specified |
| `TST-PRO-040.4` | Audit | `verify_requirement_governance.py` | `REQ-PRO-040.3` | Specified |
