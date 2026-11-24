# Stage 5 Walkthrough & Verification Report — ATT-8996

**Ticket**: `ATT-8996` (`2 SP`, `tier-a`, `severity-minor`, Epic `ATT-5910`) | **Target Release**: `V5.0.0` | **Branch**: `feature/ATT-8996` | **Gate 3**: `ATT-9687` (`PASS`) | **Req / Test**: `REQ-CON-060` / `TST-CON-060` (`[Verified]`) | **Date**: 2026-11-24

## 1. Summary of Implemented Changes
1. **Shared Pure-JVM BLE DIS Sanitizer (`BleDisStringSanitizer.kt`, `154/1,500` LoC)**:
   - Stateless `@JvmStatic` utility in `com.atrainingtracker.banalservice.devices.bluetooth_le` (reusable by follow-up `ATT-8723`) providing `sanitize(raw)` (strips all `'\u0000'` bytes, trims whitespace, returns `null` if blank, preserves UTF-8), `readSanitizedString(characteristic)` (`getStringValue(0)` with raw UTF-8 `value` fallback), `containsNul(value)`, and idempotent `sanitizeExistingDevicesTable(db)` (collects dirty rows and closes `Cursor` via `.use {}` strictly before issuing `db.update(...)` writes).
2. **Complete BLE GATT / GAP / Polar DIS Read-Site Wiring**:
   - `BTSearchForNewDevicesEngine.java`: sanitized `0x2A29` Manufacturer Name, `0x2A24` Model Number, `0x2A25` Serial Number, and `gatt.getDevice().getName()` (GAP local name).
   - `BTLEHeartRateDevice.java`: sanitized `0x2A24` & `0x2A25` before Polar store persistence and alias registration.
   - `BTLERadarDevice.java`: sanitized `0x2A24` Model Number (preventing `"\u0000"` from bypassing `!model.isEmpty()`).
   - `PolarPmdStreamCoordinator.kt`: sanitized `key` & `value` in `recordDisCharacteristic()` (backing `PolarOnlineStreamingBridge` DIS callbacks).
   - `MyBTLEDevice.java`: **untouched** (`1,494/1,500` LoC).
3. **`PolarOfflineCapabilityStore` Self-Healing & `PolarOfflineGattCoordinator` Hardening**:
   - Replaced Kotlin `.trim()` with `BleDisStringSanitizer.sanitize(...)` across all write/compare/read paths in `PolarOfflineCapabilityStore.kt` (`505/1,500` LoC) and added read-time SharedPreferences self-healing in `getModelNumber()` & `getPolarDeviceId()`.
   - Updated `PolarOfflineGattCoordinator.kt` (`1,487/1,500` LoC) `normalizeId()`, `isPolarH10Name()`, `isPolarVeritySenseName()`, and `hasExplicitNonPolarManufacturer()` so `"\u0000"` is treated as blank rather than an explicit non-Polar manufacturer.
4. **`Devices.db` Write/Read Sanitization & Idempotent v14 -> v15 Migration**:
   - `DevicesDatabaseManager.java` (`1,665/1,671` LoC): sanitized `name` & `manufacturerName` across writes (`insertNewBluetoothDeviceIntoDB`, `insertNewAntDeviceIntoDB`, `setManufacturerName`) and all cursor reads; bumped `DevicesDbHelper.DB_VERSION = 15`, added `case 15:` in `onCreate()` (100% schema parity), and wired `BleDisStringSanitizer.sanitizeExistingDevicesTable(db)` into `onUpgrade(oldVersion <= 14 && newVersion >= 15)` (automatically synced in `DatabaseSchemaRegistry`).
   - `RawDeviceDataProvider.kt` (retaining `?: ""` non-null fallback) and `DeviceDataRepository.kt` (`NAME` updates) sanitize cursor reads and writes.

## 2. Verification Evidence & Contract Compliance
- **Evidence Bundle**: [`docs/engineering/test_evidence/tickets/ATT-8996/evidence_summary.json`](../test_evidence/tickets/ATT-8996/evidence_summary.json) (SHA-256 `manifest.json` + copied JUnit XML reports).
- **Targeted Unit, Robolectric, Migration & Contract Verification (`196/196` PASS, `0` failures, `0` skips, `0` new production warnings)**:
  - `BleDisStringSanitizerTest` (`3/3`), `DevicesDatabaseManagerMigrationV15Test` (`3/3`), `DevicesDatabaseManagerMigrationV14Test` (`3/3`), `DevicesDatabaseManagerMigrationV13Test` (`4/4`), `DevicesDatabaseManagerMigrationV12Test` (`3/3`), `DevicesDatabaseManagerMigrationTest` (`4/4`), `DevicesDatabaseManagerTest` (`17/17`), `BTSearchForNewDevicesEngineTest` (`22/22`), `BTLEHeartRateDeviceTest` (`19/19`), `BTLERadarDeviceTest` (`64/64`), `PolarOfflineCapabilityStoreTest` (`8/8`), `PolarPmdStreamCoordinatorTest` (`12/12`), `PolarOfflineGattCoordinatorCoexistenceTest` (`14/14`), `DatabaseSchemaRegistryParityContractTest` (`2/2`), `SystemLiveTrackingRequirementsContractTest` (`4/4`), `ArchitectureConformanceContractTest` (`11/11`), `DetailedDesignParityContractTest` (`3/3`).
- **Live Emulator Verification (`emulator-5554`, `Pixel_8_Android_15`)**: Installed `feature/ATT-8996` debug APK, verified cold launch of `MainActivityWithNavigation` (`0` crashes/errors in `logcat`), verified live `Devices.db` `PRAGMA user_version == 15` with clean `Devices4` rows, and visually audited [`ATT-8996_emulator_smoke.png`](img/ATT-8996_emulator_smoke.png) via `read_file` (`Rule 8.5`).
- **Governance & Localization**: `verify_requirement_governance.py` 100% PASS; `Localization Parity: N/A - 0 string changes`.
- **SWE.2 / SYS Traceability**: Registered `BleDisStringSanitizer` in `docs/architecture.md` (`v1.0.38`) and grounded `BleDisStringSanitizer.kt` under `SYSREQ-TRK-001` in `docs/system/sysreq_live_tracking.md` (`v1.2.31`) & `docs/system/system_software_traceability.md` (`v1.2.30`).
- **Stage 6 Bounce 1/5 Heal (`c7474457`)**: Removed redundant `?.` safe call on non-null `Cursor` in `BleDisStringSanitizer.kt:101` (`0` Kotlin compiler warnings on `:app:compileDebugKotlin`), cleaned stale XMLs, re-executed all `17` targeted suites (`196/196` PASS, `0` fail, `0` skip), and regenerated `docs/engineering/test_evidence/tickets/ATT-8996/` with all `17` authentic JUnit XMLs + `ATT-8996_emulator_smoke.png` + SHA-256 `manifest.json`.
