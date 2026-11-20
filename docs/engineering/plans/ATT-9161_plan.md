# Stage 3 Implementation Plan: ATT-9161 - Fix PolarOnlineStreamingBridge Re-Entrant Stream Start Burst and Silent IMU Death on PMD ERROR_ALREADY_IN_STATE

**Ticket**: [ATT-9161](https://rainerblind.atlassian.net/browse/ATT-9161) | **Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-20_01_Früh` | **Branch**: `feature/ATT-9161`  
**Requirement**: `REQ-CON-053` (`REQ-CON-053.1`..`.4`, refining `SYSREQ-TRK-001`, `SYSREQ-TRK-002`) | **Test Spec**: `TST-CON-053` (`TST-CON-053.1`..`.4`)  
**Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-20

---

## 1. Architecture, Threading & Target LoC Budget (SWE.2)

| File | Current LoC | Budget / Cap | Action | Requirement |
| :--- | :---: | :---: | :--- | :--- |
| `PolarPmdStreamCoordinator.kt` *(new)* | `0` | `<= 220` | Unified 4-stream atomic CAS/`CoroutineStart.LAZY`/job-identity supervisor, PMD `ERROR_ALREADY_IN_STATE` bounded retry & decoupled `StateFlow<PolarPmdStreamWarning?>` bus | `REQ-CON-053.1`–`.3` |
| `PolarOnlineStreamingBridge.kt` | `1,306` | `<= 1,300 / 1,500` | Delegate `HR`/`ACC`/`GYRO`/`PPI` stream lifecycle & PMD retry recovery to `PolarPmdStreamCoordinator` (net LoC reduction) | `REQ-CON-053.1`–`.4` |
| `PolarPmdStreamWarningEffect.kt` *(new)* | `0` | `<= 45` | Shared `@Composable` Snackbar effect in `com.atrainingtracker.banalservice.ui.devices.polar` (`0` upward `banalservice -> trainingtracker.ui` imports) | `REQ-CON-053.3` |
| `TrackingTabsScreen.kt` | `1,417` | `<= 1,425 / 1,500` | Mount `PolarPmdStreamWarningEffect(snackbarHostState)` (`+2` LoC) | `REQ-CON-053.3` |
| `DevicesTabbedScreen.kt` | `811` | `<= 820 / 1,500` | Mount `PolarPmdStreamWarningEffect(snackbarHostState)` (`+2` LoC) | `REQ-CON-053.3` |
| `res/values*/strings_devices.xml` (9 files) | existing | `+1` key/locale | Add `polar_live_imu_stream_blocked_warning` across EN/DE/ES/FR/IT/JA/NL/PL/PT | `REQ-CON-053.3` |
| `DeviceManager.java`, `TrackerService.java`, `DevicesDatabaseManager.java` | protected | **`0` edits** | Untouched protected monoliths | `REQ-CON-053.4` |

- **Threading**: Coroutine stream loops execute on injected `dispatcher` (`Dispatchers.IO` in production; `StandardTestDispatcher` in tests); slot transitions serialize under a single lightweight `slotLock` (never held across `suspend` or `cancel()` calls); `StateFlow<PolarPmdStreamWarning?>` publishes thread-safely to Compose `Main` via `LaunchedEffect`.
- **UI Consistency (`docs/design_guidelines.md`)**: Reuses existing Material 3 `SnackbarHost`s in `TrackingTabsScreen` and `DevicesTabbedScreen` via `SnackbarDuration.Long`; introduces zero custom controls, zero `FilterChip`/raw `Switch`, and zero new public `@Preview`s.
- **SQLite / Detailed Design**: Zero SQLite migrations; zero state-machine edits in `TrackerService.java`, `DeviceManager.java`, or `MyBTLEDevice.java`.

---

## 2. Atomic Implementation Steps

### Step 1: Extract `PolarPmdStreamCoordinator.kt` (`REQ-CON-053.1`, `.2`, `.3` / `TST-CON-053.1`–`.3`)
- Create `app/src/main/java/com/atrainingtracker/banalservice/devices/bluetooth_le/polar/PolarPmdStreamCoordinator.kt`:
  - Data model `PolarPmdStreamWarning(val deviceId: Long, val deviceName: String, val polarId: String, val measurementType: PmdMeasurementType, val retryCount: Int)` and enum `StreamSlot { HR, ACC, GYRO, PPI }`.
  - **Companion Warning Bus (Multi-Device Safe)**:
    - `private val _streamWarning = MutableStateFlow<PolarPmdStreamWarning?>(null)` and `val streamWarning: StateFlow<PolarPmdStreamWarning?> = _streamWarning.asStateFlow()`.
    - `fun consumeStreamWarning(polarId: String? = null)`: clears `_streamWarning` unconditionally when `polarId == null`, or via `update { if (it?.polarId == polarId) null else it }` so one sensor never clobbers another sensor's active warning.
    - `@VisibleForTesting fun resetWarningsForTesting()` clears `_streamWarning.value = null`.
  - **Error Classifier**: `fun isPmdAlreadyInStateError(t: Throwable): Boolean` checks `(t as? BleControlPointCommandError)?.error == PmdControlPointResponse.PmdControlPointResponseCode.ERROR_ALREADY_IN_STATE`, `(t.cause as? BleControlPointCommandError)?.error == ERROR_ALREADY_IN_STATE`, or `t.message?.contains("ERROR_ALREADY_IN_STATE", ignoreCase = true) == true`.
  - **Atomic 4-Stream Slot Supervision**:
    - Per-slot `SlotState(val active = AtomicBoolean(false), var job: Job? = null)` map protected by `private val slotLock = Any()`.
    - `fun launchStream(slot: StreamSlot, preAcquire: (() -> Boolean)? = null, onFinalCleanup: (() -> Unit)? = null, block: suspend () -> Unit): Boolean`:
      1. Inside `synchronized(slotLock)`: if `isShutDownProvider() || !state.active.compareAndSet(false, true)` return `false`.
      2. If `preAcquire != null && !preAcquire.invoke()` -> rollback `state.active.set(false)` and return `false` (ensures `ACC` claims CAS before `tryAcquireAccChannel` and rolls back cleanly when `ACC_OWNER_OFFLINE` holds lock).
      3. Create `val launched = scope.launch(start = CoroutineStart.LAZY) { val myJob = coroutineContext[Job]; try { block() } finally { val shouldRelease = synchronized(slotLock) { if (state.job === myJob) { state.job = null; state.active.set(false); true } else false }; if (shouldRelease) onFinalCleanup?.invoke() } }` and publish `state.job = launched`.
      4. Outside `slotLock`: `launched.start()`, return `true`.
    - `fun cancelAllStreams(onAccReleased: () -> Unit)`: snapshots all jobs under `slotLock`, clears `job = null` & `active.set(false)`, tracks whether `ACC` was active, then outside `slotLock` cancels all jobs and invokes `onAccReleased()`.
    - `fun isActive(slot: StreamSlot): Boolean` & `fun isJobActive(slot: StreamSlot): Boolean`.
  - **Bounded PMD `ERROR_ALREADY_IN_STATE` Retry Loop**:
    - Constants: `MAX_ALREADY_IN_STATE_RETRIES = 3`, `RETRY_BACKOFF_MS = longArrayOf(500L, 1_000L, 2_000L)`.
    - Per-instance `private val warningEmittedForSession = AtomicBoolean(false)` reset via `resetSessionWarningState(polarId: String)` (sets `false` and calls `consumeStreamWarning(polarId)` strictly if owned by `polarId`).
    - `suspend fun <T> collectPmdWithAlreadyInStateRecovery(pmdType: PmdMeasurementType, sessionId: String, stopStreaming: () -> Unit, streamFactory: () -> Flow<T>, onSample: (T) -> Unit)`:
      - Loops with `var attempt = 0`: invokes `streamFactory()` fresh on each iteration, resets `attempt = 0` inside `collect { attempt = 0; onSample(it) }`.
      - On `catch (ce: CancellationException) { throw ce }`; on `catch (e: Throwable)`:
        - If `!isPmdAlreadyInStateError(e)`: log `Log.w(TAG, ...)` and break cleanly.
        - If `attempt < MAX_ALREADY_IN_STATE_RETRIES && isConnectedProvider() && !isShutDownProvider()`:
          - `val backoffMs = RETRY_BACKOFF_MS[attempt]`
          - `attempt++`
          - Log structured warning (`attempt/$MAX_ALREADY_IN_STATE_RETRIES`, `pmdType`, `sessionId`, `backoffMs`).
          - `runCatching { stopStreaming() }.onFailure { if (it is CancellationException) throw it }`
          - `delayFn(backoffMs)`
          - Re-check `if (!isConnectedProvider() || isShutDownProvider()) break` before next loop iteration!
        - Else (exhausted while connected & not shut down):
          - Log structured diagnostic warning (Polar ID, MAC, `pmdType`, retry count, Polar Flow / stale PMD state hint).
          - If `warningEmittedForSession.compareAndSet(false, true)`, publish `PolarPmdStreamWarning(...)` to `_streamWarning`.
          - Break cleanly.
- **Unit Test**: Create `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/polar/PolarPmdStreamCoordinatorTest.kt` tagged `// REQ-CON-053.1..3 / TST-CON-053.1..3` (pure JVM, `StandardTestDispatcher`, verifying atomic CAS, `preAcquire` rollback, `slot.job === myJob` non-clobbering, typed/cause/message error detection, `500/1000/2000 ms` backoff + `stopStreaming` + fresh factory invocation + first-packet retry reset, `CancellationException` propagation, multi-device warning ownership & per-session deduplication).

### Step 2: Refactor `PolarOnlineStreamingBridge.kt` onto `PolarPmdStreamCoordinator` (`REQ-CON-053.1`, `.2`, `.4` / `TST-CON-053.1`, `.2`, `.4`)
- Replace `ppiLifecycleLock`, `isAccStreamingActive`, `isGyroStreamingActive`, `isPpiStreamingActive`, `hrJob`, `accJob`, `gyroJob`, `ppiJob`, and `cancelPpiJob()` with a single `private val streamCoordinator: PolarPmdStreamCoordinator`.
- Wire all 4 stream launchers (`startHrCollection`, `startAccCollection`, `startGyroCollection`, `startPpiCollection`) through `streamCoordinator.launchStream(...)`:
  - `HR`: atomic CAS prevents 7x `00002a37` toggle bursts; uses `try { api.startHrStreaming(sessionId).collect { ... } } catch (ce: CancellationException) { throw ce } catch (t: Throwable) { Log.w(...) }`.
  - `ACC`: `preAcquire = { PolarOfflineGattCoordinator.tryAcquireAccChannel(accOwnerKey, ACC_OWNER_ONLINE) }`, `onFinalCleanup = { PolarOfflineGattCoordinator.releaseAccChannel(accOwnerKey, ACC_OWNER_ONLINE) }`, resolves settings once then invokes `collectPmdWithAlreadyInStateRecovery(PmdMeasurementType.ACC, ...)`.
  - `GYRO`: resolves settings once then invokes `collectPmdWithAlreadyInStateRecovery(PmdMeasurementType.GYRO, ...)`.
  - `PPI`: checks `isVeritySense` & `getAvailableOnlineStreamDataTypes(sessionId).contains(PPI)` then invokes `collectPmdWithAlreadyInStateRecovery(PmdMeasurementType.PPI, ...)`.
- Reset session warning state via `streamCoordinator.resetSessionWarningState(polarId)` on `markBridgeConnected`, `deviceDisconnected`, `blePowerStateChanged(false)`, and `shutDown()`, and cancel all 4 slots cleanly via `streamCoordinator.cancelAllStreams { releaseOnlineAccChannels() }`.
- Preserve ALL existing `@VisibleForTesting` getters (`isAccStreamingForTesting()`, `isGyroStreamingForTesting()`, `isPpiStreamingForTesting()`, `isHrJobActiveForTesting()`, `isAccJobActiveForTesting()`, `isGyroJobActiveForTesting()`, `isPpiJobActiveForTesting()`), `ATT-8994` `deactivateSensor()` calls, `ATT-9176` `PolarPpiStreamProcessor` calls, and `InterfaceSignatureDriftContractTest` signatures while shrinking `PolarOnlineStreamingBridge.kt` to `<= 1,300 / 1,500` LoC.
- **Unit Test**: Extend `PolarOnlineStreamingBridgeTest.kt` (`// REQ-CON-053.1..4 / TST-CON-053.1..4`) testing burst re-entrancy across all 4 streams while `resolveAccSetting`/`resolveGyroSetting` suspend, `ACC_OWNER_OFFLINE` rollback, `ERROR_ALREADY_IN_STATE` recovery on ACC/GYRO/PPI, exhaustion warning emission + disconnect reset, and zero regression on `ATT-9176` & `ATT-8994`.

### Step 3: 9-Locale Strings + Shared `PolarPmdStreamWarningEffect.kt` + Host Wiring (`REQ-CON-053.3` / `TST-CON-053.3`)
- Add `polar_live_imu_stream_blocked_warning` across all 9 `app/src/main/res/values*/strings_devices.xml` files (`values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`) matching the exact Stage 2 parity table.
- Create `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/polar/PolarPmdStreamWarningEffect.kt` (`<= 45` LoC): observes `PolarPmdStreamCoordinator.streamWarning` via `LaunchedEffect(Unit)`, formats `context.getString(R.string.polar_live_imu_stream_blocked_warning, warning.deviceName, warning.measurementType.name)`, shows `snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long)`, and calls `PolarPmdStreamCoordinator.consumeStreamWarning(warning.polarId)`.
- **Production Reachability Wiring (`Rule 81`)**: Mount `PolarPmdStreamWarningEffect(snackbarHostState)` in `TrackingTabsScreen.kt` (`+2` LoC -> `~1,419 / 1,500`) and `DevicesTabbedScreen.kt` (`+2` LoC -> `~813 / 1,500`).
- **UI & L10n Tests**: Create `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/polar/PolarPmdStreamWarningEffectTest.kt` using `androidx.compose.ui.test.junit4.v2.createComposeRule` (`0` deprecation warnings, `// REQ-CON-053.3 / TST-CON-053.3`), verifying Snackbar message rendering + `consumeStreamWarning()` dismissal; run `TranslationParityTest`.

### Step 4: Living Docs Sync & Locked Incremental Gradle Verification
- Promote `REQ-CON-053` & `TST-CON-053` from `[Planned]` to `[Implemented]`; add `PolarPmdStreamCoordinator.kt` to `SYSREQ-TRK-002` in `docs/system/sysreq_live_tracking.md` & `docs/system/system_software_traceability.md` (`Rule 81`); sync indexes & run `verify_requirement_governance.py`.
- Execute locked incremental Gradle verification (`Rule 7.5`/`Rule 8.6`, NO `--rerun-tasks`, NO `-PstampGitHash=true`, `0` new Kotlin compiler warnings):
  ```bash
  python3 tools/parallel_sprint_lock.py --timeout 1800 -- \
    ./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:testDebugUnitTest --rerun \
      --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarPmdStreamCoordinatorTest" \
      --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarOnlineStreamingBridgeTest" \
      --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarPpiStreamProcessorTest" \
      --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarOfflineGattCoordinatorCoexistenceTest" \
      --tests "com.atrainingtracker.banalservice.ui.devices.polar.PolarPmdStreamWarningEffectTest" \
      --tests "com.atrainingtracker.trainingtracker.TranslationParityTest" \
      --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest" \
      --tests "com.atrainingtracker.trainingtracker.build.InterfaceSignatureDriftContractTest" \
      --tests "com.atrainingtracker.trainingtracker.build.DeadCodeEliminationContractTest" \
      --tests "com.atrainingtracker.trainingtracker.build.SystemLiveTrackingRequirementsContractTest" \
      --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest"
  ```

---

## 3. Invariants & Hygiene Checklist
- **Protected Monoliths**: `0` edits to `DeviceManager.java`, `TrackerService.java`, `DevicesDatabaseManager.java`; `PolarOnlineStreamingBridge.kt <= 1,300 / 1,500` LoC; `TrackingTabsScreen.kt <= 1,425 / 1,500` LoC.
- **Layering & Static Hygiene**: `0` upward `banalservice -> trainingtracker.ui` imports; `0` `src/main` source reads in tests (`REQ-PRO-049.1`); hyphenated `// REQ-CON-053.x / TST-CON-053.x` test tags (`REQ-PRO-099.3`); `v2.createComposeRule` (`0` deprecation warnings); 9-locale `strings_devices.xml` parity; zero touches to user-owned dirty/untracked paths.
