# Stage 5: Walkthrough & Verification - ATT-8577: ErtsPostWorkoutRecomputer VERTICAL_SPEED m/h Unit Fix

**Ticket**: `ATT-8577` (`1 SP`, Epic `ATT-166`) | **Target Release**: `V5.0.0`  
**Branch**: `feature/ATT-8577` (rebased onto `sprint/2026-11-17_01_Früh` @ `4a8436218b0e7d6e4458eb05fe0b71475168b473`)  
**Requirement Mapping**: `REQ-FIL-021` (`REQ-FIL-021.1`..`REQ-FIL-021.2`) | **Test Mapping**: `TST-FIL-021` (`TST-FIL-021.1`..`TST-FIL-021.3`)  
**UI Consistency**: `N/A - no UI strings` (`strings*.xml` and `androidTest/` untouched)

---

## 1. Executive Summary & Implemented Changes
1. **`ErtsPostWorkoutRecomputer.bindColumnValue()` (`app/src/main/java/com/atrainingtracker/trainingtracker/database/ErtsPostWorkoutRecomputer.kt:336-383`)**:
   - Converted `alt.verticalSpeedMps` (`m/s`) to `m/h` integer persistence matching `SensorType.VERTICAL_SPEED` (`SensorValueType.INTEGER`, `internalUnit = "m/h"`): when `alt != null && alt.verticalSpeedMps.isFinite()`, computes `Math.round((alt.verticalSpeedMps * 3600.0).coerceIn(-3000.0, 3000.0))` and binds via `stmt.bindLong(bindIdx, vamMph)`; otherwise binds `stmt.bindNull(bindIdx)`.
   - Preserved `SmoothedAltitudeSlopePoint.verticalSpeedMps` (`m/s`), `ALLOWED_CANONICAL_COLUMNS` (`REQ-FIL-017.3`), device-attributed columns (`" ("`), `SLOPE` binding, `KEY_POST_WORKOUT_ERTS_SMOOTHING = false`, and `SensorType.ordinal()`/`name()`.
2. **Unit Documentation Parity (`SensorType.java:378`, `docs/architecture/sensor_units.md:61, 84`)**:
   - Updated `SensorType.VERTICAL_SPEED` Javadoc and `sensor_units.md` to record that `ATT-8577` resolved the legacy `ErtsPostWorkoutRecomputer` `m/s` discrepancy (`[-3000, 3000]` `m/h` integers), retaining the `"ATT-8577"` token verified by `SensorTypeUnitsContractTest.kt:213`.
3. **Unit Tests (`app/src/test/java/com/atrainingtracker/trainingtracker/database/ErtsPostWorkoutRecomputerTest.kt`)**:
   - Extended `testStrictColumnScopeInvariantAndRecomputation` to assert `VERTICAL_SPEED` in `m/h` (`400..1100` `m/h`).
   - Added `testVerticalSpeedRecomputation_convertsClimbAndDescentToIntegerMphAndPreservesDeviceColumns` and `testBindColumnValue_verticalSpeedScalesRoundsClampsAndHandlesNull` covering climb, descent, fractional rounding, `±3.0 m/s -> ±3000 m/h` clamping, `Double.NaN` / `alt == null -> SQLite NULL`, and byte-for-byte untouched device columns.

---

## 2. Requirement & Test Verification Matrix
* **Tier 1 Clean-Room Post-Rebase Execution** (`python3 /home/rainer/AndroidStudioProjects/aTrainingTracker/tools/parallel_sprint_lock.py --timeout 1800 -- ./gradlew :app:testDebugUnitTest --tests "*.ErtsPostWorkoutRecomputerTest" --tests "*.ExtendedRtsAltitudeSlopeSmootherTest" --tests "*.SensorTypeUnitsContractTest"`): **9/9 PASSED** (`0` failures, `0` errors, `0` skipped; `BUILD SUCCESSFUL`). Full-suite Tier 2 (`lintDebug`, `detekt`, `compileDebugAndroidTestKotlin`, full `testDebugUnitTest`) deferred to Sprint Integration Gate; `TranslationParityTest` & `androidTest` skipped (`strings*.xml` & `androidTest/` untouched).

| Requirement | Test Spec | Verification Class & Measured Scope | Result | Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-FIL-021.1` | `TST-FIL-021.1` | `ErtsPostWorkoutRecomputerTest` (5 tests: climb/descent `m/h`, rounding, device invariance, sanity/null guards) | **PASSED** (`5/5`) | `Verified` |
| `REQ-FIL-021.1` | `TST-FIL-021.2` | `ErtsPostWorkoutRecomputerTest.testBindColumnValue_verticalSpeedScalesRoundsClampsAndHandlesNull` (`±3000`, `NaN`/`null`) | **PASSED** | `Verified` |
| `REQ-FIL-021.2` | `TST-FIL-021.3` | `ExtendedRtsAltitudeSlopeSmootherTest` (`2/2`) + `SensorTypeUnitsContractTest` (`2/2`) | **PASSED** (`4/4`) | `Verified` |

---

## 3. Invariant & Governance Verification
* **Living Docs**: `REQ-FIL-021` (`docs/requirements/req_tracking_cockpit.md`) and `TST-FIL-021` (`docs/tests/tst_tracking_cockpit.md`) promoted to `[Verified]`; master indexes synchronized (`docs/requirements.md`: `1212` total, `968` Verified; `docs/tests.md`: `1219` total, `997` Verified).
* **Governance & Submodule**: `python3 tools/verify_requirement_governance.py` exited `0`; `ANT-Android-SDKs` untouched at `0e8cea714a231fb9565170d4ed01eafa868f9798`.
