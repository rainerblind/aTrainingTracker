# Stage 1 Analysis - ATT-8155: Integration Verification Test Cases for INT-SWE-01 to INT-SWE-04 (SWE.5 Finding M6)

**Ticket**: [ATT-8155](https://rainerblind.atlassian.net/browse/ATT-8155)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-16_03_Nacht` | **SP**: 3 SP  
**Requirement Mapping**: `REQ-PRO-110` (`REQ-PRO-110.1..4`)  
**Test Mapping**: `TST-PRO-110` (`TST-PRO-110.1..4`), `TST-INT-009..012`  
**Branch**: `feature/ATT-8155` | **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-17  

---

## 1. Problem Domain & Forensic Root Cause (Finding M6)
ASPICE 4.0 Assessment (2026-11-15) Finding `M6` (SWE.5 BP1–BP7, rating PA 1.1 = P) established that while `docs/architecture/integration_strategy.md` defines integration steps `INT-SWE-01..08` across interfaces `IF-SWE-01..08`, integration test verification was non-effective:
1. `INT-SWE-01` through `INT-SWE-04` were never refined into traceable test specifications (`TST-INT-*`) or bidirectional living requirement links.
2. Instrumented tests were limited to 3 smoke checks in `AppSmokeAndroidTest.kt` with zero Compose UI verification; unit tests relied heavily on isolated mocks without testing real component interactions.
3. No machine-readable JUnit XML evidence existed for component boundary interactions.

---

## 2. Technical Gap & Seam Analysis for INT-SWE-01..04
* **`INT-SWE-01` (`IF-SWE-01`: UI $\to$ ViewModel)**: Jetpack Compose Composables (`TrackingTabsScreen`, `ControlTrackingBar`) collect `StateFlow` and dispatch MVI actions (`onStartTracking()`, `updateScreenMode()`) to ViewModels (`TrackingViewModel`, `ControlTrackingViewModel`). Needs Robolectric `createComposeRule()` test verifying state hoisting and action dispatch without an emulator.
* **`INT-SWE-02` (`IF-SWE-02`: ViewModel $\to$ Repository)**: ViewModels observe reactive `Flow`s and call coroutines on `WorkoutRepository` and `BANALServiceRepository`. Needs Robolectric test verifying real SQLite database updates trigger reactive Flow emissions to ViewModel `StateFlow` without thread deadlocks.
* **`INT-SWE-03` (`IF-SWE-03`: Inter-Service Ingestion)**: `TrackerService` binds to `BANALService` via `BANALServiceComm`. Needs test verifying 1-Hz batch polling (`sampleAndWriteToDb`), priority sensor arbitration (BLE vs ANT+), and unbind resilience.
* **`INT-SWE-04` (`IF-SWE-04`: Hardware HAL $\to$ Service)**: Bluetooth SIG GATT byte streams (HR `0x2A37`, Power `0x2A63`, Radar `6a4e3204`) and Location updates ingested into `BANALService`. Needs test feeding simulated byte packets, asserting binary parsing, sensor timeout debounce (>3.5s), and delivery.

---

## 3. Independent Peer Review & Verification
* **Robolectric JVM vs. `androidTest`**: Evaluated Level 1 (Robolectric JVM on SDK 34) vs Level 2 (`connectedDebugAndroidTest`). CI and Tier 2 run `./gradlew testDebugUnitTest` without KVM emulators (Rule 79). Robolectric executes in seconds, avoids ADB flakiness, and generates JUnit XML ingested by `tools/generate_test_evidence.py` and `tools/verify_requirement_governance.py`.
* **Replay & Simulator Harnesses**: Reusable sensor packet serializers exist in `tools/ble_hr_simulator.py`, `tools/ble_power_simulator.py`, and `tools/ble_radar_simulator.py`, providing byte patterns for HAL tests.

---

## 4. Chesterton's Fence Archaeology & Invariant Preservation
* **Bottom-Up Sequence**: Preserves `INT-SWE-01..08` sequence and `IF-SWE-01..08` definitions in `docs/architecture/integration_strategy.md`.
* **Legacy TST IDs**: Preserves legacy `TST-INT-002..008` in `docs/tests/tst_process_stability.md`. Allocates new contiguous IDs `TST-INT-009` (`INT-SWE-01`), `TST-INT-010` (`INT-SWE-02`), `TST-INT-011` (`INT-SWE-03`), `TST-INT-012` (`INT-SWE-04`).
* **Submodule & Production Code**: `ANT-Android-SDKs` remains untouched at `0e8cea71`; zero modifications to `app/src/main/`.

---

## 5. Scope Bounding & Complexity Assessment
* **In-Scope**: Refine `INT-SWE-01..04` into `REQ-PRO-110.1..4` and `TST-INT-009..012` / `TST-PRO-110.1..4`; implement 4 Robolectric integration test classes in `app/src/test/.../integration/`; generate versioned JUnit test evidence.
* **Out-of-Scope**: `INT-SWE-05..08` (reserved for subsequent sprint tickets); production code refactoring.
* **Complexity**: 3 SP (Standard Complex, multi-interface test harness, fully achievable within pair capacity).

---

## 6. Change-Request Impact Analysis (REQ-PRO-064 / ASPICE SUP.10)
* **Affected Requirements**: Net-new `REQ-PRO-110` (`REQ-PRO-110.1..4`) in `docs/requirements/req_process_stability.md`.
* **Affected Architecture Elements**: `IF-SWE-01`..`IF-SWE-04` and `INT-SWE-01`..`INT-SWE-04` in `docs/architecture/interfaces.md` and `docs/architecture/integration_strategy.md`.
* **Verification Impact & Test Surface**: 4 new Robolectric integration test classes in `app/src/test/java/com/atrainingtracker/trainingtracker/integration/` (`UiPresentationIntegrationTest`, `ViewModelRepositoryIntegrationTest`, `InterServiceIngestionIntegrationTest`, `HardwareHalIngestionIntegrationTest`).
* **Risk Assessment & Invariants**: LOW risk. Additive test code and governance documentation only; zero Android production runtime logic or schema modifications.
