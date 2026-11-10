# Stage 1 Analysis: ATT-2766 - Populate Climbs.db from Workouts & Toggleable Map Climbs Layer

**Ticket**: [ATT-2766](https://atrainingtracker.atlassian.net/browse/ATT-2766)  
**Parent Epic**: [ATT-2565](https://atrainingtracker.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-2766`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation
* **Empty `Climbs.db` for Non-Route Athletes**: `ClimbsDatabaseManager` is currently only populated when importing GPX routes in `RoutesRepository`. Free-riding workouts and TCX/workout imports never feed `Climbs.db`, leaving Free-Riding Live ClimbPro (`LiveClimbsRepository`) inactive for conquered hills.
* **Missing Climbs Layer on Central Map**: `GeneralMapLayer` supports `ROUTES`, `SEGMENTS`, `KNOWN_LOCATIONS`, `TRACK`, and `CLUSTERS`, but lacks `CLIMBS`. Athletes cannot inspect their stored climbs and interactive elevation profiles on `MapScreenWithTrack`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**:
  * `RoutesRepository.kt:141, 228, 255, 444`: Sole existing call sites for `ClimbsDatabaseManager.insertClimbsWithDeduplicationBatch`. Post-workout finalization (`TrackerService.finalizeLiveSession`, `WorkoutRepository.reloadWorkoutData`) and imports (`LegacyImportEngine.persistParsedWorkout`) lack climb detection hooks.
  * `GeneralMapLayer.kt:25-30`: Defines only 5 entries. `MapFragmentWithTrackViewModel.kt` has no subscription to `ClimbsDatabaseManager`.
  * `MapContentScope.kt:116, 555`: `climbs(climbs: List<Climb>)` draws non-clickable category-colored polylines without tap callbacks or summit markers.
* **Independent Peer Review & Verification (ATT-3534, Rules 68 & 70)**:
  * Inspected `WorkoutRepository.kt:2180-2200`: `reloadWorkoutData` contains `isNewImportOrFinish`, where `freshWorkoutData.finished` runs `PeriodsRepository.onWorkoutFinished` on `Dispatchers.IO`. This is the canonical hook for async climb detection via `getWorkoutTrackPoints(workoutId, TrackType.BEST)`.
  * Inspected `ClimbDetector.detectClimbs`: Accepts `List<PathPoint>`, correctly computes ascent, grade, score, and category without modifying inputs.
  * Inspected `ClimbDetailSheet.kt` & `ClimbOnMapScreen.kt`: Ready-to-use Compose bottom sheets with elevation profile scrubbing and category badges.
  * Verified Lane 3 isolation: `PathPreviewMap.kt` and `ui/clusters/*` will not be touched (avoiding conflict with concurrent ATT-6318).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Extract climbs from finished/imported workouts via `ClimbDetector.detectClimbs` and persist with 50m spatial deduplication via `ClimbsDatabaseManager.insertClimbsWithDeduplicationBatch(detected, 50f)`.
  * Add `GeneralMapLayer.CLIMBS` to `GeneralMapLayer.kt` (with 9-language `map_layer_climbs` localization parity).
  * Expose stored climbs in `MapFragmentWithTrackViewModel` (`MapFragmentUIState.climbs`) and wire toggle in `MapScreenWithTrack`.
  * Enable click-to-open `ClimbDetailSheet` on map climb polylines/markers via `MapContentScope.climbs`.
  * Trigger `LiveClimbsRepository.refreshClimbs()` post-insertion for immediate free-riding live awareness.
* **Out-of-Scope Non-Goals**: No standalone "Climb Trophy Case" navigation drawer screen, no VAM leaderboard tables, zero edits to `PathPreviewMap.kt` / `ui/clusters/*`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022, Rule 68)
* **Original Requirement ID & Target**: `REQ-MAP-064` (*Workout Climb Ingestion & Toggleable Climbs Map Layer*), refining `REQ-MAP-027` (*ClimbPro*), `REQ-MAP-055` (*Climb Engine Optimization*), and `REQ-UI-322` (*General Map Layers*).
* **Historical Origin & Commit Trace**: `c7438e78` (ATT-2565 Initial ClimbPro), `cf4ba98b` (ATT-3053 ClimbDetailSheet), `3c261ef4` (ATT-5074 GeneralMapLayer.CLUSTERS).
* **Root Reason for Existing Formulation**: Climbs were initially engineered exclusively for pre-planned GPX routes. Free-riding climb awareness was architected in `LiveClimbsRepository` but stalled due to lack of workout ingestion.
* **Preservation of Core Invariants**: 50m start/summit deduplication strictly preserved; GPX route climb association (`routeId`) unaltered; existing layer preferences format and defaults preserved (`CLIMBS` excluded from `DEFAULT_ENABLED` to prevent visual clutter, matching `CLUSTERS`).
* **Side-Effect Audit (Rule 68)**: Workout climb extraction runs asynchronously on `Dispatchers.IO` to prevent UI frame drops; database operations are serialized inside transactions; `LiveClimbsRepository.allClimbs` updates reactively.

---

## 5. Architectural Strategy & High-Level Solution
* **Data Flow**: `WorkoutRepository.reloadWorkoutData` $\rightarrow$ if `isNewImportOrFinish` $\rightarrow$ `getWorkoutTrackPoints` $\rightarrow$ `ClimbDetector.detectClimbs` $\rightarrow$ `ClimbsDatabaseManager.insertClimbsWithDeduplicationBatch` $\rightarrow$ `LiveClimbsRepository.refreshClimbs()`.
* **Map Integration**: `GeneralMapLayer.CLIMBS` $\rightarrow$ `MapFragmentWithTrackViewModel` queries `ClimbsDatabaseManager.getAllClimbs()` $\rightarrow$ `MapScreenWithTrack` renders polylines + opens `ClimbDetailSheet` on tap.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero schema migration breaking changes (`Climbs.db` version remains 1); test pass rate remains 100%; no modification of `PathPreviewMap.kt` or `ui/clusters/*`.
* **Risk Rating**: **LOW** — Additive data pipeline and UI layer reusing existing proven components (`ClimbDetector`, `ClimbsDatabaseManager`, `ClimbDetailSheet`).
