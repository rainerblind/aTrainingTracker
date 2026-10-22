# Sprint Review & Retrospective — Sprint 2026-42.9 (`ATT-4044`)

**Date**: 2026-10-18  
**Sprint**: `2026-42.9` (`sprint/2026-42.9`, Jira Sprint ID `377`)  
**Retrospective Ticket**: [ATT-4044](https://rainerblind.atlassian.net/browse/ATT-4044)  
**Target Release**: `V4.9.40`

---

## 1. Sprint Review Summary

### 1.1 Reviewed & Accepted Tickets (`FixVersion: V4.9.40`)
1. **ATT-3980**: *[Highest] BLE scan callback accesses SensorData objects without synchronization* — **Accepted** (`V4.9.40`)
2. **ATT-3981**: *[Highest] TrackerService drops 1 Hz workout samples and crashes after database recreate* — **Accepted** (`V4.9.40`)
3. **ATT-3984**: *[High] AutoPauseManager pauses active ride on GPS signal loss (tunnel)* — **Accepted** (`V4.9.40`, *Follow-up: `ATT-4094`*)
4. **ATT-3985**: *[High] Newly paired sensors never join an active workout until tracking is restarted* — **Accepted** (`V4.9.40`)
5. **ATT-3986**: *[High] Stopping a workout does not mark it finished if mTrackingInterrupted is set* — **Accepted** (`V4.9.40`)
6. **ATT-3987**: *[High] RESUME_BY_USER resets live in-memory workout session to zero* — **Accepted** (`V4.9.40`, *Follow-up: `ATT-4095`*)
7. **ATT-3988**: *[High] Service teardown NPE in BANALService, MySensorManager and FilterManager* — **Accepted** (`V4.9.40`, *Follow-up: `ATT-4096`*)
8. **ATT-4000**: *[High] Cockpit tab navigation stops working after page-count timeout* — **Accepted** (`V4.9.40`, *Follow-up: `ATT-4097`*)
9. **ATT-4006**: *[Medium] TrackerService onDestroy blocks main thread and leaks scheduler thread* — **Accepted** (`V4.9.40`, *Follow-ups: `ATT-4099`, `ATT-4098`*)

### 1.2 Remaining Sprint Tickets Carried Forward in `Analysis` (Early Sprint Close Requested by User)
- **ATT-4007**: *[Medium] Pause duration undercounted across multiple pauses and pause flags not volatile*
- **ATT-4010**: *[Medium] Formatter.collectAndFormat returns wrong unit for speed/pace and altitude*
- **ATT-4043**: *Clean up WorkoutMasksAndCardsSection: remove redundant Details column, collapse Summary list by default, and split Zone Display Mode into Summary vs Details*
- **ATT-4042**: *Single-line Power row and clearer visual separation between domain rows in WorkoutDetails*
- **ATT-4049**: *[High] Equipment & Sensor Matrix: Do not collapse/expand all sport tabs simultaneously*

### 1.3 Follow-Up Tickets Created at Top of Backlog
- **ATT-4099**: *[High] Keep BANALService bound or snapshot BANAL state until async endWorkout() finalization completes in TrackerService*
- **ATT-4098**: *[Medium] Protect async workout finalization with foreground service lifecycle or expedited WorkManager job*
- **ATT-4097**: *[Medium] Buffer one-off navigation events via Channel in TrackingTabsViewModel and ControlTrackingViewModel across STOPPED lifecycle*
- **ATT-4096**: *[Medium] Eliminate potential MySensorManager and ProxySensor lock-order inversion by dispatching initial newValue callbacks outside locks* (Epic `ATT-431`)
- **ATT-4095**: *[Medium] Reuse in-memory LiveWorkoutSession on RESUME_BY_USER and publish session only after rehydration*
- **ATT-4094**: *[Medium] Distinguish speed sensor standstill from in-motion signal loss (tunnel/dropout) in AutoPauseManager based on last valid speed*

---

## 2. Key Retrospective Learnings & Root Cause Analysis

### Learning 1: Single Atomic Commit per Ticket ("One Commit per Ticket", Rule 44)
- **Observation**: Creating separate intermediate git commits for Stages 1, 2, 3, 4, and 5 on `feature/<KEY>` plus a `--no-ff` merge commit produced up to 6 commits per ticket on `sprint/2026-42.9`, adding noise to `git log` and requiring reviewers to hunt for the Stage 4 implementation commit (`"One commit per ticket?"` — Rainer Blind, `ATT-4044`).
- **Root Cause**: Stage skills previously did not prohibit intermediate per-stage commits and `stage5-verification/SKILL.md` used `git merge --no-ff feature/<KEY>` instead of squashing the ticket into a single atomic commit.
- **Countermeasure (`.agents/rules/aspice_governance.md` Rule 12 / Rule 44, `.agents/skills/stage1-analysis`..`stage5-verification/SKILL.md`, `.agents/skills/sprint-planner/SKILL.md`, `docs/project_protocol.md`)**:
  - Codified **Rule 44 ("One Commit per Ticket")**: Each ticket in an active sprint MUST produce **strictly one atomic commit** on `sprint/<SPRINT_NAME>` (via `git merge --squash feature/<KEY> && git commit -m "feat(<KEY>): <Summary>"` or a single commit at the end of Stage 5) containing the complete Stage 1–5 deliverables (`analysis`, `test_spec`, `plan`, `walkthrough`), living documentation updates (`docs/requirements.md`, `docs/tests.md`), production code, and unit tests.

### Learning 2: Synchronous Parent Ticket Stage Progression Upon Sub-Task Completion ("Move main ticket forward once the sub-ticket is finished", Rule 45)
- **Observation**: During `ATT-3980`, the parent ticket remained in `Analysis` while its sub-tasks (`[Analysis]` through `[Test]`) transitioned to `Erledigt`, prompting the user note: *"Move main ticket forward once the sub-ticket is finished."* (`ATT-4044`).
- **Root Cause**: Reliance on Jira's backend automation to advance parent tickets when sub-tasks complete is brittle (especially when parent summaries carry priority prefixes like `[Highest]` or when Jira automation lags), and `tools/jira_util.py` did not automatically advance the parent ticket when transitioning a sub-task to `Erledigt` (`freigabe`).
- **Countermeasure (`tools/jira_util.py`, `.agents/rules/aspice_governance.md` Rule 3 / Rule 45, `.agents/skills/stage1-analysis`..`stage5-verification/SKILL.md`, `docs/project_protocol.md`)**:
  - Enhanced `transition_issue()` in `tools/jira_util.py` with `sync_parent_stage_on_subtask_done()`: whenever a stage sub-task transitions to `Erledigt` (`freigabe`), `tools/jira_util.py` automatically advances the parent ticket to the corresponding next workflow stage (`[Analysis]` $\rightarrow$ `Test Spec`, `[Req & Test Spec]` / `[Test-Spec]` $\rightarrow$ `Implementation Plan`, `[Impl-Plan]` $\rightarrow$ `Implementation`, `[Implementation]` $\rightarrow$ `Test`, `[Test]` $\rightarrow$ `Final Review (Human)`), while preserving the strict Human Gate Guard on `Erledigt`.
  - Made `transition_issue()` idempotent when an issue is already in the target status.

### Learning 3: Project `ATT` Issue Type IDs, Priority Prefix Preservation, and Resilient Epic Resolution in `tools/jira_util.py`
- **Observation**:
  1. At sprint start, `tools/jira_util.py ensure-retro` initially failed with HTTP 400 because `ISSUE_TYPE_ALIASES` mapped `Feature`/`Bug`/`Verbesserung` to `10008`/`10009`/`10010` instead of project `ATT`'s actual IDs (`Bug=10006`, `Feature=10007`, `Verbesserung=10008`, `Epic=10001`, `Subtask=10002`).
  2. During Ceremony 2 follow-up creation, `create_issue`'s blanket regex (`r'^\s*(\[[^\]]+\]\s*)+'`) stripped priority tags (`[High]`, `[Medium]`), and passing a non-Epic issue key (`ATT-3979`) as `parent` caused a Jira HTTP 400 error.
- **Countermeasure (`tools/jira_util.py`, `.agents/skills/jira-workflow/SKILL.md`, `.agents/skills/sprint-planner/SKILL.md`, `.agents/rules/aspice_governance.md` Rule 13)**:
  - Updated `ISSUE_TYPE_ALIASES` in `tools/jira_util.py` and skill documentation to `Bug=10006`, `Feature=10007`, `Verbesserung=10008`.
  - Replaced the blanket bracket stripper in `create_issue()` with `sanitize_issue_summary()`, which preserves `[Highest]`, `[High]`, `[Medium]`, `[Low]`, `[Lowest]` while stripping category/epic prefixes.
  - Added `resolve_valid_epic_key()` in `create_issue()` so non-Epic parent keys resolve to their parent Epic (or are omitted with a warning instead of failing with HTTP 400), and added CLI aliases for `set-fix-version` / `update --fix-version` and `--summary` / `--description`.

### Learning 4: Strict Turn Boundary Before Moving Screened Tickets in Ceremony 1 (`ATT-4043` Screening Anomaly)
- **Observation**: During Ceremony 1 screening of `ATT-4043`, the agent chained `move ATT-4043 analysis` in the same tool call as reading `ATT-4042` before receiving the user's explicit approval for `ATT-4043`.
- **Root Cause**: Lack of an explicit turn-boundary rule in Ceremony 1 prohibiting pre-emptive `move <KEY> analysis` calls when presenting a ticket.
- **Countermeasure (`.agents/rules/aspice_governance.md` Rule 7, `.agents/skills/sprint-planner/SKILL.md` Ceremony 1 Step 3)**:
  - Codified that during Ceremony 1 (Sprint-Start Screening), the agent MUST NEVER call `python3 tools/jira_util.py move <KEY> "analysis"` in the same turn in which `<KEY>` is first presented to the user — it must wait for the user's explicit approval before transitioning `<KEY>` to `Analysis`.
