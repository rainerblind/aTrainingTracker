# Stage 3 Implementation Plan: ATT-9154 - Decouple Stop-Workout Post-Processing from Edit Workout Navigation

**Ticket**: [ATT-9154](https://rainerblind.atlassian.net/browse/ATT-9154) | **Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-20_02_Spät` | **Branch**: `feature/ATT-9154`  
**Requirement**: `REQ-TRK-091` (`REQ-TRK-091.1`..`.4`, refining `SYSREQ-TRK-005`, `SYSREQ-DEX-007`) | **Test Spec**: `TST-TRK-091` (`TST-TRK-091.1`..`.4`)  
**Author**: AI Agent 1 (`--as agent1`) | **Risk Rating**: MEDIUM | **Date**: 2026-11-20

---

## 1. Architecture & Target LoC Budget (SWE.2)

| File | Current LoC | Budget / Cap | Action | Requirement |
| :--- | :---: | :---: | :--- | :--- |
| `WorkoutPostProcessingCoordinator.kt` *(new)* | `0` | `<= 260` | Serialized async pipeline, atomic `stateLock` deferral, injectable test seams | `REQ-TRK-091.1`, `.2` |
| `WorkoutPostProcessingService.kt` *(new)* | `0` | `<= 140` | Thin `shortService` FGS host, API 34+ `onTimeout()`, fallback | `REQ-TRK-091.3` |
| `WorkoutPostProcessingNotificationManager.kt` *(new)* | `0` | `<= 150` | Progress notification `ID 8047`, channel `EXPORT`, API 33+ permission gate | `REQ-TRK-091.3` |
| `TrackerService.java` | `3,539` | `~3,505 / 3,559` (**shrinks**) | Snapshot armed tokens, enqueue coordinator, immediate `TRACKING_FINISHED_INTENT` | `REQ-TRK-091.1`, `.4` |
| `WorkoutRepository.kt` | `4,931` | `<= 4,960 / 5,117` | Copy `rpe`/`feel` in `_allWorkouts`; defer export while post-processing active | `REQ-TRK-091.2`, `.4` |
| `EditWorkoutViewModel.kt` | `554` | `<= 605` | Collect `allWorkouts`, telemetry-only merge preserving all 19 user-editable fields | `REQ-TRK-091.2` |
| `PolarH10OfflineController.kt` | `757` | `<= 780` | Monotonic `armedSessionToken` + `showOwnNotification: Boolean = true` | `REQ-TRK-091.1` |
| `PolarVeritySenseOfflineController.kt` | `1,473` | `<= 1,493 / 1,500` | Ultra-tight (`<= +20` LoC) `armedSessionToken` + `showOwnNotification` | `REQ-TRK-091.1`, `.4` |
| `AndroidManifest.xml` & `res/values*/strings.xml` | existing | `0` new perms | Declare `shortService` (no `connectedDevice`); 6 keys x 9 locales (`0` emojis) | `REQ-TRK-091.3` |
| `DeviceManager.java` / `DevicesDatabaseManager.java` | `1,707` / `1,669` | **`0` edits** | Untouched protected monoliths | `REQ-TRK-091.4` |

---

## 2. Atomic Implementation Steps

### Step 0: 9-Locale Progress Notification Strings (`REQ-TRK-091.3` / `TST-TRK-091.3`)
- Add the 6 Stage 2 string keys (`notification_post_processing_title`, `_stage_polar_h10`, `_stage_polar_verity`, `_stage_recompute`, `_stage_export`, `_failed`) to `res/values{,-de,-es,-fr,-it,-ja,-nl,-pl,-pt}/strings.xml` (`0` emojis, matching format specifiers).
- **French XML Escaping**: Escape apostrophes (`l\'entraînement`, `d\'entraînement`) in `res/values-fr/strings.xml` so AAPT2 compiles cleanly.
- **Test**: `TranslationParityTest` (`100%` 9-locale parity).

### Step 1: Armed-Token Isolation & Duplicate-Notification Suppression (`REQ-TRK-091.1`, `.4` / `TST-TRK-091.1`)
- In `PolarH10OfflineController.kt` (`<= +18` LoC) and `PolarVeritySenseOfflineController.kt` (ultra-tight `<= +18` LoC, keeping file strictly `<= 1,493 / 1,500`):
  - Add `private val armedTokenGenerator = AtomicLong(0L)` and `@Volatile private var currentArmedToken: Long = 0L`, incrementing & recording `currentArmedToken = armedTokenGenerator.incrementAndGet()` when `isArmed.set(true)` succeeds; expose `@JvmStatic fun getArmedSessionToken(): Long = currentArmedToken`.
  - Add optional `@JvmOverloads` parameters `showOwnNotification: Boolean = true` and `expectedArmedToken: Long = 0L` to `stopAndBackfillWorkout(...)` / `stopAndBackfillWorkoutBlocking(...)`.
  - Gate `showSyncNotification` / `dismissSyncNotification` on `showOwnNotification && needsBackfill` (coordinator passes `false` so `8045`/`8046` never overlap `8047`, while standalone/legacy callers default `true`).
  - In `finally`, clear `isArmed` / armed metadata **strictly when** `expectedArmedToken == 0L || currentArmedToken == expectedArmedToken`, preventing an earlier workout's async backfill exit from disarming a newly started subsequent workout.
- **Tests**: Extend `PolarH10OfflineControllerTest.kt` and `PolarVeritySenseOfflineControllerTest.kt` (`// REQ-TRK-091.1 / TST-TRK-091.1`).

### Step 2: `WorkoutPostProcessingNotificationManager` & `shortService` FGS (`REQ-TRK-091.3` / `TST-TRK-091.3`)
- Create `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/WorkoutPostProcessingNotificationManager.kt`:
  - `NOTIFICATION_ID_POST_PROCESSING = 8047`, channel `TrainingApplication.NOTIFICATION_CHANNEL__EXPORT`, `CATEGORY_PROGRESS`, `PRIORITY_LOW`, `setOngoing(true)`, `setProgress(totalSteps, currentStep, false)`.
  - Build/notify stage updates (`POLAR_H10`, `POLAR_VERITY`, `RECOMPUTE`, `EXPORT`), guard `notify()` on API 33+ `POST_NOTIFICATIONS` permission (`NotificationManagerCompat`), `cancel()` cleanly on normal completion, and post auto-cancel non-ongoing warning (`notification_post_processing_failed`) on stage error. Expose `notificationFactory` test seam matching `WorkoutDeletionNotificationManager`.
- Create `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/WorkoutPostProcessingService.kt` and register in `AndroidManifest.xml` with `android:foregroundServiceType="shortService"` (`0` new manifest permissions, no `connectedDevice`):
  - Promotes via `ServiceCompat.startForeground` with `ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE` on API 34+ (`0` on API < 34); implements `onTimeout(startId, fgsType)` on API 34+ to stop foreground + `stopSelf()` cleanly without `RemoteServiceException`.
  - `startForPostProcessing(context)` wraps `ContextCompat.startForegroundService(...)` in `try/catch` for `ForegroundServiceStartNotAllowedException` / `IllegalStateException`, falling back cleanly to in-process execution + `NotificationManagerCompat`.
- **Tests**: Create `WorkoutPostProcessingNotificationManagerTest.kt` & `WorkoutPostProcessingServiceTest.kt`; extend `MergedManifestFgsAuditTest.kt` (`// REQ-TRK-091.3 / TST-TRK-091.3`).

### Step 3: `WorkoutPostProcessingCoordinator` & Atomic Export Deferral (`REQ-TRK-091.1`, `.2` / `TST-TRK-091.1`, `.2`)
- Create `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/WorkoutPostProcessingCoordinator.kt`:
  - Data class `PostProcessingRequest(workoutId, baseFileName, h10Armed, h10StartMs, h10ArmedToken, verityArmed, verityStartMs, verityArmedToken, ertsEnabled)`.
  - Single-lock (`private val stateLock = Any()`) atomic registry over `activeWorkouts: MutableSet<Long>` and `deferredExportWorkouts: MutableSet<Long>`:
    - `enqueue(context, request)`: adds `workoutId` to `activeWorkouts` under `stateLock` synchronously BEFORE returning to `TrackerService.endWorkout()`, starts `WorkoutPostProcessingService` (with fallback), and dispatches onto a serialized `SupervisorJob() + Dispatchers.IO` single-worker queue (injectable `CoroutineDispatcher` & step delegates for pure-JVM testing).
    - `deferExportIfPostProcessing(workoutId): Boolean`: under `stateLock`, if `workoutId in activeWorkouts` adds to `deferredExportWorkouts` and returns `true`; else returns `false`.
    - `completePostProcessing(workoutId): Boolean`: under `stateLock`, removes from `activeWorkouts` and returns `deferredExportWorkouts.remove(workoutId)`.
  - Strict fault-isolated execution sequence (rethrowing `CancellationException`, tracking `hadWarning`):
    1. If `h10Armed`: update notification `POLAR_H10` -> `PolarH10OfflineController.stopAndBackfillWorkout(appContext, workoutId, baseFileName, h10StartMs, showOwnNotification = false, expectedArmedToken = h10ArmedToken)`.
    2. If `verityArmed`: update notification `POLAR_VERITY` -> `PolarVeritySenseOfflineController.stopAndBackfillWorkout(appContext, workoutId, baseFileName, verityStartMs, showOwnNotification = false, expectedArmedToken = verityArmedToken)`.
    3. Update notification `RECOMPUTE` -> `HrvPostWorkoutRecomputer.recomputeWorkout(appContext, workoutId)` + if `ertsEnabled` `ErtsPostWorkoutRecomputer.recomputeWorkout(appContext, workoutId)`.
    4. Await `WorkoutRepository.getInstance(appContext).reloadWorkoutData(workoutId)` so `_allWorkouts` and `EditWorkoutViewModel` receive backfilled/smoothed telemetry before export.
    5. Update notification `EXPORT` -> strictly execute `ExportManager(appContext).workoutFinished(baseFileName)` **FIRST**.
    6. Atomically call `val shouldRunDeferredExport = completePostProcessing(workoutId)` under `stateLock`; if `true`, reload fresh `WorkoutData` from SQLite (`WorkoutRepository.loadFreshWorkoutForExport(workoutId)` or `allWorkouts.value.find` after awaited `reloadWorkoutData(workoutId)` so BOTH user-saved metadata AND backfilled/smoothed telemetry are guaranteed fresh!) and invoke `ExportManager(appContext).exportWorkout(freshWorkout)`.
    7. In `finally`: guarantee `completePostProcessing(workoutId)` cleanup if aborted; dismiss notification (or show auto-cancel warning if `hadWarning`) and stop `WorkoutPostProcessingService` when queue empties.
- **Test**: Create `WorkoutPostProcessingCoordinatorTest.kt` (`// REQ-TRK-091.1`, `.2` / `TST-TRK-091.1`, `.2`).

### Step 4: Live Host Wiring — `TrackerService`, `WorkoutRepository`, `EditWorkoutViewModel` (`REQ-TRK-091.1`, `.2` / `TST-TRK-091.1`, `.2`)
- **`TrackerService.java` (`L2467-2555`, shrinks `3,539 -> ~3,505 / 3,559`)**: Keep `FINISHED = 1`, active devices, RR flush, sensors, aux metadata, `finalizeLiveSession()`, fast `PolarRawTelemetryRecorder.stopActiveSession()`, and `PolarRawTelemetryPruner.pruneExpiredSidecarsAsync()`. Synchronously capture `PostProcessingRequest` (armed flags, start times, armed tokens, `isPostWorkoutErtsSmoothingEnabled`), call `WorkoutPostProcessingCoordinator.enqueue(appContext, request)`, and immediately broadcast `TRACKING_FINISHED_INTENT` (system + `LocalBroadcastManager`).
- **`WorkoutRepository.kt` (`saveWorkout()`, `L4549-4622`, `+12` LoC)**: Keep `summariesManager.updateWorkoutData(userEditedWorkout)` and `_allWorkouts.update` immediate (adding `rpe = userEditedWorkout.rpe`, `feel = userEditedWorkout.feel`); gate `exportManager.exportWorkout(userEditedWorkout)` with `if (!WorkoutPostProcessingCoordinator.deferExportIfPostProcessing(workoutId))`.
- **`EditWorkoutViewModel.kt` (`L105-193`, `+45` LoC)**: Track `private var userClearedRecordingErrors = false` (set in `clearRecordingErrors()`). Collect `repository.allWorkouts.mapNotNull { list -> list.find { it.id == workoutId } }.distinctUntilChanged()` and merge into `_workoutData` strictly when `_workoutData.value != null` via `fresh.copy(...)` copying ALL 19 user-editable fields from `current` (`workoutName`, `sportId`, `sportName`, `bSportType`, `stravaSportName`, `equipmentId`, `equipmentName`, `clusterId`, `clusterName`, `rpe`, `feel`, `description`, `goal`, `method`, `commute`, `trainer`, `race`, `uploadToStrava`, `syncToHealthConnect`) + preserving `recordingErrors = if (userClearedRecordingErrors) null else fresh.recordingErrors`, WITHOUT re-running `suggestedSportTypeName`/`suggestedEquipmentName`/`currentBSportType` or `updateSuggested*Names` rewrites.
- **Tests**: Update `TrackerServiceFinalizationTest.kt`, `TrackerServiceStateMachineCharacterisationTest.kt`, `TrackerServiceExportGatingTest.kt`; extend `EditWorkoutViewModelTest.kt`; add `WorkoutRepositoryDeferredExportTest.kt`.

### Step 5: SWE.2/SWE.3 & `Rule 81` System Doc Grounding + Locked Verification (`REQ-TRK-091.4` / `TST-TRK-091.4`)
- Update `docs/architecture/detailed_design/tracker_service.md` (exact `TrackerService.java` LoC & `endWorkout()` transition row for `DetailedDesignParityContractTest`), `docs/architecture/detailed_design/workout_repository.md`, and `docs/architecture.md`.
- Ground the 3 net-new `.kt` filenames (`WorkoutPostProcessingCoordinator.kt`, `WorkoutPostProcessingService.kt`, `WorkoutPostProcessingNotificationManager.kt`) in `docs/system/{sysreq_live_tracking,sysreq_data_exchange,system_software_traceability,system_architecture}.md` (`Rule 81`), promote `REQ-TRK-091` & `TST-TRK-091` to `[Implemented]`, sync indexes, and verify `verify_requirement_governance.py` exits `0`.
- Run locked Gradle verification (`parallel_sprint_lock.py`, NO `--rerun-tasks`, NO `-PstampGitHash=true`, `0` new Kotlin warnings): `:app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:testDebugUnitTest --rerun` across all new/updated unit suites + `MergedManifestFgsAuditTest`, `TranslationParityTest`, `ArchitectureConformanceContractTest`, `DetailedDesignParityContractTest`, `SystemLiveTrackingRequirementsContractTest`, `SystemDataExchangeRequirementsContractTest`, `DeadCodeEliminationContractTest`, `SourceTextReadingAuditContractTest`, and `:app:assembleDebug`.

---

## 3. UI Consistency (`docs/design_guidelines.md`) & Non-Negotiable Invariants
- **UI Consistency**: `N/A - no new Compose UI screens/controls` (system progress notification on `NOTIFICATION_CHANNEL__EXPORT` + ViewModel reactive merge only; `EditWorkoutScreen.kt` layout untouched).
- **Invariants**: `0` SQLite schema migrations; `0` new manifest permissions (`FOREGROUND_SERVICE_CONNECTED_DEVICE` strictly absent); `TrackerService.java < 3,539 / 3,559`; `WorkoutRepository.kt <= 5,117`; `PolarVeritySenseOfflineController.kt <= 1,500`; `0` edits to `DeviceManager.java` & `DevicesDatabaseManager.java`; `0` emojis in notification strings; `0` new `src/main` reads.
