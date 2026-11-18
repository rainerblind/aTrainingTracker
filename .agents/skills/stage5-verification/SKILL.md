---
name: stage5-verification
description: Executes Stage 5 (Verification, Clean-Room Regression & Walkthrough) of the agile ASPICE lifecycle. Runs targeted verification tests, performs on-device checks, authors the walkthrough, updates living docs to Verified, preserves the feature branch intact, and advances the parent ticket to Final Review (Agent) for Stage 6 Cold Review before atomic sprint-branch merge.
---

# Skill: stage5-verification

## Overview
This skill guides the agent through **Stage 5 (Verification & Release Quality Gate)**. It validates the complete implementation across clean-room regression tests, updates living documents, documents the walkthrough, commits cleanly onto `feature/<KEY>` (without prematurely squashing onto `sprint/<sprint_id>`), and transitions the parent ticket to `Final Review (Agent)` (Stage 6 Cold Review before atomic integration and `Final Review (Human)`).

## Subagent Model Pairing ("Zipper Principle" & Epic-Scoped Persistent Ping-Pong — Core Rule 7)
Reuse the Epic-scoped (or standalone ticket) persistent `TypeName: "self"` subagents via `send_message`:
- **Doing (`--as agent1`)**: **`subagent_flash`** (`send_message` to the `flash` subagent) — Running Tier 1 targeted unit tests, updating `docs/requirements.md` & `docs/tests.md` to `Verified`, authoring `walkthrough.md`, and submitting Stage 5 via `submit-stage` are deterministic procedural steps.
- **Gate 5 Review (`--as agent2`)**: **`subagent_pro`** (`send_message` to the `pro` subagent) — Audits Stage 5 verification evidence, confirms all ticket changes are committed cleanly on `feature/<KEY>` (leaving `feature/<KEY>` **intact** and `sprint/<SPRINT_NAME>` untouched until Stage 6 `PASS` per `REQ-PRO-125`), and executes `python3 tools/jira_util.py --as agent2 gate-verdict <SUBTASK_KEY> PASS ...` (which advances the parent ticket to `Final Review (Agent)` and injects `stage6-final-review/SKILL.md`). Keep the persistent `subagent_pro` + `subagent_flash` pair alive until the independent Cold reviewer completes **Stage 6 (`Final Review (Agent)`)**, integrates via `--merge-ticket`, and advances the parent ticket to `Final Review (Human)`, so any Stage 6 bounce (`1/3..3/3`) can be healed cleanly on `feature/<KEY>`. Terminate standalone ticket subagents or rotate Epic-scoped subagent pairs after 3 completed tickets (`Rule 62`) once Stage 6 passes.

## Key Responsibilities (`subagent_flash` as `agent1` — `flash`)
1. **Two-Tier Unit Test Execution Strategy (`Rule 60`, `Rule 7.5`, `Rule 85` — `ATT-4465`, `ATT-8030`)**:
   - **Tier 1 — Per-Ticket Verification (Stage 4 & Stage 5, Lean Targeted Only)**:
     - Execute **only** `./gradlew :app:testDebugUnitTest --tests <NewOrModifiedTestClasses>` covering modified and directly adjacent classes (`~14s` warm daemon):
       ```bash
       ./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.<modified_package>.<ModifiedTestClass>"
       ```
     - Run `TranslationParityTest` **only** when any `values*/strings*.xml` resource file was modified, and `:app:compileDebugAndroidTestKotlin` **only** when `app/src/androidTest/` was modified:
       ```bash
       # Conditionally when strings*.xml modified:
       ./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"
       ```
     - **Zero Redundant Re-Runs (`ATT-8030`)**: If the working tree code SHA is identical to the commit already verified in Stage 4 (only walkthrough/living-doc markdown added), do **NOT** re-run Gradle in Stage 5 — reuse the Stage 4 test evidence directly.
     - **Defer Heavy Static Analysis to Tier 2**: Do **NOT** run `:app:lintDebug`, `:app:detekt`, or full-repo contract tests (`ThemeColorCentralizationAuditTest`, `SourceTextReadingAuditContractTest`, `AspiceSweAuditContractTest`) per-ticket; they execute once per sprint in Tier 2. Run `python3 tools/verify_requirement_governance.py` only when existing requirements are modified (living-doc indexes sync automatically during `--merge-ticket`).
     - **Exception (High-Risk Core Infrastructure)**: Targeted adjacent callers or full `:app:testDebugUnitTest` per-ticket is reserved *only* for High-Risk cross-cutting changes touching core shared infrastructure (`TrainingApplication`, `TrackerService`, `BANALService`, or SQLite database schema migrations).
   - **Tier 2 — Lean `Schichtwechsel` Gate (`05:00`/`15:00`/`22:00`) & Weekly Full Suite (`Rule 7.5` / `Rule 3.5`)**:
     - At each `Schichtwechsel`, the Coordinator executes the **Lean Tier-2 Gate** (`./gradlew :app:assembleDebug :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest --tests <SprintTouchedSuites> --tests "com.atrainingtracker.trainingtracker.build.*" --tests "com.atrainingtracker.trainingtracker.integration.*" --tests "com.atrainingtracker.trainingtracker.localization.*" --max-workers=1 && python3 tools/verify_requirement_governance.py`, plus Python `tools/test_*.py` and safety linters; `export-test-evidence --no-kover`) and resolves every failure before merging `sprint/<SPRINT_NAME>` into `develop`. Full `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:detekt`, and `:app:koverXmlReportDebug` run **once a week** (per `Rule 3.5`) and on releases.
   - **Explicit Test Scope in the Walkthrough**: State exactly what was run (`Tier 1 targeted: <classes>` or `Tier 2 full suite`). Never write "100% PASS" / "zero regressions" for a targeted subset without naming the scope.
   - **Behavioral Compose UI Verification Integrity (`REQ-PRO-132`)**: Never classify a test lacking `createComposeRule()` (or relying solely on `Class.forName("...Kt")` reflection / tautological data-class checks) as a `"Compose UI Test"` in the walkthrough matrix. For created or modified `@Composable` UI, verify that a real `createComposeRule()` behavioral test ran and passed in Tier 1.
2. **Conditional On-Device / Emulator Verification (Two-Tier Device Policy — ATT-6506, REQ-TST-004, Rules 60, 62 & 76)**:
   - **Stages 1–5 (In-Sprint Per-Ticket Validation)**: Autonomous agents MUST deploy and verify exclusively on an emulated device (`emulator-5554`, `Pixel_8_Android_15`). Run `python3 tools/agent_emulator.py ensure` to boot, unlock keyguard, zero animation scales (`0.0`), configure permissions, and seed golden databases. Prefix all Gradle and ADB commands with `ANDROID_SERIAL=emulator-5554` (`ANDROID_SERIAL=emulator-5554 ./gradlew installDebug`, `adb -s emulator-5554 ...`). Autonomous agents are strictly prohibited from touching or deploying to the PO's connected physical USB phone during Phase 2.
   - **UI / Compose / Resource / Navigation / Foreground Service Tickets**: Deploy debug APK to `emulator-5554` (`ANDROID_SERIAL=emulator-5554 ./gradlew installDebug`), explicitly launch the **Debug** package (`adb -s emulator-5554 shell am start -n com.atrainingtracker.debug/com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation` — never `com.atrainingtracker`, which opens the unpatched Release package), navigate on-device to **every single screen/route** whose `ViewModel`, `Fragment`, or composable was modified, and verify `adb -s emulator-5554 logcat -b crash -d` is clean. Capture on-device screenshots (`adb -s emulator-5554 exec-out screencap -p > docs/engineering/walkthroughs/img/<KEY>_<name>.png`) of the new/changed UI and reference screen, and compare against `docs/design_guidelines.md` Section 5.
   - **Deterministic `uiautomator dump` Coordinate Resolution Before `adb shell input tap` (`Rule 76` — `ATT-5663`)**: Never visually guess `(x, y)` tap coordinates from a downscaled screenshot. Before executing `adb -s emulator-5554 shell input tap`, dump the active UI hierarchy (`adb -s emulator-5554 shell uiautomator dump /sdcard/window_dump.xml && adb -s emulator-5554 shell grep -oE 'text="[^"]*"[^>]*bounds="\[[0-9,]+\]\[[0-9,]+\]"' /sdcard/window_dump.xml`) to obtain the exact `bounds="[x1,y1][x2,y2]"` center coordinates of the target tab/button. If any `input tap` opens an unintended dialog or bottom sheet, **NEVER** repeat that same `(x, y)` coordinate after dismissing the dialog.
   - **Non-UI Backend / Algorithm / DB Query / Dead-Code Cleanup Tickets**: Skip APK deployment in Stage 5 and rely on targeted unit/contract tests + `./gradlew processDebugResources` compilation verification.
   - **Ceremony 2 (Sprint-End Joint Review)**: Evaluate the integrated sprint build on the real physical phone (`Pixel 10`, `ANDROID_SERIAL=<physical_serial> ./gradlew installDebug`).
3. **Walkthrough Documentation & Automatic Living Documentation Synchronization (Concise Cap $\le 60\text{--}80$ Lines)**:
   - Author `docs/engineering/walkthroughs/<TICKET_KEY>_walkthrough.md` using `templates/walkthrough_template.md` in a **concise bullet-and-table format (target $\le 60\text{--}80$ lines)**.
    - Note: `submit-stage <TICKET_KEY> 5` synchronizes living documentation rows and headings across `docs/requirements/`, `docs/tests/`, `docs/requirements.md` and `docs/tests.md`. Status promotion from `Planned` / `Implemented` to `Verified` is conditioned on verified machine-readable test evidence (REQ-PRO-099); unbacked or failing items remain `[Implemented]`.
4. **Single-Turn Stage Submission (`submit-stage`)**:
   - Submit Stage 5 walkthrough in a single composite CLI call (evaluates test evidence, promotes verified items in living docs, creates/updates `[Test]` sub-task, and moves it to `In Überprüfung`):
     ```bash
     python3 tools/jira_util.py --as agent1 submit-stage <TICKET_KEY> 5 @docs/engineering/walkthroughs/<TICKET_KEY>_walkthrough.md
     ```

## Gate 5 Independent Review Checklist (`subagent_pro` as `agent2` — `pro`)
Message the ticket's persistent `subagent_pro` (`send_message`) to audit the Stage 5 walkthrough and repository state against this checklist and record the verdict via `--as agent2 gate-verdict`:
1. **Regression & Governance Verification**: Verify Tier 1 targeted/contract unit tests (or full suite for High-Risk tickets), `TranslationParityTest` (if strings changed), and `verify_requirement_governance.py` passed with 100% success (0 failures, 0 regressions).
2. **Evidence Integrity & Mandatory `read_file` Visual Screenshot Audit (Rules 66, 71 & 73 — `ATT-4864`, `ATT-5313`, `ATT-5402`)**: Both `agent1` (in Stage 5) and `agent2` (`subagent_pro` in Gate 5) **MUST** call `read_file` on **every** screenshot and generated image (`docs/engineering/walkthroughs/img/*.png`, `docs/guide/img/*.png`, `docs/store_presence/screenshots/*.png`, `docs/store_presence/video/frames/*.png`) to visually verify that:
   - Each image actually renders the target UI screen or dialog (never a post-crash Android launcher/app drawer fallback or an unrelated screen such as `"Zum Bäcker #46"`).
   - No buttons, segmented button options, or chips wrap mid-word (`Suppor\nt`, `Abbreche\nn`), clip (`zurücl`), or ellipsize (`Standard (An...`), no slider status labels wrap onto two lines (`6 - Schwer bis\nsehr schwer`) or sit underneath the user's thumb drag path (finger occlusion), no stray M3 default stop-dots or inverted zone color gradients appear on custom tracks, and vector icons have verified orientation (`Rules 73 & 84` — `ATT-5402`, `ATT-7525`).
   - Before/after or on/off image pairs MUST differ (`md5sum docs/engineering/walkthroughs/img/<KEY>_*.png` — identical hashes → `REVISION`). Re-check the Gate 4 AC-by-AC table against the final state.
3. **Living Documentation, Dynamic Symbol-vs-Source Sweep & SWE.2 Architecture Parity (`ATT-5431`, `Rule 81` — `ATT-6179`, `ATT-6289`)**: Confirm living documentation in `docs/requirements/` and `docs/tests/` status fields and master index counter tables are synchronized. If the ticket introduced architectural subsystems or requirement prefixes, verify that [`docs/architecture.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/architecture.md) was updated accordingly. Whenever `docs/architecture*.md` or `docs/system/*.md` was created or modified, run an automated symbol-vs-source check confirming every backticked `.kt`/`.java`/`.xml` file and class exists in `app/src/main/` and `SYS.2 <-> SYS.3` IDs match 1:1 (`declared == allocated`).
4. **Mandatory Lösungsversion (Fix Version/s) Governance Audit**:
   - In accordance with Core Rule 8 ("FixVersion set only upon completion" and sub-tasks never having `fixVersions`), active in-sprint tickets legitimately have Parent `Lösungsversion` as `None` until final acceptance during Joint Review or release. Verify that every Stage 1–5 deliverable references the target release version that `python3 tools/jira_util.py versions` reports as `ACTIVE/UNRELEASED` (never a free-text or invented version — Rule 66, `ATT-4864`) and that sub-tasks have no fix version assigned.
5. **Gate Verdict (`gate-verdict`) & Stage 6 (`Final Review (Agent)`) Handover (Core Rule 3 & REQ-PRO-058 / REQ-PRO-125)**:
   - Record the verdict in a single composite CLI call (which closes the `[Test]` sub-task to `Erledigt`, automatically advances the parent ticket to `Final Review (Agent)`, retains `agent2` assignment, and injects `stage6-final-review/SKILL.md`):
     ```bash
     python3 tools/jira_util.py --as agent2 gate-verdict <TEST_SUBTASK_KEY> PASS "<Audit Report>"
     ```
   - Keep `feature/<KEY>` intact (do **NOT** merge onto `sprint/<SPRINT_NAME>` at Gate 5!). After Gate 5 `PASS`, the Coordinator invokes a freshly spawned **Cold Reviewer** (`Model: "flash"` for low-risk / `Model: "pro"` for high-risk per `Rule 7.2`, following `stage6-final-review/SKILL.md`) to audit `git diff sprint/<SPRINT_NAME>...feature/<KEY>`, perform live verification on `emulator-5554` / simulator + `read_file` screenshot inspection, and execute `final-review-verdict <TICKET_KEY> PASS|BOUNCE ...`. Atomic `--merge-ticket` onto `sprint/<SPRINT_NAME>` runs **strictly after** Stage 6 `PASS`.
   - Note: AI agents are strictly forbidden from transitioning parent tickets to `Erledigt`.
6. **Commit onto `feature/<KEY>` (Preserve Single Atomic Commit Invariant on `sprint/<SPRINT_NAME>`, Core Rule 8 & REQ-PRO-125)**:
   - Ensure all Stage 1–5 deliverables (`analysis`, `test_spec`, `plan`, `walkthrough`), living documentation (`docs/requirements/`, `docs/tests/`, `docs/requirements.md`, `docs/tests.md`), production code, and unit tests are committed cleanly on `feature/<KEY>` (never prematurely squashed onto `sprint/<SPRINT_NAME>` before Stage 6 `PASS`):
     ```bash
     git add <ticket-files>
     git commit -m "<KEY>: <Concise Ticket Summary>"
     ```
   - **Deferred Post-Stage-6 Merge Protocol (`REQ-PRO-041.2`, `REQ-PRO-041.4`, `REQ-PRO-125`)**: Do **NOT** invoke `parallel_sprint_lock.py --merge-ticket` at Gate 5. `feature/<KEY>` remains intact so any Stage 6 `BOUNCE` heals cleanly on `feature/<KEY>`, and `parallel_sprint_lock.py --merge-ticket <KEY> --sprint sprint/<SPRINT_NAME> --summary "<KEY>: <Concise Ticket Summary>"` runs strictly after `final-review-verdict <KEY> PASS`.

## Deliverable Template
Use the standardized markdown template located at:
`templates/walkthrough_template.md`
