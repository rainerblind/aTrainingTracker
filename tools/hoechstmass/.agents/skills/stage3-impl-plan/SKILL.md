---
name: stage3-impl-plan
description: Executes Stage 3 (Implementation Plan) of the agile ASPICE lifecycle. Deconstructs requirements into bite-sized atomic steps, designs SWE.2 architecture, enforces invariants, and prepares Gate 3 review.
---

# Skill: stage3-impl-plan

## Overview
This skill guides the agent through **Stage 3 (Implementation Planning)**. It produces a detailed, step-by-step engineering plan defining the exact components, classes, schema migrations, and test mappings before writing production code.

## Subagent Model Pairing ("Zipper Principle" — Core Rule 7)
Reuse the persistent subagent pair via messages:
- **Doing (`--as agent1`)**: **`subagent_pro`** (High-reasoning model tier, warm from auditing Gate 2) — SWE.2 architectural design (layer boundaries, thread safety, decomposition into atomic steps) is authored by the senior reasoning model.
- **Gate 3 Review (`--as agent2`)**: **`subagent_flash`** (Fast-execution model tier) — Audits structural completeness (1:1 mapping of requirements/tests to atomic steps, migration checklists, and strict *Code Lock* verification), followed by executing the gate-verdict command:
  ```bash
  python3 tools/workflow_util.py --as agent2 gate-verdict <SUBTASK_KEY> PASS "<Audit Report>"
  ```
  Upon `PASS`, immediately proceeds to execute Stage 4 Doing (`--as agent1`) in the same turn for Standard tickets.

## Key Responsibilities (`subagent_pro` as `agent1`)
1. **Architectural Decomposition & SWE.2 Design**:
   - Identify clean architecture boundaries: UI/Presentation, Business Logic / ViewModels, Repositories / Data Access, Storage / Database, and Services.
   - Define thread dispatchers, concurrency bounds, and resource lifecycles.
   - If introducing new subsystems or interfaces, plan updates to living architecture documentation.
2. **Atomic Step Sequencing**:
   - Break down construction into bite-sized, order-dependent steps.
   - Map each step to specific target files and targeted unit test commands.
3. **Invariant Protection & Rollback Safety**:
   - Explicitly list non-negotiable invariants (backward compatibility, null safety, human decision gate).
   - If adding or changing UI, align with `docs/design_guidelines.md` and define reused components and theme tokens.
4. **Single-Turn Stage Submission (`submit-stage`) & Concise Deliverable Cap**:
   - Author `docs/engineering/plans/<TICKET_KEY>_plan.md` using `templates/plan_template.md` (target $\le 60\text{--}80$ lines).
   - Submit Stage 3 in a single composite CLI call:
     ```bash
     python3 tools/workflow_util.py --as agent1 submit-stage <TICKET_KEY> 3 @docs/engineering/plans/<TICKET_KEY>_plan.md
     ```

## Gate 3 Independent Review Checklist (`subagent_flash` as `agent2`)
Audit the Stage 3 plan against this checklist:
1. **Architectural Integrity & Layering**:
   - Verify component boundaries, layering rules (SWE.2), and interface stability. Note: Under ASPICE / TDD governance, production code implementation is strictly deferred to Stage 4 after the plan passes Gate 3 audit.
2. **Invariant Verification**:
   - Confirm all system invariants, non-target metrics, and file schemas are explicitly protected.
3. **Verification Coverage**:
   - Ensure every proposed change maps to an automated or manual test case.
4. **Gate Verdict (`gate-verdict`) + Fused Stage 4 Continuation**:
   - Record verdict via `--as agent2 gate-verdict`.
   - For Standard tickets, if `PASS`, **immediately proceed in the same turn** to verify `check-gate` and execute Stage 4 (`--as agent1`).
