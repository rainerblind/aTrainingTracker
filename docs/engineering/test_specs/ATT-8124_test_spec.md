# Stage 2: Requirement & Test Specification - ATT-8124 (Rev. 2 — Gate 3 Revision)

**Ticket**: [ATT-8124](https://issue-tracker/ATT-8124) | **Parent Epic**: [ATT-8119](https://issue-tracker/ATT-8119) (*Sequenced before `ATT-6034`*)  
**Target Release**: `V5.0.0` (`Rule 8.3`) | **Active Sprint**: `2026-11-22_01_Früh` (Sprint 471) | **Risk Tier**: `tier-a` (`3 SP`)  
**Requirement ID**: `REQ-TRK-093` (`REQ-TRK-093.1`–`.4`, reserved atomically) | **Test Spec ID**: `TST-TRK-093` (`TST-TRK-093.1`–`.4`)  
**Living Docs Shards**: `docs/requirements/req_tracking_cockpit_part3.md` (`v1.0.1`), `docs/tests/tst_tracking_cockpit_part2.md` (`v1.0.3`)  
**Branch**: `feature/ATT-8124` | **Author**: Lane 1 Author (`--as agent1`) | **Date**: 2026-11-22

---

## 1. Formal Requirement Specification (`REQ-TRK-093`)

### 1.1 Attributes (`REQ-PRO-077`, `REQ-PRO-131.4`)
- **System Requirement(s)**: `SYSREQ-TRK-004`, `SYSREQ-TRK-005` (zero `SYS.2`/`SYS.3` changes required)
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit
- **Implementation**: `PolarH10OfflineController.kt`, `PostWorkoutPhysiologyRecomputer.kt`, `WorkoutPostProcessingCoordinator.kt`, `TrackerService.java`, `MyBTLEDevice.java`

### 1.2 Normative Clauses (`REQ-TRK-093.1`–`.4`)
1. **Exact `startRecording` Time Anchor & Lifecycle (`REQ-TRK-093.1`)**:
   - `PolarH10OfflineController.executeArming()` SHALL capture `val armedAtMs = clockMsProvider()` **strictly after** `api.startRecording(...)` completes and store `recordingStartTimeMs = armedAtMs` under `armedStateLock`.
   - `recordingStartTimeMs` SHALL be cleared to `0L` in `clearArmedStateIfTokenMatches()` (when token matches), `resetForTesting()`, and `setSessionStartTimeMs()` when `!isArmed.get()`.
   - `getEffectiveAnchorStartTimeMs()` SHALL return `recordingStartTimeMs` when `> 0L`, else `sessionStartTimeMs`. Both `stopAndBackfillWorkout()` and `stopAndBackfillWorkoutBlocking()` SHALL default `startTimeMs = getEffectiveAnchorStartTimeMs()`, and `recordPendingSync()` SHALL persist `startMs = if (startTimeMs > 0L) startTimeMs else getEffectiveAnchorStartTimeMs()`.
2. **Workout-End Trailing RR Beat Trimming & Clock-Inversion Guard (`REQ-TRK-093.2`)**:
   - `TrackerService.endWorkout()` SHALL capture `workoutEndMs` into `PostProcessingRequest` (`val workoutEndMs: Long = 0L`, `@JvmOverloads`), forwarded to `stopAndBackfillWorkout(..., endTimeMs)` and `recordPendingSync(..., endTimeMs)` (persisting `if (endTimeMs > 0L) endTimeMs else clockMsProvider()`).
   - Shared helper `buildAnchoredSamples(workoutId, hrSamples, anchorMs, endTimeMs)` (used by `stopAndBackfillWorkout()` and `syncPendingWorkout()` with `pendingData.expectedEndMs`) SHALL guard `if (rr <= 0) continue` before `cumulativeMs += rr` & division, and handle all 3 `endTimeMs` cases: (a) `<= 0L` $\rightarrow$ no upper-bound trim; (b) `endTimeMs > 0L && endTimeMs < anchorMs` (clock backward shift / short-workout arming race) $\rightarrow$ log warning and do NOT trim all beats; (c) `endTimeMs >= anchorMs` $\rightarrow$ `break` once `beatTs > endTimeMs`. Both callers SHALL guard `intervals.isNotEmpty()` before DB patching (while still removing the strap slot entry).
3. **Single-Pass Post-Backfill Physiology Recomputation & Consistent `h10Armed` Snapshot (`REQ-TRK-093.3`)**:
   - `PostWorkoutPhysiologyRecomputer.recomputeAfterHrBackfill(context, workoutId, baseFileName)` SHALL filter nullable `hrSamples` (`mapNotNull { it?.takeIf { v -> v.isFinite() && v > 0.0 } }`) to persist HR `MIN`/`MAX`/`AVG` with valid sample count (`validHrs.size`), resolve `BSportType` via `WorkoutRepository.getWorkoutSummaryUpdateData(baseFileName)?.bSportType ?: BSportType.UNKNOWN` (zero `*DatabaseManager`/`Cursor` leaks), and persist `EFFICIENCY_FACTOR`, `AEROBIC_DECOUPLING`, and `CardioPowerDynamicsRecord`.
   - `TrackerService.endWorkout()` SHALL capture `h10Armed` **once** prior to `finalizeLiveSession()` and reuse that identical boolean both to defer pre-backfill EF/Decoupling/Dynamics inside `finalizeLiveSession()` (preserving all existing 1-/2-/4-arg method signatures) and to build `PostProcessingRequest` (eliminating TOCTOU drift between deferral and enqueue).
   - `WorkoutPostProcessingCoordinator.processRequest()` SHALL invoke `stopAndBackfillWorkout(..., endTimeMs = request.workoutEndMs, runPostBackfillRecompute = false)`, then run `runPhysiologyRecompute` (on `request.h10Armed || verityBackfilled`) and `runHrvRecompute` **strictly once** before `reloadWorkout` and export. Direct `runPostBackfillRecompute = true` calls and `syncPendingWorkout()` SHALL run both recomputers once post-patch.
4. **Synchronous Legacy GATT Release, Sensor Re-Registration & Callback Hygiene (`REQ-TRK-093.4`)**:
   - `MyBTLEDevice.pauseGattOperations()` SHALL atomically under `mGattLock` set `mIsGattPaused = true`, clear `mGattQueue`/`mGattOperationPending`, set `mState = State.DISCONNECTED`, capture & null `mBluetoothGatt` / `mCurrentGattAutoConnect` while **preserving `mIsBackgroundReconnecting`** (`REQ-CON-051.3`), then **outside `mGattLock`** cancel scan/timers, invoke `unregisterSensors()` when tearing down an active connection/registered sensors (so `readNextCharacteristic()`'s `if (!sensorsRegistered())` invokes `configureServices(gatt)` after reconnect!), and **synchronously** call `gattToClose.disconnect(); gattToClose.close();` (`BLUETOOTH_CONNECT`-guarded) before returning to `PolarOfflineGattCoordinator` on `Dispatchers.IO`.
   - `MyBTLEDevice.onConnectionStateChange()` SHALL inspect `mIsGattPaused` & `mBluetoothGatt` under `mGattLock`, then **outside `mGattLock`** close any stale `gatt != currentGatt` (`BLUETOOTH_CONNECT`-guarded) and return immediately while paused.
   - `BTLEHeartRateDeviceTest.kt` (`TST-CON-054.3`) SHALL be reconciled with GATT release on pause; existing `RESUME_SERVICE_RECREATION` (`REQ-TRK-078.1`) and `LAP_NR` (`REQ-STB-025.1`) invariants remain regression-locked.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC 1 (`TST-TRK-093.4`)**: *Given* `RESUME_SERVICE_RECREATION` and a fresh `MyAccumulatorSensor` (`zeroValue = 1`), *When* restored/constructed, *Then* zero `RESET_ACCUMULATORS_INTENT` broadcasts occur and `LAP_NR` reports `1 -> 2 -> 1`.
* **AC 2 (`TST-TRK-093.1`, `TST-TRK-093.2`)**: *Given* `executeArming()` finishes `startRecording` at `+8 s` after `sessionStartTimeMs`, *When* `stopAndBackfillWorkout` / `syncPendingWorkout` runs with trailing beats past `workoutEndMs`, `rr <= 0`, or `endTimeMs < anchorMs` clock inversion, *Then* Beat 0 uses `recordingStartTimeMs`, valid `endTimeMs >= anchorMs` trims trailing beats, `rr <= 0` is skipped, clock inversion logs a warning without dropping all beats, and empty trimmed intervals skip DB writes.
* **AC 3 (`TST-TRK-093.3`, `TST-TRK-093.4`)**: *Given* a connected `MyBTLEDevice` and an armed Polar H10 workout with live HR gaps, *When* paused/resumed and stopped, *Then* `pauseGattOperations()` synchronously closes `mBluetoothGatt` + calls `unregisterSensors()` (preserving `mIsBackgroundReconnecting` and re-running `configureServices` -> `CONNECTED_WITH_SERVICE` on reconnect), and `PostWorkoutPhysiologyRecomputer` + `HrvPostWorkoutRecomputer` each run strictly once post-backfill using a consistent `h10Armed` snapshot.

---

## 2. Test Specification (`TST-TRK-093`) & Localization Parity
* **9-Language Localization Parity**: `N/A - no UI strings`.
* **Automated Test Cases**:
  1. `TST-TRK-093.1` (`PolarH10OfflineControllerTest.kt`): verifies `recordingStartTimeMs` captured strictly after `startRecording`, cleared on disarm/reset/unarmed `setSessionStartTimeMs`, used as default anchor in `stopAndBackfillWorkout`, `stopAndBackfillWorkoutBlocking`, and `recordPendingSync`.
  2. `TST-TRK-093.2` (`PolarH10OfflineControllerTest.kt`): verifies all 3 `endTimeMs` cases (`<= 0`, `0 < endTimeMs < anchorMs` clock-inversion preservation, `endTimeMs >= anchorMs` trailing trim), `rr <= 0` guard, empty-after-trim DB skip, and `syncPendingWorkout` `expectedEndMs` trim.
  3. `TST-TRK-093.3` (`PostWorkoutPhysiologyRecomputerTest.kt`, `WorkoutPostProcessingCoordinatorTest.kt`, `TrackerServiceFinalizationTest.kt`): verifies nullable/non-finite HR filtering + count in `updateExtremaValue`, `getWorkoutSummaryUpdateData` `BSportType` resolution, consistent single `h10Armed` snapshot in `TrackerService.endWorkout()`, and strictly-once post-backfill physiology + HRV recompute before reload/export.
  4. `TST-TRK-093.4` (`MyBTLEDeviceLifecycleTest.kt`, `BTLEHeartRateDeviceTest.kt`, `MyAccumulatorSensorTest.kt`, `TrackerServiceResumeTest.kt`): verifies full `pauseGattOperations() -> sync disconnect/close -> unregisterSensors() -> paused onConnectionStateChange close outside lock -> resumeGattOperations() (preserving mIsBackgroundReconnecting) -> onServicesDiscovered -> readNextCharacteristic -> configureServices -> CONNECTED_WITH_SERVICE`, reconciles `TST-CON-054.3`, locks null-device `LAP_NR` & `RESUME_SERVICE_RECREATION`, and verifies LoC/SWE.3 parity contracts.

## 3. Traceability Matrix

| Requirement ID | Normative Contract | Test Case ID | Test File(s) |
| :--- | :--- | :--- | :--- |
| `REQ-TRK-093.1` | Post-`startRecording` anchor capture, lifecycle reset & defaults | `TST-TRK-093.1` | `PolarH10OfflineControllerTest.kt` |
| `REQ-TRK-093.2` | 3-case `endTimeMs` trimming, `rr <= 0` & empty-patch guards | `TST-TRK-093.2` | `PolarH10OfflineControllerTest.kt` |
| `REQ-TRK-093.3` | Post-backfill HR/EF/Decoupling/Dynamics + single `h10Armed` & HRV pass | `TST-TRK-093.3` | `PostWorkoutPhysiologyRecomputerTest.kt`, `WorkoutPostProcessingCoordinatorTest.kt`, `TrackerServiceFinalizationTest.kt` |
| `REQ-TRK-093.4` | Sync GATT close, `unregisterSensors`, reconnect `configureServices`, `TST-CON-054.3`, Part 1/2 lock | `TST-TRK-093.4` | `MyBTLEDeviceLifecycleTest.kt`, `BTLEHeartRateDeviceTest.kt`, `MyAccumulatorSensorTest.kt`, `TrackerServiceResumeTest.kt`, `ArchitectureConformanceContractTest`, `DetailedDesignParityContractTest` |
