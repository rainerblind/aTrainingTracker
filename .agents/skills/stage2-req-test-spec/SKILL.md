---
name: stage2-req-test-spec
description: Executes Stage 2 (Requirement & Test Specification) of the agile ASPICE lifecycle. Formulates formal requirements (REQ-XXX), test cases (TST-XXX), Given-When-Then acceptance criteria, 9-language localization parity, and synchronizes living documentation.
---

# Skill: stage2-req-test-spec

## Overview
This skill guides the agent through **Stage 2 (Requirement & Test Specification)**. It defines the formal requirement (`REQ-XXX`) and the concrete verification procedures (`TST-XXX`) required to validate the feature or fix prior to planning software construction.

## Key Responsibilities
1. **Requirement Formulation (`REQ-XXX`)**:
   - Write clear SHALL/MUST clauses, structured architectural constraints, and Given-When-Then acceptance criteria.
   - Insert new requirement rows into `docs/requirements.md` (or conduct Chesterton's Fence archaeology if modifying existing lines).
   - Validate governance via `python3 tools/verify_requirement_governance.py`.
2. **Test Specification (`TST-XXX`)**:
   - Specify deterministic unit, repository, ViewModel, database, and UI test cases.
   - Include 9-language localization audit (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) ensuring zero missing entries, matching format specifiers, and literal `\n` linebreaks (never XML entity `&#10;` which AAPT2 collapses).
   - Synchronize `docs/tests.md` with the new test definition.
3. **Traceability Matrix**:
   - Map all test cases to requirement IDs in a structured markdown table.
4. **Deliverable Production, Gate Audit & Synchronous Parent Stage Progression (Rule 3 / Rule 45)**:
   - Author `docs/engineering/test_specs/<TICKET_KEY>_test_spec.md` using `templates/test_spec_template.md`.
   - Update Stage 2 Jira subtask (`[Req & Test Spec]`) Description with the complete text.
   - Transition subtask to `In Überprüfung` and run Gate 2 audit (`tools/review_agent.py audit <KEY>`).
   - Upon Gate 2 approval (`RECOMMEND PASS`), transition subtask directly to `Erledigt` via transition `freigabe` (`python3 tools/jira_util.py move <SUBTASK_KEY> freigabe`), which automatically advances the parent ticket from `Test Spec` to `Implementation Plan`.
   - **One Commit per Ticket (Rule 44)**: Do not create a separate intermediate git commit for Stage 2; keep changes on the working branch until the single atomic ticket commit at the end of Stage 5 (or squash when merging into `sprint/<SPRINT_NAME>`).

## Deliverable Template
Use the standardized markdown template located at:
`templates/test_spec_template.md`
