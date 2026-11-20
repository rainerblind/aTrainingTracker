# Stage 1 Analysis: ATT-9161 - Fix PolarOnlineStreamingBridge Re-Entrant Stream Start Burst and Silent IMU Death on PMD ERROR_ALREADY_IN_STATE

**Ticket**: [ATT-9161](https://rainerblind.atlassian.net/browse/ATT-9161)  
**Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) (*Equipment, Sensors, Sport Types & Athlete Profile*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-20_01_Früh` | **Branch**: `feature/ATT-9161`  
**Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-20

---

## 1. Problem Statement & Field Evidence
Field captures on Pixel 10 (`5.0.0-dev-906504b8`, `2026-11-19`) with **Polar Verity Sense** (`24:AC:AC:24:82:56`, ID `24825632`, `polarLiveImuEnabled = true`) revealed two coupled defects in `PolarOnlineStreamingBridge`:
1. **Re-entrant stream start bursts**: Rapid handshake callbacks trigger repeated cancel-and-restart cycles, logging **7 `setCharacteristicNotification(00002a37)` toggles** (`true, true, true, false, true, false, true`) within `780 ms` and issuing overlapping PMD `REQUEST_MEASUREMENT_START` commands.
2. **Silent permanent IMU death on `ERROR_ALREADY_IN_STATE`**: When `startAccStreaming` / `startGyroStreaming` fail with `BleControlPointCommandError(..., ERROR_ALREADY_IN_STATE)` (either self-inflicted by re-entrant starts or caused by Android CDM auto-waking `fi.polar.polarflow` `1.5 s` earlier on `fb005c81`/`fb005c82`), `.catch` swallows the error, `finally` clears `is*StreamingActive`, and IMU remains dead for the whole session with zero retry and zero user-visible warning.

---

## 2. Independent Peer Review & Verification
Independently audited live `sprint/2026-11-20_01_Früh` @ `001dd39f` (post-`ATT-9176` `32ca4857` & `ATT-8994` `001dd39f`) + `javap` bytecode inspection of cached `polar-ble-sdk:8.6.0` (`polar-ble-sdk-8.6.0-api.jar` / `runtime.jar`, `Rule 68` side-effect trace):
1. **Verified Line-Number Shift & 4-Stream Re-Entrancy Audit (`PolarOnlineStreamingBridge.kt`, `1,306/1,500` LoC)**:
   - **`HR` (`startHrCollection()`, `L775–787`)**: Called from `bleSdkFeatureReady(FEATURE_HR)` (`L527`) and `syncAlreadyOpenSessionIfReady()` (`L754–756`). Has **zero atomic guard or active flag** and unconditionally executes `hrJob?.cancel(); hrJob = scope.launch { ... }` (`L779–780`), directly causing `00002a37` CCCD toggle storms whenever multiple callbacks overlap.
   - **`ACC` (`startAccCollection()`, `L861–888`)**: Called from `bleSdkFeatureReady(FEATURE_POLAR_ONLINE_STREAMING)` (`L535`) and `syncAlreadyOpenSessionIfReady()` (`L764–765`). Has **no CAS guard**, cancels `accJob` non-blockingly (`L869`), sets `isAccStreamingActive.set(true)` only **after** `suspend fun resolveAccSetting()` (`L872–873`), and runs `finally { isAccStreamingActive.set(false); releaseAccChannel(...) }` (`L883–886`) **without job-identity (`accJob === myJob`) verification**—allowing a cancelled prior coroutine to clobber a newly launched stream's active flag and `ACC_OWNER_ONLINE` lock!
   - **`GYRO` (`startGyroCollection()`, `L890–910`)**: Invoked from **4 sites** (`bleSdkFeatureReady(FEATURE_POLAR_ONLINE_STREAMING)` `L535`, `bleSdkFeatureReady(FEATURE_POLAR_OFFLINE_RECORDING)` `L569`, `handleDisCharacteristic()` `L800`, `syncAlreadyOpenSessionIfReady()` `L764–768`). While coroutine #1 suspends in `resolveGyroSetting()` (`L895`), `isGyroStreamingActive` stays `false`, so `maybeStartVerityStreamsIfReady()` (`L821`) bypasses the guard, cancels coroutine #1 non-blockingly, and collides on PMD `REQUEST_MEASUREMENT_START`. Its `finally` (`L907`) also lacks job-identity protection.
   - **`PPI` (`startPpiCollection()`, `L836–859`, added by `ATT-9176`)**: Already uses `ppiLifecycleLock` + atomic `isPpiStreamingActive.compareAndSet(false, true)` + `CoroutineStart.LAZY` + `if (ppiJob === myJob)` cleanup (`REQ-HRV-051.2`), **HOWEVER `L844–846` STILL lacks `ERROR_ALREADY_IN_STATE` recovery**! Because Verity Sense optical PPI uses the same PMD control point (`PmdMeasurementType.PPI`), an external Polar Flow / stale PMD state silently kills PPI too unless recovery covers `ACC`, `GYRO`, and `PPI` uniformly.
2. **Verified Polar BLE SDK `8.6.0` Recovery Contract (`javap`)**:
   - `BleControlPointCommandError.getError()` returns `PmdControlPointResponse.PmdControlPointResponseCode.ERROR_ALREADY_IN_STATE` (matching logcat `pmd cp command REQUEST_MEASUREMENT_START error: ERROR_ALREADY_IN_STATE`; checking typed error, cause, and message covers wrapped SDK errors).
   - `PolarOnlineStreamingApi.stopStreaming(String, PmdMeasurementType): Unit` dispatches an async coroutine (`BDBleApiImpl$stopPmdStreaming$1`) invoking `BlePMDClient.stopMeasurement(type)`. Therefore, a short non-zero backoff delay (e.g., `500 ms -> 1,000 ms -> 2,000 ms`, injectable for deterministic unit tests) **after** `stopStreaming` is mandatory before re-issuing `startAccStreaming` / `startGyroStreaming` / `startPpiStreaming`.
3. **Hidden Side-Effect & Layering Audit (`Rule 68`, `ArchitectureConformanceContractTest.kt`)**:
   - Extracting stream slot supervision, atomic CAS + `CoroutineStart.LAZY` + job-identity cleanup, bounded PMD retry policy, and a decoupled `StateFlow<PolarPmdStreamWarning?>` bus into a new helper **`PolarPmdStreamCoordinator.kt`** under `banalservice/devices/bluetooth_le/polar/` keeps `banalservice` free of upward `trainingtracker.ui` imports AND shrinks `PolarOnlineStreamingBridge.kt` below `1,306` LoC (well under the `1,500` LoC ceiling; protected monoliths `DeviceManager.java` `1,707/1,708`, `TrackerService.java` `3,539/3,559`, `DevicesDatabaseManager.java` `1,669/1,671` stay at **`0` edits**).
   - `TrackingTabsScreen.kt` (`1,417/1,500` LoC) and `DevicesTabbedScreen.kt` (`811/1,500` LoC) already host Material 3 `SnackbarHost`s; collecting the deduplicated warning state there surfaces a clear localized warning without adding new layouts or bloating LoC.
   - `ATT-8994` (`001dd39f`) `deactivateSensor()` disconnect invariants (`REQ-FIL-038.2`) and `ATT-9176` (`32ca4857`) optical PPI invariants (`REQ-HRV-051`) remain completely intact.

---

## 3. User Scope Grounding (`ATT-1250`) & Complexity Audit (`REQ-PRO-029`)
- **In-Scope (`2 SP` confirmed; no split required)**:
  1. Unified atomic re-entrancy guard + `CoroutineStart.LAZY` + job-identity `finally` cleanup across all 4 streams (`HR`, `ACC`, `GYRO`, `PPI`) in `PolarPmdStreamCoordinator.kt` + `PolarOnlineStreamingBridge.kt`.
  2. Bounded PMD `ERROR_ALREADY_IN_STATE` recovery (`stopStreaming(sessionId, type)` + short exponential backoff, max `3` retries, counter reset on first packet) across all 3 PMD streams (`ACC`, `GYRO`, `PPI`).
  3. Structured diagnostic logging + session-deduplicated user-visible M3 Snackbar status warning (9-locale `strings_devices.xml`) when retries are exhausted due to external (`fi.polar.polarflow`) or stale sensor PMD state.
- **Out-of-Scope**: Unpair GATT teardown / BLE connected-device scan enumeration (`ATT-9155`), silent-HR watchdog (`ATT-9155`), protected monolith edits (`DeviceManager.java`, `TrackerService.java`, `DevicesDatabaseManager.java`), SQLite schema changes, or Polar offline controller refactoring.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
- **Original Requirement ID & Target**: `REQ-SET-112` (`.2`, `.3`), `REQ-CON-045` (`.4`), `REQ-CON-050` (`.2`, `.3`), `REQ-HRV-051` (`.2`), `REQ-FIL-038` (`.2`) targeting `PolarOnlineStreamingBridge.kt`.
- **Historical Origin & Commit Trace**: `ATT-8016` (`f4bef44c`) introduced `PolarOnlineStreamingBridge`; `ATT-8714` (`1e7a2f0b`) added `syncAlreadyOpenSessionIfReady()` & disconnect job cancellation (`REQ-CON-050`); `ATT-8717` (`9f9e5967`) added DIS/feature Verity detection; `ATT-9176` (`32ca4857`) added `startPpiCollection()` with atomic CAS (`REQ-HRV-051.2`); `ATT-8994` (`001dd39f`) added `deactivateSensor()` before dynamic sensor teardown (`REQ-FIL-038.2`).
- **Root Reason for Existing Formulation**: Early iterations (`ATT-8016`/`ATT-8714`) assumed `bleSdkFeatureReady` fires once per feature after `connectToDevice` and cancelled prior jobs unconditionally as a quick reset; `ATT-9176` fixed re-entrancy only for the newly added `PPI` path, leaving `HR`/`ACC`/`GYRO` unguarded and all PMD streams without `ERROR_ALREADY_IN_STATE` recovery.
- **Preservation of Core Invariants**: Preserves single-GATT ref-counting (`REQ-SET-118`), `PolarOfflineGattCoordinator` `ACC` channel mutex (`REQ-SET-112.3`), `activeSdkStreamId` pinning (`REQ-CON-050.3`), `BSportType.RUN` cadence/dynamics gating (`REQ-SET-112.4`/`REQ-SET-116`), `ATT-9176` optical PPI H10-exclusion & `ARTIFACT_GAP` processing (`REQ-HRV-051`), `ATT-8994` `deactivateSensor()` null clearing (`REQ-FIL-038.2`), `InterfaceSignatureDriftContractTest` signatures, and `0` edits to protected monoliths.

---

## Change-Request Impact Analysis
- **Affected Requirements**: Net-new candidate **`REQ-CON-053`** / **`TST-CON-053`** (to be authored in Stage 2 in `docs/requirements/req_tracking_cockpit_part2.md` & `docs/tests/tst_tracking_cockpit_part2.md`, refining `REQ-SET-112.2`, `REQ-CON-050.2`/`.3`, and `REQ-HRV-051.2`). **`SYS.2`/`SYS.3` System-Level Impact Check (`REQ-PRO-131.4`)**: No new external BLE profile, GATT UUID, permission, or `SYS-EL-*`/`SYS-IF-*` element is added (`SYS-EL-02` Polar PMD `fb005c80` & `PolarOnlineStreamingBridge` already exist); Stage 2 will link `REQ-CON-053` under existing **`SYSREQ-TRK-001`** and **`SYSREQ-TRK-002`** in `docs/system/sysreq_live_tracking.md` and `docs/system/system_software_traceability.md`.
- **Affected Architecture Elements**: New helper `PolarPmdStreamCoordinator.kt` (`banalservice/devices/bluetooth_le/polar/`); updates to `PolarOnlineStreamingBridge.kt` (stays `< 1,306/1,500` LoC), `TrackingTabsScreen.kt` (`~1,429/1,500` LoC), `DevicesTabbedScreen.kt` (`~823/1,500` LoC), and 9-locale `strings_devices.xml`. Zero edits to `DeviceManager.java`, `TrackerService.java`, `DevicesDatabaseManager.java`, `PolarOfflineGattCoordinator.kt`, or SQLite schemas.
- **Verification Impact & Test Surface**: New pure-JVM `PolarPmdStreamCoordinatorTest.kt` + extended `PolarOnlineStreamingBridgeTest.kt` (burst re-entrancy across HR/ACC/GYRO/PPI including suspended `resolve*Setting`, job-identity `finally` non-clobbering, `ERROR_ALREADY_IN_STATE` `stopStreaming` + backoff recovery success on retry 1..3 for ACC/GYRO/PPI, retry-exhaustion diagnostic + deduplicated warning emission, disconnect/power-off/shutdown cancellation & warning reset); contract verification via `ArchitectureConformanceContractTest`, `InterfaceSignatureDriftContractTest`, `LocalizationParityContractTest`, and `SystemLiveTrackingRequirementsContractTest`.
- **Risk Assessment & Invariants**: **MEDIUM** — Race bugs in `ACC_OWNER_ONLINE` release or coroutine cancellation could block offline recording or leak jobs if `slot.job === myJob` is violated; mitigated by single-lock `CoroutineStart.LAZY` slot management, immediate `CancellationException` rethrow, deterministic `StandardTestDispatcher` unit tests, and preserving all existing `@VisibleForTesting` probes and `ATT-8994`/`ATT-9176` contracts.

---

## 6. Architectural Strategy & Localization Parity
1. **`PolarPmdStreamCoordinator.kt`**: Pure-coroutine stream slot manager (`HR`, `ACC`, `GYRO`, `PPI`) with synchronized `CoroutineStart.LAZY` CAS launch, job-identity `finally` cleanup (`slot.job === myJob`), `isPmdAlreadyInStateError(Throwable)` detector, bounded `stopStreaming` + backoff retry loop (`500 ms`, `1,000 ms`, `2,000 ms`; injectable delay for tests), and session-deduplicated `MutableStateFlow<PolarPmdStreamWarning?>`.
2. **`PolarOnlineStreamingBridge.kt`**: Delegate `HR`/`ACC`/`GYRO`/`PPI` job lifecycle and PMD retry recovery to `PolarPmdStreamCoordinator`, preserving all public/`@VisibleForTesting` methods, `ACC` mutex ownership, and `ATT-8994`/`ATT-9176` teardown invariants while reducing LoC below `1,306`.
3. **Status Warning UI & Localization Parity (9 Locales)**: Collect `PolarPmdStreamCoordinator.streamWarning` in `TrackingTabsScreen.kt` and `DevicesTabbedScreen.kt` to display a single localized Material 3 `Snackbar` using net-new string `polar_live_imu_stream_blocked_warning` added with 100% key/placeholder parity across all 9 locales (`values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`) in `strings_devices.xml`.
