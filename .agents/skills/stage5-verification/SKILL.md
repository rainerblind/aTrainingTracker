---
name: stage5-verification
description: Executes Stage 5 (Verification, Clean-Room Regression & Walkthrough) of the agile ASPICE lifecycle. Runs the full test suite, performs on-device checks, authors the walkthrough, updates living docs to Verified, integrates verified changes into the sprint branch (Strategy A), and advances the parent ticket to Final Review (Human).
---

# Skill: stage5-verification

## Overview
This skill guides the agent through **Stage 5 (Verification & Release Quality Gate)**. It validates the complete implementation across clean-room regression tests, updates living documents, documents the walkthrough, merges the verified feature branch into the sprint integration branch (`sprint/<sprint_id>`), and transitions the parent ticket to human review.

## Subagent Model Pairing ("Zipper Principle" — Core Rule 7)
- **Doing (`agent1`)**: **`flash`** (`invoke_subagent` with `Model: "flash"`) — Running `./gradlew testDebugUnitTest`, updating `docs/requirements.md` & `docs/tests.md` to `Verified`, authoring `walkthrough.md`, and squashing onto `sprint/<SPRINT_NAME>` are deterministic procedural steps.
- **Gate 5 Review (`agent2`)**: **`pro`** (`invoke_subagent` with `TypeName: "research"`, `Model: "pro"`) — Final autonomous "Chief Inspector" before `Final Review (Human)` (Ceremony 2).

## Key Responsibilities (`agent1` — `flash`)
1. **Clean-Room Regression Suite**:
   - Execute the entire unit test suite from clean state:
     ```bash
     ./gradlew testDebugUnitTest
     ```
   - Assert 100% pass rate with zero failures.
2. **On-Device / Physical Verification**:
   - For UI/Sensor changes: Deploy debug APK to Pixel 10 (`./gradlew installDebug`) and verify live rendering, navigation, and absence of crash loops.
   - **Visual Consistency Check**: For UI changes, capture on-device screenshots (`adb exec-out screencap -p > docs/engineering/walkthroughs/img/<KEY>_<name>.png`) of the new/changed UI and of the reference screen named in the plan's `UI Consistency` section. Embed them side by side in the walkthrough and compare against `docs/design_guidelines.md` Section 5.
3. **Walkthrough Documentation & Living Documentation Synchronization**:
   - Author `docs/engineering/walkthroughs/<TICKET_KEY>_walkthrough.md` using `templates/walkthrough_template.md`.
   - Update requirement status in `docs/requirements.md` to `Verified`.
   - Update test status in `docs/tests.md` to `Verified`.
4. **Single-Turn Stage Submission (`submit-stage`)**:
   - Submit Stage 5 walkthrough in a single composite CLI call (creates/updates `[Test]` sub-task and moves it to `In Überprüfung`):
     ```bash
     python3 tools/jira_util.py --as agent1 submit-stage <TICKET_KEY> 5 @docs/engineering/walkthroughs/<TICKET_KEY>_walkthrough.md
     ```

## Gate 5 Independent Review Checklist (`agent2` — `pro` `research` Subagent)
Spawn an independent `research` subagent (`Model: "pro"`) to audit the Stage 5 walkthrough and repository state against this checklist:
1. **Full-Suite Regression Execution**: Verify `./gradlew testDebugUnitTest` executed with 100% success (0 failures, 0 regressions) across all project modules.
2. **Living Documentation Parity**: Confirm `docs/requirements.md` and `docs/tests.md` status fields are updated to `Verified`.
3. **Mandatory Lösungsversion (Fix Version/s) Governance Audit**:
   - In accordance with Core Rule 8 ("FixVersion set only upon completion" and sub-tasks never having `fixVersions`), active in-sprint tickets legitimately have Parent `Lösungsversion` as `None` until final acceptance during Joint Review or release. Verify that the deliverable references the target release version (e.g. `V4.9.40`) and that sub-tasks have no fix version assigned.
4. **Gate Verdict (`gate-verdict`) & Parent Handover (Core Rule 3)**:
   - Record the verdict in a single composite CLI call (which closes the `[Test]` sub-task to `Erledigt`, automatically advances the parent ticket to `Final Review (Human)`, and assigns it to `human`):
     ```bash
     python3 tools/jira_util.py --as agent2 gate-verdict <TEST_SUBTASK_KEY> PASS "<Audit Report>"
     ```
   - Note: AI agents are strictly forbidden from transitioning parent tickets to `Erledigt`.
5. **Single Atomic Commit per Ticket & Continuous Sprint Branch Integration (Core Rule 8)**:
   - Each ticket MUST result in **strictly one atomic commit** on `sprint/<SPRINT_NAME>` containing the complete Stage 1–5 deliverables (`analysis`, `test_spec`, `plan`, `walkthrough`), living documentation (`docs/requirements.md`, `docs/tests.md`), production code, and unit tests:
     ```bash
     git add -A
     git commit -m "<KEY>: <Concise Ticket Summary>"
     git checkout sprint/<SPRINT_NAME>
     git merge --squash feature/<KEY>
     git commit -m "<KEY>: <Concise Ticket Summary>"
     git branch -D feature/<KEY>
     ```

## Deliverable Template
Use the standardized markdown template located at:
`templates/walkthrough_template.md`
