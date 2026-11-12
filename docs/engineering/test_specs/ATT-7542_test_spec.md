# Stage 2: Requirement & Test Specification - ATT-7542: Garmin Varia RearVue 820 BLE V2 24-Bit 2D Target Decoder & Pre-Handshake Fallback

**Ticket**: [ATT-7542](https://rainerblind.atlassian.net/browse/ATT-7542) | **Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5` | **Branch**: `feature/ATT-7542`  
**Requirement Mapping**: `REQ-CON-030` (`REQ-CON-030.1`, `REQ-CON-030.2`, `REQ-CON-030.3`) | **Test Spec ID**: `TST-CON-030` (`TST-CON-030.1`, `TST-CON-030.2`, `TST-CON-030.3`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-12  

---

## 1. Requirement Specification (`REQ-CON-030`)

### 1.1 Attributes & Problem Statement
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit
- Physical Garmin Varia RearVue 820 BLE V2 (`6a4e3204`) streams remain deadlocked because Battery (`0x2A19`) read/CCCD is queued after V2, and V2 CCCD writes succeed with `GATT_SUCCESS` even when 0 notifications are emitted. Real frames use `[2-byte LE header] + N * [9-byte target struct]` with 24-bit packed range words (11-bit lateral, 13-bit longitudinal range) and negative `speedY` closing velocity.

### 1.2 Functional & Architectural Requirements
1. `BTLERadarDevice.configureServices()` SHALL enqueue Battery (`0x2A19`) read and CCCD notification write before initiating V2 characteristic `6a4e3204` setup.
2. `BTLERadarDevice` SHALL NEVER subscribe to V1 `6a4e3203` before attempting V2 negotiation, avoiding peripheral firmware V1 pinning.
3. Upon V2 CCCD write completion, `BTLERadarDevice` SHALL arm a 1500 ms V2 silence watchdog; if 0 notifications arrive within 1500 ms, it SHALL automatically invoke `fallbackToV1()`. Disconnect SHALL reset V2 fallback flags.
4. `BTLERadarDevice.parseV2ThreatPacket()` SHALL demux 2-byte idle heartbeats (`len == 2`, e.g. `02 00` / `02 90`) and 5/6-byte device status frames (`0x0004`), resetting the 3.5s watchdog without emitting bogus targets.
5. `BTLERadarDevice` SHALL unpack multi-target frames (`len >= 11 && (len - 2) % 9 == 0`) with 24-bit packed range words (11-bit signed lateral $x$, 13-bit signed longitudinal $y$), map `targetClass` (`LARGE` $\to$ `TRUCK_OR_BUS`, `MODERATE` $\to$ `CAR`, `FAINT` $\to$ `UNKNOWN_OR_LIGHT`), and derive closing speed from negative $speedY$ ($v = -speedY * 0.5\text{ m/s}$).

### 1.3 Acceptance Criteria (Given-When-Then)
- *Given* a connected RearVue 820 with V2, *When* `configureServices()` runs, *Then* Battery CCCD is enqueued before V2 without touching V1.
- *Given* V2 CCCD enabled with silent peripheral, *When* 1500 ms elapse with 0 notifications, *Then* `fallbackToV1()` subscribes `6a4e3203`.
- *Given* 2-byte idle heartbeat or 5/6-byte status frame, *When* received, *Then* 3.5s watchdog is reset and `CONNECTED` state maintained with 0 targets.
- *Given* a 24-bit packed V2 frame, *When* parsed, *Then* lateral offset, range, closing speed, threat level, and size class are unpacked accurately.

### Requirement Archaeology & Chesterton's Fence Audit
- *Original Requirement ID & Target*: `REQ-CON-030` (`REQ-CON-030.1`..`REQ-CON-030.3`), targeting `BTLERadarDevice.java`.
- *Historical Origin & Commit Trace*: Introduced in `ATT-5095` (commit `524e937d`); refined in `ATT-6706`; refined in `ATT-7542` for authentic RearVue 820 framing.
- *Root Reason for Existing Formulation*: Initial driver assumed synthetic 1-byte header `0x01` and 8-byte structs with embedded threat bits; real hardware uses 24-bit packed range words and requires Battery priority and silence timeout.
- *Preservation of Core Invariants*: 3.5s starvation watchdog, V1 fallback compatibility, 0 SQLite migrations, 100% 9-language translation parity preserved.

---

## 2. Test Specification (`TST-CON-030`)

### Test Cases
- `[TST-CON-030.1]` (Unit - `BTLERadarDeviceTest.kt`): Verify Battery CCCD is queued before V2, V1 is untouched initially, and V2 notifications route through `measurementCharacteristicUpdate`.
- `[TST-CON-030.2]` (Unit - `BTLERadarDeviceTest.kt`): Verify 1500 ms V2 silence watchdog triggers `fallbackToV1()` subscribing `6a4e3203`, and verify fallback state resets on disconnect.
- `[TST-CON-030.3]` (Unit - `BTLERadarDeviceTest.kt`): Verify decoding of 24-bit packed range words, negative `speedY` closing velocity, 2-byte idle heartbeats (`0290`), 5/6-byte status frames (`06880500001d`), and vehicle size classes against authentic capture frames.
- `[TST-CON-030.8]` (Tier 1 Regression): Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.*"` asserting 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component & Method | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-CON-030.1]` | Unit | `BTLERadarDevice.configureServices` | `REQ-CON-030.1` | Specified |
| `[TST-CON-030.2]` | Unit | `BTLERadarDevice.onDescriptorWriteCompleted` / fallback | `REQ-CON-030.2` | Specified |
| `[TST-CON-030.3]` | Unit | `BTLERadarDevice.parseV2ThreatPacket` | `REQ-CON-030.3` | Specified |
| `[TST-CON-030.8]` | Regression | `com.atrainingtracker.banalservice.devices.bluetooth_le.*` | `REQ-PRO-001` | Specified |
