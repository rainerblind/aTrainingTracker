# Stage 2: Requirement & Test Specification - ATT-6688: Wire LiveMmpCurveChart into Cockpit Tracking Layouts with PR Reference Curve and Breakthrough Notifications

**Ticket**: [ATT-6688](https://rainerblind.atlassian.net/browse/ATT-6688) | **Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2` | **Branch**: `feature/ATT-6688`  
**Requirement Mapping**: `REQ-TRK-058` (`REQ-TRK-058.6`) | **Test Spec ID**: `TST-TRK-058` (`TST-TRK-058.6`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (`REQ-TRK-058.6`)

### 1.1 Functional Scope & Technical Invariants
* **Cockpit Layout Storage & DB v20 Migration (`TrackingViewsDbHelper.java`)**: `TrackingViewsDbHelper.VIEWS_TABLE` SHALL support integer column `ShowMmpCurve` (default 0), incrementing `DB_VERSION` from 19 to 20 with idempotent `onUpgrade` `ALTER TABLE`. `TrackingViewInfo` and `TrackingViewsRepository` SHALL expose `showMmpCurve: Boolean` and persist user configuration.
* **Spatial Toggle & Screen Integration (`SensorGridScreen.kt`)**: In `ScreenMode.CONFIGURATION`, `SensorGridScreen` SHALL render a `SpatialCockpitToggleCard` allowing the athlete to toggle the MMP curve on that tab. In `ScreenMode.TRACKING`, when `showMmpCurve == true`, `LiveMmpCurveChart` SHALL render below or alongside cockpit telemetry.
* **Live Telemetry & Historical PR Streaming (`TrackingViewModel.kt`)**: `TrackingViewModel` SHALL extract 1-Hz rolling means (`POWER_5S`, `POWER_1M`, `POWER_5M`, `POWER_20M`), session peaks (`POWER_PEAK_5S`, `POWER_PEAK_1M`, `POWER_PEAK_5M`, `POWER_PEAK_20M`), and instantaneous power from `allSensorData` into `LiveMmpCurveData`. It SHALL load historical PR power benchmarks (`referencePoints`) from `TABLE_EXTREMA_VALUES` via `WorkoutSummariesDatabaseManager`.
* **Reference Curve Overlay & Breakthrough Badges (`LiveMmpCurveChart.kt`)**: The composable SHALL support `referencePoints: Map<Int, Double>`, rendering a dashed reference polyline and benchmark pins. When a session peak exceeds the historical benchmark, it SHALL highlight the breakthrough duration with a prominent badge/accent pin.
* **Test Hygiene Modernization (`LiveMmpCurveChartTest.kt`)**: Brittle source-text `File.readLines()` grepping SHALL be replaced with semantic Compose UI and state model test assertions conforming to `Rule 5.4`.

### 1.2 Given-When-Then Acceptance Criteria
* **AC-1 (DB Migration Idempotency)**: *Given* an existing user database at schema v19, *When* upgraded to v20, *Then* `ShowMmpCurve` column is added with default `0` without corrupting existing tile or tab configurations.
* **AC-2 (Configuration Toggle & Layout Binding)**: *Given* the Cockpit tab configuration screen, *When* toggling the MMP curve card, *Then* `showMmpCurve` state updates and persists, and returning to tracking displays `LiveMmpCurveChart`.
* **AC-3 (Live Telemetry & Reference Overlay)**: *Given* an active cycling workout with power telemetry and historical PR benchmarks, *When* 1-Hz updates emit, *Then* `LiveMmpCurveChart` renders live session peak curve, rolling duration points, and dashed historical reference curve without recomposition churn (`Rule 66`).
* **AC-4 (Breakthrough Highlighting)**: *Given* an athlete exceeding a historical PR benchmark at 5s, 1m, 5m, or 20m during a ride, *When* session peak updates, *Then* a breakthrough accent badge is highlighted for that duration.
* **AC-5 (Test Hygiene)**: *Given* `LiveMmpCurveChartTest.kt`, *When* executed, *Then* tests assert semantic contract properties without source-text `File.readLines()` grepping (`Rule 5.4`).

### Requirement Archaeology & Chesterton's Fence Audit
* *Original Requirement ID & Target*: Net-new sub-requirement `REQ-TRK-058.6`, extending `REQ-TRK-058.4` (Composable Vector Chart) on `TrackingViewsDbHelper.java`, `SensorGridScreen.kt`, `TrackingViewModel.kt`, and `LiveMmpCurveChart.kt`.
* *Historical Origin & Commit Trace*: `ATT-1289`, `ATT-1290`, `ATT-1298` (`LiveMmpCurveChart` initial draft and accumulators), and `ATT-6688`.
* *Root Reason for Existing Formulation*: `ATT-1298` implemented isolated chart math, leaving Cockpit tab toggles, PR reference curves, and breakthroughs as follow-up integration.
* *Preservation of Core Invariants*: 1-Hz background telemetry execution (`REQ-TRK-007`), pause freeze, zero Compose snapshot state writes in composition (`Rule 66`), and 100% 9-language translation parity strictly preserved.

---

## 2. Test Specification & Traceability Matrix (`TST-TRK-058.6`)

### 2.1 Concrete Verification Procedures
* **`TST-TRK-058.6a` (DB Schema v20 Migration Test)**: In `TrackingViewsDbMigrationV20Test.kt`, verify `onUpgrade` from version 19 to 20 adds `ShowMmpCurve` to `VIEWS_TABLE` with default `0` and preserves existing layouts.
* **`TST-TRK-058.6b` (LiveMmpCurveChart Semantic & Reference Curve Test)**: In `LiveMmpCurveChartTest.kt`, assert logarithmic duration projection, dashed reference curve mapping, breakthrough pin styling, and zero snapshot writes without `readLines()`.
* **`TST-TRK-058.6c` (TrackingViewModel MMP Telemetry Integration Test)**: In `TrackingViewModelMmpTest.kt`, assert 1-Hz rolling means and session peaks assemble into `LiveMmpCurveData`, and breakthroughs are flagged when session peaks exceed benchmarks.
* **`TST-TRK-058.6d` (9-Language Localization Audit)**: Run `TranslationParityTest` verifying string resources (`config_tracking__showMmpCurve`, `mmp_breakthrough`) across all 9 locales.

### 2.2 Traceability Matrix

| Req ID | Test Case | Target Component | Verification Scope | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-058.6` | `TST-TRK-058.6a` | `TrackingViewsDbMigrationV20Test` | Schema v20 migration & `ShowMmpCurve` default column integrity | Specified |
| `REQ-TRK-058.6` | `TST-TRK-058.6b` | `LiveMmpCurveChartTest` | Logarithmic scale, reference curve overlay, breakthrough pins & hygiene | Specified |
| `REQ-TRK-058.6` | `TST-TRK-058.6c` | `TrackingViewModelMmpTest` | Telemetry extraction, `LiveMmpCurveData` streaming & PR comparison | Specified |
| `REQ-UI-106` | `TST-TRK-058.6d` | `TranslationParityTest` | 100% 9-locale parity for MMP Cockpit strings in `strings_cockpit.xml` | Specified |
| `REQ-PRO-001` | `TST-TRK-058.5` | Targeted Regression Suite | `--tests "com.atrainingtracker.trainingtracker.ui.tracking.*"` | Specified |
