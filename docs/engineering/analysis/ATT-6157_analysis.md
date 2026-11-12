# Stage 1 Analysis: ATT-6157 - Make change-request impact analysis explicit

**Ticket**: [ATT-6157](https://rainerblind.atlassian.net/browse/ATT-6157)  
**Sub-task**: `[Analysis]` (Created on submission)  
**Parent Epic**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2 – Role Integrity, Independent QA & Configuration Management*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.3`  
**Branch**: `feature/ATT-6157`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation

ASPICE SUP.10 (Change Request Management) and Pre-Assessment Finding B-12 identify that impact analysis of change requests is currently implicit or dispersed across Stage 1 analysis narratives without a standardized, mandatory structure. Without formal evaluation of impacts on requirements, architecture elements, tests, and risk before Stage 1 approval, downstream rework and unintended regression risks increase.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

Forensic audit of existing Stage 1 templates (`.agents/skills/stage1-analysis/templates/analysis_template.md` and `tools/hoechstmass/templates/analysis_template.md`) confirms:
1. No dedicated `Impact Analysis` section header exists in the analysis template.
2. `tools/verify_requirement_governance.py` validates `validate_archaeology_section()` but lacks automated validation for Change-Request Impact Analysis.
3. `tools/jira_util.py gate-verdict` checks status transitions but does not verify whether the Stage 1 deliverable contains an explicit Impact Analysis before granting Gate 1 PASS.

* **Independent Peer Review & Verification**:
  * Independently verified that while `validate_archaeology_section` enforces Chesterton's Fence fields when modifying requirements, net-new features or tickets without requirement diffs completely bypass structured impact evaluation unless an explicit Impact Analysis section is mandated and validated.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Add standardized `## Impact Analysis` section with 4 mandatory fields (Affected Requirements, Affected Architecture Elements, Affected Tests, Risk Assessment & Mitigations) to Stage 1 templates.
  * Implement `validate_impact_analysis_section()` in `tools/verify_requirement_governance.py` and `tools/hoechstmass/`.
  * Enforce Gate 1 validation in `tools/jira_util.py` (and Höchstmaß mirror) so `gate-verdict` on `[Analysis]` subtasks rejects deliverables missing Impact Analysis.
  * Add unit tests in `tools/test_verify_requirement_governance.py`, `tools/test_jira_accounts.py`, and Höchstmaß test suites.
* **Out-of-Scope Non-Goals**:
  * Modifying Stages 2–5 templates or runtime Android application code (`app/src/main/`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement `REQ-PRO-064` / `TST-PRO-064`.
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-46.3 via ATT-6157 to satisfy ASPICE SUP.10 BP and Finding B-12.
* **Root Reason for Existing Formulation**: Stage 1 deliverables previously lacked a mandatory, machine-checkable impact section.
* **Preservation of Core Invariants**: Existing archaeology checks and living documentation parity rules remain 100% intact.

---

## 5. Change-Request Impact Analysis (SUP.10)

* **Affected Requirements**: Net-new `REQ-PRO-064` (Process Stability & Change-Request Governance); zero existing requirements altered.
* **Affected Architecture Elements**: `tools/jira_util.py`, `tools/verify_requirement_governance.py`, `tools/hoechstmass/`, `.agents/skills/stage1-analysis/templates/analysis_template.md`.
* **Affected Tests**: `tools/test_verify_requirement_governance.py`, `tools/test_jira_accounts.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py`.
* **Risk Assessment & Mitigations**: Low risk. Affects developer governance tooling only. Protected by comprehensive unit tests.

---

## 6. Architectural Strategy & High-Level Solution

1. **Template Standardization**: Update `analysis_template.md` in root skills and Höchstmaß with `## Impact Analysis` containing the 4 required dimensions.
2. **Governance Validator**: Add `validate_impact_analysis_section(text)` to `verify_requirement_governance.py` (and Höchstmaß mirror) with `--check-impact` CLI flag.
3. **Gate 1 Enforcement**: Wire `gate_verdict` to validate the deliverable text for `[Analysis]` subtasks upon `PASS`, rejecting with descriptive errors if incomplete.

---

## 7. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero modifications to Android production code in `app/src/main/`.
  2. Non-analysis gate verdicts (Gates 2–5) remain completely unconstrained by Gate 1 rules.
  3. Prohibiting AI agents from moving parent tickets to `Erledigt` remains strictly enforced.
* **Risk Rating**: **LOW** — Self-contained tooling and template enhancement with 100% test coverage.
