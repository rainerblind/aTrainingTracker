# Stage 2: Requirement & Test Specification - ATT-7559: Make process metrics credible: count gate rejections and apply bug severity rubric

**Ticket**: [ATT-7559](https://rainerblind.atlassian.net/browse/ATT-7559)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*ASPICE Governance & Process Stability*)  
**Target Release**: `V5.1.0`  
**Active Sprint**: `2026-46.6`  
**Requirement Mapping**: `REQ-PRO-092` (*Process Metrics Credibility, Bug Severity Rubric, and Downstream Gate Defect Accounting*)  
**Test Spec ID**: `TST-PRO-092` (*Process Metrics Credibility, Bug Severity Rubric & Downstream Escape Verification Suite*)  
**Branch**: `feat/ATT-7559`  
**Author**: AI Agent 1 (Implementer)  
**Reviewer**: AI Agent 2 (Tester / Reviewer)  
**Date**: 2026-11-14  

---

## 1. Requirement Specification (REQ-PRO-092)

### 1.1 Problem Statement & Rationale
Quantitative process metrics recorded in `docs/engineering/metrics/process_metrics.jsonl` lacked credibility as process evidence (ASPICE finding F2). 100% first-pass rates across Gates 1–5 masked 22 Stage 6 bounces, 100% of bugs were recorded as "Minor" due to omitting `labels` and `description` from Jira queries, gate subtasks without verdicts inflated FTPR via fallback, ADF comment bodies crashed regex parsers, and downstream escapes were not attributed. Establishing credible metrics, robust ADF handling, unverified subtask segregation, downstream escape attribution, and a documented bug severity rubric satisfies ASPICE PA 4.1, PA 4.2, and SUP.9.

### 1.2 Attributes
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review

### 1.3 Functional & Technical Clauses
1. **Jira API Metric Field Ingestion (`REQ-PRO-092.1`)**:
   - `collect_sprint_metrics_for_id` SHALL include `labels` and `description` in queried issue fields.
   - Bug severity parser SHALL prioritize labels (`severity-critical`, `severity-major`, `severity-minor`, `severity-cosmetic`), followed by regex extraction from description (`**Severity**: <Level>`), before priority fallback.
2. **Robust ADF Comment Parsing (`REQ-PRO-092.2`)**:
   - `parse_verdicts_from_comments` and `parse_final_review_agent_verdicts_from_comments` SHALL handle ADF dict bodies recursively without raising `AttributeError` or `TypeError`.
3. **Segregation of Unverified Gate Subtasks (`REQ-PRO-092.3`)**:
   - Subtasks in `Erledigt` lacking explicit verdict comments SHALL NOT increment `passed_first_time`; they SHALL be tracked separately as `unverified`.
4. **Downstream Defect Escape Accounting (`REQ-PRO-092.4`)**:
   - Swiss Cheese layer escape rates ($p_g$) SHALL account for Stage 6 bounces attributable to upstream gate phases.
5. **Plausibility Warning Thresholds (`REQ-PRO-092.5`)**:
   - Emit a prominent warning (`PA 4.1 Plausibility Warning`) when first-time pass rate is 100.0% across $\ge 20$ gate subtasks without a single revision or bounce.
6. **Bug Severity Rubric Governance (`REQ-PRO-092.6`)**:
   - Document 4-tier rubric in `docs/project_governance.md`; enforce that crash/ANR origin bugs are assigned at least `Major` or `Critical`.

### 1.4 Acceptance Criteria (Given-When-Then)
- **Criterion 1**: *Given* sprint issues with severity labels, *When* metrics are collected, *Then* severities reflect labels (e.g. 1 Critical, 5 Major in Sprint 441) instead of all Minor.
- **Criterion 2**: *Given* ADF-formatted comment dicts, *When* parsed, *Then* text is extracted recursively and verdicts evaluated without exception.
- **Criterion 3**: *Given* subtasks without verdicts in `Erledigt`, *When* metrics are parsed, *Then* they are recorded as `unverified` and excluded from `passed_first_time`.
- **Criterion 4**: *Given* Stage 6 bounces, *When* escape rates are computed, *Then* downstream defect escapes yield $p_g > 0$.
- **Criterion 5**: *Given* 100% FTPR across $\ge 20$ gates, *When* metrics are generated, *Then* a plausibility warning is emitted.

### 1.5 System Invariants
- Zero Android application runtime bytecode modifications (`app/src/main/` untouched).
- Pure Python 3 standard library tooling.
- Backward compatibility with existing `process_metrics.jsonl` records.

---

## 2. Test Specification (TST-PRO-092)

### Test Case 1: `test_collect_sprint_metrics_extracts_labels_and_description` (`TST-PRO-092.1`)
- **Scope**: Unit Test (`tools/test_sprint_metrics.py`)
- **Action**: Pass mock parent issues with `labels: ["severity-critical"]` and `labels: ["severity-major"]` to `parse_sprint_gate_metrics`.
- **Expected Result**: Assert `metrics["problem_resolution"]["by_severity"]["Critical"] == 1` and `Major == 5`, proving zero default Minor fallback.

### Test Case 2: `test_parse_verdicts_from_adf_comments` (`TST-PRO-092.2`)
- **Scope**: Unit Test (`tools/test_sprint_metrics.py`)
- **Action**: Pass ADF dict comment structures (`{"type": "doc", "content": [...]}`) to `parse_verdicts_from_comments` and `parse_final_review_agent_verdicts_from_comments`.
- **Expected Result**: Successfully parses `["REVISION", "PASS"]` and `["BOUNCE 1/3"]` without throwing `TypeError` or `AttributeError`.

### Test Case 3: `test_gate_subtasks_without_verdicts_marked_unverified` (`TST-PRO-092.3`)
- **Scope**: Unit Test (`tools/test_sprint_metrics.py`)
- **Action**: Pass gate subtasks in status `Erledigt` with empty comments.
- **Expected Result**: Subtasks increment `unverified` count and do NOT increment `passed_first_time`.

### Test Case 4: `test_downstream_stage6_bounces_attributed_to_escape_rate` (`TST-PRO-092.4`)
- **Scope**: Unit Test (`tools/test_sprint_metrics.py`)
- **Action**: Evaluate sprint with zero direct gate revisions but 5 Stage 6 bounces.
- **Expected Result**: Upstream gate escape rates reflect downstream defects, satisfying $p_g > 0$.

### Test Case 5: `test_plausibility_warning_emitted_on_100_percent_pass` (`TST-PRO-092.5`)
- **Scope**: Unit Test (`tools/test_sprint_metrics.py`)
- **Action**: Evaluate $\ge 20$ gate subtasks with 100% first-pass rate.
- **Expected Result**: `metrics["plausibility_warnings"]` contains warning regarding potential gate discrimination blind spot.

### Test Case 6: `test_bug_severity_rubric_enforcement` (`TST-PRO-092.6`)
- **Scope**: Unit Test (`tools/test_jira_accounts.py`)
- **Action**: Attempt to create a bug containing "crash" or "ANR" with `--severity=minor`.
- **Expected Result**: Validation flags error or enforces at least `Major` severity unless overridden.

---

## 3. Bidirectional Traceability Matrix

| Requirement | Test Spec | Verification Script / Class | Invariant | Status |
|---|---|---|---|---|
| `REQ-PRO-092.1` | `TST-PRO-092.1` | `tools/test_sprint_metrics.py` | Accurate severity distribution | Verified |
| `REQ-PRO-092.2` | `TST-PRO-092.2` | `tools/test_sprint_metrics.py` | ADF parsing robustness | Verified |
| `REQ-PRO-092.3` | `TST-PRO-092.3` | `tools/test_sprint_metrics.py` | Zero unverified fallback inflation | Verified |
| `REQ-PRO-092.4` | `TST-PRO-092.4` | `tools/test_sprint_metrics.py` | Downstream escape attribution | Verified |
| `REQ-PRO-092.5` | `TST-PRO-092.5` | `tools/test_sprint_metrics.py` | Plausibility warning thresholds | Verified |
| `REQ-PRO-092.6` | `TST-PRO-092.6` | `tools/test_jira_accounts.py` | Governance rubric enforcement | Verified |
