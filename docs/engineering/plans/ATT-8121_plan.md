# Stage 3: Implementation Plan - ATT-8121: Preference/DataStore Singleton Encapsulation & Leak Elimination

**Ticket**: [ATT-8121](https://issue-tracker/ATT-8121) | **Parent Epic**: [ATT-8119](https://issue-tracker/ATT-8119) | **Tier**: `tier-a` (`2 SP`)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-22_01_Früh` (ID `471`) | **Branch**: `feature/ATT-8121`  
**Requirement Mapping**: `REQ-SET-128` (`REQ-SET-128.1`–`REQ-SET-128.4`) | **Test Mapping**: `TST-SET-130` (`TST-SET-130.1`–`TST-SET-130.4`)  
**Author**: Lane 3 Author (`agent1`) | **Date**: 2026-11-22

---

## 1. Architectural Design (SWE.2)
* **Singleton Pattern Alignment**: Bring `SettingsDataStore`, `MyPreferenceManager`, and `ProFeatureManager` into full parity with `TuningPreferencesDataStore` (`REQ-TRK-079.1`): `private constructor`, `context.applicationContext ?: context` normalization, thread-safe `getInstance(context)`, `@VisibleForTesting internal fun createForTesting(...)` accepting injectable test `CoroutineScope`/`DataStore`, and synchronized `setInstanceForTesting(...)`.
* **Fallback-Safe `getInstance` Guard (`REQ-SET-128.4`)**: Add a shared `internal fun hasRealFilesDir(context: Context): Boolean` helper alongside `resolveDataStore(context)` in `SettingsDataStore.kt`. In `SettingsDataStore` and `MyPreferenceManager`, track `private val isInMemoryFallback: Boolean = dataStore is InMemoryPreferencesDataStore` and `@Volatile private var isTestOverride: Boolean = false`. Fast path (`existing != null && (isTestOverride || !existing.isInMemoryFallback)`) incurs zero lock and zero `filesDir` I/O in production. If an un-injected JVM fallback exists (`!isTestOverride && existing.isInMemoryFallback`), keep returning it for MockK contexts (`!hasRealFilesDir(context)`) and atomically replace it under `synchronized(this)` if a real Android/Robolectric `filesDir` context arrives.
* **Shared Process Scope in `MyPreferenceManager`**: Replace per-instance `CoroutineScope(SupervisorJob() + Dispatchers.IO)` (`:151`) with `companion object { private val sharedAppScope = CoroutineScope(SupervisorJob() + Dispatchers.IO) }`, injected as default `appScope: CoroutineScope = sharedAppScope`.
* **Dead Collector Cleanup**: Delete `ATrainingTrackerMap.kt:215-216` (`tuningDataStore`, `tuningConfig`) and unused imports (`:27, 29, 30`).
* **SWE.2 / Detailed Design Impact**: No new subsystem, service, DB, repository, or prefix family (`Rule 8.8`); zero changes to `docs/architecture.md`, `docs/architecture/detailed_design/*`, `strings*.xml`, or `TuningPreferencesDataStore.kt`.

## 2. Non-Negotiable System Invariants
1. **Zero `runBlocking` Added**: `DataStoreHotPathContractTest` (`REQ-SET-079`) remains 100% green.
2. **Zero Activity Retention**: Stored `context` on `SettingsDataStore` and `ProFeatureManager` is strictly `context.applicationContext ?: context` (`MyPreferenceManager` stores no `Context` field at all).
3. **Zero Cross-Test Singleton Pollution**: All migrated `mockkConstructor` tests switch to `setInstanceForTesting(mock)` in `@Before` and `setInstanceForTesting(null)` in `@After`; Robolectric persistence tests use `createForTesting(context)`; `hasRealFilesDir` upgrade guard protects unmocked JVM ViewModel tests.
4. **Monolith & Binary Hygiene**: `TuningPreferencesDataStore.kt` stays untouched (`<= 2,410` LoC); `AndroidViewModel` `@JvmOverloads` constructors (`ZonesSettingsViewModel`) remain reflective-factory compatible (`Rule 3.5`).

## 3. Atomic Step-by-Step Construction Plan (Stage 4 — Strictly Post-Gate-3)
1. **Step 1 — Gate 3 Lock Check (`Rule 4.1`)**: Run `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` and verify `GATE_PASSED` (exit `0`) before editing any file under `app/src/`.
2. **Step 2 — `SettingsDataStore.kt` (`REQ-SET-128.1`, `.4`)**: Add `hasRealFilesDir(context)`, privatize constructor with `context.applicationContext ?: context` + injectable `appScope`, add fallback-upgrade `getInstance`, `@VisibleForTesting internal fun createForTesting(...)`, and `isTestOverride`-aware `setInstanceForTesting(...)`.
3. **Step 3 — `SettingsDataStore` Production Callers (`REQ-SET-128.2`, 3 files / 5 sites)**: Update `ZonesSettingsViewModel.kt:52, 238`, `ZonesSettingsActivity.kt:170, 960`, and `TrackingViewModel.kt:237` to `SettingsDataStore.getInstance(...)`.
4. **Step 4 — `MyPreferenceManager.kt` & 13 Production Callers (`REQ-SET-128.1`, `.2`, `.4`)**: Privatize constructor, move default scope to `companion object`'s `sharedAppScope`, add `getInstance`/`createForTesting`/`setInstanceForTesting` with `hasRealFilesDir` guard; migrate all 17 sites across `MainActivityWithNavigation.kt`, `ATrainingTrackerApp.kt`, `RoutesViewModel.kt`, `PeriodsViewModel.kt`, `EditWorkoutViewModel.kt`, `TrackOnMapScreen.kt`, `WorkoutSummariesViewModel.kt`, `SegmentListViewModel.kt`, `WorkoutClustersViewModel.kt`, `AdvancedTuningScreen.kt`, `WorkoutMasksAndCardsSection.kt`, `AdvancedTuningDialog.kt`, and `TrackOnMapAftermathViewModel.kt`.
5. **Step 5 — `ProFeatureManager.kt` & 2 Production Callers (`REQ-SET-128.1`, `.2`)**: Privatize constructor, normalize `context.applicationContext ?: context`, add `@Volatile` on `instance`, `@Synchronized` on `setInstanceForTesting`, and `@VisibleForTesting internal fun createForTesting(...)`; migrate `ATrainingTrackerApp.kt:890` and `SupporterDebugSection.kt:252` to `ProFeatureManager.getInstance(context)`.
6. **Step 6 — `ATrainingTrackerMap.kt` Dead Collector Removal (`REQ-SET-128.3`)**: Delete lines `215-216` (`tuningDataStore`, `tuningConfig`) and unused imports (`collectAsState`, `TuningConfig`, `TuningPreferencesDataStore`).
7. **Step 7 — Migrate Existing Unit Tests (26 files)**:
   * Replace direct `SettingsDataStore(...)` calls with `SettingsDataStore.createForTesting(...)` in the 10 verified test files (`BatterySaverModelPureTest`, `GarageLeagueDetectorTest`, `SettingsDataStoreBaselinesTest`, `SettingsDataStoreConfiguredTest`, `AthleteMetricsRepositorySyncTest`, `SettingsDataStoreZoneAtomicTest`, `SettingsDataStoreJavaHelperNonBlockingTest`, `SettingsDataStoreZoneGaugeTest`, `SettingsDataStorePowerZonesTest`, `SettingsDataStoreWeightTest`).
   * Replace `mockkConstructor(SettingsDataStore::class)` with `SettingsDataStore.setInstanceForTesting(mock)` + `@After` reset in `TrackingViewModelMapStateTest` and `TrackingViewModelGridTest`.
   * Replace `mockkConstructor(MyPreferenceManager::class)` with `MyPreferenceManager.setInstanceForTesting(mock)` + `@After` reset in all 11 ViewModel test files (`RoutesViewModelTest`, `RoutesViewModelFilterTest`, `PeriodsViewModelTest`, `PeriodPrimaryRegionSelectionTest`, `WorkoutSummariesViewModelFilterTest`, `SegmentListViewModelTest`, `WorkoutClustersViewModelTest`, `TrackOnMapClusterActionViewModelTest`, `TrackOnMapViewModelTelemetryTest`, `TrackOnMapAftermathViewModelTest`, `TrackOnMapAftermathViewModelFallbackTest`), and direct `MyPreferenceManager(context)` with `MyPreferenceManager.createForTesting(context)` in `WorkoutClustersSelectableMarkersTest` and `TelemetryChartStackVisibilityTest`.
   * Update `ProFeatureManagerTest.kt:150` to `ProFeatureManager.createForTesting(...)`.
8. **Step 8 — New Lifecycle & Isolation Verification Suite (`TST-SET-130.1`–`.3`)**: Create `app/src/test/java/com/atrainingtracker/trainingtracker/settings/PreferenceSingletonsLifecycleTest.kt` verifying non-public constructors, `applicationContext` normalization, `MyPreferenceManager` shared `SupervisorJob` reuse, `createForTesting` scope injection/cancellation, and `InMemoryPreferencesDataStore` -> real-`filesDir` singleton upgrade + `setInstanceForTesting` pinning.
9. **Step 9 — Targeted Verification (`TST-SET-130.4`)**: Run targeted Gradle test suites (never `--rerun-tasks`) + `verify_requirement_governance.py` and promote `REQ-SET-128`/`TST-SET-130` to `[Implemented]`.

## 4. Targeted Verification & Rollback Plan
* **Verification Commands**:
  ```bash
  ./gradlew :app:testDebugUnitTest --rerun \
    --tests "com.atrainingtracker.trainingtracker.settings.*" \
    --tests "com.atrainingtracker.trainingtracker.monetization.*" \
    --tests "com.atrainingtracker.trainingtracker.zones.*" \
    --tests "com.atrainingtracker.trainingtracker.batterysaver.BatterySaverModelPureTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.*" \
    --tests "com.atrainingtracker.trainingtracker.ui.routes.*" \
    --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*" \
    --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummariesViewModelFilterTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.aftermath.editworkout.*" \
    --tests "com.atrainingtracker.trainingtracker.ui.segments.segmentlist.SegmentListViewModelTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" \
    --tests "com.atrainingtracker.trainingtracker.ui.map.*" \
    --tests "com.atrainingtracker.trainingtracker.ui.athleteprofile.AthleteMetricsRepositorySyncTest" \
    --tests "com.atrainingtracker.trainingtracker.integration.ViewModelRepositoryIntegrationTest" \
    --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest"
  ```
* **Rollback**: Isolated on `feature/ATT-8121`; revert branch commits cleanly before Stage 6 squash-merge.
