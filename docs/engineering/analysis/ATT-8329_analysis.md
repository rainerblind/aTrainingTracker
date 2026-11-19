# Stage 1 Analysis: ATT-8329 - Executable Verification for Tracking Cockpit (3 TSTs from ATT-8145)

**Ticket**: [ATT-8329](https://rainerblind.atlassian.net/browse/ATT-8329) (`1 SP`, Verbess.) | **Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) | **Release**: `V5.0.0`
**Branch**: `feature/ATT-8329` (off `sprint/2026-11-19_01_Früh` @ `3c05aa80`) | **Author**: Lane A Flash (`agent1`) | **Date**: 2026-11-19

## 1. Problem Statement & Root Cause Analysis
During ASPICE 4.0 Finding M1 triage (`ATT-8145`), 3 test specifications in `docs/tests/tst_tracking_cockpit.md` lacked executable test-code tags (`TST-*`) or complete unit test backing and were downgraded to `[Implemented]`: `TST-FIL-002`, `TST-FIL-003`, and `TST-CON-007`.

## 2. Independent Peer Review & Verification (Codebase Audit)
1. **`TST-CON-007` (`REQ-CON-016`, `tst_tracking_cockpit.md:297`)**: **100% of specified test procedures already exist** in `BatteryDeviceTest.kt:159-272` (`testRemainingDuration_constantPercentage_countsDownMonotonicallyWithoutDrift`, `testRemainingDuration_stepDrop_recalibratesSmoothly`, `testRemainingDuration_floorClamping_preventsPrematureDepletion`, plus warmup/pause tests). Gap is strictly missing `TST-CON-007` / `REQ-CON-016` tags in `BatteryDeviceTest.kt` and `Component` entry in `tst_tracking_cockpit.md:299`.
2. **`TST-FIL-003` (`REQ-FIL-004`, `tst_tracking_cockpit.md:45`)**: Atomic altitude correction is implemented across `LiveWorkoutSession.applyRetroactiveAltitudeCorrection` (`LiveWorkoutSession.java:394`), `WorkoutSamplesDatabaseManager.applyAltitudeDriftCorrection`, and `WorkoutSummariesDatabaseManager.shiftAltitudeData` (`:1466`, transactional update of `TABLE_EXTREMA_VALUES` + `ALTITUDE_STREAM` + `ASCENDING`/`DESCENDING`). In-memory session and sample-DB corrections are already tested in `LiveWorkoutSessionAltitudeDriftTest.kt` and `WorkoutSamplesDatabaseManagerTest.kt` (missing `TST-FIL-003` tags), whereas `WorkoutSummariesDatabaseManager.shiftAltitudeData` currently has `0` direct unit tests.
3. **`TST-FIL-002` (`REQ-FIL-006`, `REQ-FIL-011`, `tst_tracking_cockpit.md:15`)**: `ATT-8145_unbacked_triage.csv:195` misattributed `TST-FIL-002` to `Filter*Test.kt` (`MyFilter`), whereas `tst_tracking_cockpit.md:18` specifies `WorkoutSamplesDatabaseManager.calcExtremaValue()` (`:119`) returning `null` (not `0.0`) for all-`NULL` or missing sensor columns. Currently `0` non-mocked unit tests exercise `calcExtremaValue()` against SQLite.

## 3. User Scope Grounding & Lane Isolation (ATT-1250)
- **In-Scope (`1 SP` confirmed)**: Executable Robolectric/JVM unit tests & `TST-*` tagging for `TST-FIL-002`, `TST-FIL-003`, and `TST-CON-007`; promote those 3 TSTs and linked REQs (`REQ-FIL-004`, `REQ-FIL-006`, `REQ-CON-016`) to `[Verified]` once tests pass in Stage 4/5.
- **Out-of-Scope**: `0` `app/src/main/` edits; `0` `src/main/` reads in tests (`REQ-PRO-049.1`); `Localization Parity: N/A`; `TST-TRK-087` untouched; 4 dirty `tools/` files untouched.

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
- **Original Targets**: `REQ-FIL-006` (`REQ-FIL-011`) -> `TST-FIL-002`; `REQ-FIL-004` (`ATT-425`/`ATT-38`) -> `TST-FIL-003`; `REQ-CON-016` (`ATT-1631`) -> `TST-CON-007`.
- **Origin & Root Reason**: Legacy pre-tag tests (`ATT-425`, `ATT-1631`, `ATT-6127`) predated `verify_requirement_governance.py --check-verified-evidence` (`ATT-8145`); `ATT-8145` downgraded unbacked items to prevent M1 false verification claims.
- **Preservation of Core Invariants**: Existing `REQ-FIL-004`, `REQ-FIL-006`, `REQ-FIL-011`, `REQ-CON-016` specifications, production SQLite schemas, and `SourceTextReadingAuditContractTest` invariants remain unchanged.

## 5. Change-Request Impact Analysis (SUP.10)
- **Affected Requirements**: `REQ-FIL-004` / `TST-FIL-003`, `REQ-FIL-006` / `TST-FIL-002`, `REQ-CON-016` / `TST-CON-007` (`[Implemented] -> [Verified]` post-execution). **Zero `SYS.2`/`SYS.3` impact** (`REQ-PRO-131.4`: unit-test & evidence backfill only).
- **Affected Architecture Elements**: Test files only (`BatteryDeviceTest.kt`, `LiveWorkoutSessionAltitudeDriftTest.kt`, `WorkoutSamplesDatabaseManagerTest.kt`, `WorkoutSummariesDatabaseManagerTest.kt`) + `docs/requirements/req_tracking_cockpit_part1.md`, `docs/tests/tst_tracking_cockpit.md`, `docs/requirements.md`, `docs/tests.md`.
- **Verification Impact & Test Surface**: Incremental `./gradlew :app:testDebugUnitTest --rerun --tests ...` on `BatteryDeviceTest`, `LiveWorkoutSessionAltitudeDriftTest`, `WorkoutSamplesDatabaseManagerTest`, `WorkoutSummariesDatabaseManagerTest`, plus `verify_requirement_governance.py --check-verified-evidence`.
- **Risk Assessment & Invariants**: **LOW** (`1 SP`, test-only). Zero production code edits; zero `src/main` reads; all 3 TSTs verified by executable automated tests (no physical-only deferral needed).

## 6. Architectural Strategy
1. Tag `BatteryDeviceTest.kt` with `TST-CON-007` / `REQ-CON-016`.
2. Tag `LiveWorkoutSessionAltitudeDriftTest.kt` + `WorkoutSamplesDatabaseManagerTest.kt` with `TST-FIL-003` and add `WorkoutSummariesDatabaseManager.shiftAltitudeData` Robolectric test in `WorkoutSummariesDatabaseManagerTest.kt`.
3. Add `WorkoutSamplesDatabaseManager.calcExtremaValue()` Robolectric SQLite tests (all-`NULL`, missing column, populated values across `MAX`/`MIN`/`AVG`/`START`/`END`) in `WorkoutSamplesDatabaseManagerTest.kt` tagged `TST-FIL-002`.
