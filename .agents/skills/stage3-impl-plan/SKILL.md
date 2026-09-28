---
name: stage3-impl-plan
description: Executes Stage 3 (Implementation Plan) of the agile ASPICE lifecycle. Deconstructs requirements into bite-sized atomic steps, designs SWE.2 architecture, enforces invariants, and prepares Gate 3 review.
---

# Skill: stage3-impl-plan

## Overview
This skill guides the agent through **Stage 3 (Implementation Planning)**. It produces a detailed, step-by-step engineering plan defining the exact components, classes, SQLite migrations, and test mappings before writing production code.

## Key Responsibilities
1. **Architectural Decomposition (SWE.2)**:
   - Identify clean architecture boundaries: UI (Jetpack Compose), ViewModel (StateFlow), Repository, Database (SQLite / Room / ContentProvider), Service (`TrackerService`).
   - Define thread dispatchers (`dbDispatcher`, `Dispatchers.Main`, `Dispatchers.IO`).
2. **Atomic Step Sequencing**:
   - Break down construction into bite-sized, order-dependent steps.
   - Map each step to specific target files and targeted unit test commands.
3. **Invariant Protection & Rollback Safety**:
   - Explicitly list non-negotiable invariants (backward compatibility, null safety, human decision gate).
4. **Deliverable Production & Gate Audit**:
   - Author `docs/engineering/plans/<TICKET_KEY>_plan.md` using `templates/plan_template.md`.
   - Update Stage 3 Jira subtask (`[Impl-Plan]`) Description with the complete text.
   - Transition subtask to `In Überprüfung` and run Gate 3 audit (`tools/review_agent.py audit <KEY>`).
   - Upon Gate 3 approval (`RECOMMEND PASS`), transition subtask directly to `Erledigt` via transition `freigabe`.

## Deliverable Template
Use the standardized markdown template located at:
`templates/plan_template.md`
