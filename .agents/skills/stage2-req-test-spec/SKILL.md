---
name: stage2-req-test-spec
description: Executes Stage 2 (Requirement & Test Specification) of the agile ASPICE lifecycle. Formulates formal requirements (REQ-XXX), test cases (TST-XXX), Given-When-Then acceptance criteria, 9-language localization parity, and synchronizes living documentation.
---

# Skill: stage2-req-test-spec

## Overview
This skill guides the agent through **Stage 2 (Requirement & Test Specification)**. It defines the formal requirement (`REQ-XXX`) and the concrete verification procedures (`TST-XXX`) required to validate the feature or fix prior to planning software construction.

## Subagent Model Pairing ("Zipper Principle" & Persistent Ping-Pong — Core Rule 7)
Reuse the ticket's two persistent `TypeName: "self"` subagents via `send_message`:
- **Doing (`--as agent1`)**: **`subagent_flash`** (`send_message` to the ticket's `flash` subagent, which is already warm from auditing Gate 1!) — Translating the approved Stage 1 analysis into formal `REQ-XXX` (`SHALL`), `TST-XXX` (`Given-When-Then`), and 9-locale string tables is structured specification drafting.
- **Gate 2 Review (`--as agent2`)**: **`subagent_pro`** (`send_message` to the ticket's `pro` subagent, which authored Stage 1!) — Deep semantic scrutiny challenging whether the specified tests genuinely prove the fix/invariants and auditing all 4 fields of *Chesterton's Fence*, followed by executing `python3 tools/jira_util.py --as agent2 gate-verdict <SUBTASK_KEY> PASS ...`.

## Key Responsibilities (`subagent_flash` as `agent1` — `flash`)
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
4. **Single-Turn Stage Submission (`submit-stage`)**:
   - Author `docs/engineering/test_specs/<TICKET_KEY>_test_spec.md` using `templates/test_spec_template.md`.
   - Submit Stage 2 in a single composite CLI call (creates/updates `[Req & Test Spec]` sub-task and moves it to `In Überprüfung`):
     ```bash
     python3 tools/jira_util.py --as agent1 submit-stage <TICKET_KEY> 2 @docs/engineering/test_specs/<TICKET_KEY>_test_spec.md
     ```
   - **One Commit per Ticket (Core Rule 8)**: Do not create an intermediate git commit for Stage 2; keep changes on the working branch until Stage 5.

## Gate 2 Independent Review Checklist (`subagent_pro` as `agent2` — `pro`)
Message the ticket's persistent `subagent_pro` (`send_message`) to audit the Stage 2 deliverable and living documentation against this checklist and record the verdict via `--as agent2 gate-verdict`:
1. **Requirement Phrasing Standards**:
   - Verify standard syntax: `SHALL` for functional behavior, `MUST` for technical constraints.
   - Verify atomic, unambiguous, system-centric, and state-oriented descriptions (not implementation steps).
   - Verify explicit `Given-When-Then` acceptance criteria and System Invariants.
   - **User Scope Grounding (`ATT-1250`)**: Verify requirements strictly focus on the scoped feature or bugfix. Do NOT require expanding requirements to unrelated components or unrequested workflows.
2. **Test Case Traceability**:
   - Verify concrete test procedure and expected result in `docs/tests.md`.
   - Ensure complete bidirectional traceability between requirements and test cases.
   - **Stage Awareness**: In Gate 2 (Stage 2), test cases and procedures are SPECIFIED in `docs/tests.md` and the deliverable. Implementation code and unit test files are constructed during Stage 4. Do NOT reject Gate 2 for uncommitted test code files.
3. **Requirement Archaeology (Chesterton's Fence)**:
   - If existing requirements are modified, relaxed, or replaced in `docs/requirements.md`, verify presence and validity of the 4-field archaeology section:
     - `Original Requirement ID & Target`
     - `Historical Origin & Commit Trace`
     - `Root Reason for Existing Formulation` ("Why was this fence built?")
     - `Preservation of Core Invariants` ("Why is it safe to modify now?")
   - Net-new requirements (novel IDs absent from base) are exempt from this hurdle.
4. **Gate Verdict (`gate-verdict`)**:
   - Record the verdict in a single composite CLI call (which closes the sub-task to `Erledigt`, advances the parent ticket to `Implementation Plan`, and automatically injects `stage3-impl-plan/SKILL.md` into stdout):
     ```bash
     python3 tools/jira_util.py --as agent2 gate-verdict <TEST_SPEC_SUBTASK_KEY> PASS "<Audit Report>"
     ```
     *(Or `REVISION` if changes are required.)*

## Deliverable Template
Use the standardized markdown template located at:
`templates/test_spec_template.md`
