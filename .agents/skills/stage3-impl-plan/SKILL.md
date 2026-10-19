---
name: stage3-impl-plan
description: Executes Stage 3 (Implementation Plan) of the agile ASPICE lifecycle. Deconstructs requirements into bite-sized atomic steps, designs SWE.2 architecture, enforces invariants, and prepares Gate 3 review.
---

# Skill: stage3-impl-plan

## Overview
This skill guides the agent through **Stage 3 (Implementation Planning)**. It produces a detailed, step-by-step engineering plan defining the exact components, classes, SQLite migrations, and test mappings before writing production code.

## Subagent Model Pairing ("Zipper Principle" — Rule 46)
- **Doing (`agent1`)**: **`pro`** (`invoke_subagent` with `Model: "pro"`) — SWE.2 architectural design (layer boundaries, thread safety, SQLite `onCreate`/`onUpgrade` parity, decomposition into atomic steps) is authored by the senior reasoning model.
- **Gate 3 Review (`agent2`)**: **`flash`** (`tools/review_agent.py audit <KEY> --model flash` or independent `research` subagent with `Model: "flash"`) — Audits structural completeness (1:1 mapping of `REQ`/`TST` to atomic steps, DB migration checklist, and strict *Code Lock* verification).

## Key Responsibilities
1. **Architectural Decomposition (SWE.2)**:
   - Identify clean architecture boundaries: UI (Jetpack Compose), ViewModel (StateFlow), Repository, Database (SQLite / Room / ContentProvider), Service (`TrackerService`).
   - Define thread dispatchers (`dbDispatcher`, `Dispatchers.Main`, `Dispatchers.IO`).
2. **Atomic Step Sequencing**:
   - Break down construction into bite-sized, order-dependent steps.
   - Map each step to specific target files and targeted unit test commands.
3. **Invariant Protection & Rollback Safety**:
   - Explicitly list non-negotiable invariants (backward compatibility, null safety, human decision gate).
   - **UI Consistency (Rule 23)**: If the ticket adds or changes UI, read `docs/design_guidelines.md` (Section 5) and fill the plan's `UI Consistency` section: closest existing reference screen, reused components (`ui/components/`), theme tokens (shapes, spacing, `MaterialTheme.colorScheme`, `TTColor`), and a justification for any new one-off style.
4. **Deliverable Production, Gate Audit & Synchronous Parent Stage Progression (Rule 3 / Rule 45)**:
   - Author `docs/engineering/plans/<TICKET_KEY>_plan.md` using `templates/plan_template.md`.
   - Update Stage 3 Jira subtask (`[Impl-Plan]`) Description with the complete text.
   - Transition subtask to `In Überprüfung` and run Gate 3 audit (`tools/review_agent.py audit <KEY>` — defaults to `flash`).
   - Upon Gate 3 approval (`RECOMMEND PASS`), transition subtask directly to `Erledigt` via transition `freigabe` (`python3 tools/jira_util.py move <SUBTASK_KEY> freigabe`), which automatically advances the parent ticket from `Implementation Plan` to `Implementation`.
   - **One Commit per Ticket (Rule 44)**: Do not create a separate intermediate git commit for Stage 3; keep changes on the working branch until the single atomic ticket commit at the end of Stage 5 (or squash when merging into `sprint/<SPRINT_NAME>`).

## Deliverable Template
Use the standardized markdown template located at:
`templates/plan_template.md`
