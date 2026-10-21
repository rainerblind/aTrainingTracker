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

---

## 3. Android Platform & Lifecycle Safety

### 3.1 Specific Direct Platform Intents Over Generic App Settings (Rule 21)
* **Direct Target Intents**:
  * User prompts requesting system permissions, battery optimization exemptions, or hardware settings must target the most specific direct intent:
    * System permission requests: direct Android runtime permission dialog (`ActivityCompat.requestPermissions`).
    * Battery optimization: `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with package URI.
    * Bluetooth / Location toggles: `ACTION_REQUEST_ENABLE` or `ACTION_LOCATION_SOURCE_SETTINGS`.
* **Generic Fallback As Last Resort**:
  * Navigating to generic Application Details Settings (`ACTION_APPLICATION_DETAILS_SETTINGS`) is only permissible as a last-resort fallback when direct intents are unavailable or permissions are permanently blocked ("Don't ask again").

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

### 3.4 Audible Outdoor Safety Alerts (`USAGE_MEDIA`) & Static `AudioTrack` Replay (Rule 53 — ATT-4371)
* **Media Stream Routing Over Sonification**:
  * Explicit user-triggered outdoor audio utilities (such as the Cockpit Bike Bell or acoustic safety warnings) **MUST** configure `AudioAttributes` with `AudioAttributes.USAGE_MEDIA` (or `AudioAttributes.USAGE_ALARM`), **NEVER** `AudioAttributes.USAGE_ASSISTANCE_SONIFICATION`.
  * Android routes `USAGE_ASSISTANCE_SONIFICATION` to the system ringer/notification stream, which is completely silenced whenever the phone is on Vibrate or Silent mode.
* **Reliable `MODE_STATIC` Replay**:
  * When re-triggering a `MODE_STATIC` `AudioTrack` (or overlapping rapid chimes), always call `stop()` and `reloadStaticData()` (or instantiate a fresh `AudioTrack` per strike and release on completion) so subsequent taps never silently no-op.

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

### 5.2 Two-Tier Unit Test Execution Strategy: Per-Ticket vs Sprint Integration Gate (Rule 60 — ATT-4465)
* **Tier 1 — Per-Ticket Verification (Stages 4 & 5, ~10–20 Seconds)**:
  * During Stage 4 construction and Stage 5 ticket verification, execute targeted unit and contract test classes covering all modified and directly adjacent packages:
    ```bash
    ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.specific.*"
    ```
  * Whenever any `strings.xml` resource file is modified, also run `TranslationParityTest` (~3s):
    ```bash
    ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"
    ```
  * **High-Risk Exception**: Run the full `./gradlew testDebugUnitTest` suite per-ticket *only* for High-Risk cross-cutting changes touching core shared infrastructure (`TrainingApplication`, `TrackerService`, `BANALService`, or SQLite database schema migrations).
* **Tier 2 — Sprint Integration Gate (Once per Sprint in `Review & Retro` Before Merging `sprint/<ID>` into `develop`, ~4 Minutes)**:
  * During the Sprint `Review & Retro` ceremony before merging `sprint/<SPRINT_NAME>` into `develop`, execute the full clean-room test suite:
    ```bash
    ./gradlew testDebugUnitTest
    ```
  * Verify 100% pass rate across all 3,660+ unit tests across all project modules. Because each ticket is squashed into a single atomic commit on `sprint/<SPRINT_NAME>` (Core Rule 8), any cross-package regression caught at the Sprint Integration Gate can be pinpointed immediately.
