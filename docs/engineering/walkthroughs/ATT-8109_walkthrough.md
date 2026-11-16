# Stage 5 Walkthrough - ATT-8109: Instance-Isolated isReceivingData for BLE & ANT+ Radar Devices

**Ticket**: [ATT-8109](https://rainerblind.atlassian.net/browse/ATT-8109) (`severity-major`) | **Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) | **Sprint**: `Schicht 2026-11-16_02_Spät`  
**Target Release**: `V5.0.0` | **Branch**: `feature/ATT-8109` | **Commit SHA**: `9b3ea3f9`  
**Requirements**: `REQ-CON-044` (`REQ-CON-044.1`..`REQ-CON-044.3`) | **Test Spec**: `TST-CON-044` (`TST-CON-044.1`..`TST-CON-044.4`)  
**Author**: AI Agent 1 (Implementer, Lane 1 Flash) | **Date**: 2026-11-16  

---

## 1. Executive Summary & Problem Resolution
In multi-device pairing setups (e.g. multiple paired BLE radars such as `RearVue8` and `ATT-Rdr`), connecting one radar caused `DeviceManager` and the Start screen header to falsely display **all** paired BLE radars as connected and active.
* **Root Cause**: `BTLERadarDevice.isReceivingData()` in `BTLERadarDevice.java` checked only the global transport singleton `RadarStateRepository.getState(RadarSource.BLE).getConnectionState() == CONNECTED`, bypassing `MyBTLEDevice`'s per-instance GATT state (`super.isReceivingData()` / `mState == State.CONNECTED_WITH_SERVICE`).
* **Fix**:
  1. `BTLERadarDevice.java`: Qualified `isReceivingData()` with `super.isReceivingData() && RadarStateRepository.getState(RadarSource.BLE).getConnectionState() == RadarConnectionState.CONNECTED`. Disconnected peer BLE radars now return `false`, while fail-safe watchdog starvation detection (`TST-CON-041.6`) is preserved.
  2. `ANTBikeRadarDevice.kt`: Added instance-level packet reception timestamp (`lastPacketReceivedRealtimeMs`), reset on watchdog timeout and shutdown, evaluating `SystemClock.elapsedRealtime() - lastPacketReceivedRealtimeMs <= WATCHDOG_TIMEOUT_MS (3500L)` alongside `RadarSource.ANT_PLUS`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Living Doc Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-CON-044.1` (*Instance BLE Gating*) | `TST-CON-044.1` | Automated Unit Test | **PASSED** | `[Verified]` |
| `REQ-CON-044.2` (*Watchdog Fail-Safe*) | `TST-CON-044.2` | Automated Unit Test | **PASSED** | `[Verified]` |
| `REQ-CON-044.3` (*Instance ANT+ Gating*) | `TST-CON-044.3` | Automated Unit Test | **PASSED** | `[Verified]` |
| `REQ-CON-041` / Invariants | `TST-CON-044.4` | Targeted Regression & Governance | **PASSED** | `[Verified]` |

---

## 3. Automated Test Evidence (`82/82 PASS`)
Executed via `./gradlew testDebugUnitTest --tests "*BTLERadarDeviceTest*" --tests "*ANTBikeRadarDeviceTest*" --tests "*RadarStateRepositoryTest*" --max-workers=2`:
* **`BTLERadarDeviceTest.kt`** (56/56 tests PASSED):
  - `testMultiInstanceBleRadar_disconnectedDeviceReturnsFalse` (`// TST-CON-044.1`): PASSED.
  - `testIsReceivingData_returnsFalseOnWatchdogTimeoutEvenIfGattConnected` (`// TST-CON-044.2`): PASSED.
  - All 54 existing V1/V2 parsing, service configuration, and reconnect tests: PASSED.
* **`ANTBikeRadarDeviceTest.kt`** (21/21 tests PASSED):
  - `testMultiInstanceAntRadar_silentDeviceReturnsFalse` (`// TST-CON-044.3`): PASSED.
  - `testIsReceivingData_isolatedToAntPlusSource`: PASSED.
  - All 19 existing target parsing, watchdog timeout, and channel event tests: PASSED.
* **`RadarStateRepositoryTest.kt`** (5/5 tests PASSED): All multi-source arbitration tests PASSED.

---

## 4. Verification & Clean-Room Governance Audit
* `python3 tools/verify_requirement_governance.py`: **PASS** (100% living doc parity, 0 ungrounded requirements, 0 code orphans).
* `python3 tools/verify_requirement_governance.py --check-code-orphans`: **PASS** (0 code-to-shard orphans, 2357 files scanned).
* `python3 tools/verify_requirement_governance.py --check-req-description`: **PASS** (100% description coverage).
* `python3 tools/verify_requirement_governance.py --verify-sys-trace --check-reverse-sys-trace`: **PASS** (100% bidirectional set equality to `SYSREQ-TRK-001`, `SYSREQ-TRK-002`).
* `ANT-Android-SDKs` submodule: clean & untouched at `0e8cea714a231fb9565170d4ed01eafa868f9798`.
