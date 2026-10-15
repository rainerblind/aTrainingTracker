# Stage 3: Implementation Plan - ATT-3678: Add Pedal Power Balance simulation support to tools/ble_power_simulator.py

**Ticket**: [ATT-3678](https://rainerblind.atlassian.net/browse/ATT-3678)  
**Sub-task**: [ATT-3687](https://rainerblind.atlassian.net/browse/ATT-3687) (`[Impl-Plan]`)  
**Parent Epic**: None (*Developer Tooling & Testing Infrastructure*)  
**Target Release**: `None`  
**Active Sprint**: `2026-42.5`  
**Requirement Mapping**: `REQ-TOOL-005`  
**Test Mapping**: `TST-TOOL-005`  
**Branch**: `feature/ATT-3678`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Problem Description & Background

During the Sprint 2026-42.4 review of `ATT-1295` (*Visual pedal power balance indicator tile in cockpit*), live on-device testing and visual inspection of physiological asymmetry tiers (Optimal Green: $48..52\%$, Mild Asymmetry Amber: $45..47\%$ or $53..55\%$, Severe Asymmetry Red: $<45\%$ or $>55\%$) was blocked because `tools/ble_power_simulator.py` only supported Power and Cadence.

This plan details the changes required to upgrade `tools/ble_power_simulator.py` to support dual-sided BLE pedal power balance in accordance with the Bluetooth SIG Cycling Power Service profile (UUID `0x1818`) and the parsing logic in `BTLEBikePowerDevice.java`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-TOOL-005` (*Bluetooth Low Energy Cycling Power Simulator Pedal Power Balance Simulation*)
* **Test Mapping**: `TST-TOOL-005` (*BLE Cycling Power Simulator Pedal Power Balance Verification Suite*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Production App Code Modification**: Android app code (`app/src/main`) remains completely untouched; the existing `BTLEBikePowerDevice.java` parser already natively supports pedal balance per specification.
2. **Preservation of Existing Simulator Capabilities**: Instantaneous power adjustment (`+`, `-`, `w<num>`), cadence simulation (`c<num>`), and advertising remain 100% backward compatible. Disabling balance (`b off`) reproduces the exact 8-byte legacy packet structure.
3. **Hardware-Independent Unit Testing**: All unit tests in `tools/test_ble_power_simulator.py` execute cleanly in headless environments without physical Bluetooth adapters or BlueZ system daemons.
4. **Human Decision Gate Invariant**: Parent ticket `ATT-3678` will not be transitioned to `Erledigt` by agents; only moved to `Final Review (Human)` upon verification completion.

---

## 4. Proposed Architectural Changes

### Component 1: `CyclingPowerFeatureCharacteristic` (`tools/ble_power_simulator.py`)
* Update feature bitmask from `[0x08, 0x00, 0x00, 0x00]` to `[0x09, 0x00, 0x00, 0x00]`:
  - Bit 0 (`0x01`): `PEDAL_POWER_BALANCE_SUPPORTED_MASK`
  - Bit 3 (`0x08`): `CRANK_REVOLUTION_DATA_SUPPORTED_MASK`
* Update diagnostic log message on read.

### Component 2: `CyclingPowerMeasurementCharacteristic` (`tools/ble_power_simulator.py`)
* Introduce `self.pedal_balance = 50.0` (percentage Right pedal, default $50.0\%$, range $0.0..100.0\%$, or `None` when disabled).
* In `build_payload()`:
  - Base flags `0x0020` (Crank Revolution Data Present).
  - If `self.pedal_balance is not None`:
    - Set bit 0 in flags (`flags |= 0x0001`). Bit 1 remains 0 (Left Pedal Reference).
    - Encode `raw_balance = max(0, min(200, int(round(self.pedal_balance * 2))))` as a 1-byte UINT8.
    - Insert at offset 4 (immediately after Instantaneous Power, before Crank Revolution Data).
    - Resulting packet length: 9 bytes.
  - If `self.pedal_balance is None`:
    - Bit 0 in flags remains 0 (`flags = 0x0020`).
    - No balance byte inserted.
    - Resulting packet length: 8 bytes.

### Component 3: CLI Command Handler (`tools/ble_power_simulator.py`)
* Implement pure helper `handle_cli_command(line: str, meas_char) -> str`:
  - Parse `b <float|int>` (e.g. `b 50`, `b50`, `b 54`, `b 48`, `b 60`): validate within $[0.0, 100.0]$, assign to `meas_char.pedal_balance`.
  - Parse `b off` (case-insensitive): assign `None` to `meas_char.pedal_balance`.
  - Retain `+`, `-`, `w<num>`, `c<num>`, `q`.
  - Produce formatted status line displaying Power, Cadence, and Balance percentage with Left/Right split (or "deaktiviert").
* Wire `handle_cli_command` into `keyboard_input_loop` and update help banner.

### Component 4: Test Suite (`tools/test_ble_power_simulator.py`)
* Dedicated Python unittest suite verifying:
  - Feature characteristic bitmask reading.
  - Measurement payload flags and byte layout across 50%, 54%, 40%, 48%, clamped bounds, and disabled states.
  - CLI command parsing logic for all commands and malformed inputs.

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* No UI changes (Tooling enhancement in `tools/`).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Implement Feature & Measurement Changes in `tools/ble_power_simulator.py`
* Update `CyclingPowerFeatureCharacteristic` with bit 0 set (`0x09`).
* Update `CyclingPowerMeasurementCharacteristic` with `pedal_balance` attribute and conditional 1-byte encoding at offset 4.
* Add `handle_cli_command` helper and update `keyboard_input_loop`.

### Step 2: Implement Unit Test Suite in `tools/test_ble_power_simulator.py`
* Create `tools/test_ble_power_simulator.py` with comprehensive test cases for bitmasks, payloads, and CLI commands.

### Step 3: Execute Unit Tests
* Run `python3 -m unittest tools/test_ble_power_simulator.py`.
* Run `./gradlew testDebugUnitTest` to ensure zero regressions across all Android tests.

---

## 6. Verification & Rollback Plan

* **Verification**:
  - `python3 -m unittest tools/test_ble_power_simulator.py` must pass with 100% success.
  - `./gradlew testDebugUnitTest` must pass with 100% success.
  - Stage 5 Walkthrough deliverable documenting the changes and verification evidence.
* **Rollback**:
  - Isolated feature branch `feature/ATT-3678`. Git reset or branch abandonment leaves `sprint/2026-42.5` unaffected.
