# Stage 1 Analysis: ATT-4770 - Add Sport Type Filter Icons in Periods Header

**Ticket**: [ATT-4770](https://rainerblind.atlassian.net/browse/ATT-4770)  
**Sub-task**: [ATT-4810](https://rainerblind.atlassian.net/browse/ATT-4810) (`[Analysis]`)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0: Self-Contained Persistence, Full-History Map Previews & Timeline Analytics*)  
**Target Release**: Sprint 2026-43.10 | **Active Sprint**: `sprint/2026-43.10`  
**Branch**: `feature/ATT-4770`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-23  

---

## 1. Problem Statement & Motivation

Currently, the Periods screen (`PeriodsTabsScreen.kt`) displays aggregated metrics across all sport types simultaneously. Athletes primarily track endurance disciplines (`Bike` + `Run`) and want a glanceable way to filter the entire Periods view directly in the top header row.
* Gym sessions, walks, and miscellaneous activities (`Other`) skew seasonal distance/duration totals and clutter mini-maps.
* Top header row (`primaryContainer`, opposite the "Zeiträume" title) has unused whitespace ideal for compact sport filter toggles.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification (ATT-3534)

* **Independent Peer Review & Verification**:
  * *Database vs In-Memory Projection Audit*: The ticket suggests persisting `workoutIdToSportMap` in `PeriodSummaries.db` if needed. Forensic audit of `PeriodsRepository.kt:971-1057` reveals `enrich()` already has full in-memory access to `groupWorkouts` from `WorkoutRepository.allWorkouts`. Storing `workoutIdToAllPolylinesMap: Map<Long, String>` on `PeriodSummary` enables pure, instantaneous StateFlow projection in `PeriodsViewModel` without an unnecessary SQLite v27 $\rightarrow$ v28 migration (Coding Standards §1.1, DRY).
  * *Sport Ordering (Rule 57)*: Sport selector icons must follow canonical order `[BSportType.BIKE, BSportType.RUN, BSportType.UNKNOWN]` with `Other` always on the far right (`docs/design_guidelines.md` §1.2).
  * *Visual Styling*: Selected icons render in `onPrimaryContainer`; deselected icons render dimmed (`onPrimaryContainer.copy(alpha = TTAlpha.Disabled)`).
  * *Empty Set Guard*: Toggling prevents deselecting the final active sport to prevent an accidental empty state.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Add 3 sport icon buttons `[Bike, Run, Other]` to the `primaryContainer` header row in `PeriodsTabsScreen.kt`.
  * Maintain `selectedSports` in `PeriodsViewModel`, initialized to `setOf(BSportType.BIKE, BSportType.RUN)`.
  * Project filtered `PeriodSummary` instances: recompute totals (`totalWorkouts`, `totalDurationSec`, `totalDistance`), filter `sportStats`, `polylines`, `workoutIdToPolylineMap`, and `extremaMarkers`, omitting periods with 0 filtered workouts.
  * Dynamically update `PeriodBarGraph`, `PeriodSummaryCard`, and map previews upon filter toggles.
  * Unit tests validating sport projection, empty-guard behavior, and bar graph/card consistency.
* **Out-of-Scope Non-Goals**:
  * Modifying `PeriodSummaries.db` schema (v27 remains untouched).
  * Custom sport tags outside canonical `BSportType`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement `REQ-PER-022` (*Periods Sport Type Header Filtering & Multi-Sport Projections*), refining `REQ-PER-018` and `REQ-PER-019`. Targets `PeriodsTabsScreen.kt`, `PeriodsViewModel.kt`, `PeriodData.kt`, `PeriodsRepository.kt`.
* **Historical Origin & Commit Trace**: Sprint 2026-43.9 Review (`ATT-4478`), Sprint 2026-43.2 (Rule 57, commit `d5a81e3a` / `ATT-4372`), and `PeriodMapScreen.kt:133-157` (`ATT-440`).
* **Root Reason for Existing Formulation**: Detail map had local sport filtering, but top-level multi-period lists lacked global filter controls.
* **Preservation of Core Invariants**: Timeline gap interpolation, Douglas-Peucker simplification, multi-region camera framing, and 100% clean-room test pass rate strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution

| Component | Responsibility |
| :--- | :--- |
| `PeriodData.kt` | Add `val workoutIdToAllPolylinesMap: Map<Long, String> = emptyMap()` to `PeriodSummary`. |
| `PeriodsRepository.kt` | Populate `workoutIdToAllPolylinesMap` in `enrich()`. |
| `PeriodsViewModel.kt` | Expose `selectedSports: StateFlow<Set<BSportType>>`, `toggleSportFilter(sport)`, and `filteredGroupedPeriods`. |
| `PeriodsTabsScreen.kt` | Render toggleable sport icon buttons in top header row; bind to `filteredGroupedPeriods`. |

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**: Zero `PeriodSummaries.db` schema migrations; 60 fps `LazyColumn` scrolling; non-empty filter guard; 100% clean-room test pass rate.
* **Risk Rating**: **LOW** (Presentation-layer StateFlow filtering and Compose UI controls; zero database or background service risk).
