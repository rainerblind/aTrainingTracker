# Stage 5: Walkthrough & Verification - ATT-8676: Radar Speed Units (`MyHelper.getUnitsId` & Aftermath)

**Ticket**: [ATT-8676](https://rainerblind.atlassian.net/browse/ATT-8676) | **Sub-task**: `[Test]` | **Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-18_01_Früh` | **Branch**: `feature/ATT-8676`  
**Requirement Mapping**: `REQ-TRK-088` (`REQ-TRK-088.1`–`.3`) | **Test Mapping**: `TST-TRK-088` (`TST-TRK-088.1`–`.3`)  
**Author**: AI Agent 1 (Implementer, `--as agent1`) | **Date**: 2026-11-18

---

## 1. Executive Summary & Verification Overview
Resolved the radar approach speed unit mismatch by adding `SensorType.RADAR_APPROACH_SPEED_mps` alongside `SensorType.SPEED_mps` in `MyHelper.getUnitsId()` (`km/h` Metric / `mile/h` Imperial) and `MyHelper.getShortUnitsId()` (`km/h` Metric / `mi/h` Imperial), and synchronized `SensorType.java:440` Javadoc and `docs/architecture/sensor_units.md` (`L76, L91`, v`1.0.1`). `internalUnit = "m/s"`, `@Deprecated SensorType.getUnitId() == R.string.units_speed_basic`, FIT/CSV raw `m/s` exports, `RADAR_CLOSEST_DISTANCE_m`, `RADAR_PASSING_DISTANCE_m`, `W_PRIME_BAL`, `WorkoutRepository.kt` (`5117` LoC), and `values*/strings*.xml` (`Localization Parity: N/A - no UI strings`) remain unchanged.

---

## 2. Requirement & Test Verification Matrix
| Requirement | Test Spec | Verification Method | Result | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-088.1` | `TST-TRK-088.1` | `MyHelperRadarUnitsTest` (Metric & Imperial `getUnitsId`/`getShortUnitsId`, invariants) | **PASSED (`7/7`)** | `Verified` |
| `REQ-TRK-088.1`, `REQ-CON-028` | `TST-TRK-088.2` | `SpeedFormatterTest` (`3/3`), `RadarTrafficSummaryCardTest` (`5/5`), `WorkoutRepositoryMarkerTest` (`3/3`), `TrackingViewModelGridTest` (`7/7`) | **PASSED (`18/18`)** | `Verified` |
| `REQ-TRK-088.2`, `REQ-TRK-088.3`, `REQ-PRO-049.1` | `TST-TRK-088.3` | `SensorTypeUnitsContractTest` (`2/2`), `SystemArchitectureDesignContractTest` (`9/9`), `SourceTextReadingAuditContractTest` (`4/4`), `ArchitectureConformanceContractTest` (`11/11`), `DeadCodeEliminationContractTest` (`4/5`; `1` pre-existing `ATT-8823`), `verify_requirement_governance.py` | **PASSED (`30/31`; `0` ATT-8676 failures)** | `Verified` |

---

## 3. Automated Test Evidence (Single Consolidated `--max-workers=1 --rerun-tasks` Run)
Command (under `/tmp/att_build_merge.lock`): `python3 tools/parallel_sprint_lock.py --timeout 1800 -- ./gradlew :app:testDebugUnitTest --max-workers=1 --rerun-tasks --tests "com.atrainingtracker.trainingtracker.MyHelperRadarUnitsTest" --tests "com.atrainingtracker.banalservice.sensor.formater.SpeedFormatterTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.RadarTrafficSummaryCardTest" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepositoryMarkerTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingViewModelGridTest" --tests "com.atrainingtracker.banalservice.sensor.SensorTypeUnitsContractTest" --tests "com.atrainingtracker.trainingtracker.build.SystemArchitectureDesignContractTest" --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest" --tests "com.atrainingtracker.trainingtracker.build.DeadCodeEliminationContractTest"`

* **Result (`56` tests across `10` JUnit XMLs in `app/build/test-results/testDebugUnitTest/`)**: `55` PASS, `1` FAIL, `0` SKIP (`9/10` suites 100% PASS):
  * `MyHelperRadarUnitsTest`: `7/7` PASS (`0` FAIL, `0` SKIP)
  * `SpeedFormatterTest`: `3/3` PASS (`0` FAIL, `0` SKIP)
  * `RadarTrafficSummaryCardTest`: `5/5` PASS (`0` FAIL, `0` SKIP)
  * `WorkoutRepositoryMarkerTest`: `3/3` PASS (`0` FAIL, `0` SKIP)
  * `TrackingViewModelGridTest`: `7/7` PASS (`0` FAIL, `0` SKIP)
  * `SensorTypeUnitsContractTest`: `2/2` PASS (`0` FAIL, `0` SKIP)
  * `SystemArchitectureDesignContractTest`: `9/9` PASS (`0` FAIL, `0` SKIP)
  * `SourceTextReadingAuditContractTest`: `4/4` PASS (`0` FAIL, `0` SKIP; `0` new `src/main` reads)
  * `ArchitectureConformanceContractTest`: `11/11` PASS (`0` FAIL, `0` SKIP)
  * `DeadCodeEliminationContractTest`: `4/5` PASS (`1` FAIL, `0` SKIP) — pre-existing unrelated `ATT-8823` failure in `testComposePreviewsVisibilityAudit` (`EditDeviceDialog.kt` `RadarLightControlSectionOffline*Preview` declared `internal`; untouched by `ATT-8676`).
* **Governance Verification**:
  * `verify_requirement_governance.py --repo-root . --sync-indexes --check-size --check-doc-headers --check-conflict-markers --check-living-doc-layout`: **exit `0`** (`docs/requirements.md`: Total=`1245`, Verified=`1001`; `docs/tests.md`: Total=`1252`, Verified=`1030`).
  * `verify_requirement_governance.py --repo-root .`: **exit `0`** (all `22` checks PASS, incl. `verify_sensor_type_javadocs()` across `67` `SensorType` constants).
  * `has_verified_test_evidence("TST-TRK-088")`: **`(True, 'Verified by 7 passing testcase(s)')`**.
  * `verify_requirement_governance.py --repo-root . --check-verified-evidence`: **exit `1`** solely due to `2` pre-existing unrelated sprint-base items (`TST-ARC-003` from `ATT-8823` in `EditDeviceDialog.kt` and `TST-TRK-087` from `ATT-8848`), with `0` errors on `TST-TRK-088` / `ATT-8676`.

---

## 4. Hardware / Physical Verification & Visual Consistency
* Pure unit/helper mapping & doc-sync fix (`N/A - no UI strings`, zero Android XML/Compose/manifest changes); skipped `:app:lintDebug` and physical device deployment per `Rule 7.6`.
* **Visual Consistency**: No UI changes.

---

## 5. Invariant & Governance Verification
1. **Storage & Export Invariants**: `RADAR_APPROACH_SPEED_mps.internalUnit == "m/s"`, `ordinal()`, `name()`, `@Deprecated getUnitId() == R.string.units_speed_basic`, FIT/CSV raw `m/s`, and `WorkoutRepository.kt` (`5117` LoC) untouched.
2. **Living Documentation Synchronized**: `REQ-TRK-088` & `TST-TRK-088` promoted to `[Verified]`; `docs/requirements.md` & `docs/tests.md` synced.
3. **Branch & Gate Discipline**: All changes committed on `feature/ATT-8676` (not merged/squashed onto `sprint/2026-11-18_01_Früh`); parent ticket remains governed for Stage 6 Cold Review and `Final Review (Human)`.
