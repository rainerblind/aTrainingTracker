h2. Stage 4 Software Construction: ATT-6692
* **Reactive All Stored Climbs Flow in LiveClimbsRepository (REQ-MAP-067.1)**:
  - Added `_allStoredClimbs = MutableStateFlow<List<Climb>>(emptyList())` and public `val allStoredClimbs: StateFlow<List<Climb>> = _allStoredClimbs.asStateFlow()`.
  - In `refreshClimbs(activeRouteId: Long?)`, fetched all stored catalog climbs via `climbsDbManager.getAllClimbs()` on IO dispatcher and emitted them to `_allStoredClimbs`.
* **MapFragmentWithTrackViewModel Decoupling & Reactive Flow (REQ-MAP-067.2)**:
  - Replaced direct dependency on `ClimbsDatabaseManager` with `LiveClimbsRepository`.
  - Rewired `mapClimbsFlow` to reactively `combine(enabledLayers, liveClimbsRepository.allStoredClimbs)`, ensuring real-time UI updates upon climb backfill/ingestion.
* **Architectural Cleanliness & Repository Encapsulation (REQ-MAP-067.3)**:
  - Removed grandfathered exemption for `MapFragmentWithTrackViewModel.kt` in `RepositoryUiCleanlinessContractTest.kt`.
  - Zero UI/ViewModel direct database manager references remain.
* **Unit Testing & Verification (TST-MAP-069.1..3)**:
  - `LiveClimbsRepositoryTest`: Verified `allStoredClimbs` flow emission on refresh.
  - `MapFragmentWithTrackViewModelTest`: Verified UI state updates when layer is enabled and climbs flow emits.
  - `GeneralMapLayersContractTest`: Verified layer toggling and gating using `mockLiveClimbsRepo.allStoredClimbs`.
  - `RepositoryUiCleanlinessContractTest`: Cleanliness verified with zero grandfathered exemptions.
