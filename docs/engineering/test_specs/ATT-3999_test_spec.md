# Stage 2: Requirement & Test Specification - ATT-3999: [Bug] [High] Remove blocking runBlocking DataStore reads from UI and sensor hot paths

**Ticket**: [ATT-3999](https://rainerblind.atlassian.net/browse/ATT-3999)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: None (*Product Backlog / Bugfix*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.2`  
**Requirement Mapping**: `REQ-SET-079` (*Non-Blocking DataStore Architecture, Reactive In-Memory StateFlow Caching & Pure Parameter Passing for UI and Sensor Hot Paths*)  
**Test Spec ID**: `TST-SET-079`  
**Branch**: `feature/ATT-3999`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Requirement Specification (REQ-SET-079)

### 1.1 Problem Statement & Rationale
In **aTrainingTracker**, maintaining smooth 60–120 fps rendering in the live cockpit (`TrackingTabsScreen`, `SensorGridScreen`, map/telemetry charts) and ensuring low-latency responsiveness across sensor pipelines (`SpeedAndLocationDevice`, `VerticalSpeedAndSlopeDevice`, `CyclingPowerMetricsDevice`, `TrackerService`) is essential for athlete safety and user satisfaction during high-intensity training.

During the migration of configuration persistence from legacy `SharedPreferences` to Jetpack `DataStore`, synchronous bridges were introduced in `SettingsDataStoreJavaHelper.kt` to allow legacy Java drivers and synchronous callers to query user preferences. Because `DataStore` natively exposes asynchronous Kotlin `Flow`s, these helper functions relied on `runBlocking` and `runBlocking(Dispatchers.IO)`. Calling `runBlocking` synchronously parks the caller thread until coroutine scheduling, Protobuf/Preferences disk deserialization, and flow emission complete. Furthermore, `SettingsDataStore(context)` and `TuningPreferencesDataStore(context)` were instantiated ad-hoc on every query.

These synchronous bridges were subsequently called directly from high-frequency hot paths:
1. On the **Android Main (UI) Thread**: In `TrackingTabsScreen.kt`, `LaunchedEffect(filteredSensorData, ...)` executes on every incoming sensor update (up to 10 Hz) and calls `calculateZoneIndex` for HR and Power (4 to 10 `runBlocking` calls per tick). In `TrackingViewModel.kt`, `applySensorData` calls `calculateZoneColor` and `calculateZoneGaugeState` on every sensor tick (up to 12 `runBlocking` calls per tick). Combined, this amounts to **12 to 22 `runBlocking` calls per sensor tick** directly on the Compose Main thread (100–200 blocking calls per second at 10 Hz), creating severe frame drops, UI jank, and ANR hazards.
2. On the **Location Looper Thread**: `SpeedAndLocationDevice.java` invokes `SettingsDataStoreJavaHelper.getGpsAccuracyThreshold(mContext)` on every incoming GPS location fix (1 Hz).
3. On the **Sensor Handler Thread**: `VerticalSpeedAndSlopeDevice.java` invokes `SettingsDataStoreJavaHelper.getSlopeMinSpeed(mContext)` inside `calculateMetrics()` every second via a Handler runnable.
4. During **UI Chart Rendering**: `TelemetryZoneMath.kt` invokes `loadHeartRateThresholds` and `loadPowerThresholds` (4 to 6 `runBlocking` calls each) during view layout and drawing passes in `ElevationProfile.kt`, `TelemetryMetricGraph.kt`, and `MapDetailLayout.kt`.

### 1.2 Functional & Architectural Requirements
The system SHALL eliminate blocking `runBlocking` DataStore reads from all UI and sensor hot paths through reactive in-memory caching, singleton encapsulation, and pure parameter passing:

1. *Reactive In-Memory StateFlow Caching in DataStore Repositories (`TuningPreferencesDataStore.kt`, `SettingsDataStore.kt`, `REQ-SET-079.1`)*:
   - `TuningPreferencesDataStore` SHALL provide a singleton accessor `getInstance(context: Context)` and maintain an application-scoped `CoroutineScope(SupervisorJob() + Dispatchers.Default)` (`appScope`).
   - `TuningPreferencesDataStore` SHALL expose an in-memory cached `StateFlow<TuningConfig>` eagerly collected from `tuningConfigFlow` (`stateIn(appScope, SharingStarted.Eagerly, TuningPreferencesDefaults.createDefaultTuningConfig())`).
   - `TuningPreferencesDataStore` SHALL expose a non-blocking $O(1)$ synchronous getter `getTuningConfig(): TuningConfig` reading `tuningConfigStateFlow.value`.
   - `SettingsDataStore` SHALL expose an in-memory cached state / `StateFlow` for zone threshold cutoffs (HR and Power), FTP, athlete weight, Critical Power, and $W'$, eagerly updated from `dataStore.data`.
   - `SettingsDataStore` SHALL expose non-blocking $O(1)$ synchronous getters `getZoneMaxSync(zoneType: ZoneType, zoneIndex: Int): Int`, `getFtpSync(): Int?`, `getAthleteWeightKgSync(): Float`, `getCpWattsSync(): Int?`, and `getWPrimeKjSync(): Float?`.
   - Unused dead code `SettingsDataStore.getSummary(zoneType)` calling `runBlocking` SHALL be completely removed.

2. *Zero Thread Blocking in Java Bridge (`SettingsDataStoreJavaHelper.kt`, `REQ-SET-079.2`)*:
   - All synchronous bridge query functions in `SettingsDataStoreJavaHelper` (`getZoneMax`, `getZoneDisplayOptions`, `getTuningConfig`, `getGpsAccuracyThreshold`, `getAltitudeFilterWindow`, `getSlopeMinSpeed`, `getFtp`, `isAutoPauseEnabled`, `getAutoPauseSpeedThresholdKmh`, `getAutoPauseDebounceSec`, `getAutoPauseConfig`, `getAthleteWeightKg`, `getCpWatts`, `getEffectiveCpWatts`, `getWPrimeKj`, `getEffectiveWPrimeKj`) SHALL read directly from the singleton in-memory caches of `SettingsDataStore` and `TuningPreferencesDataStore` without calling `runBlocking` or `runBlocking(Dispatchers.IO)`.
   - `SettingsDataStoreJavaHelper.setAthleteWeightKg(context, weightKg)` SHALL dispatch asynchronously onto `appScope.launch` instead of parking the caller thread via `runBlocking(Dispatchers.IO)`.
   - All public static method signatures in `SettingsDataStoreJavaHelper` SHALL remain 100% backward-compatible, ensuring that existing unit tests utilizing `mockkStatic(SettingsDataStoreJavaHelper::class)` (`TelemetryZoneMathTest`, `SpeedAndLocationDeviceDecayTest`, `TrackerServiceResilienceTest`, `TrackerServiceDeviceNameQuotingTest`) continue to pass without regression.
   - KDoc comments claiming `runBlocking(Dispatchers.IO)` prevents UI thread blocking SHALL be corrected to document thread-safe, non-blocking in-memory semantics.

3. *Pure Parameter Passing & Decoupled UI Hot Paths (`BatterySaverModel.kt`, `TrackingTabsScreen.kt`, `TrackingViewModel.kt`, `REQ-SET-079.3`)*:
   - `BatterySaverModel.kt` SHALL expose a pure function `calculateZoneIndex(value: Double, cutoffs: List<Int>): Int` that computes the 1-based zone index without receiving `Context` or accessing `SettingsDataStoreJavaHelper`.
   - The legacy overload `calculateZoneIndex(context: Context, zoneType: SettingsDataStore.ZoneType, value: Double): Int` SHALL be retained as a backward-compatible wrapper delegating to the non-blocking helper.
   - `TrackingTabsScreen.kt`'s `LaunchedEffect(filteredSensorData, ...)` SHALL pass pre-resolved or observed zone cutoffs into `calculateZoneIndex`, completely eliminating repetitive DataStore queries during live sensor frame delivery.
   - `TrackingViewModel.kt` SHALL observe zone threshold flows in `observeZoneDisplayOptions()`, maintaining in-memory cutoff lists per `ZoneType`. `calculateZoneColor` and `calculateZoneGaugeState` SHALL evaluate these in-memory cutoff lists directly, executing zero `SettingsDataStoreJavaHelper` or `runBlocking` calls on the UI thread during sensor update processing.

4. *Sensor Driver Non-Blocking Threshold Evaluation (`SpeedAndLocationDevice.java`, `VerticalSpeedAndSlopeDevice.java`, `REQ-SET-079.4`)*:
   - `SpeedAndLocationDevice.onNewLocation` SHALL read `gpsAccuracyThresholdMeters` via the non-blocking helper without thread parking or disk I/O on the location looper thread.
   - `VerticalSpeedAndSlopeDevice.calculateMetrics` SHALL read `slopeMinSpeedMps` via the non-blocking helper without thread parking on the sensor handler thread.

5. *UI Chart Rendering Non-Blocking Threshold Loading (`TelemetryZoneMath.kt`, `REQ-SET-079.5`)*:
   - `loadHeartRateThresholds` and `loadPowerThresholds` in `TelemetryZoneMath.kt` SHALL execute non-blocking $O(1)$ memory reads via `SettingsDataStoreJavaHelper.getZoneMax`, ensuring that view layout and Compose drawing passes in `ElevationProfile.kt`, `MapDetailLayout.kt`, and `TelemetryMetricGraph.kt` never park the Main thread.

6. *Worker Thread Contract Guarding (`LocationNameResolver.kt`, `REQ-SET-079.6`)*:
   - `LocationNameResolver.resolveLocationNameBlocking` SHALL be explicitly annotated with `@WorkerThread`, and its KDoc SHALL document that it executes blocking network/disk geocoding and must only be invoked from background worker threads.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Main Thread Freedom from runBlocking)**:
  * *Given* an active workout with HR and power sensors transmitting live telemetry,
  * *When* sensor values update at high frequency (1–10 Hz) in `TrackingTabsScreen` and `TrackingViewModel`,
  * *Then* zero `runBlocking` or `runBlocking(Dispatchers.IO)` calls are executed on the Android Main (UI) thread.
* **Criterion 2 (Sensor Driver Low-Latency Threshold Resolution)**:
  * *Given* active GPS and barometer sensor sampling,
  * *When* incoming location fixes arrive in `SpeedAndLocationDevice.onNewLocation` or metrics are evaluated in `VerticalSpeedAndSlopeDevice.calculateMetrics`,
  * *Then* threshold lookups are resolved via $O(1)$ in-memory access with zero thread parking on the location looper or sensor handler thread.
* **Criterion 3 (Instant Preference Mutation Reactivity)**:
  * *Given* an athlete adjusting tuning preferences (e.g. GPS accuracy rejection threshold, auto-pause debounce, slope min speed) or training zone boundaries in Settings,
  * *When* the change is persisted,
  * *Then* the in-memory `StateFlow` cache updates immediately and the updated values are evaluated on the very next telemetry tick without requiring an application restart.
* **Criterion 4 (Pure BatterySaverModel Calculation)**:
  * *Given* `BatterySaverModel.calculateZoneIndex(value: Double, cutoffs: List<Int>)`,
  * *When* evaluated across any numeric input and monotonic cutoff list,
  * *Then* it returns the correct 1-based zone index purely in-memory without `Context` or DataStore dependencies.
* **Criterion 5 (Backward Compatibility & Static Mocking Invariant)**:
  * *Given* existing unit test suites utilizing `mockkStatic(SettingsDataStoreJavaHelper::class)` (including `TelemetryZoneMathTest`),
  * *When* unit tests are executed,
  * *Then* static method mock bindings continue to function seamlessly with zero test regressions.
* **Criterion 6 (Dead Code Retirement & Worker Thread Annotation)**:
  * *Given* `SettingsDataStore.kt` and `LocationNameResolver.kt`,
  * *When* inspected,
  * *Then* `SettingsDataStore.getSummary(zoneType)` is removed, and `LocationNameResolver.resolveLocationNameBlocking` is annotated with `@WorkerThread`.

### 1.4 System Invariants
- **Zero Behavioral / Sensor Metric Regression**: GPS filtering thresholds, slope and VAM calculations, auto-pause debounce, power metrics (NP, IF, TSS, $W'$ balance), and AMOLED battery saver dimming logic must produce bit-for-bit identical results.
- **100% Clean-Room Test Suite Pass Rate**: All existing unit tests across `app/src/test` must continue to pass with zero regressions.
- **Strict Mocking Compatibility**: Static mock bindings via `mockkStatic(SettingsDataStoreJavaHelper::class)` must continue to function in existing and future test suites.
- **Thread Safety**: All in-memory caches must be thread-safe across Main, Sensor Handler, and Background IO threads via immutable data classes and volatile/atomic references.
- **Scope Bounding (ATT-1250)**: Broader `WorkoutDataMapper.kt` database and polyline optimizations are governed exclusively by dedicated ticket `ATT-4003` and are strictly out of scope.
- **Parent Ticket Human Decision Gate**: Moving `ATT-3999` to `Erledigt` is reserved exclusively for the human user.

---

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Refines and supersedes the synchronous DataStore query and bridge specifications in `REQ-SET-073` (*Advanced Sensor & Service Tuning Preferences*) and `REQ-SET-074` (*AMOLED Battery Saver*), and interacts with `REQ-UI-399.1`, `REQ-TRK-019`, `REQ-MIG-039.2`, `REQ-TRK-018`, `REQ-UI-260`, `REQ-UI-370`. Targets `SettingsDataStoreJavaHelper.kt`, `SettingsDataStore.kt`, `TuningPreferencesDataStore.kt`, `BatterySaverModel.kt`, `TrackingTabsScreen.kt`, `TrackingViewModel.kt`, `SpeedAndLocationDevice.java`, `VerticalSpeedAndSlopeDevice.java`, `TelemetryZoneMath.kt`, and `LocationNameResolver.kt`.
2. *Historical Origin & Commit Trace*: Introduced in commit `72e33747` and expanded across `a48c8a38` (ATT-1265), `f2c2409f` (ATT-1304), `f37f3b94` (ATT-3184), `559376e2` / `1e76228d` (ATT-1289), `5bd9e171` (ATT-3677), `61ccb801` (ATT-3862), `e5d9225e` (ATT-3860), and `83d9b354` (ATT-1268). Discovered during whole-app code review of `feature/ATT-3921` (`0234bfc0`), filed under `ATT-3999`.
3. *Root Reason for Existing Formulation*: Synchronous bridges with `runBlocking` and `runBlocking(Dispatchers.IO)` were introduced as an expedient bridge for legacy Java classes (`BANALService`, `TrackerService`, sensor devices) to access asynchronous DataStore `Flow`s without rewriting Java drivers. Misleading KDoc comments claimed `runBlocking(Dispatchers.IO)` avoided UI blocking. High-frequency callers in Compose UI (`LaunchedEffect`, `TrackingViewModel`) and sensor drivers subsequently invoked these bridges on hot paths.
4. *Preservation of Core Invariants*: Sensor accuracy and filtering thresholds, slope and VAM algorithms, power metrics, auto-pause debounce, and AMOLED battery saver dimming logic remain 100% mathematically and functionally identical. The public static API and return types of `SettingsDataStoreJavaHelper` remain intact, preserving static mock compatibility (`mockkStatic(SettingsDataStoreJavaHelper::class)`) for all existing test suites. Instant reactivity (settings UI changes immediately update in-memory caches) and thread safety across Main/Sensor/Background threads are strictly guaranteed.

---

## 2. Test Specification (TST-SET-079)

### Test Case 1: In-Memory Reactive Caching & Non-Blocking Synchronous Query Contract Unit Tests (`TST-SET-079.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/settings/SettingsDataStoreJavaHelperNonBlockingTest.kt`
* **Preconditions**: Robolectric environment or mocked Application Context with DataStore instances.
* **Actions**:
  1. Verify `TuningPreferencesDataStore.getInstance(context).tuningConfigStateFlow.value` updates immediately when preferences change.
  2. Verify `SettingsDataStoreJavaHelper.getZoneMax`, `getTuningConfig`, `getFtp`, `getAthleteWeightKg`, `getCpWatts`, `getWPrimeKj`, and `getAutoPauseConfig` return expected values synchronously without parking caller threads.
  3. Verify `setAthleteWeightKg` persists asynchronously without blocking the caller thread.
  4. Verify that mutating settings through `TuningPreferencesDataStore` or `SettingsDataStore` propagates immediately to the in-memory cache without application restart.
* **Expected Result**: All getters return correct, up-to-date values as $O(1)$ non-blocking memory operations; zero thread parking occurs.

### Test Case 2: Pure BatterySaverModel Zone Index Calculation Unit Tests (`TST-SET-079.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/batterysaver/BatterySaverModelPureTest.kt`
* **Preconditions**: Standard JVM unit test (no Android Context required for pure function).
* **Actions**:
  1. Test `calculateZoneIndex(value, cutoffs)` with 4-cutoff list (Heart Rate):
     - Values $\le z1 \rightarrow 1$
     - $z1 < \text{value} \le z2 \rightarrow 2$
     - $z2 < \text{value} \le z3 \rightarrow 3$
     - $z3 < \text{value} \le z4 \rightarrow 4$
     - Values $> z4 \rightarrow 5$
     - Boundary clamping: value at exact cutoff matches lower zone.
     - Negative and zero values map to Zone 1.
  2. Test `calculateZoneIndex(value, cutoffs)` with 6-cutoff list (Power):
     - Values $\le z1 \rightarrow 1$
     - Values mapping across Zones 2, 3, 4, 5, 6
     - Values $> z6 \rightarrow 7$
  3. Test backward-compatible overload `calculateZoneIndex(context, zoneType, value)` delegating to non-blocking helper.
* **Expected Result**: Returns exact expected 1-based zone index across all intervals and boundaries with 100% mathematical parity.

### Test Case 3: Hot Path UI & Sensor Thread Decoupling Architecture Contract Tests (`TST-SET-079.3`)
* **Scope**: Architectural Contract Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/settings/DataStoreHotPathContractTest.kt`
* **Preconditions**: Inspect target classes via reflection and mock verification.
* **Actions**:
  1. Verify `TrackingViewModel` computes `calculateZoneColor` and `calculateZoneGaugeState` using in-memory state without calling `runBlocking`.
  2. Verify `SpeedAndLocationDevice` and `VerticalSpeedAndSlopeDevice` access thresholds via non-blocking helper.
  3. Verify `TelemetryZoneMath.loadHeartRateThresholds` and `loadPowerThresholds` execute non-blocking memory reads.
  4. Verify `mockkStatic(SettingsDataStoreJavaHelper::class)` continues to pass in `TelemetryZoneMathTest.kt`.
  5. Verify `SettingsDataStore.kt` contains zero `runBlocking` calls and `getSummary` is removed.
  6. Verify `LocationNameResolver.resolveLocationNameBlocking` has `@WorkerThread` annotation.
* **Expected Result**: All architectural assertions pass; zero `runBlocking` on hot paths.

### Test Case 4: Clean-Room Full Suite Regression Execution (`TST-SET-079.4`)
* **Scope**: Regression Test Suite
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full 430+ class test suite with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SET-079.1` | Unit | `SettingsDataStoreJavaHelper.*`, `TuningPreferencesDataStore.getInstance` | `REQ-SET-079.1`, `REQ-SET-079.2` | Planned |
| `TST-SET-079.2` | Unit | `BatterySaverModel.calculateZoneIndex(Double, List<Int>)` | `REQ-SET-079.3` | Planned |
| `TST-SET-079.3` | Contract | `TrackingViewModel`, `SpeedAndLocationDevice`, `VerticalSpeedAndSlopeDevice`, `TelemetryZoneMath`, `LocationNameResolver` | `REQ-SET-079.1`, `REQ-SET-079.2`, `REQ-SET-079.3`, `REQ-SET-079.4`, `REQ-SET-079.5`, `REQ-SET-079.6` | Planned |
| `TST-SET-079.4` | Regression | Full Test Suite (`./gradlew testDebugUnitTest`) | `REQ-PRO-001`, `REQ-SET-079` | Planned |
