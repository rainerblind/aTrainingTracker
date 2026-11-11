# Stage 5: Walkthrough & Verification - ATT-5103: Support ANT+ Bike Lights profile (Device Type 0x23) for radar tail light mode control and workout auto-switch

**Ticket**: [ATT-5103](https://rainerblind.atlassian.net/browse/ATT-5103)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar: BLE & ANT+ Acquisition, Live Edge Overlay, Audio/Haptic Alerts & Traffic Analytics*)  
**Target Release**: `v2026.46`  
**Active Sprint**: `sprint/2026-46.2`  
**Requirement Mapping**: `REQ-CON-033`  
**Test Mapping**: `TST-CON-033`  
**Branch**: `feature/ATT-5103`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-11  

---

## 1. Executive Summary & Verification Overview

This walkthrough documents the full construction, verification, and regression testing for **ATT-5103**:
1. **ANT+ Bike Lights Constants (`REQ-CON-033.1`, `AntChannelHelper.kt`)**:
   - Added standard profile constants: `BIKE_LIGHT_DEVICE_TYPE = 35` (`0x23`), `BIKE_LIGHT_RF_FREQUENCY = 57` (2457 MHz), `BIKE_LIGHT_CHANNEL_PERIOD = 4084` counts (~8.02 Hz).
2. **Unified Radar Light Controller Abstraction (`REQ-CON-033.4`, `RadarLightDeviceClient.kt`, `RadarLightController.kt`)**:
   - Introduced `RadarLightDeviceClient` interface decoupling `RadarLightController` from `BTLERadarDevice`.
   - Conformed both `BTLERadarDevice` and `ANTBikeLightDevice` to `RadarLightDeviceClient`.
   - Retained complete feature parity across BLE and ANT+ for Cockpit quick-toggle sheet (`ATT-5102`) and workout auto-switch (`ATT-5102`).
3. **ANT+ Bike Lights Profile Driver (`REQ-CON-033.2`, `REQ-CON-033.3`, `ANTBikeLightDevice.kt`)**:
   - Decodes broadcast Data Page `0x01` (Main Light State: light index, light type, current mode, battery state) and Data Page `0x02` (Capabilities bitmask).
   - Implements mandatory 3-step state machine:
     - **Connect Command (Page `0x21` / 33)**: Transmits acknowledged Page `0x21` when Page `0x01` reports `Light Index == 0` (unassociated), assigning `Light Index = 1`, phone controller ID, and light type `2` (Tail Light).
     - **Mode Settings Command (Page `0x22` / 34)**: Once association is confirmed (`Light Index == 1`), transmits acknowledged Page `0x22` with target mode byte (`OFF`, `SOLID_HIGH`, `SOLID_LOW/PELOTON`, `DAY_FLASH`, `NIGHT_FLASH`) and incrementing sequence counter.
     - **Disconnect Command (Page `0x20` / 32)**: Transmits acknowledged Page `0x20` on session teardown to cleanly release the light.
4. **Companion Channel Model & Isolation (`REQ-CON-033.1`, `REQ-CON-033.5`, `ANTBikeRadarDevice.kt`)**:
   - `ANTBikeRadarDevice` automatically instantiates and starts companion `ANTBikeLightDevice` on Predefined Network slot 1 (`ANT_PLUS`) using the paired radar's device number.
   - Zero duplicate device rows created in `DevicesDatabaseManager` (`DB_VERSION` unchanged).
   - Fully isolated: asynchronous acknowledged transmissions and potential companion timeouts never stall or delay primary radar target demuxing (Pages `0x30`/`0x31`) or the 3.5s starvation watchdog.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-CON-033.1` | `TST-CON-033.1` | Channel acquisition & companion binding unit test (`ANTBikeLightDeviceTest.kt`) | **PASSED** | `Verified` |
| `REQ-CON-033.2` | `TST-CON-033.2` | Page 0x01 & Page 0x02 broadcast decoding test (`ANTBikeLightDeviceTest.kt`) | **PASSED** | `Verified` |
| `REQ-CON-033.3` | `TST-CON-033.3` | Stateful 3-step handshake (0x21, 0x22, 0x20) test (`ANTBikeLightDeviceTest.kt`) | **PASSED** | `Verified` |
| `REQ-CON-033.4` | `TST-CON-033.4` | Unified controller abstraction & auto-switch test (`RadarLightControllerTest.kt`) | **PASSED** | `Verified` |
| `REQ-CON-033.5` | `TST-CON-033.5` | Channel isolation & radar telemetry non-interference test (`ANTBikeRadarDeviceTest.kt`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-PRO-001` | Living requirement governance audit (`verify_requirement_governance.py`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit & Integration Test Suite
Command executed under `python3 tools/parallel_sprint_lock.py`:
```bash
./gradlew testDebugUnitTest \
  --tests "com.atrainingtracker.banalservice.devices.ant_plus.*" \
  --tests "com.atrainingtracker.banalservice.devices.radar.*"
```

Output:
```text
> Task :app:compileDebugUnitTestKotlin UP-TO-DATE
> Task :app:compileDebugUnitTestJavaWithJavac UP-TO-DATE
> Task :app:processDebugUnitTestJavaRes
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 8m 17s
33 actionable tasks: 15 executed, 18 from cache
```
Test results: All targeted unit tests executed with 100% pass rate (zero failures, zero regressions).

### Living Requirement Governance Audit
Command:
```bash
python3 tools/verify_requirement_governance.py
```

Output:
```text
PASS: Living documentation master tables match all 9 domain shards with 100% parity.
PASS: Zero unmounted Composable islands detected in git diff.
PASS: Repository encapsulation verified (zero leaky DatabaseManager/Cursor members).
PASS: Zero architecture or system specification modifications detected.
Net-new requirement(s) detected: REQ-CON-033. Bypassing archaeology check cleanly.
```

---

## 4. Hardware & Architecture Verification
* **Backend Protocol Integration**: Pure protocol driver and companion channel architecture; zero new UI layouts introduced.
* **UI Reusability**: Reuses existing Cockpit Quick-Switch sheet (`RadarLightQuickSwitchSheet.kt`) and Settings (`RadarSettingsSection.kt`) introduced in ATT-5102, ensuring identical UX across BLE and ANT+ radar units.

---

## 5. Invariant & Governance Verification

1. **Predefined Network Slot 1 Protection**: Acquired strictly on slot 1 (`ANT_PLUS`), avoiding raw key rejection or RF bounds errors.
2. **Zero Duplicate Devices**: Companion channel lifecycle managed directly via paired radar device; zero database pollution.
3. **Radar Telemetry Non-Interference**: Non-blocking asynchronous ACK transmission preserves 0x28 radar target stream and 3.5s starvation watchdog.
4. **Living Documentation Synchronized**: Status in `docs/requirements.md` and `docs/tests.md` updated to `Verified`.
5. **Parent Ticket Final Review**: Parent ticket transitioned to `Final Review (Human)` and assigned to Rainer Blind.
