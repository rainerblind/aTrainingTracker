# Stage 5 Walkthrough: ATT-4770 — Add Sport Type Filter Icons in Periods Header

**Ticket**: [ATT-4770](https://rainerblind.atlassian.net/browse/ATT-4770)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0: Self-Contained Persistence, Full-History Map Previews & Timeline Analytics*)  
**Target Release**: `V4.9.41` | **Active Sprint**: `sprint/2026-43.10` | **Branch**: `feature/ATT-4770`  
**Requirement Mapping**: `REQ-PER-022` (refining `REQ-PER-018`, `REQ-PER-019`) | **Test Spec ID**: `TST-PER-028`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-23  

---

## 1. Executive Summary
Implemented multi-sport filtering in the Periods header (`primaryContainer`) enabling athletes to dynamically filter `PeriodBarGraph`, `PeriodSummaryCard`, and map previews across Day, Week, Month, and Year tabs by sport type (`Bike`, `Run`, `Other`). Sport icons follow canonical order `[Bike, Run, Other]` with `Other` on the far right (Rule 57). Active icons render with `onPrimaryContainer` and deselected icons render dimmed (`onPrimaryContainer.copy(alpha = TTAlpha.Disabled)`). Implemented a non-empty selection guard preventing an empty filter set. Stored `workoutIdToAllPolylinesMap` on `PeriodSummary` and implemented pure in-memory StateFlow projection (`filteredGroupedPeriods`) in `PeriodsViewModel`, avoiding SQLite v28 migrations while recalculating totals and omitting 0-workout periods.

---

## 2. Key Architectural Changes

| File / Component | Summary of Changes |
| :--- | :--- |
| `PeriodData.kt` | Added `val workoutIdToAllPolylinesMap: Map<Long, String> = emptyMap()` to `PeriodSummary` to preserve full-history route strings for in-memory sport projection. |
| `PeriodsRepository.kt` | Enriched `workoutIdToAllPolylinesMap` from `allPolylineMap` in `enrich()`; guaranteed `workoutIdToSportMap` is preserved across both live workout groups and fast-path DB loads. |
| `PeriodsViewModel.kt` | Added `selectedSports: StateFlow<Set<BSportType>>` initialized to `setOf(BIKE, RUN)`; implemented `toggleSportFilter(sport)` with non-empty guard; implemented `filterPeriodSummary` recalculating totals, filtering polylines and extremaMarkers, and omitting 0-workout periods; exposed `filteredGroupedPeriods: StateFlow<List<List<PeriodSummary>>>`. |
| `PeriodsTabsScreen.kt` | Added 3 toggleable sport icon buttons in canonical order `[Bike, Run, Other]` in the `primaryContainer` header row using `onPrimaryContainer` (active) and `TTAlpha.Disabled` (dimmed); guarded page indexing in `HorizontalPager`. |
| `PeriodsScreen.kt` | Collected `filteredGroupedPeriods` and `selectedSports`; wired them into `PeriodsTabsScreen` and connected filter toggle callbacks. |
| `PeriodsViewModelTest.kt` | Authored comprehensive unit tests verifying `TST-PER-028.1`–`TST-PER-028.4` (default filter set, non-empty guard, metrics recalculation, zero-workout period omission, polyline/marker filtering, and reactive hierarchy emission). |

---

## 3. Verification & Test Results

* **Tier 1 Unit & Contract Tests**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*" --tests "com.atrainingtracker.trainingtracker.build.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"` -> `BUILD SUCCESSFUL` (100% pass rate across 96+ tests).
* **Resource Compilation Gate**: `./gradlew processDebugResources` -> `BUILD SUCCESSFUL` (2s).
* **Requirement Governance Audit**: `python3 tools/verify_requirement_governance.py` -> `EXIT CODE 0` (clean pass).
* **Target Release Version**: `V4.9.41` (FixVersion assigned to parent ticket upon sprint acceptance per Rule 8).

---

## 4. Requirement & Test Traceability

| Requirement ID | Test ID | Status | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-PER-022.1` | `TST-PER-028.4` | `Verified` | Canonical `[Bike, Run, Other]` header layout, M3 tokens & `PeriodsTabsScreen.kt` |
| `REQ-PER-022.2` | `TST-PER-028.1` | `Verified` | `PeriodsViewModelTest.testDefaultFilterSet_andEmptyGuard` |
| `REQ-PER-022.3` | `TST-PER-028.2`, `TST-PER-028.3` | `Verified` | `PeriodsViewModelTest.testFilterPeriodSummary_*` |
| `REQ-PER-022.4` | `TST-PER-028.4` | `Verified` | `PeriodsViewModelTest.testFilteredGroupedPeriods_emitsTransformedHierarchy` |
| `REQ-PER-022.5` | `TST-PER-028.5` | `Verified` | Full Period List Test Suite (`BUILD SUCCESSFUL`) |
