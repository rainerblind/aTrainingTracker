# Software Interface Specifications: aTrainingTracker (ASPICE SWE.2.BP2)

This document formalizes the software interface specifications of **aTrainingTracker** in accordance with **Automotive SPICE (ASPICE v3.1 / v4.0) SWE.2 Base Practice 2 (Specify interfaces of software elements)**.

Each architectural interface defines its provider, consumer, operations, data schemas, threading contracts, and error handling protocols.

---

## 1. Interface Specification Matrix

| Interface ID | Boundary | Provider Element | Consumer Element | Protocol / Mechanism | Threading Contract |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `IF-SWE-01` | UI $\to$ ViewModel | ViewModels (`TrackingViewModel`, `TrackOnMapAftermathViewModel`) | Jetpack Compose Composables | Unidirectional MVI / `StateFlow` | `Dispatchers.Main` |
| `IF-SWE-02` | ViewModel $\to$ Repository | Repositories (`WorkoutRepository`, `BANALServiceRepository`) | ViewModels | Asynchronous Coroutines (`suspend fun`) | `Dispatchers.IO` |
| `IF-SWE-03` | Inter-Service Ingestion | `BANALServiceComm` (Local Binder on `BANALService`) | `TrackerService` | Android Local Binder IPC & Broadcasts | Service Thread / Binder Pool |
| `IF-SWE-04` | Hardware $\to$ Services | Hardware HAL (BLE Gatt, ANT+ Plugin, Location Provider) | `BANALService`, `TrackerService` | System Callbacks (`onCharacteristicChanged`) | Hardware Callback Thread |
| `IF-SWE-05` | Service/Repo $\to$ Domain | Domain Calculators (`AutoPauseManager`, `ClimbDetector`) | Services & Repositories | Pure JVM Synchronous Methods | Caller Thread (`Dispatchers.IO`) |
| `IF-SWE-06` | Service/Repo $\to$ SQLite | Database Managers (`WorkoutSamplesDatabaseManager`) | `TrackerService`, Repositories | SQLite WAL Mode & Synchronized Transactions | `dbDispatcher` / `Dispatchers.IO` |
| `IF-SWE-07` | Repository $\to$ Exporters | Exporters (`FitFileWriter`, `GPXFileWriter`, `TCXFileWriter`) | `ExportManager`, `WorkoutRepository` | Streaming File Writers & Serializers | `Dispatchers.IO` (WorkManager) |
| `IF-SWE-08` | Repository $\to$ Cloud APIs | Cloud Endpoints (`StravaUploader`, `HealthConnectManager`, `HealthConnectHrvImporter`) | `ExportManager`, UI Controllers, `HrvReadinessRepository` | REST (OkHttp) & HealthConnectClient | `Dispatchers.IO` (WorkManager) |

---

## 2. Detailed Software Interface Specifications

### 2.1 IF-SWE-01: Presentation Layer UI to ViewModel (Jetpack Compose MVI)
* **Provider**: Lifecycle ViewModels (`TrackingViewModel`, `ControlTrackingViewModel`, `TrackOnMapAftermathViewModel`, `MapFragmentWithTrackViewModel`).
* **Consumer**: Declarative Jetpack Compose Composables (`TrackingTabsScreen`, `TrackOnMapScreen`, `WorkoutSummariesTabbedScreen`).
* **Operations / Method Signatures**:
  * `val uiState: StateFlow<TrackingScreenState>`: Observable read-only stream of immutable tracking screen state (`TrackingViewModel`).
  * `val mapState: StateFlow<TrackingMapState>`: Observable stream of tracking map state (`TrackingViewModel`).
  * `val uiState: StateFlow<AftermathMapUIState>`: Observable stream of workout aftermath map state (`TrackOnMapAftermathViewModel`).
  * `fun updateScreenMode(mode: ScreenMode)`: Updates display mode between data fields and map (`TrackingViewModel`).
  * `fun onStartTracking()`: Dispatches workout tracking start action (`ControlTrackingViewModel`).
  * `fun onPauseTracking()`: Dispatches workout pause action (`ControlTrackingViewModel`).
  * `fun onResumeTracking()`: Dispatches workout resume action (`ControlTrackingViewModel`).
  * `fun onStopTracking()`: Dispatches workout stop action (`ControlTrackingViewModel`).
* **Data Payloads / Schemas**:
  * Immutable Kotlin state models (`TrackingScreenState`, `TrackingMapState`, `AftermathMapUIState`, `ScreenMode`).
  * Telemetry primitives formatted via canonical display utilities (`DistanceFormatter`, `AltitudeFormatter`, `SpeedFormatter`).
* **Concurrency & Threading**:
  * State emission collected on `Dispatchers.Main` via `collectAsStateWithLifecycle()`.
  * ViewModel coroutines operate under `viewModelScope` with cancellation bound to UI lifecycle.
* **Error Handling & Fault Tolerance**:
  * Flow exceptions intercepted via `.catch { emit(...) }` or defensive fallbacks.
  * Zero `MutableState` mutations during composition; defensive fallbacks for missing or uninitialized sensors.

### 2.2 IF-SWE-02: ViewModel to Repository Layer
* **Provider**: Domain Repositories (`WorkoutRepository`, `BANALServiceRepository`, `SportTypesRepository`, `LiveSegmentsRepository`).
* **Consumer**: ViewModels (`TrackingViewModel`, `ControlTrackingViewModel`, `TrackOnMapAftermathViewModel`, `EquipmentViewModel`).
* **Operations / Method Signatures**:
  * `suspend fun loadWorkout(id: Long)`: Asynchronously loads workout entity into active memory (`WorkoutRepository`).
  * `fun getWorkoutById(id: Long): Flow<WorkoutData?>`: Reactive stream of workout record and live database updates (`WorkoutRepository`).
  * `fun saveWorkout(userEditedWorkout: WorkoutData?)`: Transactional update of workout metadata (`WorkoutRepository`).
  * `fun updateExtremaValue(workoutId: Long, extremaUpdate: ExtremaUpdate)`: Records updated sensor extremum (`WorkoutRepository`).
  * `val allFilteredSensorData: StateFlow<List<FilteredSensorData<*>>>`: Hot shared flow of live filtered sensor telemetry (`BANALServiceRepository`).
* **Data Payloads / Schemas**:
  * Domain DTOs (`WorkoutData`, `LapData`, `ExtremaUpdate`, `ExtremaType`, `SportType`, `FilteredSensorData`).
* **Concurrency & Threading**:
  * All I/O operations execute under `Dispatchers.IO` using structured concurrency.
* **Error Handling & Fault Tolerance**:
  * Null-safe queries returning `null` or empty collections rather than throwing unchecked runtime exceptions.
  * Sealed `Result<T>` hierarchies for network-backed repository operations.

### 2.3 IF-SWE-03: Service Layer Inter-Service Communication (TrackerService $\leftrightarrow$ BANALService)
* **Provider**: `BANALServiceComm` (Local Binder exposed by `BANALService`).
* **Consumer**: `TrackerService`.
* **Operations / Method Signatures**:
  * `fun getAllFilteredSensorData(): List<FilteredSensorData>`: 1-Hz batch poll of active physical/virtual sensors.
  * `fun getBestSensorData(sensorType: SensorType): SensorData<?>`: Priority-resolved single metric query.
  * `Intent(RESET_ACCUMULATORS_INTENT)` / `Intent(REQUEST_PAUSE_TRACKING)`: IPC action broadcast signaling.
* **Data Payloads / Schemas**:
  * `SensorData<T>` and `FilteredSensorData` holding timestamp, raw value, smoothed value, and sensor address.
  * Filter configuration models (`FilterData`) and `SensorType` enum tokens.
* **Concurrency & Threading**:
  * Thread-safe shared memory via synchronized methods and volatile atomic state holders.
* **Error Handling & Fault Tolerance**:
  * Volatile Binder reference guarded by null checks (`if (mBanalComm != null)`).
  * Graceful fallback when `BANALService` is unbinding or restarting during background telemetry interruptions.

### 2.4 IF-SWE-04: Hardware & Sensors to Service Layer
* **Provider**: Android Bluetooth LE subsystem, ANT+ Plugin API, and Google Play Fused Location Provider.
* **Consumer**: `BANALService` and `TrackerService`.
* **Operations / Method Signatures**:
  * `BluetoothGattCallback.onCharacteristicChanged(...)`: Raw GATT characteristic byte notification.
  * `AntPluginPcc.IPluginAccessResultReceiver`: ANT+ channel establishment and event delivery.
  * `LocationListener.onLocationChanged(Location)`: GPS / Fused Location coordinate packet.
* **Data Payloads / Schemas**:
  * Bluetooth SIG GATT byte streams (Heart Rate Measurement `0x2A37`, Cycling Power `0x2A63`, RSC `0x2A53`).
  * Android `android.location.Location` objects (latitude, longitude, altitude, speed, bearing, accuracy).
* **Concurrency & Threading**:
  * Callbacks received on OS binder/driver threads; immediately dispatched into service worker pipelines.
* **Error Handling & Fault Tolerance**:
  * Exponential backoff for GATT reconnection; automated fallback to secondary sensors when primary packets timeout (> 3.5s).
  * Runtime permission checks (`ACCESS_FINE_LOCATION`, `BLUETOOTH_SCAN`) prevent security crashes.

### 2.5 IF-SWE-05: Services & Repositories to Domain Calculators
* **Provider**: Pure JVM Domain Calculators (`AutoPauseManager`, `PowerPhysiologyCalculator`, `PowerHrDynamicsCalculator`, `WorkoutClusterEngine`, `ClimbDetector`).
* **Consumer**: `TrackerService`, `WorkoutRepository`, `LiveSegmentsRepository`.
* **Operations / Method Signatures**:
  * `fun evaluateTick(currentSpeedMps: Double?, currentTimestampMs: Long): AutoPauseTransition`: Evaluates debounce auto-pause (`AutoPauseManager`).
  * `fun calculateNormalizedPower(powerSamplesWatts: List<Double>, sampleIntervalSec: Double): Double`: Computes 30s Coggan rolling weighted power (`PowerPhysiologyCalculator`).
  * `fun calculate(powerSamples: List<Double>, hrSamples: List<Double>, workoutId: Long, dtSec: Double): CardioPowerDynamicsRecord?`: Identifies 1st-order Output-Error dynamical Power-HR parameters and cardiac drift (`PowerHrDynamicsCalculator`).
  * `fun suggestCluster(start: LatLng, end: LatLng, apex: LatLng, distance: Double, candidateSportTypes: Set<BSportType>): WorkoutCluster?`: Spatial route cluster suggestion for completed workout (`WorkoutClusterEngine`).
  * `fun findApexFromPoints(start: LatLng, points: List<LatLng>, end: LatLng?): LatLng`: Determines furthest apex waypoint from route polyline (`WorkoutClusterEngine`).
  * `fun detectClimbs(points: List<ElevationPoint>): List<Climb>`: Detects steep climb segments from workout elevation profile (`ClimbDetector`).
* **Data Payloads / Schemas**:
  * `AutoPauseTransition`, `CardioPowerDynamicsRecord`, `WorkoutCluster`, `LatLng`, `ElevationPoint`, `Climb`, and Skiba $W'_{\text{bal}}$ parameters.
* **Concurrency & Threading**:
  * Synchronous execution on caller thread; stateless mathematical calculations ensuring strict re-entrancy and thread safety.
* **Error Handling & Fault Tolerance**:
  * Guarded against division-by-zero (`Double.NaN` / `Double.POSITIVE_INFINITY` prevention).
  * Post-trim emptiness checks in moving-average and sliding-window accumulators.

### 2.6 IF-SWE-06: TrackerService & Repositories to Persistence Layer (SQLite)
* **Provider**: SQLite Database Managers (`WorkoutSamplesDatabaseManager`, `WorkoutSummariesDatabaseManager`, `LapsDatabaseManager`, `WorkoutClusterDatabaseManager`).
* **Consumer**: `TrackerService`, `WorkoutRepository`, `KnownLocationsRepository`.
* **Operations / Method Signatures**:
  * `fun recordLiveSample(fileBaseName: String, timestampMs: Long, sampleValues: Map<SensorType, Double>, isAutoPaused: Boolean)`: 1-Hz bulk time-series insert (`WorkoutRepository` / `WorkoutSamplesDatabaseManager`).
  * `fun createNewTable(workoutName: String, sensorTypes: List<SensorType>)`: Creates dynamic SQLite table for new workout telemetry (`WorkoutSamplesDatabaseManager`).
  * `fun updateWorkoutData(workoutData: WorkoutData)`: Atomic update of aggregate workout summary record (`WorkoutSummariesDatabaseManager`).
  * `fun insertCluster(cluster: WorkoutCluster): Long`: Inserts spatial route cluster definition (`WorkoutClusterDatabaseManager`).
  * `fun saveCardioPowerDynamics(workoutId: Long, record: CardioPowerDynamicsRecord): Long`: Persists identified dynamical parameters (`WorkoutRepository`).
* **Data Payloads / Schemas**:
  * Android `ContentValues`, typed SQLite tables, `WorkoutData`, `WorkoutCluster`, `CardioPowerDynamicsRecord`.
* **Concurrency & Threading**:
  * `KnownLocationsRepository` utilizes dedicated single-threaded `dbDispatcher`.
  * Other database managers route queries via `Dispatchers.IO` using SQLite WAL mode and synchronized transaction blocks.
* **Error Handling & Fault Tolerance**:
  * Automated resource reclamation via `use {}` and `try-with-resources`.
  * Non-destructive schema migration (`onUpgrade` parity) preventing table drops or historical telemetry data loss.

### 2.7 IF-SWE-07: Repository Layer to File Exporters
* **Provider**: Serializers and File Writers (`FitFileWriter`, `TCXFileWriter`, `GPXFileWriter` extending `BaseFileWriter` / `BaseExporter`).
* **Consumer**: `ExportManager`, `WorkoutRepository`.
* **Operations / Method Signatures**:
  * `fun export(exportInfo: ExportInfo): ExportResult`: Executes streaming file generation and serialization (`BaseExporter`).
  * `suspend fun exportWorkoutTo(workoutId: Long, fileFormat: FileFormat)`: Orchestrates workout export and writer dispatch (`WorkoutRepository`).
* **Data Payloads / Schemas**:
  * `ExportInfo`, `ExportResult`, `FileFormat`, `WorkoutExportHeader`.
* **Concurrency & Threading**:
  * Executed asynchronously on `Dispatchers.IO` or scheduled background `WorkManager` workers.
* **Error Handling & Fault Tolerance**:
  * Two-phase atomic writes using `.tmp` files to prevent partial or corrupted file artifacts upon process termination.

### 2.8 IF-SWE-08: Repository Layer to Cloud APIs & Health Connect
* **Provider**: External Network Clients (`StravaUploader`, `DropboxUploader`, `HealthConnectManager`, `HealthConnectHrvImporter`).
* **Consumer**: `ExportManager`, `TrainingApplication`, `EditWorkoutScreen`, `HrvReadinessRepository`.
* **Operations / Method Signatures**:
  * `fun export(exportInfo: ExportInfo): ExportResult`: Direct OkHttp REST multipart upload (`StravaUploader`) or Dropbox API v2 export (`DropboxUploader`).
  * `suspend fun syncWorkout(context: Context, workoutData: WorkoutData): Result<Unit>`: Idempotent session and telemetry sync to Android Health Connect (`HealthConnectManager`).
  * `suspend fun importRecentHrv(context: Context, daysBack: Int): Result<Int>`: Nocturnal resting HRV and HR ingestion (`HealthConnectHrvImporter`).
* **Data Payloads / Schemas**:
  * `ExportInfo`, `ExportResult`, `WorkoutData`, `HealthConnectClient`.
* **Concurrency & Threading**:
  * Asynchronous coroutines executed on `Dispatchers.IO` via OkHttp and AndroidX Health Connect Client.
* **Error Handling & Fault Tolerance**:
  * Automated token expiration handling and silent token refresh.
  * Exponential backoff retry policies for network failures; Health Connect permission checking prior to invocation.
