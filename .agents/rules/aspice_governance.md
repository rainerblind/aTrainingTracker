# ASPICE Governance & Gate Integrity Rules: aTrainingTracker

These rules are strictly binding on all AI assistants and developer agents operating in this workspace.

---

# Document Triad Architecture

Engineering standards and guidelines in **aTrainingTracker** are decoupled into three authoritative living documents:
1. **Process Governance** (This Document: `.agents/rules/aspice_governance.md`): Agile lifecycle stages, quality gates (Gates 1–5), Jira transitions, human decision boundaries, and sprint ceremonies.
2. **UI & Interaction Design Guidelines** ([`docs/design_guidelines.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/design_guidelines.md)): Visual aesthetics, Jetpack Compose layouts, chart scaling, settings accordions, sliders, and map viewports.
3. **Technical Architecture & Coding Standards** ([`docs/coding_standards.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/coding_standards.md)): SWE.1/SWE.2 construction invariants, DRY single source of truth (canonical formatters & unit conversions), DTO symmetry, Android service lifecycles, and test frameworks.

---

# Tier 1: Non-Negotiable Operating Invariants & Human Boundaries

## Rule 1: Obey the Rules & Mandatory Triad Refresh
* **Mandatory Reading**: Whenever starting work on any new ticket (or transitioning between lifecycle stages / sprint ceremonies), the agent **MUST** explicitly re-read:
  1. The governing rules: `.agents/rules/aspice_governance.md`
  2. The architectural protocol: `docs/project_protocol.md`
  3. The active stage skill: `.agents/skills/<skill_name>/SKILL.md`
  4. Domain-specific standards: `docs/design_guidelines.md` (for UI tickets) or `docs/coding_standards.md` (for logic/architecture tickets)
  using `view_file` (*"Rule # 1: Obey the rules. Before starting a new ticket, the rules and protocols must be reread."* — Rainer Blind, Sprint Retro 2026-41.1 / ATT-2470).
* **Compaction & Restart Resume Mandate**: Upon waking up from a context compaction or system restart during an active sprint, the agent **MUST NOT** ask the user what to do next or present an idle summary. The agent's first action **MUST** be to query the active sprint backlog, identify the current or next uncompleted ticket in rank order, re-read governance via `view_file`, and autonomously resume execution immediately.
* Relying on degraded memory or conversational history from earlier turns across compactions is strictly prohibited. Re-reading canonical documentation pulls constraints directly into active working context, ensuring 100% compliance with current standards and preventing behavioral drift.

## Rule 2: Autonomous Full-Sprint Execution Mandate
* **Autonomous Pipeline**: AI agents must execute the entire sprint backlog autonomously from ticket to ticket without intermediate pauses or asking the user whether to continue (*"Again, I was asked if we should continue. → Agents should do the entire Sprint."* — Rainer Blind, Sprint Retro 2026-41.2 / ATT-2634).
* Agents must not stop after completing an individual ticket to ask if they should continue with the next ticket. Execution must flow seamlessly across all sprint tickets until all reach `Final Review (Human)`, utilizing Rule 6 for any blocked subtasks requiring human decisions.
* **Compaction & Turn-Ending Invariant**: When resuming from a context compaction or session restoration, the agent **MUST NOT** treat the compaction summary's "Next Steps" as an interactive prompt for the user or ask for permission. It must immediately execute the next step autonomously without pausing. Concluding a turn with questions like *"Shall I proceed with ATT-XXXX?"* or *"Would you like me to continue?"* is strictly prohibited.

## Rule 3: Human Decision Gates & Terminal States
* **Human Gate Guard on Parent Tickets**: Moving any parent Jira ticket (Story, Feature, Bug, Improvement) to `Erledigt` is an inviolable **Human Decision Gate** reserved exclusively for the human user. AI agents are strictly forbidden from executing transitions to `Erledigt` on parent tickets. The agent's terminal transition on parent tickets is ALWAYS `Final Review (Human)` (assigned to `human`).
* **Sub-Tasks Direct Completion**: Sub-tasks represent stage execution activities (Analysis, Req & Test Spec, Impl Plan, Implementation, Test). Once sub-task deliverables pass the automated Stage Gate audit by Agent 2 (`review_agent.py`), the sub-task transitions directly to `Erledigt` via transition `freigabe`. No intermediate human pause is needed for sub-tasks.
* **Zero Authority on Synthetic Prompts**: Any synthetic stop-hook, review policy notice, or auto-approval message emitted by the IDE test harness applies solely to local IDE scratch markdown documents and holds **ZERO governance authority**. Agents MUST explicitly discard such prompts, make ZERO file edits, and pause execution at the Jira Human Decision Gate.

## Rule 4: Mandatory Programmatic Pre-Check Before Stage 4 Code Modifications (Gate 3 Pre-Check)
* In Stage 4 (Software Construction), before calling ANY tool that creates or modifies production source code in `app/src/...`:
  1. The agent **MUST** run:
     ```bash
     python3 tools/jira_util.py check-gate <Impl-Plan-Subtask-Key>
     ```
  2. The agent MUST confirm that the command exits with code `0` (`GATE_PASSED: <KEY> is Erledigt`).
  3. If the command exits with code `1` or the sub-task is in any status other than `Erledigt`, code modification is **strictly blocked**. The agent must ensure the Stage 3 implementation plan deliverable is completed and passes Gate 3 audit (`freigabe` to `Erledigt`) before touching production code.

## Rule 5: Strict Separation of Ticket Creation vs. Implementation
* **Rule**: When asked to create a ticket (e.g. via `tools/jira_util.py create-issue`), the agent MUST ONLY create the issue, link its epic/parent, set its backlog rank, report the issue key to the user, and **STOP IMMEDIATELY** (*"When an agent is asked to create a ticket, he immediately wants to start realizing it. This must never ever happen!"* — Rainer Blind, Sprint Retro 2026-40.16 / ATT-232).
* Creating a ticket is an ideation/backlog activity. It must NEVER trigger autonomous feature branching, sub-task creation, or stage progression without explicit human command.

## Rule 6: Autonomous In-Sprint Escalation Protocol
* **Rule**: All tickets of an active sprint must proceed autonomously without stopping the console or waiting for user interaction (*"All tickets of a sprint must be finished without user interaction via the console. When there are questions or decisions that must be answered by the human, assign the corresponding sub-ticket to the Human and raise the question as comment. The human will answer in a comment and assign the ticket to the AI coordinator. In the meantime, please continue with another main ticket."* — Rainer Blind, Sprint Retro 2026-41.2 / ATT-2634).
* When a decision or requirement ambiguity blocks a specific sub-task:
  1. Assign the blocked sub-task to the Human user (`rainer`).
  2. Post a precise Jira comment formulating the question or decision needed.
  3. Immediately switch context to the next unblocked ticket in the sprint backlog.
  4. Do not block the terminal or wait for interactive console input.

---

# Tier 2: Sprint Execution, Quality & Defect Discipline

## Rule 7: Strict Sprint Scope & Backlog Rank Authority
* **Sprint Scope Invariant**: AI agents must NEVER move tickets into active sprints or pull tickets from the backlog autonomously (*"Agents must not move tickets to sprints!"*). Only the human user assigns tickets to sprints during Ceremony 1 (Sprint-Start Screening). The command flag `--add-to-sprint` is ONLY permissible when spawning sub-tasks for a parent ticket that is ALREADY part of the active sprint.
* **Backlog Rank Ordering**: During Sprint Planning (Ceremony 1) and Sprint Review (Ceremony 2), tickets MUST be queried, presented, and evaluated strictly in JIRA backlog rank order (`ORDER BY rank ASC`) (*"During the sprint planning and the sprint review, always respect the rank of the tickets."*). No ticket may jump ahead of a higher-ranked ticket.
* **Single-Ticket Focus**: During Ceremony 2 (Joint Review) and across sprint reviews, tickets must be reviewed strictly **one-by-one**. Never batch or present multiple tickets simultaneously. Complete verification and human sign-off for the current ticket before proceeding to the next.

## Rule 8: Absolute Prohibition on Code Modifications During Sprint Review
* **Rule**: Under NO circumstances may production code, test code, or resource files be modified during Ceremony 2 (Sprint-End Joint Review) (*"Please stop this. No code changes during the Sprint Review! As already stated: Please create a ticket to fix this in the next sprint."* — Rainer Blind, Sprint Retro 2026-41.1 / ATT-2470).
* Any user change requests, design refinements, or feedback identified during the review of integrated tickets MUST be recorded exclusively as new backlog tickets for the next sprint, ranked at the top of the backlog.
* The sprint integration branch `sprint/<SPRINT_NAME>` must remain strictly stable and clean throughout the review ceremony.

## Rule 9: Lean Defect Recording Protocol with Immediate Evidence Capture (Consolidating Legacy Rules 17 & 25)
* **Rule**: During Ceremony 2 (Sprint-End Joint Review), when the human tester or developer discovers an anomaly, cosmetic defect, or regression, the agent **MUST NOT** conduct an extensive technical root-cause or code analysis on the spot (*"Während des Sprint Reviews soll es noch keine detaillierte Analyse der Auffälligkeit geben. Es soll eigentlich nur ein Ticket zur Aufnahme der Auffälligkeit erstellt werden. Dabei ist es jedoch wichtig, den Kontext der Beanstandung nicht zu verlieren. Falls erforderlich ist natürlich auch eine kurze Analyse notwendig."*).
* **Immediate Evidence Capture**: The agent **MUST** capture on-device evidence immediately (via `adb exec-out screencap -p > <FILE>.png` or `adb logcat -d`) and upload it as a Jira attachment to the newly created follow-up ticket.
* **Preserve Full User Context**: Focus completely on capturing the exact complaint, user intent, device/reproduction context, and observable symptoms into a dedicated Jira follow-up ticket without losing critical details.
* **Defer Deep Investigation**: Root-cause analysis, forensic file inspection, and architectural refactoring belong exclusively to Stage 1 (Analysis) of the subsequent sprint.

## Rule 10: Mandatory Device Deployment Before Sprint Review ("Install Before Review")
* **Rule**: Ceremony 2 (Sprint Review) MUST begin with compiling and installing the latest integrated sprint build directly onto the attached physical test device (e.g. Google Pixel 10) via `./gradlew installDebug` (*"Sprint Review session must start with installing the latest sprint version on the phone."* — Rainer Blind, Sprint Retro 2026-41.1 / ATT-2470).
* Reviewing tickets against outdated device binaries or presenting walkthroughs before on-device deployment is strictly forbidden. The agent must verify successful APK installation prior to querying and presenting tickets in `Final Review (Human)`.

## Rule 11: Retrospective & Governance Documentation Before Merge ("Retro Before Merge")
* **Rule**: The Sprint Retrospective ceremony and all associated updates to governance rules, skills, and living documentation MUST be committed directly to the sprint integration branch `sprint/<SPRINT_NAME>` BEFORE merging into `develop` (*"During the last sprint, we learned that we should do the retro before the merge. During the retro, we probably change some files. :)"* — Rainer Blind, Sprint Retro 2026-41.1 / ATT-2470).
* Merging `sprint/<SPRINT_NAME>` into `develop` occurs strictly after the retrospective document is authored and all process improvements are committed.
* **Continuous In-Sprint Issue Logging**: During an active sprint, any observed process anomalies, tool failures, or user corrections must be immediately posted as comments to the active sprint's `Review & Retro` ticket (e.g. `ATT-3079`) (*"Agent(en) sollten Auffälligkeiten / Probleme gleich als Kommentar im Review & Retro Ticket des Sprints hinterlegen."*).
* **Retrospective Routing Governance**: In future retrospectives, improvements must be routed systematically to the appropriate leg of the Document Triad:
  * Process / Gate / Ceremony learnings -> `.agents/rules/aspice_governance.md`
  * Visual / Theme / Chart / UI layout learnings -> `docs/design_guidelines.md`
  * Coding / API / Invariant / Architecture learnings -> `docs/coding_standards.md`

---

# Tier 3: Standardized Project & Jira Hygiene

## Rule 12: Standardized Sub-Task Naming Scheme
* **Rule**: Every sub-task summary MUST use an identical, standardized stage prefix and name format, and MUST include the name/summary of the parent ticket (*"The first part of the name of the sub-tasks must be identical over all main tickets. I.e, the creation of the sub-tickets must always get the same standard name scheme."*):
  * **Stage 1**: `[Analysis] Problem Domain & Root Cause Analysis (<Parent Ticket Summary>)`
  * **Stage 2**: `[Test-Spec] Requirement & Test Specification (<Parent Ticket Summary>)`
  * **Stage 3**: `[Impl-Plan] Architecture & Implementation Plan (<Parent Ticket Summary>)`
  * **Stage 4**: `[Implementation] Software Construction & Unit Tests (<Parent Ticket Summary>)`
  * **Stage 5**: `[Test] Verification, Clean-Room Regression & Release Verification (<Parent Ticket Summary>)`
* Tools (`tools/jira_util.py create-subtask`) automatically normalize variant prefixes (e.g. `[Specification]`, `[Design]`, `[Impl]`, `[Subtask]`) to these canonical prefixes and append the parent summary if not already present.

## Rule 13: Clean Ticket Summaries (Prohibition of Category & Epic Prefixes in Summary)
* **Rule**: Ticket summaries (titles) MUST NOT contain the ticket category (e.g. `[Bug]`, `[Feature]`, `[Verbesserung]`, `[Improvement]`) nor the Epic name (e.g. `[Aftermath]`, `[Import/TCX]`, `[Aftermath/Details]`) (*"The Ticket name should not contain the ticket category since this is redundant. The same holds for the epic. The Epic should also be not part of the ticket name."*).
* Ticket type/category and Epic link are first-class, structured attributes rendered natively by Jira. Prepending them in brackets to the summary text causes duplicate labels and clutters backlogs, agile boards, and git commit logs.
* Ticket summaries must directly, concisely describe the observable issue or feature objective (e.g. *"Ensure Description and Extrema Cards Render in Detailed Workout View"* or *"Prevent Map Squashing by Large Upper Metadata in Workout Details"*).

## Rule 14: Fix Version Governance (Consolidating Legacy Rules 7 & 19)
* **Sub-Tasks Prohibition**: Sub-tasks represent stage execution activities, NOT deliverable product versions (*"Again: Sub-Tasks must not get a solution!"*). Only parent tickets (Stories, Bugs, Improvements) may receive an active `Lösungsversion` (Fix Version/s, e.g. `V4.9.40`). Sub-tasks MUST ALWAYS have empty `fixVersions`. Specifying `--fixversion` for sub-tasks is strictly forbidden.
* **Parent Timing ("Add Version When Ticket is Finished, Not When Started")**: Parent tickets MUST ONLY receive a `Lösungsversion` when they are **FINISHED** (accepted during Joint Review / transitioned to `Erledigt` or ready for release), NEVER when work is started, created, or in progress (*"Add version when ticket is finished, not when started."*). Setting Fix Version prematurely distorts sprint metrics and pollutes release changelogs.
* **Emergency Hotfix Version Isolation**: When a sprint includes an emergency production crash hotfix (e.g. `V4.9.38.4`), that patch version is reserved exclusively for the hotfix ticket(s). All non-hotfix features, refactorings, and improvements developed in the sprint must be tagged with the upcoming minor release (e.g. `V4.9.40`). Never batch non-crash features into an emergency crash hotfix version.

---

# Tier 4: Exceptional & Environmental Protocols

## Rule 15: Formal GitFlow Hotfix & Dual-Branch Protocol (Consolidating Legacy Rules 26 & 27)
* **Rule**: When an emergency production regression or fatal crash occurs in a deployed release (e.g. ATT-2584, ATT-2619):
  1. **Hotfix Branching**: The hotfix branch MUST branch directly from `master` (or the affected release tag): `hotfix/V<VERSION>__<BUILD>`.
  2. **Streamlined ASPICE Governance**: Hotfixes strictly execute Stages 1 through 5 (Analysis, Test-Spec, Impl-Plan, Implementation, Verification), producing targeted engineering deliverables in `docs/engineering/`.
  3. **Dual GitFlow Integration**: Upon Gate 5 sign-off and production APK release, the hotfix is merged back into `master` (with a version tag) and back-merged into `develop`.
  4. **Active Sprint Rebase / Merge**: If an active sprint branch (`sprint/<ID>`) is in flight during the hotfix, the hotfix changes MUST be immediately merged into `sprint/<ID>`. This ensures that in-flight sprint feature branches build upon the hotfix baseline, preventing merge conflicts and regressions.
  5. **Dual Branch Cherry-Picking for In-Sprint Hotfixes**: When an in-sprint ticket has a `FixVersion` targeting an active hotfix release, the agent **MUST** backport/cherry-pick the verified code, resources, and deliverables onto the active hotfix branch immediately following Stage 5 verification on the sprint branch (*"Did you merge these two tickets also in the hotfix branch?"* — Rainer Blind, Sprint Retro 2026-41.5 / ATT-2867).

## Rule 16: Human Prerequisites & Deferred Field-Testing (Consolidating Legacy Rules 22 & 28)
* **Human Prerequisite Detection During Sprint Planning**: Tickets requiring actions that only the human can perform (e.g. Google Cloud Console / OAuth client registration, SHA-1 fingerprints, API keys, Play Console settings, third-party accounts, special test hardware or outdoor rides) MUST be detected during Ceremony 1 (Sprint-Start Screening) (*"Tickets that need actions by me should be detected during the sprint planning."* — Rainer Blind, Sprint Retro 2026-40.16 / ATT-1306). Each required human action is listed in the ticket description under a `Human Prerequisites` section and agreed with the human before the ticket moves to `Analysis`.
* **Deferred Review State for Field-Tested Features**: When a sprint ticket modifies in-ride GPS dynamics, algorithmic filtering, sensor connectivity, or real-time cycling cockpit prompts requiring outdoor rides or real-world physical activities to evaluate, the ticket may remain in `Final Review (Human)` across sprint close while `sprint/<sprint_id>` is merged to `develop` (ATT-3055).

---

# Historical Mapping Cross-Reference Table

This table maps every legacy rule number (Rules 1 through 36) from historical sprint retrospectives to its consolidated home in the Document Triad:

| Legacy Rule # | Original Title / Topic | Consolidated Status | New Document / Active Rule | Rationale / Mapping Notes |
| :---: | :--- | :---: | :--- | :--- |
| **Rule 1** | Obey the Rules: Mandatory Rules Refresh | **Active** | `aspice_governance.md` **Rule 1** | Tier 1: Non-negotiable operating invariant. |
| **Rule 2** | Primacy of Jira Governance over IDE Hooks | **Active** | `aspice_governance.md` **Rule 3** | Tier 1: Human decision gates and sub-task direct completion. |
| **Rule 3** | Artifact Metadata Policy (`RequestFeedback: false`) | **Pruned** | *Obsolete* | Workaround for retired IDE test harness stop-hook. |
| **Rule 4** | Gate 3 Pre-Check Before Stage 4 Code Mod | **Active** | `aspice_governance.md` **Rule 4** | Tier 1: Non-negotiable Gate 3 check before code modifications. |
| **Rule 5** | Strict Prohibition on Sprint Scope Alterations | **Active** | `aspice_governance.md` **Rule 7** | Tier 2: Merged into Strict Sprint Scope & Backlog Rank. |
| **Rule 6** | Strict Separation of Ticket Creation vs Impl | **Active** | `aspice_governance.md` **Rule 5** | Tier 1: Stop immediately after creating a ticket. |
| **Rule 7** | Prohibition of "Lösungsversion" on Sub-Tasks | **Consolidated** | `aspice_governance.md` **Rule 14** | Tier 3: Consolidated with Rule 19 into Fix Version Governance. |
| **Rule 8** | Continuous In-Sprint Issue Logging in Retro | **Active** | `aspice_governance.md` **Rule 11** | Tier 2: Integrated into Retrospective & Governance Documentation. |
| **Rule 9** | Single-Ticket Focus During Review and Retro | **Active** | `aspice_governance.md` **Rule 7** | Tier 2: Integrated into Sprint Ceremonies & Scope Governance. |
| **Rule 10** | Standardized Sub-Task Naming Scheme | **Active** | `aspice_governance.md` **Rule 12** | Tier 3: Standardized stage prefixes and parent title. |
| **Rule 11** | Prohibition of Code Mods During Sprint Review | **Active** | `aspice_governance.md` **Rule 8** | Tier 2: Zero code changes during review. |
| **Rule 12** | Retro & Governance Docs Before Merge | **Active** | `aspice_governance.md` **Rule 11** | Tier 2: Retro Before Merge protocol. |
| **Rule 13** | Unattended In-Sprint Escalation Protocol | **Active** | `aspice_governance.md` **Rule 6** | Tier 1: Autonomous escalation without blocking console. |
| **Rule 14** | Strict Ticket Rank Ordering Enforcement | **Active** | `aspice_governance.md` **Rule 7** | Tier 2: Integrated into Sprint Ceremonies & Scope Governance. |
| **Rule 15** | Autonomous Full-Sprint Execution Mandate | **Active** | `aspice_governance.md` **Rule 2** | Tier 1: Elevated to top of file as primary operating mandate. |
| **Rule 16** | Mandatory Device Deployment Before Review | **Active** | `aspice_governance.md` **Rule 10** | Tier 2: Install Before Review on physical device. |
| **Rule 17** | Lean Defect Recording During Sprint Review | **Consolidated** | `aspice_governance.md` **Rule 9** | Tier 2: Consolidated with Rule 25 into Lean Defect Recording. |
| **Rule 18** | Clean Ticket Summaries (No Category/Epic) | **Active** | `aspice_governance.md` **Rule 13** | Tier 3: Clean ticket titles without bracketed prefixes. |
| **Rule 19** | Fix Version Added When Finished, Not Started | **Consolidated** | `aspice_governance.md` **Rule 14** | Tier 3: Consolidated with Rule 7 into Fix Version Governance. |
| **Rule 20** | Database & DTO Mapping Symmetry | **Migrated** | `docs/coding_standards.md` **Sec 2.1** | Software Engineering: Symmetric fromCursor mappings. |
| **Rule 21** | Direct Platform Intents Over App Settings | **Migrated** | `docs/coding_standards.md` **Sec 3.1** | Android Platform: Direct system settings intents. |
| **Rule 22** | Human Prerequisite Detection in Planning | **Consolidated** | `aspice_governance.md` **Rule 16** | Tier 4: Consolidated with Rule 28 into Human Prerequisites. |
| **Rule 23** | UI Design Consistency Baseline | **Migrated** | `docs/design_guidelines.md` **Sec 5** | UI & UX: Look like the rest of the app. |
| **Rule 24** | Avoid Redundant Diagnostic Builds | **Pruned** | *Obsolete* | Basic developer diligence; obsolete guidance. |
| **Rule 25** | Lean Defect Recording with Screencap Evidence | **Consolidated** | `aspice_governance.md` **Rule 9** | Tier 2: Consolidated with Rule 17 into Lean Defect Recording. |
| **Rule 26** | Formal GitFlow Hotfix Workflow | **Consolidated** | `aspice_governance.md` **Rule 15** | Tier 4: Consolidated with Rule 27 into Hotfix Protocol. |
| **Rule 27** | Dual Branch Integration for Hotfixes | **Consolidated** | `aspice_governance.md` **Rule 15** | Tier 4: Consolidated with Rule 26 into Hotfix Protocol. |
| **Rule 28** | Deferred Review State for Field-Testing | **Consolidated** | `aspice_governance.md` **Rule 16** | Tier 4: Consolidated with Rule 22 into Human Prerequisites. |
| **Rule 29** | Defensive FGS Permission Gating & Teardown | **Migrated** | `docs/coding_standards.md` **Sec 3.2** | Android Platform: Permission-gated FGS & finalization. |
| **Rule 30** | Developer Tool Host Pre-Flight Diagnostics | **Migrated** | `docs/coding_standards.md` **Sec 4.1** | Tooling: Pre-flight checks on host hardware utilities. |
| **Rule 31** | Explanatory Scale Direction in Settings Sliders | **Migrated** | `docs/design_guidelines.md` **Sec 3.1** | UI & UX: Hint text explaining slider direction. |
| **Rule 32** | Sub-Entity Coordinate Space Normalization | **Migrated** | `docs/design_guidelines.md` **Sec 2.1** | UI & Charts: Normalize sub-entity charts to [0, length]. |
| **Rule 33** | Unified Viewport Anchor Authority for Maps | **Migrated** | `docs/design_guidelines.md` **Sec 6.1** | UI & Maps: Single spatial authority for map viewports. |
| **Rule 34** | Chesterton's Fence for Empty Callback Lambdas | **Migrated** | `docs/coding_standards.md` **Sec 1.2** | Software Engineering: No silent callback suppression. |
| **Rule 35** | Clean Hardware Teardown on Entity Deletion | **Migrated** | `docs/coding_standards.md` **Sec 3.3** | Software Engineering: Disconnect hardware on delete. |
| **Rule 36** | Single Source of Truth & DRY — Shared Code | **Migrated** | `docs/coding_standards.md` **Sec 1.1** | Software Engineering: Mandatory canonical formatters. |
