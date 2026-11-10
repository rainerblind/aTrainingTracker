# ATT-6321 Walkthrough: Filter Workout List by Clicked BSportType on KnownLocationCard

**Ticket**: [ATT-6321](https://atrainingtracker.atlassian.net/browse/ATT-6321)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte (Known Locations, Geofences & Auto-Naming)*)  
**Sprint**: `2026-46.1` | **Target Release**: `V5.0.0` | **Status**: Verified  
**Requirement Mapping**: `REQ-MAP-062` | **Test Mapping**: `TST-MAP-064`  

---

## 1. Executive Summary

In [ATT-6210], `KnownLocationCard` was enhanced to render workout start counts partitioned by `BSportType` (`Bike`, `Run`, `Other`) via `SportStartsItem`. However, `clickable(onClick = onShowWorkouts)` was bound to the enclosing `Row`, opening all workouts unfiltered instead of filtering by the tapped sport.

Under `ATT-6321` (governed by `REQ-MAP-062` / `TST-MAP-064`):
- **Independent Touch Targets (`REQ-MAP-062.1`)**: Refactored `SportStartsItem` with individual `clickable(onClick)` and test tags `location_starts_badge_${item.id}_bike`, `_run`, `_other`. Propagated `onShowWorkoutsForSport(item, bSportType)` across `KnownLocationCard`, `KnownLocationsListContent`, and `KnownLocationsScreen`.
- **Fallback 0-Starts Retention (`REQ-MAP-062.2`)**: Retained outer clickable callback `onShowWorkouts(item)` when `sportStarts.total == 0` or clicking badge container background.
- **Base Sport Type Dimension (`REQ-MAP-062.3`)**: Added `bSportType: BSportType? = null` to `WorkoutFilterCriteria` with predicate matching in `matches()`, active filter count calculation, and backward-compatible JSON serialization. Added removable chip in `ActiveFilterChipsRow.kt` and `WorkoutTabsScreen.kt`.
- **Cross-Screen Navigation & Tab Sync (`REQ-MAP-062.4`)**: Configured criteria with location bounds and `bSportType` in `ATrainingTrackerApp.kt`. Synchronized `pagerState` in `WorkoutSummariesTabbedScreen.kt` to target sport tab (Bike = 1, Run = 2, Other = 3).

---

## 2. Traceability Matrix

| Requirement | Test Specification | Production Code | Verification Tests | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-MAP-062.1` | `TST-MAP-064.1` | `KnownLocationsScreen.kt` | `KnownLocationsScreenDrillDownTest` | **Verified** |
| `REQ-MAP-062.2` | `TST-MAP-064.2` | `KnownLocationsScreen.kt` | `KnownLocationsScreenDrillDownTest` | **Verified** |
| `REQ-MAP-062.3` | `TST-MAP-064.3` | `WorkoutFilterCriteria.kt`, `ActiveFilterChipsRow.kt` | `WorkoutFilterCriteriaTest` | **Verified** |
| `REQ-MAP-062.4` | `TST-MAP-064.4` | `ATrainingTrackerApp.kt`, `WorkoutSummariesTabbedScreen.kt` | `KnownLocationsScreenDrillDownTest` | **Verified** |
| `REQ-MAP-062` | `TST-MAP-064.5` | Targeted Test Suite | Targeted Test Suite | **Verified** |

---

## 3. Verification & Test Execution

- **Tier 1 Targeted Test Scope (Rule 60)**:
  - Command: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --max-workers=2 --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.build.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.ui.components.core.*"`
  - Scope: Targeted tests across known locations UI, aftermath workout lists & filter criteria, ASPICE SWE build audits, theme centralization, and core components.
  - Result: **100% PASS** (0 failures, 0 errors, 0 regressions).
- **Living Documentation Governance**:
  - `python3 tools/verify_requirement_governance.py --sync-indexes && python3 tools/verify_requirement_governance.py`
  - Result: Master index tables synchronized with 100% parity (`docs/requirements.md`: 934 requirements, `docs/tests.md`: 936 tests).
- **Physical Device Execution**:
  - `adb devices` reported no physical devices attached. Per `Rule 79`, emulator launch was omitted.
