# Stage 2: Requirement & Test Specification - ATT-7666: Fix OutOfMemoryError in PeriodsRepository and PeriodSummaryCard

**Ticket**: [ATT-7666](https://rainerblind.atlassian.net/browse/ATT-7666)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No Crashes & App Stability*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.6`  
**Requirement Mapping**: `REQ-PER-039` (*Lazy Decoded Path Resolution, Bounded Period Mini-Map Caching and Lite Mode Rendering*)  
**Test Spec ID**: `TST-PER-047`  
**Branch**: `feature/ATT-7666`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Requirement Specification (REQ-PER-039)

### 1.1 Problem Statement & Rationale
Eager `List<LatLng>` decoding in `PeriodsRepository.enrich()` into the singleton `_groupedPeriods` `StateFlow` and repeated OpenGL `GoogleMap` instantiation in `LazyColumn` period cards cause severe heap exhaustion (507MB / 512MB, <1% free) and thread leaks (~65 maps threads) during extended sessions.

### 1.2 Attributes
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit

### 1.3 Functional & Architectural Requirements
1. `PeriodsRepository.enrich()` SHALL NOT eagerly decode `LatLng` paths or retain populated collections in `workoutIdToPathMap`; `workoutIdToPathMap` in `PeriodSummary` SHALL remain empty in the singleton repository.
2. `PeriodSummary.polylines` SHALL be strictly bounded to `previewWorkouts` (`<= 8` tracks), and 4x `workoutIdToAllPolylinesMap` collection duplication across all hierarchy levels SHALL be eliminated.
3. `PeriodMapUtils` SHALL cache computed `PeriodCardMapState` in a thread-safe `LruCache<String, PeriodCardMapState>` (capacity: 64 entries) keyed by period sort key.
4. `PeriodSummaryCard.PeriodMultiWorkoutMap` SHALL pass `googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) }` when rendering vector polylines (`visuals.heatmapProvider == null`).

### 1.4 Acceptance Criteria (Given-When-Then)
- *Given* a database with historical periods across Days, Weeks, Months, and Years,
  *When* `PeriodsRepository.loadFromDatabase()` executes,
  *Then* `workoutIdToPathMap` remains empty in `_groupedPeriods`, eliminating eager `LatLng` retention.
- *Given* scrolling period cards in `LazyColumn`,
  *When* card mini-maps are composed,
  *Then* `GoogleMapOptions().liteMode(true)` is configured for vector polylines, and preview states are served from the bounded LRU cache without spawning redundant OpenGL threads.
- *Given* a user tapping a Period card map,
  *When* `onMapClick` is triggered,
  *Then* interactive `PeriodMapScreen` launches with full interactive zoom/pan capabilities.

### 1.5 System Invariants
Zero visual regression on period card thumbnails; tap-to-open `PeriodMapScreen` intact; bounded heap usage during continuous scrolling.

---

## 2. Test Specification (TST-PER-047)

### Test Case 1: Empty workoutIdToPathMap in Repository (`TST-PER-047.1`)
- **Scope**: Unit Test in `PeriodsRepositoryMemoryTest.kt`
- **Preconditions**: `PeriodsRepository` initialized with simulated workout polylines.
- **Action**: Run `enrich()`.
- **Expected Result**: `summary.workoutIdToPathMap.isEmpty()` is true.

### Test Case 2: Bounded polylines Count (`TST-PER-047.2`)
- **Scope**: Unit Test in `PeriodsRepositoryMemoryTest.kt`
- **Preconditions**: Period with > 10 workouts containing polylines.
- **Action**: Run `enrich()`.
- **Expected Result**: `summary.polylines.size <= 8` (`MAX_SUMMARY_THUMBNAIL_TRACKS`).

### Test Case 3: LRU Preview Cache Hit & Capacity (`TST-PER-047.3`)
- **Scope**: Unit Test in `PeriodMapUtilsTest.kt`
- **Preconditions**: `PeriodMapUtils` LRU cache configured.
- **Action**: Call `prepareCardMapPreviewSync` multiple times.
- **Expected Result**: Identical state returned on cache hit without re-decoding; cache size bounded to 64.

### Test Case 4: GoogleMap Lite Mode Configuration Audit (`TST-PER-047.4`)
- **Scope**: Unit / Contract Test in `PeriodSummaryCardTest.kt`
- **Preconditions**: Vector polyline period card.
- **Action**: Inspect `googleMapOptionsFactory`.
- **Expected Result**: Configured with `liteMode(true)`.

### Test Case 5: Tier 1 Targeted Regression Suite (`TST-PER-047.5`)
- **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*"`
- **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PER-047.1` | Unit | `PeriodsRepository.enrich` | `REQ-PER-039.1` | Specified |
| `TST-PER-047.2` | Unit | `PeriodsRepository.enrich` | `REQ-PER-039.2` | Specified |
| `TST-PER-047.3` | Unit | `PeriodMapUtils` | `REQ-PER-039.3` | Specified |
| `TST-PER-047.4` | Unit | `PeriodSummaryCard` | `REQ-PER-039.4` | Specified |
| `TST-PER-047.5` | Tier 1 Unit | `com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*` | `REQ-PRO-001` | Specified |
