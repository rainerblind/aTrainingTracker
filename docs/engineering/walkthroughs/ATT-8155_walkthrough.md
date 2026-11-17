# Stage 5: Walkthrough & Verification - ATT-8155: Integration Verification Test Cases for INT-SWE-01 to INT-SWE-04

**Ticket**: [ATT-8155](https://rainerblind.atlassian.net/browse/ATT-8155) | **Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) | **Release**: `V5.0.0` | **SP**: 3 SP
**Requirement Mapping**: `REQ-PRO-110` (`REQ-PRO-110.1..4`) | **Test Mapping**: `TST-PRO-110`, `TST-INT-009..012`
**Branch**: `feature/ATT-8155-heal` | **Verified Code Commit**: `12c80390` | **Author**: AI Agent 1 | **Date**: 2026-11-17

---

## 1. Executive Summary & Bounce 1/3 Remediation
Resolves ASPICE 4.0 Assessment (2026-11-15) Finding `M6` (SWE.5 BP1–BP7) and Cold Stage 6 Bounce 1/3 defects with genuine Level 1 Robolectric JVM integration suites across `INT-SWE-01..04`:
1. **`INT-SWE-01` (`IF-SWE-01` / `TST-INT-009`)**: `UiPresentationIntegrationTest.kt` drives `ControlTrackingButton` via real `ControlTrackingViewModel` (`onStartTracking`, `onPauseTracking`, `onResumeTracking`, `onStopTracking`) and `TrackingTabsViewModel`, asserting Compose recomposition via `waitForIdle()`, `assertTextEquals()`, and broadcast intents.
2. **`INT-SWE-02` (`IF-SWE-02` / `TST-INT-010`)**: `ViewModelRepositoryIntegrationTest.kt` backs `WorkoutRepository` and `WorkoutSummariesViewModel` with real Robolectric SQLite (`WorkoutSummariesDatabaseManager` + `RealSqliteTestHelper`), verifying reactive Flow updates on insert and update.
3. **`INT-SWE-03` (`IF-SWE-03` / `TST-INT-011`)**: `InterServiceIngestionIntegrationTest.kt` wires real `BANALService` + `MySensorManager` + `BANALServiceComm` local binder to `TrackerService.sampleAndWriteToDb()`, verifying `LiveWorkoutSession` stats, SQLite sample/summary persistence, unbind null-safety, and sensor priority arbitration (`BIKE_SPEED > GPS`).
4. **`INT-SWE-04` (`IF-SWE-04` / `TST-INT-012`)**: `HardwareHalIngestionIntegrationTest.kt` exercises `BTLEHeartRateDevice` (`0x2A37`), `BTLEBikePowerDevice` (`0x2A63`), `BTLERadarDevice` V1+V2 (`parseV2ThreatPacket` -> `RadarStateRepository`), `SpeedAndLocationDevice_GPS.onNewLocation()` -> `MySensorManager`, and real `BTLERadarDevice.WATCHDOG_TIMEOUT_MS` (`3500ms`) + `watchdogRunnableForTesting` -> `DISCONNECTED_ERROR`.
5. **Contract Lock (`TST-PRO-110`)**: `AspiceSweAuditContractTest.kt` locks all 4 integration test files and `TST-INT-009..012` governance mappings.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Measured Result | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-110.1` | `TST-INT-009` | `UiPresentationIntegrationTest` (Compose + ViewModels) | **3/3 PASSED** | `Verified` |
| `REQ-PRO-110.2` | `TST-INT-010` | `ViewModelRepositoryIntegrationTest` (Real SQLite + ViewModel) | **3/3 PASSED** | `Verified` |
| `REQ-PRO-110.3` | `TST-INT-011` | `InterServiceIngestionIntegrationTest` (`TrackerService` <-> `BANALServiceComm`) | **3/3 PASSED** | `Verified` |
| `REQ-PRO-110.4` | `TST-INT-012` | `HardwareHalIngestionIntegrationTest` (HR, Power, Radar V1/V2, GPS, Watchdog) | **5/5 PASSED** | `Verified` |
| `REQ-PRO-110` | `TST-PRO-110` | `AspiceSweAuditContractTest` (SWE.1/2/5 Contract Lock) | **10/10 PASSED** | `Verified` |

---

## 3. Automated Test Evidence (`29/29 PASSED` on Commit `12c80390`)
```bash
python3 tools/parallel_sprint_lock.py ./gradlew :app:testDebugUnitTest \
  --tests "com.atrainingtracker.trainingtracker.integration.*" \
  --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest" \
  :app:compileDebugAndroidTestKotlin --max-workers=2
# BUILD SUCCESSFUL in 1m 33s — 29/29 tests PASSED (0 failures, 0 errors, 0 skipped):
# - UiPresentationIntegrationTest:          3/3 PASSED (INT-SWE-01 / TST-INT-009)
# - ViewModelRepositoryIntegrationTest:     3/3 PASSED (INT-SWE-02 / TST-INT-010)
# - InterServiceIngestionIntegrationTest:   3/3 PASSED (INT-SWE-03 / TST-INT-011)
# - HardwareHalIngestionIntegrationTest:    5/5 PASSED (INT-SWE-04 / TST-INT-012)
# - TrackerServiceIntegrationTest:          5/5 PASSED (REQ-TRK-075)
# - AspiceSweAuditContractTest:            10/10 PASSED (TST-PRO-078, TST-PRO-093, TST-PRO-110)
# - :app:compileDebugAndroidTestKotlin:     PASS (UP-TO-DATE)
```
*Tier 1 targeted scope explicitly bounded to `com.atrainingtracker.trainingtracker.integration.*` + `AspiceSweAuditContractTest` + `:app:compileDebugAndroidTestKotlin`; full Tier 2 suite deferred to sprint gate.*

---

## 4. Hardware / Device Verification Policy
Non-UI test-suite-only ticket (`app/src/main/` untouched); per `stage5-verification` skill, APK deployment is skipped and verification rests on Level 1 Robolectric JVM integration suites + `:app:compileDebugAndroidTestKotlin`.

---

## 5. Invariant & Governance Verification
1. **Living Documentation**: `REQ-PRO-110.1..4`, `TST-INT-009..012`, and `TST-PRO-110` remain `[Verified]`, matching exact test assertions on `12c80390`.
2. **Governance Check**: `python3 tools/verify_requirement_governance.py` exited `0` (100% master-to-shard parity across 9 domains, 0 ungrounded requirements, 0 orphans across 2,380 scanned files).
3. **Submodule & Production Integrity**: `app/src/main/` unmodified (0 files); `ANT-Android-SDKs` intact at `0e8cea714a231fb9565170d4ed01eafa868f9798`.
4. **Sprint Branch Boundary**: `refs/heads/sprint/2026-11-17_01_Früh` untouched pending Gate 5 squash-merge.
