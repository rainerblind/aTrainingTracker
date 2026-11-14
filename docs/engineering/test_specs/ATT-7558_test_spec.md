# Stage 2: Requirement & Test Specification - ATT-7558: Make SUP.1 QA audits independent and track corrective actions

**Ticket**: [ATT-7558](https://rainerblind.atlassian.net/browse/ATT-7558)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*ASPICE Governance & Process Stability*)  
**Target Release**: `V5.1.0`  
**Active Sprint**: `2026-46.6`  
**Requirement Mapping**: `REQ-PRO-091` (*Independent ASPICE SUP.1 QA Auditing, Representative Sampling & Corrective Action Tracking*)  
**Test Spec ID**: `TST-PRO-091` (*Independent ASPICE SUP.1 QA Auditing, Representative Sampling & Corrective Action Verification Suite*)  
**Branch**: `feat/ATT-7558`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Requirement Specification (REQ-PRO-091)

### 1.1 Problem Statement & Rationale
ASPICE evaluation finding F1 identified three major weaknesses in process quality assurance: auditor role independence (audits were authored by `coordinator`), under-sampling (static 2-ticket sample regardless of sprint size), and open-loop non-compliances (prior non-compliant findings had no tracked Jira corrective action tickets and were never reviewed at sprint planning). `REQ-PRO-091` mandates auditor role independence (`qa` / `agent2`), dynamic sample size calculation ($\ge \max(2, \lceil 0.10 \times N \rceil)$), mandatory Jira corrective action links for non-compliant findings, and Ceremony 1 verification of corrective action closure.

### 1.2 Attributes
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review

### 1.3 Functional & Architectural Requirements
1. *Independent QA Auditor Role Enforcement (`REQ-PRO-091.1`)*: The QA process audit tool (`qa-audit`) and audit records in `docs/engineering/qa_audits/<sprint>.md` SHALL enforce an independent auditor role (`qa` / `agent2`) distinct from `coordinator` and `agent1`. Execution under `coordinator` or `agent1` SHALL be prohibited.
2. *Representative Statistical Sampling Ratio (`REQ-PRO-091.2`)*: `qa-audit` SHALL dynamically calculate sample size as $\ge \max(2, \lceil 0.10 \times N \rceil)$ completed parent sprint tickets, documenting the population, sample size, and statistical/risk rationale in Section 1.
3. *Closed-Loop Jira Corrective-Action Tracking (`REQ-PRO-091.3`)*: For any audit evaluating a `NON-COMPLIANT` overall verdict or finding, Section 4 of `docs/engineering/qa_audits/<sprint>.md` SHALL link every non-compliant finding to a dedicated Jira corrective-action ticket (`[ATT-XXXX](...)`).
4. *Ceremony 1 Corrective Action Closure Verification (`REQ-PRO-091.4`)*: `.agents/skills/sprint-planner/SKILL.md` Ceremony 1 SHALL mandate reviewing prior sprint QA audits and verifying that all corrective-action tickets from non-compliant findings are closed or actively scheduled.
5. *Automated Governance Verifier Enforcement (`REQ-PRO-091.5`)*: `tools/verify_requirement_governance.py --check-qa-audits` (and Höchstmaß mirror) SHALL validate auditor independence, sample proportion, and valid Jira issue links for all non-compliant findings.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Auditor Independence)**: *Given* a sprint QA audit, *When* recorded, *Then* the auditor role is `qa` or `agent2`, and `coordinator` is rejected.
* **Criterion 2 (Representative Sample)**: *Given* a sprint with $N$ completed parent tickets, *When* `qa-audit` executes, *Then* sample size is $\ge \max(2, \lceil 0.10 \times N \rceil)$ with documented rationale.
* **Criterion 3 (Corrective Action Linkage)**: *Given* a `NON-COMPLIANT` verdict, *When* the audit report is filed, *Then* each non-compliant finding links to an authentic Jira corrective action ticket.
* **Criterion 4 (Ceremony 1 Verification)**: *Given* Ceremony 1 at Sprint-Start, *When* executed, *Then* open corrective action tickets from prior audits are inspected for closure.

### 1.5 System Invariants
- Zero Android application runtime bytecode modifications in `app/src/main/`.
- Pure Python 3 standard library tooling.
- Rule 3 human-only gate preserved.

---

## 2. Test Specification (TST-PRO-091)

### Test Case 1: QA Auditor Role Independence & Coordinator Rejection (`TST-PRO-091.1`)
* **Scope**: Unit Test
* **Target File**: `tools/test_jira_accounts.py`
* **Preconditions**: Jira mock configured with active sprint.
* **Action**: Execute `jira_util.qa_audit` with `role="qa"` and `role="coordinator"`.
* **Expected Result**: `role="qa"` generates report successfully; `role="coordinator"` or `role="agent1"` raises `ValueError` / exits with error.

### Test Case 2: Representative Sample Size Dynamic Scaling (`TST-PRO-091.2`)
* **Scope**: Unit Test
* **Target File**: `tools/test_jira_accounts.py`
* **Preconditions**: Jira mock returns 25 completed tickets.
* **Action**: Execute `jira_util.qa_audit` without manual sample override.
* **Expected Result**: Samples at least $\lceil 25 \times 0.10 \rceil = 3$ tickets, documenting population and sampling rationale.

### Test Case 3: Corrective Action Link Validation in Verifier (`TST-PRO-091.3`)
* **Scope**: Unit Test
* **Target File**: `tools/test_verify_requirement_governance.py`
* **Preconditions**: Report with `NON-COMPLIANT` findings.
* **Action**: Run `verify_qa_audits` against report without Jira keys vs report with valid `ATT-XXXX` links.
* **Expected Result**: Fails when ticket links are missing; passes when all non-compliant findings link to Jira keys.

### Test Case 4: Auditor Independence Verification in Verifier (`TST-PRO-091.4`)
* **Scope**: Unit Test
* **Target File**: `tools/test_verify_requirement_governance.py`
* **Preconditions**: Report with `Auditor Role: coordinator` vs `Auditor Role: qa`.
* **Action**: Run `verify_qa_audits`.
* **Expected Result**: Fails on `coordinator` or `agent1`; passes on `qa` or `agent2`.

### Test Case 5: Counterpart Mirror Parity (`TST-PRO-091.5`)
* **Scope**: Unit Test
* **Target File**: `tools/hoechstmass/tools/test_hoechstmass_tools.py`
* **Action**: Run unit tests in Höchstmaß mirror verifying identical behavior for `qa_audit` and `verify_qa_audits`.
* **Expected Result**: 100% test pass rate.

### Test Case 6: Governance Verification & Living Doc Sync Suite (`TST-PRO-091.6`)
* **Scope**: Governance Verification
* **Target File**: `tools/verify_requirement_governance.py`
* **Action**: Run `python3 tools/verify_requirement_governance.py`.
* **Expected Result**: Exit code 0, 100% parity verified.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-091.1` | Unit | `test_qa_audit_enforces_independent_role` | `REQ-PRO-091.1` | Verified |
| `TST-PRO-091.2` | Unit | `test_qa_audit_dynamic_sample_size` | `REQ-PRO-091.2` | Verified |
| `TST-PRO-091.3` | Unit | `test_verify_qa_audits_requires_corrective_action_links` | `REQ-PRO-091.3` | Verified |
| `TST-PRO-091.4` | Unit | `test_verify_qa_audits_rejects_coordinator_role` | `REQ-PRO-091.4` | Verified |
| `TST-PRO-091.5` | Unit | `test_hoechstmass_qa_audit_and_verify_parity` | `REQ-PRO-091.5` | Verified |
| `TST-PRO-091.6` | Governance | `tools/verify_requirement_governance.py` | `REQ-PRO-091.5` | Verified |
