# Stage 3: Implementation Plan - ATT-6688: Wire LiveMmpCurveChart into Cockpit Tracking Layouts with PR Reference Curve and Breakthrough Notifications

**Ticket**: [ATT-6688](https://rainerblind.atlassian.net/browse/ATT-6688)  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2` | **Branch**: `feature/ATT-6688`  
**Requirement Mapping**: `REQ-TRK-058.6` | **Test Mapping**: `TST-TRK-058.6` (`TST-TRK-058.6a`–`d`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Description & Background
1. **Zero Production Call Sites**: `LiveMmpCurveChart` (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/LiveMmpCurveChart.kt`) was authored in `ATT-1298` but has no call sites in `SensorGridScreen.kt`, `TrackingViewModel.kt`, or Cockpit tab layout configuration.
2. **Missing PR Overlay & Breakthrough Alerting**: Athletes lack live visual comparison against historical season/all-time PR power benchmarks (`referencePoints`), and real-time breakthroughs across benchmark durations (`5s`, `1m`, `5m`, `20m`) are unnotified.
3. **Test Hygiene Debt (`Rule 5.4`)**: `LiveMmpCurveChartTest.kt` line 62 uses brittle `File.readLines()` source grepping rather than semantic contract and state model assertions.

---

## 2. System Invariants & Preserved Behavior
1. **Database Schema & Migration Safety (`Rule 62`)**: Increment `TrackingViewsDbHelper.DB_VERSION` from 19 to 20 with idempotent `addColumn(db, VIEWS_TABLE, SHOW_MMP_CURVE, "int DEFAULT 0")`. Existing tab views remain intact without corruption.
2. **Zero Snapshot State Mutation in Composition (`Rule 66`)**: `LiveMmpCurveChart` remains purely declarative. Breakthrough notifications and benchmark pins derive purely from incoming state models without snapshot mutations.
3. **Performance Budget & 1-Hz Sampling (`REQ-TRK-007`)**: MMP duration aggregation and curve redraw cost remains bounded to $< 2\text{ ms}$ on main thread per tick.
4. **UI Consistency & 9-Locale Parity**: Reuses existing `mmp_curve` strings and standard `SpatialCockpitToggleCard` patterns. Preserves 100% 9-language translation parity in `strings_cockpit.xml`.

---

## 3. Proposed Architectural Changes (SWE.2)
* **Database & Repository Layer**:
  - `TrackingViewsDatabaseManager.java`: Bump `DB_VERSION` to 20, add column `SHOW_MMP_CURVE = "ShowMmpCurve"`, implement `updateShowMmpCurve(long viewId, boolean showMmpCurve)` and SQLite upgrade migration.
  - `TrackingViewsRepository.kt`: Add `showMmpCurve: Boolean = false` to `TrackingViewInfo`, query `SHOW_MMP_CURVE`, and expose `updateShowMmpCurve(tabViewId, showMmpCurve)`.
  - `WorkoutSummariesDatabaseManager.java` & `WorkoutRepository.kt`: Query historical benchmark PRs (`POWER_PEAK_5S`, `POWER_PEAK_1M`, `POWER_PEAK_5M`, `POWER_PEAK_20M`) from `TABLE_EXTREMA_VALUES`.
* **ViewModel Layer (`TrackingViewModel.kt`)**:
  - In `TrackingScreenState`, add `showMmpCurve: Boolean`, `mmpCurveData: LiveMmpCurveData`, and `mmpReferencePoints: Map<Int, Double>`.
  - In `applySensorData` / state combination flow, extract rolling averages and session peaks for durations 5s, 60s, 300s, 1200s and instantaneous power from `allSensorData` into `LiveMmpCurveData`.
  - Expose `tabToggleActions.onToggleMmpCurve(Boolean)`.
* **UI & Composable Layer (`LiveMmpCurveChart.kt`, `SensorGridScreen.kt`)**:
  - `LiveMmpCurveChart.kt`: Add `referencePoints: Map<Int, Double>` parameter. Render dashed reference polyline and benchmark pins. Render prominent transient breakthrough badge ("PR! 5s +25W") and pin highlight when session peak exceeds historical PR.
  - `SensorGridScreen.kt`: In `ScreenMode.CONFIGURATION`, add `SpatialCockpitToggleCard` for MMP curve. In `ScreenMode.TRACKING`, render `LiveMmpCurveChart` when `state.showMmpCurve == true`.
* **UI Consistency Checklist**:
  - Reference component: `SpatialCockpitToggleCard` and `ElevationProfile` card in `SensorGridScreen.kt`.
  - Tokens: `MaterialTheme.colorScheme.surfaceVariant` for card container, `MaterialTheme.colorScheme.primary` for session peak curve, tertiary/outline for reference curve, gold accent for PR breakthrough badge.

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Database Migration v20 & Repository Plumbing (`REQ-TRK-058.6`, `TST-TRK-058.6a`)
* **Files**: `TrackingViewsDatabaseManager.java`, `TrackingViewsRepository.kt`, `WorkoutSummariesDatabaseManager.java`, `TrackingViewsDbMigrationV20Test.kt`.
* **Changes**: Add `SHOW_MMP_CURVE` column, bump `DB_VERSION` to 20, implement `updateShowMmpCurve`, wire into `TrackingViewInfo`. Add helper to query historical benchmark PRs from `TABLE_EXTREMA_VALUES`.

### Step 2: LiveMmpCurveChart PR Reference Curve & Breakthrough Badge (`REQ-TRK-058.6`, `TST-TRK-058.6b`)
* **Files**: `LiveMmpCurveChart.kt`, `LiveMmpCurveChartTest.kt`.
* **Changes**: Accept `referencePoints` parameter, render dashed benchmark curve, detect breakthroughs, and draw transient breakthrough badge and pin highlight. Replace `File.readLines()` in `LiveMmpCurveChartTest.kt` with semantic Compose and model tests.

### Step 3: TrackingViewModel Telemetry Extraction & Cockpit Screen Wiring (`REQ-TRK-058.6`, `TST-TRK-058.6c`)
* **Files**: `TrackingViewModel.kt`, `SensorGridScreen.kt`, `TrackingViewModelMmpTest.kt`.
* **Changes**: Stream live rolling means and session peaks into `LiveMmpCurveData`. Add `SpatialCockpitToggleCard` to tab configuration and render `LiveMmpCurveChart` in tracking layout when active.

### Step 4: 9-Locale Translation Parity & Regression Verification (`REQ-UI-106`, `TST-TRK-058.6d`)
* **Files**: `strings_cockpit.xml` across all 9 locales.
* **Changes**: Add `config_tracking__show_mmp_curve` and `mmp_breakthrough_pr` strings if missing. Run `TranslationParityTest`.

### Step 5: Full Verification & Governance Check
* **Details**: Run unit tests:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.*"
  ```
  Run governance check: `python3 tools/verify_requirement_governance.py`.
