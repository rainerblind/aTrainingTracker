# Stage 3: Implementation Plan - ATT-6692: Reactive allStoredClimbs & MapFragmentWithTrackViewModel Decoupling

**Ticket**: [ATT-6692](https://rainerblind.atlassian.net/browse/ATT-6692)  
**Parent Epic**: [ATT-2565](https://rainerblind.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-MAP-067` (`REQ-MAP-067.1`..`REQ-MAP-067.3`)  
**Test Mapping**: `TST-MAP-069` (`TST-MAP-069.1`..`TST-MAP-069.4`)  
**Branch**: `feature/ATT-6692`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-11  

---

## 1. Problem Description & Background
`MapFragmentWithTrackViewModel.kt` previously injected `ClimbsDatabaseManager` directly and implemented `mapClimbsFlow` mapping exclusively over `enabledLayers`. This caused newly persisted climbs to stay hidden until manual layer toggling and breached the repository-only UI architectural boundary (`ATT-432`).

---

## 2. Traceability & Requirements Mapping
* **REQ-MAP-067.1** $\rightarrow$ `TST-MAP-069.1`: Expose reactive `val allStoredClimbs: StateFlow<List<Climb>>` on `LiveClimbsRepository`.
* **REQ-MAP-067.2** $\rightarrow$ `TST-MAP-069.2`: Decouple `MapFragmentWithTrackViewModel` from `ClimbsDatabaseManager` via `LiveClimbsRepository` injection and combine `(enabledLayers, allStoredClimbs)`.
* **REQ-MAP-067.3** $\rightarrow$ `TST-MAP-069.3`: Remove `MapFragmentWithTrackViewModel` exclusion in `RepositoryUiCleanlinessContractTest`.
* **REQ-MAP-067** $\rightarrow$ `TST-MAP-069.4`: Targeted and regression suite verification across map, climbs, and architecture tests.

---

## 3. System Invariants & Preserved Behavior
1. **Clean Architecture Boundary**: ViewModels interact strictly with domain repositories; zero database manager imports in `ui/map/`.
2. **Reactive Live Map**: Stored climbs update dynamically while `GeneralMapLayer.CLIMBS` is enabled without layer toggling.
3. **Route Tracking Invariant**: Active navigated route filtering in `LiveClimbsRepository.allClimbs` remains 100% intact.
4. **Human Gate**: Parent ticket progresses to `Final Review (Human)`, never directly to `Erledigt`.

---

## 4. Proposed Architectural Changes
* **`LiveClimbsRepository.kt`**: Declare `_allStoredClimbs = MutableStateFlow<List<Climb>>(emptyList())`, expose `allStoredClimbs: StateFlow<List<Climb>>`, and update on init and inside `refreshClimbs()`.
* **`MapFragmentWithTrackViewModel.kt`**: Replace `climbsDbManager: ClimbsDatabaseManager?` with `liveClimbsRepo: LiveClimbsRepository?`, combine `enabledLayers` + `allStoredClimbs` in `mapClimbsFlow`.
* **`RepositoryUiCleanlinessContractTest.kt`**: Remove `MapFragmentWithTrackViewModel` exemption filter.
* **UI Consistency**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Reactive Stored Climbs StateFlow (`REQ-MAP-067.1`)
* Files: `LiveClimbsRepository.kt`.
* Changes: Expose `allStoredClimbs: StateFlow<List<Climb>>`. Populate on init and re-query in `refreshClimbs()`.

### Step 2: ViewModel Decoupling & Reactive Layer Combination (`REQ-MAP-067.2`)
* Files: `MapFragmentWithTrackViewModel.kt`.
* Changes: Remove `ClimbsDatabaseManager` dependency; inject `LiveClimbsRepository`. Combine `enabledLayers` with `liveClimbsRepository.allStoredClimbs` in `mapClimbsFlow`.

### Step 3: Remove Architectural Exemption (`REQ-MAP-067.3`)
* Files: `RepositoryUiCleanlinessContractTest.kt`.
* Changes: Remove `MapFragmentWithTrackViewModel` from the exclusion list.

### Step 4: Unit Test Updates & Verification (`TST-MAP-069.1`..`TST-MAP-069.3`)
* Files: `LiveClimbsRepositoryTest.kt`, `MapFragmentWithTrackViewModelTest.kt`, `GeneralMapLayersContractTest.kt`.
* Changes: Update test view model instantiation with `mockLiveClimbsRepo`. Add tests verifying immediate climb emission upon `allStoredClimbs` change while `CLIMBS` is enabled.

### Step 5: Targeted Unit Test Execution (`TST-MAP-069.4`)
* Command: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.climbs.*" --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.RepositoryUiCleanlinessContractTest"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests under `parallel_sprint_lock.py`, followed by clean-room test suite in Stage 5.
* **Rollback**: Clean revert of `feature/ATT-6692` branch without impacting `sprint/2026-46.2`.
