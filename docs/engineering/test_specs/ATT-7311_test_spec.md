# Stage 2: Requirement & Test Specification - ATT-7311: [Bug] Deduplicate overlapping climbs in Climbs.db and map layer after historical backfill

**Ticket**: [ATT-7311](https://rainerblind.atlassian.net/browse/ATT-7311)  
**Parent Epic**: [ATT-2565](https://rainerblind.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.4`  
**Requirement Mapping**: `REQ-CLM-011` (*Deduplicate Overlapping Climbs in Climbs.db & Map Layer*)  
**Test Spec ID**: `TST-CLM-011`  
**Branch**: `subagent-Lane-3-Agent---ATT-7311-self-b7801336`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-CLM-011)

### 1.1 Problem Statement & Rationale
During Sprint 2026-46.3 Joint Review of ATT-6692, the Product Owner observed duplicate climbs on the map and in `Climbs.db` on device. Spatial deduplication in `ClimbsDatabaseManager.insertClimbsWithDeduplicationBatch` previously relied on an overly strict 50m tolerance between start and summit coordinates simultaneously (`startDist <= 50m && endDist <= 50m`). Repeated passes, historical backfills across identical road segments, or GPX route imports versus outdoor workout GPS tracks drift by more than 50m due to GPS jitter and barometric detection latency, failing to match existing climb records. Furthermore, the engine lacked corridor overlap deduplication for ascents along the same road segment, existing databases retained duplicate rows from prior backfills, and the map layer lacked display-level deduplication.

### 1.2 Functional & Architectural Requirements

1. **Spatial Endpoint & Corridor Overlap Deduplication (`REQ-CLM-011.1`)**:
   - In `ClimbsDatabaseManager.insertClimbWithDeduplication` and `insertClimbsWithDeduplicationBatch`, candidate climbs SHALL be evaluated against existing climbs using expanded endpoint tolerance ($\le 100\text{ m}$) AND corridor overlap analysis.
   - Two climbs $A$ and $B$ SHALL be classified as duplicates / overlapping if:
     - (a) Both start coordinates are within $\le 100\text{ m}$ AND both summit coordinates are within $\le 100\text{ m}$; OR
     - (b) The start-to-summit heading difference is $\le 45^\circ$, and the spatial polyline overlap between the two climbs is $\ge 70\%$ of the shorter climb's length within a perpendicular cross-track corridor of $\le 40\text{ m}$.
   - When a candidate climb matches an existing climb:
     - If the candidate climb has an associated `routeId != null` and the existing climb has `routeId == null`, the existing record SHALL be updated with the `routeId`.
     - If the candidate climb provides longer distance or higher elevation gain while maintaining physical plausibility, the existing record SHALL be updated with the superior climb geometry.
     - The existing climb ID SHALL be returned without inserting a redundant row.

2. **Idempotent Historical Database Consolidation Migration (`REQ-CLM-011.2`)**:
   - `ClimbsDatabaseManager` SHALL provide `suspend fun deduplicateExistingClimbs(thresholdMeters: Float = 100.0f): Int`, scanning all stored climbs in `Climbs.db`, identifying duplicate/overlapping climb clusters, consolidating metadata/route IDs, deleting redundant rows, and returning the count of purged records.
   - `WorkoutRepository` SHALL gate a one-time startup consolidation pass on boolean preference flag `climbs_workout_deduplication_v3` (`PREF_CLIMBS_WORKOUT_DEDUPLICATION_V3`). When false, it SHALL execute `climbsDb.deduplicateExistingClimbs()`, set `climbs_workout_deduplication_v3` to true, and invoke `LiveClimbsRepository.refreshClimbs()` if any duplicate climbs were purged. Subsequent calls SHALL be O(1) no-ops.

3. **Map Layer Display Deduplication Parity (`REQ-CLM-011.3`)**:
   - In `MapFragmentWithTrackViewModel.kt`, `mapClimbsFlow` SHALL filter candidate climbs for display such that if any overlapping or duplicate ascents exist in memory, only the canonical non-overlapping climb is emitted to `uiState.climbs`, guaranteeing zero visually overlapping duplicate climb polylines on `MapScreenWithTrack`.

### 1.3 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Endpoint & Corridor Deduplication on Ingestion)**:
  - *Given* two GPS recordings of the same hill where start coordinates differ by 75m and summits differ by 65m,
  - *When* `insertClimbsWithDeduplicationBatch` evaluates the candidate climb,
  - *Then* the climb is matched as an existing ascent and zero duplicate rows are inserted into `Climbs.db`.
* **Criterion 2 (Corridor Overlap Deduplication)**:
  - *Given* an existing climb in `Climbs.db` and a candidate climb sharing $\ge 70\%$ corridor overlap in the same heading direction,
  - *When* insertion is attempted,
  - *Then* the candidate climb is deduplicated against the existing ascent.
* **Criterion 3 (Idempotent Database Cleanup)**:
  - *Given* a device with existing duplicate climbs in `Climbs.db` from historical backfill,
  - *When* `WorkoutRepository.triggerHistoricalClimbBackfillIfPending()` runs with unset `climbs_workout_deduplication_v3`,
  - *Then* `deduplicateExistingClimbs()` purges the redundant entries, marks `climbs_workout_deduplication_v3 = true`, and refreshes `LiveClimbsRepository`.
* **Criterion 4 (Map Layer Non-Overlapping Polylines)**:
  - *Given* `MapScreenWithTrack` with `GeneralMapLayer.CLIMBS` enabled,
  - *When* climbs are rendered on the map,
  - *Then* only distinct, non-overlapping climb polylines are rendered.

### 1.4 System Invariants
1. `Climbs.db` schema version 1 SQLite structure and column definitions remain intact.
2. Route climb associations (`routeId`) are never dropped during deduplication or consolidation.
3. Thread safety on `Dispatchers.IO` and coroutine mutexes.
4. 100% test pass rate across existing climb suites.

---

## 2. Test Specification (TST-CLM-011)

### Test Case 1: `testEndpointAndCorridorOverlapDeduplication` (`TST-CLM-011.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/database/ClimbsDatabaseManagerTest.kt`
* **Preconditions**: In-memory or mocked `ClimbsDatabaseManager`.
* **Action**:
  1. Insert climb A (1500m length, Cat 4).
  2. Attempt insertion of climb B with start 70m away, summit 60m away (endpoint match).
  3. Attempt insertion of climb C with start 120m away but $\ge 70\%$ polyline overlap in same heading.
  4. Attempt insertion of climb D with opposite heading (180° bearing difference).
* **Expected Result**:
  - Climbs B and C return existing climb A ID; no new rows inserted.
  - Climb D inserts as a new distinct record.

### Test Case 2: `testDeduplicateExistingClimbs_purgesDuplicatesAndPreservesRouteId` (`TST-CLM-011.2`)
* **Scope**: Database & Migration Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/database/ClimbsDatabaseManagerTest.kt` and `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutHistoricalClimbBackfillTest.kt`
* **Preconditions**: Database populated with duplicate entries of the same hill (one with `routeId = 10L`, one with `routeId = null`).
* **Action**: Invoke `deduplicateExistingClimbs()`.
* **Expected Result**:
  - Purged count is 1.
  - Remaining climb retains `routeId = 10L`.
  - Database contains exactly 1 canonical record.

### Test Case 3: `testMapFragmentWithTrackViewModel_displayDeduplication` (`TST-CLM-011.3`)
* **Scope**: ViewModel StateFlow Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapFragmentWithTrackViewModelTest.kt`
* **Preconditions**: Mock `LiveClimbsRepository.allStoredClimbs` emitting 2 overlapping climbs.
* **Action**: Enable `GeneralMapLayer.CLIMBS`.
* **Expected Result**:
  - `uiState.climbs` emits exactly 1 deduplicated climb for map highlight rendering.

### Test Case 4: Localization & Invariant Audit (`TST-CLM-011.4`)
* **Scope**: Localization & Code Cleanliness
* **Goal**: Verify zero hardcoded strings and 100% parity across all 9 locales for climb UI components.

### Test Case 5: Tier 1 Targeted Regression Suite (`TST-CLM-011.5`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.Climbs*" --tests "com.atrainingtracker.trainingtracker.climbs.*" --tests "com.atrainingtracker.trainingtracker.ui.map.MapFragmentWithTrackViewModel*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutHistoricalClimbBackfillTest"`
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-CLM-011.1` | Unit | `ClimbsDatabaseManager.insertClimbsWithDeduplicationBatch` | `REQ-CLM-011.1` | Specified |
| `TST-CLM-011.2` | DB / Migration | `ClimbsDatabaseManager.deduplicateExistingClimbs` | `REQ-CLM-011.2` | Specified |
| `TST-CLM-011.3` | ViewModel | `MapFragmentWithTrackViewModel.mapClimbsFlow` | `REQ-CLM-011.3` | Specified |
| `TST-CLM-011.4` | Localization | `TranslationParityTest` | `REQ-CLM-011`, `REQ-UI-106` | Specified |
| `TST-CLM-011.5` | Regression (Tier 1) | Targeted climb & map test suites | `REQ-PRO-001` | Specified |
