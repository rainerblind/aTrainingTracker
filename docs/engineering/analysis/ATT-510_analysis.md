# Analysis Report: Overview of Laps in Workout Summaries (ATT-510)

* **Parent Ticket**: [ATT-510](https://rainerblind.atlassian.net/browse/ATT-510) (*[Feature] Overview of laps in Workout Summaries*)
* **Active Sub-Task**: [ATT-889](https://rainerblind.atlassian.net/browse/ATT-889) (*[Subtask] [Analysis] Overview of laps in Workout Summaries*)
* **Parent Epic**: [ATT-826](https://rainerblind.atlassian.net/browse/ATT-826) (*[Epic] Laps*)
* **Target Version**: `V4.9.36`
* **Branch**: `feature/ATT-510`

---

## 1. Problem Statement & User Motivation

Athletes frequently track structured interval training sessions, tempo runs, hill repeats, or auto-lap workouts containing multiple split segments. Currently, while `TrackerService` and `LegacyImportEngine` record individual split events into `LapsDatabaseManager` (`Laps.db`), workout summary cards (`WorkoutSummary.kt`) in the aftermath screen provide no visual representation or performance breakdown of recorded laps.

Furthermore, the user requested extending lap functionality to allow custom naming and descriptions (planned across ATT-510 and ATT-511):
> *"Next, I would like to extend the lap functionality: ATT-510 and ATT-511.*  
> *The main idea is to let the user name individually name each lap. The default name would be Lap 1, Lap 2, ...*  
> *Moreover, the user should also be able to add a (short) description to each lap.*  
> *I like the idea to highlight the fastest lap with a rabbit. Please use a hedgehog for the slowest. :)*  
> *For the map integration, we need some more thoughts."*

This analysis establishes the architectural foundation for lap visualization in `WorkoutSummary`, defines the database schema upgrade for `name` and `description` persistence, implements rabbit (🐇) and hedgehog (🦔) split performance badges, and avoids regression across existing backup and export workflows.

---

## 2. Technical Inventory: Current Laps & Aftermath Architecture

### 2.1 Database Layer (`LapsDatabaseManager.java`)
* SQLite Database: `Laps.db` (`Laps.TABLE = "Laps"`).
* Current Schema Version: `DB_VERSION = 1`.
* Current Table Columns:
  * `_id INTEGER PRIMARY KEY AUTOINCREMENT`
  * `workoutID int`
  * `lapNr int`
  * `timeStart DATETIME DEFAULT CURRENT_TIMESTAMP`
  * `timeTotal_s int`
  * `distanceTotal_m real`
  * `speedAverage_mps real`
* **Critical Finding 1**: `LapsDatabaseManager` currently has **NO** `getLaps(workoutId)` or batch query method. Callers (like `TCXFileWriter`) performed raw SQL queries directly on the SQLite database instance.
* **Critical Finding 2**: `LapsDbHelper.onUpgrade()` currently executes `db.execSQL("drop table if exists Laps");` with a `// TODO: alter table instead of deleting!`. Any version bump without modifying `onUpgrade` would result in catastrophic data loss.
* **Critical Finding 3**: Columns `name` and `description` do not currently exist in `Laps.db`.

### 2.2 Data Mapping Layer (`WorkoutData.kt`, `WorkoutDataMapper.kt`, `WorkoutRepository.kt`)
* `WorkoutData`: Holds composite workout data (`headerData`, `detailsData`, `descriptionData`, `extremaData`, `exportStatuses`). Currently lacks a `laps` property.
* `WorkoutDataMapper`: Converts SQLite cursors to `WorkoutData` instances. Supports single mapping (`fromCursor(cursor)`) and batch chunked mapping (`fromCursor(cursor, batch)`).
* `WorkoutRepository`: Manages `allWorkouts: StateFlow<List<WorkoutData>>` using progressive batch loading (ATT-346 / ATT-359) to prevent UI thread starvation. Adding lap loading must use vectorized batch queries to avoid $O(N)$ database query spikes.

### 2.3 UI Layer (`WorkoutSummary.kt`)
* `WorkoutSummary.kt` renders individual workout cards in `WorkoutList.kt`.
* Current sections in visual order:
  1. `WorkoutHeader` (Title, date, sport, cluster button, edit icon, export menu)
  2. `HorizontalDivider`
  3. `WorkoutDescription` (Notes, goals, method)
  4. `WorkoutDetails` (Time, distance, elevation)
  5. `WorkoutExtrema` (Heart rate, speed/pace, cadence, power extrema rows)
  6. `StravaActivitySection` (If synced)
  7. `WorkoutMediaSection` (Map preview and elevation profile)
  8. `ExportStatus` (Export indicators)
* Inserting a dedicated `WorkoutLaps` section between `WorkoutExtrema` and `StravaActivitySection` / `WorkoutMediaSection` provides natural analytical continuity.

---

## 3. Database Schema Migration & Backwards Compatibility

### 3.1 Migration Strategy (`DB_VERSION = 2`)
1. Increment helper version in `LapsDbHelper`:
   ```java
   public static final int DB_VERSION = 2;
   ```
2. Define new column constants in `LapsDatabaseManager.Laps`:
   ```java
   public static final String NAME = "name";
   public static final String DESCRIPTION = "description";
   ```
3. Update `CREATE_TABLE` statement for fresh installations:
   ```java
   protected static final String CREATE_TABLE = "create table " + Laps.TABLE + " ("
           + Laps.C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
           + Laps.WORKOUT_ID + " int,"
           + Laps.LAP_NR + " int,"
           + Laps.TIME_START + " DATETIME DEFAULT CURRENT_TIMESTAMP,"
           + Laps.TIME_TOTAL_s + " int,"
           + Laps.DISTANCE_TOTAL_m + " real,"
           + Laps.SPEED_AVERAGE_mps + " real,"
           + Laps.NAME + " TEXT,"
           + Laps.DESCRIPTION + " TEXT)";
   ```
4. Safe, non-destructive migration in `onUpgrade`:
   ```java
   @Override
   public void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
       if (oldVersion < 2) {
           db.execSQL("ALTER TABLE " + Laps.TABLE + " ADD COLUMN " + Laps.NAME + " TEXT;");
           db.execSQL("ALTER TABLE " + Laps.TABLE + " ADD COLUMN " + Laps.DESCRIPTION + " TEXT;");
       }
   }
   ```
   *Never* drop the `Laps` table. Existing laps from historical workouts remain 100% intact with `name` and `description` initialized to `NULL`.

### 3.2 Backup & Restore Compatibility (`ImportEngine.kt`, `BackupManager.kt`)
* `ImportEngine.kt` uses dynamic cursor column inspection in `copyLaps(srcDb, targetDb, oldId, newId)`:
  * Copies all present columns by type (`INTEGER`, `FLOAT`, `STRING`, `BLOB`, `NULL`).
  * Older backups without `name`/`description` import seamlessly into `DB_VERSION = 2` (columns remain null).
  * Backups created with `DB_VERSION = 2` preserve lap names and descriptions.

---

## 4. Domain & Data Flow Architecture

### 4.1 Domain Model: `LapData`
```kotlin
package com.atrainingtracker.trainingtracker.ui.aftermath

import androidx.compose.runtime.Immutable

@Immutable
data class LapData(
    val id: Long = 0,
    val workoutId: Long,
    val lapNr: Long,
    val timeStart: String? = null,
    val timeTotalS: Int,
    val distanceTotalM: Double,
    val speedAverageMps: Double,
    val name: String? = null,
    val description: String? = null
) {
    /**
     * Resolves the user-facing title for the lap:
     * Returns custom name if present and non-blank; otherwise defaults to "Lap X".
     */
    fun getDisplayName(fallbackIndex: Int): String {
        return if (!name.isNullOrBlank()) {
            name
        } else {
            "Lap $fallbackIndex"
        }
    }
}
```

### 4.2 Query & Mutation Methods in `LapsDatabaseManager.java`
1. **Single Workout Retrieval**:
   ```java
   public List<LapData> getLaps(long workoutId)
   ```
   Queries `Laps` table where `workoutID = ?` ordered by `lapNr ASC, _id ASC`.
2. **Vectorized Batch Retrieval (High Performance)**:
   ```java
   public Map<Long, List<LapData>> getLapsForWorkouts(List<Long> workoutIds)
   ```
   Queries `Laps` table where `workoutID IN (?, ?, ...)` chunked by 500 IDs, grouping records into `Map<Long, List<LapData>>`. Eliminates N+1 database operations during `WorkoutList` scrolling.
3. **Lap Mutation (Groundwork for ATT-511)**:
   ```java
   public boolean updateLapDetails(long workoutId, long lapNr, @Nullable String name, @Nullable String description)
   ```
   Updates `name` and `description` for a specified lap.

### 4.3 Batch Metadata Integration in `WorkoutRepository` & `WorkoutDataMapper`
* In `WorkoutDataMapper.BatchMetadata`:
  ```kotlin
  data class BatchMetadata(
      val extrema: Map<Long, List<WorkoutSummariesDatabaseManager.ExtremaRecord>>,
      val stravaData: Map<String, String>,
      val clusterNames: Map<Long, String> = emptyMap(),
      val laps: Map<Long, List<LapData>> = emptyMap()
  )
  ```
* In `WorkoutRepository.loadWorkoutsChunked()`:
  * Fetches `lapsDb.getLapsForWorkouts(chunkIds)` in parallel with extrema and Strava data.
  * Injects `batchMetadata` into `mapper.fromCursor(c, batchMetadata)`.
* In `WorkoutData`:
  * Add field `val laps: List<LapData> = emptyList()`.

---

## 5. UI Component Architecture: `WorkoutLaps`

### 5.1 Component Design (`WorkoutLaps.kt`)
* **Placement**: Located inside `WorkoutSummary.kt` directly following `WorkoutExtrema`.
* **Visibility Rule**: Only displayed if `workoutData.laps.isNotEmpty()`. If an activity has 0 recorded laps, the section is completely omitted with zero padding overhead.
* **Expandable Ergonomics**:
  * If `laps.size <= 3`: Display all laps directly.
  * If `laps.size > 3`: Default to collapsed showing the first 3 laps plus an expandable toggle button: *"Show all X laps"* / *"Show fewer"*. This prevents long interval sessions (e.g. 15–20 laps) from dominating vertical space in `WorkoutList`.
* **Performance Badges (Fastest & Slowest Laps)**:
  * When `laps.size >= 2`:
    * **Fastest Lap (Rabbit 🐇)**: Lap with the highest valid `speedAverageMps` (lowest pace). Styled with subtle green/teal badge: `🐇 Fastest`.
    * **Slowest Lap (Hedgehog 🦔)**: Lap with the lowest valid `speedAverageMps` (`> 0`). Styled with subtle amber/orange badge: `🦔 Slowest`.
  * If all laps have identical speed (or single lap session), badges are omitted.

### 5.2 Split Column Layout
| Column | Alignment | Formatting Source | Example |
| :--- | :--- | :--- | :--- |
| **Lap / Name** | Start | `lap.getDisplayName(index)` + Description snippet if present | **Lap 1**<br>*Warmup jog* |
| **Time** | End | `formatters.time.format(lap.timeTotalS.toLong())` | `04:15` |
| **Distance** | End | `formatters.distance.format_with_units(lap.distanceTotalM)` | `1.00 km` |
| **Pace / Speed** | End | Sport-dependent (`pace.format_with_units` for run/walk, `speed.format_with_units` for bike) | `4:15 min/km`<br>`28.4 km/h` |
| **Badge** | Center | Rabbit 🐇 or Hedgehog 🦔 pill badge | `🐇` / `🦔` |

### 5.3 Map Integration Deferral
In accordance with user feedback (*"For the map integration, we need some more thoughts."*), spatial map segment highlighting is deferred to a future dedicated design ticket under Epic ATT-826.

---

## 6. System Invariants & Non-Regression Rules

1. **Non-Destructive Schema Migration**: `onUpgrade` in `LapsDbHelper` MUST NOT drop `Laps.TABLE`. All existing lap split times, distances, and speeds MUST be preserved across the upgrade from `DB_VERSION = 1` to `2`.
2. **List Scrolling Performance (Zero N+1 Queries)**: Loading 100 workouts in `WorkoutList` MUST NOT issue 100 individual SQL queries for laps. Laps MUST be batch-fetched using `getLapsForWorkouts(chunkIds)` alongside existing chunked metadata.
3. **Card Touch Invariants (`REQ-SET-071` / ATT-850)**: Clicking the general surface of `WorkoutLaps` MUST navigate to the detailed map screen (`onMapClick`), consistent with other body sections in `WorkoutSummary`. (In ATT-511, tapping an individual lap row's edit trigger will launch the lap editor).
4. **Formatter Consistency (`REQ-UI-016`)**: Lap times, distances, and speeds MUST use `LocalMetricFormatter.current` to respect user locale and unit preferences (Metric vs. Imperial).
5. **Zero Laps Invariant**: Workouts with 0 laps MUST NOT display an empty card, headers, or blank dividers.

---

## 7. Stage 2 Requirements & Test Mapping

* **New Functional Requirement**: `REQ-UI-141` (*Workout Summary Lap Overview & Performance Highlights*).
* **New Verification Test**: `TST-UI-094` (*Workout Lap Overview Table & Rabbit/Hedgehog Highlight Verification*).
