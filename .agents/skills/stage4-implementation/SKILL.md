---
name: stage4-implementation
description: Executes Stage 4 (Software Construction & Implementation) of the agile ASPICE lifecycle. Verifies Gate 3 sign-off, executes atomic implementation steps, writes production code and targeted unit tests, and maintains documentation integrity.
---

# Skill: stage4-implementation

## Overview
This skill guides the agent through **Stage 4 (Implementation / Software Construction)**. It covers modifying production Android code (`app/src/main/...`) and unit tests (`app/src/test/...`) in strict accordance with the approved Stage 3 Implementation Plan.

## Subagent Model Pairing ("Zipper Principle" — Core Rule 7)
- **Doing (`agent1`)**: **`flash`** *(Standard)* / **`pro`** *(High-Risk)* (`invoke_subagent` with `Model: "flash"` for standard tickets, or `Model: "pro"` for `HIGH`-risk concurrency/DB/service lifecycle tickets) — Executes the Stage 3 file-and-method blueprint, unit tests, and 9-locale XML updates.
- **Gate 4 Review (`agent2`)**: **`pro`** *(Standard)* / **`flash`** *(High-Risk)* (`invoke_subagent` with `TypeName: "research"`, `Model: "pro"` for standard tickets, or `Model: "flash"` when `pro` authored a High-Risk implementation) — Audits the `git diff` for subtle race conditions, Compose recomposition traps, resource leaks, and side effects (or 1:1 plan fidelity & contract tests when `pro` wrote the code).

## Key Responsibilities (`agent1`)
1. **Pre-Implementation Gate Verification (Core Rule 4 — `REQ-PRO-016`)**:
   - Before modifying any source files in `app/src/...`, verify that the Stage 3 Plan subtask is in status `Erledigt`:
     ```bash
     python3 tools/jira_util.py check-gate <Impl-Plan-Subtask-Key>
     ```
   - If exit code is not 0, halt immediately.
2. **Software Construction**:
   - Execute the planned atomic steps sequentially.
   - Follow clean architecture and Kotlin/Java best practices (`docs/coding_standards.md` and `docs/design_guidelines.md`).
   - Add comprehensive internal documentation (KDoc/JavaDoc) for all new or modified public methods and classes (`REQ-PRO-011`).
   - For string additions: ensure entries exist across all 9 supported language directories (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) with consistent format specifiers.
3. **Targeted Unit Testing**:
   - Run exclusively targeted unit tests for modified packages/classes (5–15s runtime) to verify logic rapidly during development:
     ```bash
     ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.*"
     ```
   - Defer full test suite execution (`./gradlew testDebugUnitTest`) to Stage 5.
4. **Single-Turn Stage Submission (`submit-stage`)**:
   - Submit Stage 4 summary and targeted test results in a single composite CLI call (creates/updates `[Implementation]` sub-task and moves it to `In Überprüfung`):
     ```bash
     python3 tools/jira_util.py --as agent1 submit-stage <TICKET_KEY> 4 "<Implementation Summary & Targeted Test Results>"
     ```
   - **One Commit per Ticket (Core Rule 8)**: Defer committing until Stage 5 verification completes so the entire ticket is committed as a single atomic commit on `sprint/<SPRINT_NAME>`.

## Gate 4 Independent Review Checklist (`agent2` — `pro`/`flash` `research` Subagent)
Spawn an independent `research` subagent (`Model: "pro"` for Standard tickets, `Model: "flash"` for High-Risk tickets authored by `pro`) to audit the `git diff` against this checklist:
1. **Diff Scrutiny**: Inspect `git diff` against the approved Stage 3 implementation plan. Confirm zero unapproved files or unintended modifications.
2. **Side-Effect Audit**: Verify adjacent callers, interfaces, and non-target metrics/features were NOT altered or broken.
3. **Quality & Compliance**:
   - Verify class/method headers comply with self-documenting standards (KDoc/JavaDoc).
   - Verify 9-language localization parity across all user-facing strings (`EN, DE, ES, FR, IT, JA, NL, PL, PT`).
4. **Gate Verdict (`gate-verdict`)**:
   - Record the verdict in a single composite CLI call (which closes the sub-task to `Erledigt`, advances the parent ticket to `Test`, and automatically injects `stage5-verification/SKILL.md` into stdout):
     ```bash
     python3 tools/jira_util.py --as agent2 gate-verdict <IMPL_SUBTASK_KEY> PASS "<Audit Report>"
     ```
     *(Or `REVISION` if changes are required.)*
