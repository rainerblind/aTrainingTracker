# Stage 5: Walkthrough & Verification - ATT-3921: Cockpit: Defaults for Bike Power should contain new Sensors (TSS, IF, ...)

**Ticket**: [ATT-3921](https://rainerblind.atlassian.net/browse/ATT-3921)  
**Sub-task**: [ATT-3977](https://rainerblind.atlassian.net/browse/ATT-3977) (`[Test]`)  
**Parent Epic**: [ATT-1340](https://rainerblind.atlassian.net/browse/ATT-1340) (*[Tracking/Cockpit] Real-Time Tracking & Cockpit UI*)  
**Target Release**: `TBD (Set on Completion per Rule 14)`  
**Active Sprint**: `Sprint 2026-42.8`  
**Requirement Mapping**: `REQ-UI-409`  
**Test Mapping**: `TST-UI-372`  
**Branch**: `feature/ATT-3921`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Executive Summary & Verification Overview

This walkthrough documents the implementation and clean-room verification of **ATT-3921** (*Cockpit: Defaults for Bike Power should contain new Sensors (TSS, IF, ...)*):

1. **`ActivityType.BIKE_POWER` Sensor Catalog (`REQ-UI-409.1`)**:
   - Added `SensorType.W_PRIME_BAL` immediately after `SensorType.TSS` in `ActivityType.getSensorTypeArray(ActivityType.BIKE_POWER, Context)` in [`ActivityType.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/ActivityType.java), ensuring complete parity with all derived cycling power metrics (`POWER`, `POWER_TO_WEIGHT`, `NORMALIZED_POWER`, `INTENSITY_FACTOR`, `WORK_KJ`, `TSS`, `W_PRIME_BAL`, `PEDAL_POWER_BALANCE`, `TORQUE`).
2. **4-Tab Default `BIKE_POWER` Cockpit Architecture & Non-Destructive Seeding (`REQ-UI-409.2`)**:
   - Updated [`TrackingViewsDatabaseManager.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/TrackingViewsDatabaseManager.java) (`DB_VERSION = 13` unchanged) so `ActivityType.BIKE_POWER` seeds 4 purpose-built default tabs on `onCreate`:
     - **Tab 1 — Standard** (`R.string.text_default`, `layoutNr = 1`, `showLapButton = false, showMap = false, showLiveSegments = true, showLiveClimbs = true, showNavigationHints = true`): Row 1 (`TIME_ACTIVE`, `TIME_OF_DAY` `SMALL`), Row 2 (`SPEED_mps`, `DISTANCE_m` `NORMAL`), Row 3 (`HR`, `CADENCE` `LARGE`), Row 4 (`POWER` `HUGE`), Row 5 (`POWER_TO_WEIGHT`, `NORMALIZED_POWER` `NORMAL`), Row 6 (`ALTITUDE`, `ASCENT` `NORMAL`).
     - **Tab 2 — Dedicated Power Tab** (`R.string.tab_power`, `layoutNr = 2`, `showLapButton = false, showMap = false, showLiveSegments = false, showLiveClimbs = false, showNavigationHints = true`) via `getDefaultPowerRowDataList()`: Row 1 (`TIME_ACTIVE`, `HR`, `CADENCE` `SMALL`), Row 2 (`POWER` `HUGE`), Row 3 (`POWER_TO_WEIGHT`, `NORMALIZED_POWER` `LARGE`), Row 4 (`INTENSITY_FACTOR`, `TSS`, `WORK_KJ` `NORMAL`), Row 5 (`W_PRIME_BAL` `LARGE`), Row 6 (`PEDAL_POWER_BALANCE`, `TORQUE` `SMALL`).
     - **Tab 3 — Map** (`R.string.tab_map`, `layoutNr = 3` for `BIKE_POWER`, `2` for others; `showLapButton = false, showMap = true, showLiveSegments = false, showLiveClimbs = false, showNavigationHints = true`) via `getDefaultMapRowDataList(ActivityType.BIKE_POWER)`: Row 1 (`TIME_ACTIVE`, `DISTANCE_m` `NORMAL`), Row 2 (`SPEED_mps`, `HR` `NORMAL`), Row 3 (`POWER`, `POWER_TO_WEIGHT` `NORMAL`).
     - **Tab 4 — Laps** (`R.string.tab_laps`, `layoutNr = 4` for `BIKE_POWER`, `3` for others; `showLapButton = true, showMap = false, showLiveSegments = false, showLiveClimbs = false, showNavigationHints = false`) via `getDefaultLapRowDataList(ActivityType.BIKE_POWER)`: Row 1 (`TIME_LAP` `LARGE`, `LAP_NR` `SMALL`), Row 2 (`SPEED_mps`, `DISTANCE_m_LAP` `NORMAL`), Row 3 (`HR`, `CADENCE` `NORMAL`), Row 4 (`POWER` `LARGE`, `POWER_TO_WEIGHT` `NORMAL`), Row 5 (if `havePressureSensor`: `ALTITUDE`, `ASCENT` `NORMAL`).
   - Added non-destructive helper `ensurePowerTabExistsForBikePower(SQLiteDatabase db)` for safe idempotent inspection/seeding without overwriting user-customized layouts.
3. **Live-Cockpit Gating, Empty-Row Collapse & All-Gated Empty-State Protection (`REQ-UI-409.3`)**:
   - Added `val sensorType: SensorType? = null` to `SensorFieldState` in [`SensorFieldState.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldState.kt) (populated in [`TrackingViewModel.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingViewModel.kt)).
   - Added pure, null-safe functions `isSensorTypeVisibleForTuning`, `filterVisibleFieldsForTuning`, and `packVisibleRowsForTuning` in [`SensorFieldState.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldState.kt) and memoized `packVisibleRowsForTuning(state.fields, tuningConfig, state.screenMode)` inside [`SensorGridScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt) with `.defaultMinSize(minHeight = 48.dp)` empty-state protection.
4. **9-Language Localization Parity (`REQ-UI-409.4`)**:
   - Added `tab_power` across all 9 supported locale files (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-409.1` | `TST-UI-372.1` | `ActivityType.BIKE_POWER` sensor catalog unit test (`BikePowerCockpitDefaultsAndGatingTest`) | **PASSED** | `Verified` |
| `REQ-UI-409.2` | `TST-UI-372.2` | 4-tab `BIKE_POWER` default layout & toggle matrix tests (`BikePowerCockpitDefaultsAndGatingTest`, `TrackingViewsDatabaseManagerTest`) | **PASSED** | `Verified` |
| `REQ-UI-409.3` | `TST-UI-372.3` | Live cockpit gating, empty-row collapse & custom coordinate preservation (`BikePowerCockpitDefaultsAndGatingTest`) | **PASSED** | `Verified` |
| `REQ-UI-409.4` | `TST-UI-372.4` | 9-Language Localization Parity Audit (`BikePowerCockpitDefaultsAndGatingTest`) | **PASSED** | `Verified` |
| `REQ-UI-409.3` | `TST-UI-372.5` | All-gated empty grid & null `TuningConfig` boot fallback (`BikePowerCockpitDefaultsAndGatingTest`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-372.6` | Full Clean-Room `./gradlew testDebugUnitTest` | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)
```text
BUILD SUCCESSFUL
100% pass rate across all unit and Robolectric tests with 0 failures.
```

### Targeted Unit & Integration Tests (`BikePowerCockpitDefaultsAndGatingTest`, `TrackingViewsDatabaseManagerTest`)
```text
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 11s
32 tests completed, 0 failed
```

---

## 4. Hardware / Physical Verification (Pixel 10)

### Visual Consistency (Rule 23)
* **Checked against `docs/design_guidelines.md` §5 & §5.9**:
  - Reuses the canonical `SensorGridScreen`, `SensorFieldView`, and `TrackingTabBar` components without introducing any new custom UI widgets, colors, shapes, or typography.
  - Preserves `CockpitThemeMode` (System, Light, Dark, High-Contrast Black on White, High-Contrast White on Black / AMOLED) and `ViewSize` (`HUGE`, `LARGE`, `NORMAL`, `SMALL`) tokens.
* **Deviations & justification**: Extends `docs/design_guidelines.md` §5.9 from 3 tabs to 4 tabs specifically for `ActivityType.BIKE_POWER` by inserting the dedicated `Power` (`R.string.tab_power`) tab at `layoutNr = 2` so the `Standard` tab remains clean and readable (`6` rows) while all 9 power & physiology sensors are accessible on the `Power` tab.

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**: Clean-room unit test suite executed with 100% pass rate.
2. **Living Documentation Synchronized**: Status of `REQ-UI-409` in `docs/requirements.md` and `TST-UI-372` in `docs/tests.md` updated to `Verified`.
3. **Subtask Completion**: Stage 5 subtask `ATT-3977` transitioned to `Erledigt` via `freigabe`.
4. **Parent Ticket Final Review**: Parent ticket `ATT-3921` merged into `sprint/2026-42.8` (`--no-ff`) and transitioned to `Final Review (Human)` for final release sign-off.
