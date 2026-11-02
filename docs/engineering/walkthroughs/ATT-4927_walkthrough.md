# Stage 5: Walkthrough & Verification - ATT-4927: Sport-Dependent Bar Support, Empty Tile Fallback & Upgrade Parity

**Ticket**: [ATT-4927](https://rainerblind.atlassian.net/browse/ATT-4927)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-TRK-046` (`REQ-TRK-046.1`..`REQ-TRK-046.6`)  
**Test Mapping**: `TST-TRK-046` (`TST-TRK-046.1`..`TST-TRK-046.6`)  
**Branch**: `feature/ATT-4927`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`)  
**Date**: 2026-11-01  

---

## 1. Executive Summary & Verification Overview
ATT-4927 resolves issues identified in Sprint Review 2026-44.1:
1. Unified sport-dependent bar gating via canonical predicate `isBarSupported(SensorType, BSportType)`: HR is supported for Run and Bike; Power, Pedal Power Balance, and W' Battery are supported strictly for Bike; all other combinations return false.
2. `TrackingViewsDatabaseManager` bumped `DB_VERSION = 18` with non-destructive v18 migration re-evaluating `ShowBar` from the athlete's legacy `SettingsDataStoreJavaHelper.getZoneDisplayOptions(context, zoneType).showGauge` per zone profile (`HR_RUN`, `HR_BIKE`, `PWR_BIKE`), setting Bike Balance/W'Bal to 1, setting unsupported to 0, and enforcing `ShowValue = 1` whenever `ShowBar == 0`.
3. `DatabaseSchemaRegistry.kt` references `TrackingViewsDbHelper.DB_VERSION` directly for `TrackingViews.db` and `FlexibleGenericView.db` (Rule 62).
4. `EditSensorFieldDialog` and `EditSensorFieldViewModel` hide Value/Bar element switches when unsupported and persist `showValue = true, showBar = false`.
5. `SensorFieldView.kt` incorporates empty-tile fallback logic (`fieldState.showValue || !hasActiveBar`), ensuring cockpit cards never render blank.
6. `TrackingViewModel.kt` restricts `pedalBalanceState` and `wPrimeBatteryState` publishing strictly to `BSportType.BIKE`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Component | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-046.1` | `TST-TRK-046.1` | `TrackingViewsRealSqliteMigrationTest.testTrackingViewsDbHelper_v17ToV18Migration_sportDependentBarsAndLegacyGaugeParity` & `DatabaseSchemaRegistry` | **PASSED** | `Verified` |
| `REQ-TRK-046.2` | `TST-TRK-046.3` | `TrackingViewsDatabaseManagerTest.testIsBarSupported_sportDependentFeasibility` & `EditSensorFieldViewModelTest` | **PASSED** | `Verified` |
| `REQ-TRK-046.3` | `TST-TRK-046.4` | `SensorFieldZoneDisplayTest.sensorFieldState_emptyTileFallbackLogic` & `SensorFieldView.kt` | **PASSED** | `Verified` |
| `REQ-TRK-046.6` | `TST-TRK-046.3` | `TrackingViewModel.kt` Bike gating & `EditSensorFieldViewModelTest.testDisplayElements_saveChanges_unsupportedBarEnforcesShowValueTrueAndShowBarFalse` | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Verification Suite (Rule 60)
* **Explicit Test Scope**:
  - `com.atrainingtracker.trainingtracker.database.*` (including `TrackingViewsRealSqliteMigrationTest`, `TrackingViewsDatabaseManagerTest`)
  - `com.atrainingtracker.trainingtracker.ui.tracking.*` (including `EditSensorFieldViewModelTest`, `SensorFieldZoneDisplayTest`, `TrackingViewsRepositoryTest`)
  - `com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest`
  - `com.atrainingtracker.trainingtracker.build.*`
* **Execution**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Result**: **BUILD SUCCESSFUL** (100% pass rate, 0 failures, 0 regressions).
* **Requirement Governance**: `python3 tools/verify_requirement_governance.py` passed with exit code 0 (`PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified with all 4 mandatory fields`).
* **ADB On-Device Verification**: `adb devices` checked; no physical device was connected. Verification conducted via authentic Robolectric SQLite migration tests and ViewModel/Compose contract tests.

---

## 4. Invariant & Governance Verification
1. **Zero Empty Tiles**: Sensor cards fall back to rendering the numeric value row whenever no active bar can be drawn (`fieldState.showValue || !hasActiveBar`).
2. **Upgrade Parity**: Existing athletes with legacy global `showGauge = false` retain `ShowBar = 0` on upgrade; sports without zone support are purged to `ShowBar = 0, ShowValue = 1`.
3. **Single Source of Truth (Rule 62)**: `DatabaseSchemaRegistry.kt` dynamically maps to `TrackingViewsDatabaseManager.TrackingViewsDbHelper.DB_VERSION` (18).
4. **Living Documentation**: `REQ-TRK-046` in `docs/requirements/req_tracking_cockpit.md` and `TST-TRK-046` in `docs/tests/tst_tracking_cockpit.md` synchronized to `Verified`.
