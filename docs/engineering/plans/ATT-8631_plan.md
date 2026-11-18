# Stage 3: Implementation Plan - ATT-8631: Eliminate Source-Text Reading in SensorTypeUnitsContractTest

**Ticket**: [ATT-8631](https://rainerblind.atlassian.net/browse/ATT-8631) | **Sub-task**: `[Impl-Plan]` | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-17_03_Nacht` | **Branch**: `feature/ATT-8631`  
**Requirement Mapping**: `REQ-PRO-126` (`REQ-PRO-126.1`–`.4`, refining `REQ-PRO-049` / `REQ-TRK-083.3`) | **Test Mapping**: `TST-PRO-126` (`TST-PRO-126.1`–`.4`)  
**Author**: AI Agent 1 (Implementer, `--as agent1`) | **Date**: 2026-11-18

---

## 1. Problem Description & Background
`SourceTextReadingAuditContractTest.testSourceReadingAudit_noUnallowlistedSourceReaders` (`REQ-PRO-049.1`) fails on `SensorTypeUnitsContractTest.kt` (`:50, 138, 140`) reading `app/src/main/.../SensorType.java` via `readText()` for Javadoc parity (`ATT-8579`). Upstream `ATT-8498` (`d7dec447`) already deleted `AdvancedTuningZoneDisplayTest.kt`. Removing `src/main` reads from `SensorTypeUnitsContractTest.kt` and migrating static `SensorType.java` Javadoc checks into `tools/verify_requirement_governance.py` fixes the failure without inflating `LEGITIMATE_GOVERNANCE_GUARDS` (`22`).

---

## 2. Traceability & Requirements Mapping
| Requirement | Atomic Steps | Verification / Test Cases | Scope |
| :--- | :--- | :--- | :--- |
| `REQ-PRO-126.1` | Step 1 | `TST-PRO-126.2` (`SensorTypeUnitsContractTest` `2/2`) | Remove `SensorType.java` reads; keep `SensorType.values()` & `sensor_units.md` checks |
| `REQ-PRO-126.2` | Step 2 | `TST-PRO-126.4` (`verify_requirement_governance.py` exit `0`) | Add `verify_sensor_type_javadocs()` wired into `verify_governance()` |
| `REQ-PRO-126.3` | Steps 1, 4 | `TST-PRO-126.1` (`SourceTextReadingAuditContractTest` `4/4`) | Keep `LEGITIMATE_GOVERNANCE_GUARDS == 22` & `baseline == 105` (`0` stale) |
| `REQ-PRO-126.4` | Steps 3, 4 | `TST-PRO-126.3`, `TST-PRO-126.4` (`unittest` + governance) | Offline `tempfile` unit tests & live `SensorType.java` verification |

* **UI Consistency**: N/A — no UI changes (`Localization Parity: N/A - no UI strings`).

---

## 3. System Invariants & Preserved Behavior
1. **Zero Production Code Changes**: `app/src/main/` (`SensorType.java` `ordinal()`, `name()`, `getInternalUnit()`, `CREATOR`) remains completely unmodified.
2. **Audit Guard & Baseline Caps**: `SourceTextReadingAuditContractTest.LEGITIMATE_GOVERNANCE_GUARDS` stays strictly at `22` (`REQ-PRO-049.2`); `app/source-reading-baseline.txt` stays at `105` (`REQ-PRO-049.3`).
3. **Contract Coverage Parity**: Every `SensorType` constant continues to enforce non-blank `internalUnit`, preceding `/** ... */` Javadoc containing `"Internal unit:"` + unit literal, and bidirectional `docs/architecture/sensor_units.md` / `docs/architecture.md` parity (`REQ-TRK-083`).
4. **Parent Human Gate**: Parent `ATT-8631` terminates at `Final Review (Human)`.

---

## 4. Proposed Architectural Changes
* **Component 1 (`SensorTypeUnitsContractTest.kt`)**: Keep strictly JVM reflection (`SensorType.values()`) and markdown doc verification (`docs/architecture/sensor_units.md`, `docs/architecture.md`). Remove `SensorType.java` path resolution (`:50`), `parsePrecedingJavadocs()` (`:87-131`), and `sensorTypeFile.readText()` (`:138,140`); rename test method to `allSensorTypes_haveValidInternalUnit()`.
* **Component 2 (`tools/verify_requirement_governance.py`)**: Add `verify_sensor_type_javadocs(repo_root=None, require_file=False)` wired into `verify_governance()` (plus `--check-sensor-javadocs` CLI flag). Parses `SensorType.java` enum constants via block/Javadoc scanning (`/** ... */`), extracts each constant's declared string `internalUnit` literal, asserts preceding Javadoc contains `"Internal unit:"` and that literal, and cross-checks `docs/architecture/sensor_units.md` rows.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Refactor `SensorTypeUnitsContractTest.kt` (`REQ-PRO-126.1`, `REQ-PRO-126.3`)
* Files: `app/src/test/java/com/atrainingtracker/banalservice/sensor/SensorTypeUnitsContractTest.kt`
* Changes: Update `findRepoRoot()` to check `docs/architecture/sensor_units.md` & `docs/architecture.md` (zero `app/src/main/` path); delete `parsePrecedingJavadocs()` and `sensorTypeFile.readText()`; retain all `internalUnit` and `sensor_units.md`/`architecture.md` assertions in `allSensorTypes_haveValidInternalUnit()` and `sensorUnitsDoc_matchesAllSensorTypesBidirectionally_andArchitectureLinksIt()`.

### Step 2: Add `verify_sensor_type_javadocs()` to `tools/verify_requirement_governance.py` (`REQ-PRO-126.2`)
* Files: `tools/verify_requirement_governance.py`
* Changes: Implement `verify_sensor_type_javadocs(repo_root=None, require_file=False)` parsing `SensorType.java` enum constants + preceding `/** ... */` Javadoc + `"Internal unit:"` + constructor `internalUnit` literal (cross-checked against `docs/architecture/sensor_units.md`), wire into `verify_governance()`, and expose `--check-sensor-javadocs`.

### Step 3: Add Offline Python Unit Tests (`REQ-PRO-126.4`)
* Files: `tools/test_verify_requirement_governance.py`
* Changes: Add `tempfile`-isolated unit tests (`Rule 5`) covering valid constants, missing Javadoc block, missing `"Internal unit:"`, and mismatched `internalUnit` vs Javadoc/`sensor_units.md`, plus live `--check-sensor-javadocs` CLI execution.

### Step 4: Verify Gradle Contracts & Governance (`TST-PRO-126.1`–`.4`)
* Commands (under `tools/parallel_sprint_lock.py` for Gradle):
  * `python3 -m unittest tools/test_verify_requirement_governance.py`
  * `python3 tools/verify_requirement_governance.py --check-sensor-javadocs` and full `python3 tools/verify_requirement_governance.py`
  * `python3 /home/rainer/AndroidStudioProjects/aTrainingTracker/tools/parallel_sprint_lock.py ./gradlew :app:testDebugUnitTest --tests com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest --tests com.atrainingtracker.banalservice.sensor.SensorTypeUnitsContractTest --tests com.atrainingtracker.trainingtracker.build.DetailedDesignParityContractTest --tests com.atrainingtracker.trainingtracker.build.InterfaceSignatureDriftContractTest`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted Python & Gradle suites pass 100% (`SourceTextReadingAuditContractTest` `4/4`, `SensorTypeUnitsContractTest` `2/2`, `verify_requirement_governance.py` `exit 0`); `REQ-PRO-126`/`TST-PRO-126` promoted to `[Verified]` in Stage 5.
* **Rollback**: Isolated on `feature/ATT-8631`; branch drop restores `sprint/2026-11-17_03_Nacht` without side effects.
