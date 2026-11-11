# Stage 2: Requirement & Test Specification - ATT-6738: Fix hallucinated sensors, FGS types, and omitted cloud services in system_context.md

**Ticket**: [ATT-6738](https://rainerblind.atlassian.net/browse/ATT-6738)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PRO-053` (*System Context and External Interfaces Specification*)  
**Test Spec ID**: `TST-PRO-053`  
**Branch**: `feature/ATT-6738`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-PRO-053)

### 1.1 Problem Statement & Rationale
`docs/system/system_context.md` previously claimed non-existent on-board sensors (`TYPE_STEP_COUNTER`, `TYPE_ACCELEROMETER`), mislabeled Android API 37, cited non-existent foreground service types (`FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE`), listed stale `.java` file extensions, and omitted active cloud endpoints (Open-Meteo, Firebase, Google Play Review/Auth, Health Connect HRV/RHR read). REQ-PRO-053 must be refined to enforce 100% code-grounded parity and dynamic filesystem/symbol verification.

### 1.2 Functional & Architectural Requirements
1. **On-Board Sensors Parity (`REQ-PRO-053.1`)**:
   - `docs/system/system_context.md` SHALL specify only the hardware sensors implemented in `app/src/main/`: `Sensor.TYPE_PRESSURE` (`AltitudeFromPressureDevice.java`) and `Sensor.TYPE_PROXIMITY` (`BatterySaverController.kt`).
   - `Sensor.TYPE_STEP_COUNTER` and `Sensor.TYPE_ACCELEROMETER` SHALL NOT be listed.
2. **Platform & Service Execution Parity (`REQ-PRO-053.1`)**:
   - `docs/system/system_context.md` SHALL document `FOREGROUND_SERVICE_TYPE_LOCATION` and `FOREGROUND_SERVICE_TYPE_HEALTH` conforming to `AndroidManifest.xml` and `TrackerService.java`.
   - `targetSdkVersion: 37` SHALL be correctly labeled as Android 17.
   - Cloud uploaders SHALL reference `StravaUploader.kt` and `GoogleDriveUploader.kt`.
   - Fused location API SHALL be grounded with `SpeedAndLocationDevice_GoogleFused.java` (`GoogleApiClient` / `LocationServices.FusedLocationApi`).
3. **Cloud & External Peripherals Completeness (`REQ-PRO-053.1`)**:
   - SHALL document Open-Meteo Elevation API (`ElevationService.kt`), Firebase Crashlytics & Analytics (`firebase-bom:34.18.0`), Google Play Auth (`play-services-auth:21.3.0`) and In-App Review (`play:review-ktx:2.0.2`).
   - Domain 1 SHALL document BLE `SERVICE_DEVICE_INFORMATION` (`0x180A`) and Garmin Varia Radar V1 (`6a4e3203-...`) and Light Control (`6a4e3202-...`) UUIDs.
   - Domain 4 SHALL document Health Connect read capabilities: `READ_HEART_RATE_VARIABILITY` and `READ_RESTING_HEART_RATE`.
4. **Dynamic Contract Verification (`REQ-PRO-053.2`)**:
   - `SystemContextContractTest.kt` SHALL dynamically parse all backticked `.kt`, `.java`, and `.xml` filenames and assert their existence on disk.
   - `SystemContextContractTest.kt` SHALL dynamically assert cited sensor and FGS symbols against authentic Android framework / production constants.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (System Context Specification Grounding)**:
  * *Given* `docs/system/system_context.md`,
  * *When* parsed by `SystemContextContractTest`,
  * *Then* it SHALL document `Sensor.TYPE_PRESSURE`, `Sensor.TYPE_PROXIMITY`, FGS types `location|health`, API 37 (Android 17), `StravaUploader.kt`, `GoogleDriveUploader.kt`, `ElevationService.kt`, and all authentic BLE/Cloud constants.
* **Criterion 2 (Dynamic Contract Assertions)**:
  * *Given* `SystemContextContractTest.kt`,
  * *When* executed via `./gradlew testDebugUnitTest`,
  * *Then* all dynamic `File.exists()` checks for referenced code files and symbol existence checks SHALL pass with 100% success.
* **Criterion 3 (Requirement Governance)**:
  * *Given* `tools/verify_requirement_governance.py`,
  * *When* executed on `feature/ATT-6738`,
  * *Then* living docs master tables and shard files SHALL pass with 100% parity.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-053` / `TST-PRO-053` (*System Context and External Interfaces Specification*).
* **Historical Origin & Commit Trace**: Independent ASPICE pre-assessment finding S-01 (2026-11-08) and ticket ATT-6158 (commit `4ced957a`).
* **Root Reason for Existing Formulation**: Drafted under initial SYS.2 baseline with assumptions carrying over speculative sensor constants and legacy Java file suffixes.
* **Preservation of Core Invariants**: The 5-domain SYS.2/SYS.3 architecture is preserved; removing hallucinated constants and hardening test assertions ensures strict code grounding and zero test regressions.

---

## 2. Test Specification (TST-PRO-053)

### Test Case 1: `testSystemContext_allReferencedCodeFilesExistOnDisk` (`TST-PRO-053.1`)
* **Scope**: Contract Test (`app/src/test/java/.../build/SystemContextContractTest.kt`)
* **Preconditions**: Repository workspace initialized with authentic codebase files.
* **Action**: Dynamically extracts regex `` `([A-Za-z0-9_]+\.(?:kt|java|xml))` `` from `docs/system/system_context.md` and checks on-disk existence via repository tree walk.
* **Expected Result**: 100% of referenced code/resource files exist on disk (size $\ge 10$).

### Test Case 2: `testSystemContext_codeGroundingParity` (`TST-PRO-053.2`)
* **Scope**: Contract Test (`app/src/test/java/.../build/SystemContextContractTest.kt`)
* **Preconditions**: `system_context.md` updated with authentic sensors, FGS types, and cloud services.
* **Action**: Asserts presence of `Sensor.TYPE_PRESSURE`, `Sensor.TYPE_PROXIMITY`, `FOREGROUND_SERVICE_TYPE_LOCATION`, `FOREGROUND_SERVICE_TYPE_HEALTH`, Open-Meteo elevation endpoint, Firebase dependencies, Google Play Auth/Review, and BLE UUIDs.
* **Expected Result**: All assertions evaluate to true.

### Test Case 3: `testSystemContext_referencedPlatformSymbolsGrounded` (`TST-PRO-053.3`)
* **Scope**: Contract Test (`app/src/test/java/.../build/SystemContextContractTest.kt`)
* **Preconditions**: `system_context.md` content loaded.
* **Action**: Dynamically extracts all `Sensor.TYPE_*` and `FOREGROUND_SERVICE_TYPE_*` tokens and verifies they match the authentic allowed sets: `{Sensor.TYPE_PRESSURE, Sensor.TYPE_PROXIMITY}` and `{FOREGROUND_SERVICE_TYPE_LOCATION, FOREGROUND_SERVICE_TYPE_HEALTH}`.
* **Expected Result**: Zero hallucinated platform symbols present in markdown.

### Test Case 4: Targeted Clean-Room Execution (`TST-PRO-053.4`)
* **Command**: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.SystemContextContractTest"`
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-053.1` | Contract | `testSystemContext_allReferencedCodeFilesExistOnDisk` | `REQ-PRO-053.2` | Specified |
| `TST-PRO-053.2` | Contract | `testSystemContext_codeGroundingParity` | `REQ-PRO-053.1` | Specified |
| `TST-PRO-053.3` | Contract | `testSystemContext_referencedPlatformSymbolsGrounded` | `REQ-PRO-053.1`, `REQ-PRO-053.2` | Specified |
| `TST-PRO-053.4` | Clean-Room | Targeted Unit Suite Execution | `REQ-PRO-001`, `REQ-PRO-053` | Specified |
