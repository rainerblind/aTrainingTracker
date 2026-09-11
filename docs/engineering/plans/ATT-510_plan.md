# Implementation Plan: Overview of Laps in Workout Summaries (ATT-510)

* **Parent Ticket**: [ATT-510](https://rainerblind.atlassian.net/browse/ATT-510) (*[Feature] Overview of laps in Workout Summaries*)
* **Active Sub-Task**: [ATT-893](https://rainerblind.atlassian.net/browse/ATT-893) (*[Subtask] [Impl-Plan] Overview of laps in Workout Summaries*)
* **Parent Epic**: [ATT-826](https://rainerblind.atlassian.net/browse/ATT-826) (*[Epic] Laps*)
* **Target Version**: `V4.9.36`
* **Branch**: `feature/ATT-510`
* **Requirement**: `REQ-UI-141`
* **Verification**: `TST-UI-094`

---

## 1. Executive Summary & Design Overview

This plan establishes the architecture and implementation steps to display a structured overview of all recorded laps in `WorkoutSummary` cards across workout history views (`WorkoutTabsScreen`, `WorkoutList.kt`), in full compliance with `REQ-UI-141` and `TST-UI-094`.

Key capabilities:
1. **Database Schema Upgrade (`Laps.db` DB_VERSION = 2)**:
   - Non-destructive `ALTER TABLE` adding nullable columns `name TEXT` and `description TEXT`.
   - Replaces the legacy dangerous `onUpgrade` behavior (`drop table if exists Laps`) to guarantee 100% preservation of all historical athlete data.
   - Groundwork for individual lap naming and descriptions (ATT-511).
2. **Vectorized Batch Query Architecture (`LapsDatabaseManager.java`)**:
   - Introduces `getLapsForWorkouts(List<Long> workoutIds): Map<Long, List<LapData>>`.
   - Integrates with `WorkoutRepository` chunked batch loading (ATT-346 / ATT-359), eliminating N+1 database queries during list scrolling.
3. **UI Split Table & Performance Badges (`WorkoutLaps.kt`)**:
   - Displays lap names (defaulting to `"Lap X"`, or custom name), formatted split active time, distance, and sport-appropriate pace/speed.
   - Highlights the **Fastest Lap** with a Rabbit badge (`🐇`) and the **Slowest Lap** with a Hedgehog badge (`🦔`) when $\ge 2$ laps exist.
   - Expandable threshold: defaults to showing 3 laps when $> 3$ laps, with a *"Show all X laps"* / *"Show fewer"* toggle.
   - Seamlessly omitted when a workout contains 0 recorded laps.

---

## 2. Technical Architecture & File Inventory

### 2.1 Component 1: Domain Model (`LapData.kt`)
* **File [NEW]**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/LapData.kt`
* **Purpose**: Immutable domain model representing a single lap.
* **Properties**:
  * `id: Long = 0` (Primary key in `_id`)
  * `workoutId: Long`
  * `lapNr: Long` (0- or 1-based internal sequence)
  * `timeStart: String? = null`
  * `timeTotalS: Int` (Active lap duration in seconds)
  * `distanceTotalM: Double` (Lap distance in meters)
  * `speedAverageMps: Double` (Lap average speed in m/s)
  * `name: String? = null` (Optional custom name, e.g. "Interval 1")
  * `description: String? = null` (Optional notes/description)
* **Helper Methods**:
  * `getDisplayName(fallbackIndex: Int): String`: returns `name` if not blank, else `"Lap $fallbackIndex"`.

---

### 2.2 Component 2: Database Layer (`LapsDatabaseManager.java`)
* **File [MODIFY]**: `app/src/main/java/com/atrainingtracker/trainingtracker/database/LapsDatabaseManager.java`
* **Changes**:
  1. Define column constants in `Laps`:
     * `public static final String NAME = "name";`
     * `public static final String DESCRIPTION = "description";`
  2. In `LapsDbHelper`:
     * Bump `DB_VERSION = 2`.
     * Update `CREATE_TABLE` to include `NAME + " TEXT, " + DESCRIPTION + " TEXT"`.
     * Overhaul `onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)`:
       ```java
       if (oldVersion < 2) {
           db.execSQL("ALTER TABLE " + Laps.TABLE + " ADD COLUMN " + Laps.NAME + " TEXT;");
           db.execSQL("ALTER TABLE " + Laps.TABLE + " ADD COLUMN " + Laps.DESCRIPTION + " TEXT;");
       }
       ```
  3. Single retrieval method:
     * `public List<LapData> getLaps(long workoutId)`:
       Queries `Laps` where `workoutID = ?` ordered by `lapNr ASC, _id ASC`.
  4. Vectorized batch retrieval method:
     * `public Map<Long, List<LapData>> getLapsForWorkouts(List<Long> workoutIds)`:
       Queries `Laps` where `workoutID IN (?, ?, ...)` chunked by 500 IDs, returning `Map<Long, List<LapData>>`.
  5. Lap mutation method (ATT-511 groundwork):
     * `public boolean updateLapDetails(long workoutId, long lapNr, @Nullable String name, @Nullable String description)`

---

### 2.3 Component 3: Data Mapping & Repository Layer
* **File [MODIFY]**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutData.kt`
  * Add field: `val laps: List<LapData> = emptyList()`.
* **File [MODIFY]**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDataMapper.kt`
  * In `BatchMetadata`: add `val laps: Map<Long, List<LapData>> = emptyMap()`.
  * In `fromCursor(cursor: Cursor)`: resolve single workout laps via `lapsDb.getLaps(workoutId)`.
  * In `fromCursor(cursor: Cursor, batch: BatchMetadata)`: assign `batch.laps[workoutId] ?: emptyList()`.
* **File [MODIFY]**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt`
  * In `loadWorkoutsChunked()`: batch-fetch `lapsDb.getLapsForWorkouts(chunkIds)` and include in `BatchMetadata`.
  * In `reloadWorkoutData(workoutId: Long)`: refresh workout data including fresh laps.

---

### 2.4 Component 4: UI Component Layer (`WorkoutLaps.kt` & `WorkoutSummary.kt`)
* **File [NEW]**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/WorkoutLaps.kt`
  * Dedicated composable `WorkoutLaps`:
    * Computes fastest lap (max `speedAverageMps`) and slowest lap (min `speedAverageMps` > 0) when `laps.size >= 2`.
    * Renders section header: *"Laps (X)"* with lap count.
    * Expandable toggle: when `laps.size > 3`, shows first 3 laps by default with a *"Show all X laps"* / *"Show fewer"* text button.
    * Table row layout:
      * Column 1: Lap display name (`getDisplayName(index)`) + optional description note below.
      * Column 2: Split Time (`formatters.time.format(lap.timeTotalS.toLong())`).
      * Column 3: Split Distance (`formatters.distance.format_with_units(lap.distanceTotalM)`).
      * Column 4: Split Pace/Speed (sport-aware via `LocalMetricFormatter`).
      * Column 5: Performance Badge (`🐇` Rabbit for fastest, `🦔` Hedgehog for slowest).
* **File [MODIFY]**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt`
  * Render `WorkoutLaps(workoutData.laps, workoutData.bSportType, modifier = mapClickModifier)` directly after `WorkoutExtrema`.
  * Preserves `mapClickModifier` for whole-card map navigation (`REQ-SET-071`).
* **File [MODIFY]**: `app/src/main/res/values/strings.xml` (+ localized variants)
  * String resources:
    * `laps_header`: "Laps (%1$d)"
    * `show_all_laps`: "Show all %1$d laps"
    * `show_fewer_laps`: "Show fewer"
    * `fastest_lap`: "Fastest lap"
    * `slowest_lap`: "Slowest lap"

---

## 3. Detailed Verification Plan

### 3.1 Automated Clean-Room Unit Tests
1. **`LapsDatabaseManagerTest.kt`** (`app/src/test/java/com/atrainingtracker/trainingtracker/database/LapsDatabaseManagerTest.kt`):
   * Test database creation and upgrade from `v1` to `v2`.
   * Assert non-destructive migration: historical rows retain correct split metrics and get `null` for `name` and `description`.
   * Test `getLaps(workoutId)` single query ordering and contents.
   * Test `getLapsForWorkouts(workoutIds)` vectorized batch retrieval across multiple workout IDs.
   * Test `updateLapDetails(...)` mutation.
2. **`WorkoutDataMapperLapsTest.kt`** (`app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDataMapperLapsTest.kt`):
   * Test single mapping populates `WorkoutData.laps`.
   * Test batch mapping correctly indexes and assigns laps to corresponding workouts.
3. **`WorkoutLapsTest.kt`** (`app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/WorkoutLapsTest.kt`):
   * Test rabbit (`🐇`) badge assigned strictly to lap with highest average speed.
   * Test hedgehog (`🦔`) badge assigned strictly to lap with lowest average speed.
   * Test single-lap activities omit both rabbit and hedgehog badges.
   * Test identical speed laps omit badges.
   * Test default name fallback (`"Lap 1"`, `"Lap 2"`).
   * Test custom name display when present.
   * Test description display when present.
   * Test expandable threshold: initial count = 3 when total = 5; clicking expand shows 5.
   * Test empty laps collection returns empty/null without layout footprint.
4. **Full Regression Suite**:
   * Run `./gradlew testDebugUnitTest` across all modules.

---

## 4. System Invariants & Non-Regression Rules

1. **Zero Data Loss Invariant**: Under no circumstances may `Laps.db` table `Laps` be dropped during version upgrade.
2. **Zero N+1 Query Invariant**: Loading workout history in `WorkoutList` must batch-fetch laps in chunks, preserving $O(1)$ query batches per screen.
3. **Click-to-Map Navigation Invariant (`REQ-SET-071`)**: Tapping the `WorkoutLaps` area continues to route to `TrackOnMapScreen`.
4. **Formatter Invariant (`REQ-UI-016`)**: Metric formatting must strictly utilize `LocalMetricFormatter.current`.
5. **Zero-Laps Invariant**: Activities without laps must not display any header, card, or empty space.
