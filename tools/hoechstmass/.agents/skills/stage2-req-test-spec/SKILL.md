---
name: stage2-req-test-spec
description: Executes Stage 2 (Requirement & Test Specification) of the agile ASPICE lifecycle. Formulates formal requirements (REQ-XXX), test cases (TST-XXX), Given-When-Then acceptance criteria, and synchronizes living documentation.
---

# Skill: stage2-req-test-spec

## Overview
This skill guides the agent through **Stage 2 (Requirement & Test Specification)**. It defines the formal requirement (`REQ-XXX`) and the concrete verification procedures (`TST-XXX`) required to validate the feature or fix prior to planning software construction.

## Subagent Model Pairing ("Zipper Principle" — Core Rule 7)
Reuse the persistent subagent pair via messages:
- **Doing (`--as agent1`)**: **`subagent_flash`** (Fast-execution model tier, warm from auditing Gate 1) — Translating approved Stage 1 analysis into formal `REQ-XXX` (`SHALL`/`MUST`), `TST-XXX` (`Given-When-Then`), and living specification updates.
- **Gate 2 Review (`--as agent2`)**: **`subagent_pro`** (High-reasoning model tier) — Deep semantic scrutiny challenging whether specified tests genuinely prove the fix/invariants and auditing Chesterton's Fence archaeology, followed by executing the gate-verdict command:
  ```bash
  python3 tools/workflow_util.py --as agent2 gate-verdict <SUBTASK_KEY> PASS "<Audit Report>"
  ```
  Upon `PASS`, immediately proceeds to execute Stage 3 Doing (`--as agent1`) in the same turn.

## Key Responsibilities (`subagent_flash` as `agent1`)
1. **Requirement Formulation (`REQ-XXX`)**:
   - Write clear `SHALL` (functional behavior) and `MUST` (technical constraints) clauses, structured architectural invariants, and Given-When-Then acceptance criteria.
   - Insert new requirement rows into living specifications under `docs/requirements/` using multi-line section format.
   - Validate requirement governance tooling.
2. **Test Specification (`TST-XXX`)**:
   - Specify deterministic unit, repository, service, database, and integration test cases.
   - Synchronize living test documentation under `docs/tests/`.
3. **Traceability Matrix**:
   - Map all test cases to requirement IDs in a structured markdown table.
4. **Single-Turn Stage Submission (`submit-stage`) & Concise Deliverable Cap**:
   - Author `docs/engineering/test_specs/<TICKET_KEY>_test_spec.md` using `templates/test_spec_template.md` (target $\le 60\text{--}80$ lines).
   - Submit Stage 2 in a single composite CLI call:
     ```bash
     python3 tools/workflow_util.py --as agent1 submit-stage <TICKET_KEY> 2 @docs/engineering/test_specs/<TICKET_KEY>_test_spec.md
     ```

## Gate 2 Independent Review Checklist (`subagent_pro` as `agent2`)
Audit the Stage 2 deliverable and living documentation against this checklist:
1. **Requirement Phrasing Standards**:
   - Verify standard syntax: `SHALL` for functional behavior, `MUST` for technical constraints.
   - Verify atomic, unambiguous, system-centric, and state-oriented descriptions.
   - Verify explicit Given-When-Then acceptance criteria and System Invariants.
2. **Test Case Traceability**:
   - Verify concrete test procedure and expected outcome in test documentation.
   - Ensure complete bidirectional traceability between requirements and test cases.
3. **Requirement Archaeology (Chesterton's Fence)**:
   - If existing requirements are modified, relaxed, or replaced, verify presence and validity of all 4 mandatory fields. Net-new requirements are exempt.
4. **Gate Verdict (`gate-verdict`) + Fused Stage 3 Continuation**:
   - Record verdict via `--as agent2 gate-verdict`.
   - If `PASS`, **immediately proceed in the same turn** to execute Stage 3 (`--as agent1`).
