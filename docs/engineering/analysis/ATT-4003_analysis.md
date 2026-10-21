# Stage 1 Analysis: ATT-4003 - [Verbesserung] [High] Remove blocking per-workout I/O from mapper and DB queries from composition

**Ticket**: [ATT-4003](https://rainerblind.atlassian.net/browse/ATT-4003)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.5` | **Branch**: `feature/ATT-4003`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`) | **Date**: 2026-10-21  

---

## 1. Problem Domain & Root Cause Analysis

1. **`WorkoutDataMapper` N+1 Queries & Read-Time DB Writes**:
   - `PeriodsRepository.getWorkoutsForRange` invokes single-item `mapper.fromCursor(c)`, executing ~65 individual SQLite queries per workout (extrema per sensor, sport, equipment, locations, laps). For a Year range with 300 workouts, this executes ~19,500 queries sequentially.
   - While `fromSnapshot` uses `BatchMetadata` for extrema, it still executes 8+ synchronous queries per workout: `SportTypeDatabaseManager` (3x), `EquipmentDbHelper` (1x), `KnownLocationsDatabaseManager` (2x), and `AthleteMetricsDatabaseManager` for FTP and Weight (2x).
   - `resolveAuthoritativeApex` (`PolyUtil.decode`) and `reconcileAltitudeExtrema` (`NumericalEncodingUtils.decodeDoubles`) decode track streams on every workout read and execute synchronous writes (`updateExtremaValue`) during read iterations.
2. **Synchronous DB Queries in Compose Composition (`WorkoutClustersTabsScreen`)**:
   - In `WorkoutClustersTabsScreen.kt`, `sportFilteredClusters.filter` calls `viewModel.getLinkedEquipmentSet(cluster.probableSportId)` inside Compose layout passes. This executes 4+ database queries synchronously on the Android Main thread on every recomposition and GPS fix (`currentLocation` emission).
3. **Redundant Full-Table Scans on Samples DB (`WorkoutRepository`)**:
   - Opening Aftermath scans the workout's samples table 6 times sequentially: 4 passes for `TrackType.entries` in `getWorkoutTrackPoints` plus 2 passes for HR and Power zone distributions. All 6 queries execute unprojected `SELECT *` (`db.query(tableName, null, ...)`), deserializing unused columns and causing memory bloat and disk I/O thrashing.

---

## 2. Call Site & Blast Radius Audit

| Component | Target File | Nature of Change & Impact |
| :--- | :--- | :--- |
| **Data Mapper** | `WorkoutDataMapper.kt` | Batch sport types, equipment, locations, and athlete metrics in `BatchMetadata`; decouple self-healing DB writes from batch read iterations. |
| **Periods Repo** | `PeriodsRepository.kt` | Refactor `getWorkoutsForRange` from $O(N \times 65)$ `fromCursor` loop to batch snapshot mapping (`readCursorSnapshot` + `BatchMetadata` + `fromSnapshot`). |
| **Clusters UI** | `WorkoutClustersTabsScreen.kt` | Remove synchronous `getLinkedEquipmentSet` calls from composition filter pipeline; observe precomputed state. |
| **Clusters VM** | `WorkoutClustersViewModel.kt` | Precompute sport-to-equipment map on `Dispatchers.IO` and expose as `StateFlow`; update filter matching to use in-memory map. |
| **Workout Repo** | `WorkoutRepository.kt` | Add column projections and consolidate multi-pass samples table scans for track points and zone distributions into single-pass/projected reads. |
| **Map Aftermath** | `TrackOnMapAftermathViewModel.kt` | Consume consolidated track query result without redundant sequential table scans. |

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Migrate `PeriodsRepository.getWorkoutsForRange` to batch snapshot mapping.
  2. Eliminate remaining per-workout DB queries in `WorkoutDataMapper.fromSnapshot` via expanded `BatchMetadata`.
  3. Precompute cluster equipment mappings on `Dispatchers.IO` to eliminate Main-thread DB queries in Compose.
  4. Project required columns and consolidate redundant samples table scans in `WorkoutRepository`.
  5. Decouple self-healing DB writes from read-path batch cursor mapping.
* **Out-of-Scope Non-Goals**:
  1. No changes to spatial clustering algorithms or cluster geometry (`WorkoutClusterEngine`).
  2. No changes to live workout recording or sensor acquisition (`TrackerService`, `SpeedAndLocationDevice`).
  3. No changes to UI visual design, chart rendering components, or theme styling.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**: Refines `REQ-DAT-009` (*Authoritative Altitude Extrema Reconciliation & Self-Healing*) and `REQ-MAP-020` (*Authoritative Workout Max Line Distance (Apex) Calculation & Self-Anchored Displacement Integrity*).
* **Historical Origin & Commit Trace**: `ATT-508` (`b4f69aef` / `f3122721`) for altitude extrema reconciliation; `ATT-528` (`d4a22c01`) for authoritative apex derivation; `ATT-359` / `ATT-2309` for batch metadata.
* **Root Reason for Existing Formulation**: Self-healing checks were embedded directly into `WorkoutDataMapper` as an expedient lazy-repair hook for legacy corrupted records. However, running string decoding and database writes during batch read queries creates severe jank and N+1 query multiplication.
* **Preservation of Core Invariants**: Correctness of apex coordinates and altitude extrema is preserved (reconciliation preserved for single-workout loading / background migration); mapping symmetry between single and batch modes is maintained; cluster filter results remain 100% identical.

---

## 5. System Invariants & Risk Assessment

* **Core Invariants**:
  1. `fromCursor` and `fromSnapshot` mapping symmetry MUST be strictly preserved (`WorkoutDataMappingSymmetryTest`, Rule 20).
  2. Cluster equipment filtering criteria and results MUST remain 100% identical.
  3. Track points and HR/Power zone distribution calculations MUST yield identical telemetry values and percentages.
  4. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **MEDIUM** — Touches core data mapping and repository pipelines across Aftermath and Clusters; requires strict verification of mapping symmetry and telemetry parity, but does not alter SQLite schema or live tracking services.
