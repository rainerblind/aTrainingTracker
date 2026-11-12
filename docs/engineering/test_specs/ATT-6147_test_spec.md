# Stage 2: Requirement & Test Specification - ATT-6147: Isolate per-role Jira credentials

**Ticket**: [ATT-6147](https://rainerblind.atlassian.net/browse/ATT-6147)  
**Sub-task**: `[Req & Test Spec]` (Created on submission)  
**Parent Epic**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2 – Role Integrity, Independent QA & Configuration Management*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.3`  
**Requirement Mapping**: `REQ-PRO-019` (*Multi-Account Jira Authentication, Atomic Resolution & Terminal Error Handling*)  
**Test Spec ID**: `TST-PRO-012`  
**Branch**: `feature/ATT-6147`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`)  
**Date**: 2026-11-12  

---

## 1. Requirement Specification (`REQ-PRO-019`)

### 1.1 Problem Statement & Rationale
Under ASPICE SUP.1 (independence) and SUP.8 (configuration management), storing all agent credentials (`agent1`, `agent2`, `coordinator`) in a single shared `.env.jira` compromises role segregation, allowing any process to inspect other roles' tokens. Furthermore, `.gitignore` previously matched only `.env.jira` rather than `.env.jira*`. This requirement isolates credentials into dedicated per-role files (`.env.jira.<role>`), eliminates human credentials from the workspace, and updates `.gitignore` to prevent secret leakage.

### 1.2 Functional & Architectural Requirements
1. **Gitignore Wildcard (`REQ-PRO-019.5`)**: `.gitignore` SHALL ignore all role credential variants using `.env.jira*`.
2. **Per-Role Credential Partitioning (`REQ-PRO-019.6`)**: Credentials SHALL be partitioned into `.env.jira.agent1`, `.env.jira.agent2`, `.env.jira.coordinator`. Shared metadata (`JIRA_URL`, `JIRA_PROJECT`) SHALL remain in `.env.jira` or duplicated.
3. **Role-Aware Configuration Resolution (`REQ-PRO-019.7`)**: `get_config(role=None)` in `tools/jira_util.py` and `load_env_config(role=None)` in `tools/hoechstmass/tools/jira_util.py` SHALL load `.env.jira.<role>` when `role` is specified, overlaying shared `.env.jira` without cross-role credential disclosure.
4. **Zero Human Credentials (`REQ-PRO-019.8`)**: Workspace and template files (`tools/hoechstmass/.env.jira.example`) SHALL contain zero `JIRA_USER`/`JIRA_TOKEN` human credentials.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-PRO-019` (`REQ-PRO-019.1`, `REQ-PRO-019.5`). Targets `tools/jira_util.py`, `tools/hoechstmass/tools/jira_util.py`, `.gitignore`, `tools/test_jira_accounts.py`.
* `Historical Origin & Commit Trace`: Introduced in Sprint 2026-44 via ATT-4520 (`2b1a9e40`) and refined in ATT-1123, ATT-1200, and ATT-6146 (`51509174`).
* `Root Reason for Existing Formulation`: A single monolithic `.env.jira` was chosen as a transitional stepping stone when migrating from single-account human authentication to multi-agent credentials.
* `Preservation of Core Invariants`: Legitimate agent operations (`agent1`, `agent2`, `coordinator`) continue normal execution; Human Decision Gate remains inviolable; pure Python standard library retained.

### 1.4 Acceptance Criteria (Given-When-Then)
* **AC-1 (Zero Human Credentials)**:
  * *Given* workspace `.env.jira*` and `tools/hoechstmass/.env.jira.example`
  * *When* inspected for `JIRA_USER` / `JIRA_TOKEN`
  * *Then* human credentials are absent.
* **AC-2 (Per-Role Isolation)**:
  * *Given* an environment provided only with `.env.jira.agent1`
  * *When* `jira_util.py` is invoked with `--as agent2`
  * *Then* it aborts with exit code 1 without falling back or leaking credentials.
* **AC-3 (Multi-Role Coexistence)**:
  * *Given* `.env.jira.agent1`, `.env.jira.agent2`, `.env.jira.coordinator` present
  * *When* commands run with `--as <role>`
  * *Then* each command authenticates strictly with its designated role credentials.

---

## 2. Test Specification (`TST-PRO-012`)

### Test Case 1: Per-Role Credential File Loading & Isolation (`TST-PRO-012.4`)
* **Scope**: Unit Test (`tools/test_jira_accounts.py`)
* **Action**: Create mock `.env.jira.agent1` and `.env.jira.agent2`; verify `get_config("agent1")` loads only agent1 keys; verify `--as agent2` aborts when agent2 file is missing.
* **Expected Result**: Exact role isolation, exit code 1 on cross-role access.

### Test Case 2: Gitignore Wildcard Verification (`TST-PRO-012.5`)
* **Scope**: Unit Test (`tools/test_jira_accounts.py`)
* **Action**: Verify `.gitignore` contains `.env.jira*`.
* **Expected Result**: Assert pattern match.

### Test Case 3: Höchstmaß Parameterized Parity (`TST-PRO-012.6`)
* **Scope**: Unit Test (`tools/hoechstmass/tools/test_hoechstmass_tools.py`)
* **Action**: Verify `load_env_config(role="agent1")` loads per-role config.
* **Expected Result**: 100% test pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-012.4` | Unit | `get_config` | `REQ-PRO-019.6`, `REQ-PRO-019.7` | Specified |
| `TST-PRO-012.5` | Unit | `.gitignore` inspection | `REQ-PRO-019.5` | Specified |
| `TST-PRO-012.6` | Unit | `load_env_config` | `REQ-PRO-019.7` | Specified |
| `TST-PRO-036.4` | Contract | `HoechstmassToolingContractTest` | `REQ-PRO-019.8` | Specified |
