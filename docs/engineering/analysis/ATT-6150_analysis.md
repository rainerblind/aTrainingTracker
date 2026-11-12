# Stage 1 Analysis: ATT-6150 - Record story points and sprint metrics every sprint

**Ticket**: [ATT-6150](https://rainerblind.atlassian.net/browse/ATT-6150)  
**Sub-task**: `[Analysis]` (Created on submission)  
**Parent Epic**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2 – Role Integrity, Independent QA & Configuration Management*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.3`  
**Branch**: `feature/ATT-6150`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation
Under ASPICE MAN.3 (Project Monitoring and Control) and GP 2.1 (Resource & Work Product Management), sprint velocity and process metrics must be consistently captured into configuration management. In `docs/engineering/metrics/process_metrics.jsonl`, only 7 sprint entries exist (versus 63 historical retros), and early records (sprints 369–371) recorded `completed_story_points: 0.0, total_story_points: 0.0`.
The goal of ATT-6150 (Remediation B-05) is to:
1. Enforce that every sprint records planned and completed story points (`customfield_10016`) into `docs/engineering/metrics/process_metrics.jsonl` during Ceremony 2 (`sprint_metrics.py`).
2. Implement governance verification in `tools/verify_requirement_governance.py` (`--check-metrics [SPRINT]`) and its Höchstmaß counterpart to verify historical metric persistence and assert that planned and completed story points are $> 0$.
3. Optimize subtask comment query resolution in `collect_sprint_metrics_for_id` to eliminate redundant serial network calls.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**:
  * Story points extraction in `tools/sprint_metrics.py` supports `customfield_10016`, but historical sprint collection was not systematically mandated at sprint close in earlier sprints, causing missing story point values (`0.0`).
  * `tools/verify_requirement_governance.py` validates gates (`--check-gates`) and living docs, but lacks automated verification for sprint metrics records (`--check-metrics`).
  * `collect_sprint_metrics_for_id` in `tools/jira_util.py` performs serial HTTP requests per subtask (`GET /rest/api/2/issue/{st_key}/comment`), leading to high latency on large sprints ($>400$ issues) when executing `sprint-metrics`.
* **Independent Hypothesis Verification**:
  * Audit of `docs/engineering/metrics/process_metrics.jsonl`: Sprints 404, 437, 438, 439 have story points populated ($>0$), while 369–371 have `0.0`.
  * Audit of `tools/sprint_metrics.py`: Confirms `total_sp` and `completed_sp` are calculated from `customfield_10016` / `customfield_10026`.
  * Audit of `tools/jira_util.py`: `fetch_all_sprint_issues` already fetches `comment` in fields. Subtasks returned as top-level issues contain comment payloads; pre-indexing them eliminates serial REST calls.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Add `--check-metrics [SPRINT]` flag and `verify_sprint_metrics(repo_root, sprint_name)` function to `tools/verify_requirement_governance.py` and `tools/hoechstmass/tools/verify_requirement_governance.py`.
  2. Optimize subtask comment indexing in `tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py` for high-speed metric calculation.
  3. Update `.agents/skills/sprint-planner/SKILL.md` Ceremony 2 to include `verify_requirement_governance.py --check-metrics`.
  4. Formulate formal requirement `REQ-PRO-060` and test specification `TST-PRO-060` in living docs.
  5. Add comprehensive unit tests in `tools/test_verify_requirement_governance.py` and `tools/test_sprint_metrics.py`.
* **Out-of-Scope Non-Goals**:
  * No runtime Android production modifications in `app/src/main/`.
  * No modification to Jira Cloud custom field schemas or external webhooks.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Net-new requirement `REQ-PRO-060` (*Sprint Metrics Persistence & Story Point Governance*). Targets `tools/sprint_metrics.py`, `tools/jira_util.py`, `tools/verify_requirement_governance.py`, and `docs/engineering/metrics/process_metrics.jsonl`.
* `Historical Origin & Commit Trace`: Introduced in Sprint 2026-46.3 via ATT-6150 under Epic ATT-6141 to satisfy ASPICE pre-assessment finding B-05.
* `Root Reason for Existing Formulation`: Sprints previously lacked CI gate enforcement for process metric recording, resulting in gaps and zero-value historical records.
* `Preservation of Core Invariants`: Zero changes to Android runtime code; pure Python 3 standard library; offline mock testing resilience preserved.

---

## 5. Architectural Strategy & High-Level Solution
1. **Governance Verifier (`--check-metrics`)**:
   Implement `verify_sprint_metrics()`: validates `process_metrics.jsonl` existence, valid JSONL schema, and positive planned/completed story points (`> 0`) for the target or latest sprint.
2. **Subtask Query Indexing Optimization**:
   Index subtask comments from `fetch_all_sprint_issues` in `collect_sprint_metrics_for_id`, avoiding per-subtask REST requests.
3. **Counterpart Parity & Living Docs**:
   Mirror all changes in `tools/hoechstmass/`; define `REQ-PRO-060` and `TST-PRO-060` in living documentation.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Pure Python standard library; zero production code impact; full backward compatibility with existing CLI commands.
* **Risk Rating**: **LOW** — Tooling, process documentation, and governance verifier enhancement.
