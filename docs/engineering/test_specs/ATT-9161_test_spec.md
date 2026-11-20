# Stage 2: Requirement & Test Specification - ATT-9161

**Ticket**: [ATT-9161](https://rainerblind.atlassian.net/browse/ATT-9161) | **Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-20_01_Früh` | **Branch**: `feature/ATT-9161`  
**Requirement Mapping**: `REQ-CON-053` | **Test Spec ID**: `TST-CON-053` | **Date**: 2026-11-20

---

## 1. Requirement Specification (`REQ-CON-053`)

### 1.1 Attributes & System Traceability
- **System Requirement(s)**: `SYSREQ-TRK-001` (Multi-Sensor Acquisition & Profile Interoperability), `SYSREQ-TRK-002` (Automatic Reconnection & Exponential Backoff Resilience) — synced in `docs/system/sysreq_live_tracking.md` and `docs/system/system_software_traceability.md` (new file names deferred from `docs/system/` until Stage 4 per `Rule 81`; no new external BLE UUID/permission/element).
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit, Integration

### 1.2 Functional & Architectural Requirements
1. **`REQ-CON-053.1` (Unified 4-Stream Atomic Re-Entrancy Guard & Job-Identity Cleanup)**:
   - Extract `PolarPmdStreamCoordinator` (`banalservice/devices/bluetooth_le/polar/`) to manage all 4 live stream slots (`HR`, `ACC`, `GYRO`, `PPI`) under a single lock with pre-launch atomic `compareAndSet(false, true)`, `CoroutineStart.LAZY` job publication, and `if (slot.job === myJob)` identity-checked `finally` cleanup.
   - Re-entrant invocations of `startHrCollection`, `startAccCollection`, `startGyroCollection`, or `startPpiCollection` from overlapping `bleSdkFeatureReady`, `syncAlreadyOpenSessionIfReady`, or `disInformationReceived` (`maybeStartVerityStreamsIfReady`) callbacks SHALL be atomic no-ops while a stream slot is active or in-flight (including while `resolveAccSetting`, `resolveGyroSetting`, or `getAvailableOnlineStreamDataTypes` suspends), preventing `00002a37` CCCD toggle storms and overlapping PMD `REQUEST_MEASUREMENT_START` commands.
   - For `ACC`, the coordinator SHALL claim the atomic slot BEFORE calling `PolarOfflineGattCoordinator.tryAcquireAccChannel(..., ACC_OWNER_ONLINE)` and roll back the slot atomically if the channel is held by offline recording; `finally` SHALL clear active flags and release `ACC_OWNER_ONLINE` strictly when `slot.job === myJob`.
2. **`REQ-CON-053.2` (Bounded PMD `ERROR_ALREADY_IN_STATE` Recovery Across `ACC`, `GYRO`, and `PPI`)**:
   - Classify PMD `ERROR_ALREADY_IN_STATE` via typed `BleControlPointCommandError.error == PmdControlPointResponseCode.ERROR_ALREADY_IN_STATE` (direct or `cause`) and case-insensitive `"ERROR_ALREADY_IN_STATE"` message fallback.
   - When `startAccStreaming`, `startGyroStreaming`, or `startPpiStreaming` fails with `ERROR_ALREADY_IN_STATE` while connected and not shut down, the stream loop SHALL execute up to `MAX_ALREADY_IN_STATE_RETRIES = 3` recovery attempts: log structured warning, call `api.stopStreaming(sessionId, pmdMeasurementType)`, suspend for injectable backoff (`500 ms -> 1,000 ms -> 2,000 ms`), and re-subscribe.
   - Receiving a valid data packet after recovery SHALL reset the retry counter to `0`; `CancellationException` SHALL always rethrow immediately, and `deviceDisconnected`, `blePowerStateChanged(false)`, and `shutDown()` SHALL cancel all stream slots cleanly.
3. **`REQ-CON-053.3` (Retry-Exhaustion Diagnostics, Deduplicated Warning Bus & 9-Locale Snackbar)**:
   - When bounded `ERROR_ALREADY_IN_STATE` retries are exhausted while still connected, the bridge SHALL log structured diagnostics (Polar ID, MAC, stream type, attempt count, likely Polar Flow / stale PMD state conflict) and emit a session-deduplicated `PolarPmdStreamWarning` onto `PolarPmdStreamCoordinator.streamWarning` (`StateFlow<PolarPmdStreamWarning?>` with explicit `consumeStreamWarning()` and automatic reset on connect/disconnect/power-off/`shutDown()`), so simultaneous `ACC`/`GYRO`/`PPI` exhaustion surfaces ONE warning per connect cycle.
   - A compact shared composable `PolarPmdStreamWarningEffect(snackbarHostState)` SHALL observe `streamWarning` in `TrackingTabsScreen.kt` and `DevicesTabbedScreen.kt`, displaying `R.string.polar_live_imu_stream_blocked_warning` (`SnackbarDuration.Long`) and invoking `consumeStreamWarning()` upon dismissal/acknowledgement.
4. **`REQ-CON-053.4` (Monolith Ceilings & Regression Invariants)**:
   - Preserve `PolarOnlineStreamingBridge.kt <= 1,306 / 1,500` LoC, `TrackingTabsScreen.kt <= 1,450 / 1,500` LoC, `0` edits to protected monoliths (`DeviceManager.java`, `TrackerService.java`, `DevicesDatabaseManager.java`), `0` upward `banalservice -> trainingtracker.ui` imports, all `InterfaceSignatureDriftContractTest` signatures, `ATT-9176` optical PPI H10-exclusion/`PolarPpiStreamProcessor` behavior, and `ATT-8994` dynamic sensor `deactivateSensor()` before `unregisterSensor`/`removeSensor`.

### 1.3 Localization Parity Table (`strings_devices.xml` — 9 Locales)
Key: `polar_live_imu_stream_blocked_warning` (args: `%1$s` = sensor name, `%2$s` = stream type `ACC`/`GYRO`/`PPI`; zero inline emojis):

| Locale | Exact String Value |
| :--- | :--- |
| `values` (EN) | `%1$s: Live IMU (%2$s) blocked by another app (e.g. Polar Flow) or stale sensor state. Close Polar Flow or restart the sensor.` |
| `values-de` (DE) | `%1$s: Live-IMU (%2$s) durch eine andere App (z. B. Polar Flow) oder einen hängenden Sensorzustand blockiert. Polar Flow schließen oder Sensor neu starten.` |
| `values-es` (ES) | `%1$s: IMU en directo (%2$s) bloqueado por otra app (p. ej. Polar Flow) o un estado obsoleto del sensor. Cierra Polar Flow o reinicia el sensor.` |
| `values-fr` (FR) | `%1$s : IMU en direct (%2$s) bloqué par une autre appli (ex. Polar Flow) ou un état obsolète du capteur. Fermez Polar Flow ou redémarrez le capteur.` |
| `values-it` (IT) | `%1$s: IMU live (%2$s) bloccato da un\'altra app (es. Polar Flow) o da uno stato obsoleto del sensore. Chiudi Polar Flow o riavvia il sensore.` |
| `values-ja` (JA) | `%1$s：ライブIMU（%2$s）が他のアプリ（Polar Flowなど）またはセンサーの古い状態によりブロックされています。Polar Flowを閉じるか、センサーを再起動してください。` |
| `values-nl` (NL) | `%1$s: Live-IMU (%2$s) geblokkeerd door een andere app (bijv. Polar Flow) of een vastgelopen sensorstatus. Sluit Polar Flow of start de sensor opnieuw op.` |
| `values-pl` (PL) | `%1$s: Live IMU (%2$s) zablokowane przez inną aplikację (np. Polar Flow) lub nieaktualny stan czujnika. Zamknij Polar Flow lub uruchom ponownie czujnik.` |
| `values-pt` (PT) | `%1$s: IMU ao vivo (%2$s) bloqueado por outro app (ex.: Polar Flow) ou estado antigo do sensor. Feche o Polar Flow ou reinicie o sensor.` |

---

## 2. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
- **Original Requirement ID & Target**: Refines `REQ-SET-112` (`.2`/`.3`), `REQ-CON-045` (`.4`), `REQ-CON-050` (`.2`/`.3`), `REQ-HRV-051` (`.2`), and preserves `REQ-FIL-038` (`.2`) targeting `PolarOnlineStreamingBridge.kt`.
- **Historical Origin & Commit Trace**: `ATT-8016` (`f4bef44c`), `ATT-8714` (`1e7a2f0b`), `ATT-8717` (`9f9e5967`), `ATT-9176` (`32ca4857`), `ATT-8994` (`001dd39f`).
- **Root Reason for Existing Formulation**: Earlier iterations assumed single-shot `bleSdkFeatureReady` delivery and cancelled jobs unconditionally; `ATT-9176` added atomic CAS only for `PPI`, leaving `HR`/`ACC`/`GYRO` unguarded and all PMD streams without `ERROR_ALREADY_IN_STATE` recovery.
- **Preservation of Core Invariants**: Preserves single-GATT ref-counting (`REQ-SET-118`), `ACC` offline mutex (`REQ-SET-112.3`), `activeSdkStreamId` pinning (`REQ-CON-050.3`), `BSportType.RUN` cadence/dynamics gating (`REQ-SET-112.4`/`REQ-SET-116`), optical PPI H10 exclusion & `PolarPpiStreamProcessor` (`REQ-HRV-051`), dynamic sensor `deactivateSensor()` (`REQ-FIL-038.2`), and `0` edits to protected monoliths.

---

## 3. Acceptance Criteria (Given-When-Then)
- *Given* a Polar H10 or Verity Sense connects with `polarLiveImuEnabled = true` and rapid overlapping `bleSdkFeatureReady`, `syncAlreadyOpenSessionIfReady`, and `disInformationReceived` callbacks fire while `resolveAccSetting`/`resolveGyroSetting`/`getAvailableOnlineStreamDataTypes` suspend, *When* streams start, *Then* `PolarPmdStreamCoordinator` admits at most ONE active subscription per stream (`HR`, `ACC`, `GYRO`, `PPI`), produces zero cancel-and-restart bursts, rolls back `ACC` CAS cleanly if offline recording holds `ACC_OWNER_OFFLINE`, and prevents cancelled jobs from clobbering newer slots or `ACC_OWNER_ONLINE`.
- *Given* `startAccStreaming`, `startGyroStreaming`, or `startPpiStreaming` fails with `BleControlPointCommandError(..., ERROR_ALREADY_IN_STATE)` (or wrapped/stringified error), *When* caught by the supervisor, *Then* `api.stopStreaming(sessionId, pmdType)` executes followed by backoff (`500 ms -> 1,000 ms -> 2,000 ms`, max `3` retries); if a retry succeeds, IMU/PPI packets flow normally without sensor power-cycle and retry count resets on first frame.
- *Given* all `3` `ERROR_ALREADY_IN_STATE` recovery attempts fail while connected (e.g., Polar Flow holds PMD), *When* retries exhaust across one or more PMD streams, *Then* structured diagnostics log once per stream and ONE deduplicated `PolarPmdStreamWarning` surfaces a localized 9-locale M3 Snackbar via `PolarPmdStreamWarningEffect` in `TrackingTabsScreen`/`DevicesTabbedScreen`, clearing on consume or disconnect/`shutDown()`.
- *Given* Verity Sense disconnects, Bluetooth powers off, or `shutDown()` runs, *When* teardown executes, *Then* all 4 stream slots cancel cleanly, `ATT-8994` `deactivateSensor()` and `ATT-9176` `PolarPpiStreamProcessor`/`stopStreaming(PPI)` invariants hold, `PolarOnlineStreamingBridge.kt <= 1,306 / 1,500` LoC, and protected monoliths remain untouched.

---

## 4. Test Specification (`TST-CON-053`)
- **`TST-CON-053.1` (`PolarPmdStreamCoordinatorTest.kt` & `PolarOnlineStreamingBridgeTest.kt` — Atomic Re-Entrancy, ACC Mutex Rollback & Job Identity)**: Verify rapid duplicate `FEATURE_HR` / `FEATURE_POLAR_ONLINE_STREAMING` / `FEATURE_POLAR_OFFLINE_RECORDING` / `DIS 0x2A24` / `syncAlreadyOpenSessionIfReady` bursts while `resolveAccSetting`/`resolveGyroSetting` suspend invoke `startHrStreaming`, `startAccStreaming`, `startGyroStreaming`, and `startPpiStreaming` strictly once each; verify `ACC` CAS rolls back cleanly when `ACC_OWNER_OFFLINE` holds the channel; verify a cancelled job's `finally` never clears a newer job's active flag or `ACC_OWNER_ONLINE`.
- **`TST-CON-053.2` (`PolarPmdStreamCoordinatorTest.kt` & `PolarOnlineStreamingBridgeTest.kt` — Bounded `ERROR_ALREADY_IN_STATE` Recovery for ACC/GYRO/PPI)**: Verify typed `BleControlPointCommandError(ERROR_ALREADY_IN_STATE)`, wrapped cause, and message fallback trigger `stopStreaming(id, ACC/GYRO/PPI)` + `500 ms -> 1,000 ms -> 2,000 ms` backoff; verify recovery on retry 1..3 restores packet flow & resets attempt counter; verify non-`ERROR_ALREADY_IN_STATE` errors do not loop and `CancellationException` / disconnect / `blePowerStateChanged(false)` / `shutDown()` immediately halt pending backoff.
- **`TST-CON-053.3` (`PolarPmdStreamCoordinatorTest.kt`, `PolarOnlineStreamingBridgeTest.kt`, `PolarPmdStreamWarningEffectTest.kt` — Exhaustion Warning, Deduplication & 9-Locale Parity)**: Verify exhaustion after `3` retries emits ONE session-deduplicated `PolarPmdStreamWarning` even when `ACC`, `GYRO`, and `PPI` fail together, clears on `consumeStreamWarning()` / disconnect / reconnect / `shutDown()`, and renders the localized Snackbar in `PolarPmdStreamWarningEffect` (`createComposeRule()`); verify `TranslationParityTest` passes across all 9 locales for `polar_live_imu_stream_blocked_warning`.
- **`TST-CON-053.4` (Regression & Contract Suites)**: Verify existing `PolarOnlineStreamingBridgeTest` (incl. `ATT-9176` PPI & `ATT-8994` `deactivateSensor()`), `ArchitectureConformanceContractTest` (`PolarOnlineStreamingBridge.kt <= 1,306/1,500`, `0` protected monolith edits, `0` upward `banalservice -> ui` imports), `InterfaceSignatureDriftContractTest`, `DeadCodeEliminationContractTest`, `SystemLiveTrackingRequirementsContractTest`, and `SourceTextReadingAuditContractTest`.

---

## 5. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-CON-053.1` | Pure JVM / Bridge Unit | `PolarPmdStreamCoordinator`, `PolarOnlineStreamingBridge` | `REQ-CON-053.1` | Specified |
| `TST-CON-053.2` | Pure JVM / Bridge Unit | `PolarPmdStreamCoordinator`, `PolarOnlineStreamingBridge` | `REQ-CON-053.2` | Specified |
| `TST-CON-053.3` | Unit / Compose UI / L10n | `PolarPmdStreamCoordinator`, `PolarPmdStreamWarningEffect`, `strings_devices.xml` | `REQ-CON-053.3` | Specified |
| `TST-CON-053.4` | Regression / Contracts | `PolarOnlineStreamingBridge`, Contracts | `REQ-CON-053.4` | Specified |
