# Stage 3: Implementation Plan - ATT-6427: Repository-Only Live Recording Service Database Access Layer (Phase C)

**Ticket**: [ATT-6427](https://rainerblind.atlassian.net/browse/ATT-6427) | **Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2` | **Branch**: `feature/ATT-6427` | **Req/Test**: `REQ-TRK-066` / `TST-TRK-066`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Description & Architecture Rationale
Following Phase A (UI) and Phase B (Engines/Exporters), live foreground recording services (`TrackerService.java`, `BANALService.java`) and the hardware coordinator (`DeviceManager.java`) still directly invoke `*DatabaseManager.getInstance(...)` and `*DbHelper.getInstance(...)`. Furthermore, `DeviceManager` executes raw `Cursor` queries on `DevicesDbHelper.DEVICES`. We decouple these services into domain repositories (`WorkoutRepository`, `SportTypesRepository`, `DeviceDataRepository`, `KnownLocationsRepository`, `WorkoutRrRepository`), encapsulate paired remote device queries in typed `PairedRemoteDeviceDto`s, and eliminate all direct SQLite manager calls.

---

## 2. System Invariants & Preserved Behavior
1. **Foreground Service Lifecycle & Cadence**: 1-Hz sample recording on `mDbExecutor` preserved with zero dropped samples or thread contention.
2. **Minimal Transaction Finalization (`REQ-TRK-030.1`)**: Unconditional `FINISHED = 1` committed in dedicated minimal transaction before auxiliary metadata.
3. **Dynamic Schema Recovery (`REQ-TRK-026.4` / `REQ-TRK-051.1`)**: Mid-workout sensor discovery and PRAGMA table info self-healing preserved.
4. **Crash-Resume Fidelity (`REQ-TRK-031.3` / `REQ-TRK-036.2`)**: Bit-exact recovery of total/active time, distance, calories, and streams.
5. **Zero Backdoor Getters (§2.5)**: Repositories expose zero public methods returning `Cursor`, `SQLiteDatabase`, or `*DatabaseManager`.

---

## 3. UI Consistency (Design Guidelines)
* **Architectural Decoupling**: No UI modifications; pure service and repository persistence refactoring.

---

## 4. Step-by-Step Implementation Sequence

### Step 1: DeviceDataRepository PairedRemoteDeviceDto & Delegates (`REQ-TRK-066.3`, `TST-TRK-066.2`)
* **Files**: `banalservice/ui/devices/devicedata/DeviceDataRepository.kt`, `DeviceDataRepositoryTest.kt`
* **Changes**: Add `PairedRemoteDeviceDto` (id, paired, deviceType, antDeviceNumber, btAddress). Expose `getPairedRemoteDevices(protocol)` with zero leaked Cursors, `recordActiveDevice(workoutId, deviceId)`, and smartphone/battery delegates.

### Step 2: SportTypesRepository Query Delegates (`REQ-TRK-066.2`, `TST-TRK-066.4`)
* **Files**: `repositories/SportTypesRepository.kt`, `BANALServiceLifecycleTest.kt`
* **Changes**: Expose `getDefaultSportTypeId()`, `getSportTypesIdList(sportType, avgSpeed)`, and missing query delegates on `SportTypesRepository`.

### Step 3: WorkoutRepository Live Tracking Persistence Delegates (`REQ-TRK-066.1`, `TST-TRK-066.3`)
* **Files**: `ui/aftermath/WorkoutRepository.kt`, `TrackerServiceFinalizationTest.kt`
* **Changes**: Expose `createSamplesTable(baseFileName)`, `recordLiveSample(...)` / schema healing, `applyAltitudeDriftCorrection(...)`, `getResumedWorkoutState()`, `getEncodedStreams(workoutId)`, and `getExtremaRecords(workoutId)`.

### Step 4: Refactor BANALService.java (`REQ-TRK-066.2`, `TST-TRK-066.4`)
* **Files**: `banalservice/BANALService.java`
* **Changes**: Replace all 7 `SportTypeDatabaseManager` calls with `SportTypesRepository` delegates.

### Step 5: Refactor DeviceManager.java (`REQ-TRK-066.3`, `TST-TRK-066.2`)
* **Files**: `banalservice/devices/DeviceManager.java`
* **Changes**: Replace `DevicesDatabaseManager` calls and eliminate raw Cursor query in `createPairedRemoteDevices` by calling `DeviceDataRepository.getPairedRemoteDevices(protocol)`.

### Step 6: Refactor TrackerService.java (`REQ-TRK-066.1`, `REQ-TRK-066.4`, `TST-TRK-066.3`)
* **Files**: `tracker/TrackerService.java`
* **Changes**: Route all 27 database calls through `WorkoutRepository`, `SportTypesRepository`, `DeviceDataRepository`, `KnownLocationsRepository`, and `WorkoutRrRepository`.

### Step 7: Author RepositoryServiceCleanlinessContractTest (`TST-TRK-066.1`)
* **Files**: `app/src/test/java/com/atrainingtracker/trainingtracker/service/RepositoryServiceCleanlinessContractTest.kt`
* **Changes**: Verify zero direct `*DatabaseManager.getInstance()` or `*DbHelper.getInstance()` calls in `TrackerService.java`, `BANALService.java`, and `DeviceManager.java`.

### Step 8: Targeted Tier 1 Verification & Living Docs Parity (`TST-TRK-066.5`, `REQ-TRK-066`)
* **Files**: `docs/requirements/req_tracking_cockpit.md`, `docs/tests/tst_tracking_cockpit.md`
* **Verification**: Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tracker.*" --tests "com.atrainingtracker.banalservice.*" --tests "com.atrainingtracker.trainingtracker.service.*"`. Update living doc status tags to `[Verified]`.
