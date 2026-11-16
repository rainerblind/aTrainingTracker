---
name: sprint-planner
description: Facilitates the agile Sprint lifecycle ceremonies. Conducts upfront Coordinator batch audit and exception-based clarification at Sprint-Start (keeping approved tickets in Zu erledigen until Stage 1 Analysis actually starts for each ticket) and facilitates the one-by-one Sprint-End Joint Review (evaluating tickets in Final Review, merging accepted tickets, or bouncing back to Analysis).
---

# Skill: sprint-planner

## Overview
This skill acts as the **Agile Sprint Facilitator & Scrum Master**. It orchestrates the two human-in-the-loop anchor points of every sprint and governs the **Strategy A (Sprint Integration Branch)** continuous integration model:
1. **Phase 1: Sprint-Start & Coordinator Ticket-Screening** (Establishing `sprint/<sprint_id>`, auditing all sprint tickets in detail upfront, and resolving any open questions while keeping tickets in `Zu erledigen` until each ticket's Stage 1 Analysis starts).
2. **Phase 2: Autonome In-Sprint Pipeline mit Continuous Sprint Integration** (Agents work autonomously, merging verified tickets directly into `sprint/<sprint_id>` to eliminate merge conflicts).
3. **Phase 3: Sprint-Ende & Joint Review** (Strict one-by-one evaluation on the integrated, Tier-2-verified sprint baseline, release sign-off, and final merge to `develop`).

### PO Coordinator Model Selection & Phase-Boundary Handoff Checkpoints (`Rule 81` — `ATT-6289`)
The Product Owner (human user) manually switches the top-level Coordinator model across the three sprint phases (while persistent Epic subagents in Phase 2 explicitly specify `Model: "pro"` and `Model: "flash"` per Core Rule 7):
* **Planning (Ceremony 1)**: `Argon` or `Carbon`
* **Main Execution (Phase 2 Coordinator)**: `Flash`
* **Review & Retro (Ceremony 2 — Sprint & Epic Reviews)**: `Gemini 4 Carbon` (`Pro` model for Sprint and Epic Reviews only)

Because model switching in the chat UI is a manual human action, the Coordinator **MUST** enforce two explicit phase-boundary handoff checkpoints (and **zero** interruptions inside Phase 2 itself):
1. **End of Ceremony 1 $\rightarrow$ Phase 2 Handoff**: After sprint screening and open questions are resolved (with all tickets kept in `Zu erledigen` per Rule 78), conclude Ceremony 1 with: *"Ceremony 1 Planning complete. Please switch the model to `Flash` and reply `start` to begin autonomous Phase 2 execution."* Wait for the user's `start` on `Flash` before spawning subagents.
2. **End of Phase 2 $\rightarrow$ Ceremony 2 Handoff**: After all sprint tickets reach `Final Review (Human)` and the Tier 2 Sprint Integration & CI Gate + `./gradlew installDebug` pass 100%, conclude Phase 2 with: *"Phase 2 and Tier 2 Gate complete. Please switch the model to `Gemini 4 Carbon` to begin Ceremony 2 (Sprint & Epic Review and Retro)."*

---

## Strategy A Branching Model & PO-Aligned Continuous `Schicht` Cadence (`ATT-8030` evolved)

To eliminate merge conflicts across shared living documents (`docs/requirements.md`, `docs/tests.md`, Navigation, ViewModels), maintain continuous autonomous delivery, and decouple human device testing from development blocking:
* **PO-Aligned 3-`Schicht` Cadence (`05:00`, `15:00`, `22:00`) & Naming (`YYYY-MM-DD_0x_Schichtname`)**:
  * Continuous autonomous sprints operate as three PO-aligned **`Schicht` windows** local time:
    - **`01_Früh`**: `05:00 – 15:00` (10h)
    - **`02_Spät`**: `15:00 – 22:00` (7h)
    - **`03_Nacht`**: `22:00 – 05:00` (7h)
    using naming **`YYYY-MM-DD_0x_Schichtname`** (e.g. `2026-11-16_02_Spät`, `2026-11-16_03_Nacht`).
  * **Continuous Zero-Pause WIP Cutover (`05:00` / `15:00` / `22:00`)**: Active `feature/<KEY>` subagent implementations do **NOT** stop or revert at the Schicht boundary. At cutoff: freeze merges onto `sprint/<CLOSING_SCHICHT>`, keep all Stage-6-verified `Final Review (Human)` items in the closing Schicht (archived post-close into `Bereit für PO Abnahme` ID `453` with `sprint-<SCHICHT_ID>` label and zero artificial WIP/overflow cap), run the Tier-2 Gate (`./gradlew testDebugUnitTest :app:compileDebugAndroidTestKotlin assembleDebug --max-workers=2 && python3 tools/verify_requirement_governance.py` — `:app:lintDebug`, `:app:detekt`, and `:app:koverXmlReportDebug` run weekly during the Dual-Vendor Code Review / ASPICE Audit per `Rule 3.5` and on releases), deploy best-effort to `Pixel 10` (`ANDROID_SERIAL=<physical_serial> ./gradlew installDebug`) ONLY if attached (never blocking rollover if unplugged), merge `sprint/<CLOSING_SCHICHT>` `--no-ff` into `develop`, and tag `sprint/<CLOSING_SCHICHT>`.
  * Immediately branch `sprint/<NEW_SCHICHT>` off updated `develop`, carry active WIP directly into the new Jira Schicht (`carryover-wip`), refill open lane capacity from **`Vom PO Freigegeben`** (ID `447`) `ORDER BY rank ASC`, and resume Strategy A merges onto `sprint/<NEW_SCHICHT>` (or pause cleanly when both `447` and active WIP are empty).
* **Protected Four-Tier Jira Board (`SUP.10.BP4`)**:
  1. **Tier 1 (`active` Schicht)**: Current `Schicht` execution container.
  2. **Tier 2 (`Vom PO Freigegeben`, permanent `future` pseudo-sprint ID `447`)**: Strictly PO-curated approved queue positioned below active/planned Schichten. Agents SHALL NEVER promote Product Backlog tickets into ID `447` or reorder it (`jira_util.py` tamper lock); `--as coordinator` may pull ranked `Zu erledigen` tickets out of `447` into a starting Schicht and keep `447` below future Schichten via `/rest/agile/1.0/sprint/{id}/swap`.
  3. **Protected Human Queues (`future` pseudo-sprints — NEVER touched by AI work loops)**:
     * **`Bereit für PO Abnahme`** (ID `453`): Collects completed `Final Review (Human)` tickets moved out of closed Schichten post-close (`archive-po-acceptance`) so Backlog and `447` remain uncluttered until PO sign-off -> `Erledigt`.
     * **`Bereit für PO Systemtest`** (ID `454`): Collects `human-execution` physical system verification tickets (`SYS.5`) and *Deferred Field Test* items requiring real outdoor rides / hardware testing (`Rule 7.6`). Never pulled into active AI sprints (`pack-sprint` skips `human-execution` and `454`).
  4. **Tier 4 (`Product Backlog`)**: Unapproved backlog / follow-up proposals.
* **Dual-Ticket Retrospective Model (`Review & Retro` + Rolling `Wochen Review & Retro`)**:
  * Retain a lean per-Schicht `Review & Retro - Schicht YYYY-MM-DD_0x_Schichtname (HH:MM - HH:MM)` ticket (logs automated metrics, gates, QA audit, optional `installDebug` status & anomalies) alongside a persistent rolling **`Wochen Review & Retro`** ticket (Epic `ATT-232`) pushed forward week-to-week for the 1x/week consolidated human retrospective (weekly SPC trends, `docs/management/risk_register.md`, skill updates, and weekly external review tickets).
* **Sprint Branch**: Created at Schicht-Start from `develop`: `sprint/<SCHICHT_NAME>` (e.g. `sprint/2026-11-16_02_Spät`).
* **Feature Branches**: Each ticket branches from `sprint/<SCHICHT_NAME>`: `feature/<KEY>`.
* **Single Atomic Commit per Ticket ("One Commit per Ticket", Rule 44)**: As soon as a ticket completes Stage 5 (Clean-room tests pass & Gate 5 audit passes), the agent integrates `feature/<KEY>` into the active `sprint/<SCHICHT_NAME>` as **strictly one atomic commit** via `git merge --squash feature/<KEY> && git commit -m "<KEY>: <Summary>"` (or `--merge-ticket`), deletes `feature/<KEY>`, completes Stage 6 Cold Review, and transitions the parent ticket to `Final Review (Human)` (`sprint-<SCHICHT_ID>` label).
* **Schicht-End Integration**: Upon Tier-2 Gate green at `05:00`/`15:00`/`22:00` (or interactive Ceremony 2 close), `sprint/<CLOSING_SCHICHT>` merges `--no-ff` into `develop` (`sprint/<CLOSING_SCHICHT>` tag preserved, local branch deleted).

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
   * **Ensure Sprint `Review & Retro` and Weekly Cadence Tickets Exist**: Jira has an automation intended to create ceremony tickets, but it frequently fails to fire. Therefore, during Ceremony 1 (Sprint Planning), the agent **MUST** ensure both sprint and rolling weekly tickets exist:
     ```bash
     python3 tools/jira_util.py ensure-retro
     python3 tools/jira_util.py ensure-weekly-retro
     python3 tools/jira_util.py ensure-weekly-reviews
     ```
     `ensure-weekly-reviews` checks whether `> 7 days` have elapsed since the latest completed reviews in `docs/engineering/code_reviews/` and `docs/engineering/aspice_audits/` and automatically creates deduplicated Weekly Dual-Vendor Code Review and ASPICE 4.0 Audit tickets under Epic `ATT-232` ranked at `rank-top`.
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
     * **Human Prerequisite & Upstream Ticket Dependency Detection (`Rule 22` & `Rule 81` — `ATT-6181`/`ATT-6139`)**: Check whether any ticket depends on actions only the human can perform (OAuth registration, Play Console, external hardware) OR declares upstream Jira ticket dependencies (`Dependencies: ATT-XXXX`) that were left in the backlog outside the sprint; flag any missing upstream prerequisite immediately.
     * **Ambiguity & Edge-Case Audit**: Critically verify that the problem statement, acceptance criteria, and architectural boundaries are unambiguous.
   * **Project Risk Register Review & Timestamp Update (`REQ-STP-010` — `ATT-6151`, ASPICE `MAN.3.BP5`)**:
     * The Coordinator MUST inspect `docs/management/risk_register.md`:
       1. Verify that active project risks (`RISK-001`..`RISK-006`) are reviewed against current sprint scope and operational context.
       2. Confirm that every risk evaluated with High Exposure ($E \ge 15$) links to an active or planned Jira remediation ticket.
       3. Append a row to the Sprint Review Log table in `docs/management/risk_register.md` recording the review date, current sprint ID, and reviewer notes.
   * **Prior Sprint QA Audit Corrective Action Verification (`REQ-PRO-091.4` — `ATT-7558`, ASPICE `SUP.1`)**:
     * The Coordinator MUST inspect the previous sprint's QA process audit report in `docs/engineering/qa_audits/`:
       1. Review Section 4 ("Non-Compliance Tracking & Remediation") of the prior sprint's QA audit report.
       2. Verify that all referenced Jira corrective-action tickets (`[ATT-XXXX]`) for non-compliant findings are either completed (`Erledigt`) or actively scheduled in the active sprint backlog.
       3. Flag any unaddressed or orphaned non-compliance findings to the human user before sprint execution begins.
   * **Consolidated Presentation & Approval — Keep Tickets in `Zu erledigen` Until Stage 1 Starts (`Rule 78` — `ATT-5829`, `"Tickets sollten von den Agenten erst dann nach Analyse geschoben werden, wenn die Analyse startet."`)**:
     * Present a compact overview table of all sprint tickets (Rank, Key, Summary, Story Points, Audit Status) together with any specific clarifying questions or proposed estimates in a single turn.
     * Wait for the user's confirmation (and answers to any open questions) and update any refined descriptions (`python3 tools/jira_util.py update-desc <KEY> ...`).
     * **Strict Prohibition of Batch-Moving to `Analysis` at Sprint-Start (`Rule 78`)**: Do **NOT** batch-transition all sprint tickets to `Analysis` during Ceremony 1. Approved sprint tickets **MUST remain in `Zu erledigen`** until Stage 1 (`[Analysis]`) actually begins for that specific ticket in Phase 2 (so the Jira board reflects live progress and untouched tickets remain in `Zu erledigen` if a sprint ends early).
4. **Handoff to Autonomous Execution (Phase 2)**:
   Once all regular sprint tickets have been audited and confirmed in `Zu erledigen` (with the `Review & Retro` ticket retained for in-sprint anomaly logging and Ceremony 2 retrospective documentation), Phase 1 concludes. The agent runs `python3 tools/jira_util.py list` and verifies that the total sprint story point load aligns with the target capacity envelope (~24–30 SP for ~12–15 atomic tickets and ~30–45 min of focused human on-device verification). The implementation agents (`agent1`, `agent2`) take over Phase 2:
   * **Continuous Unattended Execution, In-Progress Rollover Priority (`Rule 82` — `ATT-7161`), Just-In-Time `Analysis` Transition (`Rule 78`) & Full-Sprint Mandate**: The sprint pipeline runs continuously and autonomously across the entire sprint backlog (`ORDER BY rank ASC`) without stopping the console, pausing between tickets, or asking whether to continue (`"Again, I was asked if we should continue. → Agents should do the entire Sprint."`). **Always drain and heal rollover in-progress / Stage-6-bounced tickets FIRST** (`status IN ('Implementation', 'Analysis', 'Test Spec', 'Implementation Plan', 'Test', 'Final Review (Agent)') ORDER BY rank ASC`, excluding `Review & Retro`) before starting untouched `Zu erledigen` tickets, so bounced rollover WIP is never stranded in `Implementation` by `Zu erledigen`-only queries (`ATT-7161`). Right when work on an untouched `Zu erledigen` ticket begins at Stage 1, transition **only that ticket** from `Zu erledigen` to `Analysis` (`python3 tools/jira_util.py move <KEY> "analysis"`).
   * **Automatic Realization of Mid-Sprint Added Tickets (`Rule 65` — `ATT-4776`, `"When a new ticket is added to the sprint when the sprint is already started. Please realize this ticket."`)**: Because only the human user assigns backlog tickets to sprints, any ticket added by the human user to an already-started sprint during Phase 2 (`status = 'Zu erledigen'`, excluding `Review & Retro`) carries implicit human approval. Before concluding Phase 2, always re-query the active sprint for any newly added `Zu erledigen` tickets (`python3 tools/jira_util.py search "project = ATT AND sprint = <SPRINT_ID> AND status = 'Zu erledigen' ORDER BY rank ASC"`), and for each one, move it to `Analysis` (`python3 tools/jira_util.py move <KEY> "analysis"`) when starting its Stage 1 and execute Stages 1–5 on `sprint/<SPRINT_NAME>` without stopping or asking for confirmation.
   * **Synchronous Parent Stage Progression (Rule 3 / Rule 45 / REQ-PRO-058)**: Whenever a stage subtask (`[Analysis]`, `[Req & Test Spec]`, `[Impl-Plan]`, `[Implementation]`, `[Test]`) transitions to `Erledigt` via `freigabe`, the parent ticket immediately advances to the next workflow column (`Test Spec` $\rightarrow$ `Implementation Plan` $\rightarrow$ `Implementation` $\rightarrow$ `Test` $\rightarrow$ `Final Review (Agent)`), enforced automatically by `tools/jira_util.py move <SUBTASK_KEY> freigabe`. Once in `Final Review (Agent)` (Stage 6), an independent **Cold `Gemini 4 Carbon` (`Model: "pro"`)** reviewer audits the squashed commit and executes `final-review-verdict <KEY> PASS|BOUNCE ...` (with up to 3 healing bounces) to advance the ticket to `Final Review (Human)`.
   * **In-Sprint Human Escalation Protocol**: If an agent encounters a blocker, design decision, or requirement ambiguity that strictly requires human guidance, the agent MUST NOT halt or prompt the console. Instead:
     1. Post a clarifying question/decision comment on the subtask: `python3 tools/jira_util.py comment <SUBTASK_KEY> "Question for Human: ..."`
     2. Reassign the subtask to Human: `python3 tools/jira_util.py assign <SUBTASK_KEY> human`
     3. Immediately switch context to the next available sprint ticket and continue execution.
     4. When the human answers in a comment and reassigns to coordinator, the coordinator reassigns to `agent1` to resume the ticket.
   * Each ticket branches from the active `sprint/<SPRINT_NAME>` branch.
   * **Mandatory Rules & Skills Refresh ("Obey the Rules")**: At the start of handling each ticket, the agent MUST explicitly re-read `.agents/rules/aspice_governance.md` and the skill for the active stage using `view_file` to reload constraints into active working memory and prevent context drift across compactions.
   * **One Commit per Ticket (Rule 44)**: Upon completing Stage 5 verification, the ticket is integrated back into `sprint/<SPRINT_NAME>` as a **single atomic commit** (`git merge --squash feature/<KEY> && git commit -m "<KEY>: <Summary>"`).
   * **Parallel Sprint Execution via Multi-Lane Greedy Bin-Packing (`Rule 9`, `REQ-PRO-041`, `Rule 81`, `Rule 85`, `REQ-PRO-097` — `ATT-6289`, `ATT-7025`, `ATT-8030`, `ATT-8162`)**: Dynamically fill `1..3` active parallel lanes using **Multi-Lane Greedy Bin-Packing**, scanning **`Vom PO Freigegeben`** (ID `447`) / sprint queue strictly in `ORDER BY rank ASC` (never naive `top[:X]` slicing!) and selecting only completely disjoint Parent Epics with zero package and zero living-doc shard overlap (preferring at most 1–2 UI-touching Epics alongside Backend/Service/DB/Process Epics). Each lane accommodates tickets up to the per-lane capacity ceiling (`max_sp_per_lane`, default 8 SP per `Schicht` per `REQ-PRO-097`); excess tickets of saturated Epics remain in `447` without skipping higher-ranked eligible tickets. Architecture/strategy ADRs (`Migrate vs. Keep`) MUST NOT spawn backlog follow-up tickets without prior PO approval. Persistent subagents for parallel tickets MUST be spawned with `Workspace: "share"` to ensure isolated Git worktrees. Persistent pairs in parallel lanes operate on detached `HEAD` (`git checkout --detach sprint/<SCHICHT_NAME>`) in a single shared Epic worktree to avoid intra-pair split-brain. Heavy Gradle test runs, emulator ADB verification (`emulator-5554`), and Stage 5 squash-merges onto `sprint/<SCHICHT_NAME>` serialize under POSIX `flock` on `/tmp/att_build_merge.lock` (`python3 tools/parallel_sprint_lock.py <cmd>`). Stage 5 squash-merges execute via `python3 tools/parallel_sprint_lock.py --merge-ticket <KEY> --sprint sprint/<SCHICHT_NAME> --summary "<KEY>: <Summary>"`.
   * **Pre-Build Debug APK & Best-Effort Install onto Physical Phone (`Rule 64`, `Rule 85` — `ATT-4718`, `ATT-8030`)**: Immediately after Tier-2 passes on `sprint/<CLOSING_SCHICHT>`, assemble the debug APK (`./gradlew assembleDebug`), deploy onto an attached physical device ONLY if connected (`ANDROID_SERIAL=<physical_serial> ./gradlew installDebug`, skipping emulators per `REQ-TST-004` — if unplugged, log deferral cleanly and continue rollover without pausing), and merge `--no-ff` to `develop`.
   * **Tier 2 Schicht Integration & CI Static-Analysis Gate at Boundary (`05:00`/`15:00`/`22:00` — `Rules 66, 81, 85`)**: Once `sprint/<CLOSING_SCHICHT>` freezes at the cutoff while active feature worktrees keep running uninterrupted, run `./gradlew testDebugUnitTest :app:compileDebugAndroidTestKotlin assembleDebug --max-workers=2 && python3 tools/verify_requirement_governance.py` on `sprint/<CLOSING_SCHICHT>` (`:app:lintDebug`, `:app:detekt`, and `:app:koverXmlReportDebug` are coupled to weekly Dual-Vendor Code Reviews / ASPICE Audits per `Rule 3.5` and releases) (plus `git ls-tree HEAD ANT-Android-SDKs` verifying `160000` gitlink integrity) and resolve any failures before merging into `develop`. Then move closing `Final Review (Human)` items to `Bereit für PO Abnahme` (`453`) post-close, open `sprint/<NEW_SCHICHT>`, and roll active WIP straight across via `carryover-wip`.
   * **Lost-Wakeup Watchdog (`Rule 66` — `ATT-4864`)**: After every subagent dispatch, set one `schedule` watchdog (`DurationSeconds=1200`, `TimerCondition=<subagent id>`); if it fires, re-derive the state from Jira/git and continue. Never end a Phase 2 Coordinator turn with only a status report while tickets remain, and never let a subagent take over the Coordinator role (see `aspice_governance.md` Core Rule 7).
   * *In-Sprint Anomaly Logging*: Any process hiccups, tool issues, or user corrections during execution are immediately logged as comments in the sprint's `Review & Retro` ticket.

---

## Ceremony 2: Sprint-Ende & Joint Review

### Objective
Jointly review all completed sprint tickets with the human user against expectations on the integrated sprint build, authorize release, handle rejected items, and cleanly close the sprint into `develop`.

### Step-by-Step Procedure
0. **Step 0.5 — Stage 6 (`Final Review (Agent)`) Verification & Retroactive Cold Review Sweep (`REQ-PRO-058`, `ATT-7101`)**:
   Before presenting tickets to the human user in Step 3, verify whether any completed sprint tickets are still in status **`Final Review (Agent)`**:
   ```bash
   python3 tools/jira_util.py search "project = ATT AND sprint = <SPRINT_ID> AND status = 'Final Review (Agent)' ORDER BY rank ASC"
   ```
   * **Retroactive Batch Mode (When tickets remain in `Final Review (Agent)` at Sprint-End)**:
     1. Group the `Final Review (Agent)` tickets by Parent Epic (or batches of 5–10 tickets) and dispatch fresh **Cold `Gemini 4 Carbon` (`Model: "pro"`)** reviewers (`stage6-final-review/SKILL.md`) to audit each ticket's squashed commit on `sprint/<SPRINT_NAME>` against the 4-part Cold Audit Checklist.
     2. For each ticket that passes, execute `python3 tools/jira_util.py --as agent2 final-review-verdict <KEY> PASS "<Executive Review Summary>"` to advance it to `Final Review (Human)` and assign it to `human`.
     3. For any ticket that fails the Cold Audit, execute `python3 tools/jira_util.py --as agent2 final-review-verdict <KEY> BOUNCE "<Defect Report>" --bounce-target=implementation`, heal the defect on `sprint/<SPRINT_NAME>` (up to 3 bounces), and re-verify until all sprint tickets have advanced to `Final Review (Human)`.
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
      Since Rule 66 this run is a **confirmation** of the green Tier 2 gate executed at the end of Phase 2 and ensures unit tests pass cleanly before `docs/engineering/test_evidence/<sprint>.md` is exported (`REQ-PRO-100`); any failure here is itself a retro anomaly.

2. **Query Tickets Ready for Review in Rank Order**:
   Find all sprint tickets in status `Final Review (Human)` sorted strictly by backlog rank for the exact active sprint ID or name:
   ```bash
   python3 tools/jira_util.py search "project = ATT AND sprint = <SPRINT_ID> AND status = 'Final Review (Human)' ORDER BY rank ASC"
   ```

3. **Collaborative Ticket Inspection**:
   Ensure git is checked out on `sprint/<SPRINT_NAME>` (which contains all integrated sprint changes).
   * **Rule (Strict Rank Order)**: Review tickets strictly in backlog rank order (`ORDER BY rank ASC`) (`"During the sprint planning and the sprint review, always respect the rank of the tickets."`). No ticket may jump ahead of a higher-ranked ticket.
   * **Rule (Single-Ticket Focus & Zero Tool Calls in Presentation Turn — `Rule 68` / `Rule 79`, `ATT-5082`, `ATT-5890`)**: Evaluate tickets **strictly one-by-one** (`"Please make one ticket after the other. Please also keep this in mind for the retro."` / `"During Review: Only one ticket at each time. 🙂"` / `"Gerade wurden irgendwie zwei Tickets vermischt. Ich ahbe nur das erste angenommen."`). Never batch or present multiple tickets simultaneously. Complete all preparatory tool calls (`set-fixversion` for Ticket $N-1$, `read_file` / `git show` for Ticket $N$) **before** emitting the presentation response for Ticket $N$, and ensure the response presenting Ticket $N$ contains **zero tool calls** so the agent turn terminates immediately and never continues after a trailing tool call to output Ticket $N+1$ before the user has responded to Ticket $N$. If a turn during Ceremony 2 revisits a *previous* ticket (e.g., to create or update a follow-up ticket) while ticket $N$ is pending review, and the user says *"Let's go to the next one"*, that means returning to ticket $N$ — **never** marking ticket $N$ approved or jumping to ticket $N+1$ before the user has explicitly evaluated ticket $N$.
   For each ticket in `Final Review (Human)`:
   * Present the walkthrough deliverable (`docs/engineering/walkthroughs/<KEY>_walkthrough.md`), a summary of changes, and a concise **Code Review of the actual git diff** (`git show` / key modified files, verifying edge-case handling, resource safety, and adherence to architectural invariants) (`"Within the last tickets, you also did a review of the code-changes. I would like to keep this."` — Sprint Retro 2026-42.4 / ATT-3679).
   * **Critical Co-Reviewer Completeness & Dynamic Symbol-vs-Source Sweep (`ATT-3534`, `Rule 81` — `ATT-6179`, `ATT-6289`)**: On refactoring, API modernization, cleanup, or migration tickets, run a targeted codebase completeness sweep (`grep -rn` or `rg` across `app/src/main`) to verify that no deprecated patterns, legacy helper calls, unmounted UI/importer islands, or `*DatabaseManager` backdoor getters were missed. On architecture (`SWE.2`) and system (`SYS.2`/`SYS.3`) documentation tickets, run a dynamic symbol-vs-source sweep verifying that every backticked `.kt`/`.java`/`.xml` filename, class, method signature, and manifest attribute exists in `app/src/main/` and that cross-document requirement allocations (`SYS.2 <-> SYS.3`) have exact bidirectional ID set equality.
   * **Mandatory Proactive Review Recommendation (`Rule 63` — `ATT-4670`, `"For some recent tickets, I asked for your suggestion during the review. This should become standard."`)**: Always conclude the presentation of each ticket with a dedicated **`Recommendation`** section stating:
     1. Your explicit verdict recommendation (e.g., **Accept as `i.O.`**, **Accept with Follow-Up Ticket**, or **Deferred Field Test**) and the technical rationale why.
     2. Whether the ticket has an **on-device UI/behavioral touchpoint** (giving the exact screen/navigation path on the phone if the user wants to test it) or is a **pure backend/unit-test ticket** already 100% verified by the automated test suite.
   * User verifies on-device behavior (Pixel 10 APK built from `sprint/<SPRINT_NAME>` and deployed to real physical hardware per Two-Tier Device Policy `ATT-6506` / `REQ-TST-004`: `ANDROID_SERIAL=<physical_serial> ./gradlew installDebug`) and inspects the code review and recommendation.
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
       * When a sprint ticket modifies in-ride GPS dynamics, algorithmic filtering, sensor connectivity, or real-time cycling cockpit prompts requiring outdoor rides or real-world physical activities to evaluate, move it into the dedicated human field-verification queue **`Bereit für PO Systemtest`** (ID `454`) with label `human-execution` via `python3 tools/jira_util.py --as coordinator defer-systemtest <KEY>` (keeping status `Final Review (Human)` / assigned to `human` across sprint close while `sprint/<SPRINT_NAME>` is merged to `develop`).
     * **Nicht in Ordnung (n.i.O. / Rejection of Core Scope)**:
       * Move ticket back to `Analysis` with explicit human feedback:
         ```bash
         python3 tools/jira_util.py comment <KEY> "Revision needed: [Human feedback]"
         python3 tools/jira_util.py move <KEY> "analysis"
         ```
4. **Milestone Epic Review & Human-Confirmed Closure (`REQ-PRO-040`)**:
   * Inspect project Epics via CLI:
     ```bash
     python3 tools/jira_util.py list-epics
     ```
   * If any **Milestone Epic** (e.g. `Periods 2.0`, `Cycling Dynamics`) has **0 open child tickets** remaining and all intended scope is verified:
     1. Proactively present the Epic completion status to the human user.
     2. Recommend closing the Milestone Epic (Evergreen Epics remain open indefinitely).
     3. Upon explicit human confirmation, close the Epic:
        ```bash
        python3 tools/jira_util.py --as coordinator close-epic <EPIC_KEY>
        ```
     4. *Strict Invariant*: Standard sprint tickets (`Feature`, `Bug`, `Verbesserung`) remain strictly under the Human Decision Gate. Only Milestone Epics with 0 open child tickets may be closed via `close-epic` after human confirmation.
5. **Sprint Retrospective, Tier 2 Sprint Integration Gate & Process Hardening ("Retro Before Merge")**:
   * **Mandate**: Conduct the Retrospective, run the Tier 2 Sprint Integration Gate (`./gradlew testDebugUnitTest --max-workers=2 && python3 tools/verify_requirement_governance.py` if not already verified on the exact final code tree of `sprint/<SPRINT_NAME>`), and update governance documents on `sprint/<SPRINT_NAME>` **BEFORE** merging into `develop`.
   * **Mandatory Quantitative Sprint Metrics & Scorecard Generation (PA 4.1 & PA 4.2 — `REQ-PRO-038`, `REQ-PRO-060`, `ATT-5764`, `ATT-6150`)**:
     At the very start of Step 4, the Coordinator **MUST** execute:
     ```bash
     python3 tools/jira_util.py --as coordinator sprint-metrics --record --post-to-retro
     python3 tools/verify_requirement_governance.py --check-metrics
     ```
     This calculates Gate 1–5 First-Time-Pass-Rates (FTPR), Reason Swiss Cheese defect escape rates ($p_1 \dots p_5$, $P_{\text{escape}} = \prod p_i$), and Statistical Process Control (SPC) moving baseline limits ($\mu \pm 2\sigma$), idempotently updates `docs/engineering/metrics/process_metrics.jsonl`, verifies that planned and completed story points are $> 0$ via `--check-metrics`, and posts the markdown scorecard directly as a comment on the sprint's `Review & Retro` ticket.
   * **Mandatory Sprint Quality Gate Records Export (`REQ-PRO-059`, `ATT-6149`)**:
     To maintain independent ASPICE SUP.1 (Quality Assurance) and GP 2.2 (Work Product Evidence) proof in repository configuration management, the Coordinator **MUST** export all Gate 1–5 and Stage 6 review verdicts to version control and verify its presence before sprint merge:
     ```bash
     python3 tools/jira_util.py --as coordinator export-gates
     python3 tools/verify_requirement_governance.py --check-gates
     ```
     This exports all gate decisions, review comments, evaluator roles, actor IDs, and timestamps to `docs/engineering/gates/<sprint>.md`.
   * **Mandatory Sprint Test & Coverage Evidence Export (`REQ-PRO-100`, `ATT-8144`)**:
     To maintain baseline-controlled test results and coverage metrics in repository configuration management (`SUP.8`), the Coordinator **MUST** export unit test counts, per-TST statuses, and Kover line/branch coverage to version control and verify its presence before sprint merge:
     ```bash
     python3 tools/jira_util.py --as coordinator export-test-evidence
     python3 tools/verify_requirement_governance.py --check-test-evidence
     ```
     This persists versioned evidence to `docs/engineering/test_evidence/<sprint>.md` and `<sprint>.json`.
   * **Mandatory Sprint QA Process-Compliance Audit (`REQ-PRO-061`, `REQ-PRO-091`, `ATT-6154`, `ATT-7558`)**:
     To maintain independent ASPICE SUP.1 process compliance evidence, an independent QA auditor (`--as qa` or `--as agent2`, rejecting `coordinator` and `agent1` per `REQ-PRO-091`) **MUST** dynamically sample $\ge \max(2, \lceil 0.10 \times N \rceil)$ sprint tickets and execute an independent QA process-compliance audit before sprint merge:
     ```bash
     python3 tools/jira_util.py --as agent2 qa-audit   # or --as qa
     python3 tools/verify_requirement_governance.py --check-qa-audits
     ```
     This evaluates the 5-point lifecycle process checklist (artifacts, gate order, 4-Augen role separation, living doc parity, commit hygiene) and persists the audit report in `docs/engineering/qa_audits/<sprint>.md`.
   * **Mandatory 'QA Findings & Corrective Actions' Retrospective Section & Multi-Sprint PO Escalation (`REQ-PRO-091.4`, `ATT-8153`)**:
     The Retrospective document (`docs/engineering/retros/Sprint_Review_and_Retro_<KEY>.md`) MUST contain a dedicated `## QA Findings & Corrective Actions` section reviewing:
     1. Audit verdict and findings from `docs/engineering/qa_audits/<sprint>.md`.
     2. Status of all dedicated corrective action tickets created by `qa-audit` under Epic ATT-8138/ATT-232.
     3. **Multi-Sprint PO Escalation Protocol (`REQ-PRO-091.4`, SUP.1 BP6)**: When an open QA corrective action ticket remains unresolved for more than 2 sprints (> 2 sprints) or the same non-conformance category recurs in consecutive sprints, the Retrospective deliverable MUST explicitly include a prominent `⚠️ PO Escalation: Persistent QA Non-Conformance` alert with the ticket links, root-cause summary, and escalation rationale.
   * **Quality Gate Verdict Spot-Check Auditing (`REQ-PRO-092.6`, `ATT-7559`)**:
     To prevent gate discrimination blind spots and verify that gate subtask verdicts authentically reflect technical quality, the QA Auditor / Coordinator **MUST** spot-check a sample of completed gate subtasks (Gates 1–5), verify that written review verdicts correlate with codebase evidence, and document any discrepancy observations or false-pass rates in the Retrospective deliverable.
   * **Mandatory Independent QA & Cross-Vendor Review Recording (`REQ-PRO-062`, `ATT-6155`, `ATT-8030`, `ATT-8142`)**:
     To maintain demonstrable QA independence from the authoring agent vendor under ASPICE SUP.1 (Quality Assurance), the Coordinator **MUST** record the independent Human Sprint Review (Ceremony 2) and any Layer 5 cross-vendor reviews into repository configuration management before sprint merge. All review records mandate `--verdict` and explicit `--findings` (defaulting to PASS or ungrounded success strings is strictly prohibited). If interactive Ceremony 2 review was skipped (12h auto-merge cadence or direct-to-Retro PO directive), record `--verdict "SKIPPED"` or `--verdict "NOT PERFORMED"` truthfully with explicit rationale and Rule 1.3 process deviation note:
     ```bash
     python3 tools/jira_util.py --as coordinator record-review --reviewer "Human" --scope "Ceremony 2 Sprint Review" --verdict "PASS" --findings "<Detailed review findings and acceptance notes>"
     # When skipped:
     python3 tools/jira_util.py --as coordinator record-review --reviewer "Human" --scope "Ceremony 2 Sprint Review" --verdict "SKIPPED" --findings "<PO directive / skip rationale>"
     python3 tools/verify_requirement_governance.py --check-reviews
     ```
     This serializes traceable reviewer metadata (Human process owner or external AI vendor, date, reviewed targets, scope, and findings/verdict) to `docs/engineering/reviews/<sprint>.md`.
   * **Dual-Ticket Weekly Retrospective Roll-Up & Weekly Review Backlog Placement (`ensure-weekly-retro`, `ensure-weekly-reviews`, `ATT-8030`, `ATT-8213`)**:
     Ensure the persistent rolling **`Wochen Review & Retro`** ticket (`python3 tools/jira_util.py --as coordinator ensure-weekly-retro`) exists under Epic `ATT-232` to aggregate weekly SPC trends, `risk_register.md` updates, skill hardening, and weekly external review summaries. Run `python3 tools/jira_util.py ensure-weekly-reviews` to verify that the next subsystem Dual-Vendor Code Review ticket is created and ranked at `rank-top` if overdue.
   * **Closed-Loop Process Improvement Trigger (PA 4.2 $\rightarrow$ PA 5.2)**:
     Whenever the scorecard detects an out-of-control condition (`⚠️ PA 4.2 Special Cause Variation` where any metric falls outside $\mu \pm 2\sigma$), the Retrospective document (`docs/engineering/retros/Sprint_Review_and_Retro_<KEY>.md`) **MUST** contain an explicit Root-Cause Analysis (RCA) and concrete process/skill countermeasure prior to sprint merge.
   * **Mandatory Tier 2 Re-Verification When Any Ticket Is Dropped or Reverted During Review (`Rule 77` — `ATT-5762`)**: If any ticket commit is removed (`git reset --hard`) or reverted (`git revert`) from `sprint/<SPRINT_NAME>` during Ceremony 2 (e.g., `ATT-5665`), the pre-review Tier 2 test run is invalidated because the code state at Sprint-End has changed. You **MUST** re-execute the full Tier 2 Sprint Integration Gate (`./gradlew testDebugUnitTest --max-workers=2 && python3 tools/verify_requirement_governance.py`) and `./gradlew installDebug` on the post-drop sprint branch **before** merging into `develop`.
   * Synthesize real-time comments logged during the sprint in the `Review & Retro` ticket into actionable root cause analyses.
   * Update `docs/management/risk_register.md` (`REQ-STP-010`, Sprint Review Log + risk exposures/mitigation tickets) when new systemic risks or mitigations surfaced during the sprint.
   * Document insights in `docs/engineering/retros/Sprint_Review_and_Retro_<KEY>.md`, including the Quantitative Sprint Scorecard section (`PA 4.1 & PA 4.2`) and the Planned vs. Completed Story Points velocity metric table alongside ticket counts (`REQ-PRO-029`).
   * Update `.agents/rules/aspice_governance.md`, relevant skills, `docs/coding_standards.md`, `docs/design_guidelines.md`, and `docs/engineering/rule_history_archive.md` with permanent countermeasures.
   * Commit retrospective deliverables to `sprint/<SPRINT_NAME>`.
6. **Sprint Closure, Milestone Tagging & Merge to `develop`**:
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
