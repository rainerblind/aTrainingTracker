# Stage 5: Walkthrough & Verification - ATT-8579: Canonical SensorType Storage and Display Units

**Ticket**: `ATT-8579` (`2 SP`, Epic `ATT-431`) | **Target Release**: `V5.0.0`  
**Branch**: `feature/ATT-8579` (base `sprint/2026-11-17_01_Früh` @ `e3c278bb`, Stage 4 Commit `cb131de1`)  
**Requirement Mapping**: `REQ-TRK-083` (`REQ-TRK-083.1`..`REQ-TRK-083.3`) | **Test Mapping**: `TST-TRK-083` (`TST-TRK-083.1`..`TST-TRK-083.3`)  
**UI Consistency**: `N/A - no UI or strings modified`

---

## 1. Executive Summary & Implemented Deliverables
1. **Canonical Unit Data Dictionary (`docs/architecture/sensor_units.md`, `docs/architecture.md` §5.4)**:
   - Created a 67-row data dictionary covering every `SensorType` constant with `SensorCategory`, `SensorSubCategory`, internal SQLite/sensor-bus storage unit, `SensorValueType`, range/sentinels (`BATTERY_REMAINING_TIME` `-1`/`<= -2`, `PACE_spm` `MAX_PACE`, `HRV_DFA_A1` / `POWER_TO_WEIGHT` / `W_PRIME_BAL` `< 0`/non-finite), `MyFormatter` & `MyHelper.getUnitsId()` display mapping, and export units (`FitFileWriter.kt`, `TCXFileWriter.kt`, `GPXFileWriter.java`, `CSVFileWriter.java`, `GCFileWriter.java`).
   - Documented observed legacy discrepancies (`VERTICAL_SPEED` vs. `ErtsPostWorkoutRecomputer` `ATT-8577`, `W_PRIME_BAL` `PowerToWeightFormatter`, `MyHelper.getUnitsId()` radar fallback) verbatim with zero runtime modifications.
2. **Code-Level `internalUnit` Metadata & Javadoc (`SensorType.java`)**:
   - Added `@NonNull private final String internalUnit`, `@NonNull public String getInternalUnit()`, and per-constant `/** ... */` Javadoc across all 67 enum constants in `app/src/main/java/com/atrainingtracker/banalservice/sensor/SensorType.java`.
   - Preserved all 67 `ordinal()` positions (`Parcelable` `CREATOR`), `name()` identifiers (`WorkoutSamplesDatabaseManager.makeColumns`), `@Deprecated getUnitId()`, and runtime formatters/exporters.
3. **Automated Bidirectional Parity Contract (`SensorTypeUnitsContractTest.kt`)**:
   - Added `app/src/test/java/com/atrainingtracker/banalservice/sensor/SensorTypeUnitsContractTest.kt` enforcing non-blank `internalUnit` and per-constant Javadoc on all 67 `SensorType.values()`, exact bidirectional set/metadata parity with `docs/architecture/sensor_units.md`, and `docs/architecture.md` cross-link verification.

---

## 2. Requirement & Test Verification Matrix
* **Tier 1 Targeted Execution** (`./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.banalservice.sensor.SensorTypeUnitsContractTest" --tests "com.atrainingtracker.banalservice.sensor.SensorTypeFormatterTest" --tests "com.atrainingtracker.banalservice.sensor.SensorTypeMmpTest" --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest" :app:compileDebugAndroidTestKotlin` under `parallel_sprint_lock.py`): **28/28 PASSED** (`0` failures, `0` errors, `0` skipped) + `:app:compileDebugAndroidTestKotlin` **PASSED** (`BUILD SUCCESSFUL`). Full-repo Tier 2 suite deferred to Sprint Integration Gate.

| Requirement | Test Spec | Verification Class & Measured Scope | Result | Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-083.2`, `REQ-TRK-083.3` | `TST-TRK-083.1` | `SensorTypeUnitsContractTest.allSensorTypes_haveValidInternalUnitAndPrecedingJavadoc` (67 constants) | **PASSED** | `Verified` |
| `REQ-TRK-083.1`, `REQ-TRK-083.3` | `TST-TRK-083.2` | `SensorTypeUnitsContractTest.sensorUnitsDoc_matchesAllSensorTypesBidirectionally_andArchitectureLinksIt` | **PASSED** | `Verified` |
| `REQ-TRK-083.1`..`REQ-TRK-083.3` | `TST-TRK-083.3` | `SensorTypeFormatterTest` (`4/4`), `SensorTypeMmpTest` (`2/2`), `AspiceSweAuditContractTest` (`11/11`), `ArchitectureConformanceContractTest` (`9/9`), `:app:compileDebugAndroidTestKotlin` | **PASSED** | `Verified` |

---

## 3. Invariant & Governance Verification
1. **Living Documentation**: `REQ-TRK-083` (`docs/requirements/req_tracking_cockpit.md`) and `TST-TRK-083` (`docs/tests/tst_tracking_cockpit.md`) promoted to `[Verified]`; master indexes synchronized (`docs/requirements.md`: `1208` total, `971` Verified; `docs/tests.md`: `1215` total, `1000` Verified).
2. **Governance & Submodule Hygiene**: `verify_requirement_governance.py` and `--text-file docs/engineering/walkthroughs/ATT-8579_walkthrough.md` passed (`exit 0`); `ANT-Android-SDKs` untouched at `0e8cea714a231fb9565170d4ed01eafa868f9798`; zero UI strings or APK runtime behavior modified (`N/A - no UI or strings modified`).
