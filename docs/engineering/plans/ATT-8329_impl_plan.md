# Stage 3: Implementation Plan - ATT-8329

**Ticket**: [ATT-8329](https://rainerblind.atlassian.net/browse/ATT-8329) (`1 SP`, Verbess.) | **Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) | **Release**: `V5.0.0`
**Branch**: `feature/ATT-8329` | **Req / Test**: `REQ-FIL-006`/`REQ-FIL-011` (`TST-FIL-002`), `REQ-FIL-004` (`TST-FIL-003`), `REQ-CON-016` (`TST-CON-007`)
**Author**: Lane A Flash (`agent1`) | **Date**: 2026-11-19

---

## 1. Architecture & Layering Overview
* **Scope**: Pure unit & Robolectric test additions/tagging + living-doc `[Verified]` promotion (`0` edits to `app/src/main/`, `ANT-Android-SDKs`, or `docs/architecture.md`; `0` `src/main/` file reads per `REQ-PRO-049.1` / `ATT-8199`; `Localization Parity: N/A - no UI strings`; `Rule 8.5`: N/A).
* **Lane Isolation**: Zero overlap with Lane B / Lane C; 4 user-owned dirty `tools/` files untouched.

---

## 2. Atomic Implementation Steps

| Step | Target Files | Exact Changes | Traces To |
| :--- | :--- | :--- | :--- |
| **1** | `app/src/test/.../banalservice/devices/BatteryDeviceTest.kt` | Update KDoc header (`:43`) and test method docstrings (`L159-272`) with `REQ-CON-016` / `TST-CON-007` tags covering monotonic countdown, step-drop recalibration, lower-bound floor clamping, warmup stabilization, and pause neutrality (`0` logic/mocking changes). | `REQ-CON-016`<br>`TST-CON-007` |
| **2** | `app/src/test/.../trainingtracker/database/WorkoutSamplesDatabaseManagerTest.kt` | Add `@Test` methods tagged `TST-FIL-002` / `REQ-FIL-006` / `REQ-FIL-011` testing `dbManager.calcExtremaValue(mockSummariesDb, baseFileName, extremaType, sensorType)` across all 5 `ExtremaType` modes (`MAX`, `AVG`, `MIN`, `START`, `END`) against real Robolectric SQLite tables named via `WorkoutSamplesDatabaseManager.getTableName(baseFileName)` (`<= 64` chars, non-blank): (a) all-`NULL` sensor column -> `null` (not `0.0`), (b) missing sensor column -> `null`, (c) populated rows (`100.0, 150.0, 200.0`) -> `200.0, 150.0, 100.0, 100.0, 200.0`. Also tag existing `applyAltitudeDriftCorrection` Case A/B tests with `TST-FIL-003` / `REQ-FIL-004`. | `REQ-FIL-006`<br>`REQ-FIL-011`<br>`TST-FIL-002`<br>`TST-FIL-003` |
| **3** | `app/src/test/.../trainingtracker/tracker/LiveWorkoutSessionAltitudeDriftTest.kt` | Add `REQ-FIL-004` / `TST-FIL-003` tags to KDoc and existing Case A/B `applyRetroactiveAltitudeCorrection` tests. | `REQ-FIL-004`<br>`TST-FIL-003` |
| **4** | `app/src/test/.../trainingtracker/database/WorkoutSummariesAltitudeShiftTest.kt` *(new)* | Create dedicated `@RunWith(RobolectricTestRunner::class)` `@Config(sdk = [34])` suite isolated from MockK `WorkoutSummariesDatabaseManagerTest.kt`. In `@Before`/`@After`, call `WorkoutSummariesDatabaseManager.setInstanceForTesting(null)` and delete `WorkoutSummariesDbHelper.DB_NAME` cleanly. Seed `WorkoutSummaries` (`ALTITUDE_STREAM` encoded via `NumericalEncodingUtils.encodeDoubles(...)`, `ASCENDING`, `DESCENDING`) and `TABLE_EXTREMA_VALUES` (`MIN`/`MAX` for `SensorType.ALTITUDE`), execute `shiftAltitudeData()` for Case A (`t0Ms <= 0`, uniform shift) and Case B (`t0Ms > 0 && t1Ms > t0Ms`, piecewise-linear interpolation), and assert `NumericalEncodingUtils.decodeDoubles(stream)` round-trips, `TABLE_EXTREMA_VALUES` Min/Max updates, and recomputed `ASCENDING`/`DESCENDING` inside SQLite transactions (`TST-FIL-003.2`). | `REQ-FIL-004`<br>`TST-FIL-003` |
| **5** | `docs/requirements/req_tracking_cockpit_part1.md`<br>`docs/tests/tst_tracking_cockpit.md`<br>`docs/engineering/test_specs/ATT-8329_test_spec.md`<br>`docs/requirements.md`, `docs/tests.md` | Promote `REQ-FIL-004`, `REQ-FIL-006`, `REQ-CON-016`, `TST-FIL-002`, `TST-FIL-003`, `TST-CON-007` to `[Verified]` in Stage 5 (after Stage 4/Gate 4 pass), sync master indexes, and verify `verify_requirement_governance.py --check-verified-evidence` (for our 3 TSTs). | `REQ-PRO-099`<br>`REQ-PRO-120` |

---

## 3. Targeted Verification Plan (Tier 1 & Governance — Rule 7.5)
* **Code Lock**: Run `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` (`GATE_PASSED`) before Stage 4 edits.
* **Incremental Gradle Tier 1** (`--rerun`, NEVER `--rerun-tasks`):
  ```bash
  python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew :app:testDebugUnitTest --rerun --console=plain \
    --tests "com.atrainingtracker.banalservice.devices.BatteryDeviceTest" \
    --tests "com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManagerTest" \
    --tests "com.atrainingtracker.trainingtracker.database.WorkoutSummariesAltitudeShiftTest" \
    --tests "com.atrainingtracker.trainingtracker.tracker.LiveWorkoutSessionAltitudeDriftTest" \
    --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest"
  ```
* **Governance**: `python3 tools/verify_requirement_governance.py --base-ref refs/heads/sprint/2026-11-19_01_Früh` (`exit 0`).

---

## 4. System Invariants & Rollback Safety
1. `0` edits to `app/src/main/` production code or SQLite schemas; `0` `src/main/` file reads in tests (`REQ-PRO-049.1`).
2. `WorkoutSummariesDatabaseManager.setInstanceForTesting(null)` prevents singleton pollution across Robolectric tests; 4 dirty `tools/` files untouched.
