# Stage 3: Implementation Plan - ATT-1294: Live Sparkline Graph Tiles & Time Horizon

**Ticket**: [ATT-1294](https://rainerblind.atlassian.net/browse/ATT-1294) | **Sub-task**: [ATT-6411](https://rainerblind.atlassian.net/browse/ATT-6411) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-1294`  
**Requirement Mapping**: `REQ-TRK-059` (`REQ-TRK-059.1`–`5`) | **Test Mapping**: `TST-TRK-059` (`TST-TRK-059.1`–`5`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Architecture Overview

Athletes need real-time temporal telemetry trends within cockpit tiles across all numeric metrics. We implement SQLite schema v19 in `TrackingViewsDatabaseManager`, add `Graph` display element and a 7-option time horizon selector to `EditSensorFieldDialog`, create an $O(1)$ memory-bounded `LiveSparklineBuffer`, and render `SensorSparklineView` with zero composition recomposition churn.

---

## 2. Traceability & Requirements Mapping

* `REQ-TRK-059.1` (DB v19 Schema & Migration) $\to$ `TST-TRK-059.1` (`TrackingViewsDbMigrationV19Test`)
* `REQ-TRK-059.2` (Numeric Metric Gating) $\to$ `TST-TRK-059.2` (`TrackingViewsSparklineSupportTest`)
* `REQ-TRK-059.3` (Edit Dialog & 9-Locale Parity) $\to$ `TST-TRK-059.4` (`EditSensorFieldDialogSparklineTest`, `TranslationParityTest`)
* `REQ-TRK-059.4` (O(1) Sparkline Buffer Engine) $\to$ `TST-TRK-059.3` (`LiveSparklineBufferTest`)
* `REQ-TRK-059.5` (Sparkline Rendering & Composition Safety) $\to$ `TST-TRK-059.5` (`SensorFieldViewTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Database Backward Compatibility**: Upgrade from v18 to v19 cleanly adds `ShowGraph` (0) and `GraphHorizonS` (300) without corrupting existing user tile layouts.
2. **Rule 66 Composition Safety**: Zero `MutableState` snapshot writes during composition in `SensorSparklineView`.
3. **Display Element Invariance**: Existing `showValue` and `showBar` behaviors, gauge bars, and view size scaling remain 100% preserved.

---

## 4. Proposed Architectural Changes

* **Database Layer (`TrackingViewsDatabaseManager.java`)**:
  - Bump `DB_VERSION = 19`; add `ShowGraph` and `GraphHorizonS` columns; implement upgrade migration and `isGraphSupported(sensorType)`.
* **Domain / Telemetry Layer**:
  - `LiveSparklineBuffer.kt`: Circular ring buffer (1m–1h) and pairwise decimation (Full Workout), $< 1\text{ KB}$ memory per tile.
  - `SensorFieldConfig` & `SensorFieldState`: Add `showGraph`, `graphHorizonSeconds`, and `sparklineSamples`.
* **Presentation Layer**:
  - `EditSensorFieldDialog.kt`: Add `Graph` chip and 7 time-horizon selector chips.
  - `SensorSparklineView.kt` / `SensorFieldView.kt`: Dynamic polyline & gradient sparkline below value or full tile.
  - **UI Consistency**: Reuses `MaterialTheme.colorScheme.primary` and `.tertiary`, spacing `4.dp`, shapes `MaterialTheme.shapes.small`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: DB Schema v19 Migration & Gating Helper
* Files: `TrackingViewsDatabaseManager.java`, `SensorFieldConfig.kt`
* Bump to `DB_VERSION = 19`, add `ShowGraph`/`GraphHorizonS` columns, `onUpgrade` migration, and `isGraphSupported`.

### Step 2: Live Sparkline Buffer Engine & State Models
* Files: `LiveSparklineBuffer.kt`, `SensorFieldState.kt`
* Implement circular/decimation buffer, add fields to `SensorFieldState`.

### Step 3: Edit Dialog Time Horizon Selector & 9-Locale Resources
* Files: `EditSensorFieldDialog.kt`, `EditSensorFieldViewModel.kt`, `strings_cockpit.xml` (9 locales)
* Wire `Graph` chip and 7 horizon options with 100% 9-language translation parity.

### Step 4: Sparkline Composable & Tile Layout Integration
* Files: `SensorSparklineView.kt`, `SensorFieldView.kt`, `TrackingViewModel.kt`
* Render sparklines in Value+Graph and Graph-only modes; wire 1-Hz numeric sample updates.

### Step 5: Unit Tests & Verification
* Files: `TrackingViewsDbMigrationV19Test.kt`, `TrackingViewsSparklineSupportTest.kt`, `LiveSparklineBufferTest.kt`, `EditSensorFieldDialogSparklineTest.kt`
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`
