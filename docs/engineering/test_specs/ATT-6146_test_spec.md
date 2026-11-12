# Stage 2: Requirement & Test Specification - ATT-6146: Remove human role from agent-selectable roles in jira_util

**Ticket**: [ATT-6146](https://rainerblind.atlassian.net/browse/ATT-6146)  
**Sub-task**: `[Req & Test Spec]` (Created on submission)  
**Parent Epic**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2 – Role Integrity, Independent QA & Configuration Management*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.3`  
**Requirement Mapping**: `REQ-PRO-019` (*Multi-Account Jira Authentication*), `REQ-PRO-020` (*Role Locking & Anti-Spoofing*)  
**Test Spec ID**: `TST-PRO-012`, `TST-PRO-013`  
**Branch**: `feature/ATT-6146`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`)  
**Date**: 2026-11-12  

---

## 1. Requirement Specification (`REQ-PRO-019`, `REQ-PRO-020`)

### 1.1 Problem Statement & Rationale
`tools/jira_util.py` currently permits `"human"` as an agent-selectable role in `VALID_ROLES`, and `resolve_account()` falls back to personal default credentials (`JIRA_USER`, `JIRA_TOKEN`) when role-specific credentials are missing. This violates ASPICE SUP.1 independence and SUP.10 configuration management, allowing agents to masquerade as the human operator and bypass human decision gates (sprint screening, terminal closure to `Erledigt`, and un-bounced sign-offs).

### 1.2 Functional & Architectural Requirements
1. **Elimination of Human Role from Agent Selection (`REQ-PRO-020.5`)**: `VALID_ROLES` in `tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py` SHALL strictly consist of automated roles: `["agent1", "agent2", "coordinator"]`.
2. **Fatal Governance Abort on Human Role Selection (`REQ-PRO-020.6`)**: If an agent attempts to invoke `jira_util.py` with `--as human`, `--as=human`, or `JIRA_ACTOR=human` (or aliases `user`, `rainer`), the tool SHALL immediately abort with exit code 1 and emit: `Error: 'human' role cannot be selected via agent CLI or environment variable. Human actions must use interactive confirmation or the Jira web UI.`
3. **Strict Non-Human Credential Isolation (`REQ-PRO-019.4`)**: In `resolve_account(role, config)`, for non-human roles (`agent1`, `agent2`, `coordinator`), the system MUST NOT fall back to `(JIRA_USER, JIRA_TOKEN)`. If role credentials are missing or partial, `resolve_account` SHALL abort with exit code 1: `Error: Missing role-specific credentials for role '<role>'. Fallback to human default credentials (JIRA_USER/JIRA_TOKEN) is prohibited under ASPICE SUP.1 role separation.`
4. **Preservation of Ticket Assignment to Human (`REQ-PRO-020.7`)**: Ticket assignment (`assign_issue(..., "human", role=role)`) using `ROLE_ACCOUNT_IDS["human"]` SHALL remain fully functional.
5. **Tooling Synchronization with Höchstmaß (`REQ-PRO-036.5`)**: `tools/hoechstmass/tools/jira_util.py` and its test suite SHALL mirror the identical role validation and credential isolation logic.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-PRO-019` (`REQ-PRO-019.2`, `REQ-PRO-019.4`) and `REQ-PRO-020` (`REQ-PRO-020.1`, `REQ-PRO-020.5`). Targets `tools/jira_util.py`, `tools/hoechstmass/tools/jira_util.py`, `tools/test_jira_accounts.py`, and `docs/requirements/req_process_stability.md`.
* `Historical Origin & Commit Trace`: Introduced in Sprint 2026-44 via ATT-4520 (commit `2b1a9e40`) and refined in ATT-1123 / ATT-1200.
* `Root Reason for Existing Formulation`: Multi-account authentication originally provided a transitional fallback to default `JIRA_USER`/`JIRA_TOKEN` so developers with a single personal API token could run CLI tools before independent agent accounts were provisioned.
* `Preservation of Core Invariants`: Autonomous agent operations using service accounts (`agent1`, `agent2`, `coordinator`) remain 100% operational; ticket transitions to `Final Review (Human)` remain functional; human gate is strictly protected from agent impersonation; zero impact on Android runtime (`app/src/main/`).

### 1.4 Acceptance Criteria (Given-When-Then)
* **AC-1 (Human Role CLI/Env Rejection)**:
  * *Given* an agent runs any command with `--as human`, `--as=human`, or `JIRA_ACTOR=human`
  * *When* `jira_util.py` starts
  * *Then* it aborts with exit code 1 and a fatal governance error message.
* **AC-2 (Zero Fallback to Human Credentials)**:
  * *Given* a non-human role (`agent1`, `agent2`, `coordinator`) and no role-specific credentials in config
  * *When* `resolve_account` runs
  * *Then* it aborts with exit code 1 and does not return or fall back to `(JIRA_USER, JIRA_TOKEN)`.
* **AC-3 (Valid Agent Role Operation)**:
  * *Given* valid role credentials in config
  * *When* `resolve_account` runs for `agent1`, `agent2`, or `coordinator`
  * *Then* it returns the corresponding credentials with exit code 0.
* **AC-4 (Assignment to Human Preserved)**:
  * *Given* `assign_issue(issue_key, "human", role="agent1")`
  * *When* executed
  * *Then* it resolves the account ID from `ROLE_ACCOUNT_IDS["human"]` and assigns the issue.

---

## 2. Test Specification (`TST-PRO-012`, `TST-PRO-013`)

### Test Case 1: Human Role Selection Governance Rejection (`TST-PRO-013.4`)
* **Scope**: Tooling Unit Test (`tools/test_jira_accounts.py`)
* **Action**: Execute `parse_role_from_args` with `--as human`, `--as=human`, and with `JIRA_ACTOR=human`.
* **Expected Result**: Raises `SystemExit(1)` and emits governance error message.

### Test Case 2: Elimination of Default Credential Fallback (`TST-PRO-012.3`)
* **Scope**: Tooling Unit Test (`tools/test_jira_accounts.py`)
* **Action**: Call `resolve_account("agent1", config)` with only `JIRA_USER` and `JIRA_TOKEN` configured.
* **Expected Result**: Raises `SystemExit(1)` and emits credential isolation error without falling back.

### Test Case 3: Legitimate Role Credential Resolution (`TST-PRO-012.1`)
* **Scope**: Tooling Unit Test (`tools/test_jira_accounts.py`)
* **Action**: Call `resolve_account` for `agent1`, `agent2`, and `coordinator` with configured role tokens.
* **Expected Result**: Returns exact role credentials without errors.

### Test Case 4: Höchstmaß Standalone Parity (`TST-PRO-013.5`)
* **Scope**: Tooling Unit Test (`tools/hoechstmass/tools/test_hoechstmass_tools.py`)
* **Action**: Execute `python3 tools/hoechstmass/tools/test_hoechstmass_tools.py`.
* **Expected Result**: 100% pass rate validating identical role locking and credential isolation.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-013.4` | Tooling Unit | `parse_role_from_args` | `REQ-PRO-020.5`, `REQ-PRO-020.6` | Specified |
| `TST-PRO-012.3` | Tooling Unit | `resolve_account` | `REQ-PRO-019.4` | Specified |
| `TST-PRO-012.1` | Tooling Unit | `resolve_account` | `REQ-PRO-019.1` | Specified |
| `TST-PRO-013.5` | Tooling Unit | `test_hoechstmass_tools.py` | `REQ-PRO-036.5` | Specified |
