# Software Architecture: aTrainingTracker (ASPICE SWE.2)

This document establishes the comprehensive software architectural design and component interfaces of **aTrainingTracker**, fulfilling the **Automotive SPICE (ASPICE v3.1 / v4.0) SWE.2** standard across Base Practices `BP.1` through `BP.5`.

---

## 1. Architectural Overview & Component Model (SWE.2.BP1, BP2)

aTrainingTracker is engineered as an offline-first, reactive Android fitness tracking platform combining real-time hardware telemetry ingestion (ANT+ and Bluetooth Low Energy) with high-performance Jetpack Compose user interfaces, multi-layer SQLite persistence, and mathematical domain engines.

```mermaid
graph TD
    subgraph UI_Layer["1. Presentation Layer (Jetpack Compose & MVI)"]
        NavHost["Compose NavHost (Single Activity Architecture)"]
        Screens["Screens & Composables (Cockpit, Maps, Aftermath, Settings)"]
        ViewModels["Lifecycle ViewModels (Tracking, Aftermath, Clusters, Devices)"]
    end

    subgraph Domain_Layer["2. Domain & Scientific Calculators (SWE.3)"]
        Physiology["PowerPhysiologyCalculator, DynamicalPowerHrEngine & SlidingWindowPowerAccumulator (FTP, NP, TSS, IF, MMP, Power-HR OE Dynamics)"]
        Climbs["ClimbDetector & PolylineProgressTracker"]
        Clusters["WorkoutClusterEngine & SpatialRegionEngine"]
        AutoPause["AutoPauseManager (Debounce & State Machine)"]
    end

    subgraph Service_Layer["3. Background Services (Android Foreground Services)"]
        TrackerService["TrackerService (Session State, Sample Recording, GPS)"]
        BANALService["BANALService (ANT+ & BLE Sensor Ingestion, ProxySensor)"]
    end

    subgraph Data_Layer["4. Data Layer & Reactive Repositories"]
        WorkoutRepo["WorkoutRepository & Flow Data Sources"]
        BANALRepo["BANALServiceRepository (Shared StateFlows)"]
        SettingsRepo["TuningPreferencesDataStore & SettingsDataStore"]
    end

    subgraph Persistence_Layer["5. Persistence & Cloud Layer"]
        SamplesDB["WorkoutSamples.db (1-Hz Time-Series Data)"]
        SummariesDB["WorkoutSummaries.db (Extrema & Aggregates)"]
        LapsDB["Laps.db & Clusters.db (Splits & Lieblingsstrecken)"]
        Exporters["FitFileWriter, GPXFileWriter, TCXFileWriter, StravaUploader, HealthConnectManager, HealthConnectRecordMapper, HealthConnectHrvImporter"]
    end

    subgraph Hardware_Layer["6. Hardware & External Peripherals"]
        BLE["Bluetooth LE Sensors (HRM, Power, Cadence, Radar)"]
        ANT["ANT+ Hardware Dongle / Internal Chipset"]
        Sensors["GNSS / GPS, Barometer, Accelerometer"]
    end

    NavHost --> Screens
    Screens --> ViewModels
    ViewModels --> WorkoutRepo
    ViewModels --> BANALRepo
    ViewModels --> SettingsRepo

    WorkoutRepo --> Domain_Layer
    TrackerService --> Domain_Layer
    TrackerService --> SamplesDB
    TrackerService --> SummariesDB
    TrackerService --> LapsDB

    BANALService --> BANALRepo
    TrackerService <--> BANALService

    Hardware_Layer --> BANALService
    Hardware_Layer --> TrackerService

    WorkoutRepo --> Exporters
```

### 1.1 Architectural Layers & Subsystems
1. **Presentation Layer (Compose MVI)**: Single-activity architecture (`MainActivityWithNavigation`) hosting a declarative Compose `NavHost`. UI components observe unidirectional `StateFlow` streams from ViewModels with zero direct Android framework service binding.
2. **Domain & Scientific Engine Layer**: Pure JVM mathematical models executing signal filtering (α-exponential smoothing, moving averages), physiological modeling (Normalized Power, Training Stress Score, Intensity Factor, Zone distributions), spatial clustering (Lieblingsstrecken / DBSCAN line clustering), and climb categorization (`ClimbDetector`).
3. **Service Layer**: Two specialized Android foreground services (`location|health` foreground service types):
   - `BANALService`: Manages ANT+ and BLE peripheral lifecycles, connection arbitration, and sensor failover.
   - `TrackerService`: Coordinates session recording, active/pause timers, 1-Hz high-precision database serialization, and crash recovery.
4. **Data & Repository Layer**: Exposes conflated reactive flows, in-memory caching, and asynchronous mutation channels. Decouples UI controllers from SQLite storage.
5. **Persistence & Serialization Layer**: Specialized SQLite database managers utilizing WAL mode and serialized dispatchers, complemented by streaming binary FIT, XML GPX, and TCX exporters and background cloud upload workers.

---

## 2. Requirement Allocation to Architecture (SWE.2.BP3)

All system requirements across the 9 living domain shards are allocated directly to corresponding architectural subsystems:

| Domain Shard | Requirement Prefix Families | Allocated Architectural Components | Primary Responsibilities |
| :--- | :--- | :--- | :--- |
| **Tracking & Live Sensor Cockpit** | `CON`, `FIL`, `FUSION`, `SEN`, `TRK`, `WEA` | `BANALService`, `TrackerService`, `ProxySensor`, `MySensorManager`, `AutoPauseManager`, `CyclingPowerMetricsDevice`, `SlidingWindowPowerAccumulator`, `LiveMmpCurveChart`, `HRVMetricsDevice`, `RrArtifactFilter`, `DfaCalculator`, `HrvSignalQualityAnalyzer` | ANT+/BLE telemetry ingestion, sensor fusion, auto-pause debounce, 1-Hz sample recording, live MMP curve & duration benchmarks, real-time HRV metric calculation, artifact filtering & firmware smoothing/synthetic signal quality analysis. |
| **Post-Workout Analytics & Periods** | `PER`, `PERF` | `WorkoutRepository`, `PowerPhysiologyCalculator`, `ZoneDistributionCalculator`, `PeriodsRepository`, `HrvReadinessRepository`, `HrvReadinessDatabaseManager`, `HrvReadinessCalculator`, `HealthConnectHrvImporter`, `MorningHrvCheckViewModel`, `HrvReadinessTrendMath`, `HrvReadinessTrendCard`, `HrvReadinessTrendChart`, `WorkoutRrRepository`, `WorkoutRrDatabaseManager`, `HrvPostWorkoutRecomputer`, `PowerHrDynamicsCalculator`, `PowerHrDynamicsInfoDialog` | Physiological computation (NP, TSS), 5/7 power zone distributions, period statistics & trend filters, morning HRV readiness persistence & per-posture SWC baseline analytics, nocturnal Health Connect HRV ingestion, guided morning resting HRV protocol, longitudinal HRV readiness trend charts in Periods screen, lossless workout RR intervals persistence and centered post-workout HRV recomputation, 1st-order dynamical Power-HR system identification & explanation sheet. |
| **Maps, Polylines & Navigation** | `GEO`, `MAP`, `NAV`, `ROU` | `ClimbDetector`, `PolylineProgressTracker`, `MapFragmentWithTrackViewModel`, `ATrainingTrackerMap` | Web Mercator tile rendering, polyline compression, climb categorization, live segment gate matching. |
| **Persistence, Migration & Cloud Export** | `DAT`, `EXP`, `EXT`, `MIG` | `WorkoutSamplesDatabaseManager`, `WorkoutSummariesDatabaseManager`, `WorkoutRrDatabaseManager`, `HrvReadinessDatabaseManager`, `FitFileWriter`, `GPXFileWriter`, `TCXFileWriter`, `StravaUploader`, `GoogleDriveBackupManager` | Time-series SQLite storage, workout RR intervals storage, morning HRV readiness SQLite storage, schema migration, Strava OAuth/upload retry, FIT/TCX/GPX serialization with native Garmin FIT HrvMesg export/import. |
| **Settings, Profiles & Localization** | `LOC`, `MON`, `PRI`, `SET` | `TuningPreferencesDataStore`, `EquipmentDbHelper`, `EquipmentRepository`, `ProFeatureManager`, `SupporterTier`, `GarageLeagueDetector`, `HealthConnectManager`, `HealthConnectRecordMapper`, `SensorQuotaReconciliationManager` | DataStore persistence, 9-locale string parity, sensor quotas, monetization, vehicle equipment, Health Connect sync & record mapping. |
| **UI Components & Design System** | `UI`, `UNT` | Jetpack Compose theme (`ATrainingTrackerTheme`), `MetricItem`, `ElevationProfile`, `TelemetryMetricGraph`, `AppModalBottomSheet`, `WorkoutPhotoStrip`, `WorkoutTimePhotoPickerSheet`, `MorningHrvCheckScreen` | High-contrast AMOLED theming, responsive landscape layouts, chart scrubbing, reusable Compose components, chronological workout photo selection, guided morning resting HRV acquisition. |
| **Engineering Process & Stability** | `ARC`, `BLD`, `BUG`, `INT`, `LIV`, `LOG`, `MAN`, `NFR`, `PRO`, `REG`, `SEC`, `STB`, `STR`, `TOOL`, `TST` | ASPICE Dual-Agent Quality Gates, `jira_util.py`, `verify_requirement_governance.py`, ProGuard rules | CI/CD build hygiene, Chesterton's fence archaeology, crash resilience, process kill simulation. |
| **Jubilee Milestones & App Reviews** | `REV` | `MilestoneAutoNamingManager`, `FirstJubileeCelebrationDialog`, `FirstJubileeActionHelper` | Milestone counting, achievement celebration, Google Play in-app review prompting. |
| **Store Presence** | `STP` | `PlayStoreListingContractTest`, Google Play assets, screenshots | Google Play store presence parity, localized descriptions, automated metadata validation. |

---

## 3. Dynamic Behavior & State Machines (SWE.2.BP4)

### 3.1 Workout Tracking Lifecycle & Crash Recovery
The recording state machine transitions deterministically across top-level `TrackingMode` states: `IDLE`, `READY`, `TRACKING`, and `PAUSED`. Internal pause sub-states are delegated to `AutoPauseManager` / `AutoPauseState` (`TRACKING`, `PAUSE_DEBOUNCE_PENDING`, `AUTO_PAUSED`, `MANUAL_PAUSED`, `SENSOR_LOST`). Persistent database state enables active session recovery upon unexpected process termination.

```mermaid
stateDiagram-v2
    [*] --> IDLE: App Initialization
    IDLE --> READY: Service Bound & Sensors Pre-Warmed
    READY --> TRACKING: User Press Start / Auto-Start
    TRACKING --> PAUSED: User Press Pause / Auto-Pause (AutoPauseManager)
    PAUSED --> TRACKING: User Press Resume / Auto-Resume
    TRACKING --> READY: Crash Recovery (Session State Re-seeded)
    PAUSED --> IDLE: User Press Finish / Discard (Session Finalized)
    TRACKING --> IDLE: User Press Finish / Session Finalized
    IDLE --> [*]
```

### 3.2 Dual-Protocol Sensor Arbitration & Failover
`ProxySensor` dynamically resolves the highest-accuracy telemetry source among competing physical interfaces (ANT+, Bluetooth Low Energy, Smartphone Internal Sensors, and DEM lookup).

```mermaid
sequenceDiagram
    autonumber
    participant HW as Hardware (BLE / ANT+)
    participant BS as BANALService
    participant PS as ProxySensor
    participant TS as TrackerService
    participant UI as Cockpit UI (Compose)

    HW->>BS: Raw Telemetry Packet (Power / Cadence / Speed)
    BS->>BS: Parse Profile & Update Signal RSSI
    BS->>PS: Deliver Metric Stream
    alt Primary Source Active (e.g. Dual-Sided BLE Power Meter)
        PS->>PS: Apply Exponential Smoothing (α-filter)
    else Primary Lost / Packet Timeout (> 3.5s)
        PS->>PS: Switch to Fallback Source (e.g. ANT+ Single-Sided)
    end
    PS->>TS: Emit Unified SensorData<T>
    TS->>TS: 1-Hz Accumulation (Energy / Distance / Laps)
    TS-->>UI: StateFlow Update (Conflated 1-Hz UI Frame)
```

### 3.3 Post-Workout Persistence, Route Clustering & Cloud Export
Upon session completion, the recording pipeline persists all time-series samples and metadata asynchronously, triggers route clustering, and queues background cloud synchronization.

```mermaid
sequenceDiagram
    autonumber
    participant TS as TrackerService
    participant DB as SQLite Databases (Samples & Summaries)
    participant RLE as WorkoutClusterEngine (Clusters.db)
    participant EXP as Export & Cloud Sync Pipeline
    participant STR as Strava API / Google Drive

    TS->>DB: Write Final Time-Series & Lap Records (Dispatchers.IO)
    TS->>DB: Compute Extrema (Max Speed, Max HR, NP, TSS)
    TS->>RLE: Submit GPS Track Polyline
    RLE->>RLE: Match against Existing Lieblingsstrecken (Start/Apex/End)
    alt New Cluster Detected
        RLE->>DB: Persist New Route Cluster
    else Matching Cluster Found
        RLE->>DB: Associate Workout with Cluster ID
    end
    TS->>EXP: Trigger Auto-Export Workers (WorkManager)
    EXP->>EXP: Build Binary .FIT and XML .TCX/.GPX Files
    EXP->>STR: Execute OAuth Refresh & Multipart Upload
    STR-->>EXP: 201 Created / Upload Polling ID
```

---

## 4. Concurrency, Threading & Resource Invariants (SWE.2.BP5)

### 4.1 Coroutine Dispatcher Separation
To prevent UI jank, thread contention, and Android SQLite database lock errors, execution threads are strictly partitioned:
- **`Dispatchers.Main`**: Exclusively dedicated to Jetpack Compose rendering, UI animations, and immediate state consumption. Strictly zero file I/O or SQLite database operations.
- **`Dispatchers.IO`**: Network operations (Strava API, Google Drive, TCX downloads), filesystem exports, JSON file parsing, and general SQLite database operations across `WorkoutRepository`, `WorkoutSamplesDatabaseManager`, `WorkoutSummariesDatabaseManager`, and `ClimbsDatabaseManager` leveraging SQLite WAL (Write-Ahead Logging) and transaction locks.
- **`dbDispatcher` (`Executors.newSingleThreadExecutor().asCoroutineDispatcher()`)**: Dedicated single-threaded dispatcher utilized in `KnownLocationsRepository` to serialize location database mutations and prevent SQLite lock contention.

### 4.2 Foreground Service & Permission Governance
- `TrackerService` and `BANALService` run as explicit Android Foreground Services with `location|health` foreground service types declared in `AndroidManifest.xml`.
- Runtime permissions (`ACCESS_FINE_LOCATION`, `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BODY_SENSORS`) are guarded defensibly on startup; missing permissions gracefully degrade telemetry without crash loops or unhandled exceptions (`REQ-STB-008`).

### 4.3 SQLite Schema Migration & Data Integrity Invariants
- All SQLite databases employ an idempotent schema upgrade architecture (`onUpgrade` parity).
- Schema alterations execute non-destructive `ALTER TABLE ADD COLUMN` operations within defensive `try-catch` blocks checking existing column presence via `PRAGMA table_info`.
- Under no circumstances does schema migration drop user tables or purge historical telemetry records.

### 4.4 Status Bar Notification Architecture & Monolithic Density Packaging (`REQ-UI-452`)
- **Monochrome Status Bar Silhouette**: All foreground service and application status bar notifications (`TrackerService`, `TrainingApplication`, `WorkoutDeletionNotificationManager`, `ExportNotificationManager`, `SensorBatteryNotificationManager`) route through dedicated density-independent vector asset `ic_stat_notify_logo.xml` (24x24dp, `#FFFFFF` with alpha channel). Colored multi-density raster PNGs are prohibited in `.setSmallIcon(...)` to prevent `SystemUI` Binder IPC `RemoteServiceException` / `BadForegroundServiceNotificationException` crashes.
- **Monolithic Density App Bundle Packaging**: In `app/build.gradle`, `bundle { density { enableSplit = false } }` is enforced to ensure all drawable resources remain packaged inside `base.apk`, preventing Split-APK resource desynchronization across Android devices.

### 4.5 Paired Bluetooth LE Address Resolution, Scan-Assisted Connection & Search Lifecycle (`REQ-CON-023`)
- **Random Static MAC Classification & Address Type**: Unbonded BLE fitness peripherals commonly utilize Random Static MAC addresses (identifiable by bits 47..46 = `11b`, i.e. `(firstOctet & 0xC0) == 0xC0`). When Android's `BluetoothRemoteDevices` cache is empty (e.g. after phone reboot or Bluetooth toggle), calling `getRemoteDevice(mAddress)` defaults to `ADDRESS_TYPE_PUBLIC`, causing peripherals to ignore `CONNECT_IND`. On API 33+, `MyBTLEDevice` explicitly invokes `BluetoothAdapter.getRemoteLeDevice(mAddress, ADDRESS_TYPE_RANDOM)`.
- **Active Scan-Assisted Cache Warming**: `MyBTLEDevice` executes a lightweight, address-filtered `BluetoothLeScanner` scan during `startSearching()` (permission-gated by `BLUETOOTH_SCAN` on API 31+ or `BLUETOOTH_ADMIN` / `ACCESS_FINE_LOCATION` on API < 31). The scan is unconditionally stopped on GATT connection (`STATE_CONNECTED`), timeout, manual disconnect, or device shutdown.
- **Configurable Search Duration**: Sequential search in `DeviceManager` honors `TrainingApplication.getBtleSearchPeriodMs()` (range $3\text{--}15$s, default 5s) instead of legacy hardcoded 10s intervals, preventing head-of-line blocking when paired sensors are offline.

### 4.6 Chronological MediaStore Querying & Workout Photo Picker Permissions (`REQ-UI-455`)
- **Temporal Window Querying**: Runtime media read permissions (`READ_MEDIA_IMAGES` on API 33+, `READ_MEDIA_VISUAL_USER_SELECTED` on API 34+, and `READ_EXTERNAL_STORAGE` on API <= 32) enable `WorkoutPhotoManager` to pre-filter local photos matching the workout time window `[startTimeMs - 5m .. endTimeMs + 5m]` via `MediaStore.Images.Media.EXTERNAL_CONTENT_URI` using `DATE_TAKEN` with fallback to `DATE_ADDED * 1000L`.
- **Zero-Dead-End Fallback Architecture**: In compliance with Android privacy invariants, permission denial or zero matching images seamlessly and automatically triggers fallback to `ActivityResultContracts.PickMultipleVisualMedia()`, ensuring zero dead-ends and allowing athletes to browse their full gallery without permissions.
- **Rule 73 Stacked Button Ergonomics**: `WorkoutTimePhotoPickerSheet` is presented via `AppModalBottomSheet(scrollable = false)` hosting a 3-column `LazyVerticalGrid` and vertically stacked full-width CTAs ("Hinzufügen (<count>)" and "Alle Bilder anzeigen"), eliminating horizontal button starvation in German and other localized viewports.

### 4.7 Subscription Downgrade Sensor Quota Reconciliation (`REQ-SET-084.2`, `REQ-SET-084.5`, `REQ-SET-084.7`)
- **Centralized Reconciliation Management**: `SensorQuotaReconciliationManager` governs sensor pairing quotas across the application, decoupling reconciliation logic from `DeviceListViewModel`.
- **Global Lifecycle Triggers & Presentation**: Evaluates paired sensor count against `MAX_FREE_PAIRED_DEVICES = 2` upon application start/resume (`MainActivityWithNavigation.onResume`), tracking cockpit entry (`TrackingTabsScreen`), reactive transitions to Free tier (`!isProActive()`), and pre-tracking actions in `ControlTrackingViewModel`. Presents `KeepTwoSensorsBottomSheet` globally within `ATrainingTrackerApp`.
- **Heuristic Default Preselection & Unpair Recommendation (`ATT-5490`)**:
  - `determineDefaultKeepDeviceIds` preselects `SPEED_AND_LOCATION_GOOGLE_FUSED` over `SPEED_AND_LOCATION_GPS` in Slot 1 when both location sensors are paired (preserving whichever single location sensor is paired if only one exists). Slot 2 prioritizes the most recently active external remote sensor (`protocol != Protocol.SMARTPHONE`), evaluating `isConnected == true` first and raw epoch millisecond `lastSeen` timestamps second. Only when no external remote sensors exist does the heuristic fall back to `ALTITUDE_FROM_PRESSURE` (Barometer) and remaining smartphone devices.
  - `determineRecommendedUnpairDeviceId` recommends unpairing `ALTITUDE_FROM_PRESSURE` first; if both Google Fused and GPS are paired, it recommends unpairing `SPEED_AND_LOCATION_GPS` next, ensuring the athlete's preferred Google Fused location source is retained.
- **In-Ride Suppression & Non-Destructive Pruning**: Reconciliation is strictly suppressed during active workout recording (`!isTracking()`). Unselected sensors are marked `PAIRED = 0` in persistence while custom names, calibration factors, wheel circumferences, and equipment links remain 100% intact.
- **Service-Level Hardware Connection Clamping**: As defense-in-depth, `DeviceManager.startSearchForPairedDevices()` clamps remote device creation to `MAX_FREE_PAIRED_DEVICES = 2` on Free tier to prevent connecting excess hardware prior to UI reconciliation.

### 4.8 Location Sensor Priority & Fresh-Installation Default Pairing (`REQ-TRK-024`)
- **Google Fused Priority Over GPS**: In `MySensorManager`, `DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED` strictly precedes `DeviceType.SPEED_AND_LOCATION_GPS` across `SPEED_PRIORITY`, `ALTITUDE_PRIORITY`, and `LOCATION_PRIORITY`. While dedicated physical wheel/footpod sensors (`RUN_SPEED`, `BIKE_SPEED`, `BIKE_POWER`) and barometric pressure sensors (`ALTITUDE_FROM_PRESSURE`) retain absolute precedence, Google Fused Location Provider delivers faster time-to-first-fix and greater trajectory stability than raw GPS when location-derived speed, distance, altitude, or coordinates are utilized.
- **Fresh Installation Default Pairing & De-Googled Fallback**: On fresh database initialization (`DevicesDatabaseManager.DevicesDbHelper.addLocationDevicesToDb`), Google Play Services availability is evaluated via `GooglePlayServicesUtil.isGooglePlayServicesAvailable(mContext) == ConnectionResult.SUCCESS`. If available, `SPEED_AND_LOCATION_GOOGLE_FUSED` is paired by default (`PAIRED = true`) and raw GPS is left unpaired (`PAIRED = false`). On de-Googled environments where Play Services is absent or unresolvable, the system deterministically falls back to pairing raw GPS (`PAIRED = true`). Existing installations and database upgrades preserve the athlete's persisted sensor pairing configuration without modification.

### 4.9 Resource and Timing Budgets (SWE.2.BP4, `REQ-PRO-052`, `REQ-NFR-003`..`REQ-NFR-014`)
In accordance with **ASPICE SWE.2 Base Practice 4 (Describe resource and timing budgets)**, the system establishes quantified resource consumption limits and latency budgets across runtime-critical subsystems to guarantee responsiveness, prevent thread starvation, and ensure all-day battery efficiency during multi-hour endurance sessions:

| Subsystem Domain | Critical Component | Resource / Timing Metric | Budget Ceiling | Allocation Rationale & Non-Functional Trace (REQ-NFR-*, ATT-6139) |
| :--- | :--- | :--- | :--- | :--- |
| **Tracking Core** | `TrackerService` | Periodic Tick Execution Latency | < 50 ms | `REQ-NFR-003`: 1000 ms periodic cycle (`scheduleAtFixedRate`) on background thread; guarantees 950 ms idle headroom. |
| **Tracking Core** | `TrackerService` | CPU Consumption | < 5% single-core | `REQ-NFR-004`: Mid-tier reference device; prevents CPU throttle and heat buildup. |
| **Tracking Core** | `TrackerService` | Dedicated Heap Footprint | < 40 MB | `REQ-NFR-005`: Prevents GC churn and OutOfMemoryError during 12+ hour recordings. |
| **Tracking Core** | `TrackerService` | Battery Consumption | < 5%/h | `REQ-NFR-006`: Background recording with screen off, active GPS, and BLE/ANT+ sensors. |
| **Sensor Layer** | `BANALService` / `ProxySensor` | Sensor Dispatch Latency | < 25 ms | `REQ-NFR-007`: Time from hardware event ingestion to listener notification across registered sensors. |
| **Sensor Layer** | `BANALService` / `ProxySensor` | Connection Failover Window | 3.5 s | `REQ-NFR-008`: Maximum timeout before initiating automatic failover or sensor reconnection. |
| **Tracking Core** | `TrackerService` | Power Management (WakeLock) | Bound to Tracking | `REQ-NFR-009`, `REQ-TRK-003`: `PARTIAL_WAKE_LOCK` (`aTrainingTracker:TrackingLock`) strictly acquired only during `TRACKING` and released immediately on `STOP` (`TrackerService.java:842-857`). |
| **Persistence** | `WorkoutSamplesDatabaseManager` | SQLite Sample Write Latency | < 30 ms | `REQ-NFR-010`: Live 1-Hz sample insertion; strictly executed off main thread via `TrackerService`'s `mScheduler` / `mDbExecutor` (`Dispatchers.IO` for repository/import writes; `dbDispatcher` strictly for `KnownLocationsRepository`). |
| **Persistence** | `WorkoutSamplesDatabaseManager` | Storage Growth Rate | ~360 KB/h | `REQ-NFR-011`: At 1-Hz telemetry recording (~100 bytes/sample); ~3.6 MB for a 10-hour endurance activity. |
| **Cockpit & UI** | `SensorGridScreen` | UI Frame Rendering Time | < 16.6 ms (60 FPS) | `REQ-NFR-012`: Compose telemetry grid recomposition bound to 16.6 ms budget without dropped frames. |
| **Cockpit & UI** | `ATrainingTrackerMap` | Map Polyline Update Latency | < 30 ms | `REQ-NFR-013`: Latency to update path geometry on live map canvas upon receiving location updates. |
| **Cockpit & UI** | `TrackingTabsViewModel` | Telemetry StateFlow Emission | 1 Hz (conflated) | `REQ-NFR-014`: Prevents recomposition churn on live cockpit tabs during high-frequency sensor updates. |

---

## 5. Software Interface Specifications (SWE.2.BP2)

In accordance with **ASPICE SWE.2 Base Practice 2 (Specify interfaces of software elements)**, all boundaries between presentation, domain, service, repository, persistence, and external layers are formally specified in [`docs/architecture/interfaces.md`](architecture/interfaces.md).

### 5.1 Software Interface Summary Matrix

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

For comprehensive operation signatures, payload schemas, concurrency rules, and fault-tolerance protocols, see the authoritative specification in [`docs/architecture/interfaces.md`](architecture/interfaces.md).

---

## 6. Software Integration and Verification Strategy (SWE.5.BP1)

In accordance with **Automotive SPICE (ASPICE v3.1 / v4.0) SWE.5 Base Practice 1 (Develop software integration strategy and software verification strategy)**, the complete software integration sequence across architectural interfaces (`IF-SWE-01` through `IF-SWE-08`), three hierarchical verification levels (Level 1 JVM/Robolectric, Level 2 Instrumented/Emulator, Level 3 Physical Hardware & Simulators), entry/exit criteria, and two-tier regression selection are formally specified in [`docs/architecture/integration_strategy.md`](architecture/integration_strategy.md).

### 6.1 Integration Sequence & Boundary Allocation

Software components are integrated bottom-up to validate database persistence and hardware ingestion layers prior to domain calculations and presentation bindings:

| Sequence Step | Integration Step ID | Architectural Interface | Subsystems Integrated | Verification Level | Mocked vs. Real Boundary |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Step 1 | `INT-SWE-06` | `IF-SWE-06` | Persistence & SQLite Managers | Level 1 & 2 | Real SQLite with temp/in-memory DB; WAL serialized via `dbDispatcher` (`KnownLocationsRepository`) / single-thread executor |
| Step 2 | `INT-SWE-04` | `IF-SWE-04` | Hardware HAL to Services | Level 1 & 3 | Mocked hardware via fake GPS & BLE simulators; real service listeners |
| Step 3 | `INT-SWE-03` | `IF-SWE-03` | TrackerService $\leftrightarrow$ BANALService | Level 1 & 2 | Real Android Binder IPC test harness; stubbed sensor streams |
| Step 4 | `INT-SWE-05` | `IF-SWE-05` | Domain Calculators & Engines | Level 1 | Pure JVM mathematical models with synthetic time-series fixtures |
| Step 5 | `INT-SWE-02` | `IF-SWE-02` | ViewModel to Repository Layer | Level 1 | Real repositories with test SQLite / fake DataStore; real coroutine scopes |
| Step 6 | `INT-SWE-07` | `IF-SWE-07` | Repositories to File Exporters | Level 1 | Real streaming binary/XML writers; mock storage volume |
| Step 7 | `INT-SWE-08` | `IF-SWE-08` | Repositories to Cloud & Health Connect | Level 1 | Authentic test doubles and repository fakes for REST endpoints; test doubles for HealthConnectClient |
| Step 8 | `INT-SWE-01` | `IF-SWE-01` | UI Composables to ViewModels | Level 1 & 2 | Jetpack Compose MVI with Robolectric Compose test rule |

For complete verification level definitions, entry/exit criteria, two-tier regression selection, and defect bisection workflows, see [`docs/architecture/integration_strategy.md`](architecture/integration_strategy.md).




