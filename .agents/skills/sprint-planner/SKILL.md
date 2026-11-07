---
name: sprint-planner
description: Facilitates the agile Sprint lifecycle ceremonies. Conducts upfront Coordinator batch audit and exception-based clarification at Sprint-Start (keeping approved tickets in Zu erledigen until Stage 1 Analysis actually starts for each ticket) and facilitates the one-by-one Sprint-End Joint Review (evaluating tickets in Final Review, merging accepted tickets, or bouncing back to Analysis).
---

# Skill: sprint-planner

## Overview
This skill acts as the **Agile Sprint Facilitator & Scrum Master**. It orchestrates the two human-in-the-loop anchor points of every sprint and governs the **Strategy A (Sprint Integration Branch)** continuous integration model:
1. **Phase 1: Sprint-Start & Coordinator Ticket-Screening** (Establishing `sprint/<sprint_id>`, auditing all sprint tickets in detail upfront, and resolving any open questions while keeping tickets in `Zu erledigen` until each ticket's Stage 1 Analysis starts).
2. **Phase 2: Autonome In-Sprint Pipeline mit Continuous Sprint Integration** (Agents work autonomously, merging verified tickets directly into `sprint/<sprint_id>` to eliminate merge conflicts).
3. **Phase 3: Sprint-Ende & Joint Review** (Strict one-by-one evaluation on the integrated sprint build, release sign-off, and final merge to `develop`).

---

## Strategy A Branching Model: Sprint Integration Branch (`sprint/<sprint_id>`)

To eliminate merge conflicts across shared living documents (`docs/requirements.md`, `docs/tests.md`, Navigation, ViewModels) and allow subsequent sprint tickets to build immediately upon prior tickets:
* **Sprint Branch**: Created at Sprint-Start from `develop`: `sprint/<SPRINT_NAME>` (e.g. `sprint/2026-40.1`).
* **Feature Branches**: Each sprint ticket branches from `sprint/<SPRINT_NAME>`: `feature/<KEY>`.
* **Single Atomic Commit per Ticket ("One Commit per Ticket", Rule 44)**: As soon as a ticket completes Stage 5 (Clean-room tests pass & Gate 5 audit passes), the agent integrates `feature/<KEY>` into `sprint/<SPRINT_NAME>` as **strictly one atomic commit** via `git merge --squash feature/<KEY> && git commit -m "<KEY>: <Summary>"` (or a single Stage 5 commit), deletes `feature/<KEY>` (`git branch -D feature/<KEY>`), and transitions the parent ticket to `Final Review (Human)`.
* **Sprint-End Integration**: At Sprint-End, the human user validates the entire sprint build. Upon final sign-off, `sprint/<SPRINT_NAME>` is merged into `develop` (`--no-ff`) and deleted.

---

## Ceremony 1: Sprint-Start & Interaktives Ticket-Screening

### Objective
Ensure that every ticket committed to the active sprint has clear, unambiguous acceptance criteria, an understood problem statement, and no unresolved architectural questions before development begins, while establishing the clean sprint integration branch.

### Step-by-Step Procedure
1. **Query Active Sprint Backlog & Ensure `Review & Retro` Ticket Exists**:
   * Find all tickets in the active sprint currently in status `Zu erledigen` using the exact active sprint ID or name (do not use generic `openSprints()` which can match multiple concurrently active sprints):
     ```bash
     python3 tools/jira_util.py search "project = ATT AND sprint = <SPRINT_ID> AND status = 'Zu erledigen' ORDER BY rank ASC"
     ```
   * **Ensure Sprint `Review & Retro` Ticket Exists**: Jira has an automation intended to create a `Review & Retro` ticket for each sprint, but it frequently fails to fire. Therefore, during Ceremony 1 (Sprint Planning), the agent **MUST** check if a `Review & Retro` ticket exists in the active sprint and create it if missing:
     ```bash
     python3 tools/jira_util.py ensure-retro
     ```
     This command checks the active sprint for a `Review & Retro` ticket and, if none exists, creates `Review & Retro - Sprint <SPRINT_NAME>` (Type: `Verbesserung` / `10008`, Epic: `ATT-232`) and adds it to the active sprint.
   * *Strict Invariant*: Aside from `ensure-retro` for the sprint's `Review & Retro` ceremony ticket, AI agents MUST NEVER move backlog feature/bug tickets into active sprints autonomously (`"Agents must not move tickets to sprints!"`). Only the human user assigns backlog tickets to sprints.
2. **Establish Sprint Integration Branch**:
   Create and push the sprint integration branch from latest `develop`:
   ```bash
   git checkout develop
   git pull origin develop
   git checkout -b sprint/<SPRINT_NAME>  # e.g. sprint/2026-40.1
   ```
3. **Upfront Coordinator Batch Audit & Exception-Based Clarification (`Rule 77` — `ATT-5762`, `"At the sprint start, we do not have to go through each ticket in Detail. All tickets are approved by the human by moving the ticket into the sprint. Nevertheless, the coordinating Agent should critically check each ticket in detail and ask questions before the sprint is started."`)**:
   * **No Mandatory One-by-One Walkthrough at Sprint-Start**: Because only the human user assigns tickets to an active sprint, placing a ticket into the sprint already constitutes human pre-approval. Do **NOT** force a slow ticket-by-ticket walkthrough at Sprint-Start (note: strict one-by-one review `Rule 68` applies exclusively to **Ceremony 2: Sprint Review**).
   * **Mandatory Deep Coordinator Audit of Every Sprint Ticket**: Before starting Phase 2, the Coordinator MUST inspect every regular sprint ticket (`python3 tools/jira_util.py show <KEY>`, skipping `Review & Retro`) in detail:
     * **Duplicate / Overlapping Ticket Detection (`Rule 63` — `ATT-4670`)**: Check whether any two tickets in the sprint address the same root cause or overlapping scope. Flag any duplicate immediately and propose consolidating into the primary ticket.
     * **Story Point Estimation & Complexity Split Guardrail (`REQ-PRO-029`)**: Check that every ticket has a Story Point estimate (`1`, `2`, `3`, or `>= 5 -> mandatory split` on the AAA scale). Propose and set (`python3 tools/jira_util.py set-points <KEY> <POINTS>`) estimates for any unestimated tickets.
     * **Human Prerequisite Detection (`Rule 22`)**: Check whether any ticket depends on actions only the human can perform (OAuth registration, Play Console, external hardware).
     * **Ambiguity & Edge-Case Audit**: Critically verify that the problem statement, acceptance criteria, and architectural boundaries are unambiguous.
   * **Consolidated Presentation & Approval — Keep Tickets in `Zu erledigen` Until Stage 1 Starts (`Rule 78` — `ATT-5829`, `"Tickets sollten von den Agenten erst dann nach Analyse geschoben werden, wenn die Analyse startet."`)**:
     * Present a compact overview table of all sprint tickets (Rank, Key, Summary, Story Points, Audit Status) together with any specific clarifying questions or proposed estimates in a single turn.
     * Wait for the user's confirmation (and answers to any open questions) and update any refined descriptions (`python3 tools/jira_util.py update-desc <KEY> ...`).
     * **Strict Prohibition of Batch-Moving to `Analysis` at Sprint-Start (`Rule 78`)**: Do **NOT** batch-transition all sprint tickets to `Analysis` during Ceremony 1. Approved sprint tickets **MUST remain in `Zu erledigen`** until Stage 1 (`[Analysis]`) actually begins for that specific ticket in Phase 2 (so the Jira board reflects live progress and untouched tickets remain in `Zu erledigen` if a sprint ends early).
4. **Handoff to Autonomous Execution (Phase 2)**:
   Once all regular sprint tickets have been audited and confirmed in `Zu erledigen` (with the `Review & Retro` ticket retained for in-sprint anomaly logging and Ceremony 2 retrospective documentation), Phase 1 concludes. The agent runs `python3 tools/jira_util.py list` and verifies that the total sprint story point load aligns with the target capacity envelope (~24–30 SP for ~12–15 atomic tickets and ~30–45 min of focused human on-device verification). The implementation agents (`agent1`, `agent2`) take over Phase 2:
   * **Continuous Unattended Execution, Just-In-Time `Analysis` Transition (`Rule 78`) & Full-Sprint Mandate**: The sprint pipeline runs continuously and autonomously across the entire sprint backlog (`ORDER BY rank ASC`) without stopping the console, pausing between tickets, or asking whether to continue (`"Again, I was asked if we should continue. → Agents should do the entire Sprint."`). Right when work on an individual ticket begins at Stage 1, transition **only that ticket** from `Zu erledigen` to `Analysis` (`python3 tools/jira_util.py move <KEY> "analysis"`).
   * **Automatic Realization of Mid-Sprint Added Tickets (`Rule 65` — `ATT-4776`, `"When a new ticket is added to the sprint when the sprint is already started. Please realize this ticket."`)**: Because only the human user assigns backlog tickets to sprints, any ticket added by the human user to an already-started sprint during Phase 2 (`status = 'Zu erledigen'`, excluding `Review & Retro`) carries implicit human approval. Before concluding Phase 2, always re-query the active sprint for any newly added `Zu erledigen` tickets (`python3 tools/jira_util.py search "project = ATT AND sprint = <SPRINT_ID> AND status = 'Zu erledigen' ORDER BY rank ASC"`), and for each one, move it to `Analysis` (`python3 tools/jira_util.py move <KEY> "analysis"`) when starting its Stage 1 and execute Stages 1–5 on `sprint/<SPRINT_NAME>` without stopping or asking for confirmation.
   * **Synchronous Parent Stage Progression (Rule 3 / Rule 45)**: Whenever a stage subtask (`[Analysis]`, `[Req & Test Spec]`, `[Impl-Plan]`, `[Implementation]`, `[Test]`) transitions to `Erledigt` via `freigabe`, the parent ticket immediately advances to the next workflow column (`Test Spec` $\rightarrow$ `Implementation Plan` $\rightarrow$ `Implementation` $\rightarrow$ `Test` $\rightarrow$ `Final Review (Human)`), enforced automatically by `tools/jira_util.py move <SUBTASK_KEY> freigabe`.
   * **In-Sprint Human Escalation Protocol**: If an agent encounters a blocker, design decision, or requirement ambiguity that strictly requires human guidance, the agent MUST NOT halt or prompt the console. Instead:
     1. Post a clarifying question/decision comment on the subtask: `python3 tools/jira_util.py comment <SUBTASK_KEY> "Question for Human: ..."`
     2. Reassign the subtask to Human: `python3 tools/jira_util.py assign <SUBTASK_KEY> human`
     3. Immediately switch context to the next available sprint ticket and continue execution.
     4. When the human answers in a comment and reassigns to coordinator, the coordinator reassigns to `agent1` to resume the ticket.
   * Each ticket branches from the active `sprint/<SPRINT_NAME>` branch.
   * **Mandatory Rules & Skills Refresh ("Obey the Rules")**: At the start of handling each ticket, the agent MUST explicitly re-read `.agents/rules/aspice_governance.md` and the skill for the active stage using `view_file` to reload constraints into active working memory and prevent context drift across compactions.
   * **One Commit per Ticket (Rule 44)**: Upon completing Stage 5 verification, the ticket is integrated back into `sprint/<SPRINT_NAME>` as a **single atomic commit** (`git merge --squash feature/<KEY> && git commit -m "<KEY>: <Summary>"`).
   * **Pre-Build Debug APK at Very End of Phase 2 (`Rule 64` — `ATT-4718`, `"Build debug APK at very end of phase 2."`)**: Immediately after the final sprint ticket is squashed onto `sprint/<SPRINT_NAME>` at the end of Phase 2, run `./gradlew assembleDebug` (or `./gradlew installDebug` if the test device is connected) so the integrated sprint debug APK is already compiled and verified before Ceremony 2 begins.
   * **Tier 2 Sprint Integration Gate at the Very End of Phase 2 (`Rule 66` — `ATT-4864`, `"Maybe, the agents should do this before the sprint review."`)**: Before the APK pre-build, run `./gradlew testDebugUnitTest --max-workers=2 && python3 tools/verify_requirement_governance.py` on `sprint/<SPRINT_NAME>` and fix all failures as separate attributed commits, so Ceremony 2 starts from a green suite (Sprint 2026-44.1: 4 failures from stale tests and a cross-test coroutine leak surfaced only in the human review).
   * **Lost-Wakeup Watchdog (`Rule 66` — `ATT-4864`)**: After every subagent dispatch, set one `schedule` watchdog (`DurationSeconds=1200`, `TimerCondition=<subagent id>`); if it fires, re-derive the state from Jira/git and continue. Never end a Phase 2 Coordinator turn with only a status report while tickets remain, and never let a subagent take over the Coordinator role (see `aspice_governance.md` Core Rule 7).
   * *In-Sprint Anomaly Logging*: Any process hiccups, tool issues, or user corrections during execution are immediately logged as comments in the sprint's `Review & Retro` ticket.

---

## Ceremony 2: Sprint-Ende & Joint Review

### Objective
Jointly review all completed sprint tickets with the human user against expectations on the integrated sprint build, authorize release, handle rejected items, and cleanly close the sprint into `develop`.

### Step-by-Step Procedure
1. **Mandatory Device Build, Deployment & Background Tier 2 Unit Test Launch ('Install & Test at Review Start' — Rules 62 & 65)**:
   At the very start of the Sprint Review session (before commencing ticket inspection with the human user), the agent MUST:
   1. Compile and deploy the latest integrated sprint APK from `sprint/<SPRINT_NAME>` to the attached physical device (e.g. Google Pixel 10), and launch `com.atrainingtracker.debug` (`"Sprint Review session must start with installing the latest sprint version on the phone."`):
      ```bash
      ./gradlew installDebug && adb shell am start -n com.atrainingtracker.debug/com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation
      ```
   2. Immediately launch the **Tier 2 Sprint Integration Gate** (`./gradlew testDebugUnitTest --max-workers=2 && python3 tools/verify_requirement_governance.py`) **in the background** (`WaitMsBeforeAsync` $\le 1000$) right at the start of the review (`"We should start the full set of unit tests right at the start of the review in the background. Then, I don't have to wait for the results (and we might even get active during the review)."` — Retro 2026-43.6, `ATT-4596`; `--max-workers=2` prevents Robolectric fork `EOFException` — `ATT-4776`):
      ```bash
      ./gradlew testDebugUnitTest --max-workers=2 && python3 tools/verify_requirement_governance.py
      ```
      Since Rule 66 this run is a **confirmation** of the green Tier 2 gate executed at the end of Phase 2 and ensures `docs/engineering/test_results/index.html` (`app/build/reports/tests/testDebugUnitTest/index.html`) displays the complete HTML report of all unit tests (Rule 67 — `ATT-4936`); any failure here is itself a retro anomaly.

2. **Query Tickets Ready for Review in Rank Order**:
   Find all sprint tickets in status `Final Review (Human)` sorted strictly by backlog rank for the exact active sprint ID or name:
   ```bash
   python3 tools/jira_util.py search "project = ATT AND sprint = <SPRINT_ID> AND status = 'Final Review (Human)' ORDER BY rank ASC"
   ```

3. **Collaborative Ticket Inspection**:
   Ensure git is checked out on `sprint/<SPRINT_NAME>` (which contains all integrated sprint changes).
   * **Rule (Strict Rank Order)**: Review tickets strictly in backlog rank order (`ORDER BY rank ASC`) (`"During the sprint planning and the sprint review, always respect the rank of the tickets."`). No ticket may jump ahead of a higher-ranked ticket.
   * **Rule (Single-Ticket Focus — `Rule 68` / `ATT-5082`)**: Evaluate tickets **strictly one-by-one** (`"Please make one ticket after the other. Please also keep this in mind for the retro."` / `"During Review: Only one ticket at each time. 🙂"`). Never batch or present multiple tickets simultaneously. Complete verification and human acceptance for the current ticket before proceeding to the next. If a turn during Ceremony 2 revisits a *previous* ticket (e.g., to create or update a follow-up ticket) while ticket $N$ is pending review, and the user says *"Let's go to the next one"*, that means returning to ticket $N$ — **never** marking ticket $N$ approved or jumping to ticket $N+1$ before the user has explicitly evaluated ticket $N$.
   For each ticket in `Final Review (Human)`:
   * Present the walkthrough deliverable (`docs/engineering/walkthroughs/<KEY>_walkthrough.md`), a summary of changes, and a concise **Code Review of the actual git diff** (`git show` / key modified files, verifying edge-case handling, resource safety, and adherence to architectural invariants) (`"Within the last tickets, you also did a review of the code-changes. I would like to keep this."` — Sprint Retro 2026-42.4 / ATT-3679).
   * **Critical Co-Reviewer Completeness Sweep (ATT-3534)**: On refactoring, API modernization, cleanup, or migration tickets, run a targeted codebase completeness sweep (`grep -rn` or `rg` across `app/src/main`) to verify that no deprecated patterns, legacy helper calls, or orphaned references were missed in unmentioned files across the repository.
   * **Mandatory Proactive Review Recommendation (`Rule 63` — `ATT-4670`, `"For some recent tickets, I asked for your suggestion during the review. This should become standard."`)**: Always conclude the presentation of each ticket with a dedicated **`Recommendation`** section stating:
     1. Your explicit verdict recommendation (e.g., **Accept as `i.O.`**, **Accept with Follow-Up Ticket**, or **Deferred Field Test**) and the technical rationale why.
     2. Whether the ticket has an **on-device UI/behavioral touchpoint** (giving the exact screen/navigation path on the phone if the user wants to test it) or is a **pure backend/unit-test ticket** already 100% verified by the automated test suite.
   * User verifies on-device behavior (Pixel 10 APK built from `sprint/<SPRINT_NAME>`) and inspects the code review and recommendation.
   * **Evaluation Decision**:
     * **In Ordnung (i.O. / Accepted)**:
       1. Set the active unreleased Fix Version (`Lösungsversion`) on the parent ticket (Rule 14):
          ```bash
          python3 tools/jira_util.py set-fixversion <KEY> <VERSION>
          ```
       2. Human user transitions ticket from `Final Review (Human)` to `Erledigt` in Jira.
     * **User Feedback / Refinements ("No Code Changes During Review & Lean Defect Recording")**:
       * **Mandate**: Under NO circumstances may code be modified during Ceremony 2 (`"No code changes during the Sprint Review!"`).
       * **Lean Defect Recording**: Do NOT conduct a deep technical root-cause or code analysis during review (`"Während des Sprint Reviews soll es noch keine detaillierte Analyse der Auffälligkeit geben. Es soll eigentlich nur ein Ticket zur Aufnahme der Auffälligkeit erstellt werden. Dabei ist es jedoch wichtig, den Kontext der Beanstandung nicht zu verlieren. Falls erforderlich ist natürlich auch eine kurze Analyse notwendig."`). Only perform a brief sanity check if strictly required to frame the issue. Focus on capturing the exact human observation, steps to reproduce, user intent, and context without loss into a dedicated Jira follow-up ticket (automatically ranked at the top of the backlog):
         ```bash
         # TYPE can be "Bug" (10006), "Feature" (10007), or "Verbesserung" (10008)
         python3 tools/jira_util.py create-issue "<CLEAN_SUMMARY>" "<DESC_OR_@FILE>" <Feature|Bug|Verbesserung> [EPIC_KEY]
         # Upload on-device screenshot or log evidence:
         python3 tools/jira_util.py upload <NEW_KEY> <SCREENSHOT_PATH>
         ```
       * Set Fix Version on the current sprint ticket (`python3 tools/jira_util.py set-fixversion <KEY> <VERSION>`), have the human user transition the current sprint ticket to `Erledigt`, and proceed strictly to the next ticket.
     * **Deferred Review State for Field-Tested Features**:
       * When a sprint ticket modifies in-ride GPS dynamics, algorithmic filtering, sensor connectivity, or real-time cycling cockpit prompts requiring outdoor rides or real-world physical activities to evaluate, the ticket may remain in `Final Review (Human)` across sprint close while `sprint/<SPRINT_NAME>` is merged to `develop`.
     * **Nicht in Ordnung (n.i.O. / Rejection of Core Scope)**:
       * Move ticket back to `Analysis` with explicit human feedback:
         ```bash
         python3 tools/jira_util.py comment <KEY> "Revision needed: [Human feedback]"
         python3 tools/jira_util.py move <KEY> "analysis"
         ```
4. **Sprint Retrospective, Tier 2 Sprint Integration Gate & Process Hardening ("Retro Before Merge")**:
   * **Mandate**: Conduct the Retrospective, run the Tier 2 Sprint Integration Gate (`./gradlew testDebugUnitTest --max-workers=2 && python3 tools/verify_requirement_governance.py` if not already verified on the exact final code tree of `sprint/<SPRINT_NAME>`), and update governance documents on `sprint/<SPRINT_NAME>` **BEFORE** merging into `develop`.
   * **Mandatory Tier 2 Re-Verification When Any Ticket Is Dropped or Reverted During Review (`Rule 77` — `ATT-5762`)**: If any ticket commit is removed (`git reset --hard`) or reverted (`git revert`) from `sprint/<SPRINT_NAME>` during Ceremony 2 (e.g., `ATT-5665`), the pre-review Tier 2 test run is invalidated because the code state at Sprint-End has changed. You **MUST** re-execute the full Tier 2 Sprint Integration Gate (`./gradlew testDebugUnitTest --max-workers=2 && python3 tools/verify_requirement_governance.py`) and `./gradlew installDebug` on the post-drop sprint branch **before** merging into `develop`.
   * Synthesize real-time comments logged during the sprint in the `Review & Retro` ticket into actionable root cause analyses.
   * Document insights in `docs/engineering/retros/Sprint_Review_and_Retro_<KEY>.md`, including the Planned vs. Completed Story Points velocity metric table alongside ticket counts (`REQ-PRO-029`).
   * Update `.agents/rules/aspice_governance.md`, relevant skills, `docs/coding_standards.md`, `docs/design_guidelines.md`, and `docs/engineering/rule_history_archive.md` with permanent countermeasures.
   * Commit retrospective deliverables to `sprint/<SPRINT_NAME>`.
5. **Sprint Closure, Milestone Tagging & Merge to `develop`**:
   * Once all tickets and the Retrospective are signed off, merge `sprint/<SPRINT_NAME>` into `develop`, delete the local sprint branch (to keep the active branch list clean), and create an immutable `sprint/<SPRINT_NAME>` Git tag on the `--no-ff` merge commit so every completed sprint remains directly addressable by name (`git checkout sprint/<SPRINT_NAME>`, `git diff`, `regression-bisect`):
   ```bash
   git checkout develop
   git pull origin develop
   git merge --no-ff sprint/<SPRINT_NAME> -m "Merge branch 'sprint/<SPRINT_NAME>' into develop"
   git branch -d sprint/<SPRINT_NAME>
   git tag -f sprint/<SPRINT_NAME>
   git push origin develop
   git push origin sprint/<SPRINT_NAME>
   # Post-condition verification: ensure no merged sprint branches remain locally and sprint tag exists
   git branch --list 'sprint/*'
   git tag --list 'sprint/<SPRINT_NAME>'
   ```
   * Transition the sprint's `Review & Retro` ticket to `Final Review (Human)` (for human closure to `Erledigt`).
   * Post final integration notice to Jira.
