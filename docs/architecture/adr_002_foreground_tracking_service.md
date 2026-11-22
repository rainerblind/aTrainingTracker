# ADR-002: Foreground Tracking Service Architecture (`TrackerService`)

- **Document ID**: `DOC-ARC-ADR002`
- **Version**: 1.0.0
- **Status**: Approved
- **Approver**: Rainer Blind (PO)
- **Deciders**: Architecture Board, Product Owner (Pending Sign-Off)
- **Date**: 2026-11-17
- **Target Release**: `5.1.0` (Active Sprint: `2026-11-17_01_Früh`)
- **Parent Epic**: [ATT-8138](https://issue-tracker/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)
- **Ticket**: [ATT-8161](https://issue-tracker/ATT-8161)
- **Requirement Mapping**: `REQ-PRO-116` (`REQ-PRO-116.1`, `REQ-PRO-116.2`), `REQ-NFR-003`, `REQ-NFR-009`, `REQ-TRK-003`, `REQ-TRK-015`
- **Test Mapping**: `TST-PRO-116` (`TST-PRO-116.1`, `TST-PRO-116.2`), `TrackerServiceStateMachineCharacterisationTest`, `TrackerServiceCrashRecoveryCharacterisationTest`

---

## 1. Context & Problem Statement

`aTrainingTracker` records multi-hour outdoor and indoor athletic sessions (cycling, running, unicycling) at a deterministic **1-Hz sampling cadence** while the smartphone screen is locked or placed in a pocket/bar mount. The tracking runtime (`TrackerService.java`) must maintain live `TrackingMode` state (`IDLE`, `READY`, `TRACKING`, `PAUSED`), coordinate `AutoPauseManager`, ingest `BANALService` sensor streams, flush 1-Hz telemetry and high-frequency RR batches (`mLiveRrQueue`), and recover seamlessly if Android OS kills and recreates the process.

## 2. Decision Drivers & Evaluated Alternatives

Key selection criteria: (1) deterministic `1000 ms` sampling cadence (`< 50 ms` tick budget, `REQ-NFR-003`), (2) Android 14+ (API 34+) `foregroundServiceType` compliance without background execution restrictions, (3) crash recovery across process death, and (4) bounded battery impact (`< 5%/h`, `REQ-NFR-006`).

### Alternative 1: `WorkManager` / `JobScheduler` Periodic Workers
- **Description**: Schedule workout recording and telemetry polling via deferred `WorkManager` workers or `JobInfo.Builder.setPeriodic()`.
- **Evaluation**: **Rejected**. Android enforces a 15-minute minimum period on `PeriodicWorkRequest`, subjects background jobs to Doze/App Standby batching, and makes no guarantee of sub-second or 1-Hz execution latency while the screen is off.

### Alternative 2: Unbound Background `Service` Without Foreground Notification or WakeLock
- **Description**: Run a plain background `Service` relying on Activity bindings and standard OS scheduling.
- **Evaluation**: **Rejected**. Since Android 8.0 (API 26), background services are killed within minutes when the UI enters the background, and CPU deep-sleep suspends `ScheduledExecutorService` ticks when the screen turns off.

### Alternative 3: Dedicated Foreground Service (`TrackerService`) with `START_STICKY`, `PARTIAL_WAKE_LOCK`, and Partitioned Executors (Selected)
- **Description**: Declare `TrackerService` in `AndroidManifest.xml` with `android:foregroundServiceType="location|health"` and `ACCESS_BACKGROUND_LOCATION` (`REQ-TRK-015`). Acquire `PowerManager.PARTIAL_WAKE_LOCK` (`aTrainingTracker:TrackingLock`) strictly during active `TRACKING` via `TrackerService#acquireWakeLock` and release on stop/pause via `TrackerService#releaseWakeLock`. Partition work across `mScheduler` (`ScheduledExecutorService(1)` for 1-Hz `sampleAndWriteToDb`), `mDbExecutor` (single-thread FIFO init/rehydration/`endWorkout`), and `mRrFlushExecutor`. Return `START_STICKY` so `rehydrateLiveSession` / `recreateValuesWhenResuming` restore active sessions after OS process death.
- **Evaluation**: **Selected**. Guarantees continuous 1-Hz telemetry across 10+ hour rides, isolates database/export I/O off the main looper and 1-Hz tick thread, and enforces API 34+ foreground service invariants.

## 3. Decision Outcome

`TrackerService` remains the authoritative foreground service orchestrator for workout lifecycles (`docs/architecture/detailed_design/tracker_service.md`), retaining `START_STICKY` recovery, bounded `PARTIAL_WAKE_LOCK`, and the three single-purpose executors (`mScheduler`, `mDbExecutor`, `mRrFlushExecutor`).

## 4. Architectural Consequences & Trade-offs

### Positive Consequences
- **Deterministic 1-Hz Sampling**: `mScheduler` maintains steady 1-Hz time-series capture (`WorkoutSamplesDatabaseManager`) with screen off.
- **Crash Resilience**: `START_STICKY` + persisted session state (`LiveWorkoutSession`) recover interrupted sessions without user data loss.
- **API 34+ Compliance**: Explicit `location|health` FGS types and `ACCESS_BACKGROUND_LOCATION` prevent `SecurityException` on OS restart.

### Negative / Mitigated Consequences
- **Persistent Notification Requirement**: Requires an always-visible foreground status bar notification during tracking (`REQ-UI-452`, monochrome `ic_stat_notify_logo.xml`).
- **WakeLock Battery Risk**: Mitigated by strictly scoping `acquireWakeLock()` / `releaseWakeLock()` to active `TRACKING` state (`ArchitectureConformanceContractTest`).

---

## Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-17 | AI Agent (`ATT-8161`, `ATT-8164`) | `ATT-8161`, `ATT-8164` | Retrospective ADR baseline with standardized `DOC-ARC-ADR002` control header. |
