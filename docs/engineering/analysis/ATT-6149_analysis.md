# Stage 1 Analysis: ATT-6149 - Export Jira gate records into repository at sprint end

**Ticket**: [ATT-6149](https://rainerblind.atlassian.net/browse/ATT-6149)  
**Sub-task**: `[Analysis]` (Created on submission)  
**Parent Epic**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2 – Role Integrity, Independent QA & Configuration Management*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.3`  
**Branch**: `feature/ATT-6149`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation
Under ASPICE SUP.1 (Quality Assurance & Independence) and GP 2.2 (Evidence of Process Work Products), gate approvals, verdicts, and closure decisions currently exist only inside Jira Cloud (Pre-Assessment Finding B-04). An external assessor without Jira tenant access cannot verify QA independence, gate compliance, or review trails.  
The goal is to provide an automated sprint-end command (`export-gates`) in `tools/jira_util.py` that dumps all Gate 1–5 and Stage 6 verdicts, evaluators, roles, models, and timestamps into `docs/engineering/gates/<sprint>.md` (AC-1), and enforce in `tools/verify_requirement_governance.py` that post-sprint verification fails if the sprint gate record is missing (AC-2).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**:
  * `tools/jira_util.py` provides composite commands for stage submission and gate verdicts, but lacks an export mechanism to serialize verdicts into git version control.
  * `ALLOWED_ENGINEERING_ENTRIES` in `tools/verify_requirement_governance.py` and `tools/hoechstmass/hoechstmass_config.json` lacks `"gates"`, making any gate export folder fail directory structure validation.
  * `tools/verify_requirement_governance.py` does not currently verify presence of gate records upon sprint completion.
* **Independent Codebase Verification**:
  * Audit of `tools/sprint_metrics.py`: Confirms `parse_sprint_gate_metrics` already traverses sprint parent issues, subtasks, and comments via Jira REST API, proving all necessary gate verdict metadata is queryable.
  * Audit of `docs/engineering/`: Contains `retros/` and `metrics/`, but zero gate record exports (`gates/` is missing).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Add `export-gates [--sprint <ID|NAME>] [--out <FILE>]` command to `tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py`.
  2. Format output as structured markdown in `docs/engineering/gates/<sprint>.md` with executive summary and granular audit log (verdict, role, actor, timestamp, comment excerpt).
  3. Add `"gates"` to `ALLOWED_ENGINEERING_ENTRIES` in `tools/verify_requirement_governance.py` and `tools/hoechstmass/hoechstmass_config.json`.
  4. Implement `verify_gate_records()` in `tools/verify_requirement_governance.py` (`--check-gates [SPRINT]`) to fail if the export file is absent (AC-2).
  5. Update `sprint-planner/SKILL.md` Ceremony 2 instructions to include `export-gates`.
  6. Define net-new requirement `REQ-PRO-059` and test spec `TST-PRO-059`.
  7. Add unit test coverage in `tools/test_jira_datacenter.py` and `tools/test_jira_accounts.py`.
* **Out-of-Scope Non-Goals**:
  * Zero modifications to Android production runtime code in `app/src/main/`.
  * No modification to Jira Cloud permissions or webhook architecture.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Net-new requirement `REQ-PRO-059` (*Repository Export of Sprint Gate Records & Independent QA Evidence*). Targets `tools/jira_util.py`, `tools/verify_requirement_governance.py`, and `docs/engineering/gates/`.
* `Historical Origin & Commit Trace`: Introduced in Sprint 2026-46.3 via ATT-6149 under Epic ATT-6141 to resolve ASPICE pre-assessment finding B-04.
* `Root Reason for Existing Formulation`: Gate verdicts previously existed only inside Jira Cloud REST API, lacking in-repo configuration management and QA evidence.
* `Preservation of Core Invariants`: Zero modifications to Android production runtime code in `app/src/main/`; pure Python standard library retained; offline/mock resilience preserved.

---

## 5. Architectural Strategy & High-Level Solution
1. **Command Implementation (`export-gates`)**:
   Query active/specified sprint issues; extract Gate 1–5 subtask comments and Stage 6 parent comments; format executive table and detailed verdict logs; write to `docs/engineering/gates/<sprint>.md`.
2. **Directory & Governance Validation**:
   Register `"gates"` in `ALLOWED_ENGINEERING_ENTRIES`; implement `verify_gate_records(sprint_name, repo_root)` in `tools/verify_requirement_governance.py`.
3. **Höchstmaß Synchronization**:
   Mirror command in `tools/hoechstmass/tools/jira_util.py` and add `"gates"` to `hoechstmass_config.json`.
4. **Ceremony 2 Integration**:
   Document execution of `python3 tools/jira_util.py export-gates` during sprint review in `sprint-planner/SKILL.md`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Offline/mock resilience in unit tests; zero regression on existing CLI commands; pure Python 3 standard library; zero production code impact.
* **Risk Rating**: **LOW** — Localized tooling and documentation enhancement.
