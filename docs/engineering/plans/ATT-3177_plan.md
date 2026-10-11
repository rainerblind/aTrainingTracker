# Stage 3: Implementation Plan - ATT-3177

## 1. Executive Summary & Architectural Invariants (SWE.2)
* **Issue**: [ATT-3177](https://atrainingtracker.atlassian.net/browse/ATT-3177): `[Verbesserung] Configure standard tracking tabs with targeted navigation, segment, and climb defaults on initial install`
* **Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355): *Good and consistent UI*
* **Requirement**: `REQ-UI-344`
* **Test Specification**: `TST-UI-304`
* **Sprint**: `2026-41.9`

### Architectural Invariants
1. **Canonical Default Toggle Matrix Alignment (Design Guidelines §5.9)**:
   - **Standard Tab** (`layoutNr = 1`): General cockpit telemetry. In-ride intelligence enabled (`SHOW_NAVIGATION_HINTS = 1`, `SHOW_LIVE_SEGMENTS = 1`, `SHOW_LIVE_CLIMBS = 1`). Map canvas and lap button disabled (`SHOW_MAP = 0`, `SHOW_ELEVATION_PROFILE = 0`, `SHOW_LAP_BUTTON = 0`).
   - **Map Tab** (`layoutNr = 2`): Navigation and terrain guidance. Map canvas, elevation profile, and navigation hints enabled (`SHOW_MAP = 1`, `SHOW_ELEVATION_PROFILE = 1`, `SHOW_NAVIGATION_HINTS = 1`). Live climb and segment bottom sheets disabled (`SHOW_LIVE_SEGMENTS = 0`, `SHOW_LIVE_CLIMBS = 0`) to prevent occluding the route map. Lap button disabled (`SHOW_LAP_BUTTON = 0`).
   - **Laps Tab** (`layoutNr = 3`): Pure interval split monitoring. Lap button enabled (`SHOW_LAP_BUTTON = 1`). Navigation cues, map, elevation profile, live climbs, and live segments disabled (`SHOW_NAVIGATION_HINTS = 0`, `SHOW_MAP = 0`, `SHOW_ELEVATION_PROFILE = 0`, `SHOW_LIVE_CLIMBS = 0`, `SHOW_LIVE_SEGMENTS = 0`).
2. **Elevation Profile Authority**: `SHOW_ELEVATION_PROFILE` continues to be synchronized with `showMap ? 1 : 0` (`REQ-UI-009`).
3. **Database Schema & Migration Stability**: Database schema version remains at V12. Zero schema changes and zero database migrations required. Existing user-configured tracking tabs in SQLite remain completely untouched.
4. **User Sovereignty**: Athletes retain 100% customization freedom to toggle any feature on any tab via `ConfigTrackingTabsActivity`.
5. **Scope Bounding (Rule 26 / ATT-1250)**: Confined strictly to `TrackingViewsDatabaseManager.java` and `TrackingViewsDatabaseManagerTest.kt`.

---

## 2. Component Architecture & Data Flow

```
+-----------------------------------------------------------------------------------------+
| TrackingViewsDbHelper.onCreate(db: SQLiteDatabase)                                      |
|                                                                                         |
|  For each ActivityType (GENERIC_HR, RUN, BIKE, BIKE_POWER):                             |
|       │                                                                                 |
|       ├──► addDefaultTab(..., layoutNr = 1, LapBtn=F, Map=F, Seg=T, Climb=T, Nav=T)    |
|       │      └── Standard Tab: Telemetry + In-Ride Intelligence (Climbs, Segments, Nav) |
|       │                                                                                 |
|       ├──► addDefaultTab(..., layoutNr = 2, LapBtn=F, Map=T, Seg=F, Climb=F, Nav=T)    |
|       │      └── Map Tab: Spatial Navigation (Map Canvas, Elevation Profile, Nav Hints) |
|       │                                                                                 |
|       └──► addDefaultTab(..., layoutNr = 3, LapBtn=T, Map=F, Seg=F, Climb=F, Nav=F)    |
|              └── Laps Tab: Interval Training (Lap Button Split Control Only)             |
|                                                                                         |
|  addDefaultTab(...) populates ContentValues for LayoutViewsTable:                       |
|       SHOW_LAP_BUTTON = showLapButton ? 1 : 0                                           |
|       SHOW_MAP = showMap ? 1 : 0                                                        |
|       SHOW_ELEVATION_PROFILE = showMap ? 1 : 0                                          |
|       SHOW_LIVE_SEGMENTS = showLiveSegments ? 1 : 0                                     |
|       SHOW_LIVE_CLIMBS = showLiveClimbs ? 1 : 0       <-- Parameterized (was: 1)       |
|       SHOW_NAVIGATION_HINTS = showNavigationHints ? 1 : 0 <-- Parameterized (was: 1)   |
+-----------------------------------------------------------------------------------------+
```

---

## 3. Step-by-Step Construction Plan (Atomic Steps)

### Step 1: Extend `addDefaultTab` Signature & Add Overload in `TrackingViewsDatabaseManager.java`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/database/TrackingViewsDatabaseManager.java`
* **Location**: Lines 826–848
* **Action**:
  1. Define full signature:
     ```java
     public long addDefaultTab(
         @NonNull SQLiteDatabase db,
         @NonNull ActivityType activityType,
         String name,
         int layoutNr,
         boolean showLapButton,
         boolean showMap,
         boolean showLiveSegments,
         boolean showLiveClimbs,
         boolean showNavigationHints,
         List<RowData> rowDataList
     )
     ```
  2. In `addDefaultTab`, update `ContentValues` population:
     ```java
     values.put(SHOW_LIVE_CLIMBS, showLiveClimbs ? 1 : 0);
     values.put(SHOW_NAVIGATION_HINTS, showNavigationHints ? 1 : 0);
     ```
  3. Provide backward-compatible overload for legacy callers:
     ```java
     public long addDefaultTab(
         @NonNull SQLiteDatabase db,
         @NonNull ActivityType activityType,
         String name,
         int layoutNr,
         boolean showLapButton,
         boolean showMap,
         boolean showLiveSegments,
         List<RowData> rowDataList
     ) {
         return addDefaultTab(db, activityType, name, layoutNr, showLapButton, showMap, showLiveSegments, true, true, rowDataList);
     }
     ```

### Step 2: Update `onCreate` Tab Seeding in `TrackingViewsDatabaseManager.java`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/database/TrackingViewsDatabaseManager.java`
* **Location**: Lines 816–823
* **Action**:
  Update loop in `onCreate()`:
  ```java
  for (ActivityType activityType : viewMap.keySet()) {
      // Standard Tab: layoutNr 1, live segments, live climbs, nav hints
      addDefaultTab(db, activityType, mContext.getString(R.string.text_default), 1, false, false, true, true, true, viewMap.get(activityType));
      // Map Tab: layoutNr 2, map, elevation profile, nav hints
      addDefaultTab(db, activityType, mContext.getString(R.string.tab_map), 2, false, true, false, false, true, getDefaultStartRowDataList(activityType));
      // Laps Tab: layoutNr 3, lap button only
      addDefaultTab(db, activityType, mContext.getString(R.string.tab_laps), 3, true, false, false, false, false, getDefaultLapRowDataList(activityType));
  }
  ```

### Step 3: Implement Unit Tests in `TrackingViewsDatabaseManagerTest.kt`
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/database/TrackingViewsDatabaseManagerTest.kt`
* **Actions**:
  1. Add `testOnCreate_seedsCanonicalDefaultTabConfigurationMatrix`:
     - Invoke `dbHelper.onCreate(mockDb)`.
     - Capture all `ContentValues` passed to `mockDb.insert("LayoutViewsTable", any(), capture(slot))`.
     - Group inserted tabs by `LAYOUT_NR`.
     - Assert that every Standard tab (`layoutNr = 1`) has `SHOW_NAVIGATION_HINTS = 1`, `SHOW_LIVE_SEGMENTS = 1`, `SHOW_LIVE_CLIMBS = 1`, `SHOW_MAP = 0`, `SHOW_ELEVATION_PROFILE = 0`, `SHOW_LAP_BUTTON = 0`.
     - Assert that every Map tab (`layoutNr = 2`) has `SHOW_NAVIGATION_HINTS = 1`, `SHOW_MAP = 1`, `SHOW_ELEVATION_PROFILE = 1`, `SHOW_LIVE_SEGMENTS = 0`, `SHOW_LIVE_CLIMBS = 0`, `SHOW_LAP_BUTTON = 0`.
     - Assert that every Laps tab (`layoutNr = 3`) has `SHOW_LAP_BUTTON = 1`, `SHOW_NAVIGATION_HINTS = 0`, `SHOW_LIVE_SEGMENTS = 0`, `SHOW_LIVE_CLIMBS = 0`, `SHOW_MAP = 0`, `SHOW_ELEVATION_PROFILE = 0`.
  2. Add `testAddDefaultTab_propagatesConfigurableToggles`:
     - Directly call `addDefaultTab` with `showLiveClimbs = false, showNavigationHints = false`.
     - Verify inserted `ContentValues` has `SHOW_LIVE_CLIMBS = 0` and `SHOW_NAVIGATION_HINTS = 0`.
  3. Ensure existing `testAddDefaultTab_insertsViewSize` continues to pass cleanly via overload or explicit parameters.

---

## 4. UI Consistency & Living Guidelines Alignment (Rule 23)
* **Living Style Guide**: Aligns 100% with `docs/design_guidelines.md` §5.9 (*Default Tracking Screens Configuration (Fresh Install)*).
* **Zero UI Regression**: Does not introduce any new UI widgets or alter existing layouts; directly feeds canonical settings into SQLite on fresh database initialization.

---

## 5. Test Strategy & Verification Gate Alignment
1. **Targeted Unit Test**:
   - Run `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManagerTest`.
   - Verify that all database helper test cases pass with 100% success.
2. **Clean-Room Full Suite Regression**:
   - Run `./gradlew testDebugUnitTest`.
   - Verify 100% pass rate across the full test suite with zero regressions.
3. **Requirement Governance Verification**:
   - Run `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-41.9`.

---

## 6. Risk Assessment & Rollback Strategy

| Risk | Probability | Severity | Mitigation |
| :--- | :---: | :---: | :--- |
| Existing user views modified or reset | None | High | `onCreate()` only runs on fresh database creation; existing user databases never invoke `onCreate()`. |
| Athlete wants climbs or segments on Map tab | Low | Low | Athlete can easily re-enable any toggle via `ConfigTrackingTabsActivity` at any time. |
| Inadvertent regression in row/filter seeding | None | High | Row seeding with `SensorFilterDefaults` and `VIEW_SIZE` is untouched and verified by existing tests. |
| **Rollback Plan** | In the unlikely event of an issue, reverting commit restores legacy hardcoded defaults in under 1 minute. |
