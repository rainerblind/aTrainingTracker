---
name: stage5-verification
description: Executes Stage 5 (Verification, Clean-Room Regression & Walkthrough) of the agile ASPICE lifecycle. Runs the full test suite, performs on-device checks, authors the walkthrough, updates living docs to Verified, integrates verified changes into the sprint branch (Strategy A), and advances the parent ticket to Final Review (Human).
---

# Skill: stage5-verification

## Overview
This skill guides the agent through **Stage 5 (Verification & Release Quality Gate)**. It validates the complete implementation across clean-room regression tests, updates living documents, documents the walkthrough, merges the verified feature branch into the sprint integration branch (`sprint/<sprint_id>`), and transitions the parent ticket to human review.

## Subagent Model Pairing ("Zipper Principle" & Persistent Ping-Pong — Core Rule 7)
Reuse the ticket's two persistent `TypeName: "self"` subagents via `send_message`:
- **Doing (`--as agent1`)**: **`subagent_flash`** (`send_message` to the ticket's `flash` subagent) — Running `./gradlew testDebugUnitTest`, updating `docs/requirements.md` & `docs/tests.md` to `Verified`, authoring `walkthrough.md`, and submitting Stage 5 via `submit-stage` are deterministic procedural steps.
- **Gate 5 Review (`--as agent2`)**: **`subagent_pro`** (`send_message` to the ticket's `pro` subagent) — Final autonomous "Chief Inspector" before `Final Review (Human)` (Ceremony 2), executing `python3 tools/jira_util.py --as agent2 gate-verdict <SUBTASK_KEY> PASS ...`. After squashing the branch onto `sprint/<SPRINT_NAME>`, terminate both ticket subagents via `manage_subagents(Action="kill", ConversationIds=[...])`.

## Key Responsibilities (`subagent_flash` as `agent1` — `flash`)
1. **Two-Tier Unit Test Execution Strategy (Rule 60 — `ATT-4465`)**:
   - **Tier 1 — Per-Ticket Verification (Stage 4 & Stage 5, ~10–20s)**:
     - Run targeted unit and contract tests covering all modified and directly adjacent packages/classes:
       ```bash
       ./gradlew testDebugUnitTest --tests "com.atrainingtracker.<modified_package>.*"
       ```
     - Run `TranslationParityTest` whenever any `strings.xml` resource file is modified (~3s):
       ```bash
       ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"
       ```
     - Run `python3 tools/verify_requirement_governance.py` when existing requirements are modified.
     - **Exception (High-Risk Core Infrastructure)**: Run the full `./gradlew testDebugUnitTest` suite per-ticket *only* for High-Risk cross-cutting changes touching core shared infrastructure (`TrainingApplication`, `TrackerService`, `BANALService`, or SQLite database schema migrations).
   - **Tier 2 — Sprint Integration Gate (Once per Sprint in `Review & Retro`, ~4m)**:
     - The full clean-room regression suite (`./gradlew testDebugUnitTest` — 3,660+ tests) is executed once during the Sprint `Review & Retro` ticket before merging `sprint/<SPRINT_NAME>` into `develop`.
2. **Conditional On-Device / Physical Verification (Rule 60)**:
   - **UI / Compose / Resource / Navigation / Foreground Service Tickets**: Deploy debug APK to Pixel 10 (`./gradlew installDebug`) and verify live rendering, navigation, and absence of crash loops. Capture on-device screenshots (`adb exec-out screencap -p > docs/engineering/walkthroughs/img/<KEY>_<name>.png`) of the new/changed UI and reference screen, and compare against `docs/design_guidelines.md` Section 5.
   - **Non-UI Backend / Algorithm / DB Query / Dead-Code Cleanup Tickets**: Skip physical APK deployment in Stage 5 and rely on targeted unit/contract tests + `./gradlew assembleDebug` compilation verification.
3. **Walkthrough Documentation & Living Documentation Synchronization (Concise Cap $\le 60\text{--}80$ Lines)**:
   - Author `docs/engineering/walkthroughs/<TICKET_KEY>_walkthrough.md` using `templates/walkthrough_template.md` in a **concise bullet-and-table format (target $\le 60\text{--}80$ lines)**.
   - Update requirement status in `docs/requirements.md` to `Verified`.
   - Update test status in `docs/tests.md` to `Verified`.
4. **Single-Turn Stage Submission (`submit-stage`)**:
   - Submit Stage 5 walkthrough in a single composite CLI call (creates/updates `[Test]` sub-task and moves it to `In Überprüfung`):
     ```bash
     python3 tools/jira_util.py --as agent1 submit-stage <TICKET_KEY> 5 @docs/engineering/walkthroughs/<TICKET_KEY>_walkthrough.md
     ```

## Gate 5 Independent Review Checklist (`subagent_pro` as `agent2` — `pro`)
Message the ticket's persistent `subagent_pro` (`send_message`) to audit the Stage 5 walkthrough and repository state against this checklist and record the verdict via `--as agent2 gate-verdict`:
1. **Regression & Governance Verification**: Verify Tier 1 targeted/contract unit tests (or full suite for High-Risk tickets), `TranslationParityTest` (if strings changed), and `verify_requirement_governance.py` passed with 100% success (0 failures, 0 regressions).
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
