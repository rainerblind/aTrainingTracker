# Stage 5: Walkthrough & Verification - ATT-6688: Wire LiveMmpCurveChart into Cockpit Tracking Layouts with PR Reference Curve and Breakthrough Notifications

**Ticket**: [ATT-6688](https://rainerblind.atlassian.net/browse/ATT-6688) | **Sub-task**: `[Test]`  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2` | **Branch**: `feature/ATT-6688`  
**Requirement Mapping**: `REQ-TRK-058.6` | **Test Mapping**: `TST-TRK-058.6` (`TST-TRK-058.6a`–`d`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-11  

---

## 1. Executive Summary & Verification Overview
* **Cockpit Layout Integration**: Added `SHOW_MMP_CURVE = "ShowMmpCurve"` integer column to `VIEWS_TABLE`, upgrading SQLite schema to DB version 20 via idempotent `onUpgrade` and `CREATE_VIEWS_TABLE_V20`. Implemented `updateShowMmpCurve` in `TrackingViewsDatabaseManager` and `TrackingViewsRepository`. Added `SpatialCockpitToggleCard` for MMP curve in `SensorGridScreen.kt` configuration mode and rendered `LiveMmpCurveChart` in tracking layouts (portrait and landscape) when enabled.
* **Historical PR Reference Curve & Breakthrough Alerts**: Added `WorkoutSummariesDatabaseManager.getHistoricalPowerBenchmarks()` querying `TABLE_EXTREMA_VALUES` for historical peak powers (`5s`, `1m`, `5m`, `20m`), cleanly exposed in `WorkoutRepository`. Extended `LiveMmpCurveData` with `referencePoints` and `getBreakthroughs()`. In `LiveMmpCurveChart.kt`, rendered dashed reference polyline and pins with transient golden PR breakthrough notifications and pin rings (`TTColor.Gold`).
* **Rule 5.4 Test Modernization**: Replaced source-text `File.readLines()` grepping in `LiveMmpCurveChartTest.kt` with behavioral unit tests asserting logarithmic normalization, parity, breakthrough detection, and headroom calculations.
* **Telemetry Streaming**: Extended `TrackingViewModel.kt` to extract rolling means and session peaks from `allSensorData` at 1Hz into `LiveMmpCurveData`.
* **100% 9-Locale Parity**: Added `config_tracking__show_mmp_curve` and `mmp_breakthrough_pr` across all 9 `strings_cockpit.xml` files (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-058.6` | `TST-TRK-058.6a` | `TrackingViewsDbMigrationV20Test` | **PASSED** | `Verified` |
| `REQ-TRK-058.6` | `TST-TRK-058.6b` | `LiveMmpCurveChartTest` | **PASSED** | `Verified` |
| `REQ-TRK-058.6` | `TST-TRK-058.6c` | `TrackingViewModelMmpTest` | **PASSED** | `Verified` |
| `REQ-UI-106` | `TST-TRK-058.6d` | `TranslationParityTest` | **PASSED** | `Verified` |
| `REQ-PRO-022` | `TST-TRK-058.6` | `verify_requirement_governance.py` | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Regression Scope
```text
./gradlew testDebugUnitTest \
  --tests "com.atrainingtracker.trainingtracker.ui.tracking.LiveMmpCurveChartTest" \
  --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingViewModelMmpTest" \
  --tests "com.atrainingtracker.trainingtracker.database.TrackingViewsDbMigrationV20Test" \
  --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"

BUILD SUCCESSFUL in 2m 5s
33 actionable tasks: 2 executed, 31 up-to-date
```

### AAPT2 Resource & Governance Verification
```text
./gradlew processDebugResources
BUILD SUCCESSFUL in 1s (18 up-to-date)

python3 tools/verify_requirement_governance.py --text-file docs/engineering/test_specs/ATT-6688_test_spec.md
PASS: Living documentation master tables match all 9 domain shards with 100% parity.
PASS: Zero unmounted Composable islands detected in git diff.
PASS: Repository encapsulation verified (zero leaky DatabaseManager/Cursor members).
PASS: Zero architecture or system specification modifications detected.
PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified with all 4 mandatory fields.
```

---

## 4. Invariant & Governance Verification
1. **Database Migration Safety (Rule 62)**: Existing user tabs and tiles remain intact upon upgrade; `DatabaseSchemaRegistry` verifies max supported version 20.
2. **Snapshot State Hygiene (Rule 66)**: Zero mutable snapshot writes inside `@Composable` functions; chart derives state purely from incoming immutable models.
3. **Architecture & Encapsulation (Rule 81)**: Zero leaky DatabaseManager or Cursor getters exposed on `WorkoutRepository` or `TrackingViewsRepository`.
4. **Living Documentation**: Synchronized in `docs/requirements/req_tracking_cockpit.md` and `docs/tests/tst_tracking_cockpit.md`.
