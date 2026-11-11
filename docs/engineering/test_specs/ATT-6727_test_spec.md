# Stage 2: Requirement & Test Specification - ATT-6727: Audit all architecture docs against source symbols and enforce dynamic component-existence contract test

**Ticket**: [ATT-6727](https://rainerblind.atlassian.net/browse/ATT-6727)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PRO-043` (`REQ-PRO-043.1`, `REQ-PRO-043.4`, `REQ-PRO-043.5`)  
**Test Spec ID**: `TST-PRO-043` (`TST-PRO-043.1`, `TST-PRO-043.2`, `TST-PRO-043.4`, `TST-PRO-043.5`)  
**Branch**: `feature/ATT-6727`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`)  
**Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-PRO-043)

### 1.1 Problem Statement & Rationale
During Sprint 2026-46.1 review of ATT-6179, an automated audit revealed that while 6 placeholder components were removed, 8 drifted or fictitious software component names remained in `docs/architecture.md` and `docs/architecture/interfaces.md`: `UserPreferences`, `EquipmentDatabaseManager`, `PrivacyZoneManager`, `BillingManager`, `TTTheme`, `CustomLineChart`, `ElevationPoint`, and `StoreMetadataContractTest`. Furthermore, `PowerHrDynamicsCalculator` / `PowerHrDynamicsInfoDialog` were misplaced under the `REV` shard instead of `PER`/`PERF`, and `AspiceSweAuditContractTest` only verified a static 6-element blacklist instead of enforcing dynamic symbol-to-codebase existence.

### 1.2 Functional & Architectural Requirements
1. `docs/architecture.md` and `docs/architecture/interfaces.md` SHALL replace all drifted symbols with authentic production classes (`SettingsDataStore`, `EquipmentDbHelper`, `EquipmentRepository`, `ProFeatureManager`, `SupporterTier`, `GarageLeagueDetector`, `ATrainingTrackerTheme`, `TelemetryMetricGraph`, `PathPoint`, `PlayStoreListingContractTest`).
2. Fictitious `PrivacyZoneManager` and `"GPS obfuscation"` SHALL be permanently purged from `docs/architecture.md`.
3. `PowerHrDynamicsCalculator` and `PowerHrDynamicsInfoDialog` SHALL be relocated to Post-Workout Analytics (`PER`, `PERF`).
4. `AspiceSweAuditContractTest.kt` SHALL expand its static blacklist to assert absence of all 14 drifted tokens (`TST-PRO-043.2`).
5. `AspiceSweAuditContractTest.kt` SHALL implement a dynamic component-existence contract test (`testArchitectureDocuments_allAllocatedAndSpecifiedComponentsExistInCodebase`) extracting backticked symbols from architecture allocation tables and interfaces, asserting each non-SDK project symbol resolves to an authentic declaration in `app/src/main/` or `tools/` (`TST-PRO-043.4`).

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-043` (*SWE.2 Architecture & Codebase Resynchronization*), refining `REQ-PRO-034`. Targets `docs/architecture.md`, `docs/architecture/interfaces.md`, and `AspiceSweAuditContractTest.kt`.
* **Historical Origin & Commit Trace**: Commit `053531cb` (`ATT-5431`) authored initial architecture documentation. Ticket `ATT-6179` addressed initial 6 placeholders; ticket `ATT-6727` completes the sweep of 8 secondary drifted symbols and adds dynamic automated conformance testing.
* **Root Reason for Existing Formulation**: Authored rapidly during initial ASPICE SWE.2 compliance work before dynamic symbol extraction tooling was available.
* **Preservation of Core Invariants**: ASPICE SWE.2 base practices (`BP.1`..`BP.5`), Mermaid dynamic models, 9-domain shard taxonomy, and 100% build contract test pass rate remain fully preserved. Zero production runtime code changes.

### 1.4 Acceptance Criteria (Given-When-Then)
* *Given* `docs/architecture.md` and `docs/architecture/interfaces.md`,
* *When* inspected for component nomenclature,
* *Then* all authentic symbols SHALL be present and all 14 drifted tokens (`RouteLearningEngine`, `GpxExporter`, `TcxExporter`, `WorkoutSamplesDb`, `MilestoneDetector`, `InAppReviewManager`, `UserPreferences`, `EquipmentDatabaseManager`, `PrivacyZoneManager`, `BillingManager`, `TTTheme`, `CustomLineChart`, `ElevationPoint`, `StoreMetadataContractTest`) SHALL NOT exist.
* *Given* `AspiceSweAuditContractTest.kt`,
* *When* executed via `./gradlew testDebugUnitTest`,
* *Then* static blacklist assertions and dynamic component existence assertions SHALL pass with 100% success.

---

## 2. Test Specification (TST-PRO-043)

### Test Case 1: Drifted Component Absence & Blacklist Test (`TST-PRO-043.2`)
* **Scope**: Contract Test (`AspiceSweAuditContractTest.testArchitectureDocument_authenticComponentNomenclature`)
* **Assertion**: Asserts all 14 drifted placeholder tokens are absent from `docs/architecture.md` and `docs/architecture/interfaces.md`.

### Test Case 2: Dynamic Architecture Symbol Existence Contract Test (`TST-PRO-043.4`)
* **Scope**: Dynamic Contract Test (`AspiceSweAuditContractTest.testArchitectureDocuments_allAllocatedAndSpecifiedComponentsExistInCodebase`)
* **Assertion**: Dynamically extracts allocated component symbols from `docs/architecture.md` Section 2 table and `docs/architecture/interfaces.md`, filters through explicit SDK allow-list, and asserts each project symbol exists as an authentic declaration in `app/src/main/` or `tools/`.

### Test Case 3: Tier 1 Targeted Contract Regression Suite (`TST-PRO-043.5`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest*"`
* **Expected Result**: 100% pass rate with 0 errors or failures.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-PRO-043.1` | Contract | `testArchitectureDocument_authenticComponentNomenclature` | `REQ-PRO-043.1` | Specified |
| `TST-PRO-043.2` | Contract | `testArchitectureDocument_authenticComponentNomenclature` | `REQ-PRO-043.1` | Specified |
| `TST-PRO-043.3` | Contract | `testArchitectureDocument_authenticComponentNomenclature` | `REQ-PRO-043.2`, `REQ-PRO-043.3` | Specified |
| `TST-PRO-043.4` | Dynamic Contract | `testArchitectureDocuments_allAllocatedAndSpecifiedComponentsExistInCodebase` | `REQ-PRO-043.5` | Specified |
| `TST-PRO-043.5` | Targeted Regression | `AspiceSweAuditContractTest` | `REQ-PRO-043` | Specified |
