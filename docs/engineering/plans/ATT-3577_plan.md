# Stage 3: Implementation Plan - ATT-3577

## Ticket Overview
- **Ticket ID**: ATT-3577
- **Subtask ID**: ATT-3757
- **Summary**: Migrate Remaining Inline Cursor Extractions in WorkoutClusterEngine and EquipmentDataProvider to fromCursor Mappers
- **Requirement ID**: `REQ-DAT-026`
- **Test Specification ID**: `TST-DAT-021`
- **Active Branch**: `feature/ATT-3577`

---

## 1. SWE.2 Architecture & Design Decisions

### 1.1 Architectural Context & Strategy
This plan implements `REQ-DAT-026`, eliminating all remaining manual inline cursor column resolutions from `WorkoutClusterEngine.kt`, `EquipmentDataProvider.kt`, and `WorkoutClusterRepository.kt` in strict compliance with Rule 20 (Database & DTO Mapping Symmetry) and Rule 36 (Single Source of Truth & DRY).

In Clean Architecture:
- Data persistence entities and row mappers belong in the database layer (`WorkoutClusterDatabaseManager.kt`).
- Computational engines (`WorkoutClusterEngine.kt`), repositories (`WorkoutClusterRepository.kt`), and presentation data providers (`EquipmentDataProvider.kt`) must never interact with raw SQLite cursor column indices directly.

### 1.2 Component Design & Data Transfer Objects

#### 1.2.1 `WorkoutBoundsDto`
- **Location**: `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterDatabaseManager.kt`
- **Definition**:
  ```kotlin
  data class WorkoutBoundsDto(
      val minLat: Double?,
      val minLng: Double?,
      val maxLat: Double?,
      val maxLng: Double?
  ) {
      val hasPoints: Boolean
          get() = minLat != null && minLng != null && maxLat != null && maxLng != null

      companion object {
          fun fromCursor(cursor: Cursor): WorkoutBoundsDto {
              val minLatIdx = cursor.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.BOUND_MIN_LAT)
              val minLngIdx = cursor.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.BOUND_MIN_LNG)
              val maxLatIdx = cursor.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.BOUND_MAX_LAT)
              val maxLngIdx = cursor.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.BOUND_MAX_LNG)

              return WorkoutBoundsDto(
                  minLat = if (cursor.isNull(minLatIdx)) null else cursor.getDouble(minLatIdx),
                  minLng = if (cursor.isNull(minLngIdx)) null else cursor.getDouble(minLngIdx),
                  maxLat = if (cursor.isNull(maxLatIdx)) null else cursor.getDouble(maxLatIdx),
                  maxLng = if (cursor.isNull(maxLngIdx)) null else cursor.getDouble(maxLngIdx)
              )
          }
      }
  }
  ```

#### 1.2.2 `ClusterWorkoutCandidateDto`
- **Location**: `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterDatabaseManager.kt`
- **Definition**:
  ```kotlin
  data class ClusterWorkoutCandidateDto(
      val workoutId: Long,
      val workoutName: String?,
      val fileBaseName: String?,
      val sportId: Long,
      val distance: Double
  ) {
      companion object {
          fun fromCursor(cursor: Cursor): ClusterWorkoutCandidateDto {
              val idIdx = cursor.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID)
              val nameIdx = cursor.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.WORKOUT_NAME)
              val fileIdx = cursor.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.FILE_BASE_NAME)
              val sportIdx = cursor.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.SPORT_ID)
              val distIdx = cursor.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.DISTANCE_TOTAL_m)

              return ClusterWorkoutCandidateDto(
                  workoutId = cursor.getLong(idIdx),
                  workoutName = if (!cursor.isNull(nameIdx)) cursor.getString(nameIdx) else null,
                  fileBaseName = if (!cursor.isNull(fileIdx)) cursor.getString(fileIdx) else null,
                  sportId = cursor.getLong(sportIdx),
                  distance = cursor.getDouble(distIdx)
              )
          }
      }
  }
  ```

#### 1.2.3 `WorkoutEquipmentDto`
- **Location**: `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterDatabaseManager.kt`
- **Definition**:
  ```kotlin
  data class WorkoutEquipmentDto(
      val sportId: Long,
      val equipmentId: Long
  ) {
      companion object {
          fun fromCursor(cursor: Cursor): WorkoutEquipmentDto {
              val sportId = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.SPORT_ID))
              val equipmentId = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.EQUIPMENT_ID))
              return WorkoutEquipmentDto(sportId, equipmentId)
          }
      }
  }
  ```

#### 1.2.4 Centralization of Batch Key Extraction
- Move `WorkoutBatchKey` and `fromCursorBatchKeys` from `WorkoutClusterRepository.kt` into `WorkoutClusterDatabaseManager.kt`.
- In `WorkoutClusterRepository.kt`, provide binary/source compatible delegation:
  ```kotlin
  typealias WorkoutBatchKey = com.atrainingtracker.trainingtracker.database.WorkoutBatchKey
  typealias WorkoutBatchKeys = com.atrainingtracker.trainingtracker.database.WorkoutBatchKeys
  
  // Inside companion object:
  @JvmStatic
  fun fromCursorBatchKeys(cursor: Cursor): WorkoutBatchKeys =
      WorkoutClusterDatabaseManager.fromCursorBatchKeys(cursor)
  ```
- This ensures `WorkoutClusterRepository.kt` contains strictly **0 occurrences** of `getColumnIndex` or `getColumnIndexOrThrow` while preserving 100% backward compatibility.

---

## 2. Step-by-Step Implementation Decomposition

### Step 1: Repository Hygiene (Remove Stray Files)
- Remove the two scratch files accidentally committed in `4f39e595` (`ATT-3362`):
  - `git rm docs/attachments/com.atrainingtracker_issue_248c68bd7d93ef60ad269eaa8cf2c1e1_crash_session_6ACF7DAD038400010C9158367004874E_DNE_0_v2_stacktrace.txt`
  - `git rm docs/engineering/backlog/ATT-3529_fix_pairing_item_resources_not_found_crash.md`

### Step 2: DTO & Mapper Additions in `WorkoutClusterDatabaseManager.kt`
- Add `WorkoutBoundsDto`, `ClusterWorkoutCandidateDto`, `WorkoutEquipmentDto`, `WorkoutBatchKey`, and `WorkoutBatchKeys`.
- Add `WorkoutClusterDatabaseManager.fromCursorBatchKeys(cursor: Cursor): WorkoutBatchKeys`.
- In `WorkoutClusterRepository.kt`, replace the raw column resolutions with delegation to `WorkoutClusterDatabaseManager.fromCursorBatchKeys(cursor)`.

### Step 3: Refactor `WorkoutClusterEngine.kt`
- In `recalculateClusterAnchors(context, cluster)` (lines 404–425):
  - Replace manual index resolution and column reading with:
    ```kotlin
    cursor.use { c ->
        while (c.moveToNext()) {
            val bounds = WorkoutBoundsDto.fromCursor(c)
            if (bounds.minLat != null && bounds.minLat < minLat) minLat = bounds.minLat
            if (bounds.maxLat != null && bounds.maxLat > maxLat) maxLat = bounds.maxLat
            if (bounds.minLng != null && bounds.minLng < minLng) minLng = bounds.minLng
            if (bounds.maxLng != null && bounds.maxLng > maxLng) maxLng = bounds.maxLng
            if (bounds.minLat != null) hasPoints = true
        }
    }
    ```
- In `clusterExistingWorkouts(...)` (lines 511–531):
  - Replace manual index resolution with:
    ```kotlin
    cursor.use { c ->
        val total = c.count
        var count = 0
        while (c.moveToNext()) {
            count++
            if (count % 5 == 0 || count == total) {
                listener?.onPhase2Progress(count, total)
            }

            val candidate = ClusterWorkoutCandidateDto.fromCursor(c)
            val workoutId = candidate.workoutId
            val workoutName = candidate.workoutName
            val fileBaseName = candidate.fileBaseName
            val sportId = candidate.sportId
            val distance = candidate.distance
            ...
    ```
- Verify `WorkoutClusterEngine.kt` contains strictly **0 occurrences** of `getColumnIndex` and `getColumnIndexOrThrow`.

### Step 4: Refactor `EquipmentDataProvider.kt`
- In `getEquipmentData(cursor: Cursor)`:
  - Replace lines 32–33 with:
    ```kotlin
    val key = WorkoutEquipmentDto.fromCursor(cursor)
    val bSportType = sportTypeDatabaseManager.getBSportType(key.sportId)
    val equipmentName = if (key.equipmentId > 0) equipmentDbHelper.getEquipmentNameFromId(key.equipmentId) else null

    return EquipmentData(
        bSportType = bSportType,
        equipmentId = key.equipmentId,
        equipmentName = equipmentName
    )
    ```
- Verify `EquipmentDataProvider.kt` contains strictly **0 occurrences** of `getColumnIndex` and `getColumnIndexOrThrow`.

### Step 5: Expand `WorkoutClusterMapperTest.kt`
- Implement static contract test `testSourceCodeContract_zeroManualCursorExtractionsInTargetClasses`:
  - Read source files for `WorkoutClusterEngine.kt`, `WorkoutClusterRepository.kt`, and `EquipmentDataProvider.kt`.
  - Assert 0 occurrences of `getColumnIndex(` or `getColumnIndexOrThrow(`.
- Implement unit tests:
  - `testWorkoutBoundsDto_fromCursor_extractsNonNullCoordinates()`
  - `testWorkoutBoundsDto_fromCursor_handlesNullCoordinates()`
  - `testClusterWorkoutCandidateDto_fromCursor_extractsAllFields()`
  - `testWorkoutEquipmentDto_fromCursor_extractsSportAndEquipmentId()`
  - Verify cursor lifecycle preservation (`cursor.close()` and `cursor.moveToNext()` are never called by mappers).

### Step 6: Full Clean-Room Regression Verification
- Run `./gradlew testDebugUnitTest` verifying 100% test pass rate across all unit tests.

---

## 3. Risk Assessment & Invariant Safeguards
- **Cursor Lifecycle**: Cursors are opened and closed by calling query routines (`cursor.use { ... }`). Mappers must strictly operate on the current row without moving or closing the cursor.
- **Null Safety**: Bounding coordinates can be SQL NULL for stationary workouts. Null checks and `hasPoints` boolean must behave identically to the existing code.
- **Equipment Short-Circuiting (`REQ-STB-005`)**: When `equipmentId <= 0`, `EquipmentDbHelper` must never be queried.
- **Repository Compatibility**: External callers and existing tests referencing `WorkoutClusterRepository.fromCursorBatchKeys` or `WorkoutBatchKey` remain 100% compatible via typealiases and companion delegation.
