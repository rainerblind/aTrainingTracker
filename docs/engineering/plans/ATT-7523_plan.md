# Stage 3: Implementation Plan - ATT-7523: ClimbDeduplicator Spatial Indexing & UI Lag Fix

**Ticket**: [ATT-7523](https://rainerblind.atlassian.net/browse/ATT-7523) | **Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-2565](https://rainerblind.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5`  
**Requirement Mapping**: `REQ-CLM-011` (`REQ-CLM-011.1`, `REQ-CLM-011.3`)  
**Test Mapping**: `TST-CLM-011` (`TST-CLM-011.1`..`TST-CLM-011.5`)  
**Branch**: `feature/ATT-7523` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-12  

---

## 1. Problem Description & Background
During physical Pixel 10 review of ATT-7311, opening Segmente, Routen, and Anstiege (climbs) suffered multi-second UI freezes due to unindexed quadratic spatial checks in `ClimbDeduplicator`, unpruned polyline segment searches in corridor checks, and redundant deduplication passes in `MapFragmentWithTrackViewModel`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-CLM-011` (`REQ-CLM-011.1` Bounding Box Pre-Filter & Segment Decimation; `REQ-CLM-011.3` Single Repository-Level Caching)
* **Test Mapping**: `TST-CLM-011` (`TST-CLM-011.1`..`TST-CLM-011.5` in `docs/tests/tst_maps_navigation.md`)

---

## 3. System Invariants & Preserved Behavior
1. **Detection Parity**: 100m endpoint proximity, $45^\circ$ heading alignment, 50% elevation gain compatibility, and $\ge 70\%$ corridor overlap tolerances are strictly preserved.
2. **Database Invariance**: `Climbs.db` SQLite schema v1 remains unchanged; zero DB migration required.
3. **No UI Thread Blocking**: Repository deduplication remains strictly offloaded to worker coroutines.
4. **Parent Human Gate Invariance**: Final completion reserved exclusively for human PO in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: Model Layer (`Climb.kt`)
* Define `GeoBoundingBox(val minLat: Double, val maxLat: Double, val minLng: Double, val maxLng: Double)` with `fun overlaps(other: GeoBoundingBox, marginMeters: Double = 100.0): Boolean`.
* Add cached property `val boundingBox: GeoBoundingBox` on `Climb` lazily computed across start/end and `pathPoints`.

### Component 2: Geometric Deduplication Engine (`ClimbDeduplicator.kt`)
* In `isDuplicateOrOverlapping`, insert $O(1)$ fast bounding box pre-filtering check: return `false` immediately if bounding boxes expanded by $\ge 100\text{m}$ do not intersect.
* In corridor overlap check, prune candidate segments outside sample point bounding boxes and apply early-exit counters for match ratio.

### Component 3: ViewModel Layer (`MapFragmentWithTrackViewModel.kt`)
* In `mapClimbsFlow`, consume `liveClimbsRepository.allStoredClimbs` directly without re-invoking `ClimbDeduplicator.deduplicateForDisplay`.

### UI Consistency (Rule 23)
* No UI changes (algorithmic performance optimization and ViewModel stream cleanup only).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Model & Bounding Box (`Climb.kt`, `ClimbDeduplicator.kt`)
* Add `GeoBoundingBox` and `Climb.boundingBox`. Add bounding box overlap fast pre-filter in `ClimbDeduplicator.isDuplicateOrOverlapping`.
* Unit test: `ClimbDeduplicatorTest.kt` verifying $O(1)$ distant climb rejection.

### Step 2: Corridor Segment Pruning & Performance Benchmark (`ClimbDeduplicator.kt`)
* Implement segment pruning and early-exit logic during corridor overlap analysis.
* Add benchmark test `TST-CLM-011.5` in `ClimbDeduplicatorTest.kt` asserting $\le 100\text{ms}$ execution for 300 climbs.

### Step 3: ViewModel Redundant Pass Elimination (`MapFragmentWithTrackViewModel.kt`)
* Stream `allStoredClimbs` directly in `mapClimbsFlow`. Update `MapFragmentWithTrackViewModelTest.kt` to verify single-pass emission.

### Step 4: Full Regression Verification
* Execute targeted tests: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.climbs.*" --tests "com.atrainingtracker.trainingtracker.database.Climbs*" --tests "com.atrainingtracker.trainingtracker.ui.map.MapFragmentWithTrackViewModel*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Run targeted climb and map unit test suites under `parallel_sprint_lock.py`, verifying 100% pass and sub-100ms benchmark.
* **Rollback**: Branch isolation allows full revert without impacting `develop` or parallel lanes.
