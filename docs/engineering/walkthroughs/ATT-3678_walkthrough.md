# Stage 5: Walkthrough & Verification - ATT-3678: Add Pedal Power Balance simulation support to tools/ble_power_simulator.py

**Ticket**: [ATT-3678](https://rainerblind.atlassian.net/browse/ATT-3678)  
**Sub-task**: [ATT-3689](https://rainerblind.atlassian.net/browse/ATT-3689) (`[Test]`)  
**Parent Epic**: None (*Developer Tooling & Testing Infrastructure*)  
**Target Release**: `None`  
**Active Sprint**: `2026-42.5`  
**Requirement Mapping**: `REQ-TOOL-005`  
**Test Mapping**: `TST-TOOL-005`  
**Branch**: `feature/ATT-3678`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Executive Summary & Verification Overview

During the Sprint 2026-42.4 review of `ATT-1295` (*Visual pedal power balance indicator tile in cockpit*), live on-device testing and manual verification of physiological asymmetry tiers (Optimal Green: $48..52\%$, Mild Asymmetry Amber: $45..47\%$ or $53..55\%$, Severe Asymmetry Red: $<45\%$ or $>55\%$) was blocked because `tools/ble_power_simulator.py` only supported Power and Cadence.

Ticket `ATT-3678` upgrades `tools/ble_power_simulator.py` to support dual-sided BLE pedal power balance in accordance with the Bluetooth SIG Cycling Power Service profile (UUID `0x1818`) and the parsing logic in `BTLEBikePowerDevice.java`.

### Key Enhancements
1. **Cycling Power Feature Characteristic (`UUID 0x2A65`)**:
   - Bit 0 (`0x01`, `PEDAL_POWER_BALANCE_SUPPORTED_MASK`) set alongside bit 3 (`0x08`, `CRANK_REVOLUTION_DATA_SUPPORTED_MASK`), broadcasting `[0x09, 0x00, 0x00, 0x00]`.
2. **Cycling Power Measurement Characteristic (`UUID 0x2A63`)**:
   - Added configurable `pedal_balance` attribute (default $50.0\%$, float range $0.0..100.0\%$, or `None` when disabled).
   - Dynamically sets bit 0 in 16-bit flags (`0x0021`) and packs `raw_balance = int(round(pedal_balance * 2))` in $1/2\%$ resolution at offset 4 (immediately after instantaneous power, before crank revolution data).
   - When disabled (`None`), bit 0 in flags is cleared (`0x0020`) and no balance byte is inserted, falling back cleanly to the legacy 8-byte layout.
3. **Interactive Terminal Controls**:
   - Added `b <0-100>` (e.g. `b 50`, `b 54`, `b 40`) and `b off` commands to dynamically adjust or silence pedal balance during live simulation.
   - Enriched live CLI ticker with active balance percentage and L/R split.
4. **Clean-Room Verification**:
   - Created `tools/test_ble_power_simulator.py` with 6 unit tests covering feature bitmasks, measurement payload structure, physiological asymmetry tiers, clamping, disabled fallback, and CLI commands.
   - Verified 100% pass rate across the full clean-room unit test suite (`./gradlew testDebugUnitTest`, 3m 53s).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TOOL-005.1` | `TST-TOOL-005.1` | Automated Unit Test (`test_feature_characteristic_bitmask`) | **PASSED** | `Verified` |
| `REQ-TOOL-005.2` | `TST-TOOL-005.2` | Automated Unit Test (`test_measurement_default_payload_balanced`, `test_measurement_asymmetry_tiers`, `test_measurement_balance_disabled`) | **PASSED** | `Verified` |
| `REQ-TOOL-005.3` | `TST-TOOL-005.3` | Automated Unit Test (`test_cli_command_balance`, `test_cli_command_power_and_cadence`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-TOOL-005.4` | Clean-Room Suite (`testDebugUnitTest` + `test_ble_power_simulator.py`) | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)
```text
BUILD SUCCESSFUL in 3m 53s
32 actionable tasks: 1 executed, 31 up-to-date
```

### Dedicated Unit Tests (`tools/test_ble_power_simulator.py`)
```text
Ungültiger Balance-Bereich: Erlaubt sind 0.0 bis 100.0 % (z.B. b 50, b 54, b 40, b off)
Ungültiger Balance-Bereich: Erlaubt sind 0.0 bis 100.0 % (z.B. b 50, b 54, b 40, b off)
Ungültige Balance-Eingabe (z.B. b 50, b 54, b off)
..[*] Read CyclingPowerFeature (0x2A65) -> Pedal Power Balance & Crank Revolution Data Supported
....
----------------------------------------------------------------------
Ran 6 tests in 0.001s

OK
```

### Targeted Android Cockpit Math Test (`PedalPowerBalanceMathTest.kt`)
```text
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 3s
32 actionable tasks: 1 executed, 31 up-to-date
```

---

## 4. Hardware / Physical Verification (Pixel 10)

Pure developer tooling enhancement (`tools/ble_power_simulator.py`); zero Android APK runtime bytecode changes (`app/src/main` completely untouched). Verified via automated unit tests and clean-room regression suite.

### Visual Consistency (Rule 23 — mandatory if UI is added or changed)
No UI changes.

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**: Clean-room test suite executed with 100% pass rate.
2. **Living Documentation Synchronized**: `REQ-TOOL-005` in `docs/requirements.md` and `TST-TOOL-005` in `docs/tests.md` updated to `Verified`.
3. **Subtask Completion**: Stage 5 subtask transitioned to `Erledigt` via `freigabe`.
4. **Parent Ticket Final Review**: Parent ticket `ATT-3678` transitioned to `Final Review (Human)` and assigned to `human` for final acceptance.
5. **Continuous Sprint Branch Integration (Strategy A)**: Feature branch `feature/ATT-3678` merged into `sprint/2026-42.5` with `--no-ff`.
