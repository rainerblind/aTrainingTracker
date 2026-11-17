# Stage 5: Walkthrough & Verification - ATT-8156: Integration Verification for INT-SWE-05 to INT-SWE-08

**Ticket**: [ATT-8156](https://rainerblind.atlassian.net/browse/ATT-8156) | **Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) | **Release**: `V5.0.0` | **SP**: 3 SP
**Requirement Mapping**: `REQ-PRO-111` (`REQ-PRO-111.1..5`) | **Test Mapping**: `TST-PRO-111`, `TST-INT-013..016`
**Branch**: `feature/ATT-8156` (rebased onto `b81888c8`) | **Author**: AI Agent 1 | **Date**: 2026-11-17

---

## 1. Executive Summary & Verification Overview
Completes ASPICE 4.0 Assessment (2026-11-15) Finding `M6` (SWE.5 BP1–BP7) remediation by implementing 4 Level 1 Robolectric JVM integration suites for `INT-SWE-05..08` (`IF-SWE-05..08`) and synchronizing `docs/architecture/integration_strategy.md`:
1. **`INT-SWE-05` (`IF-SWE-05` / `TST-INT-013`)**: `DomainCalculatorsIntegrationTest.kt` verifies `AutoPauseManager`, `PowerPhysiologyCalculator` (CP/W', NP, W'bal), `PowerHrDynamicsCalculator` (cardiac drift), `WorkoutClusterEngine`, and `ClimbDetector` across synthetic time-series and elevation profiles.
2. **`INT-SWE-06` (`IF-SWE-06` / `TST-INT-014`)**: `PersistenceSqliteIntegrationTest.kt` verifies real SQLite `WorkoutSummariesDatabaseManager`, `WorkoutSamplesDatabaseManager.createNewTable()` + 20 concurrent `Dispatchers.IO` inserts (`RealSqliteTestHelper`), `LapsDatabaseManager`, `KnownLocationsRepository.recordWorkoutStart()` (`dbDispatcher`), and `KnownLocationsDbHelper.onUpgrade` (`v1 -> v6`).
3. **`INT-SWE-07` (`IF-SWE-07` / `TST-INT-015`)**: `FileExportersIntegrationTest.kt` seeds real SQLite workouts/samples/laps and runs `FitFileWriter` (Garmin `Decode`), `GPXFileWriter` & `TCXFileWriter` (XML DOM + zero `.tmp` leaks), and `ExportManager` -> `ExportStatusDatabaseManager`.
4. **`INT-SWE-08` (`IF-SWE-08` / `TST-INT-016`)**: `CloudHealthConnectIntegrationTest.kt` tests `StravaUploader` over an `OkHttpClient` `Interceptor` double (bearer-token multipart upload, polling, PUT activity update, `ExportStatusRepository` SQLite persistence, HTTP 503 retry classification), `DropboxUploader` missing-credential guard, `HealthConnectManager.syncWorkout()` -> `ExportStatusDatabaseManager`, and `HealthConnectHrvImporter` -> `HrvReadinessRepository`.
5. **Strategy Sync & Contract Lock (`REQ-PRO-111.5` / `TST-PRO-111`)**: `docs/architecture/integration_strategy.md` updated to `6,540` tests / `1,119` suites / `29m 21s` Tier-2 (`2,635.73s` sum) from `sprint_455.md` & `ATT-8500`, `INT-SWE-01..08` Level 1 coverage, and `emulator-5554` (`Pixel_8_Android_15`) policy; locked via `AspiceSweAuditContractTest.kt`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Measured Result | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-111.1` | `TST-INT-013` | `DomainCalculatorsIntegrationTest.kt` (`AutoPause`, `PowerPhysiology`, `PowerHr`, `Cluster`, `Climb`) | **3/3 PASSED** | `Verified` |
| `REQ-PRO-111.2` | `TST-INT-014` | `PersistenceSqliteIntegrationTest.kt` (Real SQLite, WAL, `KnownLocationsRepo`, `onUpgrade` v1->v6) | **3/3 PASSED** | `Verified` |
| `REQ-PRO-111.3` | `TST-INT-015` | `FileExportersIntegrationTest.kt` (`FitFileWriter`, `GPXFileWriter`, `TCXFileWriter`, `ExportManager`) | **3/3 PASSED** | `Verified` |
| `REQ-PRO-111.4` | `TST-INT-016` | `CloudHealthConnectIntegrationTest.kt` (`StravaUploader`, `DropboxUploader`, `HealthConnectManager/Hrv`) | **3/3 PASSED** | `Verified` |
| `REQ-PRO-111.5` | `TST-PRO-111` | `AspiceSweAuditContractTest.kt` (`INT-SWE-05..08` + `integration_strategy.md` parity) | **11/11 PASSED** | `Verified` |

---

## 3. Automated Test Evidence (`43/43 PASSED` on Rebased `feature/ATT-8156`)
```bash
python3 tools/parallel_sprint_lock.py ./gradlew :app:testDebugUnitTest \
  --tests "com.atrainingtracker.trainingtracker.integration.*" \
  --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest" \
  :app:compileDebugAndroidTestKotlin --max-workers=2
# BUILD SUCCESSFUL in 2m 36s — 43/43 tests PASSED (0 failures, 0 errors, 0 skipped):
# - DomainCalculatorsIntegrationTest:      3/3 PASSED (INT-SWE-05 / TST-INT-013)
# - PersistenceSqliteIntegrationTest:      3/3 PASSED (INT-SWE-06 / TST-INT-014)
# - FileExportersIntegrationTest:          3/3 PASSED (INT-SWE-07 / TST-INT-015)
# - CloudHealthConnectIntegrationTest:     3/3 PASSED (INT-SWE-08 / TST-INT-016)
# - UiPresentationIntegrationTest:         4/4 PASSED (INT-SWE-01 / TST-INT-009)
# - ViewModelRepositoryIntegrationTest:    3/3 PASSED (INT-SWE-02 / TST-INT-010)
# - InterServiceIngestionIntegrationTest:  3/3 PASSED (INT-SWE-03 / TST-INT-011)
# - HardwareHalIngestionIntegrationTest:   5/5 PASSED (INT-SWE-04 / TST-INT-012)
# - TrackerServiceIntegrationTest:         5/5 PASSED (REQ-TRK-075)
# - AspiceSweAuditContractTest:           11/11 PASSED (TST-PRO-078, TST-PRO-093, TST-PRO-110, TST-PRO-111)
# - :app:compileDebugAndroidTestKotlin:    PASS
```
*Tier 1 targeted scope explicitly bounded to `com.atrainingtracker.trainingtracker.integration.*` + `AspiceSweAuditContractTest` + `:app:compileDebugAndroidTestKotlin`; full Tier 2 suite deferred to sprint gate.*

---

## 4. Hardware / Device Verification Policy
Non-UI test-suite-only & architecture-doc sync ticket (`app/src/main/` untouched); per `stage5-verification` skill, APK deployment is skipped and verification rests on Level 1 Robolectric JVM integration suites + `:app:compileDebugAndroidTestKotlin`.

---

## 5. Invariant & Governance Verification
1. **Living Documentation**: `REQ-PRO-111.1..5`, `TST-INT-013..016`, and `TST-PRO-111` promoted to `[Verified]`; master index tables synced (`Total=1204/1211`, 100% parity).
2. **Governance Check**: `python3 tools/verify_requirement_governance.py` and `--text-file` checks across `ATT-8156_{analysis,test_spec,plan,walkthrough}.md` exited `0` (0 ungrounded symbols, 0 orphans).
3. **Submodule & Production Integrity**: `app/src/main/` unmodified (0 files); `ANT-Android-SDKs` intact at `0e8cea714a231fb9565170d4ed01eafa868f9798`.
4. **Sprint Branch Boundary**: `refs/heads/sprint/2026-11-17_01_Früh` untouched pending Gate 5 squash-merge.
