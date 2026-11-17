# Stage 3 Implementation Plan - ATT-8156: Integration Verification for INT-SWE-05 to INT-SWE-08

**Ticket**: [ATT-8156](https://rainerblind.atlassian.net/browse/ATT-8156) | **Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)
**Release**: `V5.0.0` | **Sprint**: `2026-11-17_01_Früh` | **SP**: 3 SP
**Mapping**: `REQ-PRO-111` (`REQ-PRO-111.1..5`) | `TST-PRO-111` (`TST-PRO-111.1..3`), `TST-INT-013..016`
**Branch**: `feature/ATT-8156` | **Author**: AI Agent 1 | **Date**: 2026-11-17

---

## 1. Architectural Seam Decomposition (SWE.2)
Completes Level 1 Robolectric JVM integration coverage for the remaining four bottom-up architectural boundaries (`IF-SWE-05..08`), adhering to healed `ATT-8155` authenticity standards (real SQLite/domain/writer components; zero mock-returning-mock or primitive constant stubs):

| Step ID | Boundary | Interface | Real Components Under Test | Test File | Test ID |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `INT-SWE-05` | Domain Calculators | `IF-SWE-05` | `AutoPauseManager`, `PowerPhysiologyCalculator`, `PowerHrDynamicsCalculator`, `WorkoutClusterEngine`, `ClimbDetector` | `DomainCalculatorsIntegrationTest.kt` | `TST-INT-013` |
| `INT-SWE-06` | SQLite Persistence | `IF-SWE-06` | `WorkoutSamplesDatabaseManager`, `WorkoutSummariesDatabaseManager`, `KnownLocationsDatabaseManager`, `LapsDatabaseManager`, `KnownLocationsRepository` | `PersistenceSqliteIntegrationTest.kt` | `TST-INT-014` |
| `INT-SWE-07` | File Exporters | `IF-SWE-07` | `ExportManager`, `WorkoutRepository`, `FitFileWriter`, `GPXFileWriter`, `TCXFileWriter`, `ExportStatusDatabaseManager` | `FileExportersIntegrationTest.kt` | `TST-INT-015` |
| `INT-SWE-08` | Cloud & Health | `IF-SWE-08` | `StravaUploader` (OkHttp `Interceptor` double), `DropboxUploader`, `HealthConnectManager`, `HealthConnectHrvImporter`, `HrvReadinessRepository`, `ExportStatusDatabaseManager` | `CloudHealthConnectIntegrationTest.kt` | `TST-INT-016` |

---

## 2. Atomic Implementation Steps (SWE.3)

* **Step 1 (`INT-SWE-05` / `TST-INT-013` / `DomainCalculatorsIntegrationTest.kt`)**:
  - Author Robolectric (`sdk = [34]`) test chaining synthetic speed/power/HR/GPS-elevation time-series through real `AutoPauseManager.evaluateTick()`, `PowerPhysiologyCalculator.calculateNormalizedPower()`, `PowerHrDynamicsCalculator.calculate()`, `WorkoutClusterEngine.findApexFromPoints()` + `suggestCluster()`, and `ClimbDetector.detectClimbs()`.
* **Step 2 (`INT-SWE-06` / `TST-INT-014` / `PersistenceSqliteIntegrationTest.kt`)**:
  - Author Robolectric (`sdk = [34]`) test backed by real SQLite (`RealSqliteTestHelper`) verifying `WorkoutSamplesDatabaseManager.createNewTable()` + 1-Hz sample writes, `WorkoutSummariesDatabaseManager` updates, `LapsDatabaseManager` lap persistence, `KnownLocationsRepository.recordWorkoutStart()` (`dbDispatcher`), and non-destructive `onUpgrade` schema parity.
* **Step 3 (`INT-SWE-07` / `TST-INT-015` / `FileExportersIntegrationTest.kt`)**:
  - Seed real SQLite workout/sample/lap rows and run real `FitFileWriter`, `GPXFileWriter`, `TCXFileWriter` via `ExportManager` into temp storage; assert valid FIT header / GPX & TCX XML structure, zero leftover `.tmp` files, and `ExportStatusDatabaseManager` status updates.
* **Step 4 (`INT-SWE-08` / `TST-INT-016` / `CloudHealthConnectIntegrationTest.kt`)**:
  - Wire real `StravaUploader` (with `OkHttpClient` `Interceptor` transport double) and `DropboxUploader` to `ExportStatusDatabaseManager`, verifying OAuth refresh/retry and upload status transitions; wire `HealthConnectManager` & `HealthConnectHrvImporter` (fake `HealthConnectClient`) to real `ExportStatusDatabaseManager` & `HrvReadinessRepository`.
* **Step 5 (`REQ-PRO-111.5` / `docs/architecture/integration_strategy.md` Sync)**:
  - Update `docs/architecture/integration_strategy.md`: replace stale `3,660+` / `~2–3 minutes` with authentic `sprint_455.md` / `ATT-8500` metrics (`6,540` tests, `1,119` suites, `29m 21s` Tier-2 wall-clock / `2,635.73s` cumulative sum), expand Level 1 scope to `INT-SWE-01..08`, and align local `emulator-5554` (`Pixel_8_Android_15`) policy with `stage5-verification/SKILL.md` (keeping every backticked filename 100% repo-grounded).
* **Step 6 (`TST-PRO-111` / `AspiceSweAuditContractTest.kt` + Living Docs)**:
  - Extend `AspiceSweAuditContractTest.kt` to lock `INT-SWE-05..08` files (`RobolectricTestRunner`, `sdk = [34]`, `TST-INT-013..016`) and `integration_strategy.md` metric/emulator parity; promote `REQ-PRO-111`, `TST-INT-013..016`, `TST-PRO-111` to `[Verified]` in Stage 5 after passing runs.

---

## 3. Invariant Protection & Code Lock (Core Rule 4)
* **Code Lock**: Zero edits to `app/src/main/` or `app/src/test/` during Stage 3 planning.
* **Production & Submodule Safety**: `app/src/main/` remains unmodified in Stage 4; `ANT-Android-SDKs` stays untouched at `0e8cea714a231fb9565170d4ed01eafa868f9798`.
* **No Mocked-Away Seams**: Every test interacts across real component boundaries (real SQLite, real file I/O, real domain models, real `OkHttpClient` pipeline).

---

## 4. Verification Commands (Stage 4 / Stage 5)
```bash
python3 tools/parallel_sprint_lock.py ./gradlew :app:testDebugUnitTest \
  --tests "com.atrainingtracker.trainingtracker.integration.*" \
  --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest" \
  :app:compileDebugAndroidTestKotlin --max-workers=2
python3 tools/verify_requirement_governance.py
```
