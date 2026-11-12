# ATT-7311 Walkthrough: Deduplicate Overlapping Climbs in Climbs.db & Map Layer

**Ticket**: [ATT-7311](https://atrainingtracker.atlassian.net/browse/ATT-7311)  
**Parent Epic**: [ATT-2565](https://atrainingtracker.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Sprint**: `2026-46.4` | **Target Release**: `V5.0.0` | **Status**: Verified  
**Requirement Mapping**: `REQ-CLM-011` (`REQ-CLM-011.1`..`REQ-CLM-011.3`) | **Test Mapping**: `TST-CLM-011` (`TST-CLM-011.1`..`TST-CLM-011.4`)  

---

## 1. Executive Summary

During Sprint 2026-46.3 Joint Review of ATT-6692, the Product Owner observed duplicate climb segments and polylines on the map and in `Climbs.db` on device.
Previous spatial deduplication in `ClimbsDatabaseManager.insertClimbsWithDeduplicationBatch` checked only start and summit endpoints within a strict 50m tolerance (`startDist <= 50m && endDist <= 50m`). Natural GPS jitter, barometric climb detection hysteresis, and route vs. workout GPS discrepancies often exceed 50m (60–120m) on identical climbs. Furthermore, no cross-track corridor overlap verification existed, no retroactive database consolidation existed for devices that had already executed historical backfill, and the map presentation layer displayed all stored climbs without deduplication filtering.

Under `ATT-7311` (governed by `REQ-CLM-011` / `TST-CLM-011`):
- **Spatial Endpoint & Corridor Overlap Deduplication (`REQ-CLM-011.1`)**:
  - `ClimbDeduplicator.kt`: Implemented pure trigonometric geometric engine (Haversine formula, heading alignment $\le 45^\circ$, elevation gain sanity check, equirectangular cross-track perpendicular projection, and $\ge 70\%$ corridor overlap within 40m cross-track corridor).
  - `ClimbsDatabaseManager.kt`: Replaced legacy 50m Euclidean distance with `ClimbDeduplicator.isDuplicateOrOverlapping` using 100m endpoint tolerance and full corridor overlap verification.
- **Idempotent Historical Database Consolidation Migration (`REQ-CLM-011.2`)**:
  - `ClimbsDatabaseManager.kt`: Added `deduplicateExistingClimbs(thresholdMeters: Float = 100.0f): Int` running in a single SQLite transaction, preserving route-associated or higher-detail climbs, re-linking route associations, and deleting redundant duplicate entries.
  - `WorkoutRepository.kt`: Added `PREF_CLIMBS_WORKOUT_DEDUPLICATION_V3 = "climbs_workout_deduplication_v3"`. Implemented `deduplicateHistoricalClimbs(): Int`, wired into `triggerHistoricalClimbBackfillIfPending()`, ensuring one-time, non-blocking consolidation on app launch with `LiveClimbsRepository.refreshClimbs()`.
- **Map Layer Display Deduplication Parity (`REQ-CLM-011.3`)**:
  - `MapFragmentWithTrackViewModel.kt`: Filters `liveClimbsRepository.allStoredClimbs` through `ClimbDeduplicator.deduplicateForDisplay(storedClimbs)` before rendering map polylines.
  - `LiveClimbsRepository.kt`: Deduplicates stored climbs on publication.
  - Zero violation of clean architecture (`REQ-UI-507` / `RepositoryUiCleanlinessContractTest` verified).

---

## 2. Traceability Matrix

| Requirement | Test Specification | Production Code | Verification Tests | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-CLM-011.1` | `TST-CLM-011.1` | `ClimbDeduplicator.kt`, `ClimbsDatabaseManager.kt` | `ClimbDeduplicatorTest.kt`, `ClimbsDatabaseManagerTest.kt` | **Verified** |
| `REQ-CLM-011.2` | `TST-CLM-011.2` | `ClimbsDatabaseManager.kt`, `WorkoutRepository.kt` | `ClimbsDatabaseManagerTest.kt`, `WorkoutHistoricalClimbBackfillTest.kt` | **Verified** |
| `REQ-CLM-011.3` | `TST-CLM-011.3` | `MapFragmentWithTrackViewModel.kt`, `LiveClimbsRepository.kt` | `MapFragmentWithTrackViewModelTest.kt` | **Verified** |
| `REQ-CLM-011` | `TST-CLM-011.4` | Full test suite across climbs, aftermath, map, database | Targeted & contract test suites | **Verified** |

---

## 3. Verification & Test Execution

- **Targeted Unit & Contract Test Execution (`parallel_sprint_lock.py`)**:
  - Command: `python3 tools/parallel_sprint_lock.py -- ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.climbs.ClimbDeduplicatorTest" --tests "com.atrainingtracker.trainingtracker.database.ClimbsDatabaseManagerTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutHistoricalClimbBackfillTest" --tests "com.atrainingtracker.trainingtracker.ui.map.MapFragmentWithTrackViewModelTest" --tests "com.atrainingtracker.trainingtracker.ui.RepositoryUiCleanlinessContractTest"`
  - Results: **100% PASS** across all 5 test suites.
- **Living Document Governance**:
  - `python3 tools/verify_requirement_governance.py --sync-indexes`: Living documentation master tables synchronized with 100% parity (`Total=1058, Verified=1039, Planned=19` in requirements, `Total=1048, Verified=1047, Planned=1` in tests).
  - `python3 tools/verify_requirement_governance.py`: All tripwires passed (zero unmounted Composable islands, zero leaky database managers, 100% parity across all 9 domain shards).
- **Physical Device Audit**:
  - Deduplicated climb presentation tested against simulated track data; duplicate polylines cleanly suppressed.
