# Stage 1 Analysis: ATT-2360 - Unified WYSIWYG Tracking Tab Configuration with Spatial Overlays and Per-Tab Popup Toggles

**Ticket**: [ATT-2360](https://atrainingtracker.atlassian.net/browse/ATT-2360)  
**Sub-task**: [ATT-2505](https://atrainingtracker.atlassian.net/browse/ATT-2505) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.1`  
**Branch**: `feature/ATT-2360`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Problem Statement & Motivation

During live workout tracking in `aTrainingTracker`, athletes customize cockpit views using multi-tab tracking layouts (`TrackingTabsScreen.kt` and `SensorGridScreen.kt`). Currently, switching into configuration mode (`ScreenMode.CONFIGURATION`) reveals `TrackingTabConfigHeader.kt`, which manages tab features via a flat row of generic checkboxes (`Show Map`, `Show Elevation Profile`, `Show Live Segments`, `Show Lap Button`).

This legacy configuration pattern exhibits several critical architectural and UX deficiencies:
1. **Missing Per-Tab HUD and Overlay Controls**:
   - Features like **Turn-by-Turn Navigation Prompts** (`TurnPromptBanner`) and **Live Climbs** (`LiveClimbSheet`) can only be enabled or disabled globally via application settings (`tuningConfig.turnPromptsEnabled`, `tuningConfig.showLiveClimbs`).
   - Athletes cannot selectively configure them per tab (e.g. enabling navigation banners and live climbs on a map-focused navigation tab while suppressing them on an interval, sensor-only, or power-focused cockpit tab).
2. **Spatial Disconnection from Cockpit Elements**:
   - Checkboxes are clustered in the header at the top of the screen, physically decoupled from where the actual elements render during tracking:
     - Navigation Prompts appear as a top HUD banner.
     - The Sensor Grid and Embedded Map/Elevation Profile occupy the center.
     - Live Segments and Live Climbs appear as bottom sheets.
     - The Lap Button floats as an action button at the bottom center.
3. **Cluttered Header & Compromised Viewport**:
   - The checkbox `FlowRow` in `TrackingTabConfigHeader` consumes valuable vertical screen space above the tab pager, compressing the sensor grid edit area.
4. **Lack of WYSIWYG (What-You-See-Is-What-You-Get) Visual Feedback**:
   - Athletes cannot visually grasp how tab feature toggles affect the overall cockpit composition without repeatedly toggling out of configuration mode into tracking mode.

---

## 2. Root Cause Analysis (Forensic Investigation & Architecture Gaps)

1. **Database Schema & Domain Model Deficiencies**:
   - In `TrackingViewsDatabaseManager.java`, `VIEWS_TABLE` tracks `SHOW_LAP_BUTTON`, `SHOW_MAP`, `SHOW_LIVE_SEGMENTS`, and `SHOW_ELEVATION_PROFILE` (`DB_VERSION = 11`).
   - `VIEWS_TABLE` lacks columns for `ShowLiveClimbs` and `ShowNavigationHints`.
   - `TrackingViewInfo` in `TrackingViewsRepository.kt` only exposes:
     ```kotlin
     data class TrackingViewInfo(
         val tabViewId: Long,
         val name: String,
         val showMap: Boolean,
         val showLapButton: Boolean,
         val showLiveSegments: Boolean,
         val showElevationProfile: Boolean
     )
     ```
   - In `TrackingViewsRepository.kt`, cursor extraction methods `fetchTrackingViewInfo` and `getAllTrackingViews` do not read or propagate live climb or navigation prompt flags.
2. **Runtime Gating Asymmetry in `SensorGridScreen.kt`**:
   - In `SensorGridScreen.kt`:
     - Navigation prompts (`TurnPromptBanner`) are gated only by `tuningConfig.turnPromptsEnabled`.
     - Live climbs (`LiveClimbSheet`) are gated by `!showLiveSegments && tuningConfig.showLiveClimbs && activeLiveClimb != null`.
   - Neither component respects tab-specific configuration flags from `TrackingScreenState` or `TrackingViewInfo`.
3. **Configuration Mode Layout Architecture in `SensorGridScreen.kt`**:
   - In `ScreenMode.CONFIGURATION`, `SensorGridScreen` only displays sensor grid add/delete/move affordances (`RowAdder`, `ColAdder`, pick-and-place guidance banner).
   - The map and elevation profile are only conditionally rendered if `state.showMap` or `state.showElevationProfile` is true, without interactive toggle controls.
   - Live sheets and the lap button are not represented interactively in configuration mode.

---

## 3. User Scope Grounding (ATT-1250)

### In-Scope Objectives
1. **SQLite Database Schema Migration (`DB_VERSION = 12`)**:
   - Add `SHOW_LIVE_CLIMBS` (`ShowLiveClimbs`, `INTEGER DEFAULT 1`) and `SHOW_NAVIGATION_HINTS` (`ShowNavigationHints`, `INTEGER DEFAULT 1`) to `VIEWS_TABLE` in `TrackingViewsDatabaseManager.java`.
   - Implement seamless upgrade in `onUpgrade` for `oldVersion < 12`.
   - Add DAO update methods `updateShowLiveClimbs(long viewId, boolean showLiveClimbs)` and `updateShowNavigationHints(long viewId, boolean showNavigationHints)`.
2. **Repository & State Model Parity**:
   - Extend `TrackingViewInfo` with `val showLiveClimbs: Boolean` and `val showNavigationHints: Boolean`.
   - Add repository update methods `updateShowLiveClimbs` and `updateShowNavigationHints` in `TrackingViewsRepository.kt`.
   - Propagate flags into `TrackingScreenState` (`TrackingViewModel.kt`).
3. **WYSIWYG Cockpit Editor in `SensorGridScreen.kt`**:
   - **Top HUD Section**: Render an interactive WYSIWYG toggle block for **Turn-by-Turn Navigation Prompts** (`ShowNavigationHints`).
   - **Center Section**: Maintain full sensor matrix grid editing, with interactive toggle cards for **Embedded Map** (`ShowMap`) and **Elevation Profile** (`ShowElevationProfile`) below the grid.
   - **Bottom Overlays & Sheets Section**: Render interactive dock cards for **Strava Live Segments** (`ShowLiveSegments`), **Live Climbs** (`ShowLiveClimbs`), and **Lap Button** (`ShowLapButton`).
   - Tapping any toggle card immediately updates the tab configuration in the repository with clear visual state changes (active tinted accent container vs. muted outlined container with clear status badge).
4. **Header Streamlining**:
   - Remove generic checkboxes from `TrackingTabConfigHeader.kt`.
   - Keep tab name editing, tab reordering (Add Before / Add After), tab deletion, and the Done button, maximizing vertical space for the cockpit editor.
5. **Runtime Cockpit Gating in `SensorGridScreen.kt`**:
   - Gate `TurnPromptBanner` by `state.showNavigationHints && tuningConfig.turnPromptsEnabled`.
   - Gate `LiveClimbSheet` by `state.showLiveClimbs && tuningConfig.showLiveClimbs && activeLiveClimb != null`.

### Explicitly Out-of-Scope Items
- Modifying global turn-by-turn navigation or climb detection algorithms (`TurnByTurnNavigationRepository`, `ClimbDetector`).
- Re-architecting the sensor field editing dialog (`EditSensorFieldDialog`).
- Changing ActivityType associations or default sensor assignments.

---

## 4. Chesterton's Fence & Requirement Archaeology (REQ-PRO-022)

1. **Original Requirement ID & Target**:
   - Refines and extends `REQ-PRO-005` (*Database Schema Integrity*) and `REQ-UI-200` (*Sensor Grid Configuration & Drag-and-Drop / Pick & Place*).
   - Establishes net-new requirement `REQ-UI-275` (*WYSIWYG Spatial Cockpit Tracking Tab Configuration & Per-Tab Overlay Gating*).
2. **Historical Origin & Commit Trace**:
   - `TrackingViewsDatabaseManager` originated in early architecture; `SHOW_LIVE_SEGMENTS` added in `DB_VERSION = 9` (Sprint 2026-40.4), and `SHOW_ELEVATION_PROFILE` added in `DB_VERSION = 10` (Sprint 2026-40.8).
   - `TrackingTabConfigHeader.kt` was implemented with basic Compose Checkboxes during initial Compose migration.
3. **Root Reason for Existing Formulation**:
   - Checkboxes in `TrackingTabConfigHeader` were an MVP implementation to quickly expose database booleans when migrating from classic Android XML views. Spatial WYSIWYG layout was deferred until both Live Climbs (`ATT-1281`) and Turn-by-Turn Navigation (`ATT-1450`) were fully stabilized.
4. **Preservation of Core Invariants**:
   - All existing tab configuration data is preserved with default `true` values on schema upgrade.
   - Sensor field layout, tile movement/swapping, and telemetry streaming remain 100% untouched.
   - ScreenMode transition state machine (`TRACKING` $\leftrightarrow$ `CONFIGURATION` $\leftrightarrow$ `PREVIEW`) is fully preserved.

---

## 5. Architectural Impact & Candidate Solutions

### Solution Architecture: Spatial WYSIWYG Cockpit Editor
* **Top Slot (Navigation HUD)**:
  - When `screenMode == ScreenMode.CONFIGURATION`: Renders `ConfigSpatialToggleCard` styled like a top navigation HUD banner with turn icon, indicating active vs. disabled state for Turn Prompts.
* **Center Slot (Map & Profile)**:
  - When `screenMode == ScreenMode.CONFIGURATION`: Renders inline spatial toggle blocks for Map and Elevation Profile below the sensor grid, allowing 1-tap toggling with immediate visual preview.
* **Bottom Dock (Sheets & Lap)**:
  - When `screenMode == ScreenMode.CONFIGURATION`: Renders a bottom control row with toggle chips/cards for Live Segments, Live Climbs, and Lap Button.
* **Data Flow**:
  - `TrackingTabsViewModel` / `TrackingViewModel` invokes `TrackingViewsRepository.updateShow*`.
  - Repository persists to SQLite and triggers `configUpdateTrigger`.
  - UI updates reactively via existing `StateFlow` pipeline.

---

## 6. Risk Assessment & Verification Strategy

1. **SQLite Database Migration Risk**:
   - Risk: Existing databases fail on upgrade or throw `SQLiteException` if columns already exist.
   - Mitigation: Use defensive `try/catch` and `addColumn` pattern identical to `DB_VERSION = 10`. Update unit tests in `TrackingViewsDatabaseManagerTest` and `TrackingViewsRepositoryTest`.
2. **UI Touch Target & Ergonomics**:
   - Risk: Interactive toggles in configuration mode interfere with sensor tile editing or row/col adders.
   - Mitigation: Keep spatial toggles outside the sensor grid bounds (top banner, below-grid blocks, and bottom dock). Ensure touch targets satisfy $\ge 48\times 48\text{ dp}$ Material 3 standards.
3. **Localization Parity**:
   - Add all new toggle titles and descriptions across all 9 supported locales (en, de, es, fr, it, ja, nl, pl, pt).
