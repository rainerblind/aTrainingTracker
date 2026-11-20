# Stage 5 Verification & Walkthrough (Bounce 1): ATT-9154 - Decouple Stop-Workout Post-Processing from Edit Workout Navigation with Background Progress Notification

**Ticket**: [ATT-9154](https://rainerblind.atlassian.net/browse/ATT-9154) | **Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-20_02_Spät` | **Branch**: `feature/ATT-9154`  
**Verified Production SHA**: `7725517b` (Gate 4 Bounce 1 `ATT-9361` PASS; `Rule 7.5` doc-only Stage 5 Bounce 1 with `0` `app/` drift: `git diff 7725517b..HEAD -- app/` is empty, `0` redundant Gradle runs, `0` device touches)  
**Requirements / Test Spec**: `REQ-TRK-091` (`REQ-TRK-091.1`..`.4`, refining `SYSREQ-TRK-005` & `SYSREQ-DEX-007`) / `TST-TRK-091` (`TST-TRK-091.1`..`.4`) — `[Verified]`  
**Date**: 2026-11-20 | **Author**: AI Agent 1 (`--as agent1`)

---

## 1. Root Cause, Verified Architecture & Stage 6 Bounce 1 Heals
1. **Non-Blocking Stop & Armed-Token Isolation (`REQ-TRK-091.1`)**:
   - `TrackerService.endWorkout()` commits `FINISHED = 1`, persists devices/RR/sensors/metadata, runs `finalizeLiveSession()`, stops `PolarRawTelemetryRecorder.stopActiveSession()`, captures armed flags/start timestamps/monotonic armed tokens synchronously, enqueues `WorkoutPostProcessingCoordinator`, and broadcasts `TRACKING_FINISHED_INTENT` immediately (`< 1 s`).
   - `WorkoutPostProcessingCoordinator.kt` (`485` LoC) drains a serialized `SupervisorJob() + Dispatchers.IO` queue (`H10(showOwnNotification=false)` -> `Verity(showOwnNotification=false)` -> `HRV` -> `ERTS` -> `refreshWorkout` -> `ExportManager.workoutFinished` -> deferred `exportWorkout`) with per-stage fault isolation and idempotent `finally` completion.
   - **Bounce 1 Concurrency & Progress Hardening (`7725517b`)**: Added dedicated `hostLock` with strict lock ordering **`hostLock -> stateLock`** (never `stateLock -> hostLock`, never held across blocking I/O stages) across `enqueue()` + `startForegroundHost()` and `drainQueue()` `finally` + `onBatchCompleted()`/`stopForegroundHost()`, preventing back-to-back TOCTOU FGS/notification clobber; propagated `initialHadWarning = batchHadWarning` on `shouldRestartDrain == true`; exposed `@Volatile currentProgress: StageProgress?` (`stage`, `currentStep`, `totalSteps`) so `onStartCommand()` renders active progress instead of resetting to `0/1`.
   - `PolarH10OfflineController.kt` (`788` LoC) and `PolarVeritySenseOfflineController.kt` (`1,483 / 1,500` LoC) accept `showOwnNotification = false` and `expectedArmedToken` on `suspend fun stopAndBackfillWorkout(...)` (preserving 4-arg `*Blocking` signatures for `IF-SWE-04`).
2. **Atomic Export Deferral, `syncToHealthConnect` Preservation & Edit-Safe Refresh (`REQ-TRK-091.2`)**:
   - **Bounce 1 Health Connect Toggle Preservation (`7725517b`)**: Changed `deferredExportWorkouts` to `MutableMap<Long, Boolean?>`, wired `WorkoutRepository.saveWorkout()` -> `deferExportIfPostProcessing(workoutId, userEditedWorkout.syncToHealthConnect)`, returned `DeferredExportDecision(pending, syncToHealthConnect)` from `completePostProcessing(workoutId)` (`containsKey` before `remove`), and applied `freshWorkout.copy(syncToHealthConnect = decision.syncToHealthConnect)` before `triggerDeferredExport(...)` so transient Health Connect ON/OFF overrides survive SQLite reload.
   - `WorkoutRepository.loadFreshWorkoutForExport(workoutId)` reads `summariesManager.getWorkoutCursor(workoutId).use { ... }` directly on `Dispatchers.IO` strictly after `ExportManager.workoutFinished()`; `EditWorkoutViewModel.kt` (`608` LoC) merges telemetry-only fields on reactive `allWorkouts` updates while preserving explicit `createNewCluster()` `clusterId` reloads.
3. **`shortService` FGS, 1-Arg/2-Arg `onTimeout`, Notification `8047`/`8048` & 9-Locale Parity (`REQ-TRK-091.3`)**:
   - **Bounce 1 Android 14 & 15/16+ `shortService` Timeout Fix (`7725517b`)**: `WorkoutPostProcessingService.kt` (`197` LoC, `shortService`, `exported="false"`, `0` new permissions, `0` `connectedDevice`) overrides **both** 1-arg `onTimeout(startId: Int)` AND 2-arg `onTimeout(startId: Int, fgsType: Int)` via shared `handleTimeout(startId, fgsType)`, cleanly calling `demoteForeground()` + `performStopSelf()` without cancelling `WorkoutPostProcessingCoordinator`, and renders active `currentProgress` in `onStartCommand()`.
   - `WorkoutPostProcessingNotificationManager.kt` (`157` LoC) posts 9-locale progress (`0` emojis, escaped French apostrophes) on ID `8047` with API 33+ permission gating, cancels `8047` on completion, and cancels `8047` + posts an auto-cancel warning on dedicated ID `8048` on failure.
4. **Protected Monoliths & Architecture Parity (`REQ-TRK-091.4`)**:
   - `TrackerService.java = 3,511 / 3,559` (`-28` LoC, `0` Bounce 1 edits); `WorkoutRepository.kt = 4,953 / 5,117`; `PolarVeritySenseOfflineController.kt = 1,483 / 1,500`; `0` edits to `DeviceManager.java` (`1,707/1,708`) and `DevicesDatabaseManager.java` (`1,669/1,671`).

---

## 2. Verification Evidence (`Rule 7.5` Reused Gate 4 Bounce 1 SHA `7725517b` — `0` `app/` Drift)
- **Build & Compiler Hygiene**: `:app:compileDebugKotlin`, `:app:compileDebugUnitTestKotlin`, `:app:assembleDebug` -> **PASS** (`0` new Kotlin compiler warnings across all `ATT-9154` production & unit test files).
- **Tier 1 Targeted & Contract Suites (`113/113 PASS` Bounce 1 16-suite @ `7725517b` layered on `208/208` cumulative 23-suite baseline; `0` failed, `0` errors, `0` skipped)**:

| Test Scope / Suite Group | Result | Verified SHA | Requirement / Scope |
| :--- | :---: | :---: | :--- |
| `WorkoutPostProcessing{Coordinator,NotificationManager,Service}Test` (`7`+`3`+`5`) | `15/15 PASS` | `7725517b` | `REQ-TRK-091.1`–`.3` (`+5` Bounce 1: 1-arg `onTimeout`, `hostLock`, `initialHadWarning`, `currentProgress`, `syncToHealthConnect`) |
| `TrackerServiceFinalizationTest` (`30`) & `MergedManifestFgsAuditTest` (`6`) | `36/36 PASS` | `7725517b` | `REQ-TRK-091.1`/`.3` non-blocking stop, `shortService`, `0` `connectedDevice` |
| `EditWorkout{ViewModel,DataPreservation,NewClusterSync,Clustering}Test` (`14`+`2`+`2`+`6`) | `24/24 PASS` | `7725517b` | `REQ-TRK-091.2` edit-safe refresh & `createNewCluster` `clusterId` |
| `WorkoutRepository{DeferredExport,SaveClustering}Test` (`4`+`5`) | `9/9 PASS` | `7725517b` | `REQ-TRK-091.2` (`+1` Bounce 1 `syncToHealthConnect` true/false end-to-end) |
| 5 Contract suites (`ArchitectureConformance`, `DeadCodeElimination`, `DetailedDesignParity`, `InterfaceSignatureDrift`, `SourceTextReadingAudit`) | `29/29 PASS` | `7725517b` | `REQ-TRK-091.4` LoC caps, `IF-SWE-04`, `0` dead code, `0` `src/main` reads |
| **Bounce 1 Re-Verified Subtotal (16 suites)** | **`113/113 PASS`** | **`7725517b`** | **All Bounce 1 impacted & contract suites green** |
| Unchanged sibling suites (`TrackerServiceStateMachineCharacterisationTest` `8` @ `9ca92f78`; `TranslationParityTest` `11`, `PolarH10OfflineControllerTest` `15`, `PolarVeritySenseOfflineControllerTest` `30`, `SystemLiveTrackingRequirementsContractTest` `4`, `SystemDataExchangeRequirementsContractTest` `4`, `WorkoutSummaryRepositoryContractTest` `23` @ `7d3b0908`) | `95/95 PASS` (in `208/208` cumulative 23-suite baseline) | `9ca92f78` / `7d3b0908` | 9-locale parity (`0` emojis), Polar armed-token isolation, state machine & `SYSREQ` contracts (`0` edits in `7725517b`) |

### Verbatim `TST-TRK-091` Method Citations (`25/25` Verified)
- `WorkoutPostProcessingCoordinatorTest` (`7`): `testEnqueue_executesStrictStageOrderWithoutBlockingCaller_tstTrk091_1`, `testEarlySaveDuringPostProcessing_defersExportAndExportsFreshRowAfterWorkoutFinished_tstTrk091_2`, `testFaultIsolation_stageExceptionContinuesPipelineClearsActiveStateAndPostsWarning_tstTrk091_1`, `testBackToBackEnqueue_serializesSingleWorkerAndNeverClobbersNewWork_tstTrk091_1`, `testDeferredExport_preservesExplicitSyncToHealthConnectTrueAndFalseOverrides_tstTrk091_2`, `testEnqueueDuringIdleFinalization_hostLockPreventsFgsOrNotificationClobber_tstTrk091_1`, `testShouldRestartDrain_preservesBatchHadWarningFromPrecedingJob_tstTrk091_1`
- `WorkoutPostProcessingNotificationManagerTest` (`3`): `testShowStageProgress_api33PermissionGranted_postsProgressNotification_tstTrk091_3`, `testShowStageProgress_api33PermissionDenied_suppressesNotifyAndWarning_tstTrk091_3`, `testShowWarningAndCancelNotification_usesDedicatedWarningId8048AndCancels8047_tstTrk091_3`
- `WorkoutPostProcessingServiceTest` (`5`): `testDetermineForegroundServiceType_api34ReturnsShortService_api33ReturnsZero_tstTrk091_3`, `testOnTimeout_stopsForegroundCleanlyWithoutCancellingActiveCoordinatorWork_tstTrk091_3`, `testOnTimeoutSingleArg_api34ShortServiceStopsForegroundWithoutCancellingCoordinatorWork_tstTrk091_3`, `testOnStartCommand_rendersActiveCurrentProgressInsteadOfResettingToZeroOfOne_tstTrk091_3`, `testStartForPostProcessing_fgsStartDisallowed_fallsBackCleanlyWithoutThrowing_tstTrk091_3`
- `TrackerServiceFinalizationTest` (`1`): `testEndWorkout_decouplesPostProcessingAndBroadcastsTrackingFinishedImmediately_tstTrk091_1`
- `PolarH10OfflineControllerTest` (`1`): `testStopAndBackfillWorkout_staleArmedTokenPreservesNewerWorkoutArmedStateAndSuppressesNotification_tstTrk091_1`
- `PolarVeritySenseOfflineControllerTest` (`1`): `testStopAndBackfillWorkout_staleArmedTokenPreservesNewerWorkoutArmedStateAndSuppressesNotification_tstTrk091_1`
- `MergedManifestFgsAuditTest` (`1`): `testManifest_postProcessingService_declaresShortServiceAndExportedFalse_tstTrk091_3`
- `WorkoutRepositoryDeferredExportTest` (`4`): `testSaveWorkout_whenPostProcessingActive_persistsMetadataAndRpeFeelWhileDeferringExport_tstTrk091_2`, `testSaveWorkout_whenPostProcessingInactive_triggersExportImmediately_tstTrk091_2`, `testLoadFreshWorkoutForExport_readsDirectSqliteCursorAndClosesCleanly_tstTrk091_2`, `testSaveWorkout_deferredExportPreservesSyncToHealthConnectTrueAndFalseOverrides_tstTrk091_2`
- `EditWorkoutViewModelTest` (`2`): `testReactiveTelemetryRefresh_preservesAllUserEditsAndSkipsSuggestionRewrites_tstTrk091_2`, `testCreateNewClusterInitialReload_adoptsClusterIdWhileAllWorkoutsRefreshPreservesIt_tstTrk091_2`

---

## 3. Static Analysis & Governance Parity
- **4/4 Python Safety Linters**: `check_secrets_hygiene.py`, `lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py` -> **PASS**.
- **Governance & Verified Evidence**: `verify_requirement_governance.py --repo-root . --sync-indexes`, `--base-ref refs/heads/sprint/2026-11-20_02_Spät`, and `--check-verified-evidence` (incl. clean-checkout empty `--test-results-dir`) -> **EXIT `0`** (`docs/tests/tst_tracking_cockpit.md = 3,965 / 4,000`).
- **Branch Preservation (`REQ-PRO-125`)**: `feature/ATT-9154` kept intact and unmerged for Stage 6 Cold Final Review re-audit.
