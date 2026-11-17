# Stage 3: Implementation Plan - ATT-8153: Escalate QA non-conformances to dedicated corrective action tickets and track them to closure in the Retro

**Ticket**: [ATT-8153](https://rainerblind.atlassian.net/browse/ATT-8153)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-16_02_Spät`  
**Requirement Mapping**: `REQ-PRO-091` (`REQ-PRO-091.3`, `REQ-PRO-091.4`)  
**Test Mapping**: `TST-PRO-091` (`TST-PRO-091.11`, `TST-PRO-091.12`, `TST-PRO-091.13`)  
**Branch**: `feature/ATT-8153` | **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-16  

---

## 1. Problem Description & Background
ASPICE 4.0 Audit Finding `M5` (SUP.1 BP5/BP6, GP 2.1.6) identified that `qa_audit` in `tools/jira_util.py` (L4443) named the audited ticket itself as its own "corrective action ticket" (`ca_key = r.get("corrective_action") or f"[{r['key']}](...)"`), leaving process deviations open-loop without dedicated Jira tracking or Retrospective review. `verify_qa_audits` tolerated self-referential links, and `.agents/skills/sprint-planner/SKILL.md` Ceremony 2 lacked mandatory QA tracking and PO escalation for persistent non-conformances.

---

## 2. Traceability & Requirements Mapping
* **`REQ-PRO-091.3` $\rightarrow$ `TST-PRO-091.11`**: `qa_audit` creates/links dedicated, deduplicated Jira corrective action tickets in raw Backlog (`add_to_sprint=False`, `rank_top=True`), ensuring `ca_key != audited_key`.
* **`REQ-PRO-091.3` $\rightarrow$ `TST-PRO-091.12`**: `verify_qa_audits` rejects self-referential corrective action links for current/future audits while grandfathering historical sprints.
* **`REQ-PRO-091.4` $\rightarrow$ `TST-PRO-091.13`**: Ceremony 2 Retrospective protocol mandates QA tracking section and PO escalation for actions older than 2 sprints.

---

## 3. System Invariants & Preserved Behavior
1. **Raw Backlog Barrier (`REQ-PRO-105.1`)**: Corrective action tickets MUST be placed in raw Backlog (`add_to_sprint=False`); agents never add tickets to active sprints.
2. **Idempotent Deduplication**: Multiple non-compliant tickets in the same sprint sharing a category link to one single ticket per category per sprint (`[QA Corrective Action] <Category> - Sprint <Sprint>`).
3. **Historical Audit Grandfathering**: Grandfathers legacy sprint reports prior to `2026-11-16_02_Spät` so existing repo baselines remain green.
4. **Submodule & Gate Integrity**: `ANT-Android-SDKs` untouched at `160000 commit 0e8cea714a231fb9565170d4ed01eafa868f9798`; Rule 3 human decision gate preserved.

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Step 1 (`REQ-PRO-091.3` / `tools/jira_util.py`)**:
  - In `qa_audit`, map failed checks (`deliv_pass`, `gate_pass`, `role_sep_pass`, `living_doc_pass`, `commit_pass`, `po_approval_pass`) to standard categories (`Lifecycle Deliverables`, `Stage Subtask Progression`, `4-Augen Role Separation`, `Living Documentation Parity`, `Commit Hygiene`, `PO Approval Provenance`).
  - Implement idempotent ticket lookup/creation in raw Backlog (`issuetype_id="10005"`, parent `ATT-8138`, `add_to_sprint=False`, `rank_top=True`, `role=role`) under `[QA Corrective Action] <Category> - Sprint <Sprint>` with safe mock/dry-run fallback.
* **Step 2 (`REQ-PRO-091.3` / `tools/jira_util.py`)**:
  - In `format_qa_audit_markdown`, render distinct `ca_key != audited_key` links in Section 4 (`* **<KEY>**: Process deviation in <Category>; Corrective Action Ticket: [<CA_KEY>](url)`).
* **Step 3 (`REQ-PRO-091.3` / `tools/verify_requirement_governance.py`)**:
  - In `verify_qa_audits`, enforce `ca_key != audited_key` for current/future sprints, while grandfathering legacy historical sprints prior to `2026-11-16_02_Spät`.
* **Step 4 (`REQ-PRO-091.4` / `.agents/skills/sprint-planner/SKILL.md`)**:
  - In Ceremony 2 Retrospective protocol, add mandatory 'QA Findings & Corrective Actions' section and mandate explicit PO escalation when an unclosed action is older than 2 sprints (> 2 sprints) or repeats in consecutive sprints.
* **Step 5 (`TST-PRO-091.11..13`)**:
  - Add unit tests in `tools/test_jira_accounts.py` and `tools/test_verify_requirement_governance.py`.
  - Target commands:
    `python3 -m unittest tools/test_jira_accounts.py tools/test_verify_requirement_governance.py`
    `python3 tools/verify_requirement_governance.py --check-qa-audits`

---

## 5. Verification & Rollback Plan
* **Verification**: Run targeted Python test suites (all 178+ tests passing), `verify_requirement_governance.py` with 0 orphans, and 100% index parity.
* **Rollback**: `git checkout feature/ATT-8153 -- .` or `git revert` cleanly reverts changes without touching sprint branch.
