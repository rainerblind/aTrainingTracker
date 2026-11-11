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
* **Parallel Sprint Execution Across Disjoint Epics (`Rule 9`, `REQ-PRO-041`)**: Up to 2 tickets may run concurrently across disjoint Epics using `Workspace: "share"` worktrees. All heavy Gradle test runs and Stage 5 squash-merges serialize under POSIX `flock` on `/tmp/att_build_merge.lock` (`python3 tools/parallel_sprint_lock.py <cmd>`). Stage 5 rebases execute `python3 tools/verify_requirement_governance.py --sync-indexes` under the merge lock prior to squashing.
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
3. **Upfront Coordinator Batch Audit & Just-In-Time `Analysis` Transition (`Rule 77` & `Rule 78`)**:
   - Inspect each committed ticket in detail upfront (checking for duplicates, human prerequisites, and Story Point estimates 1, 2, 3 SP; split any $\ge 5\text{ SP}$ ticket).
   - Clarify any ambiguous requirements in a consolidated turn, keeping approved tickets in `To Do` / `Zu erledigen` until Stage 1 (`[Analysis]`) actually begins for each ticket.

---

## Ceremony 2: Sprint-End & Joint Review
0. **Stage 6 (`Final Review (Agent)`) Verification & Retroactive Cold Review Sweep (`REQ-PRO-058`)**:
   - Query whether any sprint tickets remain in `Final Review (Agent)`:
     ```bash
     python3 tools/jira_util.py search "sprint = <SPRINT_ID> AND status = 'Final Review (Agent)' ORDER BY rank ASC"
     ```
   - If any tickets are still in `Final Review (Agent)`, run the Stage 6 Cold `Model: "pro"` Audit (`stage6-final-review/SKILL.md`) in Epic-grouped batches and execute `final-review-verdict <KEY> PASS|BOUNCE ...` (with up to 3 healing bounces) until all completed tickets are in `Final Review (Human)`.
1. **Full Integration Verification**:
   - Execute the complete test suite and requirement governance check across the integrated sprint branch. Specify `Two-Tier Device Policy` (`REQ-TST-004`): `emulator-5554` for autonomous Stages 1–6 and physical phone (`Pixel 10`) for Ceremony 2.
2. **Ticket-by-Ticket Acceptance (`Rule 68` & `Rule 79`)**:
   - Present each completed ticket in `Final Review (Human)` strictly one-by-one in rank order with its walkthrough summary, code diff review, and proactive recommendation.
   - Complete all preparatory tool calls (`set-fixversion` for Ticket $N-1$, `read_file` / `git show` for Ticket $N$) **before** emitting the presentation response for Ticket $N$, and include **zero tool calls** in the presentation message itself so the turn terminates immediately and waits for the user's verdict.
   - Human accepts (`Erledigt` / Done) or rejects back to `Analysis` with feedback.
3. **Milestone Epic Review & Human-Confirmed Closure (`REQ-PRO-040`)**:
   - Inspect project Epics via CLI:
     ```bash
     python3 tools/jira_util.py list-epics
     ```
   - If any **Milestone Epic** has **0 open child tickets** remaining and all intended scope is verified:
     1. Proactively present the Epic completion status to the human user.
     2. Recommend closing the Milestone Epic (Evergreen Epics remain open indefinitely).
     3. Upon explicit human confirmation, close the Epic:
        ```bash
        python3 tools/jira_util.py --as coordinator close-epic <EPIC_KEY>
        ```
     4. *Strict Invariant*: Standard parent tickets remain strictly under the Human Decision Gate. Only Milestone Epics with 0 open child tickets may be closed via `close-epic` after human confirmation.
4. **Sprint Retrospective & Quantitative Process Control (PA 4.1 & PA 4.2)**:
   - Execute automated scorecard generation before merging:
     ```bash
     python3 tools/jira_util.py --as coordinator sprint-metrics --record --post-to-retro
     ```
   - Embed the quantitative scorecard (Gate 1–5 FTPR, Swiss Cheese defect escape rates $p_1 \dots p_5$, and Statistical Process Control limits $\mu \pm 2\sigma$) into the retrospective deliverable `docs/engineering/retros/Sprint_Review_and_Retro_<SPRINT_NAME>.md` using `templates/sprint_retro_template.md`.
   - **Closed-Loop Trigger (PA 4.2 $\rightarrow$ PA 5.2)**: If any metric triggers an out-of-control condition (`⚠️ PA 4.2 Special Cause Variation` outside $\mu \pm 2\sigma$), the retrospective report MUST define an explicit Root-Cause Analysis (RCA) and concrete process/skill countermeasure prior to sprint merge.
   - Calculate planned vs. completed velocity (tickets and story points).
5. **Final Branch Integration**:
   - Merge `sprint/<SPRINT_NAME>` into develop/main (`--no-ff`), delete the sprint branch, and close the sprint.
