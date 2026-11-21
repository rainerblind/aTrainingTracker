# Stage 5: Verification & Walkthrough - ATT-6176: `ANTBikePowerDevice.onNewCalculatedWheelSpeed` Null Guard & Standstill Pace

**Ticket**: `ATT-6176` (`1 SP`, `Bug`) | **Parent Epic**: `ATT-6033`  
**Target Release**: `V5.0.0` (`Rule 8.3`) | **Active Sprint**: `2026-11-21_02_Spät` (Sprint 469)  
**Branch**: `feature/ATT-6176` (Code SHA `8d76a1f4` — preserved intact for Stage 6 Cold Review) | **Date**: 2026-11-21  
**Requirement**: `REQ-STB-026` (`[Verified]`) | **Test Spec**: `TST-STB-026` (`[Verified]`)

---

## 1. Delivered Changes (`app/` & Living Docs)
* **Pre-Dereference Null Guard & Standstill Pace (`ANTBikePowerDevice.java`, `395 -> 397` LoC `<= 1,500`)**:
  * In `onNewCalculatedWheelSpeed` (`:297-312`), moved `if (calculatedWheelSpeed == null) { return; }` before `createWheelSpeedSensors()` and before `double speed_mps = calculatedWheelSpeed.doubleValue() / 3.6;`, preventing NPEs and preventing premature `SPEED_mps`/`PACE_spm` sensor & `DevicesDatabaseManager.putBikePowerSensorFlags` mutations on `null` callbacks (`REQ-STB-026.1`).
  * Replaced `mPaceSensor.newValue(1 / speed_mps)` with `mPaceSensor.newValue(speed_mps > 0.0 ? 1.0 / speed_mps : null);` (`REQ-STB-026.2`), preventing `Double.POSITIVE_INFINITY` emission at standstill (`speed_mps <= 0.0`) while preserving `ThresholdSensor(..., SPEED_THRESHOLD = 8)` initial `null` retention and 8-tick decay to `null`.
* **Unit Tests (`ANTBikePowerDeviceTest.kt`, `236 -> 363` LoC)**:
  * Added `testANTBikePower_nullCalculatedWheelSpeed_returnsWithoutCreatingSensorsOrMutatingDb`, `testANTBikePower_initialZeroWheelSpeed_setsZeroSpeedAndNullPace`, `testANTBikePower_positiveWheelSpeed_computesMetersPerSecondAndFinitePace`, and `testANTBikePower_zeroWheelSpeedAfterMotion_decaysPaceToNullAtThreshold` (all tagged `// Verifies REQ-STB-026 / TST-STB-026`).
* **UI / Strings / Architecture (`Rule 8.8`)**: `0` `strings*.xml` edits (`Localization Parity: N/A`); `docs/architecture.md` = None; `docs/architecture/detailed_design/` = None (`REQ-PRO-114`).

---

## 2. Verification Summary (`Rule 7.5` Doc-Only Fast Path)
*Zero source/test edits occurred after Gate 4 (`ATT-9566`) @ `8d76a1f4`; Gate 4's verified test evidence is reused verbatim (zero redundant Gradle re-runs). Live `emulator-5554` verification runs independently in Stage 6 Cold Review (`Rule 3.3`).*

| Check / Suite | Scope | Result (`@ 8d76a1f4`) | Reference |
|---|---|---|---|
| `:app:testDebugUnitTest --rerun` | `ANTBikePowerDeviceTest` (incl. 4 new `ATT-6176` tests) | **10/10 PASS** (`0` fail, `0` skip) | `TST-STB-026.1`, `.2` |
| `:app:testDebugUnitTest --rerun` | `ArchitectureConformanceContractTest` (`<= 1,500` LoC, boundaries) | **11/11 PASS** (`0` fail, `0` skip) | `TST-STB-026.3` |
| **Targeted Gradle Total** | `Tier 1 targeted` (`ANTBikePowerDeviceTest` + `ArchitectureConformanceContractTest`) | **21/21 PASS** (`0` fail, `0` skip) | Gate 4 `ATT-9566` |
| Python Safety Linters | `lint_cursor_safety`, `lint_lifecycle_safety`, `lint_safe_enum`, `check_secrets_hygiene` | **4/4 PASS** (`9,262` tracked files) | Clean |
| Living Doc & Governance | `verify_requirement_governance.py --sync-indexes --check-verified-evidence --check-living-doc-layout --test-results-dir /tmp/no_xmls` | **PASS** (`exit 0`, `0` unbacked) | `REQ-STB-026`, `TST-STB-026` |

---

## 3. Traceability & Bug-Fix Evidence (`REQ-PRO-121.2`)
* **`REQ-STB-026` (`[Verified]`) -> `TST-STB-026` (`[Verified]`) -> `ANTBikePowerDeviceTest.kt`**: Verified fail-safe `null` early-return, finite positive pace (`10.0 m/s -> 0.1 s/m`), initial `BigDecimal.ZERO -> pace == null`, and 8-tick standstill decay back to `null` (`0` `Double.POSITIVE_INFINITY`), plus `ANTBikePowerDevice.java = 397 <= 1,500` LoC.
* **Branch State**: Committed on `feature/ATT-6176`; `sprint/2026-11-21_02_Spät` untouched pending Stage 6 Cold Review (`Rule 3.3`).
