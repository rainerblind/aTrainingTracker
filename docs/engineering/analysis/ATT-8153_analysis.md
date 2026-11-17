# Stage 1 Analysis: ATT-8153 - Escalate QA non-conformances to dedicated corrective action tickets and track them to closure in the Retro

**Ticket**: [ATT-8153](https://rainerblind.atlassian.net/browse/ATT-8153)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-16_02_Spät`  
**Branch**: `feature/ATT-8153` | **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-16  

---

## 1. Problem Statement & Motivation
Under ASPICE 4.0 Audit Finding `M5` (SUP.1 BP5/BP6, GP 2.1.6):
1. **Self-Referential Corrective Action Fallback**: In `tools/jira_util.py` (L4443), when `qa_audit` formats Section 4 ("Non-Compliance Tracking & Remediation") for non-compliant tickets, it falls back to:
   `ca_key = r.get("corrective_action") or f"[{r['key']}](https://rainerblind.atlassian.net/browse/{r['key']})"`
   This mistakenly names the audited ticket itself as its own "corrective action ticket" (e.g. `sprint_2026-46.6.md` L84: `* **ATT-7563**: Process deviation noted; Corrective Action Ticket: [ATT-7563](...)`), failing to file dedicated remediation tickets.
2. **Permissive Governance Verifier**: `tools/verify_requirement_governance.py` (`verify_qa_audits`) merely checks `re.findall(r"\[(ATT-\d+|PROJ-\d+)\]", sec4_text)`, accepting the audited ticket's own key and allowing open-loop deviations to pass undetected.
3. **Missing Retrospective Tracking & PO Escalation**: Retrospective deliverables (`docs/engineering/retros/`) and `sprint-planner/SKILL.md` Ceremony 2 lack a mandatory QA findings tracking section. Open corrective actions from prior sprints are not tracked to closure, and recurring deviations ($\ge 2$ sprints) are never escalated to the Product Owner.

---

## 2. Root Cause Analysis & Independent Peer Review
* **RCA 1 (No Dedicated Ticket Creation in `qa_audit`)**: `qa_audit` lacks automated corrective action ticket creation or lookup. When non-compliances are detected, it relies solely on the fallback string containing the audited ticket key.
* **RCA 2 (Missing Non-Conformance Deduplication)**: Multiple tickets often fail the same criterion (e.g. 4 tickets failed role separation in Sprint 46.6). Creating a ticket per audited ticket would cause ticket bloat; tickets must be deduplicated per non-conformance type per sprint.
* **RCA 3 (Open-Loop Retrospective Process)**: Ceremony 2 in `.agents/skills/sprint-planner/SKILL.md` documents SPC and review status, but lacks an explicit protocol to audit open QA corrective action tickets or escalate tickets unresolved after 2 sprints to the PO.
* **Independent Peer Review & Verification (ATT-3534)**:
  * Audited `docs/engineering/qa_audits/sprint_2026-46.6.md` L82–86: Verified that non-compliant tickets `ATT-7563`, `ATT-7565`, `ATT-7576`, `ATT-7736` all reference themselves as their corrective action tickets.
  * Audited `Sprint_Review_and_Retro_ATT-7749.md`: Confirmed zero mention of QA non-compliances or corrective action ticket tracking.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Enhance `qa_audit` in `tools/jira_util.py` to identify failed criteria, deduplicate by non-conformance category, create/link a dedicated corrective action ticket under Epic `ATT-8138` (or `ATT-232`) in raw Backlog (`add_to_sprint=False`), and record distinct ticket links in Section 4.
  2. Tighten `verify_qa_audits` in `tools/verify_requirement_governance.py` to reject audit reports where corrective action links match the audited ticket key (`ca_key == audited_key`).
  3. Update `.agents/skills/sprint-planner/SKILL.md` Ceremony 2 to mandate a 'QA Findings & Corrective Actions' section in the Retrospective, with explicit PO escalation when an unclosed action is older than 2 sprints or repeats in consecutive sprints.
  4. Add unit test coverage in `tools/test_jira_accounts.py` and `tools/test_verify_requirement_governance.py`.
* **Out-of-Scope Non-Goals**:
  - Automatically closing Jira tickets (human PO authority per Rule 3).
  - Modifying the 6 core audit evaluation criteria implemented in `ATT-8152`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-PRO-091` (`REQ-PRO-091.3` Closed-Loop Jira Corrective-Action Tracking, `REQ-PRO-091.4` Ceremony 1/2 Verification & Escalation) and `TST-PRO-091`.
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-46.3 via `ATT-6154` (`REQ-PRO-061`); extended in Sprint 2026-46.6 via `ATT-7558` (`REQ-PRO-091`); amended in Sprint 2026-11-16_02_Spät via `ATT-8152` and `ATT-8153`.
* **Root Reason for Existing Formulation**: The self-referential fallback was an early placeholder before dedicated automated issue creation under Epic ATT-8138/ATT-232 was formalized.
* **Preservation of Core Invariants**: Raw Backlog placement (`REQ-PRO-105.1`); offline mock fallback for test execution; `ANT-Android-SDKs` submodule integrity (`160000 commit 0e8cea714a231fb9565170d4ed01eafa868f9798`).

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-PRO-091` (`REQ-PRO-091.3`, `REQ-PRO-091.4`); targets `TST-PRO-091`.
* **Affected Architecture Elements**: `tools/jira_util.py`, `tools/verify_requirement_governance.py`, `tools/test_jira_accounts.py`, `tools/test_verify_requirement_governance.py`, `.agents/skills/sprint-planner/SKILL.md`.
* **Verification Impact & Test Surface**: Unit tests in `test_jira_accounts.py` and `test_verify_requirement_governance.py`, governance check `--check-qa-audits`.
* **Risk Assessment & Invariants**: **LOW** (2 SP improvement in tooling and agile ceremony instructions; zero production Android runtime code changes).

---

## 6. Architectural Strategy & High-Level Solution
* **Deduplicated Corrective Action Ticket Creation**:
  * Map failures to 6 categories: `Lifecycle Deliverables`, `Stage Subtask Progression`, `4-Augen Role Separation`, `Living Documentation Parity`, `Commit Hygiene`, `PO Approval Provenance`.
  * For each failed category in the sprint, check if a corrective action ticket exists (`[QA Corrective Action] <Category> - Sprint <Sprint>`). If not, create a `Verbesserung` (`10005`) in raw Backlog under Parent Epic `ATT-8138` with `role="qa"`.
  * Section 4 formats distinct markdown links: `* **<AUDITED_KEY>**: Process deviation in <Category>; Corrective Action Ticket: [<CA_KEY>](url)`.
* **Verifier Disparity Check**:
  * In `verify_qa_audits`, assert `ca_key != audited_key` for every non-compliant ticket finding.
* **Ceremony 2 Retrospective Protocol**:
  * Add 'QA Findings & Corrective Actions' section to Ceremony 2 retrospective instructions; specify PO escalation for open tickets older than 2 sprints.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: (1) Zero runtime changes in `app/src/main/`; (2) Pure Python standard library; (3) Three-Tier Board Guard (`REQ-PRO-105.1`) preserved; (4) `ANT-Android-SDKs` untouched at `0e8cea714a231fb9565170d4ed01eafa868f9798`.
* **Risk Rating**: **LOW** (developer tooling & governance guidance with full automated test coverage).
