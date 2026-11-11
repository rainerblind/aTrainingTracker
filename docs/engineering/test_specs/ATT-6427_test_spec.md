# Stage 2: Requirement & Test Specification - ATT-6427: Repository-Only Live Recording Service Database Access Layer (Phase C)

**Ticket**: [ATT-6427](https://rainerblind.atlassian.net/browse/ATT-6427) | **Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2` | **Branch**: `feature/ATT-6427`  
**Requirement Mapping**: `REQ-TRK-066` (resolving tentative `REQ-TRK-055` from Stage 1 analysis)  
**Test Spec ID**: `TST-TRK-066`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-TRK-066)

### 1.1 Problem Statement & Rationale
Following Phase A UI decoupling (`ATT-432`) and Phase B engine decoupling (`ATT-6426`), foreground recording services (`TrackerService`, `BANALService`) and hardware coordinators (`DeviceManager`) still directly instantiate SQLite database managers and execute raw `Cursor` queries. This violates SWE.2 Clean Architecture boundaries. All database persistence operations in Phase C SHALL route through domain repositories, and paired remote device queries SHALL be encapsulated behind domain DTOs (`PairedRemoteDeviceDto`).

### 1.2 Functional & Architectural Requirements
* **REQ-TRK-066.1** (*TrackerService Decoupling*): `TrackerService.java` SHALL eliminate all direct calls to `WorkoutSummariesDatabaseManager`, `WorkoutSamplesDatabaseManager`, `LapsDatabaseManager`, `SportTypeDatabaseManager`, `ActiveDevicesDbHelper`, `WorkoutRrDatabaseManager`, and `KnownLocationsDatabaseManager`, routing session lifecycle, 1-Hz writes, and crash-resume through domain repositories.
* **REQ-TRK-066.2** (*BANALService Decoupling*): `BANALService.java` SHALL eliminate all direct calls to `SportTypeDatabaseManager`, routing sport type ID and UI name queries through `SportTypesRepository`.
* **REQ-TRK-066.3** (*DeviceManager Decoupling & PairedRemoteDeviceDto*): `DeviceManager.java` SHALL eliminate all direct calls to `DevicesDatabaseManager` and raw `Cursor` queries on `DevicesDbHelper.DEVICES`. `DeviceDataRepository` SHALL expose `getPairedRemoteDevices(protocol)` returning typed `PairedRemoteDeviceDto` instances with zero leaked Cursors.
* **REQ-TRK-066.4** (*High-Frequency Execution & Invariant Preservation*): 1-Hz sample recording delegates SHALL execute on `mDbExecutor` with zero thread contention or dropped samples. Unconditional `FINISHED = 1` transaction (`REQ-TRK-030.1`), mid-workout PRAGMA table info self-healing (`REQ-TRK-026.4` / `REQ-TRK-051.1`), and bit-exact crash resume (`REQ-TRK-031.3`) SHALL remain strictly preserved.
* **REQ-TRK-066.5** (*Zero Backdoor Getters*): Domain repositories SHALL NOT expose `get*DatabaseManager()`, `get*DbHelper()`, `SQLiteDatabase`, or raw `Cursor` getters to service callers (§2.5).

### 1.3 Acceptance Criteria (Given-When-Then) & System Invariants
* *Given* `TrackerService.java`, `BANALService.java`, and `DeviceManager.java`,
* *When* auditing source code and AST references,
* *Then* zero occurrences of `*DatabaseManager.getInstance(...)` or `*DbHelper.getInstance(...)` exist across Phase C target files.
* *Given* `DeviceManager.createPairedRemoteDevices(...)`,
* *When* querying paired ANT+ or Bluetooth LE devices,
* *Then* `DeviceDataRepository.getPairedRemoteDevices(protocol)` returns typed `PairedRemoteDeviceDto` objects with zero direct `Cursor` access.
* *Invariants*: Unconditional `FINISHED = 1` minimal transaction preserved (`REQ-TRK-030.1`); 1-Hz telemetry cadence on `mDbExecutor` preserved; zero SQLite schema alterations; 100% unit test pass rate.

---

## 2. Test Specification (TST-TRK-066)

### Test Case 1: AST DatabaseManager Prohibition Contract Test (`TST-TRK-066.1`)
* **Scope**: Automated Contract Test (`RepositoryServiceCleanlinessContractTest.kt`)
* **Assertion**: Verify zero direct `*DatabaseManager.getInstance()` or `*DbHelper.getInstance()` calls exist in `TrackerService.java`, `BANALService.java`, and `DeviceManager.java`.

### Test Case 2: PairedRemoteDeviceDto & DeviceDataRepository Query Test (`TST-TRK-066.2`)
* **Scope**: Repository Unit Test (`DeviceDataRepositoryTest.kt`)
* **Assertion**: Verify `DeviceDataRepository.getPairedRemoteDevices(protocol)` returns valid `PairedRemoteDeviceDto` instances for ANT+ and BTLE with zero Cursor leaks.

### Test Case 3: TrackerService Lifecycle & Persistence Invariants Test (`TST-TRK-066.3`)
* **Scope**: Service Unit Tests (`TrackerServiceResumeTest.kt`, `TrackerServiceFinalizationTest.kt`)
* **Assertion**: Verify crash resume, 1-Hz sample writes, lap persistence, and unconditional `FINISHED = 1` finalization execute identically via repositories.

### Test Case 4: BANALService Sport Type Delegation Test (`TST-TRK-066.4`)
* **Scope**: Service Unit Test (`BANALServiceLifecycleTest.kt`)
* **Assertion**: Verify sport type lookups and active lists delegate to `SportTypesRepository`.

### Test Case 5: Tier 1 Targeted Regression Suite (`TST-TRK-066.5`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tracker.*" --tests "com.atrainingtracker.banalservice.*" --tests "com.atrainingtracker.trainingtracker.repositories.*"`
* **Assertion**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-066.1` | Contract | `RepositoryServiceCleanlinessContractTest.kt` | `REQ-TRK-066.1` – `REQ-TRK-066.3`, `REQ-TRK-066.5` | Specified |
| `TST-TRK-066.2` | Unit | `DeviceDataRepositoryTest.kt` | `REQ-TRK-066.3` | Specified |
| `TST-TRK-066.3` | Unit | `TrackerServiceResumeTest.kt`, `TrackerServiceFinalizationTest.kt` | `REQ-TRK-066.1`, `REQ-TRK-066.4` | Specified |
| `TST-TRK-066.4` | Unit | `BANALServiceLifecycleTest.kt` | `REQ-TRK-066.2` | Specified |
| `TST-TRK-066.5` | Regression | `tracker.*`, `banalservice.*`, `repositories.*` | `REQ-PRO-001` | Specified |
