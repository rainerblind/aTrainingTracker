# Stage 1 Analysis: ATT-8123 — `RadarLightQuickSwitchSheet` Action-Callback Safety & StVZO-Safe Deferred BLE GATT Registration

- **Ticket**: `ATT-8123` (`tier-a`, `2 SP`, `severity-major`, Bug, Parent Epic `ATT-8119`)
- **Target Release**: `V5.0.0` | **Branch**: `feature/ATT-8123` | **Localization Parity**: `N/A - 0 string changes`
- **Reserved IDs**: `REQ-CON-055` (`SYSREQ-TRK-002`, `SYSREQ-TRK-007`), `TST-CON-055`

## Root Cause Analysis (Codebase-Verified)
1. **Silent No-Op Callback Defaults & Unverified Modal Wrapper (`RadarLightQuickSwitchSheet.kt:72-131`)**:
   - Line 106 already forwards `onSnoozeSelected = onSnoozeSelected` from `RadarLightQuickSwitchSheet` to `RadarLightQuickSwitchSheetContent` (remediated since the `sprint/2026-46.7` review snapshot).
   - **Remaining Root Cause**: Both `RadarLightQuickSwitchSheet` (`:80-82`) and `RadarLightQuickSwitchSheetContent` (`:126-129`) still declare silent no-op defaults (`onAutoSelected = {}`, `onSnoozeSelected = {}`, `onResumeAudio = {}`, `onDismiss = {}`), allowing any caller or wrapper refactor to drop audio-snooze / resume / dismiss callbacks with zero compiler warning.
   - **Test Gap**: `RadarLightQuickSwitchSheetTest.kt` contains 9 call sites to `RadarLightQuickSwitchSheetContent` and **0** real Compose UI tests rendering `RadarLightQuickSwitchSheet(...)` itself (`testModalSheet_composableSignaturesExposeAllRequiredComponents` checks reflection only).
2. **Synchronous Pre-Read Registration & Unclamped Initial Light Command (`BTLERadarDevice.java:297-397, 576-627, 734-748`)**:
   - In `configureServices(BluetoothGatt)` (`:298-304`), `UUID_CHARACTERISTIC_MODEL_NUMBER` is queued asynchronously via `enqueueReadCharacteristic(modelChar)` on `MyBTLEDevice.mGattQueue`.
   - Immediately afterward (synchronously, before the GATT read callback fires), lines `:354` (RearVue 820 `6a4e2f00`) and `:376` (legacy `6a4e3202`, e.g., Garmin Varia `RTL516` StVZO) invoke `RadarLightController.registerRadarDevice(this, true, mIsStvzo)` while `mIsStvzo == false` and `hasHardwareBrake == false`, and lines `:361-365` / `:386-390` immediately enqueue the active light mode (`setLightMode(activeMode)` on RearVue; raw `enqueueWriteCharacteristic(lightChar, ...)` bypassing StVZO clamping on legacy `6a4e3202`).
   - Consequence: StVZO lamps (`RTL516`, `010-03180-10`, `RVR316`) are initially registered as non-StVZO flashing lights (and RearVue 820 transiently activates smartphone IMU `SmartBrakeLightDetector` before `hasHardwareBrake = true` arrives), and non-compliant modes (`DAY_FLASH`/`NIGHT_FLASH`) can be written to StVZO hardware on reconnect.
   - **Failure / Disconnect Races**: In `MyBTLEDevice.java:728-743`, failed characteristic reads (`status != GATT_SUCCESS`) still invoke `onOtherCharacteristicUpdate(gatt, characteristic)` with `getStringValue(0) == null`, while `readCharacteristic(...) == false` or `GATT_OP_TIMEOUT_MS` (`2500L`) never invokes `onCharacteristicRead`. Additionally, `BTLERadarDevice.disconnectFromGatt()` (`:576-604`) and `shutDown()` (`:607-627`) do not reset `mIsStvzo`/`mModelNumber` or invalidate stale GATT sessions against late `onOtherCharacteristicUpdate` callbacks.

## Change Impact Analysis (ASPICE SUP.10 / `REQ-PRO-064`)
- **Affected Requirements**: New `REQ-CON-055` (refining `REQ-CON-031`, `REQ-CON-047`, `REQ-TRK-064.1`; linked to `SYSREQ-TRK-002`, `SYSREQ-TRK-007`). Zero `SYS.2`/`SYS.3` spec changes.
- **Affected Architecture Elements & Verified Callers**:
  - `RadarLightQuickSwitchSheet.kt` (`:72-131`, `@Preview`s `:267, 289, 307`): remove `= {}` defaults from `onAutoSelected`, `onSnoozeSelected`, `onResumeAudio`, `onDismiss` on both `RadarLightQuickSwitchSheet` and `RadarLightQuickSwitchSheetContent`.
  - Sole production caller: `TrackingTabsScreen.kt:1144` (already passes all 5 callbacks explicitly — zero prod caller breakage).
  - Test callers to pass explicit callbacks: `RadarLightQuickSwitchSheetTest.kt` (`:86, 115, 143, 187, 216, 247, 292, 371, 389` + new wrapper Compose tests), `RadarLightModeGridTest.kt` (`:215, 269`).
  - `BTLERadarDevice.java` (`1,406` LoC; `ArchitectureConformanceContractTest` cap `<= 1,500` LoC — ~94 lines headroom; target `<= 1,470` LoC): defer light registration + StVZO-clamped initial mode dispatch until `UUID_CHARACTERISTIC_MODEL_NUMBER` read completes, fails (`null`/empty), or times out (`MODEL_NUMBER_READ_TIMEOUT_MS = 3000L`), with immediate registration when DIS/Model-Number characteristic is absent and strict disconnect/shutdown/stale-GATT guards.
- **Verification Impact & Test Surface**:
  - `RadarLightQuickSwitchSheetTest.kt` (add real `RadarLightQuickSwitchSheet(...)` modal wrapper Compose tests for all 4 snooze presets + `Resume Alerts` verifying `onSnoozeSelected`/`onResumeAudio` executes before `onDismiss`).
  - `BTLERadarDeviceTest.kt` (deferred StVZO `RTL516` & RearVue `010-03180-10` registration + StVZO `DAY_FLASH -> SOLID` clamping, absent-DIS immediate fallback, read-error `null`/empty fallback, timeout fallback, duplicate-registration guard, post-disconnect/shutdown late-callback drop).
  - Regression suites: `RadarLightModeGridTest`, `RadarLightControllerTest`, `ArchitectureConformanceContractTest`, `JavaCoreMigrationStrategyContractTest`.
- **Risk Assessment & System Invariants (`HIGH` — `tier-a`)**:
  - Invariants: `BTLERadarDevice.java <= 1,500` LoC; `BTLERadarDevice.isReceivingData()` untouched (scoped to `ATT-8109`); `WATCHDOG_TIMEOUT_MS == 3500L` preserved (`SystemLiveTrackingRequirementsContractTest`, `MeasuredNfrBenchmarkTest`); immediate registration preserved when DIS/Model-Number characteristic is absent (`BTLERadarDeviceTest:990-1033` exact FIFO queue order `[6a4e2f11 CCCD, 6a4e2f14 CCCD, 06 09 01 14]`); direct `onOtherCharacteristicUpdateForTesting` without prior `configureServices` preserved (`BTLERadarDeviceTest:497`); `0` `strings*.xml` edits.
