# Stage 3: Implementation Plan - ATT-7025: Fix Multi-Pair Worktree Split-Brain, Local Sprint Rebase & Deterministic --merge-ticket Subcommand

**Ticket**: [ATT-7025](https://rainerblind.atlassian.net/browse/ATT-7025)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.3`  
**Requirement Mapping**: `REQ-PRO-041` (`REQ-PRO-041.1`, `REQ-PRO-041.2`, `REQ-PRO-041.4`)  
**Test Mapping**: `TST-PRO-041` (`TST-PRO-041.2`, `TST-PRO-041.6`) | **Branch**: `feature/ATT-7025`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Description & Background
During parallel sprint execution with multiple agent pairs, worktree split-brain, remote tracking fetch failures on local sprint branches, and branch checkout collisions during Stage 5 integration disrupt execution. This plan establishes the single-worktree detached `HEAD` protocol for agent pairs and implements the deterministic `--merge-ticket` subcommand in `tools/parallel_sprint_lock.py` under POSIX `flock`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-041` (`041.1` Shared Epic Worktree, `041.2` POSIX Flock `--merge-ticket`, `041.4` Rebase-then-Recalculate)
* **Test Mapping**: `TST-PRO-041` (`TST-PRO-041.2` Unit/Integration Suite, `TST-PRO-041.6` Clean-Room Governance)

---

## 3. System Invariants & Preserved Behavior
1. **Python 3 Standard Library Only**: No external pip packages (`subprocess`, `fcntl`, `os`, `sys`, `argparse`).
2. **Atomic 1-Commit-per-Ticket**: Rebase + soft reset guarantees strictly one squashed commit per ticket on `sprint/<SPRINT_NAME>`.
3. **Branch Cleanliness**: Sibling worktrees detached; zero orphaned `feature/*` or `temp_sprint` branches after merge.
4. **Human Decision Gate**: Parent ticket transitions to terminal states remain reserved for human review.

---

## 4. Proposed Architectural Changes

### Component 1: Deterministic `--merge-ticket` Subcommand (`parallel_sprint_lock.py`)
* Location: `tools/parallel_sprint_lock.py` and mirror `tools/hoechstmass/tools/parallel_sprint_lock.py`
* Implementation:
  - Add `--merge-ticket <KEY>`, `--sprint <REF>`, and `--summary "<SUMMARY>"` CLI args.
  - Discover `main_worktree` and `current_worktree` via `git worktree list --porcelain`.
  - Guard `ANT-Android-SDKs` submodule against `120000` symlink staging.
  - Detach `HEAD`, rebase onto local sprint branch ref, soft-reset to squash all ticket changes.
  - Recalculate living docs via `verify_requirement_governance.py --sync-indexes`, commit single atomic commit.
  - Fast-forward main repository (`git -C <MAIN_WORKTREE> merge --ff-only <SHA>`) or update branch ref (`git branch -f`).
  - Detach sibling worktrees from `feature/<KEY>` and delete `feature/<KEY>`.

### Component 2: Multi-Worktree Integration Test Suite (`test_flock_serialization.py`)
* Location: `tools/test_flock_serialization.py`
* Implementation: Automated unit tests creating multi-worktree git fixtures; verifies single-worktree merge, concurrent multi-worktree serialization under flock, fast-forward propagation, and stale branch deletion.

### Component 3: Skill & Governance Documentation Alignment
* Files: `.agents/skills/stage5-verification/SKILL.md`, `.agents/skills/sprint-planner/SKILL.md`, `.agents/rules/aspice_governance.md`, `docs/requirements/req_process_stability.md`.
* Codify the detached `HEAD` pair protocol and update Stage 5 commands to use `--merge-ticket`.

### UI Consistency (Rule 23)
* No UI changes (pure developer tooling, testing, and process documentation).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Implement `--merge-ticket` in `tools/parallel_sprint_lock.py` & Hoechstmass Mirror
* Files: `tools/parallel_sprint_lock.py`, `tools/hoechstmass/tools/parallel_sprint_lock.py`

### Step 2: Implement Multi-Worktree Tests in `tools/test_flock_serialization.py`
* File: `tools/test_flock_serialization.py`

### Step 3: Run Targeted Tooling Unit Tests
* Command: `python3 -m unittest tools/test_flock_serialization.py`

### Step 4: Update Process Skills, Rules & Living Documentation
* Files: `.agents/skills/stage5-verification/SKILL.md`, `.agents/skills/sprint-planner/SKILL.md`, `.agents/rules/aspice_governance.md`, `docs/requirements/req_process_stability.md`

### Step 5: Clean-Room Governance & Verification Execution
* Command: `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Run `python3 -m unittest tools/test_flock_serialization.py` and `python3 tools/verify_requirement_governance.py` ensuring 100% pass rate.
* **Rollback**: Branch isolation (`feature/ATT-7025`) allows instant reset (`git reset --hard`) without repository side effects.
