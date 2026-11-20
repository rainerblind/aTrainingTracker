# Stage 1: Problem Domain & Root Cause Analysis - ATT-8421: iTPMS Persisted Baseline Wiring, Null GNSS Accuracy/Bearing Gating & Baseline Label Audit

**Ticket**: `ATT-8421` (`2 SP`, `Bug`, `Priority: Medium`, `severity-major`) | **Parent Epic**: `ATT-166`  
**Active Sprint**: `2026-11-20_01_Früh` (`465`) | **Branch**: `feature/ATT-8421` (base `00549967`) | **Author**: AI Agent 1 (`agent1`, Gemini 4 Neon) | **Date**: 2026-11-20

---

## 1. Problem Domain, Root Cause Analysis & Codebase Audit
* **Defect 1 — Persisted `baselineCircumference` Never Loaded (`BANALServiceRepository.kt:606, 727`, `1,186 / 1,500` LoC)**:
  * `perDeviceItpmsEstimators.getOrPut(devId) { IndirectTpmsEstimator() }` instantiates every per-device estimator with default `baselineCircumferenceMeters = 0.0`.
  * `DevicesDatabaseManager.getBaselineCircumference(long deviceID)` (`DevicesDatabaseManager.java:646-655`, `1,669 / 1,671` LoC — note line shifted from `632` in ticket) has **zero** callers in `app/src/main/`.
  * Because `IndirectTpmsEstimator.onGatedStep()` (`IndirectTpmsEstimator.kt:224`, `283 / 1,500` LoC) guards `if (baselineCircumferenceMeters > 0.0 && !lowPressureAlertFired)`, `TpmsAlertResult.LowPressure` (`REQ-TRK-050.2`, `REQ-SEN-030.2`) is dead at runtime even after saving/resetting the baseline in `EditDeviceDialog`.
  * `clearBreadcrumbs()` (`:727`) calls `it.resetSession()` on cached instances without refreshing `baselineCircumferenceMeters`, and `BANALServiceRepository` never observes `BANALService.CALIBRATION_FACTOR_CHANGED` or `DeviceDataRepository` baseline updates.
* **Defect 2 — Null GNSS Accuracy/Bearing Fallbacks Bypass Gating (`BANALServiceRepository.kt:596, 610`)**:
  * Line `596` defaults null `SensorType.ACCURACY` to `5.0`, silently passing `MAX_GNSS_ACCURACY_METERS (6.0)` (`IndirectTpmsEstimator.kt:172`).
  * Line `610` defaults null `_currentBearing.value` to `0.0`, producing `0.0 deg/s` bearing rate across consecutive ticks and bypassing straight-line gating (`MAX_BEARING_RATE_DEG_PER_SEC = 3.0`, `:179`).
* **Defect 3 — Baseline Label Localization Audit (`WheelCircumferenceSection.kt:134`, `169` LoC; `WheelCircumferenceHistoryChart.kt`, `299` LoC; `EditEquipmentDialog.kt`, `320` LoC)**:
  * Preceding commit `00549967` (`ATT-8422`) already extracted `WheelCircumferenceSection.kt` from `EditDeviceDialog.kt` (`1,774 / 2,004` LoC) and replaced hardcoded `"Baseline: $baselineMm mm"` with `stringResource(R.string.devices_wheel_baseline_format, baselineMm)` across all 9 active locales (`values`, `de/es/fr/it/ja/nl/pl/pt`).
  * Live grep confirms `0` remaining hardcoded English `"Baseline"` UI labels in `EditDeviceDialog.kt`, `WheelCircumferenceSection.kt`, `WheelCircumferenceHistoryChart.kt`, or `EditEquipmentDialog.kt`.

---

## 2. Independent Peer Review & Verification (4-Augen-Prinzip — Rules 69 & 70)
1. **Defect 3 Already Satisfied by `ATT-8422` (`00549967`)**: Verified `devices_wheel_baseline_format` exists symmetrically in all 9 `strings_devices.xml` files and all `WheelCircumferenceHistoryChart` strings use `stringResource(...)`. Zero XML edits are needed in `ATT-8421`; Stage 2/4 will retain regression checks via `TranslationParityTest` & `EditDeviceWheelCircumferenceTest`.
2. **Main-Thread SQLite Avoidance & All Baseline-Reset Paths (`BANALServiceRepository.kt:125, 262-267, 366-370, 727`; `DeviceDataRepository.kt:423-429, 527-547`, `738 / 1,500` LoC)**:
   * `startObservingBANALService()` executes on `Dispatchers.Main` (`:125, 461`). Querying SQLite inline per 1-Hz tick risks main-thread jank. Mirroring `refreshWheelPositionsCache()` (`:736-786`), per-device baselines should be cached in `ConcurrentHashMap<Long, Double>` loaded on `Dispatchers.IO` (plus an injectable loader for deterministic JVM unit tests), applying loaded values to both new and live cached `IndirectTpmsEstimator` instances (`baselineCircumferenceMeters` is evaluated only after `2.0 km` lock).
   * Audit of `DeviceDataRepository.kt` uncovered two silent reset paths without broadcasts: `resetTirePressureBaseline(deviceId, null)` (`:543`) and `updateDevice()` (`:424`) when only `baselineCircumference` changes. Emitting a reactive `DeviceDataRepository.baselineCircumferenceChanged` `SharedFlow` (matching `EquipmentRepository.equipmentLinksChanged` at `:367`), observing `BANALService.CALIBRATION_FACTOR_CHANGED`, and reloading baselines in `clearBreadcrumbs()` / `READY -> TRACKING` closes every live & workout-start update path.
3. **Preserving GPS-Free `DualWheelTpmsEstimator` (`BANALServiceRepository.kt:596-705`)**:
   * Skipping on missing/invalid GNSS quality (`acc == null || !acc.isFinite() || acc < 0.0 || bearing == null || !bearing.isFinite()`) MUST guard **only** Section 1 (GNSS `IndirectTpmsEstimator`, `:600-645`) and MUST NOT skip Section 2 (`DualWheelTpmsEstimator`, `:648-705`, `REQ-SEN-030.1`), which is GPS-free.
   * Verified `speedMps = currentSpeed ?: 0.0` (`:612`) is already safely rejected by `MIN_SPEED_MPS (4.0)` (`IndirectTpmsEstimator.kt:172`) and does not bypass gating.
4. **Deterministic Testability & Zero Monolith Growth**: Extracting `@VisibleForTesting` iTPMS tick/baseline helpers in `BANALServiceRepository.kt` (`1,186 -> ~1,270 / 1,500` LoC) enables pure behavioral JVM unit tests with `0` `src/main` file-text reads (`REQ-PRO-049.1`) and `0` edits to protected monoliths (`DevicesDatabaseManager.java`, `EditDeviceDialog.kt`, `DeviceManager.java`, `TrackerService.java`).

---

## 3. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
1. **Original Requirement ID & Target**: Amends/hardens `REQ-TRK-050` (`REQ-TRK-050.1`, `.2`, `.4`) in `docs/requirements/req_tracking_cockpit_part1.md` and `REQ-SEN-030.2` in `docs/requirements/req_tracking_cockpit_part2.md` (with `TST-TRK-050` / `TST-SEN-030.2` in `docs/tests/tst_tracking_cockpit.md`), targeting `BANALServiceRepository.kt` and `DeviceDataRepository.kt`.
2. **Historical Origin & Commit Trace**: `e7cc0f39` (`ATT-4880` single-wheel iTPMS & `Devices.db` v8 `baselineCircumference`), `a5665f9a` (`ATT-7753` per-device map & `DualWheelTpmsEstimator`), `f55c9fee` (`ATT-8206` living-doc shards), `00549967` (`ATT-8422` localized `devices_wheel_baseline_format` & `WheelCircumferenceSection.kt`).
3. **Root Reason for Existing Formulation**: `ATT-4880` unit-tested `IndirectTpmsEstimator` directly with a constructor baseline and implemented `DevicesDatabaseManager.getBaselineCircumference()`, while `BANALServiceRepository` kept default `IndirectTpmsEstimator()` and defensive `?: 5.0` / `?: 0.0` fallbacks inside its untested 1-Hz coroutine loop.
4. **Preservation of Core Invariants**:
   * `IndirectTpmsEstimator` gating thresholds (`<= 6.0 m`, `< 3.0 deg/s`, `>= 4.0 m/s`, `2.0 km` lock, `-0.6%` low-pressure, `-1.0%` slow-puncture CUSUM) remain unchanged.
   * GPS-free `DualWheelTpmsEstimator` (`REQ-SEN-030.1`) continues running when GNSS accuracy/bearing is null.
   * `persistConvergedCircumferences()`, `clearBreadcrumbs()` session resets, `Devices.db` v8 schema, and `CALIBRATION_FACTOR` / raw wheel accumulators remain invariant.
   * `0` edits to protected monoliths (`DeviceManager.java`, `TrackerService.java`, `DevicesDatabaseManager.java`, `EditDeviceDialog.kt`); all modified production files remain well below `1,500` LoC (`ArchitectureConformanceContractTest`); `0` `src/main` reads in new tests (`REQ-PRO-049.1`).

---

## 4. Scope Bounding & Complexity Assessment (`ATT-1250`, `REQ-PRO-029`)
* **In Scope (`2 SP` — Standard, no split required)**: Load persisted `DevicesDatabaseManager.getBaselineCircumference(devId)` into `BANALServiceRepository` per-device `IndirectTpmsEstimator` instances; propagate live baseline resets from `DeviceDataRepository` + reload on `clearBreadcrumbs()`/`READY -> TRACKING`; skip GNSS `IndirectTpmsEstimator` telemetry when accuracy or bearing is null/non-finite while keeping `DualWheelTpmsEstimator` active; verify 9-locale Baseline label parity from `ATT-8422`; add behavioral unit tests covering baseline loading, live reset/reload, `LowPressure` alert emission, and null-GNSS skipping.
* **Out of Scope**: `IndirectTpmsEstimator` algorithm/threshold changes, `Devices.db`/`Equipment.db` schema changes, `EditDeviceDialog`/`WheelCircumferenceSection` UI redesign (`ATT-8422`), `EquipmentViewModel` cross-bike baseline architecture, `TrackerService`/`DeviceManager` modifications.

---

## Change-Request Impact Analysis
* **Affected Requirements**: Refines `REQ-TRK-050` (`REQ-TRK-050.1`, `.2`, `.4`) and `REQ-SEN-030.2` (plus `TST-TRK-050` / `TST-SEN-030.2`) with runtime repository baseline loading, reset propagation, and strict null-GNSS skipping; verifies `REQ-SEN-033.3` / `REQ-LOC-001` 9-locale parity already landed in `ATT-8422` (`0` new XML strings needed). **SYS.2/SYS.3 Impact (`REQ-PRO-131.4`)**: Zero new hardware sensors, BLE/ANT+ profiles, HAL/FGS surfaces, or external APIs; existing links under `SYSREQ-TRK-004` & `SYSREQ-TRK-010` (`docs/system/sysreq_live_tracking.md`, `docs/system/system_software_traceability.md`) and `IF-SWE-03` (`docs/architecture/interfaces.md`) remain accurate without structural changes.
* **Affected Architecture Elements**: `BANALServiceRepository.kt` (`1,186 -> ~1,270 / 1,500` LoC, `trainingtracker/repositories/` — allowed `DevicesDatabaseManager` access outside UI/domain), `DeviceDataRepository.kt` (`738 -> ~755 / 1,500` LoC, reactive baseline-changed signal). Zero edits to `IndirectTpmsEstimator.kt` (`283` LoC), `WheelCircumferenceSection.kt` (`169` LoC), `WheelCircumferenceHistoryChart.kt` (`299` LoC), `EditDeviceDialog.kt` (`1,774` LoC), `DevicesDatabaseManager.java` (`1,669` LoC), `DeviceManager.java`, or `TrackerService.java`.
* **Verification Impact & Test Surface**: New behavioral JVM unit tests in `BANALServiceRepositoryItpmsWiringTest.kt` (zero `src/main` reads, zero reflection hacks) verifying persisted baseline loading -> `TpmsAlertResult.LowPressure` firing, live baseline reset propagation & `clearBreadcrumbs()` reload, and null/non-finite GNSS accuracy or bearing skipping without disrupting `DualWheelTpmsEstimator`; regression runs on `BANALServiceRepositoryMultiWheelTpmsTest`, `IndirectTpmsEstimatorTest`, `EditDeviceWheelCircumferenceTest`, `TranslationParityTest`, `ArchitectureConformanceContractTest`, `DeadCodeEliminationContractTest`, and `SourceTextReadingAuditContractTest`.
* **Risk Assessment & Invariants**: **LOW** (`2 SP`, tightly scoped repository wiring & null guard; zero schema, UI, or monolith changes). Mitigated risks: (1) main-thread SQLite avoidance via `Dispatchers.IO` baseline cache + live estimator update, (2) silent reset paths in `DeviceDataRepository` (`resetTirePressureBaseline(id, null)` & `updateDevice`) closed via reactive `SharedFlow` + `clearBreadcrumbs()` reload, (3) GPS-free `DualWheelTpmsEstimator` isolated from GNSS accuracy/bearing skip guard, (4) `BANALServiceRepository.kt` kept well below `1,500` LoC cap.
