# Stage 5: Verification & Walkthrough - ATT-9605: `ANTBikeSpeedDevice` & `ANTBikeSpeedAndCadenceDevice` Null Speed Guard & Standstill Pace

**Ticket**: `ATT-9605` (`1 SP`, `Bug`, `tier-a`) | **Parent Epic**: `ATT-6033`  
**Target Release**: `V5.0.0` (`Rule 8.3`) | **Active Sprint**: `2026-11-23_03_Nacht` (Sprint 476)  
**Branch**: `feature/ATT-9605` (preserved intact for Stage 6 Cold Review) | **Date**: 2026-11-23  
**Requirement**: `REQ-CON-058` (`[Verified]`) | **Test Spec**: `TST-CON-058` (`[Verified]`)  
**Evidence Bundle**: `docs/engineering/test_evidence/tickets/ATT-9605/` (`evidence_summary.json`, SHA-256 `manifest.json`)

---

## 1. Delivered Changes (`app/` & Living Docs)
* **`ANTBikeSpeedDevice.java` (`145 -> 149` LoC `<= 1,500`)**:
  * In `onNewCalculatedSpeed` (`:112-121`), added `if (calculatedSpeed == null) { return; }` before `myLog(...)` and before `calculatedSpeed.doubleValue()`, preventing NPEs and leaving `mSpeedSensor` / `mPaceSensor` undisturbed (`REQ-CON-058.1`).
  * Cached `double speed = calculatedSpeed.doubleValue();` (direct `m/s` per `AntPlusBikeSpeedDistancePcc.CalculatedSpeedReceiver`), emitted `mSpeedSensor.newValue(speed)`, and replaced `1 / calculatedSpeed.doubleValue()` with `mPaceSensor.newValue(speed > 0.0 ? 1.0 / speed : null);` (`REQ-CON-058.2`).
* **`ANTBikeSpeedAndCadenceDevice.java` (`250 -> 254` LoC `<= 1,500`)**:
  * Applied the identical `calculatedSpeed == null` early-return and `speed > 0.0 ? 1.0 / speed : null` pace guard in `onNewCalculatedSpeed` (`:148-155`), keeping `releaseAccess()` (`REQ-CON-025.1`), `onNewCalculatedAccumulatedDistance`, and `onNewCalculatedCadence` untouched.
* **Unit Tests**:
  * Created `ANTBikeSpeedDeviceTest.kt` (`6/6` tests, `TST-CON-058.1`) and extended `ANTBikeSpeedAndCadenceDeviceTest.kt` (`+6` new tests alongside `3` existing `TST-CON-025` tests = `9/9`, `TST-CON-058.2`), all annotated with `// Verifies REQ-CON-058 / TST-CON-058`.
* **UI / Strings / Architecture (`Rule 8.8`)**: `0` `strings*.xml` edits (`Localization Parity: N/A - 0 string changes`); `docs/architecture.md` = None; `docs/architecture/detailed_design/` = None (`REQ-PRO-114`).

---

## 2. Verification Summary (`Tier 1 Targeted` + Locked `emulator-5554` Smoke)
| Check / Suite | Scope | Result | Reference |
| :--- | :--- | :--- | :--- |
| `:app:testDebugUnitTest --rerun` | `ANTBikeSpeedDeviceTest` (`6` new `TST-CON-058.1` tests) | **6/6 PASS** (`0` fail, `0` skip) | `TST-CON-058.1` |
| `:app:testDebugUnitTest --rerun` | `ANTBikeSpeedAndCadenceDeviceTest` (`3` `TST-CON-025` + `6` `TST-CON-058.2`) | **9/9 PASS** (`0` fail, `0` skip) | `TST-CON-058.2` |
| `:app:testDebugUnitTest --rerun` | `ANTBikePowerDeviceTest` (sibling parity regression) | **10/10 PASS** (`0` fail, `0` skip) | `REQ-STB-026` |
| `:app:testDebugUnitTest --rerun` | `ArchitectureConformanceContractTest` (`<= 1,500` LoC, boundaries) | **11/11 PASS** (`0` fail, `0` skip) | `TST-CON-058.3` |
| **Targeted Gradle Total** | `Tier 1 targeted` (4 suites, `0` warnings in modified files) | **36/36 PASS** (`0` fail, `0` skip) | Evidence bundle |
| Python Safety Linters | `lint_cursor_safety`, `lint_lifecycle_safety`, `lint_safe_enum` | **3/3 PASS** | Clean |
| Live Emulator (`emulator-5554`) | `installDebug` + cold launch `MainActivityWithNavigation` (`Pixel_8_Android_15`, locked via `agent_emulator.py exec`) | **PASS** (`0` crashes/exceptions; `Rule 8.5` PNG inspected via `read_file`) | [`ATT-9605_emulator_smoke.png`](img/ATT-9605_emulator_smoke.png) |
| Governance & Automated Gate | `verify_requirement_governance.py --base sprint/2026-11-23_03_Nacht` + `auto_gate.py --ticket ATT-9605 --tier tier-a --write-bundle` | **PASS** (`exit 0`, SHA-256 `manifest.json`) | `REQ-CON-058`, `TST-CON-058` |

---

## 3. Traceability & Rollback State
* **`SYSREQ-TRK-001` -> `REQ-CON-058` (`[Verified]`) -> `TST-CON-058` (`[Verified]`)**: Verified fail-safe `null` early-return (initial & post-motion, including `ANTBikeSpeedDevice.myLog` path), direct `m/s` scaling (`10.0 m/s -> 0.1 s/m`), initial `BigDecimal.ZERO -> pace == null`, 8-tick `SPEED_THRESHOLD` standstill decay to `null` (`0` `Double.POSITIVE_INFINITY`), and negative speed `null` pace across both ANT+ drivers.
* **Branch State**: Committed cleanly on `feature/ATT-9605`; `sprint/2026-11-23_03_Nacht` untouched pending independent Stage 6 Cold Review (`Rule 3.3`).
