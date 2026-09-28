---
name: jira-workflow
description: Guides role-safe Jira operations, stage transitions, subtask lifecycle management, and human gate governance using tools/jira_util.py.
---

# Skill: jira-workflow

## Overview
This skill provides standardized guidance for interacting with Jira in accordance with the project's Scrum/ASPICE workflow rules and multi-account CLI tooling (`tools/jira_util.py`).

## Roles & Conventions
* **`agent1` (Implementer)**: Default role for creating subtasks, authoring deliverables, updating descriptions, and moving tickets between work states.
* **`agent2` (Auditor)**: Independent role for executing automated ASPICE gate reviews (`tools/review_agent.py`). Hardwired to prevent impersonation.
* **`coordinator` (Orchestrator)**: Backlog grooming, subtask discovery, sprint queries. Cannot close tickets or override auditor verdicts.
* **`human` (User / Gatekeeper)**: Holds exclusive authority for approving release gates and transitioning parent tickets to `Erledigt`.

## Common CLI Commands
```bash
# Show parent issue details and subtasks
python3 tools/jira_util.py show <KEY>

# Check issue status
python3 tools/jira_util.py status <KEY>

# Create a subtask linked to parent and added to active sprint
python3 tools/jira_util.py create-subtask <PARENT_KEY> "<SUMMARY>" "<DESC>" --add-to-sprint

# Update subtask description with deliverable content
python3 tools/jira_util.py update-desc <KEY> "<CONTENT_OR_@FILE>"

# Post a role-attributed comment
python3 tools/jira_util.py comment <KEY> "<COMMENT_TEXT>"

# Transition subtask states
python3 tools/jira_util.py move <SUBTASK_KEY> in_progress
python3 tools/jira_util.py move <SUBTASK_KEY> in_review
python3 tools/jira_util.py move <SUBTASK_KEY> freigabe     # Moves subtask directly to Erledigt!

# Transition parent ticket states
python3 tools/jira_util.py move <PARENT_KEY> "test"
python3 tools/jira_util.py move <PARENT_KEY> "final review" # Assigns to human for final review

# Check Gate 3 prerequisite before modifying code
python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>
```

## Critical Governance Invariants
1. **Subtask Direct Completion**: Subtasks in `In Überprüfung` that have passed automated Gate audit transition directly to `Erledigt` via transition `freigabe`. No intermediate human review pause is required for subtasks.
2. **Parent Final Review Gate**: Parent tickets mandate a `Final Review (Human)` state after Stage 5 (`Test`). AI agents transition the parent ticket to `Final Review (Human)` and assign it to the user.
3. **Erledigt Prohibition for Parent Tickets**: AI agents are strictly forbidden from transitioning parent tickets to `Erledigt`. This action is reserved exclusively for the human user.
4. **Prohibition on Agent Sprint Manipulation**: Agents must NEVER move tickets into active sprints or pull tickets from the backlog autonomously (`"Agents must not move tickets to sprints!"`). Only the human user assigns tickets to sprints during Sprint-Start Screening. `--add-to-sprint` is only valid for subtasks of a parent ticket already in the active sprint.
5. **Separation of Ticket Creation vs. Implementation**: Creating a ticket (`create-issue`) is strictly an ideation/logging step (`"When an agent is asked to create a ticket, he immediately wants to start realizing it. This must never ever happen!"`). The agent must report the new issue key and STOP immediately. Never branch, create subtasks, or start stages without explicit human instruction.
6. **No "Lösungsversion" (Fix Version) on Sub-Tasks**: Sub-tasks represent stage execution activities, not deliverable product solutions (`"Again: Sub-Tasks must not get a solution!"`). Sub-tasks must NEVER have a `fixVersions` value assigned. Only parent user-facing tickets have a `Lösungsversion`.
7. **Continuous In-Sprint Retro Logging**: During the sprint, any observed process anomalies, tool failures, or user corrections must be immediately posted as comments on the active sprint's `Review & Retro` ticket (`"Agent(en) sollten Auffälligkeiten / Probleme gleich als Kommentar im Review & Retro Ticket des Sprints hinterlegen."`).
