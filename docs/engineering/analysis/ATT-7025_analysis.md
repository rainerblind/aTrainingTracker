# Stage 1 Analysis: ATT-7025 - Fix Multi-Pair Worktree Split-Brain, Local Sprint Rebase & Deterministic --merge-ticket Subcommand

**Ticket**: [ATT-7025](https://rainerblind.atlassian.net/browse/ATT-7025)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.3`  
**Branch**: `feature/ATT-7025` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
During 3-pair parallel sprint execution (`sprint/2026-46.2`), inspecting `git worktree list`, `git branch -a`, and Stage 5 integration revealed three systemic flaws in the parallel worktree protocol (`REQ-PRO-041`):
1. **Intra-Epic Worktree Split-Brain**: Spawning both `subagent_pro` and `subagent_flash` with `Workspace: "share"` creates two distinct worktrees per Epic. Git rejects checking out the same branch across worktrees (`fatal: '...' is already checked out`), and uncommitted files in one worktree are invisible to the partner agent.
2. **Missing Remote Tracking Branch**: `stage5-verification/SKILL.md` instructs `git rebase origin/sprint/<SPRINT_NAME>`. Because sprint integration branches are local during Phase 2 and not pushed to `origin`, git aborts with `fatal: invalid upstream 'origin/sprint/...'`.
3. **Main Workspace Checkout Collision**: Stage 5 instructs subagents to run `git checkout sprint/<SPRINT_NAME>`. Because the primary repository already checks out `sprint/<SPRINT_NAME>`, Git fails with `fatal: '...' is already used by worktree at '/home/rainer/AndroidStudioProjects/aTrainingTracker'`. Ad-hoc workarounds leave dirty states and dozens of orphaned `feature/*` branches.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification**:
  * **Worktree Split-Brain Verified**: Antigravity provisions an independent git worktree directory for each subagent launched with `Workspace: "share"`. Without detached `HEAD` and shared directory alignment, subagents cannot ping-pong cleanly across stages.
  * **Invalid Upstream Ref Verified**: `git branch -a` confirms `sprint/2026-46.3` exists strictly as a local ref (`refs/heads/sprint/2026-46.3`). Referencing `origin/sprint/...` fails deterministically.
  * **Checkout Collision Verified**: Git worktree semantics strictly forbid two worktrees checking out the same branch simultaneously. Any `git checkout sprint/...` from a linked worktree fails.
  * **Missing Atomic Merge Command Verified**: `parallel_sprint_lock.py` currently only wraps arbitrary shell strings, forcing error-prone, fragile multi-line shell snippets in agent skills that fail to clean up branches or handle merge collisions.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Add `--merge-ticket <KEY> --sprint <REF> --summary "<SUMMARY>"` CLI subcommand to `tools/parallel_sprint_lock.py` and hoechstmass mirror.
  * Under `/tmp/att_build_merge.lock`, execute: submodule symlink guard, commit wip, rebase onto local `sprint/<SPRINT_NAME>`, soft-reset, `--sync-indexes`, single atomic commit, and fast-forward main worktree (`git -C <MAIN_WORKTREE> merge --ff-only <SHA>`).
  * Automate detached HEAD positioning and stale `feature/<KEY>` deletion.
  * Add multi-worktree regression test in `tools/test_flock_serialization.py`.
  * Align `.agents/skills/stage5-verification/SKILL.md`, `.agents/skills/sprint-planner/SKILL.md`, `.agents/rules/aspice_governance.md` (`Rule 9`), and `docs/requirements/req_process_stability.md` (`REQ-PRO-041.2`, `REQ-PRO-041.4`).
* **Out-of-Scope Non-Goals**:
  * Modifying Antigravity platform worktree provisioning internals.
  * Modifying Phase 3 Sprint Review & Retro ceremonies or release management.
  * Modifying any Android application code in `app/src/main/`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-041` (`REQ-PRO-041.1`, `REQ-PRO-041.2`, `REQ-PRO-041.4`) in `docs/requirements/req_process_stability.md`.
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-45.2 (`ATT-5914`, commit `d002a9bd`), calibrated to concurrency=3 in Sprint 2026-46.1 (`ATT-6743`, commit `3f5925ef`).
* **Root Reason for Existing Formulation**: Concurrency was structured assuming worktree isolation and POSIX `flock` serialization, but the Stage 5 git integration commands were written for a single-worktree repo with remote tracking.
* **Preservation of Core Invariants**: Multi-worktree concurrency ($\le 3$), POSIX `flock` mutual exclusion on build/merge, 1-commit-per-ticket history, zero branch collisions, and ASPICE human gate governance remain strictly intact.

---

## 5. Architectural Strategy & High-Level Solution
1. **Detached `HEAD` Protocol for Subagent Pairs**: Document that subagent pairs operate on detached `HEAD` (`git checkout --detach sprint/<SPRINT_NAME>`) in a single Epic worktree directory, eliminating branch checkout collisions and split-brain states.
2. **Deterministic `--merge-ticket` CLI Subcommand**: Implement within `tools/parallel_sprint_lock.py` using standard library `subprocess`, `os`, `sys`, and `fcntl`.
   * Discover main worktree via `git worktree list --porcelain`.
   * Stash/commit working files; verify `ANT-Android-SDKs` is not a symlink (`120000`).
   * Rebase against local `sprint/<SPRINT_NAME>`, soft-reset to squash, execute `python3 tools/verify_requirement_governance.py --sync-indexes`, commit single atomic commit.
   * Fast-forward main worktree checkout via `git -C <MAIN_WORKTREE> merge --ff-only <COMMIT_SHA>` (or `git branch -f` if not checked out).
   * Clean up `feature/<KEY>` branches across worktrees and leave worktree detached at `sprint/<SPRINT_NAME>`.
3. **Automated Verification**: Extend `tools/test_flock_serialization.py` with multi-worktree fixture validating sequential and concurrent merges without branch lock collisions or leftover branches.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Strictly Python 3 standard library in `tools/parallel_sprint_lock.py`.
  2. Single atomic commit per ticket on `sprint/<SPRINT_NAME>`.
  3. Zero stale `feature/*` or `temp_sprint` branches after merge.
  4. Human Decision Gate (`REQ-PRO-016`) and Rule 81 tripwires remain inviolable.
* **Risk Rating**: **LOW**. Process and developer tooling enhancement; zero production runtime code affected; verified via isolated temporary git repository unit tests.
