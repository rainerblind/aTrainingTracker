# Stage 3: Implementation Plan - ATT-4478: Upgrade PeriodBarGraph with Rest Timeline Spacing, Sub-Hour Labels & Metric Modes

**Ticket**: [ATT-4478](https://rainerblind.atlassian.net/browse/ATT-4478)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0: Self-Contained Persistence, Full-History Map Previews & Timeline Analytics*)  
**Target Release**: Sprint 2026-43.9 | **Active Sprint**: `2026-43.9`  
**Requirement Mapping**: `REQ-PER-018` | **Test Mapping**: `TST-PER-024`  
**Branch**: `feature/ATT-4478`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-23  

---

## 1. Problem Description & Background

`PeriodBarGraph` in `PeriodsTabsScreen.kt:207-277` plots raw non-zero periods directly from SQLite. Because zero-workout days/weeks are not stored, rest periods are collapsed out of the timeline, distorting chronological rhythms. Furthermore, integer division (`totalDurationSec / 3600`) hides value labels for sub-hour workouts (<60m), bars lack compact X-axis period labels, and bars render in a monolithic single color without sport-type breakdown or metric switching.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-PER-018` (*Continuous Timeline PeriodBarGraph, Sub-Hour Value Formatting & Sport/Metric Modes*)
  * `TST-PER-024.1` (Timeline gap interpolation & rest baseline ticks)
  * `TST-PER-024.2` (Sub-hour `<1m`, `45m`, `1.5h` duration and X-axis period labels)
  * `TST-PER-024.3` (Metric switching across Duration, Distance, and Ascent)
  * `TST-PER-024.4` (Sport-stacked proportional segments for `BIKE`, `RUN`, `OTHER`)
  * `TST-PER-024.5` (Empty list & single-item edge-case safety)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Database Migrations**: Zero schema changes to `PeriodSummaries.db` or `WorkoutSummaries.db`.
2. **Scroll Sync & Navigation Parity**: `LaunchedEffect(firstVisibleIndex)` bidirectionally synchronizes graph scroll with `LazyListState`; clicking an active bar scrolls `PeriodList` to `originalIndex`.
3. **9-Language Parity**: Reuses canonical localized strings (`R.string.filter_section_duration`, `filter_section_distance`, `stats_ascent`) across all 9 languages without string resource churn.
4. **Non-Crashing Null Safety**: Zero crashes on empty lists, missing sport stats, or single-day periods.

---

## 4. Proposed Architectural Changes

### Component 1: `PeriodBarGraph.kt` & Data Models (`ui.aftermath.periodlist`)
* Decouple `PeriodBarGraph` into standalone file. Define `PeriodBarMetric` and `PeriodBarItem`.
* Logic helpers: `interpolateTimelineGaps` (rest baseline ticks), `formatMetricValue` (`<1m`, `45m`, `1.5h`, `12h`), and `formatPeriodXLabel` (`dd.MM`, `Www`, `MMM`, `yyyy`).
* Stacked bars by `BSportType` (`BIKE`: `#1E88E5`, `RUN`: `#FF7043`, `OTHER`: `#26A69A`).
* Compact `SingleChoiceSegmentedButtonRow` for metric switching.

### Component 2: `PeriodsTabsScreen.kt` Integration & UI Consistency (Rule 23)
* Embed `PeriodBarGraph` in collapsing header above `PeriodList`.
* Reused tokens: `SingleChoiceSegmentedButtonRow`, `SegmentedButton`, `MaterialTheme.colorScheme.primary`, `outlineVariant`, `surfaceContainerLowest`, `TTAlpha.Disabled`. Semantic sport tints match brand palette.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Implement `PeriodBarGraph.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodBarGraph.kt`
* Pure logic helpers (`interpolateTimelineGaps`, `formatMetricValue`, `formatPeriodXLabel`), composable `PeriodBarGraph`, sport-stacked rendering, and `@Preview`.

### Step 2: Integrate `PeriodBarGraph` in `PeriodsTabsScreen.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsTabsScreen.kt`
* Replace inline `PeriodBarGraph` with new standalone component.

### Step 3: Implement Unit Tests
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodBarGraphTest.kt`
* Test cases `TST-PER-024.1` through `TST-PER-024.5`.
* Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted package unit tests, `verify_requirement_governance.py`, and full clean-room suite in Stage 5.
* **Rollback**: Clean git revert of Stage 4 commits on `feature/ATT-4478`.
