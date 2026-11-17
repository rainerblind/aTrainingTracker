# Stage 2: Requirement & Test Specification - ATT-8156: Integration Verification Test Cases for INT-SWE-05 to INT-SWE-08

**Ticket**: [ATT-8156](https://rainerblind.atlassian.net/browse/ATT-8156)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-17_01_Früh` | **SP**: 3 SP  
**Requirement Mapping**: `REQ-PRO-111` (`REQ-PRO-111.1..5`)  
**Test Spec Mapping**: `TST-PRO-111` (`TST-PRO-111.1..4`), `TST-INT-013..016`  
**Branch**: `feature/ATT-8156` | **Author**: AI Agent 1 | **Date**: 2026-11-17

---

## 1. Requirement Specification (`REQ-PRO-111`)
* **Attributes**: Priority: `High` | Type: `Functional` | Verification Method: `Integration / Review`
* **REQ-PRO-111.1 (`INT-SWE-05` Domain Calculators & Algorithms — [Verified])**: Real pure-JVM calculators (`AutoPauseManager`, `PowerPhysiologyCalculator`, `PowerHrDynamicsCalculator`, `WorkoutClusterEngine`, `ClimbDetector`) SHALL have integration verification test coverage driven by synthetic multi-sensor time-series and elevation profiles without primitive constant arithmetic stubs.
* **REQ-PRO-111.2 (`INT-SWE-06` SQLite Persistence Foundation — [Verified])**: Real SQLite database managers (`WorkoutSamplesDatabaseManager`, `WorkoutSummariesDatabaseManager`, `KnownLocationsDatabaseManager`, `LapsDatabaseManager`) and `KnownLocationsRepository` SHALL have Robolectric integration test coverage asserting WAL concurrency, dynamic sample table creation (`createNewTable`), 1-Hz bulk inserts, and non-destructive `onUpgrade` schema parity (`RealSqliteTestHelper`).
* **REQ-PRO-111.3 (`INT-SWE-07` Repository/ExportManager to File Exporters — [Verified])**: Real streaming writers (`FitFileWriter`, `GPXFileWriter`, `TCXFileWriter`) driven from real SQLite workout/sample/lap records via `ExportManager` / `WorkoutRepository` SHALL have integration test coverage asserting byte-stream/XML validity, `.tmp` atomic cleanup, and `ExportStatusDatabaseManager` state updates.
* **REQ-PRO-111.4 (`INT-SWE-08` Repository/ExportManager to Cloud & Health Connect — [Verified])**: Cloud and Health Connect connectors (`StravaUploader`, `DropboxUploader`, `HealthConnectManager`, `HealthConnectHrvImporter`) wired to real `ExportStatusDatabaseManager` / `HrvReadinessRepository` with authentic OkHttp `Interceptor` / `HealthConnectClient` test doubles (no mock-returning-mock assertions) SHALL have integration test coverage asserting bearer-token injection, multipart upload + polling + PUT update, HTTP 503 retry classification, `DropboxUploader` missing-credential guard, record mapping, and nocturnal HRV import.
* **REQ-PRO-111.5 (`integration_strategy.md` Factual Sync — [Verified])**: `docs/architecture/integration_strategy.md` MUST reflect factual Tier-2 metrics from `sprint_455.md` (`6,540` tests / `2,635.73s` sum; `1,119` suites / `29m 21s` wall-clock per `Sprint_Review_and_Retro_ATT-8500.md`), Level 1 scope (`INT-SWE-01..08`), and local `emulator-5554` (`Pixel_8_Android_15`) policy matching `stage5-verification/SKILL.md`.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-PRO-111` refining `REQ-PRO-045` (`docs/architecture/integration_strategy.md`, `docs/architecture/interfaces.md` `IF-SWE-05..08`).
* **Historical Origin & Commit Trace**: Sprint `2026-11-17_01_Früh` via `ATT-8156`, building on healed `ATT-8155` (`23009d93`) to close ASPICE 4.0 Assessment Finding `M6` (SWE.5 BP1–BP7).
* **Root Reason for Existing Formulation**: `integration_strategy.md` listed `INT-SWE-05..08` and stale `3,660+` / `~2–3 min` / retired-emulator text without executable integration tests.
* **Preservation of Core Invariants**: Preserves `REQ-PRO-045`, `REQ-PRO-110`, `TST-INT-002..012`, zero `app/src/main/` modifications, and `ANT-Android-SDKs` (`0e8cea71`).

---

## 3. Acceptance Criteria (Given-When-Then)
* **AC-1 (`INT-SWE-05`)**: *Given* synthetic speed, power, HR, and elevation streams, *When* processed by real `AutoPauseManager`, `PowerPhysiologyCalculator`, `PowerHrDynamicsCalculator`, `WorkoutClusterEngine`, and `ClimbDetector`, *Then* NP/TSS, cardiac drift, cluster apex, climb detection, and auto-pause transitions match deterministic invariants.
* **AC-2 (`INT-SWE-06`)**: *Given* real Robolectric SQLite managers (`WorkoutSamplesDatabaseManager`, `WorkoutSummariesDatabaseManager`, `KnownLocationsDatabaseManager`), *When* dynamic tables are created, samples/locations are written concurrently, and `onUpgrade` runs, *Then* WAL reads/writes and schema columns survive without data loss.
* **AC-3 (`INT-SWE-07`)**: *Given* a populated SQLite workout in temp storage, *When* `FitFileWriter`, `GPXFileWriter`, and `TCXFileWriter` export via `ExportManager`, *Then* valid FIT/GPX/TCX files are produced, `.tmp` files are cleaned up, and `ExportStatusDatabaseManager` records completion.
* **AC-4 (`INT-SWE-08`)**: *Given* `StravaUploader`/`DropboxUploader` (OkHttp `Interceptor` double) and `HealthConnectManager`/`HealthConnectHrvImporter` (fake `HealthConnectClient`) wired to real `ExportStatusDatabaseManager` and `HrvReadinessRepository`, *When* upload/HTTP 503 retry/Dropbox credential guard/sync/HRV import runs, *Then* bearer-token upload/PUT + retry classification, Health Connect session mapping, and SQLite/HRV persistence succeed.
* **AC-5 (`integration_strategy.md` Sync & Evidence)**: *Given* `docs/architecture/integration_strategy.md` and JUnit execution, *When* governance & tests run, *Then* metrics (`6,540` tests / `1,119` suites / `29m 21s` Tier-2) and `emulator-5554` policy match reality and JUnit XML captures `TST-INT-013..016` + `TST-PRO-111`.

---

## 4. Test Specifications & Traceability Matrix (`9-Locale Parity`: N/A — zero `strings*.xml` edits)

| Test Case | Scope | Target File / Class | Requirement / Interface | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-INT-013` | Integration | `integration/DomainCalculatorsIntegrationTest.kt` | `REQ-PRO-111.1` (`IF-SWE-05` / `INT-SWE-05`) | Verified |
| `TST-INT-014` | Integration | `integration/PersistenceSqliteIntegrationTest.kt` | `REQ-PRO-111.2` (`IF-SWE-06` / `INT-SWE-06`) | Verified |
| `TST-INT-015` | Integration | `integration/FileExportersIntegrationTest.kt` | `REQ-PRO-111.3` (`IF-SWE-07` / `INT-SWE-07`) | Verified |
| `TST-INT-016` | Integration | `integration/CloudHealthConnectIntegrationTest.kt` | `REQ-PRO-111.4` (`IF-SWE-08` / `INT-SWE-08`) | Verified |
| `TST-PRO-111` | Contract | `build/AspiceSweAuditContractTest.kt` | `REQ-PRO-111.5` & SWE.5 `INT-SWE-05..08` Lock | Verified |
