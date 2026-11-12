# Stage 3: Implementation Plan - ATT-6146: Remove human role from agent-selectable roles in jira_util

**Ticket**: [ATT-6146](https://rainerblind.atlassian.net/browse/ATT-6146) | **Sub-task**: `[Impl-Plan]` | **Parent**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2*)  
**Target Release**: `V5.0.0` | **Sprint**: `sprint/2026-46.3` | **Branch**: `feature/ATT-6146`  
**Requirement Mapping**: `REQ-PRO-019` (`REQ-PRO-019.4`), `REQ-PRO-020` (`REQ-PRO-020.5`, `REQ-PRO-020.6`, `REQ-PRO-020.7`), `REQ-PRO-036` (`REQ-PRO-036.5`)  
**Test Mapping**: `TST-PRO-012` (`TST-PRO-012.1`, `TST-PRO-012.3`), `TST-PRO-013` (`TST-PRO-013.4`, `TST-PRO-013.5`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-12  

---

## 1. Problem Description & Background
`VALID_ROLES` in `tools/jira_util.py` includes `"human"`, and `resolve_account` silently falls back to the human's credentials (`JIRA_USER`, `JIRA_TOKEN`) when role-specific credentials are missing. This allows automated agents to run `--as human` or unset role tokens to execute actions under the human operator's identity, bypassing human decision gates (sprint screening, terminal closure to `Erledigt`, and un-bounced sign-offs), violating ASPICE SUP.1 (independence) and SUP.10 (pre-assessment finding B-01).

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-PRO-020.5` (`VALID_ROLES` restricted to `["agent1", "agent2", "coordinator"]`), `REQ-PRO-020.6` (fatal governance abort on `--as human`), `REQ-PRO-019.4` (prohibit human credential fallback for agent roles), `REQ-PRO-020.7` (issue assignment to `ROLE_ACCOUNT_IDS["human"]` preserved), `REQ-PRO-036.5` (Höchstmaß mirrored).
* **Test Specs**: `TST-PRO-013.4` (CLI/env human rejection), `TST-PRO-012.3` (credential isolation), `TST-PRO-012.1` (valid role auth), `TST-PRO-013.5` (Höchstmaß unit parity).

---

## 3. System Invariants & Preserved Behavior
1. **Agent Continuity**: `agent1`, `agent2`, `coordinator` continue normal operation with dedicated service accounts.
2. **Issue Assignment Parity**: Calling `assign_issue(..., "human", role=role)` remains functional via `ROLE_ACCOUNT_IDS["human"]`.
3. **Pure Standard Library**: Zero external pip dependencies.
4. **Android Runtime Isolation**: Zero changes to production code in `app/src/main/`.

---

## 4. Proposed Architectural Changes
* **Component 1 (Role Parsing & Validation)**: In `tools/jira_util.py`, set `VALID_ROLES = ["agent1", "agent2", "coordinator"]`. In `parse_role_from_args()`, intercept `"human"`, `"user"`, `"rainer"` in CLI/env and abort with exit 1: `Error: 'human' role cannot be selected via agent CLI or environment variable. Human actions must use interactive confirmation or the Jira web UI.`
* **Component 2 (Credential Resolution & Fallback Prohibition)**: In `resolve_account(role, config)`: If `role` is an agent role and role credentials are missing/partial, abort with exit 1: `Error: Missing role-specific credentials for role '<role>'. Fallback to human default credentials (JIRA_USER/JIRA_TOKEN) is prohibited under ASPICE SUP.1 role separation.`
* **Component 3 (Höchstmaß & Living Docs)**: Mirror in `tools/hoechstmass/tools/jira_util.py`; update `docs/requirements/req_process_stability.md` (`REQ-PRO-019`, `REQ-PRO-020`) and `docs/tests/tst_process_stability.md` (`TST-PRO-012`, `TST-PRO-013`).
* **UI Consistency**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Step 1: Restrict `VALID_ROLES` & Governance Abort**: In `tools/jira_util.py`, remove `"human"` from `VALID_ROLES`; add explicit abort for human roles in `parse_role_from_args()`.
* **Step 2: Enforce Credential Isolation**: In `tools/jira_util.py`, update `resolve_account()` to prohibit human fallback for agent roles.
* **Step 3: Mirror in Höchstmaß**: Apply identical changes to `tools/hoechstmass/tools/jira_util.py`.
* **Step 4: Living Docs & Chesterton's Fence**: Sync `req_process_stability.md` (`REQ-PRO-019`, `REQ-PRO-020`) and `tst_process_stability.md` (`TST-PRO-012`, `TST-PRO-013`).
* **Step 5: Test Modernization & Verification**: Update `tools/test_jira_accounts.py` and `tools/hoechstmass/tools/test_hoechstmass_tools.py` for AC-1 and AC-2.
  * Commands: `python3 -m unittest tools/test_jira_accounts.py && PYTHONPATH=tools/hoechstmass/tools python3 -m unittest tools/hoechstmass/tools/test_hoechstmass_tools.py && python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: `test_jira_accounts.py` 100% pass, `test_hoechstmass_tools.py` 100% pass, `verify_requirement_governance.py` exit 0.
* **Rollback**: `git reset --hard HEAD` reverts changes on isolated branch `feature/ATT-6146`.
