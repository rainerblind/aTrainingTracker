# Stage 3: Implementation Plan — ATT-7737: RearVue 820 BLE Tail Light Control Fails & Reverts to Off

**Ticket**: `ATT-7737` (`2 SP`) | **Parent Epic**: `ATT-1286` | **Target Release**: `V5.0.0` | **Branch**: `feature/ATT-7737`
**Risk Classification (`Rule 7.1`)**: **`HIGH-RISK` (Binary BLE GATT Protocol)** — Flash (`agent2`) reviews Gate 3 ONLY; Pro (`agent1`) executes Stage 4.
**Requirements**: `REQ-CON-031.1`, `REQ-CON-031.2`, `REQ-CON-031.5`, `REQ-CON-031.6` | **Tests**: `TST-CON-031.1`, `TST-CON-031.2`, `TST-CON-031.4`, `TST-CON-031.5`, `TST-CON-031.6`

## 1. Problem Description & Traceability
Physical RearVue 820 light control failed because `BTLERadarDevice` did not subscribe `6a4e2f11` `(WRITE, INDICATE)` `0x2902` CCCD before writes, forced `WRITE_TYPE_NO_RESPONSE` (`60e14c9b`), and `MyBTLEDevice.onCharacteristicWrite()` discarded non-zero `status` while optimistic `updateHardwareLightMode()` updates snapped back to `OFF` on `6a4e2f14` status frames after `1000 ms`.

## 2. System Invariants & Preserved Behavior
1. Serialized GATT FIFO queue in `MyBTLEDevice`: `6a4e2f11` CCCD write MUST precede initial `setLightMode()` write.
2. Legacy `6a4e3202` (`PROPERTY_WRITE_NO_RESPONSE`-only) preserves `WRITE_TYPE_NO_RESPONSE`.
3. `ECHO_GUARD_WINDOW_MS` (`1000L` ms), StVZO mode filtering, `ANTBikeLightDevice.kt`, `ANT-Android-SDKs` (`160000`), and 9-locale parity remain untouched.
4. `Rule 8.5` / `Rule 23` UI Consistency: **No UI changes.** `EditDeviceDialog.kt` (`RadarLightControlSection`) and `RadarSettingsSection.kt` layout/widgets stay untouched.

## 3. Proposed Architectural Changes
- **`MyBTLEDevice.java`**: Add `protected void onCharacteristicWriteCompleted(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status)` hook before `onGattOperationCompleted()`; verify `WriteCharacteristicOp.execute()` prefers `WRITE_TYPE_DEFAULT` when `(properties & PROPERTY_WRITE) != 0` and uses `WRITE_TYPE_NO_RESPONSE` strictly when `(properties & PROPERTY_WRITE) == 0 && (properties & PROPERTY_WRITE_NO_RESPONSE) != 0`.
- **`BTLERadarDevice.java`**: Extract property-aware `resolveWriteType(BluetoothGattCharacteristic)`; in `configureServices()` subscribe `setCharacteristicNotification(controlWriteChar, true)` + `0x2902` CCCD (`ENABLE_INDICATION_VALUE` / `ENABLE_NOTIFICATION_VALUE`) on `6a4e2f11` before re-applying active mode; decode `6a4e2f11` indications in `onOtherCharacteristicUpdate()`; handle non-`GATT_SUCCESS` in `onCharacteristicWriteCompleted()` (including `GATT_INSUFFICIENT_AUTHENTICATION` `createBond()` attempt and clearing unconfirmed optimistic override instead of snapping back silently).
- **`RadarLightController.kt` + `EditDeviceDialog.kt` Audit**: Add `onHardwareLightModeWriteFailed(failedMode: RadarLightMode, rollbackMode: RadarLightMode?)` (or track confirmed vs pending light mode without snapping prematurely to `OFF`); audit `EditDeviceDialog.kt` (`L1112-1297`) reactive `StateFlow` bindings (`isAutoMode`, `currentMode`, `isLightControlAvailable`, `isStvzo`) and zero draft-dirtying.
- **`tools/ble_radar_simulator.py`**: Update `RadarControlWriteCharacteristic` (`6a4e2f11`) properties to `['write', 'indicate']`, implement `StartNotify()`/`StopNotify()`, reject writes until CCCD indications are enabled, and emit `6a4e2f11` indication feedback on write.

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (`MyBTLEDevice.java`)**: Expose `onCharacteristicWriteCompleted(gatt, characteristic, status)` from `mGattCallback.onCharacteristicWrite()` with error logging on `status != GATT_SUCCESS`; verify `WriteCharacteristicOp` write-type selection logic.
2. **Step 2 (`BTLERadarDevice.java`)**: Add `resolveWriteType()`; subscribe `6a4e2f11` CCCD in `configureServices()` strictly before active-mode re-apply; handle `UUID_CHARACTERISTIC_RADAR_CONTROL_WRITE` updates in `onOtherCharacteristicUpdate()`; override `onCharacteristicWriteCompleted()` to handle `status != GATT_SUCCESS`, `GATT_INSUFFICIENT_AUTHENTICATION` bonding, and confirmed-mode reconciliation (`mConfirmedLightMode`).
3. **Step 3 (`RadarLightController.kt` & `EditDeviceDialog.kt` Audit)**: Reconcile failed/pending BLE light write state cleanly without premature `OFF` snap-back; confirm `EditDeviceDialog.kt` bindings require no layout change.
4. **Step 4 (`tools/ble_radar_simulator.py`)**: Change `6a4e2f11` flags to `['write', 'indicate']`, enforce `StartNotify()` CCCD check on `WriteValue()`, and emit `6a4e2f11` indication confirmation.
5. **Step 5 (Unit Tests — tag `TST-CON-031`, `TST-CON-031.1`, `TST-CON-031.2`, `TST-CON-031.4`, `TST-CON-031.5`, `TST-CON-031.6` in KDoc/docstrings for `--check-verified-evidence`)**:
   - `BTLERadarDeviceTest.kt` (`TST-CON-031`, `TST-CON-031.1`, `TST-CON-031.2`): `6a4e2f11` CCCD queued before `setLightMode()`, `WRITE_TYPE_DEFAULT` on `(WRITE|INDICATE)`, `6a4e3202` `WRITE_TYPE_NO_RESPONSE`, `6a4e2f11` indication decoding, non-zero `status` handling.
   - `MyBTLEDeviceQueueTest.kt` (`TST-CON-031.2`): `onCharacteristicWriteCompleted()` callback forwarding and `WriteCharacteristicOp` write-type rules.
   - `RadarLightControllerTest.kt` (`TST-CON-031`, `TST-CON-031.4`, `TST-CON-031.5`): confirmed/rollback mode state without `OFF` snap-back, workout start/stop lifecycle, `EditDeviceDialog` binding coherence.
   - `tools/test_ble_radar_simulator.py` (`TST-CON-031.6`): `6a4e2f11` properties `['write', 'indicate']` and write rejection prior to `StartNotify()`.

## 5. Exact Tier 1 Verification & Rollback Plan
- **Tier 1 Commands**:
  - `python3 -m unittest tools/test_ble_radar_simulator.py`
  - `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.BTLERadarDeviceTest" --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.MyBTLEDeviceQueueTest" --tests "com.atrainingtracker.banalservice.devices.radar.RadarLightControllerTest"`
  - `python3 tools/verify_requirement_governance.py`
- **Rollback**: Revert commits on `feature/ATT-7737` cleanly; physical RearVue 820 + Pixel 10 test in Ceremony 2 (`Rule 7.6`).
