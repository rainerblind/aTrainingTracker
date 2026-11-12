# Stage 1 Analysis: ATT-6146 - Remove human role from agent-selectable roles in jira_util

**Ticket**: [ATT-6146](https://rainerblind.atlassian.net/browse/ATT-6146)  
**Sub-task**: `[Analysis]` (Created on submission)  
**Parent Epic**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2 – Role Integrity, Independent QA & Configuration Management*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.3`  
**Branch**: `feature/ATT-6146`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation
In `tools/jira_util.py`, `VALID_ROLES` currently includes `"human"` alongside automated roles (`"agent1"`, `"agent2"`, `"coordinator"`). Furthermore, `resolve_account(role, config)` silently falls back to the human's personal credentials (`JIRA_USER`, `JIRA_TOKEN`) when role-specific credentials are unset or incomplete.  
This creates a critical governance vulnerability (Pre-Assessment Finding B-01, ASPICE SUP.1 & SUP.10): an automated agent can supply `--as human` or `JIRA_ACTOR=human` to impersonate the human user, or invoke commands without role credentials to execute API actions under the human's identity, effectively bypassing human decision gates (sprint screening, terminal closure to `Erledigt`, and un-bounced sign-offs).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**:
  * `tools/jira_util.py` (Line 31) defines `VALID_ROLES = ["agent1", "agent2", "coordinator", "human"]`.
  * `parse_role_from_args()` permits `--as human` and `JIRA_ACTOR=human`.
  * `resolve_account()` (Lines 230-237) falls back to `config.get("JIRA_USER")` and `config.get("JIRA_TOKEN")` whenever role-specific keys are missing, blurring the boundary between automated agents and the human operator.
  * Similar fallback and `VALID_ROLES` structure exists in `tools/hoechstmass/tools/jira_util.py` (Lines 81, 265-290).
* **Independent Codebase Verification**:
  * Audit of `.env.jira`: Contains isolated service account tokens (`JIRA_AGENT1_*`, `JIRA_AGENT2_*`, `JIRA_COORDINATOR_*`), demonstrating that agent-specific credentials already exist in practice and fallback is neither required nor acceptable.
  * Audit of call sites: Ticket assignment to `"human"` (e.g. `assign_issue(..., "human", role=role)`) uses `ROLE_ACCOUNT_IDS["human"]` where caller `role` is an agent (`agent1` or `agent2`). Removing `"human"` from `VALID_ROLES` does not break assigning issues to the human.
  * Audit of human actions: Real human actions occur via the Jira web UI or dedicated human-guarded paths, not via agent CLI flags.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Remove `"human"` from `VALID_ROLES` in `tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py`.
  2. In `parse_role_from_args()`, explicitly reject `--as human`, `--as=human`, and `JIRA_ACTOR=human` with a fatal governance error message (AC-1).
  3. In `resolve_account()`, eliminate fallback to `(JIRA_USER, JIRA_TOKEN)` for non-human roles (`agent1`, `agent2`, `coordinator`). Abort immediately if role credentials are missing or partial (AC-2).
  4. Update `tools/test_jira_accounts.py` and `tools/hoechstmass/tools/test_hoechstmass_tools.py` to enforce AC-1 and AC-2 and remove outdated fallback tests.
  5. Refine process requirements `REQ-PRO-019` and `REQ-PRO-020` in `docs/requirements/req_process_stability.md` and test specs in `docs/tests/tst_process_stability.md` with Chesterton's Fence archaeology.
* **Out-of-Scope Non-Goals**:
  * Retain `ROLE_ACCOUNT_IDS["human"]` for ticket assignment.
  * No modification to runtime Android code (`app/src/main/`).
  * No modification to Jira cloud permissions or Atlassian tenant settings.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-019` (*Multi-Account Jira Authentication, Atomic Resolution & Terminal Error Handling*) and `REQ-PRO-020` (*Role Locking, Precedence, Anti-Spoofing & Actor Comment Attribution*). Targets `tools/jira_util.py`, `tools/test_jira_accounts.py`, `docs/requirements/req_process_stability.md`.
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-44 via ATT-4520 (`2b1a9e40`) and refined in ATT-1123 / ATT-1200.
* **Root Reason for Existing Formulation**: Multi-account authentication initially needed a transitional fallback to `JIRA_USER`/`JIRA_TOKEN` so developers with single personal API tokens could adopt the CLI before separate agent service accounts were provisioned.
* **Preservation of Core Invariants**: Eliminating the fallback and removing `"human"` from agent-selectable roles reinforces the inviolability of the Human Decision Gate, SUP.1 independence, and prevents accidental or malicious role spoofing.

---

## 5. Architectural Strategy & High-Level Solution
1. **Restrict Agent Roles**: Define `VALID_ROLES = ["agent1", "agent2", "coordinator"]`.
2. **Deterministic CLI Governance Abort (AC-1)**:
   In `parse_role_from_args()`: If `--as` or `JIRA_ACTOR` resolves to `"human"`, `"user"`, or `"rainer"`, print diagnostic:
   `Error: 'human' role cannot be selected via agent CLI or environment variable. Human actions must use interactive confirmation or the Jira web UI.` and exit with code 1.
3. **Strict Role Credential Isolation (AC-2)**:
   In `resolve_account(role, config)`: For agent roles, strictly require `JIRA_{ROLE}_USER` and `JIRA_{ROLE}_TOKEN`. If either is missing, print:
   `Error: Missing role-specific credentials for role '{role}'. Fallback to human default credentials (JIRA_USER/JIRA_TOKEN) is prohibited under ASPICE SUP.1 role separation.` and exit with code 1.
4. **Mirror to Höchstmaß**: Apply identical safeguards to `tools/hoechstmass/tools/jira_util.py`.
5. **Test Suite Alignment**: Update `tools/test_jira_accounts.py` and `tools/hoechstmass/tools/test_hoechstmass_tools.py` to assert AC-1 rejection and AC-2 rejection of human fallback for agent roles.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Legitimately configured agent roles (`agent1`, `agent2`, `coordinator`) continue normal operation.
  2. Ticket assignment to human (`assign_issue(..., "human")`) remains functional.
  3. `tools/verify_requirement_governance.py` passes with exit code 0.
  4. Production code (`app/src/main/`) remains untouched.
* **Risk Rating**: **LOW** — Localized tooling and process governance hardening with zero impact on Android runtime.
