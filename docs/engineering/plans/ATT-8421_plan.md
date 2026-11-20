# Stage 3: Implementation Plan - ATT-8421: iTPMS Persisted Baseline Wiring, Live Reset Propagation & Null GNSS Gating

**Ticket**: `ATT-8421` (`2 SP`, `Bug`, `Priority: Medium`, `severity-major`) | **Parent Epic**: `ATT-166` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-20_01_Früh` (`465`) | **Branch**: `feature/ATT-8421` | **Author**: AI Agent 1 (`Pro` / Neon) | **Date**: 2026-11-20  
**Requirement Mapping**: `REQ-SEN-034` (`REQ-SEN-034.1`–`.3`) | **Test Mapping**: `TST-SEN-034` (`TST-SEN-034.1`–`.3`)

---

## 1. Architecture, Threading & LoC Budget (`SWE.2` / `SWE.3`)
* **Layer Boundaries & Threading**:
  * `DeviceDataRepository.kt` (`banalservice/ui/devices/devicedata/`) emits device IDs on a companion buffered `MutableSharedFlow<Long>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)` whenever `baselineCircumference` is persisted/reset (`resetTirePressureBaseline(id, mm)`, `resetTirePressureBaseline(id, null)`, or `updateDevice()` with changed `baselineCircumference`).
  * `BANALServiceRepository.kt` (`trainingtracker/repositories/`, outside UI/domain so `DevicesDatabaseManager` access is 100% compliant with `ArchitectureConformanceContractTest`) loads `DevicesDatabaseManager.getBaselineCircumference(devId)` off the main thread on injectable `itpmsIoDispatcher: CoroutineDispatcher = Dispatchers.IO` into `cachedBaselineCircumferences: ConcurrentHashMap<Long, Double>`, pushing sanitized values (`raw.takeIf { it.isFinite() && it > 0.0 } ?: 0.0`) onto live `perDeviceItpmsEstimators[devId]` instances.
  * Zero SQLite schema bumps (`Devices.db` v8 unchanged), zero `TrackerService` / `BANALService` / `DeviceManager` / `MyBTLEDevice` edits (`REQ-PRO-114` N/A), zero UI / XML edits (`Localization Parity: N/A - already localized by ATT-8422`).
* **Touched Production Files & Exact LoC Budgets (All Well Below `1,500` Cap)**:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/repositories/BANALServiceRepository.kt`: `1,186 -> ~1,275 / 1,500` LoC (`> 225` LoC headroom).
  * `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicedata/DeviceDataRepository.kt`: `738 -> ~758 / 1,500` LoC (`> 740` LoC headroom).
* **Untouched Protected Monoliths & Sibling Files (`0` LoC Edits)**:
  * `DevicesDatabaseManager.java` (`1,669 / 1,671`), `EditDeviceDialog.kt` (`1,774 / 2,004`), `WheelCircumferenceSection.kt` (`169`), `WheelCircumferenceHistoryChart.kt` (`299`), `IndirectTpmsEstimator.kt` (`283`), `DeviceManager.java` (`1,708`), `TrackerService.java` (`3,559`).

---

## 2. UI Consistency (`docs/design_guidelines.md`)
* **N/A — Zero UI or XML modifications**: Defect 3 (`"Baseline: $baselineMm mm"`) is already resolved by `ATT-8422` (`00549967`) via `R.string.devices_wheel_baseline_format` across all 9 active locales (`values`, `de/es/fr/it/ja/nl/pl/pt`) in `WheelCircumferenceSection.kt:134`. Regression coverage is retained via `TranslationParityTest` and `EditDeviceWheelCircumferenceTest`.

---

## 3. System Invariants & Preserved Contracts
1. **Estimator Math & Thresholds**: `IndirectTpmsEstimator` thresholds (`<= 6.0 m`, `< 3.0 deg/s`, `>= 4.0 m/s`, `2.0 km` lock, `-0.6%` low-pressure, `-1.0%` CUSUM) and `DualWheelTpmsEstimator` thresholds remain untouched.
2. **Zero-Baseline Overwrite & Idempotency**: Every async load / reset / broadcast / `clearBreadcrumbs()` / `READY -> TRACKING` refresh overwrites `estimator.baselineCircumferenceMeters` with `0.0` whenever the stored value is `<= 0.0` or non-finite; dual emission of `CALIBRATION_FACTOR_CHANGED` + `baselineCircumferenceChanged` on `resetTirePressureBaseline(id, mm)` is strictly idempotent.
3. **GPS-Free Dual-Wheel Isolation**: Moving `location != null` and GNSS `acc`/`bearing` guards strictly into Section 1 keeps Section 2 (`DualWheelTpmsEstimator`) operational under `TrackingMode.TRACKING` even when GNSS `location`, `accuracy`, or `bearing` is `null` or non-finite.
4. **JVM Test Safety & Hygiene**: `CALIBRATION_FACTOR_CHANGED` registered strictly via `registerInternalReceiver()` (safe at `Build.VERSION.SDK_INT == 0`); `BANALServiceRepository.resetForTesting()` cancels previous `repositoryScope` to prevent leaked collectors; `0` `src/main` reads (`REQ-PRO-049.1`), `0` compiler warnings, `0` dead code (`DeadCodeEliminationContractTest`), Gate 3 Code Lock (`0` edits under `app/` in Stage 3).

---

## 4. Atomic Stage 4 Implementation Sequence

### Step 0: Gate 3 Code Lock Verification (`Rule 4`)
* Run `python3 /home/rainer/AndroidStudioProjects/aTrainingTracker/tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` and confirm `GATE_PASSED` (exit `0`) before modifying any file under `app/src/`.

### Step 1: Buffered Reactive Baseline Signal in `DeviceDataRepository` (`REQ-SEN-034.1` -> `TST-SEN-034.1`)
* **Target**: `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicedata/DeviceDataRepository.kt` (`738 -> ~758` LoC).
* In `companion object` (beside `_deviceBatteryUpdates` at `:150`), add:
  * `private val _baselineCircumferenceChanged = MutableSharedFlow<Long>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)`
  * `@JvmStatic val baselineCircumferenceChanged: SharedFlow<Long> = _baselineCircumferenceChanged.asSharedFlow()`
  * `@JvmStatic fun notifyBaselineCircumferenceChanged(deviceId: Long) { _baselineCircumferenceChanged.tryEmit(deviceId) }`
* Emit `notifyBaselineCircumferenceChanged(deviceId)` in:
  1. `resetTirePressureBaseline(deviceId, circumferenceMm)` (`:527-547`) after **both** the `circumferenceMm != null && circumferenceMm > 0` branch and the `else` (`devicesDatabaseManager.resetBaselineCircumference(deviceId)`) branch.
  2. `updateDevice(finalState)` (`:423-436`) whenever `originalState.baselineCircumference != finalState.baselineCircumference`.

### Step 2: Persisted Baseline Cache, Live Propagation & Reloads in `BANALServiceRepository` (`REQ-SEN-034.1` -> `TST-SEN-034.1`)
* **Target**: `app/src/main/java/com/atrainingtracker/trainingtracker/repositories/BANALServiceRepository.kt` (`1,186 -> ~1,275` LoC).
* Add state & injectable test seams (exercised directly in `BANALServiceRepositoryItpmsWiringTest`, zero dead code):
  * `private val cachedBaselineCircumferences = ConcurrentHashMap<Long, Double>()`
  * `@Volatile private var itpmsIoDispatcher: CoroutineDispatcher = Dispatchers.IO`
  * `@Volatile private var baselineCircumferenceLoader: (Long) -> Double = { devId -> DevicesDatabaseManager.getInstance(appContext).getBaselineCircumference(devId) }`
  * `@VisibleForTesting internal fun configureItpmsForTesting(loader: (Long) -> Double, ioDispatcher: CoroutineDispatcher = Dispatchers.IO)`
* Add helpers:
  * `private fun sanitizeBaselineMeters(raw: Double): Double = if (raw.isFinite() && raw > 0.0) raw else 0.0`
  * `private fun applyResolvedBaseline(deviceId: Long, rawBaselineMeters: Double)`: computes `resolved = sanitizeBaselineMeters(rawBaselineMeters)`, stores `cachedBaselineCircumferences[deviceId] = resolved`, and updates `perDeviceItpmsEstimators[deviceId]?.baselineCircumferenceMeters = resolved` (explicitly overwriting with `0.0` when `<= 0.0`/non-finite).
  * `fun refreshItpmsBaselineForDevice(deviceId: Long)`: launches on `repositoryScope` + `itpmsIoDispatcher`, loads `baselineCircumferenceLoader(deviceId)` inside `try/catch`, calls `applyResolvedBaseline(deviceId, loaded)`.
  * `fun refreshAllItpmsBaselines()`: collects union of `perDeviceItpmsEstimators.keys`, `cachedBaselineCircumferences.keys`, and `_activeRemoteDevicesIds.value` and refreshes each off-main-thread on `itpmsIoDispatcher`.
  * `private fun getOrCreateItpmsEstimator(deviceId: Long): IndirectTpmsEstimator`: reads `cachedBaselineCircumferences[deviceId]`; if absent, triggers `refreshItpmsBaselineForDevice(deviceId)` once; creates/retrieves `perDeviceItpmsEstimators.getOrPut(deviceId) { IndirectTpmsEstimator(cachedBaseline ?: 0.0) }` and syncs cached value if present.
* Wire all live & session-boundary reload hooks:
  1. In `init`: collect `DeviceDataRepository.baselineCircumferenceChanged` -> `refreshItpmsBaselineForDevice(devId)` (matching `EquipmentRepository.equipmentLinksChanged` at `:367`).
  2. Add `calibrationFactorChangedReceiver` delegating to `@VisibleForTesting internal fun handleCalibrationFactorChangedBroadcast(intent: Intent?)`: extracts `intent?.getLongExtra(BANALService.DEVICE_ID, -1L) ?: -1L` (matching `DeviceDataRepository.sendCalibrationChangedBroadcast()` at `:581`); if `> 0L`, calls `refreshItpmsBaselineForDevice(devId)`, else falls back to `refreshAllItpmsBaselines()`. Register strictly via `registerInternalReceiver(calibrationFactorChangedReceiver, IntentFilter(BANALService.CALIBRATION_FACTOR_CHANGED))` (JVM `SDK_INT == 0` safe).
  3. In `clearBreadcrumbs()` (`:720-731`) and `trackingModeReceiver` (`previousMode == TrackingMode.READY && newTrackingMode == TrackingMode.TRACKING` at `:265`): call `refreshAllItpmsBaselines()` so every cached estimator has a fresh DB baseline at workout start.
  4. In `companion object.resetForTesting()`: cancel `INSTANCE?.repositoryScope?.cancel()` before replacing `INSTANCE` so prior test collectors never leak.

### Step 3: Strict Null/Non-Finite GNSS Guard & GPS-Free Dual-Wheel Isolation (`REQ-SEN-034.2` -> `TST-SEN-034.2`)
* **Target**: `app/src/main/java/com/atrainingtracker/trainingtracker/repositories/BANALServiceRepository.kt` (`:589-707`).
* Extract `@VisibleForTesting internal fun processItpmsTelemetryTick(wheelDevices: List<MyDevice>, location: LatLng?, accuracyMeters: Double?, bearingDeg: Double?, speedMps: Double?, altitudeMeters: Double?, yawRateRadPerSec: Double)` and invoke it from `startObservingBANALService()` under `if (TrainingApplication.getTrackingMode() == TrackingMode.TRACKING)` (removing the outer `&& location != null` gate):
  * Pass raw nullable `acc = (binder.getBestSensorData(SensorType.ACCURACY)?.value as? Number)?.toDouble()` (no `?: 5.0`) and `_currentBearing.value` (no `?: 0.0`).
  * **Section 1 (GNSS `IndirectTpmsEstimator`)**: Execute ONLY when `location != null && accuracyMeters != null && accuracyMeters.isFinite() && accuracyMeters >= 0.0 && bearingDeg != null && bearingDeg.isFinite()`. Use `getOrCreateItpmsEstimator(devId)` and pass validated non-null `accuracyMeters` & `bearingDeg`.
  * **Section 2 (GPS-Free `DualWheelTpmsEstimator`)**: Execute unconditionally when `wheelDevices.size >= 2` regardless of whether `location`, `accuracyMeters`, or `bearingDeg` is `null` or non-finite.

### Step 4: Deterministic Behavioral Unit Tests & Locked Incremental Verification (`TST-SEN-034.1`–`.3`)
* **NEW Test File**: `app/src/test/java/com/atrainingtracker/trainingtracker/repositories/BANALServiceRepositoryItpmsWiringTest.kt` (explicit hyphenated `// REQ-SEN-034.x / TST-SEN-034.x` tags, `0` `src/main` reads, `0` `Thread.sleep`, deterministic `StandardTestDispatcher` + `advanceUntilIdle()`, `@After` singleton cleanup):
  1. `testPersistedBaselineLoaded_triggersLowPressureAlertAtStartLock_tstSen034_1`: fake loader returns `2.130`, cold-start async IO hydration populates live estimator before `2.0 km` lock at `2.115 m` (`-0.7%`), asserting `WorkoutNavigationEvents` receives `WARNING` low-pressure alert.
  2. `testZeroOrNonFiniteBaseline_overwritesStaleNonZeroBaseline_tstSen034_1`: estimator starts with `2.130`, loader changes to `0.0` (and `Double.NaN`), reload overwrites `baselineCircumferenceMeters == 0.0` and suppresses `LowPressure` alert.
  3. `testLiveBaselineResetSignals_sharedFlowBroadcastAndClearBreadcrumbs_tstSen034_1`: verifies `DeviceDataRepository.notifyBaselineCircumferenceChanged(id)`, `DeviceDataRepository.resetTirePressureBaseline(id, mm)` / `(id, null)` / `updateDevice()`, `handleCalibrationFactorChangedBroadcast(intent)` (specific ID + fallback `-1L`), and `clearBreadcrumbs()` all update live cached `perDeviceItpmsEstimators[id]` idempotently.
  4. `testNullAndNonFiniteGnssAccuracyOrBearing_skipsIndirectTpmsWithoutFabricatingDefaults_tstSen034_2`: feeds ticks with `accuracy = null`/`NaN`/`-1.0` and `bearing = null`/`NaN` -> `IndirectTpmsEstimator` remains unlocked (`startCircumferenceMeters == 0.0`, zero alerts).
  5. `testNullLocationAccuracyBearing_dualWheelTpmsStillDetectsRearPuncture_tstSen034_2`: feeds `FRONT` + `REAR` wheel ticks with `location = null`, `accuracy = null`, `bearing = null` -> Section 1 skipped while Section 2 (`DualWheelTpmsEstimator`) locks `startRatio` and triggers `CRITICAL` rear puncture alert.
* **Locked Incremental Gradle Command (`Rule 7.5` — NEVER `--rerun-tasks`, NEVER `-PstampGitHash=true`)**:
  ```bash
  python3 tools/parallel_sprint_lock.py --timeout 1800 -- ./gradlew :app:testDebugUnitTest --rerun \
    --tests "com.atrainingtracker.trainingtracker.repositories.BANALServiceRepositoryItpmsWiringTest" \
    --tests "com.atrainingtracker.trainingtracker.repositories.BANALServiceRepositoryMultiWheelTpmsTest" \
    --tests "com.atrainingtracker.trainingtracker.repositories.BANALServiceRepositoryMemoryTest" \
    --tests "com.atrainingtracker.banalservice.tpms.IndirectTpmsEstimatorTest" \
    --tests "com.atrainingtracker.banalservice.ui.devices.editdevice.EditDeviceWheelCircumferenceTest" \
    --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" \
    --tests "*ArchitectureConformanceContractTest" \
    --tests "*DeadCodeEliminationContractTest" \
    --tests "*SourceTextReadingAuditContractTest" \
    --tests "*SystemLiveTrackingRequirementsContractTest" \
    :app:assembleDebug
  ```

### Step 5: Living Docs Promotion to `[Implemented]` & Governance Gate
* Promote `REQ-SEN-034` & `TST-SEN-034` to `[Implemented]` with exact test citations, bump versions + Change History rows, run `--sync-indexes`, run all 4 Python safety linters, and confirm `verify_requirement_governance.py` exits `0` with `0` compiler warnings.

---

## 5. Requirement-to-Step & Test Traceability Matrix

| Requirement | Plan Step(s) | Target File(s) | Test Case | Test Suite |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-SEN-034.1` | Steps 1, 2 | `DeviceDataRepository.kt`, `BANALServiceRepository.kt` | `TST-SEN-034.1` | `BANALServiceRepositoryItpmsWiringTest` |
| `REQ-SEN-034.2` | Step 3 | `BANALServiceRepository.kt` | `TST-SEN-034.2` | `BANALServiceRepositoryItpmsWiringTest` |
| `REQ-SEN-034.3` | Steps 4, 5 | `BANALServiceRepository.kt`, `DeviceDataRepository.kt` (0 XML/monolith edits) | `TST-SEN-034.3` | `EditDeviceWheelCircumferenceTest`, `TranslationParityTest`, `BANALServiceRepositoryMultiWheelTpmsTest`, `IndirectTpmsEstimatorTest`, Governance Contracts |
