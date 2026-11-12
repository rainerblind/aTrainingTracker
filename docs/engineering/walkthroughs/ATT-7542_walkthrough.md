# Stage 5: Walkthrough & Verification - ATT-7542: Garmin Varia RearVue 820 BLE V2 24-Bit Decoder & Fallback

**Ticket**: [ATT-7542](https://rainerblind.atlassian.net/browse/ATT-7542)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.5`  
**Requirement Mapping**: `REQ-CON-030` (`REQ-CON-030.1`, `REQ-CON-030.2`, `REQ-CON-030.3`)  
**Test Mapping**: `TST-CON-030` (`TST-CON-030.1`, `TST-CON-030.2`, `TST-CON-030.3`, `TST-CON-030.8`)  
**Branch**: `feature/ATT-7542`  
**Author**: AI Agent 2 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Executive Summary & Verification Overview

ATT-7542 resolves Garmin Varia RearVue 820 BLE V2 acquisition and protocol framing:
1. **Pre-Handshake GATT Ordering**: `BTLERadarDevice.configureServices()` prioritizes Battery (`0x2A19`) CCCD subscription prior to V2 (`6a4e3204`) negotiation, and defers V1 (`6a4e3203`) subscription to prevent firmware V1 locking across reconnects (`REQ-CON-030.1`).
2. **1500 ms Silence Watchdog Fallback**: Configured `mV2SilenceRunnable` (`V2_SILENCE_TIMEOUT_MS = 1500L`) armed upon V2 CCCD completion and canceled upon the arrival of any V2 telemetry frame. On silence timeout, automatically invokes `fallbackToV1()`. Fallback state and silence watchdog are cleanly reset on disconnect (`REQ-CON-030.2`).
3. **Authentic RearVue 820 Wire Protocol Demuxer**: Replaced synthetic parser in `parseV2ThreatPacket()` to support:
   - 2-byte idle heartbeats (`02 00` / `02 90`, bit 0 `0x0001` set) resetting the 3.5s starvation watchdog and maintaining `RadarConnectionState.CONNECTED` without bogus targets.
   - 5/6-byte device status frames (`0x0004` bit set in 16-bit header) resetting watchdog.
   - `2 + 9*N` multi-target packets with 24-bit packed range word unpacking (11-bit signed lateral X * 0.1m, 13-bit signed range Y * 0.1m), vehicle class mapping (`LARGE` $\to$ `TRUCK_OR_BUS`, `MODERATE` $\to$ `CAR`, `FAINT`/`UNKNOWN` $\to$ `UNKNOWN_OR_LIGHT`), and negative `speedY` closing speed derivation (`approachSpeedMps = -speedY * 0.5f`) (`REQ-CON-030.3`).
4. **Stale-Track Ageing**: Configured split ageing (`STALE_TRACK_MOVING_MS = 800L`, `STALE_TRACK_PARKED_MS = 5000L`).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-CON-030.1` | `[TST-CON-030.1]` | Unit Test (`BTLERadarDeviceTest.testConfigureServices_enqueuesBatteryCccdBeforeV2MtuAndCccd`) | **PASSED** | `Verified` |
| `REQ-CON-030.2` | `[TST-CON-030.2]` | Unit Test (`BTLERadarDeviceTest.testV2SilenceWatchdog_fallsBackToV1AfterTimeout`) | **PASSED** | `Verified` |
| `REQ-CON-030.3` | `[TST-CON-030.3]` | Unit Test (`BTLERadarDeviceTest.testParseV2ThreatPacket_decodesAuthenticRearVue820CaptureFrame`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `[TST-CON-030.8]` | Tier 1 Targeted (`com.atrainingtracker.banalservice.devices.bluetooth_le.*`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Regression Scope
- Command: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*"`
- Result: **110 tests passed**, 0 failures, 0 skipped (100% green).
- Governance Verification: `python3 tools/verify_requirement_governance.py --text-file docs/engineering/test_specs/ATT-7542_test_spec.md` $\to$ **PASSED** (100% parity, 0 violations).

---

## 4. Hardware / Physical Verification (Pixel 10)

- Pure protocol, GATT lifecycle, and sensor acquisition driver change.
- Verified via unit test suite covering authentic RearVue 820 capture frames (`02389c17f50f0310070080b5243b48093c09fd80`), 2-byte idle heartbeats, 5/6-byte device status frames, Battery CCCD ordering, 1500 ms V2 silence watchdog fallback, and disconnect reset.

---

## 5. Invariant & Governance Verification

1. **Zero Regressions**: 110 unit tests in BLE peripheral stack passed 100% green.
2. **Living Documentation**: Synchronized to `[Verified]` in `docs/requirements/req_tracking_cockpit.md` and `docs/tests/tst_tracking_cockpit.md`.
3. **No Docs in Stage 4**: Adhered to Rule 85 (canonical docs only in Stages 1, 2, 3, 5).
4. **Target Release Governance**: Aligned to active release `V5.0.0` (Rule 66 / Rule 84).
