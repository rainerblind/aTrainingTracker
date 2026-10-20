# Stage 3: Implementation Plan - ATT-3999: [Bug] [High] Remove blocking runBlocking DataStore reads from UI and sensor hot paths

**Ticket**: [ATT-3999](https://rainerblind.atlassian.net/browse/ATT-3999)  
**Sub-task**: [ATT-4427](https://rainerblind.atlassian.net/browse/ATT-4427) (`[Impl-Plan]`)  
**Parent Epic**: None (*Product Backlog / Bugfix*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.2`  
**Requirement Mapping**: `REQ-SET-079` (`REQ-SET-079.1` .. `REQ-SET-079.6`)  
**Test Mapping**: `TST-SET-079` (`TST-SET-079.1` .. `TST-SET-079.4`)  
**Branch**: `feature/ATT-3999`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Problem Description & Background

In **aTrainingTracker**, maintaining smooth 60–120 fps rendering in the live cockpit (`TrackingTabsScreen`, `SensorGridScreen`, map/telemetry charts) and ensuring low-latency responsiveness across sensor pipelines (`SpeedAndLocationDevice`, `VerticalSpeedAndSlopeDevice`, `CyclingPowerMetricsDevice`, `TrackerService`) is essential for athlete safety and user satisfaction during high-intensity training.

### The Defect Mechanism
During the migration of configuration persistence from legacy `SharedPreferences` to Jetpack `DataStore`, synchronous bridges were introduced in `SettingsDataStoreJavaHelper.kt` to allow legacy Java drivers and synchronous callers to query user preferences. Because `DataStore` natively exposes asynchronous Kotlin `Flow`s, these helper functions relied on `runBlocking` and `runBlocking(Dispatchers.IO)`.

Calling `runBlocking` synchronously parks the caller thread until coroutine scheduling, Protobuf/Preferences disk deserialization, and flow emission complete. Furthermore, `SettingsDataStore(context)` and `TuningPreferencesDataStore(context)` were instantiated ad-hoc on every query.

These synchronous bridges were subsequently called directly from high-frequency hot paths:
1. On the **Android Main (UI) Thread**: In `TrackingTabsScreen.kt`, `LaunchedEffect(filteredSensorData, ...)` executes on every incoming sensor update (up to 10 Hz) and calls `calculateZoneIndex` for HR and Power (4 to 10 `runBlocking` calls per tick). In `TrackingViewModel.kt`, `applySensorData` calls `calculateZoneColor` and `calculateZoneGaugeState` on every sensor tick (up to 12 `runBlocking` calls per tick). Combined, this amounts to **12 to 22 `runBlocking` calls per sensor tick** directly on the Compose Main thread (100–200 blocking calls per second at 10 Hz), creating severe frame drops, UI jank, and ANR hazards.
2. On the **Location Looper Thread**: `SpeedAndLocationDevice.java` invokes `SettingsDataStoreJavaHelper.getGpsAccuracyThreshold(mContext)` on every incoming GPS location fix (1 Hz).
3. On the **Sensor Handler Thread**: `VerticalSpeedAndSlopeDevice.java` invokes `SettingsDataStoreJavaHelper.getSlopeMinSpeed(mContext)` inside `calculateMetrics()` every second via a Handler runnable.
4. During **UI Chart Rendering**: `TelemetryZoneMath.kt` invokes `loadHeartRateThresholds` and `loadPowerThresholds` (4 to 6 `runBlocking` calls each) during view layout and drawing passes in `ElevationProfile.kt`, `TelemetryMetricGraph.kt`, and `MapDetailLayout.kt`.

This implementation plan details the SWE.2 architecture and step-by-step construction to eliminate all blocking `runBlocking` DataStore reads through reactive in-memory caching, singleton encapsulation, and pure parameter passing without breaking system invariants or existing test contracts.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-SET-079` (*Non-Blocking DataStore Architecture, Reactive In-Memory StateFlow Caching & Pure Parameter Passing for UI and Sensor Hot Paths*)
  * `REQ-SET-079.1`: Reactive in-memory `StateFlow` caching in DataStore repositories (`TuningPreferencesDataStore.kt`, `SettingsDataStore.kt`) with singleton encapsulation and non-blocking $O(1)$ synchronous getters; removal of dead code `getSummary(zoneType)`.
  * `REQ-SET-079.2`: Zero thread blocking in Java bridge (`SettingsDataStoreJavaHelper.kt`), non-blocking $O(1)$ memory access, asynchronous `setAthleteWeightKg` dispatch, KDoc correction, and strict preservation of public static method signatures for `mockkStatic` test compatibility.
  * `REQ-SET-079.3`: Pure parameter passing in `BatterySaverModel.calculateZoneIndex(Double, List<Int>)`, decoupling `TrackingTabsScreen.kt` and `TrackingViewModel.kt` from DataStore queries during live sensor updates.
  * `REQ-SET-079.4`: Sensor driver low-latency threshold resolution in `SpeedAndLocationDevice.java` and `VerticalSpeedAndSlopeDevice.java` via non-blocking memory access.
  * `REQ-SET-079.5`: UI chart rendering non-blocking threshold loading in `TelemetryZoneMath.kt` (`loadHeartRateThresholds`, `loadPowerThresholds`).
  * `REQ-SET-079.6`: Worker thread contract guarding in `LocationNameResolver.kt` via `@WorkerThread` annotation and KDoc warning.
* **Test Mapping**: `TST-SET-079` (*Non-Blocking DataStore Architecture, Reactive In-Memory StateFlow Caching, Pure Parameter Passing & Hot Path Decoupling Verification Suite*)
  * `[TST-SET-079.1]`: In-Memory Reactive Caching & Non-Blocking Synchronous Query Contract Unit Tests in `SettingsDataStoreJavaHelperNonBlockingTest.kt`.
  * `[TST-SET-079.2]`: Pure BatterySaverModel Zone Index Calculation Unit Tests in `BatterySaverModelPureTest.kt`.
  * `[TST-SET-079.3]`: Hot Path UI & Sensor Thread Decoupling Architecture Contract Tests in `DataStoreHotPathContractTest.kt`.
  * `[TST-SET-079.4]`: Clean-Room Full Suite Regression Execution (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Behavioral or Sensor Metric Regression**: GPS filtering thresholds, slope and VAM calculations, auto-pause debounce, power metrics (NP, IF, TSS, $W'$ balance), and AMOLED battery saver dimming logic must produce bit-for-bit identical results.
2. **100% Clean-Room Test Suite Pass Rate**: All existing unit tests across `app/src/test` (including `TelemetryZoneMathTest`, `SpeedAndLocationDeviceDecayTest`, `TrackerServiceResilienceTest`, and `TrackerServiceDeviceNameQuotingTest`) must continue to pass with zero regressions.
3. **Strict Mocking Compatibility**: Static mock bindings via `mockkStatic(SettingsDataStoreJavaHelper::class)` must continue to function seamlessly in existing and future test suites.
4. **Instant Reactivity**: Any preference change made by the user in Settings screens must propagate immediately to the in-memory `StateFlow` and be evaluated on the very next telemetry tick.
5. **Thread Safety**: All in-memory caches must be thread-safe across Main, Sensor Handler, and Background IO threads via immutable data classes, atomic references, and supervisor coroutine scopes.
6. **Scope Bounding (ATT-1250)**: Broader `WorkoutDataMapper.kt` database and polyline optimizations are governed exclusively by dedicated ticket `ATT-4003` and are strictly out of scope.
7. **Parent Ticket Human Decision Gate**: Moving `ATT-3999` to `Erledigt` is reserved exclusively for the human user.

---

## 4. Proposed Architectural Changes (SWE.2)

```mermaid
flowchart TD
    subgraph DataStore Layer [Tier 1: Reactive In-Memory StateFlow Caching]
        DS[Jetpack DataStore Disk / Preferences] -->|Flow emissions| EagerScope[Application Supervisor CoroutineScope]
        EagerScope -->|stateIn Eagerly| TuningStateFlow["TuningPreferencesDataStore.tuningConfigStateFlow (StateFlow)"]
        EagerScope -->|stateIn Eagerly| SettingsStateFlow["SettingsDataStore.snapshotStateFlow (StateFlow)"]
    end

    subgraph Bridge Layer [Synchronous In-Memory Bridge]
        TuningStateFlow -->|O(1) .value read| JavaHelper[SettingsDataStoreJavaHelper]
        SettingsStateFlow -->|O(1) .value read| JavaHelper
    end

    subgraph Sensor & Service Drivers [Java Hot Paths - Zero Thread Blocking]
        JavaHelper -->|O(1) getGpsAccuracyThreshold| SpeedDevice[SpeedAndLocationDevice.onNewLocation]
        JavaHelper -->|O(1) getSlopeMinSpeed| SlopeDevice[VerticalSpeedAndSlopeDevice.calculateMetrics]
        JavaHelper -->|O(1) getFtp / getWeight| PowerDevice[CyclingPowerMetricsDevice]
        JavaHelper -->|O(1) getAutoPauseConfig| TrackerService[TrackerService]
    end

    subgraph UI Hot Paths [Tier 2: Pure Functions & Reactive ViewModels]
        SettingsStateFlow -->|Flow observation| TrackingVM[TrackingViewModel]
        TrackingVM -->|In-memory cached thresholds| ZoneColor[calculateZoneColor - Pure]
        TrackingVM -->|In-memory cached thresholds| ZoneGauge[calculateZoneGaugeState - Pure]
        TrackingVM -->|Pass cutoffs List<Int>| Screen[TrackingTabsScreen LaunchedEffect]
        Screen -->|Pure function call| PureZoneIndex[calculateZoneIndex(value, cutoffs) - Pure]
    end
```

### Component 1: `TuningPreferencesDataStore.kt`
- Introduce a thread-safe singleton pattern: `getInstance(context: Context)`.
- Maintain an application-scoped `CoroutineScope(SupervisorJob() + Dispatchers.Default)` (`appScope`).
- Expose `val tuningConfigStateFlow: StateFlow<TuningConfig> = tuningConfigFlow.stateIn(appScope, SharingStarted.Eagerly, TuningPreferencesDefaults.createDefaultTuningConfig())`.
- Expose a non-blocking $O(1)$ synchronous getter: `fun getTuningConfig(): TuningConfig = tuningConfigStateFlow.value`.

### Component 2: `SettingsDataStore.kt`
- Enhance the existing singleton `SettingsDataStore.getInstance(context)`.
- Introduce an application-scoped supervisor coroutine scope: `private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)`.
- Introduce an immutable in-memory snapshot data class `SettingsSnapshot` holding:
  - `hrRunZones: List<Int>` (Z1..Z4 max cutoffs)
  - `hrBikeZones: List<Int>` (Z1..Z4 max cutoffs)
  - `pwrBikeZones: List<Int>` (Z1..Z6 max cutoffs)
  - `ftp: Int?`
  - `athleteWeightKg: Float`
  - `cpWatts: Int?`
  - `wPrimeKj: Float?`
  - `hrRunDisplayOptions: ZoneDisplayOptions`
  - `hrBikeDisplayOptions: ZoneDisplayOptions`
  - `pwrBikeDisplayOptions: ZoneDisplayOptions`
- Eagerly update this snapshot from `dataStore.data` using `stateIn(appScope, SharingStarted.Eagerly, SettingsSnapshot.DEFAULT)`.
- Expose non-blocking synchronous accessors:
  - `fun getZoneMaxSync(zoneType: ZoneType, zoneIndex: Int): Int`
  - `fun getZoneDisplayOptionsSync(zoneType: ZoneType): ZoneDisplayOptions`
  - `fun getFtpSync(): Int?`
  - `fun getAthleteWeightKgSync(): Float`
  - `fun getCpWattsSync(): Int?`
  - `fun getWPrimeKjSync(): Float?`
  - `fun launchSetAthleteWeightKg(weightKg: Float)` (asynchronous `appScope.launch`)
- Remove unused dead method `fun getSummary(zoneType: ZoneType): String = runBlocking { ... }`.

### Component 3: `SettingsDataStoreJavaHelper.kt`
- Refactor all public static query methods (`getZoneMax`, `getZoneDisplayOptions`, `getTuningConfig`, `getGpsAccuracyThreshold`, `getAltitudeFilterWindow`, `getSlopeMinSpeed`, `getFtp`, `isAutoPauseEnabled`, `getAutoPauseSpeedThresholdKmh`, `getAutoPauseDebounceSec`, `getAutoPauseConfig`, `getAthleteWeightKg`, `getCpWatts`, `getEffectiveCpWatts`, `getWPrimeKj`, `getEffectiveWPrimeKj`):
  - Delegate directly to `SettingsDataStore.getInstance(context)` and `TuningPreferencesDataStore.getInstance(context)` synchronous getters.
  - Eliminate all `runBlocking` and `runBlocking(Dispatchers.IO)` calls.
- Refactor `setAthleteWeightKg(context, weightKg)`:
  - Delegate to `SettingsDataStore.getInstance(context).launchSetAthleteWeightKg(weightKg)`.
- Update KDoc to remove false claims regarding `Dispatchers.IO` and document thread-safe in-memory operation.
- Retain all public method signatures, preserving `mockkStatic(SettingsDataStoreJavaHelper::class)` compatibility.

### Component 4: `BatterySaverModel.kt` & `TrackingTabsScreen.kt`
- In `BatterySaverModel.kt`, add a pure function:
  ```kotlin
  fun calculateZoneIndex(value: Double, cutoffs: List<Int>): Int {
      for (i in cutoffs.indices) {
          if (value <= cutoffs[i]) return i + 1
      }
      return cutoffs.size + 1
  }
  ```
- Retain `calculateZoneIndex(context: Context, zoneType: SettingsDataStore.ZoneType, value: Double): Int` as a backward-compatible wrapper retrieving cutoffs from non-blocking `SettingsDataStoreJavaHelper`.
- In `TrackingTabsScreen.kt`, pass pre-resolved or observed cutoffs into `calculateZoneIndex(hrValue, hrCutoffs)` and `calculateZoneIndex(powerValue, pwrCutoffs)` inside `LaunchedEffect(filteredSensorData, ...)`.

### Component 5: `TrackingViewModel.kt`
- Maintain in-memory zone cutoff lists per `ZoneType` in `TrackingViewModel`.
- In `observeZoneDisplayOptions()`, observe zone threshold flows or snapshots alongside display options.
- In `calculateZoneColor` and `calculateZoneGaugeState`, evaluate the in-memory cutoff lists directly without calling `SettingsDataStoreJavaHelper`.

### Component 6: Sensor Drivers & Telemetry Charts
- `SpeedAndLocationDevice.java:268` and `VerticalSpeedAndSlopeDevice.java:244` continue calling `SettingsDataStoreJavaHelper` methods, which now return immediately in $O(1)$ from memory.
- `TelemetryZoneMath.kt:228-254` continues calling `SettingsDataStoreJavaHelper.getZoneMax`, which now returns immediately in $O(1)$ from memory, preserving `TelemetryZoneMathTest.kt` mock contracts.

### Component 7: `LocationNameResolver.kt`
- Add `@WorkerThread` annotation to `resolveLocationNameBlocking`.
- Update KDoc documenting network/disk reverse geocoding behavior and warning against UI thread invocation.

### UI Consistency (Rule 23)
* **Reference screen / component**: Live Cockpit (`TrackingTabsScreen.kt`, `SensorGridScreen.kt`).
* **Reused components**: Existing composable cards, tiles, and themes. No new UI controls or dialogs are introduced; this ticket is an internal architectural optimization for frame rate and responsiveness.
* **Theme tokens**: Preserves all existing `TTColor` zone accent tokens (`TTColor.Zone1` .. `Zone7`), `MaterialTheme.colorScheme` tokens, and typography tokens.
* **New one-off styles & justification**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Eager In-Memory Reactive Caching in `TuningPreferencesDataStore.kt`
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStore.kt`
* **Changes**:
  1. Add thread-safe singleton `getInstance(context: Context)`.
  2. Instantiate application-scoped supervisor scope `appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)`.
  3. Expose `tuningConfigStateFlow: StateFlow<TuningConfig>` eagerly collected from `tuningConfigFlow`.
  4. Expose non-blocking synchronous getter `fun getTuningConfig(): TuningConfig = tuningConfigStateFlow.value`.
* **Verification**: Verify compilation and existing tuning preference tests pass.

### Step 2: Eager In-Memory Reactive Caching in `SettingsDataStore.kt`
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/settings/SettingsDataStore.kt`
* **Changes**:
  1. Define immutable data class `SettingsSnapshot` capturing all zone thresholds, FTP, weight, CP, $W'$, and zone display options.
  2. Add supervisor coroutine scope `appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)`.
  3. Eagerly collect `dataStore.data` into `snapshotStateFlow: StateFlow<SettingsSnapshot>`.
  4. Expose non-blocking synchronous accessors: `getZoneMaxSync`, `getZoneDisplayOptionsSync`, `getFtpSync`, `getAthleteWeightKgSync`, `getCpWattsSync`, `getWPrimeKjSync`, and `launchSetAthleteWeightKg`.
  5. Delete unused dead method `getSummary(zoneType: ZoneType)`.
* **Verification**: Verify compilation and existing settings data store tests pass.

### Step 3: Zero-Blocking Synchronous Java Bridge in `SettingsDataStoreJavaHelper.kt`
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/settings/SettingsDataStoreJavaHelper.kt`
* **Changes**:
  1. Remove all `runBlocking` and `runBlocking(Dispatchers.IO)` invocations.
  2. Implement all query methods as direct non-blocking delegations to `SettingsDataStore.getInstance(context)` and `TuningPreferencesDataStore.getInstance(context)`.
  3. Implement `setAthleteWeightKg` via `launchSetAthleteWeightKg`.
  4. Correct misleading KDoc comments to document thread-safe in-memory semantics.
  5. Ensure all public static method signatures and return types remain identical.
* **Verification**:
  ```bash
  ./gradlew testDebugUnitTest --tests "*TelemetryZoneMathTest*" --tests "*SpeedAndLocationDeviceDecayTest*"
  ```

### Step 4: Pure Function in `BatterySaverModel.kt` & Decoupling `TrackingTabsScreen.kt`
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/batterysaver/BatterySaverModel.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt`
* **Changes**:
  1. In `BatterySaverModel.kt`, add pure `calculateZoneIndex(value: Double, cutoffs: List<Int>): Int`.
  2. Keep legacy `calculateZoneIndex(context, zoneType, value)` delegating to non-blocking helper.
  3. In `TrackingTabsScreen.kt`, pass pre-resolved or observed cutoffs into `calculateZoneIndex` inside `LaunchedEffect(filteredSensorData, ...)`.
* **Verification**:
  ```bash
  ./gradlew testDebugUnitTest --tests "*BatterySaver*"
  ```

### Step 5: Reactive Threshold Caching in `TrackingViewModel.kt`
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/TrackingViewModel.kt`
* **Changes**:
  1. Maintain in-memory cutoff threshold map `zoneCutoffsMap`.
  2. Observe zone cutoff flows in `observeZoneDisplayOptions()`.
  3. In `calculateZoneColor` and `calculateZoneGaugeState`, evaluate in-memory cutoffs without calling `SettingsDataStoreJavaHelper`.
* **Verification**:
  ```bash
  ./gradlew testDebugUnitTest --tests "*TrackingViewModelTest*"
  ```

### Step 6: Worker Thread Annotation in `LocationNameResolver.kt`
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/location/LocationNameResolver.kt`
* **Changes**:
  1. Add `@androidx.annotation.WorkerThread` to `resolveLocationNameBlocking`.
  2. Document blocking network/geocoding behavior and prohibit UI thread invocation in KDoc.
* **Verification**: Verify compilation.

### Step 7: Author Unit & Architectural Contract Tests (`TST-SET-079`)
* **Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/settings/SettingsDataStoreJavaHelperNonBlockingTest.kt` (`TST-SET-079.1`)
  - `app/src/test/java/com/atrainingtracker/trainingtracker/batterysaver/BatterySaverModelPureTest.kt` (`TST-SET-079.2`)
  - `app/src/test/java/com/atrainingtracker/trainingtracker/settings/DataStoreHotPathContractTest.kt` (`TST-SET-079.3`)
* **Changes**:
  1. Implement `SettingsDataStoreJavaHelperNonBlockingTest.kt` testing synchronous in-memory retrieval, instant reactivity, and non-blocking weight save.
  2. Implement `BatterySaverModelPureTest.kt` testing pure zone index calculations across HR and Power cutoffs, boundaries, and edge cases.
  3. Implement `DataStoreHotPathContractTest.kt` asserting zero `runBlocking` in `SettingsDataStoreJavaHelper.kt`, zero `runBlocking` in `SettingsDataStore.kt`, removal of `getSummary`, presence of `@WorkerThread` on `resolveLocationNameBlocking`, and preservation of static mocking.
* **Verification**:
  ```bash
  ./gradlew testDebugUnitTest --tests "*SettingsDataStoreJavaHelperNonBlockingTest*" --tests "*BatterySaverModelPureTest*" --tests "*DataStoreHotPathContractTest*"
  ```

### Step 8: Clean-Room Regression Verification (`TST-SET-079.4`)
* **Command**:
  ```bash
  ./gradlew testDebugUnitTest
  ```
* **Goal**: Verify 100% pass rate across the full 430+ class test suite with zero regressions.

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Targeted unit testing after each step (5–15 seconds).
  - Architectural contract verification ensuring zero `runBlocking` on hot paths.
  - Full clean-room test suite run (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback**:
  - All changes are isolated on `feature/ATT-3999`. In the event of an unresolvable defect or regression, the branch can be cleanly reset to `develop` without side effects on production or main branches.
