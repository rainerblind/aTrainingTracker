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
* **REQ-PRO-110.1 (INT-SWE-01: UI to ViewModel MVI Integration — [Planned])**: Jetpack Compose Composables (`TrackingTabsScreen`, `ControlTrackingBar`) and ViewModels (`TrackingViewModel`, `ControlTrackingViewModel`) SHALL have integration verification test coverage via Robolectric `createComposeRule()` asserting unidirectional MVI `StateFlow` collection and action dispatch (`onStartTracking()`, `updateScreenMode()`) without emulator dependency.
* **REQ-PRO-110.2 (INT-SWE-02: ViewModel to Repository Data Layer Integration — [Planned])**: ViewModels and repositories (`WorkoutRepository`, `BANALServiceRepository`) SHALL have integration test coverage asserting real SQLite updates (`WorkoutSamplesDatabaseManager`) trigger reactive `Flow` emissions to ViewModel `StateFlow` without main-thread deadlocks.
* **REQ-PRO-110.3 (INT-SWE-03: Inter-Service Ingestion Binder IPC Integration — [Planned])**: Inter-service communication between `TrackerService` and `BANALService` via `BANALServiceComm` local binder SHALL have integration test coverage asserting 1-Hz batch polling (`getAllFilteredSensorData`), sensor priority arbitration (BLE vs ANT+), and unbind null-safety resilience.
* **REQ-PRO-110.4 (INT-SWE-04: Hardware HAL Byte Stream Ingestion & Sensor Replay — [Planned])**: Ingestion of Bluetooth SIG GATT characteristic byte streams (Heart Rate `0x2A37`, Cycling Power `0x2A63`, Radar `6a4e3204`) and Location updates into `BANALService` / `TrackerService` SHALL have integration test coverage asserting binary packet parsing, synthetic sensor replay, and timeout debounce (>3.5s).

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-PRO-110` refining `REQ-PRO-045` (`docs/architecture/integration_strategy.md`) and `docs/architecture/interfaces.md` (`IF-SWE-01..04`). Targets `app/src/test/java/com/atrainingtracker/trainingtracker/integration/` and `tools/generate_test_evidence.py`.
* **Historical Origin & Commit Trace**: Sprint 2026-11-16_03_Nacht via ticket `ATT-8155` resolving ASPICE 4.0 Assessment (2026-11-15) Finding `M6` (SWE.5 BP1–BP7, PA 1.1 = P).
* **Root Reason for Existing Formulation**: `integration_strategy.md` defined `INT-SWE-01..08` abstractly without corresponding test classes or versioned evidence, relying on unit mock tests.
* **Preservation of Core Invariants**: Preserves the 8-step bottom-up integration order, legacy `TST-INT-002..008`, Rule 79 (Robolectric Level 1 fast gate without emulators), and zero modifications to `app/src/main/` or `ANT-Android-SDKs` (`0e8cea71`).

---

## 3. Acceptance Criteria (Given-When-Then)
* **Criterion 1 (INT-SWE-01: UI to ViewModel)**: *Given* `TrackingTabsScreen` composed with `TrackingViewModel`, *When* `updateScreenMode` or tracking control actions are dispatched, *Then* state changes reflect in Compose nodes without recomposition exceptions.
* **Criterion 2 (INT-SWE-02: ViewModel to Repository)**: *Given* `WorkoutRepository` backed by real SQLite, *When* workout telemetry samples are written, *Then* `getWorkoutById` emits updated records to the ViewModel StateFlow.
* **Criterion 3 (INT-SWE-03: Inter-Service Binder IPC)**: *Given* `TrackerService` connected to `BANALServiceComm`, *When* 1-Hz polling tick triggers, *Then* sensor telemetry is retrieved, prioritized, and binder unbind handles null gracefully.
* **Criterion 4 (INT-SWE-04: HAL Ingestion & Replay)**: *Given* GATT byte streams for HR (`0x2A37`), Power (`0x2A63`), and Radar (`6a4e3204`), *When* injected into service listeners, *Then* packets parse correctly and dropouts (>3.5s) debounce cleanly.
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
