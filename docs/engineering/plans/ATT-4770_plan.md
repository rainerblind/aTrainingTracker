# Stage 3: Implementation Plan - ATT-4770: Add Sport Type Filter Icons in Periods Header

**Ticket**: [ATT-4770](https://rainerblind.atlassian.net/browse/ATT-4770)  
**Sub-task**: [ATT-4812](https://rainerblind.atlassian.net/browse/ATT-4812) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0: Self-Contained Persistence, Full-History Map Previews & Timeline Analytics*)  
**Target Release**: Sprint 2026-43.10 | **Active Sprint**: `sprint/2026-43.10`  
**Requirement Mapping**: `REQ-PER-022` (refining `REQ-PER-018`, `REQ-PER-019`) | **Test Mapping**: `TST-PER-028`  
**Branch**: `feature/ATT-4770`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-23  

---

## 1. Problem Description & Background

`PeriodsTabsScreen.kt` currently presents unified multi-sport metrics across all tabs. Athletes want to isolate endurance workouts (`Bike` + `Run`) while filtering out miscellaneous activities (`Other`) from period summaries, bar charts, and map previews directly from the top header row.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-PER-022` (*Periods Sport Type Header Filtering & Multi-Sport Projections*)
  * `TST-PER-028.1` (Default filter set & non-empty guard), `TST-PER-028.2` (Reactive period projection & omission of 0-workout periods), `TST-PER-028.3` (Map previews & polylines filtering), `TST-PER-028.4` (Header layout & canonical ordering), `TST-PER-028.5` (Regression suite).

---

## 3. System Invariants & Preserved Behavior

1. **Zero DB Migrations**: `PeriodSummaries.db` remains at v27; all projections are in-memory StateFlow transformations.
2. **Canonical Sport Ordering (Rule 57)**: `Other` (`BSportType.UNKNOWN`) is always on the far right (`[Bike, Run, Other]`).
3. **Non-Empty Filter Guard**: Toggling prevents deselecting the final active sport, preventing blank screens.
4. **Timeline & Map Parity**: `PeriodBarGraph` gap interpolation and `PeriodSummaryCard` decimation remain preserved.

---

## 4. Proposed Architectural Changes

### Component 1: `PeriodData.kt` & `PeriodsRepository.kt`
* Add `val workoutIdToAllPolylinesMap: Map<Long, String> = emptyMap()` to `PeriodSummary`.
* In `enrich()`: Populate `workoutIdToAllPolylinesMap` from `allPolylineMap` to allow per-sport polyline filtering.

### Component 2: `PeriodsViewModel.kt`
* Expose `selectedSports: StateFlow<Set<BSportType>>` initialized to `setOf(BIKE, RUN)`.
* Implement `toggleSportFilter(sport: BSportType)` with non-empty guard (`if (current.size > 1)`).
* Implement `filterPeriodSummary(summary, sports): PeriodSummary?` and expose `filteredGroupedPeriods`.

### Component 3: `PeriodsTabsScreen.kt` & `PeriodsScreen.kt` (UI Consistency - Rule 23)
* Render 3 `IconButton`s in the `primaryContainer` header row using `sport.iconResId` and `sport.stringResId`.
* Styling: Active uses `onPrimaryContainer`; inactive uses `onPrimaryContainer.copy(alpha = TTAlpha.Disabled)`.
* Bind `PeriodsTabsScreen` and child composables (`PeriodBarGraph`, `PeriodList`) to `filteredGroupedPeriods`.
* **UI Consistency**: Reuses `IconButton`, `Icon`, and `LayoutConstants.HEADER_TITLE_ROW_HEIGHT` matching M3 design tokens.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Add `workoutIdToAllPolylinesMap` to `PeriodSummary` & Populate in `enrich()`
* Files: `PeriodData.kt`, `PeriodsRepository.kt`
* Preserve all polylines paired with workout IDs for in-memory sport projection.

### Step 2: Implement Reactive Sport Filtering in `PeriodsViewModel.kt`
* File: `PeriodsViewModel.kt`
* Add `selectedSports`, `toggleSportFilter`, `filterPeriodSummary`, and `filteredGroupedPeriods`.

### Step 3: Add Sport Filter Icons in `PeriodsTabsScreen.kt` & Wire in `PeriodsScreen.kt`
* Files: `PeriodsTabsScreen.kt`, `PeriodsScreen.kt` (canonical `[Bike, Run, Other]`, opacity states, pass `filteredGroupedPeriods`).

### Step 4: Add Unit Tests in `PeriodsViewModelTest.kt`
* File: `PeriodsViewModelTest.kt` (tests `TST-PER-028.1`..`TST-PER-028.4`).
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*"`

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted package unit tests, UI theme audit, and `verify_requirement_governance.py`.
* **Rollback**: Clean git branch revert on `feature/ATT-4770` with zero schema side effects.
