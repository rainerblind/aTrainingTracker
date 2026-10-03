# Engineering Implementation Plan - ATT-1309: Reactive Equipment Loading & Lifecycle Synchronization

**Ticket**: [ATT-1309](https://rainerblind.atlassian.net/browse/ATT-1309)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-39.2`  
**Components**: 
* `com.atrainingtracker.trainingtracker.ui.equipment.EquipmentViewModel`
* `com.atrainingtracker.trainingtracker.ui.equipment.EquipmentTabsScreen`
**Requirement Mapping**: `REQ-UI-160` (`docs/requirements.md`)  
**Test Spec Mapping**: `TST-UI-112` (`docs/tests.md`)  
**Branch**: `bugfix/ATT-1309`  

---

## 1. Executive Summary & Objective

In the Jetpack Compose single-activity architecture (`ATT-1082`, `REQ-UI-159`), navigating to "Räder" (Bikes) or "Schuhe" (Shoes) via the navigation drawer displays an empty list, even when equipment items are present in SQLite (`Equipment.db`).

The forensic RCA revealed that `EquipmentViewModel` initialized `_bikes` and `_shoes` to `emptyList()`, but lacked an `init` block to invoke `loadEquipment()` upon instantiation. In the legacy Fragment architecture, `EquipmentFragment` explicitly invoked `loadEquipment()` in `onResume()` and registered a `BroadcastReceiver` for `SYNCHRONIZE_EQUIPMENT_STRAVA_FINISHED`. Under Compose navigation, `EquipmentTabsScreen` was instantiated without triggering data loading, and because the ViewModel is scoped to the Activity (`viewModel(activity)`), the empty StateFlows persisted indefinitely.

The implementation will introduce:
1. Automated ViewModel self-initialization in `EquipmentViewModel.init`.
2. Falling-edge observation of `EquipmentRepository.isSyncing` (`true -> false`) to reload equipment reactively when Strava sync finishes, while suppressing redundant cold-start queries.
3. Thread-safe coroutine job cancellation/coalescing in `loadEquipment()` against overlapping triggers.
4. Clean architectural separation where `EquipmentTabsScreen` remains 100% declarative without leaking sync or side-effect logic into the Composable.
5. Automated unit verification via `EquipmentViewModelLifecycleTest.kt`.

---

## 2. Requirements Traceability Matrix

| Requirement Clause | Architecture / Component | Implementation Detail | Test Verification (`TST-UI-112`) |
| :--- | :--- | :--- | :--- |
| **`REQ-UI-160.1`** (Automated Self-Initialization) | `EquipmentViewModel.kt` | `init { loadEquipment(); observeSyncStatus() }` launching on `ioDispatcher` to populate `_bikes` and `_shoes`. | `EquipmentViewModelLifecycleTest.testInit_automaticallyLoadsEquipmentOnCreation` |
| **`REQ-UI-160.2`** (Falling-Edge Strava Sync Observation) | `EquipmentViewModel.kt` | Constructor-injected `syncStatusFlow: StateFlow<Boolean> = EquipmentRepository.isSyncing`; reloads on `wasSyncing && !syncing`; ignores initial `false`. | `EquipmentViewModelLifecycleTest.testObserveSyncStatus_reloadsOnFallingEdge` & `testObserveSyncStatus_suppressesColdStartRedundancy` |
| **`REQ-UI-160.3`** (Concurrency & Deduplication Guard) | `EquipmentViewModel.kt` | `@Volatile private var loadJob: Job? = null` with `@Synchronized fun loadEquipment()` cancelling pending jobs. | `EquipmentViewModelLifecycleTest.testLoadEquipment_cancelsPreviousPendingJob` |
| **`REQ-UI-160.4`** (Declarative UI Layer) | `EquipmentTabsScreen.kt` | Verified caller contract; collects `viewModel.bikes` and `viewModel.shoes` directly with zero ad-hoc side-effect hooks. | `EquipmentTabsScreen` code inspection and existing UI tests |
| **`REQ-UI-160.5`** (Backward Interoperability & Invariants) | `EquipmentFragment.kt`, DB helpers | Zero alterations to SQLite schema, queries, or legacy fragment contracts. | Clean-room test suite (`./gradlew testDebugUnitTest`) |

---

## 3. Step-by-Step Implementation Changes

### 3.1 Component 1: `EquipmentViewModel.kt`

1. **Constructor Injection for Sync Flow**:
   Update constructor signature to accept `syncStatusFlow: StateFlow<Boolean> = EquipmentRepository.isSyncing` with `@JvmOverloads` preservation:
   ```kotlin
   class EquipmentViewModel @JvmOverloads constructor(
       application: Application,
       private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
       private val dbEquipmentHelper: EquipmentDbHelper = EquipmentDbHelper(application),
       private val dbLinksHelper: SportTypeEquipmentLinkManager = SportTypeEquipmentLinkManager.getInstance(application),
       private val dbSportHelper: SportTypeDatabaseManager = SportTypeDatabaseManager.getInstance(application),
       private val dbDevicesHelper: DevicesDatabaseManager = DevicesDatabaseManager.getInstance(application),
       private val dbSummariesManager: WorkoutSummariesDatabaseManager = WorkoutSummariesDatabaseManager.getInstance(application),
       private val syncStatusFlow: StateFlow<Boolean> = EquipmentRepository.isSyncing
   ) : AndroidViewModel(application)
   ```

2. **Automated Initialization (`init`) & Sync Observer**:
   ```kotlin
   init {
       loadEquipment()
       observeSyncStatus()
   }

   /**
    * Observes the equipment synchronization state flow in [viewModelScope].
    * Triggers a reload if and only if the sync status transitions from true to false
    * (falling edge), indicating a background Strava sync has finished.
    */
   private fun observeSyncStatus() {
       viewModelScope.launch(ioDispatcher) {
           var wasSyncing = false
           syncStatusFlow.collect { syncing ->
               if (wasSyncing && !syncing) {
                   loadEquipment()
               }
               wasSyncing = syncing
           }
       }
   }
   ```

3. **Thread-Safe Job Deduplication in `loadEquipment()`**:
   ```kotlin
   @Volatile
   private var loadJob: Job? = null

   /**
    * Loads equipment items asynchronously from the database and updates
    * [_bikes] and [_shoes] StateFlows. Cancels any pending prior job to prevent
    * overlapping or stale writes during rapid successive trigger events.
    */
   @Synchronized
   fun loadEquipment() {
       loadJob?.cancel()
       loadJob = viewModelScope.launch(ioDispatcher) {
           val fetchItems = { sportType: BSportType ->
               val equipmentDataList = dbEquipmentHelper.getEquipmentItems(sportType)
               equipmentDataList.map { data ->
                   val linkedDeviceIds = dbEquipmentHelper.getDeviceIdsForEquipment(data.id)
                   val sensorNames = linkedDeviceIds.mapNotNull { deviceId ->
                       dbDevicesHelper.getDeviceName(deviceId)
                   }.joinToString(", ")

                   val linkedSportTypeIds = dbLinksHelper.getSportTypeIdsForEquipment(data.id)
                   val sportTypeNames = linkedSportTypeIds.mapNotNull { sportId ->
                       dbSportHelper.getUIName(sportId)
                   }.joinToString(", ")

                   val stats = dbSummariesManager.getEquipmentStats(data.id)

                   EquipmentItem(
                       id = data.id,
                       name = data.name,
                       linkedDeviceIds = linkedDeviceIds,
                       linkedDeviceNames = sensorNames,
                       linkedSportTypeIds = linkedSportTypeIds,
                       linkedSportTypeNames = sportTypeNames,
                       frameType = data.frameType,
                       stravaName = data.stravaName,
                       stravaId = data.stravaId,
                       isRetired = data.isRetired,
                       firstUsed = stats.firstUsage?.substringBefore(" "),
                       lastUsed = stats.lastUsage?.substringBefore(" "),
                       statsData = StatsData.fromDatabase(
                           primaryTitle = data.name,
                           secondaryTitle = getApplication<Application>().getString(R.string.stats_total),
                           stats = stats,
                           equipmentId = data.id
                       )
                   )
               }
           }

           val loadedBikes = fetchItems(BSportType.BIKE)
           val loadedShoes = fetchItems(BSportType.RUN)

           _bikes.value = loadedBikes
           _shoes.value = loadedShoes
       }
   }
   ```

4. **KDoc Headers**:
   Add compliant class-level and method-level headers for all public and internal components.

### 3.2 Component 2: `EquipmentTabsScreen.kt`

* Verify declarative contract: `EquipmentTabsScreen` requires zero modifications. It collects `viewModel.bikes` and `viewModel.shoes`, which are automatically kept up to date by `EquipmentViewModel`.

### 3.3 Component 3: Unit Verification Suite (`EquipmentViewModelLifecycleTest.kt`)

Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentViewModelLifecycleTest.kt`:
1. `testInit_automaticallyLoadsEquipmentOnCreation`:
   * Setup mock `EquipmentDbHelper` returning 1 bike and 1 shoe.
   * Instantiate `EquipmentViewModel`.
   * Advance coroutine scheduler (`testDispatcher.scheduler.advanceUntilIdle()`).
   * Assert `viewModel.bikes.value.size == 1` and `viewModel.shoes.value.size == 1` without calling `loadEquipment()` manually.
2. `testObserveSyncStatus_reloadsOnFallingEdge`:
   * Pass `MutableStateFlow(false)` as `syncStatusFlow`.
   * Advance scheduler: assert DB was queried once during `init`.
   * Emit `true` (sync running): advance scheduler; assert query count remains 1.
   * Emit `false` (sync finished): advance scheduler; assert query count increments to 2.
3. `testObserveSyncStatus_suppressesColdStartRedundancy`:
   * Pass `MutableStateFlow(false)`.
   * Advance scheduler; assert query count is strictly 1 (no duplicate query on cold start).
4. `testLoadEquipment_cancelsPreviousPendingJob`:
   * Trigger multiple rapid calls to `loadEquipment()` and verify no unhandled cancellation exceptions propagate and final state is consistent.
5. Verify `EquipmentViewModelRetiredTest.kt` continues to pass with 0 regressions.

---

## 4. System Invariants & Risk Mitigation

* **Database Invariant**: Zero changes to SQLite tables, column indices, or DAO query methods.
* **StateFlow Immutability**: `bikes` and `shoes` remain read-only public `StateFlow<List<EquipmentItem>>`.
* **Single-Activity Interop**: `EquipmentViewModel` remains scoped to the Activity in `ATrainingTrackerApp.kt`; loaded data survives drawer destination switches.
* **Legacy Fragment Safety**: `EquipmentFragment` continues to invoke `loadEquipment()` on resume safely (deduplicated by `loadJob?.cancel()`).

---

## 5. Verification & Testing Strategy

1. **Automated Unit Tests (SWE.4)**:
   * Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.equipment.*"`.
2. **Repository Clean-Room Regression (SWE.5)**:
   * Execute `./gradlew testDebugUnitTest` to guarantee 0 regressions across all modules.
