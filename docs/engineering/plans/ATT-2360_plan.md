# Stage 3: Implementation Plan - ATT-2360: Unified WYSIWYG Tracking Tab Configuration with Spatial Overlays and Per-Tab Popup Toggles

**Ticket**: [ATT-2360](https://atrainingtracker.atlassian.net/browse/ATT-2360)  
**Sub-task**: [ATT-2507](https://atrainingtracker.atlassian.net/browse/ATT-2507) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-UI-275` (*Unified WYSIWYG Tracking Tab Configuration with Spatial Overlays and Per-Tab Popup Toggles*)  
**Test Mapping**: `TST-UI-235` (*Unified WYSIWYG Tracking Tab Configuration Verification*)  
**Branch**: `feature/ATT-2360`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Problem Description & Background

In live tracking, athletes customize their cockpit across multiple tabs (`TrackingTabsScreen.kt` and `SensorGridScreen.kt`). Currently, switching into configuration mode (`ScreenMode.CONFIGURATION`) displays `TrackingTabConfigHeader.kt`, managing feature flags via a flat row of generic checkboxes (`Show Map`, `Show Elevation Profile`, `Show Live Segments`, `Show Lap Button`) in the header.

This legacy layout creates several problems:
1. **Missing Per-Tab HUD & Sheet Controls**: Features like Turn-by-Turn Navigation Prompts (`TurnPromptBanner`) and Live Climbs (`LiveClimbSheet`) are only controlled globally via settings and cannot be enabled or disabled per tab.
2. **Spatial Disconnection**: Checkboxes are detached from the physical screen positions where elements actually render.
3. **Header Clutter**: Checkboxes compress the sensor grid edit viewport.
4. **Lack of WYSIWYG Visual Feedback**: Athletes cannot see how toggles affect cockpit composition.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-275` (*Unified WYSIWYG Tracking Tab Configuration with Spatial Overlays and Per-Tab Popup Toggles*)
  * Refines and extends `REQ-PRO-005` (*Database Schema Integrity*) and `REQ-UI-200` (*Sensor Grid Configuration & Drag-and-Drop / Pick & Place*).
  * Upgrades SQLite database to `DB_VERSION = 12` with `SHOW_LIVE_CLIMBS` and `SHOW_NAVIGATION_HINTS` defaulting to `1`.
  * Enriches `TrackingViewInfo` with `showLiveClimbs: Boolean = true` and `showNavigationHints: Boolean = true`.
  * Streamlines `TrackingTabConfigHeader` by removing generic checkboxes.
  * Replaces checkboxes with a spatial WYSIWYG cockpit editor in `SensorGridScreen.kt` (`ScreenMode.CONFIGURATION`).
  * Enforces runtime gating in `SensorGridScreen.kt` for `TurnPromptBanner` and `LiveClimbSheet`.
  * Enforces 100% 9-language translation parity.
* **Test Mapping**: `TST-UI-235` (*Unified WYSIWYG Tracking Tab Configuration Verification*)
  * `TST-UI-235.1`: Database v12 schema, column creation, and upgrade defaults unit tests (`TrackingViewsDatabaseManagerTest.kt`).
  * `TST-UI-235.2`: Repository extraction, update methods, and default parameter tests (`TrackingViewsRepositoryTest.kt`).
  * `TST-UI-235.3`: UI architecture, header streamlining, WYSIWYG toggles, and runtime gating contract tests (`TrackingTabWysiwygContractTest.kt`).
  * `TST-UI-235.4`: 9-Language localization parity audit (`TrackingTabLocalizationTest.kt`).
  * `TST-UI-235.5`: Clean-room full suite regression (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Production Regressions**: Full clean-room test suite executed with 100% pass rate.
2. **Zero Feature Loss on Database Upgrade**: In `onUpgrade` for `oldVersion < 12`, existing tracking tab rows in `ViewsTable` receive `1` (true) for `ShowLiveClimbs` and `ShowNavigationHints`, ensuring existing user setups remain fully operational.
3. **Sensor Field Layout & Manipulation Invariants**: Adding rows, adding columns, tile editing, deleting, swapping, and pick-and-place moving remain 100% untouched.
4. **Subtask Self-Sufficiency**: Subtask [ATT-2507](https://atrainingtracker.atlassian.net/browse/ATT-2507) transitions directly to `Erledigt` upon passing Gate 3 audit via `freigabe`.
5. **Parent Human Gate Invariance**: Terminal completion of parent ticket [ATT-2360](https://atrainingtracker.atlassian.net/browse/ATT-2360) remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes (SWE.2 Architecture)

```
┌────────────────────────────────────────────────────────┐
│ UI Layer: TrackingTabsScreen & SensorGridScreen         │
│ - Streamlined TrackingTabConfigHeader (Name, Add, Del)  │
│ - ScreenMode.CONFIGURATION Spatial WYSIWYG Editor:      │
│   • Top: Navigation Prompts banner toggle card         │
│   • Mid: Map & Elevation Profile toggle blocks         │
│   • Bottom: Live Segments, Climbs & Lap Button dock    │
│ - ScreenMode.TRACKING: Per-tab runtime gating          │
└──────────────────────────┬─────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────┐
│ ViewModel Layer: TrackingViewModel & TrackingTabsVM     │
│ - TrackingScreenState (showLiveClimbs, showNavHints)   │
│ - StateFlow updates triggered via repository flows      │
└──────────────────────────┬─────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────┐
│ Repository Layer: TrackingViewsRepository              │
│ - TrackingViewInfo data class (showLiveClimbs, Nav)    │
│ - updateShowLiveClimbs & updateShowNavigationHints     │
│ - Dispatchers.IO execution & configUpdateTrigger        │
└──────────────────────────┬─────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────┐
│ Database Layer: TrackingViewsDatabaseManager.java       │
│ - DB_VERSION = 12                                      │
│ - VIEWS_TABLE: ShowLiveClimbs, ShowNavigationHints     │
│ - Defensive onUpgrade with default 1                   │
└────────────────────────────────────────────────────────┘
```

---

## 5. UI Consistency (Rule 23)

* **Closest Existing Reference Screen**:
  - `SensorGridScreen.kt` Cockpit HUD layout and `TurnPromptBanner.kt`.
* **Reused Components**:
  - `Surface`, `Text`, `Icon`, `Switch` / `FilterChip` / `Card`, Material Theme tokens.
* **Theme Tokens**:
  - **Shapes**: `RoundedCornerShape(12.dp)` for spatial toggle blocks (following Section 5.3 dominant shape); `RoundedCornerShape(8.dp)` for inner badges and chips.
  - **Spacing**: `8.dp` vertical spacing between cockpit blocks, `16.dp` horizontal screen padding (Section 5.2).
  - **Colors**:
    - Active toggle container: `MaterialTheme.colorScheme.primaryContainer` with `onPrimaryContainer` text/icon.
    - Inactive/disabled toggle container: `MaterialTheme.colorScheme.surfaceVariant` with `outline` border and `onSurfaceVariant` text/icon.
    - Route/Navigation semantics: Green tint accent (`TTColor.RouteSelected`) for Navigation Prompts toggle (Section 5.4 Route & Navigation Domain Tints).
  - **Typography**:
    - `MaterialTheme.typography.titleSmall` for toggle headers, `MaterialTheme.typography.labelSmall` for status badges.
* **Checked against `docs/design_guidelines.md` §5**:
  - shapes ☑ spacing ☑ colors/themes ☑ typography/icons ☑ placement ☑
* **Deviations & Justification**: None.

---

## 6. Step-by-Step Implementation Sequence

### Step 1: Database Migration to v12 (`TrackingViewsDatabaseManager.java`)
* Target: `app/src/main/java/com/atrainingtracker/trainingtracker/database/TrackingViewsDatabaseManager.java`
* Actions:
  - Increment `DB_VERSION` from 11 to 12.
  - Define constants `SHOW_LIVE_CLIMBS = "ShowLiveClimbs"` and `SHOW_NAVIGATION_HINTS = "ShowNavigationHints"`.
  - Define `CREATE_VIEWS_TABLE_V12` containing both columns as `int`.
  - In `onCreate(db)`, use `CREATE_VIEWS_TABLE_V12`.
  - In `addDefaultTab(...)`, insert `SHOW_LIVE_CLIMBS = 1` and `SHOW_NAVIGATION_HINTS = 1`.
  - In `onUpgrade(db, oldVersion, newVersion)`:
    ```java
    if (oldVersion < 12) {
        try {
            addColumn(db, VIEWS_TABLE, SHOW_LIVE_CLIMBS, "int DEFAULT 1");
        } catch (Exception e) {
            Log.w(TAG, "Column " + SHOW_LIVE_CLIMBS + " might already exist.", e);
        }
        try {
            addColumn(db, VIEWS_TABLE, SHOW_NAVIGATION_HINTS, "int DEFAULT 1");
        } catch (Exception e) {
            Log.w(TAG, "Column " + SHOW_NAVIGATION_HINTS + " might already exist.", e);
        }
        db.execSQL("UPDATE " + VIEWS_TABLE + " SET " + SHOW_LIVE_CLIMBS + " = 1 WHERE " + SHOW_LIVE_CLIMBS + " IS NULL;");
        db.execSQL("UPDATE " + VIEWS_TABLE + " SET " + SHOW_NAVIGATION_HINTS + " = 1 WHERE " + SHOW_NAVIGATION_HINTS + " IS NULL;");
    }
    ```
  - Add public methods:
    ```java
    public void updateShowLiveClimbs(long viewId, boolean showLiveClimbs) {
        updateBoolean(viewId, TrackingViewsDbHelper.SHOW_LIVE_CLIMBS, showLiveClimbs);
    }
    public void updateShowNavigationHints(long viewId, boolean showNavigationHints) {
        updateBoolean(viewId, TrackingViewsDbHelper.SHOW_NAVIGATION_HINTS, showNavigationHints);
    }
    ```

### Step 2: Repository Parity (`TrackingViewsRepository.kt`)
* Target: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/TrackingViewsRepository.kt`
* Actions:
  - Update `TrackingViewInfo`:
    ```kotlin
    data class TrackingViewInfo(
        val tabViewId: Long,
        val name: String,
        val showMap: Boolean,
        val showLapButton: Boolean,
        val showLiveSegments: Boolean,
        val showElevationProfile: Boolean,
        val showLiveClimbs: Boolean = true,
        val showNavigationHints: Boolean = true
    )
    ```
  - In `fetchTrackingViewInfo` and `getAllTrackingViews`, extract `SHOW_LIVE_CLIMBS` and `SHOW_NAVIGATION_HINTS` with fallback to `1` / `true`.
  - Add repository methods:
    ```kotlin
    suspend fun updateShowLiveClimbs(tabViewId: Long, showLiveClimbs: Boolean) {
        withContext(Dispatchers.IO) {
            viewsDbManager.updateShowLiveClimbs(tabViewId, showLiveClimbs)
            configUpdateTrigger.value++
        }
    }
    suspend fun updateShowNavigationHints(tabViewId: Long, showNavigationHints: Boolean) {
        withContext(Dispatchers.IO) {
            viewsDbManager.updateShowNavigationHints(tabViewId, showNavigationHints)
            configUpdateTrigger.value++
        }
    }
    ```

### Step 3: ViewModel State Propagation (`TrackingViewModel.kt` & `TrackingTabsViewModel.kt`)
* Target: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingViewModel.kt` and `TrackingTabsViewModel.kt`
* Actions:
  - Add `showLiveClimbs: Boolean = true` and `showNavigationHints: Boolean = true` to `TrackingScreenState`.
  - In `TrackingViewModel.kt`, map `viewInfo?.showLiveClimbs ?: true` and `viewInfo?.showNavigationHints ?: true` into `TrackingScreenState`.
  - In `TrackingTabsViewModel.kt`, add delegation methods `onUpdateShowLiveClimbs(tabId, show)` and `onUpdateShowNavigationHints(tabId, show)`.

### Step 4: Streamlined Header (`TrackingTabConfigHeader.kt` & `TrackingTabsScreen.kt`)
* Target: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabConfigHeader.kt`
* Actions:
  - Remove `FlowRow` containing checkboxes (`Show Map`, `Show Elevation Profile`, `Show Live Segments`, `Show Lap Button`).
  - Keep `OutlinedTextField` for tab name, `IconButton` for Done, and management buttons (`Add Before`, `Delete`, `Add After`).
  - In `TrackingTabsScreen.kt`, remove unused checkbox callbacks from `TrackingTabConfigHeader` invocation.

### Step 5: Spatial WYSIWYG Cockpit Editor & Runtime Gating (`SensorGridScreen.kt`)
* Target: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`
* Actions:
  - Create reusable composable `SpatialCockpitToggleCard` with Material 3 styling (12.dp rounded corners, icon, title, subtitle/status, active container color vs. outlined muted container).
  - In `ScreenMode.CONFIGURATION`:
    1. **Top Section**: Render `SpatialCockpitToggleCard` for Navigation Prompts (`showNavigationHints`).
    2. **Center Section (below sensor grid)**: Render a 2-column or stacked row of `SpatialCockpitToggleCard` for Embedded Map (`showMap`) and Elevation Profile (`showElevationProfile`).
    3. **Bottom Dock**: Render dock cards for Live Segments (`showLiveSegments`), Live Climbs (`showLiveClimbs`), and Lap Button (`showLapButton`).
  - In `ScreenMode.TRACKING`:
    - Gate `TurnPromptBanner` by `state.showNavigationHints && tuningConfig.turnPromptsEnabled`.
    - Gate `LiveClimbSheet` by `state.showLiveClimbs && tuningConfig.showLiveClimbs && activeLiveClimb != null`.

### Step 6: 9-Language Localization Parity
* Target: `app/src/main/res/values*/strings.xml` (all 9 files)
* Add strings:
  - `config_tracking__show_navigation_hints`: "Navigation Prompts" / "Abbiegehinweise"
  - `config_tracking__show_live_climbs`: "Live Climbs" / "Live-Anstiege"
  - `config_tracking__wysiwyg_active`: "Active" / "Aktiv"
  - `config_tracking__wysiwyg_hidden`: "Hidden" / "Ausgeblendet"

### Step 7: Unit & Contract Tests
* Targets:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/database/TrackingViewsDatabaseManagerTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/TrackingViewsRepositoryTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/TrackingTabWysiwygContractTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/TrackingTabLocalizationTest.kt`
* Verify 100% targeted tests pass via `./gradlew testDebugUnitTest --tests ...`.

---

## 7. Verification & Testing Protocol

```bash
# 1. Run targeted database, repository, contract, and localization tests:
./gradlew testDebugUnitTest \
  --tests com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManagerTest \
  --tests com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewsRepositoryTest \
  --tests com.atrainingtracker.trainingtracker.ui.tracking.TrackingTabWysiwygContractTest \
  --tests com.atrainingtracker.trainingtracker.ui.tracking.TrackingTabLocalizationTest

# 2. Run full clean-room regression:
./gradlew testDebugUnitTest
```
