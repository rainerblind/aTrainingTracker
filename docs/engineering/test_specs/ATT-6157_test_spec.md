# Stage 2: Requirement & Test Specification - ATT-6157: Make change-request impact analysis explicit

**Ticket**: [ATT-6157](https://rainerblind.atlassian.net/browse/ATT-6157)  
**Sub-task**: `[Req & Test Spec]` (Created on submission)  
**Parent Epic**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2 – Role Integrity, Independent QA & Configuration Management*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.3`  
**Requirement Mapping**: `REQ-PRO-064` (*Change-Request Impact Analysis Governance*)  
**Test Spec ID**: `TST-PRO-064`  
**Branch**: `feature/ATT-6157`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`)  
**Date**: 2026-11-12  

---

## 1. Requirement Specification (`REQ-PRO-064`)

### 1.1 Problem Statement & Rationale
Under ASPICE SUP.10 (Change Request Management) and Pre-Assessment Finding B-12, change request impact analysis must be formally and explicitly documented before Stage 1 approval rather than remaining implicit or omitted. Formal evaluation across four dimensions—affected requirements, affected architecture, verification impact, and risks/invariants—prevents downstream architecture drift and unexpected regressions.

### 1.2 Functional & Architectural Requirements
1. **Standardized Impact Analysis Template (`REQ-PRO-064.1`)**: Stage 1 analysis templates in `.agents/skills/stage1-analysis/templates/analysis_template.md` and `tools/hoechstmass/templates/analysis_template.md` SHALL feature a dedicated "Change Impact Analysis" (or "Impact Analysis") section mandating 4 discrete dimensions: Affected Requirements, Affected Architecture Elements, Verification Impact & Test Surface, and Risk Assessment & Invariants.
2. **Automated Impact Analysis Verifier (`REQ-PRO-064.2`)**: `tools/verify_requirement_governance.py` and Höchstmaß mirror SHALL implement `validate_impact_analysis_section(text)` and `verify_stage1_impact_analysis(analysis_file=None, repo_root=None)` with `--check-impact [FILE]`, validating that the section header exists and all 4 dimensions are populated with non-empty, non-placeholder content.
3. **Gate 1 Automated & Checklist Enforcement (`REQ-PRO-064.3`)**: The Gate 1 review process in `.agents/skills/stage1-analysis/SKILL.md` and `gate_verdict` in `tools/jira_util.py` (and Höchstmaß mirror) SHALL mandate that `gate-verdict <SUBTASK> PASS` on `[Analysis]` subtasks validates the presence and completeness of the Impact Analysis section, rejecting incomplete deliverables with an actionable error.
4. **Core Invariants & Non-Analysis Gate Independence (`REQ-PRO-064.4`)**: Gate verdict execution for non-analysis subtasks (Gates 2, 3, 4, 5) SHALL NOT be constrained by Gate 1 impact analysis checks; zero modifications to Android production runtime code in `app/src/main/`.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Net-new requirement `REQ-PRO-064` (`REQ-PRO-064.1`–`REQ-PRO-064.4`). Targets Stage 1 templates, `tools/verify_requirement_governance.py`, `tools/jira_util.py`, and `tools/hoechstmass/`.
* `Historical Origin & Commit Trace`: Introduced in Sprint 2026-46.3 via ATT-6157 under Epic ATT-6141 to satisfy ASPICE pre-assessment finding B-12.
* `Root Reason for Existing Formulation`: Gaps in explicit change-request impact analysis under ASPICE SUP.10.
* `Preservation of Core Invariants`: Zero changes to Android runtime code; pure Python 3 standard library; non-analysis gates unaffected.

### 1.4 Acceptance Criteria (Given-When-Then)
* **AC-1 (Section Validation)**:
  * *Given* a Stage 1 analysis deliverable,
  * *When* `validate_impact_analysis_section()` runs,
  * *Then* it passes if and only if all 4 dimensions are explicitly specified with non-empty, non-placeholder content.
* **AC-2 (Gate 1 Rejection)**:
  * *Given* an `[Analysis]` subtask submitted for Gate 1 review without a valid Impact Analysis section,
  * *When* `gate-verdict` is called with `PASS`,
  * *Then* it aborts with exit code 1 and descriptive error output.
* **AC-3 (Non-Analysis Invariance)**:
  * *Given* a non-analysis subtask (Gates 2..5),
  * *When* `gate-verdict` is called,
  * *Then* it succeeds without executing Stage 1 impact analysis checks.

---

## 2. Test Specification (`TST-PRO-064`)

### Test Case 1: Impact Analysis Section Validation (`TST-PRO-064.1`)
* **Scope**: Unit Test (`tools/test_verify_requirement_governance.py` & `tools/hoechstmass/tools/test_hoechstmass_tools.py`)
* **Action**: Assert `validate_impact_analysis_section()` returns True for valid 4-dimension impact text, and returns False with error messages for missing sections, omitted dimensions, or placeholder text.
### Test Case 2: Governance CLI `--check-impact` Flag (`TST-PRO-064.2`)
* **Scope**: Unit Test (`tools/test_verify_requirement_governance.py`)
* **Action**: Run `verify_stage1_impact_analysis(file)` and CLI `--check-impact [FILE]`; assert exit code 0 on valid deliverables and exit code 1 on malformed files.
### Test Case 3: Gate 1 Impact Analysis Verification (`TST-PRO-064.3`)
* **Scope**: Unit Test (`tools/test_jira_accounts.py` & `tools/hoechstmass/tools/test_hoechstmass_tools.py`)
* **Action**: Assert `gate-verdict` on `[Analysis]` subtasks fails when impact analysis is missing/incomplete; assert valid deliverable passes; assert non-analysis subtasks (Gates 2..5) proceed unconstrained.
### Test Case 4: Counterpart Parity & Template Verification (`TST-PRO-064.4`)
* **Scope**: Unit Test (`tools/hoechstmass/tools/test_hoechstmass_tools.py`)
* **Action**: Assert Stage 1 templates in root and Höchstmaß contain the 4 impact analysis dimensions; assert Höchstmaß mirror tools maintain 100% functional parity.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-064.1` | Unit | `validate_impact_analysis_section` | `REQ-PRO-064.1`, `REQ-PRO-064.2` | Specified |
| `TST-PRO-064.2` | Unit | `verify_stage1_impact_analysis` (`--check-impact`) | `REQ-PRO-064.2` | Specified |
| `TST-PRO-064.3` | Unit | `gate_verdict` (Stage 1 validation) | `REQ-PRO-064.3`, `REQ-PRO-064.4` | Specified |
| `TST-PRO-064.4` | Parity | `tools/hoechstmass/` mirror & templates | `REQ-PRO-064.1`–`REQ-PRO-064.4` | Specified |
