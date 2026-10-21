# Stage 3: Implementation Plan - ATT-4003: [Verbesserung] [High] Remove blocking per-workout I/O from mapper and DB queries from composition

**Ticket**: [ATT-4003](https://rainerblind.atlassian.net/browse/ATT-4003) | **Sub-task**: Pending Submission (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.5` | **Branch**: `feature/ATT-4003`  
**Requirement Mapping**: `REQ-DAT-033` (amending `REQ-DAT-009`, `REQ-MAP-020`) | **Test Mapping**: `TST-DAT-028`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`) | **Date**: 2026-10-21  

---

## 1. Problem Description & Background
1. `PeriodsRepository.getWorkoutsForRange` invokes single-item `mapper.fromCursor`, executing ~65 queries per workout (~19,500 queries for 300 workouts). `WorkoutDataMapper.fromSnapshot` executes 8+ queries per row and triggers synchronous DB writes (`updateExtremaValue`) and stream decodes on read paths.
2. `WorkoutClustersTabsScreen` calls `viewModel.getLinkedEquipmentSet` in Compose layout passes, running synchronous SQLite queries on the Main thread on every GPS fix / recomposition.
3. `WorkoutRepository` executes 6 sequential unprojected `SELECT *` scans on samples during Aftermath loading.

---

## 2. Proposed Architectural Changes (SWE.2)

| Layer / Class | Architectural Responsibility & Modification |
| :--- | :--- |
| **Data Mapper**<br>`WorkoutDataMapper.kt` | Expand `BatchMetadata` to hold pre-fetched `sportTypes`, `equipmentNames`, `locationNames`, and `athleteMetrics` (FTP/weight); add `reconcile: Boolean = false` parameter to `fromSnapshot` to skip read-time `PolyUtil.decode` and DB writes during batch mapping; support `currentCoroutineContext().ensureActive()`. |
| **Periods Repo**<br>`PeriodsRepository.kt` | Refactor `getWorkoutsForRange(startS, endS)` to batch pipeline: read `RawCursorSnapshot`s, pre-fetch metadata for chunk IDs in one pass on `Dispatchers.IO`, and map via `fromSnapshot(snapshot, batch, reconcile = false)`. |
| **Clusters VM**<br>`WorkoutClustersViewModel.kt` | Precompute `linkedEquipmentBySport: StateFlow<Map<Long, Set<String>>>` on `Dispatchers.IO` via `flowOn(Dispatchers.IO)` and expose cached in-memory accessor `getLinkedEquipmentSetCached(sportId)`. |
| **Clusters UI**<br>`WorkoutClustersTabsScreen.kt` | Collect `linkedEquipmentBySport` from ViewModel; evaluate equipment filter in-memory inside `filterCriteria.matches(...)` with zero Main-thread DB queries. |
| **Workout Repo**<br>`WorkoutRepository.kt` | Add explicit column projections to `getWorkoutTrackPoints` and zone distributions; implement single-pass projected samples loader `loadWorkoutDetailTelemetry` consolidating track and zone extraction. |
| **Map Aftermath**<br>`TrackOnMapAftermathViewModel.kt` | Consume consolidated or projected sample data without redundant sequential full-table scans. |

### UI Consistency (Rule 23)
* **Reference**: `WorkoutClustersTabsScreen.kt` (Material 3 Tabs, HorizontalPager, filter chips).
* **Modifications**: Zero visual or UI layout changes; strictly replaces blocking ViewModel call with in-memory state read.

---

## 3. Step-by-Step Implementation Sequence (Stage 4)

### Step 1: Expand `BatchMetadata` & Non-Reconciling Mode in `WorkoutDataMapper.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDataMapper.kt`
* Add fields to `BatchMetadata`: `sportTypes: Map<Long, SportTypeInfo>`, `equipmentNames: Map<Long, String>`, `locationNames: Map<LatLng, String>`, `athleteMetrics: Map<Long, AthleteMetricsInfo>`.
* In `fromSnapshot(snapshot, batch, reconcile: Boolean = false)`: resolve sports, equipment, locations, and athlete metrics from `batch` lookup; when `reconcile == false`, bypass `PolyUtil.decode` and `updateExtremaValue` DB writes.

### Step 2: Batch Mapping Pipeline in `PeriodsRepository.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsRepository.kt`
* In `getWorkoutsForRange`: extract snapshots via `mapper.readCursorSnapshot(c)`; pre-fetch chunk extrema, strava, cluster names, laps, sport types, and equipment; map via `fromSnapshot(snapshot, batch, reconcile = false)` with `ensureActive()`.

### Step 3: Non-Blocking Equipment Precomputation in Clusters VM & Tabs Screen
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersViewModel.kt`, `WorkoutClustersTabsScreen.kt`
* ViewModel: add `linkedEquipmentBySport` StateFlow computed on `Dispatchers.IO`.
* Tabs Screen: collect `linkedEquipmentBySport` and pass in-memory lookup to `filterCriteria.matches(...)`.

### Step 4: Projected & Consolidated Samples Telemetry Loading in `WorkoutRepository.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt`, `TrackOnMapAftermathViewModel.kt`
* Add explicit `String[]` projections to `getWorkoutTrackPoints`, `getHeartRateZoneDistribution`, `getPowerZoneDistribution`.
* Add `loadWorkoutDetailTelemetry(...)` for single-pass extraction of tracks and zone samples.

### Step 5: Unit Testing & Verification (`TST-DAT-028`)
* Tests: `WorkoutDataMappingSymmetryTest.kt`, `WorkoutClustersViewModelTest.kt`, `WorkoutRepositoryTelemetryTest.kt`, `PeriodsRepositorySyncTest.kt`.
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.ui.clusters.*"`

---

## 4. System Invariants & Rollback Plan
* **Invariants**: Single-row `fromCursor` and batch `fromSnapshot` mapping parity (Rule 20); identical cluster filter results; exact telemetry and zone distribution parity; clean-room 100% test pass rate.
* **Rollback Plan**: Changes confined to `feature/ATT-4003`; branch isolation permits clean checkout or git revert.
