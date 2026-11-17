# Stage 2: Requirement & Test Specification - ATT-8153: Escalate QA non-conformances to dedicated corrective action tickets and track them to closure in the Retro

**Ticket**: [ATT-8153](https://rainerblind.atlassian.net/browse/ATT-8153)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-16_02_Spät`  
**Requirement Mapping**: `REQ-PRO-091` (`REQ-PRO-091.3`, `REQ-PRO-091.4`)  
**Test Spec ID**: `TST-PRO-091` (`TST-PRO-091.11`, `TST-PRO-091.12`, `TST-PRO-091.13`)  
**Branch**: `feature/ATT-8153` | **Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-16  

---

## 1. Requirement Specification (REQ-PRO-091)
* **Attributes**: Priority: `High` | Type: `Non-Functional` | Verification Method: `Unit / Review` | Target Status: `[Verified]`
* **REQ-PRO-091.3 (Closed-Loop Jira Corrective-Action Tracking)**:
  * For any audit evaluating a `NON-COMPLIANT` overall verdict or finding, Section 4 of `docs/engineering/qa_audits/<sprint>.md` SHALL link every non-compliant finding to a distinct, dedicated Jira corrective-action ticket (`ca_key != audited_key`). Self-referential links naming the audited ticket itself are strictly prohibited.
  * The QA audit tool SHALL idempotently create or link dedicated corrective action tickets (`issuetype_id="10005"` / `Verbesserung`, parent Epic `ATT-8138` or `ATT-232`) in raw Backlog (`add_to_sprint=False`, `rank_top=True`), deduplicated per non-conformance category per sprint (`[QA Corrective Action] <Category> - Sprint <Sprint>`).
  * `tools/verify_requirement_governance.py --check-qa-audits` SHALL enforce `ca_key != audited_key` for current and future sprints while grandfathering legacy audits prior to sprint `2026-11-16_02_Spät`.
* **REQ-PRO-091.4 (Retrospective & Ceremony Corrective Action Tracking & Escalation)**:
  * `.agents/skills/sprint-planner/SKILL.md` Ceremony 1 (Sprint Planning) SHALL mandate reviewing open QA corrective action tickets from the prior sprint's audit.
  * Ceremony 2 (Sprint Review & Retrospective) SHALL mandate a dedicated 'QA Findings & Corrective Actions' section in `docs/engineering/retros/Sprint_Review_and_Retro_<KEY>.md`, auditing the status of all active corrective action tickets.
  * When an open QA corrective action remains unresolved for more than 2 sprints (> 2 sprints) or repeats across consecutive sprints, Ceremony 2 SHALL explicitly escalate the deviation to the Product Owner (PO) in the Retrospective deliverable.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-PRO-091` (`REQ-PRO-091.3`, `REQ-PRO-091.4`) and `TST-PRO-091`. Targets `tools/jira_util.py`, `tools/verify_requirement_governance.py`, `tools/test_jira_accounts.py`, `tools/test_verify_requirement_governance.py`, and `.agents/skills/sprint-planner/SKILL.md`.
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-46.3 via `ATT-6154` (`REQ-PRO-061`), extended in Sprint 2026-46.6 via `ATT-7558`, bugfixed in Sprint 2026-11-16_02_Spät via `ATT-8152`, and closed-loop escalation added via `ATT-8153`.
* **Root Reason for Existing Formulation**: Early tooling prioritized report formatting and used self-referential placeholders before dedicated issue creation under Epic ATT-8138/ATT-232 and multi-sprint PO escalation were integrated.
* **Preservation of Core Invariants**: Raw Backlog placement (`REQ-PRO-105.1`); offline mock fallback for test execution; `ANT-Android-SDKs` submodule integrity (`160000 commit 0e8cea714a231fb9565170d4ed01eafa868f9798`).

---

## 3. Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Dedicated Corrective Action Ticket Creation & Deduplication)**: *Given* an execution of `qa_audit` detecting non-compliant tickets, *When* non-compliances are processed, *Then* it generates or links dedicated corrective action tickets in raw Backlog (`add_to_sprint=False`, `rank_top=True`), deduplicated by non-conformance category, ensuring `ca_key != audited_key` in Section 4.
* **Criterion 2 (Idempotent Deduplication)**: *Given* repeated executions of `qa_audit` on the same sprint, *When* corrective action tickets already exist for a category, *Then* existing tickets are reused without creating duplicates.
* **Criterion 3 (Verifier Self-Reference Rejection)**: *Given* an audit report evaluated by `verify_qa_audits`, *When* checked for current and future sprints, *Then* it rejects reports containing self-referential corrective action links (`ca_key == audited_key`), while grandfathering legacy historical sprints prior to `2026-11-16_02_Spät`.
* **Criterion 4 (Retrospective Tracking & PO Escalation)**: *Given* Ceremony 2 in `sprint-planner/SKILL.md`, *When* executed at Sprint-End, *Then* it mandates a dedicated 'QA Findings & Corrective Actions' section in the Retrospective, and escalates open actions older than 2 sprints to the PO.

---

## 4. Test Specification (TST-PRO-091)
* **TST-PRO-091.11 (Dedicated Corrective Action Ticket Creation & Deduplication Test)**: Verify in `tools/test_jira_accounts.py` that `qa_audit` creates or links distinct dedicated corrective action tickets in raw Backlog (`add_to_sprint=False`, `rank_top=True`), deduplicated by non-conformance category, ensuring `ca_key != audited_key` in Section 4.
* **TST-PRO-091.12 (Self-Reference Rejection in Verifier Test)**: Verify in `tools/test_verify_requirement_governance.py` that `verify_qa_audits` rejects reports where corrective action links match the audited ticket key (`ca_key == audited_key`) for active/current sprints while grandfathering legacy historical audits prior to `2026-11-16_02_Spät`.
* **TST-PRO-091.13 (Ceremony 2 Retrospective QA Tracking & Multi-Sprint PO Escalation Review Test)**: Verify in `tools/test_jira_accounts.py` / review that Ceremony 2 instructions in `.agents/skills/sprint-planner/SKILL.md` require a dedicated 'QA Findings & Corrective Actions' section in the Retrospective and explicit PO escalation when an unclosed corrective action remains open for > 2 sprints.

---

## 5. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-091.11` | Unit | `tools/jira_util.py`, `tools/test_jira_accounts.py` | `REQ-PRO-091.3` | Planned |
| `TST-PRO-091.12` | Unit | `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py` | `REQ-PRO-091.3` | Planned |
| `TST-PRO-091.13` | Review/Unit | `.agents/skills/sprint-planner/SKILL.md`, `tools/test_jira_accounts.py` | `REQ-PRO-091.4` | Planned |
