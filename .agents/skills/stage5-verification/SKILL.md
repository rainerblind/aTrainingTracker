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
     - Run targeted unit and contract tests covering all modified and directly adjacent packages/classes (plus `ThemeColorCentralizationAuditTest` whenever any UI/Compose file is modified, `Rule 64` — `ATT-4718`):
       ```bash
       ./gradlew testDebugUnitTest --tests "com.atrainingtracker.<modified_package>.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"
       ```
     - Run `TranslationParityTest` whenever any `strings.xml` resource file is modified (~3s):
       ```bash
       ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"
       ```
     - Run fast AAPT2 resource compilation verification to rapidly trap 9-language XML/AAPT2 resource errors without full APK build overhead (~5s, ATT-3534):
       ```bash
       ./gradlew processDebugResources
       ```
     - Run `python3 tools/verify_requirement_governance.py` when existing requirements are modified.
     - **Exception (High-Risk Core Infrastructure)**: Run the full `./gradlew testDebugUnitTest` suite per-ticket *only* for High-Risk cross-cutting changes touching core shared infrastructure (`TrainingApplication`, `TrackerService`, `BANALService`, or SQLite database schema migrations).
   - **Tier 2 — Sprint Integration Gate (Once per Sprint at the End of Phase 2, ~8m)** (Rule 66 — `ATT-4864`):
     - After the **last** sprint ticket is squashed onto `sprint/<SPRINT_NAME>`, the Coordinator runs the full suite `./gradlew testDebugUnitTest --max-workers=2 && python3 tools/verify_requirement_governance.py` and fixes every failure (stale tests of earlier tickets, cross-test leaks) as separate attributed commits **before** Ceremony 2. The background run at the start of the Sprint Review is a confirmation only.
   - **Explicit Test Scope in the Walkthrough**: State exactly what was run (`Tier 1 targeted: <packages/classes>` or `full suite`). Never write "100% PASS" / "zero regressions" for a targeted subset without naming the scope.
2. **Conditional On-Device / Physical Verification (Rules 60 & 62 — `ATT-4465`, `ATT-4596`)**:
   - **UI / Compose / Resource / Navigation / Foreground Service Tickets**: Deploy debug APK to Pixel 10 (`./gradlew installDebug`), explicitly launch the **Debug** package (`adb shell am start -n com.atrainingtracker.debug/com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation` — never `com.atrainingtracker`, which opens the unpatched Release package), navigate on-device to **every single screen/route** whose `ViewModel`, `Fragment`, or composable was modified, and verify `adb logcat -b crash -d` is clean. Capture on-device screenshots (`adb exec-out screencap -p > docs/engineering/walkthroughs/img/<KEY>_<name>.png`) of the new/changed UI and reference screen, and compare against `docs/design_guidelines.md` Section 5.
   - **Non-UI Backend / Algorithm / DB Query / Dead-Code Cleanup Tickets**: Skip physical APK deployment in Stage 5 and rely on targeted unit/contract tests + `./gradlew processDebugResources` compilation verification.
3. **Walkthrough Documentation & Automatic Living Documentation Synchronization (Concise Cap $\le 60\text{--}80$ Lines)**:
   - Author `docs/engineering/walkthroughs/<TICKET_KEY>_walkthrough.md` using `templates/walkthrough_template.md` in a **concise bullet-and-table format (target $\le 60\text{--}80$ lines)**.
    - Note: `submit-stage <TICKET_KEY> 5` **automatically** flips `Planned` / `Status: Planned (<TICKET_KEY>)` rows and headings across `docs/requirements/`, `docs/tests/`, `docs/requirements.md` and `docs/tests.md` to `Verified`—no manual `replace_file_content` calls are needed just to flip the status column.
4. **Single-Turn Stage Submission (`submit-stage`)**:
   - Submit Stage 5 walkthrough in a single composite CLI call (auto-flips `Planned` $\rightarrow$ `Verified` in living docs, creates/updates `[Test]` sub-task, and moves it to `In Überprüfung`):
     ```bash
     python3 tools/jira_util.py --as agent1 submit-stage <TICKET_KEY> 5 @docs/engineering/walkthroughs/<TICKET_KEY>_walkthrough.md
     ```

## Gate 5 Independent Review Checklist (`subagent_pro` as `agent2` — `pro`)
Message the ticket's persistent `subagent_pro` (`send_message`) to audit the Stage 5 walkthrough and repository state against this checklist and record the verdict via `--as agent2 gate-verdict`:
1. **Regression & Governance Verification**: Verify Tier 1 targeted/contract unit tests (or full suite for High-Risk tickets), `TranslationParityTest` (if strings changed), and `verify_requirement_governance.py` passed with 100% success (0 failures, 0 regressions).
2. **Evidence Integrity & Mandatory `read_file` Visual Screenshot Audit (Rules 66, 71 & 73 — `ATT-4864`, `ATT-5313`, `ATT-5402`)**: Both `agent1` (in Stage 5) and `agent2` (`subagent_pro` in Gate 5) **MUST** call `read_file` on **every** screenshot and generated image (`docs/engineering/walkthroughs/img/*.png`, `docs/guide/img/*.png`, `docs/store_presence/screenshots/*.png`, `docs/store_presence/video/frames/*.png`) to visually verify that:
   - Each image actually renders the target UI screen or dialog (never a post-crash Android launcher/app drawer fallback or an unrelated screen such as `"Zum Bäcker #46"`).
   - No buttons or chips wrap mid-word (`Suppor\nt`, `Abbreche\nn`) across modified dialogs/bottom sheets (`Rule 73` stacked full-width CTA check in German) and no untranslated English strings or `%%` typos appear on localized screens.
   - Before/after or on/off image pairs MUST differ (`md5sum docs/engineering/walkthroughs/img/<KEY>_*.png` — identical hashes → `REVISION`). Re-check the Gate 4 AC-by-AC table against the final state.
3. **Living Documentation & SWE.2 Architecture Parity (`ATT-5431`)**: Confirm living documentation in `docs/requirements/` and `docs/tests/` status fields and master index counter tables are synchronized. If the ticket introduced architectural subsystems or requirement prefixes, verify that [`docs/architecture.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/architecture.md) was updated accordingly. Confirm all documentation claims are grounded in authentic code.
4. **Mandatory Lösungsversion (Fix Version/s) Governance Audit**:
   - In accordance with Core Rule 8 ("FixVersion set only upon completion" and sub-tasks never having `fixVersions`), active in-sprint tickets legitimately have Parent `Lösungsversion` as `None` until final acceptance during Joint Review or release. Verify that every Stage 1–5 deliverable references the target release version that `python3 tools/jira_util.py versions` reports as `ACTIVE/UNRELEASED` (never a free-text or invented version — Rule 66, `ATT-4864`) and that sub-tasks have no fix version assigned.
5. **Gate Verdict (`gate-verdict`) & Parent Handover (Core Rule 3)**:
   - Record the verdict in a single composite CLI call (which closes the `[Test]` sub-task to `Erledigt`, automatically advances the parent ticket to `Final Review (Human)`, and assigns it to `human`):
     ```bash
     python3 tools/jira_util.py --as agent2 gate-verdict <TEST_SUBTASK_KEY> PASS "<Audit Report>"
     ```
   - Note: AI agents are strictly forbidden from transitioning parent tickets to `Erledigt`.
6. **Single Atomic Commit per Ticket & Continuous Sprint Branch Integration (Core Rule 8)**:
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
