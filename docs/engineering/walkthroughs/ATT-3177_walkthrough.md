# ATT-3177 Walkthrough: Configure Standard Tracking Tabs with Targeted Navigation, Segment, and Climb Defaults on Initial Install

## Overview
- **Issue**: [ATT-3177](https://atrainingtracker.atlassian.net/browse/ATT-3177)
- **Requirement**: `REQ-UI-344`
- **Test Specification**: `TST-UI-304`
- **Sprint**: `2026-41.9`
- **Status**: Verified

## Problem Addressed
Previously, when a fresh install of `aTrainingTracker` created its SQLite database (`TrackingViewsDbHelper.onCreate()`), default tracking tabs across all 4 activity profiles (`RUNNING`, `CYCLING`, `SWIMMING`, `OTHER`) were provisioned with generic defaults.
Specifically, `SHOW_LIVE_CLIMBS` and `SHOW_NAVIGATION_HINTS` were universally hardcoded to `1` across all layout types (Standard, Map, Laps). Consequently:
- The **Map Tab** (`layoutNr = 2`) had live segment and climb sheets enabled by default, cluttering the map canvas and competing for vertical screen space.
- The **Laps Tab** (`layoutNr = 3`) had navigation hints, segments, and climbs enabled by default, cluttering the focused manual lap split review workflow.
- In `TrackingViewsDbHelper.addDefaultTab(...)`, `SHOW_LIVE_CLIMBS` and `SHOW_NAVIGATION_HINTS` columns were hardcoded to `1` in the `ContentValues` mapping instead of being parameterized.

## Architectural Changes & Solution

1. **Explicit Parameterization in `TrackingViewsDatabaseManager.java`**:
   - Extended `addDefaultTab` method signature to explicitly accept `boolean showLiveClimbs, boolean showNavigationHints`:
     ```java
     private static void addDefaultTab(
             SQLiteDatabase db,
             int activityType,
             int layoutNr,
             String name,
             boolean showLapButton,
             boolean showMap,
             boolean showElevationProfile,
             boolean showLiveSegments,
             boolean showLiveClimbs,
             boolean showNavigationHints
     )
     ```
   - Bound values into `ContentValues`:
     ```java
     values.put(TrackingViewsTable.TrackingViewsColumns.SHOW_LIVE_CLIMBS, showLiveClimbs ? 1 : 0);
     values.put(TrackingViewsTable.TrackingViewsColumns.SHOW_NAVIGATION_HINTS, showNavigationHints ? 1 : 0);
     ```
   - Added an 8-parameter overload retaining full backward compatibility for any existing callers:
     ```java
     private static void addDefaultTab(
             SQLiteDatabase db,
             int activityType,
             int layoutNr,
             String name,
             boolean showLapButton,
             boolean showMap,
             boolean showElevationProfile,
             boolean showLiveSegments
     ) {
         addDefaultTab(db, activityType, layoutNr, name, showLapButton, showMap, showElevationProfile, showLiveSegments, true, true);
     }
     ```

2. **Canonical Default Tab Configuration Matrix (`onCreate()`)**:
   - Updated the profile initialization loop in `onCreate(SQLiteDatabase db)`:
     - **Standard Tab** (`layoutNr = 1`): Lap=0, Map=0, Elevation=0, Segments=1, Climbs=1, NavHints=1.
     - **Map Tab** (`layoutNr = 2`): Lap=0, Map=1, Elevation=1, Segments=0, Climbs=0, NavHints=1.
     - **Laps Tab** (`layoutNr = 3`): Lap=1, Map=0, Elevation=0, Segments=0, Climbs=0, NavHints=0.

3. **No Disruptive Database Migrations**:
   - Changes are confined strictly to initial table creation (`onCreate()`). Existing user databases and custom tabs remain completely untouched.

4. **Living Design Guidelines Alignment**:
   - Codified in `docs/design_guidelines.md` §5.9 (*Initial Default Tracking Tabs Configuration Matrix*).

5. **Scope Bounding (Rule 26 / ATT-1250)**:
   - Modifications are bounded strictly to `TrackingViewsDatabaseManager.java` and `TrackingViewsDatabaseManagerTest.kt`.
   - Zero modifications to SQLite schema versions, sensor layers, or tracking services.

## Verification & Test Results

- **Targeted Unit Tests (`TrackingViewsDatabaseManagerTest.kt`)**:
  - `testOnCreate_seedsCanonicalDefaultTabConfigurationMatrix`: Verifies that `onCreate()` seeds the exact canonical configuration matrix for Standard (1, 0, 0, 0, 1, 1, 1), Map (2, 0, 1, 1, 0, 0, 1), and Laps (3, 1, 0, 0, 0, 0, 0) across all 4 activity profiles (`RUNNING`, `CYCLING`, `SWIMMING`, `OTHER`).
  - `testAddDefaultTab_propagatesAllConfigurableToggles`: Verifies boolean parameter propagation into SQLite table rows.
  - `testAddDefaultTab_backwardCompatibleOverload_delegatesWithClimbsAndHintsEnabled`: Verifies the legacy overload sets default `true, true`.
  - Result: **100% targeted unit tests passed**.

- **Clean-Room Regression**:
  - Full project unit test suite (`./gradlew testDebugUnitTest`) executed cleanly with zero regressions.

## ASPICE Traceability
- **Requirement**: `REQ-UI-344` in `docs/requirements.md` (Verified)
- **Test Specification**: `TST-UI-304` in `docs/tests.md` (Verified)
- **Implementation Plan**: `docs/engineering/plans/ATT-3177_plan.md`
- **Analysis**: `docs/engineering/analysis/ATT-3177_analysis.md`
