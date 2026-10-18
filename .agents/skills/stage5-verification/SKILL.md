---
name: stage5-verification
description: Executes Stage 5 (Verification, Clean-Room Regression & Walkthrough) of the agile ASPICE lifecycle. Runs the full test suite, performs on-device checks, authors the walkthrough, updates living docs to Verified, integrates verified changes into the sprint branch (Strategy A), and advances the parent ticket to Final Review (Human).
---

# Skill: stage5-verification

## Overview
This skill guides the agent through **Stage 5 (Verification & Release Quality Gate)**. It validates the complete implementation across clean-room regression tests, updates living documents, documents the walkthrough, merges the verified feature branch into the sprint integration branch (`sprint/<sprint_id>`), and transitions the parent ticket to human review.

## Key Responsibilities
1. **Clean-Room Regression Suite**:
   - Execute the entire unit test suite from clean state:
     ```bash
     ./gradlew testDebugUnitTest
     ```
   - Assert 100% pass rate with zero failures.
2. **On-Device / Physical Verification**:
   - For UI/Sensor changes: Deploy debug APK to Pixel 10 (`./gradlew installDebug`) and verify live rendering, navigation, and absence of crash loops.
   - **Visual Consistency Check (Rule 23)**: For UI changes, capture on-device screenshots (`adb exec-out screencap -p > docs/engineering/walkthroughs/img/<KEY>_<name>.png`) of the new/changed UI and of the reference screen named in the plan's `UI Consistency` section. Embed them side by side in the walkthrough (Light and Dark/AMOLED where relevant) and compare against `docs/design_guidelines.md` Section 5. Fix visible mismatches (shapes, spacing, colors, tab/button styles, branding) before handing over to `Final Review (Human)`.
3. **Walkthrough Documentation**:
   - Author `docs/engineering/walkthroughs/<TICKET_KEY>_walkthrough.md` using `templates/walkthrough_template.md`.
4. **Living Documentation Synchronization**:
   - Update requirement status in `docs/requirements.md` to `Verified`.
   - Update test status in `docs/tests.md` to `Verified`.
5. **Subtask Completion & Parent Handover (Rule 3 / Rule 45)**:
   - Update Stage 5 Jira subtask (`[Test]`) Description with the walkthrough text.
   - Transition subtask to `In Überprüfung` and run Gate 5 audit (`tools/review_agent.py audit <KEY>`).
   - Upon Gate 5 approval (`RECOMMEND PASS`), transition subtask directly to `Erledigt` via transition `freigabe`:
     ```bash
     python3 tools/jira_util.py move <TEST_SUBTASK_KEY> freigabe
     ```
     This automatically transitions the parent ticket to `Final Review (Human)` and assigns it to `human`.
   - Note: AI agents are strictly forbidden from transitioning parent tickets to `Erledigt`.
6. **Single Atomic Commit per Ticket & Continuous Sprint Branch Integration (Strategy A, Rule 44)**:
   - Each ticket MUST result in **strictly one atomic commit** on `sprint/<SPRINT_NAME>` containing the complete Stage 1–5 deliverables (`analysis`, `test_spec`, `plan`, `walkthrough`), living documentation (`docs/requirements.md`, `docs/tests.md`), production code, and unit tests:
     ```bash
     # Commit on feature/<KEY> (or squash all commits from feature/<KEY> onto sprint/<SPRINT_NAME>):
     git add -A
     git commit -m "feat(<KEY>): <Concise Ticket Summary>"
     git checkout sprint/<SPRINT_NAME>
     git merge --squash feature/<KEY>
     git commit -m "feat(<KEY>): <Concise Ticket Summary>"
     git branch -D feature/<KEY>
     ```
   - This guarantees one clean atomic commit per ticket in `git log`, zero merge conflicts between tickets, and continuously integrated living docs.

## Deliverable Template
Use the standardized markdown template located at:
`templates/walkthrough_template.md`
