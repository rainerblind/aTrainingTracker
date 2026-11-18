# Stage 2: Requirement & Test Specification - ATT-8631

**Ticket**: [ATT-8631](https://rainerblind.atlassian.net/browse/ATT-8631) (`1 SP`, `severity-major`) | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)  
**Target Release**: `V5.0.0` | **Sprint**: `sprint/2026-11-17_03_Nacht` | **Branch**: `feature/ATT-8631`  
**Requirement Mapping**: `REQ-PRO-126` (`[Planned]`, refining `REQ-PRO-049` / `REQ-TRK-083.3`) | **Test Spec ID**: `TST-PRO-126` (`[Proposed]`)  
**Localization parity**: `N/A - no UI strings` | **Date**: 2026-11-18

---

## 1. Requirement Specification (`REQ-PRO-126` `[Planned]`)
* **Attributes**: Priority: `Medium` | Type: `Non-Functional` | Verification Method: `Unit / Review`
* **Implementation**: `app/src/test/java/com/atrainingtracker/banalservice/sensor/SensorTypeUnitsContractTest.kt`, `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`
* **REQ-PRO-126.1 (Zero `app/src/main/` Source Reads in `SensorTypeUnitsContractTest.kt`)**: `SensorTypeUnitsContractTest.kt` SHALL NOT reference `app/src/main/` paths or invoke source-reading APIs (`.readText()`, `readLines()`, `readString()`, `useLines()`, `bufferedReader()`), while preserving JVM reflection verification of `SensorType.values()` (`getInternalUnit()`, `SensorCategory`, `SensorSubCategory`, `SensorValueType`) and bidirectional parity with `docs/architecture/sensor_units.md` and `docs/architecture.md` (`REQ-TRK-083.1`, `REQ-TRK-083.3`).
* **REQ-PRO-126.2 (Static `SensorType.java` Javadoc Enforcement in Governance Tooling)**: `tools/verify_requirement_governance.py` SHALL implement `verify_sensor_type_javadocs()` (invoked by `verify_governance()`) to parse `app/src/main/java/com/atrainingtracker/banalservice/sensor/SensorType.java` and fail non-zero if any enum constant lacks a preceding `/** ... */` Javadoc block documenting `"Internal unit:"` and its declared `internalUnit` literal (cross-checked against `docs/architecture/sensor_units.md`), migrating static source Javadoc verification out of JUnit (`REQ-TRK-083.3`, `TST-TRK-083.1`).
* **REQ-PRO-126.3 (Preservation of `REQ-PRO-049` Guard & Baseline Ratchets)**: `SourceTextReadingAuditContractTest.LEGITIMATE_GOVERNANCE_GUARDS` MUST remain strictly capped at `22` (`REQ-PRO-049.2`) and `app/source-reading-baseline.txt` at `105` with `0` stale entries (`REQ-PRO-049.3`), passing `4/4` tests in `SourceTextReadingAuditContractTest`.
* **REQ-PRO-126.4 (Offline Governance Unit Testing)**: `tools/test_verify_requirement_governance.py` SHALL verify `verify_sensor_type_javadocs()` offline via isolated `tempfile` fixtures (valid Javadocs, missing Javadoc block, missing `"Internal unit:"` / mismatched `internalUnit`) and live repository execution (`Rule 5`).
* **Given-When-Then Acceptance Criteria**:
  * *Given* `SensorTypeUnitsContractTest.kt` and `app/src/test/java/`, *When* `./gradlew :app:testDebugUnitTest --tests com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest --tests com.atrainingtracker.banalservice.sensor.SensorTypeUnitsContractTest` runs, *Then* `SourceTextReadingAuditContractTest` passes `4/4` (`22` guards, `105` baseline, `0` unallowlisted) and `SensorTypeUnitsContractTest` passes `2/2` via JVM reflection and `docs/architecture/`.
  * *Given* synthetic `SensorType.java` `tempfile` fixtures and live `HEAD`, *When* `verify_sensor_type_javadocs()` and `python3 tools/verify_requirement_governance.py` execute, *Then* invalid fixtures fail deterministically and live repository checks exit `0`.
* **System Invariants**: Zero `app/src/main/` edits (`SensorType.java` `ordinal()`/`name()`/`getInternalUnit()` unchanged); `LEGITIMATE_GOVERNANCE_GUARDS == 22`; `app/source-reading-baseline.txt == 105`; `Localization parity: N/A - no UI strings`.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-PRO-126` (`REQ-PRO-126.1`–`REQ-PRO-126.4`), refining `REQ-PRO-049` (`ATT-6186`, `ATT-6731`, `ATT-7525`) and migrating static `SensorType.java` Javadoc verification from `REQ-TRK-083.3` / `TST-TRK-083.1` (`ATT-8579`).
* **Historical Origin & Commit Trace**: `REQ-PRO-049` (`c91e62b6`, `6ec52d7b`, `97133154`) prohibited unallowlisted `src/main` test reads; `ATT-8498` (`d7dec447`) deleted `AdvancedTuningZoneDisplayTest.kt`; `ATT-8579` (`367a412f`) added `SensorTypeUnitsContractTest.kt` reading `SensorType.java`.
* **Root Reason for Existing Formulation**: `ATT-8579` co-located static `SensorType.java` Javadoc parsing inside JUnit `SensorTypeUnitsContractTest.kt` without adding it to the `22`-entry `LEGITIMATE_GOVERNANCE_GUARDS`.
* **Preservation of Core Invariants**: Preserves `REQ-PRO-049.2` (`22` guards), `REQ-PRO-049.3` (`105` baseline, `0` stale), `SensorType.java` `ordinal()`/`name()`/`getInternalUnit()`, `docs/architecture/sensor_units.md` parity, and continuous Javadoc verification in `verify_requirement_governance.py`.

---

## 2. Test Specification (`TST-PRO-126` `[Proposed]`)
* **`TST-PRO-126.1` (`SourceTextReadingAuditContractTest.kt`)**: Assert `SourceTextReadingAuditContractTest` passes `4/4` (`0` unallowlisted readers, `LEGITIMATE_GOVERNANCE_GUARDS.size == 22`, `baseline.size == 105`, `0` stale entries) after removing `src/main` reads from `SensorTypeUnitsContractTest.kt`.
* **`TST-PRO-126.2` (`SensorTypeUnitsContractTest.kt`)**: Assert `allSensorTypes_haveValidInternalUnit()` and `sensorUnitsDoc_matchesAllSensorTypesBidirectionally_andArchitectureLinksIt()` pass `2/2` via JVM reflection (`SensorType.values()`) and `docs/architecture/`.
* **`TST-PRO-126.3` (`tools/test_verify_requirement_governance.py`)**: Offline `tempfile` unit tests for `verify_sensor_type_javadocs()` covering valid constants, missing `/** ... */` Javadoc, missing `"Internal unit:"`, and mismatched `internalUnit` tokens (`Rule 5`).
* **`TST-PRO-126.4` (`tools/verify_requirement_governance.py`)**: Full `python3 tools/verify_requirement_governance.py` and `--check-doc-headers --check-commit-hygiene --check-conflict-markers` exit `0` across live `SensorType.java` and living docs (`Localization parity: N/A - no UI strings`).

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-126.1` | Unit / Contract | `SourceTextReadingAuditContractTest` (`4/4`, `22` guards, `105` baseline) | `REQ-PRO-126.1`, `REQ-PRO-126.3`, `REQ-PRO-049` | Proposed |
| `TST-PRO-126.2` | Unit / Contract | `SensorTypeUnitsContractTest` (`2/2` JVM reflection & `sensor_units.md`) | `REQ-PRO-126.1`, `REQ-TRK-083.1` | Proposed |
| `TST-PRO-126.3` | Python Unit (Offline) | `tools/test_verify_requirement_governance.py` (`verify_sensor_type_javadocs`) | `REQ-PRO-126.2`, `REQ-PRO-126.4` | Proposed |
| `TST-PRO-126.4` | Governance Gate | `tools/verify_requirement_governance.py` (`verify_governance()` & headers) | `REQ-PRO-126.2`, `REQ-PRO-126.4` | Proposed |
