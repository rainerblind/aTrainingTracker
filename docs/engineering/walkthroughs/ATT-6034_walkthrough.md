# Stage 5 Verification Walkthrough: ATT-6034

**Ticket**: [ATT-6034](https://issue-tracker/ATT-6034) | **Parent Epic**: [ATT-6033](https://issue-tracker/ATT-6033)  
**Target Release**: `V5.0.0` (`Rule 8.3`) | **Active Sprint**: `2026-11-22_01_Früh` (Sprint 471) | **Risk Tier**: `tier-a` (`5 SP`)  
**Requirements & Tests**: `REQ-TRK-094` (`REQ-TRK-094.1`–`.4`, `[Verified]`) | `TST-TRK-094` (`TST-TRK-094.1`–`.4`, `[Verified]`)  
**Gate 3 Sub-task**: `ATT-9636` (`GATE_PASSED`) | **Branch**: `feature/ATT-6034` | **Date**: 2026-11-22

---

## 1. Summary of Changes Implemented
1. **Teilpaket A — Shutdown Deduplication & Type-Safe Lap Extraction (`REQ-TRK-094.1`, `REQ-TRK-094.3`)**:
   - Created `LapTelemetryExtractor.kt` (`com.atrainingtracker.banalservice.sensor`) with finite-guarded `Number` extraction (`value.toDouble().isFinite()` checked before `.toInt()`), `includeDisplayStrings` toggle, `"??:??:??"` / `"0"` defaults, and safe `lapSpeedMps`. Replaced all 3 duplicated sites (`TrackerService.BanalFinalizationSnapshot.capture()`, `TrackerService.createNewLap()`, `BANALService.newLap()`).
   - Deduplicated `TrackerService.handleActionStop()` and `onDestroy()` via `stopTrackingControllersAndTransientState(boolean fromOnDestroy)` and `enqueueAsyncFinalization(boolean fromOnDestroy, @Nullable Integer stoppingGeneration)`, preserving `ATT-6178` non-blocking `onDestroy()`, `ATT-9154` `< 1 s` stop, `mSessionGeneration`-gated teardown, and strict `!fromOnDestroy` vs `fromOnDestroy` radar/watchdog/executor boundaries (`TrackerServiceFinalizationTest.kt:1035`).
   - Extracted `private static` `resetPauseLocks(@Nullable Context)` and `resetActiveTrackingState(@Nullable Context, boolean)` in `TrainingApplication.java` across `stopTracking()`, `resetTrackingStateOnAbort()`, and `onTrackingAborted()`, preserving `stopTracking()`'s pre-/post-dispatch ordering and leaving `trackingStopped()` untouched.
2. **Teilpaket B — `PostWorkoutFinalizer` Extraction (`REQ-TRK-094.2`)**:
   - Created stateless Kotlin `object PostWorkoutFinalizer` (`com.atrainingtracker.trainingtracker.tracker`) encapsulating all 7 independently fault-isolated `finalizeLiveSession` stages and `guessCommuteAndTrainer(repository, workoutId)` with lazy `knownLocationsRepositoryProvider` (`this::getKnownLocationsRepository`), `activeSessionDeviceIdsProvider` (backed by thread-safe `ConcurrentHashMap.newKeySet()`), and `fallbackSportTypeIdProvider` (`this::getSportTypeId`) evaluated strictly inside Stage 5 / Stage 6 `try/catch` blocks, zero `*DatabaseManager`/`Cursor` handles, and thin protected delegates on `TrackerService` preserving all existing signatures and `mFirstJubileeCelebrationData`.
3. **Teilpaket C — Parameterized Strava Sync Preferences & Shared Scheduler (`REQ-TRK-094.4`)**:
   - Replaced the 18 copy-pasted static Strava preference methods in `TrainingApplication.java` with `private enum StravaSyncPref` (`EQUIPMENT`, `ROUTES`, `SEGMENTS`) + 6 helpers retaining all 18 `public static` entry points, distinct `"never"` string resources, and null `cSharedPreferences`/`cAppContext` guards.
   - Created `StravaSyncScheduler.kt` (`com.atrainingtracker.trainingtracker.onlinecommunities.strava`) deduplicating `schedule(context)` across `StravaEquipmentSyncWorker`, `StravaRoutesSyncWorker`, and `StravaSegmentsSyncWorker` while preserving per-worker `TAG`, `WORK_NAME`, `entityLabel`, WorkManager guard, cancellation, and backoff constraints.
4. **Monolith LoC Ratchets & SWE.2/SWE.3 Parity**:
   - `TrackerService.java`: **`3,555 -> 3,180` LoC** (`-375` LoC; `ArchitectureConformanceContractTest` baseline tightened `3,559 -> 3,220`).
   - `TrainingApplication.java`: **`2,135 -> 2,038` LoC** (`-97` LoC; `ArchitectureConformanceContractTest` baseline tightened `2,135 -> 2,050`).
   - `BANALService.java`: **`1,098 -> 1,085` LoC** (`-13` LoC).
   - Updated `docs/architecture.md` (`v1.0.23`) and `docs/architecture/detailed_design/tracker_service.md` (`v1.0.10`, `3,180 LOC`).

---

## 2. Verification Evidence (`ATT-9544` Machine Bundle)
* **Evidence Bundle**: [`docs/engineering/test_evidence/tickets/ATT-6034/manifest.json`](../test_evidence/tickets/ATT-6034/manifest.json) | [`evidence_summary.json`](../test_evidence/tickets/ATT-6034/evidence_summary.json)
* **Unit & Contract Test Suites (`34` suites, `224/224` PASS, `0` failures, `0` errors, `0` skipped, `0` new compiler warnings)**:
  * **New Suites**: `LapTelemetryExtractorTest` (`5/5`), `PostWorkoutFinalizerTest` (`6/6`), `StravaSyncSchedulerTest` (`4/4`).
  * **Core Tracking & Application**: `TrainingApplicationTrackingTest` (`20/20`, incl. 5 new characterization tests), all 18 `TrackerService*Test` suites (`TrackerServiceFinalizationTest` `34/34`, `TrackerServiceResumeTest` `18/18`, `TrackerServiceStateMachineCharacterisationTest` `8/8`, `TrackerServiceCrashRecoveryCharacterisationTest` `7/7`, `TrackerServiceLapTest` `7/7`, `TrackerServiceResilienceTest` `7/7`, `TrackerServiceAsyncInitTest` `6/6`, `TrackerServiceRrFlushTest` `5/5`, `TrackerServiceFGSTypeTest` `4/4`, `TrackerServicePragmaSchemaTest` `4/4`, `TrackerServiceWorkoutStartTest` `4/4`, `TrackerServiceDeviceNameQuotingTest` `3/3`, `TrackerServiceDecoupledSummaryTest` `2/2`, `TrackerServiceErtsFinalizationTest` `2/2`, `TrackerServiceHeapWatchdogTest` `2/2`, `TrackerServiceExportGatingTest` `1/1`, `TrackerServiceMultiSensorSummaryTest` `1/1`, `TrackerServiceRrOpticalIsolationTest` `1/1`).
  * **BANALService & Strava Workers**: `BANALServiceArbitrationCharacterisationTest` (`5/5`), `BANALServiceLifecycleTest` (`3/3`), `BANALServiceMultisportTransitionTest` (`4/4`), `BANALServiceSportInferenceTest` (`2/2`), `StravaEquipmentSyncWorkerTest` (`11/11`), `StravaRoutesSyncWorkerTest` (`11/11`), `StravaSegmentsSyncWorkerTest` (`11/11`).
  * **Contract Suites**: `RepositoryServiceCleanlinessContractTest` (`4/4`), `ArchitectureConformanceContractTest` (`11/11`), `DetailedDesignParityContractTest` (`3/3`), `InterfaceSignatureDriftContractTest` (`3/3`), `JavaCoreMigrationStrategyContractTest` (`5/5`).
* **Emulator Smoke Check (`emulator-5554`, `Pixel_8_Android_15`, `Rule 7.6` / `Rule 8.5`)**:
  * `:app:installDebug` -> `MainActivityWithNavigation` cold start (`Status: ok`), `adb logcat -b crash -d` empty (`0` crashes), screenshot visually inspected via `read_file`: [`att6034_emulator_smoke.png`](../test_evidence/tickets/ATT-6034/screenshots/att6034_emulator_smoke.png).
* **Governance & Localization**: `python3 tools/verify_requirement_governance.py --sync-indexes && --base-ref sprint/2026-11-22_01_Früh` -> **PASS (exit `0`)**; `Localization Parity: N/A - no UI strings`.
