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
   - File the ticket cleanly in Jira via CLI:
     ```bash
     python3 tools/jira_util.py create-issue "[Improvement] [Subsystem] Short Summary" "@scratch/ticket_desc.txt" "10008" "PARENT_EPIC_KEY"
     ```
   - Crucial: Backlog tickets should NOT be added to the active sprint (`--add-to-sprint` is omitted) so active sprint commitments remain stable.

## Deliverable Template
Use the standardized markdown template located at:
`templates/backlog_ticket_template.md`
