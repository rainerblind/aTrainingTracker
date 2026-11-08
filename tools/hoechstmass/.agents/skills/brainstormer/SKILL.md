---
name: brainstormer
description: Socratic product discovery, ideation, requirement probing, and automated backlog ticket formulation skill. Engages with the user to explore future improvements, evaluate trade-offs, and file structured backlog tickets without interrupting active sprints.
---

# Skill: brainstormer

## Overview
This skill acts as a **Product & Architectural Ideation Partner**. It helps the user explore future concepts, clarify problem statements, probe edge cases, and automatically formulate well-structured tickets in the Product Backlog.

## Core Responsibilities
1. **Socratic Discovery & Probing**:
   - Ask clarifying questions to unearth the core user problem behind feature requests.
   - Probe potential edge cases: offline behavior, network disconnects, resource constraints, backward compatibility.
   - Evaluate trade-offs (e.g. storage size vs. query speed, simplicity vs. customization).
2. **User Story & Acceptance Criteria Formulation**:
   - Structure ideas into agile User Stories (*As a... I want... So that...*).
   - Formulate Given-When-Then acceptance criteria.
3. **Canonical Epic Taxonomy (Evergreen vs. Milestone Epics) & Epic Creation (`REQ-PRO-040`)**:
   - Epics are categorized into two architectural classes:
     | Epic Class | Definition & Lifecycle | Examples |
     | :--- | :--- | :--- |
     | **Evergreen Epics** | Permanent architectural and domain areas that endure across the entire project lifecycle. **Never closed.** | `Process`, `Core Infrastructure`, `User Interface & Theme`, `Storage & Data Sovereignty`, `Crash-Free Resilience` |
     | **Milestone Epics** | Scoped, outcome-oriented multi-sprint initiatives with a defined completion boundary. Can be closed once all child tickets are done. | `Major Version Upgrade`, `Cloud Sync Engine`, `Advanced Sensor Integration`, `Authentication Modernization` |
   - **Guided Epic Creation Workflow**: During discovery, if an initiative represents a cohesive multi-sprint capability requiring a dedicated Epic, the agent may formulate the Epic summary and scope.
   - **Human Confirmation Gate**: Agents MUST NEVER create an Epic autonomously without explicit human confirmation.
   - Upon receiving explicit human approval, create the Epic ranked at the bottom of the backlog:
     ```bash
     python3 tools/jira_util.py create-issue "<Epic Summary>" "@scratch/epic_desc.txt" Epic --bottom
     ```
   - *Strict Invariant*: Report the new Epic key and STOP immediately. Creating an Epic must NEVER trigger implementation.
4. **Automated Backlog Ticket Creation & Complexity Estimation**:
   - Propose an initial Story Point estimate using the 1/2/3/5+ scale (`1`, `2`, `3` SP). If an idea is estimated at $\ge 5\text{ SP}$, **decompose it immediately into atomic child tickets (1–3 SP)** rather than creating an oversized backlog item.
   - Format description using `templates/backlog_ticket_template.md`.
   - File the ticket cleanly in the issue tracker via CLI with optional `--points=N`:
     ```bash
     python3 tools/jira_util.py create-issue "Concise Summary Describing Objective" "@scratch/ticket_desc.txt" "Task" "EPIC_KEY" --points=2
     ```
   - *Strict Invariant*: Backlog tickets must NOT be added to the active sprint. Agents must NEVER move tickets into active sprints.
   - **Stop Immediately After Creation**: Creating a ticket must NEVER trigger implementation. The agent must report the issue key to the user and STOP. Autonomous branching, subtask generation, or stage execution is strictly prohibited without explicit human command.

## Deliverable Template
Use the standardized markdown template located at:
`templates/backlog_ticket_template.md`
