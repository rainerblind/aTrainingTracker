# Stage 3: Implementation Plan - ATT-8326: Executable/Attested Verification for Maps & Navigation (7 TSTs)

**Ticket**: `ATT-8326` (`1 SP`) | **Parent Epic**: `ATT-8138` | **Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-18_02_Spät`  
**Requirement Mapping**: `REQ-SET-050`, `REQ-UI-112`, `REQ-SET-051`, `REQ-SET-053`, `REQ-SET-054`, `REQ-MAP-016`, `REQ-MAP-018`  
**Test Mapping**: `TST-NAV-001`, `TST-NAV-002`, `TST-NAV-003`, `TST-NAV-005`, `TST-NAV-006`, `TST-MAP-010`, `TST-MAP-020`  
**Branch**: `feature/ATT-8326` | **Date**: 2026-11-18

---

## 1. Problem Description & Background
* Implement deterministic JVM/Robolectric/`createComposeRule()` behavioral verification for the 7 Maps & Navigation TSTs downgraded to `[Implemented]` in `ATT-8145`, strictly obeying `ATT-8198` (`UiReflectionOnlyTestAuditContractTest` & `SourceTextReadingAuditContractTest`: zero `Class.forName("...Kt")` stubs, zero `src/main` file reads) and honestly scoping native Google Maps GL `TileOverlay` compositing to Stage 5 `emulator-5554` visual verification.

## 2. Traceability & Requirements Mapping
* `TST-NAV-001` -> `REQ-SET-050` | `TST-NAV-002` -> `REQ-UI-112` | `TST-NAV-003` -> `REQ-SET-051` | `TST-NAV-005` -> `REQ-SET-053` | `TST-NAV-006` -> `REQ-SET-054`
* `TST-MAP-010` -> `REQ-MAP-016` | `TST-MAP-020` -> `REQ-MAP-018` (`docs/tests/tst_maps_navigation.md` v1.0.4; no `docs/architecture.md` or `docs/architecture/detailed_design/` changes needed).

## 3. System Invariants & Preserved Behavior
1. **Zero Functional Behavior Mutation**: Pure `@VisibleForTesting` zoom-styling extraction in `MapContentScope.kt` (`585` -> ~`600` LoC) preserves exact zoom thresholds (`<10`, `<13`, `<=15`, `>15`).
2. **Contract Audit Compliance (`ATT-8198`)**: Zero new `Class.forName("...Kt")` reflection tests under `ui/` and zero `src/main` file reads; baselines (`189` / `105`) untouched.
3. **Localization & Tooling Hygiene**: Zero `strings*.xml` edits (`TranslationParityTest` unaffected); 4 pre-existing dirty `tools/` files untouched.
4. **Parent Human Gate Invariance**: Parent ticket terminates at `Final Review (Human)`.

## 4. Proposed Architectural Changes
* **Component 1 — `MapContentScope.kt` (`ui/map/`)**: Extract internal data class `ZoomAdaptiveStyling(val trackAlpha: Float, val markerAlphaMult: Float, val heatmapWeight: Double, val heatmapStartIntensity: Float)` and `@VisibleForTesting internal fun resolveZoomAdaptiveStyling(currentZoom: Float)` from `MapContentScopeImpl.Render(currentZoom)` (`:234-261`).
* **Component 2 — Navigation & Dialog Behavioral Compose UI Tests (`ui/navigation/`, `ui/settings/`)**:
  * `NavigationDrawerAndDialogContractTest.kt` (`TST-NAV-001`, `TST-NAV-002`, `TST-NAV-003`): `createComposeRule()` + Robolectric `@Config(sdk = [34])`: `testDrawerStructureAndGroupPlacement()`, `testDrawerExpansionAndSelectionDispatch()`, `testDrawerTerminologyLocalization_enAndDe()` (`Equipment`/`Ausrüstung`, `Synchronization`/`Synchronisation`, `Export`/`Export`), and `testTrackingTabsRouteAndActivityTypeSelectionDialog()` (`NavRoutes.toBottomSheetType(R.id.drawer_tracking_layouts)` + `ActivityTypeSelectionDialog` selection).
  * `UnitsSettingsDialogTest.kt` (`TST-NAV-005`): `testUnitsDialog_savePersistsAndCancelDiscards()` (`createComposeRule()` + Robolectric `SP_UNITS` Save/Cancel persistence).
  * `ExportSettingsDialogTest.kt` (`TST-NAV-006`): `testExportDialog_savePersistsAndCancelDiscards()` (`createComposeRule()` + Robolectric `TrainingApplication.exportTo*()` Save/Cancel persistence).
* **Component 3 — Heatmap Zoom & Detail Visibility Tests (`ui/map/`)**:
  * `MapHeatmapZoomAndDetailTest.kt` (`TST-MAP-010`, `TST-MAP-020`): `testMapContentScopeHeatmapDslAndZoomAdaptiveStyling()` (`MapContentScopeImpl.collect` + `resolveZoomAdaptiveStyling` at zooms `9f`/`11f`/`14f`/`17f`) and `testDetailedZoom13To18_clusterAndPeriodHeatmapTilesVisibleAndAlphaBounded()` (`PolylineHeatmapTileProvider.getTile` across zooms `13..18`, `CLASSIC_BLUE_STOPS` base alpha `<= 180/255`, `HOT`, `PeriodMapUtils.getPeriodMapVisuals(..., isInteractive = true, zoom = 16f)`).
* **UI Consistency**: No UI changes.

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Extract Zoom-Adaptive Helper & Implement All 4 Test Suites (After `check-gate` PASS)
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapContentScope.kt`; `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/NavigationDrawerAndDialogContractTest.kt`; `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/units/UnitsSettingsDialogTest.kt`; `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/export/ExportSettingsDialogTest.kt`; `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapHeatmapZoomAndDetailTest.kt`.
* **Changes**: Extract `resolveZoomAdaptiveStyling(currentZoom: Float)` in `MapContentScope.kt` and implement the 4 behavioral test suites with explicit `// Linked test specs:` tags covering all 7 TSTs.

### Step 2: Execute Targeted Tests & Contract Audits Under Sprint Lock, Then Stage 5 Emulator & `[Verified]` Sync
* **Command (Stage 4 & Stage 5)**:
  `python3 tools/parallel_sprint_lock.py ./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.NavigationDrawerAndDialogContractTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.units.UnitsSettingsDialogTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.export.ExportSettingsDialogTest" --tests "com.atrainingtracker.trainingtracker.ui.map.MapHeatmapZoomAndDetailTest" --tests "com.atrainingtracker.trainingtracker.ui.map.PolylineHeatmapTileProviderTest" --tests "com.atrainingtracker.trainingtracker.build.UiReflectionOnlyTestAuditContractTest" --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest"`
* **Stage 5 Emulator & Promotion**: Deploy debug APK onto `emulator-5554` (`Rule 8.5` screenshot `read_file` check for drawer/dialogs & heatmap GL overlay), promote the 7 TSTs in `docs/tests/tst_maps_navigation.md` to `[Verified]`, and run `python3 tools/verify_requirement_governance.py --sync-indexes --check-verified-evidence`.

## 6. Verification & Rollback Plan
* **Verification**: Targeted Tier-1 Gradle unit/Compose/contract suite (Stage 4/5) + `emulator-5554` visual check + governance verification.
* **Rollback**: Branch isolation on `feature/ATT-8326` allows full revert without impacting `sprint/2026-11-18_02_Spät` or `develop`.
