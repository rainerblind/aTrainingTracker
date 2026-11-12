# Stage 3: Implementation Plan - ATT-6147: Isolate per-role Jira credentials

**Ticket**: [ATT-6147](https://rainerblind.atlassian.net/browse/ATT-6147) | **Sub-task**: `[Impl-Plan]` | **Parent**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2*)  
**Target Release**: `V5.0.0` | **Sprint**: `sprint/2026-46.3` | **Branch**: `feature/ATT-6147`  
**Requirement Mapping**: `REQ-PRO-019` (`REQ-PRO-019.5`, `REQ-PRO-019.6`, `REQ-PRO-019.7`, `REQ-PRO-019.8`)  
**Test Mapping**: `TST-PRO-012` (`TST-PRO-012.4`, `TST-PRO-012.5`, `TST-PRO-012.6`), `TST-PRO-036.4`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-12  

---

## 1. Problem Description & Background
Monolithic credential storage (`.env.jira`) exposes all role tokens to all agents, violating ASPICE SUP.1 (independence) and SUP.8 (configuration management). Human credentials (`JIRA_USER`/`JIRA_TOKEN`) must be eliminated from workspace and examples (AC-1), and agent credentials partitioned into gitignored per-role files (`.env.jira.agent1`, `.env.jira.agent2`, `.env.jira.coordinator`) loaded strictly per `--as <role>` invocation without cross-role disclosure or fallback (AC-2).

---

## 2. Traceability & Requirements Mapping
* **Requirements**:
  * `REQ-PRO-019.5`: `.gitignore` updated to wildcard `.env.jira*`.
  * `REQ-PRO-019.6`: Per-role files (`.env.jira.<role>`) with shared non-secret config.
  * `REQ-PRO-019.7`: Role-aware config resolution in `get_config(role=None)`.
  * `REQ-PRO-019.8`: Zero human credentials in workspace and examples.
* **Test Specs**: `TST-PRO-012.4` (role file loading & isolation), `TST-PRO-012.5` (`.gitignore` wildcard), `TST-PRO-012.6` (Höchstmaß parity), `TST-PRO-036.4` (Gradle tooling contract).

---

## 3. System Invariants & Preserved Behavior
1. **Backward Compatibility**: Single `.env.jira` fallback preserved if per-role files are absent.
2. **Environment Variable Override**: `os.environ` keys continue to take precedence.
3. **Pure Standard Library**: Zero external Python pip dependencies.
4. **Android Runtime Isolation**: Zero modifications to production source code in `app/src/main/`.

---

## 4. Proposed Architectural Changes
* **Component 1 (Git Protection & Credential Partitioning)**: Update `.gitignore` with `.env.jira*`. Partition `.env.jira` into `.env.jira.agent1`, `.env.jira.agent2`, and `.env.jira.coordinator` containing shared metadata (`JIRA_URL`, `JIRA_PROJECT`) and isolated role tokens.
* **Component 2 (Role-Aware Config Loading - `tools/jira_util.py`)**: Update `get_config(role=None)` to inspect and load `.env.jira.<role>` when `role` is supplied, merging with base `.env.jira`. Update `resolve_account(role, config=None)` to call `get_config(role=role)`.
* **Component 3 (Höchstmaß Framework Synchronization)**: Mirror role-aware loading in `tools/hoechstmass/tools/jira_util.py`. Clean `tools/hoechstmass/.env.jira.example` of `JIRA_USER`/`JIRA_TOKEN` human credentials.
* **Component 4 (Documentation & Living Protocol)**: Update `docs/project_protocol.md` and living requirements in `docs/requirements/req_process_stability.md` and `docs/tests/tst_process_stability.md`.
* **UI Consistency**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Step 1: Gitignore Hardening & Per-Role File Creation**: Update `.gitignore` with `.env.jira*`. Partition `.env.jira` into `.env.jira.agent1`, `.env.jira.agent2`, and `.env.jira.coordinator`.
* **Step 2: Role-Aware Loader in `tools/jira_util.py`**: Update `get_config(role=None)` and `resolve_account(role, config=None)` to load role-specific files.
* **Step 3: Höchstmaß Mirroring & Example Scrubbing**: Update `tools/hoechstmass/tools/jira_util.py` and clean `tools/hoechstmass/.env.jira.example`.
* **Step 4: Living Documentation & Protocol Sync**: Update `docs/project_protocol.md`, `req_process_stability.md`, and `tst_process_stability.md`.
* **Step 5: Test Suite Expansion & Verification**: Add test cases for per-role isolation and gitignore in `tools/test_jira_accounts.py` and `test_hoechstmass_tools.py`.
  * Commands: `python3 -m unittest tools/test_jira_accounts.py && PYTHONPATH=tools/hoechstmass/tools python3 -m unittest tools/hoechstmass/tools/test_hoechstmass_tools.py && python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: 100% pass across all unit tests in `tools/`, `verify_requirement_governance.py` exits 0, and `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.HoechstmassToolingContractTest"` passes.
* **Rollback**: `git reset --hard HEAD` on isolated branch `feature/ATT-6147`.
