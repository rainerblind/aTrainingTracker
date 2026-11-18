# Stage 1 Analysis: ATT-8326 - Executable/Attested Verification for Maps & Navigation (7 TSTs from ATT-8145)

**Ticket**: `ATT-8326` (`1 SP`, `Priority: Low`) | **Parent Epic**: `ATT-8138` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-18_02_Spät` | **Branch**: `feature/ATT-8326` (base `15de06fa`) | **Date**: 2026-11-18

## 1. Problem Statement & Motivation
* During ASPICE 4.0 Finding M1 triage (`ATT-8145`, commit `d5a3d1bb`), 7 legacy tests in `docs/tests/tst_maps_navigation.md` (`TST-NAV-001`, `TST-NAV-002`, `TST-NAV-003`, `TST-NAV-005`, `TST-NAV-006`, `TST-MAP-010`, `TST-MAP-020`) lacked linked executable test code or attested manual execution records (`docs/engineering/test_evidence/ATT-8145_unbacked_triage.csv`) and were downgraded from `[Verified]` to `[Implemented]`.
* This ticket replaces unbacked `[Verified]` claims with deterministic JVM/Robolectric/`createComposeRule()` behavioral tests (strictly obeying `ATT-8198` `UiReflectionOnlyTestAuditContractTest` and `SourceTextReadingAuditContractTest`, zero `Class.forName("...Kt")` or `src/main` reads) and honest scoping of any native Google Maps GL tile compositing.

## 2. Forensic Mapping & Independent Peer Review (`ATT-3534`)
| TST ID | Req ID | Live Codebase Target & Line Range | Deterministic Testability & Findings |
| :--- | :--- | :--- | :--- |
| `TST-NAV-001` | `REQ-SET-050` | `AppNavigationDrawer.kt:187-259, 405-523` | **Testable (Compose UI + Unit)**: Drawer evolved (`REQ-UI-397/422/463`) into 6 groups (`drawer__training`, `drawer__maps`, `drawer__my_stuff`, `prefsOnlineCommunities`, `drawer__settings`, `drawer__info_support`); `Sport Types` & `Training Zones` sit in `Settings`, `Strava`/`Dropbox`/`Export` in `Synchronization`. Verify group structure, accordion expansion, and item click dispatch via `createComposeRule()`. |
| `TST-NAV-002` | `REQ-UI-112` | `AppNavigationDrawer.kt:213-232`; `values*/strings.xml` | **Testable (Compose UI + Localization)**: `drawer__my_stuff` = `Equipment`/`Ausrüstung`, `prefsOnlineCommunities` = `Synchronization`/`Synchronisation`, `prefsExportTitle` = `Export`/`Export`. Peer check: legacy `TST-NAV-002` prose says `"Dateiexport" (DE)`, whereas `REQ-UI-112` (`req_ui_design_system_part1.md:585`) specifies `"Export"` (`values-de/strings.xml:308`). |
| `TST-NAV-003` | `REQ-SET-051` | `NavRoutes.kt:218`; `ATrainingTrackerApp.kt:814-820`; `ActivityTypeSelectionDialog.kt:42-80`; `MainActivityWithNavigation.kt:879-887` | **Testable (Compose UI + Unit)**: `drawer_tracking_layouts` -> `SettingsBottomSheetType.ACTIVITY_TYPE`; `ActivityTypeSelectionDialog` renders all `ActivityType.values()` and clicking a row invokes `onTypeSelected(type)` + `onDismiss()`. |
| `TST-NAV-005` | `REQ-SET-053` | `NavRoutes.kt:216`; `ATrainingTrackerApp.kt:797-799`; `UnitsSettingsDialog.kt:33-85` | **Testable (Compose UI + Robolectric Prefs)**: `drawer_units` -> `SettingsBottomSheetType.UNITS`; selecting `MyUnits.IMPERIAL` and tapping Save persists `TrainingApplication.SP_UNITS` to `SharedPreferences` and dismisses; Cancel discards changes. |
| `TST-NAV-006` | `REQ-SET-054` | `NavRoutes.kt:215`; `ATrainingTrackerApp.kt:794-796`; `ExportSettingsDialog.kt:30-116` | **Testable (Compose UI + Robolectric Prefs)**: `drawer_export` -> `SettingsBottomSheetType.EXPORT`; toggling TCX/GPX/GCJson/CSV/FIT/RawSensor and tapping Save persists `TrainingApplication.exportTo*()` flags; Cancel discards changes. |
| `TST-MAP-010` | `REQ-MAP-016` | `MapContentScope.kt:132-143, 233-261, 356-399`; `PolylineHeatmapTileProvider.kt:79-89`; `ThermalColorLut.kt:90-94` | **Testable (Unit)**: `ThermalColorLut.BLUE_STOPS` and `calculateEffectiveStrokeWidth` are covered in `PolylineHeatmapTileProviderTest.kt`, but `MapContentScopeImpl` (`heatmap(...)`, `trackAlpha` `0.4f..1.0f`, marker culling `markerAlphaMult == 0.0f` at `zoom < 13` -> `1.0f` at `zoom > 15`) has no test. Extracting pure `@VisibleForTesting resolveZoomAdaptiveStyling(zoom)` enables direct unit verification. |
| `TST-MAP-020` | `REQ-MAP-018` | `WorkoutClusterHeatmapScreen.kt:598-604`; `MapScreenWithTrack.kt:844-850`; `InteractivePeriodMap.kt:182-184`; `PeriodMapUtils.kt:176-233`; `PolylineHeatmapTileProvider.kt` | **Testable (Robolectric Tile + Config) + Scoped GL**: Verify `PolylineHeatmapTileProvider` produces non-empty 256×256 PNG tiles across zooms `13..18` under both Cluster (`CLASSIC_BLUE`, `useClassicStepIntensity = true`, base alpha ≤ `180/255`) and Period (`HOT`, `normalizeCorridors = true`, `getPeriodMapVisuals(..., isInteractive = true)`) configs so terrain/roads stay legible. Live Google Maps GL `TileOverlay` compositing is scoped out of JVM as Play Services native rendering. |

## 3. User Scope Grounding (`ATT-1250`)
* **In-Scope**: Add behavioral `createComposeRule()` / Robolectric / JVM tests for all 7 TSTs; extract a minimal `@VisibleForTesting` pure zoom-styling helper in `MapContentScope.kt` if needed; update `docs/tests/tst_maps_navigation.md` (`[Verified]` only where backed by passing executable tests, reconciling legacy wording drift in `TST-NAV-001`/`002` without altering production behavior).
* **Out-of-Scope**: Refactoring drawer hierarchy, changing `UnitsSettingsDialog`/`ExportSettingsDialog` UI, modifying `PolylineHeatmapTileProvider` rasterizer math, or touching dirty `tools/` files.

## 4. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
* **Original Target & Commit Trace**: `TST-NAV-001/002/003/005/006`, `TST-MAP-010/020` in `docs/tests/tst_maps_navigation.md` (`06990c88`, `ATT-4594`); downgraded from `[Verified]` to `[Implemented]` in `ATT-8145` (`d5a3d1bb`).
* **Root Reason & Invariants**: `ATT-8145` rightly prevented unbacked `[Verified]` statuses per ASPICE Finding M1. Promoting back to `[Verified]` requires real executable assertions (and `ATT-8198` bans reflection-only `ui/` tests and `src/main` file reading) while preserving existing drawer routing (`REQ-UI-397/422/463`), `Export`/`Units` preference keys, and `MapContentScope` zoom thresholds.

## 5. Change-Request Impact Analysis (`SUP.10`)
* **Affected Requirements**: `REQ-SET-050`, `REQ-SET-051`, `REQ-SET-053`, `REQ-SET-054`, `REQ-UI-112`, `REQ-MAP-016`, `REQ-MAP-018` (`SYS.2`/`SYS.3` impact: None — pure UI/navigation/heatmap verification, no sensor/HAL/external API change).
* **Affected Architecture Elements**: `MapContentScope.kt` (optional `@VisibleForTesting` pure helper extraction), new tests under `ui/navigation/`, `ui/settings/`, `ui/map/`.
* **Verification Impact & Test Surface**: `tst_maps_navigation.md` (7 TSTs), targeted Robolectric/`createComposeRule()`/`UiReflectionOnlyTestAuditContractTest`/`SourceTextReadingAuditContractTest`/`verify_requirement_governance.py`.
* **Risk Assessment & Invariants**: **LOW** risk — production behavior unchanged; `TranslationParityTest` untouched (no `strings.xml` edits); `ATT-8198` baseline counts (`189` / `105`) strictly preserved.

## 6. Architectural Strategy & High-Level Solution
1. Add behavioral Compose UI tests (`createComposeRule()` + Robolectric `@Config(sdk = [34])`) in `ui/navigation/` (`NavigationDrawerAndDialogContractTest.kt` or focused per-area tests) covering `AppNavigationDrawer` structure/localization/clicks (`TST-NAV-001`, `TST-NAV-002`) and `ActivityTypeSelectionDialog` (`TST-NAV-003`), plus `ui/settings/` tests for `UnitsSettingsDialog` (`TST-NAV-005`) and `ExportSettingsDialog` (`TST-NAV-006`).
2. Extract `@VisibleForTesting internal fun resolveZoomAdaptiveStyling(currentZoom: Float)` in `MapContentScope.kt` and add `MapHeatmapZoomAndDetailTest.kt` in `ui/map/` verifying `MapContentScopeImpl.heatmap(...)` + zoom-adaptive alpha/marker culling (`TST-MAP-010`) and detailed-zoom (`13..18`) tile generation + `getPeriodMapVisuals` (`TST-MAP-020`).
3. Update `docs/tests/tst_maps_navigation.md` (and linked requirements' `[Verified]` status per `verify_requirement_governance.py`) only for tests backed by passing suites.

## 7. System Invariants & Risk Assessment
* **Core Invariants**: Zero regressions in existing navigation/settings/heatmap suites; zero `Class.forName("...Kt")` or `src/main` reads in new tests (`ATT-8198`); parent ticket Human Decision Gate (`Final Review (Human)`) strictly preserved.
* **Risk Rating**: **LOW** — Additive test coverage and pure helper extraction with zero production behavior mutation.
