# Stage 2: Requirement & Test Specification - ATT-2005: [WorkoutSummaries] Support Marking Workouts as "Race" (Wettkampf) Across DB, Header Badge, Edit Dialog, and Filters

**Ticket**: [ATT-2005](https://rainerblind.atlassian.net/browse/ATT-2005)  
**Sub-task**: [ATT-2025](https://rainerblind.atlassian.net/browse/ATT-2025) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Improve WorkoutSummaries*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.11`  
**Requirement Mapping**: `REQ-UI-236` (*WorkoutSummaries: Support Marking Workouts as "Race" (Wettkampf) Across DB, Header Badge, Edit Dialog, and Filters*)  
**Test Spec ID**: `TST-UI-195`  
**Branch**: `feature/ATT-2005`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Requirement Specification (REQ-UI-236)

### 1.1 Problem Statement & Rationale
Endurance athletes regularly participate in competitive events and races (e.g. 10k, marathon, gran fondo, criterium, time trial). Athletes require a first-class "Race" (Wettkampf) classification to distinguish milestone competitions from everyday training sessions. This classification must display a distinctive athletic-grade badge in workout summaries, allow toggling in the edit dialog, provide filtering in the workout list, allow disabling via mask preferences, and automatically propagate to third-party services like Strava as native race workout types.

### 1.2 Functional & Architectural Requirements

1. **SQLite Database Schema & Migration (`WorkoutSummariesDatabaseManager.java`)**:
   - The system SHALL define `public static final String RACE = "race";`.
   - The database version SHALL be incremented from `DB_VERSION = 22` to `DB_VERSION = 23`.
   - `CREATE_TABLE_WORKOUT_SUMMARIES` SHALL include `+ WorkoutSummaries.RACE + " int DEFAULT 0,"`.
   - In `onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)`:
     - When `oldVersion < 23`, the system SHALL call `addColumnIfNotExists(db, WorkoutSummaries.TABLE, WorkoutSummaries.RACE, "int", "0")`.
   - In `updateWorkoutData(WorkoutData workoutData)`:
     - The system SHALL persist `values.put(WorkoutSummaries.RACE, workoutData.getRace() ? 1 : 0);`.

2. **Domain Models & Data Layer Mapping (`WorkoutData.kt`, `WorkoutHeaderData.kt`, `WorkoutDataMapper.kt`, `WorkoutRepository.kt`)**:
   - `WorkoutData.kt` SHALL include `val race: Boolean = false`.
   - `WorkoutHeaderData.kt` SHALL include `val race: Boolean = false`.
   - `WorkoutData.headerData` SHALL forward `race = race`.
   - `WorkoutDataMapper.kt` SHALL read `WorkoutSummaries.RACE` from Cursor (using `getColumnIndex` safely) and map `cursor.getInt(...) == 1` to `race`.
   - `WorkoutRepository.kt` in `saveWorkout` SHALL preserve `race = userEditedWorkout.race` during in-memory state copy so UI state flows reflect modifications immediately.

3. **Workout Header Badge (`WorkoutHeader.kt`)**:
   - In Row A (Sport specific info row), when `data.race == true`, the system SHALL render a Material 3 `Surface` chip:
     - Shape: `RoundedCornerShape(4.dp)`.
     - Container tone: `MaterialTheme.colorScheme.tertiaryContainer`.
     - Content tone: `MaterialTheme.colorScheme.onTertiaryContainer`.
     - Vector Icon: `Icons.Default.EmojiEvents` (14.dp) with localized content description `stringResource(R.string.race_badge)`.
     - Text: `stringResource(R.string.race_badge)`, styled with `MaterialTheme.typography.labelSmall`, bold.
   - The badge SHALL strictly contain zero raw unicode emojis (no `🏁` emoji characters).

4. **Edit Workout UI & Preferences (`EditWorkoutViewModel.kt`, `EditWorkoutScreen.kt`, `MyPreferenceManager.kt`, `AdvancedTuningDialog.kt`)**:
   - `EditWorkoutViewModel.kt` SHALL provide `fun updateIsRace(isChecked: Boolean)`, updating `_workoutData.value = _workoutData.value?.copy(race = isChecked)`.
   - `EditWorkoutScreen.kt` SHALL render a checkbox/switch for Race when `fieldPreferences.showRace == true`.
   - `MyPreferenceManager.kt` (`EditWorkoutFieldPreferences`) SHALL include `val showRace: Boolean = true`, persisted with key `KEY_SHOW_RACE = "show_race"`.
   - `AdvancedTuningDialog.kt` under "Workout Masks" SHALL provide a toggle for the Race field visibility.

5. **Workout List Filtering (`WorkoutFilterCriteria.kt`, `WorkoutFilterBottomSheet.kt`)**:
   - `WorkoutFilterCriteria.kt` SHALL include `val isRace: Boolean? = null`.
   - `matches(workout: WorkoutData)` predicate SHALL evaluate:
     ```kotlin
     if (isRace != null && workout.race != isRace) return false
     ```
   - JSON serialization and deserialization (`toJson`, `fromJson`) SHALL include `isRace`.
   - `WorkoutFilterBottomSheet.kt` SHALL render an interactive filter chip for Race events.

6. **External Platform Integration (`StravaUploader.kt`)**:
   - When uploading an activity to Strava where `race == true`, `StravaUploader.kt` SHALL append multipart form parameter `workout_type`:
     - For `BSportType.BIKE`: `formBuilder.add("workout_type", "11")` (Strava Race Ride).
     - For `BSportType.RUN`: `formBuilder.add("workout_type", "1")` (Strava Race Run).

7. **9-Language Localization Parity**:
   - All newly introduced string keys (`race`, `race_badge`, `pref_show_race`, `filter_race_only`) SHALL be translated across all 9 application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Database Migration & Persistence)**:
  * *Given* a SQLite database at schema version 22,
  * *When* upgraded to version 23,
  * *Then* the column `race` (int, default 0) SHALL be added without error, existing records SHALL retain default `race = 0`, and new records SHALL persist `race = 1` or `0`.
* **Criterion 2 (Edit Workout & Header Display)**:
  * *Given* an athlete editing a completed workout in `EditWorkoutScreen`,
  * *When* toggling the 'Race' checkbox to checked and saving,
  * *Then* `WorkoutHeader` in Row A SHALL immediately display the Race badge with the trophy icon and localized text.
* **Criterion 3 (Aesthetic & Visual Constraint)**:
  * *Given* a workout with `race == true`,
  * *When* `WorkoutHeader` renders,
  * *Then* the Race badge SHALL use Material 3 `tertiaryContainer` styling with vector icon `Icons.Default.EmojiEvents` and SHALL contain zero raw unicode emojis.
* **Criterion 4 (Workout List Filtering)**:
  * *Given* a workout list containing standard workouts and race workouts,
  * *When* applying filter `isRace = true`,
  * *Then* only workouts where `race == true` SHALL be displayed.
* **Criterion 5 (Mask Preference)**:
  * *Given* an athlete disabling `showRace` in Advanced Settings,
  * *When* opening `EditWorkoutScreen`,
  * *Then* the Race toggle SHALL NOT be rendered.
* **Criterion 6 (Strava Sync Workout Type)**:
  * *Given* an activity marked with `race = true`,
  * *When* uploaded to Strava,
  * *Then* multipart parameter `workout_type` SHALL be set to `"11"` for Bike and `"1"` for Run.

### 1.4 System Invariants
1. **Zero Data Loss**: Database upgrades must be fully backwards-compatible and idempotent.
2. **Thread Safety**: Database write and upgrade routines run strictly on IO dispatchers.
3. **No Raw Emojis**: Badges adhere to clean Material 3 vector styling.
4. **Parent Gate Governance**: Ticket `ATT-2005` stops at `Final Review (Human)`.

---

## 2. Test Specification (TST-UI-195)

### Test Case 1: Database Migration & Persistence (`[TST-UI-195.1]`)
* **Scope**: Database Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseRaceMigrationTest.kt`
* **Preconditions**: In-memory SQLite database initialized at `DB_VERSION = 22`.
* **Action**:
  1. Trigger upgrade to `DB_VERSION = 23`.
  2. Verify column `race` exists and defaults to 0.
  3. Insert/update a record with `race = true` and retrieve via cursor; verify `race == true`.
* **Expected Result**: Clean migration with 0 data loss and correct persistence.

### Test Case 2: Domain Model & Mapper Contract (`[TST-UI-195.2]`)
* **Scope**: Domain Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDataRaceMappingTest.kt`
* **Preconditions**: Mock/Robolectric cursor containing `WorkoutSummaries.RACE` column.
* **Action**:
  1. Test `WorkoutDataMapper` cursor extraction for `race = 1` and `race = 0`.
  2. Test `WorkoutData.headerData` forwards `race`.
  3. Test `WorkoutRepository.saveWorkout` in-memory update updates `race`.
* **Expected Result**: `race` boolean correctly mapped across domain entities.

### Test Case 3: Workout Header Race Badge Visual Contract (`[TST-UI-195.3]`)
* **Scope**: UI Visual Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/header/WorkoutHeaderRaceBadgeVisualContractTest.kt`
* **Preconditions**: `WorkoutHeader.kt` composable source.
* **Action**:
  1. Assert presence of `data.race` conditional check in Row A.
  2. Assert use of `Icons.Default.EmojiEvents` (or vector trophy asset) and absence of raw unicode emojis (`🏁`).
  3. Assert use of `tertiaryContainer` and `onTertiaryContainer`.
* **Expected Result**: Visual contract passes with 100% adherence to design guidelines.

### Test Case 4: Workout Filter Criteria Predicate & Serialization (`[TST-UI-195.4]`)
* **Scope**: Filter Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/workoutlist/WorkoutFilterCriteriaRaceTest.kt`
* **Preconditions**: Workouts with `race = true` and `race = false`.
* **Action**:
  1. Assert `matches()` filters correctly when `isRace = true`, `isRace = false`, and `isRace = null`.
  2. Assert JSON serialization roundtrips `isRace` without data loss.
* **Expected Result**: Filter predicate and serialization pass 100%.

### Test Case 5: Strava Uploader Race Workout Type Mapping (`[TST-UI-195.5]`)
* **Scope**: Integration Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/strava/StravaUploaderRaceWorkoutTypeTest.kt`
* **Preconditions**: Workouts with `race = true` across `BSportType.BIKE` and `BSportType.RUN`.
* **Action**:
  1. Assert Strava `workout_type` is `"11"` for Bike race.
  2. Assert Strava `workout_type` is `"1"` for Run race.
  3. Assert Strava `workout_type` is omitted or default when `race = false`.
* **Expected Result**: Strava workout_type parameters correctly formatted.

### Test Case 6: 9-Language Localization Parity Audit (`[TST-UI-195.6]`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/TranslationParityTest.kt`
* **Goal**: Verify all newly introduced string keys exist across all 9 locales:
  * EN, DE, ES, FR, IT, JA, NL, PL, PT
* **Expected Result**: 100% parity, zero missing entries, zero format specifier mismatches.

### Test Case 7: Clean-Room Full Suite Regression (`[TST-UI-195.7]`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: 100% pass rate with zero regressions across entire test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-195.1]` | Unit / DB | `WorkoutSummariesDatabaseManager.onUpgrade` & `updateWorkoutData` | `REQ-UI-236` | Specified |
| `[TST-UI-195.2]` | Unit / Mapper | `WorkoutDataMapper.fromCursor` & `WorkoutRepository.saveWorkout` | `REQ-UI-236` | Specified |
| `[TST-UI-195.3]` | Visual Contract | `WorkoutHeader` Row A Badge | `REQ-UI-236` | Specified |
| `[TST-UI-195.4]` | Unit / Filter | `WorkoutFilterCriteria.matches` & JSON serialization | `REQ-UI-236` | Specified |
| `[TST-UI-195.5]` | Unit / Sync | `StravaUploader` multipart payload building | `REQ-UI-236` | Specified |
| `[TST-UI-195.6]` | Localization | `TranslationParityTest` (9 locales) | `REQ-UI-236`, `REQ-UI-106` | Specified |
| `[TST-UI-195.7]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
