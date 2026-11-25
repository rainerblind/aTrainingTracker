# Stage 5 Walkthrough & Verification Report — ATT-8723

**Ticket**: `ATT-8723` (`2 SP`, `tier-a`, Epic `ATT-5910`) | **Target Release**: `V5.0.0` | **Branch**: `feature/ATT-8723` | **Gate 3**: `ATT-9689` (`PASS`) | **Req / Test**: `REQ-CON-062` / `TST-CON-062` (`[Verified]`) | **Date**: 2026-11-24

## 1. Summary of Implemented Changes
1. **Dedicated BLE DIS Reconnect Backfill Helper (`BleDisReconnectBackfillHelper.kt`, `182/1,500` LoC)**:
   - Stateless `@JvmStatic` utility in `com.atrainingtracker.banalservice.devices.bluetooth_le` reusing `BleDisStringSanitizer` (`ATT-8996`) with partitioned ownership and zero duplicate GATT reads:
     - `needsManufacturerBackfill(current)`: returns `true` strictly for `null`, blank/whitespace-only, or NUL-containing (`'\u0000'`) values; clean values skip reads on subsequent reconnects.
     - `enqueueMissingManufacturerRead(device, gatt)`: guards shutdown/pause/`deviceId <= 0`/already-queued `0x2A29` and enqueues `0x2A29` via `enqueueReadCharacteristic()` when `0x180A`/`0x2A29` is present.
     - `onDisCharacteristicRead(device, gatt, snapshot)`: sanitizes `0x2A29` via `BleDisStringSanitizer.readSanitizedString(snapshot)` and null-safely posts (`device.mHandler?.post`) `applyBackfilledManufacturerName(gatt, clean)` so Binder advances `mGattQueue` immediately without blocking on SQLite.
     - `enqueueMissingPolarDisReads(device, context, gatt, hasPmd, hasPftp)`: conditionally enqueues only missing Polar `0x2A24`/`0x2A25` reads when `hasPmd || hasPftp` (preserving `REQ-CON-049.2` non-Polar isolation) and re-hydrates `PolarOfflineGattCoordinator.registerDeviceAliases(existingPolarId, address)` on cold-start reconnects whenever cached `existingPolarId != null` skips `0x2A25`.
2. **Minimal `MyBTLEDevice.java` & `BTLEHeartRateDevice.java` Wiring (Strict Monolith LoC Compliance)**:
   - `MyBTLEDevice.java` (`1,494 -> 1,498/1,500` LoC, `+4` LoC): wired `onDisCharacteristicRead` strictly inside `onCharacteristicRead`'s `GATT_SUCCESS && snapshot != null` branch (immutable cloned snapshot; rejects failed-read stale bytes), wired `enqueueMissingManufacturerRead` in `readNextCharacteristic()` immediately after `configureServices(gatt)` (covering all 7 BLE subclasses including `BTLERadarDevice`, behind telemetry CCCDs), and added `mGattLock`-guarded `applyBackfilledManufacturerName(gatt, name)` calling `setManufacturerName(name)`.
   - `BTLEHeartRateDevice.java` (`597 -> 583/1,500` LoC, `-14` LoC): delegated `configureServices()` Polar `0x2A24`/`0x2A25` backfill strictly before `super.configureServices(gatt)` to `BleDisReconnectBackfillHelper.enqueueMissingPolarDisReads(...)`.
   - `DevicesDatabaseManager.java` (`1,665/1,671` LoC) and `BTLERadarDevice.java` (`1,333/1,500` LoC): **untouched**.

## 2. Verification Evidence & Contract Compliance
- **Evidence Bundle**: [`docs/engineering/test_evidence/tickets/ATT-8723/evidence_summary.json`](../test_evidence/tickets/ATT-8723/evidence_summary.json) (SHA-256 `manifest.json`, `12` copied JUnit XML reports, and emulator smoke screenshot).
- **Tier 1 Targeted Unit & Contract Verification (`178/178` PASS, `0` failures, `0` errors, `0` skips, `0` new production Kotlin warnings)**:
  - `BleDisReconnectBackfillHelperTest` (`4/4`), `BleDisStringSanitizerTest` (`3/3`), `MyBTLEDeviceQueueTest` (`9/9`), `MyBTLEDeviceLifecycleTest` (`16/16`), `BTLEHeartRateDeviceTest` (`20/20`), `BTLERadarDeviceTest` (`64/64`), `BTSearchForNewDevicesEngineTest` (`22/22`), `PolarOfflineCapabilityStoreTest` (`8/8`), `PolarOfflineGattCoordinatorCoexistenceTest` (`14/14`), `ArchitectureConformanceContractTest` (`11/11`), `DetailedDesignParityContractTest` (`3/3`), `SystemLiveTrackingRequirementsContractTest` (`4/4`).
- **Live Emulator Verification (`emulator-5554`, `Pixel_8_Android_15`)**: Installed `feature/ATT-8723` debug APK inside `agent_emulator.py exec --ensure`, verified clean cold launch (`Status: ok`, `LaunchState: COLD`, `0` crashes in `logcat -b crash -d`), and visually audited [`ATT-8723_emulator_smoke.png`](img/ATT-8723_emulator_smoke.png) via `read_file` (`Rule 8.5`).
- **Governance & Localization**: `verify_requirement_governance.py` 100% PASS; `Localization Parity: N/A - 0 string changes`.
- **SWE.2 / SWE.3 / SYS Traceability**: Updated `docs/architecture.md` (`v1.0.39`), `docs/architecture/detailed_design/my_btle_device.md` (`v1.0.16`, `REQ-PRO-114`), `docs/system/sysreq_live_tracking.md` (`v1.2.32`), and `docs/system/system_software_traceability.md` (`v1.2.31`).
