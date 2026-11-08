# Stage 1 Analysis: ATT-5911 - tools/jira_util.py & Skills: Add 'list-epics' / 'close-epic' CLI support and empower agents to create & close Epics after human confirmation

**Ticket**: [ATT-5911](https://rainerblind.atlassian.net/browse/ATT-5911)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Process*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Branch**: `feature/ATT-5911`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
As the project evolves, two classes of Epics have emerged without formalized lifecycle tooling:
1. **Evergreen Epics** (Navigation-Drawer destinations like `ATT-66` Maps, `ATT-68` Workouts, and Cross-Cutting areas like `ATT-355` UI, `ATT-232` Process) that remain open permanently.
2. **Temporary Milestone Epics** (`ATT-1286` Radar, `ATT-2565` Climbs, `ATT-3539` HRV) created for multi-sprint initiatives that conclude once all child tickets are completed.
Currently, `tools/jira_util.py` has no command to inspect Epics and their open/done child counts (`list-epics`), and its blanket Human Gate guard in `transition_issue()` blocks closing completed Milestone Epics even when 0 open child tickets remain and the human user explicitly approves. Furthermore, agent skills (`brainstormer`, `sprint-planner`, `jira-workflow`) lack documentation of the Canonical Epic Taxonomy and Epic closure governance.

---

## 2. Root Cause Analysis & Independent Peer Review
* **Architectural Gap**:
  - `tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py`: No `list-epics` or `close-epic` subcommands. Line 832 unconditionally aborts when transitioning any non-subtask to `Erledigt`, offering no safe path for verified Epic closure.
* **Independent Peer Review & Verification (4-Augen-Prinzip)**:
  - Confirmed `issuetype = Epic ORDER BY key ASC` fetches all 41 project Epics across Cloud and Data Center.
  - Audited child ticket resolution: Cloud uses `parent = "<EPIC_KEY>"`; Data Center uses `"Epic Link" = "<EPIC_KEY>"` / `cf[10014]` or `parent`. Both can be queried efficiently to verify 0 open child tickets (`status != Erledigt`).
  - Confirmed Epic transition to `Erledigt` uses transition ID `41` (`isGlobal: True`).
  - Established triple guardrail for `close-epic`: (1) Role check (`coordinator` or `human` only), (2) Type check (`issuetype == Epic`), and (3) Completeness check (0 open child tickets). Standard parent tickets (`Feature`, `Bug`, `Verbesserung`) remain strictly blocked by the Human Gate.

---

## 3. Scope Grounding & Non-Goals (ATT-1250)
* **In-Scope Goals**:
  - Add `list-epics` to `tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py`, listing Epics with open/done child ticket counts.
  - Add `close-epic <EPIC_KEY>` (restricted to `--as coordinator` or `human`), verifying issue type is Epic, verifying 0 open child tickets exist, and transitioning to `Erledigt`.
  - Update `.agents/skills/brainstormer/SKILL.md`, `.agents/skills/sprint-planner/SKILL.md`, and `.agents/skills/jira-workflow/SKILL.md` (and their `tools/hoechstmass` counterparts) with the Canonical Epic Taxonomy and safe creation/closure workflows.
  - Author automated unit tests in `tools/test_jira_epics.py` and `tools/hoechstmass/tools/test_hoechstmass_tools.py`.
* **Out-of-Scope Non-Goals**: Permitting agents to close standard parent tickets (`Feature`, `Bug`, `Verbesserung`) or modifying Android production code in `app/src/main/`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Refined Requirements**: Extends `REQ-PRO-016` (*Human Decision Gate*), `REQ-PRO-024` (*Modular Skills*), and `REQ-PRO-036` (*Parameterized Tooling*). Defines `REQ-PRO-040`.
* **Historical Trace**: `ATT-3323` (commit `6925d864` - Inviolable Human Decision Gates), `ATT-5830` (Data Center Support).
* **Root Reason for Existing Formulation**: `REQ-PRO-016` blocked non-subtask completion to prevent agents from closing in-sprint parent tickets before Ceremony 2 Joint Review. It did not distinguish between sprint child tickets and overarching Milestone Epics.
* **Preservation of Core Invariants**: Standard sprint tickets remain 100% protected by the Human Gate; `close-epic` requires explicit human consultation, coordinator/human role, and 0 open child tickets.

---

## 5. System Invariants & Risk Assessment
* **Core Invariants**: Human Decision Gate on standard tickets inviolable; Evergreen Epics remain open; `tools/jira_util.py` and `tools/hoechstmass/tools/jira_util.py` remain 100% synchronized (Rule 36 DRY); zero Android runtime changes.
* **Risk Rating**: **LOW** (External Python CLI enhancements and markdown skill documentation).
