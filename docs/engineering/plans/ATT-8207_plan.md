# Stage 3 Implementation Plan: ATT-8207 - Preserve Single-Atomic-Commit Invariant Across Stage 6 Cold Review Bounces

**Document ID**: `DOC-ENG-PLAN-ATT-8207`  
**Version**: `1.0.1`  
**Status**: `Draft`  
**Approver**: `Pending PO Sign-Off`  
**Ticket**: [ATT-8207](https://rainerblind.atlassian.net/browse/ATT-8207) (`2 SP`) | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-17_03_Nacht` | **Branch**: `feature/ATT-8207`  
**Requirement Mapping**: `REQ-PRO-125` (`REQ-PRO-125.1`–`.4`) | **Test Mapping**: `TST-PRO-125` (`TST-PRO-125.1`–`.4`) | **Date**: `2026-11-18`

---

## 1. Problem & Traceability Summary
* **Goal**: Defer Strategy A squash-merge onto `sprint/<SPRINT_NAME>` from Gate 5 `PASS` to post-Stage-6 `final-review-verdict PASS`, keep Stage 6 Cold Review strictly read-only (`git diff sprint/<SPRINT_NAME>...feature/<KEY>`), and handle Stage 6 `BOUNCE` subtasks cleanly without crashing `Erledigt` workflows.
* **Traceability**: `REQ-PRO-125.1` $\leftrightarrow$ `TST-PRO-125.1`, `.2`; `REQ-PRO-125.2` $\leftrightarrow$ `TST-PRO-125.1`, `.2`; `REQ-PRO-125.3` $\leftrightarrow$ `TST-PRO-125.3`; `REQ-PRO-125.4` $\leftrightarrow$ `TST-PRO-125.4`.

---

## 2. System Invariants & Preserved Behavior
1. **Stage 6 Read-Only Zero-Build / Zero-Lock Audit (`Rule 3.3`)**: Zero `./gradlew` and zero `/tmp/att_build_merge.lock` calls during Cold Review; `--merge-ticket` acquires `flock` strictly *after* `final-review-verdict PASS`.
2. **Human Gate & Authorship Separation**: `Rule 3.1` (human-only `Erledigt`) and `REQ-PRO-107.1` (Gate `N` reviewer $\ne$ Stage `N+1` author) preserved.
3. **Work-Product Header & Mirror Decoupling (`REQ-PRO-120`, `HoechstmassToolingContractTest`)**: Bump `**Version**` + `Change History` on modified governed docs; keep `tools/hoechstmass/` free of `ATT` / `com.atrainingtracker` / proprietary IDs.
4. **Zero Runtime Impact**: Zero `app/src/main/` edits, zero `ANT-Android-SDKs` edits, zero `tools/sprint_bisect.py` edits; `Localization Parity: N/A - no UI strings`.

---

## 3. Proposed Architectural Changes
1. **Cross-Worktree `--merge-ticket` & Clean Index Resolution (`tools/parallel_sprint_lock.py`, `tools/locked_build_merge.py`, `tools/hoechstmass/tools/parallel_sprint_lock.py`)**:
   * When `--merge-ticket <KEY> --sprint <SPRINT_REF>` is invoked from an unrelated worktree (not on `feature/<KEY>` and not holding direct `sprint_ref` edits), fail fast if `current_wt` is dirty—never run blind `git add -A` in an unrelated worktree; locate `feature/<KEY>`'s clean worktree/branch, detach cleanly, checkout `--detach feature/<KEY>`, and rebase onto `sprint_ref`.
   * During `git rebase sprint_ref`, if conflicts occur *exclusively* in `docs/requirements.md` and/or `docs/tests.md`, resolve via `git checkout --ours -- <conflicted_index_files>` + explicit `git add <conflicted_index_files>` + `GIT_EDITOR=true git rebase --continue`; abort on any other conflict. After `--sync-indexes`, stage only updated `docs/requirements.md` / `docs/tests.md` explicitly (never blind `git add -A`), commit with `REQ-PRO-108.3` trailers, fast-forward `sprint_ref`, and delete `feature/<KEY>`.
2. **Jira `Erledigt` Workflow Fallback & Subtask Reuse (`tools/jira_util.py`, `tools/sprint_metrics.py`, `tools/hoechstmass/tools/{jira_util.py,sprint_metrics.py}`)**:
   * In `final_review_verdict(..., "BOUNCE")` (`bounce_count <= 3`): query `subtasks` on `parent_key`; reopen target/downstream subtasks to `in_progress`/`todo` *only* when Jira exposes a matching outgoing transition (skip gracefully when `Erledigt` has `transitions: []`).
   * In `submit_stage`: (a) reuse an existing open/active subtask (`Zu erledigen`/`In Arbeit`/`In Überprüfung`) or a reopenable closed subtask (transitioning `in_progress -> in_review`); (b) when all matching subtasks are locked in `Erledigt` (`transitions: []`), create a numbered bounce subtask (`[<Prefix> - Bounce N]`, e.g. `[Implementation - Bounce 1]`, `[Test - Bounce 1]`) so the subtask traverses `In Arbeit -> In Überprüfung -> Erledigt` cleanly and `gate_verdict REVISION` never crashes.
   * Update `STAGE_SPECS` patterns, `resolve_desired_parent_stage_for_subtask`, `gate_verdict` (`[test...]` checks), and `sprint_metrics.identify_gate_number` to match `[<Stage> - Bounce N]`, and update `REQ-PRO-107.1` lookup (`tools/jira_util.py:1442-1464`) to inspect the active/latest matching preceding subtask rather than breaking on the first closed one.
3. **Governance Rules, Skills & CM Plan Alignment (`REQ-PRO-125.1`, `.2`, `.4`)**:
   * Update `.agents/rules/aspice_governance.md` (`Rule 3.3`, `Rule 8.2`, `Version` `1.0.0` $\rightarrow$ `1.0.1`), `docs/management/cm_plan.md` (`§3`, `§4`, `Version` `1.2.0` $\rightarrow$ `1.2.1`), `.agents/skills/{stage5-verification,stage6-final-review,sprint-planner}/SKILL.md`, and `tools/hoechstmass/.agents/{rules,skills}/` mirrors (Stage 5 keeps `feature/<KEY>` intact; Stage 6 audits `git diff sprint/<SPRINT>...feature/<KEY>` read-only; post-`PASS` runs `--merge-ticket`; preserve all `AspiceSweAuditContractTest` & `HoechstmassCoreFrameworkContractTest` markers).

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (`REQ-PRO-125.1`, `.2`)**: Update `_merge_ticket_impl` in `tools/parallel_sprint_lock.py` and `tools/hoechstmass/tools/parallel_sprint_lock.py` (clean cross-worktree `feature/<KEY>` guard + explicit `--ours` / `--sync-indexes` / explicit `git add` for `docs/requirements.md` and `docs/tests.md`).
2. **Step 2 (`REQ-PRO-125.3`)**: Update `final_review_verdict`, `submit_stage`, `STAGE_SPECS`, `resolve_desired_parent_stage_for_subtask`, `gate_verdict`, `REQ-PRO-107.1` latest-subtask lookup, and `sprint_metrics.identify_gate_number` across `tools/{jira_util,sprint_metrics}.py` and `tools/hoechstmass/tools/{jira_util,sprint_metrics}.py`.
3. **Step 3 (`REQ-PRO-125.1`, `.2`, `.4`)**: Update `.agents/rules/aspice_governance.md`, `.agents/skills/{stage5-verification,stage6-final-review,sprint-planner}/SKILL.md`, `docs/management/cm_plan.md`, and `tools/hoechstmass/.agents/` mirrors with version bumps & Change History.
4. **Step 4 (`TST-PRO-125.1`, `.3`, `.4`)**: Add unit tests in `tools/test_flock_serialization.py`, `tools/test_jira_accounts.py`, and `tools/hoechstmass/tools/test_hoechstmass_tools.py` covering post-`PASS` `--merge-ticket`, cross-worktree dirty guard, index conflict `--sync-indexes`, reopenable vs. locked-`Erledigt` bounce subtasks, latest-subtask `REQ-PRO-107.1` check, and mirror parity.

---

## 5. Verification & Rollback Plan
* **Verification Commands**: `python3 -m unittest discover -s tools -p "test_*.py"`, `python3 -m unittest discover -s tools/hoechstmass/tools -p "test_*.py"`, `python3 tools/verify_requirement_governance.py --check-doc-headers --check-commit-hygiene --check-conflict-markers`, `python3 tools/verify_requirement_governance.py`, and targeted `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest" --tests "com.atrainingtracker.trainingtracker.build.HoechstmassToolingContractTest" --tests "com.atrainingtracker.trainingtracker.build.HoechstmassCoreFrameworkContractTest"`.
* **Rollback**: Isolated on `feature/ATT-8207`; `sprint/2026-11-17_03_Nacht` stays untouched until Stage 6 `PASS`.

---

## 6. Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-18 | AI Agent 1 (Implementer) | `ATT-8207` | Initial Stage 3 implementation plan (`REQ-PRO-125` / `TST-PRO-125`). |
| `1.0.1` | 2026-11-18 | AI Agent 1 (Implementer) | `ATT-8207` | Addressed Gate 3 REVISION (`ATT-8788`): locked-`Erledigt` `[<Stage> - Bounce N]` fallback, latest-subtask `REQ-PRO-107.1` lookup, and clean cross-worktree `--merge-ticket` staging. |
