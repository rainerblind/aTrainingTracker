# Stage 3: Implementation Plan - ATT-2766: Populate Climbs.db from Workouts & Toggleable Map Climbs Layer

**Ticket**: [ATT-2766](https://atrainingtracker.atlassian.net/browse/ATT-2766)  
**Parent Epic**: [ATT-2565](https://atrainingtracker.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-2766`  
**Requirement Mapping**: `REQ-MAP-064` (`REQ-MAP-064.1`..`REQ-MAP-064.4`) | **Test Mapping**: `TST-MAP-066` (`TST-MAP-066.1`..`TST-MAP-066.6`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Background
Currently, `ClimbsDatabaseManager` (`Climbs.db`) is only populated when importing GPX routes in `RoutesRepository`. Workouts recorded or imported by athletes do not feed `Climbs.db`, preventing Free-Riding Live ClimbPro (`LiveClimbsRepository`) from operating during free rides. In addition, `MapScreenWithTrack` lacks a toggleable `CLIMBS` layer to inspect stored climbs and view their elevation profiles on tap.

---

## 2. Traceability & Requirements Mapping
* **REQ-MAP-064.1**: Post-workout & import climb extraction and 50m spatial deduplication (`WorkoutRepository.kt`) -> `TST-MAP-066.1`.
* **REQ-MAP-064.2**: `GeneralMapLayer.CLIMBS` enum entry, default-off state, and 9-language localization parity -> `TST-MAP-066.2`, `TST-MAP-066.5`.
* **REQ-MAP-064.3**: `MapFragmentWithTrackViewModel` `uiState.climbs` flow and dynamic layer gating -> `TST-MAP-066.3`.
* **REQ-MAP-064.4**: `MapScreenWithTrack` layer menu, climb polyline rendering, and `ClimbDetailSheet` tap presentation -> `TST-MAP-066.4`.

---

## 3. System Invariants & Preserved Behavior
1. **Deduplication Threshold**: 50m start/summit spatial deduplication in `ClimbsDatabaseManager` strictly preserved (`Location.distanceBetween <= 50f`).
2. **Database Schema & Stability**: `ClimbsDbHelper` remains version 1; table `climbs` schema unchanged.
3. **Map Layer Non-Cluttering Default**: `GeneralMapLayer.DEFAULT_ENABLED` strictly preserves 4 entries (excluding `CLIMBS` and `CLUSTERS`).
4. **Lane 3 Worktree Isolation**: Zero edits to `PathPreviewMap.kt` or `ui/clusters/*` (strictly isolated for concurrent `ATT-6318`).
5. **Thread Safety & Async Execution**: Post-workout climb detection and SQLite queries run strictly on `Dispatchers.IO`.

---

## 4. Proposed Architectural Changes
* **Strings Localization (`values*/strings.xml`)**: Add `map_layer_climbs` with 100% parity across all 9 supported locales.
* **General Map Layer (`GeneralMapLayer.kt`)**: Add `CLIMBS` to `GeneralMapLayer`. `DEFAULT_ENABLED` remains unchanged (4 entries).
* **Workout Ingestion (`WorkoutRepository.kt`)**: In `reloadWorkoutData`, when `isNewImportOrFinish == true`, call `extractAndPersistClimbsFromWorkout(workoutId)`. Loads `getWorkoutTrackPoints(workoutId, TrackType.BEST)`, runs `ClimbDetector.detectClimbs`, persists via `insertClimbsWithDeduplicationBatch`, and calls `LiveClimbsRepository.refreshClimbs()`.
* **ViewModel Layer (`MapFragmentWithTrackViewModel.kt`)**: Inject `ClimbsDatabaseManager`. Expose `climbs: List<Climb>` in `MapFragmentUIState`. When `CLIMBS in enabledLayers`, load stored climbs; emit `emptyList()` when disabled.
* **Map Screen & Content Scope (`MapContentScope.kt`, `MapScreenWithTrack.kt`)**: Update `MapContentScope.climbs` to accept `onClimbClick: (Long) -> Unit`. Wire `CLIMBS` toggle in layer menu with `getClimbCategoryColors(ClimbCategory.CAT_1).first`. Tapping a climb opens `ClimbDetailSheet(climb = selectedClimb, onDismiss = { selectedClimb = null })`.
* **UI Consistency**: Reuses `ClimbDetailSheet`, `getClimbCategoryColors`, Material 3 `DropdownMenuItem`, `Checkbox`, and standard sheet scaffolds. Zero new one-off design tokens.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Step 1: Localization & Enum Definition**: Add `map_layer_climbs` to all 9 `strings.xml` files. Add `CLIMBS` to `GeneralMapLayer.kt`.
* **Step 2: Post-Workout Ingestion**: Implement `extractAndPersistClimbsFromWorkout` in `WorkoutRepository.kt` on `Dispatchers.IO`.
* **Step 3: ViewModel Integration**: Add `climbs` to `MapFragmentUIState` and wire `mapClimbsFlow` in `MapFragmentWithTrackViewModel.kt`.
* **Step 4: Map Screen & Click Handling**: Add `onClimbClick` to `MapContentScope.kt`, wire `CLIMBS` layer menu item and `ClimbDetailSheet` state in `MapScreenWithTrack.kt`.
* **Step 5: Automated Test Suite**: Update `GeneralMapLayersContractTest.kt` (`TST-MAP-066.2`), `GeneralMapLayersLocalizationTest.kt` (`TST-MAP-066.5`), `MapFragmentWithTrackViewModelTest.kt` (`TST-MAP-066.3`), `MapScreenWithTrackTest.kt` (`TST-MAP-066.4`), and add `WorkoutClimbIngestionTest.kt` (`TST-MAP-066.1`).
* **Step 6: Targeted Verification**: Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.database.ClimbsDatabaseManagerTest" --tests "com.atrainingtracker.trainingtracker.climbs.*"` asserting 100% PASS.

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted unit tests during Stage 4 construction and Tier 1 regression suite in Stage 5.
* **Rollback**: Branch isolation on `feature/ATT-2766` allows instant rollback (`git checkout sprint/2026-46.1 && git branch -D feature/ATT-2766`).
