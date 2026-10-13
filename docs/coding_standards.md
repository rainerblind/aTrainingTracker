# aTrainingTracker: Technical Architecture & Coding Standards

This document serves as the authoritative living source of truth for software engineering (SWE.1 / SWE.2) construction invariants, technical architecture patterns, and testing standards across **aTrainingTracker**.

---

## 1. Core Engineering Principles & Software Construction

### 1.1 Single Source of Truth & DRY (Don't Repeat Yourself) — Shared Code Over Duplication (Rule 36)
* **Mandatory Reuse Over Divergent Logic**:
  * Always favor existing shared utilities, repositories, formatters, and reusable composables over writing parallel, ad-hoc, or duplicate code.
  * Before introducing new helper methods or metric conversion logic, inspect existing utilities in `com.atrainingtracker.trainingtracker.util`, `com.atrainingtracker.trainingtracker.ui.common`, and `ui.components`.
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

### 5.2 Phased Execution: Stage 4 vs Stage 5
* **Stage 4 Construction (Fast Unit Loop)**:
  * During Stage 4 implementation, execute targeted unit test classes (5–15 seconds):
    ```bash
    ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.specific.*"
    ```
  * Maintain rapid developer velocity without running the full 430+ class test suite on every minor code edit.
* **Stage 5 Verification (Clean-Room Full Suite)**:
  * In Stage 5, execute the full clean-room test suite:
    ```bash
    ./gradlew testDebugUnitTest
    ```
  * Verify 100% pass rate across all project modules before presenting the ticket for Human Review.
