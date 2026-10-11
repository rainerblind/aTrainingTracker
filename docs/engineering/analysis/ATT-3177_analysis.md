# Stage 1: Problem Domain & Root Cause Analysis - ATT-3177

## 1. Executive Summary & Problem Domain
* **Issue**: [ATT-3177](https://atrainingtracker.atlassian.net/browse/ATT-3177): `[Verbesserung] Configure standard tracking tabs with targeted navigation, segment, and climb defaults on initial install`
* **Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355): *Good and consistent UI*
* **Target Requirement**: `REQ-UI-344`
* **Target Test Spec**: `TST-UI-304`
* **Sprint**: `2026-41.9`

### Problem Description
When an athlete installs the application or creates a fresh database, `TrackingViewsDatabaseManager.TrackingViewsDbHelper.onCreate()` initializes three default tracking views for each sport type (`ActivityType`):
1. **Standard** (`R.string.text_default`): General cockpit view.
2. **Map** (`R.string.tab_map`): Map view.
3. **Laps** (`R.string.tab_laps`): Interval/lap view.

During historical database version 12 evolution (where Live Climbs and Live Navigation Hints were integrated into tracking tabs), `addDefaultTab()` was configured to unconditionally set:
```java
values.put(SHOW_LIVE_CLIMBS, 1);
values.put(SHOW_NAVIGATION_HINTS, 1);
```
Consequently, both the Map and Laps tabs currently display unsolicited live climb bottom sheets and navigation cues, diluting the focused purpose of each screen and creating visual clutter.

The default behavior on fresh install must be updated so each tab possesses targeted, purposeful defaults matching `docs/design_guidelines.md` §5.9.

---

## 2. Forensic Investigation & Technical Root Cause

### 2.1 Code Touchpoint in `TrackingViewsDatabaseManager.java`
In `app/src/main/java/com/atrainingtracker/trainingtracker/database/TrackingViewsDatabaseManager.java`:
```java
// Lines 815-824:
EnumMap<ActivityType, List<RowData>> viewMap = createViewMap();
for (ActivityType activityType : viewMap.keySet()) {
    // show the default tab with an lap button
    addDefaultTab(db, activityType, mContext.getString(R.string.text_default), 1, false, false, true, viewMap.get(activityType));
    // shot a tab with just some standard fields and the map but no lap button
    addDefaultTab(db, activityType, mContext.getString(R.string.tab_map), 2, false, true, false, getDefaultStartRowDataList(activityType));
    addDefaultTab(db, activityType, mContext.getString(R.string.tab_laps), 3, true, false, false, getDefaultLapRowDataList(activityType));
}

// Lines 826-848:
public long addDefaultTab(@NonNull SQLiteDatabase db, @NonNull ActivityType activityType, String name, int layoutNr, boolean showLapButton, boolean showMap, boolean showLiveSegments, List<RowData> rowDataList) {
    ...
    values.put(SHOW_LAP_BUTTON, showLapButton ? 1 : 0);
    values.put(SHOW_MAP, showMap ? 1 : 0);
    values.put(SHOW_ELEVATION_PROFILE, showMap ? 1 : 0);
    ...
    values.put(SHOW_LIVE_SEGMENTS, showLiveSegments ? 1 : 0);
    values.put(SHOW_LIVE_CLIMBS, 1);
    values.put(SHOW_NAVIGATION_HINTS, 1);
    newViewId = db.insert(VIEWS_TABLE, null, values);
```
1. `addDefaultTab()` currently accepts parameters for `showLapButton`, `showMap`, and `showLiveSegments`, but hardcodes `SHOW_LIVE_CLIMBS = 1` and `SHOW_NAVIGATION_HINTS = 1`.
2. As a result:
   - The **Map Tab** (`layoutNr = 2`) receives `SHOW_LIVE_CLIMBS = 1`, causing Live Climb bottom sheets to pop up and occlude the map during active climbs.
   - The **Laps Tab** (`layoutNr = 3`) receives `SHOW_LIVE_CLIMBS = 1` and `SHOW_NAVIGATION_HINTS = 1`, displaying unsolicited bottom sheets and turn cues on an interval-focused screen.
3. `SHOW_ELEVATION_PROFILE` is tied to `showMap ? 1 : 0`, which properly enables elevation profile only on the Map Tab.

### 2.2 Canonical Default Toggle Matrix
Per `docs/design_guidelines.md` §5.9:

| Feature / Toggle | Standard Tab (Layout 1) | Map Tab (Layout 2) | Laps Tab (Layout 3) |
| :--- | :---: | :---: | :---: |
| **Live Navigation Hints** | ✅ Enabled (1) | ✅ Enabled (1) | ❌ Disabled (0) |
| **Live Segments** | ✅ Enabled (1) | ❌ Disabled (0) | ❌ Disabled (0) |
| **Live Climbs** | ✅ Enabled (1) | ❌ Disabled (0) | ❌ Disabled (0) |
| **Map Canvas** | ❌ Disabled (0) | ✅ Enabled (1) | ❌ Disabled (0) |
| **Elevation Profile** | ❌ Disabled (0) | ✅ Enabled (1) | ❌ Disabled (0) |
| **Lap Button** | ❌ Disabled (0) | ❌ Disabled (0) | ✅ Enabled (1) |

---

## 3. Chesterton's Fence & Requirement Archaeology

1. **Original Requirement ID & Target**:
   - `REQ-UI-009` (*Elevation Profile on Map Tab*): Mandated that elevation profile is enabled alongside map canvas in `addDefaultTab` (`SHOW_ELEVATION_PROFILE = showMap ? 1 : 0`).
   - `REQ-UI-253` (*Tracking Views Null-Safe Enum Deserialization, Fresh-Install Seeding & DB Repair Migration*): Mandated that `addDefaultTab` seeds valid row configurations into `LayoutRowsTable`.
   - `REQ-UI-198` (*Cockpit Sensor Field Domain-Specific Default Filter Presets Architecture*): Mandated that `addDefaultTab` assigns smoothed presets via `SensorFilterDefaults`.
2. **Historical Origin & Commit Trace**:
   - Live Climbs and Navigation Hints were added to `VIEWS_TABLE` in database schema V12 (`ATT-2360`, commit `f731522a`).
   - At the time of introduction, setting `SHOW_LIVE_CLIMBS = 1` and `SHOW_NAVIGATION_HINTS = 1` unconditionally was a fast prototype default to ensure users immediately saw the new features without manual configuration.
3. **Reason for Refinement (Chesterton's Fence Forensics)**:
   - Field experience and athlete feedback revealed that unsolicited climb sheets on the Map tab cover the route map during critical ascent segments.
   - On the Laps tab, navigation cues and climb sheets distract from interval pacing and split monitoring.
   - Refined defaults provide a clean separation of concerns: Standard for general telemetry + live intelligence, Map for route navigation + elevation profile, Laps for pure interval tracking.
4. **Preservation of Core Invariants**:
   - `addDefaultTab` continues to link `SHOW_ELEVATION_PROFILE` to `showMap ? 1 : 0`.
   - Row seeding with `SensorFilterDefaults` and `VIEW_SIZE` remains completely unchanged.
   - User customization remains 100% sovereign: athletes can freely toggle any feature on any tab via `ConfigTrackingTabsActivity`.
   - No migration is performed on existing user databases: user-configured tabs remain intact.

---

## 4. Scope Bounding & Invariants (Rule 26 / ATT-1250)

* **Bounded Scope**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/database/TrackingViewsDatabaseManager.java`:
    - Extend `addDefaultTab` signature with `boolean showLiveClimbs, boolean showNavigationHints`.
    - Provide backward-compatible overload if needed or update existing test/internal callers.
    - Update `onCreate()` to pass canonical flags:
      - Standard: `showLapButton=false, showMap=false, showLiveSegments=true, showLiveClimbs=true, showNavigationHints=true`
      - Map: `showLapButton=false, showMap=true, showLiveSegments=false, showLiveClimbs=false, showNavigationHints=true`
      - Laps: `showLapButton=true, showMap=false, showLiveSegments=false, showLiveClimbs=false, showNavigationHints=false`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/database/TrackingViewsDatabaseManagerTest.kt`:
    - Add test verifying that `onCreate()` seeds default tabs with the exact expected toggle matrix.
    - Update/overload `testAddDefaultTab_insertsViewSize`.
  - `docs/requirements.md` (`REQ-UI-344`) and `docs/tests.md` (`TST-UI-304`).
* **Zero Out-of-Scope Modifications**:
  - No changes to `TrackingTabsScreen.kt`, `SensorGridScreen.kt`, or `TrackingViewModel.kt`.
  - No changes to database schema version (stays at V12).
  - No database migration or mutation of existing user rows.

---

## 5. ASPICE Traceability Matrix & Impact Analysis

| Component | Target File | Impact |
| :--- | :--- | :--- |
| **Database Management** | `TrackingViewsDatabaseManager.java` | Extend `addDefaultTab(...)` to accept `showLiveClimbs` and `showNavigationHints`, and update `onCreate()` calls. |
| **Contract Verification** | `TrackingViewsDatabaseManagerTest.kt` | Verify initial tab creation values in SQLite against design matrix. |
| **Living Requirements** | `docs/requirements.md` | Add `REQ-UI-344`. |
| **Living Test Specs** | `docs/tests.md` | Add `TST-UI-304`. |
| **Design Guidelines** | `docs/design_guidelines.md` | §5.9 canonical matrix verified. |
