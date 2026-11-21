# Stage 3 Implementation Plan: ATT-9346 — Dynamic Polar H10 `ACC` Rate Renegotiation & Running Dynamics Recovery

**Ticket**: `ATT-9346` (`2 SP`, `Bug`, `severity-major`, Risk: `MEDIUM`) | **Parent Epic**: `ATT-5910`  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-21_01_Früh` (Sprint 468) | **Branch**: `feature/ATT-9346`  
**Traces**: `REQ-SET-122` (`REQ-SET-122.1`–`.4`) <-> `TST-SET-124` (`TST-SET-124.1`–`.4`)  
**UI Consistency / Localization**: `N/A - no UI or strings*.xml changes` | **DB Migrations**: `None` (`0` schema bumps)

---

## 1. SWE.2 / SWE.3 Architectural Design & Concurrency Boundaries
* **Layer Boundaries**: All stream-slot restart & cancellation orchestration lives in `PolarPmdStreamCoordinator.kt` (`339 -> ~410` LoC), keeping `PolarOnlineStreamingBridge.kt` strictly `<= 1,500` LoC (`1,469 -> ~1,490/1,500` LoC). `PolarH10RunningDynamicsEstimator.kt` (`545 -> 546` LoC) remains pure-Kotlin stateless-of-Android DSP.
* **SWE.2 / SWE.3 Living Docs (`Rule 8.8`, `REQ-PRO-114`)**: Zero new subsystems/FGS/DB managers/repositories/prefixes and zero edits to `TrackerService.java`, `BANALService.java`, `DeviceManager.java`, or `MyBTLEDevice.java` -> `docs/architecture.md` and `docs/architecture/detailed_design/*.md` subsystem topology remain unchanged.
* **Dispatcher & Single-Flight Concurrency Model (`REQ-SET-122.2`)**:
  - `PolarPmdStreamCoordinator.SlotState` gains `renegotiating: AtomicBoolean` and `@Volatile renegotiateJob: Job?`, guarded under existing `slotLock`.
  - `renegotiateSlot(slot, isAllowedToRelaunch, onOldSlotCancelled, stopStreaming, beforeRelaunch, relaunch)` launches a `CoroutineStart.LAZY` sibling coroutine directly on `scope` (never nested under `state.job`, preventing self-cancellation when invoked via `processAccData()` inside `StreamSlot.ACC`!).
  - Inside `renegotiateJob`: (1) under `slotLock`, snapshot & clear `state.job = null`, `state.active.set(false)`, and invoke `onOldSlotCancelled()` (deterministically releasing `PolarOfflineGattCoordinator.ACC_OWNER_ONLINE` across all aliases + clearing `activeAccSampleRateHz = null` since clearing `state.job` bypasses `launchStream`'s `onFinalCleanup`); (2) cancel `oldJob` outside `slotLock` (without blocking `join()`, avoiding `Dispatchers.Unconfined` self-join deadlock in JVM tests); (3) verify `isAllowedToRelaunch()` (`!isShutDown && isConnected && !isOfflineGattYielded`), run `runCatching { stopStreaming() }` (rethrowing `CancellationException`); (4) re-verify `isAllowedToRelaunch()`, execute `beforeRelaunch()` (`runningDynamicsEstimator.reset()` ONLY — keeping `cadenceEstimatorInstance` and `mStridesSensor` intact); (5) clear `state.renegotiating`/`state.renegotiateJob` in `finally` (job-identity checked) and call `relaunch()` (`startAccCollection(api)`), which dynamically re-evaluates `isRunSportActive()` so any rapid sport flip during renegotiation converges to the latest sport state.
  - `cancelAllRenegotiations()` (called by `cancelAllStreams()` and `PolarOnlineStreamingBridge.pauseForOfflineSync()`) atomically snapshots, clears, and cancels all `renegotiateJob`s -> covers `deviceDisconnected()`, `blePowerStateChanged(false)`, `pauseForOfflineSync()` (`ATT-9175`), and `shutDown()`.
* **Exact `activeAccSampleRateHz` & `availableAccRates` Lifecycle Boundaries (`REQ-SET-122.1`)**:
  - **Set**: Inside `resolveAccSetting(api, sessionId)` right after `requestStreamSettings(ACC)` returns: cache `availableAccRates = rates`, compute `desiredRate` via coordinator helper (`computeDesiredAccRateHz(rates, isVeritySense, highRateRunningDynamicsActive)`), and store `activeAccSampleRateHz = setting.settings[SAMPLE_RATE]?.firstOrNull()`. After `startAccCollection` finishes resolving settings, if `isRunSportActive()` already flipped mid-`requestStreamSettings`, `maybeRenegotiateAccRate(isRunSportActive())` converges cleanly.
  - **Clear**: (a) `startAccCollection` `onFinalCleanup`: `activeAccSampleRateHz = null`; (b) `renegotiateSlot` `onOldSlotCancelled`: `activeAccSampleRateHz = null`; (c) `detachBridgeSessionState()` & `shutDown()`: `activeAccSampleRateHz = null; availableAccRates = emptySet()`.
  - **No-Loop & Verity Invariant**: `maybeRenegotiateAccRate()` returns immediately when `isVeritySense`, `!isConnectedFlag.get()`, `isOfflineGattYielded()`, `!streamCoordinator.isActive(ACC)`, `activeAccSampleRateHz == null`, or `desiredRate == activeAccSampleRateHz` (so firmware omitting `200 Hz` produces `0` restarts).

---

## 2. Atomic Implementation Steps (`Stage 4` — Locked Until Gate 3 `PASS`)

| Step | Target File(s) | Atomic Change | Traces |
| :---: | :--- | :--- | :--- |
| **1** | `PolarPmdStreamCoordinator.kt` | Add `renegotiating`/`renegotiateJob` to `SlotState`, implement `renegotiateSlot(...)` (sibling `scope` coroutine, single-flight, `onOldSlotCancelled` under `slotLock`, `isAllowedToRelaunch()` guards, job-identity `finally`), add `cancelAllRenegotiations()` wired into `cancelAllStreams()`, and add `computeDesiredAccRateHz()` helper (preserving `collectBounded(..., DROP_OLDEST, 64)` & `collectPmdWithAlreadyInStateRecovery`). | `REQ-SET-122.2` |
| **2** | `PolarOnlineStreamingBridge.kt` | Add `availableAccRates`/`activeAccSampleRateHz` with exact set/clear boundaries; add `BANALService.MULTISPORT_TRANSITION_INTENT` to `sportChangedReceiver` filter; call `streamCoordinator.cancelAllRenegotiations()` in `pauseForOfflineSync()`; wire `maybeRenegotiateAccRate(runActive)` in `syncCadenceSensorRegistration()` (`beforeRelaunch` resets `runningDynamicsEstimator` only, `relaunch` calls `startAccCollection(api)` re-evaluating `isRunSportActive()`); keep file strictly `<= 1,500` LoC with unchanged public/JVM signatures. | `REQ-SET-122.1`, `REQ-SET-122.4` |
| **3** | `PolarH10RunningDynamicsEstimator.kt` | In `resetForGap()` (`:180-193`), reset `avgDtMs = DEFAULT_DT_MS` so prior low-rate bursts or `> 500 ms` gaps cannot poison `avgDtMs <= 7.5 ms` on `200 Hz` resume. | `REQ-SET-122.3` |
| **4** | `PolarPmdStreamCoordinatorTest.kt`, `PolarOnlineStreamingBridgeTest.kt`, `PolarH10RunningDynamicsEstimatorTest.kt` | Add targeted unit/integration tests for `TST-SET-124.1`–`.3`: `UNKNOWN`/`BIKE` (`100 Hz`) -> `RUN` (`200 Hz`) via user intent, `MULTISPORT_TRANSITION_INTENT`, Tier-3 inference inside `processAccData()`, & mid-renegotiation sport-flip convergence; `RUN -> BIKE` downgrade (`100 Hz`); `STRIDES` continuity; `ACC_OWNER_ONLINE` release/re-acquire; `deviceDisconnected`/`pauseForOfflineSync`/`cancelAllStreams`/`shutDown` cancellation; `50/100 Hz`-only zero-loop guard; Verity `52 Hz` isolation; `resetForGap()` + realistic asymmetric running waveform + `MySensorManager` propagation. | `TST-SET-124.1`–`.3` |
| **5** | Tier 1 Verification & Living Docs | Run targeted `./gradlew :app:testDebugUnitTest --rerun --tests ...` (including `PolarDualImuBoundedHeapStressTest`, `ArchitectureConformanceContractTest`, `InterfaceSignatureDriftContractTest`), promote `REQ-SET-122` / `TST-SET-124` to `[Implemented]` with verbatim `TestClass#testMethod` citations, and verify `verify_requirement_governance.py`. | `TST-SET-124.4` |

---

## 3. Targeted Verification Command (Tier 1, `Rule 7.5`)
```bash
./gradlew :app:testDebugUnitTest --rerun \
  --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarPmdStreamCoordinatorTest" \
  --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarOnlineStreamingBridgeTest" \
  --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarH10RunningDynamicsEstimatorTest" \
  --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarDualImuBoundedHeapStressTest" \
  --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest" \
  --tests "com.atrainingtracker.trainingtracker.build.InterfaceSignatureDriftContractTest"
```

---

## 4. Non-Negotiable System Invariants & Rollback Safety
* `PolarOnlineStreamingBridge.kt <= 1,500` LoC (`1,469 -> ~1,490`), zero public/JVM signature breaks; `ATT-8017` `CADENCE` & cumulative `STRIDES` continuity across `100 -> 200 Hz` upgrade; `ATT-9343` `DROP_OLDEST` (`64`) bounded heap; `ATT-9161` single-flight `StreamSlot` & `ERROR_ALREADY_IN_STATE` recovery; `ATT-9175` offline GATT yield; Verity Sense `52 Hz` isolation; zero DB schema or UI/string changes; Rule 3 Human Gate preserved.
