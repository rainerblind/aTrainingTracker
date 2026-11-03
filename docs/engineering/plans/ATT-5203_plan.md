# Stage 3: Implementation Plan - ATT-5203: Introduce 1/2/3/5+ Story Point Estimation, Complexity Split Guardrail & Sprint Capacity Tracking

**Ticket**: [ATT-5203](https://rainerblind.atlassian.net/browse/ATT-5203)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Requirement Mapping**: `REQ-PRO-029` (`REQ-PRO-029.1`–`REQ-PRO-029.4`)  
**Test Mapping**: `TST-PRO-022` (`TST-PRO-022.1`–`TST-PRO-022.4`)  
**Branch**: `feature/ATT-5203`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-11-03  

---

## 1. Problem Description & Background

In the autonomous AAA pipeline (13–16 tickets/sprint across 5 dual-agent stages), velocity is currently tracked solely by raw ticket count, obscuring differences between 1-SP UI tweaks and 3-SP cross-layer features. Without an objective complexity scale, oversized tickets ($\ge 5\text{ SP}$) risk entering sprints. Story points (`customfield_10016`) provide an automated complexity split guardrail, sprint capacity predictor (~24–30 SP for ~30–45 min human review), and empirical velocity tracking.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-PRO-029` (*AAA 1/2/3/5+ Story Point Estimation, Complexity Split Guardrail & Sprint Capacity Tracking*).
* **Test Mapping**: `TST-PRO-022` (`TST-PRO-022.1`–`TST-PRO-022.4` in `tools/test_jira_story_points.py`, CLI integration, skill audits, and governance checks).

---

## 3. System Invariants & Preserved Behavior

1. **Jira CLI Backward Compatibility**: All existing commands, switches, and transition workflows in `tools/jira_util.py` remain 100% backward-compatible.
2. **Missing Field Resilience**: Missing or null `customfield_10016` on tickets or subtasks must never raise `KeyError` or `TypeError`.
3. **Core Governance & Human Decision Sovereignty**: Parent ticket human gate, 8 Core Rules, and 5-stage dual-agent verification remain inviolate.
4. **Zero Runtime Impact**: Zero modifications to Android application code (`app/src/...`).

---

## 4. Proposed Architectural Changes

### Component 1: `tools/jira_util.py` REST & CLI Integration (`REQ-PRO-029.2`, `REQ-PRO-029.3`)
* **Constants**: Bind `STORY_POINTS_FIELD = "customfield_10016"`.
* **Command `set-points KEY POINTS`**: Implement `set_story_points(issue_key, points, role)` executing `PUT /rest/api/2/issue/{key}` with `float(points)` (or `None` when points is `none`/`clear`). Validate non-negative numbers.
* **`create_issue` Extension**: Accept `points=None`; add `--points=N` and `--story-points=N` flags to CLI parser.
* **`show_issue` Extension**: Add `customfield_10016` to queried fields; display `*Story Points*: {N} SP` (or `None`).
* **`list_sprint_issues` Extension**: Add `customfield_10016` to query; format each issue with `[{N} SP]` or `[No SP]`; calculate and print summary footer `Sprint Total: {count} issues, {total_sp} Story Points ({unestimated} unestimated)`.
* **CLI Routing & Help**: Register `set-points` in command dispatcher and update `USAGE_TEXT`.

### Component 2: Skills & Beamer Governance (`REQ-PRO-029.1`, `REQ-PRO-029.4`)
* **`sprint-planner/SKILL.md`**: Document 1/2/3/5+ scale in Ceremony 1 screening; mandate split of any ticket $\ge 5\text{ SP}$; track sprint capacity (~24–30 SP); add planned vs. completed SP in Ceremony 2 retro.
* **`brainstormer/SKILL.md`**: Document `--points=1|2|3` in ticket creation; decompose $\ge 5\text{ SP}$ concepts.
* **`docs/presentations/agile_agentic_aspice_beamer.tex`**: Document complexity split guardrail & human capacity bottleneck.

### Component 3: Calibration & Unit Testing (`TST-PRO-022.1`, `TST-PRO-022.2`)
* **`tools/test_jira_story_points.py`**: Unit tests for point parsing, payload building, list aggregation, and CLI commands.
* **Active Sprint Calibration**: Populate initial Story Point estimates on all active sprint `2026-45.2` tickets.

### UI Consistency (Rule 23)
* No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

* **Step 1: Implement Story Point Support in `tools/jira_util.py` (`REQ-PRO-029.2`, `REQ-PRO-029.3`)**: Add `STORY_POINTS_FIELD`, `set_story_points`, `--points` support in `create_issue`, and SP formatting/aggregation in `show_issue` and `list_sprint_issues`.
* **Step 2: Create Automated Unit Test Suite `tools/test_jira_story_points.py` (`TST-PRO-022.1`)**: Test `set_story_points`, `--points` parsing, `list_sprint_issues` math (nulls, floats, totals), and `show_issue`.
* **Step 3: Update Agile Skills & Beamer Presentation (`REQ-PRO-029.1`, `REQ-PRO-029.4`)**: Update `.agents/skills/sprint-planner/SKILL.md`, `.agents/skills/brainstormer/SKILL.md`, and `docs/presentations/agile_agentic_aspice_beamer.tex`.
* **Step 4: Active Sprint Calibration & Live CLI Integration (`TST-PRO-022.2`)**: Run `jira_util.py set-points` across all tickets in active sprint `2026-45.2` and verify `jira_util.py list` output.
* **Step 5: Verification & Governance Audit (`TST-PRO-022.4`)**: Run `python3 tools/test_jira_story_points.py` and `python3 tools/verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan

* **Verification**: Execute `python3 -m unittest tools/test_jira_story_points.py`, test live `jira_util.py list` and `show ATT-5203`, and run `python3 tools/verify_requirement_governance.py`.
* **Rollback**: Branch isolation on `feature/ATT-5203` allows clean revert via `git checkout -- .` if anomalies occur.
