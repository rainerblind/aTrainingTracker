# ATT-6692 Walkthrough: Reactive allStoredClimbs StateFlow & MapFragmentWithTrackViewModel Decoupling

**Ticket**: [ATT-6692](https://atrainingtracker.atlassian.net/browse/ATT-6692)  
**Parent Epic**: [ATT-2565](https://atrainingtracker.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Sprint**: `2026-46.2` | **Target Release**: `V5.0.0` | **Status**: Verified  
**Requirement Mapping**: `REQ-MAP-067` (`REQ-MAP-067.1`..`REQ-MAP-067.3`) | **Test Mapping**: `TST-MAP-069` (`TST-MAP-069.1`..`TST-MAP-069.3`)  

---

## 1. Executive Summary

In `ATT-2766`, `MapFragmentWithTrackViewModel.kt` directly referenced `ClimbsDatabaseManager` and only queried `climbsDb.getAllClimbs()` inside a flow mapped solely from `enabledLayers`. This had two critical shortcomings:
1. When the `CLIMBS` layer was already active, newly detected or backfilled climbs (e.g. from GPX import or historical backfill) were not reactively displayed until the user manually toggled the layer off and on.
2. Direct access to `ClimbsDatabaseManager` from a ViewModel violated the repository boundary architecture (`ATT-432`), requiring a temporary grandfathered exemption in `RepositoryUiCleanlinessContractTest`.

Under `ATT-6692` (governed by `REQ-MAP-067` / `TST-MAP-069`):
- **Reactive All Stored Climbs Flow in LiveClimbsRepository (`REQ-MAP-067.1`)**:
  - `LiveClimbsRepository.kt`: Exposed `val allStoredClimbs: StateFlow<List<Climb>>` backed by `_allStoredClimbs`.
  - In `refreshClimbs(activeRouteId: Long?)`, populated `_allStoredClimbs` on `Dispatchers.IO` using `climbsDbManager.getAllClimbs()`.
- **MapFragmentWithTrackViewModel Decoupling & Reactive Flow (`REQ-MAP-067.2`)**:
  - `MapFragmentWithTrackViewModel.kt`: Replaced direct dependency on `ClimbsDatabaseManager` with domain repository `LiveClimbsRepository`.
  - Re-implemented `mapClimbsFlow` as a reactive `combine(enabledLayers, liveClimbsRepository.allStoredClimbs)`. When `GeneralMapLayer.CLIMBS` is enabled, any emission on `allStoredClimbs` immediately pushes the updated climb list to the map UI.
- **Architectural Cleanliness & Repository Encapsulation (`REQ-MAP-067.3`)**:
  - `RepositoryUiCleanlinessContractTest.kt`: Removed grandfathered exemption for `MapFragmentWithTrackViewModel.kt`.
  - Verified repository encapsulation via `verify_requirement_governance.py --check-repo-encapsulation` (0 violations).

---

## 2. Traceability Matrix

| Requirement | Test Specification | Production Code | Verification Tests | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-MAP-067.1` | `TST-MAP-069.1` | `LiveClimbsRepository.kt` | `LiveClimbsRepositoryTest.kt` | **Verified** |
| `REQ-MAP-067.2` | `TST-MAP-069.2` | `MapFragmentWithTrackViewModel.kt` | `MapFragmentWithTrackViewModelTest.kt`, `GeneralMapLayersContractTest.kt` | **Verified** |
| `REQ-MAP-067.3` | `TST-MAP-069.3` | `MapFragmentWithTrackViewModel.kt`, `RepositoryUiCleanlinessContractTest.kt` | `RepositoryUiCleanlinessContractTest.kt`, `verify_requirement_governance.py` | **Verified** |

---

## 3. Verification & Test Execution

- **Targeted Test Execution (`parallel_sprint_lock.py`)**:
  - Command: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.climbs.*" --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.RepositoryUiCleanlinessContractTest"`
  - Results: **100% PASS** (all climbs, map, and cleanliness contract tests succeeded).
- **Architecture Governance**:
  - `python3 tools/verify_requirement_governance.py --check-repo-encapsulation`: **PASS** (Zero leaky DatabaseManager/Cursor members).
  - `python3 tools/verify_requirement_governance.py --check-specs`: **PASS** (Zero spec violations).
- **Physical Device Audit**:
  - `adb devices` checked: no physical devices connected; per Rule 79 emulator launch omitted.
