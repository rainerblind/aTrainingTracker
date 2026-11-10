# aTrainingTracker: Technical Architecture & Coding Standards

This document serves as the authoritative living source of truth for software engineering (SWE.1 / SWE.2) construction invariants, technical architecture patterns, and testing standards across **aTrainingTracker**.

---

## 1. Core Engineering Principles & Software Construction

### 1.1 Reuse Before Create & DRY (Shared Code, Utilities & Architecture Over Duplication) (Rule 36)
* **Overarching Invariant**:
  * Before designing or implementing a new helper method, repository, calculator, formatter, or data structure, developers and AI agents **MUST** inspect the existing codebase and reuse existing shared architectural components.
  * Always favor existing shared utilities, repositories, formatters, and reusable composables over writing parallel, ad-hoc, or duplicate code.
  * Look first in:
    * `com.atrainingtracker.trainingtracker.util`
    * `com.atrainingtracker.trainingtracker.formatters`
    * `com.atrainingtracker.trainingtracker.ui.common`
    * `com.atrainingtracker.trainingtracker.ui.components`
* **Canonical Formatters & Unit Parity**:
  * All distance, altitude, speed, pace, and time presentation across both classic View and Jetpack Compose layers **MUST** route through canonical formatters:
    * **Distance**: `DistanceFormatter`
    * **Altitude / Elevation**: `AltitudeFormatter` / `ElevationUtils`
    * **Speed**: `SpeedFormatter` / `SpeedUtils`
    * **Pace**: `PaceFormatter`
    * **Compose Telemetry**: `LocalMetricFormatter`
  * All unit math and unit system conversions **MUST** route through canonical conversion utilities:
    * `MyHelper.mps2userUnit()` and `MyHelper.UserUnit2mps()`
    * `SensorFilterPreferencesHelper`
  * **Strict Prohibition**: Inline manual string template formatting (e.g. `String.format("%.1f km", dist / 1000f)` or `"${meters} m"`) and manual unit math (e.g. multiplying by `3.6f` directly in UI or ViewModels) are **strictly forbidden**. Manual formatting creates subtle rounding discrepancies, ignores athlete imperial/metric preferences, and breaks localization parity.
* **Proactively Root Out Divergence**:
  * When modifying existing components, actively audit callers for parallel implementations. Consolidate divergent legacy formatting into the canonical formatters.

### 1.2 Chesterton's Fence for Empty Callback Lambdas & Gesture Contract Tests (Rule 34)
* **No Casual Callback Suppression**:
  * When refactoring screens, views, or container composables, developers and AI agents **MUST NOT** pass an empty lambda (`{}`) or suppress an existing callback parameter without first inspecting all internal call sites within the child component to audit every place that callback is invoked.
  * Passing `{}` is functionally a deletion of code and feature behavior. If a callback was wired to user actions, passing `{}` silently breaks touch interactions (e.g. tile customization, delete buttons, selection toggles).
* **Intent-Explicit Callback Signatures**:
  * Avoid dual-use generic callbacks (e.g. an `onEdit` callback triggered on single-click in configuration mode, but on long-click in tracking mode). Name callbacks explicitly after athlete intent (e.g. `onConfigureField`, `onPickTileToMove`, `onToggleMap`).
* **Gesture Contract Tests**:
  * Interactive components handling multi-gesture inputs (`combinedClickable`, gestures) **MUST** have automated gesture contract tests asserting callback dispatch across all supported screen modes (`ScreenMode.TRACKING`, `ScreenMode.CONFIGURATION`, `ScreenMode.PREVIEW`).

### 1.3 Prohibition of Sorting and Ranking on Formatted Display Strings (ATT-3324)
* **Strict Numeric Primitive Invariant**:
  * All sorting comparators, tie-breakers, candidate rankings, and thresholds in domain repositories and ViewModels **MUST** evaluate raw numeric primitives (`Double`, `Float`, `Long`, `Int`), never formatted display strings.
  * Display formatters switch units at boundary thresholds (e.g. `DistanceFormatter` switches from meters `"995 m"` to kilometers `"2.40 km"` at 1,000m). Because string comparison is lexicographical by ASCII code, `"2.40 km" < "995 m"` evaluates as true, causing candidate entities to flip incorrectly at unit boundaries.
  * Models exposing formatted strings for UI presentation must also expose raw unrounded primitives (`Double`) with default fallback values (e.g. `Double.MAX_VALUE`) for sorting and mathematical comparison.

### 1.4 Explicit Confirmation for Destructive or Cache-Invalidating Operations (ATT-3355)
* **Destructive & Bulk Re-computation Guard**:
  * Any user-triggered action that invalidates existing historical caches, re-clusters historical workouts, clears bulk data, or resets persistent preferences **MUST** be protected by an explicit confirmation dialog (`AppDialogActions` / `AlertDialog`).
  * The dialog must clearly explain the consequences of the re-computation (e.g. re-clustering historical workouts) before executing the background task.

### 1.5 Reactive Flow Subscription Over Static `.value` Reads in `combine(...)` (Rule 39 — ATT-3581)
* **Active Flow Subscription Invariant**:
  * When a reactive pipeline in a ViewModel (such as `TrackingViewModel.observeMapState` using `combine(...)`) computes dynamic UI or camera state (e.g. Follow-Me speed-dependent zoom, bearing rotation) from live telemetry flows (`currentSpeed`, `currentBearing`), those telemetry flows **MUST** be subscribed as active inputs to `combine(...)`.
  * **Prohibition**: Never read `.value` of a rapidly changing `StateFlow` inside the lambda of a `combine(...)` that only subscribes to static preference flows (`SettingsDataStore`). Preference flows only emit when user settings change, leaving the derived state frozen during live activity.

### 1.6 Growing Warm-Up Window Over Blank Delay for Rolling Telemetry Metrics (Rule 40 — ATT-1290 / ATT-1291)
* **Immediate Live Feedback**:
  * Live rolling-window physiological metrics (such as 30-second Coggan Normalized Power, Intensity Factor, and TSS) **MUST** compute over the growing window ($1 \le k < W$) during the initial warm-up phase ($t < 30\text{ s}$) rather than emitting `null` (`"--"`) until the full window $W$ is reached.
  * Returning `"--"` for 30 seconds while a sensor is actively transmitting misleads the athlete into thinking the sensor connection is broken.

### 1.7 Backend Filter & Service Key Normalization & Post-Trim Empty Guard in Rolling Filters (Rule 50 — ATT-4282)
* **Never Pass Localized UI Sentinel Strings Into Backend Service or Filter Keys**:
  * When a UI selector uses a sentinel ID (`selectedDeviceId <= 0`, e.g. `-1L`) paired with a localized display string (`context.getString(R.string.bestSensor)` / `"Best Sensor"`) to represent automatic `"Best Sensor"` selection, the ViewModel **MUST** normalize `deviceId <= 0` to `null` before constructing backend identifiers (`FilterData.deviceName = null`).
  * Passing the localized UI string `"Best Sensor"` as `deviceName` into `BANALService.createFilter(FilterData)` registers the filter under `"Best Sensor"` while `TrackingViewModel` queries `getFilteredSensorData(FilterData(..., deviceName = null))`, causing live cockpit tiles to display `"--"` until tracking is restarted.
* **Post-Trim Emptiness Guard in Rolling Window Filters**:
  * Any rolling-window or moving-average filter (`TimedMovingAverageFilter`) that prunes expired samples (`trimValues()`) inside `getFilteredValue()` **MUST** check `if (mValues.isEmpty()) return null;` *after* calling `trimValues()` before dividing by `mValues.size()`.
  * Checking `isEmpty()` only before `trimValues()` causes `0.0 / 0 = Double.NaN` (`"--"`) whenever all buffered samples in the time window expire simultaneously.

### 1.8 Constructor State Seeding Parity & Sensor-Presence Gating in Stateful Metric Devices (Rules 51 & 52 — ATT-4283, ATT-4368)
* **Constructor Initialization Parity**:
  * Any stateful virtual sensor or physiological metric device (such as `CyclingPowerMetricsDevice` tracking `W' Balance`, `NP`, `IF`, `TSS`) that initializes its baseline capacity in `resetSession()` **MUST** also invoke `resetSession()` (or seed the identical baseline state) inside its constructor.
  * Unit tests that explicitly call `device.resetSession()` in `@Before` can mask uninitialized constructor state (`0.0` instead of full `W'` capacity) that occurs when the service instantiates the device in production.
* **Sensor-Presence Gating Before Publishing to `MySensor` & Summary Persistence (Rule 52 — ATT-4368)**:
  * While internal accumulator variables (`currentWPrimeBalance = wPrimeCapacity`) should be initialized immediately, virtual metric devices **MUST NOT** call `MySensor.newValue(...)` until the underlying physical/source sensor (`SensorType.POWER`) is registered or emits a real sample, and `WorkoutDataMapper` **MUST** gate derived summary rows (`W'bal`, `NP`, `EF`) on the presence of recorded source telemetry (`avgPower != null || maxPower != null`).
  * Unconditionally publishing a seeded value to `MySensor` during `resetSession()` causes `TrackerService` to persist phantom `EXTREMA` rows (e.g., `W'bal = 100% / 20.0 kJ`) on non-power workouts such as walks or runs.

### 1.9 Origin-Aware Cockpit Tab Navigation on `TrackingMode.TRACKING` Transitions (Rule 55 — ATT-4373)
* **Preserve Active Cockpit Tab When Auto-Pause Ends**:
  * Rising-edge transitions to `TrackingMode.TRACKING` in `TrackingTabsViewModel` **MUST** distinguish between an initial workout start (`READY -> TRACKING`) / manual resume from the **Control** tab (`Page 0`) versus an **Auto-Pause** resume (`PAUSED (isAutoPaused) -> TRACKING`).
  * Emitting `TabNavigationEvent.NavigateTo(0)` when Auto-Pause finishes pulls the athlete away from whichever secondary telemetry or Map tab they were viewing. Auto-scroll to the first telemetry tab (`NavigateTo(0)`) is only permitted when starting tracking or resuming from the **Control** tab (`Page 0`).

### 1.10 No Snapshot State Writes During Composition (Rule 66 — ATT-4863 / ATT-4838 / ATT-4864)
* **Never write a Compose `MutableState` inside a `remember { ... }` calculation or anywhere else in the composition body.** Writing snapshot state while composing invalidates the current composition and causes extra recompositions (on 1-Hz cockpit data this becomes recomposition churn).
  * ❌ `var lastIndex by remember { mutableStateOf<Int?>(null) }` + `val res = remember(key) { ...; lastIndex = res.segmentIndex; res }`
  * ✅ Cache values that only need to survive between calculations in a **plain holder object** (`private class SegmentIndexHolder(var index: Int? = null)`, `val holder = remember(key) { SegmentIndexHolder() }`), or move the write into a `SideEffect` / `LaunchedEffect` / ViewModel.
* Sprint 2026-44.1 removed this pattern from `SensorGridScreen` (ATT-4863) and re-introduced it in `TrackingTabsScreen` (ATT-4838) in the same sprint — Gate 4 explicitly checks for it.

### 1.11 No Pager/Scroll State Keys in Self-Animating `LaunchedEffect`s & Cross-Surface Marker Parity (Rule 80 — ATT-6208 / ATT-6322, ATT-6211 / ATT-6320, ATT-6286 / ATT-6323)
* **Never Include `pagerState.currentPage` in a `LaunchedEffect` Key List That Scrolls the Pager (`ATT-6208` $\rightarrow$ `ATT-6322`)**:
  * A `LaunchedEffect` whose body calls `pagerState.animateScrollToPage(targetPage)` or `pagerState.scrollToPage(targetPage)` **MUST NOT** include `pagerState.currentPage` (or `scrollState.value`) in its `LaunchedEffect(...)` key list.
  * During `animateScrollToPage`, `pagerState.currentPage` updates as soon as the scroll crosses the 50% midpoint, which immediately cancels and restarts the `LaunchedEffect` coroutine mid-animation and leaves the `HorizontalPager` visually frozen at 50% between tabs.
  * When switching tabs programmatically for an automated snapshot (`pendingAutoShare`), use `pagerState.scrollToPage(targetPage)` (instant jump without intermediate transition frames), never inject a duplicate visible `WorkoutHeader` into the child tab layout, and always restore the caller's original tab/screen state in `onSnapshotComplete`.
* **Cross-Surface Map Marker Parity (`ATT-6211` $\rightarrow$ `ATT-6320`) & Title Row Budget When Replacing Icon Badges With Text Buttons (`ATT-6286` $\rightarrow$ `ATT-6323`)**:
  * Whenever a domain entity's map marker geometry or icon is updated (e.g. *Lieblingsorte* heart marker in `KnownLocationThumbnailMap`), Stage 1–4 **MUST** audit all 3 map contexts: (1) list card thumbnail, (2) detail/edit screen map (`EditKnownLocationScreen`), and (3) global inspection map (`MapScreenWithTrack`).
  * When replacing a compact `32.dp` icon badge with a text `OutlinedButton` (e.g. `"Wechseln"` on `SupporterPlanCard`), audit the remaining horizontal width budget for long German compound titles (`"Bronze-Unterstützer"`) so single-letter line wraps (`Unterstütze\nr`) cannot occur.

### 1.12 Mandatory Production Caller & UI Reachability Invariant — Zero "Island" Components (`Rule 81` — `ATT-5085`/`ATT-6702`, `ATT-3052`/`ATT-6711`, `ATT-4981`/`ATT-6712`, `ATT-1298`/`ATT-6687`/`ATT-6688`, `ATT-3905`/`ATT-6723`)
* **Every New Composable, Dialog, Importer, and `SensorType` Must Be Reachable in Production**:
  * Creating a production `@Composable` screen, settings section, dialog, or bottom sheet (`RadarSettingsSection`, `HealthConnectSettingsDialog`, `LiveMmpCurveChart`, `CardioPowerDynamicsCard`) that is only referenced from `@Preview` functions or unit tests is **strictly forbidden**.
  * Before submitting Stage 4 and passing Gate 4:
    1. **UI / Settings Reachability**: Run `grep -rn "<ComposableName>(" app/src/main/` and confirm at least one live production caller reachable from `MainActivityWithNavigation`, `AppNavigationDrawer`, `AdvancedTuningScreen`, `SensorGridScreen`, or `WorkoutDetailTabbedContent`.
    2. **`SensorType` Selection Reachability**: Whenever a new `SensorType` enum constant is added (`POWER_5S..POWER_PEAK_20M`, `RADAR_*`, `HRV_*`, `LIVE_HR_DRIFT`), register it in the appropriate sport-specific arrays inside `ActivityType.getSensorTypeArray()` (and `DeviceType`) so `EditSensorFieldDialog` exposes the metric to the athlete.
    3. **Background Sync / Backfill Trigger Reachability**: Whenever a background importer or historical backfill engine (`HealthConnectHrvImporter`, `CardioPowerBackfill`) is introduced, wire both its user-facing settings control and its runtime execution trigger (`Application` startup / `WorkManager` / post-workout hook).

---

## 2. Database & Data Transfer Object (DTO) Architecture

### 2.1 Database & DTO Mapping Symmetry (Rule 20)
* **Symmetric Cursor Mapping Pathways**:
  * When adding or altering entity/database columns, all mapping pathways **MUST** map the fields symmetrically:
    1. Single-item `fromCursor(Cursor)`
    2. Batch-item `fromCursor(Cursor, List<Entity>)` or bulk fetchers
    3. Repository cache updaters and in-memory state models
  * Relying on default constructor arguments in Kotlin data classes without explicitly mapping newly added columns in all cursor extraction overloads is prohibited.
  * Unmapped columns cause silent data loss where single-row queries populate the new property, but batch queries fall back to default values.
* **Architectural Contract Tests**:
  * Every DTO with multiple `fromCursor` pathways must be protected by an architectural contract unit test verifying that both single and batch overloads yield identical objects for identical cursor rows.

### 2.2 Single Source of Truth for `DatabaseSchemaRegistry` & Schema Version Validation (Rule 62 — ATT-4592 / ATT-4596)
* **Direct Reference to `SQLiteOpenHelper.DB_VERSION` Constants**:
  * `DatabaseSchemaRegistry` (and any backup/restore or migration schema validator) **MUST** reference each `SQLiteOpenHelper` subclass's `DB_VERSION` / `C_DB_VERSION` constant directly rather than duplicating raw integer literals (`maxVersion = 1`, `25`, etc.).
  * Whenever a database's `DB_VERSION` is incremented (e.g., for an index or column migration), `DatabaseSchemaRegistry` must automatically reflect the updated version and must be guarded by a contract test asserting equality between every registered database's `maxVersion` and its corresponding `SQLiteOpenHelper` version constant.

### 2.3 SQLite Schema Migration & `onCreate`/`onUpgrade` Parity (Rule 64 — ATT-3534)
* **Symmetrical Schema Evolution**:
  * Whenever a database's `DB_VERSION` / `C_DB_VERSION` is incremented, both `onCreate()` (the fresh-installation schema definition) and `onUpgrade()` (the incremental migration pathway) **MUST** be updated symmetrically.
  * Fresh installs and incrementally upgraded databases must yield 100% equivalent schema structures (identical columns, constraints, indices, and defaults).
* **Safe `DEFAULT` Constraints on Added `NOT NULL` Columns**:
  * When executing `ALTER TABLE ... ADD COLUMN` for a non-null property on an existing table in `onUpgrade()`, the column definition **MUST** include an explicit safe `DEFAULT` constraint (e.g. `DEFAULT 0` or `DEFAULT ''`).
  * Adding a `NOT NULL` column without a `DEFAULT` constraint causes SQLite migrations to crash with `SQLiteException: Cannot add a NOT NULL column with default value NULL` on existing user databases.
* **Automated Migration Contract Unit Tests**:
  * Every database schema version increment **MUST** be accompanied by an automated migration contract unit test verifying that a database upgraded incrementally through all `onUpgrade()` steps produces identical table schemas and columns as a fresh database initialized via `onCreate()`.

### 2.4 Three-Way Parity for Spatial Fingerprint & Summary Metric Formula Changes: Live + Import + DB Migration (Rule 80 — ATT-5899 / ATT-6319)
* **Live Recording + Bulk/File Import + Historical Database Self-Healing**:
  * Whenever a persisted spatial fingerprint or summary metric calculation is changed (such as `WorkoutClusterEngine.findApexFromPoints(start, points, end)` switching point-to-point $A \to B$ workouts from `apex = end` to true path-midpoint / cross-track deviation apex), Stage 1–4 **MUST** enforce 3-way parity across:
    1. **Live Recording**: `TrackerService` / live summary finalization.
    2. **File & Bulk Import**: `LegacyImportEngine` / `StravaBulkExportImporter` (`grep` all callers computing `APEX_LAT`/`APEX_LON` or `EXTREMA_VALUES`).
    3. **Historical Database Migration**: A background/startup migration or re-clustering pass updating existing rows in `WorkoutSummaries.db` (`EXTREMA_VALUES`) and `WorkoutClusters.db` so newly recorded workouts and historical workouts share a single consistent coordinate convention.

### 2.5 True Repository Encapsulation — Prohibition of `*DatabaseManager` / `SQLiteDatabase` / `Cursor` Backdoor Getters (`Rule 81` — `ATT-6426` / `ATT-6698`, `ATT-432` / `ATT-6696`)
* **Encapsulate Persistence Behind Domain Methods & Domain DTOs**:
  * Domain repositories (`WorkoutRepository`, `WorkoutClusterRepository`, `ExportStatusRepository`, `EquipmentRepository`, `WorkoutRrRepository`) **MUST NOT** expose public or internal `get*DatabaseManager()`, `get*DbHelper()`, `SQLiteDatabase`, or raw Android `Cursor` getters to UI, ViewModel, exporter, or engine callers merely to satisfy literal `DatabaseManager.getInstance(` source checks.
  * Repository methods must accept and return domain models/primitives (or streaming domain callbacks for bulk sample iteration) and keep SQLite table/column names, cursors, and `*DatabaseManager` instances strictly private to the data/repository layer.

---

## 3. Android Platform & Lifecycle Safety

### 3.1 Specific Direct Platform Intents & Variant-Scoped Custom URI Schemes (Rules 21 & 63 — ATT-4668 / ATT-4706)
* **Direct Target Intents**:
  * User prompts requesting system permissions, battery optimization exemptions, or hardware settings must target the most specific direct intent:
    * System permission requests: direct Android runtime permission dialog (`ActivityCompat.requestPermissions`).
    * Battery optimization: `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with package URI.
    * Bluetooth / Location toggles: `ACTION_REQUEST_ENABLE` or `ACTION_LOCATION_SOURCE_SETTINGS`.
* **Generic Fallback As Last Resort**:
  * Navigating to generic Application Details Settings (`ACTION_APPLICATION_DETAILS_SETTINGS`) is only permissible as a last-resort fallback when direct intents are unavailable or permissions are permanently blocked ("Don't ask again").
* **Variant-Scoped Custom URI Schemes Over Path Suffixes (`Rule 63` — `ATT-4668` / `ATT-4706`)**:
  * When isolating deep-link or OAuth callback `<intent-filter>`s between co-installed build variants (`com.atrainingtracker` vs `com.atrainingtracker.debug`), scope the **`android:scheme`** (or `android:host`) by `${applicationId}` (e.g. `android:scheme="strava.${applicationId}"`) whenever the OAuth provider permits custom schemes, rather than only adding `android:path="/${applicationId}"` onto a shared `scheme://host`.
  * In Android's `IntentFilter.matchData()`, any co-installed APK (such as an older Release build) that declares `android:scheme` + `android:host` **without** an `android:path` attribute treats the omitted path as a wildcard (`/*`, `MATCH_DATA_AUTHORITY = 0x308000`), and `IntentResolver` includes all matching filters with equal `priority` in `ResolverActivity` ("Öffnen mit") regardless of path vs. authority match specificity.

### 3.2 Defensive Service Lifecycle Finalization & FGS Permission Gating (Rule 29)
* **Permission-Gated Foreground Service Types**:
  * Never pass Android 14+ (API 34+) specialized foreground service types (`FOREGROUND_SERVICE_TYPE_HEALTH`, `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE`) to `startForeground()` unconditionally.
  * The calling service **MUST** dynamically verify that the corresponding runtime permission (e.g. `ACTIVITY_RECOGNITION`, `BLUETOOTH_CONNECT`) is granted via `ContextCompat.checkSelfPermission(...)`.
  * Unconditionally requesting types without runtime permissions triggers a fatal `SecurityException` in Android's `ActivityManagerService`.
* **Unconditional Teardown & Guaranteed Finalization**:
  * Critical session closure, database finalization (`FINISHED = 1`), and terminal broadcast intents must **NEVER** be gated behind transient error flags (such as `mTrackingInterrupted`).
  * If a session ID is valid, finalization must execute in an explicit SQLite transaction (`beginTransaction() ... setTransactionSuccessful() ... endTransaction()`) and completion broadcasts dispatched within guaranteed `try/finally` blocks.

### 3.3 Clean Hardware Teardown and Orphan Defense on Persistence Deletion (Rule 35)
* **Synchronized Hardware Disconnect**:
  * When an entity (such as a paired sensor or bike profile) is deleted from local persistence (database), the background service managing active hardware connections (`BANALService`) **MUST** be immediately notified to cleanly disconnect and unregister the hardware resource.
* **Orphan Defense in UI ViewModels**:
  * ViewModels **MUST** defensively discard orphaned IDs that do not exist in the database rather than constructing invalid fallback data models (`DeviceType.DUMMY`) that open broken, empty, or dimmed modal dialogs.

### 3.4 Audible Outdoor Safety Alerts (`USAGE_MEDIA` / `USAGE_ALARM`) & Static `AudioTrack` Replay (Rules 53 & 76 — ATT-4371, ATT-5649)
* **Media & Alarm Stream Routing Over Sonification**:
  * Explicit user-triggered outdoor audio utilities (such as the Cockpit Bike Bell or acoustic safety warnings) **MUST** configure `AudioAttributes` with `AudioAttributes.USAGE_ALARM` or `AudioAttributes.USAGE_MEDIA`, **NEVER** `AudioAttributes.USAGE_ASSISTANCE_SONIFICATION`.
  * Android routes `USAGE_ASSISTANCE_SONIFICATION` to the system ringer/notification stream, which is completely silenced whenever the phone is on Vibrate or Silent mode.
* **Reliable Warm `MODE_STATIC` Replay**:
  * When re-triggering a persistent `MODE_STATIC` `AudioTrack` (such as `BikeBellAudioSynthesizer`), always use warm replay: `track.pause()` + `track.playbackHeadPosition = 0` + `track.play()`.
  * **Prohibition**: Never call `track.stop()` prior to `play()` on persistent static tracks. Calling `stop()` moves the track to `STATE_STOPPED`, prompting Android AudioFlinger to tear down the active mixer port and send hardware SmartPA amplifiers into standby, which incurs 200–385 ms port renegotiation latency and truncates initial attack transients (ATT-5649). `track.pause()` preserves the warm AudioFlinger mixer port in `STATE_PAUSED` without renegotiation latency.

### 3.5 Mandatory `@JvmOverloads constructor` for Reflective Android Framework Classes (`AndroidViewModel`, `ViewModel`, `Fragment`) (Rule 62 — ATT-4667 / ATT-4596)
* **Bytecode Constructor Generation for Default Parameters**:
  * Whenever default constructor parameters (e.g., `ioDispatcher: CoroutineDispatcher = Dispatchers.IO`, repositories, or clock providers) are added to an `AndroidViewModel`, `ViewModel`, or `Fragment` instantiated via default Android framework reflection (`viewModel(activity)` / `AndroidViewModelFactory`), the primary constructor **MUST** be annotated with `@JvmOverloads constructor(...)` (or an explicit `ViewModelProvider.Factory` must be wired at all call sites).
  * Without `@JvmOverloads`, Kotlin only emits the multi-argument + `DefaultConstructorMarker` constructor in bytecode, causing `ViewModelProvider.AndroidViewModelFactory`'s reflective `modelClass.getConstructor(Application::class.java)` lookup to crash at runtime with `NoSuchMethodException` even though Kotlin unit tests compile and pass.

---

## 4. Tooling & Host Diagnostics

### 4.1 Developer Tool Host Pre-Flight Diagnostics (Rule 30)
* **Startup Diagnostics**:
  * Any developer tool, simulator, or hardware replay utility interacting with host system hardware (such as Bluetooth LE Broadcaster/Peripheral emulation via BlueZ/D-Bus, ADB mock locations, serial interfaces) **MUST** execute a self-diagnostic pre-flight check at startup.
* **Actionable Error Surfacing**:
  * The tool must verify that controller hardware supports required roles (e.g. LE peripheral mode), that D-Bus/system permissions are granted, and that target ADB devices are online. If prerequisites fail, the tool must surface clear actionable diagnostics rather than failing silently.

---

## 5. Testing Standards & Framework Invariants

### 5.1 Prohibition of `returnDefaultValues = true`
* **Real Mocking Over Silent Defaults**:
  * Test configurations **MUST NOT** enable `returnDefaultValues = true` in Gradle `testOptions.unitTests`.
  * Enabling default return values causes unmocked Android framework methods to silently return `null`, `0`, or `false`, masking severe regressions, null pointer exceptions, and incorrect control flow.
  * Every Android SDK dependency in unit tests must be explicitly mocked (e.g. via MockK, Robolectric, or clean architectural abstraction).

### 5.2 Two-Tier Unit Test Execution Strategy: Per-Ticket vs Sprint Integration Gate (Rules 60 & 61 — ATT-4465, ATT-4520)
* **Tier 1 — Per-Ticket Verification (Stages 4 & 5, ~10–20 Seconds)**:
  * During Stage 4 construction and Stage 5 ticket verification, execute incremental targeted unit and contract test classes covering all modified and directly adjacent packages (**never** pass `--rerun-tasks`, which forces a 4+ minute full recompilation):
    ```bash
    ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.specific.*"
    ```
  * Whenever any `strings.xml` resource file is modified, also run `TranslationParityTest` (~3s):
    ```bash
    ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"
    ```
  * **High-Risk Exception**: Run the full `./gradlew testDebugUnitTest` suite per-ticket *only* for High-Risk cross-cutting changes touching core shared infrastructure (`TrainingApplication`, `TrackerService`, `BANALService`, or SQLite database schema migrations).
* **Tier 2 — Sprint Integration Gate (Once per Sprint at the Very End of Phase 2, Before Ceremony 2, ~8 Minutes — Rule 66 / ATT-4864)**:
  * After the last sprint ticket is squashed onto `sprint/<SPRINT_NAME>` (and before the Sprint Review), execute the full clean-room test suite and fix all failures as separate attributed commits; the run at the start of the Sprint Review is a confirmation:
    ```bash
    ./gradlew testDebugUnitTest --max-workers=2 && python3 tools/verify_requirement_governance.py
    ```
  * Verify 100% pass rate across all 3,660+ unit tests across all project modules. Because each ticket is squashed into a single atomic commit on `sprint/<SPRINT_NAME>` (Core Rule 8), any cross-package regression caught at the Sprint Integration Gate can be pinpointed immediately.

### 5.3 Caller-Site Integration Verification for Optional Default Parameters (Rule 61 — ATT-4015 / ATT-4520)
* **No Unwired Default Parameter Fallbacks**:
  * Whenever a shared utility, calculator, or repository method adds an optional parameter with a default value (e.g., `lastSegmentIndex: Int? = null` in `PolylineProgressTracker.projectLocation`) to fix a production behavior defect, Stage 3 (`Implementation Plan`), Stage 4 (`Implementation`), and Gate 4 Review **MUST** audit and wire **all intended production callers** to pass the stateful parameter.
  * Unit-testing only the utility method while leaving production call sites on the default `null` fallback leaves the bug active at runtime.
* **Connected User Device Data Protection (`adb uninstall` / `pm clear` Prohibition)**:
  * Agents **MUST NEVER** run `adb uninstall` or `adb shell pm clear` on `com.atrainingtracker` or `com.atrainingtracker.debug` on a connected user device without explicit human confirmation naming the exact package and first pulling a verified `.attbackup` archive to the host machine.

### 5.4 Contract Test Standards & Prohibition of UI Literal Source-Grep (ATT-4028 / REQ-TST-002)
* **Prohibition of Source-Text UI Grep**:
  * Unit and contract tests **MUST NOT** read source code files (`File.readText()`) to assert on Compose design tokens (e.g. `RoundedCornerShape(12.dp)`, `TTColor.RouteSelected`), raw string/emoji literals, internal call parameters, or raw SVG path commands (`V20`, `h-2`).
  * Text-grep assertions freeze non-functional formatting, whitespace, and private implementation details, triggering false-positive test failures during innocent refactors while verifying zero runtime behavior.
  * UI contracts must be verified via semantic state models, Compose semantics/UI testing, or behavioral formatters. Vector drawables must be verified via XML DOM parsing and geometric vertex milestone evaluation. Excision/decoupling must be verified via programmatic reflection (`Class.forName`, `declaredFields`, `declaredMethods`) and file existence checks.
* **Preservation of System Architectural Governance Guards**:
  * System policy and governance guards (such as `TryWithResourcesCursorAuditTest` for leak prevention, `ManifestSecurityContractTest` for Android security boundaries, `TranslationParityTest` for 9-language localization, and Rule 34 empty callback guards) enforce essential repository-wide invariants and **MUST NOT** be confused with prohibited UI source-grep tests.

### 5.5 Test Isolation: No Real Background Work with Mocked Android Contexts (Rule 66 — ATT-4840 / ATT-4864)
* Plain JVM unit tests (MockK, no Robolectric) **MUST NOT** let production code start real asynchronous infrastructure — `DataStore` singletons (`TuningPreferencesDataStore`, `SettingsDataStore`), `stateIn(CoroutineScope(Dispatchers.Default))`, `WorkManager`, executors — with a mocked `Context`.
* Such work fails *asynchronously* (e.g. `Method getFilesDir ... not mocked`) after the test has finished; `kotlinx-coroutines-test` then reports it as `UncaughtExceptionsBeforeTest` in an **unrelated** `runTest`-based test that happens to run next in the same JVM fork, making failures order-dependent and misattributed (Sprint 2026-44.1: `TrainingApplicationTrackingTest` broke `BatterySaverControllerTest`).
* Whenever production code under test gains a new dependency on such infrastructure (e.g. `TrainingApplication.startTracking()` reading `SettingsDataStoreJavaHelper.getAutoPauseConfig()`), stub the synchronous bridge in **all** existing tests that exercise that path (`mockkStatic(SettingsDataStoreJavaHelper::class)`), or use Robolectric with a real temporary context.
* A production `try/catch` around a synchronous call does not catch exceptions thrown later on another dispatcher.
* **Reset Progress / Loading State Before Emitting Terminal Completion Events (`Rule 74` — `ATT-5565`)**: In asynchronous repository or ViewModel methods that update both an in-progress state (`_deletionProgress`, `_isLoading`) and a terminal completion event (`deleteFinishedEvent`, `saveFinishedEvent`), always reset the progress/loading state to `Idle`/`false` **before** posting the completion event so observers awakened by the completion event never observe a stale `InProgress` value.
* **Null-Safe Static `SharedPreferences` Getters in `TrainingApplication` & MockK Arity Sync on Default-Arg Signatures (`Rule 75` — `ATT-5559`, `ATT-5560`)**:
  * Every static `SharedPreferences` getter/setter in `TrainingApplication.java` (`getClusterScoreThreshold()`, `getClusterTolEndpoints()`, etc.) **MUST** guard `cSharedPreferences != null` (`return cSharedPreferences != null ? cSharedPreferences.getFloat(...) : DEFAULT;`) so JVM ViewModel/Engine unit tests that do not execute `TrainingApplication.onCreate()` never fail with `NullPointerException`.
  * Whenever a default-argument parameter is added to a mocked Kotlin method (such as `WorkoutClusterEngine.suggestCluster`), grep all test files (`grep -rn "suggestCluster(" app/src/test/`) and update every `every { mock.suggestCluster(...) }` stub's `any()` argument count to match the new synthetic `$default` method arity.

### 5.6 Upgrade-Path Behaviour for Persisted Data & Removed Settings (Rule 66 — ATT-4841 / ATT-4864)
* When a DB migration back-fills new columns or a setting is removed/replaced, the back-fill MUST reproduce the user's **previous effective behaviour** (carry over the old preference), not the new default for fresh installs. Document "what changes for an existing user after the update" in the Stage 3 plan; Gate 4 verifies it (Sprint 2026-44.1: removing the global `showGauge` (default `false`) and back-filling `ShowBar = 1` forced zone bars onto all existing layouts — ATT-4927).

### 5.7 Broadcast Receiver Ordering, Java-to-Kotlin Nullability & Startup Self-Healing (Rule 67 — ATT-4926, ATT-4922 / ATT-4936)
* **End-to-End Broadcast Listener Sequence Verification (`ATT-4926` $\rightarrow$ `ATT-5080`)**:
  * Multiple components register `BroadcastReceiver`s for the same lifecycle intent (e.g., `ClockDevice` and `FilterManager` both listen for `RESET_ACCUMULATORS_INTENT`). When modifying a broadcast receiver or `reset()` method, verify the **end-to-end execution order across all registered listeners** (in `ATT-4926`, `ClockDevice` reset `TIME_ACTIVE` to `0` (`0:00:00`) first, and `FilterManager.resetAllFilters()` ran second and overwrote `InstantaneousFilter.mValue` with `null` (`--:--:--`)).
* **Java Platform-Type Nullability Guard (`ATT-4926` $\rightarrow$ `ATT-5080`)**:
  * Any Java method returning a formatted display string (`SensorData.getStringValue()`, `MyFormatter.format()`) **MUST** return a non-null fallback (`"--"`) when its underlying value is `null` or be explicitly annotated `@Nullable` so Kotlin callers cannot crash with a `NullPointerException` (`Parameter specified as non-null is null`).
* **Startup Self-Healing for Terminal Database States (`ATT-4922` $\rightarrow$ `ATT-5081`)**:
  * When adding recovery logic for records stuck in a terminal DB state (`FINISHED_FAILED`) from previous app versions, always wire an automatic background trigger on app startup (`MainActivityWithNavigation` / `Application.onCreate`) in addition to interactive settings dialog triggers so recovery happens without requiring manual user reconnection.

### 5.8 Side-Effect, Wrapper/Proxy & Shared Repository State Audits (Rule 68 — ATT-5080, ATT-5074 / ATT-5082)
* **Unwrap `ProxySensor` / Decorator Wrappers in `instanceof` Checks (`ATT-5080` $\rightarrow$ `ATT-5191`)**:
  * When `deviceName == null` (`"Best Sensor"`), `MySensorManager.getSensor(sensorType)` returns a `ProxySensor<T>` (which extends `MySensor<T>`, **not** `MyAccumulatorSensor`), wrapping the underlying active sensor (`proxySensor.getBestSensor()`).
  * Before checking `sensor instanceof MyAccumulatorSensor` (or any concrete sensor subclass) in `FilterManager` or service helpers, always unwrap `ProxySensor` first (`if (sensor instanceof ProxySensor<?> proxy && proxy.getBestSensor() != null) sensor = proxy.getBestSensor();`), and ensure unit tests cover `"Best Sensor"` (`ProxySensor`-wrapped) instances in addition to direct subclass mocks.
* **Companion `StateFlow` Hydration Parity in Fast/Lightweight Repository Loaders (`ATT-5074` $\rightarrow$ `ATT-5196`)**:
  * When adding a fast/lightweight loading path to a shared repository (e.g., `WorkoutClusterRepository.loadAllClustersLightweight()` populating `_allClusters`), audit all downstream UI consumers (`MapFragmentWithTrackViewModel`, `ClusterOnMapSheet`) to ensure companion `StateFlow`s (`_clusterStats`) are also hydrated if empty, so UI views opened before the primary list screen never render with missing metadata.
* **Cross-Consumer Scope Audit for Shared Map Layers (`ATT-5074` $\rightarrow$ `ATT-5197`)**:
  * Before wiring a map layer or repository flow into multiple ViewModels, verify whether the overlay belongs only on the general inspection map (`MapFragmentWithTrackViewModel`) or also on the live tracking cockpit map (`TrackingViewModel` / `SensorGridScreen`). Historical discovery layers (*Lieblingsstrecken* / `CLUSTERS`) belong strictly on the general map, never on the live tracking cockpit map.

### 5.9 Workout Detail State Stability, Null-Island (`0.0, 0.0`) GPS Filtering & Code-Grounded Documentation (`Rule 73` — `ATT-907`, `ATT-43`, `ATT-5418` / `ATT-5402`)
* **Never Unmount `WorkoutDetailTabbedContent` (`HorizontalPager`) During Background Repository Refresh (`ATT-907` $\rightarrow$ `ATT-5478`)**:
  * In `WorkoutDetailScreen.kt` / `WorkoutSummariesWithMapViewModel.kt`, once a `WorkoutData` instance is loaded for a `workoutId`, background repository updates (`refreshWorkout(workoutId)` after lap split/move, photo edits, or `ExportWorkoutBroadcast` status updates) **MUST** update `selectedWorkout` in-place without setting `isDetailLoading = true` or replacing `WorkoutDetailTabbedContent` with a full-screen `CircularProgressIndicator`. Unmounting `WorkoutDetailTabbedContent` destroys the `HorizontalPager` state (jumping from Tab 2 *Runden & Segmente* back to Tab 0 *Karte & Diagramme*) and forces repeated Google Map re-initializations.
* **Reject `(0.0, 0.0)` Null-Island EXIF & Map Bounds Coordinates (`ATT-43` / `ATT-907` $\rightarrow$ `ATT-5479`)**:
  * Android `ExifInterface.getLatLong()` and uninitialized SQLite `REAL` columns often yield `(0.0, 0.0)` when no GPS fix was written. Always reject `(abs(lat) < 1e-5 && abs(lng) < 1e-5)` when extracting photo EXIF coordinates (`WorkoutPhotoStorageManager`) and when building `LocationMarker` lists or `LatLngBounds` (`MapViewportHelper`), so a single `(0.0, 0.0)` marker never zooms the map out to a 5,000 km continent view where the workout polyline becomes sub-pixel invisible.
* **Code-Grounded User Guide & Multimodal `docs/guide/img/*.png` Audit (`ATT-2275` $\rightarrow$ `ATT-5418`)**:
  * User Guide chapters (`docs/guide/`) **MUST** be grounded strictly in existing source code (`app/src/main/`)—never document speculative keywords from a ticket prompt without code verification—and every image in `docs/guide/img/*.png` must be visually inspected via `read_file` by both `agent1` and `agent2`.
* **Dynamic Code-Grounding & Bidirectional ID Set Equality for Architecture (`SWE.2`) & System (`SYS.2`/`SYS.3`) Specifications (`Rule 81` — `ATT-6179`/`ATT-6727`, `ATT-6180`/`ATT-6732`, `ATT-6158`..`ATT-6164`/`ATT-6738`..`ATT-6741`)**:
  * Every backticked `.kt`/`.java`/`.xml` filename, class/interface/DTO symbol, method signature, and manifest permission/FGS attribute cited in `docs/architecture.md`, `docs/architecture/*.md`, and `docs/system/*.md` **MUST** be verified programmatically against `app/src/main/` (never invent plausible-sounding class names or check only prompt examples).
  * Cross-document requirement allocations (`SYS.2` `SYSREQ-*` $\leftrightarrow$ `SYS.3` `system_architecture.md`) **MUST** satisfy exact bidirectional set equality (`declared_ids == allocated_ids`) with zero unallocated orphans and zero ghost IDs.
* **Plain-JVM (`Build.VERSION.SDK_INT == 0`) & Unattached `Context` Safety Guards (`Rule 81` — `ATT-5102`, `ATT-6173`)**:
  * In plain JVM unit tests (`SDK_INT == 0` without Robolectric), `ContextCompat.registerReceiver` invokes `Process.myPid()` (throwing `Method myPid not mocked`), calling `context.filesDir` on an unattached `Service` throws `Method getFilesDir not mocked`, and relaxed MockK `context.filesDir` returns `File("")` which crashes `DataStore` asynchronously on `Dispatchers.IO` (`UncaughtExceptionsBeforeTest`).
  * Guard internal `ContextCompat.registerReceiver` helpers when `Build.VERSION.SDK_INT == 0`, annotate methods accepting injectable `sdkInt: Int = Build.VERSION.SDK_INT` with `@SuppressLint("NewApi")` rather than adding `&& Build.VERSION.SDK_INT >= ...` (which breaks JVM tests), and resolve `DataStore` contexts synchronously inside `try/catch` checking `dir.path.isNotEmpty()` before launching any background coroutine.
