# Stage 2 Requirement & Test Specification: ATT-9346 — Dynamic Polar H10 `ACC` Rate Renegotiation & Running Dynamics Recovery

**Ticket**: `ATT-9346` (`2 SP`, `Bug`, `severity-major`, Risk: `MEDIUM`) | **Parent Epic**: `ATT-5910`  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-21_01_Früh` (Sprint 468) | **Branch**: `feature/ATT-9346`  
**Living Docs**: `docs/requirements/req_settings_profiles.md` (`v1.0.17`, `REQ-SET-122` `[Planned]`), `docs/tests/tst_settings_profiles.md` (`v1.0.16`, `TST-SET-124` `[Planned]`)  
**Localization Parity**: `N/A - no new or modified UI strings` (all `VERTICAL_OSCILLATION` / `GROUND_CONTACT_TIME` 9-locale strings already present from `ATT-8018`)

---

## 1. Formal Requirements (`REQ-SET-122`, `[Planned]`)
* **Attributes**: **Priority**: `High` | **Type**: `Functional` | **Verification Method**: `Unit, Integration` | **System Requirement(s)**: `SYSREQ-TRK-002`, `SYSREQ-TRK-003` (zero `SYS.2`/`SYS.3` topology changes) | **Implementation**: `PolarPmdStreamCoordinator.kt`, `PolarOnlineStreamingBridge.kt`, `PolarH10RunningDynamicsEstimator.kt`

| ID | Normative Contract (`SHALL` / `MUST`) |
| :--- | :--- |
| **`REQ-SET-122.1`** | `PolarOnlineStreamingBridge` SHALL track per-connection `availableAccRates: Set<Int>` and `activeAccSampleRateHz: Int?` (cleared on disconnect/`shutDown()`), subscribe to `BANALService.MULTISPORT_TRANSITION_INTENT` alongside `SPORT_TYPE_CHANGED_BY_USER_INTENT`, and compute `desiredAccRateHz` against `availableAccRates` (`200 Hz` for `!isVeritySense && runActive` when advertised, else `100/50 Hz`; `52 Hz` for Verity Sense). When `StreamSlot.ACC` is active and `activeAccSampleRateHz != desiredAccRateHz` while connected and `!isOfflineGattYielded()`, `syncCadenceSensorRegistration()` SHALL request single-flight renegotiation (`100/50 -> 200 Hz` on `RUN` entry via user tap, route select, Tier-3 inference, multisport, or crash-recovery reconnect; `200 -> 100/50 Hz` on `RUN` exit). When `200 Hz` is unadvertised, `desiredAccRateHz == activeAccSampleRateHz` MUST be a no-op (zero restart loops). Verity Sense MUST stay pinned at `52 Hz` without renegotiation or VO/GCT registration. Mid-run `100/50 -> 200 Hz` upgrades (`runActive == true`) SHALL reset `runningDynamicsEstimator` only, preserving `cadenceEstimatorInstance` and cumulative `mStridesSensor`. |
| **`REQ-SET-122.2`** | `PolarPmdStreamCoordinator` SHALL provide a single-flight, non-reentrant `renegotiateSlot(slot, onOldSlotCancelled, stopStreaming, relaunch)` executing on a sibling `scope` coroutine so calls originating from inside `processAccData()` (`StreamSlot.ACC` collector) cannot self-cancel renegotiation. It SHALL cancel the old slot, explicitly execute `onOldSlotCancelled` to release `PolarOfflineGattCoordinator.ACC_OWNER_ONLINE` across aliases (since `cancelSlot()` clears `state.job` before cancellation and bypasses `launchStream`'s `onFinalCleanup`), invoke `stopStreaming`, and re-check `!isShutDownProvider() && isConnectedProvider()` before calling `relaunch` (preserving `ATT-9343` `DROP_OLDEST` capacity `64` and `ATT-9161` `ERROR_ALREADY_IN_STATE` recovery). Any in-flight renegotiation coroutine MUST be cancelled atomically on `cancelAllStreams()`, `deviceDisconnected()`, `pauseForOfflineSync()` (`ATT-9175`), and `shutDown()` so delayed restarts MUST NEVER fire post-disconnect or during offline PFTP yield. |
| **`REQ-SET-122.3`** | `PolarH10RunningDynamicsEstimator.resetForGap()` SHALL reset `avgDtMs = DEFAULT_DT_MS` (`5.0 ms`) alongside filter/buffer state so prior low-rate bursts or `> 500 ms` BLE gaps cannot permanently poison `avgDtMs <= 7.5 ms` once `200 Hz` resumes. Post-renegotiation VO (`5.0..13.0 cm`) and GCT (`140..400 ms`) SHALL propagate cleanly through `MySensorManager`, `WorkoutSamples.db`, `CSVFileWriter`, and `FitFileWriter` (`mm`/`ms`). |
| **`REQ-SET-122.4`** | `PolarOnlineStreamingBridge.kt` MUST stay `<= 1,500` LoC (`ArchitectureConformanceContractTest`) with unchanged public/JVM signatures (`InterfaceSignatureDriftContractTest`); zero SQLite schema bumps; `Localization Parity: N/A`. |

---

## 2. Acceptance Criteria (`Given-When-Then`)
1. **Post-Connect `UNKNOWN`/`BIKE` -> `RUN` Upgrade (`REQ-SET-122.1`, `.2`, `.3`)**: *Given* Polar H10 connects before `BSportType.RUN` settles (`100 Hz` negotiated) and later `BSportType.RUN` activates via user intent, `MULTISPORT_TRANSITION_INTENT`, Tier-3 inference inside `processAccData()`, or post-crash reconnect, *When* `syncCadenceSensorRegistration(true)` runs, *Then* sibling-coroutine `renegotiateSlot(ACC)` cancels `100 Hz`, releases & re-acquires `ACC_OWNER_ONLINE`, stops `ACC`, restarts at `200 Hz` (`DROP_OLDEST`), preserves cumulative `STRIDES`, and emits valid `VERTICAL_OSCILLATION` (`5.0..13.0 cm`) and `GROUND_CONTACT_TIME` (`140..400 ms`) registered in `MySensorManager`.
2. **`RUN` -> `BIKE` Downgrade (`REQ-SET-122.1`)**: *Given* an active `200 Hz` H10 `RUN` stream switches to `BIKE`, *When* `syncCadenceSensorRegistration(false)` runs, *Then* VO/GCT/cadence sensors unregister and `ACC` downgrades cleanly back to `100 Hz`.
3. **Teardown & Offline-Yield Safety (`REQ-SET-122.2`)**: *Given* a renegotiation is in flight when `deviceDisconnected()`, `pauseForOfflineSync()`, `cancelAllStreams()`, or `shutDown()` fires, *When* cancellation runs, *Then* the renegotiation coroutine terminates immediately and `startAccStreaming()` is NEVER invoked post-disconnect or during offline GATT yield.
4. **Unsupported-Rate & Verity Sense Isolation (`REQ-SET-122.1`)**: *Given* an H10 advertising only `50/100 Hz` or a Verity Sense (`52 Hz`), *When* `BSportType.RUN` activates and `processAccData()` streams continuously, *Then* zero restart loops occur and Verity Sense remains at `52 Hz` with zero VO/GCT sensors.
5. **Gap Recovery (`REQ-SET-122.3`)**: *Given* `PolarH10RunningDynamicsEstimator` ingests `100 Hz` samples (`avgDtMs -> 10 ms`) followed by a `> 500 ms` gap and `200 Hz` running samples (including asymmetric stance/flight waveforms), *When* `resetForGap()` fires, *Then* `avgDtMs` resets to `5.0 ms` and valid VO/GCT emit cleanly while sustained `50/100 Hz` and `< 130 spm` walking GCT remain suppressed.

---

## 3. Traceability Matrix (`TST-SET-124`, `[Planned]`)

| Test Case ID | Req ID | Target Test Suite | Verification Procedure & Expected Result |
| :--- | :--- | :--- | :--- |
| **`TST-SET-124.1`** | `REQ-SET-122.1` | `PolarOnlineStreamingBridgeTest` | H10 connect in `UNKNOWN`/`BIKE` (`100 Hz`) -> activate `RUN` via user intent, `MULTISPORT_TRANSITION_INTENT`, and inside `processAccData()` (Tier-3 inference) -> verify `stopStreaming(ACC)` + `200 Hz` `startAccStreaming`, `STRIDES` preserved, VO/GCT non-null in `MySensorManager` (`ProxySensor` + named); verify `RUN -> BIKE` downgrade to `100 Hz`; verify `50/100 Hz`-only zero-loop guard and Verity `52 Hz` isolation. |
| **`TST-SET-124.2`** | `REQ-SET-122.2` | `PolarPmdStreamCoordinatorTest`, `PolarOnlineStreamingBridgeTest` | Verify `renegotiateSlot()` runs on sibling `scope` coroutine (surviving self-trigger from active `ACC` job), enforces single-flight dedup, runs `onOldSlotCancelled` before `relaunch` (`ACC_OWNER_ONLINE` release/re-acquire), preserves `DROP_OLDEST` (`64`) & `ERROR_ALREADY_IN_STATE` recovery, and cancels cleanly on `cancelAllStreams()`, `deviceDisconnected()`, `pauseForOfflineSync()`, and `shutDown()` with `0` late relaunches. |
| **`TST-SET-124.3`** | `REQ-SET-122.3` | `PolarH10RunningDynamicsEstimatorTest`, `PolarOnlineStreamingBridgeTest` | Feed `100 Hz` -> `> 500 ms` gap -> `200 Hz` sinusoidal & asymmetric stance/flight running waveforms -> verify `resetForGap()` resets `avgDtMs` and emits bounded VO (`5.0..13.0 cm`) & GCT (`140..400 ms`), while sustained `50/100 Hz` and `< 130 spm` walking GCT stay `null`; verify `MySensorManager` propagation for DB/CSV/FIT. |
| **`TST-SET-124.4`** | `REQ-SET-122.4` | `ArchitectureConformanceContractTest`, `InterfaceSignatureDriftContractTest` | Assert `PolarOnlineStreamingBridge.kt <= 1,500` LoC, zero public/JVM signature drift, and `verify_requirement_governance.py` exit `0`. |

---

## 4. Chesterton's Fence Audit (`REQ-PRO-022`) & System Invariants
* **Original Requirement ID & Target**: `REQ-SET-116` (`REQ-SET-116.1`/`.2`) & `ATT-9161` single-flight PMD stream coordination in `PolarOnlineStreamingBridge.kt`, `PolarPmdStreamCoordinator.kt`, `PolarH10RunningDynamicsEstimator.kt`.
* **Historical Origin & Commit Trace**: `6be4eb31` (`ATT-8018`), `104a028f` (`ATT-9161`), `ed05d679` (`ATT-9175`), `f8c03d6f` (`ATT-9343`).
* **Root Reason for Existing Formulation**: `ATT-8018` avoided `200 Hz` BLE outside `RUN` assuming sport was locked pre-connect; `ATT-9161` enforced single-flight `launchStream()` without a rate-change restart primitive.
* **Preservation of Core Invariants**: `ATT-8017` cadence (`strides/min`) & cumulative `STRIDES` preserved across `100 -> 200 Hz` upgrade; `ATT-9343` `DROP_OLDEST` (`64`); `ATT-9161` `ERROR_ALREADY_IN_STATE` recovery; `ATT-9175` offline GATT yield; Verity Sense `52 Hz` isolation; `PolarOnlineStreamingBridge.kt <= 1,500` LoC; zero schema/UI changes.
