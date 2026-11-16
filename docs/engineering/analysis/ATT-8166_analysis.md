# Stage 1 Analysis: ATT-8166 - Require explicit PO approval for agent-originated change requests before sprint planning

**Ticket**: [ATT-8166](https://rainerblind.atlassian.net/browse/ATT-8166)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-47.1`  
**Branch**: `feature/ATT-8166` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-16

---

## 1. Problem Statement & Motivation
Under ASPICE 4.0 Finding `SUP.10` (Change Request Management, SUP.10 BP3/BP4):
1. Change request approval previously relied on informal sprint placement. Agents autonomously spawned unapproved follow-up tickets (e.g. ADR-001 child tickets `ATT-7902`, `ATT-7907`–`ATT-7910`).
2. Although the PO ruled in Sprint 46.6 retro to close core service Kotlin migration tickets, the PO subsequently approved `ATT-7902` in a Jira comment (`2026-11-14T20:28:50`) as tests-only scaffolding. Commit `43846f1a` landed on `sprint/2026-46.7` without a documented, auditable repository disposition record.
3. Under the 12h continuous sprint model (`ATT-8162` / `ATT-8030`), tamper-proof `SUP.10.BP4` approval is achieved via the PO-curated container sprint `Vom PO Freigegeben` (ID `447`), requiring strict tooling enforcement against agent promotion and immutable changelog auditability.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Tooling Gaps in `tools/jira_util.py`**:
  * `create_issue` creates tickets in raw Backlog (`add_to_sprint=False`), but `move_issues_to_sprint_guarded` does not verify that candidate issues entering active sprints originate exclusively from `Vom PO Freigegeben` (ID 447).
  * `qa_audit` audits 5 lifecycle dimensions (deliverables, gates 1–5, 4-Augen role separation, living docs, commit hygiene), but completely lacks Dimension 6: verifying human PO `accountId` changelog evidence for `Vom PO Freigegeben` placement or direct human creation.
* **`ATT-7902` Historical Archaeology**:
  * Jira changelog confirms PO Rainer Blind (`712020:9d9c7abd-b9d3-4b45-b2e0-91c432c3ab78`) moved `ATT-7902` into `Vom PO Freigegeben` and active sprint `2026-46.7`, and explicitly authorized it on `2026-11-14T20:28:50` under a strict tests-only constraint (`0` edits in `app/src/main/`).
  * However, no formal disposition comment was recorded to close the loop with the Sprint 46.6 retro action item.
* **Next Sequential Process Requirement ID**:
  * Codebase audit on `e1b4d615` confirms highest existing IDs are `REQ-PRO-104` / `TST-PRO-104`. `REQ-PRO-105` and `TST-PRO-105` are next and available. Per `REQ-PRO-104`, all new REQs must include `- **Description**:`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Enforce raw Backlog guard in `tools/jira_util.py`: agents (`agent1`, `agent2`, `coordinator`) are forbidden from moving raw Backlog tickets into `Vom PO Freigegeben` or active sprints (preserving `ensure-retro`/`ensure-weekly-reviews` exceptions).
  2. Implement `verify_po_approval_changelog()` in `tools/jira_util.py` querying `/rest/api/2/issue/{key}?expand=changelog` to confirm human PO `accountId` provenance.
  3. Integrate Dimension 6 (`po_approval_pass`) into `qa_audit()` ensuring every sampled regular sprint ticket was placed into `Vom PO Freigegeben` by the human PO or created directly by the human PO.
  4. Post a formal disposition record comment on `ATT-7902` documenting explicit PO authorization and SUP.10 compliance.
  5. Specify `REQ-PRO-105` and `TST-PRO-105` with mandatory `- **Description**:` in living documentation.
* **Out-of-Scope Non-Goals**:
  - Modifying Android app source code (`app/src/`) or build logic (100% tooling & governance scope).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Extends `REQ-PRO-064` (Change Impact Analysis / SUP.10), `REQ-PRO-091` (QA audit protocol), and `REQ-PRO-097` (Sprint bin-packing) via net-new `REQ-PRO-105`.
* **Historical Origin & Commit Trace**: `ATT-7749` (Sprint 46.6 Retro), `ATT-7902` (`43846f1a`, `f565c688`), `ATT-8030`, `ATT-8162`.
* **Root Reason for Existing Formulation**: Manual conventions lacked automated changelog verification, allowing unverified backlog items to be pulled into sprints.
* **Preservation of Core Invariants**: Preserve `cutoff_return=True` returning untouched tickets to ID 447; preserve `ensure-retro` active sprint placement; keep `ANT-Android-SDKs` at `160000 commit 0e8cea714a231fb9565170d4ed01eafa868f9798`.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-PRO-105` / `TST-PRO-105` in `docs/requirements/req_process_stability.md` & `docs/tests/tst_process_stability.md`.
* **Affected Architecture Elements**: `tools/jira_util.py`, `tools/test_jira_accounts.py`, `ATT-7902` Jira comments.
* **Verification Impact & Test Surface**: `tools/test_jira_accounts.py`, `verify_requirement_governance.py`.
* **Risk Assessment & Invariants**: **LOW** (`1 SP`, tooling-only guardrail; zero production code or runtime logic risk).

---

## 6. Architectural Strategy & High-Level Solution
* Add `verify_po_approval_changelog()` in `tools/jira_util.py` checking Jira changelog for `Sprint` field change to `Vom PO Freigegeben` (ID 447) authored by `ROLE_ACCOUNT_IDS['human']` or direct creation by human PO.
* Add Dimension 6 to `qa_audit()` reporting PO approval status per sampled ticket in markdown audit tables.
* Add unit test coverage in `tools/test_jira_accounts.py` asserting changelog validation and rejection of unapproved tickets.
* Post formal disposition comment on `ATT-7902`.
