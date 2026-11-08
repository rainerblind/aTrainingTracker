# Stage 2: Requirement & Test Specification - ATT-5765

**Ticket**: [ATT-5765](https://rainerblind.atlassian.net/browse/ATT-5765)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5765`  
**Requirement Mapping**: `REQ-PRO-038` (*Automated Quantitative Sprint Metrics & Statistical Process Control*)  
**Test Spec ID**: `TST-PRO-038`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-07  

---

## 1. Requirement Specification (REQ-PRO-038)

### 1.1 Problem Statement & Rationale
Achieve ASPICE Level 4 (*Predictable*) process attributes `PA 4.1` (Quantitative Analysis) and `PA 4.2` (Quantitative Control) by automatically aggregating Gate 1–5 First-Time-Pass-Rates (FTPR), empirical Swiss Cheese layer defect escape probabilities, and Human Acceptance Rate directly from Jira comment attribution and persisting historical metrics for statistical process control.

### 1.2 Functional & Architectural Requirements
1. **Sprint Metrics Computation (`REQ-PRO-038.1`)**:
   - `tools/jira_util.py` (and `tools/hoechstmass/tools/jira_util.py`) SHALL provide a `sprint-metrics` command.
   - The tool SHALL parse subtask review comments (`[ROLE: agent2]`) to compute Gate 1–5 FTPR, empirical Swiss Cheese escape rates ($p_1 \dots p_5$), Human Acceptance Rate, and velocity.
2. **Time-Series Persistence (`REQ-PRO-038.2`)**:
   - The tool SHALL support `--record` to append/upsert JSON metrics into `docs/engineering/metrics/process_metrics.jsonl`.
   - The tool SHALL support `--backfill=N` to query and record metrics for the last $N$ closed sprints.
3. **Automated Retrospective Commenting (`REQ-PRO-038.3`)**:
   - The tool SHALL support `--post-to-retro` to comment the formatted metric table directly on the sprint's `Review & Retro` ticket.
4. **Statistical Process Control (`REQ-PRO-038.4`)**:
   - The tool SHALL calculate rolling mean $\mu$ and standard deviation $\sigma$, asserting control limits and flagging special cause variations ($\pm 2\sigma$).
5. **Governance Directory Whitelist (`REQ-PRO-038.5`)**:
   - `tools/verify_requirement_governance.py` and `hoechstmass_config.json` SHALL whitelist `metrics` in `ALLOWED_ENGINEERING_ENTRIES`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Metrics Aggregation & Table)**:
  * *Given* an active sprint with subtasks audited by `agent2`,
  * *When* `python3 tools/jira_util.py sprint-metrics` is run,
  * *Then* a structured table with Gate 1–5 FTPR, Swiss Cheese escape rates, and velocity is output.
* **Criterion 2 (Persistence & Control Limits)**:
  * *Given* `--record` and historical metrics in `process_metrics.jsonl`,
  * *When* the sprint record is evaluated,
  * *Then* metrics are upserted idempotently and deviations beyond $\mu \pm 2\sigma$ emit `[PA 4.2 Outlier]` alerts.

### 1.4 System Invariants
* Zero modifications to Android runtime source code in `app/src/main/`.
* Backwards compatibility for all existing `jira_util.py` subcommands.
* 100% build and governance verification pass rate.

---

## 2. Test Specification (TST-PRO-038)

### Test Case 1: Sprint Metrics Parsing & Computation (`TST-PRO-038.1`)
* **Scope**: Automated Python Unit Test (`tools/test_sprint_metrics.py`)
* **Assertions**: Assert `parse_sprint_gate_metrics()` computes correct Gate 1–5 FTPR, escape rates ($p_1 \dots p_5$), and velocity from mocked Jira issue payloads.

### Test Case 2: Time-Series Persistence & Idempotent Upsert (`TST-PRO-038.2`)
* **Scope**: Automated Python Unit Test (`tools/test_sprint_metrics.py`)
* **Assertions**: Assert `--record` writes valid JSONL lines to a target file; re-recording the same sprint updates the line in place without duplicating records.

### Test Case 3: Statistical Process Control & $\mu \pm 2\sigma$ Outlier Detection (`TST-PRO-038.3`)
* **Scope**: Automated Python Unit Test (`tools/test_sprint_metrics.py`)
* **Assertions**: Assert `calculate_spc_limits()` calculates rolling mean $\mu$ and $\sigma$, correctly flagging values outside $\mu \pm 2\sigma$.

### Test Case 4: Governance Whitelist & Parity (`TST-PRO-038.4`)
* **Scope**: Integration Verification
* **Assertions**: `python3 tools/verify_requirement_governance.py` succeeds with 0 errors when `docs/engineering/metrics/` is present.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-038.1` | Unit (Python) | `parse_sprint_gate_metrics` | `REQ-PRO-038.1` | Specified |
| `TST-PRO-038.2` | Unit (Python) | `record_sprint_metrics` | `REQ-PRO-038.2` | Specified |
| `TST-PRO-038.3` | Unit (Python) | `calculate_spc_limits` | `REQ-PRO-038.4` | Specified |
| `TST-PRO-038.4` | Governance | `verify_requirement_governance.py` | `REQ-PRO-038.5` | Specified |
