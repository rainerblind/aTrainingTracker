# Stage 1: Problem Domain & Root Cause Analysis (RCA) - ATT-3577

## Ticket Overview
- **Ticket ID**: ATT-3577
- **Subtask ID**: ATT-3755
- **Summary**: Migrate Remaining Inline Cursor Extractions in WorkoutClusterEngine and EquipmentDataProvider to fromCursor Mappers
- **Type**: Improvement (Verbesserung)
- **Component**: Database Architecture / Cursor Mapping Contracts (Rule 20, Rule 36)
- **Sprint**: 2026-42.5
- **Active Branch**: `feature/ATT-3577`

---

## 1. Problem Domain & Forensic Root Cause Analysis

### 1.1 Technical Background & Context
In sprint 2026-42.3, ticket `ATT-3366` (*Eliminate manual cursor column extractions in repositories using fromCursor mappers*, `REQ-DAT-025`) established authoritative cursor row and batch mappers for `RoutesDatabaseManager.kt`, `WorkoutClusterDatabaseManager.kt`, and `WorkoutClusterRepository.kt`. The objective was to eliminate low-level, error-prone manual `getColumnIndexOrThrow` calls in data repositories and UI components, enforcing Rule 20 (Database & DTO Mapping Symmetry) and Rule 36 (Single Source of Truth & DRY).

However, forensic analysis reveals that two critical adjacent classes in the exact same domain were omitted from that refactoring, and a third class retained low-level column resolutions in its file:

1. **`WorkoutClusterEngine.kt`**: Retains two raw, un-abstracted `getColumnIndexOrThrow` cursor extraction blocks:
   - Lines 407–424 in `recalculateClusterAnchors(context: Context, cluster: WorkoutCluster)`: Extracts bounding box coordinates (`BOUND_MIN_LAT`, `BOUND_MIN_LNG`, `BOUND_MAX_LAT`, `BOUND_MAX_LNG`) via repeated `c.getColumnIndexOrThrow(...)` and manual null-checking logic.
   - Lines 512–527 in `clusterExistingWorkouts(...)`: Extracts cluster candidate workout summary rows (`C_ID`, `WORKOUT_NAME`, `FILE_BASE_NAME`, `SPORT_ID`, `DISTANCE_TOTAL_m`) via inline column index resolutions inside the phase 2 iteration loop.

2. **`EquipmentDataProvider.kt`**: Retains raw, un-abstracted `getColumnIndexOrThrow` calls:
   - Lines 32–33 in `getEquipmentData(cursor: Cursor)`: Calls `cursor.getColumnIndexOrThrow(WorkoutSummaries.SPORT_ID)` and `cursor.getColumnIndexOrThrow(WorkoutSummaries.EQUIPMENT_ID)` directly instead of delegating to an authoritative mapper or DTO.

3. **`WorkoutClusterRepository.kt`**: Although `WorkoutClusterRepository` refactored its query calls in ATT-3366, the actual mapper definitions (`WorkoutBatchKey.fromCursor(cursor)` and `fromCursorBatchKeys(cursor)`) were implemented directly inside `WorkoutClusterRepository.kt`. Because Clean Architecture dictates that repositories must not contain raw cursor column mapping mechanics (which belong in the database manager layer), static architectural audits targeting repositories require `WorkoutClusterRepository.kt` to delegate to `WorkoutClusterDatabaseManager.kt`.

4. **Stray Files in Repository Tree**: In commit `4f39e595` (`ATT-3362`), two unrelated scratch files were inadvertently committed to the repository:
   - `docs/attachments/com.atrainingtracker_issue_248c68bd7d93ef60ad269eaa8cf2c1e1_crash_session_6ACF7DAD038400010C9158367004874E_DNE_0_v2_stacktrace.txt`
   - `docs/engineering/backlog/ATT-3529_fix_pairing_item_resources_not_found_crash.md`

### 1.2 Forensic Audit of Existing Cursor Extraction Implementations

#### 1.2.1 `WorkoutClusterEngine.kt` Bounding Box Extraction (Lines 407–424)
```kotlin
cursor.use { c ->
    val minLatIdx = c.getColumnIndexOrThrow(WorkoutSummaries.BOUND_MIN_LAT)
    val minLngIdx = c.getColumnIndexOrThrow(WorkoutSummaries.BOUND_MIN_LNG)
    val maxLatIdx = c.getColumnIndexOrThrow(WorkoutSummaries.BOUND_MAX_LAT)
    val maxLngIdx = c.getColumnIndexOrThrow(WorkoutSummaries.BOUND_MAX_LNG)

    while (c.moveToNext()) {
        val wMinLat = if (c.isNull(minLatIdx)) null else c.getDouble(minLatIdx)
        val wMinLng = if (c.isNull(minLngIdx)) null else c.getDouble(minLngIdx)
        val wMaxLat = if (c.isNull(maxLatIdx)) null else c.getDouble(maxLatIdx)
        val wMaxLng = if (c.isNull(maxLngIdx)) null else c.getDouble(maxLngIdx)

        if (wMinLat != null && wMinLat < minLat) minLat = wMinLat
        if (wMaxLat != null && wMaxLat > maxLat) maxLat = wMaxLat
        if (wMinLng != null && wMinLng < minLng) minLng = wMinLng
        if (wMaxLng != null && wMaxLng > maxLng) maxLng = wMaxLng
        if (wMinLat != null) hasPoints = true
    }
}
```
*Deficiency*: The bounding coordinates are extracted through loose local variables and manual column index resolution. This logic lacks an authoritative DTO and should be encapsulated into `WorkoutBoundsDto.fromCursor(cursor)`.

#### 1.2.2 `WorkoutClusterEngine.kt` Candidate Summary Extraction (Lines 512–527)
```kotlin
cursor.use { c ->
    val total = c.count
    val idIdx = c.getColumnIndexOrThrow(WorkoutSummaries.C_ID)
    val nameIdx = c.getColumnIndexOrThrow(WorkoutSummaries.WORKOUT_NAME)
    val fileIdx = c.getColumnIndexOrThrow(WorkoutSummaries.FILE_BASE_NAME)
    val sportIdx = c.getColumnIndexOrThrow(WorkoutSummaries.SPORT_ID)
    val distIdx = c.getColumnIndexOrThrow(WorkoutSummaries.DISTANCE_TOTAL_m)

    var count = 0
    while (c.moveToNext()) {
        count++
        if (count % 5 == 0 || count == total) {
            listener?.onPhase2Progress(count, total)
        }

        val workoutId = c.getLong(idIdx)
        val workoutName = c.getString(nameIdx)
        val fileBaseName = c.getString(fileIdx)
        val sportId = c.getLong(sportIdx)
        val distance = c.getDouble(distIdx)
        ...
```
*Deficiency*: The workout row extraction requires 5 distinct local column index lookups and unpacks raw primitive values directly into the clustering loop. This should be encapsulated into `ClusterWorkoutCandidateDto.fromCursor(cursor)`.

#### 1.2.3 `EquipmentDataProvider.kt` Extraction (Lines 31–43)
```kotlin
class EquipmentDataProvider(
    private val equipmentDbHelper: EquipmentDbHelper,
    private val sportTypeDatabaseManager: SportTypeDatabaseManager
) {
    fun getEquipmentData(cursor: Cursor): EquipmentData {
        val sportId = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.SPORT_ID))
        val equipmentId = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.EQUIPMENT_ID))

        val bSportType = sportTypeDatabaseManager.getBSportType(sportId)
        val equipmentName = if (equipmentId > 0) equipmentDbHelper.getEquipmentNameFromId(equipmentId) else null

        return EquipmentData(
            bSportType = bSportType,
            equipmentId = equipmentId,
            equipmentName = equipmentName
        )
    }
}
```
*Deficiency*: UI-layer data provider directly performs SQLite schema queries and column index resolutions. Extracting `WorkoutEquipmentDto.fromCursor(cursor)` (or helper) centralizes this mapping.

---

## 2. Architectural Invariants & Chesterton's Fence Analysis

### 2.1 Chesterton's Fence Archaeology (`REQ-DAT-025`)
- **Original Requirement**: `REQ-DAT-025` (*Authoritative Database Cursor Row and Batch Mappers for Route and Cluster Repositories*, ATT-3366).
- **Historical Commit**: Commit `776d3fc0` established `WorkoutBatchKey.fromCursor`, `fromCursorBatchKeys`, and `WorkoutClusterDatabaseManager.fromCursor`.
- **Underlying Rationale**: Eliminate duplicated cursor column extractions and index lookups across query loops.
- **Why Was Scope Left Incomplete?**: In ATT-3366, the focus was strictly on `WorkoutClusterRepository.kt` and `RoutesDatabaseManager.kt`. `WorkoutClusterEngine.kt` and `EquipmentDataProvider.kt` were adjacent consumers not included in the test specification. Furthermore, `WorkoutBatchKey` was colocated in `WorkoutClusterRepository.kt` rather than centralized in `WorkoutClusterDatabaseManager.kt`.

### 2.2 Preservation of Core Invariants
1. **Cursor Lifecycle Ownership**: Mappers (`fromCursor`) must never advance (`moveToNext`), reset (`moveToFirst`), or close (`close`) the cursor. Cursors must remain managed by caller `.use { ... }` blocks (REQ-DAT-025 Section 1).
2. **Coordinate & Bounds Fidelity**: Null-safe bounds handling (`if (cursor.isNull(minLatIdx)) null else cursor.getDouble(minLatIdx)`) and min/max anchor expansion logic must be preserved 100%.
3. **Equipment Boundary Guards (`REQ-STB-005`)**: When `equipmentId <= 0`, `equipmentName` must remain `null` without querying `EquipmentDbHelper`.
4. **Database Schemas & Signatures**: No SQLite schema or table alterations. Zero backward-incompatible API changes for existing callers of `WorkoutBatchKey` or `WorkoutClusterRepository`.

---

## 3. Scope Bounding & Gap Analysis (ATT-1250)

### 3.1 In-Scope Deliverables
1. **DTO & Mapper Construction in `WorkoutClusterDatabaseManager.kt`**:
   - `WorkoutBoundsDto(minLat, minLng, maxLat, maxLng)` with `WorkoutBoundsDto.fromCursor(cursor: Cursor)`.
   - `ClusterWorkoutCandidateDto(workoutId, workoutName, fileBaseName, sportId, distance)` with `ClusterWorkoutCandidateDto.fromCursor(cursor: Cursor)`.
   - `WorkoutEquipmentDto(sportId, equipmentId)` with `WorkoutEquipmentDto.fromCursor(cursor: Cursor)`.
   - Move `WorkoutBatchKey` and `fromCursorBatchKeys` to `WorkoutClusterDatabaseManager.kt`.
2. **Refactoring of `WorkoutClusterEngine.kt`**:
   - Replace lines 407–424 with `WorkoutBoundsDto.fromCursor(c)`.
   - Replace lines 512–527 with `ClusterWorkoutCandidateDto.fromCursor(c)`.
   - Guarantee strictly **0 occurrences** of `getColumnIndex` or `getColumnIndexOrThrow` in `WorkoutClusterEngine.kt`.
3. **Refactoring of `EquipmentDataProvider.kt`**:
   - Delegate cursor extraction to `WorkoutEquipmentDto.fromCursor(cursor)`.
   - Guarantee strictly **0 occurrences** of `getColumnIndex` or `getColumnIndexOrThrow` in `EquipmentDataProvider.kt`.
4. **Refactoring of `WorkoutClusterRepository.kt`**:
   - Delegate `fromCursorBatchKeys` to `WorkoutClusterDatabaseManager.fromCursorBatchKeys(cursor)`.
   - Maintain `WorkoutBatchKey` typealias for backward compatibility.
   - Guarantee strictly **0 occurrences** of `getColumnIndex` or `getColumnIndexOrThrow` in `WorkoutClusterRepository.kt`.
5. **Removal of Stray Files**:
   - Delete `docs/attachments/com.atrainingtracker_issue_248c68bd7d93ef60ad269eaa8cf2c1e1_crash_session_6ACF7DAD038400010C9158367004874E_DNE_0_v2_stacktrace.txt` via `git rm`.
   - Delete `docs/engineering/backlog/ATT-3529_fix_pairing_item_resources_not_found_crash.md` via `git rm`.
6. **Automated Contract & Unit Testing**:
   - Expand `WorkoutClusterMapperTest.kt` with a static source code contract test asserting strictly 0 occurrences of `getColumnIndex` and `getColumnIndexOrThrow` across `WorkoutClusterEngine.kt`, `WorkoutClusterRepository.kt`, and `EquipmentDataProvider.kt`.
   - Add unit tests for `WorkoutBoundsDto.fromCursor`, `ClusterWorkoutCandidateDto.fromCursor`, and `WorkoutEquipmentDto.fromCursor`.

### 3.2 Out-of-Scope Items
- Modifying SQLite database schemas (`WorkoutCluster.db`, `WorkoutSummaries.db`, `Equipment.db`).
- Altering the clustering algorithm math or cluster matching heuristics in `WorkoutClusterEngine.kt`.
- Refactoring `WorkoutDataMapper.kt` beyond using shared DTOs if desirable, maintaining strict ticket boundaries.

---

## 4. Proposed Architectural Design (SWE.1 / SWE.2)

### 4.1 Data Transfer Objects
```kotlin
data class WorkoutBoundsDto(
    val minLat: Double?,
    val minLng: Double?,
    val maxLat: Double?,
    val maxLng: Double?
) {
    val hasPoints: Boolean get() = minLat != null && minLng != null && maxLat != null && maxLng != null

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

---

## 5. Verification Strategy & Gate Criteria

### 5.1 Verification Checklist
- [ ] Contract test in `WorkoutClusterMapperTest.kt` scans `WorkoutClusterEngine.kt`, `WorkoutClusterRepository.kt`, and `EquipmentDataProvider.kt` and asserts 0 occurrences of `getColumnIndex` or `getColumnIndexOrThrow`.
- [ ] Unit tests in `WorkoutClusterMapperTest.kt` verify `WorkoutBoundsDto.fromCursor`, `ClusterWorkoutCandidateDto.fromCursor`, and `WorkoutEquipmentDto.fromCursor` mapping with valid and null data.
- [ ] Stray files deleted via `git rm`.
- [ ] Clean-room regression `./gradlew testDebugUnitTest` runs with 100% test pass rate.
