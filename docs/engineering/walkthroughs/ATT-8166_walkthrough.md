# Stage 5 Walkthrough: ATT-8166 - Require explicit PO approval for agent-originated change requests before sprint planning

**Ticket**: [ATT-8166](https://rainerblind.atlassian.net/browse/ATT-8166)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-47.1`  
**Requirement Mapping**: `REQ-PRO-105` (`REQ-PRO-105.1`..`REQ-PRO-105.4`) [Verified]  
**Test Spec Mapping**: `TST-PRO-105` (`TST-PRO-105.1`..`TST-PRO-105.4`) [Verified]  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-16

---

## 1. Executive Summary & Verification Walkthrough
Remediated ASPICE 4.0 Finding `SUP.10` (Change Request Management, SUP.10 BP3/BP4):
1. **Raw Backlog Enforcement on Issue Creation (`REQ-PRO-105.1`)**: In `tools/jira_util.py`, `create_issue()` strictly creates issues in raw Backlog (`add_to_sprint=False`), hard-rejecting `--add-to-sprint` or direct sprint assignment when invoked by agent roles (`agent1`, `agent2`, `coordinator`), while preserving ceremony exceptions (`ensure_retro_ticket`, `create_subtask`).
2. **Two-Way Barrier on PO Queue (`REQ-PRO-105.2`)**: `move_issues_to_sprint_guarded()` hard-rejects moving raw Backlog tickets into `Vom PO Freigegeben` (ID `447`) or active sprints by ANY agent role, allowing only Coordinator `pack_sprint_from_po_queue` (`447 -> active_sprint`) and `return_unstarted_sprint_issues` (`active_sprint -> 447`).
3. **Jira Changelog Provenance in QA Audits (`REQ-PRO-105.3`)**: Implemented `verify_po_approval_changelog()` in `tools/jira_util.py` querying `/rest/api/2/issue/{key}?expand=changelog` to verify human PO `accountId` (`ROLE_ACCOUNT_IDS['human']`) placement into ID `447` or direct human creation. Integrated Dimension 6 (`po_approval_pass`) into `qa_audit()` with ceremony exemptions (`Review & Retro`, `Weekly Dual-Vendor Code Review`).
4. **Historical Exception Disposition (`REQ-PRO-105.4`)**: Documented formal SUP.10 disposition comment on `ATT-7902` referencing PO Rainer Blind's tests-only authorization (`2026-11-14T20:28:50`) and closing the loop with the Sprint 46.6 retro.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-105.1` | `TST-PRO-105.1` | `test_agent_create_issue_cannot_add_to_sprint` (`tools/test_jira_accounts.py`) | **PASSED** | `Verified` |
| `REQ-PRO-105.2` | `TST-PRO-105.2` | `test_agent_cannot_move_raw_backlog_to_active_sprint` (`tools/test_jira_accounts.py`) | **PASSED** | `Verified` |
| `REQ-PRO-105.3` | `TST-PRO-105.3` | `test_verify_po_approval_changelog_*` & Dimension 6 `qa_audit` tests | **PASSED** | `Verified` |
| `REQ-PRO-105.4` | `TST-PRO-105.4` | `ATT-7902` Jira disposition comment verification & governance suite | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence
* **Stage 5 Python Unit Suites**:
  `python3 -m unittest tools/test_jira_accounts.py tools/test_verify_requirement_governance.py tools/test_generate_test_evidence.py tools/hoechstmass/tools/test_hoechstmass_tools.py`: **176 / 176 PASSED** (`0` failures, `0` errors).
* **Live Jira Cloud Sanity Check**:
  Verified `verify_po_approval_changelog()` against live Jira Cloud issues:
  - `ATT-8016`: `approved=True` (PO approved placement into `Vom PO Freigegeben` on 2026-11-15T22:16:09).
  - `ATT-8314`: `approved=True` (Ceremony ticket exempt).
  - `ATT-8148`: `approved=True` (PO approved placement into `Vom PO Freigegeben` on 2026-11-15T19:05:18).
* **Stage 5 Governance CLI Gates**:
  `verify_requirement_governance.py` (all checks pass: 0 code orphans, 18/18 shard headers synchronized, 100% description ratchet compliance, 362/362 classified `System-Refinement` REQs, 0 unbacked `[Verified]` tests): **ALL PASS**.

---

## 4. Hardware / Physical Verification & Invariants
* **Runtime / Submodule Scope**: Zero production logic edits in `app/src/main/` or `app/src/test/`; `ANT-Android-SDKs` untouched at `160000` (`0e8cea71`).
* **Living Docs & Gate Invariants**: `REQ-PRO-105` & `TST-PRO-105` promoted to `[Verified]`; master tables and shard headers synchronized at 100% parity; Rule 3 Human Gate preserved.
