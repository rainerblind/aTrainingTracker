# Stage 3: Implementation Plan - ATT-7559: Make process metrics credible: count gate rejections and apply bug severity rubric

**Ticket**: [ATT-7559](https://rainerblind.atlassian.net/browse/ATT-7559)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*ASPICE Governance & Process Stability*)  
**Target Release**: `V5.1.0`  
**Active Sprint**: `2026-46.6`  
**Requirement Mapping**: `REQ-PRO-092` (`REQ-PRO-092.1`–`REQ-PRO-092.6`)  
**Test Mapping**: `TST-PRO-092` (`TST-PRO-092.1`–`TST-PRO-092.6`)  
**Branch**: `feat/ATT-7559`  
**Author**: AI Agent 1 (Implementer)  
**Reviewer**: AI Agent 2 (Tester / Reviewer)  
**Date**: 2026-11-14  

---

## 1. Executive Summary & Architecture Overview (SWE.2)

### 1.1 Goal
Remediate ASPICE finding F2 to ensure that quantitative process metrics (PA 4.1, PA 4.2) and problem-resolution management (SUP.9) are trustworthy and substantiated by credible evidence. This requires eliminating false 100% first-pass rates caused by fallback inflation, parsing Jira Cloud v3 ADF comment structures, requesting `labels` and `description` during sprint metric issue queries, attributing downstream Stage 6 defect escapes to upstream gate barriers, establishing plausibility checks, documenting the authoritative Bug Severity Rubric, and introducing gate verdict spot-check audits.

### 1.2 Target Architecture & Affected Modules
1. **Metric Ingestion (`tools/jira_util.py` & `tools/hoechstmass/tools/jira_util.py`)**:
   - Update `collect_sprint_metrics_for_id` to include `labels,description` in the Jira REST query string.
   - In `create_issue`, validate that crash/ANR origin bugs are assigned at least `Major` or `Critical` severity.
2. **Comment Parsing & ADF Support (`tools/sprint_metrics.py` & `tools/hoechstmass/tools/sprint_metrics.py`)**:
   - Introduce `extract_text_from_comment_body(body)` to recursively traverse Atlassian Document Format (ADF) dicts.
   - Update `parse_verdicts_from_comments` and `parse_final_review_agent_verdicts_from_comments` to process extracted plain text.
3. **Metric Computation & Escape Modeling (`tools/sprint_metrics.py` & `tools/hoechstmass/tools/sprint_metrics.py`)**:
   - In `parse_sprint_gate_metrics`:
     - Segregate `Erledigt` subtasks lacking verdict comments into an `unverified` counter (`gate_data[g]["unverified"]`), excluding them from `passed_first_time`.
     - Attribute downstream Gate 6 bounces to upstream gate escape rates ($p_3, p_4, p_5$).
     - Implement a plausibility check emitting a warning when first-time pass rate is 100% across $\ge 20$ gate subtasks.
4. **Governance Documentation (`docs/project_governance.md`)**:
   - Formally document the 4-tier Bug Severity Rubric (Critical, Major, Minor, Cosmetic) and crash/ANR criteria.
5. **Sprint Planning & Retrospective Guidelines (`.agents/skills/sprint-planner/SKILL.md`)**:
   - In Ceremony 2, add instructions to perform a spot-check sample of gate verdicts and record discrepancy rates.
6. **Test Verification Suites (`tools/test_sprint_metrics.py`, `tools/test_jira_accounts.py`, `tools/hoechstmass/tools/test_sprint_metrics.py`)**:
   - Expand unit tests to validate all new parsing, counting, warning, and rubric enforcement logic.

---

## 2. Invariants & Guardrails

- **Invariant 1 (Standard Library Only)**: All tooling code in `tools/sprint_metrics.py`, `tools/jira_util.py`, and mirror repositories must rely strictly on standard library Python 3 modules (`re`, `json`, `math`, `datetime`, `argparse`).
- **Invariant 2 (Zero Android Application Footprint)**: No modifications may be made to `app/src/main/` or production Android runtime classes.
- **Invariant 3 (Backward Compatible Metric Schema)**: Additions to `process_metrics.jsonl` (`unverified`, `plausibility_warnings`, `downstream_escapes`) must be additive so historical metric records load without regression.
- **Invariant 4 (Truth in Process Metrics)**: Unverified subtasks must never be counted as `passed_first_time`.

---

## 3. Atomic Implementation Steps (Stage 4 Plan)

### Step 1: Add `labels,description` to Jira REST Issue Queries
- **Files**: `tools/jira_util.py`, `tools/hoechstmass/tools/jira_util.py`
- **Actions**:
  - In `collect_sprint_metrics_for_id`, update `fields` query parameter:
    ```python
    f"summary,status,issuetype,priority,subtasks,comment,labels,description,{STORY_POINTS_FIELD}"
    ```
  - In Höchstmaß mirror `tools/hoechstmass/tools/jira_util.py`, make the identical field parameter addition.

### Step 2: Implement Recursive ADF Text Extraction in `sprint_metrics.py`
- **Files**: `tools/sprint_metrics.py`, `tools/hoechstmass/tools/sprint_metrics.py`
- **Actions**:
  - Implement helper `extract_text_from_comment_body(body)`:
    ```python
    def extract_text_from_comment_body(body):
        if isinstance(body, str):
            return body
        if isinstance(body, dict):
            texts = []
            def _recurse(node):
                if isinstance(node, dict):
                    if node.get("type") == "text" and "text" in node:
                        texts.append(node["text"])
                    for v in node.values():
                        _recurse(v)
                elif isinstance(node, list):
                    for item in node:
                        _recurse(item)
            _recurse(body)
            return " ".join(texts)
        return str(body) if body is not None else ""
    ```
  - Use `extract_text_from_comment_body(c.get("body", ""))` in `parse_verdicts_from_comments` and `parse_final_review_agent_verdicts_from_comments`.

### Step 3: Segregate Unverified Subtasks in `parse_sprint_gate_metrics`
- **Files**: `tools/sprint_metrics.py`, `tools/hoechstmass/tools/sprint_metrics.py`
- **Actions**:
  - Add `"unverified": 0` to `gate_data` dictionary initialization for each gate.
  - In the subtask comment evaluation loop:
    ```python
    if not verdicts:
        st_status = st.get("fields", {}).get("status", {}).get("name", "").lower()
        if st_status in ("erledigt", "done", "closed"):
            gate_data[g_num]["unverified"] += 1
        continue
    ```
  - Ensure unverified subtasks do NOT increment `passed_first_time` or `total` evaluated with verdicts, and record `unverified_subtasks` in `gates_summary` and overall metrics.

### Step 4: Attribute Downstream Defect Escapes & Add Plausibility Warning
- **Files**: `tools/sprint_metrics.py`, `tools/hoechstmass/tools/sprint_metrics.py`
- **Actions**:
  - If `gate_data[6]["bounces"] > 0`, distribute downstream escapes across Gates 3, 4, 5 (e.g. Plan, Implementation, Test) to adjust effective escape rates:
    $$p_g^{\text{effective}} = \max(p_g, \text{downstream\_escape\_ratio})$$
  - In `parse_sprint_gate_metrics`, evaluate plausibility:
    - If total evaluated gate subtasks $\ge 20$ and `total_passed_first_time == total_evaluated_subtasks`:
      - Add warning `"PA 4.1 Plausibility Warning: Gate first-time pass rate is 100.0% across N subtasks without revisions, indicating potential gate discrimination blind spots."` to `metrics["plausibility_warnings"]`.

### Step 5: Document Bug Severity Rubric in `docs/project_governance.md`
- **Files**: `docs/project_governance.md`
- **Actions**:
  - Add Section detailing the 4-tier Bug Severity Rubric:
    - **Critical**: Startup failure, recording loop termination, data corruption/loss, ANR blocking core flow.
    - **Major**: Secondary screen crash/ANR, sensor reconnect failure, severe battery drain, functional defect with workaround.
    - **Minor**: Non-crashing logic defect, transient UI glitch, localized string formatting flaw.
    - **Cosmetic**: Visual styling quirk, margin/alignment irregularity, icon tint mismatch.

### Step 6: Enforce Crash/ANR Severity Rubric in `tools/jira_util.py`
- **Files**: `tools/jira_util.py`
- **Actions**:
  - In `create_issue`, when `issuetype_id == "10006"` (Bug), inspect summary and description for crash/ANR keywords (`crash`, `anr`, `fatal exception`, `sigsegv`, `nullpointerexception`). If present and chosen severity is `minor` or `cosmetic`, require explicit override flag or elevate/reject.

### Step 7: Update Sprint Planner Ceremony 2 Guidelines
- **Files**: `.agents/skills/sprint-planner/SKILL.md`
- **Actions**:
  - In Ceremony 2 (Retrospective), add step to sample gate verdicts (spot-check) and record discrepancy observations in the retrospective deliverable.

### Step 8: Update Unit Test Suites
- **Files**: `tools/test_sprint_metrics.py`, `tools/test_jira_accounts.py`, `tools/hoechstmass/tools/test_sprint_metrics.py`
- **Actions**:
  - Add test cases covering:
    - Ingestion of `labels` and `description` yielding proper severity distribution.
    - ADF dict parsing in `parse_verdicts_from_comments` and `parse_final_review_agent_verdicts_from_comments`.
    - Segregation of unverified subtasks from `passed_first_time`.
    - Downstream escape attribution to $p_g$.
    - Plausibility warning generation on 100% FTPR over $\ge 20$ subtasks.
    - Bug severity rubric validation for crash/ANR keywords.

---

## 4. Verification & Validation Plan (Stage 5 Blueprint)

### 4.1 Automated Test Execution Commands
```bash
# 1. Run sprint metrics and Jira accounts test suites
python3 -m unittest tools/test_sprint_metrics.py -v
python3 -m unittest tools/test_jira_accounts.py -v
python3 -m unittest tools/hoechstmass/tools/test_sprint_metrics.py -v

# 2. Run living doc governance check and index verification
python3 tools/verify_requirement_governance.py --sync-indexes
python3 tools/verify_requirement_governance.py --check-all
```

### 4.2 Living Documentation Transition
- Update `REQ-PRO-092` in `docs/requirements/req_process_stability.md` from `[Planned]` to `[Verified]`.
- Update `TST-PRO-092` in `docs/tests/tst_process_stability.md` from `[Planned]` to `[Verified]`.
- Synchronize master tables via `python3 tools/verify_requirement_governance.py --sync-indexes`.

### 4.3 Walkthrough Document
- Author `docs/engineering/walkthroughs/ATT-7559_walkthrough.md` documenting:
  - Exact changes made to `tools/sprint_metrics.py`, `tools/jira_util.py`, and `docs/project_governance.md`.
  - ADF parsing evidence and test outputs.
  - Severity extraction demonstration showing resolution of the all-Minor bug defect.
  - Plausibility warning and downstream escape verification.

---

## 5. Gate 3 Readiness Checklist

| Criterion | Status | Description |
|---|---|---|
| Traceability to REQ-PRO-092 | PASS | All 6 clauses mapped to concrete implementation steps |
| Traceability to TST-PRO-092 | PASS | All 6 test cases mapped to automated test procedures |
| Invariant Preservation | PASS | Pure Python standard library, zero runtime app modifications |
| Robustness & ADF Support | PASS | Recursive traversal handles string and dict comment bodies |
| Governance & Rubric Alignment | PASS | Rubric documented in `docs/project_governance.md` and enforced |
