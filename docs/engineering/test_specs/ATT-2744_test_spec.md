# Stage 2: Requirement & Test Specification - ATT-2744: Improve tracking tab configuration toggles layout and eliminate text truncation

**Ticket**: [ATT-2744](https://atrainingtracker.atlassian.net/browse/ATT-2744)  
**Sub-task**: [ATT-2813](https://atrainingtracker.atlassian.net/browse/ATT-2813) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Unscheduled` (In-Sprint `2026-41.4`)  
**Active Sprint**: `2026-41.4`  
**Requirement Mapping**: `REQ-UI-295` (*Unified Scrollable Container Architecture, Full-Width Spatial Toggles Layout, and Viewport Slotting for Tracking Tab Configuration Mode*)  
**Test Spec ID**: `TST-UI-255`  
**Branch**: `improvement/ATT-2744`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Requirement Specification (REQ-UI-295)

### 1.1 Problem Statement & Rationale
In tracking tab configuration mode (`ScreenMode.CONFIGURATION`), spatial toggles below the sensor grid are currently split across 2-column side-by-side rows inside and outside an arbitrary elevated grey `Surface` container. On portrait mobile viewports (e.g. Pixel 10), each column provides only ~39 dp of title text clearance alongside the 20 dp icon and 85 dp status badge pill ("Aktiv" / "Ausgeblendet"), causing severe ellipsis truncation ("K...", "H...", "Live Se...", "Live-An..."). Furthermore, the arbitrary grey container causes visual confusion, and the floating `LapButton` (`+ Runde`) in `TrackingTabsScreen.kt` continues to render at `Alignment.BottomCenter` in edit mode, obscuring lower controls.

### 1.2 Functional & Architectural Requirements
1. **Full-Width Spatial Toggle Architecture**:
   - In `ScreenMode.CONFIGURATION`, all 5 spatial feature toggles below the sensor grid (Map `showMap`, Elevation Profile `showElevationProfile`, Live Segments `showLiveSegments`, Live Climbs `showLiveClimbs`, and Lap Button `showLapButton`) SHALL render as a single vertical sequence of full-width `SpatialCockpitToggleCard`s enclosed in a `Column` with `Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)` and `verticalArrangement = Arrangement.spacedBy(6.dp)`.
   - Side-by-side 2-column `Row` constructs for spatial toggles SHALL be completely eliminated, providing ~320 dp of usable horizontal text clearance and ensuring that all titles and status badge pills display completely without ellipsis truncation across all 9 supported application locales.
2. **Elimination of Arbitrary Grey Dock Container**:
   - The arbitrary nested grey dock card (`Surface` with tonal elevation 2 dp enclosing Live Segments, Live Climbs, and Lap Button) SHALL be eliminated. All spatial toggles SHALL share identical card shapes (`RoundedCornerShape(12.dp)`), margins, and visual tokens.
3. **Floating Lap Button Edit Mode Suppression**:
   - In `TrackingTabsScreen.kt`, the floating `LapButton` (`+ Runde`) at `Alignment.BottomCenter` SHALL be suppressed whenever `screenMode == ScreenMode.CONFIGURATION` (`shouldShowLapButton = currentViewInfo?.showLapButton == true && screenMode != ScreenMode.CONFIGURATION`), preventing it from floating over and occluding lower configuration toggles.
4. **Preserved Invariants**:
   - Unified scrollable configuration container (`verticalScroll(rememberScrollState()).navigationBarsPadding()`) remains strictly preserved.
   - Exclusion of interactive live map and elevation profile in `CONFIGURATION` mode remains strictly preserved.
   - `ScreenMode.TRACKING` and `ScreenMode.PREVIEW` layouts remain 100% unaltered.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Refines `REQ-UI-295` (*Unified Scrollable Container Architecture and Viewport Insets Slotting for Tracking Tab Configuration Mode (`ScreenMode.CONFIGURATION`)*) and `REQ-UI-275` Clause 5 (*Spatial WYSIWYG Cockpit Editor*).
* **Historical Origin & Commit Trace**: Sprint 2026-41.1 commit `1607a7ec` (`ATT-2360`) and Sprint 2026-41.3 commit `e5033b5c` (`ATT-2620`).
* **Root Reason for Existing Formulation**: Live Segments, Live Climbs, and Lap Button were conceptualized as bottom-dock items, prompting the author to group them into an elevated grey `Surface` container and pair toggles into 2-column rows to save vertical space. However, on portrait smartphone viewports (e.g. Pixel 10), 2-column rows restrict title clearance to ~39 dp against an 85 dp status pill, causing severe ellipsis truncation ("K...", "H...", "Live Se...", "Live-An..."), while the grey container created arbitrary visual hierarchy, and the floating `LapButton` was not suppressed in edit mode.
* **Preservation of Core Invariants**: Unified scrollable container (`verticalScroll(rememberScrollState()).navigationBarsPadding()`), live map/elevation exclusion in config mode, runtime toggle gating, and 100% full-suite test pass rate remain strictly preserved.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Full-Width Toggles & Elimination of 2-Column Rows)**:
  * *Given* an athlete in `ScreenMode.CONFIGURATION`,
  * *When* inspecting the spatial toggle cards below the sensor grid,
  * *Then* Map, Elevation Profile, Live Segments, Live Climbs, and Lap Button SHALL render as full-width cards in a single column without 2-column side-by-side rows and without an arbitrary nested grey dock card.
* **Criterion 2 (Text Truncation Elimination)**:
  * *Given* an athlete viewing tracking tab configuration on a portrait mobile screen across any of the 9 supported languages,
  * *When* the toggle cards render,
  * *Then* the full labels ("Karte", "Höhenprofil", "Live-Segmente", "Live-Anstiege", "Runden-Knopf", etc.) SHALL be fully readable without ellipsis truncation.
* **Criterion 3 (Floating Lap Button Suppression in Edit Mode)**:
  * *Given* an athlete in `ScreenMode.CONFIGURATION` on a tab with the lap button enabled,
  * *When* scrolling through configuration controls,
  * *Then* the floating `LapButton` (`+ Runde`) SHALL NOT be displayed.
* **Criterion 4 (Tracking Mode Fidelity)**:
  * *Given* an athlete in `ScreenMode.TRACKING`,
  * *When* tracking is active,
  * *Then* the floating `LapButton` SHALL display at `Alignment.BottomCenter` whenever enabled for that tab.

### 1.4 System Invariants
1. Zero regression in `ScreenMode.TRACKING` and `ScreenMode.PREVIEW`.
2. Zero regression in tab configuration persistence and SQLite storage.
3. 100% clean-room unit test pass rate.

---

## 2. Test Specification (TST-UI-255)

### Test Case 1: `testSensorGridScreen_configurationMode_fullWidthTogglesEliminatesTwoColumnRows` (`TST-UI-255.1`)
* **Scope**: Contract / Architectural Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/TrackingTabWysiwygContractTest.kt`
* **Preconditions**: `SensorGridScreen.kt` exists.
* **Action**: Parse `SensorGridScreen.kt` and inspect the `ScreenMode.CONFIGURATION` spatial toggle section.
* **Expected Result**:
  - Assert that all 5 lower toggles (Map, Elevation, Live Segments, Live Climbs, Lap Button) are rendered with `Modifier.fillMaxWidth()` or enclosed in a unified Column.
  - Assert that no 2-column side-by-side `Row` constructs exist for spatial toggle cards.
  - Assert that no nested grey dock `Surface` with `tonalElevation = 2.dp` wraps Live Segments, Climbs, and Lap Button.

### Test Case 2: `testTrackingTabsScreen_suppressesLapButtonInConfigurationMode` (`TST-UI-255.2`)
* **Scope**: Contract / Architectural Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/TrackingTabWysiwygContractTest.kt`
* **Preconditions**: `TrackingTabsScreen.kt` exists.
* **Action**: Parse `TrackingTabsScreen.kt` and inspect the `shouldShowLapButton` evaluation.
* **Expected Result**:
  - Assert that `shouldShowLapButton` explicitly requires `screenMode != ScreenMode.CONFIGURATION`.

### Test Case 3: 9-Language String Verification Audit (`TST-UI-255.3`)
* **Scope**: Localization Parity Test
* **Goal**: Verify that all 6 toggle string resources exist, are non-empty, and contain zero unescaped format tokens across all 9 supported application locales:
  - `config_tracking__show_navigation_hints`
  - `config_tracking__show_map`
  - `config_tracking__showElevationProfile`
  - `config_tracking__showLiveSegments`
  - `config_tracking__show_live_climbs`
  - `config_tracking__showLapButton`
  - `config_tracking__wysiwyg_active`
  - `config_tracking__wysiwyg_hidden`
* **Locales**: `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
* **Expected Result**: 100% parity across all 9 locales.

### Test Case 4: Full Clean-Room Regression Suite (`TST-UI-255.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: 100% pass rate across all project tests with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-255.1` | Contract | `SensorGridScreen.kt` spatial toggles layout | `REQ-UI-295` (Clause 2) | Specified |
| `TST-UI-255.2` | Contract | `TrackingTabsScreen.kt` `shouldShowLapButton` | `REQ-UI-295` (Clause 4) | Specified |
| `TST-UI-255.3` | Localization | 9-Language string resources | `REQ-UI-295` (Clause 2) | Specified |
| `TST-UI-255.4` | Regression | Full Gradle unit test suite | `REQ-UI-295` (Invariants) | Specified |
