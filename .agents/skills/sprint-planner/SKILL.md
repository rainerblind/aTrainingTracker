---
name: sprint-planner
description: Facilitates the agile Sprint lifecycle ceremonies. Conducts interactive ticket-by-ticket Sprint-Start Screening with the human user (clarifying ambiguous requirements and moving ready tickets to Analysis) and facilitates the Sprint-End Joint Review (evaluating tickets in Final Review, merging accepted tickets, or bouncing back to Analysis).
---

# Skill: sprint-planner

## Overview
This skill acts as the **Agile Sprint Facilitator & Scrum Master**. It orchestrates the two human-in-the-loop anchor points of every sprint and governs the **Strategy A (Sprint Integration Branch)** continuous integration model:
1. **Phase 1: Sprint-Start & Interaktives Ticket-Screening** (Establishing `sprint/<sprint_id>` and aligning requirements).
2. **Phase 2: Autonome In-Sprint Pipeline mit Continuous Sprint Integration** (Agents work autonomously, merging verified tickets directly into `sprint/<sprint_id>` to eliminate merge conflicts).
3. **Phase 3: Sprint-Ende & Joint Review** (Joint evaluation on the integrated sprint build, release sign-off, and final merge to `develop`).

---

## Strategy A Branching Model: Sprint Integration Branch (`sprint/<sprint_id>`)

To eliminate merge conflicts across shared living documents (`docs/requirements.md`, `docs/tests.md`, Navigation, ViewModels) and allow subsequent sprint tickets to build immediately upon prior tickets:
* **Sprint Branch**: Created at Sprint-Start from `develop`: `sprint/<SPRINT_NAME>` (e.g. `sprint/2026-40.1`).
* **Feature Branches**: Each sprint ticket branches from `sprint/<SPRINT_NAME>`: `feature/<KEY>`.
* **In-Sprint Merge**: As soon as a ticket completes Stage 5 (Clean-room tests pass & Gate 5 audit passes), the agent merges `feature/<KEY>` into `sprint/<SPRINT_NAME>` (`--no-ff`), deletes `feature/<KEY>`, and transitions the parent ticket to `Final Review (Human)`.
* **Sprint-End Integration**: At Sprint-End, the human user validates the entire sprint build. Upon final sign-off, `sprint/<SPRINT_NAME>` is merged into `develop` (`--no-ff`) and deleted.

---

## Ceremony 1: Sprint-Start & Interaktives Ticket-Screening

### Objective
Ensure that every ticket committed to the active sprint has clear, unambiguous acceptance criteria, an understood problem statement, and no unresolved architectural questions before development begins, while establishing the clean sprint integration branch.

### Step-by-Step Procedure
1. **Query Active Sprint Backlog**:
   Find all tickets in the active sprint currently in status `Zu erledigen` using the exact active sprint ID or name (do not use generic `openSprints()` which can match multiple concurrently active sprints):
   ```bash
   python3 tools/jira_util.py search "project = ATT AND sprint = <SPRINT_ID> AND status = 'Zu erledigen' ORDER BY rank ASC"
   ```
   *Strict Invariant*: AI agents MUST NEVER move tickets into active sprints or pull backlog items autonomously (`"Agents must not move tickets to sprints!"`). Only the human user assigns tickets to sprints.
2. **Establish Sprint Integration Branch**:
   Create and push the sprint integration branch from latest `develop`:
   ```bash
   git checkout develop
   git pull origin develop
   git checkout -b sprint/<SPRINT_NAME>  # e.g. sprint/2026-40.1
   ```
3. **Ticket-by-Ticket Walkthrough with the Human User**:
   For each ticket in sequence:
   * **Present Summary & Context**: Present the ticket key, title, and current description to the user.
   * **Human Prerequisite Detection (Rule 22)**: Explicitly check whether the ticket depends on actions only the human can perform (e.g. Google Cloud Console / OAuth client registration, SHA-1 fingerprints, API keys, Play Console settings, third-party accounts, test hardware/sensors, outdoor rides). If so, name each action to the user, record them in the ticket description under a `Human Prerequisites` section, and agree whether they are completed before development starts (`"Tickets that need actions by me should be detected during the sprint planning."` — Retro 2026-40.16, ATT-1306).
   * **Screening Evaluation**:
     * *Case A: Clean & Unambiguous*: If the ticket description clearly articulates the goal, scope, and expected outcome, confirm with the user and advance the ticket to `Analysis`:
       ```bash
       python3 tools/jira_util.py move <KEY> "analysis"
       ```
     * *Case B: Ambiguous / Incomplete / Questions Open*:
       * Ask targeted, Socratic questions to resolve open design decisions, edge cases, and user expectations.
       * Jointly formulate clean requirements, acceptance criteria, and scope boundaries.
       * Update the Jira ticket description:
         ```bash
         python3 tools/jira_util.py update-desc <KEY> "<UPDATED_SPECIFICATION>"
         ```
       * Once aligned, advance the ticket to `Analysis`:
         ```bash
         python3 tools/jira_util.py move <KEY> "analysis"
         ```
4. **Handoff to Autonomous Execution (Phase 2)**:
   Once all sprint tickets are in status `Analysis`, Phase 1 concludes. The implementation agents (`agent1`, `agent2`) take over Phase 2:
   * **Continuous Unattended Execution & Full-Sprint Mandate**: The sprint pipeline runs continuously and autonomously across the entire sprint backlog without stopping the console, pausing between tickets, or asking whether to continue (`"Again, I was asked if we should continue. → Agents should do the entire Sprint."`).
   * **In-Sprint Human Escalation Protocol**: If an agent encounters a blocker, design decision, or requirement ambiguity that strictly requires human guidance, the agent MUST NOT halt or prompt the console. Instead:
     1. Post a clarifying question/decision comment on the subtask: `python3 tools/jira_util.py comment <SUBTASK_KEY> "Question for Human: ..."`
     2. Reassign the subtask to Human: `python3 tools/jira_util.py assign <SUBTASK_KEY> human`
     3. Immediately switch context to the next available sprint ticket and continue execution.
     4. When the human answers in a comment and reassigns to coordinator, the coordinator reassigns to `agent1` to resume the ticket.
   * Each ticket branches from the active `sprint/<SPRINT_NAME>` branch.
   * **Mandatory Rules & Skills Refresh ("Obey the Rules")**: At the start of handling each ticket, the agent MUST explicitly re-read `.agents/rules/aspice_governance.md` and the skill for the active stage using `view_file` to reload constraints into active working memory and prevent context drift across compactions.
   * Upon completing Stage 5 verification, the ticket is merged back into `sprint/<SPRINT_NAME>` immediately via `--no-ff`.
   * *In-Sprint Anomaly Logging*: Any process hiccups, tool issues, or user corrections during execution are immediately logged as comments in the sprint's `Review & Retro` ticket.

---

## Ceremony 2: Sprint-Ende & Joint Review

### Objective
Jointly review all completed sprint tickets with the human user against expectations on the integrated sprint build, authorize release, handle rejected items, and cleanly close the sprint into `develop`.

### Step-by-Step Procedure
1. **Mandatory Device Build & Deployment ('Install Before Review')**:
   Before commencing ticket inspection with the human user, the agent MUST compile and deploy the latest integrated sprint APK from `sprint/<SPRINT_NAME>` to the attached physical device (e.g. Google Pixel 10) (`"Sprint Review session must start with installing the latest sprint version on the phone."`):
   ```bash
   ./gradlew installDebug
   ```
   Confirm successful installation before proceeding to ticket walkthroughs.

2. **Query Tickets Ready for Review in Rank Order**:
   Find all sprint tickets in status `Final Review (Human)` sorted strictly by backlog rank for the exact active sprint ID or name:
   ```bash
   python3 tools/jira_util.py search "project = ATT AND sprint = <SPRINT_ID> AND status = 'Final Review (Human)' ORDER BY rank ASC"
   ```

3. **Collaborative Ticket Inspection**:
   Ensure git is checked out on `sprint/<SPRINT_NAME>` (which contains all integrated sprint changes).
   * **Rule (Strict Rank Order)**: Review tickets strictly in backlog rank order (`ORDER BY rank ASC`) (`"During the sprint planning and the sprint review, always respect the rank of the tickets."`). No ticket may jump ahead of a higher-ranked ticket.
   * **Rule (Single-Ticket Focus)**: Evaluate tickets **strictly one-by-one** (`"Please make one ticket after the other. Please also keep this in mind for the retro."`). Never batch or present multiple tickets simultaneously. Complete verification and human acceptance for the current ticket before proceeding to the next.
   For each ticket in `Final Review (Human)`:
   * Present the walkthrough deliverable (`docs/engineering/walkthroughs/<KEY>_walkthrough.md`) and summary of changes.
   * User verifies on-device behavior (Pixel 10 APK built from `sprint/<SPRINT_NAME>`) and inspects code diffs.
   * **Evaluation Decision**:
     * **In Ordnung (i.O. / Accepted)**:
       1. Human user transitions ticket from `Final Review (Human)` to `Erledigt` in Jira.
     * **User Feedback / Refinements ("No Code Changes During Review & Lean Defect Recording")**:
       * **Mandate**: Under NO circumstances may code be modified during Ceremony 2 (`"No code changes during the Sprint Review!"`).
       * **Lean Defect Recording**: Do NOT conduct a deep technical root-cause or code analysis during review (`"Während des Sprint Reviews soll es noch keine detaillierte Analyse der Auffälligkeit geben. Es soll eigentlich nur ein Ticket zur Aufnahme der Auffälligkeit erstellt werden. Dabei ist es jedoch wichtig, den Kontext der Beanstandung nicht zu verlieren. Falls erforderlich ist natürlich auch eine kurze Analyse notwendig."`). Only perform a brief sanity check if strictly required to frame the issue. Focus on capturing the exact human observation, steps to reproduce, user intent, and context without loss into a dedicated Jira follow-up ticket via `tools/jira_util.py create-issue`.
       * Rank the new ticket at the top of the backlog via Jira Agile API.
       * Transition the current sprint ticket to `Erledigt`.
       * Proceed strictly to the next ticket.
     * **Nicht in Ordnung (n.i.O. / Rejection of Core Scope)**:
       * Move ticket back to `Analysis` with explicit human feedback:
         ```bash
         python3 tools/jira_util.py comment <KEY> "Revision needed: [Human feedback]"
         python3 tools/jira_util.py move <KEY> "analysis"
         ```
4. **Sprint Retrospective & Process Hardening ("Retro Before Merge")**:
   * **Mandate**: Conduct the Retrospective and update governance documents on `sprint/<SPRINT_NAME>` **BEFORE** merging into `develop` (`"During the last sprint, we learned that we should do the retro before the merge. During the retro, we probably change some files. :)"`).
   * Synthesize real-time comments logged during the sprint in the `Review & Retro` ticket into actionable root cause analyses.
   * Document insights in `docs/engineering/Sprint_Review_and_Retro_<KEY>.md`.
   * Update `.agents/rules/aspice_governance.md`, relevant skills, and `docs/project_protocol.md` with permanent countermeasures.
   * Commit retrospective deliverables to `sprint/<SPRINT_NAME>`.
5. **Sprint Closure & Merge to `develop`**:
   * Once all tickets and the Retrospective are signed off:
   ```bash
   git checkout develop
   git pull origin develop
   git merge --no-ff sprint/<SPRINT_NAME> -m "Merge branch 'sprint/<SPRINT_NAME>' into develop"
   git push origin develop
   git branch -d sprint/<SPRINT_NAME>
   # Post-condition verification: ensure no merged sprint branches remain locally
   git branch --list 'sprint/*'
   ```
   * Transition the sprint's `Review & Retro` ticket to `Erledigt` with the human user.
   * Post final integration notice to Jira.
