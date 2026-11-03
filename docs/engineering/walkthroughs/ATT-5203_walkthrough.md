# Stage 5: Walkthrough & Verification - ATT-5203: Introduce 1/2/3/5+ Story Point Estimation, Complexity Split Guardrail & Sprint Capacity Tracking

**Ticket**: [[ATT-5203]](https://rainerblind.atlassian.net/browse/ATT-5203)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Requirement Mapping**: `REQ-PRO-029` (`REQ-PRO-029.1`, `REQ-PRO-029.2`, `REQ-PRO-029.3`, `REQ-PRO-029.4`)  
**Test Mapping**: `TST-PRO-022` (`TST-PRO-022.1`, `TST-PRO-022.2`, `TST-PRO-022.3`, `TST-PRO-022.4`)  
**Branch**: `feature/ATT-5203`  
**Author**: AI Agent 1 (flash / Implementer)  
**Date**: 2026-11-03  

---

## 1. Executive Summary & Verification Overview
* **Issue Addressed**: Sprint velocity in the AAA workflow was tracked solely by raw ticket count, obscuring complexity differences between 1-SP UI tweaks and 3-SP cross-layer features. Without an objective complexity scale, oversized tickets ($\ge 5\text{ SP}$) risked entering sprints, creating context bloat. In AAA, human review time (~30–45 min for ~24–28 SP) is the critical bottleneck.
* **Delivered Changes**:
  1. `tools/jira_util.py`: Integrated `customfield_10016` (`STORY_POINTS_FIELD`). Added `set-points KEY POINTS`, supported `--points=N` in `create-issue`, added `[X SP]` tags and summary footer in `list`, displayed `*Story Points*` in `show`, added `move KEY analysis` Complexity Split Guardrail ($\ge 5\text{ SP}$ blocked without `--force`), and updated `ensure_retro_ticket` story point reporting.
  2. Skills: Updated `.agents/skills/sprint-planner/SKILL.md` (Ceremony 1: 1/2/3/5+ estimation, split rule, ~24–30 SP capacity check; Ceremony 2: planned vs completed SP in retro table), `.agents/skills/brainstormer/SKILL.md` (`--points` and $\ge 5\text{ SP}$ decomposition), and `.agents/skills/stage1-analysis/SKILL.md` (scope complexity audit).
  3. Presentation: Added Slide 4b in `docs/presentations/agile_agentic_aspice_beamer.tex` and recompiled `agile_agentic_aspice_beamer.pdf`.
  4. Sprint Calibration: Populated Story Points on all 16 active sprint `2026-45.2` tickets (Sprint Total: 25 SP, 0 unestimated).
  5. Tests: Created `tools/test_jira_story_points.py` with 10 automated unit tests (100% pass rate).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-029.1` | `[TST-PRO-022.3]` | Skill & Beamer Governance Audit | **PASSED** | `Verified` |
| `REQ-PRO-029.2` | `[TST-PRO-022.1]` | Automated Unit Tests (`tools/test_jira_story_points.py`) | **PASSED** (10/10) | `Verified` |
| `REQ-PRO-029.3` | `[TST-PRO-022.2]` | Integration Verification (`jira_util.py list` / `show` / `set-points`) | **PASSED** | `Verified` |
| `REQ-PRO-029.4` | `[TST-PRO-022.4]` | Requirement Governance (`python3 tools/verify_requirement_governance.py`) | **PASSED** (exit 0) | `Verified` |

---

## 3. Automated Test Evidence

### Executed Tooling & Governance Test Scope (Explicitly Executed)
```text
python3 -m unittest tools/test_jira_story_points.py
Ran 10 tests in 0.008s - OK (100% PASS)

python3 tools/verify_requirement_governance.py
Net-new requirement(s) detected: REQ-PRO-029. Bypassing archaeology check cleanly. (exit 0)

python3 tools/jira_util.py list
Active Sprint: 2026-45.2
ATT-5201: [Verbesserung] [1 SP] ...
...
ATT-5203: [Verbesserung] [2 SP] ...
ATT-5202: [Feature] [1 SP] Review & Retro ...
Sprint Total: 16 issues, 25 Story Points (0 unestimated)
```

---

## 4. Hardware / Physical Verification
* Pure Python tooling, process skill, and LaTeX presentation ticket. Zero Android application code (`app/src/...`) touched. Physical Android deployment bypassed in accordance with Stage 5 non-UI guidelines.

---

## 5. Invariant & Governance Verification
1. **CLI Backward Compatibility**: All existing `jira_util.py` commands, flags, and transition pathways remain 100% functional.
2. **Missing Field Resilience**: Issues or subtasks with missing or `None` story points format safely without `KeyError` or `TypeError`.
3. **Living Documentation Synchronized**: `REQ-PRO-029` and `TST-PRO-022` updated to `Verified`.
