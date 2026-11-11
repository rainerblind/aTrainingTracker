---
name: stage6-final-review
description: Executes Stage 6 ('Final Review (Agent)') of the agile ASPICE lifecycle. Conducts an independent, zero-context Cold Carbon (Model: 'pro') audit of the squashed commit on the sprint branch, enforces up to 3 autonomous healing bounces back to the implementing pair, and posts a structured Executive Review Summary before advancing the ticket to 'Final Review (Human)'.
---

# Skill: stage6-final-review

## Overview
This skill guides the agent through **Stage 6 (`Final Review (Agent)` / Gate 6)**. While Stages 1–5 (`Analysis` $\rightarrow$ `Test Spec` $\rightarrow$ `Implementation Plan` $\rightarrow$ `Implementation` $\rightarrow$ `Test`) are executed by a persistent Epic-scoped `pro`/`flash` subagent pair (`Rules 60–63`), **Stage 6 (`Final Review (Agent)`)** introduces an orthogonal Swiss Cheese defensive barrier ($p_6$).

A freshly spawned **Cold `Gemini 4 Carbon` (`Model: "pro"`)** reviewer — with **zero shared conversational history** from Stages 1–5 — audits the integrated commit on `sprint/<SPRINT_NAME>` against the original Jira ticket description, living requirements/tests, and codebase reachability before the ticket is handed over to the human Product Owner (`Final Review (Human)`).

## Independent Cold Reviewer Architecture
- **Reviewer Identity**: A fresh `TypeName: "self"`, `Model: "pro"` (**`Gemini 4 Carbon`**) subagent (or the Coordinator / Retrospective Cold Reviewer operating in **Retroactive Batch Mode** during Ceremony 2 Step 0.5).
- **Zero-Context Independence**: Must **NEVER** be the `subagent_pro` or `subagent_flash` that authored or reviewed Stages 1–5 of the ticket.
- **Keep Original Pair Alive Until Stage 6 Passes**: During in-sprint execution, the original persistent `subagent_pro` + `subagent_flash` pair is kept alive (or re-invoked if in Retroactive Batch Mode) until Stage 6 `Final Review (Agent)` passes (`I.O.`), so any bounce (`1/3..3/3`) can be healed immediately.

## 4-Part Cold Audit Checklist (`--as agent2`)
For each ticket in `Final Review (Agent)`, the Cold Reviewer inspects the ticket (`python3 tools/jira_util.py show <TICKET_KEY>`) and its integrated commit on `sprint/<SPRINT_NAME>` (`git log --grep="<TICKET_KEY>" -n 1` and `git show <COMMIT_HASH>`):

1. **Zero-Context Code vs. Requirement Audit**:
   - Compare the actual `git show <COMMIT_HASH>` diff directly against the original Jira ticket description and `REQ-XXX` acceptance criteria in `docs/requirements/`.
   - Verify that every promised behavior, edge case, and UI/API contract is genuinely implemented in production code (no partial stubs, placeholder comments, or uncalled dead methods).
2. **Unintended Collateral & Regression Audit**:
   - Inspect all modified files in `git show --stat <COMMIT_HASH>`.
   - Verify zero unintended edits to unrelated components, zero submodule gitlink regressions (`ANT-Android-SDKs`), zero hardcoded secrets, and strict adherence to layer boundaries (UI $\rightarrow$ ViewModel $\rightarrow$ Repository $\rightarrow$ Database/Service; zero `*DatabaseManager` or `Cursor` leaks into UI/ViewModels).
   - For new UI screens, dialogs, `SensorType` enums, or background workers, verify live production caller reachability (`Rule 81`).
3. **Test Authenticity & Edge-Case Audit**:
   - Inspect the unit/integration tests added or updated in the commit.
   - Verify tests exercise real production classes and methods (never tautological mock-only assertions or unauthorized production source-text reading per `REQ-PRO-049`).
   - Run the targeted unit test suite if not yet verified in the current batch.
4. **Documentation & Hygiene Audit**:
   - Verify `REQ-XXX` and `TST-XXX` entries are marked `[Verified]` in their domain shards (`docs/requirements/*.md`, `docs/tests/*.md`) and master index tables (`docs/requirements.md`, `docs/tests.md`) are 100% synchronized (`python3 tools/verify_requirement_governance.py`).
   - Verify engineering deliverables (`analysis`, `test_spec`, `plan`, `walkthrough`) exist on disk.

## Recording the Stage 6 Verdict (`final-review-verdict`)

### Case A — `PASS` (`I.O.` $\rightarrow$ `Final Review (Human)`)
When all 4 checklist parts pass, record the `PASS` verdict using a single composite CLI call containing the **Executive Review Summary** for the human Product Owner:
```bash
python3 tools/jira_util.py --as agent2 final-review-verdict <TICKET_KEY> PASS "h3. Cold Final Review (Agent) — Executive Summary
* *Commit*: <COMMIT_HASH>
* *What Changed*: <1-2 concise bullets summarizing the production change>
* *Requirements & Tests Verified*: <REQ-XXX / TST-XXX> (<TestClassName> — 100% pass)
* *Collateral & Reachability Audit*: Clean diff, production reachability and layer boundaries verified.
* *Residual Risk / Human Focus*: <Low/Medium — specific area for PO spot-check on Pixel 10 if applicable>"
```
This atomically:
1. Posts the `[ROLE: agent2]` comment tagged with `[FINAL REVIEW (AGENT) — PASS]` on `<TICKET_KEY>`.
2. Transitions `<TICKET_KEY>` from `Final Review (Agent)` to `Final Review (Human)` via transition `I.O.`.
3. Assigns `<TICKET_KEY>` to `human`.

### Case B — `BOUNCE` (3-Bounce Healing Loop $\rightarrow$ `Implementation` / `Analysis` / `Zu erledigen`)
If a defect, missing requirement, broken test, reachability gap, or governance violation is found:
```bash
python3 tools/jira_util.py --as agent2 final-review-verdict <TICKET_KEY> BOUNCE "h3. Cold Final Review (Agent) — Defect Report
* *Finding 1*: <File/Line & root cause>
* *Required Fix*: <Exact remediation required>
* *Verification*: <Test command that must pass>" --bounce-target=implementation
```
- **Bounces `1/3`, `2/3`, `3/3`**:
  1. `jira_util.py` tags the comment `[FINAL REVIEW (AGENT) — BOUNCE X/3]`, transitions `<TICKET_KEY>` back to `Implementation` (or `Analysis` / `Zu erledigen`), and assigns it to `agent1`.
  2. The implementing agent/pair fixes the defect, runs the targeted tests and `verify_requirement_governance.py`, updates the squashed commit on `sprint/<SPRINT_NAME>`, and moves the ticket back to `Final Review (Agent)`:
     ```bash
     python3 tools/jira_util.py --as agent1 move <TICKET_KEY> "final review (agent)"
     ```
  3. The Cold Reviewer re-audits the updated commit and records `final-review-verdict <TICKET_KEY> PASS ...` (or another `BOUNCE`).
- **Escalation After 3 Bounces (`> 3`)**:
  - If a 4th bounce is triggered, `jira_util.py` automatically tags `[FINAL REVIEW (AGENT) — ESCALATED AFTER 3 BOUNCES]`, transitions `<TICKET_KEY>` to `Final Review (Human)`, and assigns it to `human` so the Product Owner can inspect the disputed ticket during Ceremony 2 without an infinite loop.
