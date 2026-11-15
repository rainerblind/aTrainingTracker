# Stage 3 Implementation Plan: ATT-7923

Target Release: V5.0.0

## SWE.2 Architecture & Threading Design
- **Shared GATT Arbitration (`PolarOfflineGattCoordinator.kt`)**: Extracts Polar 8-hex ID extraction, active Polar HRM DB lookup, `MyBTLEDevice.pauseGattOperations()` / `finally { resumeGattOperations() }` yield, and `PolarBleApi` feature-readiness awaiting so `PolarH10OfflineController` and `PolarVeritySenseOfflineController` share one tested arbitration path without code duplication.
- **Capability Store & Discovery (`PolarOfflineCapabilityStore.kt`, `BTSearchForNewDevicesEngine.java`, `BluetoothConstants.java`)**: Persists detected `FEATURE_POLAR_OFFLINE_RECORDING` / PFTP (`0000FEEE-...`) + PMD (`FB005C80-...`) capability per MAC in `SharedPreferences` while keeping `Devices.db` at `DB_VERSION = 13` (reusing `polarH10OfflineRecording` `DEFAULT 1`).
- **Optical Offline Controller (`PolarVeritySenseOfflineController.kt`)**: Runs non-blockingly on an injected IO dispatcher (`SupervisorJob` scope), enforcing API >= 33 and `isArmed` CAS fringe-flap invariants (`onWorkoutStarted` no-ops if already armed; mid-workout `0x180D` flaps never interrupt PFTP). Exposes `stopAndFetchOfflineHr()` and `removeOfflineRecord()` for `ATT-7924`.
- **Service Reachability (`TrackerService.java`)**: Surgical calls to `PolarVeritySenseOfflineController.setSessionStartTimeMs()`, `onWorkoutStarted(this)`, and `onWorkoutStopped(this)` beside `PolarH10OfflineController`.

## UI Consistency (`docs/design_guidelines.md`)
- `EditDeviceDialog.kt` preserves Material 3 `Card` + `AttFusedToggleRow` (no raw `Switch`, no `FilterChip`, no nested `ElevatedCard`): shows H10 HR+HRV copy when `isPolarH10Device(data)` and optical HR-only copy (`device_polar_verity_backup_recording_title` / `device_polar_verity_backup_recording_desc`) when `isPolarVeritySenseDevice(data)`, disabled with Android 13 hint below API 33 and default ON on API 33+. Zero decorative emojis across all 9 `strings_devices.xml` locales.

## Atomic Implementation Steps
| Step | Target Files | Change Summary | Verification |
| :--- | :--- | :--- | :--- |
| 1 | `PolarOfflineGattCoordinator.kt`, `PolarH10OfflineController.kt` | Extract shared GATT yield, Polar ID, device lookup & feature await helpers; keep H10 behavior identical | `PolarH10OfflineControllerTest` |
| 2 | `BluetoothConstants.java`, `BTSearchForNewDevicesEngine.java`, `PolarOfflineCapabilityStore.kt` | Detect PFTP+PMD Polar offline capability in discovery & SDK feature callback; recognize Verity Sense/OH1 names | Unit test in `PolarVeritySenseOfflineControllerTest` |
| 3 | `PolarVeritySenseOfflineController.kt` | Implement `onWorkoutStarted` (API>=33, `isArmed`, `setLocalTime`, `getOfflineRecordingStatus`, stop active `HR`, `startOfflineRecording(HR, null, null)`, `SYSTEM_BUSY` non-blocking handling), `onWorkoutStopped`, `stopAndFetchOfflineHr`, `removeOfflineRecord` | `PolarVeritySenseOfflineControllerTest` |
| 4 | `TrackerService.java` | Wire `setSessionStartTimeMs`, `onWorkoutStarted`, `onWorkoutStopped` cleanly | `TrackerServiceStateMachineCharacterisationTest` |
| 5 | `EditDeviceDialog.kt`, `res/values*/strings_devices.xml` (9 locales) | Generalize `PolarBackupRecordingSection` with `isPolarVeritySenseDevice(data)` and 9-locale HR-only copy | `EditDevicePolarBackupTest`, `TranslationParityTest`, `emulator-5554` screenshot |
| 6 | `docs/architecture.md`, `req_settings_profiles.md`, `tst_settings_profiles.md` | Document `PolarOfflineGattCoordinator` & `PolarVeritySenseOfflineController` in §3.4; mark `REQ-SET-108` / `TST-SET-110` `[Implemented]` | `verify_requirement_governance.py`, `:app:lintDebug`, Detekt |

## Invariants & Traceability
- `Devices.db` stays `DB_VERSION = 13` (`DevicesDatabaseManagerMigrationV13Test`).
- Every requirement `REQ-SET-108.1..108.3` maps 1:1 to `TST-SET-110.1..110.5` across Steps 1–6.
