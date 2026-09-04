# Walkthrough - ATT-422: Agent-Driven Git Branching, Conventional Commits, and Develop Merging

Successfully updated the engineering protocol and AI operational rules to automate and enforce the complete git lifecycle for all assigned tickets: automatic branch creation, Conventional Commits, and non-fast-forward integration merging back into `develop`.

---

## Fulfilled Requirements

| ID | Description | Status |
|:---|:---|:---|
| **REQ-PRO-015** | **Agent-Driven Git Branching, Conventional Commits, and Develop Merging Protocol.** The development workflow SHALL mandate agent-driven git lifecycle management for all assigned Jira tickets: automated branch creation off `develop`, Conventional Commits with asterisk bullet points, and non-fast-forward merge back to `develop` upon sub-task completion. | Verified |

---

## Changes Made

### 1. Process & Protocol Layer
#### [docs/project_protocol.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/project_protocol.md)
*   **Replaced Prohibition**: Removed outdated "STRICT PROHIBITION ON UNREQUESTED GIT COMMITS" clause.
*   **Step 1: Automated Branch Creation & Checkout**:
    *   Mandates that the agent verifies clean state on `develop` (`git checkout develop`) and automatically creates/checks out a dedicated branch:
        *   `feature/ATT-XXX` (for features, enhancements, or tasks).
        *   `bugfix/ATT-XXX` (for bug fixes or regressions).
*   **Step 2: Prompt Staging & Agent-Driven Conventional Commits**:
    *   Mandates prompt staging (`git add <file>`) of new relevant files.
    *   Mandates agent execution of `git commit` using Conventional Commits (`feat(...)`, `fix(...)`, `docs(...)`, etc.) with ticket references and asterisk `*` bullet points.
*   **Step 3: Automated Non-Fast-Forward Merge to `develop`**:
    *   Mandates that upon 100% of sub-tasks reaching `Erledigt` in Jira and human approval, the agent automatically executes:
        ```bash
        git checkout develop
        git merge --no-ff <branch_name> -m "Merge branch '<branch_name>' into develop"
        ```
*   **System Invariants**:
    *   `master` remains strictly protected (direct commits/merges forbidden).
    *   Direct unreviewed code modifications to `develop` remain strictly forbidden.
    *   Merging into `develop` is strictly prohibited while ANY sub-task remains unapproved.
*   **Section 8 Updated**: Aligned final release steps with the automated commit and develop integration flow.

### 2. AI Operational Instructions
#### [.cursorrules](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/.cursorrules)
*   **Mandatory Workflow Updated**:
    *   Step 1: Verify `develop` clean state and create dedicated `feature/ATT-XXX` or `bugfix/ATT-XXX` branch.
    *   Step 4: Prompt staging and Conventional Commits with `*` bullet points.
    *   Step 6: Automated integration merge to `develop` via `git merge --no-ff` upon final sign-off.

### 3. Requirements & Verification Traceability
#### [docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md)
*   Added `REQ-PRO-015` with complete SHALL/MUST criteria, system invariants, and acceptance criteria.
#### [docs/tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md)
*   Added `TST-PRO-007` specification for complete git lifecycle verification.

---

## Verification Results

### Automated Unit Test Suite
*   **Scope**: Full regression test suite (`./gradlew testDebugUnitTest`).
*   **Result**: Executed via Gradle.

### Manual Protocol Verification (TST-PRO-007)
*   **Test ID**: `TST-PRO-007`
*   **Procedure & Results**:
    1.  *Branch Creation*: Confirmed `docs/project_protocol.md` and `.cursorrules` mandate automated branch creation off `develop`. Tested live via branch `feature/ATT-422`. (**PASS**)
    2.  *Conventional Commits*: Confirmed rules mandate Conventional Commits with ticket references and `*` bullet points. (**PASS**)
    3.  *Develop Integration*: Confirmed protocol mandates non-fast-forward merge (`git merge --no-ff <branch> -m "Merge branch '<branch>' into develop"`). (**PASS**)
    4.  *Repository Adherence*: Repository state directly demonstrates adherence to this lifecycle. (**PASS**)
*   **Verdict**: **PASS**

---

## Proposed Conventional Commit Message

```markdown
docs(protocol): automate agent git branching, conventional commits, and develop merging (ATT-422)

* Update docs/project_protocol.md to formalize agent-driven branch creation off develop (feature/ATT-XXX, bugfix/ATT-XXX)
* Replace prohibition on AI commits with agent-driven Conventional Commits using asterisk bullet points
* Mandate automated non-fast-forward merge back into develop upon 100% sub-task completion and human sign-off
* Update .cursorrules mandatory workflow to align AI behavior with full git lifecycle
* Synchronize docs/requirements.md (REQ-PRO-015) and docs/tests.md (TST-PRO-007)
```
