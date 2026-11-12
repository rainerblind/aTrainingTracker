# Stage 3: Implementation Plan - ATT-6155: Record cross-vendor independent reviews as QA evidence

**Ticket**: [ATT-6155](https://rainerblind.atlassian.net/browse/ATT-6155)  
**Sub-task**: `[Impl-Plan]` (Created on submission)  
**Parent Epic**: [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) (*ASPICE CL2 – Role Integrity, Independent QA & Configuration Management*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.3`  
**Requirement Mapping**: `REQ-PRO-062` (`REQ-PRO-062.1`–`REQ-PRO-062.6`)  
**Test Mapping**: `TST-PRO-062` (`TST-PRO-062.1`–`TST-PRO-062.4`)  
**Branch**: `feature/ATT-6155`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-12  

---

## 1. Problem Description & Background
ASPICE SUP.1 requires demonstrable QA independence from the work product author. Currently, in-sprint agents operate under the same vendor framework, while independent Human Sprint Reviews (Ceremony 2) and cross-vendor AI reviews lack structured repository persistence. This plan outlines 4 atomic steps to register `docs/engineering/reviews/`, implement the `record-review` CLI tool, add governance verification (`--check-reviews`), and integrate Ceremony 2 recording without blocking autonomous Phase 2 execution.

---

## 2. Traceability & Requirements Mapping
* **`REQ-PRO-062.1`**: Structured review record schema (`TST-PRO-062.1`).
* **`REQ-PRO-062.2`**: `record-review` CLI command in `tools/jira_util.py` (`TST-PRO-062.1`).
* **`REQ-PRO-062.3`**: Registration of `reviews` in `ALLOWED_ENGINEERING_ENTRIES` (`TST-PRO-062.4`).
* **`REQ-PRO-062.4`**: Governance verifier `--check-reviews [SPRINT]` validation (`TST-PRO-062.2`, `TST-PRO-062.3`).
* **`REQ-PRO-062.5`**: Non-blocking invariant for Phase 2 transitions (`TST-PRO-062.2`).
* **`REQ-PRO-062.6`**: Ceremony 2 review recording mandate in sprint-planner skill (`TST-PRO-062.1`).

---

## 3. System Invariants & Preserved Behavior
1. **Zero Runtime Impact**: Zero modifications to production Android code or application resources in `app/src/main/`.
2. **Standard Library Footprint**: Pure Python standard library (`json`, `os`, `re`, `sys`).
3. **Phase 2 Non-Blocking Invariant**: Autonomous progression (`Stage 5 -> Final Review`) never blocked by review records.
4. **Tooling Mirror Parity**: 100% functional parity between root tools and `tools/hoechstmass/tools/`.

---

## 4. Proposed Architectural Changes
* **`tools/verify_requirement_governance.py` & `tools/hoechstmass/...`**: Register `"reviews"` in `ALLOWED_ENGINEERING_ENTRIES` and `hoechstmass_config.json`; add `verify_independent_reviews()` and `--check-reviews` CLI parameter.
* **`tools/jira_util.py` & `tools/hoechstmass/...`**: Implement `record_review(sprint_arg, ticket_key, reviewer, vendor, model, version, scope, verdict, findings, out_path, role, repo_root)` and wire `record-review` CLI command.
* **`.agents/skills/sprint-planner/SKILL.md`**: Mandate `python3 tools/jira_util.py record-review` during Ceremony 2 Joint Review.
* **UI Consistency**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Directory Registration & Verifier Extension
* Files: `tools/verify_requirement_governance.py`, `tools/hoechstmass/tools/verify_requirement_governance.py`, `tools/hoechstmass/hoechstmass_config.json`
* Action: Add `"reviews"` to allowlists; implement `verify_independent_reviews(sprint_name, repo_root)` and wire `--check-reviews`.

### Step 2: Implement `record-review` Command in `jira_util.py`
* Files: `tools/jira_util.py`, `tools/hoechstmass/tools/jira_util.py`
* Action: Add `record_review(...)` function supporting Human and Cross-Vendor AI metadata; wire `record-review` CLI parser.

### Step 3: Integrate Ceremony 2 Skill Mandate
* Files: `.agents/skills/sprint-planner/SKILL.md`, `tools/hoechstmass/.agents/skills/sprint-planner/SKILL.md`
* Action: Add `record-review` and `--check-reviews` to Ceremony 2 checklist.

### Step 4: Unit Test Coverage & Verification
* Files: `tools/test_jira_accounts.py`, `tools/test_verify_requirement_governance.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py`
* Action: Add test cases validating `record_review`, `verify_independent_reviews`, and allowlist registration.
* Commands:
  - `python3 -m unittest tools/test_jira_accounts.py tools/test_verify_requirement_governance.py`
  - `PYTHONPATH=tools/hoechstmass/tools python3 -m unittest tools/hoechstmass/tools/test_hoechstmass_tools.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute unit tests, test live `record-review` and `--check-reviews`, and run living doc parity checks.
* **Rollback**: Branch isolation allows full revert without impacting `develop` or sprint branch.
