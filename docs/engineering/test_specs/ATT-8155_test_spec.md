# Stage 2: Requirement & Test Specification - ATT-8155: Integration Verification Test Cases for INT-SWE-01 to INT-SWE-04

**Ticket**: [ATT-8155](https://rainerblind.atlassian.net/browse/ATT-8155)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-16_03_Nacht` | **SP**: 3 SP  
**Requirement Mapping**: `REQ-PRO-110` (`REQ-PRO-110.1..4`)  
**Test Spec Mapping**: `TST-PRO-110` (`TST-PRO-110.1..4`), `TST-INT-009..012`  
**Branch**: `feature/ATT-8155` | **Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-17  

---

## 1. Requirement Specification (REQ-PRO-110)
* **Attributes**: Priority: `High` | Type: `Functional` | Verification Method: `Integration / Review`
* **REQ-PRO-110.1 (INT-SWE-01: UI to ViewModel MVI Integration — [Verified])**: Jetpack Compose Composables (`ControlTrackingButton.kt`) and ViewModels (`TrackingViewModel.kt`, `ControlTrackingViewModel.kt`, `TrackingTabsViewModel.kt`) SHALL have integration verification test coverage via Robolectric `createComposeRule()` asserting unidirectional MVI `StateFlow` / `LiveData` collection and action dispatch (`onStartTracking()`, `onPauseTracking()`, `onResumeTracking()`, `onStopTracking()`, `updateScreenMode()`, `setScreenMode()`) with `waitForIdle()` Compose node assertions without emulator dependency.
* **REQ-PRO-110.2 (INT-SWE-02: ViewModel to Repository Data Layer Integration — [Verified])**: ViewModels (`WorkoutSummariesViewModel.kt`) and repositories (`WorkoutRepository.kt`, `BANALServiceRepository.kt`) SHALL have integration test coverage asserting real SQLite updates (`WorkoutSummariesDatabaseManager.java` via `RealSqliteTestHelper.kt`) trigger reactive `Flow` emissions (`getWorkoutById`, `allWorkouts`) to ViewModel `StateFlow` without main-thread deadlocks.
* **REQ-PRO-110.3 (INT-SWE-03: Inter-Service Ingestion Binder IPC Integration — [Verified])**: Inter-service communication between `TrackerService.java` and `BANALService.java` via `BANALServiceComm` local binder SHALL have integration test coverage asserting 1-Hz batch polling (`sampleAndWriteToDb`, `getBestSensorData()`, `getAllSensorData()`) into `LiveWorkoutSession` and SQLite (`WorkoutSamplesDatabaseManager.java`, `WorkoutSummariesDatabaseManager.java`), sensor priority arbitration (`MySensorManager.java`), and unbind null-safety resilience.
* **REQ-PRO-110.4 (INT-SWE-04: Hardware HAL Byte Stream Ingestion & Sensor Replay — [Verified])**: Ingestion of Bluetooth SIG GATT characteristic byte streams (`BTLEHeartRateDevice.java` `0x2A37`, `BTLEBikePowerDevice.java` `0x2A63`, `BTLERadarDevice.java` `6a4e3204` V1 & V2) and Location updates (`SpeedAndLocationDevice_GPS.java` `onNewLocation()`) into `MySensorManager.java` / `RadarStateRepository.kt` SHALL have integration test coverage asserting binary packet parsing, location accumulation, and `BTLERadarDevice` watchdog timeout debounce (`WATCHDOG_TIMEOUT_MS` = 3,500 ms).

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-110` refining `REQ-PRO-045` (`docs/architecture/integration_strategy.md`) and `docs/architecture/interfaces.md` (`IF-SWE-01..04`). Targets `app/src/test/java/com/atrainingtracker/trainingtracker/integration/` and `tools/generate_test_evidence.py`.
* **Historical Origin & Commit Trace**: Sprint `2026-11-16_03_Nacht` / `2026-11-17_01_Früh` via ticket `ATT-8155` (`96bfc669`, `23009d93`, Bounce 2/3 Rule 8.7 grounding remediation) resolving ASPICE 4.0 Assessment (2026-11-15) Finding `M6` (SWE.5 BP1–BP7, PA 1.1 = P).
* **Root Reason for Existing Formulation**: `integration_strategy.md` defined `INT-SWE-01..08` abstractly without test classes; initial `REQ-PRO-110` wording contained unverified component aliases remediated under Rule 8.7 alongside `TrackingViewModel` (`uiState`, `updateScreenMode()`) Compose coverage.
* **Preservation of Core Invariants**: Preserves the 8-step bottom-up integration order, legacy `TST-INT-002..008`, Rule 79 (Robolectric Level 1 fast gate without emulators), and zero modifications to `app/src/main/` or `ANT-Android-SDKs` (`0e8cea71`).

---

## 3. Acceptance Criteria (Given-When-Then)
* **Criterion 1 (INT-SWE-01: UI to ViewModel)**: *Given* `ControlTrackingButton.kt`, `ControlTrackingViewModel.kt`, `TrackingTabsViewModel.kt`, and `TrackingViewModel.kt` composed via `createComposeRule()`, *When* `onStartTracking()`, `onPauseTracking()`, `onResumeTracking()`, `onStopTracking()`, `updateScreenMode()`, or `setScreenMode()` are dispatched, *Then* state changes recompose Compose nodes (`waitForIdle()` + `assertTextEquals()`) without exceptions.
* **Criterion 2 (INT-SWE-02: ViewModel to Repository)**: *Given* `WorkoutRepository.kt` and `WorkoutSummariesViewModel.kt` backed by real `WorkoutSummariesDatabaseManager.java` SQLite, *When* workout summary records are inserted and updated, *Then* `getWorkoutById` and `allWorkouts` emit updated records to the ViewModel StateFlow.
* **Criterion 3 (INT-SWE-03: Inter-Service Binder IPC)**: *Given* `TrackerService.java` connected to real `BANALService.java` (`BANALServiceComm`), *When* `sampleAndWriteToDb()` polls `getBestSensorData()` and `getAllSensorData()`, *Then* sensor telemetry is arbitrated by `MySensorManager.java`, persisted to SQLite, and unbind handles null gracefully.
* **Criterion 4 (INT-SWE-04: HAL Ingestion & Replay)**: *Given* GATT byte streams for HR (`0x2A37`), Power (`0x2A63`), Radar V1/V2 (`6a4e3204`), and `Location` fixes, *When* ingested via `BTLEHeartRateDevice`, `BTLEBikePowerDevice`, `BTLERadarDevice`, and `SpeedAndLocationDevice_GPS`, *Then* packets parse into `MySensorManager` / `RadarStateRepository` and `BTLERadarDevice` watchdog (`3,500 ms`) transitions to `DISCONNECTED_ERROR`.
* **Criterion 5 (Evidence Archival & CLI Export)**: *Given* integration test execution via `./gradlew testDebugUnitTest`, *When* `tools/generate_test_evidence.py` or `tools/jira_util.py export-test-evidence` runs, *Then* test results for `INT-SWE-01..04` are captured in machine-readable JUnit XML and archived in `docs/engineering/test_evidence/`.

---

## 4. Test Specifications & Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement / Interface | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-INT-009` | Integration | `integration/UiPresentationIntegrationTest.kt` | `REQ-PRO-110.1` (`IF-SWE-01` / `INT-SWE-01`) | Specified |
| `TST-INT-010` | Integration | `integration/ViewModelRepositoryIntegrationTest.kt` | `REQ-PRO-110.2` (`IF-SWE-02` / `INT-SWE-02`) | Specified |
| `TST-INT-011` | Integration | `integration/InterServiceIngestionIntegrationTest.kt` | `REQ-PRO-110.3` (`IF-SWE-03` / `INT-SWE-03`) | Specified |
| `TST-INT-012` | Integration | `integration/HardwareHalIngestionIntegrationTest.kt` | `REQ-PRO-110.4` (`IF-SWE-04` / `INT-SWE-04`) | Specified |
| `TST-PRO-110` | Contract | `build/AspiceSweAuditContractTest.kt` | `REQ-PRO-110` (SWE.5 BP1–BP7 Governance) | Specified |
