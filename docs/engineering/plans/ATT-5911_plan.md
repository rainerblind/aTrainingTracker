# Stage 3 Implementation Plan: ATT-5911 - tools/jira_util.py & Skills: Add 'list-epics' / 'close-epic' CLI support and empower agents to create & close Epics after human confirmation

**Ticket**: [ATT-5911](https://rainerblind.atlassian.net/browse/ATT-5911)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Requirement Mapping**: `REQ-PRO-040` (`040.1`–`040.4`)  
**Test Spec Mapping**: `TST-PRO-040` (`040.1`–`040.4`)  
**Branch**: `feature/ATT-5911`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Technical Architecture & SWE.2 Design
* **Decomposition & Boundaries**:
  - `tools/jira_util.py` & `tools/hoechstmass/tools/jira_util.py`: Implement `list_epics()` and `close_epic(epic_key)`.
  - `transition_issue()`: Support `allow_epic_close=True` so `close_epic()` can transition verified Epics to `Erledigt` while strictly preserving the Human Decision Gate guard against AI agents closing standard parent tickets (`Feature`, `Bug`, `Verbesserung`).
  - Cross-Deployment Compatibility: Query child issues via `parent in (...)` in Jira Cloud and `"Epic Link" in (...)` / `cf[10014]` / `parent in (...)` in Jira Data Center.
  - Agent Skills: Codify the Canonical Epic Taxonomy across `brainstormer`, `sprint-planner`, and `jira-workflow` (in `.agents/skills/` and `tools/hoechstmass/.agents/skills/`).
* **Triple Guardrail Invariant (`REQ-PRO-040.2`)**: `close-epic` strictly verifies:
  1. *Role*: `--as coordinator` or `human` only (rejects `agent1`, `agent2`).
  2. *Issue Type*: Target must be an `Epic` (`id == 10001` or name `Epic`).
  3. *Completeness*: Target Epic must have exactly 0 open child tickets (`status != Erledigt`).

---

## 2. Atomic Implementation Steps

| Step | Component | Target Files | Action & Invariants | Traceability |
| :--- | :--- | :--- | :--- | :--- |
| **1** | Core Tooling | `tools/jira_util.py`<br>`tools/hoechstmass/tools/jira_util.py` | Implement `list_epics()` and `close_epic()` with triple guardrail and `allow_epic_close` in `transition_issue()`. | `REQ-PRO-040.1`<br>`REQ-PRO-040.2`<br>`TST-PRO-040.1-2` |
| **2** | Brainstormer Skill | `.agents/skills/brainstormer/SKILL.md`<br>`tools/hoechstmass/.../brainstormer/SKILL.md` | Add Canonical Epic Taxonomy table (Evergreen vs. Milestone) and guided Epic creation workflow (`create-issue ... Epic --bottom`). | `REQ-PRO-040.3`<br>`TST-PRO-040.4` |
| **3** | Sprint Planner Skill | `.agents/skills/sprint-planner/SKILL.md`<br>`tools/hoechstmass/.../sprint-planner/SKILL.md` | Add Ceremony 2 Milestone Epic review: checking `list-epics` and human-confirmed closure via `close-epic`. | `REQ-PRO-040.3`<br>`TST-PRO-040.4` |
| **4** | Jira Workflow Skill | `.agents/skills/jira-workflow/SKILL.md`<br>`tools/hoechstmass/.../jira-workflow/SKILL.md` | Document `list-epics` and `close-epic` commands, role restrictions, and guardrail rules. | `REQ-PRO-040.3`<br>`TST-PRO-040.4` |
| **5** | Unit Tests | `tools/test_jira_epics.py`<br>`tools/hoechstmass/tools/test_hoechstmass_tools.py` | Author unit tests covering list-epics aggregation, role rejection, non-Epic rejection, open child abort, and successful closure. | `REQ-PRO-040.4`<br>`TST-PRO-040.1-3` |
| **6** | Verification | Test & Governance | Execute `python3 -m unittest tools/test_jira_epics.py` and `python3 tools/verify_requirement_governance.py`. | `REQ-PRO-040.4`<br>`TST-PRO-040.4` |

---

## 3. System Invariants & Rollback Safety
* **Code Lock (Rule 4)**: No production code in `app/src/main/` touched.
* **Human Gate Integrity (`REQ-PRO-016`)**: Standard parent tickets remain 100% blocked from `Erledigt` transition by agents.
* **Tooling Parity (Rule 36 DRY)**: Root `tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py` remain synchronized.
* **Risk Rating**: **LOW** (Python CLI automation and markdown skill documentation).
