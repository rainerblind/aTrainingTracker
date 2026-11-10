# Stage 2: Requirement & Test Specification - ATT-2766: Populate Climbs.db from Workouts & Toggleable Map Climbs Layer

**Ticket**: [ATT-2766](https://atrainingtracker.atlassian.net/browse/ATT-2766) | **Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-2565](https://atrainingtracker.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-2766`  
**Requirement Mapping**: `REQ-MAP-064` (`REQ-MAP-064.1`..`REQ-MAP-064.4`) | **Test Spec ID**: `TST-MAP-066` (`TST-MAP-066.1`..`TST-MAP-066.6`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (`REQ-MAP-064`)

### 1.1 Problem Statement & Rationale
Currently, `ClimbsDatabaseManager` (`Climbs.db`) is only populated when importing GPX routes in `RoutesRepository`. Workouts recorded or imported by athletes do not feed `Climbs.db`, leaving Free-Riding Live ClimbPro (`LiveClimbsRepository`) inactive for conquered hills. Furthermore, `MapScreenWithTrack` lacks a toggleable `CLIMBS` layer to inspect stored climbs and their elevation profiles across the general map.

### 1.2 Functional & Architectural Requirements
* **REQ-MAP-064.1** (*Post-Workout & Import Climb Extraction*): Upon workout completion or import (`isNewImportOrFinish` in `WorkoutRepository.reloadWorkoutData`), the system SHALL retrieve track points via `getWorkoutTrackPoints(workoutId, TrackType.BEST)` on `Dispatchers.IO`, detect qualifying ascents via `ClimbDetector.detectClimbs(points, routeId = null)`, persist them to `Climbs.db` with 50m spatial deduplication via `ClimbsDatabaseManager.insertClimbsWithDeduplicationBatch`, and trigger `LiveClimbsRepository.refreshClimbs()`.
* **REQ-MAP-064.2** (*General Map Layer Model & Default Preferences*): `GeneralMapLayer` SHALL declare `CLIMBS` (total 6 entries). `GeneralMapLayer.DEFAULT_ENABLED` SHALL remain 4 entries, strictly excluding `CLIMBS` and `CLUSTERS` to prevent initial map clutter. `map_layer_climbs` SHALL maintain 100% parity across all 9 supported locales.
* **REQ-MAP-064.3** (*ViewModel State & Dynamic Layer Gating*): `MapFragmentUIState` SHALL expose `val climbs: List<Climb> = emptyList()`. `MapFragmentWithTrackViewModel` SHALL observe `enabledLayers` and query `ClimbsDatabaseManager.getAllClimbs()` when `CLIMBS` is enabled; when disabled, `uiState.climbs` SHALL emit `emptyList()`.
* **REQ-MAP-064.4** (*Map Screen Styling & Tap-to-Inspect Interaction*): `MapScreenWithTrack` layer menu SHALL render the `CLIMBS` toggle item. In `ATrainingTrackerMap`, climb polylines SHALL only render when `CLIMBS in enabledLayers`. Tapping a climb SHALL open `ClimbDetailSheet(climb = selectedClimb, onDismiss = { selectedClimb = null })`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Workout Ingestion & Deduplication)**: *Given* an athlete finishes recording or imports a workout containing climbs, *When* post-workout processing runs in `reloadWorkoutData`, *Then* detected climbs are persisted to `Climbs.db` with 50m spatial deduplication (`routeId = null`) and `LiveClimbsRepository` refreshes.
* **AC-2 (Map Layer Toggle & ClimbDetailSheet)**: *Given* `MapScreenWithTrack`, *When* `GeneralMapLayer.CLIMBS` is enabled, *Then* stored climbs are rendered and tapping a climb opens `ClimbDetailSheet`.
* **AC-3 (Non-Cluttering Default State)**: *Given* a fresh install or unconfigured map layer preferences, *When* opening `MapScreenWithTrack`, *Then* `GeneralMapLayer.CLIMBS` is disabled by default.

---

## 2. Test Specification & Traceability Matrix (`TST-MAP-066`)

| Req ID | Test Case | Target Component | Verification Scope |
| :--- | :--- | :--- | :--- |
| `REQ-MAP-064.1` | `TST-MAP-066.1` | `WorkoutClimbIngestionTest.kt` | Verify climb extraction on finish/import, 50m deduplication, and `LiveClimbsRepository.refreshClimbs()` call |
| `REQ-MAP-064.2` | `TST-MAP-066.2` | `GeneralMapLayersContractTest.kt` | Verify `GeneralMapLayer` enum (6 entries) and `DEFAULT_ENABLED` (4 entries, excluding `CLIMBS`/`CLUSTERS`) |
| `REQ-MAP-064.3` | `TST-MAP-066.3` | `MapFragmentWithTrackViewModelTest.kt` | Verify `uiState.climbs` emits stored climbs when enabled, and `emptyList()` when disabled |
| `REQ-MAP-064.4` | `TST-MAP-066.4` | `MapScreenWithTrackTest.kt` | Verify Climbs toggle in layer menu, conditional polyline rendering, and `ClimbDetailSheet` presentation |
| `REQ-MAP-064.2` | `TST-MAP-066.5` | `GeneralMapLayersLocalizationTest.kt` | Verify 9-language localization parity for `map_layer_climbs` |
| `REQ-MAP-064` | `TST-MAP-066.6` | Targeted Test Suite | Targeted tests across `ui.map.*`, `database.ClimbsDatabaseManagerTest`, `climbs.*` |
