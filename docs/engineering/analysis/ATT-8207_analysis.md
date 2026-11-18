# Stage 1 Analysis: ATT-8207 - Preserve Single-Atomic-Commit Invariant Across Stage 6 Cold Review Bounces

**Document ID**: `DOC-ENG-ANALYSIS-ATT-8207`  
**Version**: `1.0.0`  
**Status**: `Draft`  
**Approver**: `Pending PO Sign-Off`  
**Ticket**: [ATT-8207](https://rainerblind.atlassian.net/browse/ATT-8207) (`2 SP`) | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-17_03_Nacht` | **Branch**: `feature/ATT-8207` | **Date**: `2026-11-18`

---

## 1. Problem Statement & Motivation
* `Rule 8.2` (`.agents/rules/aspice_governance.md:76`) and `docs/management/cm_plan.md:59` mandate **strictly one squashed atomic commit** per ticket on `sprint/<SPRINT_NAME>`.
* Currently, Gate 5 `PASS` (`.agents/skills/stage5-verification/SKILL.md:14,66-79`) executes `--merge-ticket` (`tools/parallel_sprint_lock.py:226-389`), squash-merging and deleting `feature/<KEY>` *before* Stage 6 Cold Review (`Rule 3.3`). Every Stage 6 `BOUNCE` pollutes `sprint/<SPRINT_NAME>` with unverified commits (`ATT-7991`: 3 commits `c422e1b6`, `6e9b3c06`, `533d88b1`; `ATT-7902`: 3 commits `43846f1a`, `84695b32`, `f565c688`), breaking `tools/sprint_bisect.py` and duplicating `[Implementation]`/`[Test]` subtasks.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Cause 1 — Premature Integration at Gate 5**: `stage5-verification/SKILL.md:14,76-78` and `sprint-planner/SKILL.md:48,113` squash `feature/<KEY>` onto `sprint/<SPRINT_NAME>` and delete `feature/<KEY>` at Gate 5 `PASS`, while `submit_stage(..., 5)` (`tools/jira_util.py:1418-1419`) already flips living docs to `[Verified]`.
* **Cause 2 — Multi-Lane Interleaving Breaks Post-Merge Amending**: Git log (`c422e1b6..533d88b1`) confirms parallel lanes interleave commits between bounces (`ATT-7918` `60ec5c3b` landed between `ATT-7991` commits `c422e1b6` and `6e9b3c06`; 8 commits landed before `533d88b1`). Once buried behind sibling commits: (a) `git commit --amend` cannot edit a non-`HEAD` commit; (b) `rebase -i`/`--autosquash` rewrites shared sprint history and breaks concurrent worktrees; (c) `git revert` + re-commit leaves 3 commits (`Rule 8.2` violation); and (d) a `05:00/15:00/22:00` `Schicht` rollover (`Rule 2.2`) merges unapproved `C1` into `develop`.
* **Cause 3 — Subtask Duplication on Bounce (`tools/jira_util.py:1514,2338-2360`)**: `submit_stage` skips subtasks in `("erledigt", "done", "closed", "resolved")`, and `final_review_verdict(..., BOUNCE)` transitions only the parent ticket—leaving Stage 4/5 subtasks `Erledigt`. Healing then calls `create_subtask(...)`, duplicating `[Implementation]`/`[Test]` pairs and skewing `REQ-PRO-107.1` lookup (`tools/jira_util.py:1442-1464`).
* **Independent Peer Review & Verification**: Ticket hypothesis verified against `tools/parallel_sprint_lock.py:226-389`, `tools/jira_util.py:1403-1539,2257-2368`, and `tools/hoechstmass/` mirrors (`tools/hoechstmass/tools/jira_util.py:1006-1224`, `tools/hoechstmass/tools/parallel_sprint_lock.py`). Blind spots uncovered: (a) `Rule 3.3` forbids `--merge-ticket` lock acquisition *during* the read-only Stage 6 audit, so Cold Review must audit `git diff sprint/<SPRINT_NAME>...feature/<KEY>` read-only and trigger `--merge-ticket` strictly post-`PASS`; (b) `tools/parallel_sprint_lock.py:300-317` must resolve shared `docs/requirements.md` / `docs/tests.md` index updates cleanly if `sprint/<SPRINT_NAME>` advanced while `feature/<KEY>` waited in Stage 6.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**: Keep `feature/<KEY>` intact at Gate 5 `PASS`; Stage 6 read-only audit via `git diff sprint/<SPRINT_NAME>...feature/<KEY>`; execute atomic `--merge-ticket` onto `sprint/<SPRINT_NAME>` strictly on Stage 6 `PASS`; reopen existing Stage 4/5 subtasks on `BOUNCE` in `tools/jira_util.py`; align `aspice_governance.md`, `stage5-verification/SKILL.md`, `stage6-final-review/SKILL.md`, `docs/management/cm_plan.md`, `tools/hoechstmass/` mirrors, and contract/unit tests.
* **Out-of-Scope Non-Goals**: Runtime code in `app/src/main/`, `tools/sprint_bisect.py` algorithm rewrites, or mutating historical commits.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-058` (`REQ-PRO-058.1-.3`), `REQ-PRO-041` (`.2, .4`), `REQ-PRO-109` (`.2, .5`) in `docs/requirements/req_process_stability.md`; `.agents/rules/aspice_governance.md` (`Rule 3.3`, `Rule 8.2`), `docs/management/cm_plan.md`.
* **Historical Origin & Commit Trace**: `ATT-7101` (`REQ-PRO-058`), `ATT-7025` (`49936dce`, `--merge-ticket` under `flock`), `ATT-8030` (Zero-Build/Zero-Lock Stage 6), `ATT-8154` (`REQ-PRO-109`), `ATT-8163` (`c43cd960`, `cm_plan.md` v1.1.0).
* **Root Reason for Existing Formulation**: Gate 5 originally terminated the AI flow; `ATT-7101` appended Stage 6 after the Gate 5 squash-merge, exposing `sprint/<SPRINT_NAME>` to bounce pollution and rollover leaks.
* **Preservation of Core Invariants**: Preserve `Rule 3.1` (human-only `Erledigt`), `Rule 3.3` (zero `./gradlew` and zero `flock` during Stage 6 audit; lock only post-`PASS` for `--merge-ticket`), `Rule 8.2` (one atomic squashed commit with `REQ-PRO-108.3` trailers), `Rule 9.1-.5` (disjoint worktrees & `--sync-indexes`), `REQ-PRO-107.1` authorship separation, and `HoechstmassToolingContractTest` / `AspiceSweAuditContractTest` parity.

---

## Change Impact Analysis
* **Affected Requirements**: Refines `REQ-PRO-058` (`REQ-PRO-058.1-.3`), `REQ-PRO-041` (`.4`), and `REQ-PRO-109` (`.2, .5`) in `docs/requirements/req_process_stability.md` and `docs/tests/tst_process_stability.md`.
* **Affected Architecture Elements**: `.agents/rules/aspice_governance.md` (`Rule 3.3`, `Rule 8.2`), `.agents/skills/{stage5-verification,stage6-final-review,sprint-planner}/SKILL.md`, `docs/management/cm_plan.md`, `tools/jira_util.py`, `tools/parallel_sprint_lock.py`, `tools/locked_build_merge.py`, and `tools/hoechstmass/` mirrors.
* **Verification Impact & Test Surface**: `tools/test_jira_accounts.py`, `tools/test_flock_serialization.py`, `tools/hoechstmass/tools/test_hoechstmass_tools.py`, `verify_requirement_governance.py`, and Tier 2 `AspiceSweAuditContractTest` / `HoechstmassToolingContractTest`.
* **Risk Assessment & Invariants**: `MEDIUM` (`2 SP`; process/tooling-critical). Preserves read-only Stage 6 audit, POSIX `flock` serialization, `REQ-PRO-107.1` role separation, and `Hoechstmass` project-agnostic contracts.

---

## 6. Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-18 | AI Agent 1 (Implementer) | `ATT-8207` | Initial Stage 1 forensic & Chesterton's Fence analysis. |
