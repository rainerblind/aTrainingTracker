# Sprint Review & Retrospective — Sprint 2026-43.10 (`ATT-4776`)

* **Sprint**: `2026-43.10` (`id = 390`)
* **Sprint Branch**: `sprint/2026-43.10` $\rightarrow$ `develop`
* **Release Target**: `V4.9.41`
* **Date**: 2026-10-24

---

## 1. Sprint Review Summary (8 / 8 Tickets Accepted as `i.O.`)

| Rank | Ticket | Epic | Summary | Commit | Verdict |
| :---: | :--- | :--- | :--- | :---: | :--- |
| 1 | **`ATT-4775`** | `ATT-2273` | Fix `TipsGuideScreen` Cockpit Layout Deep Link and Add Dedicated Sport Types Tip Card | `0d9dc645` | **Accepted (`i.O.`)**, `FixVersion = V4.9.41` |
| 2 | **`ATT-4774`** | `ATT-2144` | Reclaim Vertical Space in `WorkoutDetailTabbedContent`: Scroll-Away Collapsing Header, Single-Line Landscape `WorkoutHeader`, Inline Zoom Controls, and 72% X-Axis Plot Alignment | `56b230ec` | **Accepted (`i.O.`)**, `FixVersion = V4.9.41` |
| 3 | **`ATT-4773`** | `ATT-2144` | Optimize Landscape Cockpit Vertical Space: Sensor Icons in Center Status Bar and Auto-Hiding Floating Tab Row | `fed637ba` | **Accepted (`i.O.`)**, `FixVersion = V4.9.41` |
| 4 | **`ATT-4772`** | `ATT-4474` | Suppress Redundant Vector Polylines in `PeriodSummaryCard` Mini-Map When $> 25$ ($> 30$) Workouts Are Rendered via Heatmap | `8aedf7fb` | **Accepted (`i.O.`)**, `FixVersion = V4.9.41` |
| 5 | **`ATT-4770`** | `ATT-4474` | Add Sport Type Filter Icons `[Bike, Run, Other]` in Periods Header (Default: `Bike + Run`) Filtering `PeriodBarGraph`, `PeriodSummaryCard`, and Maps | `59bdc36b` | **Accepted (`i.O.`)**, `FixVersion = V4.9.41` |
| 6 | **`ATT-4771`** | `ATT-4474` | Show Full `'km'` / `'mi'` Distance Unit Instead of `'k'` in `PeriodBarGraph` Top Value Labels | `2341ce50` | **Accepted (`i.O.`)**, `FixVersion = V4.9.41` |
| 7 | **`ATT-4769`** | `ATT-4474` | Restore Sequential Blue Color Palette (Light Cyan $\rightarrow$ Blue $\rightarrow$ Deep Indigo) in `PolylineHeatmapTileProvider` Color LUT | `8fbb22ca` | **Accepted (`i.O.`)**, `FixVersion = V4.9.41` |
| 8 | **`ATT-4802`** | `ATT-3189` | Speed toggles periodically between plausible value and near-zero on 7–8s location update intervals | `46247016` | **Accepted (`i.O.`)**, `FixVersion = V4.9.41` |

---

## 2. Tier 2 Sprint Integration Gate Verification

* **Full Unit Test Suite & Requirement Governance (`task-324`)**:
  * Command: `./gradlew testDebugUnitTest --max-workers=2 && python3 tools/verify_requirement_governance.py`
  * Result: **100% PASSED** (`BUILD SUCCESSFUL`, 0 test failures, 0 requirement governance violations).
  * Note: Unconstrained parallel test worker forks (`task-320`) hit a Gradle worker IPC `java.io.EOFException` due to concurrent Robolectric heap pressure; bounding with `--max-workers=2` eliminated the worker fork crash completely.

---

## 3. Retrospective Root Cause Analysis & Permanent Countermeasures (`Rule 65`)

1. **Automatic Execution of Mid-Sprint Ticket Additions (`"When a new ticket is added to the sprint when the sprint is already started. Please realize this ticket."` — `ATT-4776`)**:
   * **Observation**: While Phase 2 autonomous execution was running on the initial 7 tickets, the human user added a newly discovered defect ticket (`ATT-4802`) to the active sprint (`2026-43.10`) in status `Zu erledigen`. After completing the 7th ticket (`ATT-4769`), the Coordinator queried the sprint backlog, saw `ATT-4802` in `Zu erledigen`, and paused Phase 2 to ask the user whether to move `ATT-4802` to `Analysis` and execute it (citing the Ceremony 1 turn-boundary rule).
   * **Root Cause**: `.agents/skills/sprint-planner/SKILL.md` and `.agents/rules/aspice_governance.md` (`Rule 2`) did not explicitly distinguish between Ceremony 1 (Sprint-Start Screening, where tickets are screened before Phase 2 starts) and **mid-sprint ticket additions during Phase 2**. Because only the human user is permitted to assign backlog tickets into an active sprint, any ticket added by the human user into an already-started sprint during Phase 2 (`status = 'Zu erledigen'`, excluding `Review & Retro`) already carries implicit human approval to be realized in the current sprint.
   * **Countermeasure (`Rule 65`)**: Codified in `.agents/rules/aspice_governance.md` (`Rule 2`) and `.agents/skills/sprint-planner/SKILL.md` (`Phase 2`) that whenever a new ticket is added to an already-started sprint in status `Zu erledigen` (excluding `Review & Retro`), the Coordinator **MUST** automatically transition it to `Analysis` (`python3 tools/jira_util.py move <KEY> "analysis"`) and execute Stages 1–5 autonomously as part of Phase 2 without pausing or asking for confirmation.
2. **Proactive Epic Subagent Pair Rotation After 3 Tickets (`ATT-4771` Stage 5 `INVALID_ARGUMENT (code 400)`)**:
   * **Observation**: In Epic `ATT-4474` (4 tickets), persistent `subagent_flash` (`6e67f87a`) completed 2.8 tickets across fused turns before hitting `INVALID_ARGUMENT (code 400)` at `ATT-4771` Stage 5 due to accumulated transcript/tool payload size; Rule 62 (`Subagent Error Replacement`) recovered seamlessly (`b7b1fc27`).
   * **Countermeasure (`Rule 65`)**: Updated `.agents/rules/aspice_governance.md` (`Rule 7`) to proactively rotate (kill and respawn) an Epic-scoped `(subagent_pro, subagent_flash)` pair after every **3 completed tickets** in large ($\ge 4$-ticket) Epics to avoid hitting the payload ceiling mid-ticket.
3. **Worker-Bounded Tier 2 Unit Test Execution (`--max-workers=2`)**:
   * **Countermeasure (`Rule 65`)**: Updated `.agents/skills/sprint-planner/SKILL.md` (`Ceremony 2 Step 1`) and `docs/coding_standards.md` (`Section 5.2`) to pass `--max-workers=2` to the Tier 2 `./gradlew testDebugUnitTest` run so concurrent Robolectric test forks never trigger a Gradle worker IPC `java.io.EOFException`.
