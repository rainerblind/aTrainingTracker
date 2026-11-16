# Stage 1 Analysis: ATT-8151 - Enforce technical role separation for agent credentials and record producing agent identity in git

**Ticket**: [ATT-8151](https://rainerblind.atlassian.net/browse/ATT-8151)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-16_02_Spät`  
**Branch**: `feature/ATT-8151` | **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-16  

---

## 1. Problem Statement & Motivation
Under ASPICE 4.0 Audit Finding `M4` (SUP.1 BP1, GP 2.1.4, SUP.8 BP4):
1. **Unenforced Role Isolation**: Any agent process can pass any `--as <role>` flag; all `.env.jira.*` token files reside in the same workspace. An implementer agent can trivially impersonate reviewer `agent2` or `coordinator`.
2. **Missing `qa` Role Credential Mapping**: `qa` exists in `VALID_ROLES` but is omitted from `resolve_account` and credential isolation in `get_config`, crashing with missing credentials. Additionally, `agent2` and `qa` are not validated as distinct accounts.
3. **Anonymized Git Commit History**: Commits are authored uniformly by 'Rainer Blind', obscuring the producing agent role and model tier in git history and CI audit logs.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA 1 (`--as` Overrides `JIRA_ACTOR`)**: In `tools/jira_util.py` (L424–445), CLI parameter `--as` unconditionally overrides `JIRA_ACTOR`. If Coordinator binds a subagent to `JIRA_ACTOR=agent1`, the subagent can still invoke `--as agent2 gate-verdict`.
* **RCA 2 (Omitted `qa` Account Resolution)**: In `tools/jira_util.py`, `resolve_account` maps only `agent1`, `agent2`, `coordinator`. Role `qa` has no key mapping, and `get_config` does not strip `JIRA_QA_*` tokens during role isolation.
* **RCA 3 (Missing Commit Trailers in Merge Lock)**: In `tools/parallel_sprint_lock.py` (L284), `_merge_ticket_impl` commits with `git commit -m summary` without recording `Agent-Role:`, `Agent-Model:`, or `Reviewed-by:` trailers.
* **Independent Peer Review & Verification (ATT-3534)**:
  * Audited `tools/jira_util.py`: Confirmed L62 has `qa` in `VALID_ROLES`, but L310 lacks `qa` mapping and L270 omits `qa` from credential isolation.
  * Audited `tools/parallel_sprint_lock.py`: Confirmed single-line commit message without trailer formatting.
  * Audited `docs/management/risk_register.md`: Confirmed single-OS user environment risk must be formally registered.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Enforce role binding: in `tools/jira_util.py`, if `JIRA_ACTOR` is set and caller passes mismatched `--as <role>`, reject immediately (`REQ-PRO-108.1`).
  2. Implement `qa` role credential mapping and isolation in `tools/jira_util.py`, validating that `agent2` and `qa` are distinct Jira accounts (`REQ-PRO-108.2`).
  3. Add `Agent-Role:`, `Agent-Model:`, and `Reviewed-by:` trailers in `tools/parallel_sprint_lock.py` `--merge-ticket` commits (`REQ-PRO-108.3`).
  4. Document single-OS user residual risk in `docs/management/risk_register.md` (`REQ-PRO-108.4`).
  5. Add unit tests for role binding, QA mapping, and commit trailers in `tools/test_jira_accounts.py`.
* **Out-of-Scope Non-Goals**:
  - Operating system multi-user account provisioning (all subagents run in user workspace).
  - Amending historical git commit history (trailers apply forward).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Extends `REQ-PRO-060` (Role operations) and `REQ-PRO-041` (Parallel sprint lock) via net-new `REQ-PRO-108`.
* **Historical Origin & Commit Trace**: `ATT-4290` (`REQ-PRO-060`), `ATT-7025` (`REQ-PRO-041`), `ATT-8150` (`REQ-PRO-107`).
* **Root Reason for Existing Formulation**: Permissive `--as` was designed for convenient CLI testing before multi-agent role separation was formalized.
* **Preservation of Core Invariants**: Unbound manual CLI usage remains functional when `JIRA_ACTOR` is unset; atomic squash merge and `ANT-Android-SDKs` submodule integrity are preserved.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-PRO-108` / `TST-PRO-108`; references `REQ-PRO-060`, `REQ-PRO-041`.
* **Affected Architecture Elements**: `tools/jira_util.py`, `tools/parallel_sprint_lock.py`, `tools/locked_build_merge.py`, `tools/test_jira_accounts.py`, `docs/management/risk_register.md`.
* **Verification Impact & Test Surface**: Unit tests in `tools/test_jira_accounts.py`, `tools/test_flock_serialization.py`, `verify_requirement_governance.py`.
* **Risk Assessment & Invariants**: **MEDIUM** (3 SP, tooling & commit metadata; zero Android runtime code impact).

---

## 6. Architectural Strategy & High-Level Solution
* **Role Binding**: In `tools/jira_util.py get_active_role`, if `os.environ.get("JIRA_ACTOR")` is present and differs from `role_from_cli`, fail with `Error: Role mismatch. Subagent bound to '{env_actor}' via JIRA_ACTOR cannot assume role '{role_from_cli}' (REQ-PRO-108.1)`.
* **QA Credential Mapping**: Add `"qa": ("JIRA_QA_USER", "JIRA_QA_TOKEN")` to `role_mapping` in `resolve_account`; filter `JIRA_QA_*` in `get_config`. Verify account ID difference between `agent2` and `qa`.
* **Commit Trailers**: In `_merge_ticket_impl`, construct trailer block with `Agent-Role:`, `Agent-Model:`, and `Reviewed-by:` (auto-detected from environment or CLI parameters).

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: (1) Permissive role selection when `JIRA_ACTOR` is not set; (2) Atomicity of squash commits; (3) Zero code orphans in living docs; (4) Submodule untouched at `0e8cea714a231fb9565170d4ed01eafa868f9798`.
* **Risk Rating**: **MEDIUM** (Process tooling and git trailer enhancements; verified by unit tests).
