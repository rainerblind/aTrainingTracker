# Stage 5 Walkthrough: ATT-8121 - Preference/DataStore Singleton Encapsulation & Leak Elimination

**Ticket**: [ATT-8121](https://issue-tracker/ATT-8121) | **Parent Epic**: [ATT-8119](https://issue-tracker/ATT-8119) | **Tier**: `tier-a` (`2 SP`, `severity-major`)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-22_01_Früh` (ID `471`) | **Branch**: `feature/ATT-8121`  
**Requirement / Test**: `REQ-SET-128` (`[Verified]`) / `TST-SET-130` (`[Verified]`) | **Gate 3**: `ATT-9597` (`PASS`)  
**Author**: Lane 3 Author (`agent1`) | **Date**: 2026-11-22

---

## 1. Summary of Implemented Changes
* **`SettingsDataStore.kt` (`REQ-SET-128.1`, `.4`)**: Added shared `internal fun hasRealFilesDir(context)` rejecting `null`, `File("")`, and throwing mocks (Gate 3 Guardrail 1); privatized primary constructor with `context.applicationContext ?: context` normalization and injectable `appScope`; added fallback-to-real-`filesDir` upgrade guard in `getInstance` (cancelling the discarded fallback's `appScope` per Guardrail 3, with zero lock/`filesDir` overhead on production fast path); added `@VisibleForTesting internal fun createForTesting(...)` and `isTestOverride`-aware `setInstanceForTesting(...)`.
* **`MyPreferenceManager.kt` (`REQ-SET-128.1`, `.2`, `.4`)**: Routed `resolveUserPreferencesDataStore` through `hasRealFilesDir(context)`; privatized primary constructor; replaced per-instance `SupervisorJob` scope with single process-wide `companion object` `sharedAppScope` (never cancelled on upgrade or tests per Guardrail 3); added thread-safe `getInstance(context)` with fallback-upgrade guard, `@VisibleForTesting internal fun createForTesting(...)`, and synchronized `setInstanceForTesting(...)`.
* **`ProFeatureManager.kt` (`REQ-SET-128.1`, `.2`)**: Privatized primary constructor (dropping redundant `@JvmOverloads` while preserving `@JvmStatic` on `getInstance` for `DeviceManager.java:1622` per Guardrail 2), normalized `context.applicationContext ?: context` before default dependency lookup in `getInstance` and `@VisibleForTesting internal fun createForTesting(...)`, added `@Volatile` on `instance`, and `@Synchronized` on `setInstanceForTesting`.
* **All 24 Production Call Sites Migrated (`REQ-SET-128.2`)**: Zero external constructor calls remain in `app/src/main/`:
  * `SettingsDataStore.getInstance` (`5` sites / `3` files): `ZonesSettingsViewModel.kt`, `ZonesSettingsActivity.kt`, `TrackingViewModel.kt`.
  * `MyPreferenceManager.getInstance` (`17` sites / `13` files): `MainActivityWithNavigation.kt`, `ATrainingTrackerApp.kt`, `RoutesViewModel.kt`, `PeriodsViewModel.kt`, `EditWorkoutViewModel.kt`, `TrackOnMapScreen.kt`, `WorkoutSummariesViewModel.kt`, `SegmentListViewModel.kt`, `WorkoutClustersViewModel.kt`, `AdvancedTuningScreen.kt`, `WorkoutMasksAndCardsSection.kt`, `AdvancedTuningDialog.kt`, `TrackOnMapAftermathViewModel.kt`.
  * `ProFeatureManager.getInstance` (`2` sites / `2` files): `ATrainingTrackerApp.kt:890`, `SupporterDebugSection.kt:252`.
* **Dead Map Collector Removed (`REQ-SET-128.3`)**: Deleted unused `tuningDataStore` / `tuningConfig` (`tuningConfigFlow.collectAsState`) and 3 unused imports in `ATrainingTrackerMap.kt`.
* **Unit Tests Migrated & Added (`27` test files)**: Migrated 10 direct `SettingsDataStore` tests, 1 `ProFeatureManagerTest`, 2 direct `MyPreferenceManager` tests to `createForTesting(...)`, migrated 13 `mockkConstructor` tests (`2` `SettingsDataStore` + `11` `MyPreferenceManager`) to `setInstanceForTesting(...)` + symmetric `@After` reset, and added `PreferenceSingletonsLifecycleTest.kt` (`7` tests, symmetric 4-singleton reset per Guardrail 4).

## 2. Verification Evidence (`TST-SET-130`)
* **Machine Evidence Bundle**: [`docs/engineering/test_evidence/tickets/ATT-8121/manifest.json`](../test_evidence/tickets/ATT-8121/manifest.json)
* **Tier-1 Targeted Unit & Contract Suite (`231/231 PASS` across `34` suites, `0` fail, `0` err, `0` skip, `0` new Kotlin warnings)**:
  * `PreferenceSingletonsLifecycleTest` (`7/7`), `TuningPreferencesDataStoreLifecycleTest` (`2/2`), `DataStoreHotPathContractTest#testSettingsDataStore_readsDoNotContainRunBlocking` (`1/1`), `ArchitectureConformanceContractTest` (`11/11`), `ProFeatureManagerTest` (`7/7`), `GarageLeagueDetectorTest` (`16/16`), `SettingsDataStore{ZoneAtomic,JavaHelperNonBlocking,PowerZones,Weight,ZoneGauge,Baselines,Configured}Test` (`46/46`), `BatterySaverModelPureTest` (`5/5`), `AthleteMetricsRepositorySyncTest` (`3/3`), `TrackingViewModel{MapState,Grid,Hrv}Test` + `TrackingTabConfigZoneDisplayTest` (`20/20`), `RoutesViewModel{,Filter}Test` (`12/12`), `PeriodsViewModelTest` + `PeriodPrimaryRegionSelectionTest` (`20/20`), `WorkoutSummariesViewModelFilterTest` (`11/11`), `EditWorkoutViewModelTest` (`16/16`), `SegmentListViewModelTest` (`4/4`), `WorkoutClusters{ViewModel,SelectableMarkers}Test` (`20/20`), `TrackOnMap{ClusterAction,ViewModelTelemetry,Aftermath,AftermathFallback}Test` + `TelemetryChartStackVisibilityTest` (`25/25`), `ViewModelRepositoryIntegrationTest` (`3/3`).
  * *Pre-Existing Out-of-Scope Observation*: `DataStoreHotPathContractTest#testHotPathFiles_containZeroRunBlockingCalls` fails on untouched `SettingsDataStoreJavaHelper.kt:381` (introduced upstream by `ATT-9348`, commit `b0bf0bc1`). `SettingsDataStore.kt` itself passes `testSettingsDataStore_readsDoNotContainRunBlocking` (`0` `runBlocking` added).
* **Live `emulator-5554` Smoke Verification (`Rule 8.5` & `Rule 9.3`)**:
  * Installed debug APK (`:app:installDebug`), launched `com.atrainingtracker.debug/.../MainActivityWithNavigation` (`Status: ok`, `CRASH_LOGCAT_CLEAN`), captured and visually inspected [`att8121_emulator_smoke.png`](../test_evidence/tickets/ATT-8121/screenshots/att8121_emulator_smoke.png) via `read_file` confirming clean top-bar Pro/sensor badges, tracking tabs, and zero crashes/glitches.
* **Requirement Governance**: `verify_requirement_governance.py --sync-indexes` and `--base-ref sprint/2026-11-22_01_Früh` PASS (`100%` parity, `N/A - 0 string changes`).

## 3. Acceptance Criteria Sign-Off
| Criterion | Status | Evidence |
| :--- | :---: | :--- |
| AC1: Private constructors + `applicationContext` normalization | **MET** | `PreferenceSingletonsLifecycleTest` (`7/7`), `TuningPreferencesDataStoreLifecycleTest` (`2/2`), `0` external ctor calls in `app/src/main/` |
| AC2: `ATrainingTrackerMap` dead collector removed + zero `MyPreferenceManager` per-call `SupervisorJob` leaks | **MET** | `ATrainingTrackerMap.kt` diff, `MyPreferenceManager.sharedAppScope`, `PreferenceSingletonsLifecycleTest` |
| AC3: JVM/Robolectric `InMemoryPreferencesDataStore` upgrade guard & hot-path invariants | **MET** | `PreferenceSingletonsLifecycleTest` (`hasRealFilesDir` + upgrade/pinning tests), `DataStoreHotPathContractTest`, `ArchitectureConformanceContractTest` (`11/11`) |
