# Stage 3 Implementation Plan: ATT-9176 - Polar Verity Sense Optical PPI Streaming & Workout DFA alpha 1 Isolation

**Ticket**: [ATT-9176](https://rainerblind.atlassian.net/browse/ATT-9176) | **Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-20_01_Früh` | **Branch**: `feature/ATT-9176`  
**Requirement**: `REQ-HRV-051` (`REQ-HRV-051.1`..`.4`, refining `SYSREQ-TRK-012`) | **Test Spec**: `TST-HRV-051` (`TST-HRV-051.1`..`.4`)  
**Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-20

---

## 1. Architecture & Target LoC Budget (SWE.2)

| File | Current LoC | Budget / Cap | Action | Requirement |
| :--- | :---: | :---: | :--- | :--- |
| `PolarPpiStreamProcessor.kt` *(new)* | `0` | `<= 140` | New pure-JVM stateful PPI sample processor | `REQ-HRV-051.1` |
| `PolarOnlineStreamingBridge.kt` | `1,228` | `<= 1,310 / 1,500` | Decoupled Verity PPI lifecycle + atomic guard + `processPpiData` | `REQ-HRV-051.1`, `.2` |
| `HrvRollingEngine.kt` | `274` | `<= 290` | Non-sticky `activeWindowHasOpticalPpi` DFA a1 suppression + optical signal quality | `REQ-HRV-051.3` |
| `TrackerService.java` | `3,538` | `3,538 / 3,559` (**`+0` net**) | In-place `!event.isOpticalPpi()` guard on `L293` | `REQ-HRV-051.4` |
| `DeviceManager.java` | `1,707` | `1,707 / 1,708` (**`0` edits**) | Untouched (already sets `RR_PRIORITY_PPG = 2` for Verity Sense) | Invariant |
| `DevicesDatabaseManager.java` | `1,669` | `1,669 / 1,671` (**`0` edits**) | Untouched | Invariant |
| `PolarVeritySenseOfflineController.kt` | `1,473` | `1,473` (**`0` edits**) | Untouched (offline `HR` independent of PMD `PPI`) | Invariant |
| `RrBeatEvent.kt` / `DfaCalculator.kt` | existing | **0 edits** | Untouched (`isOpticalPpi` already delivered in `ATT-9177`) | Invariant |

---

## 2. Atomic Implementation Steps

### Step 0: Pure-JVM `PolarPpiStreamProcessor.kt` (`REQ-HRV-051.1` / `TST-HRV-051.1`)
- Create `app/src/main/java/com/atrainingtracker/banalservice/devices/bluetooth_le/polar/PolarPpiStreamProcessor.kt`:
  - Data/state fields (guarded via `@Synchronized` on `processSamples` & `reset`): `lastBeatTimestampMs: Double = 0.0`, `lastNormalRrMs: Double = RrBeatNormalization.DEFAULT_REFERENCE_RR_MS`, `packetCounter: Int = 0`.
  - Pure helper `isHardwareArtifact(sample: PolarPpiData.PolarPpiSample): Boolean = sample.blockerBit || (sample.skinContactSupported && !sample.skinContactStatus)`.
  - `processSamples(samples: List<PolarPpiData.PolarPpiSample>, packetArrivalMs: Long): List<RrBeatEvent>`:
    1. Filter to `positiveSamples = samples.filter { it.ppi > 0 }`; if empty, return `emptyList()` (zero/negative `ppi` dropped outright).
    2. Increment `packetCounter++` and invoke `RrBeatNormalization.reconstructBundle(rrListMs = positiveSamples.map { it.ppi.toDouble() }, packetArrivalMs = packetArrivalMs, previousBeatTimestampMs = lastBeatTimestampMs, previousNormalRrMs = lastNormalRrMs, packetCounter = packetCounter, protocol = ProtocolType.BLE)` to obtain monotonic UTC timestamps and drift gaps.
    3. Maintain `var cleanRefRrMs = lastNormalRrMs` while iterating `bundle.events.zip(positiveSamples)` per beat:
       - `val hardwareFlagged = isHardwareArtifact(sample)`
       - `val outOfRange = RrBeatNormalization.isOutOfPhysiologicalRange(event.rrMs)`
       - Recompute `physGap = RrBeatNormalization.estimatePhysiologicalGapBeats(event.rrMs, cleanRefRrMs)` against the clean-only reference RR; preserve any beat-0 drift gap via `val effectiveGapBeats = if (outOfRange) maxOf(event.gapBeats, physGap) else event.gapBeats` (giving `gapBeats = 0` for non-drifted physiological hardware-flagged beats).
       - If `hardwareFlagged`, stamp `artifactFlag = RrBeatEvent.ARTIFACT_GAP`, `gapBeats = effectiveGapBeats`, `isOpticalPpi = true` and **do NOT** update `cleanRefRrMs`.
       - Else, keep `event.artifactFlag` (`ARTIFACT_NORMAL` if physiological & no drift gap, `ARTIFACT_GAP` if out-of-range/drift gap), stamp `gapBeats = effectiveGapBeats`, `isOpticalPpi = true`, and if `!outOfRange` update `cleanRefRrMs = event.rrMs`.
    4. Commit `lastBeatTimestampMs = bundle.lastBeatTimestampMs` and `lastNormalRrMs = cleanRefRrMs`; return the list of `RrBeatEvent`s.
  - `reset()` clears `lastBeatTimestampMs = 0.0`, `lastNormalRrMs = RrBeatNormalization.DEFAULT_REFERENCE_RR_MS`, and `packetCounter = 0` (plus `@VisibleForTesting fun getLastNormalRrMsForTesting(): Double`).
- **Unit Test**: Create `app/src/test/java/com/atrainingtracker/banalservice/devices/bluetooth_le/polar/PolarPpiStreamProcessorTest.kt` tagged with hyphenated `// REQ-HRV-051.1 / TST-HRV-051.1` verifying valid batches, `ppi <= 0` drops, `blockerBit` & `skinContactSupported && !skinContactStatus` -> `ARTIFACT_GAP` (`gapBeats = 0`), `skinContactSupported == false` pass-through, clean-only `lastNormalRrMs` stability (even when a physiological hardware-flagged beat precedes an out-of-range beat), `RrArtifactFilter.filterLiveCausal` & `filterBidirectionalBeats` artifact preservation + bias-free `rMSSD` exclusion, and `reset()`.

### Step 1: Decoupled Verity Sense PPI Lifecycle in `PolarOnlineStreamingBridge.kt` (`REQ-HRV-051.1`, `REQ-HRV-051.2` / `TST-HRV-051.2`)
- Add imports `PolarPpiData`, `CoroutineStart`, `Job` (`kotlinx.coroutines.Job` already imported).
- Add fields: `private val isPpiStreamingActive = AtomicBoolean(false)`, `private val ppiLifecycleLock = Any()`, `@Volatile private var ppiJob: Job? = null`, `private val ppiStreamProcessor = PolarPpiStreamProcessor()`.
- **Decoupled 3-Hook Launch** (NEVER nested in `else if` behind `accJob`/`gyroJob` or gated on `isGyroStreamingActive`):
  1. In `startImuCollection()` (`L807`): after `startAccCollection(api)` and `if (isVeritySense) startGyroCollection(api)`, call `if (isVeritySense) startPpiCollection(api)`.
  2. In `syncAlreadyOpenSessionIfReady(api)` (`L742-754`): when `streamingReadyId != null`, keep ACC/GYRO logic intact AND **independently** check `if (isVeritySense && !isPpiStreamingActive.get() && ppiJob?.isActive != true) { startPpiCollection(api) }`.
  3. Rename `maybeStartVerityGyroIfReady()` (`L795`) -> `maybeStartVerityStreamsIfReady()` (called from `handleDisCharacteristic` `L782` and `bleSdkFeatureReady(FEATURE_POLAR_OFFLINE_RECORDING)` `L554`): independently check `!isGyroStreamingActive.get() -> startGyroCollection(api)` AND `!isPpiStreamingActive.get() -> startPpiCollection(api)` when `FEATURE_POLAR_ONLINE_STREAMING` is ready.
- **Race-Free Atomic Launch & Teardown (`startPpiCollection` / `cancelPpiJob`)**:
  - Under `synchronized(ppiLifecycleLock)`: return immediately if `isShutDown.get() || !isVeritySense || !isPpiStreamingActive.compareAndSet(false, true)`. Create `val job = scope.launch(start = CoroutineStart.LAZY) { ... }`, assign `ppiJob = job`, then exit lock and invoke `job.start()`.
  - Inside coroutine: query `api.getAvailableOnlineStreamDataTypes(sessionId)`; if `!availableTypes.contains(PolarBleApi.PolarDeviceDataType.PPI)` return cleanly; otherwise collect `api.startPpiStreaming(sessionId).catch { ... }.collect { processPpiData(it, System.currentTimeMillis()) }`. In `finally`, under `synchronized(ppiLifecycleLock)`, clear `ppiJob = null; isPpiStreamingActive.set(false)` **strictly if `ppiJob === coroutineContext[Job]`** (eliminating stale-finally clobber across fast disconnect/reconnect).
  - `cancelPpiJob()` extracts `ppiJob`, clears `ppiJob = null` & `isPpiStreamingActive.set(false)` under `synchronized(ppiLifecycleLock)`, and calls `toCancel?.cancel()` outside the lock; invoke `cancelPpiJob()` from `cancelActiveStreamJobs()`, `deviceDisconnected`, `blePowerStateChanged(false)`, and `shutDown()`.
  - In `shutDown()` (`L1194-1199`): inside `if (isVeritySense)` (mirroring `PmdMeasurementType.GYRO` so H10 NEVER calls `stopStreaming(PPI)`), invoke `try { api.stopStreaming(streamId, PmdMeasurementType.PPI) } catch (_: Throwable) {}`.
- **`processPpiData(data: PolarPpiData, packetArrivalMs: Long)` & `mRrIntervalSensor` Precision**:
  - Guard `if (isShutDown.get()) return`; process `val events = ppiStreamProcessor.processSamples(data.samples, packetArrivalMs)`; if `events.isEmpty()` return.
  - Lazily create/register `mRrIntervalSensor` if `mRrIntervalSensor == null` (matching `dispatchRrSamples`).
  - For each `event`: emit `mRrIntervalSensor?.newValue(Math.round(event.rrMs).toInt())` **ONLY when `event.artifactFlag == RrBeatEvent.ARTIFACT_NORMAL`** (matching `ANTHeartRateDevice.java:282`); always call `dispatchRrBeat(event)` (feeding `perDeviceHrvController` + `rrBeatListeners`).
- **Rule 51 Reset**: Call `ppiStreamProcessor.reset()` in BOTH `unregisterSensors()` (`L1148`) and `onAccumulatorsReset()` (`L1143`).
- Add `@VisibleForTesting` probes `isPpiStreamingForTesting()` and `isPpiJobActiveForTesting()`.
- **Unit Test**: Extend `PolarOnlineStreamingBridgeTest.kt` (`// REQ-HRV-051.2 / TST-HRV-051.2`) covering all 3 decoupled readiness hooks, H10 non-PPI guard (zero `startPpiStreaming` / zero `stopStreaming(PPI)`), rapid duplicate readiness callbacks -> single `startPpiStreaming`, `ARTIFACT_GAP` omitted from `mRrIntervalSensor` while dispatched to `RrBeatListener`/`perDeviceHrvController`, disconnect/`blePowerStateChanged(false)`/`onAccumulatorsReset()`/`shutDown()` teardown + processor reset.

### Step 2: Dual Live `DFA alpha-1` Suppression & Optical Quality Mode in `HrvRollingEngine.kt` (`REQ-HRV-051.3` / `TST-HRV-051.3`)
- In `HrvRollingEngine.evaluate(nowMs)` (`L165-189`):
  - Compute `val activeWindowHasOpticalPpi = allBeats.any { it.beatTimestampMs >= dfaCutoff && it.isOpticalPpi }`.
  - Gate `alpha1Value`: `if (activeWindowHasOpticalPpi || dfaWindowBeats.isEmpty() || artifactRate > effectiveGateThreshold) null else ...`.
  - Pass `isOpticalPpi = activeWindowHasOpticalPpi` to `HrvSignalQualityAnalyzer.analyzeBeats(beats = dfaWindowBeats, artifactRatePercent = artifactRate, rmssdMs = rmssdValue, dfaAlpha1 = alpha1Value, isOpticalPpi = activeWindowHasOpticalPpi)`.
  - Because `HRVMetricsDevice` and `PerDeviceHrvController` both delegate to `HrvRollingEngine`, both global `"Bester Sensor"` and pinned `[Polar Verity Sense]` tiles automatically suppress `HRV_DFA_A1` while computing live `HRV_RMSSD`, `HRV_SDNN`, `HRV_ARTIFACT_RATE`, and optical quality status, and recover non-stickily once ECG beats replace the active `120 s` window.
- **Unit Tests**: Extend `HrvRollingEngineTest.kt`, `HRVMetricsDeviceTest.kt`, and `PerDeviceHrvControllerTest.kt` (`// REQ-HRV-051.3 / TST-HRV-051.3`) verifying optical `DFA a1 == null`, active `rMSSD`/`SDNN` + optical `HrvSignalQualityStatus.VALID`, non-sticky ECG recovery after 120s window rollover & `resetSession()`, and bit-identical `HrvGoldenOracleTest`.

### Step 3: `WorkoutRr.db` Optical Isolation in `TrackerService.java` (`REQ-HRV-051.4` / `TST-HRV-051.4`)
- Replace `TrackerService.java:293` in-place (**`+0` net LoC**, stays `3,538/3,559`):
  - From: `if (mAutoPauseManager.getCurrentState() == AutoPauseState.TRACKING && mWorkoutID > 0) {`
  - To: `if (!event.isOpticalPpi() && mAutoPauseManager.getCurrentState() == AutoPauseState.TRACKING && mWorkoutID > 0) {`
- **Unit Test**: Create `app/src/test/java/com/atrainingtracker/trainingtracker/tracker/TrackerServiceRrOpticalIsolationTest.kt` (reusing `TrackerServiceRrFlushTest`'s pure-JVM `TestableTrackerService` pattern, `// REQ-HRV-051.4 / TST-HRV-051.4`) verifying optical PPI beats (`isOpticalPpi = true`) produce 0 queued/persisted `WorkoutRrInterval` rows during `AutoPauseState.TRACKING` (`mWorkoutID = 42L`), while ECG beats (`isOpticalPpi = false`) queue and flush identically to before.

### Step 4: Living Docs & System Traceability Sync + Full Verification
- Promote `REQ-HRV-051` and `TST-HRV-051` from `[Planned]` to `[Implemented]` in `docs/requirements/req_aftermath_periods.md` & `docs/tests/tst_aftermath_periods.md` (with bumped versions + Change History rows) and add `PolarPpiStreamProcessor.kt` to `SYSREQ-TRK-012` in `docs/system/sysreq_live_tracking.md` & `docs/system/system_software_traceability.md` now that the file exists (`Rule 81`).
- Run `python3 tools/verify_requirement_governance.py --repo-root . --sync-indexes` and `--base-ref refs/heads/sprint/2026-11-20_01_Früh`.
- Run locked incremental Gradle verification (`Rule 7.5` / `Rule 8.6` — NO `--rerun-tasks`, NO `-PstampGitHash=true`, `0` Kotlin compiler warnings across `:app:compileDebugKotlin` & `:app:compileDebugUnitTestKotlin`):
  ```bash
  python3 tools/parallel_sprint_lock.py --timeout 1800 -- \
    ./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:testDebugUnitTest --rerun \
      --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarPpiStreamProcessorTest" \
      --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarOnlineStreamingBridgeTest" \
      --tests "com.atrainingtracker.banalservice.devices.hrv.HrvRollingEngineTest" \
      --tests "com.atrainingtracker.banalservice.devices.hrv.HRVMetricsDeviceTest" \
      --tests "com.atrainingtracker.banalservice.devices.hrv.PerDeviceHrvControllerTest" \
      --tests "com.atrainingtracker.banalservice.devices.hrv.HrvGoldenOracleTest" \
      --tests "com.atrainingtracker.banalservice.devices.hrv.HrvSignalQualityAnalyzerTest" \
      --tests "com.atrainingtracker.banalservice.devices.hrv.RrArtifactFilterTest" \
      --tests "com.atrainingtracker.trainingtracker.tracker.TrackerServiceRrOpticalIsolationTest" \
      --tests "com.atrainingtracker.trainingtracker.tracker.TrackerServiceRrFlushTest" \
      --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest" \
      --tests "com.atrainingtracker.trainingtracker.build.SystemLiveTrackingRequirementsContractTest" \
      --tests "com.atrainingtracker.trainingtracker.build.DeadCodeEliminationContractTest" \
      --tests "com.atrainingtracker.trainingtracker.build.InterfaceSignatureDriftContractTest" \
      --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest"
  ```

---

## 3. Invariants & Hygiene Checklist
- **Protected Monoliths**: `0` edits to `DeviceManager.java` (`1,707/1,708`), `DevicesDatabaseManager.java` (`1,669/1,671`), `PolarVeritySenseOfflineController.kt` (`1,473`); `+0` net LoC in `TrackerService.java` (`3,538/3,559`); `PolarOnlineStreamingBridge.kt <= 1,310/1,500`.
- **Static Traceability & Hygiene**: Hyphenated `// REQ-HRV-051.x / TST-HRV-051.x` comments in all new/modified tests (`REQ-PRO-099.3`); `0` `src/main` source-text reads in tests (`REQ-PRO-049.1`); `0` Kotlin compiler warnings; `Localization Parity: N/A - no new UI strings`; zero touches to the 5 user-owned dirty/untracked paths.
