# Stage 3 Implementation Plan: ATT-8123

- **Ticket**: `ATT-8123` (`tier-a`, `2 SP`, Target Release `V5.0.0`, `REQ-CON-055` / `TST-CON-055`)
- **Strict Code Lock**: Zero edits under `app/src/` until Gate 3 (`agent2`) passes (`check-gate`).

## Atomic Implementation Steps
1. **Step 1 — Remove Silent Action-Callback Defaults in `RadarLightQuickSwitchSheet.kt` (`REQ-CON-055.1`)**:
   - In `RadarLightQuickSwitchSheet` (`:72-85`) and `RadarLightQuickSwitchSheetContent` (`:118-131`), remove `= {}` default values from `onAutoSelected`, `onSnoozeSelected`, `onResumeAudio`, and `onDismiss` (keep non-callback display defaults `isAutoMode`, `isLightControlSupported`, `snoozeState`, `modifier`).
   - Verify `RadarLightQuickSwitchSheet` explicitly forwards all 5 action callbacks (`onModeSelected`, `onAutoSelected`, `onSnoozeSelected`, `onResumeAudio`, `onDismiss`) to `RadarLightQuickSwitchSheetContent`.
   - Update the 3 private `@Preview` composables (`:267, 289, 307`) to pass explicit empty lambdas.
   - Verify sole production caller `TrackingTabsScreen.kt:1144` compiles unchanged (already passes all 5 callbacks).
2. **Step 2 — Defer Light Registration & Initial Mode Dispatch in `BTLERadarDevice.java` (`REQ-CON-055.2`–`.4`)**:
   - Add `public static final long MODEL_NUMBER_READ_TIMEOUT_MS = 3000L;` and state fields: `mHasHardwareBrakeSensor`, `mAwaitingModelNumberForRegistration`, `mDisconnectedSinceConfigure`, `mActiveRegistrationGatt`, plus `mModelNumberFallbackRunnable` (with `@VisibleForTesting public Runnable getModelNumberFallbackRunnableForTesting()`).
   - Add private helper `completeLightControlRegistration(boolean isStvzo, boolean hasHardwareBrake, boolean reapplyActiveMode)`:
     - Guards `if (mIsShutDown || mDisconnectedSinceConfigure) return;`.
     - Cancels `mModelNumberFallbackRunnable` on `mHandler`, clears `mAwaitingModelNumberForRegistration = false`, updates `mIsStvzo` / `mHasHardwareBrakeSensor`.
     - Calls `RadarLightController.registerRadarDevice(this, mIsLightControlSupported, isStvzo, hasHardwareBrake)`.
     - If `reapplyActiveMode && mIsLightControlSupported`: reads `activeMode = RadarLightController.getCurrentMode().getValue()`; if `activeMode != RadarLightMode.OFF`, computes `safeMode = (isStvzo && !activeMode.isStvzoCompliant()) ? RadarLightMode.SOLID : activeMode`, calls `setLightMode(safeMode)`, and if `safeMode != activeMode` (or legacy path where `setLightMode` does not call `updateHardwareLightMode`) syncs `RadarLightController.updateHardwareLightMode(safeMode)` when clamped.
   - In `configureServices(BluetoothGatt gatt)`:
     - Reset `mDisconnectedSinceConfigure = false`, `mActiveRegistrationGatt = gatt`, `mIsStvzo = false`, `mHasHardwareBrakeSensor = false`, `mModelNumber = null`, cancel prior `mModelNumberFallbackRunnable`.
     - Track `boolean awaitingModelRead = (modelChar != null)` when enqueuing `UUID_CHARACTERISTIC_MODEL_NUMBER`.
     - Keep RearVue/Legacy CCCD subscriptions in place (safe read/notify setup), then:
       - If `mIsLightControlSupported && awaitingModelRead`: set `mAwaitingModelNumberForRegistration = true` and post `mModelNumberFallbackRunnable` delayed `MODEL_NUMBER_READ_TIMEOUT_MS` (do NOT register or write light mode yet).
       - Else: call `completeLightControlRegistration(false, false, mIsLightControlSupported)` immediately (preserves no-DIS FIFO queue order and radar-only registration).
   - In `onOtherCharacteristicUpdate(gatt, characteristic)` for `UUID_CHARACTERISTIC_MODEL_NUMBER`:
     - Guard `if (mIsShutDown || mDisconnectedSinceConfigure || (mActiveRegistrationGatt != null && gatt != mActiveRegistrationGatt)) return;`.
     - If `model != null && !model.isEmpty()`: update `mModelNumber`, compute `stvzo`/`hasHardwareBrake`, capture `wasPending = mAwaitingModelNumberForRegistration`, and call `completeLightControlRegistration(stvzo, hasHardwareBrake, wasPending)`.
     - Else if `mAwaitingModelNumberForRegistration`: immediately fall back via `completeLightControlRegistration(false, false, true)`.
   - In `disconnectFromGatt()` & `shutDown()`: remove `mModelNumberFallbackRunnable`, set `mDisconnectedSinceConfigure = true`, `mActiveRegistrationGatt = null`, `mAwaitingModelNumberForRegistration = false`, `mIsStvzo = false`, `mHasHardwareBrakeSensor = false`, `mModelNumber = null`.
   - Keep `BTLERadarDevice.java <= 1,500` LoC (`~1,460` LoC expected).
3. **Step 3 — Unit & Compose UI Tests (`TST-CON-055.1`–`.4`)**:
   - Update omitted lambda arguments in `RadarLightQuickSwitchSheetTest.kt` (9 sites) and `RadarLightModeGridTest.kt` (2 sites), and add modal wrapper Compose tests invoking `RadarLightQuickSwitchSheet(...)` directly for all 4 snooze presets + `Resume Alerts` verifying callback order before `onDismiss()`.
   - Add targeted `BTLERadarDeviceTest.kt` tests covering: (1) deferred StVZO `RTL516` legacy registration + `DAY_FLASH -> SOLID` clamp, (2) deferred StVZO RearVue `010-03180-10` registration (`isStvzo = true`, `hasHardwareBrakeSensor = true`) + `SOLID` clamp, (3) non-StVZO `RTL515` registration preserving `DAY_FLASH`, (4) read-error `null`/empty immediate fallback, (5) timeout fallback + idempotency (no duplicate dispatch), (6) post-disconnect & post-shutdown late model-number / timeout drop.
4. **Step 4 — Verification & Evidence**:
   - Run targeted tests (`--rerun`): `RadarLightQuickSwitchSheetTest`, `RadarLightModeGridTest`, `BTLERadarDeviceTest`, `RadarLightControllerTest`, `ArchitectureConformanceContractTest`, `JavaCoreMigrationStrategyContractTest`, `SystemLiveTrackingRequirementsContractTest`.
   - Run live `emulator-5554` install/launch smoke check (`Rule 8.5` screenshot), promote `REQ-CON-055`/`TST-CON-055` to `[Verified]`, run `verify_requirement_governance.py`, generate `auto_gate.py --write-bundle`, and advance to `Final Review (Agent)`.
