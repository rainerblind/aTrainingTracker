# Stage 3: Implementation Plan - ATT-8166: Require explicit PO approval for agent-originated change requests before sprint planning

**Ticket**: [ATT-8166](https://rainerblind.atlassian.net/browse/ATT-8166)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-47.1` | **Branch**: `feature/ATT-8166`  
**Requirement Mapping**: `REQ-PRO-105` (`REQ-PRO-105.1`..`REQ-PRO-105.4`)  
**Test Mapping**: `TST-PRO-105` (`TST-PRO-105.1`..`TST-PRO-105.4`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-16

---

## 1. Problem Description & Background
Under ASPICE SUP.10 (Change Request Management, SUP.10 BP3/BP4), change requests must carry unforgeable PO approval prior to sprint entry. Under the continuous 12h sprint model, the permanent container sprint `Vom PO Freigegeben` (ID `447`) acts as the tamper-proof approval gate. This plan details the SWE.2 architecture to enforce raw Backlog landing for agent issue creation, two-way barriers against raw Backlog promotion into `447`/active sprints, Jira changelog PO provenance verification in `qa-audit`, and formal `ATT-7902` disposition recording.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-105` (`REQ-PRO-105.1`..`REQ-PRO-105.4`) in `docs/requirements/req_process_stability.md`
* **Test Mapping**: `TST-PRO-105` (`TST-PRO-105.1`..`TST-PRO-105.4`) in `docs/tests/tst_process_stability.md`

---

## 3. System Invariants & Preserved Behavior
1. **Zero Runtime Logic Modification**: Zero edits in `app/src/main/` or `app/src/test/`; strictly tooling (`tools/jira_util.py`, `tools/test_*.py`) and governance scope.
2. **Submodule Stability**: `ANT-Android-SDKs` remains untouched at `160000 commit 0e8cea714a231fb9565170d4ed01eafa868f9798`.
3. **Ceremony & Cutoff Exemption Invariants**:
   - `ensure-retro` and `ensure-weekly-reviews` preserve necessary active sprint/backlog ticket generation.
   - `cutoff_return=True` in `return-unstarted` continues returning untouched tickets from active sprints into ID `447`.
   - `pack-sprint` continues pulling PO-curated tickets from ID `447` into the target sprint.
4. **Description Ratchet & Traceability**: `REQ-PRO-105` includes mandatory `- **Description**:`; zero code orphans introduced.

---

## 4. Proposed Architectural Changes (SWE.2)
* **`tools/jira_util.py`**:
  - `create_issue`: Ensure issues default to raw Backlog (`add_to_sprint=False`), hard-rejecting `--add-to-sprint` or direct sprint assignment when called by agent roles (`agent1`, `agent2`, `coordinator`), preserving ceremony exceptions (`ensure_retro_ticket`, `create_subtask`).
  - `move_issues_to_sprint_guarded`: Extend barrier to hard-reject moving raw Backlog tickets directly into active sprints or `Vom PO Freigegeben` (ID `447`) for all agent roles (`agent1`, `agent2`, `coordinator`), allowing only `pack_sprint_from_po_queue` (`447 -> active_sprint`) and `return_unstarted_sprint_issues` (`active_sprint -> 447`).
  - `verify_po_approval_changelog(issue_key, base_url, role)`: Queries `/rest/api/2/issue/{key}?expand=changelog` to confirm that human PO `accountId` (`ROLE_ACCOUNT_IDS['human']`) either moved the ticket into `Vom PO Freigegeben` / active sprint or created it directly. Exempts recognized ceremony issues (`Review & Retro`, `Weekly Dual-Vendor Code Review`).
  - `qa_audit()`: Integrates Dimension 6 (`po_approval_pass`) across sampled sprint tickets, formatting results in `docs/engineering/qa_audits/<sprint>.md`.
* **Jira Disposition (`ATT-7902`)**: Post authoritative SUP.10 disposition comment documenting PO Rainer Blind's tests-only authorization (`2026-11-14T20:28:50`) and closing the loop with the Sprint 46.6 retro.
* **Unit Tests (`tools/test_jira_accounts.py` or `tools/test_jira_po_approval.py`)**:
  - Implement unit test suite covering `TST-PRO-105.1`..`TST-PRO-105.4` with synthetic changelog fixtures.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Risk Classification**: **STANDARD (Low Process Risk, Zero Runtime Risk)** — executed by **`subagent_flash`**.
* **Step 1 (`tools/jira_util.py`)**: Implement `verify_po_approval_changelog()`, harden `create_issue()` and `move_issues_to_sprint_guarded()`, and add Dimension 6 (`po_approval_pass`) to `qa_audit()`.
* **Step 2 (Jira Comment)**: Post formal SUP.10 disposition comment on `ATT-7902` referencing PO approval on 2026-11-14.
* **Step 3 (Living Docs)**: Update `REQ-PRO-105` and `TST-PRO-105` to `[Implemented]` in `req_process_stability.md` & `tst_process_stability.md`, running `--sync-indexes`.
* **Step 4 (Unit Tests)**: Author `# TST-PRO-105.1`..`# TST-PRO-105.4` unit tests in `tools/test_jira_accounts.py` (or dedicated test file).
* **Step 5 (Verification Execution)**: Execute Python test suite, governance checks, and verify zero regressions:
  ```bash
  python3 -m unittest tools/test_jira_accounts.py tools/test_verify_requirement_governance.py && \
  python3 tools/verify_requirement_governance.py && \
  python3 tools/verify_requirement_governance.py --check-code-orphans && \
  python3 tools/verify_requirement_governance.py --check-req-description
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: All Python unit tests (172+ tests), live governance checks (0 code orphans, 0 header mismatches, 100% description compliance), and `verify_po_approval_changelog` tests exit `0`.
* **Rollback**: Branch isolation (`feature/ATT-8166`) allows clean rollback via `git reset --hard 86ca3f3c`.
