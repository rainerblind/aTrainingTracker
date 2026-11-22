# Living Requirements: Tracking & Live Sensor Cockpit (Part 3 of 3)

**Document ID**: `DOC-REQ-TRK-P3`  
**Version**: 1.0.2  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  

**Domain**: `tracking_cockpit_part3`  
**Prefixes**: `CON`, `FIL`, `FUSION`, `SEN`, `TRK`  
**Scope**: Live sensor telemetry, Bluetooth/ANT+ connectivity, filtering, GPS/tracking lifecycle.  
**Total Requirements**: 1

---

### REQ-TRK-093: Polar H10 Exact Recording Time Anchor, Workout-End RR Trimming, Single-Pass Post-Backfill Physiology Recomputation & Legacy GATT Release [Verified]
- **Target Release**: `V5.0.0`
- **Tests**: `TST-TRK-093`
- **System Requirement(s)**: `SYSREQ-TRK-004`, `SYSREQ-TRK-005`
- **Priority**: High
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `PolarH10OfflineController.kt`, `PostWorkoutPhysiologyRecomputer.kt`, `WorkoutPostProcessingCoordinator.kt`, `TrackerService.java`, `MyBTLEDevice.java`
- **Rationale**: Eliminates 3–15 s BLE arming drift on Polar H10 offline RR/HR backfill, trims trailing post-workout cool-down RR beats, ensures Efficiency Factor (EF), Aerobic Decoupling, `PowerHrDynamicsCalculator`, HR summary extrema, and `HrvPostWorkoutRecomputer` each run strictly once on the gap-filled series without blocking `mDbExecutor`, and releases competing legacy `BluetoothGatt` handles during Polar PMD/PFTP transfers while locking existing `RESUME_SERVICE_RECREATION` (`REQ-TRK-078.1`) and `LAP_NR` (`REQ-STB-025.1`) invariants (`ATT-8124`).
- **Description**:
  1. *Exact `startRecording` Time Anchor & Lifecycle (`REQ-TRK-093.1`)*: `PolarH10OfflineController.executeArming()` SHALL capture `recordingStartTimeMs` via an injectable `clockMsProvider` strictly after `api.startRecording(...)` completes and store it under `armedStateLock`; `recordingStartTimeMs` SHALL be cleared to `0L` in `clearArmedStateIfTokenMatches()` (when token matches), `resetForTesting()`, and `setSessionStartTimeMs()` when unarmed. `getEffectiveAnchorStartTimeMs()` SHALL return `recordingStartTimeMs` when `> 0L`, else `sessionStartTimeMs`, serving as the default `startTimeMs` for `stopAndBackfillWorkout()`, `stopAndBackfillWorkoutBlocking()`, `TrackerService.endWorkout()`, and `recordPendingSync()`.
  2. *Workout-End RR Trimming & Clock-Inversion Guard (`REQ-TRK-093.2`)*: `TrackerService.endWorkout()` SHALL pass `workoutEndMs` into `WorkoutPostProcessingCoordinator.PostProcessingRequest` (`@JvmOverloads`, default `0L`), forwarded to `stopAndBackfillWorkout(..., endTimeMs)` and `recordPendingSync(..., endTimeMs)` (persisting `if (endTimeMs > 0L) endTimeMs else clockMsProvider()`). Shared helper `buildAnchoredSamples(workoutId, hrSamples, anchorMs, endTimeMs)` (used by `stopAndBackfillWorkout()` and `syncPendingWorkout()` with `pendingData.expectedEndMs`) SHALL skip `rr <= 0` before accumulation/division, skip trimming when `endTimeMs <= 0L`, log a warning without dropping all beats on clock inversion (`0 < endTimeMs < anchorMs`), and `break` once `anchorMs + cumulativeMs > endTimeMs` when `endTimeMs >= anchorMs`; callers SHALL guard `intervals.isNotEmpty()` before DB patching.
  3. *Single-Pass Post-Backfill Physiology & Consistent `h10Armed` Snapshot (`REQ-TRK-093.3`)*: `PostWorkoutPhysiologyRecomputer.recomputeAfterHrBackfill(context, workoutId, baseFileName)` SHALL filter nullable/non-finite/non-positive `hrSamples` to persist HR `MIN`/`MAX`/`AVG` (with valid count), resolve `BSportType` via `WorkoutRepository.getWorkoutSummaryUpdateData(baseFileName)?.bSportType ?: BSportType.UNKNOWN`, and persist `EFFICIENCY_FACTOR`, `AEROBIC_DECOUPLING`, and `CardioPowerDynamicsRecord`. `TrackerService.endWorkout()` SHALL capture `h10Armed` once before `finalizeLiveSession()` and reuse that snapshot for both deferral and `PostProcessingRequest`. `WorkoutPostProcessingCoordinator` SHALL pass `runPostBackfillRecompute = false` to `stopAndBackfillWorkout()` and execute `runPhysiologyRecompute` (on `h10Armed || verityBackfilled`) and `runHrvRecompute` strictly once before `reloadWorkout` and export. `syncPendingWorkout()` and direct `runPostBackfillRecompute = true` calls SHALL run both recomputers once post-patch.
  4. *Synchronous Legacy GATT Release, Sensor Re-Registration & Crash-Recovery/`LAP_NR` Lock (`REQ-TRK-093.4`)*: `MyBTLEDevice.pauseGattOperations()` SHALL set `mIsGattPaused = true`, `mState = State.DISCONNECTED`, and null `mBluetoothGatt` under `mGattLock` while preserving `mIsBackgroundReconnecting` (`REQ-CON-051.3`), then outside `mGattLock` cancel scan/timers, invoke `unregisterSensors()` when tearing down an active connection (ensuring `readNextCharacteristic()` invokes `configureServices(gatt)` after reconnect), and synchronously disconnect/close `gattToClose` (`BLUETOOTH_CONNECT`-guarded). `onConnectionStateChange()` SHALL close any stale `gatt != currentGatt` outside `mGattLock` and return immediately while `mIsGattPaused == true`. `RESUME_SERVICE_RECREATION` (`REQ-TRK-078.1`), `LAP_NR` (`REQ-STB-025.1`), and `TST-CON-054.3` invariants remain verified.

**Acceptance Criteria (Given-When-Then)**:
• *Given* Polar H10 arming executes 8 s after `sessionStartTimeMs` and strap recording continues past `workoutEndMs`, *When* `stopAndBackfillWorkout()` or `syncPendingWorkout()` runs, *Then* Beat 0 anchors to `recordingStartTimeMs` and beats with `beatTs > workoutEndMs` (and `rr <= 0`) are excluded.
• *Given* an armed Polar H10 workout with live HR gaps and a connected legacy `MyBTLEDevice`, *When* tracking stops, *Then* `pauseGattOperations()` closes `mBluetoothGatt` (preserving `mIsBackgroundReconnecting`), H10 backfill patches HR/RR off `mDbExecutor` before `PostWorkoutPhysiologyRecomputer` and `HrvPostWorkoutRecomputer` each run once, followed by `reloadWorkout` and export.
• *Given* `RESUME_SERVICE_RECREATION` and a fresh `MyAccumulatorSensor` (`zeroValue = 1`), *When* restored/constructed, *Then* zero `RESET_ACCUMULATORS_INTENT` broadcasts occur and `LAP_NR` starts deterministically at `1`.

**Invariants**: `TrackerService.java <= 3,559` LoC; `MyBTLEDevice.java <= 1,500` LoC; `ATT-6178` non-blocking `onDestroy()` intact; `ATT-9154` `< 1 s` stop & deferred-export lock intact; `TST-CON-051.3` passive reconnect intact; zero SQLite schema changes; `Localization Parity: N/A - no UI strings`.

---

## Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-22 | Lane 1 Author (`ATT-8124`, `--as agent1`) | `ATT-8124` | Created Part 3 shard (`req_tracking_cockpit_part3.md`, keeping `part1` `3,931/4,000` and `part2` `3,973/4,000` within 4,000-line cap) and added `REQ-TRK-093` `[Planned]` (`REQ-TRK-093.1`–`.4`, linked to `SYSREQ-TRK-004`/`005`, `N/A - no UI strings`). |
| `1.0.1` | 2026-11-22 | Lane 1 Author (`ATT-8124`, `--as agent1`) | `ATT-8124` | Gate 3 (`ATT-9608`) revision: added synchronous GATT close + `unregisterSensors()` + lock-outside callback close (`REQ-TRK-093.4`), post-`startRecording` anchor lifecycle & 3-case clock-inversion trimming (`REQ-TRK-093.1`/`.2`), and null/finite HR filtering, `getWorkoutSummaryUpdateData` `BSportType` resolution & single `h10Armed` snapshot (`REQ-TRK-093.3`). |
| `1.0.2` | 2026-11-22 | Lane 1 Author (`ATT-8124`, `--as agent1`) | `ATT-8124` | Promoted `REQ-TRK-093` to `[Verified]` (`192/192` PASS across 18 unit/contract suites including `TST-TRK-093.1`–`.4` and `TST-CON-054.3`, `TrackerService.java` `3,555/3,559` LoC, `MyBTLEDevice.java` `1,464/1,500` LoC, clean `emulator-5554` smoke). |
