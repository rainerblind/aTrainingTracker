# Sprint Review & Retrospective — Sprint `2026-45.11` (`ATT-5829`)

* **Sprint**: `2026-45.11` (Jira Sprint ID `403`)
* **Retrospective Ticket**: [ATT-5829](https://rainerblind.atlassian.net/browse/ATT-5829)
* **Target Release**: `V5.0.0`
* **Integration Branch**: `sprint/2026-45.11` $\rightarrow$ `develop`
* **Date**: 2026-11-07

---

## 1. Sprint Velocity & Scope Summary (`REQ-PRO-029`)

During Phase 2 execution (while completing Ticket 7/16, `ATT-5822`), the human user requested concluding the active sprint after `ATT-5822` in order to start the next sprint promptly (*Documented Deviation per Rule 1*).

| Metric | Planned at Sprint-Start | Completed & Accepted (`i.O.`) | Deferred to Next Sprint (Rollover) | Completion Rate (of Executed Scope) |
| :--- | :---: | :---: | :---: | :---: |
| **Regular Sprint Tickets** | 16 | **7** (`ATT-5827`, `ATT-5828`, `ATT-5826`, `ATT-5825`, `ATT-5824`, `ATT-5823`, `ATT-5822`) | 9 (`ATT-5820`, `ATT-5821`, `ATT-5819`, `ATT-5818`, `ATT-5817`, `ATT-5811`, `ATT-5809`, `ATT-5765`, `ATT-5764`) | **100% Accepted** (7/7 executed; 43.8% of initial 16) |
| **Story Points (`SP`)** | 23 SP | **10 SP** | 13 SP | **100% Accepted** (10/10 SP executed; 43.5% of initial 23 SP) |
| **Ceremony Ticket** | 1 (`ATT-5829`, 1 SP) | **1** (`ATT-5829`, 1 SP) | 0 | **100%** |

---

## 2. Joint Sprint Review Results (Ceremony 2 — Strictly One-by-One per `Rule 68`)

| Rank | Ticket | Type | Epic | SP | Squash Commit | Review Verdict | Follow-Up Tickets Created |
| :---: | :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| 1 | **`ATT-5827`** — *`TrackerServiceResumeTest`: Eliminate 1s `ScheduledThreadPoolExecutor` timing race* | Bug | — | 1 | `c075e380` | **Accepted (`i.O.`)** (`V5.0.0`) | — |
| 2 | **`ATT-5828`** — *`PeriodsViewModelTest`: Inject `testDispatcher` into all `PeriodsViewModel` instances* | Bug | — | 1 | `5f593bb4` | **Accepted (`i.O.`)** (`V5.0.0`) | — |
| 3 | **`ATT-5826`** — *`WorkoutDetailTabbedContent`: Fix horizontal tab swipe from `'Map & Graphs'` tab* | Bug | `ATT-355` | 2 | `830bb3e1` | **Accepted (`i.O.`)** (`V5.0.0`) | — |
| 4 | **`ATT-5825`** — *`KnownLocationCard` (Lieblingsorte): UI polish (badge gap, altitude icon, 116×80dp map)* | Verbesserung | `ATT-355` | 1 | `4c095473` | **Accepted (`i.O.`)** (`V5.0.0`) | **`ATT-5887`** (*Harmonize `KnownLocationCard` layout and Starts / Routes visualization with `WorkoutClustersScreen`*) |
| 5 | **`ATT-5824`** — *Lieblingsstrecken (`WorkoutClustersScreen`): Merge confirmation dialog opens on first attempt* | Bug | `ATT-176` | 1 | `777ec07f` | **Accepted (`i.O.`)** (`V5.0.0`) | — |
| 6 | **`ATT-5823`** — *`KnownLocationsScreen` (Lieblingsorte): Add filter UI and default to $\ge 5$ starts* | Verbesserung | `ATT-355` | 2 | `3b1f3b33` | **Accepted (`i.O.`)** (`V5.0.0`) | — |
| 7 | **`ATT-5822`** — *Workout Clusters & Filter Sheets: Default Favorite Routes to $\ge 5$ workouts & filter `'Start at'` chips to $\ge 5$ starts* | Verbesserung | `ATT-176` | 2 | `d0761e3c` | **Accepted (`i.O.`)** (`V5.0.0`) | **`ATT-5888`** (*Always show `'Alle zurücksetzen'` button in `Active*FilterChipsRow` across all filterable lists when any filter is active*) |

### Tier 2 Sprint Integration Gate Verification
* **Command**: `./gradlew testDebugUnitTest --max-workers=2 && python3 tools/verify_requirement_governance.py`
* **Result**: **100% PASS** (`BUILD SUCCESSFUL`, 0 failures; 100% living documentation parity across all 9 domain shards).

---

## 3. Retrospective Findings, Root Cause Analysis & Permanent Countermeasures (`Rule 78`)

### Finding 1: Batch-Transitioning All Sprint Tickets to `Analysis` at Sprint-Start (`ATT-5829` User Feedback)
* **User Observation**: *"Tickets sollten von den Agenten erst dann nach Analyse geschoben werden, wenn die Analyse startet."*
* **Root Cause**: `.agents/skills/sprint-planner/SKILL.md` (Ceremony 1 Step 3 & Step 4) previously instructed the Coordinator to batch-transition all approved regular sprint tickets to `Analysis` (`python3 tools/jira_util.py move <KEY> "analysis"`) at the end of Sprint-Start. Moving all 16 tickets into `Analysis` before work on tickets 2..16 started obscured live board progress and left the 9 untouched tickets in `Analysis` when the sprint was cut short after ticket 7.
* **Permanent Countermeasure (`Rule 78`)**:
  * Updated [`.agents/skills/sprint-planner/SKILL.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/.agents/skills/sprint-planner/SKILL.md), [`.agents/rules/aspice_governance.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/.agents/rules/aspice_governance.md) (`Rule 2`), [`.agents/skills/stage1-analysis/SKILL.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/.agents/skills/stage1-analysis/SKILL.md), and [`docs/engineering/rule_history_archive.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/engineering/rule_history_archive.md) to strictly prohibit batch-moving sprint tickets to `Analysis` during Ceremony 1.
  * Approved sprint tickets remain in **`Zu erledigen`** until Stage 1 (`[Analysis]`) actually begins for that specific ticket (`python3 tools/jira_util.py move <KEY> "analysis"` executed **just-in-time** at ticket start).

### Finding 2: `ensure-retro` Substring Collision in `tools/jira_util.py`
* **Observation**: During Ceremony 1, `python3 tools/jira_util.py ensure-retro` matched the word `"Retro"` inside regular ticket `ATT-5764` (*`Rule 77 Hardening: Add automated git-branch/tag audit script into Ceremony 2 Retro...`*) instead of the dedicated `Review & Retro` ticket (`ATT-5829`).
* **Root Cause**: `ensure_retro_ticket()` in `tools/jira_util.py` used a single pass with `re.search(r'\bretro(?:spective)?\b', summary, re.IGNORECASE)`.
* **Permanent Countermeasure (`Rule 78`)**:
  * Hardened `ensure_retro_ticket()` in [`tools/jira_util.py`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/tools/jira_util.py) with a two-pass check that first matches dedicated `Review & Retro` ceremony titles (`^\s*(?:\[[^\]]+\]\s*)?(?:sprint\s+)?review\s*(?:&|and)\s*retro(?:spective)?\b`) before falling back to standalone `Retrospective` prefix titles.

### Finding 3: 4-Augen Gate 4 Catch of `File.readText()` Source-Grep & `@Preview` Completeness (`ATT-5823`)
* **Observation**: In `ATT-5823` Stage 4 (`ATT-5876`), `subagent_pro` (`--as agent2`) issued a `REVISION` verdict catching a `File.readText()` UI literal source-grep test (`docs/coding_standards.md` §5.4) and missing `@Preview` composables (`docs/design_guidelines.md` §4), which `subagent_flash` immediately fixed before Gate 4 `PASS`.
* **Takeaway**: The asymmetric `pro`/`flash` Zipper Principle (`Core Rule 7`) worked as designed.
