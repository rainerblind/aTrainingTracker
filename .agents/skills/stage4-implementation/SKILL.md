---
name: stage4-implementation
description: Executes Stage 4 (Software Construction & Implementation) of the agile ASPICE lifecycle. Verifies Gate 3 sign-off, executes atomic implementation steps, writes production code and targeted unit tests, and maintains documentation integrity.
---

# Skill: stage4-implementation

## Overview
This skill guides the agent through **Stage 4 (Implementation / Software Construction)**. It covers modifying production Android code (`app/src/main/...`) and unit tests (`app/src/test/...`) in strict accordance with the approved Stage 3 Implementation Plan.

## Key Responsibilities
1. **Pre-Implementation Gate Verification (`REQ-PRO-016`)**:
   - Before modifying any source files in `app/src/...`, verify that the Stage 3 Plan subtask is in status `Erledigt`:
     ```bash
     python3 tools/jira_util.py check-gate <Impl-Plan-Subtask-Key>
     ```
   - If exit code is not 0, halt immediately.
2. **Software Construction**:
   - Execute the planned atomic steps sequentially.
   - Follow clean architecture and Kotlin/Java best practices.
   - Add comprehensive internal documentation (KDoc/JavaDoc) for all new or modified public methods and classes (`REQ-PRO-011`).
   - For string additions: ensure entries exist across all 9 supported language directories (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) with consistent format specifiers.
3. **Targeted Unit Testing**:
   - Run exclusively targeted unit tests for modified packages/classes (5–15s runtime) to verify logic rapidly during development:
     ```bash
     ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.*"
     ```
   - Defer full test suite execution (`./gradlew testDebugUnitTest`) to Stage 5.
4. **Subtask Completion**:
   - Populate the Stage 4 Jira subtask (`[Implementation]`) Description with a concise summary of changes and targeted test results.
   - Transition subtask to `In Überprüfung` and run Gate 4 audit (`tools/review_agent.py audit <KEY>`).
   - Upon Gate 4 approval (`RECOMMEND PASS`), transition subtask directly to `Erledigt` via transition `freigabe`.
