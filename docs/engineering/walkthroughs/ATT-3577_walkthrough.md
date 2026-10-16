# Stage 5 Walkthrough: Migrate Remaining Inline Cursor Extractions in WorkoutClusterEngine and EquipmentDataProvider to fromCursor Mappers

## Ticket Reference
- **Ticket ID**: ATT-3577
- **Subtask ID**: ATT-3759
- **Branch**: `feature/ATT-3577`
- **Requirement ID**: `REQ-DAT-026`
- **Test Specification ID**: `TST-DAT-021`

---

## 1. Executive Summary
In compliance with `REQ-DAT-026`, Rule 20 (Database & DTO Mapping Symmetry), Rule 36 (Single Source of Truth & DRY), and Clean Architecture principles, all remaining manual inline `getColumnIndex` and `getColumnIndexOrThrow` cursor column extractions have been completely eliminated from `WorkoutClusterEngine.kt`, `EquipmentDataProvider.kt`, and `WorkoutClusterRepository.kt`.

All low-level cursor extraction logic is now centralized in canonical DTOs (`WorkoutBoundsDto`, `ClusterWorkoutCandidateDto`, `WorkoutEquipmentDto`, and `WorkoutBatchKey`) in `WorkoutClusterDatabaseManager.kt`.

Additionally, the two stray scratch and backlog files accidentally added to the repository in commit `4f39e595` (`ATT-3362`) have been removed via `git rm`.

Comprehensive static contract tests and unit tests in `WorkoutClusterMapperTest.kt` enforce zero manual column lookups and verify 100% mapper fidelity. The full clean-room unit test suite (3014+ tests) passes with zero regressions.

---

## 2. Changes Implemented

### 2.1 Repository Hygiene
- Excised stray files from the git repository tree:
  - `docs/attachments/com.atrainingtracker_issue_248c68bd7d93ef60ad269eaa8cf2c1e1_crash_session_6ACF7DAD038400010C9158367004874E_DNE_0_v2_stacktrace.txt`
  - `docs/engineering/backlog/ATT-3529_fix_pairing_item_resources_not_found_crash.md`

### 2.2 Production Source Refactorings
- **`WorkoutClusterDatabaseManager.kt`**:
  - Added typed data classes with authoritative `fromCursor` companion mappers:
    - `WorkoutBoundsDto(minLat, minLng, maxLat, maxLng)` with null-safe coordinates and `hasPoints` boolean.
    - `ClusterWorkoutCandidateDto(workoutId, workoutName, fileBaseName, sportId, distance)`.
    - `WorkoutEquipmentDto(sportId, equipmentId)`.
    - Centralized `WorkoutBatchKey(id, fileName, clusterId)` and `WorkoutBatchKeys`.
  - Added `@JvmStatic fun fromCursorBatchKeys(cursor: Cursor): WorkoutBatchKeys` to `WorkoutClusterDatabaseManager.Companion`.
- **`WorkoutClusterRepository.kt`**:
  - Removed internal duplicate declarations of `WorkoutBatchKey` and `WorkoutBatchKeys`.
  - Delegated `WorkoutClusterRepository.fromCursorBatchKeys(cursor: Cursor)` to `WorkoutClusterDatabaseManager.fromCursorBatchKeys(cursor)`.
  - Statically verified: **0 occurrences** of `getColumnIndex` or `getColumnIndexOrThrow` in `WorkoutClusterRepository.kt`.
- **`WorkoutClusterEngine.kt`**:
  - `recalculateClusterAnchors`: Replaced manual bounding box extraction (lines 407–424) with `WorkoutBoundsDto.fromCursor(c)`.
  - `clusterExistingWorkouts`: Replaced manual summary row extraction (lines 512–527) with `ClusterWorkoutCandidateDto.fromCursor(c)`.
  - Statically verified: **0 occurrences** of `getColumnIndex` or `getColumnIndexOrThrow` in `WorkoutClusterEngine.kt`.
- **`EquipmentDataProvider.kt`**:
  - `getEquipmentData`: Replaced manual `SPORT_ID` and `EQUIPMENT_ID` index lookups with `WorkoutEquipmentDto.fromCursor(cursor)`.
  - Statically verified: **0 occurrences** of `getColumnIndex` or `getColumnIndexOrThrow` in `EquipmentDataProvider.kt`.

### 2.3 Test Suite Updates
- **`WorkoutClusterMapperTest.kt`**:
  - Added `testSourceCodeContract_zeroManualCursorExtractionsInTargetClasses` statically inspecting `WorkoutClusterEngine.kt`, `WorkoutClusterRepository.kt`, and `EquipmentDataProvider.kt` and asserting 0 occurrences of `getColumnIndex(` or `getColumnIndexOrThrow(`.
  - Added `testWorkoutBoundsDto_fromCursor_extractsNonNullCoordinates`.
  - Added `testWorkoutBoundsDto_fromCursor_handlesNullCoordinates`.
  - Added `testClusterWorkoutCandidateDto_fromCursor_extractsAllFields`.
  - Added `testClusterWorkoutCandidateDto_fromCursor_handlesNullStrings`.
  - Added `testWorkoutEquipmentDto_fromCursor_extractsSportAndEquipmentId`.
  - Added `testEquipmentDataProvider_delegatesToWorkoutEquipmentDto`.
  - Verified cursor lifecycle preservation (mappers never call `moveToNext()` or `close()`).

---

## 3. Verification & Quality Assurance

### 3.1 Targeted Contract & Unit Tests
- `WorkoutClusterMapperTest`: 10/10 tests passed (100% pass rate).
- `EquipmentDbHelperResilienceTest`: 6/6 tests passed (100% pass rate).

### 3.2 Full Clean-Room Regression Test Suite
- Executed `./gradlew testDebugUnitTest` verifying 100% test pass rate across all 3014+ unit tests with zero regressions.

---

## 4. Living Documentation Status
- `REQ-DAT-026`: Verified.
- `TST-DAT-021`: Verified.
