---
name: brainstormer
description: Socratic product discovery, ideation, requirement probing, and automated Jira backlog ticket formulation skill. Engages with the user to explore future improvements, evaluate trade-offs, and file structured backlog tickets without interrupting active sprints.
---

# Skill: brainstormer

## Overview
This skill acts as a **Product & Architectural Ideation Partner**. It helps the user explore future concepts, clarify problem statements, probe edge cases, and automatically formulate well-structured tickets in the Jira Product Backlog.

## Core Responsibilities
1. **Socratic Discovery & Probing**:
   - Ask clarifying questions to unearth the core user problem behind feature requests.
   - Probe potential edge cases: offline behavior, sensor disconnects, battery efficiency, SQLite schema backwards compatibility.
   - Evaluate trade-offs (e.g. storage size vs. query speed, simplicity vs. customization).
2. **User Story & Acceptance Criteria Formulation**:
   - Structure ideas into agile User Stories (*As a... I want... So that...*).
   - Formulate Given-When-Then acceptance criteria.
   - Identify relevant Parent Epics (e.g. `ATT-232` Process, `ATT-1396` Lieblingsorte, `ATT-5908` Cockpit).
3. **Canonical Epic Taxonomy (Evergreen vs. Milestone Epics) & Epic Creation (`REQ-PRO-040`)**:
   - Jira Epics are categorized into two architectural classes:
     | Epic Class | Definition & Lifecycle | Examples in Project ATT |
     | :--- | :--- | :--- |
     | **Evergreen Epics** | Permanent architectural and domain areas that endure across the entire app lifecycle. **Never closed.** | `Process` (`ATT-232`), `Cockpit & Live-Tracking UX` (`ATT-5908`), `Karten & Routen` (`ATT-66`), `Workouts & Aftermath` (`ATT-68`), `Import, Export & Backup` (`ATT-281`), `Global UI & Design System` (`ATT-355`), `Crash-Free Resilience` (`ATT-235`), `Clean Code` (`ATT-431`) |
     | **Milestone Epics** | Scoped, outcome-oriented multi-sprint initiatives with a defined completion boundary. Can be closed once all child tickets are `Erledigt`. | `Periods 2.0` (`ATT-4474`), `Cycling Power Dynamics` (`ATT-6075`), `Structured Workouts & Training Plan` (`ATT-6064`), `Cycling Rearview Radar` (`ATT-1286`), `Mader-Heck Metabolic Model` (`ATT-4786`) |
   - **Guided Epic Creation Workflow**: During discovery, if an initiative represents a cohesive multi-sprint capability requiring a dedicated Epic, the agent may formulate the Epic summary and scope.
   - **Human Confirmation Gate**: Agents MUST NEVER create an Epic autonomously without explicit human confirmation.
   - Upon receiving explicit human approval, create the Epic ranked at the bottom of the backlog:
     ```bash
     python3 tools/jira_util.py create-issue "<Epic Summary>" "@scratch/epic_desc.txt" Epic --bottom
     ```
   - *Strict Invariant*: Report the new Epic key and STOP immediately. Creating an Epic must NEVER trigger implementation.
4. **Automated Jira Backlog Ticket Creation & Complexity Estimation (`REQ-PRO-029`)**:
   - Propose an initial Story Point estimate using strictly canonical **Fibonacci** numbers **`{0, 1, 2, 3, 5, 8, 13, 21}`** (`Rule 8.3`).
     - **`0–5 SP`**: Standard atomic tickets — ready to create directly.
     - **`8 SP` (Mandatory PO Discussion at Ticket Creation)**: Do **NOT** silently file an `8 SP` ticket or auto-split it without asking. Before running `create-issue`, explicitly discuss the `8 SP` estimate with the PO: explain why it is `8 SP`, present a concrete split option (`<= 5 SP` child tickets) alongside the keep-as-`8 SP` option, and wait for the PO's decision.
     - **`> 8 SP` (`13` or `21 SP`)**: **Mandatory split** — decompose into smaller Fibonacci child tickets (`<= 5 SP`, or `8 SP` only after explicit PO confirmation) rather than scheduling an unsplit oversized backlog item.
   - **Requirement-Driven Ticket Type Selection (`Rule 8.3`)**: Choose the Jira issue type strictly by its effect on requirements (`REQ-*`): **`Feature`** (`10007`) introduces a **new** requirement; **`Verbesserung`** (`10008`) **changes/refines** an already existing requirement; **`Bug`** (`10006`) leaves the existing requirement **unchanged** because the implementation failed to fulfill it (including crashes, ANRs, or data loss violating implicit invariants).
   - **Assign Initial Risk-Tier Label (`ATT-9540`, `Rule 8.3`)**: Always pass `--labels=tier-a`, `--labels=tier-b`, or `--labels=tier-c` (or follow with `set-tier`): `tier-a` (sensors/BLE/ANT+, fusion/EKF, concurrency, persistence/migrations, data loss, crash/ANR, release), `tier-b` (normal features & bugs), `tier-c` (UI-only tweaks, docs, tooling, test-only). Overridable by PO; silent downgrade prohibited.
   - **Process Tooling Diet Gate (`ATT-9547`, `Rule 8.10`)**: Before creating any ticket that proposes new process tooling (`tools/*.py`) or a new engineering document category (`docs/engineering/*`), require an explicit reference to a **measured problem** (weekly `method_scorecard.jsonl` metric, SPC alert, or archived ASPICE/QA audit finding); reject unbacked tooling proposals.
   - Format description using `templates/backlog_ticket_template.md`.
   - File the ticket cleanly in Jira via CLI with optional `--points=N` (Rule 18: Summaries MUST NOT contain category prefixes or Epic names in brackets):
     ```bash
     python3 tools/jira_util.py create-issue "Concise Summary Describing Objective or Defect" "@scratch/ticket_desc.txt" "10008" "PARENT_EPIC_KEY" --points=2
     ```
   - Crucial: Backlog tickets should NOT be added to the active sprint (`--add-to-sprint` is strictly omitted). Agents must NEVER move tickets into active sprints.
   - **STRICT INVARIANT: Stop Immediately After Creation**: Creating a ticket must NEVER trigger implementation (`"When an agent is asked to create a ticket, he immediately wants to start realizing it. This must never ever happen!"`). The agent must report the issue key to the user and STOP. Autonomous branching, subtask generation, or stage execution is strictly prohibited without explicit human command.

## Deliverable Template
Use the standardized markdown template located at:
`templates/backlog_ticket_template.md`
