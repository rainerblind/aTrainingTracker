# Stage 1 Analysis: ATT-6734 - Expand ArchitectureConformanceContractTest to physiology/ and banalservice/ui/, strip comments, and guard database/ upward imports

**Ticket**: [ATT-6734](https://rainerblind.atlassian.net/browse/ATT-6734)  
**Sub-task**: [ATT-6873](https://rainerblind.atlassian.net/browse/ATT-6873) (`[Analysis]`)  
**Parent Epic**: [ATT-6142](https://rainerblind.atlassian.net/browse/ATT-6142) (*ASPICE CL2 – System Level, Architecture Conformance, Integration & Quality Gates*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.2`  
**Branch**: `feature/ATT-6734`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
During the Sprint 2026-46.1 review of ATT-6182 (SWE.2 Automated Architecture Conformance Tests), three coverage gaps and ratcheting weaknesses were identified in `ArchitectureConformanceContractTest.kt`:
1. **Unchecked Core Scientific Engines**: `testDomainLayer_pureJvmIsolation()` scans only `domain/` (3 crop/lap files), omitting `physiology/` (`PowerPhysiologyCalculator`, `DynamicalPowerHrEngine`, `PowerHrDynamicsCalculator`, `SlidingWindowPowerAccumulator`) and pure Layer 2 calculators.
2. **Omitted Secondary UI Package**: `testUiLayer_noUnallowlistedDatabaseManagerReferences()` scans only `trainingtracker/ui/`, completely skipping `banalservice/ui/` (device tabs, pairing, sport-type Compose UI with 5 existing callers).
3. **Comment False Positives, Downward Ratchet Gap & Upward Imports**:
   - `DB_PATTERN` scans raw text including KDoc/comments, causing false positives if documentation references a `*DatabaseManager`.
   - `BASELINE_ALLOWED_UI_DB_CALLERS` statically checks size 24 without asserting `(baseline - detectedCallers).isEmpty()`; 6 refactored UI files remain in the baseline instead of ratcheting down.
   - Persistence layer (`database/`) is unguarded against upward imports (`androidx.compose.*` or presentation UI screens/composables).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification**:
  * *Domain/Physiology Audit*: Confirmed `physiology/` contains 4 files. `PowerHrDynamicsCalculator.kt` imports `CardioPowerDynamicsRecord` (an immutable data class in `database`). Regex must forbid `*DatabaseManager` / `*DbHelper` and non-record database classes while asserting zero Android View/Widget/Compose imports.
  * *UI Package & Baseline Ratchet Audit*:
    - Audited `trainingtracker/ui/`: 6 legacy files (`TrackingTabsViewModel`, `EditEquipmentDialog`, `StravaActivitySection`, `EquipmentViewModel`, `AthleteProfileViewModel`, `EquipmentSensorMatrixScreen`) no longer reference database managers. Active callers = 18.
    - Audited `banalservice/ui/`: Exactly 5 active callers detected (`DeviceDataRepository`, `RawDeviceDataProvider`, `DevicesTabbedViewModel`, `EditSportTypeDialog`, `SportTypeViewModel`).
    - Combined ratcheted baseline = $18 + 5 = 23$ callers.
  * *Comment Stripping Audit*: Verified regex comment stripping (`/* ... */` and `// ...`) cleanly eliminates KDoc/inline comment false positives.
  * *Database Upward Import Audit*: In `database/`, found unused `LookaheadScope` in `SportTypeEquipmentLinkManager.kt` and UI path rendering in `WorkoutClusterCorridorDetector.kt`. Moving UI rendering helpers to `ui/clusters/` and removing unused imports ensures database layer is 100% free of Compose and UI screens.

---

## 3. Scope Grounding & Non-Goals
* **In-Scope Goals**:
  * Expand `testDomainLayer_pureJvmIsolation()` to scan both `domain/` and `physiology/`.
  * Strip comments before `DB_PATTERN` evaluation.
  * Expand `testUiLayer_noUnallowlistedDatabaseManagerReferences()` to scan both `trainingtracker/ui/` and `banalservice/ui/`.
  * Ratchet baseline from 24 to 23 callers, enforcing `(baseline - detectedCallers).isEmpty()` and non-increasing size.
  * Add `testDatabaseLayer_noUpwardUiOrComposeImports()` guarding `database/` against `androidx.compose.*` and UI screens/composables.
  * Remove unused `LookaheadScope` from `SportTypeEquipmentLinkManager.kt` and relocate UI helper `ColoredPolylinePath` / `getVariantColor` to `ui/clusters/`.
* **Out-of-Scope Non-Goals**:
  * Refactoring existing allowlisted legacy UI database callers (tracked under separate technical debt tickets).
  * Altering database schema or SQLite query semantics.

---

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PRO-050` (*Automated Architecture Conformance Testing*), refining `REQ-PRO-034` (*SWE.1/2/4/6 Hardening*). Targets `ArchitectureConformanceContractTest.kt` and `SourceTextReadingAuditContractTest.kt`.
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-46.1 under ticket ATT-6182 to fulfill ASPICE pre-assessment finding C-08.
* **Root Reason for Existing Formulation**: Initial implementation targeted the primary `ui/` and `domain/` directories. It overlooked `banalservice/ui/` and `physiology/`, had not yet experienced comment false positives, and set a static size without downward ratcheting enforcement.
* **Preservation of Core Invariants**: Ratcheting baseline down from 24 to 23 and expanding coverage protects SWE.2 architectural layering without breaking existing production behavior.

---

## 5. System Invariants & Risk Assessment
* **Core Invariants**:
  * All 23 legitimate legacy UI callers continue functioning without breakage.
  * `ArchitectureConformanceContractTest` passes with 100% success.
  * Living documentation master tables maintain 100% mathematical parity.
* **Risk Rating**: `LOW`. Test harness and architectural guard refinement with minimal dead-import cleanup.
