# Stage 3 Implementation Plan: ATT-8126 — Live Sensor & Cockpit Hotpath De-Coupling

* **Ticket**: `ATT-8126` (`tier-a`, `2 SP`, Parent Epic `ATT-8119`) | **Target Release**: `V5.0.0` | **Requirements**: `REQ-UI-2800` (`TST-UI-2800`)

## Atomic Implementation Steps (Execute Strictly After Gate 3 PASS)

### Step 1: Repository Scopes, Conflation + Sparkline/W'bal Liveness & Concurrency (`REQ-UI-2800.1`)
1. **`FilteredSensorData.kt`**:
   * Add optional `@JvmField val deviceId: Long = -1L` (`@JvmOverloads constructor` preserving 6-arg Java/Kotlin binary compatibility) and propagate `deviceId` in `filterData`.
   * Override structural `equals(other: Any?)` and `hashCode()` comparing `sensorType`, `Objects.equals(value, other.value)` (so `Double.NaN == Double.NaN` conflates), `stringValue`, `deviceName`, `deviceId`, `filterType`, and `mFilterConstant.toBits() == other.mFilterConstant.toBits()` (matching `hashCode()`), explicitly excluding `SensorData.mTimestamp`.
2. **`BANALServiceRepository.kt`**:
   * Replace `Dispatchers.Main` (`L131`) with `defaultDispatcherForTesting ?: Dispatchers.Default` + `@VisibleForTesting internal fun setDispatcherForTesting(dispatcher: CoroutineDispatcher?)` (cleared in `resetForTesting()`).
   * Add lightweight decoupled liveness flows alongside conflated `_allFilteredSensorData`:
     * `_sparklineTick = MutableStateFlow(0L)` (`val sparklineTick: StateFlow<Long>`), incremented strictly when `updateSparklineBuffers(newSensorData)` appends `>= 1` sample to any registered buffer.
     * `_wPrimeTelemetryHeartbeat = MutableStateFlow(0L)` (`val wPrimeTelemetryHeartbeat: StateFlow<Long>`), incremented whenever `newSensorData` contains a non-null numeric `SensorType.W_PRIME_BAL` sample.
   * Add `private val itpmsLock = Any()`:
     * Synchronize `processItpmsTelemetryTick` and `clearBreadcrumbs` estimator resets (`itpmsLock` acquired sequentially after `trackLock`, never nested).
     * Wrap `loadAndApplyItpmsBaseline` (`L872-873`) generation check (`baselineLoadGenerations[deviceId]?.get() != generation`) + `applyResolvedBaseline` atomically inside `synchronized(itpmsLock)`.
     * In `persistConvergedCircumferences` (`L984-1020`), snapshot locked `(deviceId, circMm)` entries and single-estimator fallback state inside `synchronized(itpmsLock)` BEFORE performing DB/repo writes outside the lock.
   * Synchronize `applyRetroactiveAltitudeCorrection` (`L1122-1143`) on `synchronized(trackLock)`.
   * Add `private val connectionLock = Any()` guarding `bindReferenceCount`, `isBinding`, `isBoundToBanalService` across `bindToBANALService`, `unbindFromBANALService`, `forceUnbindFromBANALService`, `onServiceConnected`, and `onServiceDisconnected`:
     * In `bindToBANALService`, if `appContext.bindService(...)` returns `false` or throws, reset `isBinding = false` under `connectionLock`.
     * `unbindFromBANALService` / `forceUnbindFromBANALService` unbind whenever `bindReferenceCount == 0 && (isBoundToBanalService || isBinding)` and clear both flags + `_serviceBinder.value = null`.
     * `onServiceConnected` rejects late connect when `bindReferenceCount == 0` under `connectionLock` and calls `unbindService(this)` defensively; `onServiceDisconnected` resets flags under `connectionLock`.
   * Update `BANALServiceRepositoryPauseIsolationTest`, `BANALServiceRepositoryItpmsWiringTest`, and any other `BANALServiceRepository*Test` advancing virtual time via `Dispatchers.setMain` to inject `testDispatcher` via `setDispatcherForTesting(testDispatcher)`.
3. **`TrackingViewModel.kt` (Steady-Value Sparkline & W'bal Liveness Under Conflation)**:
   * Add `observeSparklineUpdates()`: `flatMapLatest` gated strictly on `configs.any { it.showGraph }` collecting `banalServiceRepository.sparklineTick ?: emptyFlow()` (null-safe for unstubbed mocks) to refresh `sparklineSamples`/`sparklineSpanSeconds` on `showGraph` tiles even during steady ERG/READY/PAUSED values (`0` wakeups when no tile has `showGraph`).
   * Add `observeWPrimeFreshness()`: `flatMapLatest` gated strictly on `configs.any { it.sensorType == SensorType.W_PRIME_BAL }` collecting `banalServiceRepository.wPrimeTelemetryHeartbeat ?: emptyFlow()` (`filter { it > 0L }`) to reset `scheduleWPrimeStaleCheck()` without mutating `_uiState` while steady (restoring non-stale state only if previously marked stale).
4. **`WorkoutRepository.kt`, `EquipmentRepository.kt`, `SportTypesRepository.kt`**:
   * `WorkoutRepository.kt:276` -> `Dispatchers.Default + job`; `EquipmentRepository.kt:48` & `SportTypesRepository.kt:46` -> `Dispatchers.IO + job`.

### Step 2: `RrArtifactFilter.kt` `O(N)` Single-Pass Precomputation & Thread-Safe Scratch Buffers (`REQ-UI-2800.2`)
1. Convert `rawRrs` to primitive `DoubleArray(n) { beats[it].rrMs }`, change `computeDifferences(series: DoubleArray): DoubleArray`, and add `computeLocalMediansCentered(series: DoubleArray): DoubleArray` using a **method-local** `DoubleArray(2 * MEDIAN_HALF_WINDOW + 1)` scratch array + `java.util.Arrays.sort(buf, 0, len)` (never instance fields!).
2. Compute `localMedians` and `mRrs = DoubleArray(n) { rawRrs[it] - localMedians[it] }` once before the loop in both `filterBidirectionalBeats` and `filterLiveCausal`; pass `localMedians` into `processBeats(...)` removing per-beat `computeLocalMedianCentered` calls (`L135, L212, L238`).
3. Add `computeQuartileDeviationWindow(values: DoubleArray, start: Int, end: Int, scratch: DoubleArray): Double` using a **method-local** `val qdScratch = DoubleArray(QD_WINDOW_SIZE + 1)` (capacity **`92`**, covering `filterLiveCausal`'s `[i - 86, i + 6)` 92-beat window, with defensive resize guard if `len > scratch.size`) + `System.arraycopy` + `Arrays.sort(buf, 0, len)`, keeping `internal fun computeQuartileDeviation(values: List<Double>)` delegating to the same percentile math.

### Step 3: `ProxySensor`, `FilterData`, `FilterManager` & End-to-End `deviceId` Rename Survival (`REQ-UI-2800.3`)
1. **`ProxySensor.java` (`L83-90`)**: Under `mSourceLock`, add `sensorListener` to `mSensorListeners` and snapshot `source = mSourceSensor` & `activated = mActivated`; outside `mSourceLock` (`REQ-SEN-021.2`), if `source != null`, call `source.addSensorListener(sensorListener)` and then re-check `mSourceSensor != source` under `mSourceLock` (detaching stale `source` and attaching to current `mSourceSensor` if swapped concurrently); if `source == null && activated`, push `null` once.
2. **`FilterData.kt` & `DeviceManager.java`**: Add `@JvmOverloads deviceId: Long = -1L` on `FilterData` (`hashKey` = `"id:$deviceId"` when `> 0L`, else `deviceName`). Add `public MyDevice getMyDeviceById(long deviceId)` in `DeviceManager.java`, compacting verbose `if` blocks in `getMyDeviceList()` to stay `<= 1,707` LoC (`<= 1,708` cap).
3. **`FilterManager.kt` & `MyFilter.kt`**:
   * Remove duplicate `myFilter.newValue(currentValue)` in `rebindFilter` (`L204-208`).
   * In `resolveSensor`: when `filterData.deviceId > 0L`, look up `val byId = mDeviceManager?.getMyDeviceById(filterData.deviceId)`; if `byId != null`, return `byId.getSensor(filterData.sensorType)` directly WITHOUT falling back to `getMyDeviceByName`; fall back to `getMyDeviceByName` ONLY when `byId == null && filterData.deviceName != null` (legacy/mock compatibility).
   * Add `@JvmField protected var mDeviceId: Long = -1L` and `fun updateDeviceIdentity(deviceId: Long, deviceName: String?)` on `MyFilter`, propagate `mDeviceId` in `MyFilter.filteredSensorData`, and sync `mDeviceName` from the live resolved device's name on bind/rebind (`createFilter` / `syncFilterBinding` / `rebindFilter`).
4. **End-to-End Wiring Across DB, ViewModel & Sparklines**:
   * `TrackingViewsDatabaseManager.java:1129` & `EditSensorFieldViewModel.kt:801`: pass `sourceDeviceId` into `FilterData(...)` (normalizing `<= 0L` to `-1L` / `null`).
   * `SensorFieldState.kt` & `TrackingViewModel.kt:577, 776`: carry `sourceDeviceId: Long = -1L` on `SensorFieldState` and match incoming `FilteredSensorData` by `(sensorType, filterType, filterConstant, deviceId)` when `sensorData.deviceId > 0L` (falling back to existing `configHash` by `deviceName` when `deviceId <= 0L`).
   * `BANALServiceRepository.kt:1028-1062, 1104-1115`: add `sourceDeviceId: Long = -1L` to `registerSparklineBuffer` / `SparklineRegistration` and match in `updateSparklineBuffers` by `deviceId` when `> 0L` (with `configHash` fallback), ensuring renamed pinned devices keep both tiles AND sparklines live!

### Step 4: Cockpit 1-Hz Recomposition Boundaries (`REQ-UI-2800.4`)
1. **`TrackingTabGridContent.kt` (`L81-121`)**: Wrap `val gridActions = remember(viewModel) { object : GridActions { ... } }`.
2. **`TrackingTabsScreen.kt` (`L340-423`)**:
   * Replace root `currentLocation.collectAsState()` with `LaunchedEffect(controlViewModel, routeSelectorViewModel) { controlViewModel.banalServiceRepository.currentLocation.collect { ... } }`.
   * Replace root `allFilteredSensorData.collectAsState()` with `LaunchedEffect(brightnessMode, activityType, tuningConfig, trackingTabsViewModel, batterySaverController)` returning early if `brightnessMode != DisplayBrightnessMode.AUTO`, else collecting `trackingTabsViewModel.allFilteredSensorData.collect { ... }`.
   * Preserve `beyondViewportPageCount = 0` (`L922`, `REQ-TRK-079.2`) and `SensorGridScreen` root GPS decoupling (`REQ-UI-2793`).

### Step 5: Verification, Tests & Visual Evidence (`Rule 7.5`, `Rule 8.5`)
* Add `BANALServiceRepositoryHotpathTest`, `RrArtifactFilterPerformanceEquivalenceTest`, `ProxySensorAndFilterManagerIdKeyTest`, and `CockpitRecompositionBoundsComposeTest`.
* Run targeted Gradle `--rerun` test suites + `ArchitectureConformanceContractTest` + live `emulator-5554` cockpit check (`logcat -b crash -d`) + Light/Dark PNG verification (`Rule 8.5`) + `tools/auto_gate.py --write-bundle`.
