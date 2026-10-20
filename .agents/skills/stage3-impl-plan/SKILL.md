---
name: stage3-impl-plan
description: Executes Stage 3 (Implementation Plan) of the agile ASPICE lifecycle. Deconstructs requirements into bite-sized atomic steps, designs SWE.2 architecture, enforces invariants, and prepares Gate 3 review.
---

# Skill: stage3-impl-plan

## Overview
This skill guides the agent through **Stage 3 (Implementation Planning)**. It produces a detailed, step-by-step engineering plan defining the exact components, classes, SQLite migrations, and test mappings before writing production code.

## Subagent Model Pairing ("Zipper Principle" — Core Rule 7)
- **Doing (`agent1`)**: **`pro`** (`invoke_subagent` with `Model: "pro"`) — SWE.2 architectural design (layer boundaries, thread safety, SQLite `onCreate`/`onUpgrade` parity, decomposition into atomic steps) is authored by the senior reasoning model.
- **Gate 3 Review (`agent2`)**: **`flash`** (`invoke_subagent` with `TypeName: "research"`, `Model: "flash"`) — Audits structural completeness (1:1 mapping of `REQ`/`TST` to atomic steps, DB migration checklist, and strict *Code Lock* verification).

## Key Responsibilities (`agent1` — `pro`)
1. **Architectural Decomposition (SWE.2)**:
   - Identify clean architecture boundaries: UI (Jetpack Compose), ViewModel (`StateFlow`), Repository, Database (SQLite / Room / ContentProvider), Service (`TrackerService`).
   - Define thread dispatchers (`dbDispatcher`, `Dispatchers.Main`, `Dispatchers.IO`).
2. **Atomic Step Sequencing**:
   - Break down construction into bite-sized, order-dependent steps.
   - Map each step to specific target files and targeted unit test commands.
3. **Invariant Protection & Rollback Safety**:
   - Explicitly list non-negotiable invariants (backward compatibility, null safety, human decision gate).
   - **UI Consistency**: If the ticket adds or changes UI, read `docs/design_guidelines.md` (Section 5) and fill the plan's `UI Consistency` section: closest existing reference screen, reused components (`ui/components/`), theme tokens (shapes, spacing, `MaterialTheme.colorScheme`, `TTColor`), and a justification for any new one-off style.
4. **Single-Turn Stage Submission (`submit-stage`)**:
   - Author `docs/engineering/plans/<TICKET_KEY>_plan.md` using `templates/plan_template.md`.
   - Submit Stage 3 in a single composite CLI call (creates/updates `[Impl-Plan]` sub-task and moves it to `In Überprüfung`):
     ```bash
     python3 tools/jira_util.py --as agent1 submit-stage <TICKET_KEY> 3 @docs/engineering/plans/<TICKET_KEY>_plan.md
     ```
   - **One Commit per Ticket (Core Rule 8)**: Do not create an intermediate git commit for Stage 3; keep changes on the working branch until Stage 5.

## Gate 3 Independent Review Checklist (`agent2` — `flash` `research` Subagent)
Spawn an independent `research` subagent (`Model: "flash"`) to audit the Stage 3 plan against this checklist:
1. **Architectural Integrity & Layering**:
   - Verify component boundaries, layering rules (SWE.2), and interface stability. Note: This gate audits the architectural implementation plan document (Stage 3). Under ASPICE / TDD process governance, production code implementation is strictly deferred to Stage 4 after the plan passes Gate 3 audit. Do not penalize Stage 3 for not yet having code modifications in the git diff.
2. **Invariant Verification**:
   - Confirm all system invariants, non-target metrics, and file schemas are explicitly protected.
3. **Verification Coverage**:
   - Ensure every proposed change maps to an automated or manual test case in `docs/tests.md`.
4. **Gate Verdict (`gate-verdict`)**:
   - Record the verdict in a single composite CLI call (which closes the sub-task to `Erledigt`, advances the parent ticket to `Implementation`, and automatically injects `stage4-implementation/SKILL.md` into stdout):
     ```bash
     python3 tools/jira_util.py --as agent2 gate-verdict <IMPL_PLAN_SUBTASK_KEY> PASS "<Audit Report>"
     ```
     *(Or `REVISION` if changes are required.)*

## Deliverable Template
Use the standardized markdown template located at:
`templates/plan_template.md`
