# ATT-2766 Walkthrough: Populate Climbs.db from Workouts & Toggleable Map Climbs Layer

**Ticket**: [ATT-2766](https://atrainingtracker.atlassian.net/browse/ATT-2766)  
**Parent Epic**: [ATT-2565](https://atrainingtracker.atlassian.net/browse/ATT-2565) (*ClimbPro / Live Climbs Architecture*)  
**Sprint**: `2026-46.1` | **Target Release**: `V5.0.0` | **Status**: Verified  
**Requirement Mapping**: `REQ-MAP-064` | **Test Mapping**: `TST-MAP-066`  

---

## 1. Executive Summary

Historically, `ClimbsDatabaseManager` (`Climbs.db`) was only populated when importing GPX routes via `RoutesRepository`. Workouts recorded directly or imported historically had no representation in `Climbs.db`, preventing Free-Riding Live ClimbPro (`LiveClimbsRepository`) from operating. Additionally, the global map screen (`MapScreenWithTrack`) lacked a toggleable layer to visualize stored climbs and inspect elevation profiles.

Under `ATT-2766` (governed by `REQ-MAP-064` / `TST-MAP-066`):
- **Workout Climb Ingestion & Spatial Deduplication (`REQ-MAP-064.1`)**: In `WorkoutRepository.kt`, `extractAndPersistClimbsFromWorkout` extracts qualifying climbs via `ClimbDetector.detectClimbs` on workout finish or import (`isNewImportOrFinish`), persists them with 50m spatial deduplication via `insertClimbsWithDeduplicationBatch`, and refreshes `LiveClimbsRepository`.
- **GeneralMapLayer Enum & Localization Parity (`REQ-MAP-064.2`)**: Added `GeneralMapLayer.CLIMBS` to `GeneralMapLayer.kt`. Excluded from `DEFAULT_ENABLED` to prevent visual clutter. Localized `map_layer_climbs` across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
- **ViewModel Reactive Climbs Flow (`REQ-MAP-064.3`)**: In `MapFragmentWithTrackViewModel.kt`, added `climbs: List<Climb>` to `MapFragmentUIState`, injected `ClimbsDatabaseManager`, and combined `enabledLayers` with stored climbs so `uiState.climbs` emits stored climbs when enabled, and empty list when disabled.
- **MapScreenWithTrack & MapContentScope Integration (`REQ-MAP-064.4`)**: In `MapScreenWithTrack.kt`, wired `GeneralMapLayer.CLIMBS` in layer selection menu, passed `onClimbClick` callback to `climbs(...)` in `MapContentScope.kt`, and presented `ClimbDetailSheet` on climb tap with back navigation and dismissal.

---

## 2. Traceability Matrix

| Requirement | Test Specification | Production Code | Verification Tests | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-MAP-064.1` | `TST-MAP-066.1` | `WorkoutRepository.kt`, `ClimbsDatabaseManager.kt` | `WorkoutClimbIngestionTest.kt` | **Verified** |
| `REQ-MAP-064.2` | `TST-MAP-066.2`, `TST-MAP-066.5` | `GeneralMapLayer.kt`, `strings.xml` (9 locales) | `GeneralMapLayersContractTest.kt`, `GeneralMapLayersLocalizationTest.kt` | **Verified** |
| `REQ-MAP-064.3` | `TST-MAP-066.3` | `MapFragmentWithTrackViewModel.kt` | `MapFragmentWithTrackViewModelTest.kt`, `GeneralMapLayersContractTest.kt` | **Verified** |
| `REQ-MAP-064.4` | `TST-MAP-066.4` | `MapScreenWithTrack.kt`, `MapContentScope.kt` | `MapScreenWithTrackTest.kt` | **Verified** |
| `REQ-MAP-064` | `TST-MAP-066.6` | Targeted Test Suite | Targeted Test Suite | **Verified** |

---

## 3. Verification & Test Execution

- **Tier 1 Targeted Test Scope (Rule 60)**:
  - Command: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --max-workers=2 --tests "com.atrainingtracker.banalservice.database.*" --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutClimbIngestionTest" --tests "com.atrainingtracker.trainingtracker.database.ClimbsDatabaseManagerTest" --tests "com.atrainingtracker.trainingtracker.climbs.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.ui.components.core.*" --tests "com.atrainingtracker.trainingtracker.build.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`
  - Scope: Climb ingestion and deduplication, map UI and layer contract tests, ClimbPro live repository, climbs database manager, 9-language translation parity, theme centralization audit, core UI components, and build hygiene.
  - Result: **100% PASS** (606 tests, 0 failures, 0 errors, 0 regressions).
- **Physical Device Execution**:
  - `adb devices` checked: no physical devices connected. Per Rule 79, emulator launch was omitted.
