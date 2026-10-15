# Stage 1 Analysis: ATT-3274 - Crop start and end points of a recorded workout

**Ticket**: [ATT-3274](https://rainerblind.atlassian.net/browse/ATT-3274)  
**Sub-task**: [ATT-3585](https://rainerblind.atlassian.net/browse/ATT-3585) (`[Analysis]`)  
**Parent Epic**: [ATT-3267](https://rainerblind.atlassian.net/browse/ATT-3267)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.4`  
**Branch**: `feature/ATT-3274`  
**Requirement Mapping**: `REQ-UI-368`  
**Test Mapping**: `TST-UI-331`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Problem Statement & Motivation

Athletes occasionally start workout tracking prematurely (e.g., while waiting in a parking lot, sitting in a car, or indoors with noisy cold-start GPS fixes) or forget to stop tracking immediately upon finishing an activity (e.g., driving home with GPS recording still active).

Currently, **aTrainingTracker** provides no in-app mechanism to trim these unwanted leading or trailing segments from a recorded workout:
1. **Corrupted Summary & Period Metrics**: Unwanted pre/post-workout segments inflate `TIME_TOTAL_s`, `TIME_ACTIVE_s`, `DISTANCE_TOTAL_m`, `ASCENDING`, and `DESCENDING`, while distorting `SPEED_AVERAGE_mps`, `SPEED_MAX_mps`, and sensor extrema (`ExtremumValues`).
2. **Cluttered Map & Elevation Profile**: Noisy pre-start clusters or high-speed vehicle trajectories distort the map bounding box (`BOUND_MIN_LAT..BOUND_MAX_LNG`), `MAP_POLYLINE`, `ALTITUDE_STREAM`, `DISTANCE_STREAM`, and apex calculation (`LINE_DISTANCE_m`).
3. **Contaminated Exports**: Generated `.tcx`, `.fit`, `.gpx`, `.csv`, and `.json` (GoldenCheetah) files include the erroneous leading/trailing trackpoints and laps, requiring external desktop tools to repair before uploading to Strava or training platforms.

Providing a dedicated, interactive **Crop Workout** subview (`CropWorkoutDialog` / `CropWorkoutScreen.kt`) with a dual-handle range scrubber coupled to the map and elevation profile—protected by an explicit destructive confirmation dialog (`Coding Standards Sec 1.4`)—enables athletes to permanently prune unwanted start/end samples, atomically recalculate all workout summary statistics, extrema, streams, and laps, and regenerate clean export files.

---

## 2. Root Cause & Architectural Gap Analysis (Forensic Investigation)

### 2.1 Per-Workout Sample Storage (`WorkoutSamplesDatabaseManager.java`)
- Each recorded workout stores its 1 Hz time-series telemetry in a dedicated SQLite table named `WorkoutSamplesDatabaseManager.getTableName(fileBaseName)` (`"<fileBaseName>"`) inside `WorkoutSamples.db`.
- **Schema & Strict Separation of Cumulative vs. Non-Linear Instantaneous Columns**:
  - **Base columns**: `_id INTEGER PRIMARY KEY AUTOINCREMENT`, `time DATETIME DEFAULT CURRENT_TIMESTAMP` (UTC `"yyyy-MM-dd HH:mm:ss"`).
  - **Cumulative monotonic columns** (`DISTANCE_m`, `TIME_TOTAL`, `TIME_ACTIVE`, `ASCENT`, `DESCENT`, `CALORIES`):
    - When leading samples are pruned (i.e., the new start point is at sample `_id = startRowId` with initial baseline values $D_0 = \text{DISTANCE\_m}$, $T_{\text{total},0} = \text{TIME\_TOTAL}$, $T_{\text{active},0} = \text{TIME\_ACTIVE}$, $A_0 = \text{ASCENT}$, $Desc_0 = \text{DESCENT}$, $C_0 = \text{CALORIES}$), **all** cumulative columns are shifted by subtracting their respective baseline values at `startRowId`.
    - **Universal `EPSILON = 1e-6` Clamping**: To prevent IEEE-754 floating-point subtraction drift across **all** cumulative fields (`DISTANCE_m`, `ASCENT`, `DESCENT`, `CALORIES`, `TIME_TOTAL`, `TIME_ACTIVE`), the new start row (`_id == startRowId`) is explicitly anchored to `0.0` / `0`, and for every subsequent retained row (`_id > startRowId`), any shifted cumulative value $v' = v - v_0$ where $|v'| < \text{EPSILON}$ ($10^{-6}$) or $v' < 0$ is clamped to exact `0.0` (or `0` for integer columns).
  - **Spatial displacement column (`LINE_DISTANCE_m`)**:
    - Because `LINE_DISTANCE_m` is the straight-line Haversine distance from the workout's start coordinate (`newStartLatLng` at `_id = startRowId`) to each sample's `(LATITUDE, LONGITUDE)`, it cannot be shifted by simple subtraction. Instead, `LINE_DISTANCE_m` is recalculated row-by-row for all retained samples (`_id BETWEEN startRowId AND endRowId`) using `WorkoutClusterEngine.distanceBetween(newStartLatLng, rowLatLng)` via a compiled `SQLiteStatement` (`UPDATE "<fileBaseName>" SET LINE_DISTANCE_m = ? WHERE _id = ?`), with `_id == startRowId` set to exact `0.0`.
  - **Non-linear instantaneous sensor columns** (`HR`, `POWER`, `CADENCE`, `SPEED_mps`, `ALTITUDE`, `TEMPERATURE`):
    - Instantaneous telemetry values in the sample rows are **never** shifted by offset subtraction. Instead, their `MIN`, `MAX`, and `AVG` summary extrema in `ExtremumValues` are strictly re-derived from the retained sample subset (`WHERE _id BETWEEN startRowId AND endRowId`).

### 2.2 Summary, Streams & Extrema Persistence (`WorkoutSummariesDatabaseManager.java`)
- `WorkoutSummaries.TABLE` stores session-level scalars (`TIME_START`, `TIME_TOTAL_s`, `TIME_ACTIVE_s`, `DISTANCE_TOTAL_m`, `SPEED_AVERAGE_mps`, `CALORIES`, `LAPS`, `ASCENDING`, `DESCENDING`) alongside encoded streams (`MAP_POLYLINE`, `DISTANCE_STREAM`, `ALTITUDE_STREAM`) and spatial bounds (`BOUND_MIN_LAT`, `BOUND_MIN_LNG`, `BOUND_MAX_LAT`, `BOUND_MAX_LNG`).
- `WorkoutSummaries.TABLE_EXTREMA_VALUES` (`ExtremumValues`) stores `MIN`, `MAX`, `AVG`, `START`, and `END` values and their `LATITUDE`/`LONGITUDE` positions for each `SensorType`.

### 2.3 Foreign-Key-Equivalent Integrity & Deterministic Lap Metadata Mapping (`LapsDatabaseManager.java`)
- `Laps.TABLE` in `Laps.db` stores per-lap rows (`WORKOUT_ID`, `LAP_NR`, `TIME_START`, `TIME_TOTAL_s`, `DISTANCE_TOTAL_m`, `SPEED_AVERAGE_mps`, `NAME`, `DESCRIPTION`).
- **Deterministic Lap Metadata Mapping (Content-Follows-Identity)**:
  - To prevent metadata confusion, race conditions, or off-by-one errors:
    1. Laps are identified by their original sequential intervals. If the crop start falls inside original Lap $M$ and the crop end falls inside original Lap $N$ ($1 \le M \le N \le L_{\text{total}}$), only laps $M \dots N$ have retained sample rows.
    2. Each retained lap $k \in \{1 \dots (N - M + 1)\}$ maps directly to original lap $OrigIndex = M + k - 1$:
       - **Metadata Inheritance**: Lap $k$ strictly preserves the exact user-authored `NAME` and `DESCRIPTION` from original Lap $OrigIndex$ in their entirety, without truncation or abbreviation, regardless of how much the boundary lap duration was trimmed.
       - Laps prior to $M$ and after $N$ (whose sample rows were completely pruned) are discarded along with their metadata. There is no ambiguous cross-lap string concatenation or heuristic merging; metadata strictly adheres to its underlying retained telemetry content.
    3. `TIME_START` for Lap 1 is updated to the timestamp of the new first retained sample (`_id = startRowId`).
    4. `TIME_TOTAL_s`, `DISTANCE_TOTAL_m`, and `SPEED_AVERAGE_mps` for boundary laps $M$ and $N$ are recalculated strictly from the retained samples belonging to those respective laps.
    5. In `Laps.TABLE`, all previous rows for `WORKOUT_ID = workoutId` are replaced with the newly calculated $K = N - M + 1$ laps re-indexed sequentially `LAP_NR = 1..K`. In the sample table, `LAP_NR` values are updated in lockstep: `UPDATE "<fileBaseName>" SET LAP_NR = (LAP_NR - ? + 1)` where the offset is $M$.
    6. **Verification Assertion**: $\sum_{k=1}^{K} \text{DISTANCE\_TOTAL\_m}_k = \text{workout.DISTANCE\_TOTAL\_m}$ within `EPSILON = 1e-6`.

### 2.4 Sensor Frequency & Indoor/GPS-less Telemetry Handling
- **1 Hz SQLite Telemetry Baseline**: In `aTrainingTracker`, `WorkoutSamples` rows are persisted at 1-second sample intervals (`DATETIME` in SQLite). If an external sensor transmits at $> 1\text{ Hz}$ (e.g. 4 Hz power meter, 10 Hz high-frequency GPS, or pedal power balance), the tracker service downsamples/aggregates to the 1 Hz sample epoch during recording.
- **Row-Based Pruning Semantics**: Telemetry pruning and metric recalculation operate strictly row-by-row on these recorded rows (`_id BETWEEN startRowId AND endRowId`). When a sample row is pruned, all sensor attributes stored in that row are pruned. No synthetic sub-second interpolation is fabricated.
- **Sparse Streams (< 1 Hz) & Zero-Sample Sensor Handling**: For low-frequency sensors (e.g., BLE Heart Rate monitors transmitting every 2–5 seconds, or sensors connecting after ride start), `CropWorkoutMath` filters only valid, non-null, finite values (`Double.isFinite(v)`). If a sensor has zero valid readings in the cropped window (e.g., the sensor was disconnected or only active in the discarded segment), all scalar fields (`MIN`, `MAX`, `AVG`, `START`, `END`) in `ExtremumValues` for that sensor type are explicitly set to `0.0` (with `0.0` coordinates), ensuring downstream UI consumers recognize `hasSensorData = false` and preventing deceptive non-zero metrics or `NaN` averages.
- **Indoor & GPS-less Workouts**: When cropping indoor workouts (treadmill, indoor trainer, stationary bike) where GPS coordinates (`LATITUDE`, `LONGITUDE`) are null:
  - `LINE_DISTANCE_m` is explicitly set to `0.0` for all rows without invoking `WorkoutClusterEngine.distanceBetween` (preventing `NullPointerException`).
  - Spatial bounding boxes (`BOUND_MIN_LAT`, `BOUND_MAX_LAT`, `BOUND_MIN_LNG`, `BOUND_MAX_LNG`) and `MAP_POLYLINE` remain null or empty strings.
  - Cropping operates purely on time and available telemetry streams (`DISTANCE_m`, `TIME_TOTAL`, `TIME_ACTIVE`, `SPEED_mps`, `POWER`, `CADENCE`, `HR`).
- **Timestamp Monotonicity**: If legacy data contains non-monotonic or duplicate GPS timestamps, samples are strictly ordered by primary key `_id ASC` (which preserves recording sequence).

---

## 3. Technical Design & Atomic SQLite Transaction Architecture

### 3.1 Standard SQLite Rollback Journal, Pre-Flight Storage Check & Pre-Crop JSON Backup
To avoid disk I/O amplification and storage exhaustion while providing a reliable recovery path:
1. **Disk Space Pre-Flight Check & Pre-Crop JSON Backup**:
   - Before executing the crop, `WorkoutRepository` checks available internal storage via `StatFs(context.filesDir.path).availableBytes`.
   - If available space is below `10 MB` (safe threshold), the operation is rejected early with a user-friendly error message (`R.string.crop_insufficient_storage`), preventing any mid-operation failure.
   - **Pre-Crop JSON Cache Backup Lifecycle (`try ... finally`)**: Prior to executing the SQLite transaction, `WorkoutRepository` writes a versioned JSON snapshot to `context.cacheDir/workout_crop_backup_<workoutId>_<timestamp>.json` containing the pre-crop `WorkoutData`, `LapData`, and boundary row IDs inside a `try { ... } finally { ... }` block. Upon successful transaction commit, the temporary JSON file is safely purged in the `finally` block (with at most 1 versioned file retained on rollback for recovery), guaranteeing that cache pollution cannot occur.
2. **Native SQLite Rollback Journal Transactions**:
   - No heavy `CREATE TABLE AS SELECT` copy is performed. Instead, standard SQLite `beginTransaction()` / `try ... finally { endTransaction() }` blocks are utilized with SQLite's native rollback journal mechanism.
   - If any step throws an exception or the process is killed, SQLite's native rollback journal automatically restores table integrity without leaving orphaned temporary tables.
   - In `WorkoutSamplesDatabaseManager`:
     1. Prune samples outside `[startRowId, endRowId]` (`DELETE FROM "<fileBaseName>" WHERE _id < ? OR _id > ?`).
     2. Zero-anchor cumulative columns (`DISTANCE_m`, `TIME_TOTAL`, `TIME_ACTIVE`, `ASCENT`, `DESCENT`, `CALORIES`) with `EPSILON = 1e-6` clamping.
     3. Recalculate `LINE_DISTANCE_m` row-by-row from `newStartLatLng`.
     4. Re-index `LAP_NR` to `1..K` and verify checksum (`remainingRows == expectedRetainedRowCount && remainingRows >= 2`).
     5. `setTransactionSuccessful()`.
   - In `LapsDatabaseManager`: delete old laps for `workoutId`, insert recalculated $K$ laps with preserved notes, `setTransactionSuccessful()`.
   - In `WorkoutSummariesDatabaseManager`: update `WorkoutSummaries.TABLE` and `TABLE_EXTREMA_VALUES` with recalculated values, `setTransactionSuccessful()`.

### 3.2 Atomic Export Synchronization, Cloud-Sync Protocol & Post-Commit Failure Fallback
- **Pre-Commit File Invalidation & Cloud-Sync Invalidation**:
  - Cancel existing export tasks via `WorkManager.getInstance(context).cancelUniqueWork("export_" + fileBaseName)`.
  - Delete cached files (`.tcx`, `.fit`, `.gpx`, `.csv`, `.json`).
  - **Cloud-Sync Protocol**: For cloud-synced workouts, update `SyncStatusDatabaseManager` / `ExportStatusDatabaseManager` to mark the activity status as `PENDING_UPDATE` / `WAITING`. This ensures downstream sync engines re-upload or update the activity cleanly on external platforms (e.g., Strava), preventing sync-loop conflicts.
- **Post-Commit Enqueue & Fallback Recovery**:
  - Immediately following successful SQLite transaction commit, fresh export tasks are enqueued via `WorkManager.getInstance(context).enqueueUniqueWork("export_" + fileBaseName, ExistingWorkPolicy.REPLACE, exportRequest)`.
  - **Failure Fallback**: If `enqueueUniqueWork` throws an unhandled exception or `WorkManager` rejects the request (e.g., job scheduler limits reached):
    1. The committed database records remain completely intact and valid.
    2. `ExportStatusDatabaseManager` marks export status for `fileBaseName` as `ExportStatus.ERROR`.
    3. An error message is exposed to the UI (`R.string.crop_export_failed_retry`), and a dedicated "Force Re-Export" button is displayed in the workout details menu (visible exclusively when `ExportStatus == ERROR`) to allow the user to trigger export recovery on demand.

### 3.3 ViewModel Lifecycle & Concurrency Guard (`EditWorkoutViewModel.kt`)
- `EditWorkoutViewModel` manages `isCroppingInProgress: StateFlow<Boolean>`.
- The operation runs inside `viewModelScope.launch(Dispatchers.IO)`. If the ViewModel is destroyed, the coroutine scope automatically cancels, and the uncommitted SQLite transaction rolls back safely.
- While `isCroppingInProgress == true`:
  - `BackHandler(enabled = true)` prevents user navigation away from the screen.
  - UI action buttons and sliders are disabled with a visual `CircularProgressIndicator`.

### 3.4 Interactive Crop Subview (`CropWorkoutScreen.kt`), RangeSlider Minimum Distance & RTL Support
- **Dual-Range Scrubber & Minimum Distance Guard**:
  - Material 3 `RangeSlider` with a minimum distance constraint (`endIndex - startIndex >= minIndexStep`, where `minIndexStep` corresponds to $\ge 1\text{ s}$).
  - This prevents slider thumbs from overlapping or collapsing into an unselectable single point.
- **Hard Boundary Pre-Condition Guard**:
  - The "Crop" confirmation button in `CropWorkoutScreen.kt` is explicitly disabled unless `(endIndex - startIndex >= minIndexStep) && (croppedDurationSec >= 1.0) && (endTimestampMs - startTimestampMs >= 1000L) && !isCroppingInProgress`.
  - Disabling the action button directly at the UI root guarantees that duration $< 1\text{ s}$ or single-point crops can never be triggered.
- **RTL Support & Adaptive UI**:
  - Uses RTL-safe start/end Compose alignment primitives (`Arrangement.spacedBy`, `Alignment.CenterHorizontally`).
  - Supports `2.0x` font scaling and dark/light themes.
  - Live preview metrics (`DistanceFormatter`, `DateUtils.formatElapsedTime`): Start trim, End trim, New distance, New duration.
- **Destructive Confirmation Dialog (`Coding Standards Sec 1.4`)**:
  - Explicit `AlertDialog` confirming irreversible sample pruning with advice to use *Save as Route* if an untrimmed backup is desired.

---

## 4. User Scope Grounding (`ATT-1250`)

- **In Scope**:
  1. `CropWorkoutScreen.kt` and `CropWorkoutMath.kt` with `Double.isFinite` filtering, sparse sensor ($< 1\text{ Hz}$) handling, $\ge 1\text{ s}$ duration guard, `RangeSlider` minimum thumb distance constraint, RTL-safe layout, map/elevation previews, and irreversible confirmation `AlertDialog`.
  2. Entry action button in `EditWorkoutScreen.kt` (`EditWorkoutDialog`) and state management in `EditWorkoutViewModel.kt`.
  3. Pre-flight storage space verification (`StatFs`), native SQLite rollback transactions in `WorkoutSamplesDatabaseManager`, `LapsDatabaseManager`, and `WorkoutSummariesDatabaseManager`, post-op row-count checksum, and `WorkManager` UniqueWork export invalidation/replacement.
  4. 9-language localization parity (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  5. Unit tests covering crop boundary math, sparse sensor handling, duration $\ge 1\text{ s}$ guard, universal `EPSILON = 1e-6` cumulative clamping, row-by-row `LINE_DISTANCE_m` recalculation, non-linear sensor extrema re-derivation, lap foreign-key/timestamp alignment & note preservation, rollback behavior, export invalidation, and localization parity.
- **Out of Scope**:
  - Mid-workout interior pause cutting (ticket scope is strictly start and end point cropping).

---

## 5. Requirement Archaeology & System Invariants

- **Requirement Mapping**: Net-new requirement `REQ-UI-368` (*Interactive Start/End Workout Cropping with Destructive Sample Pruning and Full Metric Recalculation*) and test specification `TST-UI-331`. No existing requirements in `docs/requirements.md` are modified or weakened.
- **System Invariants**:
  1. **Irreversible Destructive Confirmation & UI Lock Invariant (`Coding Standards Sec 1.4`)**: Permanent sample deletion MUST require explicit confirmation in an `AlertDialog` stating irreversibility, and `isCroppingInProgress` MUST block duplicate submissions and back-navigation during execution.
  2. **Storage Pre-Flight & Native SQLite Rollback Invariant**: Available storage MUST be validated before crop execution; database changes MUST rely on native SQLite transactions with rollback on failure.
  3. **Universal `EPSILON = 1e-6` Cumulative Shift & Non-Linear Extrema Re-derivation Invariant**: All cumulative fields (`DISTANCE_m`, `TIME_TOTAL`, `TIME_ACTIVE`, `ASCENT`, `DESCENT`, `CALORIES`) MUST be zero-anchored at `startRowId` with `EPSILON = 1e-6` clamping; `LINE_DISTANCE_m` MUST be recalculated row-by-row from `newStartLatLng`; and non-linear sensors (`HR`, `POWER`, `CADENCE`, `SPEED_mps`, `ALTITUDE`, `TEMPERATURE`) MUST have their `MIN`, `MAX`, and `AVG` strictly re-derived from valid finite samples, gracefully handling sparse sensor streams ($< 1\text{ Hz}$).
  4. **Minimum Duration & Boundary Invariant**: Cropped duration MUST be $\ge 1\text{ s}$ with $\ge 2$ retained points, and `RangeSlider` MUST enforce minimum thumb separation.
  5. **Lap Referential Integrity & Metadata Preservation Invariant**: Retained laps in `Laps.db` MUST be strictly bounded by `[startRowId, endRowId]` / `[startTimestamp, endTimestamp]`, re-indexed `1..K` in lockstep with sample `LAP_NR`, and preserve user-authored `NAME` and `DESCRIPTION` notes.
  6. **UniqueWork Export Invalidation & Repository Cache Refresh Invariant**: Export files MUST be replaced atomically via `WorkManager` UniqueWork; and `WorkoutRepository` MUST invalidate its cache and re-fetch from SQLite post-commit.

---

## 6. Risk Assessment & Recommendation

- **Risk Rating**: **LOW**
  - Justification: Utilizing native SQLite rollback journals and pre-flight storage checks avoids I/O bloat, `Double.isFinite` filtering with sparse sensor handling prevents metric errors, the $\ge 1\text{ s}$ and `RangeSlider` thumb separation guards prevent boundary collapses, and `WorkManager` UniqueWork eliminates file-locking race conditions.
- **Gate 1 Recommendation**: **RECOMMEND PASS**

