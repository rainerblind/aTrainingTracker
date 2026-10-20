---
name: jira-workflow
description: Guides role-safe Jira operations, composite stage transitions (submit-stage and gate-verdict), subtask lifecycle management, and human gate governance using tools/jira_util.py.
---

# Skill: jira-workflow

## Overview
This skill provides standardized guidance for interacting with Jira in accordance with the project's Scrum/ASPICE workflow rules and multi-account CLI tooling (`tools/jira_util.py`).

## Roles & Conventions
* **`agent1` (Implementer)**: Default role for authoring deliverables, submitting stages (`submit-stage`), and moving tickets between work states.
* **`agent2` (Auditor)**: Independent role for executing automated ASPICE Gate 1–5 reviews via read-only `research` subagents (`invoke_subagent`) and recording verdicts via `gate-verdict`. Hardwired to prevent impersonation.
* **`coordinator` (Orchestrator)**: Backlog grooming, subtask creation, sprint queries. Cannot close tickets or override auditor verdicts.
* **`human` (User / Gatekeeper)**: Holds exclusive authority for approving release gates and transitioning parent tickets to `Erledigt`.

## Composite Stage-Transition Commands (Preferred — 1 CLI Call per Stage/Gate)
```bash
# 1. Submit a stage deliverable as agent1 (1|analysis, 2|test-spec, 3|impl-plan, 4|impl, 5|test):
#    Atomically ensures parent stage, creates or updates the canonical stage subtask, and moves it to In Überprüfung:
python3 tools/jira_util.py --as agent1 submit-stage <PARENT_KEY> <STAGE> <TEXT_OR_@FILE>

# 2. Record an independent Gate 1-5 audit verdict as agent2 (PASS or REVISION):
#    Atomically assigns subtask to agent2, posts role-attributed audit comment, and either:
#    - PASS: moves subtask via 'freigabe' to Erledigt, advances parent ticket to next stage, and injects next stage SKILL.md!
#    - REVISION: moves subtask back to In Bearbeitung and reassigns to agent1.
python3 tools/jira_util.py --as agent2 gate-verdict <SUBTASK_KEY> PASS "<AUDIT_REPORT_OR_@FILE>"
python3 tools/jira_util.py --as agent2 gate-verdict <SUBTASK_KEY> REVISION "<AUDIT_REPORT_OR_@FILE>"
```

## Other Common CLI Commands
```bash
# Show parent issue details, comments, attachments, and subtasks
python3 tools/jira_util.py show <KEY>

# Check issue status
python3 tools/jira_util.py status <KEY>

# Search issues with JQL (always include ORDER BY rank ASC for sprint/backlog queries)
python3 tools/jira_util.py search "project = ATT AND sprint = <SPRINT_ID> ORDER BY rank ASC"

# Ensure the active sprint has a 'Review & Retro' ticket (creates one if Jira automation did not fire)
python3 tools/jira_util.py ensure-retro

# Create a parent / follow-up issue (automatically ranked at the top of the backlog!)
# TYPE can be "Bug" (10006), "Feature" (10007), or "Verbesserung" / "Improvement" (10008)
python3 tools/jira_util.py create-issue "<SUMMARY>" "<DESC_OR_@FILE>" [Feature|Bug|Verbesserung] [EPIC_KEY]

# Upload / attach a screenshot or log file to an issue
python3 tools/jira_util.py upload <KEY> <FILE_PATH>

# Rank an existing issue at the very top of the backlog
python3 tools/jira_util.py rank-top <KEY>

# Set or clear Fix Version (Lösungsversion) on a finished parent ticket (NEVER on subtasks or unfinished tickets)
python3 tools/jira_util.py set-fixversion <PARENT_KEY> <VERSION>
python3 tools/jira_util.py clear-fixversion <KEY>

# Post a role-attributed comment or update description
python3 tools/jira_util.py comment <KEY> "<COMMENT_TEXT_OR_@FILE>"
python3 tools/jira_util.py update-desc <KEY> "<CONTENT_OR_@FILE>"

# Transition parent ticket states (NEVER move parent tickets to Erledigt!)
# Note: Moving a parent ticket into a lifecycle stage automatically injects that stage's SKILL.md into stdout!
python3 tools/jira_util.py move <PARENT_KEY> "analysis"
python3 tools/jira_util.py move <PARENT_KEY> "final review" # Assigns to human for final review

# Check Gate 3 prerequisite before modifying production code
python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>
```

## Critical Governance Invariants
1. **Subtask Direct Completion & Automatic Context Injection**: Subtasks in `In Überprüfung` that pass Gate audit transition directly to `Erledigt` via `gate-verdict <SUBTASK_KEY> PASS` (or `move <SUBTASK_KEY> freigabe`), which synchronously advances the parent ticket one column forward and prints the next stage's `SKILL.md` to `stdout`.
2. **Parent Final Review Gate & `Erledigt` Prohibition**: Parent tickets mandate a `Final Review (Human)` state after Stage 5 (`Test`). AI agents are strictly forbidden from transitioning parent tickets to `Erledigt`.
3. **Prohibition on Agent Sprint Manipulation (Except `ensure-retro`)**: Agents must NEVER move backlog feature/bug tickets into active sprints autonomously.
4. **Separation of Ticket Creation vs. Implementation**: Creating a ticket (`create-issue`) is strictly an ideation/logging step. Report the new issue key and STOP immediately.
5. **Fix Version Hygiene**: Sub-tasks must NEVER receive a `Lösungsversion` (`fixVersions`). Parent tickets receive `Lösungsversion` only upon completion/acceptance during Sprint Review or release.
6. **Unattended Autonomous In-Sprint Escalation**: When a decision requires human input mid-sprint, assign the blocked sub-task to `human`, post the question as a comment, and immediately continue with the next sprint ticket.
