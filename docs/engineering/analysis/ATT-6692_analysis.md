# Stage 1 Analysis: ATT-6692 - LiveClimbsRepository & MapFragmentWithTrackViewModel: Expose reactive allStoredClimbs StateFlow and decouple MapFragmentWithTrackViewModel from ClimbsDatabaseManager

**Ticket**: [ATT-6692](https://rainerblind.atlassian.net/browse/ATT-6692)  
**Parent Epic**: [ATT-2565](https://rainerblind.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6692`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
In `ATT-2766`, `MapFragmentWithTrackViewModel.kt` injected `ClimbsDatabaseManager` directly and implemented `mapClimbsFlow` as a 1D mapping of `enabledLayers`:
```kotlin
private val mapClimbsFlow = enabledLayers.map { layers ->
    if (GeneralMapLayer.CLIMBS !in layers) emptyList() else climbsDb.getAllClimbs()
}.flowOn(defaultDispatcher).distinctUntilChanged()
```
This introduces two architectural flaws:
1. **Stale Map Climbs When Layer Is Active**: Newly extracted climbs (from GPX imports, workout finishes, or backfill) do not appear on `MapScreenWithTrack` when `CLIMBS` is already enabled, forcing athletes to manually toggle the layer off and on.
2. **Layering Boundary Violation (`ATT-432`)**: ViewModels must interact with domain Repositories rather than consuming low-level SQLite database managers directly.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification**:
  * Code audit confirms `MapFragmentWithTrackViewModel.kt` (lines 62, 71) directly imports and instantiates `ClimbsDatabaseManager`.
  * Audit of `LiveClimbsRepository.kt` shows `_allClimbs` represents route-filtered candidate climbs (`getClimbsForRoute(activeRouteId)` when navigating), not the full database catalog.
  * Audit of `RepositoryUiCleanlinessContractTest.kt:211` confirms `MapFragmentWithTrackViewModel` was explicitly grandfathered due to this leak.
  * The hypothesis is confirmed: Exposing `allStoredClimbs: StateFlow<List<Climb>>` on `LiveClimbsRepository` (re-queried upon `refreshClimbs()`) and combining `(enabledLayers, liveClimbsRepository.allStoredClimbs)` in `MapFragmentWithTrackViewModel` completely solves both staleness and architectural encapsulation.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Expose reactive `val allStoredClimbs: StateFlow<List<Climb>>` on `LiveClimbsRepository`, updated on initialization and inside `refreshClimbs()`.
  * Decouple `MapFragmentWithTrackViewModel` by replacing `climbsDbManager: ClimbsDatabaseManager?` with `liveClimbsRepo: LiveClimbsRepository?`.
  * Combine `enabledLayers` with `liveClimbsRepository.allStoredClimbs` in `MapFragmentWithTrackViewModel` for immediate live updates.
  * Remove `MapFragmentWithTrackViewModel` exemption from `RepositoryUiCleanlinessContractTest`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modification to `ClimbDetector` or `ClimbsDatabaseManager` persistence schema.
  * No UI changes to `MapScreenWithTrack` or `ClimbDetailSheet`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-MAP-064` (*Workout Climb Ingestion, Spatial Deduplication & Toggleable Map Climbs Layer*).
* **Historical Origin & Commit Trace**: `ATT-2766` (`HEAD~2`), `ATT-432` (`9b41a542`, ViewModel-Repository boundary).
* **Root Reason for Existing Formulation**: `ATT-2766` initially connected `MapFragmentWithTrackViewModel` to `ClimbsDatabaseManager` to establish the prototype `CLIMBS` toggle layer without modifying `LiveClimbsRepository`'s internal StateFlow structure.
* **Preservation of Core Invariants**: Route-aware live tracking in `LiveClimbsRepository.allClimbs` remains intact; layer toggle behavior and UI state emission contracts remain unchanged.

---

## 5. Architectural Strategy & High-Level Solution
1. **`LiveClimbsRepository.kt`**:
   - Add `private val _allStoredClimbs = MutableStateFlow<List<Climb>>(emptyList())`.
   - Expose `val allStoredClimbs: StateFlow<List<Climb>> = _allStoredClimbs.asStateFlow()`.
   - In `refreshClimbs()`, query `climbsDb.getAllClimbs()` into `_allStoredClimbs.value`.
2. **`MapFragmentWithTrackViewModel.kt`**:
   - Remove `ClimbsDatabaseManager` import and constructor argument; inject `LiveClimbsRepository`.
   - Update `mapClimbsFlow`:
     ```kotlin
     private val mapClimbsFlow = combine(enabledLayers, liveClimbsRepository.allStoredClimbs) { layers, storedClimbs ->
         if (GeneralMapLayer.CLIMBS !in layers) emptyList() else storedClimbs
     }.flowOn(defaultDispatcher).distinctUntilChanged()
     ```
3. **`RepositoryUiCleanlinessContractTest.kt`**:
   - Remove `MapFragmentWithTrackViewModel.kt` exclusion now that repository encapsulation is restored.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero direct database manager imports in `ui/map/` ViewModels.
  2. Immediate reactive rendering of newly extracted climbs without manual UI toggling.
  3. Preservation of existing route-filtered tracking behavior in `LiveClimbsRepository.allClimbs`.
* **Risk Rating**: **LOW** (internal StateFlow combination and constructor parameter refactoring with 100% test isolation).
