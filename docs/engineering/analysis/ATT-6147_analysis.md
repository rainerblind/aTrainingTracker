# Stage 1 Analysis: ATT-6147 - Isolate per-role Jira credentials

**Ticket**: [ATT-6147](https://rainerblind.atlassian.net/browse/ATT-6147)  
**Sub-task**: `[Analysis]` (Created on submission)  
**Parent Epic**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2 – Role Integrity, Independent QA & Configuration Management*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.3`  
**Branch**: `feature/ATT-6147`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation
Under ASPICE SUP.1 (independence) and SUP.8 (configuration management), storing all agent credentials (`agent1`, `agent2`, `coordinator`) in a single shared `.env.jira` file allows any automated process or agent to read credentials belonging to other roles, compromising role segregation (Pre-Assessment Finding B-02). Furthermore, legacy templates contained personal human credentials (`JIRA_USER`/`JIRA_TOKEN`).  
The goal is to eliminate human credentials from the workspace entirely (AC-1) and isolate agent credentials into dedicated, gitignored per-role files (`.env.jira.agent1`, `.env.jira.agent2`, `.env.jira.coordinator`), ensuring that each `--as <role>` invocation loads strictly that role's credentials without cross-role fallback or disclosure (AC-2).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**:
  * `tools/jira_util.py` (Line 184) and `tools/hoechstmass/tools/jira_util.py` (Line 187) hardcode loading from monolithic `.env.jira`.
  * `.gitignore` explicitly listed `.env.jira` rather than `.env.jira*`, risking uncommitted per-role credential leakage.
  * `tools/hoechstmass/.env.jira.example` historically referenced `JIRA_USER` and `JIRA_TOKEN` fallback keys.
* **Independent Codebase Verification**:
  * Audit of `.env.jira`: Contains isolated service account tokens (`JIRA_AGENT1_*`, `JIRA_AGENT2_*`, `JIRA_COORDINATOR_*`), confirming zero human `JIRA_USER`/`JIRA_TOKEN` credentials in the workspace (AC-1 ready).
  * Audit of callers: All Jira CLI actions resolve roles through `parse_role_from_args()` returning `active_role`. Passing `role` into `get_config(role)` enables deterministic, isolated file resolution.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Update `.gitignore` to explicitly ignore `.env.jira*`.
  2. Split `.env.jira` into dedicated per-role files (`.env.jira.agent1`, `.env.jira.agent2`, `.env.jira.coordinator`) with shared non-secret metadata (`JIRA_URL`, `JIRA_PROJECT`).
  3. Update `tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py` so `get_config(role=None)` / `load_env_config(role=None)` dynamically resolves `.env.jira.<role>` when `role` is specified, falling back to `.env.jira` if absent.
  4. Update `tools/hoechstmass/.env.jira.example` to remove `JIRA_USER`/`JIRA_TOKEN` references.
  5. Document per-role architecture in `docs/project_protocol.md`.
  6. Add unit test coverage in `tools/test_jira_accounts.py` for per-role file loading and cross-role isolation.
* **Out-of-Scope Non-Goals**:
  * Zero modifications to Android production runtime code in `app/src/main/`.
  * No modification to Jira Cloud permissions or tenant structure.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-019` (*Multi-Account Jira Authentication, Atomic Resolution & Terminal Error Handling*). Targets `tools/jira_util.py`, `tools/hoechstmass/tools/jira_util.py`, `tools/test_jira_accounts.py`.
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-44 via ATT-4520 (`2b1a9e40`) and refined in ATT-1123, ATT-1200, and ATT-6146 (`51509174`).
* **Root Reason for Existing Formulation**: A single monolithic `.env.jira` was chosen as a transitional stepping stone when migrating from single-account human authentication to multi-agent credentials.
* **Preservation of Core Invariants**: Legitimate agent operations (`agent1`, `agent2`, `coordinator`) continue normal execution; Human Decision Gate remains inviolable; pure Python standard library retained.

---

## 5. Architectural Strategy & High-Level Solution
1. **Gitignore Wildcard**: Update `.gitignore` to ensure all `.env.jira*` files are ignored.
2. **Per-Role Credential Partitioning**: Create `.env.jira.<role>` files containing shared metadata (`JIRA_URL`, `JIRA_PROJECT`) and role-specific keys (`JIRA_<ROLE>_USER`, `JIRA_<ROLE>_TOKEN`).
3. **Role-Aware Config Loader**: Enhance `get_config(role=None)` to check for `.env.jira.<role>` first before falling back to `.env.jira`.
4. **Strict Isolation Enforcement**: If an agent process is provided only with `.env.jira.agent1`, invoking `--as agent2` will find no `agent2` credentials and abort with exit code 1.
5. **Living Documentation & Unit Verification**: Document architecture in `docs/project_protocol.md` and verify via `tools/test_jira_accounts.py`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero regression for existing environment variable injection (`os.environ`), zero production code modifications, and 100% test pass rate across `tools/`.
* **Risk Rating**: **LOW** — Isolated tooling configuration and credential file partitioning with zero impact on Android runtime.
