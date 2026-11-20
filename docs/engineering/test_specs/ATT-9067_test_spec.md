# Stage 2: Requirement & Test Specification - ATT-9067: Round Layers icon on Workout Details map does nothing when tapped

**Ticket**: `ATT-9067` (`1 SP`, `Bug`, `severity-minor`) | **Parent Epic**: `ATT-68` | **Target Release**: `V5.0.0`  
**Active Sprint**: `466` (`2026-11-20_02_Spät`) | **Branch**: `feature/ATT-9067` (base `392e312c`)  
**Requirement Mapping**: `REQ-REV-016` (`REQ-REV-016.1`–`.2`)  
**Test Spec ID**: `TST-REV-017` (`TST-REV-017.1`–`.2`) | **Author**: AI Agent 1 (`agent1`) | **Date**: 2026-11-20

---

## 1. Requirement Specification (`REQ-REV-016`)

### 1.1 Attributes
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt` (`1,096 / 1,500` LoC), `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt` (`1,030 / 1,500` LoC)

### 1.2 Functional & Architectural Requirements
1. **Floating Map Controls Gesture & Z-Order Isolation (`REQ-REV-016.1`)**:
   - In `MapDetailLayout.kt` (`mapBox`), the `onInteractiveCanvasTouch(true)` `pointerInput(Unit)` wrapper **SHALL** enclose **strictly** `ATrainingTrackerMap` and **SHALL NOT** wrap the sibling `FLOATING CONTROLS` `Box` (`overlay()` and `topEndActions`).
   - The `FLOATING CONTROLS` `Box` **SHALL** apply `.zIndex(1f)` above `GoogleMap` `AndroidView` and clear `onInteractiveCanvasTouch(false)` on `PointerEventPass.Initial` (when `onInteractiveCanvasTouch != null`) so tapping the round Layers button or floating Share button never toggles `isTouchingInteractiveCanvas` / `HorizontalPager(userScrollEnabled)` mid-gesture.
2. **Hoisted `TrackLayersMenuButton` & Testability (`REQ-REV-016.2`)**:
   - `TrackOnMapScreen.kt` **SHALL** extract and deduplicate the duplicated `mapTopEndActions` / `topEndActions` Layers `Surface` + `DropdownMenu` block into a single reusable `TrackLayersMenuButton` composable with explicit `testTag`s (`track_layers_button`, `track_layers_menu`, `track_layers_item_<TrackType.name>`), hoisting `showTrackMenu` cleanly and preserving `controlsTopOffsetDp` (`REQ-UI-491`), `44.dp` `CircleShape` overlay styling (`REQ-UI-500.5`), `TrackType.entries` (`BEST`, `GPS`, `FUSED`, `ATT_FUSED`), and `enabledTrackTypes` persistence (`REQ-MAP-013`).
   - `MapDetailLayout.kt <= 1,500` LoC and `TrackOnMapScreen.kt <= 1,500` LoC **MUST** remain preserved (`Localization Parity: N/A - no new or modified UI strings`).

## 2. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
- *Original Requirement ID & Target*: Net-new `REQ-REV-016`, complementing `REQ-UI-491` (floating control status-bar clearance), `REQ-UI-500.5` (unified `topEndActions` docking & `44.dp` `CircleShape` invariants), `REQ-MAP-013` (persistent track selection), and `REQ-REV-015` (offscreen `snapshotHeader` isolation); targets `MapDetailLayout.kt` and `TrackOnMapScreen.kt`.
- *Historical Origin & Commit Trace*: `b185d9b1` (`ATT-6214` / `REQ-UI-500` moved Layers into `MapDetailLayout.topEndActions`), `3652c31c` (`ATT-8080` `ATT_FUSED` track source), `ba5e86e4` (`ATT-9168` / `REQ-REV-015` offscreen `snapshotHeader` fix).
- *Root Reason for Existing Formulation*: `mapBox` (`MapDetailLayout.kt:361-375`) attached `onInteractiveCanvasTouch(true)` to the outer container to lock `HorizontalPager` swiping during map panning (`REQ-UI-408.4`), unintentionally enclosing `FLOATING CONTROLS` and coupling button taps to mid-gesture `userScrollEnabled` flips, while `TrackOnMapScreen.kt` duplicated inline `showTrackMenu` lambdas without `testTag`s.
- *Preservation of Core Invariants*: Map-canvas panning on `ATrainingTrackerMap` still triggers `onInteractiveCanvasTouch(true)` outside the `32.dp` edge gutter; `controlsTopOffsetDp` (`REQ-UI-491`), `showFloatingShareButton = workoutData == null` & `44.dp` `CircleShape` (`REQ-UI-500.5`), `REQ-MAP-013` persistence, and `REQ-REV-015` offscreen `snapshotHeader` isolation remain intact.

## 3. Acceptance Criteria
- **AC-1 (`REQ-REV-016.1` — Map Floating Controls Gesture & Z-Order Isolation)**:
  - *Given* `WorkoutDetailTabbedContent` hosting `MapDetailLayout(showMap = true)` with `onInteractiveCanvasTouch` wired and `TrackLayersMenuButton` in `topEndActions`,
  - *When* the user taps the round `track_layers_button` (`44.dp`, `Icons.Default.Layers`) at `Alignment.TopEnd` (including inner coordinates `< width - 32.dp`),
  - *Then* `onInteractiveCanvasTouch(true)` is NOT emitted, `HorizontalPager(userScrollEnabled)` remains stable without mid-gesture flipping, and `FLOATING CONTROLS` (`zIndex(1f)`) receives the click cleanly above `ATrainingTrackerMap` / `GoogleMap`.
- **AC-2 (`REQ-REV-016.2` — Hoisted `TrackLayersMenuButton` & Track Selection)**:
  - *Given* `TrackOnMapScreen` (both tabbed and non-tabbed fallback modes) with `showTechnicalTracks = true` and `availableTrackTypes` / `enabledTrackTypes`,
  - *When* `track_layers_button` is tapped,
  - *Then* `track_layers_menu` (`DropdownMenu`) opens displaying all `TrackType.entries` (`BEST`, `GPS`, `FUSED`, `ATT_FUSED`) and tapping an available `track_layers_item_<TYPE>` invokes `onToggleTrackType(type)` while preserving `REQ-UI-491`, `REQ-UI-500.5`, and `REQ-MAP-013` (`Localization Parity: N/A - no new or modified UI strings`).

## 4. Test Specification (`TST-REV-017`)
- **`TST-REV-017.1` (Tabbed Map Layers Click, Gesture Isolation & Menu Selection — `TrackOnMapLayersUiTest.kt`, `@GraphicsMode(GraphicsMode.Mode.NATIVE)` `androidx.compose.ui.test.junit4.v2.createComposeRule`)**:
  - Render `WorkoutDetailTabbedContent` -> `MapDetailLayout(showMap = true)` -> `TrackLayersMenuButton` (`showTechnicalTracks = true`, `availableTrackTypes = setOf(TrackType.BEST, TrackType.GPS, TrackType.ATT_FUSED)`); verify `isTouchingInteractiveCanvas` / `onInteractiveCanvasTouch(true)` is NOT triggered when clicking `track_layers_button` (and IS triggered when touching the underlying map canvas), `track_layers_menu` opens, all `TrackType.entries` items are displayed, and clicking `track_layers_item_GPS` / `track_layers_item_ATT_FUSED` updates `enabledTrackTypes` (`0` deprecation warnings, `0` `src/main` reads).
- **`TST-REV-017.2` (Non-Tabbed Fallback & Architecture/Governance Contracts — `TrackOnMapLayersUiTest.kt`, Contracts)**:
  - Render standalone `TrackLayersMenuButton` inside `MapDetailLayout(showMap = true, showHeader = true)` (`useStatusBarsPadding = true`); verify `track_layers_button` click opens `track_layers_menu` and toggles `TrackType`; execute `MapDetailLayoutTest`, `MapDetailLayoutOffscreenHeaderUiTest`, `WorkoutDetailTabbedContentTest`, `ArchitectureConformanceContractTest` (`MapDetailLayout.kt <= 1,500`, `TrackOnMapScreen.kt <= 1,500`), `SourceTextReadingAuditContractTest` (`0` `src/main` reads), and `UiReflectionOnlyTestAuditContractTest` (`Localization Parity: N/A - no new or modified UI strings`).

## 5. Traceability Matrix

| Test Case | Scope | Test Suite(s) | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-REV-017.1` | Tabbed `WorkoutDetailTabbedContent` + `MapDetailLayout(showMap = true)` Layers click, `FLOATING CONTROLS` `.zIndex(1f)` & `onInteractiveCanvasTouch(false)` stability, `DropdownMenu` open & track toggle | `TrackOnMapLayersUiTest` (`@GraphicsMode(NATIVE)`, `v2.createComposeRule`) | `REQ-REV-016.1`, `REQ-REV-016.2` | Planned |
| `TST-REV-017.2` | Non-tabbed `TrackLayersMenuButton` parity & regression/build contracts | `TrackOnMapLayersUiTest`, `MapDetailLayoutTest`, `MapDetailLayoutOffscreenHeaderUiTest`, `WorkoutDetailTabbedContentTest`, `ArchitectureConformanceContractTest`, `SourceTextReadingAuditContractTest`, `UiReflectionOnlyTestAuditContractTest` | `REQ-REV-016.1`, `REQ-REV-016.2` | Planned |
