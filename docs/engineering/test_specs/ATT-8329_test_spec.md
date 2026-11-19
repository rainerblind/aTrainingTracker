# Stage 2: Requirement & Test Specification - ATT-8329

**Ticket**: [ATT-8329](https://rainerblind.atlassian.net/browse/ATT-8329) (`1 SP`, Verbess.) | **Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) | **Release**: `V5.0.0`
**Requirements**: `REQ-FIL-006` / `REQ-FIL-011` (`TST-FIL-002`), `REQ-FIL-004` (`TST-FIL-003`), `REQ-CON-016` (`TST-CON-007`)
**Branch**: `feature/ATT-8329` | **Author**: Lane A Flash (`agent1`) | **Date**: 2026-11-19

---

## 1. Requirement & Scope Summary (No New IDs Required)
All 3 target capabilities are already specified under existing living requirements (`REQ-FIL-006` / `REQ-FIL-011`, `REQ-FIL-004`, `REQ-CON-016`) with valid `Priority`, `Type`, `Verification Method`, and `System Requirement(s)` (`SYSREQ-TRK-003`, `SYSREQ-TRK-004`, `SYSREQ-TRK-006`; `0` net-new `SYS.2`/`SYS.3` impact). `Localization Parity: N/A - no UI strings`.
- **`REQ-FIL-006` / `REQ-FIL-011` (`TST-FIL-002`)**: `WorkoutSamplesDatabaseManager.calcExtremaValue()` SHALL return `null` (never `0.0`) across all 5 `ExtremaType` modes (`MAX`, `AVG`, `MIN`, `START`, `END`) when querying an all-`NULL` sensor column or a column absent from the workout samples table, and accurate extrema when populated.
- **`REQ-FIL-004` (`TST-FIL-003`)**: `LiveWorkoutSession.applyRetroactiveAltitudeCorrection()`, `WorkoutSamplesDatabaseManager.applyAltitudeDriftCorrection()`, and `WorkoutSummariesDatabaseManager.shiftAltitudeData()` SHALL atomically shift sample altitudes, `TABLE_EXTREMA_VALUES` (Min/Max), `ALTITUDE_STREAM`, and recomputed `ASCENDING`/`DESCENDING` in SQLite transactions.
- **`REQ-CON-016` (`TST-CON-007`)**: `BatteryDevice` SHALL maintain monotonic countdown while battery percentage is constant, smooth step-transition drain recalibration, lower-bound floor clamping, warmup stabilization, and pause neutrality.

### Chesterton's Fence Summary (Existing IDs Preserved)
- **Original IDs**: `TST-FIL-002` (`REQ-FIL-006`/`REQ-FIL-011`), `TST-FIL-003` (`REQ-FIL-004`, `ATT-425`), `TST-CON-007` (`REQ-CON-016`, `ATT-1631`).
- **Historical Origin & Reason**: Legacy tests predated `ATT-8145` (`verify_requirement_governance.py --check-verified-evidence`) and lacked explicit `TST-*` tags / complete SQLite coverage.
- **Invariants Preserved**: `0` `app/src/main/` edits, `0` `app/src/main/` test reads (`REQ-PRO-049.1` / `ATT-8199`), existing SQLite schemas & production logic untouched.

---

## 2. Test Specification & Traceability Matrix (`ATT-8329`)

| TST ID | Test File(s) & Environment | Concrete Test Procedure & Expected Result | Linked REQ | Status |
| :--- | :--- | :--- | :--- | :--- |
| **`TST-FIL-002`** | `WorkoutSamplesDatabaseManagerTest.kt` (Robolectric `@Config(sdk=[34])` real SQLite) | Execute `calcExtremaValue()` across all 5 `ExtremaType` modes (`MAX`, `AVG`, `MIN`, `START`, `END`) on: (1) all-`NULL` sensor column -> `null` (not `0.0`); (2) missing sensor column -> `null`; (3) populated sensor rows (`100.0, 150.0, 200.0`) -> `200.0, 150.0, 100.0, 100.0, 200.0`. Tag methods with `TST-FIL-002`. | `REQ-FIL-006`<br>`REQ-FIL-011` | Verified |
| **`TST-FIL-003`** | `LiveWorkoutSessionAltitudeDriftTest.kt`, `WorkoutSamplesDatabaseManagerTest.kt`, **new dedicated** `WorkoutSummariesAltitudeShiftTest.kt` (Robolectric `@Config(sdk=[34])` real SQLite, isolated from MockK `WorkoutSummariesDatabaseManagerTest.kt`) | Tag existing session (`applyRetroactiveAltitudeCorrection`) & samples (`applyAltitudeDriftCorrection`) drift tests with `TST-FIL-003`. In `WorkoutSummariesAltitudeShiftTest.kt`, execute `WorkoutSummariesDatabaseManager.shiftAltitudeData()` for Case A (uniform shift) and Case B (piecewise-linear interpolation), asserting transactional updates to `TABLE_EXTREMA_VALUES` (Min/Max), `ALTITUDE_STREAM`, `ASCENDING`, and `DESCENDING`. | `REQ-FIL-004` | Verified |
| **`TST-CON-007`** | `BatteryDeviceTest.kt` (JVM + MockK) | Tag existing methods (`testRemainingDuration_constantPercentage_countsDownMonotonicallyWithoutDrift`, `testRemainingDuration_stepDrop_recalibratesSmoothly`, `testRemainingDuration_floorClamping_preventsPrematureDepletion`, warmup/pause tests) with `REQ-CON-016` / `TST-CON-007` and execute cleanly. | `REQ-CON-016` | Verified |
