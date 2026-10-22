# Stage 4: Implementation - ATT-3577: Migrate Remaining Inline Cursor Extractions in WorkoutClusterEngine and EquipmentDataProvider to fromCursor Mappers

**Ticket**: [ATT-3577](https://rainerblind.atlassian.net/browse/ATT-3577)  
**Sub-task**: [ATT-3758](https://rainerblind.atlassian.net/browse/ATT-3758) (`[Implementation]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Active Sprint**: `2026-42.5`  
**Requirement Mapping**: `REQ-DAT-026`  
**Test Spec Mapping**: `TST-DAT-021`  
**Branch**: `feature/ATT-3577`  
**Author**: AI Agent 1 (Software Construction Engineer)  
**Date**: 2026-10-16  

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - `REQ-DAT-026` (*Authoritative Cursor Mappers for Workout Cluster Engine and Equipment Data Provider Architecture*), complementing `REQ-DAT-025`.
2. **Historical Origin & Commit Trace**:
   - Commit `776d3fc0` (`ATT-3366`) eliminated manual cursor column extractions in `RoutesDatabaseManager.kt` and `WorkoutClusterRepository.kt`, but left `WorkoutClusterEngine.kt` and `EquipmentDataProvider.kt` untouched, and placed `WorkoutBatchKey` and `fromCursorBatchKeys` directly inside `WorkoutClusterRepository.kt`.
3. **Root Reason for Existing Formulation**:
   - Incremental development focused primarily on the repository layer in ATT-3366, leaving engine loops and UI data providers as technical debt with duplicated inline `getColumnIndexOrThrow` calls.
4. **Preservation of Core Invariants**:
   - **Schema Invariance**: Zero SQLite schema or table alterations.
   - **Lifecycle Invariance**: Single-row `fromCursor` never advances or closes the cursor; callers retain `.use { ... }` ownership.
   - **Coordinate Nullability**: Null-safe bounds handling (`minLat`, `maxLat`, `minLng`, `maxLng`) and `hasPoints` boolean logic strictly preserved.
   - **Equipment Short-Circuiting**: Non-positive `equipmentId <= 0` continues to return `equipmentName = null` without querying `EquipmentDbHelper` (`REQ-STB-005`).

---

## 1. Summary of Changes

In accordance with the approved Stage 3 Implementation Plan for **ATT-3577**, this implementation delivers:

1. **Repository Hygiene**:
   - Removed stray files accidentally committed in `4f39e595` (`ATT-3362`):
     - `docs/attachments/com.atrainingtracker_issue_248c68bd7d93ef60ad269eaa8cf2c1e1_crash_session_6ACF7DAD038400010C9158367004874E_DNE_0_v2_stacktrace.txt`
     - `docs/engineering/backlog/ATT-3529_fix_pairing_item_resources_not_found_crash.md`

2. **DTO & Mapper Centralization in `WorkoutClusterDatabaseManager.kt`**:
   - Added `WorkoutBoundsDto(minLat, minLng, maxLat, maxLng)` with `hasPoints` and companion `fromCursor(cursor: Cursor)`.
   - Added `ClusterWorkoutCandidateDto(workoutId, workoutName, fileBaseName, sportId, distance)` with companion `fromCursor(cursor: Cursor)`.
   - Added `WorkoutEquipmentDto(sportId, equipmentId)` with companion `fromCursor(cursor: Cursor)`.
   - Moved `WorkoutBatchKey` and `WorkoutBatchKeys` data classes into `WorkoutClusterDatabaseManager.kt`.
   - Added `@JvmStatic fun fromCursorBatchKeys(cursor: Cursor): WorkoutBatchKeys` to `WorkoutClusterDatabaseManager.Companion`.

3. **Repository Decoupling in `WorkoutClusterRepository.kt`**:
   - Removed duplicate `WorkoutBatchKey` and `WorkoutBatchKeys` definitions.
   - Delegated companion `fromCursorBatchKeys(cursor: Cursor)` to `WorkoutClusterDatabaseManager.fromCursorBatchKeys(cursor)`.
   - Verified `WorkoutClusterRepository.kt` now has strictly **0 occurrences** of `getColumnIndex` or `getColumnIndexOrThrow`.

4. **Refactoring of `WorkoutClusterEngine.kt`**:
   - In `recalculateClusterAnchors`: Replaced manual column index extractions (lines 407–424) with `WorkoutBoundsDto.fromCursor(c)`.
   - In `clusterExistingWorkouts`: Replaced manual column index extractions (lines 512–527) with `ClusterWorkoutCandidateDto.fromCursor(c)`.
   - Verified `WorkoutClusterEngine.kt` now has strictly **0 occurrences** of `getColumnIndex` or `getColumnIndexOrThrow`.

5. **Refactoring of `EquipmentDataProvider.kt`**:
   - In `getEquipmentData`: Replaced raw `getColumnIndexOrThrow` lookups with `WorkoutEquipmentDto.fromCursor(cursor)`.
   - Verified `EquipmentDataProvider.kt` now has strictly **0 occurrences** of `getColumnIndex` or `getColumnIndexOrThrow`.

6. **Automated Contract & Unit Tests in `WorkoutClusterMapperTest.kt`**:
   - Added `testSourceCodeContract_zeroManualCursorExtractionsInTargetClasses` asserting 0 occurrences of `getColumnIndex(` or `getColumnIndexOrThrow(` across `WorkoutClusterEngine.kt`, `WorkoutClusterRepository.kt`, and `EquipmentDataProvider.kt`.
   - Added unit tests for `WorkoutBoundsDto.fromCursor` (valid and null coordinates, lifecycle checks).
   - Added unit tests for `ClusterWorkoutCandidateDto.fromCursor` (valid and null strings, lifecycle checks).
   - Added unit tests for `WorkoutEquipmentDto.fromCursor` and `EquipmentDataProvider.getEquipmentData`.

---

## 2. Verification Results

- `WorkoutClusterMapperTest`: 10/10 tests passed (100% pass rate).
- `EquipmentDbHelperResilienceTest`: 6/6 tests passed (100% pass rate).
- Stray files removed via `git rm`: Verified.
- Zero manual cursor extractions across all 3 target classes: Verified.
