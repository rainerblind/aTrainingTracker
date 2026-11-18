# Stage 2: Requirement & Test Specification - ATT-8326: Executable/Attested Verification for Maps & Navigation (7 TSTs)

**Ticket**: `ATT-8326` (`1 SP`) | **Parent Epic**: `ATT-8138` | **Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-18_02_Spät`  
**Requirements**: `REQ-SET-050`, `REQ-UI-112`, `REQ-SET-051`, `REQ-SET-053`, `REQ-SET-054`, `REQ-MAP-016`, `REQ-MAP-018`  
**Test IDs**: `TST-NAV-001`, `TST-NAV-002`, `TST-NAV-003`, `TST-NAV-005`, `TST-NAV-006`, `TST-MAP-010`, `TST-MAP-020` (all `[Implemented]` until Stage 5)  
**Branch**: `feature/ATT-8326` | **Date**: 2026-11-18

---

## 1. Requirement Coverage & Scope Alignment
* **Existing Requirements Preserved**: No `REQ-*` IDs are created or altered (`REQ-SET-050` [Medium/Functional/Unit,Integration], `REQ-UI-112` [Medium/Functional/Unit], `REQ-SET-051` [Medium/Functional/Unit,Integration], `REQ-SET-053` [Medium/Functional/Unit,Integration], `REQ-SET-054` [Medium/Functional/Unit,Integration], `REQ-MAP-016` [Medium/Functional/Unit], `REQ-MAP-018` [Medium/Functional/Review]). `SYS.2`/`SYS.3` impact: None (`Derived (SW-only)` UI/map verification).
* **Code-Grounded Reconciliations in `docs/tests/tst_maps_navigation.md` (`v1.0.4`)**:
  * `TST-NAV-001` grounded against `AppNavigationDrawer.kt:187-259` (`drawer__training`, `drawer__maps`, `drawer__my_stuff`, `prefsOnlineCommunities`, `drawer__settings`, `drawer__info_support`) where `Sport Types` & `Training Zones` reside in `Settings`, and `Strava`, `Dropbox`, `Export` in `Synchronization` (`REQ-UI-397/422/463`).
  * `TST-NAV-002` reconciled from stale `"Dateiexport"` prose to `"Export"` (`values-de/strings.xml:308`, matching `REQ-UI-112`).
  * `TST-NAV-003/005/006` grounded against `NavRoutes.kt:215-218`, `ATrainingTrackerApp.kt:794-820`, `ActivityTypeSelectionDialog.kt`, `UnitsSettingsDialog.kt`, `ExportSettingsDialog.kt`.
  * `TST-MAP-010/020` grounded against `MapContentScope.kt`, `PolylineHeatmapTileProvider.kt`, `ThermalColorLut.kt`, `WorkoutClusterHeatmapScreen.kt`, `InteractivePeriodMap.kt`, `PeriodMapUtils.kt`.
* **Contract Compliance (`ATT-8198`)**: All new tests under `ui/` MUST use real behavioral / `createComposeRule()` assertions — zero `Class.forName("...Kt")` stubs (`UiReflectionOnlyTestAuditContractTest`) and zero `src/main` file reads (`SourceTextReadingAuditContractTest`). No `strings*.xml` edits (`TranslationParityTest` unaffected).

---

## 2. Executable Test Specifications & Acceptance Criteria (Given-When-Then)

### `TST-NAV-001` (`REQ-SET-050`) — `NavigationDrawerAndDialogContractTest.kt` (`ui/navigation/`)
* **Given** `createDrawerGroups(R.string.tab_start)` and `AppNavigationDrawer` rendered via `createComposeRule()` (Robolectric `@Config(sdk = [34])`),
* **When** inspecting the 6 canonical sections and expanding collapsible `Synchronization` / `Settings` headers to tap `Sport Types` and `Export`,
* **Then** all 6 groups contain their expected destinations (`Sport Types` & `Training Zones` in `Settings`; `Strava`, `Dropbox`, `Export` in `Synchronization`), and `onItemSelected` dispatches the tapped item IDs.

### `TST-NAV-002` (`REQ-UI-112`) — `NavigationDrawerAndDialogContractTest.kt` (`ui/navigation/`)
* **Given** `AppNavigationDrawer` rendered under English (`en`) and German (`de`) Robolectric configurations,
* **When** inspecting group headers and expanding `Synchronization` / `Synchronisation`,
* **Then** labels display `"Equipment"` (EN) / `"Ausrüstung"` (DE), `"Synchronization"` (EN) / `"Synchronisation"` (DE), and `"Export"` in both EN and DE.

### `TST-NAV-003` (`REQ-SET-051`) — `NavigationDrawerAndDialogContractTest.kt` (`ui/navigation/`)
* **Given** `R.id.drawer_tracking_layouts` mapped via `NavRoutes.toBottomSheetType(...)` to `SettingsBottomSheetType.ACTIVITY_TYPE`, and `ActivityTypeSelectionDialog` rendered via `createComposeRule()`,
* **When** tapping an `ActivityType` row (e.g., `ActivityType.RUN_SPEED`),
* **Then** `onTypeSelected` receives that `ActivityType` and `onDismiss` fires (routing to `MainActivityWithNavigation.onActivityTypeSelected` / `ScreenMode.CONFIGURATION`).

### `TST-NAV-005` (`REQ-SET-053`) — `UnitsSettingsDialogTest.kt` (`ui/settings/units/`)
* **Given** `NavRoutes.toBottomSheetType(R.id.drawer_units) == SettingsBottomSheetType.UNITS` and `UnitsSettingsDialog` rendered via `createComposeRule()` (Robolectric) with initial `METRIC` preference,
* **When** selecting `MyUnits.IMPERIAL` and tapping Save (and separately tapping Cancel after toggling),
* **Then** Save persists `MyUnits.IMPERIAL.name` under `TrainingApplication.SP_UNITS` and invokes `onDismiss()`, while Cancel leaves preferences unchanged.

### `TST-NAV-006` (`REQ-SET-054`) — `ExportSettingsDialogTest.kt` (`ui/settings/export/`)
* **Given** `NavRoutes.toBottomSheetType(R.id.drawer_export) == SettingsBottomSheetType.EXPORT` and `ExportSettingsDialog` rendered via `createComposeRule()` (Robolectric),
* **When** toggling format switches (TCX, GPX, Golden Cheetah JSON, CSV, FIT, Raw Sensor) and tapping Save (and separately Cancel),
* **Then** Save persists updated `TrainingApplication.exportTo*()` flags and invokes `onDismiss()`, while Cancel discards changes.

### `TST-MAP-010` (`REQ-MAP-016`) — `MapHeatmapZoomAndDetailTest.kt` (`ui/map/`) & `PolylineHeatmapTileProviderTest.kt`
* **Given** `MapContentScopeImpl.collect { heatmap(...); tracks(...); markers(...) }` and `@VisibleForTesting resolveZoomAdaptiveStyling(zoom)` in `MapContentScope.kt`,
* **When** evaluating zoom levels `9f`, `11f`, `14f`, `17f` alongside `ThermalColorLut.BLUE_STOPS` and `PolylineHeatmapTileProvider.calculateEffectiveStrokeWidth(zoom)`,
* **Then** `markerAlphaMult == 0.0f` at city scale (`zoom < 13`), `trackAlpha == 1.0f` and `markerAlphaMult == 1.0f` at `zoom > 15`, gradient follows Cyan->Blue->Indigo, and stroke width scales monotonically.

### `TST-MAP-020` (`REQ-MAP-018`) — `MapHeatmapZoomAndDetailTest.kt` (`ui/map/`) + Emulator Check
* **Given** `PolylineHeatmapTileProvider` under Cluster (`CLASSIC_BLUE`, `useClassicStepIntensity = true`) and Period (`HOT`, `PeriodMapUtils.getPeriodMapVisuals(..., isInteractive = true, zoom = 16f)`),
* **When** invoking `getTile(x, y, zoom)` across detailed zooms `13..18`,
* **Then** each zoom yields non-empty decodable `256x256` PNG tiles with non-transparent pixels bounded by `CLASSIC_BLUE` max base alpha (`<= 180/255`) so terrain/roads stay legible. *Honest GL Boundary*: Native Google Maps `TileOverlay` GL compositing over live vector/terrain tiles is scoped out of JVM and checked on `emulator-5554` in Stage 5 (`Rule 8.5`).

---

## 3. Traceability Matrix
| Test ID | Target Test Class (`app/src/test/...`) | Method(s) / Checks | Req ID | Stage 2 Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-NAV-001` | `ui/navigation/NavigationDrawerAndDialogContractTest.kt` | `testDrawerStructureAndGroupPlacement`, `testDrawerExpansionAndSelectionDispatch` | `REQ-SET-050` | Specified (`[Implemented]`) |
| `TST-NAV-002` | `ui/navigation/NavigationDrawerAndDialogContractTest.kt` | `testDrawerTerminologyLocalization_enAndDe` | `REQ-UI-112` | Specified (`[Implemented]`) |
| `TST-NAV-003` | `ui/navigation/NavigationDrawerAndDialogContractTest.kt` | `testTrackingTabsRouteAndActivityTypeSelectionDialog` | `REQ-SET-051` | Specified (`[Implemented]`) |
| `TST-NAV-005` | `ui/settings/units/UnitsSettingsDialogTest.kt` | `testUnitsDialog_savePersistsAndCancelDiscards` | `REQ-SET-053` | Specified (`[Implemented]`) |
| `TST-NAV-006` | `ui/settings/export/ExportSettingsDialogTest.kt` | `testExportDialog_savePersistsAndCancelDiscards` | `REQ-SET-054` | Specified (`[Implemented]`) |
| `TST-MAP-010` | `ui/map/MapHeatmapZoomAndDetailTest.kt` + `PolylineHeatmapTileProviderTest.kt` | `testMapContentScopeHeatmapDslAndZoomAdaptiveStyling`, LUT/stroke tests | `REQ-MAP-016` | Specified (`[Implemented]`) |
| `TST-MAP-020` | `ui/map/MapHeatmapZoomAndDetailTest.kt` (+ Stage 5 `emulator-5554`) | `testDetailedZoom13To18_clusterAndPeriodHeatmapTilesVisibleAndAlphaBounded` | `REQ-MAP-018` | Specified (`[Implemented]`) |
