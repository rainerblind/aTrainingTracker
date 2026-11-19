# Stage 3: Implementation Plan - ATT-8965

**Ticket**: [ATT-8965](https://rainerblind.atlassian.net/browse/ATT-8965) (`REQ-CON-052`, `TST-CON-052`) | **Branch**: `feature/ATT-8965`  
**Owner**: Agent 1 (`--as agent1`) | **Status**: Implementation Plan | **Date**: 2026-11-19

---

## 1. Architectural Design & Layer Boundaries (SWE.2 / SWE.3)
- **Backoff & Preferences Helpers (`banalservice/devices/` & `trainingtracker/settings/`)**: Isolate backoff schedule calculation (`SensorReconnectBackoffPolicy.kt`), per-device periodic re-search scheduling (`DeviceSearchPeriodicRetryCoordinator.kt`), and preference storage (`SensorSearchSettings.kt`, `PreferenceManager.getDefaultSharedPreferences`) outside monoliths so `DeviceManager.java` stays `<= 1,708` LoC and `TrainingApplication.java` stays `<= 2,135` LoC (`ArchitectureConformanceContractTest`).
- **BLE & Polar SDK Integration (`MyBTLEDevice.java`, `PolarOnlineStreamingBridge.kt`)**: Keep `MyBTLEDevice.INITIAL_RECONNECT_DELAY_MS = 1000L` and `MAX_RECONNECT_DELAY_MS = 16000L` untouched (`SystemLiveTrackingRequirementsContractTest`, `SYSREQ-TRK-002`); delegate runtime reconnect step computation to `SensorReconnectBackoffPolicy`. Add cancellable backoff re-search/reconnect scheduling in `PolarOnlineStreamingBridge` aligned with `SensorSearchSettings`.
- **UI Consistency (`docs/design_guidelines.md` §1.1 & §1.4)**: Expose Periodic Re-Search toggle (default `true`) and Final Period (`1m, 2m, 5m, 10m`, default `5m`) in BOTH `SearchSettingsDialog.kt` (`SingleChoiceSegmentedButtonRow`, `icon = {}`) and `SensorSearchTuningSection.kt` (`TuningToggleItem` + `TuningSegmentedButtonRow`; zero raw `Switch` or `FilterChip`), backed by `res/xml/prefs_search.xml` defaults and 9-locale `res/values*/strings_devices.xml`.
- **SWE.3 / SWE.2 Maintenance**: Update `docs/architecture/detailed_design/banal_service.md`, `my_btle_device.md`, and `docs/architecture.md` to document periodic re-search states and backoff policy without breaching `DetailedDesignParityContractTest`.

---

## 2. Atomic Implementation Steps

| Step | Target Files | Change Summary | Verifies |
| :--- | :--- | :--- | :--- |
| **1. Backoff Policy & Settings + L10n** | `SensorReconnectBackoffPolicy.kt`, `SensorSearchSettings.kt`, `prefs_search.xml`, `res/values*/strings_devices.xml` | Implement `1s->2s->4s->8s->16s->32s->64s->128s->finalPeriodMs` (default `300_000L`, `16s` legacy fallback when disabled), SharedPreferences defaults, 9-locale strings | `TST-CON-052.1`, `TranslationParityTest` |
| **2. BLE & Polar SDK Wiring** | `MyBTLEDevice.java`, `PolarOnlineStreamingBridge.kt` | Delegate `MyBTLEDevice` reconnect delay to `SensorReconnectBackoffPolicy` (preserving `16000L` constant); add cancellable backoff reconnect/re-search in `PolarOnlineStreamingBridge` | `TST-CON-052.2` |
| **3. DeviceManager Coordinator** | `DeviceSearchPeriodicRetryCoordinator.kt`, `DeviceManager.java` | Schedule periodic re-search after `tries <= 1` exhaustion when enabled (cancelling on connect/stop/remove/`shutDown`); preserve `REQ-CON-051` queue release & `<= 1,708` LoC | `TST-CON-052.2`, `ArchitectureConformanceContractTest` |
| **4. Dual Search Settings UI** | `SearchSettingsDialog.kt`, `SensorSearchTuningSection.kt` | Add Periodic Re-Search toggle & `1m/2m/5m/10m` segmented period row to both UIs using `TuningToggleItem` & `TuningSegmentedButtonRow` (`design_guidelines.md` §1.4) | `TST-CON-052.3` |
| **5. Tests & Design Parity Sync** | Unit + Compose test suites, `banal_service.md`, `my_btle_device.md`, `docs/architecture.md` | Author non-tautological unit tests + dual `createComposeRule()` UI tests (`REQ-PRO-132`); update SWE.2/SWE.3 docs; run incremental `--rerun` tests + contracts | `TST-CON-052.1`–`.3`, `DetailedDesignParityContractTest` |

---

## 3. System Invariants & Verification Plan
- **Hard Invariants**: `MyBTLEDevice.MAX_RECONNECT_DELAY_MS == 16000L` preserved; `DeviceManager.java <= 1,708` LoC & `TrainingApplication.java <= 2,135` LoC; `REQ-CON-051` non-blocking search-queue release preserved; `MyANTDevice` untouched; disabled toggle restores `REQ-CON-036.1` bounded `READY` shutdown; zero new `src/main` test reads (`REQ-PRO-049.1`); Lane A/B isolation preserved.
- **Verification**: Run incremental `./gradlew :app:testDebugUnitTest --rerun --tests ...` (never `--rerun-tasks`) under `tools/parallel_sprint_lock.py --timeout 1800` covering all new/modified unit tests, both `createComposeRule()` UI tests, `SystemLiveTrackingRequirementsContractTest`, `ArchitectureConformanceContractTest`, `DetailedDesignParityContractTest`, and `TranslationParityTest`.
