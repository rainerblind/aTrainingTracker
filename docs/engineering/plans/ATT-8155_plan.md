# Stage 3 Implementation Plan - ATT-8155: Integration Verification Test Cases for INT-SWE-01 to INT-SWE-04

**Ticket**: [ATT-8155](https://rainerblind.atlassian.net/browse/ATT-8155) | **Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-16_03_Nacht` | **SP**: 3 SP  
**Requirement Mapping**: `REQ-PRO-110` (`REQ-PRO-110.1..4`) | **Test Mapping**: `TST-PRO-110` (`TST-PRO-110.1..4`), `TST-INT-009..012`  
**Branch**: `feature/ATT-8155` | **Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-17  

---

## 1. Architectural Seam Decomposition (SWE.2)
Implements Level 1 Robolectric JVM integration tests for the first four bottom-up integration steps:

| Step ID | Boundary | Interface | Provider | Consumer | Test Class | Test ID |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `INT-SWE-01` | UI $\to$ ViewModel | `IF-SWE-01` | `TrackingViewModel`, `ControlTrackingViewModel` | Jetpack Compose UI | `UiPresentationIntegrationTest` | `TST-INT-009` |
| `INT-SWE-02` | ViewModel $\to$ Repo | `IF-SWE-02` | `WorkoutRepository`, `BANALServiceRepository` | ViewModels | `ViewModelRepositoryIntegrationTest` | `TST-INT-010` |
| `INT-SWE-03` | Inter-Service | `IF-SWE-03` | `BANALServiceComm` (Local Binder) | `TrackerService` | `InterServiceIngestionIntegrationTest` | `TST-INT-011` |
| `INT-SWE-04` | HAL $\to$ Service | `IF-SWE-04` | Hardware HAL (BLE GATT / Location Callbacks) | `BANALService` | `HardwareHalIngestionIntegrationTest` | `TST-INT-012` |

---

## 2. Atomic Implementation Steps (SWE.3)

* **Step 1 (`INT-SWE-01` / `TST-INT-009` / `UiPresentationIntegrationTest.kt`)**:
  - Author Robolectric Compose UI test with `createComposeRule()` hosting `ControlTrackingBar` / `TrackingTabsScreen`.
  - Wire real `ControlTrackingViewModel` / `TrackingViewModel`.
  - Verify MVI action dispatch (`onStartTracking()`, `updateScreenMode()`) modifies state and updates UI nodes reactively without an emulator.
* **Step 2 (`INT-SWE-02` / `TST-INT-010` / `ViewModelRepositoryIntegrationTest.kt`)**:
  - Author integration test linking real `TrackOnMapAftermathViewModel` / `TrackingViewModel` to real `WorkoutRepository` backed by in-memory SQLite (`WorkoutSummariesDatabaseManager`).
  - Verify workout insertion/modification in SQLite triggers reactive `Flow` emissions to ViewModel `StateFlow` without main-thread blocking.
* **Step 3 (`INT-SWE-03` / `TST-INT-011` / `InterServiceIngestionIntegrationTest.kt`)**:
  - Author integration test binding `TrackerService` to `BANALServiceComm`.
  - Verify 1-Hz batch polling (`sampleAndWriteToDb`), priority sensor arbitration (multi-source HR), and null-safety during unbind.
* **Step 4 (`INT-SWE-04` / `TST-INT-012` / `HardwareHalIngestionIntegrationTest.kt`)**:
  - Author integration test injecting simulated Bluetooth SIG GATT characteristic bytes (HR `0x2A37`, Power `0x2A63`, Radar `6a4e3204`) and Location updates into `BANALService` device drivers (`BTLEHeartRateDevice`, `BTLERadarDevice`, `LocationListener`).
  - Verify binary unpacking, `SensorData` emission to `BANALServiceComm`, and sensor dropout debounce (>3.5s timeout).
* **Step 5 (Governance & Evidence Contract / `TST-PRO-110`)**:
  - Update `AspiceSweAuditContractTest.kt` asserting `INT-SWE-01..04` test classes exist on disk and map to `TST-INT-009..012`.
  - Confirm `tools/generate_test_evidence.py` captures JUnit XML evidence for `TST-INT-009..012` and `TST-PRO-110`.

---

## 3. Invariant Protection & Code Lock (Core Rule 4)
* **Code Lock**: Strict zero modifications to `app/src/main/` Android production runtime code during Stage 3 planning.
* **No Mocked-Away Seams**: Interfaces under test MUST connect real components across the boundary (real ViewModels, real Repositories, real SQLite tables, real Service Binder, real byte unpackers). External hardware only (radio waves, satellites) is simulated.
* **Submodule Integrity**: `ANT-Android-SDKs` remains untouched at `0e8cea714a231fb9565170d4ed01eafa868f9798`.

---

## 4. Verification Commands
* **Targeted Tier 1 Test Suite**:
  ```bash
  ./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.integration.*" :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest"
  ```
* **Governance Verification**:
  ```bash
  python3 tools/verify_requirement_governance.py
  ```
