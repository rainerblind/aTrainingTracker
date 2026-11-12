# Stage 2: Requirement & Test Specification - ATT-7025: Fix Multi-Pair Worktree Split-Brain, Local Sprint Rebase & Deterministic --merge-ticket Subcommand

**Ticket**: [ATT-7025](https://rainerblind.atlassian.net/browse/ATT-7025)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.3`  
**Requirement Mapping**: `REQ-PRO-041` (*Parallel Sprint Execution Protocol for Disjoint Epics*)  
**Test Spec ID**: `TST-PRO-041` (*Parallel Sprint Execution Protocol Verification Suite*)  
**Branch**: `feature/ATT-7025`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`)  
**Date**: 2026-11-11  

---

## 1. Requirement Specification (`REQ-PRO-041`)

### 1.1 Problem Statement & Rationale
During parallel sprint execution with multiple agent pairs (`Workspace: "share"`), three failure modes occur: (1) intra-Epic worktree split-brain between `pro` and `flash`, (2) invalid `origin/sprint/<SPRINT_NAME>` upstream fetch on local sprint branches, and (3) git checkout collisions when linked worktrees attempt to check out `sprint/<SPRINT_NAME>` while already active in the main workspace.

### 1.2 Functional & Architectural Requirements
* **REQ-PRO-041.1 (Shared Epic Worktree & Detached HEAD Protocol)**:
  * Persistent subagent pairs (`subagent_pro`, `subagent_flash`) for a parallel lane SHALL operate within a single shared Epic worktree directory on detached `HEAD` (`git checkout --detach sprint/<SPRINT_NAME>`).
  * Named ticket branches SHALL NOT remain checked out across multiple worktrees simultaneously.
* **REQ-PRO-041.2 (Deterministic `--merge-ticket` Under POSIX Flock)**:
  * `tools/parallel_sprint_lock.py` SHALL provide `--merge-ticket <KEY> --sprint <REF> --summary "<SUMMARY>"` running under `/tmp/att_build_merge.lock`.
  * The command SHALL:
    1. Guard `ANT-Android-SDKs` submodule against mode `120000` symlink staging.
    2. Commit uncommitted worktree changes and rebase onto the local sprint branch ref (`git rebase <REF>`).
    3. Soft-reset onto `<REF>` and execute living documentation index recalculation (`verify_requirement_governance.py --sync-indexes`).
    4. Create the single atomic commit (`git commit -m "<KEY>: <Summary>"`).
    5. Fast-forward the main repository worktree (`git -C <MAIN_WORKTREE> merge --ff-only <SHA>`) or update branch ref (`git branch -f <REF> <SHA>`).
    6. Detach sibling worktrees from `feature/<KEY>` and delete `feature/<KEY>` (`git branch -D feature/<KEY>`).
* **REQ-PRO-041.4 (Deterministic Living Docs Recalculation)**:
  * Living documentation master index recalculation SHALL execute within `--merge-ticket` prior to committing.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: `REQ-PRO-041` (`REQ-PRO-041.1`, `REQ-PRO-041.2`, `REQ-PRO-041.4`) in `docs/requirements/req_process_stability.md`.
2. *Historical Origin & Commit Trace*: Introduced in Sprint 2026-45.2 (`ATT-5914`, commit `d002a9bd`), concurrency calibrated to 3 in Sprint 2026-46.1 (`ATT-6743`, commit `3f5925ef`).
3. *Root Reason for Existing Formulation*: Single-lane sprint merge flow assumed single-worktree checkouts and remote tracking branches (`origin/sprint/...`).
4. *Preservation of Core Invariants*: Concurrency ($\le 3$), POSIX flock mutual exclusion, single atomic commit per ticket on `sprint/<SPRINT_NAME>`, zero git branch leaks, and ASPICE human gate governance are strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Atomic Merge Without Branch Collision)**:
  * *Given* a subagent worktree with completed Stage 5 changes for `<KEY>` and the main repository on `sprint/<SPRINT_NAME>`,
  * *When* `parallel_sprint_lock.py --merge-ticket <KEY> --sprint sprint/<SPRINT_NAME> --summary "<KEY>: <Summary>"` is invoked,
  * *Then* changes rebase cleanly onto local `sprint/<SPRINT_NAME>`, living doc indexes synchronize, the main workspace fast-forwards to the new commit, and `feature/<KEY>` is deleted with exit code `0`.
* **Criterion 2 (Multi-Worktree Serialization & Zero Orphan Branches)**:
  * *Given* two concurrent worktrees invoking `--merge-ticket` under `/tmp/att_build_merge.lock`,
  * *When* both commands execute,
  * *Then* commits serialize into a linear 1-commit-per-ticket history on `sprint/<SPRINT_NAME>` with zero branch collision and zero leftover `feature/*` branches.

### 1.5 System Invariants
* Strictly Python 3 standard library (`subprocess`, `fcntl`, `os`, `sys`, `argparse`).
* Zero runtime changes in `app/src/main/`.

---

## 2. Test Specification (`TST-PRO-041`)

### Test Case 1: Multi-Worktree Merge Success (`TST-PRO-041.2`)
* **Scope**: Integration Test (`tools/test_flock_serialization.py`)
* **Preconditions**: Temporary Git repository with `main_repo` on `sprint/test` and a linked worktree `wt1`.
* **Action**: Create commits in `wt1` and invoke `run_merge_ticket("ATT-TEST1", "sprint/test", "ATT-TEST1: test")`.
* **Expected Result**: Exit code 0, `sprint/test` in `main_repo` fast-forwards by exactly 1 atomic commit, `wt1` is on detached `HEAD`, and temporary feature branch is deleted.

### Test Case 2: Concurrent Multi-Worktree Merge Serialization (`TST-PRO-041.2`)
* **Scope**: Integration Test (`tools/test_flock_serialization.py`)
* **Preconditions**: Two linked worktrees (`wt1`, `wt2`) concurrently invoking `--merge-ticket`.
* **Action**: Concurrently execute `run_merge_ticket` under POSIX flock.
* **Expected Result**: Both merges succeed sequentially; `git log --oneline sprint/test` contains exactly two new linear commits; zero merge conflicts; zero remaining `feature/*` branches.

### Test Case 3: Tier 1 Process & Tooling Verification Suite (`TST-PRO-041.6`)
* **Command**: `python3 -m unittest tools/test_flock_serialization.py && python3 tools/verify_requirement_governance.py`
* **Expected Result**: 100% pass across all tests; requirement governance passes with exit code 0.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-041.2` (TC1) | Integration | `parallel_sprint_lock.py --merge-ticket` (single worktree) | `REQ-PRO-041.2`, `REQ-PRO-041.4` | Specified |
| `TST-PRO-041.2` (TC2) | Integration | `parallel_sprint_lock.py --merge-ticket` (concurrent worktrees) | `REQ-PRO-041.1`, `REQ-PRO-041.2` | Specified |
| `TST-PRO-041.6` (TC3) | Tooling Suite | `test_flock_serialization.py` & `verify_requirement_governance.py` | `REQ-PRO-041` | Specified |
