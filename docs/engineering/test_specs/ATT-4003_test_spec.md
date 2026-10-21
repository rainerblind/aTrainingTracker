# Stage 2: Requirement & Test Specification - ATT-4003: [Verbesserung] [High] Remove blocking per-workout I/O from mapper and DB queries from composition

**Ticket**: [ATT-4003](https://rainerblind.atlassian.net/browse/ATT-4003) | **Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111)  
**Target Release**: `V4.9.40` | **Sprint**: `2026-43.5` | **Branch**: `feature/ATT-4003`  
**Requirement Mapping**: `REQ-DAT-033` (amending `REQ-DAT-009`, `REQ-MAP-020`) | **Test Spec ID**: `TST-DAT-028`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`) | **Date**: 2026-10-21  

---

## 1. Requirement Specification (REQ-DAT-033)

### 1.1 Problem Statement & Rationale
`WorkoutDataMapper` and `PeriodsRepository.getWorkoutsForRange` execute $O(N \times 65)$ queries and read-time DB writes during cursor iteration. `WorkoutClustersTabsScreen` runs synchronous SQLite queries during Compose layout passes on every GPS fix. `WorkoutRepository` executes 6 unprojected `SELECT *` scans on samples during Aftermath load.

### 1.2 Functional & Architectural Requirements
1. **Batch Mapping Parity (`REQ-DAT-033.1`)**: `PeriodsRepository.getWorkoutsForRange` SHALL map workouts via `RawCursorSnapshot` + `BatchMetadata` + `fromSnapshot`. `BatchMetadata` SHALL pre-fetch sports, equipment, locations, and athlete metrics without per-workout DB queries.
2. **Decoupled Read-Time Writes (`REQ-DAT-033.2`)**: Batch mapping SHALL NOT execute synchronous SQLite writes (`updateExtremaValue`) during read iterations.
3. **Non-Blocking Cluster Equipment Filtering (`REQ-DAT-033.3`)**: `WorkoutClustersViewModel` SHALL precompute sport-equipment mappings on `Dispatchers.IO` and expose in-memory state; `WorkoutClustersTabsScreen` SHALL evaluate equipment filters in-memory on Main thread without DB queries.
4. **Projected Samples DB Consolidation (`REQ-DAT-033.4`)**: `WorkoutRepository` track and zone queries SHALL use explicit column projections and consolidate multi-pass scans.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Batch Mapping)**: *Given* 300 workouts in a range, *When* `getWorkoutsForRange` is called, *Then* it executes batch snapshot mapping with zero per-workout N+1 DB queries or read-time writes.
* **Criterion 2 (Compose Main-Thread Safety)**: *Given* `WorkoutClustersTabsScreen` displayed, *When* a GPS fix arrives, *Then* equipment filtering evaluates in-memory with zero Main-thread DB queries.
* **Criterion 3 (Projected Samples Loading)**: *Given* a workout opened in Aftermath, *When* track points and zones load, *Then* `WorkoutRepository` queries samples with explicit column projections without redundant full-table re-scans.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Refines `REQ-DAT-009` and `REQ-MAP-020` across `WorkoutDataMapper.kt`, `PeriodsRepository.kt`, `WorkoutClustersTabsScreen.kt`, `WorkoutClustersViewModel.kt`, `WorkoutRepository.kt`, and `TrackOnMapAftermathViewModel.kt`.
* `Historical Origin & Commit Trace`: `ATT-508` (`b4f69aef` / `f3122721`) for altitude reconciliation; `ATT-528` (`d4a22c01`) for apex distance derivation; `ATT-359` / `ATT-2309` for batch metadata; `ATT-1402` for cluster equipment filtering.
* `Root Reason for Existing Formulation`: Self-healing writes and direct ViewModel DB calls were added as expedient lazy hooks; samples queries omitted column projections.
* `Preservation of Core Invariants`: Correctness of apex coordinates, altitude extrema, cluster filter results, and mapping symmetry (`WorkoutDataMappingSymmetryTest`, Rule 20) are strictly preserved.

---

## 2. Test Specification (TST-DAT-028)

### Test Case 1: Batch Mapping Symmetry & Zero Read-Time Writes (`TST-DAT-028.1`)
* **Scope**: Unit & Contract Test (`WorkoutDataMappingSymmetryTest.kt`, `PeriodsRepositorySyncTest.kt`)
* **Preconditions**: Cursor with populated workout summaries, extrema, sports, and equipment.
* **Action**: Map via `fromCursor` and via `readCursorSnapshot` + expanded `BatchMetadata` + `fromSnapshot`.
* **Expected Result**: 100% field parity (Rule 20); zero SQLite writes during batch mapping iteration.

### Test Case 2: Cluster Equipment Non-Blocking In-Memory Filter (`TST-DAT-028.2`)
* **Scope**: Unit Test (`WorkoutClustersViewModelTest.kt`)
* **Preconditions**: Mock discovery manager with sport-equipment links.
* **Action**: Verify `WorkoutClustersViewModel` exposes precomputed equipment map; evaluate filter matches.
* **Expected Result**: Matches identical to direct lookup; zero DB calls during filter evaluation.

### Test Case 3: Projected Samples Telemetry Loading (`TST-DAT-028.3`)
* **Scope**: Integration Test (`WorkoutRepositoryTelemetryTest.kt`, `WorkoutRepositoryStreamTest.kt`)
* **Preconditions**: Recorded workout with samples table.
* **Action**: Load track points and zone distributions.
* **Expected Result**: Explicit column projections used; telemetry coordinates and zone percentages match 100%.

### Test Case 4: Clean-Room Regression Suite (`TST-DAT-028.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Expected Result**: 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-DAT-028.1]` | Unit / Contract | `WorkoutDataMapper.fromSnapshot`, `PeriodsRepository.getWorkoutsForRange` | `REQ-DAT-033.1`, `REQ-DAT-033.2` | Planned |
| `[TST-DAT-028.2]` | Unit | `WorkoutClustersViewModel.linkedEquipmentMap`, `WorkoutClustersTabsScreen` | `REQ-DAT-033.3` | Planned |
| `[TST-DAT-028.3]` | Integration | `WorkoutRepository.getWorkoutTrackPoints`, `getHeartRateZoneDistribution` | `REQ-DAT-033.4` | Planned |
| `[TST-DAT-028.4]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001`, `REQ-DAT-033.5` | Planned |
