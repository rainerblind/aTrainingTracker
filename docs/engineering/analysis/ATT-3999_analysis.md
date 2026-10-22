# Stage 1 Analysis: ATT-3999 - [Bug] [High] Remove blocking runBlocking DataStore reads from UI and sensor hot paths

**Ticket**: [ATT-3999](https://rainerblind.atlassian.net/browse/ATT-3999)  
**Sub-task**: [ATT-4425](https://rainerblind.atlassian.net/browse/ATT-4425) (`[Analysis]`)  
**Parent Epic**: None (*Product Backlog / Bugfix*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.2`  
**Branch**: `feature/ATT-3999`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Problem Statement & Motivation

In **aTrainingTracker**, maintaining smooth 60–120 fps rendering in the live cockpit (`TrackingTabsScreen`, `SensorGridScreen`, map/telemetry charts) and ensuring low-latency responsiveness across sensor pipelines (`SpeedAndLocationDevice`, `VerticalSpeedAndSlopeDevice`, `CyclingPowerMetricsDevice`, `TrackerService`) is essential for athlete safety and user satisfaction during high-intensity training.

### The Bug Phenomenon
During the migration of configuration persistence from legacy Android `SharedPreferences` to Jetpack `DataStore`, synchronous bridges were introduced in `SettingsDataStoreJavaHelper.kt` to allow legacy Java drivers and synchronous callers to query user preferences. Because Jetpack `DataStore` exposes asynchronous Kotlin `Flow`s, these helper functions relied on `runBlocking` and `runBlocking(Dispatchers.IO)`.

However, the implementation contains critical architectural defects:
1. **Uncached Object Instantiation on Every Query**: `SettingsDataStoreJavaHelper.kt` instantiates a new `SettingsDataStore(context)` or `TuningPreferencesDataStore(context)` on every single query instead of reusing a singleton instance or accessing in-memory state.
2. **Synchronous Thread Parking**: Calling `runBlocking` synchronously parks the caller thread until the coroutine completes. Even with `runBlocking(Dispatchers.IO)`, the caller thread is blocked while waiting for coroutine dispatch, file lock acquisition, disk read, Protobuf/Preferences deserialization, and flow emission on `Dispatchers.IO`. The KDoc comment claiming that `runBlocking(Dispatchers.IO)` *"Executes safely on Dispatchers.IO to prevent UI thread blocking"* is fundamentally incorrect.
3. **Pervasive Invocations on High-Frequency Hot Paths**: These synchronous bridges were subsequently called directly from high-frequency hot paths:
   - On the Android **Main (UI) Thread**: On every sensor tick (up to 10 Hz), `TrackingTabsScreen.kt` invokes `calculateZoneIndex` for Heart Rate and Power (4 to 10 `runBlocking` calls per tick), while `TrackingViewModel.kt` invokes `calculateZoneColor` and `calculateZoneGaugeState` (up to 12 `runBlocking` calls per tick). Combined, this amounts to **12 to 22 `runBlocking` calls per sensor tick** directly on the Compose Main thread (100–200 blocking calls per second at 10 Hz)!
   - On the **Location Looper Thread**: `SpeedAndLocationDevice.java` invokes `SettingsDataStoreJavaHelper.getGpsAccuracyThreshold(mContext)` on every incoming GPS location fix (1 Hz).
   - On the **Sensor Handler Thread**: `VerticalSpeedAndSlopeDevice.java` invokes `SettingsDataStoreJavaHelper.getSlopeMinSpeed(mContext)` inside `calculateMetrics()` every second via a Handler runnable.
   - During **UI Chart Rendering**: `TelemetryZoneMath.kt` invokes `loadHeartRateThresholds` and `loadPowerThresholds` (4 to 6 `runBlocking` calls each) during view layout and drawing passes in `ElevationProfile.kt`, `TelemetryMetricGraph.kt`, and `MapDetailLayout.kt`.

### Observable Failure Scenarios & User Impact
- **Cockpit Jank & Frame Stutter**: Rapid sensor updates (e.g. ANT+/BLE HR strap transmitting at 4 Hz, power meter at 1–4 Hz, speed/cadence at 2 Hz) trigger cascades of `runBlocking` calls on the Main thread. Each call forces coroutine scheduling and disk/memory lock synchronization, causing dropped frames, jank, and sluggish touch response on cockpit tiles and pager swipe gestures.
- **ANR (Application Not Responding) Hazard**: On slow flash storage, under heavy I/O contention (e.g. database transactions or trackpoint logging in `TrackerService`), or on the first read after process cold-start, `runBlocking` stalls the Main thread for tens to hundreds of milliseconds, creating a severe ANR risk during active recordings.
- **Sensor Telemetry Delivery Stalls**: Parking the Location looper and BANALService Handler threads behind `runBlocking(Dispatchers.IO)` introduces latency jitter into GPS filtering, VAM/slope calculation, and real-time audio cues.

---

## 2. Root Cause Analysis (Forensic Investigation)

A comprehensive codebase audit confirms the exact call sites and mechanisms producing blocking DataStore reads. A global grep revealed that **all `runBlocking` invocations across the entire `app/src/main` production source tree** are concentrated in four files: `SettingsDataStoreJavaHelper.kt`, `SettingsDataStore.kt`, `BatterySaverModel.kt`, and `LocationNameResolver.kt`.

```
app/src/main/.../settings/SettingsDataStoreJavaHelper.kt:48, 68, 80, 120, 187, 202, 217, 241
app/src/main/.../settings/SettingsDataStore.kt:460
app/src/main/.../batterysaver/BatterySaverModel.kt:53-59 (via JavaHelper)
app/src/main/.../location/LocationNameResolver.kt:129
```

### 2.1 Architectural Flaw in `SettingsDataStoreJavaHelper.kt`
Lines 45–85, 120, 187, 202, 217, and 241 contain eight distinct `runBlocking` blocks:
- `getZoneMax(context, zoneType, zoneIndex)`:
  ```kotlin
  @JvmStatic
  fun getZoneMax(context: Context, zoneType: SettingsDataStore.ZoneType, zoneIndex: Int): Int {
      val dataStore = SettingsDataStore(context)
      return runBlocking {
          when (zoneIndex) {
              1 -> dataStore.getZone1MaxFlow(zoneType).first()
              ...
          }
      }
  }
  ```
  *Defects*: Instantiates a new `SettingsDataStore(context)` instead of using `SettingsDataStore.getInstance(context)`; executes `runBlocking` on the calling thread's dispatcher.
- `getZoneDisplayOptions(context, zoneType)`:
  ```kotlin
  @JvmStatic
  fun getZoneDisplayOptions(context: Context, zoneType: SettingsDataStore.ZoneType): ZoneDisplayOptions {
      val dataStore = SettingsDataStore(context)
      return runBlocking(Dispatchers.IO) {
          dataStore.getZoneDisplayOptionsFlow(zoneType).first()
      }
  }
  ```
  *Defects*: The KDoc claims *"Executes safely on Dispatchers.IO to prevent UI thread blocking"*. In reality, `runBlocking(Dispatchers.IO)` blocks the calling thread (e.g. Main thread) until the coroutine running on `Dispatchers.IO` yields a result.
- `getTuningConfig(context)`:
  ```kotlin
  @JvmStatic
  fun getTuningConfig(context: Context): TuningConfig {
      return try {
          val tuningDataStore = TuningPreferencesDataStore(context)
          runBlocking(Dispatchers.IO) {
              tuningDataStore.tuningConfigFlow.first()
          }
      } catch (t: Throwable) {
          TuningConfig()
      }
  }
  ```
  *Defects*: Instantiates a new `TuningPreferencesDataStore(context)` on every invocation; blocks caller synchronously. This method is the underlying bottleneck for:
  - `getGpsAccuracyThreshold(context)`
  - `getAltitudeFilterWindow(context)`
  - `getSlopeMinSpeed(context)`
  - `isAutoPauseEnabled(context, sportType)`
  - `getAutoPauseSpeedThresholdKmh(context)`
  - `getAutoPauseDebounceSec(context)`
  - `getAutoPauseConfig(context)`
- `getFtp`, `getAthleteWeightKg`, `getCpWatts`, `getWPrimeKj`:
  Each uses `runBlocking(Dispatchers.IO) { dataStore.xxxFlow.first() }`.
- `setAthleteWeightKg`:
  Executes `runBlocking(Dispatchers.IO) { dataStore.setAthleteWeightKg(weightKg) }`, turning a preference write into a synchronous blocking operation.

### 2.2 Hot-Path Invocations in Jetpack Compose (`TrackingTabsScreen.kt` & `BatterySaverModel.kt`)
In `TrackingTabsScreen.kt`, lines 298–330:
```kotlin
val filteredSensorData by trackingTabsViewModel.allFilteredSensorData.collectAsState()
val activityType by trackingTabsViewModel.activityType.collectAsState()
...
LaunchedEffect(filteredSensorData, activityType, brightnessMode, tuningConfig) {
    if (brightnessMode != DisplayBrightnessMode.AUTO) return@LaunchedEffect
    ...
    val hrZone = if (hrValue != null && hrValue > 0) {
        val zoneType = if (isCycling) SettingsDataStore.ZoneType.HR_BIKE else SettingsDataStore.ZoneType.HR_RUN
        calculateZoneIndex(context, zoneType, hrValue)
    } else null

    val powerZone = if (isCycling && powerValue != null && powerValue > 0) {
        calculateZoneIndex(context, SettingsDataStore.ZoneType.PWR_BIKE, powerValue)
    } else null
    ...
}
```
Every time `filteredSensorData` updates (1–10 Hz during active tracking), `LaunchedEffect` executes on the Compose Main thread and invokes `calculateZoneIndex`.

Inspecting `BatterySaverModel.kt`, lines 52–77:
```kotlin
fun calculateZoneIndex(context: Context, zoneType: SettingsDataStore.ZoneType, value: Double): Int {
    val z1Max = SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 1)
    val z2Max = SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 2)
    val z3Max = SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 3)
    val z4Max = SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 4)
    if (zoneType == SettingsDataStore.ZoneType.PWR_BIKE) {
        val z5Max = SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 5)
        val z6Max = SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, 6)
        ...
    }
    ...
}
```
`calculateZoneIndex` sequentially calls `SettingsDataStoreJavaHelper.getZoneMax` 4 times for Heart Rate and 6 times for Power. For a workout with HR and Power sensors, that constitutes **10 blocking `runBlocking` calls per sensor frame** on the Main thread!

### 2.3 Hot-Path Invocations in `TrackingViewModel.kt`
In `TrackingViewModel.kt`, whenever sensor data updates via `applySensorData` (lines 606–610):
```kotlin
val newZoneColor = calculateZoneColor(sensorData, activityType)
val zoneType = getZoneType(sensorData.sensorType, activityType.sportType)
val displayOptions = zoneType?.let { zoneDisplayOptionsMap[it] } ?: ZoneDisplayOptions()
val numericValue = (sensorData.value as? Number)?.toDouble()
val newZoneGaugeState = calculateZoneGaugeState(zoneType, displayOptions, numericValue)
```
Inspecting `calculateZoneColor` (lines 667–675):
```kotlin
val z1Max = SettingsDataStoreJavaHelper.getZoneMax(application, zoneType, 1)
val z2Max = SettingsDataStoreJavaHelper.getZoneMax(application, zoneType, 2)
val z3Max = SettingsDataStoreJavaHelper.getZoneMax(application, zoneType, 3)
val z4Max = SettingsDataStoreJavaHelper.getZoneMax(application, zoneType, 4)
if (zoneType == SettingsDataStore.ZoneType.PWR_BIKE) {
    val z5Max = SettingsDataStoreJavaHelper.getZoneMax(application, zoneType, 5)
    val z6Max = SettingsDataStoreJavaHelper.getZoneMax(application, zoneType, 6)
    ...
}
```
And inspecting `calculateZoneGaugeState` (lines 705–712):
```kotlin
val z1Max = SettingsDataStoreJavaHelper.getZoneMax(application, zoneType, 1)
val z2Max = SettingsDataStoreJavaHelper.getZoneMax(application, zoneType, 2)
val z3Max = SettingsDataStoreJavaHelper.getZoneMax(application, zoneType, 3)
val z4Max = SettingsDataStoreJavaHelper.getZoneMax(application, zoneType, 4)
val cutoffs = if (zoneType == SettingsDataStore.ZoneType.PWR_BIKE) {
    val z5Max = SettingsDataStoreJavaHelper.getZoneMax(application, zoneType, 5)
    val z6Max = SettingsDataStoreJavaHelper.getZoneMax(application, zoneType, 6)
    ...
}
```
`calculateZoneColor` and `calculateZoneGaugeState` each invoke `getZoneMax` 4 to 6 times. For an incoming power sample, that adds **12 `runBlocking` calls on the Main thread** in `TrackingViewModel` alone. Combined with `TrackingTabsScreen`, a single power tick triggers up to **22 `runBlocking` calls**!

### 2.4 Hot-Path Invocations in Sensor Drivers
1. **`SpeedAndLocationDevice.java` (line 268)**:
   ```java
   public void onNewLocation(Location location) {
       if (location != null) {
           double accuracyThreshold = SettingsDataStoreJavaHelper.getGpsAccuracyThreshold(mContext);
           if (location.getAccuracy() <= accuracyThreshold) {
               ...
   ```
   Every GPS location fix received from Android's `LocationManager` or `FusedLocationProviderClient` executes `onNewLocation` on the location looper thread. Line 268 instantiates `TuningPreferencesDataStore` and calls `runBlocking(Dispatchers.IO)` to retrieve `gpsAccuracyThresholdMeters`.
2. **`VerticalSpeedAndSlopeDevice.java` (line 244)**:
   ```java
   double minSpeed = com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper.getSlopeMinSpeed(mContext);
   ```
   Inside `calculateMetrics()`, which is triggered every 1,000 ms by `mHandler.postDelayed(mMetricsRunnable, 1000)` on the BANALService thread. Every second, `getSlopeMinSpeed` calls `getTuningConfig(mContext)` and executes `runBlocking(Dispatchers.IO)`.

### 2.5 UI Chart Rendering Hot Paths (`TelemetryZoneMath.kt`)
In `TelemetryZoneMath.kt`, lines 221–259:
- `loadHeartRateThresholds(context, bSportType)` queries zones 1 to 4 via `SettingsDataStoreJavaHelper.getZoneMax`.
- `loadPowerThresholds(context)` queries zones 1 to 6 via `SettingsDataStoreJavaHelper.getZoneMax`.

These methods are invoked on the UI thread during layout and drawing passes in:
- `MapDetailLayout.kt` (lines 157, 161)
- `TelemetryMetricGraph.kt` (lines 113, 117)
- `ElevationProfile.kt` (lines 997, 1001)
Executing 4 to 6 `runBlocking` calls during `onDraw` or view layout creates noticeable frame drops during chart interaction and scrubbing.

### 2.6 Background Thread Invocations (`CyclingPowerMetricsDevice`, `TrackerService`, `LocationNameResolver`)
- **`CyclingPowerMetricsDevice.java`**: Default providers pass `SettingsDataStoreJavaHelper::getAthleteWeightKg`, `SettingsDataStoreJavaHelper::getFtp`, and `SettingsDataStoreJavaHelper::getEffectiveWPrimeKj`. These are called during the 1-Hz power metric evaluation loop (`calculateMetrics`). While running on a background executor, parking threads behind `runBlocking` wastes worker threads and risks thread starvation.
- **`TrackerService.java`**: Line 443 queries `SettingsDataStoreJavaHelper.getAutoPauseConfig(this)` (with a 5-second TTL cache), and lines 2721–2722 query `resolveEffectiveFtpAt` and `resolveEffectiveWeightKgAt` during session finalization.
- **`LocationNameResolver.kt` (line 129)**:
  ```kotlin
  fun resolveLocationNameBlocking(
      context: Context,
      latitude: Double,
      longitude: Double,
      locale: Locale = Locale.getDefault()
  ): String = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
      resolveLocationName(context, latitude, longitude, locale)
  }
  ```
  A forensic audit of all call sites reveals:
  - `KnownLocationsDatabaseManager.java:379, 388`: in `learnLocation()` (only invoked in unit tests).
  - `KnownLocationsDatabaseManager.java:420, 428`: in `recordWorkoutStart()` (invoked via `mDbExecutor.execute(...)` from `TrackerService.java:2083` on the first GPS fix, and from `WorkoutClusterEngine` background processing).
  - `KnownLocationsDatabaseManager.java:604`: in `healLegacyLocationsAsync()` (background worker thread).
  - `AltitudeFromPressureDevice.java:176, 196`: executed inside `mExecutor.execute(...)` (background worker thread).
  *Conclusion*: `resolveLocationNameBlocking` is already restricted to background worker threads. However, wrapping `resolveLocationName` (which can perform network geocoding via Android's `Geocoder`) in `runBlocking` makes it dangerous if ever called from the UI thread, and must be guarded by explicit `@WorkerThread` annotations and KDoc contracts.

### 2.7 Dead Code in `SettingsDataStore.kt`
Line 460 of `SettingsDataStore.kt`:
```kotlin
fun getSummary(zoneType: ZoneType): String = runBlocking {
    val z1Max = getZone1MaxFlow(zoneType).first()
    ...
}
```
A codebase-wide grep confirms that `SettingsDataStore.getSummary` has **zero call sites** anywhere in the project. It is dead code and should be removed or converted to a non-blocking/suspend function.

---

## 3. User Scope Grounding (ATT-1250)

In accordance with `ATT-1250` and Core ASPICE Governance, scope boundaries are strictly delineated to resolve the reported defect without speculative architectural drift:

* **In-Scope Goals**:
  1. **Eliminate `runBlocking` on UI and Sensor Hot Paths**:
     - Remove `runBlocking` from `SettingsDataStoreJavaHelper.kt`, replacing all queries with non-blocking $O(1)$ reads backed by in-memory reactive caching (`StateFlow.value` / cached snapshots).
     - Remove unused `runBlocking` in `SettingsDataStore.kt:460`.
     - Annotate and guard `LocationNameResolver.resolveLocationNameBlocking` with `@WorkerThread`.
  2. **Reactive In-Memory Caching in DataStore Repositories**:
     - Provide singleton access and eager in-memory `StateFlow` caching in `TuningPreferencesDataStore` and `SettingsDataStore` using the application supervisor coroutine scope.
     - Ensure changes saved via Settings UI immediately propagate to the in-memory cache without restart or latency.
  3. **Decouple Hot UI Paths via Pure Parameter Passing**:
     - Refactor `calculateZoneIndex` in `BatterySaverModel.kt` into a pure function accepting raw threshold cutoffs (`List<Int>`), retaining an overload delegating to the non-blocking helper.
     - Pass pre-resolved zone thresholds in `TrackingTabsScreen.kt` and `TrackingViewModel.kt` instead of querying DataStore per sensor sample.
  4. **Preserve Existing Unit Test & API Compatibility**:
     - Keep public static method signatures of `SettingsDataStoreJavaHelper` intact so existing unit tests utilizing `mockkStatic(SettingsDataStoreJavaHelper::class)` (`TelemetryZoneMathTest`, `SpeedAndLocationDeviceDecayTest`, `TrackerServiceResilienceTest`, `TrackerServiceDeviceNameQuotingTest`) continue to pass without regression.
  5. **Documentation Integrity**:
     - Correct misleading KDoc in `SettingsDataStoreJavaHelper.kt` regarding `runBlocking(Dispatchers.IO)`.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. **`WorkoutDataMapper.kt` I/O Optimization**: The ticket description references `WorkoutDataMapper`. However, `WorkoutDataMapper.fromSnapshot`, cursor iteration queries, polyline decoding, and Aftermath N+1 DB operations are governed exclusively by dedicated sister ticket **`ATT-4003`** (`[Verbesserung] [High] Remove blocking per-workout I/O from mapper and DB queries from composition`). Broader refactoring of `WorkoutDataMapper` is strictly out of scope for `ATT-3999`.
  2. **Altering Zone Classification or Mathematical Models**: No changes to the Coggan 7-zone power or 5-zone HR boundary formulas, Skiba $W'$ balance formulas, or AMOLED battery saver hysteresis timings.
  3. **Rewriting Java Sensor Drivers into Kotlin**: `SpeedAndLocationDevice`, `VerticalSpeedAndSlopeDevice`, `CyclingPowerMetricsDevice`, and `TrackerService` will continue as Java classes, consuming non-blocking values from `SettingsDataStoreJavaHelper`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

In accordance with `REQ-PRO-022`, the following four components are audited to understand why the existing formulations were chosen and how their invariants will be preserved:

### 4.1 `SettingsDataStoreJavaHelper`
* **Original Requirement ID & Target**: `REQ-SET-073`, `REQ-UI-399.1`, `REQ-TRK-019`, `REQ-MIG-039.2`, `REQ-TRK-018`. Targets `SettingsDataStoreJavaHelper.kt`, `SettingsDataStore.kt`, `TuningPreferencesDataStore.kt`.
* **Historical Origin & Commit Trace**: Created in `72e33747`, expanded across `a48c8a38` (ATT-1265), `f2c2409f` (ATT-1304), `f37f3b94` (ATT-3184), `559376e2` / `1e76228d` (ATT-1289), `5bd9e171` (ATT-3677), `61ccb801` (ATT-3862), `e5d9225e` (ATT-3860).
* **Root Reason for Existing Formulation**: Introduced as a bridge between legacy Java classes (`BANALService`, `TrackerService`, sensor devices) and Jetpack `DataStore`. Because `DataStore` natively uses asynchronous Kotlin `Flow`, developers used `runBlocking` as an expedient bridge. Inaccurate KDoc comments contributed to the belief that wrapping `runBlocking` with `Dispatchers.IO` eliminated UI thread blocking.
* **Preservation of Core Invariants**: The public static API contract must be preserved to avoid breaking Java callers and tests mocking `SettingsDataStoreJavaHelper`. Internal implementation transitions to non-blocking `StateFlow.value` / cached snapshots, eliminating all thread parking.

### 4.2 `BatterySaverModel`
* **Original Requirement ID & Target**: `REQ-SET-074`, `REQ-SET-073`. Targets `BatterySaverModel.kt`, `TrackingTabsScreen.kt`.
* **Historical Origin & Commit Trace**: `83d9b354` (ATT-1268), `e40a6119`, `f2c2409f` (ATT-1304), `f37f3b94` (ATT-3184).
* **Root Reason for Existing Formulation**: `calculateZoneIndex(context, zoneType, value)` was introduced to resolve the active HR and Power zone for AMOLED dimming logic. Because `Context` was passed, it directly called `SettingsDataStoreJavaHelper.getZoneMax` 4 to 6 times per zone evaluation. When wired into `TrackingTabsScreen`'s `LaunchedEffect(filteredSensorData, ...)`, it executed up to 10 `runBlocking` calls per sensor tick directly on the Compose Main thread.
* **Preservation of Core Invariants**: Mathematical zone classification logic (Zone 1..5 for HR, Zone 1..7 for Power) remains strictly unchanged. Pure threshold parameter passing eliminates `Context` dependency and blocking reads from the UI hot path.

### 4.3 `TelemetryZoneMath`
* **Original Requirement ID & Target**: `REQ-UI-260`, `REQ-UI-370`, `REQ-UI-420`. Targets `TelemetryZoneMath.kt`, `TelemetryZoneMathTest.kt`.
* **Historical Origin & Commit Trace**: `ddbca947` (ATT-1839), `58c6b552` (ATT-2031 / REQ-UI-260), `f37f3b94` (ATT-3184).
* **Root Reason for Existing Formulation**: Centralized helper to load HR and Power zone thresholds (`HeartRateZoneThresholds`, `PowerZoneThresholds`) from `SettingsDataStoreJavaHelper.getZoneMax` for chart rendering (`ElevationProfile.kt`, `TelemetryMetricGraph.kt`, `MapDetailLayout.kt`).
* **Preservation of Core Invariants**: Threshold monotonicity validation (`z1 > 0 && z2 > z1...`) and null-safety contracts are strictly preserved. Non-blocking reads from `SettingsDataStoreJavaHelper` ensure graph layout and canvas drawing on the UI thread never stall. The test contract in `TelemetryZoneMathTest.kt` (using `mockkStatic`) remains 100% green.

### 4.4 `SpeedAndLocationDevice` & `VerticalSpeedAndSlopeDevice`
* **Original Requirement ID & Target**: `REQ-SET-073`. Targets `SpeedAndLocationDevice.java`, `VerticalSpeedAndSlopeDevice.java`.
* **Historical Origin & Commit Trace**: `f2c2409f` (ATT-1304) parameterized previously hardcoded constants (`ACCURACY_THRESHOLD = 50.0f` and `SLOPE_MIN_SPEED = 0.5f`) via `SettingsDataStoreJavaHelper.getGpsAccuracyThreshold(mContext)` and `getSlopeMinSpeed(mContext)`.
* **Root Reason for Existing Formulation**: Enabled athlete configuration of GPS horizontal accuracy rejection and slope min speed. However, reading them via `getTuningConfig(mContext)` (which instantiates `TuningPreferencesDataStore` and calls `runBlocking(Dispatchers.IO)`) on every GPS fix and 1-second timer blocked the location looper and sensor handler threads.
* **Preservation of Core Invariants**: Sensor filtering accuracy, GPS rejection logic, and VAM/slope calculation algorithms are strictly preserved. Reading from an in-memory `StateFlow.value` makes threshold lookups $O(1)$ memory operations.

---

## 5. Architectural Strategy & High-Level Solution

The remediation follows a **Two-Tier Architecture**:
1. **Tier 1 (DataStore Layer)**: Eager in-memory reactive caching (`StateFlow`) shared across the application.
2. **Tier 2 (Consumer Layer)**: Decoupling UI hot paths via pure function parameters and ViewModel flow observation.

```mermaid
flowchart TD
    subgraph DataStore Layer [Tier 1: Reactive In-Memory StateFlow Caching]
        DS[Jetpack DataStore Disk / Preferences] -->|Flow emissions| EagerScope[Application Supervisor CoroutineScope]
        EagerScope -->|stateIn Eagerly| TuningStateFlow["TuningPreferencesDataStore.tuningConfigStateFlow (StateFlow)"]
        EagerScope -->|stateIn Eagerly| SettingsStateFlow["SettingsDataStore.zoneThresholdsStateFlow (StateFlow)"]
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

### 5.1 Tier 1: In-Memory Reactive Caching in Repositories
1. **`TuningPreferencesDataStore`**:
   - Introduce singleton pattern `TuningPreferencesDataStore.getInstance(context: Context)`.
   - Maintain an application-scoped `CoroutineScope(SupervisorJob() + Dispatchers.Default)` (`appScope`).
   - Expose `val tuningConfigStateFlow: StateFlow<TuningConfig> = tuningConfigFlow.stateIn(appScope, SharingStarted.Eagerly, TuningPreferencesDefaults.createDefaultTuningConfig())`.
   - Provide an instant, non-blocking synchronous getter: `fun getTuningConfig(): TuningConfig = tuningConfigStateFlow.value`.
2. **`SettingsDataStore`**:
   - `getInstance(context)` already exists.
   - Introduce an in-memory cached state / `StateFlow` for zone thresholds, FTP, weight, CP, and $W'$, initialized with standard defaults and eagerly updated by `dataStore.data`.
   - Provide non-blocking synchronous accessors: `fun getZoneMaxSync(zoneType: ZoneType, zoneIndex: Int): Int`.
   - Remove unused `fun getSummary(zoneType: ZoneType): String`.
3. **`SettingsDataStoreJavaHelper`**:
   - Rewrite all query methods (`getZoneMax`, `getTuningConfig`, `getGpsAccuracyThreshold`, `getSlopeMinSpeed`, `getAltitudeFilterWindow`, `getFtp`, `isAutoPauseEnabled`, `getAutoPauseSpeedThresholdKmh`, `getAutoPauseDebounceSec`, `getAutoPauseConfig`, `getAthleteWeightKg`, `getCpWatts`, `getEffectiveCpWatts`, `getWPrimeKj`, `getEffectiveWPrimeKj`):
     - Replace `runBlocking` with direct non-blocking reads from `SettingsDataStore.getInstance(context)` and `TuningPreferencesDataStore.getInstance(context)`.
     - Time complexity becomes $O(1)$ memory access with zero coroutine overhead, zero lock contention, and zero thread parking.
   - For `setAthleteWeightKg(context, weightKg)`:
     - Replace `runBlocking(Dispatchers.IO)` with asynchronous fire-and-forget launch on `appScope`: `appScope.launch { dataStore.setAthleteWeightKg(weightKg) }`.
   - Update KDoc to document thread-safe, non-blocking in-memory semantics.

### 5.2 Tier 2: Decoupled UI & Sensor Hot Paths
1. **`BatterySaverModel.kt`**:
   - Introduce a pure function signature:
     ```kotlin
     fun calculateZoneIndex(value: Double, cutoffs: List<Int>): Int {
         for (i in cutoffs.indices) {
             if (value <= cutoffs[i]) return i + 1
         }
         return cutoffs.size + 1
     }
     ```
   - Retain the existing `calculateZoneIndex(context: Context, zoneType: SettingsDataStore.ZoneType, value: Double): Int` as a backward-compatible wrapper that retrieves cutoffs from the non-blocking `SettingsDataStoreJavaHelper.getZoneMax`.
2. **`TrackingTabsScreen.kt`**:
   - Resolve or observe zone cutoffs in Compose state, or pass pre-resolved cutoffs into `calculateZoneIndex`, eliminating any repetitive DataStore queries during `LaunchedEffect(filteredSensorData, ...)`.
3. **`TrackingViewModel.kt`**:
   - Observe zone threshold flows in `observeZoneDisplayOptions()` alongside display options, maintaining an in-memory cutoff list per `ZoneType`.
   - `calculateZoneColor` and `calculateZoneGaugeState` read directly from these in-memory cutoff lists, completely eliminating `SettingsDataStoreJavaHelper` calls on the UI thread.
4. **`TelemetryZoneMath.kt`**:
   - `loadHeartRateThresholds` and `loadPowerThresholds` continue to call `SettingsDataStoreJavaHelper.getZoneMax`, which is now $O(1)$ non-blocking memory access, preserving 100% test compatibility with `TelemetryZoneMathTest.kt`.
5. **`LocationNameResolver.kt`**:
   - Add `@WorkerThread` annotation and update KDoc for `resolveLocationNameBlocking` to explicitly warn that it executes network/IPC geocoding and must only be invoked from background worker threads.

---

## 6. System Invariants & Risk Assessment

### Core Invariants
1. **Zero Behavioral or Sensor Metric Regression**: GPS filtering thresholds, slope and VAM calculations, auto-pause debounce, power metrics (NP, IF, TSS, $W'$ balance), and AMOLED battery saver dimming logic must produce bit-for-bit identical results.
2. **100% Clean-Room Test Suite Pass Rate**: All existing unit tests across `app/src/test` (including `TelemetryZoneMathTest`, `SpeedAndLocationDeviceDecayTest`, `TrackerServiceResilienceTest`, and `TrackerServiceDeviceNameQuotingTest`) must continue to pass with zero regressions.
3. **Strict Mocking Compatibility**: Static mock bindings via `mockkStatic(SettingsDataStoreJavaHelper::class)` must continue to function seamlessly in existing and future test suites.
4. **Instant Reactivity**: Any preference change made by the user in Settings screens must propagate immediately to the in-memory `StateFlow` and be visible on the very next telemetry tick.
5. **Thread Safety**: All in-memory caches must be thread-safe across Main, Sensor Handler, and Background IO threads via immutable data classes and volatile/atomic references.
6. **Parent Ticket Human Decision Gate**: Moving `ATT-3999` to `Erledigt` is reserved exclusively for the human user.

### Risk Assessment
* **Risk Rating**: **MEDIUM**
* **Technical Justification**:
  `SettingsDataStoreJavaHelper` is a core bridge consumed across sensors, foreground services, UI view models, and unit tests. Refactoring it touches multiple subsystems. However:
  - The public static method signatures and return types remain 100% unchanged.
  - The fallback default values (`TuningConfig()`, `SettingsDataStore` defaults) are already battle-tested and remain available if storage is not yet initialized.
  - Test coverage is high: existing test classes verify both the bridge and the consuming devices.
  - Scope is strictly bounded away from unrelated DB/mapper refactoring (`ATT-4003`).
  Therefore, the risk is rated **MEDIUM** and is fully mitigated through the proposed architecture and verification strategy.
