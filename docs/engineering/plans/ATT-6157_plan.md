# Stage 3: Implementation Plan - ATT-6157: Make change-request impact analysis explicit

**Ticket**: [ATT-6157](https://rainerblind.atlassian.net/browse/ATT-6157)  
**Sub-task**: `[Impl-Plan]` (Created on submission)  
**Parent Epic**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2 – Role Integrity, Independent QA & Configuration Management*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.3`  
**Requirement Mapping**: `REQ-PRO-064` (*Change-Request Impact Analysis Governance*)  
**Test Mapping**: `TST-PRO-064`  
**Branch**: `feature/ATT-6157`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-12  

---

## 1. Problem Description & Background

Remediate ASPICE SUP.10 (Change Request Management) and Pre-Assessment Finding B-12 by standardizing and enforcing explicit change-request impact analysis before Stage 1 approval. Analysis deliverables currently lack a mandatory machine-verifiable section for assessing impacts across requirements, architecture, tests, and risks.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-PRO-064` (`REQ-PRO-064.1` Template, `REQ-PRO-064.2` Verifier, `REQ-PRO-064.3` Gate 1 Enforcement, `REQ-PRO-064.4` Invariants)
* **Test Mapping**: `TST-PRO-064` (`TST-PRO-064.1` Section Tests, `TST-PRO-064.2` CLI Tests, `TST-PRO-064.3` Gate 1 Tests, `TST-PRO-064.4` Parity Tests)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Runtime Impact**: Zero modifications to Android production runtime code in `app/src/main/`.
2. **Non-Analysis Gate Invariance**: Gate verdicts for Stages 2–5 proceed completely unconstrained by Gate 1 checks.
3. **100% Counterpart Parity**: Root tooling and `tools/hoechstmass/` maintain exact functional and test parity.
4. **Human Gate Protection**: AI agents remain strictly prohibited from transitioning parent tickets to `Erledigt`.

---

## 4. Proposed Architectural Changes

### Component 1: Stage 1 Analysis Templates
* Add standardized `## 5. Change-Request Impact Analysis (SUP.10)` section with 4 mandatory fields:
  * `Affected Requirements` (or `Affected REQs`)
  * `Affected Architecture Elements` (or `Affected Architecture`)
  * `Affected Tests` (or `Affected Test Cases`)
  * `Risk Assessment & Mitigations` (or `Risks`)
* Files: `.agents/skills/stage1-analysis/templates/analysis_template.md`, `tools/hoechstmass/templates/analysis_template.md`.

### Component 2: Governance Verifier (`tools/verify_requirement_governance.py` & Höchstmaß)
* Implement `validate_impact_analysis_section(text)` verifying section presence and 4 populated fields without placeholder tokens (`TODO`, `TBD`, empty).
* Implement `verify_stage1_impact_analysis(analysis_file=None, repo_root=None)` and `--check-impact` CLI option.

### Component 3: Gate 1 Enforcement (`tools/jira_util.py` & Höchstmaß)
* In `gate_verdict()`, detect `[Analysis]` subtasks on `PASS` verdicts and validate deliverable description; abort with exit code 1 if invalid.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Standardize Stage 1 Templates
* Update root and Höchstmaß `analysis_template.md` with the 4-dimension Impact Analysis section.

### Step 2: Implement Governance Verifier
* Add `validate_impact_analysis_section` and `--check-impact` in `tools/verify_requirement_governance.py` and `tools/hoechstmass/tools/verify_requirement_governance.py`.

### Step 3: Implement Gate 1 Enforcement
* Add Impact Analysis validation hook in `gate_verdict` in `tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py`.

### Step 4: Unit Testing & Verification
* Add test cases in `tools/test_verify_requirement_governance.py`, `tools/test_jira_accounts.py`, and `tools/hoechstmass/tools/test_hoechstmass_tools.py`.
* Run test suites:
  ```bash
  python3 -m unittest tools/test_verify_requirement_governance.py tools/test_jira_accounts.py
  PYTHONPATH=tools/hoechstmass/tools python3 -m unittest tools/hoechstmass/tools/test_hoechstmass_tools.py
  python3 tools/verify_requirement_governance.py
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: 100% pass across all unit tests and living documentation parity verification.
* **Rollback**: Branch isolation (`feature/ATT-6157`) ensures clean discard if required.
