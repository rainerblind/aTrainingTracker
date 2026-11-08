# Stage 3: Implementation Plan - ATT-5765: Automated Quantitative Sprint Metrics (PA 4.1) & SPC (PA 4.2)

**Ticket**: [ATT-5765](https://rainerblind.atlassian.net/browse/ATT-5765)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5765`  
**Requirement Mapping**: `REQ-PRO-038` | **Test Mapping**: `TST-PRO-038`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-07  

---

## 1. Problem Description & Architecture
Implement automated quantitative sprint metrics collection (`PA 4.1`) and Statistical Process Control (`PA 4.2`) within `tools/jira_util.py` (and `tools/hoechstmass/tools/jira_util.py`):
1. **Metrics Engine (`PA 4.1`)**: Parse subtask audit comments (`[ROLE: agent2]`) to compute Gate 1–5 First-Time-Pass-Rates (FTPR), empirical Swiss Cheese layer escape rates ($p_1 \dots p_5 = 1 - \text{FTPR}$), Human Acceptance Rate, Defect Escape Rate, and throughput/risk metrics.
2. **Time-Series Persistence**: Store historical sprint metrics in `docs/engineering/metrics/process_metrics.jsonl` with idempotent record updates and `--backfill=N` support.
3. **Statistical Process Control (`PA 4.2`)**: Compute rolling mean $\mu$ and standard deviation $\sigma$ across recorded sprints, assert control limits ($\mu \pm 2\sigma$), and flag special cause variations with `⚠️ PA 4.2 Special Cause Variation — Root-Cause Analysis (RCA) in Retro required`.
4. **Governance Integration**: Whitelist `metrics` directory in `tools/verify_requirement_governance.py`, `hoechstmass_config.json`, and Rule 8 of `aspice_governance.md`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-038` (*Automated Quantitative Sprint Metrics & Statistical Process Control*)
* **Test Mapping**: `TST-PRO-038.1` (Parsing & FTPR math), `TST-PRO-038.2` (JSONL persistence & upsert), `TST-PRO-038.3` (SPC $\mu \pm 2\sigma$ outlier detection), `TST-PRO-038.4` (Governance whitelist parity)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Runtime App Impact**: Zero changes to production code in `app/src/main/`.
2. **Backwards Compatibility**: All existing `jira_util.py` subcommands remain unmodified.
3. **100% Governance Parity**: `verify_requirement_governance.py` passes cleanly with `metrics/` present.

---

## 4. Proposed Architectural Changes (SWE.2)
* **`tools/verify_requirement_governance.py` & `hoechstmass_config.json`**:
  * Add `"metrics"` to `ALLOWED_ENGINEERING_ENTRIES` and `allowed_engineering_entries`.
  * Update `docs/engineering/metrics/process_metrics.jsonl` storage path.
* **`tools/jira_util.py` & `tools/hoechstmass/tools/jira_util.py`**:
  * Add `parse_sprint_gate_metrics()`, `calculate_spc_limits()`, `record_sprint_metrics()`, and `cmd_sprint_metrics()`.
  * Wire `sprint-metrics` subcommand supporting `--sprint`, `--record`, `--post-to-retro`, and `--backfill=N`.
* **`tools/test_sprint_metrics.py`**:
  * Unit test suite verifying parsing, FTPR math, SPC limits, JSONL upsert, and outlier warnings.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (Governance Whitelist)**: Add `"metrics"` to `ALLOWED_ENGINEERING_ENTRIES` in `tools/verify_requirement_governance.py`, `tools/hoechstmass/tools/verify_requirement_governance.py`, and `tools/hoechstmass/hoechstmass_config.json`. Create `docs/engineering/metrics/`.
2. **Step 2 (Metrics & SPC Engine)**: Implement `parse_sprint_gate_metrics()`, `calculate_spc_limits()`, `record_sprint_metrics()`, and `cmd_sprint_metrics()` in `tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py`.
3. **Step 3 (Automated Unit Tests)**: Author `tools/test_sprint_metrics.py` (and mirror in `tools/hoechstmass/tools/test_sprint_metrics.py`) covering all `TST-PRO-038` test cases.
4. **Step 4 (Verification & Governance Execution)**: Run `python3 -m unittest tools/test_sprint_metrics.py` and `python3 tools/verify_requirement_governance.py`.
5. **Step 5 (Smoke Test Live CLI)**: Execute `python3 tools/jira_util.py --as coordinator sprint-metrics` and verify report.

---

## 6. Verification & Rollback Plan
* **Verification**: `python3 -m unittest tools/test_sprint_metrics.py` (100% PASS) + `python3 tools/verify_requirement_governance.py` (100% parity).
* **Rollback Plan**: `git checkout -- .` on `feature/ATT-5765`.
