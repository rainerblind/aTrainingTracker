# Stage 2: Requirement & Test Specification - ATT-8579: Canonical SensorType Storage and Display Units

**Ticket**: `ATT-8579` (`2 SP`, Epic `ATT-431`) | **Branch**: `feature/ATT-8579` | **Date**: 2026-11-17  
**Requirement**: `REQ-TRK-083` (`REQ-TRK-083.1`..`REQ-TRK-083.3`) | **Test Spec**: `TST-TRK-083` (`TST-TRK-083.1`..`TST-TRK-083.3`)  
**Author**: AI Agent 1 (`--as agent1`) | **Localization Parity**: `N/A - no UI strings`

---

## 1. Requirement Specification (`REQ-TRK-083`)

### 1.1 Attributes
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit | **System Requirement(s)**: `SYSREQ-TRK-001`, `SYSREQ-TRK-003`

### 1.2 Functional & Architectural Requirements
1. **Canonical Sensor Unit Data Dictionary (`REQ-TRK-083.1`)**:
   - `docs/architecture/sensor_units.md` (linked from `docs/architecture.md`) SHALL define a 67-row data dictionary covering all `SensorType.values()` constants with: enum `name()`, `SensorCategory`, `SensorSubCategory`, internal SQLite/sensor-bus storage unit, `SensorValueType`, valid range & sentinels (`BATTERY_REMAINING_TIME`: `-1` charging, `<= -2` stabilizing; `PACE_spm`: `> MAX_PACE` `"~~"`; `HRV_DFA_A1` / `POWER_TO_WEIGHT` / `W_PRIME_BAL`: `< 0`/non-finite `"--.--"`/`"--"`), UI `MyFormatter` + `MyHelper.getUnitsId()` Metric/Imperial conversion, and `FitFileWriter.kt` / `TCXFileWriter.kt` / `GPXFileWriter.java` / `CSVFileWriter.java` / `GCFileWriter.java` export units.
   - Observed legacy discrepancies (`W_PRIME_BAL` emitting `kJ` with `PowerToWeightFormatter`; `MyHelper.getUnitsId()` radar distance/speed fallback) SHALL be documented verbatim without altering runtime behavior.
2. **Code-Level `internalUnit` Metadata & Javadoc (`REQ-TRK-083.2`)**:
   - `app/src/main/java/com/atrainingtracker/banalservice/sensor/SensorType.java` SHALL add `private final String internalUnit`, `public String getInternalUnit()`, and per-constant Javadoc on all 67 enum constants documenting internal storage unit, sentinels, and display/export conversion.
   - Enum `ordinal()` (`Parcelable`), `name()` (`WorkoutSamplesDatabaseManager.makeColumns`), `@Deprecated getUnitId()`, and runtime formatters/exporters MUST remain unchanged (`N/A - no UI strings`).
3. **Automated Bidirectional Parity Contract (`REQ-TRK-083.3`)**:
   - `app/src/test/java/com/atrainingtracker/banalservice/sensor/SensorTypeUnitsContractTest.kt` SHALL enforce 100% coverage of `SensorType.values()` (`getInternalUnit()` + per-constant Javadoc) and bidirectional set/metadata parity with `docs/architecture/sensor_units.md` + cross-link in `docs/architecture.md`.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* all 67 constants in `SensorType.values()`, *When* `SensorTypeUnitsContractTest` inspects `getInternalUnit()`, `SensorType.java` Javadoc, `docs/architecture/sensor_units.md`, and `docs/architecture.md`, *Then* 100% of constants have non-blank `internalUnit`, per-constant Javadoc, and matching documented rows with zero missing or orphaned entries.
* *Given* existing consumers (`CREATOR`, `WorkoutSamplesDatabaseManager`, `MyFormatter`, `MyHelper`, `FitFileWriter`), *When* metadata/docs are added, *Then* `ordinal()`, `name()`, `getUnitId()`, SQLite schema, and runtime outputs remain identical.

### 1.4 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-TRK-083`, complementing `REQ-TRK-070` (*SensorType Taxonomy*) and `REQ-PRO-043` (*SWE.2 Architecture Conformance*).
* **Preservation of Core Invariants**: Preserves `Parcelable` `ordinal()`, `WorkoutSamples.db` column names (`name()`), `@Deprecated getUnitId()`, and zero runtime math/formatter/exporter modifications (`ErtsPostWorkoutRecomputer` `ATT-8577` untouched).

---

## 2. Test Specification (`TST-TRK-083`)

1. **`TST-TRK-083.1` (`SensorTypeUnitsContractTest.kt` — Code Coverage)**:
   - Assert every constant in `SensorType.values()` returns non-null, non-blank `getInternalUnit()` and has a preceding `/** ... */` Javadoc comment in `SensorType.java`.
2. **`TST-TRK-083.2` (`SensorTypeUnitsContractTest.kt` — Docs & Cross-Link Parity)**:
   - Parse `docs/architecture/sensor_units.md` table and assert exact bidirectional parity (`docConstants == SensorType.values().map { it.name }.toSet()`), matching `SensorCategory`, `SensorValueType`, and `internalUnit`, sentinel/export notes, and `docs/architecture.md` link.
3. **`TST-TRK-083.3` (Targeted JVM Regression Suite)**:
   - Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.sensor.SensorTypeUnitsContractTest" --tests "com.atrainingtracker.banalservice.sensor.SensorTypeFormatterTest" --tests "com.atrainingtracker.banalservice.sensor.SensorTypeMmpTest" --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest"`.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-083.1` | Unit / Contract | `SensorTypeUnitsContractTest`: `getInternalUnit()` + Javadoc across 67 constants | `REQ-TRK-083.2`, `REQ-TRK-083.3` | Planned |
| `TST-TRK-083.2` | Unit / Contract | `SensorTypeUnitsContractTest`: bidirectional `sensor_units.md` & `architecture.md` parity | `REQ-TRK-083.1`, `REQ-TRK-083.3` | Planned |
| `TST-TRK-083.3` | Regression | `SensorTypeFormatterTest`, `SensorTypeMmpTest`, `AspiceSweAuditContractTest`, `ArchitectureConformanceContractTest` | `REQ-TRK-083.1`..`REQ-TRK-083.3` | Planned |
