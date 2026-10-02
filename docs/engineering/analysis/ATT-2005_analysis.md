# Stage 1 Analysis: ATT-2005 - [WorkoutSummaries] Support Marking Workouts as "Race" (Wettkampf) Across DB, Header Badge, Edit Dialog, and Filters

**Ticket**: [ATT-2005](https://rainerblind.atlassian.net/browse/ATT-2005)  
**Sub-task**: [ATT-2024](https://rainerblind.atlassian.net/browse/ATT-2024) (`[Analysis]`)  
**Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Improve WorkoutSummaries*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.11`  
**Branch**: `feature/ATT-2005`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Statement & Motivation

Endurance athletes (runners, cyclists, triathletes) regularly participate in competitive events such as marathons, 10k road races, gran fondos, criteriums, time trials, and triathlons. In a competitive athlete's training log, competitive events represent pivotal milestone activities that demand clear distinction from daily training, recovery runs, or commutes.

Currently, `aTrainingTracker` supports classifying workouts by Sport (`sportId`, `bSportType`), Equipment (`equipmentId`), Commute (`commute`), and Stationary Trainer (`trainer`). However, there is no mechanism to designate a workout as a **Race** (Wettkampf).

Athletes face several shortcomings due to this gap:
1. **Lack of Visual Identity**: A grueling marathon or cycling race appears identical to an easy recovery jog or training spin in workout summaries and list headers.
2. **Missing External Sync Classification**: When syncing completed activities to external platforms such as Strava, the activity cannot be automatically designated with Strava's native race workout type (`workout_type = 1` for Run race, `workout_type = 11` for Bike race).
3. **Inability to Filter Race History**: Athletes cannot quickly review or audit their historical races, seasonal PRs, or competitive milestones via the workout filter bottom sheet.
4. **No User Preference Customization**: Users who do not race have no way to hide the race toggle in the edit dialog if they prefer a minimal editing interface.

This feature introduces first-class **Race (Wettkampf)** support across the persistence layer, domain models, header presentation, edit workflow, list filtering, preferences, and cloud uploaders.

---

## 2. Root Cause Analysis (Forensic Investigation & Architectural Gap Analysis)

Forensic examination of the current codebase reveals the exact architectural points requiring extension:

### 2.1 SQLite Schema & Persistence Layer (`WorkoutSummariesDatabaseManager.java`)
- The `WorkoutSummaries.TABLE` currently contains 22 schema revisions (`DB_VERSION = 22`).
- The table schema definition includes `COMMUTE int, TRAINER int, UPLOAD_TO_STRAVA int DEFAULT -1`, but lacks a column for `race`.
- `updateWorkoutData` explicitly populates `ContentValues` with fields like `COMMUTE`, `TRAINER`, and `UPLOAD_TO_STRAVA`, but ignores race status.
- **Architectural Requirement**: 
  - Add constant `WorkoutSummaries.RACE = "race"`.
  - Increment `DB_VERSION` from `22` to `23`.
  - Add `+ WorkoutSummaries.RACE + " int DEFAULT 0,"` to `CREATE_TABLE_WORKOUT_SUMMARIES`.
  - In `onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)`:
    ```java
    if (oldVersion < 23) {
        Log.i(TAG, "upgrading to DB version 23 (Adding race column)");
        addColumnIfNotExists(db, WorkoutSummaries.TABLE, WorkoutSummaries.RACE, "int", "0");
    }
    ```
  - In `updateWorkoutData(WorkoutData workoutData)`: persist `values.put(WorkoutSummaries.RACE, workoutData.getRace() ? 1 : 0);`.

### 2.2 Domain Models & Mappers
- `WorkoutData.kt`: Missing `val race: Boolean = false` property and getter.
- `WorkoutHeaderData.kt`: Missing `val race: Boolean = false` property.
- `WorkoutData.headerData`: Currently constructs `WorkoutHeaderData` without mapping `race`.
- `WorkoutDataMapper.kt`: Cursor projection mappings (`toWorkoutData`, `toWorkoutHeaderData`, etc.) do not extract the `race` column index or deserialize the boolean flag.
- `WorkoutRepository.kt`: In `saveWorkout(userEditedWorkout: WorkoutData?)`, the in-memory copy block lines 1164–1184 copies `commute`, `trainer`, `uploadToStrava`, etc., but must also copy `race = userEditedWorkout.race` to immediately reflect edits in active UI state flows.

### 2.3 Workout Header Presentation (`WorkoutHeader.kt`)
- In `WorkoutHeader.kt` (Row A: Sport specific info row), the header currently displays sport name, distance/time, and badges.
- **Aesthetic Constraint**: The user and product design guidelines explicitly mandate:
  - Athletic-grade Material 3 visual styling.
  - Zero raw unicode emojis (strictly no `🏁` or raw emoji characters).
  - Use vector iconography (`Icons.Default.EmojiEvents` or vector drawable `ic_trophy`) with subtle tonal container styling (`MaterialTheme.colorScheme.tertiaryContainer` / `onTertiaryContainer`) and localized string text (e.g. "Wettkampf" / "Race").

### 2.4 Edit Workout UI & Preference Configuration
- `EditWorkoutViewModel.kt`: Currently manages state flows for editable fields (`updateCommute`, `updateTrainer`, etc.). Needs `updateIsRace(isChecked: Boolean)`.
- `EditWorkoutScreen.kt`: Lacks a checkbox/switch for toggling the race attribute.
- `MyPreferenceManager.kt`: The `EditWorkoutFieldPreferences` data class configures which fields appear in `EditWorkoutScreen`. It needs `val showRace: Boolean = true` (defaulting to enabled, serialized to SharedPreferences `KEY_SHOW_RACE`).
- `AdvancedTuningDialog.kt` / Settings UI: Needs a toggle in the "Workout Masks" / Field visibility preferences to allow users to hide/show the Race field.

### 2.5 Workout Filtering (`WorkoutFilterCriteria.kt` & `WorkoutFilterBottomSheet.kt`)
- `WorkoutFilterCriteria.kt`: Contains filtering criteria like `sportIds`, `dateRange`, `commute`, `trainer`. Missing `val isRace: Boolean? = null`.
- `matches(workout: WorkoutData)` predicate must evaluate:
  ```kotlin
  if (isRace != null && workout.race != isRace) return false
  ```
- JSON serialization (`toJson`, `fromJson`) must include `isRace` for state persistence and filter presets.
- `WorkoutFilterBottomSheet.kt` / `WorkoutFilterCriteriaUi.kt`: Must provide an interactive filter chip for Race events.

### 2.6 Strava Upload Integration (`StravaUploader.kt`)
- When uploading an activity to Strava via `https://www.strava.com/api/v3/uploads`, Strava's API accepts a multipart form field `workout_type`:
  - For Ride / Cycling (`BSportType.BIKE`): `10` = Default, `11` = Race, `12` = Workout.
  - For Run (`BSportType.RUN`): `0` = Default, `1` = Race, `2` = Long Run, `3` = Workout.
- `StravaUploader.kt` does not currently query or attach `workout_type`. Querying `race` from the database and adding `workout_type` ("11" for bike race, "1" for run race) ensures native Strava race badge synchronization.

### 2.7 9-Language Localization
- All user-facing strings (`race`, `race_badge`, `pref_show_race`, `filter_race`, etc.) must be translated across all 9 supported locales:
  - English (`values`)
  - German (`values-de`)
  - Spanish (`values-es`)
  - French (`values-fr`)
  - Italian (`values-it`)
  - Japanese (`values-ja`)
  - Dutch (`values-nl`)
  - Polish (`values-pl`)
  - Portuguese (`values-pt`)

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Add column `WorkoutSummaries.RACE` (`"race"`, `int DEFAULT 0`) with `DB_VERSION` upgrade `22 -> 23` and safe idempotent migration.
  2. Map `race: Boolean` through `WorkoutData`, `WorkoutHeaderData`, `WorkoutDataMapper`, and `WorkoutRepository`.
  3. Render athletic-grade Material 3 Race badge in `WorkoutHeader.kt` Row A when `data.race == true`, using vector trophy icon and localized text (zero raw emojis).
  4. Provide Race toggle in `EditWorkoutScreen.kt` and `EditWorkoutViewModel.kt`.
  5. Add `showRace` preference toggle in `MyPreferenceManager.kt` (`EditWorkoutFieldPreferences`) and `AdvancedTuningDialog.kt`.
  6. Add `isRace: Boolean?` to `WorkoutFilterCriteria.kt` and `WorkoutFilterBottomSheet.kt`.
  7. Map `race = true` to Strava API `workout_type` (`11` for bike, `1` for run) in `StravaUploader.kt`.
  8. Full 9-language localization parity for all newly introduced string resources.
  9. Comprehensive unit, migration, and visual contract tests.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. Altering other third-party upload services (e.g. Komoot, TrainingPeaks) beyond Strava and standard TCX/GPX export metadata.
  2. Modifying live recording HUD screens to toggle race mid-recording (race is designated post-workout in edit dialog or summary).
  3. Adding race-specific leaderboards, external race registration APIs, or bib number tracking (reserved for future epic ATT-75).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Audit Statement**: Net-new requirement only (**`REQ-UI-236`**). No existing requirements in `docs/requirements.md` are altered or deleted.
* **Target Requirement**: `REQ-UI-236: Workout Classification as Race (Wettkampf)`
* **Parent Epic**: `ATT-68` (*Improve WorkoutSummaries*)
* **Verification ID**: `TST-UI-195`

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Database Schema Migration
- File: `com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManager.java`
- Increment `DB_VERSION = 23`.
- Define column constant:
  ```java
  public static final String RACE = "race";
  ```
- Update `CREATE_TABLE_WORKOUT_SUMMARIES` to append `+ WorkoutSummaries.RACE + " int DEFAULT 0,"`.
- Update `onUpgrade`:
  ```java
  if (oldVersion < 23) {
      Log.i(TAG, "upgrading to DB version 23 (Adding race column)");
      addColumnIfNotExists(db, WorkoutSummaries.TABLE, WorkoutSummaries.RACE, "int", "0");
  }
  ```
- Update `updateWorkoutData` to write `values.put(WorkoutSummaries.RACE, workoutData.getRace() ? 1 : 0);`.
- Update `insertWorkout` (if applicable) to persist race column.

### 5.2 Domain Models & Mappers
- `WorkoutData.kt`:
  ```kotlin
  val race: Boolean = false
  ```
- `WorkoutHeaderData.kt`:
  ```kotlin
  val race: Boolean = false
  ```
- `WorkoutDataMapper.kt`:
  ```kotlin
  race = cursor.getColumnIndex(WorkoutSummaries.RACE).takeIf { it >= 0 }?.let { cursor.getInt(it) == 1 } ?: false
  ```
- `WorkoutRepository.kt`:
  In `saveWorkout`, update the in-memory copy block to include `race = userEditedWorkout.race`.

### 5.3 Workout Header Badge (Material 3 Aesthetic)
- File: `com/atrainingtracker/trainingtracker/ui/aftermath/header/WorkoutHeader.kt`
- Location: Row A (alongside sport name and type info).
- Implementation: When `data.race` is true, render a compact tonal chip:
  ```kotlin
  Surface(
      shape = RoundedCornerShape(4.dp),
      color = MaterialTheme.colorScheme.tertiaryContainer,
      contentColor = MaterialTheme.colorScheme.onTertiaryContainer
  ) {
      Row(
          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
          Icon(
              imageVector = Icons.Default.EmojiEvents,
              contentDescription = stringResource(R.string.race_badge),
              modifier = Modifier.size(14.dp)
          )
          Text(
              text = stringResource(R.string.race_badge),
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold
          )
      }
  }
  ```
- Strictly zero raw emoji characters (`🏁`).

### 5.4 Edit Workout UI & Preferences
- File: `com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutViewModel.kt`
  - Add `fun updateIsRace(isChecked: Boolean)` mutating `_workoutData.value = _workoutData.value?.copy(race = isChecked)`.
- File: `com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt`
  - In the field list (respecting `fieldPreferences.showRace`), render a togglable row or checkbox for Race.
- File: `com/atrainingtracker/trainingtracker/preferences/MyPreferenceManager.kt`
  - In `EditWorkoutFieldPreferences`: add `val showRace: Boolean = true`.
  - Add SharedPreferences key `KEY_SHOW_RACE = "show_race"`.
- File: `com/atrainingtracker/trainingtracker/ui/settings/AdvancedTuningDialog.kt`
  - Add checkbox for "Race" field visibility under Edit Workout Field Visibility.

### 5.5 Workout List Filtering
- File: `com/atrainingtracker/trainingtracker/ui/workoutlist/WorkoutFilterCriteria.kt`
  - Add `val isRace: Boolean? = null`.
  - In `matches(workout: WorkoutData)`:
    ```kotlin
    if (isRace != null && workout.race != isRace) return false
    ```
  - Update `toJson()` and `fromJson()`.
- File: `com/atrainingtracker/trainingtracker/ui/workoutlist/WorkoutFilterBottomSheet.kt`
  - Add filter chip toggle for Race.

### 5.6 Strava API Synchronization
- File: `com/atrainingtracker/trainingtracker/strava/StravaUploader.kt`
  - When preparing multipart upload parameters, query `WorkoutSummaries.RACE` or read `workoutData.race`.
  - If `race == true`:
    ```kotlin
    val workoutType = when (bSportType) {
        BSportType.BIKE -> "11" // Strava Race Ride
        BSportType.RUN -> "1"   // Strava Race Run
        else -> null
    }
    workoutType?.let { formBuilder.add("workout_type", it) }
    ```

### 5.7 9-Language Localization
- Keys to add across 9 `strings.xml` files:
  - `race`: "Race" / "Wettkampf" / "Carrera" / "Course" / "Gara" / "レース" / "Wedstrijd" / "Wyścig" / "Corrida"
  - `race_badge`: "Race" / "Wettkampf" / ...
  - `pref_show_race`: "Show 'Race' in edit dialog" / "'Wettkampf' im Bearbeiten-Dialog anzeigen" / ...
  - `filter_race_only`: "Races only" / "Nur Wettkämpfe" / ...

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. **Zero Data Loss on Migration**: Adding the `race` column via `addColumnIfNotExists` with default `0` must be 100% idempotent and backwards-compatible with existing SQLite databases.
  2. **Thread Safety**: All database upgrades and write operations occur off the main UI thread via existing coroutine IO dispatchers.
  3. **Visual Quality**: Zero raw emojis in the UI; only athletic Material 3 vector assets with responsive font scaling and dark/light theme tonal adaptation.
  4. **Strict Parent Gate Governance**: Subtask `ATT-2024` through `ATT-2028` transition through their lifecycle, but parent ticket `ATT-2005` stops at `Final Review (Human)`.

* **Risk Rating**: **LOW**
  - Schema change is additive with a default value of 0.
  - Domain models utilize immutable data classes with default `race = false`.
  - UI components follow existing modular patterns (`WorkoutHeader`, `EditWorkoutScreen`, `WorkoutFilterBottomSheet`).
