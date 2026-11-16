# Stage 2: Requirement & Test Specification - ATT-8151: Enforce technical role separation for agent credentials and record producing agent identity in git

**Ticket**: [ATT-8151](https://rainerblind.atlassian.net/browse/ATT-8151)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-16_02_Spät`  
**Requirement Mapping**: `REQ-PRO-108` (*Technical Role Separation for Agent Credentials & Git Commit Identity*; extends `REQ-PRO-060`, `REQ-PRO-041`)  
**Test Spec ID**: `TST-PRO-108`  
**Branch**: `feature/ATT-8151` | **Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-16  

---

## 1. Requirement Specification (REQ-PRO-108)
* **Attributes**: Priority: `High` | Type: `Non-Functional` | Verification Method: `Unit / Review` | Target Status: `[Planned]`
* **REQ-PRO-108.1 (Subagent Identity & Role Binding)**: When the `JIRA_ACTOR` environment variable is defined in the subagent environment, `tools/jira_util.py` SHALL strictly reject any mismatched CLI `--as <role>` override with a role mismatch error. When `JIRA_ACTOR` is unset (manual developer CLI or coordinator invocation), `--as coordinator` and manual `--as <role>` calls SHALL continue to operate without rejection.
* **REQ-PRO-108.2 (Dedicated QA Role Credential Mapping & Account Independence)**: `tools/jira_util.py` SHALL map role `qa` to dedicated credentials (`JIRA_QA_USER`, `JIRA_QA_TOKEN`), support `.env.jira.qa`, include `qa` in `get_config` role credential filtering, and enforce that `agent2` and `qa` operate under distinct Jira user accounts (different account IDs or usernames).
* **REQ-PRO-108.3 (Producing Agent Identity & Reviewer Git Commit Trailers)**: `tools/parallel_sprint_lock.py` (`_merge_ticket_impl` / `--merge-ticket`) SHALL append standard git trailers (`Agent-Role:`, `Agent-Model:`, `Reviewed-by:`) to atomic squash merge commits, auto-detecting values from environment variables (`AGENT_ROLE`, `AGENT_MODEL`, `REVIEWED_BY`, `JIRA_ACTOR`) or CLI arguments, verifiable by automated unit checks.
* **REQ-PRO-108.4 (Single-OS User Residual Risk Documentation)**: `docs/management/risk_register.md` SHALL formally register the residual risk of local subagent process co-location under a single operating system user account and document the layered mitigations (token isolation, role binding, commit trailers, CI gating).

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Extends `REQ-PRO-060` (Composite CLI role operations) and `REQ-PRO-041` (Parallel sprint lock & atomic commit merging) via net-new `REQ-PRO-108`. Targets `tools/jira_util.py`, `tools/parallel_sprint_lock.py`, and `docs/management/risk_register.md`.
* **Historical Origin & Commit Trace**: `ATT-4290` (`REQ-PRO-060`), `ATT-7025` (`REQ-PRO-041`), `ATT-8150` (`REQ-PRO-107`), `ATT-8151`.
* **Root Reason for Existing Formulation**: Permissive `--as` and uniform commit authorship were established for single-developer convenience prior to multi-agent role separation.
* **Preservation of Core Invariants**: Standalone manual CLI usage when `JIRA_ACTOR` is unset remains fully functional; POSIX flock locking and `ANT-Android-SDKs` submodule integrity (`160000 commit 0e8cea714a231fb9565170d4ed01eafa868f9798`) are preserved.

---

## 3. Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Role Binding Enforcement)**: *Given* a subagent process launched with `JIRA_ACTOR=agent1`, *When* it calls `tools/jira_util.py --as agent2 gate-verdict ...`, *Then* the invocation is rejected with a role mismatch error.
* **Criterion 2 (Unset JIRA_ACTOR Backward Compatibility)**: *Given* an execution environment without `JIRA_ACTOR`, *When* `tools/jira_util.py --as coordinator show ATT-8151` is executed, *Then* the command succeeds without role mismatch rejection.
* **Criterion 3 (Dedicated QA Role & Account Separation)**: *Given* configured `agent2` and `qa` credentials, *When* `tools/jira_util.py` resolves accounts, *Then* `qa` resolves to dedicated credentials and accounts are verified distinct.
* **Criterion 4 (Git Commit Trailers)**: *Given* a completed ticket being merged onto the sprint branch via `--merge-ticket`, *When* the atomic squash commit is created, *Then* the commit message contains valid `Agent-Role:`, `Agent-Model:`, and `Reviewed-by:` git trailers.

---

## 4. Test Specification (TST-PRO-108)
* **TST-PRO-108.1 (Subagent Identity & Role Binding Enforcement Test)**: Verify that when `JIRA_ACTOR` is set in the environment, passing a mismatched `--as` flag raises a SystemExit role mismatch error, matching `--as` succeeds, and when `JIRA_ACTOR` is unset, `--as coordinator` and manual roles succeed.
* **TST-PRO-108.2 (Dedicated QA Role Credential Mapping & Account Independence Test)**: Verify `resolve_account("qa")` resolves `JIRA_QA_USER` and `JIRA_QA_TOKEN`, verify `get_config(role="qa")` isolates QA credentials, and verify validation rejects configurations where `agent2` and `qa` share identical accounts.
* **TST-PRO-108.3 (Producing Agent Identity & Reviewer Git Commit Trailers Test)**: Verify `_merge_ticket_impl` formats squash commit messages with standard git trailers (`Agent-Role:`, `Agent-Model:`, `Reviewed-by:`), correctly reading from environment or arguments and passing automated trailer validation.
* **TST-PRO-108.4 (Single-OS User Residual Risk Review Test)**: Verify that `docs/management/risk_register.md` contains an approved entry documenting the residual risk and defense-in-depth controls for co-located subagent execution under a single OS account.

---

## 5. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-108.1` | Unit | `tools/jira_util.py`, `tools/test_jira_accounts.py` | `REQ-PRO-108.1` | Planned |
| `TST-PRO-108.2` | Unit | `tools/jira_util.py`, `tools/test_jira_accounts.py` | `REQ-PRO-108.2` | Planned |
| `TST-PRO-108.3` | Unit | `tools/parallel_sprint_lock.py`, `tools/test_jira_accounts.py` | `REQ-PRO-108.3` | Planned |
| `TST-PRO-108.4` | Review / Unit | `docs/management/risk_register.md`, `tools/test_jira_accounts.py` | `REQ-PRO-108.4` | Planned |
