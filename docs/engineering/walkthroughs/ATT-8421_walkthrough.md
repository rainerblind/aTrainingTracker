# Stage 5: Verification & Walkthrough - ATT-8421: iTPMS Persisted Baseline Wiring, Live Reset Propagation & Null GNSS Gating

**Ticket**: `ATT-8421` (`2 SP`, `Bug`, `Priority: Medium`, `severity-major`) | **Parent Epic**: `ATT-166` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-20_01_Früh` (`465`) | **Branch**: `feature/ATT-8421` (preserved intact & unmerged for Stage 6 Cold Final Review per `REQ-PRO-125`)  
**Verified Production SHA (Gate 4 Re-Review `ATT-9292`)**: `ba662145` (`git diff ba662145..HEAD -- app/` = EMPTY, `Rule 7.5` doc-only Stage 5)  
**Requirement / Test IDs**: `REQ-SEN-034` (`REQ-SEN-034.1`–`.3`) [Verified] ↔ `TST-SEN-034` (`TST-SEN-034.1`–`.3`) [Verified]  
**Author**: AI Agent 1 (`agent1`, Gemini 4 Neon) | **Date**: 2026-11-20

---

## 1. Root Cause & Delivered Fixes (`REQ-SEN-034` / `TST-SEN-034`)

1. **Persisted `baselineCircumference` Loading, Monotonic Generation Token, Main-Thread Confinement & Live Reset Propagation (`REQ-SEN-034.1` / `TST-SEN-034.1`)**:
   * **Root Cause**: `BANALServiceRepository.kt:606` instantiated `IndirectTpmsEstimator()` with default `baselineCircumferenceMeters = 0.0` (never calling `DevicesDatabaseManager.getBaselineCircumference(devId)`), `clearBreadcrumbs()` reset sessions without reloading baselines, and `DeviceDataRepository` had no reactive notification on `resetTirePressureBaseline(id, null)` or baseline-only `updateDevice()` edits.
   * **Fix**:
     - `BANALServiceRepository.kt` (`1,354 / 1,500` LoC): Wired `DevicesDatabaseManager.getBaselineCircumference(devId)` into `cachedBaselineCircumferences` with `pendingBaselineLoads` cold-start deduplication and per-device monotonic `baselineLoadGenerations: ConcurrentHashMap<Long, AtomicLong>` (`nextBaselineLoadGeneration(deviceId)`). Every reload coroutine launches on `repositoryScope` (Main/collector dispatcher), runs strictly `baselineCircumferenceLoader(deviceId)` inside `withContext(itpmsIoDispatcher)`, and executes generation validation + `applyResolvedBaseline()` back on `repositoryScope` (eliminating cross-thread data races on non-`@Volatile` `IndirectTpmsEstimator.baselineCircumferenceMeters` with `0` edits to `IndirectTpmsEstimator.kt`, while discarding stale out-of-order IO completions). Explicitly overwrites live estimators with `0.0` for `<= 0.0`/non-finite baselines while preserving cached values on transient loader `Throwable`. Subscribes to `DeviceDataRepository.baselineCircumferenceChanged`, registers `BANALService.CALIBRATION_FACTOR_CHANGED` strictly via `registerInternalReceiver()` (JVM `SDK_INT == 0` safe, handling specific `DEVICE_ID` + `-1L` fallback), and reloads on `clearBreadcrumbs()` & `READY -> TRACKING`.
     - `DeviceDataRepository.kt` (`769 / 1,500` LoC): Added buffered companion `SharedFlow<Long>` `baselineCircumferenceChanged` (`extraBufferCapacity = 64`, `BufferOverflow.DROP_OLDEST`) emitted on both `resetTirePressureBaseline(deviceId, mm/null)` branches and `updateDevice()` baseline mutations.
   * *Proof (`BANALServiceRepositoryItpmsWiringTest`)*: `BANALServiceRepositoryItpmsWiringTest#testPersistedBaselineLoaded_triggersLowPressureAlertAndDeduplicatesColdStartIo_tstSen034_1`, `BANALServiceRepositoryItpmsWiringTest#testLiveResetSignalsZeroOverwriteAndErrorPreservation_tstSen034_1`, `BANALServiceRepositoryItpmsWiringTest#testDeviceDataRepositoryResetBranches_emitBaselineChangedToBanalServiceRepository_tstSen034_1`, `BANALServiceRepositoryItpmsWiringTest#testOutOfOrderAsyncIoLoad_staleCompletionIsDiscardedAndDoesNotOverwriteNewerBaseline_tstSen034_1`, & `BANALServiceRepositoryItpmsWiringTest#testDeviceDataRepositoryUpdateDevice_emitsBaselineChangedAndUpdatesLiveEstimator_tstSen034_1`.
2. **Strict Null/Non-Finite GNSS Accuracy & Bearing Gating with GPS-Free Dual-Wheel Isolation (`REQ-SEN-034.2` / `TST-SEN-034.2`)**:
   * **Root Cause**: `BANALServiceRepository.kt` defaulted null `SensorType.ACCURACY` to `5.0` (passing `<= 6.0 m`) and null `_currentBearing.value` to `0.0` (yielding `0 deg/s` bearing rate), and nested GPS-free `DualWheelTpmsEstimator` inside the outer `location != null` check.
   * **Fix**: Extracted `@VisibleForTesting internal fun processItpmsTelemetryTick(..., timestampMs: Long = System.currentTimeMillis())`, removed `?: 5.0` and `?: 0.0`, gated Section 1 (`IndirectTpmsEstimator`) strictly on `location != null && accuracyMeters != null && accuracyMeters.isFinite() && accuracyMeters >= 0.0 && bearingDeg != null && bearingDeg.isFinite()`, and kept Section 2 (GPS-free `DualWheelTpmsEstimator`) active under `TrackingMode.TRACKING` even when `location`, `accuracy`, or `bearing` is `null`.
   * *Proof (`BANALServiceRepositoryItpmsWiringTest`)*: `BANALServiceRepositoryItpmsWiringTest#testNullOrNonFiniteGnssAccuracyOrBearing_skipsIndirectTpmsWithoutFabricatingDefaults_tstSen034_2` & `BANALServiceRepositoryItpmsWiringTest#testNullLocationAccuracyBearing_dualWheelTpmsStillDetectsRearPunctureGpsFree_tstSen034_2`.
3. **Baseline Label 9-Locale Parity & Architectural Guardrails (`REQ-SEN-034.3` / `TST-SEN-034.3`)**:
   * Confirmed Defect 3 (`"Baseline: $baselineMm mm"`) was already localized across all 9 active locales (`values`, `de/es/fr/it/ja/nl/pl/pt`) by `ATT-8422` (`00549967`) via `R.string.devices_wheel_baseline_format` in `WheelCircumferenceSection.kt:134` with `0` remaining hardcoded English `"Baseline"` UI labels (`Localization Parity: N/A - already localized by ATT-8422`).
   * *Proof*: `BANALServiceRepositoryItpmsWiringTest#testBaselineCircumferenceChangedFlow_isBufferedAndDeliversWithoutDropping_tstSen034_3`, `TranslationParityTest` (`11/11`), `EditDeviceWheelCircumferenceTest` (`8/8`), and governance contracts.

---

## 2. Independent Verification Evidence (`Rule 7.5` Reuse of Gate 4 `ATT-9292` on `ba662145`)

Zero files under `app/` were modified after Gate 4 verified commit `ba662145` (`git diff ba662145..HEAD -- app/` is empty).

| Verification Suite / Check | Scope | Result |
| :--- | :--- | :--- |
| `BANALServiceRepositoryItpmsWiringTest` (`app/src/test/java/com/atrainingtracker/trainingtracker/repositories/BANALServiceRepositoryItpmsWiringTest.kt`) | `TST-SEN-034.1`, `TST-SEN-034.2`, `TST-SEN-034.3` (baseline IO hydration, out-of-order generation discard, `resetTirePressureBaseline` & `updateDevice` propagation, zero/NaN overwrite, null GNSS skipping, GPS-free dual-wheel puncture detection) | **8/8 PASS** |
| `BANALServiceRepositoryMultiWheelTpmsTest` | `TST-SEN-030.2` multi-wheel estimator & history persistence non-regression | **3/3 PASS** |
| `BANALServiceRepositoryPauseIsolationTest` | Pause isolation non-regression | **1/1 PASS** |
| `BANALServiceRepositoryMemoryTest` | 1-Hz observation loop memory & allocation non-regression | **4/4 PASS** |
| `IndirectTpmsEstimatorTest` | `TST-TRK-050` single-wheel iTPMS thresholds & CUSUM non-regression | **10/10 PASS** |
| `DualWheelTpmsEstimatorTest` | `TST-SEN-030.1` GPS-free dual-wheel ratio CUSUM non-regression | **5/5 PASS** |
| `EditDeviceWheelCircumferenceTest` | `TST-SEN-033` `ATT-8422` localized baseline label & wheel UI non-regression | **8/8 PASS** |
| `TranslationParityTest` | `TST-LOC-001` 9-locale parity (`values`, `de/es/fr/it/ja/nl/pl/pt`) | **11/11 PASS** |
| `ArchitectureConformanceContractTest` | LoC caps (`BANALServiceRepository.kt = 1,354 / 1,500`, `DeviceDataRepository.kt = 769 / 1,500`), `0` monolith edits | **11/11 PASS** |
| `DeadCodeEliminationContractTest` | Zero dead helpers/methods | **6/6 PASS** |
| `SourceTextReadingAuditContractTest` | `REQ-PRO-049.1` (`0` new `src/main` reads) | **6/6 PASS** |
| `SystemLiveTrackingRequirementsContractTest` | `SYSREQ-TRK-004`/`010` traceability parity | **4/4 PASS** |
| **Total Unit & Contract Suites (`--rerun`, Gate 4 `ATT-9292` @ `ba662145`)** | **12 suites + `:app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:assembleDebug` (`0` new Kotlin warnings)** | **77/77 PASS** (`0` fail, `0` err, `0` skip) |
| **Python Safety Linters (4/4)** | `lint_safe_enum.py`, `lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `check_secrets_hygiene.py` | **4/4 PASS (`exit 0`)** |
| **Governance & Clean-Checkout Evidence** | `verify_requirement_governance.py --base-ref` & `--check-verified-evidence` (empty test dir) | **PASS (`exit 0`)** |

---

## 3. Architecture, LoC Budget & Branch Preservation
* `BANALServiceRepository.kt`: **`1,354 / 1,500` LoC** | `DeviceDataRepository.kt`: **`769 / 1,500` LoC** | `BANALServiceRepositoryItpmsWiringTest.kt`: **`635` LoC**.
* Protected Monoliths & Sibling Files: **`0` edits** (`DevicesDatabaseManager.java`, `DeviceManager.java`, `TrackerService.java`, `EditDeviceDialog.kt`, `WheelCircumferenceSection.kt`, `WheelCircumferenceHistoryChart.kt`, `IndirectTpmsEstimator.kt`).
* SQLite schema migrations: `0` | Localization Parity: `N/A - already localized by ATT-8422` (`11/11` `TranslationParityTest` PASS).
* **Branch Preservation (`REQ-PRO-125`)**: `feature/ATT-8421` preserved intact and unmerged for Stage 6 Cold Final Review (`--merge-ticket` deferred strictly post-Stage 6 PASS).
