# Stage 2: Requirement & Test Specification - ATT-2360: Unified WYSIWYG Tracking Tab Configuration with Spatial Overlays and Per-Tab Popup Toggles

**Ticket**: [ATT-2360](https://atrainingtracker.atlassian.net/browse/ATT-2360)  
**Sub-task**: [ATT-2506](https://atrainingtracker.atlassian.net/browse/ATT-2506) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-UI-275` (*Unified WYSIWYG Tracking Tab Configuration with Spatial Overlays and Per-Tab Popup Toggles*)  
**Test Spec ID**: `TST-UI-235` (*Unified WYSIWYG Tracking Tab Configuration Verification*)  
**Branch**: `feature/ATT-2360`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Requirement Specification (`REQ-UI-275`)

### 1.1 Problem Statement & Rationale
Currently, `TrackingTabConfigHeader.kt` manages tab feature flags via a flat row of generic checkboxes (`Show Map`, `Show Elevation Profile`, `Show Live Segments`, `Show Lap Button`) situated above the tab row. This layout causes severe spatial disconnection, header clutter, and lacks per-tab gating for Turn-by-Turn Navigation Prompts and Live Climbs. In configuration mode (`ScreenMode.CONFIGURATION`), athletes should experience an intuitive, spatial WYSIWYG editor where controls correspond directly to their physical placement in the tracking cockpit.

### 1.2 Functional & Architectural Requirements
The system SHALL transform Configuration Mode (`ScreenMode.CONFIGURATION`) in `SensorGridScreen.kt` into a spatial WYSIWYG cockpit configuration interface and provide per-tab gating for Turn-by-Turn Navigation Prompts and Live Climbs (ATT-2360):
1. **Database Schema & Migration (`TrackingViewsDatabaseManager.java`)**:
   - The database version SHALL be incremented to `DB_VERSION = 12`.
   - `VIEWS_TABLE` SHALL include `SHOW_LIVE_CLIMBS` (`ShowLiveClimbs int DEFAULT 1`) and `SHOW_NAVIGATION_HINTS` (`ShowNavigationHints int DEFAULT 1`).
   - In `onUpgrade` for `oldVersion < 12`, each column addition SHALL be wrapped in a defensive `try/catch` block and execute:
     ```sql
     UPDATE ViewsTable SET ShowLiveClimbs = 1 WHERE ShowLiveClimbs IS NULL;
     UPDATE ViewsTable SET ShowNavigationHints = 1 WHERE ShowNavigationHints IS NULL;
     ```
     ensuring existing user configurations retain full feature availability without regression.
   - `TrackingViewsDatabaseManager` SHALL expose:
     - `public void updateShowLiveClimbs(long viewId, boolean showLiveClimbs)`
     - `public void updateShowNavigationHints(long viewId, boolean showNavigationHints)`
2. **Domain Model & Repository Parity (`TrackingViewsRepository.kt`)**:
   - `TrackingViewInfo` SHALL declare:
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
     providing backward-compatible defaults for existing constructors.
   - `TrackingViewsRepository` SHALL expose:
     - `suspend fun updateShowLiveClimbs(tabViewId: Long, showLiveClimbs: Boolean)`
     - `suspend fun updateShowNavigationHints(tabViewId: Long, showNavigationHints: Boolean)`
     both triggering `configUpdateTrigger` to emit updated state reactively.
3. **ViewModel State Propagation (`TrackingViewModel.kt`)**:
   - `TrackingScreenState` SHALL declare:
     ```kotlin
     val showLiveClimbs: Boolean = true,
     val showNavigationHints: Boolean = true
     ```
     populated directly from `viewInfo?.showLiveClimbs ?: true` and `viewInfo?.showNavigationHints ?: true`.
4. **Streamlined Tab Header (`TrackingTabConfigHeader.kt`)**:
   - Generic checkboxes (`Show Map`, `Show Elevation Profile`, `Show Live Segments`, `Show Lap Button`) SHALL be removed from `TrackingTabConfigHeader`.
   - Tab name editing, relative tab addition (`Add Before` / `Add After`), tab deletion, and the Done button SHALL be preserved, recovering vertical space for the cockpit editor.
5. **Spatial WYSIWYG Cockpit Editor (`SensorGridScreen.kt`)**:
   - In `ScreenMode.CONFIGURATION`:
     - *(a) Top HUD Section*: An interactive toggle card representing the Turn-by-Turn Navigation Prompts banner (`showNavigationHints`).
     - *(b) Center Section*: Below the interactive sensor grid, interactive spatial toggle blocks for Embedded Map (`showMap`) and Elevation Profile (`showElevationProfile`).
     - *(c) Bottom Dock*: Interactive dock cards for Strava Live Segments (`showLiveSegments`), Live Climbs (`showLiveClimbs`), and Lap Button (`showLapButton`).
     - Tapping any toggle card SHALL immediately persist the new state and provide immediate tactile visual state feedback (active highlighted container vs. muted outlined container with state badge).
6. **Runtime Cockpit Gating (`SensorGridScreen.kt`)**:
   - In `ScreenMode.TRACKING`:
     - `TurnPromptBanner` SHALL render IF AND ONLY IF `state.showNavigationHints && tuningConfig.turnPromptsEnabled`.
     - `LiveClimbSheet` SHALL render IF AND ONLY IF `state.showLiveClimbs && tuningConfig.showLiveClimbs && activeLiveClimb != null`.
7. **100% 9-Language Localization Parity**:
   - All newly introduced UI toggle labels and accessibility descriptions SHALL be localized across all 9 application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Refines and extends `REQ-PRO-005` (*Database Schema Integrity*) and `REQ-UI-200` (*Sensor Grid Configuration & Drag-and-Drop / Pick & Place*).
2. *Historical Origin & Commit Trace*: Sprint 2026-40.4 (`ATT-1281` introduced Live Climbs); Sprint 2026-40.6 (`ATT-1450` introduced Turn-by-Turn Navigation); `TrackingTabConfigHeader.kt` previously managed tab booleans via top checkboxes.
3. *Root Reason for Existing Formulation*: Checkboxes were an MVP implementation in the tab header. Athletes could not disable navigation prompts or live climbs per tab, and the header crowded vertical space.
4. *Preservation of Core Invariants*: Full backward compatibility for existing user databases (defaulting newly added flags to true/1), sensor tile movement and editing, and 100% clean-room test suite pass rate are strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **AC-1 (Spatial WYSIWYG Cockpit Editor)**:
  * *Given* an athlete in configuration mode (`ScreenMode.CONFIGURATION`) on any tracking tab,
  * *When* the screen renders,
  * *Then* the header SHALL NOT display checkboxes, and the cockpit editor SHALL display spatial toggle cards for Navigation Prompts at top, Map and Elevation Profile below the sensor grid, and Live Segments, Live Climbs, and Lap Button at bottom.
* **AC-2 (1-Tap Immediate Persistence & Feedback)**:
  * *Given* an athlete tapping any spatial toggle card in configuration mode,
  * *When* tapped,
  * *Then* the corresponding flag SHALL immediately update in SQLite, and the toggle card SHALL visually reflect active vs. disabled state.
* **AC-3 (Safe Database Migration v11 to v12)**:
  * *Given* an existing user database upgraded to `DB_VERSION = 12`,
  * *When* upgraded,
  * *Then* all existing tracking tabs SHALL default `ShowLiveClimbs` and `ShowNavigationHints` to 1 (true) with zero database corruption or feature loss.
* **AC-4 (Per-Tab Runtime Gating)**:
  * *Given* a tracking tab with `showNavigationHints == false` or `showLiveClimbs == false`,
  * *When* active tracking is running,
  * *Then* navigation prompts or live climbs SHALL NOT be displayed on that tab even if globally enabled in settings.

---

## 2. Test Specification (`TST-UI-235`)

### 2.1 Test Cases & Verification Procedures

#### `TST-UI-235.1`: Database Schema & Migration Unit Tests (`TrackingViewsDatabaseManagerTest.kt`)
* **Test 1.1**: Verify `DB_VERSION` constant is 12.
* **Test 1.2**: Verify `CREATE_VIEWS_TABLE_V12` contains `SHOW_LIVE_CLIMBS` and `SHOW_NAVIGATION_HINTS`.
* **Test 1.3**: Verify `onUpgrade` for `oldVersion < 12` executes `ALTER TABLE` and defensive `UPDATE` statements setting defaults to 1.
* **Test 1.4**: Verify `updateShowLiveClimbs` and `updateShowNavigationHints` execute updates with expected SQL arguments.

#### `TST-UI-235.2`: Repository & State Propagation Unit Tests (`TrackingViewsRepositoryTest.kt`)
* **Test 2.1**: Verify `fetchTrackingViewInfo` and `getAllTrackingViews` extract `showLiveClimbs` and `showNavigationHints` from cursor.
* **Test 2.2**: Verify `updateShowLiveClimbs` and `updateShowNavigationHints` invoke `TrackingViewsDatabaseManager` and increment `configUpdateTrigger`.
* **Test 2.3**: Verify `TrackingViewInfo` default parameter constructor retains `showLiveClimbs = true` and `showNavigationHints = true`.

#### `TST-UI-235.3`: UI Architecture & Contract Tests (`TrackingTabWysiwygContractTest.kt`)
* **Test 3.1**: Verify `TrackingTabConfigHeader.kt` does not contain generic checkbox rows.
* **Test 3.2**: Verify `SensorGridScreen.kt` defines spatial WYSIWYG toggles in `ScreenMode.CONFIGURATION`.
* **Test 3.3**: Verify `TurnPromptBanner` in `SensorGridScreen.kt` is gated by `state.showNavigationHints && tuningConfig.turnPromptsEnabled`.
* **Test 3.4**: Verify `LiveClimbSheet` in `SensorGridScreen.kt` is gated by `state.showLiveClimbs && tuningConfig.showLiveClimbs`.

#### `TST-UI-235.4`: 9-Language Localization Parity Audit (`TrackingTabLocalizationTest.kt`)
* **Test 4.1**: Verify all new tracking config keys exist and are non-empty across all 9 locales:
  - `values/strings.xml` (EN)
  - `values-de/strings.xml` (DE)
  - `values-es/strings.xml` (ES)
  - `values-fr/strings.xml` (FR)
  - `values-it/strings.xml` (IT)
  - `values-ja/strings.xml` (JA)
  - `values-nl/strings.xml` (NL)
  - `values-pl/strings.xml` (PL)
  - `values-pt/strings.xml` (PT)
* **Test 4.2**: Verify format specifiers match 100% across all 9 locales.

#### `TST-UI-235.5`: Clean-Room Full Suite Regression
* Execute `./gradlew testDebugUnitTest` verifying 100% pass rate with zero regressions across the entire application test suite.

---

## 3. Traceability Matrix

| Requirement Clause | Test Case ID | Test Class / File | Target Invariant / Verification |
| :--- | :--- | :--- | :--- |
| `REQ-UI-275.1` | `TST-UI-235.1` | `TrackingViewsDatabaseManagerTest.kt` | Database v12 schema, column creation, and upgrade defaults |
| `REQ-UI-275.2` | `TST-UI-235.2` | `TrackingViewsRepositoryTest.kt` | Repository extraction and update methods with StateFlow trigger |
| `REQ-UI-275.3` | `TST-UI-235.3` | `TrackingTabWysiwygContractTest.kt` | ViewModel state propagation into `TrackingScreenState` |
| `REQ-UI-275.4` | `TST-UI-235.3` | `TrackingTabWysiwygContractTest.kt` | Header streamlining (removal of generic checkboxes) |
| `REQ-UI-275.5` | `TST-UI-235.3` | `TrackingTabWysiwygContractTest.kt` | Spatial WYSIWYG cockpit editor in `ScreenMode.CONFIGURATION` |
| `REQ-UI-275.6` | `TST-UI-235.3` | `TrackingTabWysiwygContractTest.kt` | Per-tab runtime gating for TurnPromptBanner and LiveClimbSheet |
| `REQ-UI-275.7` | `TST-UI-235.4` | `TrackingTabLocalizationTest.kt` | 100% 9-language translation parity |
| `REQ-PRO-001` | `TST-UI-235.5` | Clean-Room Suite | `./gradlew testDebugUnitTest` 100% pass rate |
