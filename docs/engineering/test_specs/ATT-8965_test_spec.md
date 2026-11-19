# Stage 2: Requirement & Test Specification - ATT-8965

**Ticket**: [ATT-8965](https://rainerblind.atlassian.net/browse/ATT-8965) | **Parent**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) | **Sprint**: `2026-11-19_01_Früh`  
**Branch**: `feature/ATT-8965` | **Requirement Mapping**: `REQ-CON-052` (`SYSREQ-TRK-002`) | **Test Spec ID**: `TST-CON-052` | **Date**: 2026-11-19

---

## 1. Requirement Specification (`REQ-CON-052`)
- **Attributes**: Priority: `High` | Type: `Functional` | Verification Method: `Unit` | Status: `[Verified]`
- **System Requirement(s)**: `SYSREQ-TRK-002` *(preserves `MyBTLEDevice.INITIAL_RECONNECT_DELAY_MS = 1000L` & `MAX_RECONNECT_DELAY_MS = 16000L` reflection contract while extending runtime steady-state backoff via `SensorReconnectBackoffPolicy`)*
- **Rationale**: During active workouts, `MyBTLEDevice` retries reconnects every `16 s` indefinitely, wasting battery during longer out-of-range intervals, while `DeviceManager` (`tries <= 1`) and `PolarOnlineStreamingBridge` abandon search permanently after `mMyRemoteDeviceTries` exhaustion (`ATT-8965`, refining `REQ-CON-020.2`, `REQ-CON-036.1`, `REQ-CON-051.2`).
- **Requirements**:
  1. **`REQ-CON-052.1` (Extended Exponential Backoff Progression)**: `SensorReconnectBackoffPolicy` SHALL compute reconnect/re-search delays along `1s -> 2s -> 4s -> 8s -> 16s -> 32s -> 64s -> 128s -> <finalPeriodMs>` (default `300_000 ms` = `5 min`, capped at configured final period) and reset to `1_000 ms` upon connection / service discovery. `MyBTLEDevice.INITIAL_RECONNECT_DELAY_MS = 1000L` and `MyBTLEDevice.MAX_RECONNECT_DELAY_MS = 16000L` MUST remain intact for `SystemLiveTrackingRequirementsContractTest`.
  2. **`REQ-CON-052.2` (Unified Periodic Re-Search Across BLE, Polar SDK & DeviceManager)**: When periodic long-term re-search is enabled (default `true`), `MyBTLEDevice`, `PolarOnlineStreamingBridge`, and `DeviceManager` (on `tries <= 1` search exhaustion) SHALL schedule periodic re-search / reconnect retries via shared Kotlin policy/coordinator helpers, cancelling pending timers on connect, `startSearchForPairedDevices()`, `removeAndStopRemoteDevice()`, and `shutDown()`, and preserving `REQ-CON-051` non-blocking timeout release, `DeviceManager.java <= 1,708` LoC, and `TrainingApplication.java <= 2,135` LoC. When disabled (`false`), `DeviceManager` shuts down & removes exhausted `READY` / `PolarOnlineStreamingBridge` devices without long-term polling (`REQ-CON-036.1`) and `MyBTLEDevice` keeps bounded `1s..16s` legacy fast ceiling (`SYSREQ-TRK-002`).
  3. **`REQ-CON-052.3` (Dual Search Settings UI, Preference Defaults & 9-Locale Parity)**: `SensorSearchSettings` (`DefaultSharedPreferences`) and `xml/prefs_search.xml` SHALL persist toggle `periodicResearchEnabled` (default `true`) and discrete final period options (`1m, 2m, 5m, 10m`; default `300s` = `5 min`). BOTH `SearchSettingsDialog.kt` AND `SensorSearchTuningSection.kt` (`TuningToggleItem`, `TuningSegmentedButtonRow` per `docs/design_guidelines.md` §1.4; zero raw `Switch(`) SHALL expose both settings with full 9-language parity in `res/values*/strings_devices.xml`.

### Acceptance Criteria (Given-When-Then) & Invariants
- *Given* periodic re-search enabled with final period `5 min`, *When* consecutive retries fail on `MyBTLEDevice`, `PolarOnlineStreamingBridge`, or `DeviceManager`, *Then* delays advance `1s -> 2s -> 4s -> 8s -> 16s -> 32s -> 64s -> 128s -> 300s` (clamped at `300s`) and reset on connect.
- *Given* periodic re-search disabled, *When* `DeviceManager` exhausts `mMyRemoteDeviceTries` for an idle/Polar sensor, *Then* no periodic re-search timer is scheduled and `shutDown()` + removal occur cleanly; mid-workout `MyBTLEDevice` stays capped at `16s`.
- *Given* `SearchSettingsDialog` or `SensorSearchTuningSection` rendered in Compose, *When* toggling periodic re-search or selecting `2 min` / `5 min` / `10 min`, *Then* both views stay synchronized via `DefaultSharedPreferences`, `SensorSearchTuningSection` uses `TuningToggleItem` + `TuningSegmentedButtonRow`, and all 9 locales pass `TranslationParityTest`.
- **Invariants**: `MyBTLEDevice.MAX_RECONNECT_DELAY_MS == 16000L` preserved; `DeviceManager.java <= 1,708` LoC; `TrainingApplication.java <= 2,135` LoC; `REQ-CON-051` queue unblocking preserved; `MyANTDevice` untouched; `0` new `src/main` test reads (`REQ-PRO-049.1`); Lane A/B isolation preserved.

---

## 2. Test Specification (`TST-CON-052`, Offline Mocked)
- **`TST-CON-052.1` (Backoff Sequence, Bounds & Toggle Policy)**: Assert `1s->2s->4s->8s->16s->32s->64s->128s->finalPeriod` progression, configured `finalPeriod` clamping (including `1m`, `2m`, `5m`, `10m`), disabled `16s` fallback ceiling, and reset-on-connect.
- **`TST-CON-052.2` (BLE, Polar SDK & DeviceManager Periodic Re-Search + Cancellation)**: Assert `MyBTLEDevice`, `PolarOnlineStreamingBridge`, and `DeviceManager` (`tries <= 1`) schedule extended periodic retries when enabled, abort cleanly on disable / `shutDown()` / `removeAndStopRemoteDevice()` / `startSearchForPairedDevices()`, preserve `REQ-CON-051` queue non-blocking, and uphold `MAX_RECONNECT_DELAY_MS == 16000L` + `DeviceManager`/`TrainingApplication` LoC contracts.
- **`TST-CON-052.3` (Behavioral Compose Dual UI & 9-Locale Parity — `REQ-PRO-132`)**: Render BOTH `SearchSettingsDialog` and `SensorSearchTuningSection` via `createComposeRule()`, verify toggle + final-period selection persistence, disabled-state subordination, and `TranslationParityTest` across all 9 `values*/strings_devices.xml` locales.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-CON-052.1` | Unit | `SensorReconnectBackoffPolicyTest`, `SensorSearchSettingsTest` | `REQ-CON-052.1` | Verified |
| `TST-CON-052.2` | Unit / Contract | `MyBTLEDeviceLifecycleTest`, `PolarOnlineStreamingBridgeTest`, `DeviceManagerSearchQueueTest`, `SystemLiveTrackingRequirementsContractTest`, `ArchitectureConformanceContractTest` | `REQ-CON-052.2` | Verified |
| `TST-CON-052.3` | Compose UI / L10n | `SearchSettingsDialogTest`, `SensorSearchTuningSectionTest`, `TranslationParityTest` | `REQ-CON-052.3` | Verified |
