# Stage 1 Analysis: ATT-7559 - Make process metrics credible: count gate rejections and apply bug severity rubric

**Ticket**: [ATT-7559](https://rainerblind.atlassian.net/browse/ATT-7559)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*ASPICE Governance & Process Stability*)  
**Target Release**: `V5.1.0`  
**Active Sprint**: `2026-46.6`  
**Branch**: `feat/ATT-7559`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Statement & Motivation

ASPICE evaluation finding F2 revealed that quantitative process metrics recorded in `docs/engineering/metrics/process_metrics.jsonl` lack credibility as process evidence:
1. **Unrealistic First-Time Pass Rates**: Gates 1–5 recorded 100% first-time pass rates across hundreds of subtasks (e.g. 105/105, 104/104) without a single recorded revision verdict, contradicting the reality of 22 Stage 6 bounces at Gate 6 in Sprint 441.
2. **Deflation of Bug Severity**: 100% of bugs were categorized as "Minor" (0 Major/Critical) in recorded metrics, even though Crashlytics crashes, ANRs, and real hardware defect fixes were resolved.
3. **Unverified Fallback Inflation**: 24 subtasks without any recorded verdict comments were silently counted as "passed first time" purely because they reached status `Erledigt`.
4. **Omission of Labels & Description in API Fetch**: `collect_sprint_metrics_for_id` omitted `labels` and `description` from Jira issue queries, blinding severity parser logic and forcing fallback to Jira priority (`Medium -> Minor`).
5. **Jira Cloud ADF Body Vulnerability**: Comment body parser crashes when comment bodies are serialized in Atlassian Document Format (ADF dicts).
6. **Disjoint Escape Metrics**: Gate N escape rate $p_g$ did not account for downstream defects escaping to Stage 6.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

Forensic investigation of `tools/sprint_metrics.py`, `tools/jira_util.py`, and Jira Cloud sprint 441 confirmed:
1. **API Field Blindness**: In `tools/jira_util.py:2234` and `tools/hoechstmass/tools/jira_util.py:1029`, the Jira REST URL requested `summary,status,issuetype,priority,subtasks,comment,{STORY_POINTS_FIELD}` without `labels,description`. As a result, `issue["fields"].get("labels")` was missing, and bug severity parsing defaulted to `priority.name == "Medium" -> "Minor"`. With `labels,description` fetched, Sprint 441 properly reflects 1 Critical (`ATT-7319`) and 5 Major (`ATT-7311`, `ATT-7314`, `ATT-7316`, `ATT-7318`, `ATT-7320`).
2. **ADF Comment Structure**: `parse_verdicts_from_comments` and `parse_final_review_agent_verdicts_from_comments` assume string bodies (`c.get("body", "")`). Under Jira Cloud v3 ADF, `body` is a nested dict (`{"type": "doc", "content": [...]}`), triggering `TypeError` / `AttributeError` on regex matching.
3. **Unverified Subtask Fallback**: In `tools/sprint_metrics.py:229-235`, subtasks with no verdict comments in `Erledigt` status incremented `passed_first_time` instead of being segregated into an `unverified` category.
4. **Downstream Escapes**: The Swiss Cheese barrier escape probability $p_g$ was computed solely from local revisions ($1 - \text{FTPR}$), ignoring Stage 6 Cold Review bounces that escaped earlier gates.
5. **Lack of Plausibility & Spot-Check Controls**: No warning was emitted when gates exhibited 100% pass rates across $>20$ subtasks, and no systematic spot-check discrepancy tracking was recorded.

* **Independent Peer Review & Verification**:
  * Verified hypothesis against Jira API and codebase: Confirmed that adding `labels,description` restores authentic severity distribution without altering underlying Jira issue schemas.
  * Verified that ADF dict parsing can be cleanly solved via recursive text extraction across `content` nodes.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Add `labels,description` to `fetch_all_sprint_issues` and `collect_sprint_metrics_for_id` in `tools/jira_util.py` and Höchstmaß mirror.
  2. Implement recursive ADF text extraction in comment parsing utilities.
  3. Segregate gate subtasks without verdict comments into `unverified` rather than `passed_first_time`.
  4. Incorporate downstream Stage 6 defect escapes into Gate 3, 4, 5 escape probabilities.
  5. Add plausibility check emitting warnings when pass rate is 100% across $>20$ gate reviews.
  6. Formally document Bug Severity Rubric (Critical, Major, Minor, Cosmetic) in `docs/project_governance.md` and enforce crash/ANR classification.
  7. Add spot-check sampling and discrepancy recording to sprint review / QA audit process.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying Android application runtime classes in `app/src/main/`.
  * Rewriting legacy pre-2026 historical metrics.
  * Altering Jira Cloud workflow statuses or transitions.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

Net-new requirement only: `REQ-PRO-092` (*Credible Quantitative Process Metrics, Bug Severity Rubric, and Downstream Gate Defect Accounting*). No existing requirements in `docs/requirements.md` are modified or weakened.

---

## 5. Change-Request Impact Analysis (SUP.10)

* **Affected Requirements**: Net-new `REQ-PRO-092` (`REQ-PRO-092.1`–`REQ-PRO-092.6`).
* **Affected Architecture Elements**: `tools/sprint_metrics.py`, `tools/jira_util.py`, `tools/hoechstmass/tools/sprint_metrics.py`, `tools/hoechstmass/tools/jira_util.py`, `docs/project_governance.md`, `.agents/skills/sprint-planner/SKILL.md`.
* **Verification Impact & Test Surface**:
  - `tools/test_sprint_metrics.py` (ADF comment parsing, unverified subtask segregation, severity extraction, downstream escape attribution, plausibility warnings).
  - `tools/test_jira_accounts.py` (severity validation and issue fetch parameters).
  - `tools/verify_requirement_governance.py` (living doc parity and shard validation).
* **Risk Assessment & Invariants**:
  - *Risk*: Backward compatibility with existing JSONL records in `process_metrics.jsonl`.
  - *Mitigation*: Ensure schema additions (`unverified`, `plausibility_warnings`, `downstream_escapes`) are optional/additive so existing records load without error.
  - *Invariants*: Pure Python 3 standard library; zero Android application runtime footprint.
