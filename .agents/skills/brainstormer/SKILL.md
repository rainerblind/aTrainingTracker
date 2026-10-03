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
   - Identify relevant Parent Epics (e.g. `ATT-232` Process, `ATT-1396` Lieblingsorte, `ATT-1454` Cockpit).
3. **Automated Jira Backlog Ticket Creation**:
   - Format description using `templates/backlog_ticket_template.md`.
   - File the ticket cleanly in Jira via CLI (Rule 18: Summaries MUST NOT contain category prefixes or Epic names in brackets):
     ```bash
     python3 tools/jira_util.py create-issue "Concise Summary Describing Objective or Defect" "@scratch/ticket_desc.txt" "10008" "PARENT_EPIC_KEY"
     ```
   - Crucial: Backlog tickets should NOT be added to the active sprint (`--add-to-sprint` is strictly omitted). Agents must NEVER move tickets into active sprints.
   - **STRICT INVARIANT: Stop Immediately After Creation**: Creating a ticket must NEVER trigger implementation (`"When an agent is asked to create a ticket, he immediately wants to start realizing it. This must never ever happen!"`). The agent must report the issue key to the user and STOP. Autonomous branching, subtask generation, or stage execution is strictly prohibited without explicit human command.

## Deliverable Template
Use the standardized markdown template located at:
`templates/backlog_ticket_template.md`
