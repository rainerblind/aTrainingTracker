# Stage 2: Requirement & Test Specification - ATT-6692: Reactive allStoredClimbs & MapFragmentWithTrackViewModel Decoupling

**Ticket**: [ATT-6692](https://atrainingtracker.atlassian.net/browse/ATT-6692) | **Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-2565](https://atrainingtracker.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2` | **Branch**: `feature/ATT-6692`  
**Requirement Mapping**: `REQ-MAP-067` (`REQ-MAP-067.1`..`REQ-MAP-067.3`) | **Test Spec ID**: `TST-MAP-069` (`TST-MAP-069.1`..`TST-MAP-069.4`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (`REQ-MAP-067`)

### 1.1 Problem Statement & Rationale
In `ATT-2766`, `MapFragmentWithTrackViewModel.kt` injected `ClimbsDatabaseManager` directly and implemented `mapClimbsFlow` by mapping exclusively over `enabledLayers`. This introduced two defects:
1. *Stale Map Climbs*: Newly extracted climbs (from GPX imports, workout finishes, or backfill) do not render on `MapScreenWithTrack` while `CLIMBS` is already toggled ON until manually toggled off and back on.
2. *Architecture Boundary Leak (`ATT-432`)*: Direct database manager access from a ViewModel violates clean architecture layering, requiring a grandfathered exemption in `RepositoryUiCleanlinessContractTest`.

### 1.2 Functional & Architectural Requirements
* **REQ-MAP-067.1** (*Reactive Stored Climbs StateFlow*): `LiveClimbsRepository` SHALL expose `val allStoredClimbs: StateFlow<List<Climb>>`. Upon initialization and whenever `refreshClimbs()` is invoked, `LiveClimbsRepository` SHALL query all stored climbs from `ClimbsDatabaseManager.getAllClimbs()` on `Dispatchers.IO` and emit them to `_allStoredClimbs`.
* **REQ-MAP-067.2** (*ViewModel Repository Decoupling & Reactive Layer Combination*): `MapFragmentWithTrackViewModel` SHALL inject `LiveClimbsRepository` instead of `ClimbsDatabaseManager`. Direct references, imports, and calls to `ClimbsDatabaseManager` in `MapFragmentWithTrackViewModel` SHALL be removed. `mapClimbsFlow` SHALL combine `enabledLayers` and `liveClimbsRepository.allStoredClimbs`. When `GeneralMapLayer.CLIMBS in enabledLayers`, it SHALL emit the live stored climbs list; when disabled, it SHALL emit `emptyList()`.
* **REQ-MAP-067.3** (*Architectural Cleanliness Enforcement*): `RepositoryUiCleanlinessContractTest` SHALL remove `MapFragmentWithTrackViewModel` from the grandfathered database access exclusion list. All ViewModels in `ui/map/` MUST satisfy zero direct database manager imports.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Reactive Climb Updates on Map)**: *Given* `MapScreenWithTrack` with `GeneralMapLayer.CLIMBS` enabled, *When* new climbs are inserted into `Climbs.db` and `refreshClimbs()` is invoked, *Then* `uiState.climbs` emits the updated climb list immediately without manual layer toggling.
* **AC-2 (ViewModel Clean Architecture Decoupling)**: *Given* `MapFragmentWithTrackViewModel`, *When* inspected via compile-time reflection and `RepositoryUiCleanlinessContractTest`, *Then* zero references or imports of `ClimbsDatabaseManager` exist.
* **AC-3 (Non-Climbs Layer Optimization)**: *Given* `GeneralMapLayer.CLIMBS` is disabled, *When* `allStoredClimbs` updates, *Then* `uiState.climbs` continues to emit `emptyList()`, preventing unnecessary map recomposition.

---

## 2. Test Specification & Traceability Matrix (`TST-MAP-069`)

| Req ID | Test Case | Target Component | Verification Scope |
| :--- | :--- | :--- | :--- |
| `REQ-MAP-067.1` | `TST-MAP-069.1` | `LiveClimbsRepositoryTest.kt` | Verify `LiveClimbsRepository.allStoredClimbs` initializes with stored climbs and updates reactively on `refreshClimbs()`. |
| `REQ-MAP-067.2` | `TST-MAP-069.2` | `MapFragmentWithTrackViewModelTest.kt` | Verify `MapFragmentWithTrackViewModel` receives `LiveClimbsRepository`, combines `enabledLayers` with `allStoredClimbs`, and emits updated climbs immediately while layer is active. |
| `REQ-MAP-067.3` | `TST-MAP-069.3` | `RepositoryUiCleanlinessContractTest.kt` | Verify `MapFragmentWithTrackViewModel` has no grandfathered exemption and passes zero database manager import verification. |
| `REQ-MAP-067` | `TST-MAP-069.4` | Targeted Test Suite | Run targeted tests across `climbs.*`, `ui.map.*`, and `architecture.*` asserting 100% pass rate. |

---

## 3. Localization Audit
* **Parity Status**: Audited. No user-facing strings added or modified; 100% parity preserved across all 9 supported locales.
