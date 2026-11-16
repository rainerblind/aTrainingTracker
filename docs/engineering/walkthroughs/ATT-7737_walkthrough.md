# Walkthrough - ATT-7737: RearVue 820 Tail Light Control & State Reconciliation

Resolved why selecting a radar tail light mode (e.g., Night Flash) in `EditDeviceDialog` failed to control the RearVue 820 physical light and snapped back to `Off`.

## 1. Requirements & Acceptance Fulfilled
- **REQ-CON-031.1 (`6a4e2f11` CCCD Handshake & Indications)**: `BTLERadarDevice.configureServices()` now calls `enableCharacteristicSubscription()` on `6a4e2f11` (`WRITE, INDICATE`) before `6a4e2f14` (`NOTIFY`) and before mid-ride `setLightMode()`. `onOtherCharacteristicUpdate()` decodes `6a4e2f11` indications via `parseControlIndicationMode()` and guards `6a4e2f14` slot notifications against stale `OFF` overwrites while `mPendingLightMode` is active.
- **REQ-CON-031.2 (Property-Aware Write Type & Error Propagation)**: `BTLERadarDevice.resolveControlWriteType()` and `MyBTLEDevice.WriteCharacteristicOp.execute()` select `WRITE_TYPE_DEFAULT` whenever `PROPERTY_WRITE` is set, reserving `WRITE_TYPE_NO_RESPONSE` strictly for `PROPERTY_WRITE_NO_RESPONSE`-only characteristics (`6a4e3202`). `MyBTLEDevice.onCharacteristicWrite()` propagates status to `BTLERadarDevice.onCharacteristicWriteCompleted()`, initiating `createBond()` on auth/encryption failure and reconciling via `RadarLightController.onHardwareLightModeWriteFailed()`.
- **REQ-CON-031.5 (`EditDeviceDialog` & StVZO Parity)**: `RadarLightController.setManualLightMode()` aligns StVZO-pruned `targetMode` and `_currentMode.value` with `RadarStateRepository`; `EditDeviceDialog.kt` UI layout is unchanged (`RadarLightControlSection` binds directly to `RadarLightController` flows).
- **REQ-CON-031.6 (Simulator Parity)**: `tools/ble_radar_simulator.py` (`6a4e2f11`) now exposes `['write', 'indicate']`, enforces `StartNotify()` before accepting writes, and emits `6a4e2f11` indications.

## 2. Modified Code & Test Scope (Stage 4 SHA `cac2979b70547c772434761cfc40009e3f76883a`)
- Production: `BTLERadarDevice.java`, `MyBTLEDevice.java`, `RadarLightController.kt`, `tools/ble_radar_simulator.py` (`EditDeviceDialog.kt` layout unchanged -> non-UI ticket, no emulator deploy).
- Tests: `BTLERadarDeviceTest.kt`, `MyBTLEDeviceQueueTest.kt`, `RadarLightControllerTest.kt`, `tools/test_ble_radar_simulator.py`.

## 3. Verification Results (Tier 1)
- **TST-CON-031 (`tools/test_ble_radar_simulator.py`)**: **PASS** (`python3 -m unittest tools/test_ble_radar_simulator.py` -> **8/8 passed**, verified on Stage 4 SHA and re-run in Stage 5).
- **TST-CON-031 (`./gradlew :app:testDebugUnitTest --tests BTLERadarDeviceTest --tests MyBTLEDeviceQueueTest --tests RadarLightControllerTest`)**: **PASS** (Stage 4 clean-room run on unchanged Kotlin/Java code SHA `cac2979b`: **98/98 passed** — `BTLERadarDeviceTest` **55/55**, `MyBTLEDeviceQueueTest` **8/8**, `RadarLightControllerTest` **35/35**, 0 failed, 0 skipped; no redundant Gradle re-run per `Rule 7.5`).
- **Governance & Living Docs (`REQ-CON-031`, `TST-CON-031`)**: Promoted `[Implemented]` -> `[Verified]` with `--sync-indexes` and verified via `verify_requirement_governance.py`.
