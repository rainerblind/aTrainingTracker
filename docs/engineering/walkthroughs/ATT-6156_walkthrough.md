# Stage 5: Walkthrough & Verification - ATT-6156: Add severity classification and problem trend metrics

**Ticket**: [ATT-6156](https://rainerblind.atlassian.net/browse/ATT-6156)  
**Sub-task**: `[Test]` (Created on submission)  
**Parent Epic**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2 – Role Integrity, Independent QA & Configuration Management*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.3`  
**Requirement Mapping**: `REQ-PRO-063` (*Problem Severity Classification & Defect Trend Metrics*)  
**Test Mapping**: `TST-PRO-063`  
**Branch**: `feature/ATT-6156`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`)  
**Date**: 2026-11-12  

---

## 1. Executive Summary & Verification Overview

ATT-6156 resolves ASPICE SUP.9 (Problem Resolution Management) and MAN.3 pre-assessment finding B-11 by implementing defect severity classification and quantitative problem trend telemetry:
1. Enforced mandatory severity (`Critical`, `Major`, `Minor`, `Cosmetic`) when creating Bugs (`10006`) via `tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py`, tagging standardized `severity-<level>` labels and recording description metadata.
2. Preserved non-bug creation invariance: Feature, Improvement, Story, Task, and Epic issue creation are unaffected by the severity mandate.
3. Implemented problem trend extraction in `tools/sprint_metrics.py` and Höchstmaß mirror, categorizing bug issues by severity, tracking `open_bugs` vs `closed_bugs`, and calculating defect resolution rates.
4. Serialized `problem_trends` in `docs/engineering/metrics/process_metrics.jsonl` and rendered dedicated "Problem Trends & Defect Severity" sections in markdown summaries.
5. Extended `verify_sprint_metrics()` in `tools/verify_requirement_governance.py` and Höchstmaß mirror to validate `problem_trends` schema fields.
6. Maintained 100% mathematical living doc parity for `REQ-PRO-063` and `TST-PRO-063`.
7. Authored unit test suites across `test_jira_accounts.py`, `test_sprint_metrics.py`, `test_verify_requirement_governance.py`, and `test_hoechstmass_tools.py` with 100% pass rate.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-063.1` | `TST-PRO-063.1` | Unit Test (`test_create_issue_requires_severity_for_bugs` in `tools/test_jira_accounts.py`) | **PASSED** | `Verified` |
| `REQ-PRO-063.1` | `TST-PRO-063.4` | Unit Test (`test_hoechstmass_create_issue_severity_enforcement` in `tools/hoechstmass/tools/test_hoechstmass_tools.py`) | **PASSED** | `Verified` |
| `REQ-PRO-063.2` | `TST-PRO-063.1` | Unit Test (`test_create_issue_requires_severity_for_bugs` in `tools/test_jira_accounts.py`) | **PASSED** | `Verified` |
| `REQ-PRO-063.3` | `TST-PRO-063.2` | Unit Test (`test_parse_sprint_gate_metrics_problem_trends` in `tools/test_sprint_metrics.py`) | **PASSED** | `Verified` |
| `REQ-PRO-063.4` | `TST-PRO-063.2` | Unit Test (`test_parse_sprint_gate_metrics_problem_trends` in `tools/test_sprint_metrics.py`) | **PASSED** | `Verified` |
| `REQ-PRO-063.5` | `TST-PRO-063.3` | Unit Test (`test_format_metrics_markdown_includes_problem_trends` in `tools/test_sprint_metrics.py`) | **PASSED** | `Verified` |
| `REQ-PRO-063.6` | `TST-PRO-063.3` | Unit Test (`test_scenario_15_verify_sprint_metrics_validation` in `tools/test_verify_requirement_governance.py`) | **PASSED** | `Verified` |
| `REQ-PRO-063.6` | `TST-PRO-063.4` | Unit Test (`test_hoechstmass_verify_sprint_metrics_problem_trends` in `tools/hoechstmass/tools/test_hoechstmass_tools.py`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Python Tooling Test Suites (115 Tests Passed)
```text
Ran 38 tests in tools/test_jira_accounts.py (OK)
Ran 17 tests in tools/test_verify_requirement_governance.py (OK)
Ran 9 tests in tools/test_sprint_metrics.py (OK)
Ran 16 tests in tools/test_jira_epics.py (OK)
Ran 26 tests in tools/hoechstmass/tools/test_hoechstmass_tools.py (OK)
Ran 9 tests in tools/hoechstmass/tools/test_sprint_metrics.py (OK)
Total: 115 passed cleanly across root and Höchstmaß suites.
```

### Requirement Governance Verification
```text
PASS: Living documentation master tables match all 9 domain shards with 100% parity.
PASS: Zero Composable islands detected (no modified UI files).
PASS: Zero repository encapsulation violations detected.
PASS: Zero architecture or system specification modifications detected.
```

---

## 4. Hardware / Physical Verification (Pixel 10)

Pure developer tooling, defect classification, and ASPICE SUP.9 quantitative metrics enhancement; zero APK runtime bytecode or asset modifications in `app/src/main/`. Verified via automated unit and governance test suites.

---

## 5. Invariant & Governance Verification

1. **Zero Runtime Impact**: Zero modifications to production Android code or application resources in `app/src/main/`.
2. **Non-Bug Creation Invariance**: Non-bug issue types (Features, Improvements, Tasks) are completely unconstrained by the severity mandate.
3. **Standard Library Footprint**: Pure Python standard library (`json`, `os`, `re`, `sys`, `datetime`).
4. **100% Mirror Parity**: Exact counterpart parity maintained between root tooling and `tools/hoechstmass/`.
5. **Living Documentation Synchronized**: `REQ-PRO-063` and `TST-PRO-063` updated in living documentation with 100% mathematical parity across `docs/requirements.md` and `docs/tests.md`.
6. **Inviolable Human Gate**: Prohibiting agent roles from moving parent tickets to `Erledigt` strictly preserved.
