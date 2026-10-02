# Stage 3: Implementation Plan - ATT-2005: [WorkoutSummaries] Support Marking Workouts as "Race" (Wettkampf) Across DB, Header Badge, Edit Dialog, and Filters

**Ticket**: [ATT-2005](https://rainerblind.atlassian.net/browse/ATT-2005)  
**Sub-task**: [ATT-2026](https://rainerblind.atlassian.net/browse/ATT-2026) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Improve WorkoutSummaries*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.11`  
**Requirement Mapping**: `REQ-UI-236`  
**Test Mapping**: `TST-UI-195`  
**Branch**: `feature/ATT-2005`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Description & Background

Athletes participating in endurance events (marathons, road races, gran fondos, criteriums, time trials) need to mark completed sessions as a "Race" (Wettkampf) to distinguish competitive efforts from standard training logs. 

This requires an end-to-end implementation across:
1. **SQLite Database Persistence**: Adding column `race` with a safe, idempotent database migration from `DB_VERSION = 22` to `23`.
2. **Domain Models & Mappers**: Wiring `race: Boolean` through `WorkoutData`, `WorkoutHeaderData`, cursor mappers, and repository in-memory cache updates.
3. **Workout Header UI**: Rendering an athletic-grade Material 3 badge in Row A with vector trophy icon (`Icons.Default.EmojiEvents`), `tertiaryContainer` tone, and localized text—strictly prohibiting raw unicode emojis.
4. **Edit Workout Screen & Preferences**: Providing a toggle switch in the edit dialog and a preference toggle (`showRace`) in Advanced Settings under Workout Masks.
5. **Workout List Filters**: Adding `isRace: Boolean?` to filter criteria, bottom sheet chips, and serialization.
6. **External Platform Integration**: Mapping `race = true` to Strava's `workout_type` ("11" for Bike, "1" for Run).
7. **9-Language Localization**: Full translation parity across all 9 supported locales.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-236` (*WorkoutSummaries: Support Marking Workouts as "Race" (Wettkampf) Across DB, Header Badge, Edit Dialog, and Filters*)
* **Test Mapping**: `TST-UI-195` (*WorkoutSummaries: Support Marking Workouts as "Race" (Wettkampf) Verification Across DB, Header, Edit UI, and Filters*)
  * `[TST-UI-195.1]`: SQLite schema migration & persistence unit tests (`WorkoutSummariesDatabaseRaceMigrationTest.kt`)
  * `[TST-UI-195.2]`: Domain model & mapper contract tests (`WorkoutDataRaceMappingTest.kt`)
  * `[TST-UI-195.3]`: Workout header race badge visual contract tests (`WorkoutHeaderRaceBadgeVisualContractTest.kt`)
  * `[TST-UI-195.4]`: Workout filter criteria predicate & serialization tests (`WorkoutFilterCriteriaRaceTest.kt`)
  * `[TST-UI-195.5]`: Strava uploader race workout type tests (`StravaUploaderRaceWorkoutTypeTest.kt`)
  * `[TST-UI-195.6]`: 9-language localization parity audit (`TranslationParityTest.kt`)
  * `[TST-UI-195.7]`: Full clean-room regression suite (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Data Loss on SQLite Migration**: Upgrading from DB version 22 to 23 adds column `race int DEFAULT 0` via `addColumnIfNotExists` idempotently without dropping tables or altering existing workout rows.
2. **Thread Safety & Dispatcher Affinity**: Database operations remain strictly confined to coroutine IO dispatchers (`Dispatchers.IO`).
3. **Athletic Design Guidelines**: Badge styling in `WorkoutHeader` uses Material 3 `Surface` with `tertiaryContainer` tone and vector iconography (`Icons.Default.EmojiEvents`), strictly containing zero raw unicode emojis.
4. **9-Language Parity**: All newly introduced string keys exist across all 9 locales with identical formatting tokens.
5. **Parent Human Gate Invariance**: Terminal completion of parent ticket `ATT-2005` remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: SQLite Persistence Layer (`WorkoutSummariesDatabaseManager.java`)
- Define column constant `public static final String RACE = "race";`.
- Increment `DB_VERSION` from 22 to 23.
- Update `CREATE_TABLE_WORKOUT_SUMMARIES` to append `+ WorkoutSummaries.RACE + " int DEFAULT 0,"`.
- Update `onUpgrade`:
  ```java
  if (oldVersion < 23) {
      Log.i(TAG, "upgrading to DB version 23 (Adding race column)");
      addColumnIfNotExists(db, WorkoutSummaries.TABLE, WorkoutSummaries.RACE, "int", "0");
  }
  ```
- Update `updateWorkoutData(WorkoutData workoutData)`:
  ```java
  values.put(WorkoutSummaries.RACE, workoutData.getRace() ? 1 : 0);
  ```

### Component 2: Domain Models & Mappers
- `WorkoutData.kt`: Add `val race: Boolean = false`.
- `WorkoutHeaderData.kt`: Add `val race: Boolean = false`.
- `WorkoutData.headerData`: Map `race = race`.
- `WorkoutDataMapper.kt`: In `fromCursor` / `fromCursorBatch`, extract `race` column safely via `cursor.getColumnIndex(WorkoutSummaries.RACE)`.
- `WorkoutRepository.kt`: In `saveWorkout`, include `race = userEditedWorkout.race` inside `current.copy(...)` for in-memory cache synchronization.

### Component 3: Workout Header Presentation (`WorkoutHeader.kt`)
- In Row A (Sport specific info row), when `data.race == true`, render:
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

### Component 4: Edit Workout Screen & ViewModel
- `EditWorkoutViewModel.kt`: Add `fun updateIsRace(isChecked: Boolean)` modifying `_workoutData.value = _workoutData.value?.copy(race = isChecked)`.
- `EditWorkoutScreen.kt`: Under `if (fieldPreferences.showRace)`, render a toggle / checkbox row for "Race" / "Wettkampf".

### Component 5: Preferences & Mask Customization
- `MyPreferenceManager.kt`: In `EditWorkoutFieldPreferences`, add `val showRace: Boolean = true`, persisted with `KEY_SHOW_RACE = "show_race"`.
- `AdvancedTuningDialog.kt`: Add a checkbox/switch for "Race" under Workout Masks / Edit Workout Fields.

### Component 6: Workout List Filtering
- `WorkoutFilterCriteria.kt`: Add `val isRace: Boolean? = null`.
- `matches(workout: WorkoutData)`:
  ```kotlin
  if (isRace != null && workout.race != isRace) return false
  ```
- Update `toJson()` and `fromJson()`.
- `WorkoutFilterBottomSheet.kt`: Add filter chip for Race.

### Component 7: Strava Upload Integration
- `StravaUploader.kt`: Query `WorkoutSummaries.RACE` or read `workoutData.race`. When `true`, add multipart parameter `workout_type` (`"11"` for `BSportType.BIKE`, `"1"` for `BSportType.RUN`).

### Component 8: 9-Language Localization
- Define keys in `strings.xml` across `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`:
  - `race`
  - `race_badge`
  - `pref_show_race`
  - `filter_race_only`

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Database Schema & Migration
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManager.java`
* **Changes**: Define `RACE = "race"`, bump `DB_VERSION = 23`, add column to `CREATE_TABLE_WORKOUT_SUMMARIES`, implement upgrade block in `onUpgrade`, and persist in `updateWorkoutData`.

### Step 2: Domain Model & Data Mappers
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutData.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/header/WorkoutHeaderData.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDataMapper.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt`
* **Changes**: Add `race: Boolean = false` property, cursor extraction, header data mapping, and repository in-memory cache update.

### Step 3: Material 3 Header Badge
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/header/WorkoutHeader.kt`
* **Changes**: Render M3 tonal chip in Row A with `Icons.Default.EmojiEvents` when `data.race == true`.

### Step 4: Edit Workout Screen, ViewModel & Preferences
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutViewModel.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutScreen.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/preferences/MyPreferenceManager.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/AdvancedTuningDialog.kt`
* **Changes**: Add `updateIsRace`, UI toggle row in `EditWorkoutScreen`, preference property in `EditWorkoutFieldPreferences`, and mask switch in `AdvancedTuningDialog`.

### Step 5: Filter Criteria & Bottom Sheet
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/workoutlist/WorkoutFilterCriteria.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/workoutlist/WorkoutFilterBottomSheet.kt`
* **Changes**: Add `isRace: Boolean?`, update predicate, update JSON serialization, and add filter chip to sheet.

### Step 6: Strava Uploader Integration
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/strava/StravaUploader.kt`
* **Changes**: Query `race` column or read `workoutData.race`, and append multipart `workout_type` (`"11"` for bike, `"1"` for run).

### Step 7: 9-Language Localization
* **Files**:
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-de/strings.xml`
  - `app/src/main/res/values-es/strings.xml`
  - `app/src/main/res/values-fr/strings.xml`
  - `app/src/main/res/values-it/strings.xml`
  - `app/src/main/res/values-ja/strings.xml`
  - `app/src/main/res/values-nl/strings.xml`
  - `app/src/main/res/values-pl/strings.xml`
  - `app/src/main/res/values-pt/strings.xml`
* **Changes**: Add localized strings for `race`, `race_badge`, `pref_show_race`, and `filter_race_only`.

### Step 8: Targeted Unit & Contract Tests
* **Author Tests**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseRaceMigrationTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDataRaceMappingTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/header/WorkoutHeaderRaceBadgeVisualContractTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/workoutlist/WorkoutFilterCriteriaRaceTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/strava/StravaUploaderRaceWorkoutTypeTest.kt`
* **Targeted Test Execution**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseRaceMigrationTest" \
                              --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDataRaceMappingTest" \
                              --tests "com.atrainingtracker.trainingtracker.ui.aftermath.header.WorkoutHeaderRaceBadgeVisualContractTest" \
                              --tests "com.atrainingtracker.trainingtracker.ui.workoutlist.WorkoutFilterCriteriaRaceTest" \
                              --tests "com.atrainingtracker.trainingtracker.strava.StravaUploaderRaceWorkoutTypeTest" \
                              --tests "com.atrainingtracker.trainingtracker.TranslationParityTest"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Execute targeted unit and visual contract tests in Stage 4, followed by full repository regression suite (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback Plan**: All work is isolated on branch `feature/ATT-2005`. In case of unexpected issues, the branch can be discarded cleanly without affecting `sprint/2026-40.11`.
