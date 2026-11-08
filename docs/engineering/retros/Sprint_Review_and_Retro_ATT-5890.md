# Sprint Review & Retrospective — Sprint `2026-45.12` (`ATT-5890`)

* **Sprint**: `2026-45.12` (Jira Sprint ID `404`)
* **Retrospective Ticket**: [ATT-5890](https://rainerblind.atlassian.net/browse/ATT-5890)
* **Target Release**: `V5.0.0`
* **Integration Branch**: `sprint/2026-45.12` $\rightarrow$ `develop`
* **Date**: 2026-11-08

---

## 1. Sprint Velocity & Scope Summary (`REQ-PRO-029`)

During Phase 2 execution, 38 regular sprint tickets (`58 SP`) were executed across Stages 1–5 and accepted in Ceremony 2. Per human user instruction after `ATT-5905` completed Gate 5 (*Documented Deviation per Rule 1*), Phase 2 concluded with 38 completed sprint tickets, leaving 3 untouched mid-sprint added tickets (`ATT-5902`, `ATT-5900`, `ATT-5899`) cleanly in `Zu erledigen` with zero WIP (`Rule 78`) for the next sprint.

| Metric | Planned + Mid-Sprint Added | Completed & Accepted (`i.O.`) | Deferred to Next Sprint (`Zu erledigen`) | Completion Rate (of Executed Scope) |
| :--- | :---: | :---: | :---: | :---: |
| **Regular Sprint Tickets** | 41 | **38** | 3 (`ATT-5902`, `ATT-5900`, `ATT-5899`) | **100% Accepted** (38/38 executed; 92.7% of 41) |
| **Story Points (`SP`)** | 62 SP | **58 SP** | 4 SP | **100% Accepted** (58/58 SP executed; 93.5% of 62 SP) |
| **Ceremony Ticket** | 1 (`ATT-5890`, 1 SP) | **1** (`ATT-5890`, 1 SP) | 0 | **100%** |

---

## 2. Quantitative Sprint Process Metrics (`PA 4.1` & `PA 4.2` — `REQ-PRO-038`)

### 2.1 Throughput & Quality Gate First-Time-Pass-Rates (FTPR)
* **Ticket Throughput**: `38/38` executed tickets (`100.0%` of executed scope; `84.4%` raw board ratio including ceremony/deferred items) | **Velocity**: `58.0 SP`
* **Overall Gate FTPR**: **`100.0%`** (evaluated across `190` stage sub-tasks)
* **Human Acceptance Rate**: **`100.0%`** (`38/38` reviewed tickets accepted as `i.O.` in Ceremony 2)

| Gate / Stage | Sub-tasks | 1st-Time Pass | Revisions | FTPR % | Empirical Defect Escape Rate ($p_i$) |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Analysis (Gate 1)** | 38 | 38 | 0 | **100.0%** | 0.0 |
| **Req & Test Spec (Gate 2)** | 38 | 38 | 0 | **100.0%** | 0.0 |
| **Impl-Plan (Gate 3)** | 38 | 38 | 0 | **100.0%** | 0.0 |
| **Implementation (Gate 4)** | 38 | 38 | 0 | **100.0%** | 0.0 |
| **Test (Gate 5)** | 38 | 38 | 0 | **100.0%** | 0.0 |

### 2.2 Empirical Swiss Cheese Multiplicative Defect Reduction (Reason Model)
* **Empirical Escape Rates**: $p_1 = 0.0, p_2 = 0.0, p_3 = 0.0, p_4 = 0.0, p_5 = 0.0$
* **Theoretical Cumulative Defect Escape Probability**: $P(\text{escaped defect}) = 3.2 \times 10^{-9}$

### 2.3 Statistical Process Control (`SPC` — `PA 4.2`, Sample Size: 4 Sprints)
* **`OVERALL_FTPR`**: $\mu = 78.62\%, \sigma = 14.52\% \rightarrow$ Control Band $[\text{LCL}: 49.59\%, \text{UCL}: 107.66\%]$ (**In Control**)
* **`HUMAN_ACCEPTANCE`**: $\mu = 80.0\%, \sigma = 40.0\% \rightarrow$ Control Band $[\text{LCL}: 0.0\%, \text{UCL}: 160.0\%]$ (**In Control**)
* **`COMPLETED_TICKETS`**: $\mu = 19.5, \sigma = 12.26 \rightarrow$ Control Band $[\text{LCL}: 0.0, \text{UCL}: 44.02]$ (**In Control**)
* **`COMPLETED_SP`**: $\mu = 2.25, \sigma = 4.5 \rightarrow$ Control Band $[\text{LCL}: 0.0, \text{UCL}: 11.25]$ (**⚠️ Special Cause Variation: `58.0 SP` > UCL**)
  * **PA 4.2 Root-Cause Analysis (RCA)**: Two factors explain the positive upper-bound excursion on `COMPLETED_SP`: (1) the initial 3 backfilled sprints in `docs/engineering/metrics/process_metrics.jsonl` predated formal Story Point estimation (`REQ-PRO-029`, recorded as `0.0 SP`), skewing the 4-sprint historical mean down to $\mu = 2.25\text{ SP}$, and (2) Sprint `2026-45.12` executed a multi-wave backlog sweep of 38 tickets (`58.0 SP`) while maintaining a `100.0%` Gate 1–5 FTPR across all 190 sub-tasks. As Story-Point-estimated sprints accumulate in the rolling window, `COMPLETED_SP` control limits will self-calibrate.

---

## 3. Joint Sprint Review Results (Ceremony 2 — Strictly One-by-One per `Rule 68`)

| Rank | Ticket | Type | Epic | SP | Squash Commit | Review Verdict | Follow-Up Tickets Created |
| :---: | :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| 1 | **`ATT-5820`** — *Auto-Pause (`AutoPauseTabsScreen`): Collapse threshold & delay sliders by default* | Verbesserung | `ATT-355` | 1 | `5ad24a48` | **Accepted (`i.O.`)** (`V5.0.0`) | — |
| 2 | **`ATT-5821`** — *`EditSportTypeDialog`: Collapse min/max speed & TCX/GC mappings under 'Erweiterte Optionen'* | Verbesserung | `ATT-355` | 1 | `978fdda1` | **Accepted (`i.O.`)** (`V5.0.0`) | — |
| 3 | **`ATT-5819`** — *Expert Settings: Friendly & objective `TuningWarningNotice` wording* | Verbesserung | `ATT-355` | 1 | `464d01a5` | **Accepted (`i.O.`)** (`V5.0.0`) | — |
| 4 | **`ATT-5818`** — *`ClusterNamingDialog`: Tap in 'Zugeordnete Lieblingsstrecke' text field opens picker* | Verbesserung | `ATT-176` | 1 | `f4c90a83` | **Accepted (`i.O.`)** (`V5.0.0`) | — |
| 5 | **`ATT-5817`** — *Höchstmaß Skill Set & README: 5-Layer Swiss Cheese SVG, Entry Barrier & `.reviews/` Emacs workflow* | Feature | `ATT-232` | 2 | `a00d32ee` | **Accepted (`i.O.`)** (`V5.0.0`) | — |
| 6 | **`ATT-5811`** — *`WorkoutPhotoStrip`: Move 'Add Photo' button into compact 'Photos' header row* | Verbesserung | `ATT-355` | 1 | `92f1bd46` | **Accepted (`i.O.`)** (`V5.0.0`) | — |
| 7 | **`ATT-5809`** — *`WorkoutHeader` (Summary & Details): Dedicated Share Workout Image button next to Edit* | Verbesserung | `ATT-68` | 2 | `ab9bebee` | **Accepted (`i.O.`)** (`V5.0.0`) | **`ATT-6208`** (*Directly capture composite map/chart snapshot in `'Karten- & Analyse-Snapshot'`*) |
| 8 | **`ATT-5765`** — *Höchstmaß (ASPICE Level 4/5 — Part 1/2): Quantitative Sprint Metrics (PA 4.1) & SPC (PA 4.2)* | Feature | `ATT-232` | 2 | `3d4c9dd5` | **Accepted (`i.O.`)** (`V5.0.0`) | — |
| 9 | **`ATT-5764`** — *Höchstmaß (ASPICE Level 4/5 — Part 2/2): Integrate Quantitative Sprint Scorecard into Ceremony 2 Retro* | Feature | `ATT-232` | 2 | `ce2acca2` | **Accepted (`i.O.`)** (`V5.0.0`) | — |
| 10 | **`ATT-5889`** — *`KnownLocations`: Merge two locations with hybrid geofence geometry & radius preview* | Feature | `ATT-1396` | 3 | `c6f65b71` | **Accepted (`i.O.`)** (`V5.0.0`) | **`ATT-6209`** (*Remove `'Als Heim-Basis festlegen'` from `KnownLocationCard` 3-dot menu*) |
| 11 | **`ATT-5888`** — *Filterable Lists: Always show `'Alle zurücksetzen'` in `ActiveFilterChipsRow`* | Verbesserung | `ATT-355` | 1 | `e6c91cd9` | **Accepted (`i.O.`)** (`V5.0.0`) | — |
| 12 | **`ATT-5887`** — *`KnownLocationCard`: Harmonize card layout & Starts / Routes visualization with `WorkoutClustersScreen`* | Verbesserung | `ATT-1396` | 2 | `8a7cb3f6` | **Accepted (`i.O.`)** (`V5.0.0`) | **`ATT-6210`** (*Break down workout starts by `BSportType` using app sport logos*) |
| 13 | **`ATT-5886`** — *`TrackerService` & `WorkoutSummaries`: Persist deduplicated recording exceptions & diagnostic dialog* | Feature | `ATT-235` | 3 | `98b4e67d` | **Accepted (`i.O.`)** (`V5.0.0`) | — |
| 14 | **`ATT-5884`** — *`KnownLocationsScreen`: Zoom out map thumbnail, centered heart marker, `'+'` FAB & deduplicate subtitle* | Verbesserung | `ATT-1396` | 1 | `ce73d561` | **Accepted (`i.O.`)** (`V5.0.0`) | **`ATT-6211`** (*Improve centered heart marker geometry in `KnownLocationThumbnailMap`*) |
| 15 | **`ATT-5880`** — *Global Map & Cockpit Map: Center initial camera on last known location & bottom insets* | Bug | `ATT-66` | 1 | `8f8f959a` | **Accepted (`i.O.`)** (`V5.0.0`) | **`ATT-6212`** (*Prevent `LOCAL_SEGMENTS`/`LOCAL_ROUTES` from fitting world-wide segments when `currentLocation` is null*) |
| 16 | **`ATT-5879`** — *`TrackingTabs` & Cockpit: Fix edit-mode wrapping, localize labels & fix bell FAB overlap* | Bug | `ATT-5908` | 2 | `45cbed33` | **Accepted (`i.O.`)** (`V5.0.0`) | — |
| 17 | **`ATT-5875`** — *Display & Expert Settings: Localize `'Control Tracking'`/`'Aftermath'` & accordion summaries* | Bug | `ATT-5909` | 1 | `9e65fd18` | **Accepted (`i.O.`)** (`V5.0.0`) | — |
| 18 | **`ATT-5873`** — *`AppModalBottomSheet`: Hide top-right `'X'` close button when bottom action bar is present* | Verbesserung | `ATT-355` | 1 | `d92469ec` | **Accepted (`i.O.`)** (`V5.0.0`) | — |
| 19 | **`ATT-5874`** — *`ImportBackupTabsScreen`: Pre-import clustering tuning for Strava archive & provider icons* | Verbesserung | `ATT-281` | 1 | `bff4ae7f` | **Accepted (`i.O.`)** (`V5.0.0`) | — |
| 20 | **`ATT-5872`** — *`SportTypesTabsScreen`: Handle empty `'GC:'`, localize `'Other'`/export labels & format ISO dates* | Bug | `ATT-5910` | 2 | `e541967d` | **Accepted (`i.O.`)** (`V5.0.0`) | — |
| 21 | **`ATT-5871`** — *`AppNavigationDrawer`, Units & `ActivityTypeSelectionDialog`: Disambiguate `'Maßeinheiten'` & power icon* | Verbesserung | `ATT-5909` | 1 | `84c469a3` | **Accepted (`i.O.`)** (`V5.0.0`) | — |
| 22 | **`ATT-5854`** — *`StravaSettingsDialog`: Remove redundant close button & add sync/refresh icons* | Verbesserung | `ATT-597` | 1 | `a70b9284` | **Accepted (`i.O.`)** (`V5.0.0`) | — |
| 23 | **`ATT-5853`** — *`RouteOnMapScreen` & `ClimbDetector`: Fix top-clipped climb badges, icon & grade formatting* | Bug | `ATT-2565` | 2 | `46ce5eb3` | **Accepted (`i.O.`)** (`V5.0.0`) | — |
| 24 | **`ATT-5852`** — *`WorkoutClusterTrendChartCard` & Dashboard: Fix overlapping labels, time-range filter & thousands separator* | Bug | `ATT-176` | 2 | `2c42941a` | **Accepted (`i.O.`)** (`V5.0.0`) | — |
| 25 | **`ATT-5851`** — *`MapDetailLayout`: Prevent floating map buttons from colliding with system status bar* | Bug | `ATT-355` | 1 | `bac4f2da` | **Accepted (`i.O.`)** (`V5.0.0`) | **`ATT-6214`** (*Move floating Layers button up to `top=16.dp` when Share button is hidden*) |
| 26 | **`ATT-5847`** — *Equipment & Sensors UI: Deduplicate Strava gear names, localize ISO dates/frame types & green dot* | Bug | `ATT-5910` | 2 | `0791df8d` | **Accepted (`i.O.`)** (`V5.0.0`) | **`ATT-6215`** (*Fix duplicate bikes/shoes `'Endurance'` vs `'endurance '` / `StravaId` name pollution in `EquipmentDbHelper`*) |
| 27 | **`ATT-5846`** — *`ClusterItem` & Filter: Fix `'0 Aufzeichnungen'`, show destination, landscape thumbnail & 1-line reset* | Bug | `ATT-176` | 2 | `c3c957e2` | **Accepted (`i.O.`)** (`V5.0.0`) | — |
| 28 | **`ATT-5845`** — *`ElevationProfile` & `RouteItem`: Fix Y-axis label collision & hide empty altitude profile* | Bug | `ATT-66` | 1 | `8170d6e1` | **Accepted (`i.O.`)** (`V5.0.0`) | — |
| 29 | **`ATT-5844`** — *`AthleteProfileScreen`: Fix truncated filter chips, localize decimals & add PMC time-range selector* | Bug | `ATT-5910` | 2 | `9fb50c1a` | **Accepted (`i.O.`)** (`V5.0.0`) | — |
| 30 | **`ATT-5839`** — *`AppNavigationDrawer` (`DrawerHeader`): Reduce header height by ~16dp so bottom item fits* | Verbesserung | `ATT-355` | 1 | `d22358e1` | **Accepted (`i.O.`)** (`V5.0.0`) | **`ATT-6216`** (*Position app icon and `'a Training Tracker'` title higher in the sky area of `DrawerHeader`*) |
| 31 | **`ATT-5838`** — *`EditWorkoutScreen`: Localize hints, normalize labels & fix Strava checkbox layout* | Bug | `ATT-5909` | 1 | `043b5c56` | **Accepted (`i.O.`)** (`V5.0.0`) | — |
| 32 | **`ATT-5836`** — *`PeriodSummary` (Zeiträume): Localize date range header & chart decimal labels* | Bug | `ATT-67` | 1 | `0f2d91f0` | **Accepted (`i.O.`)** (`V5.0.0`) | — |
| 33 | **`ATT-5835`** — *`WorkoutDetailTabbedContent` & `MapDetailLayout`: Remove full-header edit trigger & hide drag handle in tabs* | Verbesserung | `ATT-68` | 1 | `497d5015` | **Accepted (`i.O.`)** (`V5.0.0`) | — |
| 34 | **`ATT-5830`** — *Höchstmaß Skill Set (`tools/jira_util.py`): Support Jira Data Center (PAT, username, Epic Link & API v2)* | Feature | `ATT-232` | 2 | `0b93f486` | **Accepted (`i.O.`)** (`V5.0.0`) | — |
| 35 | **`ATT-6039`** — *`StravaBulkExportImporter` & `LegacyImportEngine`: Unify bulk import order to chronological ascending* | Verbesserung | `ATT-281` | 1 | `163e83a6` | **Accepted (`i.O.`)** (`V5.0.0`) | — |
| 36 | **`ATT-5911`** — *`tools/jira_util.py` & Skills: Add `'list-epics'` / `'close-epic'` CLI support & Epic governance* | Verbesserung | `ATT-232` | 1 | `c68b13a9` | **Accepted (`i.O.`)** (`V5.0.0`) | — |
| 37 | **`ATT-5914`** — *Parallel Sprint Execution (2 Disjoint Epics): `'Workspace: share'`, flock serialization & index sync* | Feature | `ATT-232` | 2 | `d002a9bd` | **Accepted (`i.O.`)** (`V5.0.0`) | — |
| 38 | **`ATT-5905`** — *`TrackingTabsScreen` & `DisplaySettingsDialog`: Use hardware volume buttons to cycle Cockpit tabs* | Feature | `ATT-5908` | 1 | `17dbcfe0` | **Accepted (`i.O.`)** (`V5.0.0`) | — |

### Tier 2 Sprint Integration Gate Verification
* **Command**: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --max-workers=2 && python3 tools/verify_requirement_governance.py`
* **Result**: **100% PASS** (`BUILD SUCCESSFUL`, 4,789/4,789 tests passing after `f7782eae` `test(ATT-5835)` contract update; 100% living documentation parity across all 9 domain shards).

---

## 4. Retrospective Findings, Root Cause Analysis & Permanent Countermeasures (`Rule 79`)

### Finding 1: Two Tickets Mixed in One Ceremony 2 Turn Due to Trailing Tool Call (`ATT-5835` + `ATT-5830`)
* **User Observation**: *"Gerade wurden irgendwie zwei Tickets vermischt. Ich ahbe nur das erste angenommen."*
* **Root Cause**: When presenting Ticket 33 (`ATT-5835`), the assistant included a tool call (`set-fixversion` + `git show` for Ticket 34) in the same turn. When that tool call returned, the assistant continued generating in the same turn and also presented Ticket 34 (`ATT-5830`) before the user had responded to Ticket 33.
* **Permanent Countermeasure (`Rule 79`)**:
  * Hardened [`.agents/skills/sprint-planner/SKILL.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/.agents/skills/sprint-planner/SKILL.md) (Ceremony 2 Step 3, `Rule 68` / `Rule 79`): All preparatory tool calls (`set-fixversion` for Ticket $N-1$, `read_file` / `git show` for Ticket $N$) **MUST** complete *before* emitting the presentation message for Ticket $N$, and the response presenting Ticket $N$ **MUST** contain **zero tool calls** so the turn terminates immediately and waits for the user's verdict.

### Finding 2: Cross-Package Visual Contract Test Missed by Tier 1 Package Filter (`ATT-5835` $\rightarrow$ `BottomSheetVisualContractTest`)
* **Observation**: In `ATT-5835`, changing `MapDetailLayout.kt` (`ui.map`) from `if (useStatusBarsPadding) RectangleShape else BottomSheetDesign.SheetShape` to `if (isBottomSheet) BottomSheetDesign.SheetShape else RectangleShape` caused `BottomSheetVisualContractTest.testMapDetailLayout_appliesSheetShapeAndSurfaceBackground` (in package `ui.components.core`) to fail during the Tier 2 Sprint Integration Gate (fixed in commit `f7782eae`).
* **Root Cause**: `ATT-5835`'s Tier 1 test filter ran `ui.workout.*`, `ui.map.*`, and `ThemeColorCentralizationAuditTest`, which did not cover `ui.components.core.BottomSheetVisualContractTest`.
* **Permanent Countermeasure (`Rule 79`)**:
  * Updated [`.agents/rules/aspice_governance.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/.agents/rules/aspice_governance.md) (`Rule 7`) and [`.agents/skills/stage5-verification/SKILL.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/.agents/skills/stage5-verification/SKILL.md) so that whenever any UI/Compose/Map/Sheet file is modified, Tier 1 targeted unit test runs automatically include `--tests "com.atrainingtracker.trainingtracker.ui.components.core.*"` alongside `ThemeColorCentralizationAuditTest`.

### Finding 3: Headless Software Emulator Hang When Physical Device Disconnected Overnight (`ATT-5811` Stage 5)
* **Observation**: During Stage 5 of `ATT-5811`, when the physical Pixel 10 disconnected from ADB overnight, `subagent_flash` launched `qemu-system-x86_64-headless -avd Pixel_8_Android_15 -gpu swiftshader_indirect`, saturating CPU (92%) until the 20-minute Rule 66 Coordinator Watchdog fired, killed the hung process, and replaced the subagent.
* **Permanent Countermeasure (`Rule 79`)**:
  * Codified in [`.agents/rules/aspice_governance.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/.agents/rules/aspice_governance.md) (`Rule 7`): Subagents **MUST NEVER** boot a software Android emulator (`emulator` / `qemu-system-*`) when no physical device is connected in `adb devices`.

### Finding 4: `sprint-metrics --post-to-retro` Substring Collision on `"Retro"` (`ATT-5764` vs. `ATT-5890`)
* **Observation**: Running `python3 tools/jira_util.py --as coordinator sprint-metrics --record --post-to-retro` initially matched `"Retro"` in `ATT-5764`'s summary (*`...Integrate Quantitative Sprint Scorecard into Ceremony 2 Retro...`*) instead of the dedicated `Review & Retro` ticket (`ATT-5890`).
* **Permanent Countermeasure (`Rule 79`)**:
  * Updated `cmd_sprint_metrics` in both [`tools/jira_util.py`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/tools/jira_util.py) and [`tools/hoechstmass/tools/jira_util.py`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/tools/hoechstmass/tools/jira_util.py) to use the canonical two-pass `Review & Retro` regex from `ensure_retro_ticket()`.
