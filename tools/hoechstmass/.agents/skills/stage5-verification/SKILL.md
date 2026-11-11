---
name: stage5-verification
description: Executes Stage 5 (Verification, Clean-Room Regression & Walkthrough) of the agile ASPICE lifecycle. Runs the full test suite, performs verification checks, authors the walkthrough, updates living docs to Verified, integrates verified changes into the sprint branch, and advances the parent ticket to Final Review (Human).
---

# Skill: stage5-verification

## Overview
This skill guides the agent through **Stage 5 (Verification & Release Quality Gate)**. It validates the complete implementation across clean-room regression tests, updates living documents, documents the walkthrough, merges the verified feature branch into the sprint integration branch, and transitions the parent ticket to human review.

## Subagent Model Pairing ("Zipper Principle" — Core Rule 7)
Reuse the persistent subagent pair via messages:
- **Doing (`--as agent1`)**: **`subagent_flash`** — Running targeted unit tests, authoring `walkthrough.md`, and submitting Stage 5 via `submit-stage`.
- **Gate 5 Review (`--as agent2`)**: **`subagent_pro`** — Final autonomous "Chief Inspector" before `Final Review (Human)`. Audits verification evidence, executes Gate 5 `PASS`, and performs squashed atomic merge onto the sprint branch.

## Key Responsibilities (`subagent_flash` as `agent1`)
1. **Targeted Verification & Fast Regression**:
   - Run targeted unit and contract tests covering all modified and directly adjacent packages/modules.
   - Run requirement governance verification script.
   - For UI or resource tickets, perform visual verification and resource checks.
2. **Walkthrough Documentation & Living Spec Parity**:
   - Author `docs/engineering/walkthroughs/<TICKET_KEY>_walkthrough.md` using `templates/walkthrough_template.md` (target $\le 60\text{--}80$ lines).
   - State test scope explicitly (`Tier 1 targeted: <modules>` vs. `full suite`).
3. **Single-Turn Stage Submission (`submit-stage`)**:
   - Submit Stage 5 walkthrough in a single composite CLI call:
     ```bash
     python3 tools/workflow_util.py --as agent1 submit-stage <TICKET_KEY> 5 @docs/engineering/walkthroughs/<TICKET_KEY>_walkthrough.md
     ```

## Gate 5 Independent Review Checklist (`subagent_pro` as `agent2`)
Audit the Stage 5 walkthrough and repository state against this checklist:
1. **Regression & Governance Verification**: Verify targeted unit tests and requirement governance passed with 100% success.
2. **Evidence Integrity**: Verify walkthrough evidence demonstrates acceptance criteria satisfaction.
3. **Living Documentation Parity**: Confirm living documentation statuses and master counters are synchronized to `Verified`.
4. **Target Release Verification**: Verify the deliverable references the active unreleased target release version.
5. **Gate Verdict (`gate-verdict`) & Stage 6 (`Final Review (Agent)`) Handover (Core Rule 3 & REQ-PRO-058)**:
   - Record verdict via `--as agent2 gate-verdict`:
     ```bash
     python3 tools/workflow_util.py --as agent2 gate-verdict <TEST_SUBTASK_KEY> PASS "<Audit Report>"
     ```
   - Advances parent ticket to `Final Review (Agent)` (Stage 6 Cold Carbon Review), after which an independent Cold `Model: "pro"` reviewer audits the squashed commit and executes `final-review-verdict <TICKET_KEY> PASS|BOUNCE ...` to transition the ticket to `Final Review (Human)` and assign it to `human`. AI agents are strictly forbidden from transitioning parent tickets to completed.
6. **Single Atomic Commit per Ticket & Sprint Branch Integration (Core Rule 8 & Rule 9 — `REQ-PRO-041`)**:
   - Integrate verified ticket into the sprint branch as **strictly one squashed atomic commit**:
     ```bash
     git add -A
     git commit -m "<KEY>: <Concise Ticket Summary>"
     git checkout sprint/<SPRINT_NAME>
     git merge --squash feature/<KEY>
     git commit -m "<KEY>: <Concise Ticket Summary>"
     git branch -D feature/<KEY>
     ```
   - **Parallel Sprint Execution Merge Protocol (`REQ-PRO-041.2`, `REQ-PRO-041.4`)**: Under parallel sprint execution, serialize rebase, living docs recalculation, and squash-merging under POSIX `flock` (`/tmp/att_build_merge.lock`):
     ```bash
     python3 tools/parallel_sprint_lock.py bash -c '
       git add -A && git commit -m "<KEY>: <Concise Ticket Summary>" || true
       git fetch origin
       git rebase origin/sprint/<SPRINT_NAME>
       python3 tools/verify_requirement_governance.py --sync-indexes
       git add docs/requirements.md docs/tests.md
       git commit --amend --no-edit || true
       git checkout sprint/<SPRINT_NAME>
       git merge --squash feature/<KEY>
       git commit -m "<KEY>: <Concise Ticket Summary>"
       git branch -D feature/<KEY>
     '
     ```
