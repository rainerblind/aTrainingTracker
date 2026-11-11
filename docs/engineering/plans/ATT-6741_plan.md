# Stage 3: Implementation Plan - ATT-6741: Reconcile 13 Orphan SYSREQ IDs, Fix 10 Hallucinated Classes & Sequence Actor in System Architecture

**Ticket**: [ATT-6741](https://rainerblind.atlassian.net/browse/ATT-6741)  
**Sub-task**: [ATT-6790](https://rainerblind.atlassian.net/browse/ATT-6790) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture & Test Design*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PRO-056` (*System Architectural Design & SYSREQ Allocation*)  
**Test Mapping**: `TST-PRO-056` (*System Architectural Design Contract Test Suite*)  
**Branch**: `feature/ATT-6741` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Description & Background
ASPICE SYS.3 specification `docs/system/system_architecture.md` contains 13 orphan SYSREQ IDs in Section 5 (`SYSREQ-ACQ-001..005`, `SYSREQ-PER-001..005`, `SYSREQ-ENV-001..003`) that do not exist in SYS.2, 10 hallucinated classes/constants/XML entities, an inaccurate workout finalization actor in Sequence Diagram 4.2 (`WorkoutDetailScreen`), and a contract test gap where `SystemArchitectureDesignContractTest.kt` checked only unilateral subset containment. This plan details the surgical reconciliation of the architecture document, alignment of SYS.2 specifications, and hardening of the contract test suite.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-056` (*System Architectural Design Specification & SYSREQ Allocation*)
  * `REQ-PRO-056.1`: Architecture specification reconciliation (12 SYSREQ IDs, grounded code symbols, accurate Diagram 4.2 finalization sequence).
  * `REQ-PRO-056.2`: Contract test hardening (exact bidirectional set equality `declaredSysreqs == allocatedSysreqs`, dynamic reflection code grounding, sequence actor assertion).
* **Test Mapping**: `TST-PRO-056` (`TST-PRO-056.1` to `TST-PRO-056.5`).

---

## 3. System Invariants & Preserved Behavior
1. **Zero Runtime Production Modifications**: No modifications to application runtime logic in `app/src/main/java/`; strictly documentation and contract test alignment.
2. **SYS.3 Decomposition Invariance**: 5 System Elements (`SYS-EL-01..05`) and 6 Interfaces (`SYS-IF-01..06`) preserved.
3. **Exact Bidirectional Traceability**: Zero orphan or undeclared SYSREQ IDs allowed across SYS.2 and SYS.3.
4. **UI Consistency**: No UI changes (Rule 23: N/A, zero visual modifications).

---

## 4. Proposed Architectural Changes
* **System Architecture Specification (`docs/system/system_architecture.md`)**:
  * Restructure Section 5 into 2 authoritative domains: Domain 1 (Live Tracking Loop `SYSREQ-TRK-001..006`) and Domain 2 (Data Exchange `SYSREQ-DEX-001..006`). Update prose to state "all 12 system requirements".
  * Replace 10 hallucinated entities across Sections 2, 3, 4, 5, and 6 with authentic code elements (`RadarEdgeOverlay`, `RadarAlertManager`, `RadarAudioSynthesizer`, `NavigationAudioAlertManager`, `BikeBellAudioSynthesizer`, `SensorBatteryNotificationManager`, `GoogleApiClient` + `LocationListener`, `full_backup_content.xml`, process `com.atrainingtracker`).
  * Rewrite Diagram 4.2 to depict `ControlTrackingScreen` / notification stop -> `TrackerService.handleActionStop()` -> `mDbExecutor` executing `endWorkout()` -> `ExportManager` -> cloud upload / backup.
* **SYS.2 Specifications Alignment**:
  * Sweep `docs/system/sysreq_live_tracking.md`, `docs/system/sysreq_data_exchange.md`, and `docs/system_requirements.md` to eliminate echoed hallucinated symbols (`RadarThreatView`, `SoundEngine`, `FusedLocationProviderClient`, `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE`, `backup_rules.xml`).
* **Contract Test Suite (`SystemArchitectureDesignContractTest.kt`)**:
  * Enforce `assertEquals(declaredSysreqs, allocatedSysreqs)` in `testSystemArchitecture_crossChecksSystemRequirementsSpec`.
  * Update `testSystemArchitecture_100PercentSysreqAllocationMatrix` to assert the 12 authentic SYSREQ IDs.
  * Add `testSystemArchitecture_productionCodeGrounding` verifying authentic symbols via `Class.forName()` and `File.exists()` without source-text reading per ATT-6186.
  * Update sequence diagram assertions in `testSystemArchitecture_definesDynamicBehaviorSequenceDiagrams` to verify `ControlTrackingScreen`, `TrackerService`, and `mDbExecutor`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (Reconcile Section 5 Allocation Matrix & Prose)**:
   * Edit `docs/system/system_architecture.md`: Replace 25-item matrix with 12-item matrix (`SYSREQ-TRK-001..006`, `SYSREQ-DEX-001..006`); eliminate 13 orphan IDs; update prose counts to 12.
2. **Step 2 (Replace 10 Hallucinated Symbols & Process Name)**:
   * Edit `docs/system/system_architecture.md`: Replace non-existent symbols with grounded classes/files; update process name to `com.atrainingtracker`.
   * Harmonize `docs/system/sysreq_live_tracking.md`, `docs/system/sysreq_data_exchange.md`, and `docs/system_requirements.md`.
3. **Step 3 (Correct Sequence Diagram 4.2 Actors)**:
   * Edit `docs/system/system_architecture.md`: Update Diagram 4.2 to model `ControlTrackingScreen` / notification action delegating stop finalization to `TrackerService` on `mDbExecutor`.
4. **Step 4 (Harden Contract Test Suite)**:
   * Edit `app/src/test/java/com/atrainingtracker/trainingtracker/build/SystemArchitectureDesignContractTest.kt`: Enforce exact bidirectional set equality (`declaredSysreqs == allocatedSysreqs`), verify 12 requirements, assert `TrackerService` / `mDbExecutor` sequence actors, and add dynamic reflection checks (`Class.forName` / `File.exists`).
5. **Step 5 (Targeted Test Execution & Governance Verification)**:
   * Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.SystemArchitectureDesignContractTest"`
   * Run `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted contract test passes 100%; `verify_requirement_governance.py` verifies living documentation parity and Chesterton's Fence compliance.
* **Rollback**: Isolated branch `feature/ATT-6741` can be cleanly reverted without affecting `develop` or active sprint deliverables.
