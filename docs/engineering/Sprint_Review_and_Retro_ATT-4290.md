# Sprint Review & Retrospective — Sprint 2026-43.1 (`ATT-4290`)

**Date**: 2026-10-20  
**Sprint**: `2026-43.1` (`sprint/2026-43.1`, Jira Sprint ID `380`)  
**Retrospective Ticket**: [ATT-4290](https://rainerblind.atlassian.net/browse/ATT-4290)  
**Target Release**: `V4.9.40`

---

## 1. Sprint Review Summary

### 1.1 Reviewed & Accepted Tickets (`FixVersion: V4.9.40` — 14 / 14 Approved)

1. **ATT-4291**: *Support distance interval filtering in Routes and Favorite Routes* — **Accepted** (`V4.9.40`, *Follow-up: `ATT-4367`*)
2. **ATT-4292**: *Fix double percent symbol (%%) in non-formatted Efficiency and W'bal string resources across all 9 locales* — **Accepted** (`V4.9.40`, *Follow-ups: `ATT-4368`, `ATT-4369`*)
3. **ATT-4293**: *Fold rare high-power spikes into an overflow bar in Power Histogram with interactive Sprint Tail zoom and return bar* — **Accepted** (`V4.9.40`, *Follow-up: `ATT-4370`*)
4. **ATT-4294**: *Support all 7 cycling power zones (Z1–Z7) in Power Histogram, TelemetryMetricGraph threshold lines, and Dark Mode chart rendering* — **Accepted** (`V4.9.40`)
5. **ATT-4281**: *Add electronic bike bell to Cockpit with button, double-tap gesture, and progressive loudness escalation* — **Accepted** (`V4.9.40`, *Follow-up: `ATT-4371`*)
6. **ATT-4289**: *Move 'Sport Types' drawer item to Settings and rename 'Equipment' item to 'Bikes & Shoes'* — **Accepted** (`V4.9.40`)
7. **ATT-4288**: *Remove duplicate '+' prefix from onboarding_pair_sensor_action and devices_action_search_sensors strings* — **Accepted** (`V4.9.40`)
8. **ATT-4287**: *Add X/Y axis labels and outlier-resilient Y-scaling to Workout Cluster progression chart* — **Accepted** (`V4.9.40`)
9. **ATT-4286**: *Refine Cockpit section dividers: replace minus-like icon, increase divider prominence, and center section title* — **Accepted** (`V4.9.40`)
10. **ATT-4285**: *Keep RouteTabbedScreen in composition while EditRouteScreen bottom sheet is open to prevent tab bar scroll jump* — **Accepted** (`V4.9.40`)
11. **ATT-4284**: *Prompt for sport type selection (apply to all or individually) during batch GPX route import* — **Accepted** (`V4.9.40`, *Follow-up: `ATT-4372`*)
12. **ATT-4283**: *[Medium] Seed initial W'bal capacity on CyclingPowerMetricsDevice startup and align W/kg & W'bal behavior when stationary or paused* — **Accepted** (`V4.9.40`)
13. **ATT-4282**: *[High] Fix runtime filter registration for 'Best Sensor' fields in EditSensorFieldViewModel and NaN division in TimedMovingAverageFilter* — **Accepted** (`V4.9.40`)
14. **ATT-3997**: *[Bug] [High] BLE sensor search fails on Android 8-11 due to missing BLUETOOTH_ADMIN permission* — **Accepted** (`V4.9.40`)

---

### 1.2 Follow-Up Tickets Created at Top of Backlog
- **ATT-4367**: *Support elevation gain interval filtering (min/max range and preset intervals) in Routes, Favorite Routes, and Workouts* (`Verbesserung`, Epic `ATT-355`)
- **ATT-4368**: *Hide W'bal row in Workout Summary when workout has no recorded power data* (`Bug`, Epic `ATT-355`, with `att_workout_summary_wbal.png`)
- **ATT-4369**: *Add 'Show in Workout Summary' toggle for each model in Physiology & Training Models settings* (`Verbesserung`, Epic `ATT-355`)
- **ATT-4370**: *Align Power Histogram tail cutoff to zone boundary and make header range chip highlighting consistent* (`Verbesserung`, Epic `ATT-355`, with `att_power_histogram_tail.png`)
- **ATT-4371**: *Fix Bike Bell audio playback (USAGE_MEDIA stream, static AudioTrack replay, and enable outside TRACKING mode)* (`Bug`, Epic `ATT-355`, with `att_bike_bell.png`)
- **ATT-4372**: *Polish BatchImportSportTypeDialog layout (fix truncated segmented buttons and vertical 'Andere' chip wrap) and default to Other* (`Bug`, Epic `ATT-66`, with `att_batch_import_dialog.png`)
- **ATT-4373**: *Do not navigate to first Cockpit tab when Auto-Pause ends* (`Bug`, Epic `ATT-355`)

---

## 2. Key Retrospective Learnings & Root Cause Analysis (Rules 52–55)

### Learning 1: Sensor-Presence Gating Before Publishing Virtual Metric Values to `MySensor` (`ATT-4283` / `ATT-4368` — Rule 52)
- **Observation**: On workouts recorded without a power meter (e.g., walking or running), the Workout Summary card still displayed a `W'bal = 100%` (`20.0 kJ`) row (`ATT-4368`).
- **Root Cause**: When implementing constructor state seeding parity (`ATT-4283` / Rule 51), `CyclingPowerMetricsDevice.resetSession()` unconditionally called `mWPrimeBalSensor.newValue(wPrimeKj)`. Publishing a non-null value on `MySensor` before any `SensorType.POWER` sensor exists or emits a sample causes `TrackerService` to record an `EXTREMA` database entry (`MIN = 20.0 kJ`) for `W_PRIME_BAL` on every workout, and `WorkoutDataMapper.resolveWPrimeBalSummary()` did not check whether power data (`avgPower` / `maxPower` / `normalizedPower`) was actually recorded.
- **Countermeasure (`docs/coding_standards.md` Sec 1.8, `docs/engineering/rule_history_archive.md` Rule 52)**:
  - **Rule 52 (Sensor-Presence Gating for Virtual Metric Emission & Summary Rendering)**: While internal accumulator state (`currentWPrimeBalance = wPrimeCapacity`) should be initialized in the constructor/`resetSession()`, virtual sensor devices MUST NOT publish non-null values to `MySensor` (or persist summary extrema) unless the underlying source sensor (`SensorType.POWER`) is active/registered, and summary mappers (`WorkoutDataMapper`) MUST gate derived physiological summary rows on the presence of recorded source telemetry.

### Learning 2: Outdoor Audio Alert Stream Selection (`USAGE_MEDIA`), Static `AudioTrack` Replay & Cockpit Utility Availability (`ATT-4281` / `ATT-4371` — Rule 53)
- **Observation**: During on-device verification of the Cockpit Bike Bell (`ATT-4281`), the bell button was initially disabled before starting a workout (`TrackingMode.READY`) and produced no audible sound when clicked (`ATT-4371`).
- **Root Cause**:
  1. `BikeBellButton` copied `LapButton`'s `enabled = (trackingMode == TrackingMode.TRACKING)` guard, even though an athlete needs the bike bell while stationary (`READY` / `PAUSED`) as well as while rolling (`TRACKING`).
  2. `BikeBellSynthesizer` configured `AudioTrack` with `AudioAttributes.USAGE_ASSISTANCE_SONIFICATION`, which Android routes to the system ringer/notification stream that is muted whenever the phone is on Vibrate or Silent mode. Additionally, re-triggering a `MODE_STATIC` `AudioTrack` requires `stop()` + `reloadStaticData()` (or a fresh `AudioTrack` instance per strike), and wrapping `ElevatedButton(onClick)` inside a parent `pointerInput(detectTapGestures)` causes gesture contention.
- **Countermeasure (`docs/coding_standards.md` Sec 3.4, `docs/design_guidelines.md` Sec 5.12, `docs/engineering/rule_history_archive.md` Rule 53)**:
  - **Rule 53 (Audible Outdoor Safety Alerts & Cockpit Utility Availability)**:
    - Explicit user-triggered outdoor audio alerts (e.g., Bike Bell, acoustic warnings) MUST use `AudioAttributes.USAGE_MEDIA` (or `USAGE_ALARM`), NEVER `USAGE_ASSISTANCE_SONIFICATION`.
    - Auxiliary Cockpit utilities that do not mutate workout lap/sample records (such as `BikeBellButton`) MUST remain enabled across `READY`, `TRACKING`, and `PAUSED` modes.
    - Never wrap a clickable Material `Button`/`ElevatedButton` inside a parent `Modifier.pointerInput { detectTapGestures(...) }`.

### Learning 3: Dialog Width Budgets, Multi-Chip `FlowRow` Wrapping & Long-Locale (`de`) Verification (`ATT-4284` / `ATT-4372` — Rule 54)
- **Observation**: In `BatchImportSportTypeDialog` (`ATT-4284` / `ATT-4372`) on a German-locale device, the `SingleChoiceSegmentedButtonRow` truncated `"Auf alle anwenden"` to `"Auf alle anwen"`, and the 3-chip `Row` (`Radfahren`, `Laufen`, `Andere`) crushed `"Andere"` into a 6-line vertical stack of single characters (`A / n / d / e / r / e`).
- **Root Cause**: Standard Material 3 `AlertDialog` containers have a narrow horizontal width (`~280.dp–320.dp`). Placing 3 `FilterChip`s with leading icons inside a non-wrapping `Row(Modifier.fillMaxWidth())` leaves insufficient width for the third chip in verbose locales (`de`).
- **Countermeasure (`docs/design_guidelines.md` Sec 5.13, `docs/engineering/rule_history_archive.md` Rule 54)**:
  - **Rule 54 (Dialog Width Budgets & `FlowRow` for Multi-Chip Selectors)**: Never place 3+ `FilterChip`s or verbose segmented labels inside a single non-wrapping horizontal `Row` within a standard `AlertDialog`. Always use `FlowRow` (so chips wrap cleanly as whole pills), vertical selection rows, or `AppModalBottomSheet`, and verify dialog layouts against German (`de`) string lengths.

### Learning 4: Origin-Aware Cockpit Tab Navigation on `TrackingMode.TRACKING` Transitions (`ATT-4373` — Rule 55)
- **Observation**: Whenever Auto-Pause finished during a workout, the Cockpit automatically scrolled back to the first telemetry tab (`Tab 0` / pager page `1`), pulling the athlete away from whichever secondary data tab or Map tab they were viewing (`ATT-4373`).
- **Root Cause**: `TrackingTabsViewModel` emitted `TabNavigationEvent.NavigateTo(0)` on every rising edge to `TrackingMode.TRACKING` (`previousMode != null && previousMode != TrackingMode.TRACKING`), without distinguishing between starting/resuming from the **Control** tab (`Page 0`) versus resuming from **Auto-Pause**.
- **Countermeasure (`docs/coding_standards.md` Sec 1.9, `docs/engineering/rule_history_archive.md` Rule 55)**:
  - **Rule 55 (Origin-Aware Cockpit Tab Navigation on Resume)**: Automatic navigation to the first Cockpit telemetry tab (`NavigateTo(0)`) MUST only trigger when starting a workout (`READY -> TRACKING`) or manually resuming from the **Control** tab (`Page 0`). Resuming from **Auto-Pause** MUST preserve the user's currently active Cockpit tab.

---

## 3. Scheduled Process Improvements Implemented from `ATT-4290` Comments

### Process Improvement #1: Retire External HTTP LLM Calls in `tools/review_agent.py` & Embed Gate 1–5 Checklists Directly in Stage Skills
- **Root Cause**: `tools/review_agent.py` relied on external HTTP API keys (`.env.gemini` / `.env.claude`) that are redundant when Antigravity natively supports `invoke_subagent(TypeName="research")` with explicit model selection (`flash` / `pro`).
- **Implementation**:
  - Removed `call_gemini_api`, `call_claude_api`, and external `.env.gemini`/`.env.claude` HTTP dependencies from `tools/review_agent.py`, converting it into a pure Gate Checklist & Jira Verdict helper while preserving `AUDITOR_ROLE = "agent2"` and `GATE_DEFINITIONS` for governance unit test compatibility (`TST-PRO-013`, `TST-PRO-015`).
  - Embedded the full **Gate 1–5 Audit Checklists** and Zipper model tier instructions (`flash` for Gates 1 & 3, `pro` for Gates 2, 4 & 5) directly into `.agents/skills/stage1-analysis/SKILL.md` through `.agents/skills/stage5-verification/SKILL.md`.

### Process Improvement #2: Composite Stage-Transition Commands in `tools/jira_util.py` to Break the "40-Turn Wall"
- **Root Cause**: Each stage handoff previously required 4–5 separate sequential `jira_util.py` CLI invocations (`create-subtask`, `update-desc`, `move in_progress`, `move in_review`, `assign`, `comment`, `move freigabe`), consuming ~35–45 tool-call turns per ticket across 5 stages.
- **Implementation**:
  - Added `submit-stage` (`python3 tools/jira_util.py --as agent1 submit-stage <PARENT_KEY> <STAGE> <TEXT_OR_@FILE> [--summary "..."]`) to `tools/jira_util.py`: automatically ensures the parent ticket is in the target stage, reuses or creates the canonical stage sub-task (with `--add-to-sprint`), updates its description, transitions it to `In Überprüfung` (`in_review`), assigns `agent2`, and prints `SUBTASK_KEY`.
  - Added `gate-verdict` (`python3 tools/jira_util.py --as agent2 gate-verdict <SUBTASK_KEY> <PASS|REVISION> <TEXT_OR_@FILE>`) to `tools/jira_util.py`: hardwired to `agent2` (Anti-Spoofing lock), posts the role-attributed audit comment, and either transitions via `freigabe` (closing the sub-task to `Erledigt`, advancing the parent ticket, and injecting the next stage's `SKILL.md`) on `PASS` or bounces back to `In Bearbeitung` (`agent1`) on `REVISION`.
  - Updated all 5 stage skills (`stage1-analysis`..`stage5-verification/SKILL.md`) and `.agents/skills/jira-workflow/SKILL.md` to mandate `submit-stage` and `gate-verdict`.

### Process Improvement #3: Automatic Stage-Skill & Rule Context Injection via `tools/jira_util.py` + Rule 1 Reformulation
- **Root Cause**: After long sprints or context compactions, agents occasionally skipped manually calling `view_file` on the next stage's `SKILL.md` when transitioning between stages.
- **Implementation**:
  - Implemented `inject_stage_context()` in `tools/jira_util.py`: whenever a parent ticket transitions into `Analysis`, `Test Spec`, `Implementation Plan`, `Implementation`, or `Test` (either via `move` or automatically when `gate-verdict PASS` closes the preceding stage sub-task), `jira_util.py` automatically outputs the target stage's complete `SKILL.md` and the 8 Core Rules reminder directly into `stdout`.
  - Reformulated **Rule 1 (Obey the Rules & Mandatory Context Loading)** in `.agents/rules/aspice_governance.md` so explicit `view_file` calls are required at Ticket Start & Post-Compaction (`aspice_governance.md` + domain doc), while Stage Transitions are automatically served by `jira_util.py`'s stdout context injection.

### Process Improvement #4: Radical Simplification of Governance Rules (`62 KB` $\rightarrow$ `~4.8 KB` / 8 Crisp In-Sprint Core Rules)
- **Root Cause**: Governance rules had grown to 55 numbered rules (~36.5 KB in `.agents/rules/aspice_governance.md` + a redundant 25.4 KB copy in `docs/project_protocol.md`), creating high token overhead and burying core invariants under engineering tips and ceremony checklists.
- **Implementation**:
  - Condensed `.agents/rules/aspice_governance.md` from 36.5 KB down to **~4.8 KB** containing **8 Crisp In-Sprint Core Rules**:
    1. **Rule 1**: Obey the Rules & Mandatory Context Loading
    2. **Rule 2**: Autonomous End-to-End Execution (No Unsolicited Stopping)
    3. **Rule 3**: Human Gate Sovereignty (`Final Review (Human)` vs. `Erledigt`)
    4. **Rule 4**: Strict Code Lock Until Gate 3 (`Impl-Plan`) Approval
    5. **Rule 5**: Ticket Creation Never Triggers Implementation
    6. **Rule 6**: Unattended Sprint Escalation Protocol (`assign human` + Skip)
    7. **Rule 7**: Model-Tier "Zipper Principle" & Independent Subagent Gatekeeping
    8. **Rule 8**: One Commit Per Ticket & Sub-Task Hygiene
  - Replaced the duplicate 25.4 KB `docs/project_protocol.md` with a concise 22-line pointer & role invariant summary.
  - Preserved the complete 55-rule historical cross-reference table (`Rules 1–55`) in `docs/engineering/rule_history_archive.md`.
  - Relocated Ceremony-Only rules into `.agents/skills/sprint-planner/SKILL.md` and the GitFlow Hotfix Protocol into `.agents/skills/release-manager/SKILL.md`.

### Process Improvement #5 (+ Addendum): Store & Upgrade Agile Agentic ASPICE Beamer Presentation in `docs/presentations/`
- **Implementation**:
  - Stored `docs/presentations/agile_agentic_aspice_beamer.tex` and compiled `docs/presentations/agile_agentic_aspice_beamer.pdf` (13 slides, 16:9 `aspectratio=169`, zero overfull boxes).
  - Updated the presentation to reflect all Sprint 2026-43.1 architectural upgrades: the **`pro`/`flash` Zipper Principle**, **Native `research` Subagent Gatekeeping**, **Composite `submit-stage` & `gate-verdict` CLI + Automatic `SKILL.md` Context Injection**, **8 Crisp In-Sprint Core Rules**, and **1 Squashed Commit per Ticket**.
  - Added **Slide 10 (`Why Jira Cloud as the External State Machine & Telemetry Backbone?`)** covering Context-Compaction Immunity, Multi-Account Role-Separated Audit Trail (`JIRA_COORDINATOR_USER`, `JIRA_AGENT1_USER`, `JIRA_AGENT2_USER`), Mobile Human-in-the-Loop Sovereignty, and Quantitative Process & Velocity Telemetry.
