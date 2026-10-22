# Stage 2: Requirement & Test Specification - ATT-4032: Fix low-severity tracking robustness issues

**Ticket**: [ATT-4032](https://rainerblind.atlassian.net/browse/ATT-4032)  
**Parent Epic**: None | **Target Release**: `4.9.40` | **Active Sprint**: `2026-43.6` | **Branch**: `feature/ATT-4032`  
**Requirement Mapping**: `REQ-TRK-039` (*Tracking Subsystem Robustness, Resource Lifecycle & Rate-Limited Error Telemetry Hygiene*)  
**Test Spec ID**: `TST-TRK-039`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Requirement Specification (`REQ-TRK-039`)

### 1.1 Problem Statement & Rationale
An independent whole-app code review identified low-severity robustness, concurrency, resource handling, and lifecycle defects across tracking components:
1. `BANALService.newLap` computes `lapDistance / lapTime_s` yielding `NaN`/`Infinity` when `lapTime_s == 0`; `getSportTypeId(avgSpd)` performs unchecked `sportTypeIds.get(0)` throwing `IndexOutOfBoundsException` on empty list.
2. Synchronous SQLite queries in `MyDevice.getName()` (via `SensorData`) and `TrackerService.tracker` (`SportTypeDatabaseManager.getUIName`) incur overhead on every 1 Hz sampling tick.
3. `TrainingApplication.updateTimeAndDistanceToNotification` updates notification without checking if tracking is active, resurrecting status bar notification on late ticks.
4. Catch-all blocks in `TrackerService.tracker` and `ClockDevice.ClockTimeTask` flood logs (~3600 `Log.e`/h on persistent failure) instead of rate-limiting; `ClockDevice` timer ticks drift from monotonic time.
5. `DeviceManager.createPairedRemoteDevices` cursor lacks try-with-resources; `AltitudeFromPressureDevice.shutDown()` accidentally calls `registerReceiver`; `BatterySaverController.release()` leaks its coroutine scope.

### 1.2 Functional & Architectural Requirements
1. **Defensive Numerics & List Boundary Hardening (`REQ-TRK-039.1`)**: `BANALService.newLap` SHALL guard `lapTime_s > 0 ? (lapDistance / lapTime_s) : 0.0`, preventing `NaN`/`Infinity`. `BANALService.getSportTypeId` SHALL check `!sportTypeIds.isEmpty()` before calling `.get(0)`, returning fallback default sport ID when empty.
2. **Scheduler Thread In-Memory Caching (`REQ-TRK-039.2`)**: `MyDevice` SHALL cache `mCachedDeviceName` upon resolution; `TrackerService` SHALL cache localized sport UI names keyed by sport ID, eliminating per-second SQLite reads on the 1 Hz loop.
3. **Notification Lifecycle Gating (`REQ-TRK-039.3`)**: `TrainingApplication.updateTimeAndDistanceToNotification` SHALL evaluate `isTracking() || isPaused()` before updating the builder or calling `notify()`, preventing notification resurrection after stop.
4. **Rate-Limited Telemetry Logging & Clock Monotonicity (`REQ-TRK-039.4`)**: `TrackerService.tracker` and `ClockDevice.ClockTimeTask` SHALL enforce rate-limiting (burst 3, then every 60th log) on persistent errors. `ClockDevice` SHALL anchor timer ticks to monotonic elapsed time (`SystemClock.elapsedRealtime`).
5. **Deterministic Teardown & Leak Prevention (`REQ-TRK-039.5`)**: `DeviceManager.createPairedRemoteDevices` SHALL wrap cursor access in try-with-resources. `AltitudeFromPressureDevice.shutDown()` SHALL excise accidental `registerReceiver`. `BatterySaverController.release()` SHALL cancel its coroutine scope and clamp brightness within safety bounds.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-TRK-039`, extending `REQ-TRK-027` and `REQ-TRK-029`. Targets `BANALService.java`, `MyDevice.java`, `TrackerService.java`, `TrainingApplication.java`, `ClockDevice.java`, `DeviceManager.java`, `AltitudeFromPressureDevice.java`, and `BatterySaverController.kt`.
* **Historical Origin & Commit Trace**: Independent whole-app review (`0234bfc0`, `feature/ATT-3921`, Oct 2026). `AltitudeFromPressureDevice` accidental receiver registration originated from commit `2afc198549`. Unbounded logging was introduced when adding exception barriers in `ATT-3981`.
* **Root Reason for Existing Formulation**: Blanket catches were added to prevent background loop aborts; notification updates fired unconditionally on ticks; receiver call was a copy-paste artifact during Android 14 migration.
* **Preservation of Core Invariants**: Tracking accuracy, lap calculation, SQLite persistence, and 100% clean-room test pass rate remain strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Zero-Duration Lap Speed)**: *Given* `lapTime_s == 0`, *When* `newLap()` is called, *Then* lap speed is `0.0` and no `NaN`/`Infinity` is produced.
* **Criterion 2 (Empty Sport List)**: *Given* `getSportTypeId` with empty list, *When* queried, *Then* a fallback sport ID is returned without throwing `IndexOutOfBoundsException`.
* **Criterion 3 (Notification Resurrection Guard)**: *Given* tracking stopped (`TrackingMode.READY`), *When* `updateTimeAndDistanceToNotification` is called, *Then* notification is not posted.
* **Criterion 4 (Rate-Limited Logging & Monotonic Time)**: *Given* persistent exceptions in `tracker` or `ClockTimeTask`, *When* repeating, *Then* logs are throttled to burst 3 then periodic. *Given* timer ticks, *Then* time advances monotonically.
* **Criterion 5 (Deterministic Teardown & Scoping)**: *Given* `AltitudeFromPressureDevice.shutDown()`, *When* invoked, *Then* `registerReceiver` is not called. *Given* `BatterySaverController.release()`, *Then* coroutine scope is cancelled.

---

## 2. Test Specification (`TST-TRK-039`)

### Test Cases
* `TST-TRK-039.1` (**Lap Speed Zero-Duration & Sport List Fallback Unit Tests**): Verifies `newLap()` returns `0.0` when `lapTime_s <= 0` and `getSportTypeId` returns fallback sport ID on empty list in `TrackingRobustnessTest.kt`.
* `TST-TRK-039.2` (**Notification Stop Guard Unit Tests**): Verifies `updateTimeAndDistanceToNotification` suppresses updates when tracking mode is not active/paused in `TrainingApplicationTrackingTest.kt`.
* `TST-TRK-039.3` (**Rate-Limited Error Logging & Clock Monotonicity Tests**): Verifies burst-then-interval error log suppression and monotonic time tracking in `ClockDeviceResilienceTest.kt`.
* `TST-TRK-039.4` (**Lifecycle Teardown & Scope Cancellation Tests**): Verifies excision of redundant receiver registration in `AltitudeFromPressureDeviceTest.kt`, coroutine scope cancellation and brightness clamping in `BatterySaverControllerTest.kt`, and try-with-resources cursor safety in `DeviceManagerTeardownTest.kt`.
* `TST-TRK-039.5` (**Clean-Room Full Suite Regression Execution**): `./gradlew testDebugUnitTest` and `TranslationParityTest` achieve 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-039.1` | Unit | `BANALService.java` | `REQ-TRK-039.1` | In Progress (ATT-4032) |
| `TST-TRK-039.2` | Unit | `TrainingApplication.java` | `REQ-TRK-039.3` | In Progress (ATT-4032) |
| `TST-TRK-039.3` | Unit | `ClockDevice.java`, `TrackerService.java` | `REQ-TRK-039.4` | In Progress (ATT-4032) |
| `TST-TRK-039.4` | Unit/Arch | `AltitudeFromPressureDevice.java`, `BatterySaverController.kt`, `DeviceManager.java` | `REQ-TRK-039.5` | In Progress (ATT-4032) |
| `TST-TRK-039.5` | Regression | `./gradlew testDebugUnitTest`, `TranslationParityTest.kt` | `REQ-TRK-039.6` | In Progress (ATT-4032) |
