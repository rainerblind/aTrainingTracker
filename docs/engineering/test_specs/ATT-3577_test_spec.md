# Stage 2: Requirement & Test Specification - ATT-3577

## Ticket Overview
- **Ticket ID**: ATT-3577
- **Subtask ID**: ATT-3756
- **Summary**: Migrate Remaining Inline Cursor Extractions in WorkoutClusterEngine and EquipmentDataProvider to fromCursor Mappers
- **Requirement ID**: `REQ-DAT-026`
- **Test Specification ID**: `TST-DAT-021`
- **Active Branch**: `feature/ATT-3577`

---

## 1. Formal Requirement Specification (`REQ-DAT-026`)

### 1.1 Requirement Title
**Authoritative Cursor Mappers for Workout Cluster Engine and Equipment Data Provider Architecture**

### 1.2 Description
The system SHALL eliminate manual inline cursor column extractions across workout clustering and equipment data provider layers by introducing typed `fromCursor` DTO mappers (`WorkoutBoundsDto`, `ClusterWorkoutCandidateDto`, `WorkoutEquipmentDto`) and centralizing batch key extraction in `WorkoutClusterDatabaseManager`, enforcing Rule 20 (Database & DTO Mapping Symmetry) and Rule 36 (Single Source of Truth & DRY) (ATT-3577, complementing `REQ-DAT-025`):

1. **Workout Bounds DTO Mapper (`WorkoutBoundsDto`)**:
   - The system SHALL define `WorkoutBoundsDto(val minLat: Double?, val minLng: Double?, val maxLat: Double?, val maxLng: Double?)` with `val hasPoints: Boolean get() = minLat != null && minLng != null && maxLat != null && maxLng != null`.
   - `WorkoutBoundsDto.companion.fromCursor(cursor: Cursor): WorkoutBoundsDto` SHALL extract `BOUND_MIN_LAT`, `BOUND_MIN_LNG`, `BOUND_MAX_LAT`, `BOUND_MAX_LNG` via `cursor.getColumnIndexOrThrow(...)` and `cursor.isNull(...)`, preserving null safety.
   - `WorkoutClusterEngine.recalculateClusterAnchors` SHALL delegate cursor row extraction strictly to `WorkoutBoundsDto.fromCursor(c)`, completely eliminating direct `getColumnIndex` / `getColumnIndexOrThrow` calls.

2. **Cluster Workout Candidate DTO Mapper (`ClusterWorkoutCandidateDto`)**:
   - The system SHALL define `ClusterWorkoutCandidateDto(val workoutId: Long, val workoutName: String?, val fileBaseName: String?, val sportId: Long, val distance: Double)`.
   - `ClusterWorkoutCandidateDto.companion.fromCursor(cursor: Cursor): ClusterWorkoutCandidateDto` SHALL extract `C_ID`, `WORKOUT_NAME`, `FILE_BASE_NAME`, `SPORT_ID`, `DISTANCE_TOTAL_m` via `cursor.getColumnIndexOrThrow(...)`.
   - `WorkoutClusterEngine.clusterExistingWorkouts` SHALL delegate candidate extraction strictly to `ClusterWorkoutCandidateDto.fromCursor(c)`, completely eliminating direct `getColumnIndex` / `getColumnIndexOrThrow` calls.

3. **Workout Equipment DTO Mapper (`WorkoutEquipmentDto`)**:
   - The system SHALL define `WorkoutEquipmentDto(val sportId: Long, val equipmentId: Long)`.
   - `WorkoutEquipmentDto.companion.fromCursor(cursor: Cursor): WorkoutEquipmentDto` SHALL extract `SPORT_ID` and `EQUIPMENT_ID` via `cursor.getColumnIndexOrThrow(...)`.
   - `EquipmentDataProvider.getEquipmentData` SHALL delegate cursor reading strictly to `WorkoutEquipmentDto.fromCursor(cursor)`, completely eliminating direct `getColumnIndex` / `getColumnIndexOrThrow` calls while preserving `equipmentId > 0` short-circuit logic (`REQ-STB-005`).

4. **Clean Architecture Centralization of Batch Key Extraction**:
   - `WorkoutClusterDatabaseManager` SHALL declare and implement `WorkoutBatchKey` and `fromCursorBatchKeys(cursor: Cursor): WorkoutBatchKeys`.
   - `WorkoutClusterRepository` SHALL maintain typealiases and delegate `fromCursorBatchKeys` to `WorkoutClusterDatabaseManager.fromCursorBatchKeys(cursor)`.
   - `WorkoutClusterEngine.kt`, `WorkoutClusterRepository.kt`, and `EquipmentDataProvider.kt` SHALL contain strictly **0 occurrences** of `getColumnIndex` and `getColumnIndexOrThrow`.

5. **Repository Hygiene**:
   - Stray scratch and backlog files accidentally added in commit `4f39e595` (`ATT-3362`) SHALL be permanently excised from the repository tree via `git rm`.

6. **Preservation of System Invariants**:
   - Cursor lifecycle ownership (mappers do not advance, reset, or close cursors), spatial bounds calculation, cluster matching heuristics, equipment boundary guards, and 100% full-suite unit test pass rate MUST NOT be broken.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: Complements `REQ-DAT-025` (*Authoritative Database Cursor Row and Batch Mappers for Route and Cluster Repositories*). Formally tracked as `REQ-DAT-026`. Targets `WorkoutClusterEngine.kt`, `EquipmentDataProvider.kt`, `WorkoutClusterRepository.kt`, `WorkoutClusterDatabaseManager.kt`, and `WorkoutClusterMapperTest.kt`.
2. **Historical Origin & Commit Trace**: Sprint 2026-42.3, commit `776d3fc0` implemented `REQ-DAT-025` for `RoutesDatabaseManager.kt` and `WorkoutClusterRepository.kt`, but left `WorkoutClusterEngine.kt` and `EquipmentDataProvider.kt` with manual cursor extraction blocks and placed mapper helpers directly inside `WorkoutClusterRepository.kt`.
3. **Root Reason for Existing Formulation**: ATT-3366 focused strictly on repository classes, deferring engine and UI provider mapper extraction.
4. **Preservation of Core Invariants**: Bounding coordinate null-safety, cluster matching distance threshold, equipment boundary short-circuiting, cursor lifecycle management, and 100% unit test pass rate strictly preserved.

---

## 3. Acceptance Criteria (Given-When-Then)

- **Scenario 1: Zero Manual Cursor Index Lookups in Target Classes**:
  - *Given* `WorkoutClusterEngine.kt`, `WorkoutClusterRepository.kt`, and `EquipmentDataProvider.kt`,
  - *When* inspected by source code contract tests in `WorkoutClusterMapperTest.kt`,
  - *Then* all three files contain strictly 0 occurrences of `getColumnIndex` or `getColumnIndexOrThrow`.

- **Scenario 2: Workout Bounds DTO Mapping**:
  - *Given* a SQLite cursor positioned at a workout summary row with bounding coordinates,
  - *When* `WorkoutBoundsDto.fromCursor(cursor)` is called,
  - *Then* `minLat`, `minLng`, `maxLat`, and `maxLng` are faithfully extracted, nulls are preserved, `hasPoints` returns true when all 4 are present, and the cursor is neither moved nor closed.

- **Scenario 3: Cluster Workout Candidate DTO Mapping**:
  - *Given* a SQLite cursor positioned at a workout summary row,
  - *When* `ClusterWorkoutCandidateDto.fromCursor(cursor)` is called,
  - *Then* `workoutId`, `workoutName`, `fileBaseName`, `sportId`, and `distance` are extracted accurately, and the cursor is neither moved nor closed.

- **Scenario 4: Workout Equipment DTO Mapping**:
  - *Given* a SQLite cursor positioned at a workout summary row with `sportId` and `equipmentId`,
  - *When* `WorkoutEquipmentDto.fromCursor(cursor)` is called,
  - *Then* `sportId` and `equipmentId` are extracted, and `EquipmentDataProvider.getEquipmentData(cursor)` resolves equipment data cleanly.

- **Scenario 5: Stray Files Removed**:
  - *Given* the git repository tree,
  - *When* checking `docs/attachments/` and `docs/engineering/backlog/`,
  - *Then* the stray ATT-3529 files accidentally added in `4f39e595` are absent.

- **Scenario 6: Clean-Room Regression Suite**:
  - *Given* the complete unit test suite,
  - *When* executing `./gradlew testDebugUnitTest`,
  - *Then* 100% of tests pass without regression.

---

## 4. Test Specification (`TST-DAT-021`)

### 4.1 Test Scope & Traceability
- **Test ID**: `TST-DAT-021`
- **Traced Requirement**: `REQ-DAT-026`
- **Test File**: `app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterMapperTest.kt`

### 4.2 Test Cases

1. **`testSourceCodeContract_zeroManualCursorExtractionsInTargetClasses`**:
   - Recursively inspects `WorkoutClusterEngine.kt`, `WorkoutClusterRepository.kt`, and `EquipmentDataProvider.kt`.
   - Asserts that none of the files contain `getColumnIndex` or `getColumnIndexOrThrow`.

2. **`testWorkoutBoundsDto_fromCursor_extractsNonNullCoordinates`**:
   - Mocks cursor with valid `BOUND_MIN_LAT`, `BOUND_MIN_LNG`, `BOUND_MAX_LAT`, `BOUND_MAX_LNG`.
   - Asserts extracted values match mock inputs and `hasPoints` is true.
   - Verifies cursor position is not altered (`moveToNext()` not called) and cursor is not closed.

3. **`testWorkoutBoundsDto_fromCursor_handlesNullCoordinates`**:
   - Mocks cursor with null bounding columns (`cursor.isNull(...) == true`).
   - Asserts extracted values are `null` and `hasPoints` is false.

4. **`testClusterWorkoutCandidateDto_fromCursor_extractsAllFields`**:
   - Mocks cursor with `C_ID`, `WORKOUT_NAME`, `FILE_BASE_NAME`, `SPORT_ID`, `DISTANCE_TOTAL_m`.
   - Asserts extracted DTO matches all expected fields.
   - Verifies cursor is not moved or closed.

5. **`testWorkoutEquipmentDto_fromCursor_extractsSportAndEquipmentId`**:
   - Mocks cursor with `SPORT_ID` and `EQUIPMENT_ID`.
   - Asserts `sportId` and `equipmentId` match.
   - Verifies cursor is not moved or closed.

6. **`testCleanRoomRegression_fullSuite`**:
   - Clean-room execution of `./gradlew testDebugUnitTest` verifying 0 regressions.
