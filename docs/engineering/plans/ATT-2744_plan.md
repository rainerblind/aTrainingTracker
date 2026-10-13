# Stage 3: Implementation Plan - ATT-2744: Improve tracking tab configuration toggles layout and eliminate text truncation

**Ticket**: [ATT-2744](https://atrainingtracker.atlassian.net/browse/ATT-2744)  
**Sub-task**: [ATT-2814](https://atrainingtracker.atlassian.net/browse/ATT-2814) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Unscheduled` (In-Sprint `2026-41.4`)  
**Active Sprint**: `2026-41.4`  
**Requirement Mapping**: `REQ-UI-295` (*Unified Scrollable Container Architecture, Full-Width Spatial Toggles Layout, and Viewport Slotting for Tracking Tab Configuration Mode*)  
**Test Mapping**: `TST-UI-255`  
**Branch**: `improvement/ATT-2744`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Problem Description & Background

In tracking tab configuration mode (`ScreenMode.CONFIGURATION`), the spatial toggle cards placed below the sensor grid are currently split across 2-column side-by-side rows and partially wrapped in an arbitrary elevated grey `Surface` dock container. On portrait mobile viewports (e.g. Pixel 10), each column provides only ~39 dp of title text clearance alongside the 20 dp icon and 85 dp status badge pill ("Aktiv" / "Ausgeblendet"), causing severe ellipsis truncation ("K...", "H...", "Live Se...", "Live-An..."). Furthermore, the arbitrary grey container causes visual confusion, and the floating `LapButton` (`+ Runde`) in `TrackingTabsScreen.kt` continues to render at `Alignment.BottomCenter` in edit mode, obscuring lower controls.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-295` (*Unified Scrollable Container Architecture, Full-Width Spatial Toggles Layout, and Viewport Slotting for Tracking Tab Configuration Mode*)
* **Test Mapping**: `TST-UI-255` (*Unified Scrollable Container Architecture, Full-Width Spatial Toggles Layout, and Viewport Slotting Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing feature suites (`TrackingTabWysiwygContractTest.kt`, `SensorGridScreenContractTest.kt`) continue to pass cleanly.
2. **Preservation of ScreenMode Invariants**: `ScreenMode.TRACKING` and `ScreenMode.PREVIEW` layouts remain 100% unaltered with independent scrolling, live maps, and weighted sizing.
3. **Data Model & Dispatcher Invariance**: Toggle persistence data models (`TrackingScreenState`, `TabToggleActions`) and database operations remain untouched.
4. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
5. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `SensorGridScreen.kt` Spatial Toggles Section
- Replace the 2-column `Row` constructs and the nested `Surface` with a single unified vertical `Column` below the sensor grid:
  ```kotlin
  // Spatial WYSIWYG Toggles for Map, Elevation, Segments, Climbs & Lap Button (REQ-UI-275 / REQ-UI-295)
  Column(
      modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 4.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
      SpatialCockpitToggleCard(
          title = stringResource(R.string.config_tracking__show_map),
          isActive = state.showMap,
          onToggle = { tabToggleActions.onToggleMap(it) },
          icon = Icons.Default.Map,
          modifier = Modifier.fillMaxWidth()
      )
      SpatialCockpitToggleCard(
          title = stringResource(R.string.config_tracking__showElevationProfile),
          isActive = state.showElevationProfile,
          onToggle = { tabToggleActions.onToggleElevationProfile(it) },
          icon = Icons.Default.ShowChart,
          modifier = Modifier.fillMaxWidth()
      )
      SpatialCockpitToggleCard(
          title = stringResource(R.string.config_tracking__showLiveSegments),
          isActive = state.showLiveSegments,
          onToggle = { tabToggleActions.onToggleLiveSegments(it) },
          icon = Icons.Default.DirectionsRun,
          modifier = Modifier.fillMaxWidth()
      )
      SpatialCockpitToggleCard(
          title = stringResource(R.string.config_tracking__show_live_climbs),
          isActive = state.showLiveClimbs,
          onToggle = { tabToggleActions.onToggleLiveClimbs(it) },
          icon = Icons.Default.Terrain,
          modifier = Modifier.fillMaxWidth()
      )
      SpatialCockpitToggleCard(
          title = stringResource(R.string.config_tracking__showLapButton),
          isActive = state.showLapButton,
          onToggle = { tabToggleActions.onToggleLapButton(it) },
          icon = Icons.Default.Timer,
          modifier = Modifier.fillMaxWidth()
      )
  }
  ```

### Component 2: `TrackingTabsScreen.kt` Floating Lap Button Gating
- Update `shouldShowLapButton` condition at line 701:
  ```kotlin
  // --- Conditionally show the Lap Button (suppressed in CONFIGURATION mode per REQ-UI-295)
  val shouldShowLapButton = currentViewInfo?.showLapButton == true && screenMode != ScreenMode.CONFIGURATION
  ```

### Component 3: `TrackingTabWysiwygContractTest.kt`
- Update existing contract tests to:
  - Verify that spatial toggles below the sensor grid are rendered full-width without 2-column side-by-side rows.
  - Verify that the arbitrary grey dock `Surface` with `tonalElevation = 2.dp` is absent.
  - Verify that `TrackingTabsScreen.kt` explicitly gates `shouldShowLapButton` with `screenMode != ScreenMode.CONFIGURATION`.

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* **Reference screen / component**: `SensorGridScreen.kt` top Turn-by-Turn Navigation Hints toggle card (`SpatialCockpitToggleCard`, line 343).
* **Reused components**: `SpatialCockpitToggleCard` (`SensorGridScreen.kt`), Material 3 `Column`, `Arrangement.spacedBy(6.dp)`.
* **Theme tokens**: shapes `RoundedCornerShape(12.dp)`, spacing `16.dp` horizontal padding, `6.dp` vertical card spacing, colors `MaterialTheme.colorScheme.surfaceVariant`, `MaterialTheme.colorScheme.primaryContainer`.
* **New one-off styles & justification**: None. Perfectly harmonized with the existing top Navigation Hints toggle card.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `SensorGridScreen.kt` Layout
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/views/SensorGridScreen.kt`
* Changes:
  - Replace lines 438–503 with the unified vertical `Column` of full-width `SpatialCockpitToggleCard`s.
  - Remove unused nested `Surface` with `tonalElevation = 2.dp`.

### Step 2: Suppress Floating Lap Button in `TrackingTabsScreen.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/views/TrackingTabsScreen.kt`
* Changes:
  - Update line 701: `val shouldShowLapButton = currentViewInfo?.showLapButton == true && screenMode != ScreenMode.CONFIGURATION`.

### Step 3: Update Contract Tests in `TrackingTabWysiwygContractTest.kt`
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/TrackingTabWysiwygContractTest.kt`
* Changes:
  - Update `testSensorGridScreen_configurationMode_spatialTogglesDeclaredAndAccessible` to assert that spatial toggles are rendered full-width without 2-column rows and without the nested dock `Surface`.
  - Add test asserting `TrackingTabsScreen.kt` suppresses floating `LapButton` in `ScreenMode.CONFIGURATION`.

### Step 4: Verification via Targeted Tests
* Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.TrackingTabWysiwygContractTest"` to confirm 100% pass rate.
