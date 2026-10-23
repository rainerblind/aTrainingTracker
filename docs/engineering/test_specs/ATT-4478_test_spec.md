# Stage 2: Requirement & Test Specification - ATT-4478: Upgrade PeriodBarGraph with Rest Timeline Spacing, Sub-Hour Labels & Metric Modes

**Ticket**: [ATT-4478](https://rainerblind.atlassian.net/browse/ATT-4478)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0: Self-Contained Persistence, Full-History Map Previews & Timeline Analytics*)  
**Target Release**: Sprint 2026-43.9 | **Active Sprint**: `2026-43.9` | **Branch**: `feature/ATT-4478`  
**Requirement Mapping**: `REQ-PER-018` (refining `REQ-PER-007`)  
**Test Spec ID**: `TST-PER-024`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-23  

---

## 1. Requirement Specification (`REQ-PER-018`)

### 1.1 Problem Statement & Rationale
`PeriodBarGraph` in `PeriodsTabsScreen.kt` currently plots raw `PeriodSummary` entries directly from `PeriodSummaries.db`. Because the database stores only periods where `totalWorkouts > 0`, rest days/weeks are completely collapsed out of the timeline, distorting chronological spacing. In addition, integer division (`totalDurationSec / 3600`) suppresses value labels for sub-hour workouts (<60m), bars lack compact X-axis period labels, and bars render in a monolithic single color without sport-type breakdown or metric switching. Upgrading `PeriodBarGraph` into a standalone component with timeline gap interpolation, sub-hour formatting, sport-stacked bars (`BIKE`, `RUN`, `OTHER`), and a metric selector (Duration, Distance, Ascent) restores temporal rhythm and analytical depth.

### 1.2 Functional & Architectural Requirements
1. **Continuous Timeline Gap Interpolation (`REQ-PER-018.1`)**: `PeriodBarGraph` SHALL compute chronological bounds of the period collection and insert zero-volume rest buckets for missing calendar units (days/weeks/months) up to a bounded window. Rest buckets SHALL render as subtle baseline ticks (height 2 dp, disabled tint).
2. **Sub-Hour Duration & Value Formatting (`REQ-PER-018.2`)**: Durations `< 60s` SHALL format as `<1m`, `< 60m` SHALL format as `${m}m` (e.g., `45m`), `1h..10h` SHALL format with one decimal or integer hours (e.g., `1.5h` or `2h`), and `> 10h` SHALL format as `${h}h`. Each bar SHALL display a compact X-axis period identifier (e.g. day/month, `W42`, `Oct`, `2026`) below the bar.
3. **Sport-Stacked Proportions & Metric Switching (`REQ-PER-018.3`)**: Bars SHALL support proportional vertical segments colored by `BSportType` (`BIKE`, `RUN`, `OTHER`) using canonical theme tints. A compact control SHALL toggle between Duration, Distance, and Ascent, scaling bar heights dynamically to the visible maximum.
4. **Bidirectional Scroll & Selection Synchronization (`REQ-PER-018.4`)**: Clicking an active bar SHALL scroll `PeriodList` to the corresponding `PeriodSummaryCard`. Scrolling `PeriodList` SHALL keep `PeriodBarGraph` synchronized with the active item via `LaunchedEffect`. Zero-volume rest ticks SHALL be non-interactive.
5. **System Invariants (`REQ-PER-018.5`)**: Zero changes to `PeriodSummaries.db` schema; zero-crash handling on empty or single lists; 9-language localization parity across all user-facing strings (`R.string.filter_section_duration`, `filter_section_distance`, `stats_ascent`).

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PER-007` (*Reactive Period-Workout Association & Bar Graph*), targeting `PeriodsTabsScreen.kt` and `PeriodBarGraph.kt`.
* **Historical Origin & Commit Trace**: Sprint 2026-40.16 (ATT-375 scroll sync, ATT-440 layout spacing, ATT-488).
* **Root Reason for Existing Formulation**: Minimal inline 70-line helper plotting raw non-zero periods directly to minimize initial UI complexity.
* **Preservation of Core Invariants**: `LaunchedEffect` scroll sync, selection highlighting, and click navigation remain 100% preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Timeline Rest Spacing)**: *Given* non-consecutive periods with missing days, *When* `PeriodBarGraph` renders, *Then* continuous chronological items are generated with rest days rendered as baseline ticks preserving accurate temporal rhythm.
* **Criterion 2 (Sub-Hour Labeling)**: *Given* an active period with duration of 45 minutes, *When* value labels are drawn, *Then* the label displays `45m` rather than being omitted.
* **Criterion 3 (Multi-Metric Scaling & Sport Breakdown)**: *Given* metric mode toggled to `Distance` or `Ascent`, *When* bars render, *Then* bar heights scale to the active metric and reflect proportional `BSportType` contributions.

---

## 2. Test Specification (`TST-PER-024`)

### Test Cases
* `TST-PER-024.1` (**Timeline Gap Interpolation**): Given non-consecutive periods, assert continuous timeline items with zero-volume buckets inserted.
* `TST-PER-024.2` (**Sub-Hour & Value Formatting**): Assert duration `<60s` formats as `<1m`, `45m` formats as `45m`, `90m` formats as `1.5h`, `12h` formats as `12h`, and distance/ascent format cleanly.
* `TST-PER-024.3` (**Metric Scaling**): Assert bar height fractions scale relative to visible maximum for Duration, Distance, and Ascent modes.
* `TST-PER-024.4` (**Sport Breakdown Stacking**): Assert vertical segments reflect proportional contributions of `BIKE`, `RUN`, and `OTHER`.
* `TST-PER-024.5` (**Boundary & Empty Protection**): Assert empty list returns gracefully; single-item list scales height fraction to 1.0f.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Class / Function Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PER-024.1` | Unit | `PeriodBarGraph.interpolateTimelineGaps` | `REQ-PER-018.1` | In Progress |
| `TST-PER-024.2` | Unit | `PeriodBarGraph.formatMetricValue` | `REQ-PER-018.2` | In Progress |
| `TST-PER-024.3` | Unit | `PeriodBarGraph.calculateBarHeights` | `REQ-PER-018.3` | In Progress |
| `TST-PER-024.4` | Unit | `PeriodBarGraph.calculateSportSegments` | `REQ-PER-018.3` | In Progress |
| `TST-PER-024.5` | Unit | `PeriodBarGraph` | `REQ-PER-018.5` | In Progress |
