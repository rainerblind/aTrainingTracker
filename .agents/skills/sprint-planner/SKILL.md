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
   Find all tickets in the active sprint currently in status `Zu erledigen`:
   ```bash
   python3 tools/jira_util.py search "project = ATT AND sprint in openSprints() AND status = 'Zu erledigen' ORDER BY rank ASC"
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
4. **Handoff to Autonomous Execution**:
   Once all sprint tickets are in status `Analysis`, Phase 1 concludes. The implementation agents (`agent1`, `agent2`) take over Phase 2:
   * Each ticket branches from the active `sprint/<SPRINT_NAME>` branch.
   * Upon completing Stage 5 verification, the ticket is merged back into `sprint/<SPRINT_NAME>` immediately.
   * *In-Sprint Anomaly Logging*: Any process hiccups, tool issues, or user corrections during execution are immediately logged as comments in the sprint's `Review & Retro` ticket.

---

## Ceremony 2: Sprint-Ende & Joint Review

### Objective
Jointly review all completed sprint tickets with the human user against expectations on the integrated sprint build, authorize release, handle rejected items, and cleanly close the sprint into `develop`.

### Step-by-Step Procedure
1. **Query Tickets Ready for Review**:
   Find all sprint tickets in status `Final Review (Human)`:
   ```bash
   python3 tools/jira_util.py search "project = ATT AND sprint in openSprints() AND status = 'Final Review (Human)'"
   ```
2. **Collaborative Ticket Inspection**:
   Ensure git is checked out on `sprint/<SPRINT_NAME>` (which contains all integrated sprint changes).
   **Rule (Single-Ticket Focus)**: Evaluate tickets **strictly one-by-one** (`"Please make one ticket after the other. Please also keep this in mind for the retro."`). Never batch or present multiple tickets simultaneously. Complete verification and human acceptance for the current ticket before proceeding to the next.
   For each ticket in `Final Review (Human)`:
   * Present the walkthrough deliverable (`docs/engineering/walkthroughs/<KEY>_walkthrough.md`) and summary of changes.
   * User verifies on-device behavior (Pixel 10 APK built from `sprint/<SPRINT_NAME>`) and inspects code diffs.
   * **Evaluation Decision**:
     * **In Ordnung (i.O. / Accepted)**:
       1. Human user transitions ticket from `Final Review (Human)` to `Erledigt` in Jira.
     * **Nicht in Ordnung (n.i.O. / Rejected)**:
       * *Minor defect or scope gap*: Revert the ticket commit on `sprint/<SPRINT_NAME>` (or apply immediate fix), and move ticket back to `Analysis` with explicit human feedback:
         ```bash
         python3 tools/jira_util.py comment <KEY> "Revision needed: [Human feedback]"
         python3 tools/jira_util.py move <KEY> "analysis"
         ```
       * *Separate follow-up issue*: Keep parent ticket approved and file a dedicated Bug ticket for the next sprint backlog via `tools/jira_util.py create-issue`. (Remember: creating the ticket stops immediately and does not trigger realization).
3. **Sprint Closure & Merge to `develop`**:
   Once all accepted sprint tickets are signed off:
   ```bash
   git checkout develop
   git pull origin develop
   git merge --no-ff sprint/<SPRINT_NAME> -m "Merge branch 'sprint/<SPRINT_NAME>' into develop"
   git branch -d sprint/<SPRINT_NAME>
   ```
   Post final integration notice to Jira.
4. **Sprint Retrospective**:
   * Review sprint metrics, velocity, and process findings in the sprint's `Review & Retro` ticket (e.g. `ATT-1511`).
   * Synthesize real-time comments logged during the sprint into actionable root cause analyses.
   * Document insights in `docs/engineering/Sprint_Review_and_Retro_<KEY>.md`.
   * Update `.agents/rules/aspice_governance.md`, relevant skills, and `docs/project_protocol.md` with permanent countermeasures.
