# Stage 3: Implementation Plan - ATT-8151: Enforce technical role separation for agent credentials and record producing agent identity in git

**Ticket**: [ATT-8151](https://rainerblind.atlassian.net/browse/ATT-8151)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-16_02_Spät`  
**Requirement Mapping**: `REQ-PRO-108` (`REQ-PRO-108.1`..`REQ-PRO-108.4`)  
**Test Mapping**: `TST-PRO-108` (`TST-PRO-108.1`..`TST-PRO-108.4`)  
**Branch**: `feature/ATT-8151` | **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-16  

---

## 1. Problem Description & Background
ASPICE 4.0 Audit Finding `M4` (SUP.1 BP1, GP 2.1.4, SUP.8 BP4) identified that role separation between implementer and reviewer existed only at the prompt level. Any agent could assume any role via `--as <role>`, role `qa` lacked credential mapping and shared the `agent2` account ID, and git commits lacked producing agent/model provenance. Technical role binding, dedicated QA credentials, commit trailers, and residual risk documentation must be implemented.

---

## 2. Traceability & Requirements Mapping
* **`REQ-PRO-108.1` $\rightarrow$ `TST-PRO-108.1`**: `tools/jira_util.py` rejects mismatched `--as` when `JIRA_ACTOR` is set; preserves fallback when unset for coordinator/interactive calls.
* **`REQ-PRO-108.2` $\rightarrow$ `TST-PRO-108.2`**: `tools/jira_util.py` maps `qa` to dedicated credentials (`JIRA_QA_*`), isolates them in `get_config`, and enforces distinct account IDs between `agent2` and `qa`.
* **`REQ-PRO-108.3` $\rightarrow$ `TST-PRO-108.3`**: `tools/parallel_sprint_lock.py` (`_merge_ticket_impl`) appends standard git trailers (`Agent-Role:`, `Agent-Model:`, `Reviewed-by:`) to squash merge commits.
* **`REQ-PRO-108.4` $\rightarrow$ `TST-PRO-108.4`**: `docs/management/risk_register.md` documents `RISK-007` (single-OS user subagent co-location residual risk) with layered mitigations.

---

## 3. System Invariants & Preserved Behavior
1. **Interactive & Coordinator Fallback**: Manual CLI usage and `--as coordinator` continue working without rejection when `JIRA_ACTOR` is unset.
2. **QA Audit Compatibility**: Existing `qa-audit --as agent2` invocations remain functional alongside dedicated `qa` role support.
3. **Atomic Merge & Submodule Invariants**: Preserves POSIX flock serialization and `ANT-Android-SDKs` at `160000 commit 0e8cea714a231fb9565170d4ed01eafa868f9798`.
4. **Living Docs Governance**: Maintains 100% index parity and zero code orphans.

---

## 4. Proposed Architectural Changes
* **Role Binding (`tools/jira_util.py`)**:
  - In `get_active_role`, check if `os.environ.get("JIRA_ACTOR")` is defined. If set and `role_from_cli.lower() != env_actor.lower()`, exit with `Role mismatch (REQ-PRO-108.1)`.
* **QA Credentials & Isolation (`tools/jira_util.py`)**:
  - Add `"qa": ("JIRA_QA_USER", "JIRA_QA_TOKEN")` to `role_mapping` in `resolve_account`.
  - Update `ROLE_ACCOUNT_IDS["qa"]` to a distinct account ID and include `"qa"` in `get_config` role credential filtering.
  - Implement `validate_account_separation` asserting `agent2` and `qa` accounts are distinct.
* **Git Commit Trailers (`tools/parallel_sprint_lock.py`)**:
  - In `_merge_ticket_impl`, append formatted `Agent-Role:`, `Agent-Model:`, and `Reviewed-by:` trailers to `summary` before committing.
  - Add CLI arguments `--agent-role`, `--agent-model`, `--reviewed-by` to `parallel_sprint_lock.py`.
* **Risk Register Update (`docs/management/risk_register.md`)**:
  - Add `RISK-007` documenting single-OS user process co-location residual risk and mitigations.
* **UI Consistency**: No UI changes (process tooling and git merge scripts).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Step 1 (`REQ-PRO-108.1`)**: Implement `JIRA_ACTOR` binding and rejection logic in `tools/jira_util.py` `get_active_role`.
* **Step 2 (`REQ-PRO-108.2`)**: Add `qa` credential mapping, credential filtering in `get_config`, distinct account ID in `ROLE_ACCOUNT_IDS`, and `validate_account_separation`.
* **Step 3 (`REQ-PRO-108.3`)**: Implement git commit trailer formatting in `tools/parallel_sprint_lock.py` `_merge_ticket_impl` and add trailer parser/validation helper.
* **Step 4 (`REQ-PRO-108.4`)**: Add `RISK-007` entry to `docs/management/risk_register.md`.
* **Step 5 (`TST-PRO-108.1..4`)**: Add comprehensive unit tests in `tools/test_jira_accounts.py` and `tools/test_flock_serialization.py`.
  - Test Command: `python3 -m unittest tools/test_jira_accounts.py tools/test_flock_serialization.py`
* **Step 6 (Living Docs Promotion)**: Update `REQ-PRO-108` and `TST-PRO-108` to `[Implemented]`; run `python3 tools/verify_requirement_governance.py --sync-indexes`.

---

## 6. Verification & Rollback Plan
* **Verification**: Run `test_jira_accounts.py`, `test_flock_serialization.py`, `ReleaseCiAndLintConfigContractTest`, and all governance checks.
* **Rollback**: Branch isolation on `feature/ATT-8151` allows clean revert without impacting `sprint/2026-11-16_02_Spät`.
