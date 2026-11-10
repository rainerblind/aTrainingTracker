# Software Interface Specifications: aTrainingTracker (ASPICE SWE.2.BP2)

This document formalizes the software interface specifications of **aTrainingTracker** in accordance with **Automotive SPICE (ASPICE v3.1 / v4.0) SWE.2 Base Practice 2 (Specify interfaces of software elements)**.

Each architectural interface defines its provider, consumer, operations, data schemas, threading contracts, and error handling protocols.

---

## 1. Interface Specification Matrix

| Interface ID | Boundary | Provider Element | Consumer Element | Protocol / Mechanism | Threading Contract |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `IF-SWE-01` | UI $\to$ ViewModel | ViewModels (`TrackingViewModel`, `AftermathViewModel`) | Jetpack Compose Composables | Unidirectional MVI / `StateFlow` | `Dispatchers.Main` |
| `IF-SWE-02` | ViewModel $\to$ Repository | Repositories (`WorkoutRepository`, `BANALServiceRepository`) | ViewModels | Asynchronous Coroutines (`suspend fun`) | `Dispatchers.IO` |
| `IF-SWE-03` | Inter-Service Ingestion | `BANALServiceComm` (Local Binder on `BANALService`) | `TrackerService` | Android Local Binder IPC & Broadcasts | Service Thread / Binder Pool |
| `IF-SWE-04` | Hardware $\to$ Services | Hardware HAL (BLE Gatt, ANT+ Plugin, Location Provider) | `BANALService`, `TrackerService` | System Callbacks (`onCharacteristicChanged`) | Hardware Callback Thread |
| `IF-SWE-05` | Service/Repo $\to$ Domain | Domain Calculators (`AutoPauseManager`, `ClimbDetector`) | Services & Repositories | Pure JVM Synchronous Methods | Caller Thread (`Dispatchers.IO`) |
| `IF-SWE-06` | Service/Repo $\to$ SQLite | Database Managers (`WorkoutSamplesDatabaseManager`) | `TrackerService`, Repositories | SQLite WAL Mode & Synchronized Transactions | `dbDispatcher` / `Dispatchers.IO` |
| `IF-SWE-07` | Repository $\to$ Exporters | Exporters (`FitFileWriter`, `GPXFileWriter`, `TCXFileWriter`) | `ExportManager`, `WorkoutRepository` | Streaming File Writers & Serializers | `Dispatchers.IO` (WorkManager) |
| `IF-SWE-08` | Repository $\to$ Cloud APIs | Cloud Endpoints (`StravaUploader`, `HealthConnectManager`) | `ExportManager`, UI Controllers | REST (OkHttp/Retrofit) & HealthConnectClient | `Dispatchers.IO` (WorkManager) |

---

## 2. Detailed Software Interface Specifications

### 2.1 IF-SWE-01: Presentation Layer UI to ViewModel (Jetpack Compose MVI)
* **Provider**: Lifecycle ViewModels (`TrackingViewModel`, `ControlTrackingViewModel`, `AftermathViewModel`, `MapFragmentWithTrackViewModel`).
* **Consumer**: Declarative Jetpack Compose Composables (`TrackingTabsScreen`, `WorkoutDetailScreen`, `TrackOnMapScreen`).
* **Operations / Method Signatures**:
  * `val uiState: StateFlow<UiState>`: Observable read-only stream of immutable UI state.
  * `fun onAction(action: UiAction)`: Unidirectional event dispatch (e.g. `onStartClick()`, `onPauseClick()`, `onDiscardClick()`).
* **Data Payloads / Schemas**:
  * Immutable Kotlin data classes (`TrackingUiState`, `AftermathUiState`, `LocationUiState`).
  * Telemetry primitives formatted via canonical display utilities (`DistanceFormatter`, `AltitudeFormatter`, `SpeedFormatter`).
* **Concurrency & Threading**:
  * State emission collected on `Dispatchers.Main` via `collectAsStateWithLifecycle()`.
  * ViewModel coroutines operate under `viewModelScope` with cancellation bound to UI lifecycle.
* **Error Handling & Fault Tolerance**:
  * Flow exceptions intercepted via `.catch { emit(ErrorState) }`.
  * Zero `MutableState` mutations during composition; defensive fallbacks for missing or uninitialized sensors.

### 2.2 IF-SWE-02: ViewModel to Repository Layer
* **Provider**: Domain Repositories (`WorkoutRepository`, `BANALServiceRepository`, `SportTypesRepository`, `LiveSegmentsRepository`).
* **Consumer**: ViewModels (`TrackingViewModel`, `EquipmentViewModel`, `AftermathViewModel`).
* **Operations / Method Signatures**:
  * `suspend fun getWorkout(workoutId: Long): Workout?`: Asynchronous entity query.
  * `suspend fun saveWorkout(workout: Workout): Long`: Transactional mutation.
  * `fun observeTelemetry(): StateFlow<SensorData<*>>`: Hot shared flow of sensor events.
* **Data Payloads / Schemas**:
  * Domain DTOs (`Workout`, `SportType`, `LiveSegmentData`, `ExtremaValues`).
* **Concurrency & Threading**:
  * All I/O operations execute under `Dispatchers.IO` using structured concurrency.
* **Error Handling & Fault Tolerance**:
  * Null-safe queries returning `null` or empty collections rather than throwing unchecked runtime exceptions.
  * Sealed `Result<T>` hierarchies for network-backed repository operations.

### 2.3 IF-SWE-03: Service Layer Inter-Service Communication (TrackerService $\leftrightarrow$ BANALService)
* **Provider**: `BANALServiceComm` (Local Binder exposed by `BANALService`).
* **Consumer**: `TrackerService`.
* **Operations / Method Signatures**:
  * `fun getAllFilteredSensorData(): List<SensorData<*>>`: 1-Hz batch poll of active physical/virtual sensors.
  * `fun getBestSensorData(type: SensorType): SensorData<*>?`: Priority-resolved single metric query.
  * `Intent(RESET_ACCUMULATORS_INTENT)` / `Intent(REQUEST_PAUSE_TRACKING)`: IPC action broadcast signaling.
* **Data Payloads / Schemas**:
  * `SensorData<T>` generics holding timestamp, raw value, smoothed value, and sensor address.
  * Filter configuration models (`FilterData`).
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
* **Provider**: Pure JVM Domain Calculators (`AutoPauseManager`, `PowerPhysiologyCalculator`, `WorkoutClusterEngine`, `ClimbDetector`).
* **Consumer**: `TrackerService`, `WorkoutRepository`, `LiveSegmentsRepository`.
* **Operations / Method Signatures**:
  * `fun evaluateTick(speedMps: Double, timestampMs: Long): AutoPauseTransition`: Evaluates debounce auto-pause.
  * `fun calculateNormalizedPower(samples: List<Double>): Double`: Computes 30s Coggan rolling weighted power.
  * `fun findOrCreateCluster(track: Polyline): ClusterResult`: Spatial DBSCAN line matching.
* **Data Payloads / Schemas**:
  * Floating-point primitive arrays, coordinate lists (`List<GeoPoint>`), Skiba $W'_{\text{bal}}$ parameters.
* **Concurrency & Threading**:
  * Synchronous execution on caller thread; stateless mathematical calculations ensuring strict re-entrancy and thread safety.
* **Error Handling & Fault Tolerance**:
  * Guarded against division-by-zero (`Double.NaN` / `Double.POSITIVE_INFINITY` prevention).
  * Post-trim emptiness checks in moving-average and sliding-window accumulators.

### 2.6 IF-SWE-06: TrackerService & Repositories to Persistence Layer (SQLite)
* **Provider**: SQLite Database Managers (`WorkoutSamplesDatabaseManager`, `WorkoutSummariesDatabaseManager`, `LapsDatabaseManager`, `ClustersDatabaseManager`).
* **Consumer**: `TrackerService`, `WorkoutRepository`, `KnownLocationsRepository`.
* **Operations / Method Signatures**:
  * `fun insertSamplesBatch(samples: List<SampleRecord>)`: 1-Hz bulk time-series insert.
  * `fun updateWorkoutSummary(summary: WorkoutSummary)`: Atomic update of aggregates.
* **Data Payloads / Schemas**:
  * Android `ContentValues`, typed SQLite tables, and raw cursor extractions.
* **Concurrency & Threading**:
  * `KnownLocationsRepository` utilizes dedicated single-threaded `dbDispatcher`.
  * Other database managers route queries via `Dispatchers.IO` using SQLite WAL mode and synchronized transaction blocks.
* **Error Handling & Fault Tolerance**:
  * Automated resource reclamation via `use {}` and `try-with-resources`.
  * Non-destructive schema migration (`onUpgrade` parity) preventing table drops or historical telemetry data loss.

### 2.7 IF-SWE-07: Repository Layer to File Exporters
* **Provider**: Serializers and File Writers (`FitFileWriter`, `TCXFileWriter`, `GPXFileWriter`).
* **Consumer**: `ExportManager`, `WorkoutRepository`.
* **Operations / Method Signatures**:
  * `fun writeFitFile(workout: Workout, output: OutputStream)`: Streaming binary FIT encoding.
  * `fun writeTcxFile(workout: Workout, writer: Writer)`: XML TCX serialization.
  * `fun writeGpxFile(workout: Workout, writer: Writer)`: XML GPX track serialization.
* **Data Payloads / Schemas**:
  * Structured DTO models, FIT protocol binary structures, XML schema-compliant trees.
* **Concurrency & Threading**:
  * Executed asynchronously on `Dispatchers.IO` or scheduled background `WorkManager` workers.
* **Error Handling & Fault Tolerance**:
  * Two-phase atomic writes using `.tmp` files to prevent partial or corrupted file artifacts upon process termination.

### 2.8 IF-SWE-08: Repository Layer to Cloud APIs & Health Connect
* **Provider**: External Network Clients (`StravaUploader`, `DropboxUploader`, `HealthConnectManager`).
* **Consumer**: `ExportManager`, `TrainingApplication`, `EditWorkoutScreen`.
* **Operations / Method Signatures**:
  * `suspend fun uploadToStrava(file: File, sportType: SportType): UploadResponse`: REST multipart upload.
  * `suspend fun syncHealthConnect(workout: Workout, samples: List<Sample>): SyncResult`: Health Connect insertion.
* **Data Payloads / Schemas**:
  * Multipart form-data bodies, OAuth 2.0 bearer tokens, Health Connect `ExerciseSessionRecord` and series.
* **Concurrency & Threading**:
  * Asynchronous coroutines executed on `Dispatchers.IO` via OkHttp/Retrofit and AndroidX Health Connect Client.
* **Error Handling & Fault Tolerance**:
  * Automated token expiration handling and silent token refresh.
  * Exponential backoff retry policies for network failures; Health Connect permission checking prior to invocation.
