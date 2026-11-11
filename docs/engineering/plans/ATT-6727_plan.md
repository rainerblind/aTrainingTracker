# Stage 3: Implementation Plan - ATT-6727: Audit all architecture docs against source symbols and enforce dynamic component-existence contract test

**Ticket**: [ATT-6727](https://rainerblind.atlassian.net/browse/ATT-6727)  
**Sub-task**: [ATT-6964](https://rainerblind.atlassian.net/browse/ATT-6964) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PRO-043` (`REQ-PRO-043.1`, `REQ-PRO-043.4`, `REQ-PRO-043.5`)  
**Test Mapping**: `TST-PRO-043` (`TST-PRO-043.1`, `TST-PRO-043.2`, `TST-PRO-043.4`, `TST-PRO-043.5`)  
**Branch**: `feature/ATT-6727`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-11  

---

## 1. Problem Description & Background
Following ATT-6179, an audit discovered 8 drifted or fictitious component names across `docs/architecture.md` and `docs/architecture/interfaces.md` (`UserPreferences`, `EquipmentDatabaseManager`, `PrivacyZoneManager`, `BillingManager`, `TTTheme`, `CustomLineChart`, `ElevationPoint`, `StoreMetadataContractTest`), and a misplaced allocation of `PowerHrDynamicsCalculator` in the `REV` row. Additionally, `AspiceSweAuditContractTest` only checked a hardcoded 6-item blacklist. This plan executes the comprehensive symbol cleanup and implements an automated dynamic component-existence contract test that continuously verifies that every software component cited in architecture documents resolves to authentic code.

---

## 2. Traceability & Requirements Mapping
* **REQ-PRO-043.1** (*Component Nomenclature Code-Grounding*): Replace drifted symbols in `docs/architecture.md` and `docs/architecture/interfaces.md`, purge fictitious GPS obfuscation, and relocate Power-HR dynamics to `PER`/`PERF` (`TST-PRO-043.1`).
* **REQ-PRO-043.4** (*Drifted Component Absence & Blacklist*): Expand blacklist in `AspiceSweAuditContractTest.testArchitectureDocument_authenticComponentNomenclature` to assert absence of all 14 drifted tokens (`TST-PRO-043.2`).
* **REQ-PRO-043.5** (*Dynamic Architecture-to-Code Conformance Contract*): Implement `testArchitectureDocuments_allAllocatedAndSpecifiedComponentsExistInCodebase` in `AspiceSweAuditContractTest.kt` verifying dynamic component resolution against `app/src/main/` and `tools/` with strict SDK allow-list (`TST-PRO-043.4`).

---

## 3. System Invariants & Preserved Behavior
1. **Zero Runtime Code Impact**: Changes are strictly confined to architectural documentation and test verification code.
2. **ASPICE SWE.2 Conformance**: Retain all base practices (`BP.1`–`BP.5`), 9-domain shard taxonomy, and dynamic diagrams.
3. **Strict Code Lock**: No implementation edits prior to Gate 3 PASS.
4. **Single Atomic Commit**: 1 squashed commit onto `sprint/2026-46.2` under `/tmp/att_build_merge.lock`; parent ticket ends in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: Software Architecture Document (`docs/architecture.md`)
* Line 34: `SettingsRepo["TuningPreferencesDataStore & SettingsDataStore"]`.
* Line 89 (`PER`, `PERF`): Append `PowerHrDynamicsCalculator`, `PowerHrDynamicsInfoDialog` and description.
* Line 92 (`LOC`, `MON`, `PRI`, `SET`): Replace `EquipmentDatabaseManager` with `EquipmentDbHelper`, `EquipmentRepository`; remove `PrivacyZoneManager` and `"GPS obfuscation"`; replace `BillingManager` with `ProFeatureManager`, `SupporterTier`, `GarageLeagueDetector`.
* Line 93 (`UI`, `UNT`): Replace `TTTheme` with `ATrainingTrackerTheme`; replace `CustomLineChart` with `TelemetryMetricGraph`, `ElevationProfile`.
* Line 95 (`REV`): Remove `PowerHrDynamicsCalculator` and `PowerHrDynamicsInfoDialog`.
* Line 96 (`STP`): Replace `StoreMetadataContractTest` with `PlayStoreListingContractTest`.

### Component 2: Interface Specifications (`docs/architecture/interfaces.md`)
* Lines 106, 108: Replace `ElevationPoint` with `PathPoint`.

### Component 3: SWE Conformance Contract Test (`AspiceSweAuditContractTest.kt`)
* Expand `driftedComponents` list to all 14 invalid tokens.
* Add `@Test fun testArchitectureDocuments_allAllocatedAndSpecifiedComponentsExistInCodebase()` to dynamically scan and verify all allocated and specified software components against authentic declarations in `app/src/main/` and `tools/`.

### UI Consistency (Rule 23)
* No UI changes. Pure architectural documentation and contract test verification.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update Software Architecture Document
* File: `docs/architecture.md`
* Changes: Update lines 34, 89, 92, 93, 95, 96 with authentic symbols.

### Step 2: Update Software Interfaces Document
* File: `docs/architecture/interfaces.md`
* Changes: Replace `ElevationPoint` with `PathPoint` in lines 106 and 108.

### Step 3: Implement Dynamic Conformance Test & Expand Blacklist
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/build/AspiceSweAuditContractTest.kt`
* Changes: Expand `driftedComponents` in `testArchitectureDocument_authenticComponentNomenclature()` and add `testArchitectureDocuments_allAllocatedAndSpecifiedComponentsExistInCodebase()`.
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest*"`

### Step 4: Requirement Governance & Clean-Room Suite Execution
* Commands:
  - `python3 tools/verify_requirement_governance.py`
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest"`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest"` and verify 100% pass rate.
* **Rollback**: Branch isolation allows full revert via `git checkout -- .` and `git clean -fd`.
