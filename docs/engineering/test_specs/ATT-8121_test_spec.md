# Stage 2: Requirement & Test Specification - ATT-8121: Preference/DataStore Singleton Encapsulation & Leak Elimination

**Ticket**: [ATT-8121](https://issue-tracker/ATT-8121) | **Parent Epic**: [ATT-8119](https://issue-tracker/ATT-8119) | **Tier**: `tier-a` (`2 SP`)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-22_01_Früh` (ID `471`) | **Branch**: `feature/ATT-8121`  
**Requirement Mapping**: `REQ-SET-128` (`REQ-SET-128.1`–`REQ-SET-128.4`) | **Test Spec ID**: `TST-SET-130` (`TST-SET-130.1`–`TST-SET-130.4`)  
**Author**: Lane 3 Author (`agent1`) | **Date**: 2026-11-22

---

## 1. Requirement Specification (`REQ-SET-128`)

### 1.1 Attributes
- **Priority**: High | **Type**: Non-Functional | **Verification Method**: Unit | **Localization Parity**: `N/A - 0 string changes`

### 1.2 Normative Clauses
1. **Singleton Encapsulation & `applicationContext` Normalization (`REQ-SET-128.1`)**: `SettingsDataStore`, `MyPreferenceManager`, and `ProFeatureManager` SHALL enforce `private` primary constructors (compile-time blocking direct production instantiation, matching `TuningPreferencesDataStore` per `REQ-TRK-079.1`), normalize retained context references to `context.applicationContext ?: context`, and provide thread-safe `getInstance(context)` plus `@VisibleForTesting internal fun createForTesting(...)` (with injectable `CoroutineScope`/`DataStore`) and synchronized `setInstanceForTesting(...)` test seams.
2. **Process-Wide Scope Reuse & Production Caller Migration (`REQ-SET-128.2`)**: `MyPreferenceManager` SHALL reuse a single process-wide `CoroutineScope(SupervisorJob() + Dispatchers.IO)` across calls (allocating zero orphaned `SupervisorJob` instances per call or UI re-composition). All verified production callers of `SettingsDataStore(...)` (`ZonesSettingsViewModel`, `ZonesSettingsActivity`, `TrackingViewModel`), `MyPreferenceManager(...)` (17 sites across 13 files), and `ProFeatureManager(...)` (`ATrainingTrackerApp`, `SupporterDebugSection`) SHALL obtain instances exclusively via `getInstance(...)`.
3. **Dead Collector Removal in `ATrainingTrackerMap` (`REQ-SET-128.3`)**: `ATrainingTrackerMap.kt` SHALL remove the unused `tuningDataStore` and `tuningConfig` (`tuningConfigFlow.collectAsState`) local declarations and their unused imports so rendering a map does not mount an unread DataStore collector.
4. **JVM/Robolectric Singleton Isolation & Hot-Path Invariants (`REQ-SET-128.4`)**: `SettingsDataStore.getInstance(context)` and `MyPreferenceManager.getInstance(context)` MUST preserve `InMemoryPreferencesDataStore` fallback for unmocked JVM tests (`Rule 66` / §5.5) while automatically upgrading an auto-created fallback singleton (when `!isTestOverride`) if a real `filesDir` context (Android/Robolectric) subsequently requests `getInstance(context)`. `SettingsDataStore.kt` MUST introduce zero `runBlocking` calls (`REQ-SET-079`), and `TuningPreferencesDataStore.kt` MUST remain untouched and `<= 2,410` LoC.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC1 (`TST-SET-130.1`)**: *Given* `SettingsDataStore`, `MyPreferenceManager`, and `ProFeatureManager`, *When* inspected reflectively and called via `getInstance(activity)` followed by `getInstance(application)`, *Then* zero non-synthetic public constructors exist, `getInstance` returns identical singleton references, and any stored `Context` is `applicationContext` (never `Activity`).
* **AC2 (`TST-SET-130.2`)**: *Given* `MyPreferenceManager`, *When* `getInstance` or `createForTesting` is invoked repeatedly, *Then* all instances share the same process-wide default `CoroutineScope`/`SupervisorJob` without allocating orphaned scopes, and filter-clear/read-write DataStore operations function identically.
* **AC3 (`TST-SET-130.3`)**: *Given* a plain-JVM unmocked `Application` followed by a real-`filesDir` `Context` calling `getInstance` (without `setInstanceForTesting`), *When* `SettingsDataStore.getInstance` or `MyPreferenceManager.getInstance` resolves, *Then* the auto-created `InMemoryPreferencesDataStore` fallback upgrades cleanly to the real backing `DataStore`, while explicit `setInstanceForTesting` overrides remain pinned until reset.
* **AC4 (`TST-SET-130.4`)**: *Given* `ATrainingTrackerMap.kt`, *When* audited alongside the migrated production & test suites, *Then* `ATrainingTrackerMap` has zero `TuningPreferencesDataStore` collector references and all targeted unit/contract suites pass 100%.

---

## 2. Test Specification (`TST-SET-130`)

### Test Case 1: Singleton Encapsulation, Non-Public Constructors & `applicationContext` Normalization (`TST-SET-130.1`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/settings/PreferenceSingletonsLifecycleTest.kt` (new)
* **Procedure**: Reflectively assert zero non-synthetic `public` constructors on `SettingsDataStore`, `MyPreferenceManager`, and `ProFeatureManager` (alongside existing `TuningPreferencesDataStoreLifecycleTest`); invoke `getInstance(mockActivity)` and `getInstance(mockApplication)` and assert `assertSame`, non-`Activity` stored context, and single `appScope`/`coroutineScope` reference.

### Test Case 2: `MyPreferenceManager` Shared Scope Reuse & DataStore Isolation (`TST-SET-130.2`)
* **Target Files**: `PreferenceSingletonsLifecycleTest.kt`, `WorkoutClustersSelectableMarkersTest.kt`, `TelemetryChartStackVisibilityTest.kt`
* **Procedure**: Create multiple `MyPreferenceManager` references via `getInstance` and `createForTesting` with default scope; assert identical `appScope` / `SupervisorJob` reference (zero per-call scope allocation), plus verify injected `testScope` cancellation in `SettingsDataStore.createForTesting` and `MyPreferenceManager.createForTesting`.

### Test Case 3: `InMemoryPreferencesDataStore` Fallback Upgrade & `setInstanceForTesting` Pinning (`TST-SET-130.3`)
* **Target File**: `PreferenceSingletonsLifecycleTest.kt`
* **Procedure**: Call `getInstance(mockUnmockedApp)` (resolving `InMemoryPreferencesDataStore`), assert repeated MockK `getInstance(mockActivity)` returns the same instance, then call `getInstance(realFilesDirContext)` and assert upgrade to backing `DataStore`; verify `setInstanceForTesting(custom)` stays pinned across real-`filesDir` calls until `setInstanceForTesting(null)`.

### Test Case 4: Migrated Caller & Contract Regression Suite (`TST-SET-130.4`)
* **Procedure**: Verify `ATrainingTrackerMap.kt` has zero dead `TuningPreferencesDataStore` collector, and execute targeted Gradle unit/contract tests across all migrated test suites plus `TuningPreferencesDataStoreLifecycleTest`, `DataStoreHotPathContractTest`, and `ArchitectureConformanceContractTest`.

---

## 3. Traceability Matrix

| Requirement ID | Normative Scope | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-SET-128.1` | Private constructors, `applicationContext` normalization, `createForTesting` | `TST-SET-130.1` | Unit (Reflection + Lifecycle) |
| `REQ-SET-128.2` | `MyPreferenceManager` shared `SupervisorJob` scope + all 24 prod caller migrations | `TST-SET-130.2`, `TST-SET-130.4` | Unit + Migrated Suites |
| `REQ-SET-128.3` | Remove dead `tuningConfig` collector & unused imports in `ATrainingTrackerMap.kt` | `TST-SET-130.4` | Unit + Compile/Review |
| `REQ-SET-128.4` | Fallback-to-real-`filesDir` singleton upgrade guard & hot-path/LoC invariants | `TST-SET-130.3`, `TST-SET-130.4` | Unit + Contract Tests |
