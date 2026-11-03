# Stage 1 Analysis: ATT-5203 - AAA Workflow & jira_util.py: Introduce 1/2/3/5+ Story Point Estimation (customfield_10016), Complexity Split Guardrail & Sprint Capacity Tracking

**Ticket**: [ATT-5203](https://rainerblind.atlassian.net/browse/ATT-5203)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Branch**: `feature/ATT-5203`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03

---

## 1. Problem Statement & Motivation

In the autonomous Agile Agentic ASPICE (AAA) workflow (13–16 tickets/sprint across 5 dual-agent stages), velocity is currently tracked solely as raw ticket count. This conflates 14 trivial 1-SP UI copy changes with 12 multi-layer 3-SP sensor/database features. Furthermore, without an objective complexity guardrail, oversized tickets ($\ge 5\text{ SP}$) risk entering active sprints, causing context window bloat, prolonged agent turns, and gate revisions. In AAA, the critical bottleneck is Human Final Review & On-Device Verification time (~30–45 min for ~24–28 SP). Story Points are needed as a complexity guardrail (mandatory split for $\ge 5\text{ SP}$), sprint capacity predictor, and velocity metric.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Independent Peer Review & Verification**:
  * **Jira Field Schema & Board Binding**: Audited Jira Scrum Board 1 (`SCRUM board`) and issue `editmeta` via REST API: `customfield_10016` (*Story point estimate*, schema `com.pyxis.greenhopper.jira:jsw-story-points`, type `number`) is already globally enabled and active on project `ATT`.
  * **Tooling Deficit in `tools/jira_util.py`**: Inspected `jira_util.py` (1492 lines). Currently, zero functions reference `customfield_10016`. `list_sprint_issues` and `show_issue` ignore estimation data, and there is no command to set points (`set-points`) or pass points on ticket creation (`--points=N`).
  * **Active Sprint Baseline**: Queried active sprint `2026-45.2`: 14 of 16 tickets have `customfield_10016 = None` (only `ATT-5190` = 1.0 SP and `ATT-5203` = 2.0 SP). All sprint tickets must be retrofitted with initial estimates.
  * **Workflow & Skill Gaps**: Neither `sprint-planner/SKILL.md` nor `brainstormer/SKILL.md` mention the 1/2/3/5+ scale, the $\ge 5\text{ SP}$ split trigger, or the ~24–30 SP sprint capacity envelope.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * `tools/jira_util.py`: Add `set-points KEY POINTS` command, `--points=N` flag to `create-issue`, `[X SP]` display & total sprint points calculation in `list`, and `*Story Points*` in `show`.
  * `.agents/skills/sprint-planner/SKILL.md`: Integrate 1/2/3/5+ scale into Ceremony 1 screening, enforce $\ge 5\text{ SP}$ split rule, and track planned vs. completed SP in Ceremony 2 retro.
  * `.agents/skills/brainstormer/SKILL.md`: Document `--points=1|2|3` CLI support and immediate decomposition of $\ge 5\text{ SP}$ ideas.
  * `docs/presentations/agile_agentic_aspice_beamer.tex`: Document the Story Point Complexity Guardrail & Human Capacity model.
  * Living Documentation: Author `REQ-PRO-029` and `TST-PRO-022` in `docs/requirements/req_process_stability.md` and `docs/tests/tst_process_stability.md`.
  * Populate all 16 active sprint `2026-45.2` tickets with initial Story Point estimates via `jira_util.py set-points`.
  * Regression unit test suite: Create `tools/test_jira_story_points.py` asserting point parsing, formatting, and capacity totals.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No Android application code changes in `app/src/...` (pure tooling, process, and documentation ticket).
  * No alterations to Jira board configuration or other custom fields.
  * No automatic ticket splitting during active sprint Phase 2 (splitting occurs during Ceremony 1 Screening and Backlog refinement).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement `REQ-PRO-029` under Epic `ATT-232` (*Process*), refining `REQ-PRO-027` (*AAA Governance*). Zero existing requirements modified.
* **Historical Origin & Commit Trace**: `REQ-PRO-027` introduced in `ATT-3534` (commit `39b3df08`).
* **Root Reason for Existing Formulation**: Early AAA sprints focused on pipeline autonomy and 5-stage gate compliance. Velocity was measured purely by ticket completion count.
* **Preservation of Core Invariants**: The 1/2/3/5+ estimation framework supplements the existing 5-stage ASPICE pipeline without altering any gate contracts, CLI semantics, or human decision sovereignty.

---

## 5. Architectural Strategy & High-Level Solution

1. **`tools/jira_util.py` Story Point Primitives**:
   * Define `STORY_POINTS_FIELD = "customfield_10016"`.
   * Add `set_story_points(issue_key, points, role)` executing `PUT /rest/api/2/issue/{key}` with `float(points)` (or `None`).
   * Extend `list_sprint_issues()` to query `customfield_10016`, format each ticket with `[{points} SP]` or `[No SP]`, and print a summary footer: `Total Sprint Points: XX SP (YY unestimated)`.
   * Extend `show_issue()` to fetch and display `*Story Points*: {points} SP`.
   * Extend `create_issue()` and CLI parser to accept `--points=N` / `--story-points=N`.
2. **Skill & Governance Updates**:
   * Update `sprint-planner/SKILL.md` (Ceremony 1: propose 1/2/3/5+ SP, reject/split $\ge 5\text{ SP}$, check 24–30 SP sprint load; Ceremony 2: planned vs completed points).
   * Update `brainstormer/SKILL.md` (attach `--points`, decompose $\ge 5\text{ SP}$ epics).
   * Update `docs/presentations/agile_agentic_aspice_beamer.tex` with the complexity split guardrail.
3. **Verification**:
   * Unit tests in `tools/test_jira_story_points.py` covering formatting, validation, and CLI commands.
   * Living doc validation via `python3 tools/verify_requirement_governance.py`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. 100% backward compatibility for all existing `jira_util.py` CLI invocations.
  2. Safe handling of `None`/missing fields on subtasks and unestimated tickets (zero `KeyError`/`TypeError`).
  3. Parent ticket Human Decision Gate and Core Governance Rules 1–8 strictly preserved.
* **Risk Rating**: **LOW** — Pure Python tooling, skill documentation, and living doc additions; zero Android runtime impact.
