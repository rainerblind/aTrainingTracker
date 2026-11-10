# Stage 3 Implementation Plan: ATT-3052 - Android Health Connect Sync

* **Ticket**: [ATT-3052](https://rainerblind.atlassian.net/browse/ATT-3052)
* **Sub-task**: [ATT-6437](https://rainerblind.atlassian.net/browse/ATT-6437) (`[Impl-Plan]`)
* **Parent Epic**: [ATT-162](https://rainerblind.atlassian.net/browse/ATT-162) (*Cloud integration*)
* **Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-3052`
* **Requirements**: `REQ-SET-100`..`103` | **Test Mapping**: `TST-SET-100`..`103`
* **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-10

---

## 1. Traceability & Requirements Mapping
* `REQ-SET-100` $\to$ `TST-SET-100`: Health Connect client integration, SDK availability lifecycle, and write permissions contract.
* `REQ-SET-101` $\to$ `TST-SET-101`: `BSportType` to `ExerciseSessionRecord` mapping and time-series extraction (`HR`, `Power`, `Speed`, `Route`, `Calories`).
* `REQ-SET-102` $\to$ `TST-SET-102`: Trackless indoor workout parity (route decoupling with full non-spatial telemetry retention).
* `REQ-SET-103` $\to$ `TST-SET-103`: `HealthConnectSettingsDialog`, idempotent clientRecordId deduplication, manual re-sync, and 9-locale parity.

---

## 2. System Invariants & Preserved Behavior
1. **Core Loop Independence**: Live tracking and SQLite writes operate completely unblocked; Health Connect sync runs on `Dispatchers.IO`.
2. **Trackless Safety**: Indoor workouts without GPS never throw NPE or `IllegalArgumentException`; `exerciseRoute` is cleanly omitted (`null`).
3. **Idempotent Writes**: All records specify deterministic `clientRecordId = "workout_${fileBaseName}*"` preventing duplicate session insertion.
4. **Disjoint Shard Hygiene**: All changes confined to `settings_profiles` domain; zero modifications to files touched by concurrent lanes (`ATT-432`, `ATT-938`).

---

## 3. Proposed Architectural Changes
* **Core Engine**: `HealthConnectManager.kt` (`cloud.healthconnect`) manages SDK status, permission verification, and batch record dispatch.
* **Mapping Layer**: `HealthConnectRecordMapper.kt` transforms `WorkoutData` and `WorkoutSamplesDbHelper` cursor rows into typed Health Connect records.
* **UI & Settings**: `HealthConnectSettingsDialog.kt` provides M3 switch and permission trigger; `WorkoutHeader.kt` / `EditWorkoutScreen.kt` provide manual sync.
* **UI Consistency**: Reference: `StravaSettingsDialog` / `ExportSettingsDialog`; Reused components: `AppBottomSheetContent`, `AppDialogActions`; Theme tokens: `MaterialTheme.colorScheme.primary`, `TTColor`, 16.dp sheet padding; No one-off styles.

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Dependencies & Manifest Integration (`REQ-SET-100`)
* Update `app/build.gradle`: add `androidx.health.connect:connect-client:1.1.0`.
* Update `AndroidManifest.xml`: declare Health Connect write permissions, rationale intent filter, and `<queries>` provider package.

### Step 2: 9-Language Localization Parity (`REQ-SET-103`)
* Add `app/src/main/res/values*/strings_health_connect.xml` across all 9 locales (`values`, `-de`, `-es`, `-fr`, `-it`, `-ja`, `-nl`, `-pl`, `-pt`).

### Step 3: Record Mapper Construction (`REQ-SET-101`, `REQ-SET-102`)
* Create `HealthConnectRecordMapper.kt` in `com.atrainingtracker.trainingtracker.cloud.healthconnect`.
* Implement `mapExerciseType(...)`, `buildExerciseSessionRecord(...)`, and `extractSeriesRecords(...)` reading `WorkoutSamplesDatabaseManager`.

### Step 4: Health Connect Manager Architecture (`REQ-SET-100`, `REQ-SET-103`)
* Create `HealthConnectManager.kt` handling SDK status, permission contracts, and `syncWorkout(...)`. Wire auto-sync into `WorkoutRepository.kt`.

### Step 5: Settings UI & Manual Sync Integration (`REQ-SET-103`)
* Create `HealthConnectSettingsDialog.kt`; register `SettingsBottomSheetType.HEALTH_CONNECT` in `NavRoutes.kt` and `ATrainingTrackerApp.kt`.
* Wire manual sync action into `WorkoutHeader.kt` dropdown and `EditWorkoutScreen.kt` checkbox.

### Step 6: Verification & Targeted Unit Tests (`TST-SET-100`..`103`)
* Create `HealthConnectAvailabilityTest.kt`, `HealthConnectRecordMapperTest.kt`, and `HealthConnectSyncTest.kt`.
* Run: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.cloud.healthconnect.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`.
