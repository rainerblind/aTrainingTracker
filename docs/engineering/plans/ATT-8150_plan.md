# Stage 3: Implementation Plan - ATT-8150: Make Gate 1-5 reviews effective with separated authorship, evidence checklists and zero-revision alerts

**Ticket**: [ATT-8150](https://rainerblind.atlassian.net/browse/ATT-8150)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-47.2`  
**Requirement Mapping**: `REQ-PRO-107` (`REQ-PRO-107.1`..`REQ-PRO-107.4`)  
**Test Mapping**: `TST-PRO-107` (`TST-PRO-107.1`..`TST-PRO-107.4`)  
**Branch**: `feature/ATT-8150` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-16  

---

## 1. Problem Description & Background
ASPICE 4.0 Audit Finding `M3` (SUP.1 BP1/BP3, GP 2.1.4, GP 2.2.4) identified 100% First-Time Pass Rate (222/222 gates passed since sprint 45.12 with zero revisions) caused by fused same-subagent Gate N $\rightarrow$ Stage N+1 transitions (`Rule 7.4` / `Rule 60`). Reviewers were incentivized to rubber-stamp gates without evidence inspection. To restore gate effectiveness, authorship separation, evidence-backed checklists, and multi-sprint 100% FTPR alerts must be enforced.

---

## 2. Traceability & Requirements Mapping
* **`REQ-PRO-107.1` $\rightarrow$ `TST-PRO-107.1`**: `submit_stage` enforces author-reviewer role separation (Stage N+1 author $\ne$ Gate N reviewer on the same parent ticket).
* **`REQ-PRO-107.2` $\rightarrow$ `TST-PRO-107.2`**: `gate_verdict` requires structured checklist (`[x]`/`PASS:`) and concrete evidence anchors (`file:line`, commit SHA, command output), plus rapid-verdict threshold warning.
* **`REQ-PRO-107.3` $\rightarrow$ `TST-PRO-107.3`**: `sprint_metrics.py` detects $\ge 2$ consecutive sprints with 100% Gate 1–5 FTPR and emits a Rubber-Stamping Alert to the Retro ticket.
* **`REQ-PRO-107.4` $\rightarrow$ `TST-PRO-107.4`**: `Rule 7.1`, `Rule 7.4`, and `jira-workflow/SKILL.md` updated to deprecate same-agent fused transitions and codify independent alternating pair reviews.

---

## 3. System Invariants & Preserved Behavior
1. **Single Composite CLI Calls**: `submit-stage` and `gate-verdict` retain single-command invocations with direct subtask progression.
2. **Backwards Compatibility**: Already-closed historical subtasks without reviewer metadata are tolerated cleanly.
3. **Lock & Zero Code Orphans**: Preserves POSIX flock independence and zero code orphans in `verify_requirement_governance.py`.
4. **Human Decision Gate**: Rule 3 parent ticket human closure remains invariant.

---

## 4. Proposed Architectural Changes
* **Authorship Separation (`tools/jira_util.py`)**:
  - In `submit_stage`, inspect preceding stage subtask. If calling role/actor matches the Gate N reviewer on that parent ticket, reject with `Separated authorship violation (REQ-PRO-107.1)`.
* **Evidence-Backed Gate Verdicts (`tools/jira_util.py`)**:
  - In `gate_verdict`, regex-verify that `text_or_file` contains structured checklist items (`[x]`, `PASS:`, `✓`) and at least one concrete evidence anchor (`file:line`, commit SHA `[0-9a-f]{7,40}`, or test output hash).
  - Add fast-verdict timing flag (< 5.0 seconds).
* **Multi-Sprint 100% FTPR Alert (`tools/sprint_metrics.py`)**:
  - Evaluate `process_metrics.jsonl` history; if consecutive sprints show 100% Gate 1–5 FTPR, append Finding M3 Rubber-Stamping Alert to Retro ticket comment.
* **Rule Harmonization (`aspice_governance.md`, `jira-workflow/SKILL.md`)**:
  - Update Rule 7.1 and 7.4 deprecating fused transitions in favor of alternating Author (`agent1`) / Reviewer (`agent2`) handoffs.
* **UI Consistency**: No UI changes (governance tooling and process scripts).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Step 1 (Authorship Separation in `submit_stage`)**: Implement preceding gate reviewer check in `tools/jira_util.py` rejecting identical author/reviewer roles.
* **Step 2 (Evidence-Backed Checklist in `gate_verdict`)**: Implement checklist and evidence regex scanner in `tools/jira_util.py`, rejecting unstructured reports and logging fast-review warnings.
* **Step 3 (Multi-Sprint Retro Alert in `sprint_metrics.py`)**: Implement consecutive 100% FTPR detection in `sprint_metrics.py` and integrate into retro ticket payload.
* **Step 4 (Governance Rules & Skill Update)**: Update `Rule 7.1`, `Rule 7.4` in `aspice_governance.md` and `jira-workflow/SKILL.md` to document alternating pair reviews.
* **Step 5 (Unit Tests in `tools/test_jira_accounts.py`)**: Implement `# TST-PRO-107.1`..`# TST-PRO-107.4`, updating existing test fixtures that call `gate_verdict` with plain text to include checklist evidence.
  - Test Command: `python3 -m unittest tools/test_jira_accounts.py`
* **Step 6 (Living Docs Promotion & Parity Check)**: Update `REQ-PRO-107` and `TST-PRO-107` to `[Implemented]`; run `python3 tools/verify_requirement_governance.py --sync-indexes`.

---

## 6. Verification & Rollback Plan
* **Verification**: Execute `test_jira_accounts.py`, all 4 Python test suites (180 tests), and all 6 governance CLI checks.
* **Rollback**: Branch isolation allows full git revert of `feature/ATT-8150` without affecting active sprint.
