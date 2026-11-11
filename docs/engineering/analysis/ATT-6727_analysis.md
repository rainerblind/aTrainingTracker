# Stage 1 Analysis: ATT-6727 - Audit All Architecture Docs Against Source Symbols and Enforce Dynamic Component-Existence Contract Test

**Ticket**: [ATT-6727](https://rainerblind.atlassian.net/browse/ATT-6727)  
**Sub-task**: [ATT-6961](https://rainerblind.atlassian.net/browse/ATT-6961) (`[Analysis]`)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2` | **Branch**: `feature/ATT-6727`  
**Requirement Mapping**: `REQ-PRO-043` (`REQ-PRO-043.1`, `REQ-PRO-043.4`, `REQ-PRO-043.5`) | **Test Mapping**: `TST-PRO-043` (`TST-PRO-043.1`, `TST-PRO-043.2`, `TST-PRO-043.4`, `TST-PRO-043.5`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
During the Sprint 2026-46.1 review of ATT-6179 (*Resynchronize architecture document with code*), an automated symbol-vs-codebase audit revealed that although the 6 parenthetically cited placeholder components from ATT-6179's prompt were removed, several drifted or fictitious component names remain in `docs/architecture.md` and related architectural documentation:
1. `UserPreferences` (`docs/architecture.md` line 34) -> actual class is `SettingsDataStore`.
2. `EquipmentDatabaseManager` (`docs/architecture.md` line 92) -> actual classes are `EquipmentDbHelper` and `EquipmentRepository`.
3. `PrivacyZoneManager` (`docs/architecture.md` line 92, *"GPS obfuscation"*) -> fictitious unimplemented placeholder.
4. `BillingManager` (`docs/architecture.md` line 92) -> actual monetization classes are `ProFeatureManager`, `SupporterTier`, and `GarageLeagueDetector`.
5. `TTTheme` (`docs/architecture.md` line 93) -> actual Compose theme is `ATrainingTrackerTheme` in `Theme.kt`.
6. `CustomLineChart` (`docs/architecture.md` line 93) -> non-existent class; actual chart components are `TelemetryMetricGraph` and `ElevationProfile`.
7. `ElevationPoint` (`docs/architecture/interfaces.md` lines 106, 108) -> actual class parameter for `ClimbDetector.detectClimbs` is `PathPoint`.
8. `StoreMetadataContractTest` (`docs/architecture.md` line 96) -> actual contract test class is `PlayStoreListingContractTest`.
9. Misplaced Row Assignment: On `docs/architecture.md` line 95, `PowerHrDynamicsCalculator` and `PowerHrDynamicsInfoDialog` were erroneously placed in the *Jubilee Milestones & App Reviews (`REV`)* row instead of *Post-Workout Analytics & Periods (`PER`, `PERF`)*.
10. Statically Weak Contract Assertion: `AspiceSweAuditContractTest.testArchitectureDocument_authenticComponentNomenclature()` only checks a static 6-element blacklist instead of dynamically extracting component symbols across architectural documents and verifying their existence against authentic declarations in `app/src/main/`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

### Technical Investigation
* **Historical Context**: Early ASPICE compliance documents (`ATT-5431`, `ATT-6179`) were drafted rapidly with heuristic or aspirational naming. While ATT-6179 purged the prominent placeholders (`RouteLearningEngine`, `GpxExporter`, etc.), secondary table cells and mermaid graphs were not exhaustively parsed against production declarations.
* **Component Code-Grounding Audit**:
  * `SettingsDataStore.kt` provides reactive DataStore preferences; `UserPreferences` is a phantom symbol.
  * Vehicle equipment persistence uses `EquipmentDbHelper` and `EquipmentRepository`; `EquipmentDatabaseManager` does not exist.
  * GPS privacy obfuscation / `PrivacyZoneManager` has zero backing classes or requirements; it was an aspirational placeholder that must be pruned.
  * In-app support uses `ProFeatureManager`, `SupporterTier`, and `GarageLeagueDetector`; `BillingManager` does not exist.
  * Jetpack Compose UI theming is provided by `ATrainingTrackerTheme` (`Theme.kt`).
  * Telemetry plotting uses `TelemetryMetricGraph` and `ElevationProfile`; `CustomLineChart` does not exist.
  * `ClimbDetector.detectClimbs(points: List<PathPoint>)` consumes `PathPoint`; `ElevationPoint` does not exist.
  * `PlayStoreListingContractTest.kt` guards store presence; `StoreMetadataContractTest` is obsolete nomenclature.
  * Dynamical Power-HR system identification calculates cardiac drift on completed workouts, belonging squarely to `PER`/`PERF`.
* **Independent Peer Review & Verification (4-Augen-Prinzip)**:
  * Independently verified `docs/architecture.md` line 34, 92, 93, 95, 96 and confirmed exact presence of drifted symbols.
  * Independently audited `docs/architecture/interfaces.md` lines 106 and 108 and discovered `ElevationPoint` drift against `PathPoint`.
  * Verified that `AspiceSweAuditContractTest.kt` currently uses only static lists (`authenticComponents` and `driftedComponents`). Designing a dynamic scanner that extracts all backticked identifiers in architecture allocation tables and interfaces and asserts existence in `app/src/main/` establishes permanent, continuous conformance.

---

## 3. User Scope Grounding (ATT-1250) & Complexity Split Guardrail (REQ-PRO-029)
* **In-Scope Goals**:
  1. Synchronize `docs/architecture.md`:
     - Line 34: `UserPreferences` -> `SettingsDataStore`.
     - Line 92: `EquipmentDatabaseManager` -> `EquipmentDbHelper`, `EquipmentRepository`; remove `PrivacyZoneManager` and `"GPS obfuscation"`; replace `BillingManager` with `ProFeatureManager`, `SupporterTier`, `GarageLeagueDetector`.
     - Line 93: `TTTheme` -> `ATrainingTrackerTheme`; replace `CustomLineChart` with `TelemetryMetricGraph`, `ElevationProfile`.
     - Line 89 / 95: Move `PowerHrDynamicsCalculator` and `PowerHrDynamicsInfoDialog` from `REV` row to `PER`/`PERF` row.
     - Line 96: `StoreMetadataContractTest` -> `PlayStoreListingContractTest`.
  2. Synchronize `docs/architecture/interfaces.md`:
     - Lines 106, 108: Replace `ElevationPoint` with `PathPoint`.
  3. Expand `AspiceSweAuditContractTest.kt`:
     - Extend blacklist in `testArchitectureDocument_authenticComponentNomenclature()` to include all 8 newly identified drifted symbols.
     - Implement dynamic component existence contract test (`testArchitectureDocuments_allAllocatedAndSpecifiedComponentsExistInCodebase()`) that parses architecture documents and validates project component symbols against `app/src/main/` with an explicit SDK allow-list.
  4. Synchronize living documentation (`req_process_stability.md`, `tst_process_stability.md`).
* **Out-of-Scope Non-Goals**:
  * Modifying system requirements in `docs/system/sysreq_*.md` (already grounded under ATT-6164/ATT-6741).
  * Refactoring production runtime code (pure architectural documentation and build test hardening).
* **Complexity Rating**: 2 SP (Standard). Scope is strictly bounded to architectural markdown files and contract test assertions.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-043` (*SWE.2 Architecture & Authentic Codebase Resynchronization*), refining `REQ-PRO-034`. Targets `docs/architecture.md`, `docs/architecture/interfaces.md`, and `AspiceSweAuditContractTest.kt`.
* **Historical Origin & Commit Trace**: Introduced in sprint `2026-46.1` via ATT-6179 (commit `5cbcf81f`).
* **Root Reason for Existing Formulation**: ATT-6179 corrected the 6 examples cited in its prompt but did not perform an exhaustive symbol sweep of the entire document or implement automated dynamic extraction.
* **Preservation of Core Invariants**: ASPICE SWE.2 base practices (`BP.1` through `BP.5`), Mermaid dynamic diagrams, 9-domain shard taxonomy, and 100% build contract test pass rate remain fully preserved.

---

## 5. Architectural Strategy & High-Level Solution
* **Architectural Docs Cleanup**: Edit `docs/architecture.md` and `docs/architecture/interfaces.md` to replace all 8 drifted symbols, prune fictitious GPS obfuscation, and relocate Power-HR dynamics to `PER`/`PERF`.
* **Dynamic Contract Testing**:
  * Extract all backticked identifiers from Section 2 of `docs/architecture.md` (allocated components) and Section 1/2 of `docs/architecture/interfaces.md`.
  * Filter through a strict allow-list of external SDK types (`WorkManager`, `HealthConnectClient`, `NavHost`, `StateFlow`, `Flow`, `Job`, `Context`, `LatLng`, `OkHttpClient`, etc.), test classes (`PlayStoreListingContractTest`, `AspiceSweAuditContractTest`), and tools (`jira_util.py`, `verify_requirement_governance.py`).
  * Assert each remaining project component exists as a file or declared `class`, `interface`, `object`, `enum class`, or top-level function in `app/src/main/`.
  * Assert absence of the full blacklist of 14 drifted placeholders.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero modifications to Android production runtime code in `app/src/main/`.
  2. 100% unit and contract test pass rate.
  3. Strict single squashed commit onto `sprint/2026-46.2` under `/tmp/att_build_merge.lock`.
  4. Parent ticket ends in `Final Review (Human)` assigned to Rainer Blind.
* **Risk Rating**: **LOW**. Documentation and build contract test refinement with zero runtime exposure.
