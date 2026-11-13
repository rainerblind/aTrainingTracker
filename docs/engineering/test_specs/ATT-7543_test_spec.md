# Stage 2: Requirement & Test Specification - ATT-7543: Real-Hardware Garmin Varia / RearVue 820 BLE V1 Decoding & GATT Lifecycle

**Ticket**: [ATT-7543](https://rainerblind.atlassian.net/browse/ATT-7543) | **Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5` | **Branch**: `feature/ATT-7543`  
**Requirement Mapping**: `REQ-CON-028` (`REQ-CON-028.3`, `REQ-CON-028.4`) | **Test Spec ID**: `TST-CON-028` (`TST-CON-028.2`, `TST-CON-028.7`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-12  

---

## 1. Requirement Specification (`REQ-CON-028`)

### 1.1 Attributes & Problem Statement
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit
- Physical Garmin Varia / RearVue 820 hardware is rejected because notifications on `6a4e3203` use sequence header `(seq << 4) | 0x02` (`0x02..0xF2`), 1-byte heartbeats, 6-byte sector diagnostics, and stateful closing speed ($\Delta d / \Delta t$) with track validity bit 7 (`vid >= 0x80`). `startSearching()` and `configureServices()` must explicitly transition `RadarStateRepository` to `CONNECTING` and `CONNECTED` to render `RadarEdgeOverlay`.

### 1.2 Functional & Architectural Requirements
1. `BTLERadarDevice.startSearching()` SHALL transition `RadarStateRepository` to `RadarConnectionState.CONNECTING`.
2. `BTLERadarDevice.configureServices()` SHALL transition `RadarStateRepository` to `RadarConnectionState.CONNECTED` and arm the 3.5s watchdog upon CCCD subscription.
3. `BTLERadarDevice` SHALL demux sequence-framed packets (`(payload[0] & 0x0F) == 0x02` and `0x00`), re-arming watchdog on 1-byte heartbeats and 6-byte sector frames while pruning stale tracks (`STALE_MS = 2000L`).
4. `BTLERadarDevice` SHALL filter threat triplets discarding `vid < 0x80`, `vid == 0xFD`, and `dist == 0xFF`, deriving closing speed statefully from track history ($\Delta d / \Delta t$) and classifying `FAST_APPROACHING` vs `APPROACHING`.
5. `BTSearchForNewDevicesEngine.buildScanFilters()` SHALL include Garmin Member Service UUID `0xFE1F` (`0000fe1f-0000-1000-8000-00805f9b34fb`) alongside `UUID_SERVICE_RADAR`.

### 1.3 Acceptance Criteria (Given-When-Then)
- *Given* a connection initiated to Garmin Varia, *When* `startSearching()` runs, *Then* state transitions to `CONNECTING`.
- *Given* CCCD subscription in `configureServices()`, *When* configured, *Then* state transitions to `CONNECTED` and 3.5s watchdog is armed.
- *Given* 1-byte heartbeat or 6-byte sector frame, *When* received, *Then* watchdog is reset, stale tracks pruned, and `CONNECTED` published.
- *Given* V1 threat frame with `vid >= 0x80`, *When* parsed, *Then* targets are published with stateful closing speed and threat level.
- *Given* BLE discovery for `BIKE_RADAR`, *When* scan filters are built, *Then* Garmin 16-bit UUID `0xFE1F` filters are included.

### Requirement Archaeology & Chesterton's Fence Audit
- *Original Requirement ID & Target*: Refine `REQ-CON-028.3` and `REQ-CON-028.4` in `docs/requirements/req_tracking_cockpit.md`.
- *Historical Origin & Commit Trace*: `ATT-5083` (commit `dfbe3979`) established initial driver; `ATT-6701` unified GATT queue; `ATT-7543` aligns with real hardware capture frames.
- *Root Reason for Existing Formulation*: Simulator assumed `0x00` header, speed in byte 3, and state update only on packet arrival. Real hardware uses sequence nibbles and stateful speed derivation.
- *Preservation of Core Invariants*: 3.5s starvation watchdog, simulator preemption, zero SQLite migrations, and BLE V2/ANT+ streams remain 100% preserved.

---

## 2. Test Specification (`TST-CON-028`)

### Test Cases
- `[TST-CON-028.2]` (Unit - `BTLERadarDeviceTest.kt`): Verify `startSearching()` `CONNECTING` transition, `configureServices()` `CONNECTED` transition, sequence header (`0x02..0xF2`) demuxing, 1-byte heartbeats, 6-byte sector frames, invalid track discarding (`vid < 0x80`, `0xFD`, `dist == 0xFF`), stateful speed calculation $\Delta d / \Delta t$, and 3.5s starvation timeout.
- `[TST-CON-028.7]` (Unit - `BTSearchForNewDevicesEngineTest.kt`): Verify `buildScanFilters(DeviceType.BIKE_RADAR)` contains Garmin Member Service UUID `0xFE1F` service and data filters.
- `[TST-CON-028.5]` (Localization - `TranslationParityTest.kt`): Verify 100% string parity across all 9 locales in `strings_radar.xml`.
- `[TST-CON-028.6]` (Tier 1 Regression): Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.*" --tests "com.atrainingtracker.trainingtracker.build.*"` asserting 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component & Method | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-CON-028.2]` | Unit | `BTLERadarDevice` (lifecycle, V1 parsing, track history) | `REQ-CON-028.4` | Specified |
| `[TST-CON-028.7]` | Unit | `BTSearchForNewDevicesEngine.buildScanFilters` | `REQ-CON-028.3` | Specified |
| `[TST-CON-028.5]` | Localization | `strings_radar.xml` / `TranslationParityTest` | `REQ-CON-028.6` | Specified |
| `[TST-CON-028.6]` | Regression | `com.atrainingtracker.banalservice.*` | `REQ-PRO-001` | Specified |
