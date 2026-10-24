# Stage 2: Requirement & Test Specification - ATT-4770: Add Sport Type Filter Icons in Periods Header

**Ticket**: [ATT-4770](https://rainerblind.atlassian.net/browse/ATT-4770)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0: Self-Contained Persistence, Full-History Map Previews & Timeline Analytics*)  
**Target Release**: Sprint 2026-43.10 | **Active Sprint**: `sprint/2026-43.10` | **Branch**: `feature/ATT-4770`  
**Requirement Mapping**: `REQ-PER-022` (refining `REQ-PER-018`, `REQ-PER-019`)  
**Test Spec ID**: `TST-PER-028` (`TST-PER-028.1` - `TST-PER-028.5`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-10-23  

---

## 1. Requirement Specification (`REQ-PER-022`)

### 1.1 Problem Statement & Rationale
`PeriodsTabsScreen.kt` currently displays aggregated metrics across all sport types simultaneously. Athletes primarily track endurance disciplines (`Bike` + `Run`) and want a glanceable way to filter the entire Periods view directly in the top header row (`primaryContainer`), excluding gym sessions, walks, and miscellaneous activities (`Other`) from period summaries, bar graphs, and map previews.

### 1.2 Functional & Architectural Requirements
1. **Header Sport Filter Toggles (`REQ-PER-022.1`)**: In `PeriodsTabsScreen.kt`, the `primaryContainer` header row SHALL display 3 compact toggleable sport icon buttons on the right in canonical order `[BSportType.BIKE, BSportType.RUN, BSportType.UNKNOWN]` (Rule 57: `Other` on far right). Selected icons SHALL render in `onPrimaryContainer`; deselected icons SHALL render dimmed (`onPrimaryContainer.copy(alpha = TTAlpha.Disabled)`).
2. **Default Filter & Non-Empty Guard (`REQ-PER-022.2`)**: `selectedSports` in `PeriodsViewModel` SHALL initialize to `setOf(BSportType.BIKE, BSportType.RUN)`. Toggling via `toggleSportFilter(sport)` SHALL prevent deselecting the final remaining active sport, guaranteeing `selectedSports.isNotEmpty()`.
3. **Reactive In-Memory Multi-Sport Projection (`REQ-PER-022.3`)**: `PeriodSummary` in `PeriodData.kt` SHALL include `val workoutIdToAllPolylinesMap: Map<Long, String> = emptyMap()`, populated during `enrich()` in `PeriodsRepository.kt`. `PeriodsViewModel` SHALL project `groupedPeriods` into `filteredGroupedPeriods: StateFlow<Map<PeriodType, List<PeriodSummary>>>`, recalculating totals (`totalWorkouts`, `totalDurationSec`, `totalDistance`), filtering `sportStats`, `polylines`, `workoutIdToPolylineMap`, and `extremaMarkers`, and omitting periods with 0 filtered workouts.
4. **UI Dynamic Synchronization (`REQ-PER-022.4`)**: `PeriodBarGraph`, `PeriodSummaryCard`, and map previews SHALL observe and render `filteredGroupedPeriods` instantaneously without SQLite re-queries.
5. **System Invariants (`REQ-PER-022.5`)**: Zero `PeriodSummaries.db` schema migrations (remains v27); 60 fps `LazyColumn` scrolling; 9-language localization parity; 100% clean-room test pass rate.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-PER-022` (*Periods Sport Type Header Filtering & Multi-Sport Projections*), refining `REQ-PER-018` and `REQ-PER-019`. Targets `PeriodsTabsScreen.kt`, `PeriodsViewModel.kt`, `PeriodData.kt`, `PeriodsRepository.kt`.
* **Historical Origin & Commit Trace**: Sprint 2026-43.9 Review (`ATT-4478`), Sprint 2026-43.2 (Rule 57, commit `d5a81e3a` / `ATT-4372`), and `PeriodMapScreen.kt:133-157` (`ATT-440`).
* **Root Reason for Existing Formulation**: Detail map had local sport filtering, but top-level multi-period lists lacked global filter controls. The header row had unused whitespace, while athletes requested a high-level toggle to isolate Bike and Run from other activities without clunky modal dialogs.
* **Preservation of Core Invariants**: In-memory StateFlow projection avoids SQLite v28 migrations, keeps `PeriodSummaries.db` read-only during filtering, preserves timeline bar graph gap interpolation (`REQ-PER-018`), and maintains 100% clean-room test pass rate.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Header Buttons & Ordering)**: *Given* `PeriodsTabsScreen`, *When* rendered, *Then* 3 sport buttons are displayed on the right in canonical order `[Bike, Run, Other]`, with Bike and Run active (full opacity) and Other dimmed.
* **Criterion 2 (Dynamic Projection)**: *Given* default filters (`Bike + Run`), *When* the athlete toggles Run off, *Then* `selectedSports` becomes `[Bike]`, Run icon dims, and all cards, bar graphs, and map previews update to Bike-only totals.
* **Criterion 3 (Empty Guard)**: *Given* `selectedSports = [Bike]`, *When* tapping Bike, *Then* the tap is ignored and `selectedSports` remains `[Bike]`.
* **Criterion 4 (Zero-Workout Period Omission)**: *Given* a period with only Other workouts, *When* `Other` is deselected, *Then* that period is excluded from `filteredGroupedPeriods`; *When* toggling Other on, *Then* it appears dynamically.

---

## 2. Test Specification (`TST-PER-028`)

### Test Cases
* `TST-PER-028.1` (**Default Filter Set & Empty Guard Test**): Verify initial `selectedSports == setOf(BIKE, RUN)`; assert toggling Run removes it; assert toggling Bike when only Bike remains is ignored (empty-guard); assert toggling Unknown adds it.
* `TST-PER-028.2` (**Reactive In-Memory Period Projection Test**): Given periods with mixed Bike, Run, and Other workouts, verify `filteredGroupedPeriods` recomputes `totalWorkouts`, `totalDurationSec`, `totalDistance`, filters `sportStats`, and omits periods with 0 filtered workouts.
* `TST-PER-028.3` (**Map Preview & Polylines Filtering Test**): Verify projected `polylines`, `workoutIdToPolylineMap`, and `extremaMarkers` retain only workouts matching `selectedSports` using `workoutIdToAllPolylinesMap`.
* `TST-PER-028.4` (**Header UI Layout & Canonical Ordering Test**): Verify 3 buttons render in `[Bike, Run, Other]` order (Rule 57); assert opacity states (`1.0f` vs `TTAlpha.Disabled`); assert localized content descriptions (`R.string.sport_type_*`).
* `TST-PER-028.5` (**Clean-Room Regression Execution**): Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*"` verifying 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Class / Function Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PER-028.1` | Unit | `PeriodsViewModel.toggleSportFilter`, `selectedSports` | `REQ-PER-022.2` | Planned |
| `TST-PER-028.2` | Unit | `PeriodsViewModel.filteredGroupedPeriods` | `REQ-PER-022.3` | Planned |
| `TST-PER-028.3` | Unit | `PeriodsViewModel.filteredGroupedPeriods`, `PeriodData.kt` | `REQ-PER-022.3` | Planned |
| `TST-PER-028.4` | UI / Unit | `PeriodsTabsScreen.kt` | `REQ-PER-022.1` | Planned |
| `TST-PER-028.5` | Regression | Full Period List Test Suite | `REQ-PER-022.5` | Planned |
