# Stage 1 Analysis: ATT-8162 - Enforce capacity-bound sprint commitment and establish a V5.0.0 release plan

**Ticket**: [ATT-8162](https://rainerblind.atlassian.net/browse/ATT-8162)  
**Sub-task**: [ATT-8244](https://rainerblind.atlassian.net/browse/ATT-8244) (`[Analysis]`)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.8`  
**Branch**: `feature/ATT-8162` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-15  

---

## 1. Problem Statement & Motivation
Under ASPICE 4.0 `MAN.3` (Project Management, Finding `M10`), sprint commitments were historically overloaded (104–283 SP planned vs 24–30 SP envelope, completion down to 35% in `46.2`), causing mid-sprint aborts, idle PO wait time, and low completion credibility. Furthermore, no formal `V5.0.0` release plan exists, and `jira_util.py` emits contradictory `WARNING: Mandatory field missing!` on in-sprint tickets for `FixVersion`, conflicting with Rule 8.3 (FixVersion set only upon final human acceptance in Ceremony 2).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic Investigation**:
  * `ATT-8030` (Sprint `2026-46.7` close) already introduced the foundational 12-hour cadence (`04:00`/`16:00`), `Vom PO Freigegeben` (ID `447`), `pack-sprint`, `return-unstarted`, and dual-ticket retro (`ATT-8223`).
  * Gaps remaining for `ATT-8162`:
    1. `docs/management/release_plan.md` does not exist (`Finding M10`).
    2. `select_greedy_bin_pack_tickets()` in `jira_util.py` only selects 1 ticket per lane (`len(selected) >= max_lanes`), lacking multi-ticket capacity-bound bin-packing per lane.
    3. `show_issue()` in `jira_util.py` warns `WARNING: Mandatory field missing!` on active tickets lacking FixVersion, violating Rule 8.3.
    4. Release and sprint ceremonies lack documented integration with cumulative Pixel 10 daily-ride verification (`ATT-8141`).
* **Independent Peer Review & Verification**:
  * Verified: Codebase already has 12h cadence primitives from `ATT-8030`. `ATT-8162` completes the release plan, enhances lane bin-packing with capacity bounds, and aligns FixVersion output cleanly.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Author `docs/management/release_plan.md` for `V5.0.0` (scope epics, schedule, Pixel 10 daily-ride verification, and exit criteria).
  2. Enhance `select_greedy_bin_pack_tickets()` in `tools/jira_util.py` with multi-ticket lane-aware capacity bin-packing (`max_sp_per_lane`).
  3. Align `show_issue()` in `tools/jira_util.py` so active in-sprint tickets do not trigger misleading FixVersion warnings.
  4. Align `.agents/rules/aspice_governance.md`, `sprint-planner/SKILL.md`, and `release-manager/SKILL.md`.
  5. Define living documentation `REQ-PRO-097` and `TST-PRO-097` with 100% mathematical parity.
* **Out-of-Scope Non-Goals**:
  1. Modifying Android runtime code in `app/src/main/`.
  2. Altering fixed 12h cutoff times (`04:00` / `16:00`) or pseudo-sprint ID `447`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-PRO-097` refining `REQ-PRO-029` (Story Points) and `REQ-PRO-041` (Multi-Lane).
* **Historical Origin & Commit Trace**: Traced to ASPICE Finding `M10` (Epic `ATT-8138`), PO capacity refinement (`ATT-8030`), and Rule 8.3.
* **Root Reason for Existing Formulation**: Sprints were previously used as unbounded buffers; FixVersion warning in `jira_util.py` was an overly broad check.
* **Preservation of Core Invariants**: Retains Rule 3 human gates, detached worktree isolation, POSIX flock merge lock, and pure Python stdlib.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-PRO-097` in `docs/requirements/req_process_stability.md`.
* **Affected Architecture Elements**: `docs/management/release_plan.md`, `tools/jira_util.py`, `tools/test_jira_accounts.py`, `sprint-planner/SKILL.md`, `release-manager/SKILL.md`, `.agents/rules/aspice_governance.md`.
* **Verification Impact & Test Surface**: Net-new `TST-PRO-097` in `docs/tests/tst_process_stability.md`; unit tests in `tools/test_jira_accounts.py`.
* **Risk Assessment & Invariants**: Low risk. Pure Python tooling and management documentation; zero runtime app code changes.

---

## 6. Architectural Strategy & High-Level Solution
* **V5.0.0 Release Plan**: Formal markdown plan in `docs/management/release_plan.md` covering scope, schedule, Pixel 10 daily-ride verification, and exit criteria.
* **Lane-Aware Multi-Ticket Bin-Packing**: Refine `select_greedy_bin_pack_tickets()` to accumulate tickets per disjoint Epic lane up to `max_sp_per_lane` (or ticket cap).
* **FixVersion Alignment**: In `tools/jira_util.py`, gate FixVersion warning so it applies only to completed (`Erledigt`) parent tickets.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: Zero runtime app changes; Rule 3 human decision gate strictly preserved; pure Python stdlib resilience.
* **Risk Rating**: **LOW** (process management and tooling adjustments).
