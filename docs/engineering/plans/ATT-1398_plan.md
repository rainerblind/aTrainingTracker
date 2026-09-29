# Stage 3 Implementation Plan: ATT-1398 Lieblingsorte Intelligent Workout Auto-Naming

**Ticket**: [ATT-1398](https://atrainingtracker.atlassian.net/browse/ATT-1398)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.3`  
**Subtask**: `ATT-1566`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Architectural Design (`SWE.2`)

To eliminate cryptic workout names (e.g. timestamps like `2026-09-25_19-15-10`) and generic cluster proposals (`Workout at 2026-09-25`), we introduce a pure, testable naming resolver (`WorkoutAutoNamingHelper`) and wire it cleanly into `TrackerService` session finalization and `WorkoutClusterEngine` cluster proposal seeding.

```
+------------------------------------+
|        TrackerService.java         |
|  (Session Finalization Workflow)   |
+-----------------+------------------+
                  |
         [suggestCluster == null]
                  |
                  v
+-----------------+------------------+       +-----------------------------------+
|     WorkoutAutoNamingHelper        | ----> |   KnownLocationsDatabaseManager   |
|   (Sport & Location Heuristics)    |       |   .getMyLocation(startPos/endPos) |
+-----------------+------------------+       +-----------------------------------+
                  |
        [Resolved Auto-Name]
                  |
                  v
+-----------------+------------------+
|   WorkoutSummariesDatabaseManager  |
|    .update(TABLE, WORKOUT_NAME)    |
+------------------------------------+
```

---

## 2. Component Deconstruction

### Component 1: `WorkoutAutoNamingHelper.kt`
* **Package**: `com.atrainingtracker.trainingtracker.database`
* **Role**: Stateless singleton evaluating sport type and location endpoints.
* **API**:
  ```kotlin
  object WorkoutAutoNamingHelper {
      fun generateWorkoutName(
          context: Context,
          sportType: BSportType,
          startLocation: MyLocation?,
          endLocation: MyLocation?,
          distanceBetweenEndpoints: Float = Float.MAX_VALUE
      ): String?
  }
  ```
* **Heuristics**:
  - **Round-Trip** (`startLocation != null && (startLocation.id == endLocation?.id || distanceBetweenEndpoints <= startLocation.radius)`):
    - `RUN`: `@string/workout_name_run_from` ("Run from %1$s" / "Lauf ab %1$s")
    - `BIKE`: `@string/workout_name_ride_from` ("Ride from %1$s" / "Fahrt ab %1$s")
    - Other: `@string/workout_name_round_trip` ("Loop from %1$s" / "Runde ab %1$s")
  - **Point-to-Point** (`startLocation != null && endLocation != null && startLocation.id != endLocation.id`):
    - `RUN`: `@string/workout_name_run_from_to` ("Run from %1$s to %2$s" / "Lauf von %1$s nach %2$s")
    - `BIKE`: `@string/workout_name_ride_from_to` ("Ride from %1$s to %2$s" / "Fahrt von %1$s nach %2$s")
    - Other: `@string/workout_name_activity_from_to` ("Activity from %1$s to %2$s" / "Aktivität von %1$s nach %2$s")
  - **Start Only Known** (`startLocation != null && endLocation == null`):
    - `RUN`: `@string/workout_name_run_from`
    - `BIKE`: `@string/workout_name_ride_from`
    - Other: `@string/workout_name_activity_from`
  - **Destination Only Known** (`startLocation == null && endLocation != null`):
    - `RUN`: `@string/workout_name_run_to` ("Run to %1$s" / "Lauf nach %1$s")
    - `BIKE`: `@string/workout_name_ride_to` ("Ride to %1$s" / "Fahrt nach %1$s")
    - Other: `@string/workout_name_activity_to`
  - **Neither Known**:
    - Returns `null`.

### Component 2: `TrackerService.java`
* In `finalizeWorkoutSummary()`, when `suggestion == null` and `startPosRaw != null && endPosRaw != null`:
  - Query `KnownLocationsDatabaseManager.getMyLocation(startPosRaw)` and `getMyLocation(endPosRaw)`.
  - Calculate `distanceBetween(startPosRaw, endPosRaw)`.
  - Invoke `WorkoutAutoNamingHelper.generateWorkoutName(...)`.
  - If a non-null auto-name is returned, update `WorkoutSummaries.WORKOUT_NAME` in SQLite.

### Component 3: `WorkoutClusterEngine.kt`
* In `learnFromWorkout()` / `assignClusterToWorkout()`:
  - If a new cluster proposal name is seeded, query `KnownLocationsDatabaseManager.getMyLocation(start)`.
  - If recognized, default seed name to `@string/cluster_seed_name_loop_format` ("Loop from %1$s" / "Runde ab %1$s") instead of generic `cluster_default_name_format`.

### Component 4: 9-Language Localization
* Add 11 format keys across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.

---

## 3. Invariants & Guardrails

- **Precedence Invariant**: Existing cluster matches (`suggestion != null`) ALWAYS win over location auto-naming.
- **Manual User Edit Invariant**: User-edited workout names via `WorkoutRepository.setWorkoutName` MUST NEVER be overwritten.
- **Zero Lock Contention**: `KnownLocationsDatabaseManager` queries execute read-only queries with index lookups, completing in <1ms without blocking the finalization thread.
- **Backward Compatibility**: If no known location matches, workout naming gracefully retains `mBaseFileName` with zero exceptions.

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)

1. **Step 1**: Add 11 localized format strings across all 9 `strings.xml` resource files.
2. **Step 2**: Create `WorkoutAutoNamingHelper.kt` with pure resolution logic and unit tests in `WorkoutAutoNamingHelperTest.kt`.
3. **Step 3**: Integrate `WorkoutAutoNamingHelper` into `TrackerService.java` workout finalization.
4. **Step 4**: Integrate location-based cluster seed naming into `WorkoutClusterEngine.kt`.
5. **Step 5**: Execute targeted unit tests and full clean-room regression suite (`./gradlew testDebugUnitTest`).
