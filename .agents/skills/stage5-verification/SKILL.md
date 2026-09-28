---
name: stage5-verification
description: Executes Stage 5 (Verification, Clean-Room Regression & Walkthrough) of the agile ASPICE lifecycle. Runs the full test suite, performs on-device checks, authors the walkthrough, updates living docs to Verified, and advances the parent ticket to Final Review (Human).
---

# Skill: stage5-verification

## Overview
This skill guides the agent through **Stage 5 (Verification & Release Quality Gate)**. It validates the complete implementation across clean-room regression tests, updates living documents, documents the walkthrough, and transitions the parent ticket to human review.

## Key Responsibilities
1. **Clean-Room Regression Suite**:
   - Execute the entire unit test suite from clean state:
     ```bash
     ./gradlew testDebugUnitTest
     ```
   - Assert 100% pass rate with zero failures.
2. **On-Device / Physical Verification**:
   - For UI/Sensor changes: Deploy debug APK to Pixel 10 (`./gradlew installDebug`) and verify live rendering, navigation, and absence of crash loops.
3. **Walkthrough Documentation**:
   - Author `docs/engineering/walkthroughs/<TICKET_KEY>_walkthrough.md` using `templates/walkthrough_template.md`.
4. **Living Documentation Synchronization**:
   - Update requirement status in `docs/requirements.md` to `Verified`.
   - Update test status in `docs/tests.md` to `Verified`.
5. **Subtask Completion & Parent Handover**:
   - Update Stage 5 Jira subtask (`[Test]`) Description with the walkthrough text.
   - Transition subtask to `In Überprüfung` and run Gate 5 audit (`tools/review_agent.py audit <KEY>`).
   - Upon Gate 5 approval (`RECOMMEND PASS`), transition subtask directly to `Erledigt` via transition `freigabe`.
   - Move parent ticket to `Final Review (Human)`:
     ```bash
     python3 tools/jira_util.py move <PARENT_KEY> "final review"
     ```
   - Note: AI agents are strictly forbidden from transitioning parent tickets to `Erledigt`.

## Deliverable Template
Use the standardized markdown template located at:
`templates/walkthrough_template.md`
