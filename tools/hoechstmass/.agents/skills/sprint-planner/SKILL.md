---
name: sprint-planner
description: Facilitates the agile Sprint lifecycle ceremonies. Conducts interactive ticket-by-ticket Sprint-Start Screening with the human user (clarifying ambiguous requirements and moving ready tickets to Analysis) and facilitates the Sprint-End Joint Review (evaluating tickets in Final Review, merging accepted tickets, or bouncing back to Analysis).
---

# Skill: sprint-planner

## Overview
This skill acts as the **Agile Sprint Facilitator & Scrum Master**. It orchestrates the two human-in-the-loop anchor points of every sprint and governs the sprint integration branch continuous integration model:
1. **Phase 1: Sprint-Start & Interactive Ticket-Screening** (Establishing `sprint/<sprint_id>` and aligning requirements).
2. **Phase 2: Autonomous In-Sprint Pipeline with Continuous Sprint Integration** (Agents work autonomously, merging verified tickets directly into `sprint/<sprint_id>` to eliminate merge conflicts).
3. **Phase 3: Sprint-End & Joint Review** (Joint evaluation on the integrated sprint build, release sign-off, and final merge to main/develop).

---

## Sprint Integration Branching Model: `sprint/<sprint_id>`
* **Sprint Branch**: Created at Sprint-Start from the main development branch: `sprint/<SPRINT_NAME>`.
* **Feature Branches**: Each sprint ticket branches from `sprint/<SPRINT_NAME>`: `feature/<KEY>`.
* **Single Atomic Commit per Ticket**: As soon as a ticket completes Stage 5, the agent integrates `feature/<KEY>` into `sprint/<SPRINT_NAME>` as **strictly one atomic commit** via squash-merge, deletes `feature/<KEY>`, and transitions the parent ticket to `Final Review (Human)`.
* **Sprint-End Integration**: At Sprint-End, the human user validates the entire sprint build. Upon final sign-off, `sprint/<SPRINT_NAME>` is merged into the main development branch and deleted.

---

## Ceremony 1: Sprint-Start & Interactive Ticket Screening
1. **Query Active Sprint Backlog**:
   - Query all candidate tickets in the active sprint in rank order:
     ```bash
     python3 tools/workflow_util.py search "sprint = <SPRINT_ID> AND status = 'To Do' ORDER BY rank ASC"
     ```
   - Ensure a `Review & Retro` ceremony ticket exists in the active sprint.
   - *Strict Invariant*: AI agents MUST NEVER move backlog tickets into active sprints autonomously. Only the human user assigns backlog tickets to sprints.
2. **Establish Sprint Integration Branch**:
   - Create and push `sprint/<SPRINT_NAME>` from the main development branch.
3. **Interactive Ticket-by-Ticket Walkthrough**:
   - Walk through each committed ticket with the human user.
   - Propose Story Point estimates (1, 2, 3 SP). Enforce the $\ge 5\text{ SP}$ split guardrail: any ticket estimated at $\ge 5\text{ SP}$ must be decomposed into smaller atomic tickets.
   - Clarify ambiguous requirements, confirm acceptance criteria, and move screened tickets to `Analysis`.

---

## Ceremony 2: Sprint-End & Joint Review
1. **Full Integration Verification**:
   - Execute the complete test suite and requirement governance check across the integrated sprint branch.
2. **Ticket-by-Ticket Acceptance**:
   - Present each completed ticket in `Final Review (Human)` to the user with its walkthrough summary and test results.
   - Human accepts (`Erledigt` / Done) or rejects back to `Analysis` with feedback.
3. **Sprint Retrospective & Quantitative Process Control (PA 4.1 & PA 4.2)**:
   - Execute automated scorecard generation before merging:
     ```bash
     python3 tools/jira_util.py --as coordinator sprint-metrics --record --post-to-retro
     ```
   - Embed the quantitative scorecard (Gate 1–5 FTPR, Swiss Cheese defect escape rates $p_1 \dots p_5$, and Statistical Process Control limits $\mu \pm 2\sigma$) into the retrospective deliverable `docs/engineering/retros/Sprint_Review_and_Retro_<SPRINT_NAME>.md` using `templates/sprint_retro_template.md`.
   - **Closed-Loop Trigger (PA 4.2 $\rightarrow$ PA 5.2)**: If any metric triggers an out-of-control condition (`⚠️ PA 4.2 Special Cause Variation` outside $\mu \pm 2\sigma$), the retrospective report MUST define an explicit Root-Cause Analysis (RCA) and concrete process/skill countermeasure prior to sprint merge.
   - Calculate planned vs. completed velocity (tickets and story points).
4. **Final Branch Integration**:
   - Merge `sprint/<SPRINT_NAME>` into develop/main (`--no-ff`), delete the sprint branch, and close the sprint.
