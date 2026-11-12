# Stage 2: Requirement & Test Specification - ATT-6149: Export Jira gate records into repository at sprint end

**Ticket**: [ATT-6149](https://rainerblind.atlassian.net/browse/ATT-6149)  
**Sub-task**: `[Req & Test Spec]` (Created on submission)  
**Parent Epic**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2 – Role Integrity, Independent QA & Configuration Management*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.3`  
**Requirement Mapping**: `REQ-PRO-059` (*Repository Export of Sprint Gate Records & Independent QA Evidence*)  
**Test Spec ID**: `TST-PRO-059`  
**Branch**: `feature/ATT-6149`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`)  
**Date**: 2026-11-12  

---

## 1. Requirement Specification (`REQ-PRO-059`)

### 1.1 Problem Statement & Rationale
Under ASPICE SUP.1 (Quality Assurance) and GP 2.2 (Work Product Evidence), quality gate approvals and review verdicts currently reside exclusively inside Jira Cloud. An external assessor without Jira tenant access cannot verify review independence or gate integrity. This requirement establishes an automated export tool to serialize sprint gate records into `docs/engineering/gates/<sprint>.md` and adds a governance check enforcing export presence.

### 1.2 Functional & Architectural Requirements
1. **Export Command (`REQ-PRO-059.1`)**: `tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py` SHALL implement `export-gates [--sprint <ID|NAME>] [--out <FILE>]` to dump all Gate 1–5 and Stage 6 verdicts for a sprint.
2. **Markdown Schema (`REQ-PRO-059.2`)**: The export file (`docs/engineering/gates/<sprint>.md`) SHALL record: Sprint ID, ticket key, stage, gate (1–6), verdict (`PASS`/`BOUNCE`/`REVISION`), evaluator role (`agent2`/`human`), actor ID, and ISO timestamp.
3. **Canonical Directory (`REQ-PRO-059.3`)**: `ALLOWED_ENGINEERING_ENTRIES` in `tools/verify_requirement_governance.py` and `tools/hoechstmass/hoechstmass_config.json` SHALL include `"gates"`.
4. **Governance Enforcement (`REQ-PRO-059.4`)**: `tools/verify_requirement_governance.py` SHALL support `--check-gates [SPRINT]`, failing with exit code 1 if the sprint gate record is missing or empty.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Net-new requirement `REQ-PRO-059` (`REQ-PRO-059.1`–`REQ-PRO-059.4`). Targets `tools/jira_util.py`, `tools/hoechstmass/tools/jira_util.py`, `tools/verify_requirement_governance.py`, and `docs/engineering/gates/`.
* `Historical Origin & Commit Trace`: Introduced in Sprint 2026-46.3 via ATT-6149 under Epic ATT-6141 to resolve ASPICE pre-assessment finding B-04.
* `Root Reason for Existing Formulation`: Gate verdicts previously existed only inside Jira Cloud REST API, lacking in-repo configuration management and QA evidence.
* `Preservation of Core Invariants`: Zero modifications to Android production runtime code in `app/src/main/`; pure Python standard library retained; offline/mock resilience preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **AC-1 (Gate Export Execution)**:
  * *Given* sprint `sprint/2026-46.3` with evaluated tickets
  * *When* `tools/jira_util.py export-gates` is invoked
  * *Then* it generates `docs/engineering/gates/sprint_2026-46.3.md` listing every gate verdict with role, actor, timestamp, and verdict.
* **AC-2 (Missing Export Gate Failure)**:
  * *Given* a sprint name without an export file in `docs/engineering/gates/`
  * *When* `verify_requirement_governance.py --check-gates <sprint>` runs
  * *Then* it aborts with exit code 1.
* **AC-3 (Canonical Directory Compliance)**:
  * *Given* directory `docs/engineering/gates/`
  * *When* `verify_requirement_governance.py` verifies engineering directory structure
  * *Then* `"gates"` is accepted without errors.

---

## 2. Test Specification (`TST-PRO-059`)

### Test Case 1: export-gates CLI Command & Markdown Generation (`TST-PRO-059.1`)
* **Scope**: Unit Test (`tools/test_jira_accounts.py`)
* **Action**: Execute `export_gates` with mock Jira response; verify generated markdown contains all verdicts and metadata.

### Test Case 2: Post-Sprint Gate Verification Check (`TST-PRO-059.2`)
* **Scope**: Unit Test (`tools/test_verify_requirement_governance.py`)
* **Action**: Run `verify_gate_records` against valid vs missing sprint files; verify exit codes 0 and 1.

### Test Case 3: Canonical Directory Registration (`TST-PRO-059.3`)
* **Scope**: Unit Test (`tools/test_verify_requirement_governance.py`)
* **Action**: Assert `"gates"` exists in `ALLOWED_ENGINEERING_ENTRIES` and `hoechstmass_config.json`.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-059.1` | Unit | `export_gates` | `REQ-PRO-059.1`, `REQ-PRO-059.2` | Specified |
| `TST-PRO-059.2` | Unit | `verify_gate_records` | `REQ-PRO-059.4` | Specified |
| `TST-PRO-059.3` | Unit | `verify_engineering_directory_structure` | `REQ-PRO-059.3` | Specified |
