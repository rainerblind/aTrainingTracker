# Implementation Plan - ATT-422: Agent-Driven Git Branching, Conventional Commits, and Develop Merging

## Problem Statement
Currently, `docs/project_protocol.md` contains an outdated restriction prohibiting AI agents from executing `git commit` without explicit chat prompts, does not explicitly mandate that the agent creates and checks out the dedicated ticket branch at ticket start, and does not formalize the agent-driven merge of the branch back into `develop` upon completion. The user has requested updating the protocol and AI rules so that the AI agent automatically manages the full git lifecycle: creating the ticket branch from `develop`, staging and committing changes with Conventional Commit messages, and merging the branch back into `develop` with a standardized merge commit message upon completion.

## User Review Required

> [!IMPORTANT]
> **Complete Git Lifecycle Automation**:
> The agent is authorized and mandated to:
> 1. Automatically create and check out `feature/ATT-XXX` or `bugfix/ATT-XXX` from `develop` upon starting work on a ticket.
> 2. Automatically stage (`git add`) and commit (`git commit`) changes with Conventional Commits (`feat(...)`, `fix(...)`, `docs(...)`, etc.) and asterisk `*` bullet points.
> 3. Automatically check out `develop` and execute a non-fast-forward merge (`git merge --no-ff <branch> -m "Merge branch '<branch>' into develop"`) once 100% of sub-tasks are verified as `Erledigt` in Jira and final release approval is granted.

> [!NOTE]
> **Invariants Maintained**:
> - `master` branch remains strictly protected (direct commits/merges to `master` by agents remain forbidden).
> - Code MUST NOT be merged into `develop` if sub-tasks are incomplete or unverified.
> - Human Decision Gates in Jira (`Erledigt`) remain inviolable.

---

## Requirement & Test Mapping

| Requirement ID | Description | Component(s) | Test ID | Jira Sub-task |
|:---|:---|:---|:---|:---|
| **REQ-PRO-015** | **Agent-Driven Git Branching, Conventional Commits, and Develop Merging Protocol.** The development workflow SHALL mandate agent-driven git lifecycle management for all assigned Jira tickets: automated branch creation off `develop`, Conventional Commits with asterisk bullet points, and non-fast-forward merge back to `develop` upon sub-task completion. | `docs/project_protocol.md`, `.cursorrules` | **TST-PRO-007** | `ATT-624` (Test Gate), Current Plan |

---

## Proposed Changes

### Component: `process` (`docs/`)

#### [MODIFY] [project_protocol.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/project_protocol.md)
1. **Update Section 4 (Git Branching Strategy & Lifecycle)**:
   - Formulate the mandatory agent-driven git lifecycle:
     - **Step 1: Automatic Branch Creation**: Upon ticket assignment, agent verifies clean working directory on `develop` and creates/checks out `feature/ATT-XXX` or `bugfix/ATT-XXX` directly branching off `develop`.
     - **Step 2: Prompt Tracking & Agent-Driven Commits**: Replace outdated prohibition on unrequested commits with agent-driven Conventional Commits:
       - Agent stages files (`git add`) promptly when created/edited.
       - Agent executes `git commit` using Conventional Commits format (`feat(...)`, `fix(...)`, `docs(...)`, etc.) with ticket key reference in header/body and `*` for bullet points.
     - **Step 3: Automated Merge to `develop`**: Upon completion of all sub-tasks (`Erledigt`) and human release approval, the agent automatically switches to `develop` and executes:
       `git merge --no-ff <branch_name> -m "Merge branch '<branch_name>' into develop"`
2. **Update Section 8 (Final Documentation & Release - SWE.6)**:
   - Detail the closing git integration sequence:
     - Commit any remaining documentation / walkthrough updates on the ticket branch.
     - Switch to `develop` (`git checkout develop`).
     - Execute non-fast-forward merge (`git merge --no-ff <branch_name> -m "Merge branch '<branch_name>' into develop"`).
     - Verify clean working tree (`git status`).

---

### Component: `ai_rules` (`.cursorrules`)

#### [MODIFY] [.cursorrules](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/.cursorrules)
1. **Update `Mandatory Workflow`**:
   - Add explicit steps for agent git actions:
     - 1. Checkout `develop` and create dedicated branch `feature/ATT-XXX` or `bugfix/ATT-XXX`.
     - 2. Stage new/modified files (`git add`) and execute Conventional Commits with `*` bullet points.
     - 3. Upon 100% sub-task completion (`Erledigt`) and user release approval, checkout `develop` and merge:
       `git merge --no-ff <branch_name> -m "Merge branch '<branch_name>' into develop"`.

---

## Verification Plan

### Automated Tests
* Run full project unit test suite to guarantee zero regression:
  ```bash
  ./gradlew testDebugUnitTest
  ```

### Manual Verification
1. **Audit Protocol Text**: Verify `docs/project_protocol.md` and `.cursorrules` unambiguously specify:
   - Automated branch creation (`feature/ATT-XXX`, `bugfix/ATT-XXX`) off `develop`.
   - Conventional Commit requirements with asterisk `*` bullet points.
   - Non-fast-forward merge back into `develop` with standard merge comment.
2. **Live Execution Test**: Confirm that the active ticket (ATT-422) itself follows this exact lifecycle:
   - Branch `feature/ATT-422` was created off `develop`.
   - Stage 1 and Stage 2 commits adhere to Conventional Commits.
   - Final release merge integrates `feature/ATT-422` into `develop` with `Merge branch 'feature/ATT-422' into develop`.
