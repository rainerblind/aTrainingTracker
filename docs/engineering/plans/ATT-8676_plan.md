# Stage 3: Implementation Plan - ATT-8676: Radar Speed Units (`MyHelper.getUnitsId` & Aftermath)

**Ticket**: [ATT-8676](https://rainerblind.atlassian.net/browse/ATT-8676) | **Sub-task**: `[Impl-Plan]` | **Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-18_01_Früh` | **Branch**: `feature/ATT-8676`  
**Requirement Mapping**: `REQ-TRK-088` (`REQ-TRK-088.1`–`.3`, refining `REQ-TRK-083.1`, `REQ-CON-028`) | **Test Mapping**: `TST-TRK-088` (`TST-TRK-088.1`–`.3`)  
**Author**: AI Agent 1 (Implementer, `--as agent1`) | **Date**: 2026-11-18

---

## 1. Problem Description & Background
`SensorType.RADAR_APPROACH_SPEED_mps` formats values via `SpeedFormatter` (`km/h` Metric / `mile/h` Imperial), whereas `MyHelper.getUnitsId()` (`L152`) and `MyHelper.getShortUnitsId()` (`L162`) omit it from the `SPEED_mps` arm and fall back to `R.string.units_speed_basic` (`"m/s"`). Mapping `RADAR_APPROACH_SPEED_mps` alongside `SPEED_mps` in `MyHelper.java` aligns Live Cockpit (`TrackingViewModel`), Dialogs (`EditSensorFieldDialog`, `SensorSourceDialog`), `GetMergedDevicesUseCase`, `PebbleService`, `WorkoutDataMapper`, and `WorkoutRepository.kt:3128` with `RadarTrafficSummaryCard.kt` (`ATT-8440`), without changing internal storage (`"m/s"`), exports, or `WorkoutRepository.kt`.

---

## 2. Traceability & Requirements Mapping
| Requirement | Atomic Steps | Verification / Test Cases | Scope |
| :--- | :--- | :--- | :--- |
| `REQ-TRK-088.1` | Step 1 | `TST-TRK-088.1`, `TST-TRK-088.2` | Map `RADAR_APPROACH_SPEED_mps` in `MyHelper.getUnitsId` & `getShortUnitsId`; verify callers |
| `REQ-TRK-088.2` | Step 2 | `TST-TRK-088.3` | Sync `SensorType.java:440` Javadoc & `docs/architecture/sensor_units.md` (`L76, L91`) |
| `REQ-TRK-088.3` | Steps 1–4 | `TST-TRK-088.1`, `TST-TRK-088.3` | Preserve `internalUnit="m/s"`, `getUnitId()`, FIT/CSV, `WorkoutRepository.kt <= 5,117` LoC, `0` `src/main` reads |

* **UI Consistency**: No UI changes (`N/A - no UI strings`; reuses existing `R.string.units_speed_*` resources).

---

## 3. System Invariants & Preserved Behavior
1. **Storage, Enum & Export Parity**: `RADAR_APPROACH_SPEED_mps` keeps `internalUnit == "m/s"`, `ordinal()`, `name()`, and `@Deprecated getUnitId() == R.string.units_speed_basic`; FIT (`FitFileWriter.kt`) & CSV (`CSVFileWriter.java`) remain raw `m/s`.
2. **Strict File & Scope Boundaries**: `WorkoutRepository.kt` (`<= 5,117` LoC) untouched; `RADAR_CLOSEST_DISTANCE_m`, `RADAR_PASSING_DISTANCE_m`, `W_PRIME_BAL`, `strings_radar.xml`, and `strings.xml` untouched.
3. **Contract & Source-Read Hygiene (`REQ-PRO-049.1`)**: Zero new `app/src/main/` source-text reads (`SourceTextReadingAuditContractTest`: `LEGITIMATE_GOVERNANCE_GUARDS == 22`, `baseline == 105`); `"MyHelper.getUnitsId"` token preserved in `sensor_units.md`; `"Internal unit: {@code m/s}"` preserved in `SensorType.java`.
4. **Parent Human Gate**: Parent `ATT-8676` terminates at `Final Review (Human)`.

---

## 4. Proposed Architectural Changes
* **Component 1 (`MyHelper.java`)**: Add `RADAR_APPROACH_SPEED_mps` to the `SPEED_mps` switch arm in `getUnitsId()` (`L152` -> `getSpeedUnitNameId()`) and `getShortUnitsId()` (`L162` -> `getShortSpeedUnitNameId()`). All downstream callers (`TrackingViewModel:679`, `EditSensorFieldDialog:1176`, `SensorSourceDialog:114`, `GetMergedDevicesUseCase:55`, `PebbleService:464,469`, `WorkoutDataMapper:1048`, `WorkoutRepository:3128`) resolve automatically.
* **Component 2 (`SensorType.java` Javadoc & `docs/architecture/sensor_units.md`)**: Update `SensorType.java:440` Javadoc and `sensor_units.md:76,91` to document `MyHelper.getUnitsId` / `getShortUnitsId` speed mapping (`km/h` / `mile/h`, `mi/h` short) while keeping `"Internal unit:"` and `"MyHelper.getUnitsId"` tokens intact.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Update `MyHelper.java` (`REQ-TRK-088.1`)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/MyHelper.java`
* Changes: Group `case SPEED_mps, RADAR_APPROACH_SPEED_mps -> getSpeedUnitNameId();` at `L152` and `case SPEED_mps, RADAR_APPROACH_SPEED_mps -> getShortSpeedUnitNameId();` at `L162`. Do NOT modify `WorkoutRepository.kt` (`<= 5,117` LoC).

### Step 2: Synchronize `SensorType.java` Javadoc & `sensor_units.md` (`REQ-TRK-088.2`, `REQ-TRK-088.3`)
* Files: `app/src/main/java/com/atrainingtracker/banalservice/sensor/SensorType.java`, `docs/architecture/sensor_units.md`
* Changes: Update `L440` Javadoc display clause (retaining `"Internal unit: {@code m/s}"`, `R.string.units_speed_basic`, `"m/s"`); update `sensor_units.md` `L76` & `L91` (retaining `"MyHelper.getUnitsId"` token and leaving `RADAR_CLOSEST_DISTANCE_m`, `RADAR_PASSING_DISTANCE_m`, and `W_PRIME_BAL` unchanged).

### Step 3: Add `MyHelperRadarUnitsTest.kt` (`TST-TRK-088.1`, `TST-TRK-088.2`)
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/MyHelperRadarUnitsTest.kt` (new, zero `src/main` reads)
* Changes: Assert `getUnitsId` & `getShortUnitsId` for `RADAR_APPROACH_SPEED_mps` under `MyUnits.METRIC` (`km/h`) and `MyUnits.IMPERIAL` (`mile/h` / `mi/h`), `internalUnit == "m/s"`, `getUnitId() == units_speed_basic`, radar distance fallback, and caller unit alignment (`WorkoutDataMapper` / `WorkoutRepository` / `TrackingViewModel`) with `@After` `TrainingApplication.setUnitForTesting(null)`.

### Step 4: Consolidated Verification (`TST-TRK-088.3`, `Rule 7.5`)
* Single consolidated Gradle call under `/tmp/att_build_merge.lock` with `--max-workers=1` (preserving all JUnit XMLs together; never run standalone contract tests separately after the targeted suite):
  `python3 /home/rainer/AndroidStudioProjects/aTrainingTracker/tools/parallel_sprint_lock.py ./gradlew :app:testDebugUnitTest --max-workers=1 --tests "com.atrainingtracker.trainingtracker.MyHelperRadarUnitsTest" --tests "com.atrainingtracker.banalservice.sensor.formater.SpeedFormatterTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.RadarTrafficSummaryCardTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepositoryMarkerTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingViewModelGridTest" --tests "com.atrainingtracker.banalservice.sensor.SensorTypeUnitsContractTest" --tests "com.atrainingtracker.trainingtracker.build.SystemArchitectureDesignContractTest" --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest"`
* Governance check: `python3 tools/verify_requirement_governance.py --repo-root .` (exit `0`).

---

## 6. Verification & Rollback Plan
* **Verification**: Consolidated single-invocation Tier-1 unit + contract suite (`--max-workers=1`) and `verify_requirement_governance.py` in Stage 4/5.
* **Rollback**: Isolated on `feature/ATT-8676`; `git revert` of the 4 touched files restores prior unit mapping with zero SQLite, FIT/CSV, or localization impact (`LOW` risk).
