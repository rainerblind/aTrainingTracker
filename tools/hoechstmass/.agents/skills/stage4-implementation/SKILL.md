---
name: stage4-implementation
description: Executes Stage 4 (Software Construction & Implementation) of the agile ASPICE lifecycle. Verifies Gate 3 sign-off, executes atomic implementation steps, writes production code and targeted unit tests, and maintains documentation integrity.
---

# Skill: stage4-implementation

## Overview
This skill guides the agent through **Stage 4 (Implementation / Software Construction)**. It covers modifying production code and unit tests in strict accordance with the approved Stage 3 Implementation Plan.

## Subagent Model Pairing ("Zipper Principle" — Core Rule 7)
Reuse the persistent subagent pair via messages:
- **Doing (`--as agent1`)**: **`subagent_flash`** (Standard tickets) / **`subagent_pro`** (High-Risk architecture/concurrency tickets) — Executes the Stage 3 blueprint, unit tests, and documentation.
- **Gate 4 Review (`--as agent2`)**: **`subagent_pro`** (Standard) / **`subagent_flash`** (High-Risk) — Audits `git diff` for race conditions, resource leaks, and side effects against the approved Stage 3 plan, followed by executing the gate-verdict command:
  ```bash
  python3 tools/workflow_util.py --as agent2 gate-verdict <SUBTASK_KEY> PASS "<Audit Report>"
  ```

## Key Responsibilities (`agent1`)
1. **Pre-Implementation Gate Verification (Core Rule 4)**:
   - Before modifying any production source files, verify that Gate 3 (Implementation Plan) has passed:
     ```bash
     python3 tools/workflow_util.py check-gate <Impl-Plan-Subtask-Key>
     ```
   - If exit code is not 0 (`GATE_PASSED`), halt immediately.
2. **Software Construction & Code Standards**:
   - Execute the planned atomic steps sequentially.
   - Follow clean architecture and coding standards (`docs/coding_standards.md` and `docs/design_guidelines.md`).
   - Add comprehensive documentation (docstrings/comments) for all new public interfaces and classes.
3. **Targeted Unit Testing**:
   - Run targeted unit tests for modified packages/classes to verify logic rapidly during development. Defer full test suite execution to Stage 5.
4. **Single-Turn Stage Submission (`submit-stage`)**:
   - Submit Stage 4 summary and test results in a single composite CLI call:
     ```bash
     python3 tools/workflow_util.py --as agent1 submit-stage <TICKET_KEY> 4 "<Implementation Summary & Targeted Test Results>"
     ```
   - **Important**: Stage 4 never creates markdown files or folders inside `docs/engineering/`. Keep changes on the working branch until Stage 5.

## Gate 4 Independent Review Checklist (`agent2`)
Audit the `git diff` against this checklist:
1. **Diff Scrutiny**: Inspect `git diff` against the approved Stage 3 implementation plan. Confirm zero unapproved files or unintended modifications.
2. **Side-Effect Audit**: Verify adjacent callers, interfaces, and non-target features were NOT altered or broken.
3. **Acceptance-Criteria Audit**: Verify every acceptance criterion of the parent ticket is met with evidence (file:line or test).
4. **Quality & Compliance Certification**: Verify code cleanliness, error handling, null safety, and coding standards.
5. **Gate Verdict (`gate-verdict`)**:
   - Record verdict via `--as agent2 gate-verdict`.
   - If `PASS`, the parent ticket advances to Stage 5 (`Test`).
