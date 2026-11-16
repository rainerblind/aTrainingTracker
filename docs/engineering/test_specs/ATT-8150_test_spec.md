# Stage 2: Requirement & Test Specification - ATT-8150: Make Gate 1-5 reviews effective with separated authorship, evidence checklists and zero-revision alerts

**Ticket**: [ATT-8150](https://rainerblind.atlassian.net/browse/ATT-8150)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-47.2`  
**Requirement Mapping**: `REQ-PRO-107` (*Separated Gate Review Authorship, Evidence Checklists & Zero-Revision Alerts*; modifies `REQ-PRO-060`, extends `REQ-PRO-092`)  
**Test Spec ID**: `TST-PRO-107`  
**Branch**: `feature/ATT-8150` | **Author**: AI Agent 2 (`subagent_flash`) | **Date**: 2026-11-16  

---

## 1. Requirement Specification (REQ-PRO-107)
* **Attributes**: Priority: `High` | Type: `Non-Functional` | Verification Method: `Unit / Review` | Target Status: `[Planned]`
* **REQ-PRO-107.1 (Separated Gate N to Stage N+1 Authorship)**: `tools/jira_util.py submit-stage` SHALL enforce author-reviewer role separation by rejecting the submission of Stage N+1 if the calling actor/role matches the reviewer who recorded Gate N on that same ticket. Subagent topologies SHALL alternate `agent1` (Author) and `agent2` (Reviewer) turns without fused same-subagent transitions, while preserving backwards compatibility with already-closed prior tickets.
* **REQ-PRO-107.2 (Mandatory Evidence-Backed Gate 1–5 Checklists)**: `tools/jira_util.py gate-verdict` on Gates 1–5 SHALL validate that the verdict submission contains structured checklist items (`[x]`, `PASS:`, or `✓`) and at least one concrete, verifiable evidence reference (`file:line`, commit SHA `[0-9a-f]{7,40}`, or test/command output indicator like `BUILD SUCCESSFUL`, `testDebugUnitTest`, or `OK`), rejecting superficial or ungrounded verdicts. Suspiciously rapid reviews (< 5 seconds elapsed between subtask start and verdict) SHALL be explicitly flagged in audit logging.
* **REQ-PRO-107.3 (Multi-Sprint Rubber-Stamping Retro Alarm)**: `tools/sprint_metrics.py` SHALL evaluate multi-sprint Gate 1–5 First-Time Pass Rates in `process_metrics.jsonl`, automatically generating an ASPICE Finding M3 Rubber-Stamping Alert and posting it to the active sprint's Retro ticket whenever two or more consecutive sprints exhibit 100.0% Gate 1–5 FTPR with zero recorded revisions.
* **REQ-PRO-107.4 (Governance & Protocol Harmonization)**: Rules `7.1` and `7.4` in `.agents/rules/aspice_governance.md` and `.agents/skills/jira-workflow/SKILL.md` SHALL be formally updated to deprecate same-agent fused handoffs, codifying persistent alternating pair reviews with independent Gate N to Stage N+1 transitions.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Modifies `REQ-PRO-060` (Composite stage CLI / fused handoffs) and extends `REQ-PRO-092` (Sprint metrics & plausibility warnings) via net-new `REQ-PRO-107`. Targets `tools/jira_util.py`, `tools/sprint_metrics.py`, and project governance rules.
* **Historical Origin & Commit Trace**: `ATT-4290` (`REQ-PRO-060`), `ATT-6163` (`REQ-PRO-092`), `ATT-8149` (`REQ-PRO-106`), `ATT-8150`.
* **Root Reason for Existing Formulation**: Fused transitions were introduced in ATT-4290 to reduce subagent invocation latency, but created an inherent conflict of interest that eliminated Gate 1–5 review friction.
* **Preservation of Core Invariants**: Single composite CLI call per stage submission (`submit-stage`) and gate verdict (`gate-verdict`) is preserved; subtasks transition directly to `Erledigt` on PASS; `ANT-Android-SDKs` remains untouched at `160000 commit 0e8cea714a231fb9565170d4ed01eafa868f9798`.

---

## 3. Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Separated Gate N to Stage N+1 Authorship)**: *Given* an agent attempting to submit Stage N+1 on a ticket, *When* the caller actor matches the reviewer who recorded Gate N on that ticket, *Then* `submit-stage` rejects the operation with an ASPICE SUP.1 separated authorship violation error while allowing valid alternating author submissions.
* **Criterion 2 (Mandatory Evidence-Backed Gate 1–5 Checklist & Rapid Review Warning)**: *Given* a Gate 1–5 audit submission, *When* `gate-verdict` executes, *Then* it verifies structured checklist items and concrete evidence references (`file:line`, commit SHA, or command output hash), failing if missing or superficial, and flags reviews executed in < 5 seconds.
* **Criterion 3 (Multi-Sprint 100% FTPR Rubber-Stamping Retro Alarm)**: *Given* two or more consecutive sprints in `process_metrics.jsonl` with 100.0% Gate 1–5 FTPR, *When* `sprint-metrics` runs, *Then* a prominent Rubber-Stamping Alert is emitted and posted to the active sprint's Retro ticket.

---

## 4. Test Specification (TST-PRO-107)
* **TST-PRO-107.1 (Separated Authorship Enforcement Test)**: Verify `submit_stage` detects if the caller actor matches the Gate N reviewer on the same parent ticket, rejects self-review handoffs with an error, and permits valid alternating author submissions.
* **TST-PRO-107.2 (Evidence-Backed Gate 1–5 Checklist Validation Test)**: Verify `gate_verdict` accepts verdicts with structured checklists and verifiable evidence anchors (`file:line`, commit SHA, test execution hashes), rejects unstructured or anchorless text, and flags rapid reviews.
* **TST-PRO-107.3 (Multi-Sprint 100% FTPR Rubber-Stamping Alert Test)**: Verify `sprint_metrics` inspects historical sprint metrics, triggers a Rubber-Stamping Warning when consecutive sprints exhibit 100.0% FTPR, and verifies integration into retro comments.
* **TST-PRO-107.4 (Rule & Living Documentation Parity Test)**: Verify updated rules in `aspice_governance.md` and `jira-workflow/SKILL.md` conform to separated pair reviews and all living doc shards pass governance checks with zero code orphans.

---

## 5. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-107.1` | Unit | `tools/jira_util.py`, `tools/test_jira_accounts.py` | `REQ-PRO-107.1` | Planned |
| `TST-PRO-107.2` | Unit | `tools/jira_util.py`, `tools/test_jira_accounts.py` | `REQ-PRO-107.2` | Planned |
| `TST-PRO-107.3` | Unit | `tools/sprint_metrics.py`, `tools/test_jira_accounts.py` | `REQ-PRO-107.3` | Planned |
| `TST-PRO-107.4` | Review / Unit | `aspice_governance.md`, `jira-workflow/SKILL.md` | `REQ-PRO-107.4` | Planned |
