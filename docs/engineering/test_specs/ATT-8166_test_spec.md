# Stage 2: Requirement & Test Specification - ATT-8166: Require explicit PO approval for agent-originated change requests before sprint planning

**Ticket**: [ATT-8166](https://rainerblind.atlassian.net/browse/ATT-8166)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-47.1`  
**Requirement Mapping**: `REQ-PRO-105` (*Explicit PO Approval & Changelog Provenance for Agent-Originated Change Requests*; extends `REQ-PRO-064`, `REQ-PRO-091`, `REQ-PRO-097`)  
**Test Spec ID**: `TST-PRO-105`  
**Branch**: `feature/ATT-8166` | **Author**: AI Agent 2 (`subagent_flash`) | **Date**: 2026-11-16

---

## 1. Requirement Specification (REQ-PRO-105)
* **Attributes**: Priority: `High` | Type: `Non-Functional` | Verification Method: `Unit / Review` | Target Status: `[Planned]`
* **REQ-PRO-105.1 (Raw Backlog Enforcement on Agent Issue Creation)**: When `create-issue` is invoked by any agent role (`agent1`, `agent2`, `coordinator`), issues SHALL always be created in raw Backlog with `sprint` unset. Any attempt by agents to specify `--add-to-sprint` or directly target an active sprint SHALL be rejected with an error, preserving ceremony exceptions (`ensure-retro`, `ensure-weekly-reviews`).
* **REQ-PRO-105.2 (Two-Way Barrier on PO Queue)**: Moving raw Backlog issues into `Vom PO Freigegeben` (ID 447) or an active sprint by ANY AI agent role (`agent1`, `agent2`, `coordinator`) SHALL be strictly prohibited and hard-rejected with a clear error. ONLY authorized Coordinator ceremonies (`pack-sprint` moving from `447 -> target_sprint_id`, and `return-unstarted` moving from `active_sprint -> 447`) SHALL be permitted.
* **REQ-PRO-105.3 (Jira Changelog Provenance Verification in QA Audits)**: `tools/jira_util.py` SHALL implement `verify_po_approval_changelog()` querying the issue changelog (`/rest/api/2/issue/{key}?expand=changelog`) to confirm human PO `accountId` (`HUMAN_ACCOUNT_ID`) provenance (placement into `Vom PO Freigegeben` or direct creation by human PO) for all regular sprint tickets, integrating Dimension 6 (`po_approval_pass`) into `qa-audit` without failing on legitimate ceremony tickets (`Review & Retro`, `Weekly Dual-Vendor Code Review`).
* **REQ-PRO-105.4 (Audit Trail & Exception Disposition)**: Approved historical change-request exceptions (e.g. `ATT-7902` tests-only core service scaffolding approved in Jira comment on `2026-11-14T20:28:50`) SHALL have formal SUP.10 disposition comments recorded in Jira and living documentation.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Extends `REQ-PRO-064` (Change Impact Analysis / SUP.10), `REQ-PRO-091` (QA audit protocol), and `REQ-PRO-097` (Sprint bin-packing) via net-new `REQ-PRO-105`. Targets `tools/jira_util.py` and `tools/test_jira_accounts.py`.
* **Historical Origin & Commit Trace**: `ATT-7749` (Sprint 46.6 Retro), `ATT-7902` (`43846f1a`), `ATT-8030` (Three-Tier Board Guard), `ATT-8162` (12h continuous sprint).
* **Root Reason for Existing Formulation**: Prior conventions lacked automated changelog verification, allowing unverified backlog items to enter active sprints without documented PO approval.
* **Preservation of Core Invariants**: Preserve `return-unstarted` returning untouched sprint tickets to ID 447; preserve `ensure-retro` active sprint placement; keep `ANT-Android-SDKs` untouched at `160000 commit 0e8cea714a231fb9565170d4ed01eafa868f9798`.

---

## 3. Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Agent Backlog Placement Guard)**: *Given* an agent attempting to create an issue with `--add-to-sprint`, *When* `create-issue` runs, *Then* direct sprint placement is blocked and the issue is created strictly in raw Backlog.
* **Criterion 2 (PO Queue Two-Way Barrier)**: *Given* an issue in raw Backlog, *When* an agent role invokes `move` or `pack-sprint` targeting `Vom PO Freigegeben` (ID 447) or an active sprint, *Then* the operation is hard-rejected with a descriptive governance error.
* **Criterion 3 (Changelog Audit Dimension 6)**: *Given* an active sprint with regular sampled tickets and ceremony tickets, *When* `qa-audit` executes Dimension 6, *Then* regular tickets verify human PO changelog provenance, ceremony tickets pass exemption, and audit reports reflect `po_approval_pass`.
* **Criterion 4 (Historical Disposition Traceability)**: *Given* `ATT-7902`, *When* audited under SUP.10, *Then* formal disposition documentation confirms tests-only authorization by the Product Owner.

---

## 4. Test Specification (TST-PRO-105)
* **TST-PRO-105.1 (Agent Backlog Placement & Direct Sprint Block Test)**: Verify `create_issue` creates issues in raw Backlog without sprint assignment and rejects `--add-to-sprint` when invoked under agent roles (`agent1`, `agent2`, `coordinator`).
* **TST-PRO-105.2 (Backlog-to-PO-Queue Two-Way Guard Test)**: Verify moving issues from raw Backlog into `Vom PO Freigegeben` (ID 447) or an active sprint is hard-rejected for all agent roles, while Coordinator `pack-sprint` (`447 -> active_sprint`) and `return-unstarted` (`active_sprint -> 447`) continue to function.
* **TST-PRO-105.3 (PO Changelog Provenance & QA Audit Dimension 6 Test)**: Verify `verify_po_approval_changelog()` parses Jira changelog history, passes issues moved to ID 447 or created by human PO `accountId`, rejects unapproved agent-originated issues, and asserts `qa_audit()` integrates Dimension 6 (`po_approval_pass`) while exempting ceremony tickets.
* **TST-PRO-105.4 (Historical Exception Disposition & Living Doc Parity Test)**: Verify `ATT-7902` disposition record confirms tests-only PO authorization, and verify all living doc shards pass governance checks with Description field ratchet compliance.

---

## 5. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-105.1` | Unit | `tools/jira_util.py`, `tools/test_jira_accounts.py` | `REQ-PRO-105.1` | Planned |
| `TST-PRO-105.2` | Unit | `tools/jira_util.py`, `tools/test_jira_accounts.py` | `REQ-PRO-105.2` | Planned |
| `TST-PRO-105.3` | Unit | `tools/jira_util.py`, `tools/test_jira_accounts.py` | `REQ-PRO-105.3` | Planned |
| `TST-PRO-105.4` | Review / Unit | `tools/test_jira_accounts.py`, `ATT-7902` | `REQ-PRO-105.4` | Planned |
