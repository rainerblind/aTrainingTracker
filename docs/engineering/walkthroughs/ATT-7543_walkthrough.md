# Stage 5: Walkthrough & Verification - ATT-7543: Real-Hardware Garmin Varia / RearVue 820 BLE V1 Decoding & GATT Lifecycle

**Ticket**: [ATT-7543](https://rainerblind.atlassian.net/browse/ATT-7543)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.5`  
**Requirement Mapping**: `REQ-CON-028` (`REQ-CON-028.3`, `REQ-CON-028.4`)  
**Test Mapping**: `TST-CON-028` (`TST-CON-028.2`, `TST-CON-028.7`)  
**Branch**: `feature/ATT-7543`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Executive Summary & Verification Overview

ATT-7543 resolves physical Garmin Varia / RearVue 820 BLE radar interoperability:
1. **GATT Connection Lifecycle**: `BTLERadarDevice.startSearching()` transitions `RadarStateRepository` to `CONNECTING`. `configureServices()` transitions to `CONNECTED` and arms the 3.5s fail-safe starvation watchdog upon CCCD subscription, ensuring `RadarEdgeOverlay` renders prior to packet arrival.
2. **Real-Hardware V1 Framing**: `BTLERadarDevice.processV1Notification()` demuxes sequence-header frames `(header & 0x0F) == 0x02` (`0x02..0xF2`) and legacy `0x00`. 1-byte heartbeats (~7 Hz) and 6-byte sector diagnostics (`0x06`) are recognized as valid link-alive frames that re-arm the 3.5s watchdog, prune stale tracks (`STALE_TRACK_TIMEOUT_MS = 2000L`), and publish `CONNECTED`.
3. **Target Validity & Stateful Speed Kinematics**: Decodes threat triplets filtering out `vid < 0x80`, `vid == 0xFD`, and `dist == 0xFF`. Extracts `trackId = vid & 0x7F` and `rangeMeters = dist & 0xFF`, maintaining stateful track history to derive closing speed ($\Delta d / \Delta t$) and classify `FAST_APPROACHING` vs `APPROACHING`.
4. **Pan-Family Scan Filters**: Added `UUID_SERVICE_GARMIN_MEMBER` (`0000fe1f-0000-1000-8000-00805f9b34fb`) to `BluetoothConstants` and expanded `BTSearchForNewDevicesEngine.buildScanFilters(BIKE_RADAR)` with 4 filters (Radar Service + Data, Member Service + Data).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-CON-028.3` | `[TST-CON-028.7]` | Unit Test (`BTSearchForNewDevicesEngineTest`) | **PASSED** | `Verified` |
| `REQ-CON-028.4` | `[TST-CON-028.2]` | Unit Test (`BTLERadarDeviceTest`) | **PASSED** | `Verified` |
| `REQ-CON-028.6` | `[TST-CON-028.5]` | Localization Parity (`TranslationParityTest`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `[TST-CON-028.6]` | Tier 1 Targeted (`com.atrainingtracker.banalservice.devices.bluetooth_le.*`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Regression Scope
- Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
- Result: 104 tests passed, 0 failures, 0 skipped.
- Requirement Governance: `python3 tools/verify_requirement_governance.py` passed 100% (0 errors).

---

## 4. Hardware / Physical Verification (Pixel 10)

- Pure protocol, GATT lifecycle, and sensor acquisition driver change.
- No direct Compose/UI component bytecode changed (`TrackingTabsScreen.kt` inspected read-only).
- Verified via unit test suite covering real RearVue 820 capture sequences (`0x02..0xF2` sequence cycles, 1-byte heartbeats, 6-byte sector frames, invalid track discarding, and stateful track speed derivation).

---

## 5. Invariant & Governance Verification

1. **Zero Regressions**: All 104 unit tests in BLE peripheral stack passed 100% green.
2. **Living Documentation**: Synchronized to `[Verified]` in `docs/requirements/req_tracking_cockpit.md` and `docs/tests/tst_tracking_cockpit.md`.
3. **No Docs in Stage 4**: Adhered to Rule 85 (canonical docs only in Stages 1, 2, 3, 5).
4. **Target Release Governance**: Aligned to active release `V5.0.0` (Rule 66 / Rule 84).
