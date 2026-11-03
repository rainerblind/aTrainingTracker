# Stage 2: Requirement & Test Specification - ATT-5203: AAA Workflow & jira_util.py: Introduce 1/2/3/5+ Story Point Estimation (customfield_10016), Complexity Split Guardrail & Sprint Capacity Tracking

**Ticket**: [ATT-5203](https://rainerblind.atlassian.net/browse/ATT-5203)  
**Sub-task**: Pending (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Requirement Mapping**: `REQ-PRO-029` (*AAA 1/2/3/5+ Story Point Estimation, Complexity Split Guardrail & Sprint Capacity Tracking*)  
**Test Spec ID**: `TST-PRO-022`  
**Branch**: `feature/ATT-5203`  
**Author**: AI Agent 1 (flash / Implementer)  
**Date**: 2026-11-03  

---

## 1. Requirement Specification (REQ-PRO-029)

### 1.1 Problem Statement & Rationale
Autonomous AAA development operates at 13–16 tickets/sprint across 5 dual-agent stages. Currently, sprint velocity is tracked solely by raw ticket count, obscuring differences between 1-SP UI tweaks and 3-SP cross-layer sensor features. Furthermore, oversized tickets ($\ge 5\text{ SP}$) risk entering active sprints without a formal guardrail. In AAA, human review time (~30–45 min for ~24–28 SP) is the critical bottleneck. A discrete 1/2/3/5+ Story Point scale (`customfield_10016`) provides an automated complexity split guardrail, sprint capacity predictor, and empirical velocity metric.

### 1.2 Functional & Architectural Requirements
1. **Story Point Scale & Mandatory Split Guardrail (`REQ-PRO-029.1`)**: Adopt discrete scale: 1 SP (Trivial/Isolated), 2 SP (Standard Atomic), 3 SP (Complex/Cross-Layer), and $\ge 5\text{ SP}$ (Too Large / Multi-Concern). Any ticket estimated at $\ge 5\text{ SP}$ MUST be split into atomic child tickets (1–3 SP) before sprint entry.
2. **Jira REST API & Custom Field Integration (`REQ-PRO-029.2`)**: In `tools/jira_util.py`, bind `customfield_10016`. Provide `set-points KEY <N>` command to set points (`float`) or clear (`None`). Support optional `--points=<N>` and `--story-points=<N>` flags in `create-issue`.
3. **Sprint Capacity & Issue Display (`REQ-PRO-029.3`)**: Extend `jira_util.py show <KEY>` to display `*Story Points*: <N> SP`. Extend `jira_util.py list` to format issues with `[<N> SP]` or `[No SP]` and display summary `Total Sprint Story Points: <X> SP (<Y> unestimated)`.
4. **Ceremonies, Skills & Beamer Governance (`REQ-PRO-029.4`)**: Update `.agents/skills/sprint-planner/SKILL.md` (Ceremony 1: propose SP, reject $\ge 5\text{ SP}$, verify ~24–30 SP sprint load; Ceremony 2: planned vs completed SP in retro table), `.agents/skills/brainstormer/SKILL.md` (initial `--points`, decompose $\ge 5\text{ SP}$), and `docs/presentations/agile_agentic_aspice_beamer.tex` (complexity guardrail slide).

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-PRO-029` under Epic `ATT-232` (*Process*), refining `REQ-PRO-027` (*AAA Governance*).
* **Historical Origin & Commit Trace**: `REQ-PRO-027` introduced in `ATT-3534` (commit `39b3df08`).
* **Root Reason for Existing Formulation**: Early AAA sprints focused on pipeline autonomy and 5-stage gate compliance. Velocity was tracked purely by ticket completion count.
* **Preservation of Core Invariants**: 8 Core Rules, Persistent Zipper Principle, Human Decision Gate, and 100% backward compatibility of existing `jira_util.py` commands remain strictly preserved. Zero Android app runtime changes.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Setting Points)**: *Given* an issue key, *When* running `python3 tools/jira_util.py set-points <KEY> 2`, *Then* `customfield_10016` is set to `2.0` in Jira and shown via `show`.
* **Criterion 2 (Creating with Points)**: *Given* `create-issue ... --points=3`, *When* executed, *Then* the created issue has `customfield_10016 = 3.0`.
* **Criterion 3 (List & Show Visibility)**: *Given* active sprint `2026-45.2`, *When* running `jira_util.py list`, *Then* tickets display `[X SP]` tags and the total sprint SP summary is output.
* **Criterion 4 (Split Guardrail)**: *Given* Ceremony 1 screening, *When* a candidate ticket is evaluated at $\ge 5\text{ SP}$, *Then* `sprint-planner` mandates decomposing it into 1–3 SP tickets before sprint start.

---

## 2. Test Specification (TST-PRO-022)

* **TST-PRO-022.1 (Jira CLI Story Point Unit Tests)**: In `tools/test_jira_story_points.py`, test `set_story_points` payload construction, `--points` argument parsing in `create-issue`, `list_sprint_issues` aggregation math (handling `None`, floats, and unestimated counts), and `show_issue` SP presentation.
* **TST-PRO-022.2 (Live Jira REST API Integration)**: Verify `set-points ATT-5203 2` and `show ATT-5203` reflect `2.0 SP`. Verify all active sprint tickets are populated with initial estimates and `list` computes total sprint SP.
* **TST-PRO-022.3 (Skill & Presentation Governance Audit)**: Verify `.agents/skills/sprint-planner/SKILL.md`, `.agents/skills/brainstormer/SKILL.md`, and `docs/presentations/agile_agentic_aspice_beamer.tex` document the 1/2/3/5+ scale, split guardrail, and capacity metrics.
* **TST-PRO-022.4 (Requirement Governance Check)**: Execute `python3 tools/verify_requirement_governance.py` verifying exit code 0.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-PRO-022.1]` | Unit | `test_jira_story_points.py` | `REQ-PRO-029.2`, `REQ-PRO-029.3` | Specified |
| `[TST-PRO-022.2]` | Integration | `jira_util.py set-points`, `list`, `show` | `REQ-PRO-029.2`, `REQ-PRO-029.3` | Specified |
| `[TST-PRO-022.3]` | Governance | `sprint-planner`, `brainstormer`, `beamer.tex` | `REQ-PRO-029.1`, `REQ-PRO-029.4` | Specified |
| `[TST-PRO-022.4]` | Governance | `tools/verify_requirement_governance.py` | `REQ-PRO-029` | Specified |
