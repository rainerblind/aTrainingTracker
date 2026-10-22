# Stage 2: Requirement & Test Specification - ATT-3678: Add Pedal Power Balance simulation support to tools/ble_power_simulator.py

**Ticket**: [ATT-3678](https://rainerblind.atlassian.net/browse/ATT-3678)  
**Sub-task**: [ATT-3686](https://rainerblind.atlassian.net/browse/ATT-3686) (`[Req & Test Spec]`)  
**Parent Epic**: None (*Developer Tooling & Testing Infrastructure*)  
**Target Release**: `None`  
**Active Sprint**: `2026-42.5`  
**Requirement Mapping**: `REQ-TOOL-005` (*BLE Cycling Power Simulator Pedal Power Balance Simulation*)  
**Test Spec ID**: `TST-TOOL-005`  
**Branch**: `feature/ATT-3678`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Requirement Specification (REQ-TOOL-005)

### 1.1 Problem Statement & Rationale
Live manual verification of cockpit dual-sided pedal power balance indicator features (`REQ-UI-377` / `ATT-1295`) is blocked during sprints and reviews because `tools/ble_power_simulator.py` only transmits Instantaneous Power and Crank Revolution Data. Adding pedal power balance simulation allows realistic testing of symmetrical (Green), mild asymmetry (Amber), severe asymmetry (Red), and disabled states on real physical or emulator devices running aTrainingTracker.

### 1.2 Functional & Architectural Requirements

1. **Cycling Power Feature Characteristic (`UUID 0x2A65`)**:
   - The simulator SHALL declare bit 0 (`0x00000001`, Pedal Power Balance Supported) concurrently with bit 3 (`0x00000008`, Crank Revolution Data Supported).
   - The initial characteristic value SHALL be `[0x09, 0x00, 0x00, 0x00]`.
   - When read via BLE (`ReadValue`), it SHALL return `[0x09, 0x00, 0x00, 0x00]`, prompting `BTLEBikePowerDevice.java` to set `mIsPowerBalanceSupported = true` and register `SensorType.PEDAL_POWER_BALANCE`.

2. **Cycling Power Measurement Characteristic (`UUID 0x2A63`)**:
   - The characteristic SHALL define configurable attribute `pedal_balance` (default `50.0%`, range `0.0..100.0%`, or `None` when disabled).
   - When `pedal_balance` is enabled (`is not None`):
     - The 16-bit `flags` field SHALL set bit 0 (`0x0001`, Pedal Power Balance Present) in addition to bit 5 (`0x0020`, Crank Revolution Data Present), yielding `0x0021`.
     - Bit 1 (`0x0002`, Pedal Power Balance Reference) SHALL remain 0 (Left Pedal Reference).
     - The raw balance byte SHALL be calculated in $1/2\%$ resolution:
       $$\text{raw\_balance} = \text{int}(\text{round}(\text{pedal\_balance} \times 2))$$
       clamped to $[0, 200]$.
     - The raw balance byte SHALL be placed at offset 4 (immediately after Instantaneous Power at offset 2..3 and before Crank Revolution Data at offset 5..8), resulting in a 9-byte payload.
   - When `pedal_balance` is disabled (`None`):
     - Bit 0 in `flags` SHALL be cleared (`0x0020`).
     - The raw balance byte SHALL NOT be inserted, resulting in an 8-byte payload.

3. **Interactive CLI Controls (`keyboard_input_loop`)**:
   - The CLI parser SHALL accept `b <value>` (e.g. `b 50`, `b50`, `b 48`, `b 54`, `b 60`) where value is in $[0.0, 100.0]$, setting `pedal_balance`.
   - The CLI parser SHALL accept `b off` (case-insensitive), setting `pedal_balance = None`.
   - The live status readout SHALL show: `Leistung: <P> W | Kadenz: <C> RPM | Balance: <B>% (L/R: <L>/<R>)` or `Balance: deaktiviert`.

4. **Production Isolation**:
   - Zero changes to production app source code (`app/src/main`).

### 1.3 Acceptance Criteria (Given-When-Then)

* **Criterion 1: Feature Characteristic Bitmask**:
  * *Given* `tools/ble_power_simulator.py` instantiated,
  * *When* `CyclingPowerFeatureCharacteristic.ReadValue()` is invoked,
  * *Then* the returned byte sequence contains `0x09` at index 0, indicating both Pedal Power Balance and Crank Revolution Data are supported.

* **Criterion 2: Balanced Telemetry Payload (Optimal Tier)**:
  * *Given* `pedal_balance = 50.0%`,
  * *When* `build_payload()` is invoked,
  * *Then* the payload length is 9 bytes, flags are `0x0021`, and byte 4 equals `100` ($50.0\%$).

* **Criterion 3: Asymmetrical Telemetry Payload (Mild & Severe Tiers)**:
  * *Given* `pedal_balance = 54.0%` (Mild Asymmetry Amber),
  * *When* `build_payload()` is invoked,
  * *Then* byte 4 equals `108` ($54.0\%$).
  * *Given* `pedal_balance = 40.0%` (Severe Asymmetry Red),
  * *When* `build_payload()` is invoked,
  * *Then* byte 4 equals `80` ($40.0\%$).

* **Criterion 4: Balance Disabled Telemetry Payload**:
  * *Given* `pedal_balance = None`,
  * *When* `build_payload()` is invoked,
  * *Then* flags are `0x0020`, payload length is 8 bytes, and no balance byte is present.

* **Criterion 5: CLI Command Parsing**:
  * *Given* interactive input `b 52`,
  * *When* processed by the command handler,
  * *Then* `pedal_balance` updates to `52.0`.
  * *Given* interactive input `b off`,
  * *When* processed by the command handler,
  * *Then* `pedal_balance` updates to `None`.

### 1.4 System Invariants
- Zero changes to production Android codebase.
- Pure Python unit tests without external hardware or live D-Bus system requirements.
- Full clean-room test suite pass rate (`testDebugUnitTest` and `test_ble_power_simulator.py`).

---

## 2. Test Specification (TST-TOOL-005)

### Test Case 1: Cycling Power Feature Bitmask Unit Tests (`[TST-TOOL-005.1]`)
* **Scope**: Unit Test
* **Target File**: `tools/test_ble_power_simulator.py`
* **Preconditions**: Initialized `CyclingPowerFeatureCharacteristic` or mock/pure logic extractor.
* **Action**: Inspect initial value and read response.
* **Expected Result**: Bit 0 (`0x01`) and bit 3 (`0x08`) are set (`0x09`).

### Test Case 2: Measurement Payload Encoding & Asymmetry Tiers (`[TST-TOOL-005.2]`)
* **Scope**: Unit Test
* **Target File**: `tools/test_ble_power_simulator.py`
* **Preconditions**: Initialized measurement characteristic state.
* **Action**: Generate payloads for balance values `50.0`, `48.0`, `54.0`, `40.0`, `-5.0`, `105.0`, and `None`.
* **Expected Result**:
  * `50.0`: flags `0x0021`, raw balance `100`, length 9.
  * `54.0`: flags `0x0021`, raw balance `108`, length 9.
  * `40.0`: flags `0x0021`, raw balance `80`, length 9.
  * `-5.0`: clamped to raw balance `0`.
  * `105.0`: clamped to raw balance `200`.
  * `None`: flags `0x0020`, length 8, no balance byte.

### Test Case 3: Interactive CLI Command Parsing (`[TST-TOOL-005.3]`)
* **Scope**: Unit Test
* **Target File**: `tools/test_ble_power_simulator.py`
* **Action**: Feed command strings: `b 50`, `b50`, `b 48`, `b 54`, `b 60`, `b off`, `b OFF`, `b invalid`.
* **Expected Result**: Commands update balance state accurately; invalid commands leave state unchanged without raising unhandled exceptions.

### Test Case 4: Clean-Room Regression Suite (`[TST-TOOL-005.4]`)
* **Commands**:
  * `python3 -m unittest tools/test_ble_power_simulator.py`
  * `./gradlew testDebugUnitTest`
* **Expected Result**: 100% test pass rate with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-TOOL-005.1]` | Unit | `CyclingPowerFeatureCharacteristic` | `REQ-TOOL-005.1` | Specified |
| `[TST-TOOL-005.2]` | Unit | `CyclingPowerMeasurementCharacteristic.build_payload` | `REQ-TOOL-005.2` | Specified |
| `[TST-TOOL-005.3]` | Unit | `process_cli_command` | `REQ-TOOL-005.3` | Specified |
| `[TST-TOOL-005.4]` | Regression | `./gradlew testDebugUnitTest` + `test_ble_power_simulator.py` | `REQ-PRO-001` | Specified |
