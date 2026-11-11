# Stage 3: Implementation Plan - ATT-6734: Expand ArchitectureConformanceContractTest to physiology/ and banalservice/ui/, strip comments, and guard database/ upward imports

**Ticket**: [ATT-6734](https://rainerblind.atlassian.net/browse/ATT-6734)  
**Sub-task**: [ATT-6875](https://rainerblind.atlassian.net/browse/ATT-6875) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.2`  
**Requirement Mapping**: `REQ-PRO-050` (*Automated Architecture Conformance Testing*)  
**Test Mapping**: `TST-PRO-050` (*Automated Architecture Conformance Test Suite*)  
**Branch**: `feature/ATT-6734`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-11  

---

## 1. Problem Description & Background
`ArchitectureConformanceContractTest.kt` enforces SWE.2 layer boundaries but had three critical gaps: omitting `physiology/` from JVM purity checks, omitting `banalservice/ui/` from UI DB isolation, and evaluating unstripped text (risking false positives in KDoc). Additionally, 6 refactored UI files remained in the baseline, and the persistence layer (`database/`) lacked an upward import guard against `androidx.compose.*` and UI screens.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PRO-050` (`REQ-PRO-050.1` UI DB ban, `REQ-PRO-050.2` domain purity, `REQ-PRO-050.3` ratcheted baseline, `REQ-PRO-050.5` database upward import guard)
* **Test Mapping**: `TST-PRO-050` in `ArchitectureConformanceContractTest.kt`

---

## 3. System Invariants & Preserved Behavior
1. **Zero Production Runtime Breakage**: Refactoring unused imports and relocating UI rendering constructs strictly preserves all visual and algorithmic behavior.
2. **Backward Compatibility**: All 23 legitimate legacy UI database callers remain operable.
3. **Downward Ratcheting Invariance**: Baseline size must not exceed 23, and `(baseline - detectedCallers).isEmpty()` ensures refactored files cannot silently re-enter.

---

## 4. Proposed Architectural Changes
### Component 1: `ArchitectureConformanceContractTest.kt`
* Implement `stripComments(content: String): String` removing `/* ... */` and `// ...`.
* Expand `testDomainLayer_pureJvmIsolation()` to walk both `domain/` and `physiology/`, verifying zero `android.view`, `android.widget`, `androidx.compose`, `*DatabaseManager`, or `*DbHelper` imports (permitting pure `CardioPowerDynamicsRecord` entity).
* Expand `testUiLayer_noUnallowlistedDatabaseManagerReferences()` to evaluate both `trainingtracker/ui/` and `banalservice/ui/` with stripped comments.
* Ratchet `BASELINE_ALLOWED_UI_DB_CALLERS` to exactly 23 callers (18 in `trainingtracker/ui` + 5 in `banalservice/ui`), enforcing `(baseline - detectedCallers).isEmpty()` and `detectedCallers.size <= 23`.
* Add `testDatabaseLayer_noUpwardUiOrComposeImports()` asserting zero `androidx.compose.*` or UI screen/composable imports across all `database/` files.
* Add tripwire tests for comment false positives, `banalservice/ui` leaks, and database upward imports.

### Component 2: Persistence Layer Hygiene (`database/`)
* Remove unused `LookaheadScope` import in `SportTypeEquipmentLinkManager.kt`.
* Relocate UI rendering models (`ColoredPolylinePath`, `getVariantColor`, `CLUSTER_VARIANT_PALETTE`) from `WorkoutClusterCorridorDetector.kt` to `ui/clusters/WorkoutClusterCorridorUi.kt`, updating call sites and decoupling `database/` from `androidx.compose.*` and `ui/theme/`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (Gate 3 Check)**: Verify Gate 3 sign-off via `python3 tools/jira_util.py check-gate <IMPL_PLAN_KEY>`.
2. **Step 2 (Persistence Cleanup)**: Remove unused import in `SportTypeEquipmentLinkManager.kt`; move corridor UI path helpers to `ui/clusters/` and clean `WorkoutClusterCorridorDetector.kt`.
3. **Step 3 (Contract Test Expansion)**: Update `ArchitectureConformanceContractTest.kt` with comment stripping, `physiology/` scanning, `banalservice/ui/` scanning, 23-caller ratcheted baseline, and database upward import guard.
4. **Step 4 (Test Execution & Governance)**: Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest"` and `python3 tools/verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: `ArchitectureConformanceContractTest` passes with 100% success; governance check passes with 100% parity.
* **Rollback**: `git checkout -- .` to restore working tree.
